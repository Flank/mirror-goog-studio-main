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

package com.android.repository;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

public class RevisionTest extends TestCase {

    public final void testRevision() {

        assertThat(Revision.parseRevision("5").toString()).isEqualTo("5");
        assertThat(Revision.parseRevision("5.0").toString()).isEqualTo("5.0");
        assertThat(Revision.parseRevision("5.0.0").toString()).isEqualTo("5.0.0");
        assertThat(Revision.parseRevision("5.1.4").toString()).isEqualTo("5.1.4");
        assertThat(Revision.parseRevision("5", Revision.Precision.MICRO).toString())
                .isEqualTo("5.0.0");
        assertThat(Revision.parseRevision("5.1", Revision.Precision.MICRO).toString())
                .isEqualTo("5.1.0");

        Revision p = new Revision(5);
        assertThat(p.getMajor()).isEqualTo(5);
        assertThat(p.getMinor()).isEqualTo(Revision.IMPLICIT_MINOR_REV);
        assertThat(p.getMicro()).isEqualTo(Revision.IMPLICIT_MICRO_REV);
        assertThat(p.getPreview()).isEqualTo(Revision.NOT_A_PREVIEW);
        assertThat(p.isPreview()).isFalse();
        assertThat(p.toShortString()).isEqualTo("5");
        assertThat(Revision.parseRevision("5")).isEqualTo(p);
        assertThat(p.toString()).isEqualTo("5");
        assertThat(Revision.parseRevision("5")).isEqualTo(p);
        assertThat(p.toIntArray(false /*includePreview*/)).asList().containsExactly(5);
        assertThat(p.toIntArray(true  /*includePreview*/)).asList().containsExactly(5);

        p = new Revision(5, 0);
        assertThat(p.getMajor()).isEqualTo(5);
        assertThat(p.getMinor()).isEqualTo(0);
        assertThat(p.getMicro()).isEqualTo(Revision.IMPLICIT_MICRO_REV);
        assertThat(p.getPreview()).isEqualTo(Revision.NOT_A_PREVIEW);
        assertThat(p.isPreview()).isFalse();
        assertThat(p.toShortString()).isEqualTo("5");
        assertThat(Revision.parseRevision("5")).isEqualTo(new Revision(5));
        assertThat(p.toString()).isEqualTo("5.0");
        assertThat(Revision.parseRevision("5.0")).isEqualTo(p);
        assertThat(p.toIntArray(false /*includePreview*/)).asList().containsExactly(5, 0).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList().containsExactly(5, 0).inOrder();

        p = new Revision(5, 0, 0);
        assertThat(p.getMajor()).isEqualTo(5);
        assertThat(p.getMinor()).isEqualTo(0);
        assertThat(p.getMicro()).isEqualTo(0);
        assertThat(p.getPreview()).isEqualTo(Revision.NOT_A_PREVIEW);
        assertThat(p.isPreview()).isFalse();
        assertThat(p.toShortString()).isEqualTo("5");
        assertThat(Revision.parseRevision("5")).isEqualTo(new Revision(5));
        assertThat(p.toString()).isEqualTo("5.0.0");
        assertThat(Revision.parseRevision("5.0.0")).isEqualTo(p);
        assertThat(p.toIntArray(false /*includePreview*/)).asList()
                .containsExactly(5, 0, 0).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList()
                .containsExactly(5, 0, 0).inOrder();

        p = new Revision(5, 0, 0, 6);
        assertThat(p.getMajor()).isEqualTo(5);
        assertThat(p.getMinor()).isEqualTo(Revision.IMPLICIT_MINOR_REV);
        assertThat(p.getMicro()).isEqualTo(Revision.IMPLICIT_MICRO_REV);
        assertThat(p.getPreview()).isEqualTo(6);
        assertThat(p.isPreview()).isTrue();
        assertThat(p.toShortString()).isEqualTo("5 rc6");
        assertThat(p.toString()).isEqualTo("5.0.0 rc6");
        assertThat(Revision.parseRevision("5.0.0 rc6")).isEqualTo(p);
        assertThat(Revision.parseRevision("5.0.0-rc6").toString()).isEqualTo("5.0.0-rc6");
        assertThat(p.toIntArray(false /*includePreview*/)).asList()
                .containsExactly(5, 0, 0).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList()
                .containsExactly(5, 0, 0, 6).inOrder();

        p = new Revision(6, 7, 0);
        assertThat(p.getMajor()).isEqualTo(6);
        assertThat(p.getMinor()).isEqualTo(7);
        assertThat(p.getMicro()).isEqualTo(0);
        assertThat(p.getPreview()).isEqualTo(0);
        assertThat(p.isPreview()).isFalse();
        assertThat(p.toShortString()).isEqualTo("6.7");
        assertThat(p).isNotEqualTo(Revision.parseRevision("6.7"));
        assertThat(Revision.parseRevision("6.7")).isEqualTo(new Revision(6, 7));
        assertThat(p.toString()).isEqualTo("6.7.0");
        assertThat(Revision.parseRevision("6.7.0")).isEqualTo(p);
        assertThat(p.toIntArray(false /*includePreview*/)).asList()
                .containsExactly(6, 7, 0).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList()
                .containsExactly(6, 7, 0).inOrder();

        p = new Revision(10, 11, 12, Revision.NOT_A_PREVIEW);
        assertThat(p.getMajor()).isEqualTo(10);
        assertThat(p.getMinor()).isEqualTo(11);
        assertThat(p.getMicro()).isEqualTo(12);
        assertThat(p.getPreview()).isEqualTo(0);
        assertThat(p.isPreview()).isFalse();
        assertThat(p.toShortString()).isEqualTo("10.11.12");
        assertThat(p.toString()).isEqualTo("10.11.12");
        assertThat(p.toIntArray(false /*includePreview*/)).asList()
                .containsExactly(10, 11, 12).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList()
                .containsExactly(10, 11, 12, 0).inOrder();

        p = new Revision(10, 11, 12, 13);
        assertThat(p.getMajor()).isEqualTo(10);
        assertThat(p.getMinor()).isEqualTo(11);
        assertThat(p.getMicro()).isEqualTo(12);
        assertThat(p.getPreview()).isEqualTo(13);
        assertThat(p.isPreview()).isTrue();
        assertThat(p.toShortString()).isEqualTo("10.11.12 rc13");
        assertThat(p.toString()).isEqualTo("10.11.12 rc13");
        assertThat(Revision.parseRevision("10.11.12 rc13")).isEqualTo(p);
        assertThat(Revision.parseRevision("   10.11.12 rc13")).isEqualTo(p);
        assertThat(Revision.parseRevision("10.11.12 rc13   ")).isEqualTo(p);
        assertThat(Revision.parseRevision("   10.11.12   rc13   ")).isEqualTo(p);
        assertThat(p.toIntArray(false /*includePreview*/)).asList()
                .containsExactly(10, 11, 12).inOrder();
        assertThat(p.toIntArray(true  /*includePreview*/)).asList()
                .containsExactly(10, 11, 12, 13).inOrder();
    }

    public final void testParse() {
        Revision revision = Revision.parseRevision("1");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(0);
        assertThat(revision.getMicro()).isEqualTo(0);
        assertThat(revision.isPreview()).isFalse();

        revision = Revision.parseRevision("1.2");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(2);
        assertThat(revision.getMicro()).isEqualTo(0);
        assertThat(revision.isPreview()).isFalse();

        revision = Revision.parseRevision("1.2.3");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(2);
        assertThat(revision.getMicro()).isEqualTo(3);
        assertThat(revision.isPreview()).isFalse();

        revision = Revision.parseRevision("1.2.3-rc4");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(2);
        assertThat(revision.getMicro()).isEqualTo(3);
        assertThat(revision.isPreview()).isTrue();
        assertThat(revision.getPreview()).isEqualTo(4);

        revision = Revision.parseRevision("1.2.3-alpha5");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(2);
        assertThat(revision.getMicro()).isEqualTo(3);
        assertThat(revision.isPreview()).isTrue();
        assertThat(revision.getPreview()).isEqualTo(5);

        revision = Revision.parseRevision("1.2.3-beta6");
        assertThat(revision).isNotNull();
        assertThat(revision.getMajor()).isEqualTo(1);
        assertThat(revision.getMinor()).isEqualTo(2);
        assertThat(revision.getMicro()).isEqualTo(3);
        assertThat(revision.isPreview()).isTrue();
        assertThat(revision.getPreview()).isEqualTo(6);

        try {
            Revision.parseRevision("1.2.3-preview");
            fail();
        } catch (NumberFormatException ignored) {}

        revision = Revision.safeParseRevision("1.2.3-preview");
        assertThat(Revision.NOT_SPECIFIED).isEqualTo(revision);
    }

    public final void testParseError() {
        String errorMsg = null;
        try {
            Revision.parseRevision("not a number");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertThat(errorMsg).isEqualTo("Invalid revision: not a number");

        errorMsg = null;
        try {
            Revision.parseRevision("5 .6 .7");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertThat(errorMsg).isEqualTo("Invalid revision: 5 .6 .7");

        errorMsg = null;
        try {
            Revision.parseRevision("5.0.0 preview 1");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertThat(errorMsg).isEqualTo("Invalid revision: 5.0.0 preview 1");

        errorMsg = null;
        try {
            Revision.parseRevision("  5.1.2 rc 42  ");
            fail("Revision.parseRevision should thrown NumberFormatException");
        } catch (NumberFormatException e) {
            errorMsg = e.getMessage();
        }
        assertThat(errorMsg).isEqualTo("Invalid revision:   5.1.2 rc 42  ");
    }

    public final void testCompareTo() {
        // TODO: What's the deal with these variable names?
        Revision s4 = new Revision(4);
        Revision i4 = new Revision(4);
        Revision g5 = new Revision(5, 1, 0, 6);
        Revision y5 = new Revision(5);
        Revision c5 = new Revision(5, 1, 0, 6);
        Revision o5 = new Revision(5, 0, 0, 7);
        Revision p5 = new Revision(5, 1, 0, 0);
        Revision q5 = new Revision(5, 1, 0, 7);

        assertThat(i4).isEqualTo(s4);  // 4.0.0-0 == 4.0.0-0
        assertThat(c5).isEqualTo(g5);  // 5.1.0-6 == 5.1.0-6

        assertThat(y5).isNotEqualTo(p5);  // 5.0.0-0 != 5.1.0-0
        assertThat(g5).isNotEqualTo(p5);  // 5.1.0-6 != 5.1.0-0
        assertThat(s4).isEquivalentAccordingToCompareTo(i4);  // 4.0.0-0 == 4.0.0-0
        assertThat(s4).isLessThan(y5);  // 4.0.0-0  < 5.0.0-0
        assertThat(y5).isEquivalentAccordingToCompareTo(y5);  // 5.0.0-0 == 5.0.0-0
        assertThat(y5).isLessThan(p5);  // 5.0.0-0  < 5.1.0-0
        assertThat(o5).isLessThan(y5);  // 5.0.0-7  < 5.0.0-0
        assertThat(p5).isEquivalentAccordingToCompareTo(p5);  // 5.1.0-0 == 5.1.0-0
        assertThat(c5).isLessThan(p5);  // 5.1.0-6  < 5.1.0-0
        assertThat(p5).isGreaterThan(c5);  // 5.1.0-0  > 5.1.0-6
        assertThat(p5).isGreaterThan(o5);  // 5.1.0-0  > 5.0.0-7
        assertThat(c5).isGreaterThan(o5);  // 5.1.0-6  > 5.0.0-7
        assertThat(o5).isEquivalentAccordingToCompareTo(o5);  // 5.0.0-7 == 5.0.0-7

        assertEquals(0, s4.compareTo(i4, Revision.PreviewComparison.ASCENDING));
        assertEquals(0, s4.compareTo(i4, Revision.PreviewComparison.IGNORE));
        assertEquals(0, s4.compareTo(i4, Revision.PreviewComparison.COMPARE_NUMBER));
        assertEquals(0, s4.compareTo(i4, Revision.PreviewComparison.COMPARE_TYPE));

        assertTrue(y5.compareTo(o5, Revision.PreviewComparison.ASCENDING) < 0);
        assertTrue(y5.compareTo(o5, Revision.PreviewComparison.IGNORE) == 0);
        assertTrue(y5.compareTo(o5, Revision.PreviewComparison.COMPARE_NUMBER) > 0);
        assertTrue(y5.compareTo(o5, Revision.PreviewComparison.COMPARE_TYPE) > 0);

        assertTrue(c5.compareTo(q5, Revision.PreviewComparison.ASCENDING) < 0);
        assertTrue(c5.compareTo(q5, Revision.PreviewComparison.IGNORE) == 0);
        assertTrue(c5.compareTo(q5, Revision.PreviewComparison.COMPARE_NUMBER) < 0);
        assertTrue(c5.compareTo(q5, Revision.PreviewComparison.COMPARE_TYPE) == 0);

        assertTrue(s4.compareTo(y5, Revision.PreviewComparison.ASCENDING) < 0);
        assertTrue(s4.compareTo(y5, Revision.PreviewComparison.IGNORE) < 0);
        assertTrue(s4.compareTo(y5, Revision.PreviewComparison.COMPARE_NUMBER) < 0);
        assertTrue(s4.compareTo(y5, Revision.PreviewComparison.COMPARE_TYPE) < 0);
    }
}