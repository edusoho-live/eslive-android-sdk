package com.codeages.livecloudsdk.http;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {

    private Map<String, String> mHeaderMaps;

    public RequestInterceptor(Map<String, String> headerMaps) {
        this.mHeaderMaps = headerMaps;
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();
        if (mHeaderMaps != null && mHeaderMaps.size() > 0) {
            for (Map.Entry<String, String> entry : mHeaderMaps.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return chain.proceed(builder.build());
    }
}
