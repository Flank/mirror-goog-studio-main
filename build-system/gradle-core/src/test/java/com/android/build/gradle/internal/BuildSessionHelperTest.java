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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.builder.Version;
import com.android.ide.common.util.JvmWideVariable;
import com.android.testutils.classloader.MultiClassLoader;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Test cases for {@link BuildSessionHelper}. */
@RunWith(MockitoJUnitRunner.Silent.class)
@Ignore("issuetracker.google.com/issues/62878541")
public class BuildSessionHelperTest {

    @Mock Project project1;
    @Mock Project project2;
    @Mock Project project3;
    @Mock Project project4;

    @Before
    public void setUp() {
        Gradle gradle = mock(Gradle.class);
        doNothing().when(gradle).addBuildListener(any());

        when(project1.getGradle()).thenReturn(gradle);
        when(project2.getGradle()).thenReturn(gradle);
        when(project3.getGradle()).thenReturn(gradle);
        when(project4.getGradle()).thenReturn(gradle);

        when(project1.getProjectDir()).thenReturn(new File("project1"));
        when(project2.getProjectDir()).thenReturn(new File("project2"));
        when(project3.getProjectDir()).thenReturn(new File("project3"));
        when(project4.getProjectDir()).thenReturn(new File("project4"));
    }

    @Test
    public void testStartOnce() throws Exception {
        // Start the BuildSessionSingleton object
        BuildSessionHelper.startOnce(project1);
        BuildSessionInterface singleton = BuildSessionHelper.getSingleton();
        assertThat(singleton).isInstanceOf(BuildSessionSingleton.class);

        // If we get BuildSessionSingleton again, it must return the same singleton
        assertThat(BuildSessionHelper.getSingleton()).isSameAs(singleton);

        // If we start BuildSessionSingleton again in a different project, it must still reuse the
        // same singleton
        BuildSessionHelper.startOnce(project2);
        assertThat(BuildSessionHelper.getSingleton()).isSameAs(singleton);

        // If we start BuildSessionSingleton again in a different project with a different class
        // loader, it should create a proxy to the actual singleton
        MultiClassLoader classLoader =
                new MultiClassLoader(
                        ImmutableList.of(
                                BuildSessionHelper.class.getName(),
                                BuildSessionInterface.class.getName(),
                                BuildSessionSingleton.class.getName()));
        List<Class<?>> classes = classLoader.load();
        Class<?> buildSessionHelperClass = classes.get(0);
        Class<?> buildSessionInterfaceClass = classes.get(1);
        Class<?> buildSessionSingletonClass = classes.get(2);
        assertThat(buildSessionHelperClass.getClassLoader())
                .isNotSameAs(BuildSessionHelper.class.getClassLoader());
        assertThat(buildSessionInterfaceClass.getClassLoader())
                .isNotSameAs(BuildSessionInterface.class.getClassLoader());
        assertThat(buildSessionSingletonClass.getClassLoader())
                .isNotSameAs(BuildSessionSingleton.class.getClassLoader());

        Method startOnceMethod = buildSessionHelperClass.getMethod("startOnce", Project.class);
        startOnceMethod.invoke(null, project3);
        Method getSingletonMethod = buildSessionHelperClass.getDeclaredMethod("getSingleton");
        getSingletonMethod.setAccessible(true);
        Object proxyToSingleton = getSingletonMethod.invoke(null);
        assertThat(proxyToSingleton).isNotSameAs(singleton);

        // Start BuildSessionSingleton again in the same project, expect failure
        try {
            BuildSessionHelper.startOnce(project1);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }
        try {
            startOnceMethod.invoke(null, project1);
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException e) {
            assertThat(e.getTargetException()).isInstanceOf(IllegalStateException.class);
            assertThat(e.getTargetException().getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }

        // Get the JVM-wide variable for BuildSessionSingleton
        JvmWideVariable<Object> jvmWideSingleton =
                new JvmWideVariable<>(
                        BuildSessionHelper.class.getName(),
                        BuildSessionSingleton.class.getSimpleName(),
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        TypeToken.of(Object.class),
                        () -> null);

        // Simulate finishing the build
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();

        // After the build is finished, the JVM-wide variable for BuildSessionSingleton must be
        // un-registered and the singleton must be set to null
        try {
            jvmWideSingleton.unregister();
            fail("Expected VerifyException");
        } catch (VerifyException e) {
            assertThat(e.getMessage()).contains("has already been unregistered");
        }
        assertThat(BuildSessionHelper.getSingleton()).isNull();

        // Starting BuildSessionSingleton again must return a different singleton
        BuildSessionHelper.startOnce(project1);
        assertThat(BuildSessionHelper.getSingleton()).isNotSameAs(singleton);

        // Finish the build
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
    }

    @Test
    public void testVerifyPluginVersion() {
        // Load the plugin in a project
        Set<JvmWideVariable<?>> versions =
                BuildSessionHelper.verifyPluginVersion(
                        project1,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);

        // Load the plugin again in the same project with the same version, expect failure
        try {
            BuildSessionHelper.verifyPluginVersion(
                    project1,
                    Version.ANDROID_GRADLE_PLUGIN_VERSION,
                    Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }

        // Load the plugin again in the same project with a different version, expect failure
        try {
            BuildSessionHelper.verifyPluginVersion(
                    project1, "1.2.3", Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }
        try {
            BuildSessionHelper.verifyPluginVersion(
                    project1, Version.ANDROID_GRADLE_PLUGIN_VERSION, "0.1.2");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains(
                            Version.ANDROID_GRADLE_PLUGIN_VERSION
                                    + " was already applied to this project");
        }

        // Load the plugin again in a different project with the same version, expect success
        BuildSessionHelper.verifyPluginVersion(
                project2,
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);

        // Load the plugin again in a different project with a different version, expect failure
        try {
            BuildSessionHelper.verifyPluginVersion(
                    project3, "1.2.3", Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains("Using multiple versions of the plugin is not allowed.");
        }
        try {
            BuildSessionHelper.verifyPluginVersion(
                    project4, Version.ANDROID_GRADLE_PLUGIN_VERSION, "0.1.2");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .contains("Using multiple versions of the plugin is not allowed.");
        }
        versions.forEach(JvmWideVariable::unregister);

        // Load the plugin again in the same project with the same version, and in a different
        // build, expect success
        BuildSessionHelper.startOnce(project1);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        versions =
                BuildSessionHelper.verifyPluginVersion(
                        project1,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
        versions.forEach(JvmWideVariable::unregister);

        // Load the plugin again in the same project with a different version, and in a different
        // build, expect success
        BuildSessionHelper.startOnce(project1);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        versions = BuildSessionHelper.verifyPluginVersion(project1, "1.2.3", "0.1.2");
        versions.forEach(JvmWideVariable::unregister);

        // Load the plugin again in a different project with the same version, and in a different
        // build, expect success
        BuildSessionHelper.startOnce(project1);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        versions =
                BuildSessionHelper.verifyPluginVersion(
                        project2,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
        versions.forEach(JvmWideVariable::unregister);

        // Load the plugin again in a different project with a different version, and in a different
        // build, expect success
        BuildSessionHelper.startOnce(project1);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        versions = BuildSessionHelper.verifyPluginVersion(project2, "1.2.3", "0.1.2");
        versions.forEach(JvmWideVariable::unregister);

        // Check that the JVM-wide variables that keep track of plugin versions are un-registered at
        // the end of a build
        BuildSessionHelper.startOnce(project1);
        Set<JvmWideVariable<?>> jvmWidePluginVersionRecords =
                BuildSessionHelper.verifyPluginVersion(
                        project2,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION,
                        Version.ANDROID_GRADLE_COMPONENT_PLUGIN_VERSION);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        for (JvmWideVariable<?> jvmWidePluginVersionRecord : jvmWidePluginVersionRecords) {
            try {
                jvmWidePluginVersionRecord.unregister();
                fail("Expected VerifyException");
            } catch (VerifyException e) {
                assertThat(e.getMessage()).contains("has already been unregistered");
            }
        }
    }

    @Test
    public void testVerifySingletonExists() {
        // Do not call startOnce(), expect failure
        try {
            BuildSessionHelper.verifySingletonExists();
            fail("Expect IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Instance does not exist");
        }

        // Call startOnce(), expect success
        BuildSessionHelper.startOnce(project1);
        BuildSessionHelper.verifySingletonExists();

        // Finish the build
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
    }

    @Test
    public void testExecuteLastWhenBuildFinished() {
        AtomicInteger fooCounter = new AtomicInteger(0);

        // Register the actions to be executed when the build is finished, they should not be
        // executed yet
        BuildSessionHelper.startOnce(project1);
        BuildSessionHelper.executeLastWhenBuildFinished(fooCounter::incrementAndGet);
        BuildSessionHelper.executeLastWhenBuildFinished(fooCounter::incrementAndGet);
        assertThat(fooCounter.get()).isEqualTo(0);

        // Let the build finish, now the actions should be executed
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        assertThat(fooCounter.get()).isEqualTo(2);

        // Check that the actions will not be re-executed in the next build, since each build is
        // independent
        BuildSessionHelper.startOnce(project1);
        //noinspection ConstantConditions
        ((BuildSessionSingleton) BuildSessionHelper.getSingleton()).buildFinished();
        assertThat(fooCounter.get()).isEqualTo(2);
    }
}
