package httputils;

import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description: httpAsyncClient连接的管理类
 * @Author: shenpeng
 * @Date: 2020-05-29
 */
public class IdleConnMonitorThread extends Thread {

    private PoolingNHttpClientConnectionManager cm;

    private volatile boolean shutdown;

    private Logger logger = LoggerFactory.getLogger(IdleConnMonitorThread.class);

    public IdleConnMonitorThread(PoolingNHttpClientConnectionManager cm) {
        super();
        this.cm = cm;
    }

    @Override
    public void run() {
        try {
            while (!shutdown) {
                synchronized (this) {
                    wait(50000);
                    //关闭异常线程
                    cm.closeExpiredConnections();
                }
            }
        } catch (Exception e) {
            logger.error("xinge exception" + e.getMessage(), e);
        }
    }

    public void shutdown() {
        shutdown = true;
        synchronized (this) {
            notifyAll();
        }
    }
}
