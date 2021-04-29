package com.android.prefabaartest

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import java.io.FileOutputStream
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {
    companion object {
        init {
            System.loadLibrary("app")
        }
    }
}
