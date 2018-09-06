/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.binaries;

import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Bin2CTest {

    @Test
    public void convertToC() throws IOException {
        byte[] buffer = new byte[20];
        for (byte i = 0; i < buffer.length; i++) {
            buffer[i] = i;
        }
        Path in = Files.createTempFile("input", ".bin");
        Files.write(in, buffer);
        Path out = Files.createTempFile("output", ".cc");
        Bin2C.main(new String[] {in.toString(), out.toString(), "test_name"});
        String[] strings = Files.readAllLines(out).toArray(new String[] {});
        assertArrayEquals(
                new String[] {
                    "unsigned char test_name[] = {",
                    "0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, ",
                    "0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, };",
                    "uint64_t test_name_len = 20;",
                    "const char* test_name_hash = \"e7aebf577f60412f0312d442c70a1fa6148c090bf5bab404caec29482ae779e8\";",
                },
                strings);
    }
}
