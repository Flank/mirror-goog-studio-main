/*
 * Copyright (C) 2008 The Android Open Source Project
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import junit.framework.TestCase;
import org.easymock.Capture;
import org.easymock.EasyMock;

/**
 * Unit tests for {@link InstrumentationResultParser}.
 */
public class InstrumentationResultParserTest extends TestCase {

    private InstrumentationResultParser mParser;
    private ITestRunListener mMockListener;

    // static dummy test names to use for validation
    private static final String RUN_NAME = "foo";
    private static final String CLASS_NAME = "com.test.FooTest";
    private static final String TEST_NAME = "testFoo";
    private static final String STACK_TRACE = "java.lang.AssertionFailedException";
    private static final String ON_ERROR =
            "onError: commandError=false message=" + "INSTRUMENTATION_ABORTED: System has crashed.";
    private static final TestIdentifier TEST_ID = new TestIdentifier(CLASS_NAME, TEST_NAME);

    /**
     * @param name - test name
     */
    public InstrumentationResultParserTest(String name) {
        super(name);
    }

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // use a strict mock to verify order of method calls
        mMockListener = EasyMock.createStrictMock(ITestRunListener.class);
        mParser = new InstrumentationResultParser(RUN_NAME, mMockListener);
    }

    /**
     * Tests parsing empty output.
     */
    public void testParse_empty() {
        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(InstrumentationResultParser.NO_TEST_RESULTS_MSG);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString("");
    }

    /**
     * Tests parsing output for a successful test run with no tests.
     */
    public void testParse_noTests() {
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "Test results for InstrumentationTestRunner=");
        addLine(output, "Time: 0.001");
        addLine(output, "OK (0 tests)");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /** Ensure that all reporters receive the same events. */
    public void testParse_multiReceiver() {
        ITestRunListener mMockListener = EasyMock.createStrictMock(ITestRunListener.class);
        ITestRunListener mMockListener2 = EasyMock.createStrictMock(ITestRunListener.class);
        mParser =
                new InstrumentationResultParser(
                        RUN_NAME, Arrays.asList(mMockListener, mMockListener2));
        mParser.setEnforceTimeStamp(true);
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: com.android.cts.launcherapps:other=true");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener2.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(InstrumentationResultParser.INVALID_OUTPUT_ERR_MSG);
        mMockListener2.testRunFailed(InstrumentationResultParser.INVALID_OUTPUT_ERR_MSG);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
        mMockListener2.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockListener, mMockListener2);
        byte[] data = output.toString().getBytes();
        mParser.addOutput(data, 0, data.length);
        mParser.flush();
        EasyMock.verify(mMockListener, mMockListener2);
    }

    /**
     * Tests parsing output for a successful test run with no tests with a test time over 1000 secs
     * in that case the format of the number contains comma.
     */
    public void testParse_timeLongFormat() {
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "Test results for InstrumentationTestRunner=");
        addLine(output, "Time: 1,745.755");
        addLine(output, "OK (0 tests)");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunEnded(1745755L, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Tests parsing output for a missing time stamp, meaning an invalid output from the runner.
     */
    public void testParse_missingTimeStamp() {
        // we enforce the time stamp
        mParser.setEnforceTimeStamp(true);
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(InstrumentationResultParser.INVALID_OUTPUT_ERR_MSG);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Tests parsing output for a missing time stamp, meaning an invalid output from the runner. In
     * some case a stack or exception will be available in 'stream=' we recover it in this case to
     * have a more meaningful message.
     */
    public void testParse_missingTimeStamp_withStack() {
        // we enforce the time stamp
        mParser.setEnforceTimeStamp(true);
        StringBuilder output = new StringBuilder();
        addLine(
                output,
                "INSTRUMENTATION_RESULT: stream="
                        + InstrumentationResultParser.FATAL_EXCEPTION_MSG);
        addLine(
                output,
                "java.lang.IllegalArgumentException: Ambiguous arguments: cannot provide both test"
                        + " package and test class(es) to run");
        addLine(
                output,
                "at android.support.test.internal.runner.TestRequestBuilder.validate(TestRequestBu"
                        + "ilder.java:791)");
        addLine(
                output,
                "at android.support.test.internal.runner.TestRequestBuilder.build(TestRequestBuild"
                        + "er.java:760)");
        addLine(
                output,
                "at android.support.test.runner.AndroidJUnitRunner.buildRequest(AndroidJUnitRunner"
                        + ".java:399)");
        addLine(
                output,
                "at android.support.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java"
                        + ":298)");
        addLine(
                output,
                "at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:)");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        Capture<String> capture = new Capture<>();
        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(EasyMock.capture(capture));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
        String failure = capture.getValue();
        assertTrue(failure.startsWith(InstrumentationResultParser.INVALID_OUTPUT_ERR_MSG));
        assertTrue(failure.contains(InstrumentationResultParser.FATAL_EXCEPTION_MSG));
    }

    /** Tests parsing output for a missing time stamp but without enforcing the format. */
    public void testParse_missingTimeStamp_notEnforced() {
        mParser.setEnforceTimeStamp(false);
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        mMockListener.testRunStarted(RUN_NAME, 0);
        // no failure should be expected
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Tests parsing the fatal error output of an instrumentation invoked with "-e log true". Since
     * it is log only, it will not report directly the failure, but the stream should still be
     * populated.
     */
    public void testParse_directFailure() {
        mParser.setEnforceTimeStamp(false);
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "Time: 0");
        addLine(output, "There was 1 failure:");
        addLine(output, "1) Fatal exception when running tests");
        addLine(output, "java.lang.RuntimeException: it failed super fast.");
        addLine(output, "at stackstack");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        Capture<String> capture = new Capture<>();
        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(EasyMock.capture(capture));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
        String error = capture.getValue();
        assertTrue(error.contains("java.lang.RuntimeException: it failed super fast."));
    }

    /** Tests parsing output for a single successful test execution. */
    public void testParse_singleTest() {
        StringBuilder output = createSuccessTest();

        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testEnded(TEST_ID, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Tests parsing output for a successful test execution with metrics.
     */
    public void testParse_testMetrics() {
        StringBuilder output = buildCommonResult();

        addStatusKey(output, "randomKey", "randomValue");
        addTimeStamp(output);
        addSuccessCode(output);

        final Capture<Map<String, String>> captureMetrics = new Capture<Map<String, String>>();
        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testEnded(EasyMock.eq(TEST_ID), EasyMock.capture(captureMetrics));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());

        assertEquals("randomValue", captureMetrics.getValue().get("randomKey"));
    }

    /**
     * Test parsing output for a test that produces repeated metrics values
     * <p>
     * This mimics launch performance test output.
     */
    public void testParse_repeatedTestMetrics() {
        StringBuilder output = new StringBuilder();
        // add test start output
        addCommonStatus(output);
        addStartCode(output);

        addStatusKey(output, "currentiterations", "1");
        addStatusCode(output, "2");
        addStatusKey(output, "currentiterations", "2");
        addStatusCode(output, "2");
        addStatusKey(output, "currentiterations", "3");
        addStatusCode(output, "2");

        // add test end
        addCommonStatus(output);
        addStatusKey(output, "numiterations", "3");
        addTimeStamp(output);
        addSuccessCode(output);

        final Capture<Map<String, String>> captureMetrics = new Capture<Map<String, String>>();
        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testEnded(EasyMock.eq(TEST_ID), EasyMock.capture(captureMetrics));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());

        assertEquals("3", captureMetrics.getValue().get("currentiterations"));
        assertEquals("3", captureMetrics.getValue().get("numiterations"));
    }

    /**
     * Test parsing output for a test failure.
     */
    public void testParse_testFailed() {
        StringBuilder output = buildCommonResult();
        addTimeStamp(output);
        addStackTrace(output);
        addFailureCode(output);

        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testFailed(TEST_ID, STACK_TRACE);
        mMockListener.testEnded(TEST_ID, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Ensure that when onError message is available, we do not consider it as part of the class
     * name but as a stand alone entity.
     */
    public void testParse_onError() {
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_STATUS: class=" + CLASS_NAME);
        addLine(output, ON_ERROR);
        addLine(output, "INSTRUMENTATION_STATUS: current=1");
        addLine(output, "INSTRUMENTATION_ABORTED: System has crashed.");
        addLine(output, "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output, "INSTRUMENTATION_STATUS: numtests=1");
        addLine(output, "INSTRUMENTATION_STATUS: stream=");
        addLine(output, "INSTRUMENTATION_STATUS: test=" + TEST_NAME);
        addLine(output, "INSTRUMENTATION_STATUS_CODE: 1");

        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testFailed(
                TEST_ID,
                "Test failed to run to completion. Reason: "
                        + "'Test run failed to complete. Expected 1 tests, received 0'. "
                        + "Check device logcat for details");
        mMockListener.testEnded(TEST_ID, Collections.emptyMap());
        mMockListener.testRunFailed(
                "Test run failed to complete. Expected 1 tests, received 0. " + ON_ERROR);
        mMockListener.testRunEnded(0L, Collections.emptyMap());

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing and conversion of time output that contains extra chars.
     */
    public void testParse_timeBracket() {
        StringBuilder output = createSuccessTest();
        output.append("Time: 0.001)");

        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testEnded(TEST_ID, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(1, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output for a test run failure.
     */
    public void testParse_runFailed() {
        StringBuilder output = new StringBuilder();
        final String errorMessage = "Unable to find instrumentation info";
        addStatusKey(output, "Error", errorMessage);
        addStatusCode(output, "-1");
        output.append("INSTRUMENTATION_FAILED: com.dummy/android.test.InstrumentationTestRunner");
        addLineBreak(output);

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(errorMessage);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output when a status code cannot be parsed
     */
    public void testParse_invalidCode() {
        StringBuilder output = new StringBuilder();
        addLine(output, "android.util.AndroidException: INSTRUMENTATION_FAILED: foo/foo");
        addLine(output, "INSTRUMENTATION_STATUS: id=ActivityManagerService");
        addLine(output, "INSTRUMENTATION_STATUS: Error=Unable to find instrumentation target package: foo");
        addLine(output, "INSTRUMENTATION_STATUS_CODE: -1at com.android.commands.am.Am.runInstrument(Am.java:532)");
        addLine(output, "");
        addLine(output, "        at com.android.commands.am.Am.run(Am.java:111)");
        addLineBreak(output);

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed((String)EasyMock.anyObject());
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output for a test run failure, where an instrumentation component failed to
     * load.
     * <p>
     * Parsing input takes the from of INSTRUMENTATION_RESULT: fff
     */
    public void testParse_failedResult() {
        StringBuilder output = new StringBuilder();
        final String errorMessage = "Unable to instantiate instrumentation";
        output.append("INSTRUMENTATION_RESULT: shortMsg=");
        output.append(errorMessage);
        addLineBreak(output);
        output.append("INSTRUMENTATION_CODE: 0");
        addLineBreak(output);

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(EasyMock.contains(errorMessage));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output for a test run that did not complete.
     * <p>
     * This can occur if device spontaneously reboots, or if test method could not be found.
     */
    public void testParse_incomplete() {
        StringBuilder output = new StringBuilder();
        // add a start test sequence, but without an end test sequence
        addCommonStatus(output);
        addStartCode(output);

        mMockListener.testRunStarted(RUN_NAME, 1);
        mMockListener.testStarted(TEST_ID);
        mMockListener.testFailed(EasyMock.eq(TEST_ID),
                EasyMock.startsWith(InstrumentationResultParser.INCOMPLETE_TEST_ERR_MSG_PREFIX));
        mMockListener.testEnded(TEST_ID, Collections.EMPTY_MAP);
        mMockListener.testRunFailed(EasyMock.startsWith(
                InstrumentationResultParser.INCOMPLETE_RUN_ERR_MSG_PREFIX));
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output for a test run that did not start due to incorrect syntax supplied to am.
     */
    public void testParse_amFailed() {
        StringBuilder output = new StringBuilder();
        addLine(output, "usage: am [subcommand] [options]");
        addLine(output, "start an Activity: am start [-D] [-W] <INTENT>");
        addLine(output, "-D: enable debugging");
        addLine(output, "-W: wait for launch to complete");
        addLine(output, "start a Service: am startservice <INTENT>");
        addLine(output, "Error: Bad component name: wfsdafddfasasdf");

        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunFailed(InstrumentationResultParser.NO_TEST_RESULTS_MSG);
        mMockListener.testRunEnded(0, Collections.EMPTY_MAP);

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Test parsing output for a test run that produces INSTRUMENTATION_RESULT output.
     * <p>
     * This mimics launch performance test output.
     */
    public void testParse_instrumentationResults() {
        StringBuilder output = new StringBuilder();
        addResultKey(output, "other_pss", "2390");
        addResultKey(output, "java_allocated", "2539");
        addResultKey(output, "foo", "bar");
        addResultKey(output, "stream", "should not be captured");
        addTimeStamp(output);
        addLine(output, "INSTRUMENTATION_CODE: -1");

        Capture<Map<String, String>> captureMetrics = new Capture<Map<String, String>>();
        mMockListener.testRunStarted(RUN_NAME, 0);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.capture(captureMetrics));

        injectAndVerifyTestString(output.toString());

        assertEquals("2390", captureMetrics.getValue().get("other_pss"));
        assertEquals("2539", captureMetrics.getValue().get("java_allocated"));
        assertEquals("bar", captureMetrics.getValue().get("foo"));
        assertEquals(3, captureMetrics.getValue().size());
    }

    public void testParse_AssumptionFailuresIgnored() {
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output, "INSTRUMENTATION_STATUS: stream=");
        addLine(output,  "com.example.helloworld.FailureAssumptionTest:");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=checkIgnoreTestsArePossible");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.FailureAssumptionTest");
        addLine(output,  "INSTRUMENTATION_STATUS: current=1");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: 1");
        addLine(output,  "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output,  "INSTRUMENTATION_STATUS: stream=");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=checkIgnoreTestsArePossible");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.FailureAssumptionTest");
        addLine(output,  "INSTRUMENTATION_STATUS: current=1");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: -3");
        addLine(output,  "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output,  "INSTRUMENTATION_STATUS: stream=");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=checkAssumptionIsSkipped");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.FailureAssumptionTest");
        addLine(output,  "INSTRUMENTATION_STATUS: current=2");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: 1");
        addLine(output,  "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output,  "INSTRUMENTATION_STATUS: stream=");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=checkAssumptionIsSkipped");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.FailureAssumptionTest");
        addLine(output,  "INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: got: <false>, expected: is <true>");
        addLine(output,  "at org.junit.Assume.assumeThat(Assume.java:95)");
        addLine(output,  "at org.junit.Assume.assumeTrue(Assume.java:41)");
        addLine(output,  "at com.example.helloworld.FailureAssumptionTest.checkAssumptionIsSkipped(FailureAssumptionTest.java:19)");
        addLine(output,  "at java.lang.reflect.Method.invoke(Native Method)");
        addLine(output,  "at java.lang.reflect.Method.invoke(Method.java:372)");
        addLine(output,  "at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)");
        addLine(output,  "at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)");
        addLine(output,  "at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)");
        addLine(output,  "at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)");
        addLine(output,  "at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:325)");
        addLine(output,  "at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:78)");
        addLine(output,  "at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:57)");
        addLine(output,  "at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)");
        addLine(output,  "at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)");
        addLine(output,  "at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)");
        addLine(output,  "at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)");
        addLine(output,  "at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)");
        addLine(output,  "at org.junit.runners.ParentRunner.run(ParentRunner.java:363)");
        addLine(output,  "at org.junit.runners.Suite.runChild(Suite.java:128)");
        addLine(output,  "at org.junit.runners.Suite.runChild(Suite.java:27)");
        addLine(output,  "at org.junit.runners.ParentRunner$3.run(ParentRunner.java:290)");
        addLine(output,  "at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:71)");
        addLine(output,  "at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:288)");
        addLine(output,  "at org.junit.runners.ParentRunner.access$000(ParentRunner.java:58)");
        addLine(output,  "at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:268)");
        addLine(output,  "at org.junit.runners.ParentRunner.run(ParentRunner.java:363)");
        addLine(output,  "at org.junit.runner.JUnitCore.run(JUnitCore.java:137)");
        addLine(output,  "at org.junit.runner.JUnitCore.run(JUnitCore.java:115)");
        addLine(output,  "at android.support.test.internal.runner.TestExecutor.execute(TestExecutor.java:54)");
        addLine(output,  "at android.support.test.runner.AndroidJUnitRunner.onStart(AndroidJUnitRunner.java:228)");
        addLine(output,  "at android.app.Instrumentation$InstrumentationThread.run(Instrumentation.java:1853)");
        addLine(output,  "");
        addLine(output,  "INSTRUMENTATION_STATUS: current=2");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: -4");
        addLine(output,  "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output,  "INSTRUMENTATION_STATUS: stream=");
        addLine(output,  "com.example.helloworld.HelloWorldTest:");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=testPreconditions");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.HelloWorldTest");
        addLine(output,  "INSTRUMENTATION_STATUS: current=3");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: 1");
        addLine(output,  "INSTRUMENTATION_STATUS: numtests=3");
        addLine(output,  "INSTRUMENTATION_STATUS: stream=.");
        addLine(output,  "INSTRUMENTATION_STATUS: id=AndroidJUnitRunner");
        addLine(output,  "INSTRUMENTATION_STATUS: test=testPreconditions");
        addLine(output,  "INSTRUMENTATION_STATUS: class=com.example.helloworld.HelloWorldTest");
        addLine(output,  "INSTRUMENTATION_STATUS: current=3");
        addLine(output,  "INSTRUMENTATION_STATUS_CODE: 0");
        addLine(output,  "INSTRUMENTATION_RESULT: stream=");
        addLine(output,  "");
        addLine(output,  "Time: 0.676");
        addLine(output,  "");
        addLine(output,  "OK (2 tests)");
        addLine(output,  "");
        addLine(output,  "");
        addLine(output,  "INSTRUMENTATION_CODE: -1");


        mMockListener.testRunStarted(RUN_NAME, 3);
        final TestIdentifier IGNORED_ANNOTATION = new TestIdentifier(
                "com.example.helloworld.FailureAssumptionTest",
                "checkIgnoreTestsArePossible");
        mMockListener.testStarted(IGNORED_ANNOTATION);
        mMockListener.testIgnored(IGNORED_ANNOTATION);
        mMockListener.testEnded(IGNORED_ANNOTATION, Collections.EMPTY_MAP);
        final TestIdentifier ASSUME_FALSE = new TestIdentifier(
                "com.example.helloworld.FailureAssumptionTest",
                "checkAssumptionIsSkipped");
        mMockListener.testStarted(ASSUME_FALSE);
        mMockListener.testAssumptionFailure(EasyMock.eq(ASSUME_FALSE),
                EasyMock.startsWith(
                        "org.junit.AssumptionViolatedException: got: <false>, expected: is <true>"));
        mMockListener.testEnded(ASSUME_FALSE, Collections.EMPTY_MAP);
        final TestIdentifier HELLO_WORLD = new TestIdentifier("com.example.helloworld.HelloWorldTest",
                "testPreconditions");
        mMockListener.testStarted(HELLO_WORLD);
        mMockListener.testEnded(HELLO_WORLD, Collections.EMPTY_MAP);
        mMockListener.testRunEnded(EasyMock.eq(676L), EasyMock.anyObject());

        injectAndVerifyTestString(output.toString());
    }

    /**
     * Builds a common test result using TEST_NAME and TEST_CLASS.
     */
    private StringBuilder buildCommonResult() {
        StringBuilder output = new StringBuilder();
        // add test start bundle
        addCommonStatus(output);
        addStartCode(output);
        // add end test bundle, without status
        addCommonStatus(output);
        return output;
    }

    /**
     * Create instrumentation output for a successful single test case execution.
     */
    private StringBuilder createSuccessTest() {
        StringBuilder output = buildCommonResult();
        addTimeStamp(output);
        addSuccessCode(output);
        return output;
    }

    /**
     * Adds common status results to the provided output.
     */
    private void addCommonStatus(StringBuilder output) {
        addStatusKey(output, "stream", "\r\n" + CLASS_NAME);
        addStatusKey(output, "test", TEST_NAME);
        addStatusKey(output, "class", CLASS_NAME);
        addStatusKey(output, "current", "1");
        addStatusKey(output, "numtests", "1");
        addStatusKey(output, "id", "InstrumentationTestRunner");
    }

    /**
     * Adds a stack trace status bundle to output.
     */
    private void addStackTrace(StringBuilder output) {
        addStatusKey(output, "stack", STACK_TRACE);
    }

    /**
     * Helper method to add a status key-value bundle.
     */
    private void addStatusKey(StringBuilder outputBuilder, String key,
            String value) {
        outputBuilder.append("INSTRUMENTATION_STATUS: ");
        outputBuilder.append(key);
        outputBuilder.append('=');
        outputBuilder.append(value);
        addLineBreak(outputBuilder);
    }

    /**
     * Helper method to add a result key value bundle.
     */
    private void addResultKey(StringBuilder outputBuilder, String key,
          String value) {
      outputBuilder.append("INSTRUMENTATION_RESULT: ");
      outputBuilder.append(key);
      outputBuilder.append('=');
      outputBuilder.append(value);
      addLineBreak(outputBuilder);
    }

    /**
     * Append a line to output.
     */
    private void addLine(StringBuilder outputBuilder, String lineContent) {
        outputBuilder.append(lineContent);
        addLineBreak(outputBuilder);
    }

    /**
     * Append line break characters to output
     */
    private void addLineBreak(StringBuilder outputBuilder) {
        outputBuilder.append("\r\n");
    }

    private void addTimeStamp(StringBuilder outputBuilder) {
        outputBuilder.append("Time: 0");
        addLineBreak(outputBuilder);
    }

    private void addStartCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "1");
    }

    private void addSuccessCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "0");
    }

    private void addFailureCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "-2");
    }

    private void addStatusCode(StringBuilder outputBuilder, String value) {
        outputBuilder.append("INSTRUMENTATION_STATUS_CODE: ");
        outputBuilder.append(value);
        addLineBreak(outputBuilder);
    }

    /**
     * Inject a test string into the result parser, and verify the mock listener.
     *
     * @param result the string to inject into parser under test.
     */
    private void injectAndVerifyTestString(String result) {
        EasyMock.replay(mMockListener);
        byte[] data = result.getBytes();
        mParser.addOutput(data, 0, data.length);
        mParser.flush();
        EasyMock.verify(mMockListener);
    }
}
