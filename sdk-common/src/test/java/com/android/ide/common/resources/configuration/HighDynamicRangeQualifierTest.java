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

package com.android.ide.common.resources.configuration;

import static com.google.common.truth.Truth.assertThat;

import com.android.resources.HighDynamicRange;
import junit.framework.TestCase;

public class HighDynamicRangeQualifierTest extends TestCase {

    private HighDynamicRangeQualifier qual;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        qual = new HighDynamicRangeQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        qual = null;
        config = null;
    }

    public void testHighDynamicRange() {
        assertThat(qual.checkAndSet("highdr", config)).isTrue();
        final HighDynamicRangeQualifier configQual = config.getHighDynamicRangeQualifier();
        assertThat(configQual).isNotNull();
        assertThat(configQual.getValue()).isEqualTo(HighDynamicRange.HIGHDR);
        assertThat(configQual.toString()).isEqualTo("highdr");
    }

    public void testLowDynamicRange() {
        assertThat(qual.checkAndSet("lowdr", config)).isTrue();
        final HighDynamicRangeQualifier configQual = config.getHighDynamicRangeQualifier();
        assertThat(configQual).isNotNull();
        assertThat(configQual.getValue()).isEqualTo(HighDynamicRange.LOWDR);
        assertThat(configQual.toString()).isEqualTo("lowdr");
    }

    public void testFailures() {
        assertThat(qual.checkAndSet("", config)).named("qual test for <empty>").isFalse();
        assertThat(qual.checkAndSet("high-dr", config)).named("qual test for high-dr").isFalse();
    }
}
