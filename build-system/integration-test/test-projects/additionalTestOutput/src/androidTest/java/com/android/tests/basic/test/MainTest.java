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

package com.android.tests.basic.test;

import static org.junit.Assert.*;

import android.Manifest;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainTest {

    @Rule
    public GrantPermissionRule grantPermissionRule =
            GrantPermissionRule.grant(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Test
    public void simpleOutput() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        File outputDir = new File(arguments.getString("additionalTestOutputDir"));

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File file = new File(outputDir, "data.json");
        file.createNewFile();
        file.setWritable(true);

        FileOutputStream stream = new FileOutputStream(file);
        try {
            stream.write("Sample text to be read by AdditionalTestOutputConnectedTest.".getBytes());
        } finally {
            stream.close();
        }
    }
}
