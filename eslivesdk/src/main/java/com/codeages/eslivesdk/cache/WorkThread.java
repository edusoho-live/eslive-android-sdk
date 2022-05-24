package com.codeages.eslivesdk.cache;

import com.blankj.utilcode.util.LogUtils;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpServerConnection;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpService;

import java.io.IOException;

public class WorkThread extends Thread {

    private final HttpService          httpservice;
    private final HttpServerConnection conn;

    public WorkThread(HttpService httpservice, HttpServerConnection conn) {
        super();
        this.httpservice = httpservice;
        this.conn = conn;
    }

    @Override
    public void run() {
        HttpContext context = new BasicHttpContext();
        try {
            while (!Thread.interrupted() && this.conn.isOpen()) {
                this.httpservice.handleRequest(this.conn, context);
            }
        } catch (ConnectionClosedException ex) {
            System.err.println("Client closed connection");
        } catch (IOException ex) {
            LogUtils.e("IOException:" + ex.getMessage());
        } catch (HttpException ex) {
            LogUtils.e("Unrecoverable HTTP protocol violation:" + ex.getMessage());
        } catch (Exception ex) {
            LogUtils.e("Exception:" + ex.getMessage());
        } finally {
            try {
                this.conn.shutdown();
                this.conn.close();
            } catch (IOException ignore) {
            }
        }
        LogUtils.d("WorkThread:close");
    }

}