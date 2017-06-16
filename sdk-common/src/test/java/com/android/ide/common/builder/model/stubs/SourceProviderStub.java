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
package com.android.ide.common.builder.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class SourceProviderStub extends BaseStub implements SourceProvider {
    @NonNull private final String myName;
    @NonNull private final File myManifestFile;
    @NonNull private final Collection<File> myJavaDirectories;
    @NonNull private final Collection<File> myResourcesDirectories;
    @NonNull private final Collection<File> myAidlDirectories;
    @NonNull private final Collection<File> myRenderscriptDirectories;
    @NonNull private final Collection<File> myCDirectories;
    @NonNull private final Collection<File> myCppDirectories;
    @NonNull private final Collection<File> myResDirectories;
    @NonNull private final Collection<File> myAssetsDirectories;
    @NonNull private final Collection<File> myJniLibsDirectories;
    @NonNull private final Collection<File> myShadersDirectories;

    public SourceProviderStub() {
        this(
                "name",
                new File("manifest"),
                new File("java"),
                new File("resources"),
                new File("aidl"),
                new File("renderscript"),
                new File("c"),
                new File("cpp"),
                new File("res"),
                new File("assets"),
                new File("jniLibs"),
                new File("shaders"));
    }

    public SourceProviderStub(
            @NonNull String name,
            @NonNull File manifestFile,
            @NonNull File javaDirectory,
            @NonNull File resourcesDirectory,
            @NonNull File aidlDirectory,
            @NonNull File renderscriptDirectory,
            @NonNull File cDirectory,
            @NonNull File cppDirectory,
            @NonNull File resDirectory,
            @NonNull File assetsDirectory,
            @NonNull File jniLibsDirectory,
            @NonNull File shadersDirectory) {
        myName = name;
        myManifestFile = manifestFile;
        myJavaDirectories = Lists.newArrayList(javaDirectory);
        myResourcesDirectories = Lists.newArrayList(resourcesDirectory);
        myAidlDirectories = Lists.newArrayList(aidlDirectory);
        myRenderscriptDirectories = Lists.newArrayList(renderscriptDirectory);
        myCDirectories = Lists.newArrayList(cDirectory);
        myCppDirectories = Lists.newArrayList(cppDirectory);
        myResDirectories = Lists.newArrayList(resDirectory);
        myAssetsDirectories = Lists.newArrayList(assetsDirectory);
        myJniLibsDirectories = Lists.newArrayList(jniLibsDirectory);
        myShadersDirectories = Lists.newArrayList(shadersDirectory);
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return myManifestFile;
    }

    @Override
    @NonNull
    public Collection<File> getJavaDirectories() {
        return myJavaDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResourcesDirectories() {
        return myResourcesDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAidlDirectories() {
        return myAidlDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getRenderscriptDirectories() {
        return myRenderscriptDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getCDirectories() {
        return myCDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getCppDirectories() {
        return myCppDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResDirectories() {
        return myResDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAssetsDirectories() {
        return myAssetsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getJniLibsDirectories() {
        return myJniLibsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getShadersDirectories() {
        return myShadersDirectories;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceProvider)) {
            return false;
        }
        SourceProvider stub = (SourceProvider) o;
        return Objects.equals(getName(), stub.getName())
                && Objects.equals(getManifestFile(), stub.getManifestFile())
                && Objects.equals(getJavaDirectories(), stub.getJavaDirectories())
                && Objects.equals(getResourcesDirectories(), stub.getResourcesDirectories())
                && Objects.equals(getAidlDirectories(), stub.getAidlDirectories())
                && Objects.equals(getRenderscriptDirectories(), stub.getRenderscriptDirectories())
                && Objects.equals(getCDirectories(), stub.getCDirectories())
                && Objects.equals(getCppDirectories(), stub.getCppDirectories())
                && Objects.equals(getResDirectories(), stub.getResDirectories())
                && Objects.equals(getAssetsDirectories(), stub.getAssetsDirectories())
                && Objects.equals(getJniLibsDirectories(), stub.getJniLibsDirectories())
                && Objects.equals(getShadersDirectories(), stub.getShadersDirectories());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getManifestFile(),
                getJavaDirectories(),
                getResourcesDirectories(),
                getAidlDirectories(),
                getRenderscriptDirectories(),
                getCDirectories(),
                getCppDirectories(),
                getResDirectories(),
                getAssetsDirectories(),
                getJniLibsDirectories(),
                getShadersDirectories());
    }

    @Override
    public String toString() {
        return "SourceProviderStub{"
                + "myName='"
                + myName
                + '\''
                + ", myManifestFile="
                + myManifestFile
                + ", myJavaDirectories="
                + myJavaDirectories
                + ", myResourcesDirectories="
                + myResourcesDirectories
                + ", myAidlDirectories="
                + myAidlDirectories
                + ", myRenderscriptDirectories="
                + myRenderscriptDirectories
                + ", myCDirectories="
                + myCDirectories
                + ", myCppDirectories="
                + myCppDirectories
                + ", myResDirectories="
                + myResDirectories
                + ", myAssetsDirectories="
                + myAssetsDirectories
                + ", myJniLibsDirectories="
                + myJniLibsDirectories
                + ", myShadersDirectories="
                + myShadersDirectories
                + "}";
    }
}
