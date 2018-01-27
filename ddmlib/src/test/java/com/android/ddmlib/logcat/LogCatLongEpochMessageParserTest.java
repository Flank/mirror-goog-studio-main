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
package com.android.ddmlib.logcat;

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.Log.LogLevel;
import java.time.Instant;
import org.junit.Test;

public final class LogCatLongEpochMessageParserTest {
    @Test
    public void processLogHeader() {
        assertEquals(
                new LogCatHeader(
                        LogLevel.INFO,
                        5755,
                        5755,
                        "?",
                        "MainActivity",
                        Instant.parse("2018-01-29T23:02:29.472Z")),
                new LogCatLongEpochMessageParser()
                        .processLogHeader(
                                "[          1517266949.472  5755: 5755 I/MainActivity ]", null));
    }
}
