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

package com.android.ide.common.repository

import com.android.ide.common.resources.BaseTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoogleMavenRepositoryTest : BaseTestCase() {
    companion object {
        @ClassRule
        @JvmField
        var temp = TemporaryFolder()

        /**
         * Snapshot of what the versions were when this test was written.
         *
         * This way tests don't break when we update.
         */
        private val builtInData = mapOf(
            "master-index.xml" to """
                <?xml version='1.0' encoding='UTF-8'?>
                <metadata>
                  <com.android.support.constraint/>
                  <com.android.databinding/>
                  <com.android.support/>
                  <com.android.support.test/>
                  <com.android.support.test.janktesthelper/>
                  <com.android.support.test.uiautomator/>
                  <com.android.support.test.espresso/>
                  <android.arch.persistence.room/>
                  <android.arch.lifecycle/>
                  <android.arch.core/>
                  <com.google.android.instantapps/>
                  <com.google.android.instantapps.thirdpartycompat/>
                  <com.android.java.tools.build/>
                  <com.android.tools/>
                  <com.android.tools.layoutlib/>
                  <com.android.tools.ddms/>
                  <com.android.tools.external.com-intellij/>
                  <com.android.tools.build/>
                  <com.android.tools.analytics-library/>
                  <com.android.tools.internal.build.test/>
                  <com.android.tools.lint/>
                </metadata>
            """.trimIndent(),
            "com/android/support/group-index.xml" to """
                <?xml version='1.0' encoding='UTF-8'?>
                <com.android.support>
                  <support-compat versions="25.3.1,26.0.0-beta1"/>
                  <leanback-v17 versions="25.3.1,26.0.0-beta1"/>
                  <recommendation versions="25.3.1,26.0.0-beta1"/>
                  <support-tv-provider versions="26.0.0-beta1"/>
                  <support-vector-drawable versions="25.3.1,26.0.0-beta1"/>
                  <recyclerview-v7 versions="25.3.1,26.0.0-beta1"/>
                  <preference-leanback-v17 versions="25.3.1,26.0.0-beta1"/>
                  <preference-v14 versions="25.3.1,26.0.0-beta1"/>
                  <percent versions="25.3.1,26.0.0-beta1"/>
                  <support-media-compat versions="25.3.1,26.0.0-beta1"/>
                  <cardview-v7 versions="25.3.1,26.0.0-beta1"/>
                  <wearable versions="26.0.0-alpha1"/>
                  <exifinterface versions="25.3.1,26.0.0-beta1"/>
                  <support-annotations versions="25.3.1,26.0.0-beta1"/>
                  <appcompat-v7 versions="25.3.1,26.0.0-beta1"/>
                  <palette-v7 versions="25.3.1,26.0.0-beta1"/>
                  <multidex-instrumentation versions="1.0.1,1.0.1"/>
                  <multidex versions="1.0.1,1.0.1"/>
                  <mediarouter-v7 versions="25.3.1,26.0.0-beta1"/>
                  <preference-v7 versions="25.3.1,26.0.0-beta1"/>
                  <support-dynamic-animation versions="25.3.1,26.0.0-beta1"/>
                  <support-fragment versions="25.3.1,26.0.0-beta1"/>
                  <design versions="25.3.1,26.0.0-beta1"/>
                  <transition versions="25.3.1,26.0.0-beta1"/>
                  <customtabs versions="25.3.1,26.0.0-beta1"/>
                  <support-core-ui versions="25.3.1,26.0.0-beta1"/>
                  <gridlayout-v7 versions="25.3.1,26.0.0-beta1"/>
                  <animated-vector-drawable versions="25.3.1,26.0.0-beta1"/>
                  <support-core-utils versions="25.3.1,26.0.0-beta1"/>
                  <support-v13 versions="25.3.1,26.0.0-beta1"/>
                  <instantvideo versions="26.0.0-alpha1"/>
                  <support-v4 versions="25.3.1,26.0.0-beta1"/>
                  <support-emoji versions="26.0.0-beta1"/>
                  <wear versions="26.0.0-beta1"/>
                  <support-emoji-appcompat versions="26.0.0-beta1"/>
                  <support-emoji-bundled versions="26.0.0-beta1"/>
                </com.android.support>
            """.trimIndent(),
            "com/android/support/support-compat/25.3.1/support-compat-25.3.1.pom" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.android.support</groupId>
                  <artifactId>support-compat</artifactId>
                  <version>25.3.1</version>
                  <packaging>aar</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>support-annotations</artifactId>
                      <version>25.3.1</version>
                      <scope>compile</scope>
                    </dependency>
                  </dependencies>
                </project>
            """.trimIndent(),
            "com/android/support/leanback-v17/25.3.1/leanback-v17-25.3.1.pom" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.android.support</groupId>
                  <artifactId>leanback-v17</artifactId>
                  <version>25.3.1</version>
                  <packaging>aar</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>support-compat</artifactId>
                      <version>25.3.1</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>support-core-ui</artifactId>
                      <version>25.3.1</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>support-media-compat</artifactId>
                      <version>25.3.1</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>support-fragment</artifactId>
                      <version>25.3.1</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>com.android.support</groupId>
                      <artifactId>recyclerview-v7</artifactId>
                      <version>[25.3.1.4.5,25.4.0)</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>androidx.recyclerview</groupId>
                      <artifactId>recyclerview</artifactId>
                      <version>2.0.0</version>
                      <type>aar</type>
                      <scope>compile</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <version>1.9.5</version>
                      <type>aar</type>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
            """.trimIndent()
        )
    }

    @Test
    fun testBuiltin() {
        val repo =
            StubGoogleMavenRepository(builtInData = builtInData) // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = true)
        assertNotNull(version)
        assertEquals("26.0.0-beta1", version.toString())
    }

    @Test
    fun testBuiltinStableOnly() {
        val repo =
            StubGoogleMavenRepository(builtInData = builtInData) // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = false)
        assertNotNull(version)
        assertEquals("25.3.1", version.toString())
    }

    @Test
    fun testBuiltinFiltered() {
        val repo =
            StubGoogleMavenRepository(builtInData = builtInData) // no cache dir set: will only read built-in index
        val version =
            repo.findVersion("com.android.support", "appcompat-v7", filter = { it.major == 12 })
        assertNull(version)
    }

    @Test
    fun testBuiltinDependency() {
        val repo =
            StubGoogleMavenRepository(builtInData = builtInData) // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "leanback-v17")
        val dependencies = repo.findCompileDependencies("com.android.support", "leanback-v17", version!!)
        assertThat(dependencies).containsExactly(
            GradleCoordinate.parseCoordinateString("com.android.support:support-compat:25.3.1"),
            GradleCoordinate.parseCoordinateString("com.android.support:support-core-ui:25.3.1"),
            GradleCoordinate.parseCoordinateString("com.android.support:support-media-compat:25.3.1"),
            GradleCoordinate.parseCoordinateString("com.android.support:support-fragment:25.3.1"),
            GradleCoordinate.parseCoordinateString("com.android.support:recyclerview-v7:[25.3.1.4.5,25.4.0)"),
            GradleCoordinate.parseCoordinateString("androidx.recyclerview:recyclerview:2.0.0"))
        assertThat(dependencies[3].versionRange?.min).isEqualTo(GradleVersion.parse("25.3.1"))
        assertThat(dependencies[3].versionRange?.max).isEqualTo(GradleVersion.parse("25.3.2"))
        assertThat(dependencies[4].versionRange?.min).isEqualTo(GradleVersion.parse("25.3.1.4.5"))
        assertThat(dependencies[4].versionRange?.max).isEqualTo(GradleVersion.parse("25.4.0"))
        assertThat(dependencies[5].versionRange?.min).isEqualTo(GradleVersion.parse("2.0.0"))
        assertThat(dependencies[5].versionRange?.max).isEqualTo(GradleVersion.parse("3.0.0"))
    }

    @Test
    fun testReadingFromUrl() {
        val repo = StubGoogleMavenRepository(
            cacheDir = temp.root,
            urls = mapOf(
                "https://maven.google.com/master-index.xml" to """
                 <?xml version='1.0' encoding='UTF-8'?>
                 <metadata>
                   <foo.bar/>
                   <foo.bar.baz/>
                 </metadata>""".trimIndent(),
                "https://maven.google.com/foo/bar/group-index.xml" to """
                 <?xml version='1.0' encoding='UTF-8'?>
                 <foo.bar>
                   <my-artifact versions="1.0.1-alpha1"/>
                   <another-artifact versions="2.5.0,2.6.0-rc1"/>
                 </foo.bar>""".trimIndent()
            )
        )
        val version = repo.findVersion("foo.bar", "my-artifact", allowPreview = true)
        assertNotNull(version)
        assertEquals("1.0.1-alpha1", version.toString())

        val gc1 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.5.+")
        assertEquals("2.5.0", repo.findVersion(gc1!!).toString())
        val gc2 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.6.0-alpha1")
        assertEquals("2.6.0-rc1", repo.findVersion(gc2!!).toString())
        val gc3 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.6.+")
        assertEquals("2.6.0-rc1", repo.findVersion(gc3!!, null, allowPreview = true).toString())

        assertEquals(setOf("foo.bar", "foo.bar.baz"), repo.getGroups())
        assertEquals(setOf("my-artifact", "another-artifact"), repo.getArtifacts("foo.bar"))
        assertEquals(setOf(GradleVersion.parse("2.5.0"), GradleVersion.parse("2.6.0-rc1")), repo.getVersions("foo.bar", "another-artifact"))
    }
}
