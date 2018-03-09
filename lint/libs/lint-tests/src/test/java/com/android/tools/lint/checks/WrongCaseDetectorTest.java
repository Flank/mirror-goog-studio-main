/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class WrongCaseDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new WrongCaseDetector();
    }

    public void testBasic() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                ""
                        + "res/layout/case.xml:18: Error: Invalid tag <Merge>; should be <merge> [WrongCase]\n"
                        + "<Merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                        + " ~~~~~\n"
                        + "res/layout/case.xml:20: Error: Invalid tag <Fragment>; should be <fragment> [WrongCase]\n"
                        + "    <Fragment android:name=\"foo.bar.Fragment\" />\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/case.xml:21: Error: Invalid tag <Include>; should be <include> [WrongCase]\n"
                        + "    <Include layout=\"@layout/foo\" />\n"
                        + "     ~~~~~~~\n"
                        + "res/layout/case.xml:22: Error: Invalid tag <RequestFocus>; should be <requestFocus> [WrongCase]\n"
                        + "    <RequestFocus />\n"
                        + "     ~~~~~~~~~~~~\n"
                        + "4 errors, 0 warnings\n",
                lintProject(
                        xml(
                                "res/layout/case.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<!--\n"
                                        + "  ~ Copyright (C) 2013 The Android Open Source Project\n"
                                        + "  ~\n"
                                        + "  ~ Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                                        + "  ~ you may not use this file except in compliance with the License.\n"
                                        + "  ~ You may obtain a copy of the License at\n"
                                        + "  ~\n"
                                        + "  ~      http://www.apache.org/licenses/LICENSE-2.0\n"
                                        + "  ~\n"
                                        + "  ~ Unless required by applicable law or agreed to in writing, software\n"
                                        + "  ~ distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                                        + "  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                                        + "  ~ See the License for the specific language governing permissions and\n"
                                        + "  ~ limitations under the License.\n"
                                        + "  -->\n"
                                        + "\n"
                                        + "<Merge xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n"
                                        + "\n"
                                        + "    <Fragment android:name=\"foo.bar.Fragment\" />\n"
                                        + "    <Include layout=\"@layout/foo\" />\n"
                                        + "    <RequestFocus />\n"
                                        + "\n"
                                        + "</Merge>\n")));
    }
}
