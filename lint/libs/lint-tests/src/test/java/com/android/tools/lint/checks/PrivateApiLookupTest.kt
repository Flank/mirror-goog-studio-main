/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.BaseLintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Severity
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter

public class PrivateApiLookupTest : AbstractCheckTest() {
    private var cacheDir = File(BaseLintDetectorTest.getTempDir(), "lint-private-test-cache")
    private val db = PrivateApiLookup.get(createClient())!!
    private val logBuffer = StringBuilder()

    override fun createClient(): TestLintClient {
        cacheDir.mkdirs()
        return LookupTestClient()
    }

    private inner class LookupTestClient : AbstractCheckTest.ToolsBaseTestLintClient() {
        override fun getCacheDir(name: String?, create: Boolean): File? {
            assertNotNull(cacheDir)
            if (create && !cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir
        }

        override fun log(
            severity: Severity,
            exception: Throwable?,
            format: String?,
            vararg args: Any
        ) {
            if (format != null) {
                logBuffer.append(String.format(format, *args))
                logBuffer.append('\n')
            }
            if (exception != null) {
                val writer = StringWriter()
                exception.printStackTrace(PrintWriter(writer))
                logBuffer.append(writer.toString())
                logBuffer.append('\n')
            }
        }

        override fun log(exception: Throwable?, format: String?, vararg args: Any) {
            log(Severity.WARNING, exception, format, *args)
        }
    }

    override fun getDetector(): Detector? {
        fail("This is not used in the PrivateApiLookup test")
        return null
    }

    fun testClassAndContainerEntries() {
        assertTrue(db.findClass("android.app.Activity") > 0)
    }

    fun testMethodEntries() {
        assertEquals(
            Restriction.MAYBE_MAX_O,
            db.getMethodRestriction(
                "android/app/Activity",
                "dispatchPictureInPictureModeChanged",
                "(ZLandroid/content/res/Configuration;)"
            )
        )
        assertEquals(
            Restriction.MAYBE_MAX_P,
            db.getMethodRestriction(
                "android.animation.LayoutTransition",
                "cancel",
                "()"
            )
        )
        assertEquals(
            Restriction.DENY,
            db.getMethodRestriction(
                "android/app/Notification",
                "getContextualActions",
                "()Ljava/util/List;"
            )
        )
        assertEquals(
            Restriction.UNKNOWN,
            db.getMethodRestriction(
                "android/graphics/drawable/BitmapDrawable",
                "setTargetDensity",
                "(Landroid/util/DisplayMetrics;)V"
            )
        )
    }

    fun testFieldEntries() {
        assertEquals(
            Restriction.DENY,
            db.getFieldRestriction(
                "android/Manifest\$permission",
                "INSTALL_EXISTING_PACKAGES"
            )
        )
        assertEquals(
            Restriction.MAYBE,
            db.getFieldRestriction(
                "android/content/ContentProviderOperation",
                "mUri"
            )
        )
        assertEquals(
            Restriction.MAYBE_MAX_P,
            db.getFieldRestriction(
                "android.animation.ValueAnimator",
                "sDurationScale"
            )
        )
    }

    fun testCorruptedCacheHandling() {
        var lookup: PrivateApiLookup

        // Real cache:
        cacheDir = createClient().getCacheDir(null, true)!!
        logBuffer.setLength(0)
        lookup = PrivateApiLookup.Companion.get(LookupTestClient())!!
        assertEquals(
            Restriction.DENY,
            lookup.getFieldRestriction("android/Manifest\$permission", "INSTALL_EXISTING_PACKAGES")
        )
        assertEquals("", logBuffer.toString()) // No warnings

        // Custom cache dir: should also work
        cacheDir = File(BaseLintDetectorTest.getTempDir(), "test-cache")
        cacheDir.mkdirs()
        logBuffer.setLength(0)
        lookup = PrivateApiLookup.Companion.get(LookupTestClient())!!
        assertEquals(
            Restriction.DENY,
            lookup.getFieldRestriction("android/Manifest\$permission", "INSTALL_EXISTING_PACKAGES")
        )
        assertEquals("", logBuffer.toString()) // No warnings

        // Now truncate cache file
        val cacheFile =
            cacheDir.listFiles { it -> it.name.startsWith("private-apis") }[0]
        logBuffer.setLength(0)
        assertTrue(cacheFile.exists())
        var raf = RandomAccessFile(cacheFile, "rw")
        // Truncate file in half
        raf.setLength(100) // Broken header
        raf.close()
        PrivateApiLookup.get(LookupTestClient())
        val message = logBuffer.toString()
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"))
        assertTrue(message.contains(cacheDir.path))

        logBuffer.setLength(0)
        assertTrue(cacheFile.exists())
        raf = RandomAccessFile(cacheFile, "rw")
        // Truncate file in half in the data portion
        raf.setLength(raf.length() / 2)
        raf.close()
        lookup = PrivateApiLookup.Companion.get(LookupTestClient())!!
        // This data is now truncated: lookup returns the wrong size.
        lookup.getFieldRestriction("android/Manifest\$permission", "INSTALL_EXISTING_PACKAGES")
        assertTrue(message.contains("Please delete the file and restart the IDE/lint:"))
        assertTrue(message.contains(cacheDir.path))

        logBuffer.setLength(0)
        assertTrue(cacheFile.exists())
        raf = RandomAccessFile(cacheFile, "rw")
        // Truncate file to 0 bytes
        raf.setLength(0)
        raf.close()
        lookup = PrivateApiLookup.Companion.get(LookupTestClient())!!
        assertEquals(
            Restriction.DENY,
            lookup.getFieldRestriction("android/Manifest\$permission", "INSTALL_EXISTING_PACKAGES")
        )
        assertEquals("", logBuffer.toString()) // No warnings
    }
}
