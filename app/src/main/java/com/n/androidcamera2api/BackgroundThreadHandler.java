package com.n.androidcamera2api;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.PrecomputedText;
import android.widget.TextView;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

public class BackgroundThreadHandler {
    final private static String[] names = {"OpenCameraHandler", "CaptureSessionHandler", "CaptureRequestHandler"};
    protected Executor bgTextExecutor = null;
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
        this.bgTextExecutor = new SerialExecutor();
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
        this.bgTextExecutor = null;
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
    public void asyncSetText(TextView textView, final String longString) {
        final PrecomputedText.Params params = textView.getTextMetricsParams();
        final Reference<TextView> textViewRef = new WeakReference<>(textView);
        bgTextExecutor.execute(() -> {
            TextView tTextView = textViewRef.get();
            if (tTextView == null) return;
            final PrecomputedText precomputedText = PrecomputedText.create(longString, params);
            tTextView.post(() -> {
                TextView postTextView = textViewRef.get();
                if (postTextView == null) return;
                postTextView.setText(precomputedText);
            });
        });
    }
//    private void start() {
//        BackgroundThreadHandler mOpenCameraBgHandler, mCaptureSessionBgHandler, mCaptureRequestBgHandler;
//    }
}
