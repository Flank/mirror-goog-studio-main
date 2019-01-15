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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.apk.Apk;
import com.google.common.collect.Lists;
import com.google.common.truth.ExpectFailure;
import java.io.File;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApkSubjectTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void notInBadgingOutput() throws Exception {
        List<String> strings = Lists.newArrayList("");

        File file = new File(tmpFolder.getRoot(), "foo");
        Apk apkFile = new Apk(file);
        AssertionError assertionError =
                ExpectFailure.expectFailureAbout(
                        ApkSubject.apks(),
                        whenTesting -> whenTesting.that(apkFile).checkMaxSdkVersion(strings, 1));

        assertThat(assertionError.toString())
                .isEqualTo(
                        "maxSdkVersion not found in badging output for <Apk<"
                                + file.getAbsolutePath()
                                + ">>");
    }

    @Test
    public void findValidValue() throws Exception {
        List<String> strings = Lists.newArrayList(
                "foo",
                "maxSdkVersion:'14'",
                "bar");

        File file = new File(tmpFolder.getRoot(), "foo");
        ApkSubject.assertThat(new Apk(file)).checkMaxSdkVersion(strings, 14);
    }

    @Test
    public void findDifferentValue() throws Exception {
        List<String> strings = Lists.newArrayList(
                "foo",
                "maxSdkVersion:'20'",
                "bar");

        File file = new File(tmpFolder.getRoot(), "foo");
        Apk apkFile = new Apk(file);
        AssertionError assertionError =
                ExpectFailure.expectFailureAbout(
                        ApkSubject.apks(),
                        whenTesting -> whenTesting.that(apkFile).checkMaxSdkVersion(strings, 14));

        assertThat(assertionError.toString())
                .isEqualTo(
                        "Not true that <Apk<"
                                + file.getAbsolutePath()
                                + ">> has maxSdkVersion <14>. It is <20>");
    }
}
