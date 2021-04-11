package com.akshay.faceexpressiontest.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import com.akshay.faceexpressiontest.util.CompareSizesByViewAspectRatio;
import com.darwin.viola.still.FaceDetectionListener;
import com.darwin.viola.still.Viola;
import com.darwin.viola.still.model.CropAlgorithm;
import com.darwin.viola.still.model.FaceDetectionError;
import com.darwin.viola.still.model.FaceOptions;
import com.darwin.viola.still.model.Result;
import org.jetbrains.annotations.NotNull;
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
    TensorImage tensorImageFront, tensorImageRear;
    Viola violaFront;
    Viola violaRear;
    Button buttonStart, buttonStop;
    Bitmap cameraBitmapImageFront, cameraBitmapImageRear ;
    FaceDetectionListener listenerFront;
    FaceDetectionListener listenerRear;
    FaceOptions faceOptionsFront, faceOptionsRear;
    private MediaRecorder recorder = null;
    TextView textViewFront, textViewRear;
    ByteBuffer bufferFront, bufferRear;
    byte[] bytesFront;
    byte[] bytesRear;
    AudioRecorder audioRecorder;
    ImageProcessor imageProcessor =
            new ImageProcessor.Builder()
                    .add(new ResizeOp(48, 48, ResizeOp.ResizeMethod.BILINEAR))
                    .build();


    private String emotions[] = {"Angry", "Disgust","Fear","Surprise","Sad","Happy", "Neutral"};

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
    private Image latestImageFront,latestImageRear;
    Matrix rotationMatrix;



    private final ImageReader.OnImageAvailableListener onImageAvailableListenerFront
            = new ImageReader.OnImageAvailableListener() {
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onImageAvailable(ImageReader reader) {


            Log.d(TAG, "onImageAvailableListener Front Called");
            latestImageFront = reader.acquireLatestImage();
            //Log.d(TAG, "onImageAvailable: height " + latestImageFront.getHeight() + " width " + latestImageFront.getWidth());
            bufferFront = latestImageFront.getPlanes()[0].getBuffer();
            bytesFront = new byte[bufferFront.capacity()];
            bufferFront.get(bytesFront);
            cameraBitmapImageFront = BitmapFactory.decodeByteArray(bytesFront, 0, bytesFront.length, null);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            violaFront.detectFace(cameraBitmapImageFront, faceOptionsFront);
            latestImageFront.close();

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
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onImageAvailable(ImageReader reader) {

            Log.d(TAG, "onImageAvailableListener Rear Called");
            latestImageRear = reader.acquireLatestImage();
            //Log.d(TAG, "onImageAvailable: height " + latestImageFront.getHeight() + " width " + latestImageFront.getWidth());
            bufferRear = latestImageRear.getPlanes()[0].getBuffer();
            bytesRear = new byte[bufferRear.capacity()];
            bufferRear.get(bytesRear);
            cameraBitmapImageRear = BitmapFactory.decodeByteArray(bytesRear, 0, bytesRear.length, null);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            violaRear.detectFace(cameraBitmapImageRear, faceOptionsRear);
            latestImageRear.close();

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
            cameraOpenCloseLockRear.release();
            cameraDeviceRear = cameraDevice;
            createCameraPreviewSessionRear();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLockRear.release();
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
        rotationMatrix = new Matrix();
        rotationMatrix.postRotate(-90);
        tensorImageRear = new TensorImage(DataType.FLOAT32);
        tensorImageFront = new TensorImage(DataType.FLOAT32);
        textViewFront = view.findViewById(R.id.textview1);
        textViewRear = view.findViewById(R.id.textview2);
        buttonStart = view.findViewById(R.id.startButton);
        buttonStop = view.findViewById(R.id.stopButton);
        audioRecorder = new AudioRecorder("audiofile");

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    audioRecorder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    audioRecorder.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

        listenerFront = new FaceDetectionListener() {
            @Override
            public void onFaceDetected(@NotNull Result result) {
                Log.d("onFaceDetected", "FacePose "+result.getFacePortraits().get(0));
                Log.d("onFaceDetected", "FaceHeight "+result.getFacePortraits().get(0).getFace().getHeight() + "Width "+result.getFacePortraits().get(0).getFace().getWidth());
                cameraBitmapImageFront = result.getFacePortraits().get(0).getFace();
                cameraBitmapImageFront = toGrayscale(cameraBitmapImageFront);
                cameraBitmapImageFront = Bitmap.createScaledBitmap(cameraBitmapImageFront, cameraBitmapImageFront.getWidth(), cameraBitmapImageFront.getHeight(), true);
                cameraBitmapImageFront = Bitmap.createBitmap(cameraBitmapImageFront, 0, 0, cameraBitmapImageFront.getWidth(), cameraBitmapImageFront.getHeight(), rotationMatrix, true);
                tensorImageFront.load(cameraBitmapImageFront);
                tensorImageFront = imageProcessor.process(tensorImageFront);
                Log.d(TAG, "bitmap: height " + tensorImageFront.getHeight());
                //System.out.println("Tensor Image Type Size"+Arrays.toString(tensorImageFront.getTensorBuffer().getFloatArray()));
                passImageToTFModel(tensorImageFront, textViewFront);
                //latestImage.close();
            }

            @Override
            public void onFaceDetectionFailed(@NotNull FaceDetectionError error, @NotNull String message) {
                System.out.println("This is Message"+message);
            }
        };

        listenerRear = new FaceDetectionListener() {
            @Override
            public void onFaceDetected(@NotNull Result result) {
                Log.d("onFaceDetected", "FacePose Rear "+result.getFacePortraits().get(0));
                Log.d("onFaceDetected", "FaceHeight Rear "+result.getFacePortraits().get(0).getFace().getHeight() + "Width "+result.getFacePortraits().get(0).getFace().getWidth());
                cameraBitmapImageRear = result.getFacePortraits().get(0).getFace();
                cameraBitmapImageRear = toGrayscale(cameraBitmapImageRear);
                cameraBitmapImageRear = Bitmap.createScaledBitmap(cameraBitmapImageRear, cameraBitmapImageRear.getWidth(), cameraBitmapImageRear.getHeight(), true);
                cameraBitmapImageRear = Bitmap.createBitmap(cameraBitmapImageRear, 0, 0, cameraBitmapImageRear.getWidth(), cameraBitmapImageRear.getHeight(), rotationMatrix, true);
                tensorImageRear.load(cameraBitmapImageRear);
                tensorImageRear = imageProcessor.process(tensorImageRear);
                Log.d(TAG, "bitmap: height " + tensorImageRear.getHeight());
                //System.out.println("Tensor Image Type Size"+Arrays.toString(tensorImageRear.getTensorBuffer().getFloatArray()));
                passImageToTFModel(tensorImageRear,textViewRear);
                //latestImage.close();
            }

            @Override
            public void onFaceDetectionFailed(@NotNull FaceDetectionError error, @NotNull String message) {
                System.out.println("This is Message"+message);
            }
        };

        violaFront = new Viola(listenerFront);
        violaRear = new Viola(listenerRear);

        faceOptionsFront = new FaceOptions.Builder()
                .enableProminentFaceDetection()
                .enableDebug()
                .cropAlgorithm(CropAlgorithm.SQUARE)
                .build();

        faceOptionsRear = new FaceOptions.Builder()
                .enableProminentFaceDetection()
                .enableDebug()
                .cropAlgorithm(CropAlgorithm.SQUARE)
                .build();

        try {
            tflite = new Interpreter(loadModelFile());
            System.out.println("Model loaded Successfully");
            System.out.println(tflite);

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void passImageToTFModel(TensorImage tensorImage, TextView emotionTextView)
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
        if(emotionTextView.getId() == textViewFront.getId()){

            textViewFront.setText(emotions[maxIndex]);
            Log.d("passImageToTFModel","Emotion Front " + emotions[maxIndex]);
        }
        else{
            textViewRear.setText(emotions[maxIndex]);
            Log.d("passImageToTFModel","Emotion Rear " + emotions[maxIndex]);
        }

    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor=getContext().getAssets().openFd("depth_wise.tflite");
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
            manager.openCamera(cameraIdFront, stateCallbackFront, backgroundHandlerFront);
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
            cameraDeviceRear = null;
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
                                Range<Integer> fpsRange = new Range<>(1,1);
                                //previewRequestBuilderFront.set(CaptureRequest.JPEG_ORIENTATION, 180);

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
//            previewRequestBuilderRear
//                    = cameraDeviceRear.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            previewRequestBuilderRear.addTarget(surface);

            previewRequestBuilderRear
                    = cameraDeviceRear.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            previewRequestBuilderRear.set (CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            previewRequestBuilderRear.set (CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            previewRequestBuilderRear.addTarget (imageReaderRear.getSurface ());
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

                                Range<Integer> fpsRange = new Range<>(1,1);

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

    public class AudioRecorder {

        final MediaRecorder recorder = new MediaRecorder();
        final String path;

        /**
         * Creates a new audio recording at the given path (relative to root of SD card).
         */
        public AudioRecorder(String path) {
            this.path = sanitizePath(path);
        }

        private String sanitizePath(String path) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.contains(".")) {
                path += ".3gp";
            }
            return Environment.getExternalStorageDirectory().getAbsolutePath() + path;
        }

        /**
         * Starts a new recording.
         */

        public void start() throws IOException {
            String state = android.os.Environment.getExternalStorageState();
            if(!state.equals(android.os.Environment.MEDIA_MOUNTED))  {
                throw new IOException("SD Card is not mounted.  It is " + state + ".");
            }

            // make sure the directory we plan to store the recording in exists
            File directory = new File(path).getParentFile();
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Path to file could not be created.");
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(path);
            recorder.prepare();
            recorder.start();
        }

        /**
         * Stops a recording that has been previously started.
         */
        public void stop() throws IOException {
            recorder.stop();
            recorder.release();
        }

    }


    public static FirstFragment getFragment() {
        FirstFragment fragment = new FirstFragment();
        return fragment;
    }

}