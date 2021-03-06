package com.codeages.eslivesdk;

import com.blankj.utilcode.util.LogUtils;
import com.codeages.eslivesdk.bean.ReplayMetas;
import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.OkDownload;
import com.liulishuo.okdownload.SpeedCalculator;
import com.liulishuo.okdownload.core.breakpoint.BlockInfo;
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.listener.DownloadListener4WithSpeed;
import com.liulishuo.okdownload.core.listener.assist.Listener4SpeedAssistExtend;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.codeages.eslivesdk.LiveCloudSDK.FetchFileError;

public class DownloadReplayListener extends DownloadListener4WithSpeed {
    private final ReplayListener mReplayListener;
    private final ReplayMetas    mReplayMetas;
    private final int            mIndex;
    private       int            mDownloadCount = 0;
    private       String         mUserId;
    private       String         mUserName;

    public DownloadReplayListener(ReplayMetas replayMetas, int index, String userId, String userName, ReplayListener replayListener) {
        this.mReplayMetas = replayMetas;
        this.mIndex = index;
        this.mUserId = userId;
        this.mUserName = userName;
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
        LogUtils.i(String.format("range: %s - %s", info.getBlock(0).getRangeLeft() + "", info.getBlock(0).getRangeRight() + ""));
    }

    @Override
    public void progressBlock(@NotNull DownloadTask task, int blockIndex, long currentBlockOffset,
                              @NotNull SpeedCalculator blockSpeed) {
        LogUtils.i(task.getUrl() + "|blockIndex:" + blockIndex);
    }

    @Override
    public void progress(@NotNull DownloadTask task, long currentOffset,
                         @NotNull SpeedCalculator taskSpeed) {
        mReplayListener.onProgress(mReplayMetas, mIndex, taskSpeed.averageSpeed());
        LogUtils.i(task.getUrl() + "|taskSpeed:" + taskSpeed.averageSpeed());
    }

    @Override
    public void blockEnd(@NotNull DownloadTask task, int blockIndex, BlockInfo info,
                         @NotNull SpeedCalculator blockSpeed) {
        LogUtils.i(String.format("range: %s - %s", info.getRangeLeft(), info.getRangeRight()));
    }

    @Override
    public void taskEnd(@NotNull DownloadTask task, @NotNull EndCause cause, Exception realCause,
                        @NotNull SpeedCalculator taskSpeed) {
        if (cause == EndCause.ERROR) {
            mDownloadCount++;
            OkDownload.with().breakpointStore().remove(task.getId());
            if (mDownloadCount == 10) {
                LiveCloudLogger.getInstance(Long.parseLong(mReplayMetas.getRoomId() + ""), Long.parseLong(mUserId), mUserName, null).error(FetchFileError, task.getUrl(), null);
            }
        }
        LogUtils.i(task.getUrl() + "EndCause:" + cause.name());
    }
}
