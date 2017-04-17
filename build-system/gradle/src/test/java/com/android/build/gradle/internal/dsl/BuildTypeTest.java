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

package com.android.build.gradle.internal.dsl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.internal.ide.SyncIssueImpl;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.Message;
import com.android.sdklib.SdkVersionInfo;
import com.android.testutils.internal.CopyOfTester;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

/** Tests that the build types are properly initialized. */
public class BuildTypeTest {

    private Project project;

    private ErrorReporter errorReporter;

    @Before
    public void setUp() throws Exception {
        project = ProjectBuilder.builder().build();
        errorReporter =
                new ErrorReporter(ErrorReporter.EvaluationMode.IDE) {
                    @NonNull
                    @Override
                    public SyncIssue handleIssue(
                            @Nullable String data, int type, int severity, @NonNull String msg) {
                        return new SyncIssueImpl(type, severity, data, msg);
                    }

                    @Override
                    public void receiveMessage(@NonNull Message message) {}
                };
    }

    @Test
    public void testDebug() {
        com.android.builder.model.BuildType type = getBuildTypeWithName(BuilderConstants.DEBUG);

        assertTrue(type.isDebuggable());
        assertFalse(type.isJniDebuggable());
        assertFalse(type.isRenderscriptDebuggable());
        assertNotNull(type.getSigningConfig());
        assertTrue(type.getSigningConfig().isSigningReady());
        assertTrue(type.isZipAlignEnabled());
    }

    @Test
    public void testRelease() {
        com.android.builder.model.BuildType type = getBuildTypeWithName(BuilderConstants.RELEASE);

        assertFalse(type.isDebuggable());
        assertFalse(type.isJniDebuggable());
        assertFalse(type.isRenderscriptDebuggable());
        assertTrue(type.isZipAlignEnabled());
    }

    @Test
    public void testInitWith() {
        CopyOfTester.assertAllGettersCalled(
                BuildType.class,
                new BuildType("original", project, errorReporter),
                original -> {
                    BuildType copy = new BuildType(original.getName(), project, errorReporter);
                    copy.initWith(original);

                    // Manually call getters that don't need to be copied.
                    original.getUseJack();
                    original.getPostprocessingConfiguration();
                });
    }

    @Test
    public void testInitWith_equals() {
        BuildType original = new BuildType("foo", project, errorReporter);

        // change every value from their default.
        original.setDebuggable(true);
        original.setJniDebuggable(true);
        original.setRenderscriptDebuggable(true);
        original.setRenderscriptOptimLevel(0);
        original.setApplicationIdSuffix("foo");
        original.setVersionNameSuffix("foo");
        original.setMinifyEnabled(true);
        original.setSigningConfig(new SigningConfig("blah"));
        original.setZipAlignEnabled(false);
        original.setShrinkResources(true);
        original.getJackOptions().setEnabled(Boolean.FALSE);
        original.ndk(ndk -> ndk.abiFilters("x86"));

        BuildType copy = new BuildType(original.getName(), project, errorReporter);
        copy.initWith(original);

        assertEquals(original, copy);
    }

    private com.android.builder.model.BuildType getBuildTypeWithName(String name) {
        project.apply(ImmutableMap.of("plugin", "com.android.application"));
        project.getExtensions()
                .getByType(AppExtension.class)
                .compileSdkVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API);
        return project.getPlugins()
                .getPlugin(AppPlugin.class)
                .getVariantManager()
                .getBuildTypes()
                .get(name)
                .getBuildType();
    }
}
