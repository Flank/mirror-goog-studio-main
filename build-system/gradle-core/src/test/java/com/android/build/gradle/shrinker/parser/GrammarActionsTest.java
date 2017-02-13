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

package com.android.build.gradle.shrinker.parser;

import com.android.build.gradle.internal.incremental.ByteCodeUtils;
import com.android.build.gradle.shrinker.parser.GrammarActions.FilterSeparator;
import org.junit.Assert;
import org.junit.Test;

public class GrammarActionsTest {

    @Test
    public void testNamePatternClass() {
        NameSpecification spec = GrammarActions.name("a.b.*.d", FilterSeparator.CLASS);
        assertMatchesClass(spec, "a.b.c.d");
        assertDoesNotMatchClass(spec, "a.b.c.e.d");
        spec = GrammarActions.name("a.b.**.d", FilterSeparator.CLASS);
        assertMatchesClass(spec, "a.b.c.d");
        assertMatchesClass(spec, "a.b.c.e.d");
        spec = GrammarActions.name("a.b.?.d", FilterSeparator.CLASS);
        assertMatchesClass(spec, "a.b.c.d");
        assertDoesNotMatchClass(spec, "a.b.ce.d");
        spec = GrammarActions.name("a.b.x?z.d", FilterSeparator.CLASS);
        assertMatchesClass(spec, "a.b.xyz.d");
        assertDoesNotMatchClass(spec, "a.b.x.z.d");
    }

    private static void assertMatchesClass(NameSpecification spec, String className) {
        Assert.assertTrue(className, spec.matches(ByteCodeUtils.toInternalName(className)));
    }

    private static void assertDoesNotMatchClass(NameSpecification spec, String className) {
        Assert.assertFalse(className, spec.matches(ByteCodeUtils.toInternalName(className)));
    }

    @Test
    public void testNamePatternFile() {
        NameSpecification spec = GrammarActions.name("a/b/*/d.txt", FilterSeparator.FILE);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertFalse(spec.matches("a/b/c/e/d.txt"));
        Assert.assertTrue(spec.matches("a/b/c.e/d.txt"));
        spec = GrammarActions.name("a/b/**/d.txt", FilterSeparator.FILE);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertTrue(spec.matches("a/b/c/e/d.txt"));
        spec = GrammarActions.name("a/b/?/d.txt", FilterSeparator.FILE);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertFalse(spec.matches("a/b/ce/d.txt"));
        Assert.assertFalse(spec.matches("a/b///d.txt"));
        Assert.assertTrue(spec.matches("a/b/./d.txt"));
        spec = GrammarActions.name("a/b/x?z/d.txt", FilterSeparator.FILE);
        Assert.assertTrue(spec.matches("a/b/xyz/d.txt"));
        Assert.assertFalse(spec.matches("a/b/x/z/d.txt"));
    }

    @Test
    public void testNamePatternGENERAL() {
        NameSpecification spec = GrammarActions.name("a/b/*/d.txt", FilterSeparator.GENERAL);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertTrue(spec.matches("a/b/c/e/d.txt"));
        Assert.assertTrue(spec.matches("a/b/c.e/d.txt"));
        spec = GrammarActions.name("a/b/**/d.txt", FilterSeparator.GENERAL);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertTrue(spec.matches("a/b/c/e/d.txt"));
        spec = GrammarActions.name("a/b/?/d.txt", FilterSeparator.GENERAL);
        Assert.assertTrue(spec.matches("a/b/c/d.txt"));
        Assert.assertFalse(spec.matches("a/b/ce/d.txt"));
        Assert.assertTrue(spec.matches("a/b///d.txt"));
        Assert.assertTrue(spec.matches("a/b/./d.txt"));
    }
}
