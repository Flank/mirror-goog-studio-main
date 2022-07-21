/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintCopyReportTask
import com.android.build.gradle.internal.lint.AndroidLintGlobalTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.pipeline.IncrementalTransformTask
import com.android.build.gradle.internal.pipeline.NonIncrementalTransformTask
import com.android.build.gradle.internal.pipeline.StreamBasedTask
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask
import com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask
import com.android.build.gradle.internal.res.namespaced.CreateNonNamespacedLibraryManifestTask
import com.android.build.gradle.internal.res.namespaced.GenerateNamespacedLibraryRFilesTask
import com.android.build.gradle.internal.res.namespaced.LinkLibraryAndroidResourcesTask
import com.android.build.gradle.internal.res.namespaced.ProcessAndroidAppResourcesTask
import com.android.build.gradle.internal.res.namespaced.StaticLibraryManifestTask
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.ApkZipPackagingTask
import com.android.build.gradle.internal.tasks.AppClasspathCheckTask
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.internal.tasks.ApplicationIdWriterTask
import com.android.build.gradle.internal.tasks.AssetPackManifestGenerationTask
import com.android.build.gradle.internal.tasks.AssetPackPreBundleTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes
import com.android.build.gradle.internal.tasks.BundleReportDependenciesTask
import com.android.build.gradle.internal.tasks.BundleToApkTask
import com.android.build.gradle.internal.tasks.BundleToStandaloneApkTask
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask
import com.android.build.gradle.internal.tasks.CheckJetifierTask
import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.CheckMultiApkLibrariesTask
import com.android.build.gradle.internal.tasks.CheckProguardFiles
import com.android.build.gradle.internal.tasks.ClasspathComparisonTask
import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.gradle.internal.tasks.CompressAssetsTask
import com.android.build.gradle.internal.tasks.D8BundleMainDexListTask
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.DesugarLibKeepRulesMergeTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.android.build.gradle.internal.tasks.DexFileDependenciesTask
import com.android.build.gradle.internal.tasks.DexMergingTask
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask
import com.android.build.gradle.internal.tasks.ExtractApksTask
import com.android.build.gradle.internal.tasks.ExtractNativeDebugMetadataTask
import com.android.build.gradle.internal.tasks.ExtractProfilerNativeDependenciesTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.FeatureDexMergeTask
import com.android.build.gradle.internal.tasks.FinalizeBundleTask
import com.android.build.gradle.internal.tasks.GenerateApkDataTask
import com.android.build.gradle.internal.tasks.GenerateLibraryProguardRulesTask
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.build.gradle.internal.tasks.InstallVariantViaBundleTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import com.android.build.gradle.internal.tasks.LibraryJniLibsTask
import com.android.build.gradle.internal.tasks.LinkManifestForAssetPackTask
import com.android.build.gradle.internal.tasks.LintCompile
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.tasks.ListingFileRedirectTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestResultAggregationTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.MergeArtProfileTask
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.MergeNativeDebugMetadataTask
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.internal.tasks.ModuleMetadataWriterTask
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import com.android.build.gradle.internal.tasks.PackageBundleTask
import com.android.build.gradle.internal.tasks.PackageForUnitTest
import com.android.build.gradle.internal.tasks.PackageRenderscriptTask
import com.android.build.gradle.internal.tasks.ParseIntegrityConfigTask
import com.android.build.gradle.internal.tasks.PerModuleBundleTask
import com.android.build.gradle.internal.tasks.PerModuleReportDependenciesTask
import com.android.build.gradle.internal.tasks.PrepareLintJarForPublish
import com.android.build.gradle.internal.tasks.ProcessAssetPackManifestTask
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.ProguardConfigurableTask
import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.RecalculateStackFramesTask
import com.android.build.gradle.internal.tasks.SdkDependencyDataGeneratorTask
import com.android.build.gradle.internal.tasks.ShrinkResourcesOldShrinkerTask
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.SourceSetsTask
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureInfoTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportFeatureNamespacesTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingTriggerTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureNameWriterTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadataWriterTask
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclarationWriterTask
import com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask
import com.android.build.gradle.internal.tasks.mlkit.GenerateMlModelClass
import com.android.build.gradle.internal.transforms.LegacyShrinkBundleModuleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkAppBundleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkResourcesNewShrinkerTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import com.android.build.gradle.tasks.sync.AbstractVariantModelTask
import com.android.build.gradle.tasks.sync.AndroidTestVariantModelTask
import com.android.build.gradle.tasks.sync.ApplicationVariantModelTask
import com.android.build.gradle.tasks.sync.DynamicFeatureVariantModelTask
import com.android.build.gradle.tasks.sync.LibraryVariantModelTask
import com.android.build.gradle.tasks.sync.ModuleVariantModelTask
import com.android.build.gradle.tasks.sync.TestModuleVariantModelTask
import com.android.build.gradle.tasks.sync.TestVariantModelTask
import com.android.build.gradle.tasks.sync.UnitTestVariantModelTask
import org.gradle.api.Task
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertEquals

class BuildAnalyzerTest {

    @Test
    fun `all tasks have build analyzer annotations`() {
        val allTasks = getAllTasks()
        // When annotations for all tasks are added,
        // can just assert missingTasks is empty
        val missingTasks = allTasks.filter {
            !it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        val tasksWithAnnotations = allTasks.filter {
            it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        // Make sure a task was not added twice in list
        assertEquals(TASKS_WITH_ANNOTATIONS.toSet().size, TASKS_WITH_ANNOTATIONS.size)
        // Make sure tasks without annotation weren't accidentally added in
        assertThat(missingTasks).containsNoneIn(TASKS_WITH_ANNOTATIONS)
        assertThat(tasksWithAnnotations).containsExactlyElementsIn(TASKS_WITH_ANNOTATIONS)
        assertEquals(allTasks.size,missingTasks.size + TASKS_WITH_ANNOTATIONS.size)
    }

    //  List of tasks which has BuildAnalyzerAnnotation, added manually
    private val TASKS_WITH_ANNOTATIONS = listOf(
            AidlCompile::class.java,
            JavaPreCompileTask::class.java,
            CompileArtProfileTask::class.java,
            LintCompile::class.java,
            CompileLibraryResourcesTask::class.java,
            RenderscriptCompile::class.java,
            ShaderCompile::class.java,
            AndroidReportTask::class.java,
            AndroidUnitTest::class.java,
            DeviceProviderInstrumentTestTask::class.java,
            JacocoReportTask::class.java,
            TestServerTask::class.java,
            GenerateTestConfig::class.java,
            PackageForUnitTest::class.java,
            JacocoTask::class.java,
            ManagedDeviceInstrumentationTestTask::class.java,
            DeviceSerialTestTask::class.java,
            ManagedDeviceInstrumentationTestResultAggregationTask::class.java,
            CheckManifest::class.java,
            CompatibleScreensManifest::class.java,
            InvokeManifestMerger::class.java,
            ProcessApplicationManifest::class.java,
            ProcessLibraryManifest::class.java,
            ProcessTestManifest::class.java,
            StaticLibraryManifestTask::class.java,
            CreateNonNamespacedLibraryManifestTask::class.java,
            ManifestProcessorTask::class.java,
            AssetPackManifestGenerationTask::class.java,
            ProcessAssetPackManifestTask::class.java,
            LinkManifestForAssetPackTask::class.java,
            ProcessManifestForBundleTask::class.java,
            ProcessManifestForMetadataFeatureTask::class.java,
            ProcessManifestForInstantAppTask::class.java,
            ProcessPackagedManifestTask::class.java,
            ProcessMultiApkApplicationManifest::class.java,
            GenerateManifestJarTask::class.java,
            LinkLibraryAndroidResourcesTask::class.java,
            LinkApplicationAndroidResourcesTask::class.java,
            LinkAndroidResForBundleTask::class.java,
            GenerateResValues::class.java,
            MergeResources::class.java,
            VerifyLibraryResourcesTask::class.java,
            ProcessAndroidAppResourcesTask::class.java,
            GenerateLibraryRFileTask::class.java,
            GenerateNamespacedLibraryRFilesTask::class.java,
            LegacyShrinkBundleModuleResourcesTask::class.java,
            ParseLibraryResourcesTask::class.java,
            ExtractDeepLinksTask::class.java,
            ShrinkResourcesOldShrinkerTask::class.java,
            GenerateEmptyResourceFilesTask::class.java,
            OptimizeResourcesTask::class.java,
            ShrinkAppBundleResourcesTask::class.java,
            ShrinkResourcesNewShrinkerTask::class.java,
            PrivacySandboxSdkLinkAndroidResourcesTask::class.java,
            ExternalNativeBuildJsonTask::class.java,
            ExternalNativeBuildTask::class.java,
            ExternalNativeCleanTask::class.java,
            MergeNativeLibsTask::class.java,
            StripDebugSymbolsTask::class.java,
            LibraryJniLibsTask::class.java,
            ExtractNativeDebugMetadataTask::class.java,
            MergeNativeDebugMetadataTask::class.java,
            PrefabPackageTask::class.java,
            PrefabPackageConfigurationTask::class.java,
            ProcessJavaResTask::class.java,
            BundleLibraryJavaRes::class.java,
            MergeJavaResourceTask::class.java,
            JavaDocGenerationTask::class.java,
            JavaDocJarTask::class.java,
            PackageRenderscriptTask::class.java,
            DexMergingTask::class.java,
            DexFileDependenciesTask::class.java,
            DexArchiveBuilderTask::class.java,
            L8DexDesugarLibTask::class.java,
            D8BundleMainDexListTask::class.java,
            FeatureDexMergeTask::class.java,
            PrivacySandboxSdkDexTask::class.java,
            PrivacySandboxSdkMergeDexTask::class.java,
            MergeArtProfileTask::class.java,
            ProcessLibraryArtProfileTask::class.java,
            PrepareLintJarForPublish::class.java,
            LintModelWriterTask::class.java,
            AndroidLintTask::class.java,
            AndroidLintGlobalTask::class.java,
            AndroidLintCopyReportTask::class.java,
            AndroidLintAnalysisTask::class.java,
            LintModelMetadataTask::class.java,
            AndroidLintTextOutputTask::class.java,
            DataBindingTriggerTask::class.java,
            DataBindingGenBaseClassesTask::class.java,
            DataBindingExportFeatureNamespacesTask::class.java,
            DataBindingExportFeatureInfoTask::class.java,
            DataBindingMergeDependencyArtifactsTask::class.java,
            GenerateApkDataTask::class.java,
            FeatureSplitDeclarationWriterTask::class.java,
            ModuleMetadataWriterTask::class.java,
            FeatureSetMetadataWriterTask::class.java,
            ApplicationIdWriterTask::class.java,
            ParseIntegrityConfigTask::class.java,
            FeatureNameWriterTask::class.java,
            SdkDependencyDataGeneratorTask::class.java,
            AarMetadataTask::class.java,
            CheckAarMetadataTask::class.java,
            GenerateApiPublicTxtTask::class.java,
            AppMetadataTask::class.java,
            SigningConfigVersionsWriterTask::class.java,
            ListingFileRedirectTask::class.java,
            ApplicationVariantModelTask::class.java,
            AbstractVariantModelTask::class.java,
            LibraryVariantModelTask::class.java,
            DynamicFeatureVariantModelTask::class.java,
            AndroidTestVariantModelTask::class.java,
            TestVariantModelTask::class.java,
            UnitTestVariantModelTask::class.java,
            ModuleVariantModelTask::class.java,
            TestModuleVariantModelTask::class.java,
            InstallVariantTask::class.java,
            UninstallTask::class.java,
            InstallVariantViaBundleTask::class.java,
            DependencyReportTask::class.java,
            SigningReportTask::class.java,
            SourceSetsTask::class.java,
            PackagedDependenciesWriterTask::class.java,
            AnalyzeDependenciesTask::class.java,
            ClasspathComparisonTask::class.java,
            UnsafeOutputsTask::class.java,
            PackageApplication::class.java,
            ExtractApksTask::class.java,
            BundleToApkTask::class.java,
            BundleToStandaloneApkTask::class.java,
            SigningConfigWriterTask::class.java,
            ApkZipPackagingTask::class.java,
            BundleAar::class.java,
            FusedLibraryBundleAar::class.java,
            PackageBundleTask::class.java,
            PerModuleBundleTask::class.java,
            FinalizeBundleTask::class.java,
            BundleReportDependenciesTask::class.java,
            PerModuleReportDependenciesTask::class.java,
            AssetPackPreBundleTask::class.java,
            PackagePrivacySandboxSdkBundle::class.java,
            ValidateSigningTask::class.java,
            CheckTestedAppObfuscation::class.java,
            CheckMultiApkLibrariesTask::class.java,
            AppClasspathCheckTask::class.java,
            CheckDuplicateClassesTask::class.java,
            CheckJetifierTask::class.java,
            ExtractProguardFiles::class.java,
            CheckProguardFiles::class.java,
            MergeConsumerProguardFilesTask::class.java,
            GenerateLibraryProguardRulesTask::class.java,
            ProguardConfigurableTask::class.java,
            ExportConsumerProguardFilesTask::class.java,
            R8Task::class.java,
            CompressAssetsTask::class.java,
            GenerateBuildConfig::class.java,
            GenerateMlModelClass::class.java,
            PrivacySandboxSdkManifestGeneratorTask::class.java,
            ExtractAnnotations::class.java,
            MergeSourceSetFolders::class.java,
            StreamBasedTask::class.java,
            TransformTask::class.java,
            RecalculateStackFramesTask::class.java,
            ExtractProfilerNativeDependenciesTask::class.java,
            TransformClassesWithAsmTask::class.java,
            DesugarLibKeepRulesMergeTask::class.java,
            FusedLibraryClassesRewriteTask::class.java,
            IncrementalTransformTask::class.java,
            NonIncrementalTransformTask::class.java,
    ) as List<Class<*>>

    private fun getAllTasks(): List<Class<*>> {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val taskInterface = TypeToken.of(Task::class.java)
        return classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map{
                    classInfo -> classInfo.load() as Class<*>
                }
                .filter{
                    TypeToken.of(it).getTypes().contains(taskInterface)
                }
    }
}
