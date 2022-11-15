package com.n.androidcamera2api;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public class AndroidCameraApi extends AppCompatActivity {
    private static final String TAG = "AndroidCameraApi";
    private static final String callbackTag = "CameraState";
    private static final String TOUCH_FOCUS_TAG = "TOUCH_FOCUS_TAG";
    private static final String MANUAL_FOCUS_TAG = "MANUAL_FOCUS_TAG";
    private static final String ACTIVE_FOCUS_TAG = "ACTIVE_FOCUS_TAG";
    private boolean afEnabled = false;
    private TextureView textureView;
    private static final String[] afManualModesNames = {"AF_AUTO", "AF_MACRO", "AF_EDOF", "AF_OFF"};
    private static final int[] afManualModes = {CameraMetadata.CONTROL_AF_MODE_AUTO, CameraMetadata.CONTROL_AF_MODE_MACRO, CameraMetadata.CONTROL_AF_MODE_EDOF, CameraMetadata.CONTROL_AF_MODE_OFF};
    private static int afManualModesCurrentIndex = 0;
    protected CameraManager manager;
    protected String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder = null;
    protected CameraCharacteristics characteristics = null;
    protected SurfaceTexture texture = null;
    protected Surface surface = null;
    private MeteringRectangle[] controlAfRegion = null;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private BackgroundThreadHandler handlerManager;
    TextView distanceTextView;
    private boolean areWeFocused = false;
    private boolean mManualFocusEngaged = false;
    private int afStateLast = 0;
    Rect activeArraySize;
    Rect preActiveArraySize;
    private float lensDistanceLastDiopters = 0.0f;
    private float deltaFocus;
    private float currentFocus;
    private float currentFocusMeters = 0.0f;
    private float minimumFocusMeters = 0.0f;
    private float maximumFocusMeters = 0.0f;

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable()");
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged()");
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.StateCallback stateCallbackListener =
            new CameraCaptureSession.StateCallback(){
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "onConfigured()");
            //The camera is already closed
            if (null == cameraDevice) {
                return;
            }
            // When the session is ready, we start displaying the preview.
            cameraCaptureSessions = cameraCaptureSession;
            updatePreview();
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "onConfigureFailed()");
            Toast.makeText(AndroidCameraApi.this, "Configuration change failed", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener
            = new CameraCaptureSession.CaptureCallback() {
        private void process(@NonNull CaptureRequest request,
                             @NonNull CaptureResult result,
                             boolean partial) {
            int afStateCurrent = Objects.requireNonNull(result.get(CaptureResult.CONTROL_AF_STATE));
            if (CaptureResult.CONTROL_AF_TRIGGER_START == afStateCurrent) {
                if (areWeFocused) {
                    Log.d(callbackTag, "captureCallbackListener process() called with focused state");
                }
            }
            areWeFocused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afStateCurrent;
            if (afStateCurrent != afStateLast) {
                afStateLast = afStateCurrent;
                Log.i(callbackTag, "afState=" + afStateCurrent + ", inFocus=" + areWeFocused);
            }
            if (result.get(CaptureResult.LENS_FOCUS_DISTANCE) != null) {
                MeteringRectangle[] controlAfRegions;
                float lensDistanceDiopters = Objects.requireNonNull(result.get(CaptureResult.LENS_FOCUS_DISTANCE));
                float lensDistanceMeters = 1.0f / lensDistanceDiopters;
                distanceTextView.setText(String.format(Locale.ENGLISH, "%.2f", lensDistanceMeters *100.0f));
                if (request.get(CaptureRequest.CONTROL_AF_REGIONS) != null) {
                    controlAfRegions = Objects.requireNonNull(request.get(CaptureRequest.CONTROL_AF_REGIONS));
                } else {
                    controlAfRegions = new MeteringRectangle[0];
                }

                if(lensDistanceDiopters != lensDistanceLastDiopters) {
                    lensDistanceLastDiopters = lensDistanceDiopters;
                    Log.e(callbackTag,
                          "focusDiopters=" + lensDistanceDiopters +
                                ", focusMeters=" + lensDistanceMeters +
                                ", inFocus=" + areWeFocused +
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
            mManualFocusEngaged = false;
            process(request, result, false);
            if (request.getTag() == TOUCH_FOCUS_TAG) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                try {
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackListener, handlerManager.mRequestHandler());
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(callbackTag, "Capture AF failure: " + failure);
            mManualFocusEngaged = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_camera_api);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        View textureTouchView = (View) findViewById(R.id.textureTouchView);
        Button deltaPlusButton = (Button) findViewById(R.id.delta_plus);
        Button deltaMinusButton = (Button) findViewById(R.id.delta_minus);
        Button afOnButton = (Button) findViewById(R.id.af_on);
        final Button afTriggerButton = (Button) findViewById(R.id.af_mode);
        distanceTextView = (TextView) findViewById(R.id.distanceView);
        currentFocus = 1.0f;
        currentFocusMeters = 1.0f;
        assert deltaPlusButton != null;
        assert deltaMinusButton != null;
        assert afOnButton != null;
        assert distanceTextView != null;
        assert afTriggerButton != null;
        assert textureTouchView != null;

        textureTouchView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                final int actionMasked = motionEvent.getActionMasked();
                if (actionMasked != MotionEvent.ACTION_DOWN) {
                    return false;
                }
                if (mManualFocusEngaged) {
                    Log.d(TAG, "Manual focus already engaged");
                    return true;
                }
                view.performClick();
                MeteringRectangle focusAreaTouch = convertTouchToTexture(view, motionEvent);
                try {
                    cameraCaptureSessions.stopRepeating();
                    //cancel any existing AF trigger (repeated touches, etc.)
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener, handlerManager.mRequestHandler());

                    //Now add a new AF trigger with focus region
                    if (isMeteringAreaAFSupported()) {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
                    }
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afManualModes[afManualModesCurrentIndex]);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                    captureRequestBuilder.setTag(TOUCH_FOCUS_TAG);

                    //then we ask for a single request (not repeating!)
                    cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener, handlerManager.mRequestHandler());
                    mManualFocusEngaged = true;
                } catch (CameraAccessException | NullPointerException e) {
                    e.printStackTrace();
                }
                return true;
            }
            private boolean isMeteringAreaAFSupported() throws NullPointerException {
                return Objects.requireNonNull(characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)) >= 1;
            }
        });
        afTriggerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(++afManualModesCurrentIndex >= afManualModes.length) {
                    afManualModesCurrentIndex = 0;
                }
                afTriggerButton.setText(afManualModesNames[afManualModesCurrentIndex]);
            }
        });
        afTriggerButton.setText(afManualModesNames[afManualModesCurrentIndex]);
        deltaMinusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFocusMeters -= deltaFocus;
                if(currentFocusMeters < minimumFocusMeters) {
                    currentFocusMeters = minimumFocusMeters;
                }
                currentFocus = 1.0f/currentFocusMeters;
                takePicture(false);
            }
        });
        deltaPlusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFocusMeters += deltaFocus;
                if(currentFocusMeters > maximumFocusMeters) {
                    currentFocusMeters = maximumFocusMeters;
                }
                currentFocus = 1.0f/currentFocusMeters;
                takePicture(false);
            }
        });
        afOnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture(true);
            }
        });
    }
    protected void takePicture(boolean enableAf) {
        afEnabled = enableAf;
        createCameraPreview();
    }
    protected void createCameraPreview() {
        Log.e(TAG, "createCameraPreview()");
        try {
            if(controlAfRegion == null) {
                controlAfRegion = new MeteringRectangle[1];
                int horizontal = (activeArraySize.right-activeArraySize.left)/2;
                int vertical = (activeArraySize.bottom-activeArraySize.top)/2;
                controlAfRegion[0] = new MeteringRectangle(horizontal-10, vertical-10, 20, 20, 100);
            }
            if (texture == null) {
                texture = textureView.getSurfaceTexture();
                assert texture != null;
                texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            }
            if (surface == null) {
                surface = new Surface(texture);
            }
            if (captureRequestBuilder == null) {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                captureRequestBuilder.addTarget(surface);
            }

//            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration);
            if (afEnabled) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequestBuilder.setTag(ACTIVE_FOCUS_TAG);
            } else {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus);
                captureRequestBuilder.setTag(MANUAL_FOCUS_TAG);
                Log.e(TAG, "new MAN focus " + (currentFocus));
            }
            cameraDevice.createCaptureSession(Collections.singletonList(surface), stateCallbackListener, handlerManager.mSessionHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        assert manager != null;
        Log.e(TAG, "openCamera() Build.VERSION.SDK_IN=" + android.os.Build.VERSION.SDK_INT + ", Build.VERSION_CODES.P=" + android.os.Build.VERSION_CODES.P);
        try {
            cameraId = Objects.requireNonNull(manager.getCameraIdList()[0]);
            characteristics = manager.getCameraCharacteristics(cameraId);

            try {
                readCharacteristics();
            } catch (NullPointerException e) {
                e.printStackTrace();
                closeAppSequence();
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(AndroidCameraApi.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, handlerManager.mCameraHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void readCharacteristics() throws NullPointerException {
        for(CameraCharacteristics.Key<?> it : characteristics.getKeys()) {
            Object keyValue = characteristics.get(it);
            if (keyValue == null)  {
                keyValue = -1;
            }
            Log.e("Keys", it.toString() + ", value=" + keyValue.toString() + ",  name=" + it.getName());
        }
        float minimumFocus = Objects.<Float>requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE));
        float maximumFocus = Objects.<Float>requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE));
        int distanceCalibration = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION));
        float[] focalLengths = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        int hardwareLevel = Objects.requireNonNull(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        int sensorOrientation = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
        long maxFrameDuration = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION));
        int afMaxRegions = Objects.requireNonNull(characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF));
        activeArraySize = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        preActiveArraySize = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));

        Log.e(TAG, "params read: distCalibr=" + distanceCalibration + ", focalLen=" + Arrays.toString(focalLengths));
        Log.e(TAG, "focus read: afMaxRegions=" + afMaxRegions + ", min=" + minimumFocus + ", max=" + maximumFocus);
        Log.e(TAG, "maxFrameDuration=" + maxFrameDuration + ", hardwareLevel=" + hardwareLevel + ", sensOrient=" + sensorOrientation);
        Log.e(TAG, "activeArraySize bottom=" + activeArraySize.bottom + ", top=" + activeArraySize.top + ", left=" + activeArraySize.left + ", right=" + activeArraySize.right);
        Log.e(TAG, "preActiveArraySize bottom=" + preActiveArraySize.bottom + ", top=" + preActiveArraySize.top + ", left=" + preActiveArraySize.left + ", right=" + preActiveArraySize.right);

        if (minimumFocus != 0.0f) {
            minimumFocusMeters = 1.0f/minimumFocus;
        }
        if (maximumFocus != 0.0f) {
            maximumFocusMeters = 1.0f/maximumFocus;
        }
        float deltaStepsFocus = 20.0f;
        deltaFocus = (maximumFocusMeters - minimumFocusMeters) / deltaStepsFocus;
        currentFocusMeters = minimumFocusMeters;
        currentFocus = 1.0f/currentFocusMeters;
        Log.e(TAG, "minimumFocusMeters=" + minimumFocusMeters + ", maximumFocusMeters=" + maximumFocusMeters + ", deltaFocusMeters=" + deltaFocus);
    }
    protected MeteringRectangle convertTouchToTexture(View view, MotionEvent motionEvent) {
        final Rect sensorArraySize  = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        final int sensorOrientation = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
        Log.e("TOUCH", "X=" + motionEvent.getX() + ", Y=" + motionEvent.getY() + ", Major=" + motionEvent.getTouchMajor() + ", Minor=" + motionEvent.getTouchMinor());
        final float xNormalized = motionEvent.getX() / (float)view.getWidth();
        final float yNormalized = motionEvent.getY() / (float)view.getHeight();
        int xSensor = (int)(xNormalized * (float)sensorArraySize.width());
        int ySensor = (int)(yNormalized * (float)sensorArraySize.height());

        if (sensorOrientation == 90) {
            xSensor = (int)(yNormalized * (float)sensorArraySize.width());
            ySensor = (int)((1.0f-xNormalized) * (float)sensorArraySize.height());
        } else if (sensorOrientation == 180) {
            xSensor = (int)((1.0f-xNormalized) * (float)sensorArraySize.width());
            ySensor = (int)((1.0f-yNormalized) * (float)sensorArraySize.height());
        } else if (sensorOrientation == 270) {
            xSensor = (int)((1.0f-yNormalized) * (float)sensorArraySize.width());
            ySensor = (int)(xNormalized * (float)sensorArraySize.height());
        }

        final int halfTouchWidth  = (int)motionEvent.getTouchMajor();
        final int halfTouchHeight = (int)motionEvent.getTouchMinor();
        Log.e("TRANSLATED", "X=" + xSensor + ", Y=" + ySensor + ", halfTouchWidth=" + halfTouchWidth + ", halfTouchHeight=" + halfTouchHeight);
        return new MeteringRectangle(Math.max(xSensor - halfTouchWidth,  0),
                Math.max(ySensor - halfTouchHeight, 0),
                halfTouchWidth  * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }

    protected void updatePreview() {
        Log.d(TAG, "App updatePreview() called");
        if(cameraDevice == null) {
            Log.e(TAG, "App updatePreview() error, returning");
        } else {
            try {
                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackListener, handlerManager.mRequestHandler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
    private void startBackgroundThreads() {
        if(handlerManager == null) {
            handlerManager = new BackgroundThreadHandler();
        }
    }

    private void stopBackgroundThreads() {
        if(handlerManager != null) {
            handlerManager.stop();
            handlerManager = null;
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    public void closeAppSequence() {
        Log.d(TAG, "App closeAppSequence() called");
        Toast.makeText(AndroidCameraApi.this, "closeAppSequence() called. Exiting", Toast.LENGTH_LONG).show();
        closeCamera();
        stopBackgroundThreads();
        finish();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(AndroidCameraApi.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        Log.d(TAG, "App onResume() called");
        super.onResume();
        startBackgroundThreads();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "App onPause() called");
        closeCamera();
        stopBackgroundThreads();
        super.onPause();
    }
}
