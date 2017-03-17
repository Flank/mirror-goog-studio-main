package com.example.app;

import android.app.Activity;
import android.os.Bundle;
import com.example.bytecode.App;
import com.example.bytecode.Lib;

public class AppActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // use a class whose bytecode was generated.
        App app = new App("app");
        // also from the library.
        Lib lib = new Lib("lib");
    }
}
