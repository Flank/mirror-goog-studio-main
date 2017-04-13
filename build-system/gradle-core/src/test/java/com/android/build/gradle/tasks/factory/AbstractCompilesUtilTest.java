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

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.chooseDefaultJavaVersion;

import com.android.build.gradle.internal.scope.VariantScope;
import org.gradle.api.JavaVersion;
import org.junit.Assert;
import org.junit.Test;

/** Tests the logic for choosing Java language level. */
public class AbstractCompilesUtilTest {

    private static final String CURRENT_JDK_VERSION = "1.8";

    @Test
    public void testChooseDefaultJavaVersion_jdk8() throws Exception {
        Assert.assertEquals(
                JavaVersion.VERSION_1_6,
                chooseDefaultJavaVersion(
                        "android-15", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.NONE));
        Assert.assertEquals(
                JavaVersion.VERSION_1_6,
                chooseDefaultJavaVersion(
                        "android-15", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.JACK));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "android-21", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.NONE));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "android-21", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.JACK));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "Google Inc.:Google APIs:22",
                        CURRENT_JDK_VERSION,
                        VariantScope.Java8LangSupport.NONE));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "android-24", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.NONE));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "android-24", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.DESUGAR));
        Assert.assertEquals(
                JavaVersion.VERSION_1_7,
                chooseDefaultJavaVersion(
                        "android-24",
                        CURRENT_JDK_VERSION,
                        VariantScope.Java8LangSupport.RETROLAMBDA));
        Assert.assertEquals(
                JavaVersion.VERSION_1_8,
                chooseDefaultJavaVersion(
                        "android-24", CURRENT_JDK_VERSION, VariantScope.Java8LangSupport.JACK));
    }
}
