/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ddmlib.testrunner;

import static com.android.ddmlib.testrunner.IInstrumentationResultParser.StatusKeys.DDMLIB_LOGCAT;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.commands.am.InstrumentationData;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link InstrumentationProtoResultParser}. */
@RunWith(JUnit4.class)
public class InstrumentationProtoResultParserTest {

    @Mock ITestRunListener mockListener;

    InstrumentationProtoResultParser parser;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        parser = new InstrumentationProtoResultParser("myTestRun", ImmutableList.of(mockListener));
    }

    @Test
    public void noCallbacksAreMadeBeforeTestsStart() {
        verify(mockListener, never()).testRunStarted(any(String.class), anyInt());
    }

    @Test
    public void testRunSuccessfully() throws Exception {
        readSession("instrumentation-data-session.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(4));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)),
                        argThat(
                                testMetrics ->
                                        testMetrics
                                                .getOrDefault(DDMLIB_LOGCAT, "")
                                                .contains(
                                                        "W MainActivityTest: logcat message from test case 1")));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase2",
                                        2)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase2",
                                        2)),
                        argThat(
                                testMetrics ->
                                        testMetrics
                                                .getOrDefault(DDMLIB_LOGCAT, "")
                                                .contains(
                                                        "W MainActivityTest: logcat message from test case 2")));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testWithIgnoreAnnotation",
                                        3)));
        inOrder.verify(mockListener)
                .testIgnored(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testWithIgnoreAnnotation",
                                        3)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testWithIgnoreAnnotation",
                                        3)),
                        argThat(
                                testMetrics ->
                                        Strings.isNullOrEmpty(
                                                testMetrics.getOrDefault(DDMLIB_LOGCAT, ""))));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "failingTest",
                                        4)));
        inOrder.verify(mockListener)
                .testFailed(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "failingTest",
                                        4)),
                        contains("java.lang.AssertionError: This is a testing test"));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "failingTest",
                                        4)),
                        argThat(
                                testMetrics ->
                                        !Strings.isNullOrEmpty(
                                                testMetrics.getOrDefault(DDMLIB_LOGCAT, ""))));

        inOrder.verify(mockListener).testRunEnded(eq(57L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRunSuccessfullyWithCustomTestStatusReport() throws Exception {
        readSession("instrumentation-data-session-custom-status.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(1));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCaseWithCustomStatusAndResultReport",
                                        1)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCaseWithCustomStatusAndResultReport",
                                        1)),
                        argThat(
                                testMetrics ->
                                        new ArrayList<>(testMetrics.keySet())
                                                        .equals(
                                                                ImmutableList.of(
                                                                        "myCustomStatusKey1",
                                                                        "myCustomStatusKey2",
                                                                        DDMLIB_LOGCAT))
                                                && testMetrics
                                                        .getOrDefault("myCustomStatusKey1", "")
                                                        .equals("myCustomStatusValue1")
                                                && testMetrics
                                                        .getOrDefault("myCustomStatusKey2", "")
                                                        .equals("myCustomStatusValue2")));

        inOrder.verify(mockListener)
                .testRunEnded(
                        eq(21L),
                        eq(
                                ImmutableMap.of(
                                        "myCustomResultKey1",
                                        "myCustomResultValue1",
                                        "myCustomResultKey2",
                                        "myCustomResultValue2")));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRunCrashed() throws Exception {
        readSession("instrumentation-data-session-crash.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(2));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest2",
                                        "crashInUiThread",
                                        1)));
        inOrder.verify(mockListener)
                .testFailed(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest2",
                                        "crashInUiThread",
                                        1)),
                        contains("java.lang.RuntimeException: Crash on UI Thread"));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest2",
                                        "crashInUiThread",
                                        1)),
                        anyMap());

        inOrder.verify(mockListener).testRunFailed(eq("Process crashed."));
        inOrder.verify(mockListener).testRunEnded(eq(0L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSystemServiceCrashed() throws Exception {
        readSession("instrumentation-data-session-system-crash.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(1));

        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.CrashTest",
                                        "systemServerCrashed",
                                        1)));
        inOrder.verify(mockListener)
                .testFailed(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.CrashTest",
                                        "systemServerCrashed",
                                        1)),
                        eq(""));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.CrashTest",
                                        "systemServerCrashed",
                                        1)),
                        anyMap());

        inOrder.verify(mockListener)
                .testRunFailed(eq("INSTRUMENTATION_ABORTED: System has crashed."));
        inOrder.verify(mockListener).testRunEnded(eq(0L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testBenchmarkTest() throws Exception {
        TestIdentifier testcase1 =
                new TestIdentifier(
                        "com.example.myapplication.benchmarkexample.MyBenchmarkTest",
                        "benchmarkSomeWork",
                        1);
        TestIdentifier testcase2 =
                new TestIdentifier(
                        "com.example.myapplication.benchmarkexample.MyBenchmarkTest",
                        "benchmarkSomeWork2",
                        2);

        readSession("instrumentation-data-session-benchmark.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(2));
        inOrder.verify(mockListener).testStarted(eq(testcase1));
        inOrder.verify(mockListener).testEnded(eq(testcase1), anyMap());
        inOrder.verify(mockListener).testStarted(eq(testcase2));
        inOrder.verify(mockListener).testEnded(eq(testcase2), anyMap());
        inOrder.verify(mockListener).testRunEnded(eq(6460L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDuplicatedTestCase() throws Exception {
        String testClassName = "com.example.duplicatedtestcase.ExampleInstrumentedTest";
        String testCaseName = "exampleTestCase";
        TestIdentifier testcase1 =
                new TestIdentifier(testClassName, testCaseName, /*testIndex=*/ 1);
        TestIdentifier testcase2 =
                new TestIdentifier(testClassName, testCaseName, /*testIndex=*/ 2);

        readSession("instrumentation-data-session-duplicated-test-case.textproto");

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(2));
        inOrder.verify(mockListener).testStarted(eq(testcase1));
        inOrder.verify(mockListener).testFailed(eq(testcase1), any());
        inOrder.verify(mockListener).testEnded(eq(testcase1), anyMap());
        inOrder.verify(mockListener).testStarted(eq(testcase2));
        inOrder.verify(mockListener).testFailed(eq(testcase2), any());
        inOrder.verify(mockListener).testEnded(eq(testcase2), anyMap());
        inOrder.verify(mockListener).testRunEnded(eq(85L), anyMap());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void addOutputShouldHandleIncompleteInput() throws Exception {
        InstrumentationData.Session session = getSession("instrumentation-data-session.textproto");
        byte[] serializedSession = session.toByteArray();

        byte[] firstChunk = Arrays.copyOfRange(serializedSession, 0, serializedSession.length / 3);
        byte[] secondChunk =
                Arrays.copyOfRange(
                        serializedSession,
                        serializedSession.length / 3,
                        2 * serializedSession.length / 3);
        byte[] lastChunk =
                Arrays.copyOfRange(
                        serializedSession,
                        2 * serializedSession.length / 3,
                        serializedSession.length);

        parser.addOutput(firstChunk, 0, firstChunk.length);
        parser.addOutput(secondChunk, 0, secondChunk.length);
        parser.addOutput(lastChunk, 0, lastChunk.length);

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(4));
        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)),
                        anyMap());
        inOrder.verify(mockListener).testRunEnded(eq(57L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void addOutputShouldHandleIncompleteInputWithOffset() throws Exception {
        InstrumentationData.Session session = getSession("instrumentation-data-session.textproto");
        byte[] serializedSession = session.toByteArray();

        int firstChunkLength = serializedSession.length / 3;
        int secondChunkLength = firstChunkLength;
        int lastChunkLength = serializedSession.length - firstChunkLength - secondChunkLength;

        parser.addOutput(serializedSession, 0, firstChunkLength);
        parser.addOutput(serializedSession, firstChunkLength, secondChunkLength);
        parser.addOutput(serializedSession, firstChunkLength + secondChunkLength, lastChunkLength);

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(4));
        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)));
        inOrder.verify(mockListener)
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)),
                        anyMap());
        inOrder.verify(mockListener).testRunEnded(eq(57L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void cancel() throws Exception {
        assertThat(parser.isCancelled()).isFalse();
        parser.cancel();
        assertThat(parser.isCancelled()).isTrue();

        readSession("instrumentation-data-session.textproto");
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void handleRunFailed() throws Exception {
        InstrumentationData.Session session = getSession("instrumentation-data-session.textproto");
        byte[] serializedSession = session.toByteArray();
        byte[] firstTestStatusReport = session.getTestStatusList().get(0).toByteArray();

        // 1 byte for key (field number & wire type), 2 bytes for payload size.
        // See https://developers.google.com/protocol-buffers/docs/encoding#packed.
        parser.addOutput(serializedSession, 0, firstTestStatusReport.length + 3);

        InOrder inOrder = inOrder(mockListener);
        inOrder.verify(mockListener).testRunStarted(eq("myTestRun"), eq(4));
        inOrder.verify(mockListener)
                .testStarted(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)));

        // testEnded should not be called yet.
        inOrder.verify(mockListener, never())
                .testEnded(
                        eq(
                                new TestIdentifier(
                                        "com.example.myapplication.MainActivityTest",
                                        "testCase1",
                                        1)),
                        anyMap());

        parser.handleTestRunFailed("Some unexpected error happens in Ddmlib");

        inOrder.verify(mockListener).testRunFailed(eq("Some unexpected error happens in Ddmlib"));
        inOrder.verify(mockListener).testRunEnded(eq(0L), eq(emptyMap()));
        inOrder.verifyNoMoreInteractions();
    }

    private void readSession(String fileName) throws IOException {
        InstrumentationData.Session session = getSession(fileName);
        byte[] serializedSession = session.toByteArray();
        parser.addOutput(serializedSession, 0, serializedSession.length);
    }

    private static InstrumentationData.Session getSession(String fileName) throws IOException {
        InstrumentationData.Session.Builder builder = InstrumentationData.Session.newBuilder();
        TextFormat.merge(
                Resources.toString(
                        Resources.getResource("testdata/com/android/ddmlib/testrunner/" + fileName),
                        StandardCharsets.UTF_8),
                builder);
        return builder.build();
    }
}
