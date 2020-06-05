package httputils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @Description:
 * @Author: shenpeng
 * @Date: 2020-06-05
 */
public class HttpCallBack implements FutureCallback<HttpResponse> {

    private static Logger logger = LoggerFactory.getLogger(HttpCallBack.class);

    @Override
    public void completed(HttpResponse result) {
        HttpEntity entityRet = result.getEntity();
        try {
            String retStr = EntityUtils.toString(entityRet);
            System.out.println("ret" + retStr);
            logger.info("dt xinge ret:" + retStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void failed(Exception ex) {
        logger.error(ex.getMessage(), ex);
    }

    @Override
    public void cancelled() {

    }
}
