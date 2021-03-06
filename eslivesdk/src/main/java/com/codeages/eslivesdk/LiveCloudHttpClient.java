package com.codeages.eslivesdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LiveCloudHttpClient {

    public static void get(final String requestUrl, final OnRequestCallBack callBack) {
        get(requestUrl, 3000, callBack);
    }

    public static void get(final String requestUrl, int timeout, final OnRequestCallBack callBack) {
        HttpThreadPoolUtils.execute(() -> getRequest(requestUrl, timeout, callBack));
    }

    public static void post(final String requestUrl, final String params, final OnRequestCallBack callBack) {
        post(requestUrl, params, 3000, callBack);
    }

    public static void post(final String requestUrl, final String params, int timeout, final OnRequestCallBack callBack) {
        HttpThreadPoolUtils.execute(() -> postRequest(requestUrl, params, timeout, callBack));
    }

    private static void getRequest(String requestUrl, int timeout, OnRequestCallBack callBack) {
        String successMessage = null;
        String errorMessage = null;
        InputStream inputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
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

                successMessage = baos.toString();
            } else {
                errorMessage = "请求失败 code:" + connection.getResponseCode();
            }

        } catch (IOException e) {
            errorMessage = e.getMessage();
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
                errorMessage = e.getMessage();
                e.printStackTrace();
            }
        }
        if (callBack != null) {
            callBack.onCompletion(successMessage, errorMessage);
        }
    }

    private static void postRequest(String requestUrl, String params, int timeout, OnRequestCallBack callBack) {
        String successMessage = null;
        String errorMessage = null;
        InputStream inputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
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

                successMessage = baos.toString();
            } else {
                errorMessage = "请求失败 code:" + connection.getResponseCode();
            }

        } catch (IOException e) {
            errorMessage = e.getMessage();
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
                errorMessage = e.getMessage();
                e.printStackTrace();
            }
        }
        if (callBack != null) {
            callBack.onCompletion(successMessage, errorMessage);
        }
    }

    public interface OnRequestCallBack {
        void onCompletion(String successMsg, String errorMsg);
    }



}
