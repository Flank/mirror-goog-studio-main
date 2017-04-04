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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.android.builder.utils.PerformanceUtils;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DexByteCodeConverterTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    private ILogger logger;

    @Mock
    DexOptions dexOptions;

    private DexByteCodeConverter dexByteCodeConverter;

    @Before
    public void initLoggerMock() {
        logger = mock(StdLogger.class, withSettings().verboseLogging());
        dexByteCodeConverter =
                new DexByteCodeConverter(
                        logger,
                        null /* targetInfo */,
                        mock(JavaProcessExecutor.class),
                        false /* verboseExec */,
                        new ThrowingErrorReporter());
    }

    @Test
    public void checkSizeParser() {
        assertThat(PerformanceUtils.parseSizeToBytes("123")).isEqualTo(123);
        assertThat(PerformanceUtils.parseSizeToBytes("2k")).isEqualTo(2048);
        assertThat(PerformanceUtils.parseSizeToBytes("7M")).isEqualTo(1024L * 1024 * 7);
        assertThat(PerformanceUtils.parseSizeToBytes("17g")).isEqualTo(1024L * 1024 * 1024 * 17);
        assertThat(PerformanceUtils.parseSizeToBytes("foo")).isNull();

        assertThat(PerformanceUtils.getNumThreadsForDexArchives())
                .named("number of threads for dex archives")
                .isAtLeast(1);
    }

    @Test
    public void checkDexInProcessIsDefaultEnabled() {
        when(dexOptions.getDexInProcess()).thenReturn(true);
        // a very small number to ensure dex in process not be disabled due to memory needs.
        when(dexOptions.getJavaMaxHeapSize()).thenReturn("1024");
        assertTrue(dexByteCodeConverter.shouldDexInProcess(dexOptions));
    }

    @Test
    public void checkDisabledDexInProcessIsDisabled() {
        when(dexOptions.getDexInProcess()).thenReturn(false);
        assertFalse(dexByteCodeConverter.shouldDexInProcess(dexOptions));
    }

    @Test
    public void checkDexInProcessWithNotEnoughMemoryIsDisabled() {
        when(dexOptions.getDexInProcess()).thenReturn(true);

        // a very large number to ensure dex in process is disabled due to memory needs.
        String heapSizeSetting = "10000G";
        when(dexOptions.getJavaMaxHeapSize()).thenReturn(heapSizeSetting);
        assertFalse(dexByteCodeConverter.shouldDexInProcess(dexOptions));
        verify(logger)
                .warning(
                        contains("org.gradle.jvmargs=-Xmx"),
                        any(),
                        eq(10000 * 1024 + PerformanceUtils.NON_DEX_HEAP_SIZE / 1024 / 1024),
                        contains(heapSizeSetting));
        verifyNoMoreInteractions(logger);
    }
}