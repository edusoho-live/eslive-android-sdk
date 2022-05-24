package com.codeages.eslivesdk;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.PathUtils;
import com.blankj.utilcode.util.StringUtils;
import com.codeages.eslivesdk.bean.ReplayInfo;
import com.codeages.eslivesdk.bean.ReplayMetas;
import com.tencent.mmkv.MMKV;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import rx.Subscription;

public class LiveCloudLocal {

    public static final  int                       LOCAL_HTTP_PORT             = 20100;
    public static final  String                    LIVE_CLOUD_REPLAY_PATH      = "/live_cloud_replay";
    public static final  String                    LIVE_CLOUD_PLAYER_PATH      = "/live_cloud_player";
    public static final  String                    PLAYER_VERSION              = "player_version";
    public static final  String                    LIVE_CLOUD_REPLAY_DB        = "live_cloud_replay_db";
    public static final  String                    LIVE_CLOUD_REPLAY_METAS_URL = "live_cloud_replay_metas_url";
    private static final Map<String, Subscription> mSubCache                   = new HashMap<>();

    public static void putSub(String key, Subscription sub) {
        mSubCache.put(key, sub);
    }

    public static Subscription popSub(String key) {
        Subscription subscription = mSubCache.get(key);
        mSubCache.remove(key);
        return subscription;
    }

    public static String getPlayerVersion() {
        MMKV kv = MMKV.defaultMMKV();
        return kv.decodeString(PLAYER_VERSION, "-1");
    }

    public static void setPlayerVersion(String version) {
        MMKV kv = MMKV.defaultMMKV();
        kv.encode(PLAYER_VERSION, version);
    }

    public static void removePlayerVersion() {
        MMKV kv = MMKV.defaultMMKV();
        kv.remove(PLAYER_VERSION);
    }

    public static void setReplay(String key, ReplayMetas replayMetas) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        kv.encode(key, GsonUtils.toJson(replayMetas));
    }

    public static void removeReplay(String key) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        kv.remove(key);
    }

    public static ReplayMetas getReplay(String key) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        return GsonUtils.fromJson(kv.decodeString(key), ReplayMetas.class);
    }

    public static void setReplayStatus(String key, int status) {
        MMKV        kv          = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        ReplayMetas replayMetas = GsonUtils.fromJson(kv.decodeString(key), ReplayMetas.class);
        replayMetas.setStatus(status);
        kv.encode(key, GsonUtils.toJson(replayMetas));
    }

    public static int getReplayStatus(String key) {
        MMKV        kv          = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        ReplayMetas replayMetas = GsonUtils.fromJson(kv.decodeString(key), ReplayMetas.class);
        if (replayMetas == null) {
            return ReplayInfo.Status.NONE.ordinal();
        } else {
            return replayMetas.getStatus();
        }
    }

    public static void setMetasUrl(String key, String metasUrl) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_METAS_URL, MMKV.MULTI_PROCESS_MODE);
        kv.encode(key, metasUrl);
    }

    public static String getMetasUrl(String key) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_METAS_URL, MMKV.MULTI_PROCESS_MODE);
        return kv.decodeString(key, "");
    }

    public static void removeMetasUrl(String key) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_METAS_URL, MMKV.MULTI_PROCESS_MODE);
        kv.remove(key);
    }

    /**
     * 是否存在相同的RoomId录像
     *
     */
    public static boolean isExistReplay(int roomId, String targetKey) {
        MMKV kv = MMKV.mmkvWithID(LIVE_CLOUD_REPLAY_DB, MMKV.MULTI_PROCESS_MODE);
        for (String key : kv.allKeys()) {
            ReplayMetas tmpReplayMetas = GsonUtils.fromJson(kv.decodeString(key), ReplayMetas.class);
            if (!StringUtils.equals(targetKey, key) && tmpReplayMetas.getRoomId() == roomId) {
                return true;
            }
        }
        return false;
    }

    public static String getPlayerFilesUrl(String playerBaseUri, String playerFileName) {
        return playerBaseUri + "/" + playerFileName;
    }

    public static File getReplayDirectory(String roomId) {
        File replayDirectory = new File(getLiveCloudReplayPath() + "/" + roomId);
        if (!replayDirectory.exists()) FileUtils.createOrExistsDir(replayDirectory);
        return replayDirectory;
    }

    public static File getPlayerDirectory() {
        File playerDirectory = new File(getLiveCloudPlayerPath());
        if (!playerDirectory.exists()) FileUtils.createOrExistsDir(playerDirectory);
        return playerDirectory;
    }

    private static String getLiveCloudPlayerPath() {
        return PathUtils.getExternalAppFilesPath() + LIVE_CLOUD_PLAYER_PATH;
    }

    private static String getLiveCloudReplayPath() {
        return PathUtils.getExternalAppFilesPath() + LIVE_CLOUD_REPLAY_PATH;
    }
}
