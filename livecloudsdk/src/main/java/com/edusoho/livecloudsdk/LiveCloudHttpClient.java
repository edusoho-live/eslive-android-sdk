package com.edusoho.livecloudsdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LiveCloudHttpClient {

    public static void get(final String requestUrl, final OnRequestCallBack callBack) {
        new Thread() {
            public void run() {
                getRequest(requestUrl, callBack);
            }
        }.start();
    }

    public static void post(final String requestUrl, final String params, final OnRequestCallBack callBack) {
        new Thread() {
            public void run() {
                postRequest(requestUrl, params, callBack);
            }
        }.start();
    }

    private static void getRequest(String requestUrl, OnRequestCallBack callBack) {
        boolean isSuccess = false;
        String message;

        InputStream inputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            // 设置是否从httpUrlConnection读入，默认情况下是true;
            connection.setDoInput(true);
            // 设置是否向httpUrlConnection输出，如果是post请求，参数要放在http正文内，因此需要设为true, 默认是false;
            //connection.setDoOutput(true);//Android  4.0 GET时候 用这句会变成POST  报错java.io.FileNotFoundException
            connection.setUseCaches(false);
            connection.connect();//
            int contentLength = connection.getContentLength();
            if (connection.getResponseCode() == 200) {
                inputStream = connection.getInputStream();//会隐式调用connect()
                baos = new ByteArrayOutputStream();
                int readLen;
                byte[] bytes = new byte[1024];
                while ((readLen = inputStream.read(bytes)) != -1) {
                    baos.write(bytes, 0, readLen);
                }

                message = baos.toString();
                isSuccess = true;
            } else {
                message = "请求失败 code:" + connection.getResponseCode();
            }

        } catch (IOException e) {
            message = e.getMessage();
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                message = e.getMessage();
                e.printStackTrace();
            }
        }
        if (callBack != null) {
            if (isSuccess) {
                callBack.onSuccess(message);
            } else {
                callBack.onError(message);
            }
        }
    }

    private static void postRequest(String requestUrl, String params, OnRequestCallBack callBack) {
        boolean isSuccess = false;
        String message;
        InputStream inputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            // 设置是否从httpUrlConnection读入，默认情况下是true;
            connection.setDoInput(true);
            // 设置是否向httpUrlConnection输出，如果是post请求，参数要放在http正文内，因此需要设为true, 默认是false;
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // set  params three way  OutputStreamWriter
            OutputStreamWriter out = new OutputStreamWriter(
                    connection.getOutputStream(), StandardCharsets.UTF_8);
            // 发送请求params参数
            out.write(params);
            out.flush();
            connection.connect();

            int contentLength = connection.getContentLength();
            if (connection.getResponseCode() == 200) {
                // 会隐式调用connect()
                inputStream = connection.getInputStream();
                baos = new ByteArrayOutputStream();
                int readLen;
                byte[] bytes = new byte[1024];
                while ((readLen = inputStream.read(bytes)) != -1) {
                    baos.write(bytes, 0, readLen);
                }

                message = baos.toString();
                isSuccess = true;
            } else {
                message = "请求失败 code:" + connection.getResponseCode();
            }

        } catch (IOException e) {
            message = e.getMessage();
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                message = e.getMessage();
                e.printStackTrace();
            }
        }
        if (callBack != null) {
            if (isSuccess) {
                callBack.onSuccess(message);
            } else {
                callBack.onError(message);
            }
        }
    }

    public interface OnRequestCallBack {
        void onSuccess(String json);
        void onError(String errorMsg);
    }

}
