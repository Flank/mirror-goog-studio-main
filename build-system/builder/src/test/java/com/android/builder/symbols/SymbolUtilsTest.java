/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.symbols;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.io.StreamException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

/** Tests for {@link SymbolUtils} class. */
public class SymbolUtilsTest {

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testProguardRulesGeneration()
            throws IOException, StreamException, SAXException, ParserConfigurationException {
        String manifest =
                ""
                        + "<manifest package=\"org.sampleapp.android\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "\n"
                        + "    <application\n"
                        + "            android:name=\".SampleApp\"\n"
                        + "            android:backupAgent=\"backupAgent\">\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.SALaunchActivity\">\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.main.WPMainActivity\"\n"
                        + "                android:theme=\"@style/Calypso.NoActionBar\" />\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.accounts.SignInActivity\"\n"
                        + "                android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "                android:theme=\"@style/SignInTheme\"\n"
                        + "                android:windowSoftInputMode=\"adjustResize\">\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.accounts.NewBlogActivity\"\n"
                        + "                android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "                android:theme=\"@style/SignInTheme\"\n"
                        + "                android:windowSoftInputMode=\"adjustResize\" />\n"
                        + "\n"
                        + "        <service\n"
                        + "                android:name=\".ui.posts.services.Service1\"\n"
                        + "                android:label=\"Service 1\" />\n"
                        + "        <service\n"
                        + "                android:name=\".ui.posts.services.Service2\"\n"
                        + "                android:exported=\"false\"\n"
                        + "                android:label=\"Service 2\" />\n"
                        + "\n"
                        + "        <receiver android:name=\".ui.notifications.Receiver\" />\n"
                        + "\n"
                        + "        <provider\n"
                        + "                android:name=\"android.support.v4.content.FileProvider\"\n"
                        + "                android:authorities=\"${applicationId}.provider\"\n"
                        + "                android:exported=\"false\"\n"
                        + "                android:grantUriPermissions=\"true\">\n"
                        + "            <meta-data\n"
                        + "                    android:name=\"android.support.FILE_PROVIDER_PATHS\"\n"
                        + "                    android:resource=\"@xml/provider_paths\"/>\n"
                        + "        </provider>\n"
                        + "    </application>\n"
                        + "    <instrumentation android:name=\".Instrument\"/>"
                        + "</manifest>\n";

        InputStream stream = new ByteArrayInputStream(manifest.getBytes());
        ManifestData manifestData = new AndroidManifestParser().parse(stream);

        assertNotNull(manifestData);

        assertThat(SymbolUtils.generateKeepRules(manifestData, false))
                .containsExactly(
                        "# Generated by the gradle plugin",
                        "-keep class org.sampleapp.android.SampleApp { <init>(...); }",
                        "-keep class org.sampleapp.android.backupAgent { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.SALaunchActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.main.WPMainActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.accounts.SignInActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.accounts.NewBlogActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.posts.services.Service1 { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.posts.services.Service2 { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.notifications.Receiver { <init>(...); }",
                        "-keep class android.support.v4.content.FileProvider { <init>(...); }",
                        "-keep class org.sampleapp.android.Instrument { <init>(...); }");
    }

    @Test
    public void testMainDexRulesGeneration()
            throws SAXException, StreamException, ParserConfigurationException, IOException {
        String manifest =
                ""
                        + "<manifest package=\"org.sampleapp.android\"\n"
                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "\n"
                        + "    <application\n"
                        + "            android:name=\".SampleApp\"\n"
                        + "            android:backupAgent=\"backupAgent\"\n"
                        + "            android:process=\"DefaultProcess\">\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.SALaunchActivity\">\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.main.WPMainActivity\"\n"
                        + "                android:theme=\"@style/Calypso.NoActionBar\"\n"
                        + "                android:process=\":PrivateProcess\"/>\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.accounts.SignInActivity\"\n"
                        + "                android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "                android:theme=\"@style/SignInTheme\"\n"
                        + "                android:windowSoftInputMode=\"adjustResize\">\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "                android:name=\".ui.accounts.NewBlogActivity\"\n"
                        + "                android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "                android:theme=\"@style/SignInTheme\"\n"
                        + "                android:windowSoftInputMode=\"adjustResize\"\n"
                        + "                android:process=\"SubProcess\"/>\n"
                        + "\n"
                        + "        <service\n"
                        + "                android:name=\".ui.posts.services.Service1\"\n"
                        + "                android:label=\"Service 1\"\n"
                        + "                android:process=\":PrivateProcess\"/>\n"
                        + "        <service\n"
                        + "                android:name=\".ui.posts.services.Service2\"\n"
                        + "                android:exported=\"false\"\n"
                        + "                android:label=\"Service 2\" />\n"
                        + "\n"
                        + "        <receiver android:name=\".ui.notifications.Receiver\"\n"
                        + "                android:process=\":PrivateProcess\"/>\n"
                        + "\n"
                        + "        <provider\n"
                        + "                android:name=\"android.support.v4.content.FileProvider\"\n"
                        + "                android:authorities=\"${applicationId}.provider\"\n"
                        + "                android:exported=\"false\"\n"
                        + "                android:grantUriPermissions=\"true\">\n"
                        + "            <meta-data\n"
                        + "                    android:name=\"android.support.FILE_PROVIDER_PATHS\"\n"
                        + "                    android:resource=\"@xml/provider_paths\"/>\n"
                        + "        </provider>\n"
                        + "    </application>\n"
                        + "    <instrumentation android:name=\".Instrument\"/>"
                        + "</manifest>\n";

        InputStream stream = new ByteArrayInputStream(manifest.getBytes());
        ManifestData manifestData = new AndroidManifestParser().parse(stream);

        assertNotNull(manifestData);

        assertThat(SymbolUtils.generateKeepRules(manifestData, true))
                .containsExactly(
                        "# Generated by the gradle plugin",
                        "-keep class org.sampleapp.android.SampleApp { <init>(...); }",
                        "-keep class org.sampleapp.android.backupAgent { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.SALaunchActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.accounts.SignInActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.accounts.NewBlogActivity { <init>(...); }",
                        "-keep class org.sampleapp.android.ui.posts.services.Service2 { <init>(...); }",
                        "-keep class android.support.v4.content.FileProvider { <init>(...); }",
                        "-keep class org.sampleapp.android.Instrument { <init>(...); }");
    }
}
