package com.n.androidcamera2api;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class BasicTextureListener implements TextureView.SurfaceTextureListener {
    protected final String LOGGER_TAG;
    protected AndroidCameraApi mainApi;
    protected int width, height, halfW, halfH, quarterW, quarterH;
    protected final Paint clearPaint, whiteFivePaint, redFivePaint, greenFivePaint, blueFivePaint;
    protected final Reference<TextureView> textureViewRef;
    protected SurfaceTexture surfaceTexture = null;
    protected Surface surface = null;

    public BasicTextureListener(AndroidCameraApi mainCameraApiClass, String loggerName, int viewId) {
        super();
        this.mainApi = mainCameraApiClass;
        this.LOGGER_TAG = loggerName;
        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        whiteFivePaint = new Paint();
        whiteFivePaint.setColor(Color.WHITE);
        whiteFivePaint.setStrokeWidth(5);
        whiteFivePaint.setStyle(Paint.Style.STROKE);
        whiteFivePaint.setTextSize(72f);
        redFivePaint = new Paint();
        redFivePaint.setColor(Color.RED);
        redFivePaint.setStrokeWidth(5);
        redFivePaint.setStyle(Paint.Style.STROKE);
        redFivePaint.setTextSize(72f);
        greenFivePaint = new Paint();
        greenFivePaint.setColor(Color.GREEN);
        greenFivePaint.setStrokeWidth(5);
        greenFivePaint.setStyle(Paint.Style.STROKE);
        greenFivePaint.setTextSize(72f);
        blueFivePaint = new Paint();
        blueFivePaint.setColor(Color.BLUE);
        blueFivePaint.setStrokeWidth(5);
        blueFivePaint.setStyle(Paint.Style.STROKE);
        blueFivePaint.setTextSize(72f);
        textureViewRef = new WeakReference<>(mainApi.findViewById(viewId));
        textureViewRef.get().setSurfaceTextureListener(this);
        Log.e(LOGGER_TAG, "Constructor called BasicTextureListener()");
    }

    public void initializeSurface(int imageWidth, int imageHeight) {
        Log.e(LOGGER_TAG, "initializeSurface(" + imageWidth + ", " + imageHeight + ")   <== initialize surface");
        if (this.surfaceTexture == null) {
            this.surface = null;
            this.surfaceTexture = textureViewRef.get().getSurfaceTexture();
        }
        if (imageWidth > 0 && imageHeight > 0) {
            assert this.surfaceTexture != null;
            this.surfaceTexture.setDefaultBufferSize(imageWidth, imageHeight);
        }
        if (this.surface == null) {
            this.surface = new Surface(this.surfaceTexture);
        }
    }

    public void reassignListener() {
        Log.e(LOGGER_TAG, "reassignListener()");
        textureViewRef.get().setSurfaceTextureListener(this);
    }

    public Surface getCameraTextureSurface() {
        if (this.surface == null) {
            this.initializeSurface(0, 0);
        }
        return this.surface;
    }

    public boolean isTextureViewAvailable() {
        return textureViewRef.get().isAvailable();
    }

    public boolean isInitialized() {
        return this.surfaceTexture != null;
    }
    protected void setCanvasSizes(int width, int height) {
        Log.e(LOGGER_TAG, "setCanvasSizes(" + width + ", " + height + ")   <== canvas resized");
        this.width = width;
        this.halfW = width/2;
        this.quarterW = width/4;
        this.height = height;
        this.halfH = height/2;
        this.quarterH = width/4;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(LOGGER_TAG, "onSurfaceTextureAvailable(" + surface.toString() + ", " + width + ", " + height + ")   <== texture available");
        this.setCanvasSizes(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(LOGGER_TAG, "onSurfaceTextureSizeChanged()");
        this.setCanvasSizes(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(LOGGER_TAG, "onSurfaceTextureDestroyed()");
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.e(LOGGER_TAG, "onSurfaceTextureUpdated()");
    }
}
