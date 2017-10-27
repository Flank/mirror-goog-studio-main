/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.internal.aapt.v2;

import com.android.builder.internal.aapt.AaptException;
import com.android.testutils.truth.MoreTruth;
import com.android.tools.aapt2.Aapt2Result;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for AaptV2Jni class. */
public class AaptV2JniTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkAaptException() throws IOException {
        File intermediateDir = temporaryFolder.newFolder();
        //noinspection ConstantConditions
        try (AaptV2Jni aapt = new AaptV2Jni(intermediateDir, null, null, null)) {
            Aapt2Result result = Aapt2Result.builder().setReturnCode(1).build();
            ImmutableList<String> args = ImmutableList.of("--flag1", "arg1", "--flag2", "arg2");

            AaptException exception = aapt.buildException("link", args, result);
            File argsFile = new File(intermediateDir, "aaptCommand.txt");

            Truth.assertThat(exception.getMessage())
                    .contains(
                            "AAPT2 link failed. No issues were reported. Argument list written to: "
                                    + argsFile.getAbsolutePath());
            MoreTruth.assertThat(argsFile).contains(Joiner.on(' ').join(args));
        }
    }
}
