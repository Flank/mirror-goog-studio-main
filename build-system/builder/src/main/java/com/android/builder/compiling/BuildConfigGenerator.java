/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.builder.compiling;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.io.Closer;
import com.squareup.javawriter.JavaWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * Class able to generate a BuildConfig class in an Android project. The BuildConfig class contains
 * constants related to the build target.
 */
public class BuildConfigGenerator implements BuildConfigCreator {

    public static final String BUILD_CONFIG_NAME = "BuildConfig.java";

    private static final Set<Modifier> PUBLIC_FINAL = EnumSet.of(Modifier.PUBLIC, Modifier.FINAL);

    private final String mGenFolder;
    private final String mBuildConfigPackageName;

    private final List<BuildConfigField> mFields;

    /**
     * Creates a generator
     *
     * @param buildConfigData BuildConfigData used to derive BuildConfig Java source class
     */
    public BuildConfigGenerator(@NonNull BuildConfigData buildConfigData) {
        mBuildConfigPackageName = buildConfigData.getBuildConfigPackageName();
        mGenFolder = buildConfigData.getOutputPath().toString();
        mFields = buildConfigData.getBuildConfigFields();
    }

    /** Returns a File representing where the BuildConfig class will be. */
    @NonNull
    @Override
    public File getFolderPath() {
        return new File(mGenFolder, mBuildConfigPackageName.replace('.', File.separatorChar));
    }

    @NonNull
    @Override
    public File getBuildConfigFile() {
        File folder = getFolderPath();
        return new File(folder, BUILD_CONFIG_NAME);
    }

    /** Generates the BuildConfig class. */
    @Override
    public void generate() {
        File pkgFolder = getFolderPath();
        if (!pkgFolder.isDirectory() && !pkgFolder.mkdirs()) {
            throw new RuntimeException("Failed to create " + pkgFolder.getAbsolutePath());
        }

        File buildConfigJava = new File(pkgFolder, BUILD_CONFIG_NAME);

        try (Closer closer = Closer.create()) {
            FileOutputStream fos = closer.register(new FileOutputStream(buildConfigJava));
            OutputStreamWriter out = closer.register(new OutputStreamWriter(fos, Charsets.UTF_8));
            JavaWriter writer = closer.register(new JavaWriter(out));

            writer.emitJavadoc("Automatically generated file. DO NOT MODIFY")
                    .emitPackage(mBuildConfigPackageName)
                    .beginType("BuildConfig", "class", PUBLIC_FINAL);

            for (BuildConfigField field : mFields) {
                field.emit(writer);
            }

            writer.endType();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
