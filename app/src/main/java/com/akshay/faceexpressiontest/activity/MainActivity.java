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
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;


public class MainActivity extends AppCompatActivity {



    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, FirstFragment.getFragment())
                    .commit();
        }

    }



}