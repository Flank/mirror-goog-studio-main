package com.example.android.kotlin

import android.app.Activity
import java.util.logging.Logger.getLogger

class MainActivity : Activity() {
    companion object {
        fun stubFuncForTestingCodeCoverage() {
            getLogger("MainActivity").info("stubFuncForTestingCodeCoverage()")
        }
    }
}
