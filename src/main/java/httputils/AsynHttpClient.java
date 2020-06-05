package httputils;

import com.google.common.collect.Lists;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Description:
 * @Author: shenpeng
 * @Date: 2020-05-28
 */
public class AsynHttpClient {

    private Logger logger = LoggerFactory.getLogger(AsynHttpClient.class);

    //socket 超时时间
    public static int SOCKET_TIMEOUT = 3000;

    //连接超时时间
    public static int CONNECT_TIMEOUT = 3000;

    //请求超时时间
    public static int CONNECTION_REQUEST_TIMEOUT = 3000;

    //每条线路最大连接数
    public static int MAX_CONN_PER_ROUTE = 30;

    //最大连接数
    public static int MAX_CONN_TOTAL = 50;

    private CloseableHttpAsyncClient closeableHttpAsyncClient;

    public AsynHttpClient(String base64_auth_string) {
        String authorization = String.format(" Basic %s", base64_auth_string);

        List<NameValuePair> head = Lists.newArrayList();
        head.add(new BasicNameValuePair(HttpHeaders.AUTHORIZATION, authorization));

        List<Header> headers = Lists.newArrayList();

        for (NameValuePair pair : head) {
            Header header = new BasicHeader(pair.getName(), pair.getValue());
            headers.add(header);
        }
        headers.add(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

        ConnectionKeepAliveStrategy keepAliveStrategy = new ConnectionKeepAliveStrategy() {

            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                HeaderElementIterator it = new BasicHeaderElementIterator(
                        response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                while (it.hasNext()) {
                    HeaderElement he = it.nextElement();
                    String param = he.getName();
                    String value = he.getValue();
                    if (value != null && param.equalsIgnoreCase("timeout")) {
                        return Long.parseLong(value) * 1000;
                    }
                }
                return 60 * 1000L;
            }
        };
        ConnectingIOReactor connectingIOReactor = null;
        try {
            connectingIOReactor = new DefaultConnectingIOReactor();

            PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(
                    connectingIOReactor);
            cm.setMaxTotal(MAX_CONN_TOTAL);
            cm.setDefaultMaxPerRoute(MAX_CONN_PER_ROUTE);
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT)
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT).build();
            closeableHttpAsyncClient = HttpAsyncClients.custom().setDefaultHeaders(headers)
                    .setConnectionManager(cm).setDefaultRequestConfig(requestConfig)
                    .setMaxConnPerRoute(10).setMaxConnTotal(50)
                    .setKeepAliveStrategy(keepAliveStrategy).build();
            closeableHttpAsyncClient.start();
            new Thread(new IdleConnMonitorThread(cm)).start();
        } catch (IOReactorException e) {
            logger.error("AsynHttpClient初始化失败" + e.getMessage());
        }
    }

    public CloseableHttpAsyncClient getCloseableHttpAsyncClient() {
        return closeableHttpAsyncClient;
    }

    public void setCloseableHttpAsyncClient(CloseableHttpAsyncClient closeableHttpAsyncClient) {
        this.closeableHttpAsyncClient = closeableHttpAsyncClient;
    }
}
