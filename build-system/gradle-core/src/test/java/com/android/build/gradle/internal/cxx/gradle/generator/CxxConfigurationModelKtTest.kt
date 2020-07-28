package com.android.build.gradle.internal.cxx.gradle.generator

import org.junit.Test


internal class CxxConfigurationModelKtTest {
    @Test
    fun dontCheckInFlagSetToTrue() {
        if (!ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION) {
            error("ENABLE_CHECK_CONFIG_TIME_CONSTRUCTION should be true for test runs")
        }
    }
}