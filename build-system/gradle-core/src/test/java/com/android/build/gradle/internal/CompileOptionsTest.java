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

package com.android.build.gradle.internal;

import static org.junit.Assert.assertEquals;

import com.android.build.gradle.internal.dsl.decorator.AndroidPluginDslDecoratorKt;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.FakeServices;
import java.lang.reflect.InvocationTargetException;
import org.gradle.api.JavaVersion;
import org.junit.Test;

/**
 * Tests for {@link CompileOptions}
 */
public class CompileOptionsTest {

    private CompileOptions getCompileOptionsInstance() {
        DslServices dslServices = FakeServices.createDslServices();
        try {
            return AndroidPluginDslDecoratorKt.getAndroidPluginDslDecorator()
                    .decorate(CompileOptions.class)
                    .getDeclaredConstructor(DslServices.class)
                    .newInstance(dslServices);
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            return null;
        }
    }

    @Test
    public void sourceCompatibilityTest() {
        CompileOptions options = getCompileOptionsInstance();

        assertEquals(options.defaultJavaVersion, options.getSourceCompatibility());

        options.setSourceCompatibility("1.6");
        assertEquals(JavaVersion.VERSION_1_6, options.getSourceCompatibility());

        options.setSourceCompatibility(1.6);
        assertEquals(JavaVersion.VERSION_1_6, options.getSourceCompatibility());

        options.setSourceCompatibility(JavaVersion.VERSION_1_7);
        assertEquals(JavaVersion.VERSION_1_7, options.getSourceCompatibility());

        options.setSourceCompatibility("Version_1_7");
        assertEquals(JavaVersion.VERSION_1_7, options.getSourceCompatibility());

        options.setSourceCompatibility("VERSION_1_7");
        assertEquals(JavaVersion.VERSION_1_7, options.getSourceCompatibility());
    }

    @Test
    public void targetCompatibilityTest() {
        CompileOptions options = getCompileOptionsInstance();

        assertEquals(options.defaultJavaVersion, options.getTargetCompatibility());

        options.setTargetCompatibility("1.6");
        assertEquals(JavaVersion.VERSION_1_6, options.getTargetCompatibility());

        options.setTargetCompatibility(1.6);
        assertEquals(JavaVersion.VERSION_1_6, options.getTargetCompatibility());

        options.setTargetCompatibility(JavaVersion.VERSION_1_7);
        assertEquals(JavaVersion.VERSION_1_7, options.getTargetCompatibility());

        options.setTargetCompatibility("Version_1_7");
        assertEquals(JavaVersion.VERSION_1_7, options.getTargetCompatibility());

        options.setTargetCompatibility("VERSION_1_7");
        assertEquals(JavaVersion.VERSION_1_7, options.getTargetCompatibility());
    }

    @Test
    public void coreLibraryDesugaringEnabledTest() {
        CompileOptions options = getCompileOptionsInstance();

        assertEquals(null, options.getCoreLibraryDesugaringEnabled());

        options.setCoreLibraryDesugaringEnabled(true);
        assertEquals(true, options.getCoreLibraryDesugaringEnabled());
    }
}
