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

package com.android.build.gradle.integration.common.truth;

import com.android.SdkConstants;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.io.File;
import java.io.IOException;

/**
 * Truth subject for native libraries.
 */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class NativeLibrarySubject extends Subject<NativeLibrarySubject, File> {

    public static Subject.Factory<NativeLibrarySubject, File> nativeLibraries() {
        return NativeLibrarySubject::new;
    }

    public NativeLibrarySubject(FailureMetadata failureMetadata, File subject) {
        super(failureMetadata, subject);
    }

    /**
     * Call 'file' in shell to determine if libraries is stripped of debug symbols.
     */
    public void isStripped() throws IOException, InterruptedException {
        // file command is not available on Windows.  Skipping this check.
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return;
        }

        Process p = getFileData();
        if (p.exitValue() != 0) {
            String err = new String(ByteStreams.toByteArray(p.getErrorStream()), Charsets.UTF_8);
            failWithRawMessage("Error executing \'file\'.\n" + err);
        }
        String output = new String(ByteStreams.toByteArray(p.getInputStream()), Charsets.UTF_8);
        if (output.contains("not stripped")) {
            failWithRawMessage(
                    "Not true that <%s> is stripped.  File information:\n%s\n",
                    actualAsString(), output);
        }
    }

    public void isNotStripped() throws IOException, InterruptedException {
        // file command is not available on Windows.  Skipping this check.
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            return;
        }

        Process p = getFileData();
        if (p.exitValue() != 0) {
            String err = new String(ByteStreams.toByteArray(p.getErrorStream()), Charsets.UTF_8);
            failWithRawMessage("Error executing \'file\'.\n" + err);
        }
        String output = new String(ByteStreams.toByteArray(p.getInputStream()), Charsets.UTF_8);
        if (!output.contains("not stripped")) {
            failWithRawMessage(
                    "Not true that <%s> is not stripped.  File information:\n%s\n",
                    actualAsString(), output);
        }
    }

    /**
     * Execute 'file' to obtain data about a file.
     */
    private Process getFileData() throws IOException, InterruptedException {
        Process p;
        p = Runtime.getRuntime().exec(new String[] {"file", actual().getAbsolutePath()});
        p.waitFor();
        return p;
    }
}
