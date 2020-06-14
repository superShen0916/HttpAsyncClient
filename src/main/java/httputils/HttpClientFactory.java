package httputils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Base64Utils;

/**
 * @Description: 维护HttpClinet，分为ios的和其它的两类
 * @Author: shenpeng
 * @Date: 2020-06-05
 */
public class HttpClientFactory {

    private static AsynHttpClient iosAsynHttpClient = new AsynHttpClient(
            Base64Utils.encodeToString("xxxxxxx:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes()));

    private static AsynHttpClient otherAsynHttpClient = new AsynHttpClient(
            Base64Utils.encodeToString("xxxxxxx:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes()));

    private static Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    public static AsynHttpClient getIosAsynHttpClient() {
        return iosAsynHttpClient;
    }

    public static void setIosAsynHttpClient(AsynHttpClient iosAsynHttpClient) {
        HttpClientFactory.iosAsynHttpClient = iosAsynHttpClient;
    }

    public static AsynHttpClient getOtherAsynHttpClient() {
        return otherAsynHttpClient;
    }

    public static void setOtherAsynHttpClient(AsynHttpClient otherAsynHttpClient) {
        HttpClientFactory.otherAsynHttpClient = otherAsynHttpClient;
    }
}
