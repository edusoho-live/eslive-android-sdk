package com.codeages.livecloudsdk.server;

import com.blankj.utilcode.util.LogUtils;
import com.codeages.livecloudsdk.LiveCloudLocal;

import java.io.IOException;

public class HttpServerFactory {
    private static HttpServerFactory INSTANCE;
    private        HttpServer        mHttpServer;

    private HttpServerFactory() {

    }

    public static HttpServerFactory getInstance() {
        if (INSTANCE == null) {
            synchronized (HttpServerFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HttpServerFactory();
                }
            }
        }
        return INSTANCE;
    }

    public void start() {
        try {
            mHttpServer = new HttpServer(LiveCloudLocal.LOCAL_HTTP_PORT);
            mHttpServer.start();
        } catch (IOException ex) {
            LogUtils.e(ex.getMessage());
        }
    }

    public void stop() {
        if (mHttpServer != null) mHttpServer.stop();
        mHttpServer = null;
    }
}
