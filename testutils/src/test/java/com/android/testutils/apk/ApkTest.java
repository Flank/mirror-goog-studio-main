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

package com.android.testutils.apk;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.google.common.truth.Truth8;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApkTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkMonoDex() throws IOException {
        Path apkPath = temporaryFolder.newFile("temp.apk").toPath();
        TestDataCreator.writeApkWithMonoDex(apkPath);

        Apk apk = new Apk(apkPath);

        assertThat(apk.toString()).contains(apkPath.toString());
        assertThat(apk.getEntries()).hasSize(4);

        assertThat(apk.containsClass("Lcom/example/SomeClass;"))
                .named("Apk contains class (from main)")
                .isTrue();
        assertThat(apk.containsClass("Lcom/example/somelib/Lib;"))
                .named("Apk contains class (from secondary)")
                .isFalse();

        Truth8.assertThat(apk.getMainDexFile()).isPresent();
        assertThat(apk.getSecondaryDexFiles()).isEmpty();
        assertThat(apk.getAllDexes()).hasSize(1);

        assertNotNull(
                "apk contains java resource",
                apk.getJavaResource("java_resource"));

    }


    @Test
    public void checkMultiDexContains() throws IOException {
        Path apkPath = temporaryFolder.newFile("temp.apk").toPath();
        TestDataCreator.writeApkWithMultiDex(apkPath);

        Apk apk = new Apk(apkPath);

        assertThat(apk.toString()).contains(apkPath.toString());
        assertThat(apk.getEntries()).hasSize(6);

        assertThat(apk.containsClass("Lcom/example/SomeMultiClass;"))
                .named("Apk contains class (from main)")
                .isTrue();
        assertThat(apk.containsClass("Lcom/example/somelib/Lib;"))
                .named("Apk contains class (from secondary)")
                .isTrue();
        assertThat(apk.containsClass("Lcom/example/somelib2/Lib2;"))
                .named("Apk contains class (from secondary)")
                .isTrue();

        Truth8.assertThat(apk.getMainDexFile()).isPresent();
        assertThat(apk.getSecondaryDexFiles()).hasSize(2);
        assertThat(apk.getAllDexes()).hasSize(3);
    }

    @Test
    public void checkFileConstructor() throws IOException {
        Path apkPath = temporaryFolder.newFile("temp.apk").toPath();
        TestDataCreator.writeApkWithMonoDex(apkPath);

        Apk apk = new Apk(apkPath.toFile());

        assertThat(apk.toString()).contains(apkPath.toString());
    }

}
