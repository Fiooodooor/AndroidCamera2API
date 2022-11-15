package com.n.androidcamera2api;

import android.os.Handler;
import android.os.HandlerThread;

public class BackgroundThreadHandler {
    final private static String[] names = {"OpenCameraHandler", "CaptureSessionHandler", "CaptureRequestHandler"};
    protected Handler[] handlers = null;
    protected HandlerThread[] threads = null;

    public BackgroundThreadHandler() {
        start();
    }

    public void start() {
        if(this.threads == null) {
            this.threads = new HandlerThread[names.length];
            this.handlers = new Handler[names.length];
        }
        for(int it = 0; it < names.length; ++it) {
            this.threads[it] = new HandlerThread(names[it]);
            this.threads[it].start();
            this.handlers[it] = new Handler(threads[it].getLooper());
        }
    }
    public void stop() {
        assert this.threads != null;
        for (HandlerThread ht: this.threads) {
            assert ht != null;
            try {
                ht.quitSafely();
                ht.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assert this.handlers != null;
        for(int it = 0; it < names.length; ++it) {
            threads[it] = null;
            handlers[it] = null;
        }
        this.threads = null;
        this.handlers = null;
    }

    public Handler mCameraHandler() {
        return this.getHandler(0);
    }

    public Handler mSessionHandler() {
        return this.getHandler(1);
    }

    public Handler mRequestHandler() {
        return this.getHandler(2);
    }

    private Handler getHandler(int n) {
        assert this.handlers != null;
        if(n < names.length) {
            return handlers[n];
        } else {
            return null;
        }
    }
//    private void start() {
//        BackgroundThreadHandler mOpenCameraBgHandler, mCaptureSessionBgHandler, mCaptureRequestBgHandler;
//    }
}
