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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.profile.TaskProfilingRecord
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.ide.common.workers.GradlePluginMBeans
import com.google.common.io.Closer
import com.google.common.io.Files
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.time.Duration

abstract class BaseDexingTransform : TransformAction<BaseDexingTransform.Parameters> {
    interface Parameters: TransformParameters {
        @get:Internal
        val projectName: Property<String>
        @get:Input
        val minSdkVersion: Property<Int>
        @get:Input
        val debuggable: Property<Boolean>
        @get:Classpath
        val bootClasspath: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val primaryInput: File

    protected abstract fun computeClasspathFiles(): List<Path>

    protected abstract fun enableDesugaring(): Boolean

    override fun transform(outputs: TransformOutputs) {
        val profileMBean = GradlePluginMBeans.getProfileMBean(parameters.projectName.get())
        val timeStart = TaskProfilingRecord.clock.instant()
        val name = Files.getNameWithoutExtension(primaryInput.name)
        val outputDir = outputs.dir(name)
        Closer.create().use { closer ->

            val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                parameters.minSdkVersion.get(),
                parameters.debuggable.get(),
                ClassFileProviderFactory(parameters.bootClasspath.files.map(File::toPath))
                    .also { closer.register(it) },
                ClassFileProviderFactory(computeClasspathFiles()).also { closer.register(it) },
                enableDesugaring(),
                MessageReceiverImpl(
                    SyncOptions.ErrorFormatMode.MACHINE_PARSABLE,
                    LoggerFactory.getLogger(DexingNoDesugarTransform::class.java)
                )
            )

            ClassFileInputs.fromPath(primaryInput.toPath()).use { classFileInput ->
                classFileInput.entries { true }.use { classesInput ->
                    d8DexBuilder.convert(
                        classesInput,
                        outputDir.toPath(),
                        false
                    )
                }
            }
        }
        profileMBean?.registerSpan(null,
            GradleBuildProfileSpan.ExecutionType.ARTIFACT_TRANSFORM,
            Thread.currentThread().id, timeStart,
            Duration.between(timeStart, TaskProfilingRecord.clock.instant()))
    }
}

abstract class DexingNoDesugarTransform : BaseDexingTransform() {
    override fun computeClasspathFiles() = listOf<Path>()
    override fun enableDesugaring() = false
}

abstract class DexingWithDesugarTransform : BaseDexingTransform() {
    /**
     * Using compile classpath normalization is safe here due to the design of desugar:
     * Method bodies are only moved to the companion class within the same artifact,
     * not between artifacts.
     */
    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val classpath: FileCollection

    override fun computeClasspathFiles() = classpath.files.map(File::toPath)

    override fun enableDesugaring() = true
}

fun getDexingArtifactConfigurations(scopes: Collection<VariantScope>): Set<DexingArtifactConfiguration> {
    return scopes.map { getDexingArtifactConfiguration(it) }.toSet()
}

fun getDexingArtifactConfiguration(scope: VariantScope): DexingArtifactConfiguration {
    val minSdk = scope.minSdkVersion.featureLevel
    val debuggable = scope.variantConfiguration.buildType.isDebuggable
    val enableDesugaring = scope.java8LangSupportType == VariantScope.Java8LangSupport.D8

    return DexingArtifactConfiguration(minSdk, debuggable, enableDesugaring)
}

data class DexingArtifactConfiguration(
    private val minSdk: Int,
    private val isDebuggable: Boolean,
    private val enableDesugaring: Boolean
) {

    fun registerTransform(
        projectName: String,
        dependencyHandler: DependencyHandler,
        bootClasspath: FileCollection
    ) {
        dependencyHandler.registerTransform(getTransformClass()) { spec ->
            spec.parameters { parameters ->
                parameters.projectName.set(projectName)
                parameters.minSdkVersion.set(minSdk)
                parameters.debuggable.set(isDebuggable)
                if (enableDesugaring) {
                    parameters.bootClasspath.from(bootClasspath)
                }
            }
            spec.from.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.PROCESSED_JAR.type)
            spec.to.attribute(ARTIFACT_FORMAT, AndroidArtifacts.ArtifactType.DEX.type)

            getAttributes().forEach { attribute, value ->
                spec.from.attribute(attribute, value)
                spec.to.attribute(attribute, value)
            }
        }
    }

    private fun getTransformClass(): Class<out BaseDexingTransform> {
        return if (enableDesugaring) {
            DexingWithDesugarTransform::class.java
        } else {
            DexingNoDesugarTransform::class.java
        }
    }

    fun getAttributes(): Map<Attribute<String>, String> {
        return mapOf(
            ATTR_MIN_SDK to minSdk.toString(),
            ATTR_IS_DEBUGGABLE to isDebuggable.toString(),
            ATTR_ENABLE_DESUGARING to enableDesugaring.toString()
        )
    }
}

val ATTR_MIN_SDK: Attribute<String> = Attribute.of("dexing-min-sdk", String::class.java)
val ATTR_IS_DEBUGGABLE: Attribute<String> =
    Attribute.of("dexing-is-debuggable", String::class.java)
val ATTR_ENABLE_DESUGARING: Attribute<String> =
    Attribute.of("dexing-enable-desugaring", String::class.java)
