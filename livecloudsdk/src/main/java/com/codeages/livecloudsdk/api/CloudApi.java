package com.codeages.livecloudsdk.api;

import com.codeages.livecloudsdk.bean.PlayerInfo;
import com.codeages.livecloudsdk.bean.ReplayMetaItem;
import com.codeages.livecloudsdk.bean.ReplayMetas;

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
