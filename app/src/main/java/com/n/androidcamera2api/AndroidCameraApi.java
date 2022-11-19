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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
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
import java.util.Objects;

public class AndroidCameraApi extends AppCompatActivity {
    protected static final String TAG = "AndroidCameraApi";
    protected static final String callbackTag = "CameraState";
    protected static final String TOUCH_FOCUS_TAG = "TOUCH_FOCUS_TAG";
    protected static final String MANUAL_FOCUS_TAG = "MANUAL_FOCUS_TAG";
    protected static final String ACTIVE_FOCUS_TAG = "ACTIVE_FOCUS_TAG";
    protected static final String ACTIVE_CHANGING_FOCUS_TAG = "ACTIVE_CHANGING_FOCUS_TAG";
    protected String current_tag = ACTIVE_FOCUS_TAG;
    protected boolean afEnabled = false;
    protected static final String[] afManualModesNames = {"AF_AUTO", "AF_MACRO", "AF_EDOF", "AF_OFF"};
    protected static final int[] afManualModes = {CameraMetadata.CONTROL_AF_MODE_AUTO, CameraMetadata.CONTROL_AF_MODE_MACRO, CameraMetadata.CONTROL_AF_MODE_EDOF, CameraMetadata.CONTROL_AF_MODE_OFF};
    protected static int afManualModesCurrentIndex = 0;
    protected CameraManager manager;
    protected String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder = null;
    protected CameraCharacteristics characteristics = null;
    protected MeteringRectangle[] controlAfRegion = null;
    protected int afRegionsIndex = 0;
    protected MeteringRectangle[] afRegions = new MeteringRectangle[4];
    protected Size imageDimension;
    protected ImageReader imageReader;
    protected static final int REQUEST_CAMERA_PERMISSION = 200;
    protected BackgroundThreadHandler handlerManager;
    TextView distanceTextView;
    protected boolean areWeFocused = false;
    protected boolean mManualFocusEngaged = false;
    protected int afStateLast = 0;
    int sensorOrientation = 90;
    Rect activeArraySize;
    Rect preActiveArraySize;
    protected float lensDistanceLastDiopters = 0.0f;
    protected float deltaFocus;
    protected float currentFocus;
    protected float currentFocusMeters = 0.0f;
    protected float minimumFocusMeters = 0.0f;
    protected float maximumFocusMeters = 0.0f;

    protected CameraPreviewTextureListener cameraTextureListener;
    protected CanvasTextureListener canvasTextureListener;

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
            updatePreview(current_tag);
        }
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "onConfigureFailed()");
            Toast.makeText(AndroidCameraApi.this, "Configuration change failed", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CaptureSessionCaptureCallback(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_camera_api);
        cameraTextureListener = new CameraPreviewTextureListener(this);
        canvasTextureListener = new CanvasTextureListener(this);

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
        textureTouchView.setOnTouchListener(new ViewOnTouchListener(this));

        afTriggerButton.setOnClickListener(v -> {
            if(++afManualModesCurrentIndex >= afManualModes.length) {
                afManualModesCurrentIndex = 0;
            }
            afTriggerButton.setText(afManualModesNames[afManualModesCurrentIndex]);
        });
        afTriggerButton.setText(afManualModesNames[afManualModesCurrentIndex]);
        deltaMinusButton.setOnClickListener(v -> {
            currentFocusMeters -= deltaFocus;
            if(currentFocusMeters < minimumFocusMeters) {
                currentFocusMeters = minimumFocusMeters;
            }
            currentFocus = 1.0f/currentFocusMeters;
            takePicture(false);
        });
        deltaPlusButton.setOnClickListener(v -> {
            currentFocusMeters += deltaFocus;
            if(currentFocusMeters > maximumFocusMeters) {
                currentFocusMeters = maximumFocusMeters;
            }
            currentFocus = 1.0f/currentFocusMeters;
            takePicture(false);
        });
        afOnButton.setOnClickListener(v -> takePicture(true));
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
            cameraTextureListener.initializeSurface(imageDimension.getWidth(), imageDimension.getHeight());

            if (captureRequestBuilder == null) {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                captureRequestBuilder.addTarget(cameraTextureListener.getCameraTextureSurface());
            }

//            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, maxFrameDuration);
            if (afEnabled) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequestBuilder.setTag(ACTIVE_CHANGING_FOCUS_TAG);
                current_tag = ACTIVE_CHANGING_FOCUS_TAG;
//                captureRequestBuilder.setTag(ACTIVE_CHANGING_FOCUS_TAG);
//                current_tag = ACTIVE_CHANGING_FOCUS_TAG;
            } else {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocus);
                captureRequestBuilder.setTag(MANUAL_FOCUS_TAG);
                Log.e(TAG, "new MAN focus " + (currentFocus));
            }
            cameraDevice.createCaptureSession(Collections.singletonList(cameraTextureListener.getCameraTextureSurface()), stateCallbackListener, handlerManager.mSessionHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void openCamera() {
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
            Log.e(TAG, "openCamera StreamConfigurationMap " + map);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[13];
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
            assert keyValue != null;
            String value = keyValue.toString();
            if(keyValue.getClass().isArray()) {
                if(keyValue.getClass().getComponentType() == int.class) {
                    value = Arrays.toString((int[]) keyValue);
                } else if (keyValue.getClass().getComponentType() == float.class) {
                    value = Arrays.toString((float[]) keyValue);
                } else if (keyValue.getClass().getComponentType() == long.class) {
                    value = Arrays.toString((long[]) keyValue);
                }
            }

            Log.e("Keys", it.toString() + ", value=" + value + ",  name=" + it.getName());
        }
        float minimumFocus = Objects.<Float>requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE));
        float maximumFocus = Objects.<Float>requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE));
        int distanceCalibration = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION));
        float[] focalLengths = Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        int hardwareLevel = Objects.requireNonNull(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
        sensorOrientation = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
        long maxFrameDuration = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION));
        int afMaxRegions = Objects.requireNonNull(characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF));
        activeArraySize = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        preActiveArraySize = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
        afRegions[0] = new MeteringRectangle(0, 0, activeArraySize.width()/2, activeArraySize.height()/2, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        afRegions[1] = new MeteringRectangle(activeArraySize.width()/2, 0, activeArraySize.width()/2, activeArraySize.height()/2, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        afRegions[2] = new MeteringRectangle(0, activeArraySize.height()/2, activeArraySize.width()/2, activeArraySize.height()/2, MeteringRectangle.METERING_WEIGHT_MAX - 1);
        afRegions[3] = new MeteringRectangle(activeArraySize.width()/2, activeArraySize.height()/2, activeArraySize.width()/2, activeArraySize.height()/2, MeteringRectangle.METERING_WEIGHT_MAX - 1);

        Log.e(TAG, "params read: distCalibration=" + distanceCalibration + ", focalLen=" + Arrays.toString(focalLengths));
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

    protected void updatePreview(String use_tag) {
        Log.d(TAG, "App updatePreview() called");
        if(cameraDevice == null) {
            Log.e(TAG, "App updatePreview() error, returning");
        } else if (use_tag.equals(ACTIVE_CHANGING_FOCUS_TAG)) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{afRegions[afRegionsIndex]});
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            try {
                cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackListener, handlerManager.mRequestHandler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        if (cameraTextureListener.isTextureViewAvailable()) {
            openCamera();
        } else {
            cameraTextureListener.reassignListener();
        }
        if (!canvasTextureListener.isTextureViewAvailable()) {
            canvasTextureListener.reassignListener();
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
