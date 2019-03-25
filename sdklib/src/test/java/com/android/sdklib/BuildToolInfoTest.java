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

package com.android.sdklib;

import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import java.io.File;
import junit.framework.TestCase;

public class BuildToolInfoTest extends TestCase {

    public void testGetCurrentJvmVersion() {
        MockFileOp fop = new MockFileOp();
        recordBuildTool23(fop);
        AndroidSdkHandler sdkHandler =
                new AndroidSdkHandler(new File("/sdk"), null, fop);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        BuildToolInfo bt = sdkHandler.getBuildToolInfo(new Revision(23, 0, 2), progress);
        progress.assertNoErrorsOrWarnings();
        assertNotNull(bt);

        // Check the actual JVM running this test.
        Revision curr = BuildToolInfo.getCurrentJvmVersion();
        // We can reasonably expect this to at least run with JVM 1.5 or more
        assertTrue(curr.compareTo(new Revision(1, 5, 0)) > 0);
        // and we can reasonably expect to not be running with JVM 42.0.0
        assertTrue(curr.compareTo(new Revision(42, 0, 0)) < 0);
    }

    public void testIsPresentIn_removedComponent() {
        // Jack and Jill were introduced in 21.1.0 and removed in 28
        Revision rev21_0_0 = new Revision(21, 0, 0);
        assertFalse(BuildToolInfo.PathId.JACK.isPresentIn(rev21_0_0));
        assertFalse(BuildToolInfo.PathId.JILL.isPresentIn(rev21_0_0));

        Revision rev21_1_0 = new Revision(21, 1, 0);
        assertTrue(BuildToolInfo.PathId.JACK.isPresentIn(rev21_1_0));
        assertTrue(BuildToolInfo.PathId.JILL.isPresentIn(rev21_1_0));

        Revision rev23_0_0 = new Revision(23, 0, 0);
        assertTrue(BuildToolInfo.PathId.JACK.isPresentIn(rev23_0_0));
        assertTrue(BuildToolInfo.PathId.JILL.isPresentIn(rev23_0_0));

        Revision rev28_0_0rc1 = new Revision(28, 0, 0, 1);
        assertFalse(BuildToolInfo.PathId.JACK.isPresentIn(rev28_0_0rc1));
        assertFalse(BuildToolInfo.PathId.JILL.isPresentIn(rev28_0_0rc1));

        Revision rev28_0_0 = new Revision(28, 0, 0);
        assertFalse(BuildToolInfo.PathId.JACK.isPresentIn(rev28_0_0));
        assertFalse(BuildToolInfo.PathId.JILL.isPresentIn(rev28_0_0));
    }

    public void testIsPresentIn_componentWithNoRemovedRevision() {
        // Daemon AAPT2 was introduced in 26.0.2
        Revision rev25_0_0 = new Revision(25, 0, 0);
        assertFalse(BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(rev25_0_0));

        Revision rev26_0_0 = new Revision(26, 0, 0);
        assertFalse(BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(rev26_0_0));

        Revision rev26_0_2 = new Revision(26, 0, 2);
        assertTrue(BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(rev26_0_2));

        Revision rev28 = new Revision(28, 0, 0);
        assertTrue(BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(rev28));
    }

    private static void recordBuildTool23(MockFileOp fop) {
        fop.recordExistingFile("/sdk/build-tools/23.0.2/package.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<ns2:sdk-repository "
                        + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                        + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                        + "xmlns:ns4=\"http://schemas.android.com/repository/android/generic/01\" "
                        + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                        + "<license id=\"license-19E6313A\" type=\"text\">License text\n"
                        + "</license><localPackage path=\"build-tools;23.0.2\" obsolete=\"false\">"
                        + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"ns4:genericDetailsType\"/>"
                        + "<revision><major>23</major><minor>0</minor><micro>2</micro></revision>"
                        + "<display-name>Android SDK Build-Tools 23.0.2</display-name>"
                        + "<uses-license ref=\"license-19E6313A\"/></localPackage>"
                        + "</ns2:sdk-repository>\n");
    }

}
