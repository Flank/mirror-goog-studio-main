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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector

class BinderGetCallingInMainThreadDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return BinderGetCallingInMainThreadDetector()
    }

    private val misuseServiceJavaExample: TestFile = java(
        """
                package test.pkg;

                import android.app.Service;
                import android.content.Intent;
                import android.os.IBinder;
                import android.os.Binder;

                public class MyService extends Service {
                    @Override
                    public IBinder onBind(Intent intent) {
                        Binder.getCallingUid();
                        Binder.getCallingPid();
                        return null;
                    }
                }
        """
    ).indented()

    private val misuseServiceKotlinExample: TestFile = kotlin(
        """
                package test.pkg

                import android.app.Service
                import android.content.Intent
                import android.os.Binder
                import android.os.IBinder

                class MyService : Service() {
                    override fun onBind(intent: Intent): IBinder {
                        Binder.getCallingUid()
                        Binder.getCallingPid()
                    }
                }
        """
    ).indented()

    private val misuseFragmentJavaExample: TestFile = java(
        """
                package test.pkg;

                import android.app.Fragment;
                import android.content.Intent;
                import android.os.IBinder;
                import android.os.Binder;
                import android.os.Bundle;

                public class FirstFragment extends Fragment  {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        Binder.getCallingUid();
                        Binder.getCallingPid();
                    }
                }
        """
    ).indented()

    private val misuseFragmentKotlinExample: TestFile = kotlin(
        """
                package test.pkg

                import android.os.Binder
                import android.os.Bundle
                import android.view.LayoutInflater
                import android.view.ViewGroup
                import android.app.Fragment

                class FirstFragment : Fragment() {

                    override fun onCreateView(
                            inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?
                    ): View? {
                        Binder.getCallingUid()
                        Binder.getCallingPid()
                    }
                }
        """
    ).indented()

    private val misuseActivityJavaExample: TestFile = java(
        """
                package test.pkg;

                import android.app.Activity;
                import android.content.Intent;
                import android.os.IBinder;
                import android.os.Binder;
                import android.os.Bundle;

                public class MainActivity extends Activity {
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        Binder.getCallingUid();
                        Binder.getCallingPid();
                    }
                }
        """
    ).indented()

    private val misuseActivityKotlinExample: TestFile = kotlin(
        """
                package test.pkg

                import android.os.Binder
                import android.os.Bundle
                import android.app.Activity

                class MainActivity : Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        Binder.getCallingPid()
                        Binder.getCallingUid()
                    }
                }
        """
    ).indented()

    private val correctUsageJavaExample: TestFile = java(
        """
                package test.pkg;

                import android.os.IBinder;
                import android.os.Binder;
                import android.os.Bundle;

                public class Stub extends Binder  {
                    @Override
                    public boolean onTransact(int code, Parcel data, Parcel reply, int flags){
                        Binder.getCallingUid();
                        Binder.getCallingPid();
                        return super.onTransact(code, data, reply, flags);
                    }
                }
        """
    ).indented()

    private val correctUsageKotlinExample: TestFile = kotlin(
        """
                package test.pkg

                import android.os.Binder
                import android.os.Bundle

                class Stub : Binder() {
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        Binder.getCallingPid()
                        Binder.getCallingUid()
                        return
                    }
                }
        """
    ).indented()

    fun testDocumentationExample() {
        lint().files(misuseServiceKotlinExample).run().expect(
            """
            src/test/pkg/MyService.kt:10: Error: Binder.getCallingUid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingUid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyService.kt:11: Error: Binder.getCallingPid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingPid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    fun testComprehensiveExamples() {
        lint().files(
            misuseServiceJavaExample,
            misuseServiceKotlinExample,
            misuseFragmentJavaExample,
            misuseFragmentKotlinExample,
            misuseActivityJavaExample,
            misuseActivityKotlinExample,
            correctUsageJavaExample,
            correctUsageKotlinExample,
        ).run().expect(
            """
            src/test/pkg/FirstFragment.java:12: Error: Binder.getCallingUid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingUid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/FirstFragment.java:13: Error: Binder.getCallingPid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingPid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/FirstFragment.kt:15: Error: Binder.getCallingUid() should not be used inside onCreateView() [BinderGetCallingInMainThread]
                    Binder.getCallingUid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/FirstFragment.kt:16: Error: Binder.getCallingPid() should not be used inside onCreateView() [BinderGetCallingInMainThread]
                    Binder.getCallingPid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.java:12: Error: Binder.getCallingUid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingUid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.java:13: Error: Binder.getCallingPid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingPid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:9: Error: Binder.getCallingPid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingPid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MainActivity.kt:10: Error: Binder.getCallingUid() should not be used inside onCreate() [BinderGetCallingInMainThread]
                    Binder.getCallingUid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyService.java:11: Error: Binder.getCallingUid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingUid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyService.java:12: Error: Binder.getCallingPid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingPid();
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyService.kt:10: Error: Binder.getCallingUid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingUid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/MyService.kt:11: Error: Binder.getCallingPid() should not be used inside onBind() [BinderGetCallingInMainThread]
                    Binder.getCallingPid()
                    ~~~~~~~~~~~~~~~~~~~~~~
            12 errors, 0 warnings
            """
        )
    }
}
