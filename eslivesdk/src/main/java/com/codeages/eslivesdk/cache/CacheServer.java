package com.codeages.eslivesdk.cache;


import android.util.Log;

import com.codeages.eslivesdk.LiveCloudLocal;

import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class CacheServer extends Thread {

    private boolean                    isLoop;
    private boolean                    isPause;
    private ServerSocket               mServerSocket;
    private HttpRequestHandlerRegistry mHttpRequestHandlerRegistry;
    private ArrayList<Thread>          mThreadList;

    private CacheServer(Builder builder) {
        this.mThreadList = new ArrayList<>();
        // 创建HTTP请求执行器注册表
        mHttpRequestHandlerRegistry = new HttpRequestHandlerRegistry();
        mHttpRequestHandlerRegistry.register(builder.filter, builder.handler);
    }

    @Override
    public synchronized void start() {
        if (isLoop) {
            return;
        }
        super.start();
    }

    @Override
    public void run() {
        if (isLoop) {
            return;
        }
        init();
    }

    public void init() {
        mServerSocket = null;
        try {
            // 创建服务器套接字

            mServerSocket = new ServerSocket();
            mServerSocket.setReuseAddress(true);
            mServerSocket.bind(new InetSocketAddress(LiveCloudLocal.LOCAL_HTTP_PORT));

            // 创建HTTP协议处理器
            BasicHttpProcessor httpproc = new BasicHttpProcessor();
            // 增加HTTP协议拦截器
            httpproc.addInterceptor(new ResponseDate());
            httpproc.addInterceptor(new ResponseServer());
            httpproc.addInterceptor(new ResponseContent());
            httpproc.addInterceptor(new ResponseConnControl());
            // 创建HTTP服务
            HttpService httpService = new HttpService(httpproc,
                    new DefaultConnectionReuseStrategy(),
                    new DefaultHttpResponseFactory());
            // 创建HTTP参数
            HttpParams params = new BasicHttpParams();
            params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 15000)
                    .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE,
                            8 * 1024)
                    .setBooleanParameter(
                            CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
                    .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
                    .setParameter(CoreProtocolPNames.ORIGIN_SERVER,
                            "Android Server/1.1");
            // 设置HTTP参数
            httpService.setParams(params);

            // 设置HTTP请求执行器
            httpService.setHandlerResolver(mHttpRequestHandlerRegistry);
            /* 循环接收各客户端 */
            isLoop = true;
            while (isLoop && !Thread.interrupted()) {
                // 接收客户端套接字
                if (isPause) {
                    continue;
                }
                Socket socket = mServerSocket.accept();
                // 绑定至服务器端HTTP连接
                DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
                conn.bind(socket, params);
                // 派送至WorkerThread处理请求
                Thread t = new WorkThread(httpService, conn);
                t.setDaemon(true); // 设为守护线程
                t.start();
                mThreadList.add(t);
            }
        } catch (IOException e) {
            isLoop = false;
        } finally {
            try {
                if (mServerSocket != null) {
                    mServerSocket.close();
                    Log.d(null, "mServerSocket close");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void pause() {
        isPause = true;
    }

    void keepOn() {
        isPause = false;
    }

    void close() {
        isLoop = false;
        try {
            for (Thread t : mThreadList) {
                t.interrupt();
            }
            mThreadList.clear();
            mServerSocket.close();
        } catch (Exception e) {
            //nothing
        }
    }

    public static class Builder {

        private String             filter;
        private HttpRequestHandler handler;

        Builder setFilter(String filter) {
            this.filter = filter;
            return this;
        }

        Builder setHandler(HttpRequestHandler handler) {
            this.handler = handler;
            return this;
        }

        public CacheServer build() {
            return new CacheServer(this);
        }
    }
}