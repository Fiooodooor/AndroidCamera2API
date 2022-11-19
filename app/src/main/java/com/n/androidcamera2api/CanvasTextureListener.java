package com.n.androidcamera2api;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;

import java.util.Locale;

public class CanvasTextureListener extends BasicTextureListener {
    protected MeteringRectangle[] screenRect;
    protected Canvas drawCanvas;

    public CanvasTextureListener(AndroidCameraApi mainCameraApiClass) {
        super(mainCameraApiClass, "CanvasTextureListener", R.id.canvasTexture);
        textureViewRef.get().setOpaque(false);
        screenRect = new MeteringRectangle[4];
    }

    public void drawRectangle(int sectionNumber, float distanceCentiMeters) {
        int it = Math.max(sectionNumber, 0);
        if(isInitialized()) {
            Log.e(LOGGER_TAG, "drawRectangle()");
            drawCanvas = this.surface.lockHardwareCanvas();
            if(sectionNumber >= screenRect.length) {
                it = 0;
                drawCanvas.drawRect(0, 0, 0, 0, clearPaint);
            }
            drawCanvas.drawRect(screenRect[it].getRect(), this.redFivePaint);
            drawCanvas.drawText(String.format(Locale.getDefault(), "%.2f cm", distanceCentiMeters),
                    screenRect[it].getRect().left,
                    screenRect[it].getRect().exactCenterY(),
                    blueFivePaint);
            this.surface.unlockCanvasAndPost(drawCanvas);
            textureViewRef.get().invalidate();
            return;
        }
        Log.e(LOGGER_TAG, "drawRectangle() - not yet initialized");
    }

    @Override
    protected void setCanvasSizes(int width, int height) {
        super.setCanvasSizes(width, height);
        screenRect[0] = new MeteringRectangle(0, 0, halfW, halfH, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        screenRect[1] = new MeteringRectangle(halfW, 0, halfW, halfH, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        screenRect[2] = new MeteringRectangle(0, halfH, halfW, halfH, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        screenRect[3] = new MeteringRectangle(halfW, halfH, halfW, halfH, MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        super.onSurfaceTextureAvailable(surface, width, height);
        this.initializeSurface(width, height);
        this.drawRectangle(0, 0);
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
