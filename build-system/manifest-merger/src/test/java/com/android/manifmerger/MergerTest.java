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

package com.android.manifmerger;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.testutils.MockLog;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.CharBuffer;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link Merger} class
 */
public class MergerTest extends TestCase {

    @Mock
    ManifestMerger2.Invoker mInvoker;

    @Mock
    MergingReport mMergingReport;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    private class MergerWithMock extends Merger {

        @Override
        protected ManifestMerger2.Invoker createInvoker(@NonNull File mainManifestFile, @NonNull ILogger logger) {
            try {
                when(mMergingReport.getResult()).thenReturn(MergingReport.Result.ERROR);
                when(mMergingReport.getLoggingRecords()).thenReturn(
                        ImmutableList.<MergingReport.Record>of());
                when(mInvoker.merge()).thenReturn(mMergingReport);
            } catch (ManifestMerger2.MergeFailureException e) {
                fail(e.getMessage());
            }
            return mInvoker;
        }

        @Override
        protected File checkPath(@NonNull String path) throws FileNotFoundException {
            return new File(path); // always exists...
        }
    }

    public void testMainParameter() throws FileNotFoundException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml" };
        new MergerWithMock() {
            @Override
            protected ManifestMerger2.Invoker createInvoker(@NonNull File mainManifestFile, @NonNull ILogger logger) {
                assertEquals(args[1], mainManifestFile.getPath().replace(File.separatorChar, '/'));
                return super.createInvoker(mainManifestFile, logger);
            }
        }.process(args);
    }

    public void testDefaultLoggerParameter() throws FileNotFoundException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml" };
        new MergerWithMock() {
            @Override
            protected ILogger createLogger(@NonNull StdLogger.Level level) {
                assertEquals(StdLogger.Level.INFO, level);
                return super.createLogger(level);
            }
        }.process(args);
    }

    public void testLoggerParameter() throws FileNotFoundException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                                "--log", "VERBOSE" };
        new MergerWithMock() {
            @Override
            protected ILogger createLogger(@NonNull StdLogger.Level level) {
                assertEquals(StdLogger.Level.VERBOSE, level);
                return super.createLogger(level);
            }
        }.process(args);
    }

    public void testRemoveToolsDeclarationsParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = {
            "--main", "src/main/AndroidManifest.xml", "--remove-tools-declarations"
        };
        new MergerWithMock().process(args);
        verify(mInvoker).withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testLibParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--libs", "src/lib/AndroidManifest.xml" };
        new MergerWithMock().process(args);
        verify(mInvoker).addLibraryManifest(new File("src/lib/AndroidManifest.xml"));
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testLibsParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--libs", "src/lib1/AndroidManifest.xml" + File.pathSeparator
                + "src/lib2/AndroidManifest.xml" + File.pathSeparator
                + "src/lib3/AndroidManifest.xml" };
        new MergerWithMock().process(args);
        verify(mInvoker).addLibraryManifest(new File("src/lib1/AndroidManifest.xml"));
        verify(mInvoker).addLibraryManifest(new File("src/lib2/AndroidManifest.xml"));
        verify(mInvoker).addLibraryManifest(new File("src/lib3/AndroidManifest.xml"));
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testOverlayParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--overlays", "src/flavor1/AndroidManifest.xml" };
        new MergerWithMock().process(args);
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor1/AndroidManifest.xml"));
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testOverlaysParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--overlays", "src/flavor1/AndroidManifest.xml" + File.pathSeparator
                + "src/flavor2/AndroidManifest.xml" + File.pathSeparator
                + "src/flavor3/AndroidManifest.xml" };
        new MergerWithMock().process(args);
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor1/AndroidManifest.xml"));
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor2/AndroidManifest.xml"));
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor3/AndroidManifest.xml"));
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testPropertyParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--property", "min_sdk_version=19" };
        new MergerWithMock().process(args);
        verify(mInvoker).setOverride(ManifestSystemProperty.MIN_SDK_VERSION, "19");
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testInvalidPropertyParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--property", "Foo=19" };
        final MockLog iLogger = new MockLog();
        Merger merger = new MergerWithMock() {
            @Override
            protected ILogger createLogger(@NonNull StdLogger.Level level) {
                return iLogger;
            }
        };
        // check that return value marked the failure.
        assertEquals(1, merger.process(args));
        assertEquals(2, iLogger.getMessages().size());
        assertTrue(iLogger.getMessages().get(1).startsWith(
                "E Invalid property name Foo, allowed properties are :"));
    }

    public void testInvalidFormatPropertyParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--property", "Foo:19" };
        final MockLog iLogger = new MockLog();
        Merger merger = new MergerWithMock() {
            @Override
            protected ILogger createLogger(@NonNull StdLogger.Level level) {
                return iLogger;
            }
        };
        // check that return value marked the failure.
        assertEquals(1, merger.process(args));
        assertEquals(1, iLogger.getMessages().size());
        assertEquals("E Invalid property setting, should be NAME=VALUE format",
                iLogger.getMessages().get(0));
    }

    public void testPlaceholderParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--placeholder", "foo=bar" };
        new MergerWithMock().process(args);
        verify(mInvoker).setPlaceHolderValue("foo", "bar");
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);
    }

    public void testInvalidFormatPlaceholderParameter()
            throws FileNotFoundException, ManifestMerger2.MergeFailureException {
        final String[] args = { "--main", "src/main/AndroidManifest.xml",
                "--placeholder", "Foo:19" };
        final MockLog iLogger = new MockLog();
        Merger merger = new MergerWithMock() {
            @Override
            protected ILogger createLogger(@NonNull StdLogger.Level level) {
                return iLogger;
            }
        };
        // check that return value marked the failure.
        assertEquals(1, merger.process(args));
        assertEquals(1, iLogger.getMessages().size());
        assertEquals("E Invalid placeholder setting, should be NAME=VALUE format",
                iLogger.getMessages().get(0));
    }

    public void testCombinedParameters()
            throws IOException, ManifestMerger2.MergeFailureException {

        File outFile = File.createTempFile("test", "merger");

        final String[] args = {
            "--main",
            "src/main/AndroidManifest.xml",
            "--libs",
            "src/lib1/AndroidManifest.xml"
                    + File.pathSeparator
                    + "src/lib2/AndroidManifest.xml"
                    + File.pathSeparator
                    + "src/lib3/AndroidManifest.xml",
            "--overlays",
            "src/flavor1/AndroidManifest.xml"
                    + File.pathSeparator
                    + "src/flavor2/AndroidManifest.xml"
                    + File.pathSeparator
                    + "src/flavor3/AndroidManifest.xml",
            "--placeholder",
            "Foo=bar",
            "--property",
            "max_sdk_version=21",
            "--out",
            outFile.getAbsolutePath(),
            "--remove-tools-declarations"
        };
        Merger merger = new MergerWithMock() {
            @Override
            protected ManifestMerger2.Invoker createInvoker(@NonNull File mainManifestFile, @NonNull ILogger logger) {
                try {
                    XmlDocument xmlDocument = Mockito.mock(XmlDocument.class);
                    when(mMergingReport.getResult()).thenReturn(MergingReport.Result.SUCCESS);
                    when(mMergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED))
                            .thenReturn("Pretty combined");
                    when(mInvoker.merge()).thenReturn(mMergingReport);
                } catch (ManifestMerger2.MergeFailureException e) {
                    fail(e.getMessage());
                }
                return mInvoker;
            }
        };
        merger.process(args);
        verify(mInvoker).withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
        verify(mInvoker).addLibraryManifest(new File("src/lib1/AndroidManifest.xml"));
        verify(mInvoker).addLibraryManifest(new File("src/lib2/AndroidManifest.xml"));
        verify(mInvoker).addLibraryManifest(new File("src/lib3/AndroidManifest.xml"));
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor1/AndroidManifest.xml"));
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor2/AndroidManifest.xml"));
        verify(mInvoker).addFlavorAndBuildTypeManifest(new File("src/flavor3/AndroidManifest.xml"));
        verify(mInvoker).setOverride(ManifestSystemProperty.MAX_SDK_VERSION, "21");
        verify(mInvoker).setPlaceHolderValue("Foo", "bar");
        verify(mInvoker).merge();
        verifyNoMoreInteractions(mInvoker);

        // check the resulting file content.
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(outFile);
            CharBuffer buffer = CharBuffer.allocate(256);
            fileReader.read(buffer);
            int endOfRead = buffer.position();
            assertEquals("Pretty combined", buffer.rewind().toString().substring(0, endOfRead));
        } finally {
            if (fileReader != null) {
                fileReader.close();
            }
        }
        assertTrue(outFile.delete());
    }

    public void testMergeSuccess() throws IOException, ManifestMerger2.MergeFailureException {

        final String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.test\">\n"
                        + "\n"
                        + "    <application android:label=\"app\"/>\n"
                        + "\n"
                        + "</manifest>";
        File tmpFile = TestUtils.inputAsFile("testMergeSuccess", input);
        assertTrue(tmpFile.exists());

        final String[] args = {"--main", tmpFile.toString()};
        Merger merger = new Merger();
        // check that return value 0 for successful merge
        assertEquals(0, merger.process(args));
        assertTrue(tmpFile.delete());
    }

    public void testMergeFailure() throws IOException, ManifestMerger2.MergeFailureException {

        final String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.test\">\n"
                        + "\n"
                        + "    <application android:label=\"app\">\n"
                        + "        <activity>\n"
                        + "            <intent-filter/>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File tmpFile = TestUtils.inputAsFile("testMergeFailure", input);
        assertTrue(tmpFile.exists());

        final String[] args = {"--main", tmpFile.toString()};
        Merger merger = new Merger();
        // check that return value 1 for failed merge
        assertEquals(1, merger.process(args));
        assertTrue(tmpFile.delete());
    }
}
