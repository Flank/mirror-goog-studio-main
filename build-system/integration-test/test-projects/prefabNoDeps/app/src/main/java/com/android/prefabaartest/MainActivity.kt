package com.android.prefabaartest

import android.app.Activity

class MainActivity : Activity() {
    companion object {
        init {
            System.loadLibrary("app")
        }
    }
}
