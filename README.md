# HttpAsyncClient

项目中加上家园聚会的信鸽推送后,业务逻辑在推送时会有一些卡顿,得优化下图送逻辑.

项目中之前的信鸽推送用的是同步的HttpClient,有一下几个问题:
1. 每次推送都新建一个HttpClient,其实可以用线程安全的client来实现,没必要每次有推送请求的额时候都创建一个对象.
2. 每次推送时,新的httpClient都会与信鸽服务器建立http连接.一次完整的http连接的建立和释放还是有一些耗时的,而且这些耗时是没必要的,完全可以复用连接.
2. 使用的是同步的httpClient,但业务逻辑并不太需要同步的获取结果,只需要在投送后对结果记日志而已,没必要在玩家线程里执行记日志的逻辑,来占用玩家的业务执行时间.

所以,可以将HttpClilent改成CloseableHttpAsyncClient,异步的执行推送并且处理执行结果.并且将CloseableHttpAsyncClient设为单例的,因为CloseableHttpAsyncClient是线程安全的,所以可以多个线程共享一个client.


##### 通过源码分析request的处理流程.

提交请求后,请求会执行到`PoolingNHttpClientConnectionManager`的`requestConnection()`方法,`requestConnection()`会从CPool中获取获取可用连接,或者将请求放入pending队列.大部分流程在`CPool`的`lease`方法里:
```
/**
     * @since 4.3
     */
    public Future<E> lease(
            final T route, final Object state,
            final long connectTimeout, final long leaseTimeout, final TimeUnit tunit,
            final FutureCallback<E> callback) {
        Args.notNull(route, "Route");
        Args.notNull(tunit, "Time unit");
        Asserts.check(!this.isShutDown.get(), "Connection pool shut down");
        final BasicFuture<E> future = new BasicFuture<E>(callback);
        //加锁 抢资源的时候就得考虑线程安全的问题了
        this.lock.lock();
        try {
            final long timeout = connectTimeout > 0 ? tunit.toMillis(connectTimeout) : 0;
            final LeaseRequest<T, C, E> request = new LeaseRequest<T, C, E>(route, state, timeout, leaseTimeout, future);
            //这里就是把request提交进去,看能不能找到连接来处理
            final boolean completed = processPendingRequest(request);
            //如果请求没有被处理,则放入leasingRequests中,processPendingRequests()方法会处理这个队列中的请求.
            if (!request.isDone() && !completed) {
                this.leasingRequests.add(request);
            }
            //请求处理完毕的放入completedRequests中等待触发回调.
            if (request.isDone()) {
                this.completedRequests.add(request);
            }
        } finally {
            this.lock.unlock();
        }
        //触发已完成请求的回调方法
        fireCallbacks();
        return future;
    }
```
可以看到,lease()方法已经包含了提交请求、处理请求和触发回调方法这一整套逻辑.现在可以关注下已提交的请求会被怎样处理.
```
 private boolean processPendingRequest(final LeaseRequest<T, C, E> request) {
        //获取route,因为连接是按route分配的
        final T route = request.getRoute();
        final Object state = request.getState();
        //获取请求的超时时间
        final long deadline = request.getDeadline();
        //超时请求触发failed回调
        final long now = System.currentTimeMillis();
        if (now > deadline) {
            request.failed(new TimeoutException());
            return false;
        }
        //根据route回去对应的池子
        final RouteSpecificPool<T, C, E> pool = getPool(route);
        E entry;
        for (;;) {
        //从avaliable中获取空闲的连接,获取到了则将连接取出,并加入到leased中
            entry = pool.getFree(state);
            //没有空闲连接,则执行后续逻辑
            if (entry == null) {
                break;
            }
            //获取到的连接是关闭的或者超时的,则移除连接.
            if (entry.isClosed() || entry.isExpired(System.currentTimeMillis())) {
                entry.close();
                this.available.remove(entry);
                pool.free(entry, false);
            } else {
                break;
            }
        }
        //获取到了连接后,将任务和连接绑定,从available中移除连接,并放入leased中表示正在使用中
        if (entry != null) {
            this.available.remove(entry);
            this.leased.add(entry);
            request.completed(entry);
            onReuse(entry);
            onLease(entry);
            return true;
        }

//获取不到可用连接的话,需要创建新连接,先移除多余的连接
        // New connection is needed
        final int maxPerRoute = getMax(route);
        // Shrink the pool prior to allocating a new connection
        final int excess = Math.max(0, pool.getAllocatedCount() + 1 - maxPerRoute);
        if (excess > 0) {
            for (int i = 0; i < excess; i++) {
                final E lastUsed = pool.getLastUsed();
                if (lastUsed == null) {
                    break;
                }
                lastUsed.close();
                this.available.remove(lastUsed);
                pool.remove(lastUsed);
            }
        }
//如果连接没有到达上线,则异步创建新的连接
        if (pool.getAllocatedCount() < maxPerRoute) {
            final int totalUsed = this.pending.size() + this.leased.size();
            final int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
            if (freeCapacity == 0) {
                return false;
            }
            final int totalAvailable = this.available.size();
            if (totalAvailable > freeCapacity - 1) {
                if (!this.available.isEmpty()) {
                    final E lastUsed = this.available.removeLast();
                    lastUsed.close();
                    final RouteSpecificPool<T, C, E> otherpool = getPool(lastUsed.getRoute());
                    otherpool.remove(lastUsed);
                }
            }

            final SocketAddress localAddress;
            final SocketAddress remoteAddress;
            try {
                remoteAddress = this.addressResolver.resolveRemoteAddress(route);
                localAddress = this.addressResolver.resolveLocalAddress(route);
            } catch (final IOException ex) {
                request.failed(ex);
                return false;
            }

            final SessionRequest sessionRequest = this.ioreactor.connect(
                    remoteAddress, localAddress, route, this.sessionRequestCallback);
            final int timout = request.getConnectTimeout() < Integer.MAX_VALUE ?
                    (int) request.getConnectTimeout() : Integer.MAX_VALUE;
            sessionRequest.setConnectTimeout(timout);
            this.pending.add(sessionRequest);
            pool.addPending(sessionRequest, request.getFuture());
            return true;
        } else {
            return false;
        }
    }
```
![image](http://note.youdao.com/yws/res/5043/21C37324197F4C239D901DEE52E797B4)

所以,请求分配连接的过程就是:
1. 根据route去routeToPool中过去对应的可用连接,获取到了则将连接从avaliable中移除,并添加到leased中.没有获取到则进入下一步
2. 如果当前route和整个pool的连接都没有达到上限,则异步的建立新的连接,并且将当前任务的future放入池子的`pending`中异步处理
```
 pool.addPending(sessionRequest, request.getFuture());
```

##### 一些超时参数
1. connectTimeout  --  连接超时时间

根据网络情况，内网、外网等，可设置连接超时时间为2秒，具体根据业务调整

2. socketTimeout  --  读超时时间（等待数据超时时间）

需要根据具体请求的业务而定，如请求的API接口从接到请求到返回数据的平均处理时间为1秒，那么读超时时间可以设置为2秒，考虑并发量较大的情况，也可以通过性能测试得到一个相对靠谱的值。
socketTimeout有默认值，也可以针对每个请求单独设置。

3. connectionRequestTimeout  --  从池中获取连接超时时间

建议设置500ms即可，不要设置太大，这样可以使连接池连接不够时不用等待太久去获取连接，不要让大量请求堆积在获取连接处，尽快抛出异常，发现问题


参考链接:https://www.cnblogs.com/trust-freedom/p/6349502.html
