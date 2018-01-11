/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_ANNOTATION_PROCESSOR;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_API;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_APK;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE_ONLY;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_IMPLEMENTATION;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PROVIDED;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_PUBLISH;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_RUNTIME_ONLY;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_WEAR_APP;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.api.AndroidSourceFile;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.builder.model.SourceProvider;
import groovy.lang.Closure;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

/**
 */
public class DefaultAndroidSourceSet implements AndroidSourceSet, SourceProvider {

    @NonNull
    private final String name;
    private final boolean publishPackage;

    private final AndroidSourceDirectorySet javaSource;
    private final AndroidSourceDirectorySet javaResources;
    private final AndroidSourceFile manifest;
    private final AndroidSourceDirectorySet assets;
    private final AndroidSourceDirectorySet res;
    private final AndroidSourceDirectorySet aidl;
    private final AndroidSourceDirectorySet renderscript;
    private final AndroidSourceDirectorySet jni;
    private final AndroidSourceDirectorySet jniLibs;
    private final AndroidSourceDirectorySet shaders;
    private final String displayName;

    @Inject
    public DefaultAndroidSourceSet(@NonNull String name, Project project, boolean publishPackage) {
        this.name = name;
        this.publishPackage = publishPackage;
        displayName = GUtil.toWords(this.name);

        String javaSrcDisplayName = displayName + " Java source";

        javaSource = new DefaultAndroidSourceDirectorySet(javaSrcDisplayName, project);
        javaSource.getFilter().include("**/*.java");

        String javaResourcesDisplayName = displayName + " Java resources";
        javaResources = new DefaultAndroidSourceDirectorySet(javaResourcesDisplayName, project);
        javaResources.getFilter().exclude("**/*.java");

        String manifestDisplayName = displayName + " manifest";
        manifest = new DefaultAndroidSourceFile(manifestDisplayName, project);

        String assetsDisplayName = displayName + " assets";
        assets = new DefaultAndroidSourceDirectorySet(assetsDisplayName, project);

        String resourcesDisplayName = displayName + " resources";
        res = new DefaultAndroidSourceDirectorySet(resourcesDisplayName, project);

        String aidlDisplayName = displayName + " aidl";
        aidl = new DefaultAndroidSourceDirectorySet(aidlDisplayName, project);

        String renderscriptDisplayName = displayName + " renderscript";
        renderscript = new DefaultAndroidSourceDirectorySet(renderscriptDisplayName, project);

        String jniDisplayName = displayName + " jni";
        jni = new DefaultAndroidSourceDirectorySet(jniDisplayName, project);

        String libsDisplayName = displayName + " jniLibs";
        jniLibs = new DefaultAndroidSourceDirectorySet(libsDisplayName, project);

        String shaderDisplayName = displayName + " shaders";
        shaders = new DefaultAndroidSourceDirectorySet(shaderDisplayName, project);
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String toString() {
        return "source set " + getDisplayName();
    }

    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    @Override
    public String getApiConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_API;
        } else {
            return name + "Api";
        }
    }

    @NonNull
    @Override
    public String getCompileOnlyConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_COMPILE_ONLY;
        } else {
            return name + "CompileOnly";
        }
    }

    @NonNull
    @Override
    public String getImplementationConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_IMPLEMENTATION;
        } else {
            return name + "Implementation";
        }
    }

    @NonNull
    @Override
    public String getRuntimeOnlyConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_RUNTIME_ONLY;
        } else {
            return name + "RuntimeOnly";
        }
    }

    @Override
    @NonNull
    public String getCompileConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_COMPILE;
        } else {
            return name + "Compile";
        }
    }

    @Override
    @NonNull
    public String getPackageConfigurationName() {
        if (publishPackage) {
            if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
                return CONFIG_NAME_PUBLISH;
            } else {
                return name + "Publish";
            }
        }

        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_APK;
        } else {
            return name + "Apk";
        }
    }

    @Override
    @NonNull
    public String getProvidedConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_PROVIDED;
        } else {
            return name + "Provided";
        }
    }

    @NonNull
    @Override
    public String getWearAppConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_WEAR_APP;
        } else {
            return name + "WearApp";
        }
    }

    @NonNull
    @Override
    public String getAnnotationProcessorConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return CONFIG_NAME_ANNOTATION_PROCESSOR;
        } else {
            return name + "AnnotationProcessor";
        }
    }

    @Override
    @NonNull
    public AndroidSourceFile getManifest() {
        return manifest;
    }

    @Override
    @NonNull
    public AndroidSourceSet manifest(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getManifest());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getRes() {
        return res;
    }

    @Override
    @NonNull
    public AndroidSourceSet res(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRes());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getAssets() {
        return assets;
    }

    @Override
    @NonNull
    public AndroidSourceSet assets(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAssets());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getAidl() {
        return aidl;
    }

    @Override
    @NonNull
    public AndroidSourceSet aidl(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAidl());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getRenderscript() {
        return renderscript;
    }

    @Override
    @NonNull
    public AndroidSourceSet renderscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRenderscript());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getJni() {
        return jni;
    }

    @Override
    @NonNull
    public AndroidSourceSet jni(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJni());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getJniLibs() {
        return jniLibs;
    }

    @Override
    @NonNull
    public AndroidSourceSet jniLibs(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJniLibs());
        return this;
    }

    @NonNull
    @Override
    public AndroidSourceSet shaders(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getShaders());
        return this;
    }

    @NonNull
    @Override
    public AndroidSourceDirectorySet getShaders() {
        return shaders;
    }

    @Override
    @NonNull
    public AndroidSourceDirectorySet getJava() {
        return javaSource;
    }

    @Override
    @NonNull
    public AndroidSourceSet java(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJava());
        return this;
    }


    @Override
    @NonNull
    public AndroidSourceDirectorySet getResources() {
        return javaResources;
    }

    @Override
    @NonNull
    public AndroidSourceSet resources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getResources());
        return this;
    }

    @Override
    @NonNull
    public AndroidSourceSet setRoot(String path) {
        javaSource.setSrcDirs(Collections.singletonList(path + "/java"));
        javaResources.setSrcDirs(Collections.singletonList(path + "/resources"));
        res.setSrcDirs(Collections.singletonList(path + "/" + SdkConstants.FD_RES));
        assets.setSrcDirs(Collections.singletonList(path + "/" + SdkConstants.FD_ASSETS));
        manifest.srcFile(path + "/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
        aidl.setSrcDirs(Collections.singletonList(path + "/aidl"));
        renderscript.setSrcDirs(Collections.singletonList(path + "/rs"));
        jni.setSrcDirs(Collections.singletonList(path + "/jni"));
        jniLibs.setSrcDirs(Collections.singletonList(path + "/jniLibs"));
        shaders.setSrcDirs(Collections.singletonList(path + "/shaders"));
        return this;
    }

    // --- SourceProvider

    @NonNull
    @Override
    public Set<File> getJavaDirectories() {
        return getJava().getSrcDirs();
    }

    @NonNull
    @Override
    public Set<File> getResourcesDirectories() {
        return getResources().getSrcDirs();
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return getManifest().getSrcFile();
    }

    @Override
    @NonNull
    public Set<File> getAidlDirectories() {
        return getAidl().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getRenderscriptDirectories() {
        return getRenderscript().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getCDirectories() {
        return getJni().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getCppDirectories() {
        // The C and C++ directories are currently the same.  This may change in the future when
        // we use Gradle's native source sets.
        return getJni().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getResDirectories() {
        return getRes().getSrcDirs();
    }

    @Override
    @NonNull
    public Set<File> getAssetsDirectories() {
        return getAssets().getSrcDirs();
    }

    @NonNull
    @Override
    public Collection<File> getJniLibsDirectories() {
        return getJniLibs().getSrcDirs();
    }

    @NonNull
    @Override
    public Collection<File> getShadersDirectories() {
        return getShaders().getSrcDirs();
    }
}
