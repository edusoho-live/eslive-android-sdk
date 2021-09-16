package com.codeages.livecloudsdk.http;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

public class LiveHttpClient {

    private static final String              TOKEN       = "Authorization";
    private final        Map<String, String> mHeaderMaps = new HashMap<>();

    public <T> T createApi(final Class<T> clazz) {
        return RetrofitClient.getInstance(mHeaderMaps).create(clazz);
    }

    public LiveHttpClient addToken(String token) {
        if (!TextUtils.isEmpty(token)) {
            mHeaderMaps.put(TOKEN, String.format("Bearer %s", token));
        }
        return this;
    }

}
