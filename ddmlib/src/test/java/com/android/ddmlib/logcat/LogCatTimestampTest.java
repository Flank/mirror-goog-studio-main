/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

public class LogCatTimestampTest extends TestCase {

    private static final int YEAR = 2014;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Yerevan");

    public void testParse() {
        String time = "01-23 12:34:56.789";
        Instant parsed = LogCatTimestamp.parse(time, YEAR, ZONE_ID);
        ZonedDateTime expected =
                ZonedDateTime.of(
                        YEAR, 1, 23, 12, 34, 56, (int) TimeUnit.MILLISECONDS.toNanos(789), ZONE_ID);
        assertThat(parsed).isEqualTo(Instant.from(expected));
    }

    public void testTimestampMillisecondsTruncated() {
        Instant parsed = LogCatTimestamp.parse("01-01 00:00:00.1234", YEAR, ZONE_ID);
        ZonedDateTime expected =
                ZonedDateTime.of(
                        YEAR, 1, 1, 0, 0, 0, (int) TimeUnit.MILLISECONDS.toNanos(123), ZONE_ID);
        assertThat(parsed).isEqualTo(Instant.from(expected));
    }
}
