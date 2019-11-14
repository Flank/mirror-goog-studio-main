package com.android.prefabaartest

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import java.io.FileOutputStream
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {
    val cacert by lazy { cacheDir.resolve("cacert.pem") }

    fun extractCacert() {
        assets.open("cacert.pem").copyTo(FileOutputStream(cacert))
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!cacert.exists()) {
            extractCacert()
        }

        sample_text.text = stringFromJNI(cacert.path)
    }

    private external fun stringFromJNI(cacert: String): String

    companion object {
        init {
            System.loadLibrary("app")
        }
    }
}
