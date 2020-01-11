/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.FirebaseMessagingDetector.MISSING_TOKEN_REFRESH;
import static com.android.tools.lint.checks.infrastructure.TestFiles.java;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.checks.infrastructure.TestLintTask;
import org.junit.Test;

public class FirebaseMessagingDetectorTest {
    @NonNull
    protected TestLintTask lint() {
        TestLintTask task = TestLintTask.lint();
        task.sdkHome(AbstractCheckTest.getSdk());
        return task;
    }

    @SuppressWarnings("all") // Sample code
    final TestFile mFirebaseMessageService =
            java(
                    ""
                            + "package com.google.firebase.messaging;\n"
                            + "public class FirebaseMessagingService {\n"
                            + "  public void onNewToken(String token) {\n"
                            + "  }\n"
                            + "}");

    @Test
    public void testMissing() {
        lint().files(
                        java(
                                ""
                                        + "package com.google.firebase.samples.messaging.advanced.services;\n"
                                        + "\n"
                                        + "import com.google.firebase.messaging.FirebaseMessagingService;\n"
                                        + "\n"
                                        + "public class MessagingService extends FirebaseMessagingService {\n"
                                        + "}"),
                        mFirebaseMessageService)
                .issues(MISSING_TOKEN_REFRESH)
                .run()
                .expect(
                        ""
                                + "src/com/google/firebase/samples/messaging/advanced/services/MessagingService.java:5: Warning: Apps that use Firebase Cloud Messaging should implement onNewToken() in order to observe token changes [MissingFirebaseInstanceTokenRefresh]\n"
                                + "public class MessagingService extends FirebaseMessagingService {\n"
                                + "             ~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }

    @Test
    public void testOk() {
        lint().files(
                        java(
                                ""
                                        + "package com.google.firebase.samples.messaging.advanced.services;\n"
                                        + "\n"
                                        + "import android.util.Log;\n"
                                        + "import com.google.firebase.messaging.FirebaseMessagingService;\n"
                                        + "\n"
                                        + "public class MessagingService extends FirebaseMessagingService {\n"
                                        + "  public void onNewToken(String token) {\n"
                                        + "    Log.i(TAG, \"Received event: on-new-token: \" + token);\n"
                                        + "  }\n"
                                        + "}"),
                        mFirebaseMessageService)
                .issues(MISSING_TOKEN_REFRESH)
                .run()
                .expectClean();
    }

    @Test
    public void testSuppress() {
        lint().files(
                        java(
                                ""
                                        + "package com.google.firebase.samples.messaging.advanced.services;\n"
                                        + "\n"
                                        + "import com.google.firebase.messaging.FirebaseMessagingService;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"MissingFirebaseInstanceTokenRefresh\")\n"
                                        + "public class MessagingService extends FirebaseMessagingService {\n"
                                        + "}"),
                        mFirebaseMessageService)
                .issues(MISSING_TOKEN_REFRESH)
                .run()
                .expectClean();
    }
}
