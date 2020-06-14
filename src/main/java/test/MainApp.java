package test;

import com.alibaba.fastjson.JSONObject;
import httputils.HttpCallBack;
import httputils.HttpClientFactory;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description: 测试异步请求
 * @Author: shenpeng
 * @Date: 2020-06-05
 */
public class MainApp {

    private static Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        post();
    }

    /**
     * 模拟发送请求的方法
     * 
     * @param []
     * @return void
     * @Author: shenpeng
     * @Date: 2020-06-05
     */
    private static void post() {
        String url = "http://127.0.0.1:8080/index";
        JSONObject msg = new JSONObject();
        msg.put("i", 1);
        doAsyncPostJson(url, msg);
    }

    /**
     * 发送异步请求
     * 
     * @param [url, content]
     * @return java.lang.String
     * @Author: shenpeng
     * @Date: 2020-05-30
     */
    public static void doAsyncPostJson(String url, JSONObject content) {
        HttpPost httpPost = new HttpPost(url);
        try {
            StringEntity entity = new StringEntity(content.toString(), "utf-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);
            if (logger.isDebugEnabled()) {
                logger.debug(httpPost.toString());
            }
            logger.info("post start:" + content.toString());
            HttpClientFactory.getOtherAsynHttpClient().getCloseableHttpAsyncClient()
                    .execute(httpPost, new HttpCallBack());
            logger.info("post finish");
        } catch (Exception e) {
            logger.error("post url:" + url + " param:" + content, e);
        }
    }

}
