/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
import com.android.tools.lint.checks.AndroidPlatformAnnotationsTestMode
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

/**
 * [AndroidPlatformAnnotations] are mostly tested via the
 * [AndroidPlatformAnnotationsTestMode], which reframes every single
 * annotation test into a platform annotation test by bytecode
 * transforming the annotations over to the platform space and expecting
 * the same output as with androidx.
 *
 * This test case adds some extra manual tests for specific scenarios
 * that have come up.
 */
@Suppress("LintDocExample")
class AndroidPlatformAnnotationsTest {
    private fun lint(): TestLintTask = TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())

    @Test
    fun testRequiresApi() {
        // Regression test for 214255490 comment 41: when using a platform version
        // of android.annotation, make sure we correctly compute the API level when
        // looking up the surrounding API level from annotations (and make sure
        // we do not report method returns)
        lint().files(
            java(
                """
                package android.annotation;
                import static java.lang.annotation.ElementType.*;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                import java.lang.annotation.*;
                @Documented
                @Retention(SOURCE)
                @Target({TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE})
                public @interface RequiresApi {
                    int value() default 1;
                    int api() default 1;
                }
                """
            ).indented(),
            java(
                """
                package android.os;
                public class Build {
                    public static class VERSION {
                        public static int SDK_INT;
                        public static String CODENAME;
                    }
                    public static class VERSION_CODES {
                        public static final int CUR_DEVELOPMENT = 10000;
                        public static final int S = 31;
                        public static final int S_V2 = 32;
                        public static final int T = CUR_DEVELOPMENT;
                    }
                }
                """
            ),
            java(
                """
                package android.provider;
                import android.annotation.RequiresApi;
                import android.os.Build;
                import com.android.modules.utils.build.SdkLevel;
                public class MediaProvider {
                    private String getExternalStorageProviderAuthority() {
                        if (SdkLevel.isAtLeastS()) {
                            return getExternalStorageProviderAuthorityFromDocumentsContract();
                        }
                        return null;
                    }
                    @RequiresApi(Build.VERSION_CODES.S)
                    private String getExternalStorageProviderAuthorityFromDocumentsContract() {
                        return null;
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import android.annotation.RequiresApi;
                import android.os.Build;
                @RequiresApi(Build.VERSION_CODES.S)
                public class Test {
                    public void test(android.provider.MediaProvider provider) {
                        provider.getExternalStorageProviderAuthorityFromDocumentsContract();
                    }
                }
                """
            ).indented(),
            java(
                """
                package com.android.modules.utils.build;
                import static android.os.Build.VERSION.CODENAME;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
                import androidx.annotation.ChecksSdkIntAtLeast;
                public final class SdkLevel {
                    private SdkLevel() {
                    }

                    @ChecksSdkIntAtLeast(api = 30 /* Build.VERSION_CODES.R */)
                    public static boolean isAtLeastR() {
                        throw UnsupportedOperationException(); //
                    }

                    @ChecksSdkIntAtLeast(api = 31 /* Build.VERSION_CODES.S */, codename = "S")
                    public static boolean isAtLeastS() {
                        throw UnsupportedOperationException(); //
                    }
                }
                """
            ),
            SUPPORT_ANNOTATIONS_JAR
        ).issues(ApiDetector.UNSUPPORTED).run().expectClean()
    }
}
