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

package com.android.testutils;

import com.google.common.io.Resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Util class to help get testing resources.
 */
public final class TestResources {

    private TestResources() {}

    /**
     * Returns a file from class resources. If original resource is not file, a temp file is created
     * and returned with resource stream content; the temp file will be deleted when program exits.
     *
     * @param clazz Test class.
     * @param name Resource name.
     * @return File with resource content.
     */
    public static File getFile(Class clazz, String name) {
        URL url = Resources.getResource(clazz, name);
        if (!url.getPath().contains("jar!")) {
            return new File(url.getFile());
        }

        try {
            File tempFile = File.createTempFile(name, null);
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                Resources.copy(url, outputStream);
                tempFile.deleteOnExit();
                return tempFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
