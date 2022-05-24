package com.codeages.eslivesdk.api;

import com.codeages.eslivesdk.bean.PlayerInfo;
import com.codeages.eslivesdk.bean.ReplayMetaItem;
import com.codeages.eslivesdk.bean.ReplayMetas;

import java.util.List;

import retrofit2.http.GET;
import retrofit2.http.Url;
import rx.Observable;

public interface CloudApi {

    @GET
    Observable<ReplayMetas> getReplayMetas(@Url String url);

    @GET
    Observable<PlayerInfo> getPlayerInfo(@Url String url);

    @GET
    Observable<List<ReplayMetaItem>> getReplayInfo(@Url String url);
}
