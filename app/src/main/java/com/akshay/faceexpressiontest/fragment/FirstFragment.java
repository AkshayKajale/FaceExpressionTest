package com.akshay.faceexpressiontest.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.media.Image;
import android.os.Bundle;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.view.*;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import androidx.core.content.ContextCompat;
import com.akshay.faceexpressiontest.R;
import com.akshay.faceexpressiontest.dependency.AutoFitTextureView;
import android.hardware.camera2.params.StreamConfigurationMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import com.akshay.faceexpressiontest.util.CompareSizesByViewAspectRatio;
import com.darwin.viola.still.FaceDetectionListener;
import com.darwin.viola.still.Viola;
import com.darwin.viola.still.model.FaceDetectionError;
import com.darwin.viola.still.model.FaceOptions;
import com.darwin.viola.still.model.Result;
import com.quickbirdstudios.yuv2mat.Yuv;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

public class FirstFragment extends Fragment {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private static final String TAG = "Camera2BasicFragment";
    private TensorFlowInferenceInterface inferenceInterface;
    private Interpreter tflite;
    TensorImage tensorImage;
    ImageProcessor imageProcessor =
            new ImageProcessor.Builder()
                    .add(new ResizeOp(48, 48, ResizeOp.ResizeMethod.BILINEAR))
                    .build();


    private String emotions[] = {"Angry", "Disgust","fear","surprise","Sad","Happy", "Neutral"};

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private  HandlerThread backgroundThreadFront = null;
    private HandlerThread backgroundThreadRear = null;
    private Handler backgroundHandlerFront = null;
    private Handler backgroundHandlerRear = null;
    private ImageReader imageReaderFront = null;
    private ImageReader imageReaderRear = null;
    private AutoFitTextureView textureViewFront;
    private AutoFitTextureView textureViewRear;
    private CameraCaptureSession captureSessionFront = null;
    private CameraCaptureSession captureSessionRear  = null;
    private CameraDevice cameraDeviceFront = null;
    private CameraDevice cameraDeviceRear = null;
    private Size previewSizeFront;
    private Size previewSizeRear;
    private String cameraIdFront;
    private String cameraIdRear;
    private int sensorOrientationFront = 0;
    private int sensorOrientationRear = 0;
    private Semaphore cameraOpenCloseLockFront= new Semaphore(1);
    private Semaphore cameraOpenCloseLockRear=new Semaphore(1);
    private CaptureRequest.Builder previewRequestBuilderFront;
    private CaptureRequest.Builder previewRequestBuilderRear;
    private boolean flashSupported = false;
    private CaptureRequest previewRequestFront;
    private CaptureRequest previewRequestRear;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private CameraCharacteristics frontCameraCharacteristics;
    private CameraCharacteristics rearCameraCharacteristics;
    private Image latestImage;
    private Handler handler;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.1f;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;




    private final ImageReader.OnImageAvailableListener onImageAvailableListenerFront
            = new ImageReader.OnImageAvailableListener() {
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onImageAvailable(ImageReader reader) {

            Log.d(TAG, "onImageAvailableListener Called");
            latestImage = reader.acquireLatestImage();
            Log.d(TAG, "onImageAvailable: height " + latestImage.getHeight() + " width " + latestImage.getWidth());
//            Mat mat = imageToMat(latestImage);
            ByteBuffer buffer = latestImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            bitmapImage = toGrayscale(bitmapImage);
            System.out.println("Space"+bitmapImage.getColorSpace());
            System.out.println("This is Bitmap Pixel"+bitmapImage.getColor(20,20));

            final FaceDetectionListener listener = new FaceDetectionListener() {
                @Override
                public void onFaceDetected(@NotNull Result result) {
                    System.out.println("Face Count"+result.getFaceCount());
                    result.getFacePortraits();
                    System.out.println(result.getFacePortraits().get(0));
                }

                @Override
                public void onFaceDetectionFailed(@NotNull FaceDetectionError error, @NotNull String message) {
                    System.out.println("This is Message"+message);
                }
            };
            Viola viola = new Viola(listener);
            FaceOptions faceOptions = new FaceOptions.Builder()
                    .enableProminentFaceDetection()
                    .enableDebug()
                    .build();
            viola.detectFace(bitmapImage,faceOptions);

            System.out.println("Min Face Size"+faceOptions.getMinimumFaceSize());
            DataType myImageDataType = tflite.getInputTensor(0).dataType();
            tensorImage = new TensorImage(myImageDataType);
            tensorImage.load(bitmapImage);
            tensorImage = imageProcessor.process(tensorImage);
            Log.d(TAG, "bitmap: height " + tensorImage.getHeight());
            System.out.println("Tensor Image Type Size"+Arrays.toString(tensorImage.getTensorBuffer().getFloatArray()));
            passImageToTFModel(tensorImage);
            latestImage.close();
        }
    };

    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListenerRear
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            //Log.d(TAG, "onImageAvailableListener Called");
        }
    };


    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback(){
        private void process(CaptureResult result){

            Log.d(TAG, "process: flow coming");

        }

        private void capturePicture(CaptureResult result) {

        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextListenerFront = new TextureView.SurfaceTextureListener(){

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCameraFront(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransformFront(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextListenerRear = new TextureView.SurfaceTextureListener(){

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCameraRear(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransformRear(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private CameraDevice.StateCallback stateCallbackFront = new CameraDevice.StateCallback(){

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened. We start camera preview here.
            cameraOpenCloseLockFront.release();
            cameraDeviceFront = cameraDevice;
            createCameraPreviewSessionFront();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLockFront.release();
            cameraDevice.close();
            cameraDeviceFront = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {

            onDisconnected(cameraDevice);
            Activity activity = getActivity();

            if(activity!=null)
            {
                activity.finish();
            }
        }
    };

    private CameraDevice.StateCallback stateCallbackRear = new CameraDevice.StateCallback(){

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened. We start camera preview here.
            cameraOpenCloseLockFront.release();
            cameraDeviceRear = cameraDevice;
            createCameraPreviewSessionRear();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLockFront.release();
            cameraDevice.close();
            cameraDeviceRear = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {

            onDisconnected(cameraDevice);
            Activity activity = getActivity();

            if(activity!=null)
            {
                activity.finish();
            }
        }
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        textureViewFront = view.findViewById(R.id.texture1);
        textureViewRear = view.findViewById(R.id.texture2);
        if (!OpenCVLoader.initDebug())
            Log.e("OpenCv", "Unable to load OpenCV");
        else
            Log.d("OpenCv", "OpenCV loaded");


                float image [] = {151 ,150 ,147 ,155 ,148 ,133 ,111 ,140 ,170 ,174 ,182 ,154 ,153 ,164 ,173 ,178 ,185 ,185 ,189 ,187 ,186 ,193 ,194 ,185 ,183 ,186 ,180 ,173 ,166 ,161 ,147 ,133 ,172 ,151 ,114 ,161 ,161 ,146 ,131 ,104 ,95 ,132 ,163 ,123 ,119 ,129 ,140 ,120 ,151 ,149 ,149 ,153 ,137 ,115 ,129 ,166 ,170 ,181 ,164 ,143 ,157 ,156 ,169 ,179 ,185 ,183 ,186 ,186 ,184 ,190 ,191 ,184 ,186 ,190 ,183 ,175 ,168 ,160 ,147 ,136 ,135 ,167 ,136 ,108 ,153 ,167 ,149 ,137 ,111 ,90 ,134 ,162 ,121 ,122 ,141 ,137 ,151 ,151 ,156 ,143 ,116 ,124 ,159 ,164 ,174 ,169 ,135 ,144 ,155 ,153 ,164 ,170 ,176 ,178 ,177 ,178 ,187 ,185 ,181 ,182 ,183 ,181 ,178 ,170 ,164 ,158 ,148 ,144 ,130 ,136 ,173 ,130 ,97 ,137 ,167 ,157 ,138 ,113 ,90 ,138 ,168 ,109 ,123 ,146 ,151 ,152 ,155 ,127 ,113 ,159 ,167 ,170 ,171 ,142 ,131 ,140 ,154 ,162 ,168 ,169 ,169 ,164 ,168 ,173 ,176 ,179 ,178 ,176 ,173 ,172 ,170 ,161 ,154 ,152 ,146 ,145 ,137 ,124 ,130 ,171 ,124 ,102 ,133 ,164 ,152 ,138 ,110 ,86 ,154 ,149 ,100 ,139 ,153 ,151 ,136 ,113 ,142 ,159 ,161 ,174 ,150 ,127 ,136 ,140 ,154 ,164 ,163 ,167 ,173 ,172 ,171 ,170 ,167 ,168 ,172 ,167 ,162 ,161 ,160 ,163 ,163 ,154 ,145 ,146 ,140 ,133 ,122 ,135 ,167 ,127 ,101 ,126 ,164 ,147 ,132 ,95 ,91 ,166 ,115 ,113 ,158 ,143 ,121 ,134 ,153 ,153 ,164 ,162 ,131 ,130 ,136 ,146 ,155 ,158 ,155 ,157 ,163 ,163 ,158 ,159 ,159 ,161 ,165 ,156 ,153 ,156 ,159 ,163 ,163 ,150 ,149 ,150 ,146 ,140 ,137 ,122 ,147 ,154 ,116 ,97 ,133 ,164 ,142 ,123 ,77 ,117 ,147 ,95 ,149 ,127 ,129 ,153 ,142 ,165 ,171 ,136 ,116 ,129 ,130 ,139 ,140 ,149 ,153 ,147 ,146 ,150 ,150 ,155 ,151 ,155 ,156 ,153 ,152 ,157 ,165 ,165 ,160 ,150 ,156 ,156 ,148 ,141 ,135 ,135 ,132 ,147 ,141 ,110 ,97 ,143 ,165 ,142 ,101 ,66 ,151 ,117 ,136 ,125 ,148 ,139 ,153 ,173 ,159 ,118 ,116 ,119 ,123 ,131 ,134 ,145 ,145 ,137 ,142 ,151 ,157 ,159 ,153 ,154 ,153 ,150 ,159 ,170 ,171 ,167 ,160 ,160 ,159 ,158 ,152 ,141 ,145 ,144 ,140 ,119 ,144 ,133 ,106 ,101 ,151 ,148 ,130 ,70 ,119 ,148 ,129 ,143 ,146 ,134 ,165 ,165 ,134 ,121 ,123 ,121 ,125 ,129 ,136 ,150 ,159 ,163 ,165 ,161 ,157 ,155 ,147 ,150 ,148 ,135 ,156 ,171 ,169 ,171 ,166 ,165 ,165 ,158 ,153 ,146 ,147 ,150 ,137 ,122 ,112 ,144 ,122 ,94 ,111 ,158 ,143 ,91 ,74 ,155 ,134 ,147 ,131 ,154 ,167 ,153 ,107 ,125 ,129 ,134 ,137 ,134 ,136 ,154 ,168 ,174 ,171 ,168 ,163 ,156 ,151 ,150 ,146 ,146 ,166 ,171 ,173 ,176 ,177 ,191 ,187 ,175 ,180 ,179 ,174 ,165 ,142 ,134 ,108 ,111 ,137 ,108 ,86 ,143 ,156 ,116 ,64 ,133 ,143 ,129 ,138 ,162 ,167 ,132 ,117 ,127 ,128 ,140 ,147 ,145 ,155 ,159 ,165 ,170 ,172 ,166 ,160 ,154 ,156 ,160 ,156 ,156 ,158 ,153 ,170 ,188 ,198 ,208 ,203 ,209 ,219 ,217 ,200 ,174 ,171 ,147 ,128 ,92 ,129 ,131 ,88 ,103 ,159 ,135 ,75 ,111 ,133 ,126 ,160 ,156 ,159 ,117 ,132 ,129 ,131 ,144 ,160 ,165 ,162 ,166 ,170 ,165 ,160 ,160 ,159 ,153 ,149 ,150 ,151 ,148 ,120 ,132 ,189 ,183 ,180 ,177 ,187 ,201 ,194 ,186 ,181 ,170 ,167 ,146 ,118 ,117 ,100 ,139 ,112 ,74 ,139 ,145 ,85 ,91 ,120 ,151 ,156 ,164 ,129 ,111 ,135 ,140 ,139 ,142 ,158 ,163 ,172 ,190 ,195 ,189 ,168 ,156 ,149 ,143 ,137 ,140 ,140 ,132 ,114 ,142 ,150 ,129 ,133 ,125 ,138 ,140 ,132 ,114 ,124 ,131 ,118 ,143 ,125 ,126 ,82 ,97 ,141 ,72 ,113 ,152 ,93 ,77 ,134 ,157 ,149 ,166 ,95 ,124 ,136 ,142 ,156 ,165 ,179 ,189 ,198 ,198 ,186 ,186 ,188 ,157 ,126 ,124 ,118 ,129 ,128 ,115 ,95 ,100 ,90 ,84 ,81 ,66 ,72 ,80 ,77 ,63 ,59 ,65 ,70 ,67 ,86 ,105 ,82 ,95 ,138 ,89 ,94 ,153 ,106 ,74 ,153 ,148 ,165 ,134 ,80 ,134 ,137 ,149 ,173 ,187 ,193 ,201 ,185 ,169 ,141 ,128 ,150 ,139 ,113 ,119 ,123 ,128 ,114 ,91 ,71 ,60 ,51 ,69 ,78 ,80 ,80 ,79 ,76 ,76 ,65 ,65 ,85 ,103 ,46 ,73 ,142 ,85 ,114 ,114 ,75 ,145 ,121 ,70 ,150 ,141 ,168 ,76 ,91 ,130 ,144 ,179 ,197 ,210 ,190 ,164 ,146 ,128 ,121 ,90 ,92 ,90 ,90 ,114 ,104 ,109 ,110 ,81 ,52 ,53 ,87 ,96 ,94 ,99 ,96 ,88 ,95 ,108 ,116 ,110 ,97 ,120 ,98 ,64 ,120 ,77 ,105 ,130 ,67 ,131 ,128 ,67 ,137 ,150 ,136 ,46 ,106 ,126 ,164 ,164 ,151 ,150 ,131 ,115 ,103 ,88 ,90 ,91 ,92 ,78 ,56 ,49 ,65 ,90 ,115 ,66 ,29 ,91 ,99 ,104 ,107 ,88 ,94 ,105 ,111 ,118 ,113 ,116 ,103 ,103 ,120 ,47 ,83 ,110 ,120 ,134 ,67 ,116 ,136 ,74 ,133 ,163 ,82 ,51 ,113 ,115 ,104 ,112 ,144 ,162 ,138 ,104 ,96 ,93 ,95 ,90 ,91 ,99 ,103 ,55 ,29 ,46 ,45 ,16 ,40 ,104 ,95 ,101 ,92 ,93 ,96 ,80 ,69 ,82 ,99 ,98 ,108 ,116 ,128 ,70 ,102 ,117 ,127 ,140 ,79 ,105 ,139 ,82 ,140 ,149 ,23 ,43 ,103 ,50 ,78 ,179 ,185 ,147 ,111 ,113 ,117 ,106 ,91 ,95 ,100 ,91 ,96 ,92 ,19 ,6 ,19 ,8 ,35 ,88 ,64 ,66 ,60 ,72 ,68 ,34 ,35 ,70 ,85 ,101 ,118 ,130 ,143 ,82 ,114 ,125 ,130 ,149 ,93 ,104 ,139 ,83 ,148 ,130 ,67 ,86 ,94 ,42 ,142 ,173 ,151 ,128 ,124 ,106 ,96 ,103 ,90 ,89 ,100 ,97 ,95 ,103 ,28 ,48 ,127 ,126 ,38 ,87 ,81 ,68 ,64 ,70 ,74 ,51 ,68 ,92 ,112 ,139 ,149 ,152 ,158 ,100 ,134 ,128 ,133 ,159 ,105 ,95 ,139 ,86 ,153 ,113 ,62 ,126 ,130 ,62 ,135 ,136 ,127 ,122 ,93 ,67 ,54 ,44 ,42 ,43 ,60 ,76 ,86 ,104 ,41 ,115 ,197 ,211 ,112 ,67 ,118 ,109 ,107 ,93 ,84 ,83 ,101 ,108 ,129 ,150 ,158 ,156 ,143 ,105 ,140 ,129 ,131 ,166 ,115 ,85 ,137 ,87 ,156 ,99 ,21 ,89 ,104 ,46 ,117 ,135 ,113 ,78 ,52 ,89 ,94 ,46 ,24 ,45 ,54 ,65 ,95 ,92 ,49 ,161 ,213 ,212 ,188 ,85 ,97 ,133 ,130 ,124 ,121 ,122 ,136 ,147 ,143 ,154 ,151 ,173 ,124 ,115 ,147 ,129 ,124 ,164 ,136 ,95 ,126 ,85 ,159 ,94 ,36 ,97 ,117 ,78 ,93 ,130 ,90 ,73 ,77 ,94 ,86 ,61 ,60 ,72 ,69 ,91 ,113 ,80 ,76 ,188 ,214 ,213 ,200 ,143 ,66 ,119 ,138 ,150 ,151 ,151 ,159 ,163 ,156 ,154 ,162 ,140 ,107 ,136 ,148 ,135 ,115 ,158 ,150 ,116 ,111 ,87 ,161 ,101 ,50 ,109 ,111 ,98 ,69 ,99 ,97 ,109 ,111 ,114 ,106 ,95 ,96 ,99 ,101 ,109 ,122 ,70 ,111 ,193 ,214 ,217 ,200 ,183 ,115 ,78 ,124 ,157 ,167 ,170 ,172 ,166 ,161 ,147 ,127 ,118 ,134 ,138 ,141 ,141 ,119 ,147 ,155 ,131 ,98 ,96 ,163 ,103 ,72 ,120 ,112 ,115 ,80 ,100 ,117 ,100 ,105 ,127 ,138 ,133 ,132 ,140 ,137 ,121 ,103 ,64 ,153 ,200 ,215 ,218 ,205 ,183 ,169 ,127 ,77 ,92 ,111 ,122 ,126 ,128 ,128 ,129 ,135 ,145 ,141 ,138 ,133 ,138 ,127 ,139 ,169 ,127 ,83 ,93 ,161 ,106 ,87 ,122 ,106 ,117 ,114 ,88 ,124 ,116 ,108 ,135 ,150 ,151 ,156 ,163 ,162 ,121 ,66 ,110 ,177 ,201 ,212 ,214 ,200 ,181 ,162 ,155 ,117 ,97 ,103 ,109 ,115 ,119 ,131 ,141 ,147 ,149 ,141 ,146 ,140 ,128 ,130 ,140 ,170 ,101 ,87 ,89 ,163 ,109 ,105 ,123 ,103 ,112 ,133 ,113 ,95 ,132 ,139 ,160 ,167 ,169 ,158 ,149 ,118 ,72 ,89 ,133 ,169 ,189 ,197 ,204 ,194 ,180 ,172 ,166 ,138 ,114 ,112 ,114 ,120 ,119 ,125 ,134 ,140 ,150 ,145 ,142 ,145 ,142 ,144 ,152 ,164 ,116 ,95 ,84 ,159 ,113 ,111 ,124 ,101 ,112 ,136 ,134 ,115 ,107 ,118 ,131 ,138 ,129 ,108 ,93 ,75 ,88 ,112 ,136 ,170 ,183 ,181 ,178 ,181 ,177 ,177 ,180 ,175 ,130 ,110 ,119 ,125 ,130 ,131 ,135 ,138 ,142 ,146 ,136 ,130 ,146 ,142 ,157 ,169 ,135 ,91 ,100 ,150 ,115 ,114 ,138 ,107 ,114 ,133 ,125 ,124 ,133 ,137 ,128 ,119 ,106 ,102 ,98 ,93 ,96 ,141 ,160 ,147 ,149 ,150 ,145 ,153 ,161 ,147 ,146 ,175 ,162 ,114 ,112 ,121 ,127 ,128 ,131 ,129 ,134 ,142 ,136 ,124 ,139 ,141 ,157 ,175 ,138 ,106 ,149 ,145 ,119 ,119 ,150 ,109 ,115 ,136 ,129 ,126 ,131 ,137 ,130 ,114 ,106 ,108 ,109 ,99 ,115 ,156 ,138 ,116 ,134 ,139 ,130 ,140 ,119 ,53 ,45 ,103 ,139 ,131 ,101 ,98 ,117 ,126 ,130 ,123 ,124 ,133 ,137 ,131 ,130 ,143 ,160 ,177 ,148 ,158 ,182 ,146 ,122 ,117 ,161 ,118 ,116 ,131 ,134 ,130 ,130 ,124 ,121 ,118 ,117 ,119 ,112 ,92 ,107 ,134 ,74 ,29 ,73 ,122 ,129 ,126 ,78 ,33 ,42 ,65 ,107 ,123 ,103 ,97 ,96 ,113 ,121 ,119 ,121 ,124 ,131 ,130 ,128 ,157 ,176 ,179 ,185 ,187 ,181 ,158 ,124 ,111 ,169 ,131 ,116 ,123 ,129 ,130 ,129 ,129 ,128 ,125 ,124 ,114 ,86 ,89 ,104 ,91 ,46 ,31 ,35 ,74 ,113 ,107 ,93 ,79 ,79 ,94 ,121 ,131 ,126 ,119 ,92 ,100 ,114 ,114 ,117 ,115 ,120 ,125 ,130 ,164 ,179 ,188 ,192 ,182 ,184 ,182 ,140 ,106 ,170 ,147 ,120 ,121 ,129 ,130 ,127 ,128 ,129 ,128 ,123 ,98 ,62 ,98 ,116 ,113 ,94 ,90 ,85 ,81 ,80 ,104 ,116 ,126 ,141 ,136 ,148 ,148 ,129 ,122 ,101 ,95 ,107 ,107 ,117 ,123 ,125 ,130 ,139 ,165 ,179 ,184 ,192 ,184 ,183 ,191 ,167 ,119 ,173 ,158 ,136 ,118 ,130 ,131 ,125 ,123 ,123 ,125 ,114 ,81 ,71 ,131 ,147 ,148 ,138 ,133 ,117 ,101 ,88 ,118 ,131 ,143 ,163 ,167 ,165 ,164 ,146 ,132 ,122 ,107 ,95 ,104 ,115 ,126 ,136 ,137 ,146 ,168 ,180 ,179 ,195 ,187 ,183 ,188 ,186 ,141 ,158 ,165 ,147 ,116 ,131 ,130 ,124 ,118 ,117 ,119 ,102 ,70 ,101 ,140 ,154 ,153 ,142 ,134 ,125 ,118 ,117 ,118 ,116 ,120 ,137 ,149 ,163 ,172 ,158 ,144 ,137 ,138 ,117 ,112 ,123 ,129 ,139 ,146 ,157 ,167 ,175 ,178 ,196 ,190 ,184 ,190 ,188 ,167 ,155 ,174 ,161 ,134 ,119 ,114 ,121 ,118 ,113 ,109 ,89 ,93 ,134 ,140 ,161 ,170 ,145 ,126 ,108 ,121 ,117 ,108 ,117 ,136 ,146 ,142 ,138 ,150 ,149 ,147 ,135 ,142 ,122 ,115 ,142 ,149 ,144 ,144 ,162 ,168 ,171 ,176 ,192 ,187 ,182 ,192 ,183 ,185 ,175 ,174 ,145 ,87 ,65 ,63 ,84 ,112 ,114 ,98 ,92 ,109 ,140 ,155 ,151 ,147 ,123 ,103 ,111 ,112 ,116 ,108 ,111 ,127 ,130 ,117 ,115 ,124 ,129 ,138 ,124 ,118 ,120 ,114 ,135 ,144 ,141 ,144 ,154 ,168 ,170 ,174 ,187 ,182 ,179 ,187 ,184 ,184 ,187 ,132 ,28 ,6 ,24 ,36 ,39 ,69 ,111 ,99 ,84 ,109 ,134 ,133 ,108 ,97 ,84 ,75 ,78 ,74 ,81 ,80 ,62 ,48 ,32 ,27 ,36 ,42 ,56 ,88 ,87 ,86 ,123 ,130 ,138 ,138 ,134 ,141 ,151 ,170 ,167 ,173 ,189 ,184 ,181 ,187 ,186 ,191 ,192 ,63 ,26 ,18 ,3 ,16 ,22 ,23 ,85 ,106 ,98 ,110 ,112 ,89 ,47 ,35 ,41 ,20 ,18 ,21 ,13 ,9 ,7 ,3 ,2 ,4 ,4 ,3 ,4 ,30 ,40 ,49 ,112 ,129 ,142 ,143 ,139 ,144 ,151 ,171 ,174 ,177 ,188 ,186 ,181 ,189 ,189 ,170 ,124 ,47 ,33 ,28 ,8 ,4 ,12 ,11 ,48 ,111 ,113 ,100 ,85 ,50 ,19 ,4 ,5 ,3 ,10 ,14 ,12 ,15 ,26 ,31 ,35 ,38 ,37 ,40 ,62 ,84 ,73 ,61 ,124 ,143 ,138 ,130 ,140 ,139 ,152 ,157 ,170 ,179 ,186 ,186 ,181 ,179 ,115 ,39 ,26 ,14 ,6 ,10 ,3 ,1 ,5 ,2 ,48 ,124 ,117 ,106 ,71 ,34 ,42 ,32 ,17 ,17 ,24 ,35 ,41 ,49 ,63 ,67 ,70 ,72 ,88 ,106 ,118 ,107 ,97 ,75 ,95 ,139 ,138 ,125 ,130 ,131 ,150 ,160 ,173 ,183 ,188 ,186 ,184 ,114 ,54 ,84 ,157 ,71 ,0 ,6 ,5 ,4 ,3 ,0 ,75 ,122 ,109 ,103 ,69 ,53 ,67 ,68 ,59 ,55 ,54 ,59 ,65 ,74 ,88 ,97 ,104 ,109 ,119 ,129 ,128 ,127 ,130 ,98 ,96 ,136 ,156 ,130 ,117 ,128 ,148 ,165 ,176 ,190 ,191 ,186 ,183 ,74 ,64 ,167 ,201 ,164 ,43 ,1 ,0 ,0 ,3 ,46 ,114 ,118 ,110 ,110 ,67 ,66 ,97 ,83 ,86 ,87 ,98 ,93 ,98 ,102 ,112 ,119 ,119 ,125 ,126 ,130 ,128 ,127 ,135 ,134 ,112 ,123 ,150 ,136 ,118 ,126 ,146 ,158 ,179 ,196 ,188 ,185 ,185 ,142 ,50 ,136 ,182 ,194 ,173 ,83 ,58 ,60 ,100 ,130 ,121 ,113 ,120 ,116 ,80 ,77 ,95 ,103 ,111 ,116 ,124 ,116 ,120 ,117 ,119 ,108 ,123 ,129 ,144 ,160 ,143 ,129 ,130 ,127 ,132 ,123 ,149 ,132 ,117 ,125 ,142 ,151 ,188 ,194 ,187 ,184 ,185 ,197 ,128 ,84 ,169 ,177 ,199 ,178 ,162 ,147 ,154 ,146 ,124 ,107 ,129 ,120 ,96 ,88 ,99 ,131 ,131 ,145 ,131 ,137 ,148 ,131 ,133 ,128 ,124 ,118 ,142 ,167 ,159 ,130 ,136 ,134 ,127 ,128 ,143 ,133 ,118 ,139 ,140 ,144 ,192 ,196 ,187 ,184 ,184 ,188 ,188 ,121 ,86 ,175 ,189 ,192 ,157 ,148 ,144 ,149 ,126 ,113 ,123 ,127 ,106 ,96 ,107 ,145 ,143 ,144 ,131 ,147 ,150 ,143 ,147 ,134 ,126 ,131 ,151 ,170 ,162 ,148 ,116 ,139 ,122 ,124 ,141 ,127 ,112 ,128 ,116 ,113 ,159 ,201 ,185 ,185 ,186 ,188 ,187 ,196 ,129 ,85 ,171 ,194 ,171 ,142 ,144 ,144 ,128 ,119 ,116 ,121 ,116 ,100 ,108 ,136 ,146 ,128 ,135 ,151 ,146 ,152 ,150 ,158 ,143 ,133 ,143 ,159 ,153 ,152 ,128 ,137 ,133 ,125 ,127 ,102 ,108 ,109 ,105 ,102 ,106 ,197 ,186 ,182 ,187 ,186 ,184 ,185 ,197 ,124 ,84 ,174 ,185 ,150 ,129 ,143 ,135 ,115 ,102 ,111 ,124 ,112 ,109 ,132 ,146 ,135 ,149 ,148 ,143 ,163 ,156 ,159 ,150 ,139 ,128 ,116 ,125 ,133 ,109 ,130 ,147 ,130 ,121 ,105 ,108 ,95 ,108 ,102 ,67 ,171 ,193 ,183 ,184
        };

        try {
            tflite = new Interpreter(loadModelFile());
            System.out.println("Model loaded Successfully");
            System.out.println(tflite);

        }catch (Exception ex){
            ex.printStackTrace();
        }
        float [][][][] resizedarray = new float[1][48][48][1];
        int index = 0;
        for(int i = 0 ; i<48 ; i++)
        {
            for(int j = 0 ; j<48 ; j++)
            {
                resizedarray[0][i][j][0] = image[index++];
            }
        }
        System.out.println(Arrays.toString(resizedarray));
        float[][] prediction = new float[1][7];
//        tflite.run(resizedarray,prediction);
        tflite.run(resizedarray,prediction);

    }

    private void passImageToTFModel(TensorImage tensorImage)
    {

        Log.d(String.valueOf(tensorImage.getHeight()),"Height");
        Log.d(String.valueOf(tensorImage.getWidth()),"Width");
        TensorBuffer probabilityBuffer = TensorBuffer.createDynamic(DataType.FLOAT32);
        float[][] prediction = new float[1][7];
        float image [] = tensorImage.getTensorBuffer().getFloatArray();
        float [][][][] resizedarray = new float[1][48][48][1];
        int index = 0;
        for(int i = 0 ; i<48 ; i++)
        {
            for(int j = 0 ; j<48 ; j++)
            {
                resizedarray[0][i][j][0] = image[index++];
            }
        }
        tflite.run(resizedarray,prediction);
        Log.d(String.valueOf(probabilityBuffer),"Probability");

        int maxIndex = 0;
        float max = prediction[0][0];
        for(int i = 0 ; i<prediction[0].length ; i++)
        {
            if(prediction[0][i]>max)
            {
                max = prediction[0][i];
                maxIndex = i;
            }
        }
        System.out.println("Prediction Class\t" + emotions[maxIndex]);

    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor=getContext().getAssets().openFd("sequential.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset=fileDescriptor.getStartOffset();
        long declareLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declareLength);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        startBackgroundThread();

        if(textureViewFront.isAvailable()){
            openCameraFront(textureViewFront.getWidth(), textureViewFront.getHeight());
        }
        else{
            textureViewFront.setSurfaceTextureListener(surfaceTextListenerFront);
        }
        if(textureViewRear.isAvailable()){
            openCameraRear(textureViewRear.getWidth(), textureViewRear.getHeight());
        }
        else{
            textureViewRear.setSurfaceTextureListener(surfaceTextListenerRear);
        }
    }

    @Override
    public void onPause() {
        closeCameraFront();
        closeCameraRear();
        stopBackgroundThread();
        super.onPause();
    }

    private void openCameraFront(int width, int height)
    {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
        requestCameraPermission();
        return;
    }
        setUpCameraOutputsFront(width, height);
        configureTransformFront(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);


        try {
            if (!cameraOpenCloseLockFront.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraIdFront, stateCallbackFront, backgroundHandlerRear);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

    private void openCameraRear(int width, int height)
    {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputsRear(width, height);
        configureTransformRear(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!cameraOpenCloseLockRear.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraIdRear, stateCallbackRear, backgroundHandlerRear);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void setUpCameraOutputsFront(int width, int height) {

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try{
            for(String cameraId : manager.getCameraIdList())
            {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                frontCameraCharacteristics = cameraCharacteristics;
                Integer cameraDirection = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                if(cameraDirection != null && cameraDirection == cameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                StreamConfigurationMap map = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size aspectRatio = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByViewAspectRatio(textureViewFront.getHeight(), textureViewFront.getWidth()));

                imageReaderFront = ImageReader.newInstance(aspectRatio.getWidth(), aspectRatio.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);

//                Log.d(TAG, "setUpCameraOutputsFront: " + latestImage.getHeight());
                imageReaderFront.setOnImageAvailableListener(
                        onImageAvailableListenerFront, backgroundHandlerFront);

                Log.d(TAG,"selected aspect ratio"+aspectRatio.getHeight() + "x" + aspectRatio.getWidth() + ":" +aspectRatio.getHeight()/aspectRatio.getWidth());

                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                sensorOrientationFront = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                boolean swapdimensions = areDimensionsSwappedFront(displayRotation);
                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swapdimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                previewSizeFront = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, aspectRatio);

                this.cameraIdFront = cameraId;
                return;
            }

        }
        catch (CameraAccessException e) {
            
            Log.e(TAG, e.toString());
        }
        catch(NullPointerException e) {

            ErrorDialog.newInstance("This device doesn't support camera2 API").show(getChildFragmentManager(),FRAGMENT_DIALOG);
        }
    }

    private void closeCameraFront(){

        try{
            if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.N)
            {
                captureSessionFront.stopRepeating();
                captureSessionFront.abortCaptures();
            }
            cameraOpenCloseLockFront.acquire();
            captureSessionFront.close();
            captureSessionFront = null;
            cameraDeviceFront.close();
            cameraDeviceFront = null;
            imageReaderFront.close();
            imageReaderFront = null;
        }
        catch(InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock front camera closing.",e);
        }
        catch(CameraAccessException e){
            e.printStackTrace();
        }
        finally{
            cameraOpenCloseLockFront.release();
        }
    }

    private void closeCameraRear(){

        try{
            if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.N)
            {
                captureSessionRear.stopRepeating();
                captureSessionRear.abortCaptures();
            }
            cameraOpenCloseLockRear.acquire();
            captureSessionRear.close();
            captureSessionRear = null;
            cameraDeviceRear.close();
            cameraDeviceFront = null;
            imageReaderRear.close();
            imageReaderRear = null;
        }
        catch(InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock front camera closing.",e);

        }
        catch(CameraAccessException e) {
            e.printStackTrace();

        }
        finally{
            cameraOpenCloseLockFront.release();
        }
    }

    private void setUpCameraOutputsRear(int width, int height) {

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try{
            for(String cameraId : manager.getCameraIdList())
            {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                rearCameraCharacteristics = cameraCharacteristics;
                Integer cameraDirection = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                if(cameraDirection != null && cameraDirection == cameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                Size aspectRatio = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByViewAspectRatio(textureViewRear.getHeight(), textureViewRear.getWidth()));
                imageReaderRear = ImageReader.newInstance(aspectRatio.getWidth(), aspectRatio.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                imageReaderRear.setOnImageAvailableListener(
                        onImageAvailableListenerRear, backgroundHandlerRear);

                Log.d(TAG,"selected aspect ratio"+aspectRatio.getHeight() + "x" + aspectRatio.getWidth() + ":" +aspectRatio.getHeight()/aspectRatio.getWidth());

                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                sensorOrientationRear = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                boolean swapdimensions = areDimensionsSwappedRear(displayRotation);
                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swapdimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                previewSizeRear = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, aspectRatio);

                this.cameraIdRear= cameraId;
                return;
            }

        }
        catch (CameraAccessException e) {

            Log.e(TAG, e.toString());
        }
        catch(NullPointerException e) {

           ErrorDialog.newInstance("This device doesn't support camera2 API").show(getChildFragmentManager(),FRAGMENT_DIALOG);
        }
    }

    private void configureTransformFront(int viewWidth, int viewHeight)
    {
        Activity activity = getActivity();
        if (null == textureViewFront || null == previewSizeFront || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, (float)viewWidth, (float)viewHeight);
        RectF bufferRect = new RectF(0, 0, (float)previewSizeFront.getHeight(), (float)previewSizeFront.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSizeFront.getHeight(),
                    (float) viewWidth / previewSizeFront.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureViewFront.setTransform(matrix);
    }

    private void configureTransformRear(int viewWidth, int viewHeight)
    {
        Activity activity = getActivity();
        if (null == textureViewRear || null == previewRequestRear || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, (float)viewWidth, (float)viewHeight);
        RectF bufferRect = new RectF(0, 0, (float)previewSizeRear.getHeight(), (float)previewSizeRear.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSizeRear.getHeight(),
                    (float) viewWidth / previewSizeRear.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureViewRear.setTransform(matrix);
    }

    private boolean areDimensionsSwappedFront(int displayRotation)
    {
        boolean swappedDimensions = false;

        switch(displayRotation){

            case Surface.ROTATION_0:
                if (sensorOrientationFront == 90 || sensorOrientationFront == 270) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_180:
                if (sensorOrientationFront == 90 || sensorOrientationFront == 270) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_90:
                if (sensorOrientationFront == 0 || sensorOrientationFront == 180) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_270:
                if (sensorOrientationFront == 0 || sensorOrientationFront == 180) {
                    swappedDimensions = true;
                }

             default:
                Log.e(TAG, "Display rotation is invalid: $displayRotation");
        }

        return swappedDimensions;
    }

    private boolean areDimensionsSwappedRear(int displayRotation)
    {
        boolean swappedDimensions = false;

        switch(displayRotation){

            case Surface.ROTATION_0:
                if (sensorOrientationFront == 90 || sensorOrientationFront == 270) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_180:
                if (sensorOrientationFront == 90 || sensorOrientationFront == 270) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_90:
                if (sensorOrientationFront == 0 || sensorOrientationFront == 180) {
                    swappedDimensions = true;
                }

            case Surface.ROTATION_270:
                if (sensorOrientationFront == 0 || sensorOrientationFront == 180) {
                    swappedDimensions = true;
                }

            default:
                Log.e(TAG, "Display rotation is invalid: $displayRotation");
        }

        return swappedDimensions;
    }

    private void startBackgroundThread()
    {
        backgroundThreadFront = new HandlerThread("CameraBackgroundFront");
        backgroundThreadRear = new HandlerThread("CameraBackgroundRear");
        backgroundThreadFront.start();
        backgroundThreadRear.start();
        backgroundHandlerFront = new Handler(backgroundThreadFront.getLooper());
        backgroundHandlerRear = new Handler(backgroundThreadRear.getLooper());
    }

    private void stopBackgroundThread(){
        backgroundThreadFront.quitSafely();
        backgroundThreadRear.quitSafely();

        try{
            backgroundThreadFront.join();
            backgroundThreadFront = null;
            backgroundHandlerFront = null;

            backgroundThreadRear.join();
            backgroundThreadRear = null;
            backgroundHandlerRear = null;
        }
        catch(InterruptedException e){
            Log.e(TAG,e.toString());
        }

    }

    private void createCameraPreviewSessionFront(){

        try {
            SurfaceTexture texture = textureViewFront.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSizeFront.getWidth(), previewSizeFront.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilderFront
                    = cameraDeviceFront.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            previewRequestBuilderFront.set (CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            previewRequestBuilderFront.set (CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            previewRequestBuilderFront.addTarget (imageReaderFront.getSurface ());
            previewRequestBuilderFront.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDeviceFront.createCaptureSession(Arrays.asList(surface, imageReaderFront.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDeviceFront) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSessionFront = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilderFront.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.

                                //Set fps range from 1 to 5
                                Range<Integer> fpsRange = new Range<>(1,5);

                                previewRequestBuilderFront.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);

                                // Finally, we start displaying the camera preview.
                                previewRequestFront = previewRequestBuilderFront.build();
                                captureSessionFront.setRepeatingRequest(previewRequestFront,
                                        captureCallback, backgroundHandlerFront);


                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                           Log.d(TAG, "CaptureSession Failed");
                        }
                    }, null
            );


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void createCameraPreviewSessionRear(){

        try {
            SurfaceTexture texture = textureViewRear.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSizeRear.getWidth(), previewSizeRear.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilderRear
                    = cameraDeviceRear.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilderRear.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDeviceRear.createCaptureSession(Arrays.asList(surface, imageReaderRear.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDeviceRear) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSessionRear = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilderRear.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.

                                Range<Integer> fpsRange = new Range<>(1,5);

                                previewRequestBuilderRear.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                                // Finally, we start displaying the camera preview.
                                previewRequestRear = previewRequestBuilderRear.build();
                                captureSessionRear.setRepeatingRequest(previewRequestRear,
                                        captureCallback, backgroundHandlerRear);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "CaptureSession Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByViewAspectRatio(textureViewHeight,textureViewWidth));
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByViewAspectRatio(textureViewHeight, textureViewWidth));
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    1);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    public static FirstFragment getFragment() {
        FirstFragment fragment = new FirstFragment();
        return fragment;
    }

    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }
}
