package com.codeages.eslivesdk;

import com.codeages.eslivesdk.bean.ReplayError;
import com.codeages.eslivesdk.bean.ReplayMetas;

/**
 * 需要更新Player，onStart -> onPlayerReady -> onPlayerProgress -> onPlayerFinish -> onReady -> onProgress -> onFinish
 * 不需要更新player, onStart -> onReady -> onProgress -> onFinish
 */
public interface ReplayListener {
    // 获取到回放信息
    default void onStart(ReplayMetas metas) {

    }

    // 已经获取Player版本，需要更新Player版本
    default void onPlayerReady(ReplayMetas metas, long totalLength) {

    }

    // 更新Player版本，下载中
    default void onPlayerProgress(ReplayMetas metas, long currentOffset, String speed) {

    }

    // Player版本更新成功
    default void onPlayerFinish(ReplayMetas metas) {

    }

    // 开始下载缓存
    default void onReady(ReplayMetas metas, long totalLength) {

    }

    // 缓存下载中
    default void onProgress(ReplayMetas metas, long currentOffset, String speed) {

    }

    // 缓存下载完成
    default void onFinish(ReplayMetas metas) {

    }

    // 下载出错
    default void onError(ReplayError error) {

    }
}
