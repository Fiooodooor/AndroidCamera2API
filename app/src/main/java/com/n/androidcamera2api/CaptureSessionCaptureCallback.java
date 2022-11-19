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
    protected float lensDistanceMeters = -1.0f;
    protected float lensDistanceDiopters = 0.0f;

    public CaptureSessionCaptureCallback(AndroidCameraApi mainCameraApiClass) {
        super();
        this.mainApi = mainCameraApiClass;
    }
    private void process(@NonNull CaptureRequest request,
                         @NonNull CaptureResult result,
                         boolean partial) {
        int afStateCurrent = Objects.requireNonNull(result.get(CaptureResult.CONTROL_AF_STATE));
        mainApi.areWeFocused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afStateCurrent;
        if (result.get(CaptureResult.LENS_FOCUS_DISTANCE) != null) {
            MeteringRectangle[] controlAfRegions;
            lensDistanceDiopters = Objects.requireNonNull(result.get(CaptureResult.LENS_FOCUS_DISTANCE));
            lensDistanceMeters = 100.0f / lensDistanceDiopters;
            mainApi.handlerManager.asyncSetText(mainApi.distanceTextView, String.format(Locale.getDefault(), "%.2f cm", lensDistanceMeters));
//            mainApi.distanceTextView.setText(String.format(Locale.ENGLISH, "%.2f cm", lensDistanceMeters *100.0f));
            if (request.get(CaptureRequest.CONTROL_AF_REGIONS) != null) {
                controlAfRegions = Objects.requireNonNull(request.get(CaptureRequest.CONTROL_AF_REGIONS));
            } else {
                controlAfRegions = new MeteringRectangle[0];
            }

            if(lensDistanceDiopters != mainApi.lensDistanceLastDiopters) {
                mainApi.lensDistanceLastDiopters = lensDistanceDiopters;
                Log.e(AndroidCameraApi.callbackTag,
                        "focusDiopters=" + lensDistanceDiopters +
                                ", focusMeters=" + lensDistanceMeters +
                                ", inFocus=" + mainApi.areWeFocused +
                                ", partial=" + partial +
                                ", controlAfRegions=" + controlAfRegions[0].toString());
            }
        }
//        return lensDistanceMeters;
    }

    @Override
    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
        super.onCaptureStarted(session, request, timestamp, frameNumber);
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
        if (request.getTag() == AndroidCameraApi.TOUCH_FOCUS_TAG) {
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            try {
                mainApi.cameraCaptureSessions.setRepeatingRequest(mainApi.captureRequestBuilder.build(), this, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else if (request.getTag() == AndroidCameraApi.ACTIVE_CHANGING_FOCUS_TAG) {
            mainApi.canvasTextureListener.drawRectangle(mainApi.afRegionsIndex, lensDistanceMeters);
            ++mainApi.afRegionsIndex;
            if (mainApi.afRegionsIndex >= mainApi.afRegions.length) {
                mainApi.afRegionsIndex = 0;
            }
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{mainApi.afRegions[mainApi.afRegionsIndex]});
            mainApi.captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            try {
                mainApi.cameraCaptureSessions.capture(mainApi.captureRequestBuilder.build(), this, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
        super.onCaptureFailed(session, request, failure);
        Log.e(AndroidCameraApi.callbackTag, "Capture AF failure: " + failure);
        mainApi.mManualFocusEngaged = false;
    }

    @Override
    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
        super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
    }

    @Override
    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
        super.onCaptureSequenceAborted(session, sequenceId);
    }



    protected float computeFocusScore() {
        int[] gradientHorizontal, gradientVertical, luminanceHistogram;
        int maxGradient, row, col, rowsNumber, colsNumber;

        return 0.0f;
    }
}
