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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import proguard.ClassPath;
import proguard.ClassPathEntry;
import proguard.ClassSpecification;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.KeepClassSpecification;
import proguard.ParseException;
import proguard.ProGuard;
import proguard.classfile.util.ClassUtil;
import proguard.util.ListUtil;

public abstract class BaseProguardAction extends ProguardConfigurable {

    protected static final List<String> JAR_FILTER = ImmutableList.of("!META-INF/MANIFEST.MF");

    protected final Configuration configuration = new Configuration();

    public BaseProguardAction(@NonNull VariantScope scope) {
        super(scope);
        configuration.useMixedCaseClassNames = false;
        configuration.programJars = new ClassPath();
        configuration.libraryJars = new ClassPath();
    }

    public void runProguard() throws IOException {
        new ProGuard(configuration).execute();
    }

    @Override
    public void keep(@NonNull String keep) {
        if (configuration.keep == null) {
            configuration.keep = Lists.newArrayList();
        }

        ClassSpecification classSpecification;
        try {
            ConfigurationParser parser = new ConfigurationParser(new String[]{keep}, null);
            classSpecification = parser.parseClassSpecificationArguments();
        } catch (IOException e) {
            // No IO happens when parsing in-memory strings.
            throw new AssertionError(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        //noinspection unchecked
        configuration.keep.add(new KeepClassSpecification(
                true  /*markClasses*/,
                false /*markConditionally*/,
                false /*includedescriptorclasses */,
                false /*allowshrinking*/,
                false /*allowoptimization*/,
                false /*allowobfuscation*/,
                classSpecification));
    }

    public void dontshrink() {
        configuration.shrink = false;
    }

    public void dontobfuscate() {
        configuration.obfuscate = false;
    }

    public void dontoptimize() {
        configuration.optimize = false;
    }

    public void dontpreverify() {
        configuration.preverify = false;
    }

    public void keepattributes() {
        configuration.keepAttributes = Lists.newArrayListWithExpectedSize(0);
    }

    @Override
    public void dontwarn(@NonNull String dontwarn) {
        if (configuration.warn == null) {
            configuration.warn = Lists.newArrayList();
        }

        dontwarn = ClassUtil.internalClassName(dontwarn);

        //noinspection unchecked
        configuration.warn.addAll(ListUtil.commaSeparatedList(dontwarn));
    }

    public void dontwarn() {
        configuration.warn = Lists.newArrayList("**");
    }

    public void dontnote() {
        configuration.note = Lists.newArrayList("**");
    }

    public void forceprocessing() {
        configuration.lastModified = Long.MAX_VALUE;
    }

    protected void applyMapping(@NonNull File testedMappingFile) {
        configuration.applyMapping = testedMappingFile;
    }

    public void applyConfigurationFile(@NonNull File file) throws IOException, ParseException {
        // file might not actually exist if it comes from a sub-module library where publication
        // happen whether the file is there or not.
        if (!file.isFile()) {
            return;
        }

        ConfigurationParser parser =
                new ConfigurationParser(file, System.getProperties());
        try {
            parser.parse(configuration);
        } finally {
            parser.close();
        }
    }

    public void printconfiguration(@NonNull File file) {
        configuration.printConfiguration = file;
    }

    protected void inJar(@NonNull File jarFile) {
        inputJar(configuration.programJars, jarFile, null);
    }

    protected void outJar(@NonNull File file) {
        ClassPathEntry classPathEntry = new ClassPathEntry(file, true /*output*/);
        configuration.programJars.add(classPathEntry);
    }

    protected void libraryJar(@NonNull File jarFile) {
        inputJar(configuration.libraryJars, jarFile, null);
    }

    protected static void inputJar(
            @NonNull ClassPath classPath,
            @NonNull File file,
            @Nullable List<String> filter) {
        ClassPathEntry classPathEntry = new ClassPathEntry(file, false /*output*/);

        if (filter != null) {
            classPathEntry.setFilter(filter);
        }

        classPath.add(classPathEntry);
    }
}
