package com.codeages.livecloudsdk;

import android.content.Context;
import android.text.TextUtils;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.LogUtils;
import com.codeages.livecloudsdk.api.CloudApi;
import com.codeages.livecloudsdk.bean.ParseReplayResult;
import com.codeages.livecloudsdk.bean.PlayerInfo;
import com.codeages.livecloudsdk.bean.ReplayError;
import com.codeages.livecloudsdk.bean.ReplayInfo;
import com.codeages.livecloudsdk.bean.ReplayMetaItem;
import com.codeages.livecloudsdk.bean.ReplayMetas;
import com.codeages.livecloudsdk.http.LiveHttpClient;
import com.codeages.livecloudsdk.http.RxUtils;
import com.codeages.livecloudsdk.server.HttpServerFactory;
import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.StatusUtil;
import com.liulishuo.okdownload.core.dispatcher.DownloadDispatcher;
import com.tencent.mmkv.MMKV;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Emitter;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

public class LiveCloudSDK {

    private String         mKey;
    private String         mDownloadUrl;
    private String         mToken;
    private String         mUserId;
    private String         mUsername;
    private ReplayListener mReplayListener;

    private LiveCloudSDK(Builder builder) {
        this.mKey = builder.key;
        this.mDownloadUrl = builder.downloadUrl;
        this.mToken = builder.token;
        this.mUserId = builder.userId;
        this.mUsername = builder.username;
        this.mReplayListener = builder.replayListener;
    }

    public static void init(Context context) {
        MMKV.initialize(context);
        DownloadDispatcher.setMaxParallelRunningCount(1);
        LogUtils.getConfig().setGlobalTag("LiveCloudSDK");
        LogUtils.getConfig().setBorderSwitch(true);
    }

    /**
     * 播放缓存
     */
    public void playOfflineReplay(Context context) {
        HttpServerFactory.getInstance().start();
        if (TextUtils.isEmpty(mKey)) throw new RuntimeException("Key is not null!");
        ReplayMetas replayMetas = LiveCloudLocal.getReplay(mKey);
        if (replayMetas == null) {
            mReplayListener.onError(new ReplayError(ReplayError.NOT_EXIST));
            return;
        }
        String playUrl = "http://127.0.0.1:" + LiveCloudLocal.LOCAL_HTTP_PORT + "/live_cloud_player/index.html#/replay/22956?offline=1&roomName=" + replayMetas.getRoomName()
                + "&metasUrl=" + LiveCloudLocal.getMetasUrl(mKey)
                + "&userId=" + mUserId
                + "&userName=" + mUsername
                + "&showChat=" + replayMetas.getShowChat()
                + "&duration=" + replayMetas.getDuration()
                + "&proxyUrl=127.0.0.1:" + LiveCloudLocal.LOCAL_HTTP_PORT + "/live_cloud_replay/" + replayMetas.getRoomId();
        LiveReplayActivity.launch(context, playUrl);
    }

    /**
     * 下载缓存
     */
    public void startFetchReplay() {
        new LiveHttpClient()
                .addToken(mToken)
                .createApi(CloudApi.class)
                .getReplayMetas(mDownloadUrl)
                .compose(RxUtils.switch2Main())
                .subscribe(new Subscriber<ReplayMetas>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        LogUtils.e(e.getMessage());
                    }

                    @Override
                    public void onNext(ReplayMetas replayMetas) {
                        LiveCloudLocal.setReplay(mKey, replayMetas);
                        parsePlayerAndReplay(replayMetas);
                    }
                });
    }

    /**
     * 暂停下载
     */
    public void cancelFetchReplay() {
        ReplayMetas replayMetas = LiveCloudLocal.getReplay(mKey);
        if (replayMetas != null) {
            replayMetas.setStatus(ReplayInfo.Status.PAUSE.ordinal());
            LiveCloudLocal.setReplay(mKey, replayMetas);
        }
    }

    /**
     * 删除缓存
     */
    public void deleteReplay() {
        ReplayMetas replayMetas = LiveCloudLocal.getReplay(mKey);
        if (replayMetas != null) {
            int roomId = replayMetas.getRoomId();
            FileUtils.deleteAllInDir(LiveCloudLocal.getReplayDirectory(roomId + ""));
            FileUtils.delete(LiveCloudLocal.getReplayDirectory(roomId + ""));
        } else {
            mReplayListener.onError(new ReplayError(ReplayError.NOT_EXIST));
        }
        LiveCloudLocal.removeReplay(mKey);
        LiveCloudLocal.removeMetasUrl(mKey);
    }

    public String getReplaySize() {
        String      replaySize  = "";
        ReplayMetas replayMetas = LiveCloudLocal.getReplay(mKey);
        if (replayMetas != null) {
            int roomId = replayMetas.getRoomId();
            replaySize = FileUtils.getSize(LiveCloudLocal.getReplayDirectory(roomId + ""));
        }
        return replaySize;
    }

    public void updatePlayer() {
        LiveCloudLocal.removePlayerVersion();
        FileUtils.deleteAllInDir(LiveCloudLocal.getPlayerDirectory());
        final ReplayMetas[] finalReplayMetas = new ReplayMetas[1];
        new LiveHttpClient()
                .addToken(mToken)
                .createApi(CloudApi.class)
                .getReplayMetas(mDownloadUrl)
                .flatMap((Func1<ReplayMetas, Observable<PlayerInfo>>) replayMetas -> {
                    finalReplayMetas[0] = replayMetas;
                    return new LiveHttpClient().createApi(CloudApi.class).getPlayerInfo(replayMetas.getPlayerUrl());
                })
                .compose(RxUtils.switch2Main())
                .subscribe(new Subscriber<PlayerInfo>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(PlayerInfo playerInfo) {
                        fetchPlayer(playerInfo, finalReplayMetas[0], null, false);
                    }
                });
    }

    /**
     * 获取缓存下载状态 {@link ReplayInfo}q
     */
    public int getReplayStatus() {
        if (TextUtils.isEmpty(mKey)) throw new RuntimeException("Key is not null!");
        return LiveCloudLocal.getReplayStatus(mKey);
    }

    private void parsePlayerAndReplay(ReplayMetas replayMetas) {
        Observable<PlayerInfo>           playerObservable = new LiveHttpClient().createApi(CloudApi.class).getPlayerInfo(replayMetas.getPlayerUrl());
        Observable<List<ReplayMetaItem>> replayObservable = new LiveHttpClient().createApi(CloudApi.class).getReplayInfo(replayMetas.getDataUrl());
        Observable.combineLatest(playerObservable, replayObservable, (playerInfo, replayMetaItems) ->
                new ParseReplayResult(playerInfo, replayMetas, replayMetaItems, true))
                  .compose(RxUtils.switch2Main())
                  .subscribe(new Subscriber<ParseReplayResult>() {
                      @Override
                      public void onCompleted() {

                      }

                      @Override
                      public void onError(Throwable e) {

                      }

                      @Override
                      public void onNext(ParseReplayResult result) {
                          if (!LiveCloudLocal.getPlayerVersion().equals(result.getPlayerInfo().getVersion())) {
                              fetchPlayer(result.getPlayerInfo(), replayMetas, result.getReplayMetaItems(), result.isSign());
                          } else {
                              fetchReplay(result.getReplayMetaItems(), replayMetas);
                          }
                      }
                  });
    }

    /**
     * 更新播放器完成，并下载回放
     */
    private void fetchPlayer(PlayerInfo playerInfo, ReplayMetas replayMetas, List<ReplayMetaItem> replayMetaItems, boolean sign) {
        mReplayListener.onPlayerReady(replayMetas, playerInfo.getUrls().size());
        List<DownloadTask> downloadTasks = new ArrayList<>(playerInfo.getUrls().size());
        for (String url : playerInfo.getUrls()) {
            downloadTasks.add(downloadTaskBuild(
                    LiveCloudLocal.getPlayerFilesUrl(replayMetas.getPlayerBaseUri(), url),
                    new File(LiveCloudLocal.getPlayerDirectory() + "/" + url)
            ));
        }
        AtomicInteger index = new AtomicInteger();
        Observable.interval(200, TimeUnit.MILLISECONDS)
                  .flatMap((Func1<Object, Observable<Boolean>>) o -> Observable.create((Action1<Emitter<Boolean>>) emitter -> emitter.onNext(true), Emitter.BackpressureMode.BUFFER))
                  .takeWhile(isCancel -> index.get() < playerInfo.getUrls().size())
                  .compose(RxUtils.switch2Main())
                  .subscribe(Void -> {
                      DownloadTask      downloadTask = downloadTasks.get(index.get());
                      StatusUtil.Status taskStatus   = StatusUtil.getStatus(downloadTask);
                      if (taskStatus == StatusUtil.Status.UNKNOWN || taskStatus == StatusUtil.Status.IDLE) {
                          DownloadPlayerListener downloadPlayerListener = new DownloadPlayerListener(replayMetas, index.get() + 1, mReplayListener);
                          downloadTask.enqueue(downloadPlayerListener);
                      } else if (taskStatus == StatusUtil.Status.COMPLETED) {
                          index.getAndIncrement();
                          if (index.get() == playerInfo.getUrls().size() && getFileCounts(LiveCloudLocal.getPlayerDirectory()) == playerInfo.getUrls().size()) {
                              mReplayListener.onPlayerFinish(replayMetas);
                              LiveCloudLocal.setPlayerVersion(playerInfo.getVersion());
                              if (sign) fetchReplay(replayMetaItems, replayMetas);
                          }
                      }
                  });
    }

    private void fetchReplay(List<ReplayMetaItem> replayMetaItems, @NotNull ReplayMetas replayMetas) {
        List<DownloadTask> downloadTasks = new ArrayList<>();
        for (ReplayMetaItem replayMetaItem : replayMetaItems) {
            for (String url : replayMetaItem.getUrls()) {
                if (TextUtils.isEmpty(url)) continue;
                String replayBaseUrl = getBaseReplayUri(replayMetaItem.getType(), replayMetas);
                downloadTasks.add(downloadTaskBuild(replayBaseUrl + "/" + url,
                        new File(LiveCloudLocal.getReplayDirectory(replayMetas.getRoomId() + "") + "/" + url)));
            }
        }
        mReplayListener.onReady(replayMetas, downloadTasks.size());
        int           taskSum = downloadTasks.size();
        AtomicInteger index   = new AtomicInteger();
        LiveCloudLocal.setReplayStatus(mKey, ReplayInfo.Status.DOWNLOADING.ordinal());
        Observable.interval(200, TimeUnit.MILLISECONDS)
                  .flatMap((Func1<Object, Observable<Boolean>>) o -> Observable.create((Action1<Emitter<Boolean>>) emitter -> emitter.onNext(true), Emitter.BackpressureMode.BUFFER))
                  .takeWhile(isCancel -> {
                      int status = LiveCloudLocal.getReplayStatus(mKey);
                      return index.get() < taskSum && status == ReplayInfo.Status.DOWNLOADING.ordinal();
                  })
                  .compose(RxUtils.switch2Main())
                  .subscribe(Void -> {
                      DownloadTask      downloadTask = downloadTasks.get(index.get());
                      StatusUtil.Status taskStatus   = StatusUtil.getStatus(downloadTask);
                      if (taskStatus == StatusUtil.Status.UNKNOWN || taskStatus == StatusUtil.Status.IDLE) {
                          DownloadReplayListener downloadReplayListener = new DownloadReplayListener(replayMetas, index.get() + 1, mReplayListener);
                          downloadTask.enqueue(downloadReplayListener);
                      } else if (taskStatus == StatusUtil.Status.COMPLETED) {
                          index.getAndIncrement();
                          try {
                              if (index.get() == taskSum && getFileCounts(LiveCloudLocal.getReplayDirectory(replayMetas.getRoomId() + "")) == taskSum) {
                                  mReplayListener.onFinish(replayMetas);
                                  LiveCloudLocal.setReplayStatus(mKey, ReplayInfo.Status.COMPLETED.ordinal());
                                  LiveCloudLocal.setMetasUrl(mKey, getMetasUrl(replayMetaItems));
                              }
                          } catch (Exception ex) {
                              LogUtils.e(ex.getMessage());
                          }
                      }
                  });
    }

    private String getMetasUrl(List<ReplayMetaItem> replayMetaItems) {
        String metasUrl = "";
        for (ReplayMetaItem replayMetaItem : replayMetaItems) {
            if ("metas".equals(replayMetaItem.getType()) && replayMetaItem.getUrls().size() > 0) {
                metasUrl = replayMetaItem.getUrls().get(0);
                break;
            }
        }
        return metasUrl;
    }

    private long getFileCounts(File dir) {
        long   count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    count++;
                } else if (f.isDirectory()) {
                    count = count + getFileCounts(f);
                }
            }
        }
        return count;
    }

    private String getBaseReplayUri(String type, ReplayMetas replayMetas) {
        switch (type) {
            case "video":
                return replayMetas.getVideoBaseUri();
            case "document":
                return replayMetas.getDocumentBaseUri();
            default:
                return replayMetas.getDataBaseUri();
        }
    }

    private DownloadTask downloadTaskBuild(String url, File file) {
        return new DownloadTask.Builder(url, file)
                .setMinIntervalMillisCallbackProcess(100)
                .setConnectionCount(1)
                .setBreakpointEnabled(false)
                .build();
    }

    public static class Builder {
        private String         key;
        private String         downloadUrl;
        private String         token;
        private String         userId;
        private String         username;
        private ReplayListener replayListener;

        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        public Builder setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }

        public Builder setToken(String token) {
            this.token = token;
            return this;
        }

        public Builder setUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setReplayListener(ReplayListener replayListener) {
            this.replayListener = replayListener;
            return this;
        }

        public LiveCloudSDK build() {
            if (replayListener == null) {
                throw new RuntimeException("ReplayListener is null");
            }
            return new LiveCloudSDK(this);
        }
    }
}