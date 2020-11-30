package com.akshay.faceexpressiontest.activity;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import com.akshay.faceexpressiontest.R;
import androidx.appcompat.app.AppCompatActivity;
import com.akshay.faceexpressiontest.fragment.FirstFragment;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class MainActivity extends AppCompatActivity {

    Interpreter tflite;
    private static final String MODEL_file = "file:///assets/tf_model.pb";
    private static final String INPUT_NODE = "conv2d_1_input_1:0";
    private static final String OUTPUT_NODE = "activation_5_1/Softmax:0";
    private static final long[] input_shape = {48,48,1};
    private TensorFlowInferenceInterface inferenceInterface;
    private boolean doneLoadingModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, FirstFragment.getFragment())
                    .commit();
        }
        try {
            tflite = new Interpreter(loadModelFile());
            System.out.println("Model loaded Successfully");
            System.out.println(tflite);

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor=this.getAssets().openFd("model.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset=fileDescriptor.getStartOffset();
        long declareLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declareLength);
    }

}

