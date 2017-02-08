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

package com.android.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;

public class UnitTest {

    @Test
    public void androidResources() throws Exception {
        InputStream inputStream =
                UnitTest.class
                        .getClassLoader()
                        .getResourceAsStream("com/android/tools/test_config.properties");
        Properties properties = new Properties();
        properties.load(inputStream);

        for (Object key : properties.keySet()) {
            File file = new File(properties.get(key).toString());
            assertTrue(file.getPath(), file.exists());
        }
    }
}
