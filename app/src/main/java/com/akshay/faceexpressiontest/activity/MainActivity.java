package com.akshay.faceexpressiontest.activity;

import android.os.Bundle;
import com.akshay.faceexpressiontest.R;
import androidx.appcompat.app.AppCompatActivity;
import com.akshay.faceexpressiontest.fragment.FirstFragment;

public class MainActivity extends AppCompatActivity {

    @Override
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