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
package com.android.ide.common.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class GradleVersionTest {

    @Test
    public void testParseOneSegment() {
        GradleVersion version = GradleVersion.parse("2");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("2", version.toString());
        assertFalse(version.isPreview());
    }

    @Test
    public void testParseOneSegmentWithPlus() {
        GradleVersion version = GradleVersion.parse("+");
        assertEquals(Integer.MAX_VALUE, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("+", version.toString());
    }

    @Test
    public void testParseOneSegmentWithPreview() {
        GradleVersion version = GradleVersion.parse("2-alpha1");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(1, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("2-alpha1", version.toString());
        assertTrue(version.isPreview());
    }

    @Test
    public void testParseOneSegmentWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("2-alpha1-SNAPSHOT");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(1, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-alpha1-SNAPSHOT", version.toString());
        assertTrue(version.isPreview());
    }

    @Test
    public void testParseOneSegmentWithSnapshot() {
        GradleVersion version = GradleVersion.parse("2-SNAPSHOT");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-SNAPSHOT", version.toString());
        assertTrue(version.isPreview());
    }

    @Test
    public void testParseOneSegmentWithSnapshot_lowerCase() {
        GradleVersion version = GradleVersion.parse("2-snapshot");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-snapshot", version.toString());
    }

    @Test
    public void testParseOneSegmentWithDev() {
        GradleVersion version = GradleVersion.parse("2-dev");
        assertEquals(2, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("2-dev", version.toString());
    }

    @Test
    public void testParseTwoSegments() {
        GradleVersion version = GradleVersion.parse("1.2");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithPlus() {
        GradleVersion version = GradleVersion.parse("1.+");
        assertEquals(1, version.getMajor());
        assertEquals(Integer.MAX_VALUE, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.+", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithPreview() {
        GradleVersion version = GradleVersion.parse("1.2-alpha3");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(3, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2-alpha3", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2-alpha3-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(3, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2-alpha3-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseTwoSegmentsWithSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseThreeSegments() {
        GradleVersion version = GradleVersion.parse("1.2.3");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPlus() {
        GradleVersion version = GradleVersion.parse("1.2.+");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(Integer.MAX_VALUE, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.+", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreview() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha4");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3-alpha4", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreview2() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha-4");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3-alpha-4", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreviewAndSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha4-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2.3-alpha4-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithPreviewAndSnapshot2() {
        GradleVersion version = GradleVersion.parse("1.2.3-alpha-4-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(4, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());
        assertEquals("1.2.3-alpha-4-SNAPSHOT", version.toString());
    }

    @Test
    public void testParseThreeSegmentsWithSnapshot() {
        GradleVersion version = GradleVersion.parse("1.2.3-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());
    }

    @Test
    public void testParseWithMoreThanThreeSegments1() {
        GradleVersion version = GradleVersion.parse("1.2.3.4");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());

        List<GradleVersion.VersionSegment> additional = version.getAdditionalSegments();
        assertEquals(1, additional.size());

        GradleVersion.VersionSegment fourth = additional.get(0);
        assertEquals(4, fourth.getValue());
    }

    @Test
    public void testParseWithMoreThanThreeSegments2() {
        GradleVersion version = GradleVersion.parse("1.2.3.4.5-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertTrue(version.isSnapshot());

        List<GradleVersion.VersionSegment> additional = version.getAdditionalSegments();
        assertEquals(2, additional.size());

        GradleVersion.VersionSegment fourth = additional.get(0);
        assertEquals(4, fourth.getValue());

        GradleVersion.VersionSegment fifth = additional.get(1);
        assertEquals(5, fifth.getValue());
    }


    @Test
    public void testParseWithMoreThanThreeSegments3() {
        GradleVersion version = GradleVersion.parse("1.2.3.4.5.6-alpha9-SNAPSHOT");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
        assertEquals(9, version.getPreview());
        assertEquals("alpha", version.getPreviewType());
        assertTrue(version.isSnapshot());

        List<GradleVersion.VersionSegment> additional = version.getAdditionalSegments();
        assertEquals(3, additional.size());

        GradleVersion.VersionSegment fourth = additional.get(0);
        assertEquals(4, fourth.getValue());

        GradleVersion.VersionSegment fifth = additional.get(1);
        assertEquals(5, fifth.getValue());

        GradleVersion.VersionSegment sixth = additional.get(2);
        assertEquals(6, sixth.getValue());
    }

    @Test
    public void testCompare() {
        assertEquals(0, GradleVersion.parse("1.0.0").compareTo("1.0.0"));
        assertEquals(0, GradleVersion.parse("1.0.0-alpha1").compareTo("1.0.0-alpha1"));
        assertEquals(0, GradleVersion.parse("1.0.0-SNAPSHOT").compareTo("1.0.0-SNAPSHOT"));

        assertTrue(GradleVersion.parse("1.0.1").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("+").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.+").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.0.+").compareTo("1.0.0") > 0);

        assertTrue(GradleVersion.parse("1.0.1").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.1.0").compareTo("1.0.0") > 0);
        assertTrue(GradleVersion.parse("1.1.1").compareTo("1.0.0") > 0);

        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.1") < 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.1.0") < 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.1.1") < 0);

        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.0-alpha1") > 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.1-alpha1") < 0);
        assertTrue(GradleVersion.parse("1.0.0").compareTo("1.0.0-SNAPSHOT") > 0);
        assertTrue(GradleVersion.parse("1.0.0-alpha2").compareTo("1.0.0-alpha1") > 0);
        assertTrue(GradleVersion.parse("1.0.0-beta1").compareTo("1.0.0-alpha2") > 0);
        assertTrue(GradleVersion.parse("1.0.0-rc1").compareTo("1.0.0-alpha2") > 0);
        assertTrue(GradleVersion.parse("2.0.0-alpha1").compareTo("1.0.0-alpha1") > 0);
    }

    @Test
    public void testCompareWithExcludeAllComparison() {
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0"));
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0-alpha1"));
        assertEquals(0, GradleVersion.parse("1.0.0").compareIgnoringQualifiers("1.0.0-SNAPSHOT"));
    }

    @Test
    public void testGetSegments() {
        GradleVersion version = GradleVersion.parse("1.2.3-SNAPSHOT");
        assertNotNull(version.getMinorSegment());
        assertNotNull(version.getMicroSegment());
        assertEquals("1", version.getMajorSegment().getText());
        assertEquals("2", version.getMinorSegment().getText());
        assertEquals("3", version.getMicroSegment().getText());
        assertFalse(version.getMajorSegment().acceptsGreaterValue());
        assertFalse(version.getMinorSegment().acceptsGreaterValue());
        assertFalse(version.getMicroSegment().acceptsGreaterValue());

        version = GradleVersion.parse("1.2.+");
        assertNotNull(version.getMinorSegment());
        assertNotNull(version.getMicroSegment());
        assertEquals("1", version.getMajorSegment().getText());
        assertEquals("2", version.getMinorSegment().getText());
        assertEquals("+", version.getMicroSegment().getText());
        assertFalse(version.getMajorSegment().acceptsGreaterValue());
        assertFalse(version.getMinorSegment().acceptsGreaterValue());
        assertTrue(version.getMicroSegment().acceptsGreaterValue());

        version = GradleVersion.parse("+");
        assertEquals("+", version.getMajorSegment().getText());
        assertTrue(version.getMajorSegment().acceptsGreaterValue());
        assertNull(version.getMinorSegment());
        assertNull(version.getMicroSegment());
    }

    @Test
    public void testConstructorWith3Numbers() {
        GradleVersion version = new GradleVersion(1, 2, 3);
        assertNotNull(version.getMajorSegment());
        assertNotNull(version.getMinorSegment());
        assertNotNull(version.getMicroSegment());
        assertEquals(1, version.getMajor());
        assertEquals("1", version.getMajorSegment().getText());
        assertEquals(2, version.getMinor());
        assertEquals("2", version.getMinorSegment().getText());
        assertEquals(3, version.getMicro());
        assertEquals("3", version.getMicroSegment().getText());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2.3", version.toString());
    }

    @Test
    public void testConstructorWith2Numbers() {
        GradleVersion version = new GradleVersion(1, 2);
        assertNotNull(version.getMajorSegment());
        assertNotNull(version.getMinorSegment());
        assertNull(version.getMicroSegment());
        assertEquals(1, version.getMajor());
        assertEquals("1", version.getMajorSegment().getText());
        assertEquals(2, version.getMinor());
        assertEquals("2", version.getMinorSegment().getText());
        assertEquals(0, version.getMicro());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("1.2", version.toString());
    }

    // See https://code.google.com/p/android/issues/detail?id=201325
    @Test
    public void testFixFor201325() {
        // 2.0+ is equivalent to 2.0.+
        GradleVersion version = GradleVersion.parse("2.0+");
        assertNotNull(version.getMinorSegment());
        assertNotNull(version.getMicroSegment());
        assertEquals(2, version.getMajor());
        assertEquals("2", version.getMajorSegment().getText());
        assertEquals(0, version.getMinor());
        assertEquals("0", version.getMinorSegment().getText());
        assertTrue(version.getMicroSegment().acceptsGreaterValue());
        assertEquals(0, version.getPreview());
        assertNull(version.getPreviewType());
        assertFalse(version.isSnapshot());
        assertEquals("2.0+", version.toString());
    }

    @Test
    public void testParseArbitraryQualifiers1() {
        GradleVersion version = GradleVersion.parse("1.2-XTEXT-PATCHED");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(0, version.getMicro());
    }

    @Test
    public void testParseArbitraryQualifiers2() {
        GradleVersion version = GradleVersion.parse("1.2.3-incubating");
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getMicro());
    }

    @Test
    public void testMax() {
        GradleVersion version1 = GradleVersion.parse("2.1.3");
        GradleVersion version2 = GradleVersion.parse("2.2");

        assertNull(GradleVersion.max(null, null));
        assertEquals(version1, GradleVersion.max(version1, null));
        assertEquals(version1, GradleVersion.max(null, version1));
        assertEquals(version2, GradleVersion.max(version1, version2));
        assertEquals(version2, GradleVersion.max(version2, version1));
    }

    @Test
    public void testParseArbitraryQualifiers3() {
        GradleVersion version = GradleVersion.parse("r09");
        assertEquals(0, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getMicro());
        assertEquals("r09", version.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWithPlusAtBeginningOfSegment() {
        GradleVersion.parse("2.+1");
    }

    @Test
    public void testIsAtLeast() {
        GradleVersion version = GradleVersion.parse("2.1.3");

        assertTrue(version.isAtLeast(2, 1, 3));
        assertFalse(version.isAtLeast(2, 1, 4));
        assertFalse(version.isAtLeast(2, 2, 0));
        assertFalse(version.isAtLeast(3, 0, 0));

        assertTrue(version.isAtLeast(2, 1, 2));
        assertTrue(version.isAtLeast(1, 2, 3));

        version = GradleVersion.parse("2+");
        assertFalse(version.isAtLeast(3, 0, 0));
        assertTrue(version.isAtLeast(1, 5, 1));

        version = GradleVersion.parse("2.3.0-dev");
        assertTrue(version.isAtLeast(2, 2, 0));
        assertTrue(version.isAtLeast(2, 3, 0, null, 0, true));
        assertFalse(version.isAtLeast(2, 3, 0));
        assertFalse(version.isAtLeast(2, 4, 0));

        version = GradleVersion.parse("2.3.0-beta1");
        assertTrue(version.isAtLeast(2, 3, 0, "beta", 1, false));
        assertTrue(version.isAtLeast(2, 3, 0, "alpha", 8, false));
        assertFalse(version.isAtLeast(2, 3, 0, "beta", 2, false));
        assertFalse(version.isAtLeast(2, 3, 0, "rc", 1, false));
    }
}