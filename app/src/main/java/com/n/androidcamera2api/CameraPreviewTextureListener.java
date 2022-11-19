package com.n.androidcamera2api;

import android.graphics.SurfaceTexture;
import android.util.Log;

public class CameraPreviewTextureListener extends BasicTextureListener {

    public CameraPreviewTextureListener(AndroidCameraApi mainCameraApiClass) {
        super(mainCameraApiClass, "CameraPreviewTextureListener", R.id.previewTexture);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureAvailable(surface, width, height);
        mainApi.openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureSizeChanged(surface, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return super.onSurfaceTextureDestroyed(surface);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        super.onSurfaceTextureUpdated(surface);
    }
}
