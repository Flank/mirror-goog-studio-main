package com.example.app;

import android.app.Activity;
import android.os.Bundle;
import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.PostJavacLib;

public class AppActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // use a class whose bytecode was generated.
        App app = new App("app");
        // also from the library, as pre-javac
        Lib lib = new Lib("lib");
        // and post-javac
        PostJavacLib lib2 = new PostJavacLib("lib");
    }
}
