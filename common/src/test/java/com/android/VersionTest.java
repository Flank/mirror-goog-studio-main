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

package com.android;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

public class VersionTest {

    @Test
    public void testVersion() {
        Assert.assertNotNull(Version.ANDROID_GRADLE_PLUGIN_VERSION);
    }

    @Test
    public void testBundledVersionConsistency() throws IOException {
        String path = System.getProperty("test.version.properties");
        if (path != null) {
            try (InputStream bundled = Version.class.getResourceAsStream("version.properties");
                    InputStream source = new FileInputStream(path)) {
                Properties bundledProperties = new Properties();
                bundledProperties.load(bundled);

                Properties sourceProperties = new Properties();
                sourceProperties.load(source);

                Assert.assertEquals(sourceProperties, bundledProperties);
            }
        }
    }

    @Test
    public void testAgpAndBaseRelationship() {
        // The Base libraries such as lint are expected to be exactly
        // the AGP version plus 23 (to make it easy for example for
        // external lint check authors who have to depend on both to
        // easily figure out how to combine them.)
        String agp = Version.ANDROID_GRADLE_PLUGIN_VERSION;
        String base = Version.ANDROID_TOOLS_BASE_VERSION;
        // We don't have access to utility classes like GradleCoordinate
        // here so just do pretty simple substitution; this logic may
        // need updating if this test starts failing after a dramatic
        // change to our version string format
        Pattern pattern = Pattern.compile("(\\d+)\\..*");
        Matcher agpMatcher = pattern.matcher(agp);
        Matcher baseMatcher = pattern.matcher(base);
        Assert.assertTrue(agp, agpMatcher.find());
        Assert.assertTrue(base, baseMatcher.find());
        String agpMajorString = agpMatcher.group(1);
        int agpMajor = Integer.parseInt(agpMajorString);
        String expected = "" + (agpMajor + 23) + agp.substring(agpMatcher.end(1));
        Assert.assertEquals(expected, base);
    }
}
