package com.example.simpleapp;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("%TEXT");
        setContentView(R.layout.activity_main);
    }
}
