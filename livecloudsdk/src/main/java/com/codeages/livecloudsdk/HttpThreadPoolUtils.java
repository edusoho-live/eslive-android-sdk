package com.codeages.livecloudsdk;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpThreadPoolUtils {

    private HttpThreadPoolUtils() {
    }

    //核心线程数 当前设备可用处理器核心数*2 + 1,能够让cpu的效率得到最大程度执行
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2 + 1;
    //最大线程数
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE;
    //线程池中超过corePoolSize数目的空闲线程最大存活时间；可以allowCoreThreadTimeOut(true)使得核心线程有效时间
    private static final int KEEP_ALIVE_TIME = 5;
    //任务队列
    private static final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(MAX_POOL_SIZE);

    private static final ThreadPoolExecutor mThreadpool;

    static {
        mThreadpool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, workQueue);
    }

    public static void execute(Runnable runnable) {
        mThreadpool.execute(runnable);
    }

    public static void cancelAll(){
        try {
            mThreadpool.shutdown();
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
