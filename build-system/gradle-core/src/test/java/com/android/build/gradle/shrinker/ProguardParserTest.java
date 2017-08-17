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

package com.android.build.gradle.shrinker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.shrinker.parser.GrammarActions;
import com.android.build.gradle.shrinker.parser.ProguardFlags;
import com.android.build.gradle.shrinker.parser.UnsupportedFlagsHandler;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import org.junit.Test;

public class ProguardParserTest {
    @Test
    public void testDontFlags_noneSet() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse("-dontwarn com.**", flags, UnsupportedFlagsHandler.NO_OP);
        assertFalse(flags.isDontObfuscate());
        assertFalse(flags.isDontShrink());
        assertFalse(flags.isDontOptimize());
    }

    @Test
    public void testDontFlags_dontOptimize() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse(
                "-dontwarn com.**\n-dontoptimize", flags, UnsupportedFlagsHandler.NO_OP);
        assertFalse(flags.isDontObfuscate());
        assertFalse(flags.isDontShrink());
        assertTrue(flags.isDontOptimize());
    }

    @Test
    public void testDontFlags_dontObfuscate() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse(
                "-dontwarn com.**\n-dontobfuscate", flags, UnsupportedFlagsHandler.NO_OP);
        assertTrue(flags.isDontObfuscate());
        assertFalse(flags.isDontShrink());
        assertFalse(flags.isDontOptimize());
    }

    @Test
    public void testDontFlags_dontShrink() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse("-dontwarn com.**\n-dontshrink", flags, UnsupportedFlagsHandler.NO_OP);
        assertFalse(flags.isDontObfuscate());
        assertTrue(flags.isDontShrink());
        assertFalse(flags.isDontOptimize());
    }

    @Test
    public void includeDescriptorClasses() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse(
                "-keepclassmembers,includedescriptorclasses class ** {\n"
                        + "   public void onEvent*(***);\n"
                        + "}",
                flags,
                UnsupportedFlagsHandler.NO_OP);

        Truth.assertThat(
                        Iterables.getOnlyElement(flags.getKeepClassMembersSpecs())
                                .getMethodSpecifications())
                .hasSize(1);
    }

    @Test
    public void keepClassesWithMemberNames_modifiers() throws Exception {
        ProguardFlags flags = new ProguardFlags();
        GrammarActions.parse(
                "-keepclasseswithmembernames,includedescriptorclasses class * { \n"
                        + "    native <methods>; \n"
                        + "} ",
                flags,
                UnsupportedFlagsHandler.NO_OP);
    }
}
