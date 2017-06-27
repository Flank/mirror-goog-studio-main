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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;

/** An empty app. */
public final class MinimalSubProject extends AbstractAndroidTestApp implements AndroidTestApp {

    private MinimalSubProject(@NonNull String plugin, @Nullable String packageName) {
        String content = "\n" + "apply plugin: '" + plugin + "'\n";
        if (packageName != null) {
            content =
                    content
                            + "\n"
                            + "android.compileSdkVersion "
                            + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                            + "\n";
            TestSourceFile manifest =
                    new TestSourceFile(
                            "src/main",
                            "AndroidManifest.xml",
                            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                    + "    package=\""
                                    + packageName
                                    + "\" />");
            addFiles(manifest);
        }
        TestSourceFile build = new TestSourceFile("", "build.gradle", content);
        addFiles(build);
    }

    @NonNull
    public static MinimalSubProject lib(@NonNull String packageName) {
        return new MinimalSubProject("com.android.library", packageName);
    }

    @NonNull
    public static MinimalSubProject feature(@NonNull String packageName) {
        return new MinimalSubProject("com.android.feature", packageName);
    }

    @NonNull
    public static MinimalSubProject app(@NonNull String packageName) {
        return new MinimalSubProject("com.android.application", packageName);
    }

    public static MinimalSubProject instantApp() {
        return new MinimalSubProject("com.android.instantapp", null);
    }

    @Override
    public boolean containsFullBuildScript() {
        return true;
    }

    @NonNull
    public MinimalSubProject withFile(@NonNull String path, @NonNull String content) {
        int pos = path.lastIndexOf('/');
        if (pos == -1) {
            return withFile(new TestSourceFile("", path, content));
        }
        return withFile(
                new TestSourceFile(path.substring(0, pos), path.substring(pos + 1), content));
    }

    @NonNull
    public MinimalSubProject withFile(@NonNull TestSourceFile file) {
        addFile(file);
        return this;
    }

    @NonNull
    public MinimalSubProject appendToBuild(String snippet) {
        replaceFile(getFile("build.gradle", "").appendContent("\n" + snippet + "\n"));
        return this;
    }
}
