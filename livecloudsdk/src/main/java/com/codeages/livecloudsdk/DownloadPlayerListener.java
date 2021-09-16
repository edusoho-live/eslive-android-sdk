package com.codeages.livecloudsdk;

import com.blankj.utilcode.util.LogUtils;
import com.codeages.livecloudsdk.bean.ReplayMetas;
import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.SpeedCalculator;
import com.liulishuo.okdownload.core.breakpoint.BlockInfo;
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.listener.DownloadListener4WithSpeed;
import com.liulishuo.okdownload.core.listener.assist.Listener4SpeedAssistExtend;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class DownloadPlayerListener extends DownloadListener4WithSpeed {
    private final ReplayListener mReplayListener;
    private final ReplayMetas    mReplayMetas;
    private final int            mIndex;

    public DownloadPlayerListener(ReplayMetas replayMetas, int index, ReplayListener replayListener) {
        this.mReplayMetas = replayMetas;
        this.mIndex = index;
        this.mReplayListener = replayListener;
    }

    @Override
    public void taskStart(@NotNull DownloadTask task) {

    }

    @Override
    public void connectStart(@NotNull DownloadTask task, int blockIndex, @NotNull Map<String,
            List<String>> requestHeaderFields) {

    }

    @Override
    public void connectEnd(@NotNull DownloadTask task, int blockIndex, int responseCode,
                           @NotNull Map<String, List<String>> responseHeaderFields) {

    }

    @Override
    public void infoReady(@NotNull DownloadTask task, @NotNull BreakpointInfo info,
                          boolean fromBreakpoint,
                          @NotNull Listener4SpeedAssistExtend.Listener4SpeedModel model) {
        LogUtils.i(task.getUrl());
    }

    @Override
    public void progressBlock(@NotNull DownloadTask task, int blockIndex, long currentBlockOffset,
                              @NotNull SpeedCalculator blockSpeed) {
        LogUtils.i(task.getUrl() + "|blockIndex:" + blockIndex);
    }

    @Override
    public void progress(@NotNull DownloadTask task, long currentOffset,
                         @NotNull SpeedCalculator taskSpeed) {
        mReplayListener.onPlayerProgress(mReplayMetas, mIndex, taskSpeed.averageSpeed());
        LogUtils.i(task.getUrl() + "|taskSpeed:" + taskSpeed.averageSpeed());
    }

    @Override
    public void blockEnd(@NotNull DownloadTask task, int blockIndex, BlockInfo info,
                         @NotNull SpeedCalculator blockSpeed) {
        LogUtils.i(task.getUrl());
    }

    @Override
    public void taskEnd(@NotNull DownloadTask task, @NotNull EndCause cause, Exception realCause,
                        @NotNull SpeedCalculator taskSpeed) {
        LogUtils.i(task.getUrl() + "|EndCause:" + cause.name());

    }
}
