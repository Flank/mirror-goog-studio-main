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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SplitApksTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void checkMonoDex() throws IOException {
        Path apkPath = temporaryFolder.newFile("temp.apk").toPath();
        TestDataCreator.writeApkWithMonoDex(apkPath);
        Path apkPath2 = temporaryFolder.newFile("temp2.apk").toPath();
        TestDataCreator.writeApkWithMultiDex(apkPath2);

        SplitApks apks = new SplitApks(ImmutableList.of(new Apk(apkPath), new Apk(apkPath2)));

        assertThat(apks.toString()).contains(apkPath.toString());

        assertThat(apks.getAllDexes()).hasSize(4);
        Map<String, DexBackedClassDef> allClasses = apks.getAllClasses();
        // Check caching.
        assertThat(apks.getAllClasses()).isSameAs(allClasses);
        assertThat(allClasses).hasSize(4);

        assertThat(apks.size()).isEqualTo(2);
        assertThat(apks.get(0).toString()).contains(apkPath.toString());

        assertThat(apks.getEntries("classes.dex")).hasSize(2);
    }

    @Test
    public void checkFileConstructor() throws IOException {
        Path apkPath = temporaryFolder.newFile("temp.apk").toPath();
        TestDataCreator.writeApkWithMonoDex(apkPath);

        Apk apk = new Apk(apkPath.toFile());

        assertThat(apk.toString()).contains(apkPath.toString());
    }

}
