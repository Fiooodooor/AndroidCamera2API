package com.n.androidcamera2api;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

public class CaptureSessionCaptureCallback extends CameraCaptureSession.CaptureCallback{
    AndroidCameraApi mainApi;

    public CaptureSessionCaptureCallback(AndroidCameraApi mainCameraApiClass) {
        super();
        this.mainApi = mainCameraApiClass;
    }
    private void process(@NonNull CaptureRequest request,
                         @NonNull CaptureResult result,
                         boolean partial) {
        int afStateCurrent = Objects.requireNonNull(result.get(CaptureResult.CONTROL_AF_STATE));
        if (CaptureResult.CONTROL_AF_TRIGGER_START == afStateCurrent) {
            if (mainApi.areWeFocused) {
                Log.d(mainApi.callbackTag, "captureCallbackListener process() called with focused state");
            }
        }
        mainApi.areWeFocused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afStateCurrent;
        if (afStateCurrent != mainApi.afStateLast) {
            mainApi.afStateLast = afStateCurrent;
            Log.i(mainApi.callbackTag, "afState=" + afStateCurrent + ", inFocus=" + mainApi.areWeFocused);
        }
        if (result.get(CaptureResult.LENS_FOCUS_DISTANCE) != null) {
            MeteringRectangle[] controlAfRegions;
            float lensDistanceDiopters = Objects.requireNonNull(result.get(CaptureResult.LENS_FOCUS_DISTANCE));
            float lensDistanceMeters = 1.0f / lensDistanceDiopters;
            mainApi.distanceTextView.setText(String.format(Locale.ENGLISH, "%.2f cm", lensDistanceMeters *100.0f));
            if (request.get(CaptureRequest.CONTROL_AF_REGIONS) != null) {
                controlAfRegions = Objects.requireNonNull(request.get(CaptureRequest.CONTROL_AF_REGIONS));
            } else {
                controlAfRegions = new MeteringRectangle[0];
            }

            if(lensDistanceDiopters != mainApi.lensDistanceLastDiopters) {
                mainApi.lensDistanceLastDiopters = lensDistanceDiopters;
                Log.e(mainApi.callbackTag,
                        "focusDiopters=" + lensDistanceDiopters +
                                ", focusMeters=" + lensDistanceMeters +
                                ", inFocus=" + mainApi.areWeFocused +
                                ", partial=" + partial +
                                ", controlAfRegions=" + controlAfRegions[0].toString());
            }
        }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureResult partialResult) {
        super.onCaptureProgressed(session, request,  partialResult);
        process(request, partialResult, true);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                   @NonNull CaptureRequest request,
                                   @NonNull TotalCaptureResult result) {
        super.onCaptureCompleted(session, request, result);
        mainApi.mManualFocusEngaged = false;
        process(request, result, false);
        if (request.getTag() == mainApi.TOUCH_FOCUS_TAG) {
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            try {
                mainApi.cameraCaptureSessions.setRepeatingRequest(mainApi.captureRequestBuilder.build(), this, mainApi.handlerManager.mRequestHandler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else if (request.getTag() == mainApi.ACTIVE_CHANGING_FOCUS_TAG) {
            ++mainApi.afRegionsIndex;
            if (mainApi.afRegionsIndex >= mainApi.afRegions.length) {
                mainApi.afRegionsIndex = 0;
            }
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{mainApi.afRegions[mainApi.afRegionsIndex]});
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            try {
                mainApi.cameraCaptureSessions.capture(mainApi.captureRequestBuilder.build(), this, mainApi.handlerManager.mRequestHandler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
        super.onCaptureFailed(session, request, failure);
        Log.e(mainApi.callbackTag, "Capture AF failure: " + failure);
        mainApi.mManualFocusEngaged = false;
    }
}
