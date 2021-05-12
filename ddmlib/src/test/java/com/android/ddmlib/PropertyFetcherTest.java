/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ddmlib.PropertyFetcher.GetPropReceiver;
import com.android.ddmlib.internal.DeviceTest;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.easymock.EasyMock;

/** Unit tests for {@link PropertyFetcher}. */
public class PropertyFetcherTest extends TestCase {
    static final String GETPROP_RESPONSE =
            "\n[ro.sf.lcd_density]: [480]\n" +
            "[ro.secure]: [1]\r\n";

    static final String MULTI_LINE_PROP =
            "[persist.before]: [before]\n"
                    + "[persist.sys.boot.reason.history]: [reboot,remount,1565385848\n"
                    + "reboot,remount-test,1565385820\n"
                    + "reboot,remount-test,1565385798]\n"
                    + "[persist.after]: [after]\r\n";

    static final String MULTI_LINE_PROP_WITH_EMPTY_LINES =
            "[persist.before]: [before]\n"
                    + "[persist.sys.boot.reason.history]: [reboot,remount,1565385848\n"
                    + "\n"
                    + "reboot,remount-test,1565385820\n"
                    + "reboot,remount-test,1565385798]\n"
                    + "[persist.after]: [after]\r\n";

    static final String ERROR_MULTI_LINE_PROP =
            "[persist.before]: [before]\n"
                    + "[persist.sys.boot.reason.history]: [reboot,remount,1565385848\n"
                    + "reboot,remount-test,1565385820\n"
                    + "reboot,remount-test,ERROR\n" // not ended properly
                    + "[persist.after]: [after]\r\n";

    private static final String[] MULTILINE_CUT_LINES = {
        "[foo.bar]: [a, b\n c, d ,e\n", "f ,g] \n"
    };

    /**
     * Simple test to ensure parsing result of 'shell getprop' works as expected
     */
    public void testGetPropReceiver() {
        GetPropReceiver receiver = new GetPropReceiver();
        byte[] byteData = GETPROP_RESPONSE.getBytes();
        receiver.addOutput(byteData, 0, byteData.length);
        receiver.done();
        assertEquals("480", receiver.getCollectedProperties().get("ro.sf.lcd_density"));
    }

    // Depending on how the socket is read, the String[] can be cut
    // anywhere, including in the middle of a multiline property.
    public void testGetPropReceiver_multiline_cut() {
        GetPropReceiver receiver = new GetPropReceiver();
        for (String lines : MULTILINE_CUT_LINES) {
            byte[] bytes = lines.getBytes();
            receiver.addOutput(bytes, 0, bytes.length);
        }
        receiver.done();

        String value = receiver.getCollectedProperties().get("foo.bar");
        Assert.assertNotNull("Cut multiline failed", value);
    }

    /** Test that properties with multi-lines value are parsed. */
    public void testGetPropReceiver_multilineproperty() {
        GetPropReceiver receiver = new GetPropReceiver();
        byte[] byteData = MULTI_LINE_PROP.getBytes();
        receiver.addOutput(byteData, 0, byteData.length);
        receiver.done();
        assertEquals(
                "reboot,remount,1565385848\n"
                        + "reboot,remount-test,1565385820\n"
                        + "reboot,remount-test,1565385798",
                receiver.getCollectedProperties().get("persist.sys.boot.reason.history"));
        assertEquals("before", receiver.getCollectedProperties().get("persist.before"));
        assertEquals("after", receiver.getCollectedProperties().get("persist.after"));
    }

    /** Test that properties with multi-lines value are parsed, even if a line is empty. */
    public void testGetPropReceiver_multilineproperty_with_empty_lines() {
        GetPropReceiver receiver = new GetPropReceiver();
        byte[] byteData = MULTI_LINE_PROP_WITH_EMPTY_LINES.getBytes();
        receiver.addOutput(byteData, 0, byteData.length);
        receiver.done();
        assertEquals(
                "reboot,remount,1565385848\n"
                        + "\n"
                        + "reboot,remount-test,1565385820\n"
                        + "reboot,remount-test,1565385798",
                receiver.getCollectedProperties().get("persist.sys.boot.reason.history"));
        assertEquals("before", receiver.getCollectedProperties().get("persist.before"));
        assertEquals("after", receiver.getCollectedProperties().get("persist.after"));
    }

    /** Test that properties with multi-lines value are ignored if not well formatted */
    public void testGetPropReceiver_multilineproperty_formatError() {
        GetPropReceiver receiver = new GetPropReceiver();
        byte[] byteData = ERROR_MULTI_LINE_PROP.getBytes();
        receiver.addOutput(byteData, 0, byteData.length);
        receiver.done();
        assertNull(receiver.getCollectedProperties().get("persist.sys.boot.reason.history"));
        assertEquals("before", receiver.getCollectedProperties().get("persist.before"));
        assertEquals("after", receiver.getCollectedProperties().get("persist.after"));
    }

    /**
     * Test that getProperty works as expected when queries made in different states
     */
    public void testGetProperty() throws Exception {
        // Device latency
        final int deviceLatencyMillis = 500;

        // Property fetcher latency should be much lower than device latency, but is not
        // zero (see b/155630484)
        final int propertyFetchMaxCacheLatencyMillis = deviceLatencyMillis / 2;

        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE, deviceLatencyMillis);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        // do query in unpopulated state
        Future<String> unpopulatedFuture = fetcher.getProperty("ro.sf.lcd_density");
        // do query in fetching state
        Future<String> fetchingFuture = fetcher.getProperty("ro.secure");

        assertEquals("480", unpopulatedFuture.get());
        // do queries with short timeout to ensure props already available (i.e. don't run the query
        // on the device again)
        assertEquals(
                "1", fetchingFuture.get(propertyFetchMaxCacheLatencyMillis, TimeUnit.MILLISECONDS));
        assertEquals(
                "480",
                fetcher.getProperty("ro.sf.lcd_density")
                        .get(propertyFetchMaxCacheLatencyMillis, TimeUnit.MILLISECONDS));
    }

    /**
     * Test that getProperty always does a getprop query when requested prop is not
     * read only aka volatile
     */
    public void testGetProperty_volatile() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellResponse(mockDevice, "[dev.bootcomplete]: [0]\r\n");
        DeviceTest.injectShellResponse(mockDevice, "[dev.bootcomplete]: [1]\r\n");
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        assertEquals("0", fetcher.getProperty("dev.bootcomplete").get());
        assertEquals("1", fetcher.getProperty("dev.bootcomplete").get());
    }

    /**
     * Test that getProperty returns when the 'shell getprop' command response is invalid
     */
    public void testGetProperty_badResponse() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellResponse(mockDevice, "blargh");
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        assertNull(fetcher.getProperty("dev.bootcomplete").get());
    }

    /**
     * Test that null is returned when querying an unknown property
     */
    public void testGetProperty_unknown() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        assertNull(fetcher.getProperty("unknown").get());
    }

    /**
     * Test that getProperty propagates exception thrown by 'shell getprop'
     */
    public void testGetProperty_shellException() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellExceptionResponse(mockDevice, new ShellCommandUnresponsiveException());
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        try {
            fetcher.getProperty("dev.bootcomplete").get();
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof ShellCommandUnresponsiveException);
        }
    }

    /**
     * Tests that property fetcher works under the following scenario:
     * <ol>
     * <li>first fetch fails due to a shell exception</li>
     * <li>subsequent fetches should work fine</li>
     * </ol>
     */
    public void testGetProperty_FetchAfterException() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellExceptionResponse(mockDevice, new ShellCommandUnresponsiveException());
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        try {
            fetcher.getProperty("dev.bootcomplete").get(2, TimeUnit.SECONDS);
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof ShellCommandUnresponsiveException);
        }

        assertEquals("480", fetcher.getProperty("ro.sf.lcd_density").get(2, TimeUnit.SECONDS));
    }

    /**
     * Tests that property fetcher works under the following scenario:
     * <ol>
     * <li>first fetch succeeds, but receives an empty response</li>
     * <li>subsequent fetches should work fine</li>
     * </ol>
     */
    public void testGetProperty_FetchAfterEmptyResponse() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellResponse(mockDevice, "");
        DeviceTest.injectShellResponse(mockDevice, GETPROP_RESPONSE);
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        assertNull(fetcher.getProperty("ro.sf.lcd_density").get(2, TimeUnit.SECONDS));
        assertEquals("480", fetcher.getProperty("ro.sf.lcd_density").get(2, TimeUnit.SECONDS));
    }

    /**
     * Checks that getProperty propagates a thrown Error.
     */
    public void testGetProperty_AssertionError() throws Exception {
        IDevice mockDevice = DeviceTest.createMockDevice();
        DeviceTest.injectShellExceptionResponse(mockDevice, new AssertionError());
        EasyMock.replay(mockDevice);

        PropertyFetcher fetcher = new PropertyFetcher(mockDevice);
        try {
            fetcher.getProperty("dev.bootcomplete").get();
            fail("ExecutionException not thrown");
        } catch (ExecutionException e) {
            // expected
            assertTrue(e.getCause() instanceof AssertionError);
        }
    }
}
