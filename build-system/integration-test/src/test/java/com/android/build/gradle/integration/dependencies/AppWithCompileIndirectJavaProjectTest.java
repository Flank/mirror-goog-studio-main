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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.BuildModel.Feature.FULL_DEPENDENCIES;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.ANDROID;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Items;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.process.ProcessException;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for compile jar in app through an aar dependency
 */
public class AppWithCompileIndirectJavaProjectTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        Files.write("include 'app', 'library', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(project.getBuildFile(),
"\nsubprojects {\n" +
"    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n" +
"}\n");

        appendToFile(project.getSubproject("app").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':library')\n" +
"    apk 'com.google.guava:guava:18.0'\n" +
"}\n");

        appendToFile(project.getSubproject("library").getBuildFile(),
"\ndependencies {\n" +
"    compile project(':jar')\n" +
"}\n");

        appendToFile(project.getSubproject("jar").getBuildFile(),
"\ndependencies {\n" +
"    compile 'com.google.guava:guava:17.0'\n" +
"}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkPackagedJar() throws IOException, ProcessException {
        project.execute("clean", ":app:assembleDebug");

        File apk = project.getSubproject("app").getApk("debug");

        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/person/People;");
        assertThatApk(apk).containsClass("Lcom/example/android/multiproject/library/PersonView;");
    }

    @Test
    public void checkLevel1Model() {
        ModelContainer<AndroidProject> modelContainer = project.model()
                .level(AndroidProject.MODEL_LEVEL_1_SYNC_ISSUE).getMulti();

        Map<String, AndroidProject> models = modelContainer.getModelMap();

        // ---
        // test the dependencies on the :app module.

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();

        Dependencies appDeps = appDebug.getMainArtifact().getDependencies();

        Collection<AndroidLibrary> appLibDeps = appDeps.getLibraries();
        assertThat(appLibDeps).named("app(androidlibs) count").hasSize(1);
        AndroidLibrary appAndroidLibrary = Iterables.getOnlyElement(appLibDeps);
        assertThat(appAndroidLibrary.getProject()).named("app(androidlibs[0]) project").isEqualTo(":library");

        Collection<String> appProjectDeps = appDeps.getProjects();
        assertThat(appProjectDeps).named("app(modules) count").isEmpty();

        Collection<JavaLibrary> appJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(appJavaLibDeps).named("app(javalibs) count").isEmpty();

        // ---
        // test the dependencies on the :library module.

        Variant libDebug = ModelHelper.getVariant(models.get(":library").getVariants(), "debug");
        Truth.assertThat(libDebug).isNotNull();

        Dependencies libDeps = libDebug.getMainArtifact().getDependencies();

        assertThat(libDeps.getLibraries()).named("lib(androidlibs) count").isEmpty();

        Collection<String> libProjectDeps = libDeps.getProjects();
        assertThat(libProjectDeps).named("lib(modules) count").hasSize(1);
        String libProjectDep = Iterables.getOnlyElement(libProjectDeps);
        assertThat(libProjectDep).named("lib->:jar project").isEqualTo(":jar");

        Collection<JavaLibrary> libJavaLibDeps = appDeps.getJavaLibraries();
        assertThat(libJavaLibDeps).named("lib(javalibs) count").isEmpty();
    }

    @Test
    public void checkLevel2Model() {
        ModelContainer<AndroidProject> modelContainer = project.model()
                .level(AndroidProject.MODEL_LEVEL_LATEST)
                .withFeature(FULL_DEPENDENCIES)
                .getMulti();

        Map<String, AndroidProject> models = modelContainer.getModelMap();

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);

        // ---
        // test full transitive dependencies from the :app module.

        Variant appDebug = ModelHelper.getVariant(models.get(":app").getVariants(), "debug");
        Truth.assertThat(appDebug).isNotNull();

        DependencyGraphs dependencyGraph = appDebug.getMainArtifact().getDependencyGraphs();

        {

            // no direct android library
            assertThat(helper.on(dependencyGraph).withType(ANDROID).asList())
                    .named(":app compile Android")
                    .isEmpty();

            // no direct java library
            assertThat(helper.on(dependencyGraph).withType(JAVA).asList())
                    .named(":app compile Java")
                    .isEmpty();

            // look at direct modules
            Items moduleItems = helper.on(dependencyGraph).withType(MODULE);

            // should depend on :library
            assertThat(moduleItems.mapTo(Property.GRADLE_PATH))
                    .named(":app compile modules")
                    .containsExactly(":library");

            Items libraryItems = moduleItems.getTransitiveFromSingleItem();

            // now look at the transitive dependencies of this item
            assertThat(libraryItems.withType(ANDROID).asList())
                    .named(":app->:lib compile Android")
                    .isEmpty();

            // no direct java library
            assertThat(libraryItems.withType(JAVA).asList())
                    .named(":app->:lib compile Java")
                    .isEmpty();

            // look at direct module
            Items librarySubModuleItems = libraryItems.withType(MODULE);

            // should depend on :jar
            assertThat(librarySubModuleItems.mapTo(Property.GRADLE_PATH))
                    .named(":app compile modules")
                    .containsExactly(":jar");

            // follow the transitive dependencies again
            Items libraryToJarItems = librarySubModuleItems.getTransitiveFromSingleItem();

            // no direct android library
            assertThat(libraryToJarItems.withType(ANDROID).asList())
                    .named(":app->:lib->:jar compile Android")
                    .isEmpty();

            // no direct module dep
            assertThat(libraryToJarItems.withType(MODULE).asList())
                    .named(":app->:lib->:jar compile module")
                    .isEmpty();

            assertThat(libraryToJarItems.withType(JAVA).mapTo(COORDINATES))
                    .named(":app->:lib->:jar compile java")
                    .containsExactly("com.google.guava:guava:17.0@jar");
        }

        // same thing with the package deps. Main difference is guava available as direct
        // dependencies and transitive one is promoted
        {
            Items packageItems = helper.on(dependencyGraph).forPackage();

            // no direct android library
            assertThat(packageItems.withType(ANDROID).asList())
                    .named(":app package Android")
                    .isEmpty();

            // dependency on guava.
            assertThat(packageItems.withType(JAVA).mapTo(COORDINATES))
                    .named(":app package Java")
                    .containsExactly("com.google.guava:guava:18.0@jar");

            // look at direct module
            Items moduleItems = packageItems.withType(MODULE);

            // should depend on :library
            assertThat(moduleItems.mapTo(Property.GRADLE_PATH))
                    .named(":app package modules")
                    .containsExactly(":library");

            // now look at the transitive dependencies of this item
            Items libraryItems = moduleItems.getTransitiveFromSingleItem();

            // no direct android lib
            assertThat(libraryItems.withType(ANDROID).asList())
                    .named(":app->:lib package Android")
                    .isEmpty();

            // no direct java library
            assertThat(libraryItems.withType(JAVA).asList())
                    .named(":app->:lib package Java")
                    .isEmpty();

            // look at direct module
            Items librarySubModuleItems = libraryItems.withType(MODULE);

            // should depend on :jar
            assertThat(librarySubModuleItems.mapTo(Property.GRADLE_PATH))
                    .named(":app->:lib package modules")
                    .containsExactly(":jar");

            // follow the transitive dependencies again
            Items libraryToJarItems = librarySubModuleItems.getTransitiveFromSingleItem();

            // no direct android library
            assertThat(libraryToJarItems.withType(ANDROID).asList())
                    .named(":app->:lib->:jar package Android")
                    .isEmpty();

            // no direct module dep
            assertThat(libraryToJarItems.withType(MODULE).asList())
                    .named(":app->:lib->:jar package module")
                    .isEmpty();

            assertThat(libraryToJarItems.withType(JAVA).mapTo(COORDINATES))
                    .named(":app->:lib->:jar package java")
                    .containsExactly("com.google.guava:guava:18.0@jar");
        }

        // ---
        // test full transitive dependencies from the :library module.
        {
            Variant libDebug = ModelHelper
                    .getVariant(models.get(":library").getVariants(), "debug");
            Truth.assertThat(libDebug).isNotNull();

            DependencyGraphs compileGraph = libDebug.getMainArtifact().getDependencyGraphs();

            // no direct android library
            assertThat(helper.on(compileGraph).withType(ANDROID).asList())
                    .named(":lib compile Android")
                    .isEmpty();

            // no direct java library
            assertThat(helper.on(compileGraph).withType(JAVA).asList())
                    .named(":lib compile Java")
                    .isEmpty();

            // look at direct module
            Items moduleItems = helper.on(compileGraph).withType(MODULE);

            // should depend on :jar
            assertThat(moduleItems.mapTo(Property.GRADLE_PATH))
                    .named(":lib compile modules")
                    .containsExactly(":jar");

            // follow the transitive dependencies again
            Items jarItems = moduleItems.getTransitiveFromSingleItem();

            // no direct android library
            assertThat(jarItems.withType(ANDROID).asList())
                    .named(":lib->:jar compile Android")
                    .isEmpty();

            // no direct module dep
            assertThat(jarItems.withType(MODULE).asList())
                    .named(":lib->:jar compile module")
                    .isEmpty();

            assertThat(jarItems.withType(JAVA).mapTo(COORDINATES))
                    .named(":lib->:jar compile java")
                    .containsExactly("com.google.guava:guava:17.0@jar");
        }
    }
}
