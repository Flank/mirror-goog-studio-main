/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG
import com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlin.streams.toList
import org.junit.Rule
import org.junit.Test

class ShrinkBundleResourcesTest {
    @get:Rule
    var project = builder().fromTestProject("shrink")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN_GRADLE_6_6)
        .setTargetGradleVersion("6.6-20200609220026+0000")
        // b/157470515, http://b/149978740
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=5")
        .create()

    @get:Rule
    var projectWithDfms = builder().fromTestProject("shrinkDynamicFeatureModules")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN_GRADLE_6_6)
        .setTargetGradleVersion("6.6-20200609220026+0000")
        // b/157470515, http://b/149978740
        .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=5")
        .create()

    @Test
    fun `shrink resources in single module bundles`() {
        project.executor()
            .with(OptionalBooleanOption.ENABLE_R8, true)
            .with(BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER, true)
            .run(
                "clean",
                "packageDebugUniversalApk",
                "packageReleaseUniversalApk"
            )

        // Check that unused resources are replaced in shrunk bundle.
        val replacedFiles = listOf(
            "res/drawable-hdpi-v4/notification_bg_normal.9.png",
            "res/drawable-hdpi-v4/notification_bg_low_pressed.9.png",
            "res/drawable-hdpi-v4/notification_bg_low_normal.9.png",
            "res/drawable-hdpi-v4/notify_panel_notification_icon_bg.png",
            "res/drawable-hdpi-v4/notification_bg_normal_pressed.9.png",
            "res/drawable-mdpi-v4/notification_bg_normal.9.png",
            "res/drawable-mdpi-v4/notification_bg_low_pressed.9.png",
            "res/drawable-mdpi-v4/notification_bg_low_normal.9.png",
            "res/drawable-mdpi-v4/notify_panel_notification_icon_bg.png",
            "res/drawable-mdpi-v4/notification_bg_normal_pressed.9.png",
            "res/drawable-xhdpi-v4/notification_bg_normal.9.png",
            "res/drawable-xhdpi-v4/notification_bg_low_pressed.9.png",
            "res/drawable-xhdpi-v4/notification_bg_low_normal.9.png",
            "res/drawable-xhdpi-v4/notify_panel_notification_icon_bg.png",
            "res/drawable-xhdpi-v4/notification_bg_normal_pressed.9.png",
            "res/drawable-v21/notification_action_background.xml",
            "res/drawable/force_remove.xml",
            "res/drawable/notification_bg.xml",
            "res/drawable/notification_bg_low.xml",
            "res/drawable/notification_icon_background.xml",
            "res/drawable/notification_tile_bg.xml",
            "res/drawable/unused9.xml",
            "res/drawable/unused10.xml",
            "res/drawable/unused11.xml",
            "res/layout-v16/notification_template_custom_big.xml",
            "res/layout-v17/notification_template_lines_media.xml",
            "res/layout-v17/notification_action_tombstone.xml",
            "res/layout-v17/notification_template_media.xml",
            "res/layout-v17/notification_template_big_media_custom.xml",
            "res/layout-v17/notification_template_custom_big.xml",
            "res/layout-v17/notification_template_big_media_narrow_custom.xml",
            "res/layout-v17/notification_template_big_media_narrow.xml",
            "res/layout-v17/notification_template_big_media.xml",
            "res/layout-v17/notification_action.xml",
            "res/layout-v17/notification_template_media_custom.xml",
            "res/layout-v21/notification_template_custom_big.xml",
            "res/layout-v21/notification_action.xml",
            "res/layout-v21/notification_action_tombstone.xml",
            "res/layout-v21/notification_template_icon_group.xml",
            "res/layout/lib_unused.xml",
            "res/layout/marked_as_used_by_old.xml",
            "res/layout/notification_action.xml",
            "res/layout/notification_action_tombstone.xml",
            "res/layout/notification_media_action.xml",
            "res/layout/notification_media_cancel_action.xml",
            "res/layout/notification_template_big_media.xml",
            "res/layout/notification_template_big_media_custom.xml",
            "res/layout/notification_template_big_media_narrow.xml",
            "res/layout/notification_template_big_media_narrow_custom.xml",
            "res/layout/notification_template_icon_group.xml",
            "res/layout/notification_template_lines_media.xml",
            "res/layout/notification_template_media.xml",
            "res/layout/notification_template_media_custom.xml",
            "res/layout/notification_template_part_chronometer.xml",
            "res/layout/notification_template_part_time.xml",
            "res/layout/unused1.xml",
            "res/layout/unused2.xml",
            "res/layout/unused14.xml",
            "res/layout/unused13.xml",
            "res/menu/unused12.xml",
            "res/raw/keep.xml"
        )
        assertThat(diffFiles(project.getOriginalBundle(), project.getShrunkBundle()))
            .containsExactlyElementsIn(replacedFiles.map { "base/$it" })

        // Check that unused resources are replaced in release APK and leave as is in debug one.
        assertThat(
            diffFiles(
                project.getBundleUniversalApk(DEBUG).file.toFile(),
                project.getBundleUniversalApk(RELEASE).file.toFile(),
                setOf("META-INF/BNDLTOOL.RSA", "META-INF/BNDLTOOL.SF", "META-INF/MANIFEST.MF")
            )
        ).containsExactly("classes.dex", "AndroidManifest.xml", *replacedFiles.toTypedArray())

        // Check that unused resources are removed in project with web views and all web view
        // resources are marked as used.
        assertThat(
            diffFiles(
                project.getSubproject("webview").getOriginalBundle(),
                project.getSubproject("webview").getShrunkBundle()
            )
        ).containsExactly(
            "base/res/raw/unused_icon.png",
            "base/res/raw/unused_index.html",
            "base/res/xml/my_xml.xml"
        )
        // Check that replaced files has proper dummy content.
        assertThat(project.getSubproject("webview").getShrunkBundle()) {
            it.containsFileWithContent("base/res/raw/unused_icon.png", TINY_PNG)
            it.containsFileWithContent("base/res/raw/unused_index.html", "")
            it.containsFileWithContent("base/res/xml/my_xml.xml", TINY_PROTO_XML)
        }

        // Check that unused resources that are referenced with Resources.getIdentifier are removed
        // in case shrinker mode is set to 'strict'.
        assertThat(
            diffFiles(
                project.getSubproject("keep").getOriginalBundle(),
                project.getSubproject("keep").getShrunkBundle()
            )
        ).containsExactly(
            "base/res/raw/keep.xml",
            "base/res/layout/unused1.xml",
            "base/res/layout/unused2.xml"
        )
    }

    @Test
    fun `shrink resources in bundles with dynamic feature module`() {
        projectWithDfms.executor()
            .with(OptionalBooleanOption.ENABLE_R8, true)
            .with(BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER, true)
            .run("signReleaseBundle")

        // Check that unused resources are replaced in shrunk bundle.
        val originalBundle = projectWithDfms.getSubproject("base").getOriginalBundle()
        val shrunkBundle = projectWithDfms.getSubproject("base").getShrunkBundle()
        assertThat(diffFiles(originalBundle, shrunkBundle)).containsExactly(
            "feature/res/drawable/feat_unused.png",
            "feature/res/drawable/discard_from_feature_1.xml",
            "feature/res/layout/feat_unused_layout.xml",
            "feature/res/raw/feat_keep.xml",
            "feature/res/raw/webpage.html",
            "base/res/drawable/discard_from_feature_2.xml",
            "base/res/drawable/force_remove.xml",
            "base/res/drawable/unused5.9.png",
            "base/res/drawable/unused9.xml",
            "base/res/drawable/unused10.xml",
            "base/res/drawable/unused11.xml",
            "base/res/layout/unused1.xml",
            "base/res/layout/unused2.xml",
            "base/res/layout/unused13.xml",
            "base/res/layout/unused14.xml",
            "base/res/menu/unused12.xml",
            "base/res/raw/keep.xml"
        )

        // Check that replaced files release bundle have proper dummy content.
        val releaseBundle = projectWithDfms.getSubproject("base")
            .getOutputFile("bundle", "release", "base-release.aab")

        assertThat(releaseBundle) {
            it.containsFileWithContent("feature/res/drawable/feat_unused.png", TINY_PNG)
            it.containsFileWithContent("feature/res/layout/feat_unused_layout.xml", TINY_PROTO_XML)
            it.containsFileWithContent("feature/res/raw/webpage.html", "")
            it.containsFileWithContent("base/res/layout/unused1.xml", TINY_PROTO_XML)
            it.containsFileWithContent("base/res/raw/keep.xml", TINY_PROTO_XML)
            it.containsFileWithContent("base/res/drawable/unused5.9.png", TINY_9PNG)
        }
    }

    private fun diffFiles(
        original: File,
        shrunk: File,
        skipEntries: Set<String> = emptySet()
    ): List<String> =
        FileUtils.createZipFilesystem(original.toPath()).use { originalBundle ->
            FileUtils.createZipFilesystem(shrunk.toPath()).use { shrunkBundle ->
                val shrunkRoot = shrunkBundle.getPath("/")
                Files.walk(originalBundle.getPath("/"))
                    .filter { Files.isRegularFile(it) }
                    .filter { !skipEntries.contains(it.toString().trimStart('/')) }
                    .filter { originalPath ->
                        val shrunkPath = shrunkRoot.resolve(originalPath.toString())
                        val originalContent = Files.readAllBytes(originalPath)
                        val shrunkContent = Files.readAllBytes(shrunkPath)
                        !shrunkContent.contentEquals(originalContent)
                    }
                    .map { it.toString().trimStart('/') }
                    .toList()
            }
        }

    private fun GradleTestProject.getOriginalBundle() =
        getIntermediateFile(
            "intermediary_bundle",
            "release",
            "packageReleaseBundle",
            "intermediary-bundle.aab"
        )

    private fun GradleTestProject.getShrunkBundle() =
        getIntermediateFile(
            "intermediary_bundle",
            "release",
            "shrinkBundleReleaseResources",
            "intermediary-bundle.aab"
        )
}
