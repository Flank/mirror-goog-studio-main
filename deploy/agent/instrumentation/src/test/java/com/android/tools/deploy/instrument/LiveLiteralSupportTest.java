/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.deploy.instrument;

import org.junit.Assert;
import org.junit.Test;

public class LiveLiteralSupportTest {

    private static class FakeComposeRuntimeWithGlobalFlag {
        private static int enableLiveLiteralsCallCount = 0;
        public static boolean isLiveLiteralsEnabled = false;

        public static void enableLiveLiterals() {
            enableLiveLiteralsCallCount++;
        }
    }

    private static class FakeComposeRuntimeWithoutGlobalFlag {}

    private static class FakeLiveLiteralHelperWithFlag {
        private static boolean enabled = false;
    }

    private static class FakeLiveLiteralHelperWithoutFlag {}

    @Test
    public void testComposeWithGlobalEnableFlag() {
        // Case where it is not yet enabled.
        FakeComposeRuntimeWithGlobalFlag.enableLiveLiteralsCallCount = 0;
        FakeComposeRuntimeWithGlobalFlag.isLiveLiteralsEnabled = false;
        boolean result =
                LiveLiteralSupport.enableGlobal(FakeComposeRuntimeWithGlobalFlag.class, "FakeID");
        Assert.assertTrue(result);
        Assert.assertEquals(1, FakeComposeRuntimeWithGlobalFlag.enableLiveLiteralsCallCount);

        // Case where it is already enabled.
        FakeComposeRuntimeWithGlobalFlag.enableLiveLiteralsCallCount = 0;
        FakeComposeRuntimeWithGlobalFlag.isLiveLiteralsEnabled = true;
        result = LiveLiteralSupport.enableGlobal(FakeComposeRuntimeWithGlobalFlag.class, "FakeID");
        // It should return false and NOT call enableLiveLiterals()
        Assert.assertFalse(result);
        Assert.assertEquals(0, FakeComposeRuntimeWithGlobalFlag.enableLiveLiteralsCallCount);
    }

    @Test
    public void testComposeWithoutGlobalEnableFlag() {
        boolean result =
                LiveLiteralSupport.enableGlobal(
                        FakeComposeRuntimeWithoutGlobalFlag.class, "FakeID");
        Assert.assertFalse(result);
    }

    @Test
    public void testComposeWithHelperEnableFlag() {
        // Case where it is not yet enabled.
        FakeLiveLiteralHelperWithFlag.enabled = false;
        int result =
                LiveLiteralSupport.enableHelperClass(FakeLiveLiteralHelperWithFlag.class, "FakeID");
        Assert.assertEquals(0, result);

        // Case where it is not yet enabled.
        FakeLiveLiteralHelperWithFlag.enabled = true;
        result =
                LiveLiteralSupport.enableHelperClass(FakeLiveLiteralHelperWithFlag.class, "FakeID");
        Assert.assertEquals(1, result);
    }

    @Test
    public void testComposeWithoutHelperEnableFlag() {
        int result =
                LiveLiteralSupport.enableHelperClass(
                        FakeLiveLiteralHelperWithoutFlag.class, "FakeID");
        Assert.assertEquals(2, result);
    }
}
