package com.akshay.faceexpressiontest.activity;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import com.akshay.faceexpressiontest.R;
import androidx.appcompat.app.AppCompatActivity;
import com.akshay.faceexpressiontest.fragment.FirstFragment;

import org.opencv.android.JavaCamera2View;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import org.tensorflow.lite.Interpreter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MainActivity extends AppCompatActivity {

    Interpreter interpreter;
    private static final String MODEL_file = "file:///assets/tf_model.pb";
    private static final String INPUT_NODE = "conv2d_1_input_1:0";
    private static final String OUTPUT_NODE = "activation_5_1/Softmax:0";
    private static final long[] input_shape = {48,48,1};
    private TensorFlowInferenceInterface inferenceInterface;
    private boolean doneLoadingModel;
    JavaCamera2View cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, FirstFragment.getFragment())
                    .commit();
        }
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_file);
        System.out.println("Model Loaded Successfully");


    }

    private void loadModel(){

        try{
            inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_file);
            doneLoadingModel = true;
        }
        catch(IllegalArgumentException e){

            doneLoadingModel = false;
            e.printStackTrace();
        }


    }

}