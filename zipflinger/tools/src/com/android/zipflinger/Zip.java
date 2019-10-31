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

package com.android.zipflinger;

import java.io.File;
import java.io.IOException;

public class Zip {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: Zip dest [files_to_zip]");
            return;
        }

        File dst = new File(args[0]);
        if (dst.exists()) {
            dst.delete();
        }

        try (ZipArchive archive = new ZipArchive(dst)) {
            for (int i = 1; i < args.length; i++) {
                File src = new File(args[i]);
                BytesSource source = new BytesSource(src, src.getName(), 0);
                archive.add(source);
            }
        }
    }
}
