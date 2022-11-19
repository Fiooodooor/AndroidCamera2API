package com.n.androidcamera2api;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Objects;

public class ViewOnTouchListener implements View.OnTouchListener {
    AndroidCameraApi mainApi;

    public ViewOnTouchListener(AndroidCameraApi mainCameraApiClass) {
        super();
        this.mainApi = mainCameraApiClass;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        final int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (mainApi.mManualFocusEngaged) {
            Log.d(AndroidCameraApi.TAG, "Manual focus already engaged");
            return true;
        }
        view.performClick();
        MeteringRectangle focusAreaTouch = convertTouchToTexture(view, motionEvent, true);
        try {
            mainApi.cameraCaptureSessions.stopRepeating();
            //cancel any existing AF trigger (repeated touches, etc.)
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            mainApi.cameraCaptureSessions.capture(mainApi.captureRequestBuilder.build(), mainApi.captureCallbackListener, mainApi.handlerManager.mRequestHandler());

            //Now add a new AF trigger with focus region
            if (isMeteringAreaAFSupported()) {
                mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, AndroidCameraApi.afManualModes[AndroidCameraApi.afManualModesCurrentIndex]);
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mainApi.captureRequestBuilder.setTag(AndroidCameraApi.TOUCH_FOCUS_TAG);

            //then we ask for a single request (not repeating!)
            mainApi.cameraCaptureSessions.capture(mainApi.captureRequestBuilder.build(), mainApi.captureCallbackListener, mainApi.handlerManager.mRequestHandler());
            mainApi.mManualFocusEngaged = true;
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean isMeteringAreaAFSupported() throws NullPointerException {
        return Objects.requireNonNull(mainApi.characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)) >= 1;
    }

    protected MeteringRectangle convertTouchToTexture(View view, MotionEvent motionEvent, boolean fixedSize) {
        Log.e("TOUCH", "X=" + motionEvent.getX() + ", Y=" + motionEvent.getY() + ", Major=" + motionEvent.getTouchMajor() + ", Minor=" + motionEvent.getTouchMinor());

        final int halfTouchWidth, halfTouchHeight, xSensor, ySensor;
        final float xNormalized, yNormalized;

        if (fixedSize) {
            halfTouchWidth = 9;
            halfTouchHeight = 9;
        } else {
            halfTouchWidth  = (int)motionEvent.getTouchMajor();
            halfTouchHeight = (int)motionEvent.getTouchMinor();
        }

        xNormalized = motionEvent.getX() / (float)view.getWidth();
        yNormalized = motionEvent.getY() / (float)view.getHeight();

        if (mainApi.sensorOrientation == 0) {
            xSensor = (int) (xNormalized * (float) mainApi.activeArraySize.width());
            ySensor = (int) (yNormalized * (float) mainApi.activeArraySize.height());
        } else if (mainApi.sensorOrientation == 90) {
            xSensor = (int)(yNormalized * (float)mainApi.activeArraySize.width());
            ySensor = (int)((1.0f-xNormalized) * (float)mainApi.activeArraySize.height());
        } else if (mainApi.sensorOrientation == 180) {
            xSensor = (int)((1.0f-xNormalized) * (float)mainApi.activeArraySize.width());
            ySensor = (int)((1.0f-yNormalized) * (float)mainApi.activeArraySize.height());
        } else {
            xSensor = (int)((1.0f-yNormalized) * (float)mainApi.activeArraySize.width());
            ySensor = (int)(xNormalized * (float)mainApi.activeArraySize.height());
        }

        Log.e("TRANSLATED", "X=" + xSensor + ", Y=" + ySensor + ", halfTouchWidth=" + halfTouchWidth + ", halfTouchHeight=" + halfTouchHeight);
        return new MeteringRectangle(Math.max(xSensor - halfTouchWidth,  0),
                Math.max(ySensor - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }
}
