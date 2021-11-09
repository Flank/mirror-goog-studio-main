/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.gradle.api.Task
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.Incremental
import org.junit.Test
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Supplier

class TaskMethodModifiersAndAnnotationsTest {

    @Test
    fun `check for non-public methods with gradle input or output annotations`() {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val nonPublicMethods =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredMethods.asIterable() }
                .filter {
                    it.hasGradleInputOrOutputAnnotation() && !Modifier.isPublic(it.modifiers)
                }

        if (nonPublicMethods.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append("The following gradle-annotated methods are not public:\n")
        for (nonPublicMethod in nonPublicMethods) {
            error.append(nonPublicMethod.declaringClass.toString().substringAfter(" "))
                .append("::${nonPublicMethod.name}\n")
        }
        throw AssertionError(error.toString())
    }

    @Test
    fun `check for fields with gradle input or output annotations`() {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val annotatedFields =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredFields.asIterable() }
                .filter { it.hasGradleInputOrOutputAnnotation() }

        if (annotatedFields.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append(
                "The following fields are annotated with gradle input/output annotations, which "
                        + "should only be used on methods (e.g., the corresponding getters):\n")
        for (annotatedField in annotatedFields) {
            error.append(annotatedField.declaringClass.toString().substringAfter(" "))
                .append(".${annotatedField.name}\n")
        }
        throw AssertionError(error.toString())
    }

    @Test
    fun `check for public task setters`() {

        val currentPublicSetters =
            listOf(
                "com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask::setType",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setReportType",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setWillRun",
                "com.android.build.gradle.internal.tasks.AndroidVariantTask::setVariantName",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setSerialOption",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setUtpTestResultListener",
                "com.android.build.gradle.internal.tasks.DeviceSerialTestTask::setSerialOption",
                "com.android.build.gradle.internal.tasks.IncrementalTask::setIncrementalFolder",
                "com.android.build.gradle.internal.tasks.InstallVariantTask::setInstallOptions",
                "com.android.build.gradle.internal.tasks.InstallVariantTask::setTimeOutInMs",
                "com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask::setPreserveDefinedOption",
                "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask::setDisplayEmulatorOption",
                "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.NdkTask::setNdkConfig",
                "com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask::setVariantName",
                "com.android.build.gradle.internal.tasks.PackageRenderscriptTask::setVariantName",
                "com.android.build.gradle.internal.tasks.MergeAssetsForUnitTest::setVariantName",
                "com.android.build.gradle.internal.tasks.ProcessJavaResTask::setVariantName",
                "com.android.build.gradle.internal.tasks.SigningReportTask::setComponents",
                "com.android.build.gradle.internal.tasks.TestServerTask::setTestServer",
                "com.android.build.gradle.internal.tasks.UninstallTask::setTimeOutInMs",
                "com.android.build.gradle.tasks.factory.AndroidUnitTest::setVariantName",
                "com.android.build.gradle.tasks.BundleAar::setVariantName",
                "com.android.build.gradle.tasks.ExtractAnnotations::setBootClasspath",
                "com.android.build.gradle.tasks.ExtractAnnotations::setEncoding",
                "com.android.build.gradle.tasks.GenerateResValues::setResOutputDir",
                "com.android.build.gradle.tasks.InvokeManifestMerger::setMainManifestFile",
                "com.android.build.gradle.tasks.InvokeManifestMerger::setOutputFile",
                "com.android.build.gradle.tasks.InvokeManifestMerger::setSecondaryManifestFiles",
                "com.android.build.gradle.tasks.PackageAndroidArtifact::setJniDebugBuild",
                "com.android.build.gradle.tasks.RenderscriptCompile::setImportDirs",
                "com.android.build.gradle.tasks.RenderscriptCompile::setObjOutputDir",
                "com.android.build.gradle.tasks.ShaderCompile::setDefaultArgs",
                "com.android.build.gradle.tasks.ShaderCompile::setScopedArgs",
                "com.android.build.gradle.tasks.SourceJarTask::setVariantName",
                "com.android.build.gradle.tasks.JavaDocJarTask::setVariantName",
            )

        val classPath = ClassPath.from(this.javaClass.classLoader)
        val taskInterface = TypeToken.of(Task::class.java)
        val publicSetters =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .filter { clazz -> TypeToken.of(clazz).types.contains(taskInterface) }
                .flatMap { it.declaredMethods.asIterable() }
                .filter {
                    it.name.startsWith("set")
                            && !it.name.contains('$')
                            && Modifier.isPublic(it.modifiers)
                }

        val publicSettersAsStrings = publicSetters
                .map { "${it.declaringClass.toString().substringAfter(" ")}::${it.name}" }

        assertThat(publicSettersAsStrings)
            .named("Task public setters")
            .containsExactlyElementsIn(currentPublicSetters)

        // Check for getters and setters that have different types than can upset gradle's instansiator.
        val mismatchingGetters = publicSetters.filter { setter ->
            val matchingGetter = getMatchingGetter(setter)
            matchingGetter != null && setter.parameters.size == 1 && setter.parameters[0].type != matchingGetter.returnType
        }
        assertWithMessage("Getters and setter types don't match")
            .that(mismatchingGetters.map { "${getMatchingGetter(it)}  -  $it" }).isEmpty()
    }

    @Test
    fun checkWorkerFacadeIsNotAField() {
        Truth.assertThat(findTaskFieldsOfType(WorkerExecutorFacade::class.java)).isEmpty()
    }

    @Test
    fun checkVariantScopeIsNotAField() {
        Truth.assertThat(findTaskFieldsOfType(VariantScope::class.java)).isEmpty()
    }

    @Test
    fun checkVariantConfigurationIsNotAField() {
        Truth.assertThat(findTaskFieldsOfType(VariantDslInfo::class.java)).isEmpty()
    }

    @Test
    fun checkSupplierIsNotAField() {
        assertThat(findTaskFieldsOfType(Supplier::class.java)).isEmpty()
    }

    /**
     * Test that the workaround for https://github.com/gradle/gradle/issues/16976 in
     * [PackageAndroidArtifact] is kept up to date.
     *
     * Adding new incremental inputs to [PackageAndroidArtifact] without updating this test will
     * cause this test to fail. Please add any new incremental inputs to the appropriate
     * non-incremental input (e.g., [PackageAndroidArtifact.getAllClasspathInputFiles]) before
     * updating this test.
     */
    @Test
    fun checkIncrementalPackagingInputs() {
        val incrementalInputs =
            ClassPath.from(this.javaClass.classLoader)
                .getTopLevelClasses("com.android.build.gradle.tasks")
                .filter { it.simpleName == "PackageAndroidArtifact" }
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredMethods.asIterable() }
                .filter { it.getAnnotation(Incremental::class.java) != null }
                .map {
                    val annotation = when {
                        it.getAnnotation(Classpath::class.java) != null ->
                            it.getAnnotation(Classpath::class.java).toString()
                        it.getAnnotation(PathSensitive::class.java) != null ->
                            it.getAnnotation(PathSensitive::class.java).toString()
                        else -> "OTHER"
                    }
                    return@map "${it.name}  $annotation"
                }
        assertThat(incrementalInputs).containsExactly(
            "getAppMetadata  @org.gradle.api.tasks.PathSensitive(value=NAME_ONLY)",
            "getAssets  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getDexFolders  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getFeatureDexFolder  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getFeatureJavaResourceFiles  @org.gradle.api.tasks.Classpath()",
            "getJavaResourceFiles  @org.gradle.api.tasks.Classpath()",
            "getJniFolders  @org.gradle.api.tasks.Classpath()",
            "getManifests  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getMergedArtProfile  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getMergedArtProfileMetadata  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)",
            "getResourceFiles  @org.gradle.api.tasks.PathSensitive(value=RELATIVE)"
        )
    }


    private fun getMatchingGetter(setter: Method) : Method? {
        val name = setter.name.removePrefix("set")
        val nameWithGet = "get$name"
        val nameWithIs = "is$name"
        return setter.declaringClass.declaredMethods.firstOrNull {
            it.name == nameWithGet || it.name == nameWithIs
        }
    }

    private fun findTaskFieldsOfType(ofType: Class<*>): List<Field> {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val taskInterface = TypeToken.of(Task::class.java)
        val fieldType = TypeToken.of(ofType)
        return classPath
            .getTopLevelClassesRecursive("com.android.build")
            .map { classInfo -> classInfo.load() as Class<*> }
            .filter { clazz -> TypeToken.of(clazz).types.contains(taskInterface) }
            .flatMap { it.declaredFields.asIterable() }
            .filter { TypeToken.of(it.type).isSubtypeOf(fieldType) }
    }

    private fun AnnotatedElement.hasGradleInputOrOutputAnnotation(): Boolean {
        // look for all org.gradle.api.tasks annotations, except @CacheableTask, @Internal, and
        // @TaskAction.
        return getAnnotation(Classpath::class.java) != null
                || getAnnotation(CompileClasspath::class.java) != null
                || getAnnotation(Console::class.java) != null
                || getAnnotation(Destroys::class.java) != null
                || getAnnotation(Input::class.java) != null
                || getAnnotation(InputDirectory::class.java) != null
                || getAnnotation(InputFile::class.java) != null
                || getAnnotation(InputFiles::class.java) != null
                || getAnnotation(LocalState::class.java) != null
                || getAnnotation(Nested::class.java) != null
                || getAnnotation(Optional::class.java) != null
                || getAnnotation(OutputDirectories::class.java) != null
                || getAnnotation(OutputDirectory::class.java) != null
                || getAnnotation(OutputFile::class.java) != null
                || getAnnotation(OutputFiles::class.java) != null
                || getAnnotation(PathSensitive::class.java) != null
                || getAnnotation(SkipWhenEmpty::class.java) != null
    }
}
