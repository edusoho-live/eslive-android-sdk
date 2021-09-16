package com.codeages.livecloudsdk.cache;

import java.util.concurrent.atomic.AtomicInteger;

public class CacheServerFactory {

    private static CacheServerFactory INSTANCE;
    private final  AtomicInteger      mRefCount = new AtomicInteger(0);
    private        CacheServer        mCacheServer;

    private CacheServerFactory() {
    }

    public static CacheServerFactory getInstance() {
        if (INSTANCE == null) {
            synchronized (CacheServerFactory.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CacheServerFactory();
                }
            }
        }
        return INSTANCE;
    }

    public void start(int roomId) {
        mRefCount.incrementAndGet();
        mCacheServer = createServer(roomId);
        mCacheServer.start();
    }

    public void stop() {
        mRefCount.decrementAndGet();
        if (mRefCount.get() <= 0) {
            if (mCacheServer != null) {
                mCacheServer.close();
            }
            mRefCount.set(0);
        }
    }

    public void resume() {
        if (mCacheServer != null) {
            mCacheServer.keepOn();
        }
    }

    public void pause() {
        if (mCacheServer != null) {
            mCacheServer.pause();
        }
    }

    private CacheServer createServer(int roomId) {
        return new CacheServer.Builder()
                .setFilter("*")
                .setHandler(new FileHandler(roomId))
                .build();
    }
}
