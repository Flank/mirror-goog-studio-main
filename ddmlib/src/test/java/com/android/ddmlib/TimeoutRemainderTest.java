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
package com.android.ddmlib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class TimeoutRemainderTest {
    @Test
    public void initialTimeoutIsPreserved() {
        // Prepare
        NanoProviderMock nanoProviderMock = new NanoProviderMock();
        TimeoutRemainder rem = new TimeoutRemainder(nanoProviderMock, 1_000, TimeUnit.MILLISECONDS);

        // Act

        // Assert
        assertEquals(1_000L, rem.getRemainingUnits());
        assertEquals(1L, rem.getRemainingUnits(TimeUnit.SECONDS));
        assertEquals(1_000L, rem.getRemainingUnits(TimeUnit.MILLISECONDS));
        assertEquals(1_000_000L, rem.getRemainingUnits(TimeUnit.MICROSECONDS));
        assertEquals(1_000_000_000L, rem.getRemainingUnits(TimeUnit.NANOSECONDS));
    }

    @Test
    public void advancingTimeWorks() {
        // Prepare
        NanoProviderMock nanoProviderMock = new NanoProviderMock();
        TimeoutRemainder rem = new TimeoutRemainder(nanoProviderMock, 1_000, TimeUnit.MILLISECONDS);

        // Act
        nanoProviderMock.advanceMillis(50);

        // Assert
        assertEquals(950, rem.getRemainingUnits());

        // Act
        nanoProviderMock.advanceMillis(100);

        // Assert
        assertEquals(850, rem.getRemainingUnits());

        // Act
        nanoProviderMock.advanceMillis(500);

        // Assert
        assertEquals(350, rem.getRemainingUnits());

        // Act
        nanoProviderMock.advanceMillis(500);

        // Assert
        assertTrue(rem.getRemainingUnits() < 0);
    }

    @Test
    public void maxTimeoutValueIsPreserved() {
        // Prepare
        NanoProviderMock nanoProviderMock = new NanoProviderMock();
        TimeoutRemainder rem =
                new TimeoutRemainder(nanoProviderMock, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        // Act
        nanoProviderMock.advanceNanos(Long.MAX_VALUE - 2_000_000L);

        // Assert
        assertTrue(rem.getRemainingNanos() > 0);
        assertTrue(rem.getRemainingUnits() > 0);
    }

    private static class NanoProviderMock implements TimeoutRemainder.SystemNanoTimeProvider {
        /** Initial value is non-zero to be close to real-world (system nano time is never zero). */
        private long currentNanoTime = 2_000_000;

        @Override
        public long nanoTime() {
            return currentNanoTime;
        }

        public void advanceNanos(long value) {
            this.currentNanoTime += value;
        }

        public void advanceMillis(long value) {
            this.currentNanoTime += (value * 1_000_000);
        }
    }
}
