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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Rule
import org.junit.Test

/**
 * Sanity tests for the new namespaced resource pipeline with publication and consumption of an aar.
 *
 * Project structured such that app an lib depend on an aar (flatdir) from publishedLib
 * </pre>
 */
class NamespacedAarTest {

    val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
            .appendToBuild("android.aaptOptions.namespaced = true")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources><string name="foo">publishedLib</string></resources>""")
            .withFile(
                    "src/main/java/com/example/publishedLib/Example.java",
                    """package com.example.publishedLib;
                    public class Example {
                        public static int CONSTANT = 4;
                        public static int getFooString() { return R.string.foo; }
                    }""")

    val lib = MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                    """android.aaptOptions.namespaced = true
                    repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="from_published_lib">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/lib2/Example.java",
                    """package com.example.lib2;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """android.aaptOptions.namespaced = true
                    repositories { flatDir { dirs rootProject.file('publishedLib/build/outputs/aar/') } }
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="mystring">My String</string>
                        <string name="from_lib1">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                        public static final int APP_STRING = R.string.mystring;
                        public static final int PUBLISHED_LIB_STRING =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":publishedLib", publishedLib)
                    .subproject(":lib", lib)
                    .subproject(":app", app)
                    .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkBuilds() {
        project.executor().run(":publishedLib:assembleRelease")
        project.executor().run(":lib:assembleDebug", ":app:assembleDebug")

    }

}
