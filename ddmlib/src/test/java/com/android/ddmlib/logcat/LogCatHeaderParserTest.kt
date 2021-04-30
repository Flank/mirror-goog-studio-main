/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.ddmlib.logcat

import com.android.ddmlib.IDevice
import com.android.ddmlib.Log.LogLevel.INFO
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val YEAR = 2014
private val ZONE_ID = ZoneId.of("Asia/Yerevan")
private const val EPOCH_SEC = 1517266949L
private const val EPOCH_MILLI = 472L
private const val MONTH = "05"
private const val DAY = "26"
private const val HOUR = "14"
private const val MIN = "58"
private const val SEC = "23"
private const val MILLI = "972"
private const val PID_UNKNOWN = 5755
private const val PID_APP = 10001
private const val INVALID_NUMBER = "1234567890123456789012345678901234567890"
private const val PID_EMPTY_APP = 20001
private const val TID = 601
private const val TID_HEX = "0x100"
private const val TAG = "Tag"
private const val APP_UNKNOWN = "?"
private const val APP_NAME = "com.android.app"

@RunWith(JUnit4::class)
class LogCatHeaderParserTest {

    @get:Rule
    var mockitoJunit = MockitoJUnit.rule()

    @Mock
    private lateinit var mockDevice: IDevice

    private val logCatHeaderParser = LogCatHeaderParser(YEAR, ZONE_ID)

    @Before
    fun mockDevice() {
        `when`(mockDevice.getClientName(PID_APP)).thenReturn(APP_NAME)
        `when`(mockDevice.getClientName(PID_EMPTY_APP)).thenReturn("")
    }

    @Test
    fun parseHeader_withEpoch() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $EPOCH_SEC.$EPOCH_MILLI $PID_UNKNOWN:$TID ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_UNKNOWN,
                TID,
                APP_UNKNOWN,
                TAG,
                Instant.ofEpochSecond(EPOCH_SEC, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }

    @Test
    fun parseHeader_withDateTime() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $MONTH-$DAY $HOUR:$MIN:$SEC.$MILLI $PID_UNKNOWN:$TID ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_UNKNOWN,
                TID,
                APP_UNKNOWN,
                TAG,
                Instant.from(
                    ZonedDateTime.of(
                        YEAR,
                        MONTH.toInt(),
                        DAY.toInt(),
                        HOUR.toInt(),
                        MIN.toInt(),
                        SEC.toInt(),
                        MILLISECONDS.toNanos(MILLI.toLong()).toInt(),
                        ZONE_ID
                    )
                )
            )
        )
    }

    @Test
    fun parseHeader_withSpaces() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[   $EPOCH_SEC.$EPOCH_MILLI   $PID_UNKNOWN:$TID   ${INFO.priorityLetter}/$TAG   ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_UNKNOWN,
                TID,
                APP_UNKNOWN,
                TAG,
                Instant.ofEpochSecond(EPOCH_SEC, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }

    @Test
    fun parseHeader_withHexTid() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $EPOCH_SEC.$EPOCH_MILLI $PID_UNKNOWN:$TID_HEX ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_UNKNOWN,
                Integer.decode(TID_HEX),
                APP_UNKNOWN,
                TAG,
                Instant.ofEpochSecond(EPOCH_SEC, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }

    @Test
    fun parseHeader_withAppName() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $EPOCH_SEC.$EPOCH_MILLI $PID_APP:$TID ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_APP,
                TID,
                APP_NAME,
                TAG,
                Instant.ofEpochSecond(EPOCH_SEC, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }

    @Test
    fun parseHeader_withInvalidPid() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $EPOCH_SEC.$EPOCH_MILLI $INVALID_NUMBER:$TID ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                -1,
                TID,
                APP_UNKNOWN,
                TAG,
                Instant.ofEpochSecond(EPOCH_SEC, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }

    @Test
    fun parseHeader_withInvalidEpochSeconds() {
        assertThat(
            logCatHeaderParser.parseHeader(
                "[ $INVALID_NUMBER.$EPOCH_MILLI $PID_APP:$TID ${INFO.priorityLetter}/$TAG ]",
                mockDevice
            )
        ).isEqualTo(
            LogCatHeader(
                INFO,
                PID_APP,
                TID,
                APP_NAME,
                TAG,
                Instant.ofEpochSecond(0, MILLISECONDS.toNanos(EPOCH_MILLI))
            )
        )
    }
}
