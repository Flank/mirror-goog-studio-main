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

package com.android.build.api.component.analytics

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.Component
import com.android.build.api.variant.Instrumentation
import com.android.build.api.variant.JavaCompilation
import com.android.build.api.variant.Sources
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AsmClassesTransformRegistration
import com.google.wireless.android.sdk.stats.AsmFramesComputationModeUpdate
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.gradle.utils.`is`
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledComponentTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: Component

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledComponent by lazy {
        object: AnalyticsEnabledComponent(delegate, stats, FakeObjectFactory.factory) {}
    }

    abstract class MockedVisitor : AsmClassVisitorFactory<InstrumentationParameters.None>

    @Test
    fun transformClasspathWith() {
        Mockito.`when`(delegate.instrumentation)
            .thenReturn(Mockito.mock(Instrumentation::class.java))
        val block = { _ : InstrumentationParameters  -> }
        proxy.instrumentation.transformClassesWith(
            MockedVisitor::class.java,
            InstrumentationScope.PROJECT,
            block
        )

        proxy.instrumentation.transformClassesWith(
            MockedVisitor::class.java,
            InstrumentationScope.ALL,
            block
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_VALUE
            }
        ).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_TRANSFORM_CLASSES_WITH_VALUE
            }
        ).isEqualTo(2)

        Truth.assertThat(stats.asmClassesTransformsCount).isEqualTo(2)
        Truth.assertThat(stats.asmClassesTransformsList.first().classVisitorFactoryClassName)
            .isEqualTo("com.android.build.api.component.analytics.AnalyticsEnabledComponentTest\$MockedVisitor")
        Truth.assertThat(stats.asmClassesTransformsList.last().classVisitorFactoryClassName)
            .isEqualTo("com.android.build.api.component.analytics.AnalyticsEnabledComponentTest\$MockedVisitor")

        Truth.assertThat(stats.asmClassesTransformsList.first().scope)
            .isEqualTo(AsmClassesTransformRegistration.Scope.PROJECT)
        Truth.assertThat(stats.asmClassesTransformsList.last().scope)
            .isEqualTo(AsmClassesTransformRegistration.Scope.ALL)

        val instrumentationDelegate = (proxy.instrumentation as AnalyticsEnabledInstrumentation)
            .delegate
        Mockito.verify(instrumentationDelegate, times(1))
            .transformClassesWith(MockedVisitor::class.java, InstrumentationScope.PROJECT, block)
        Mockito.verify(instrumentationDelegate, times(1))
            .transformClassesWith(MockedVisitor::class.java, InstrumentationScope.ALL, block)
    }

    @Test
    fun setAsmFramesComputationNode() {
        Mockito.`when`(delegate.instrumentation)
            .thenReturn(Mockito.mock(Instrumentation::class.java))
        proxy.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        proxy.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
        proxy.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
        )
        proxy.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(8)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_VALUE
            }
        ).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_SET_ASM_FRAMES_COMPUTATUION_MODE_VALUE
            }
        ).isEqualTo(4)

        Truth.assertThat(stats.framesComputationModeUpdatesCount).isEqualTo(4)
        Truth.assertThat(stats.framesComputationModeUpdatesList[0].mode).isEqualTo(
            AsmFramesComputationModeUpdate.Mode.COPY_FRAMES
        )
        Truth.assertThat(stats.framesComputationModeUpdatesList[1].mode).isEqualTo(
            AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
        Truth.assertThat(stats.framesComputationModeUpdatesList[2].mode).isEqualTo(
            AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
        )
        Truth.assertThat(stats.framesComputationModeUpdatesList[3].mode).isEqualTo(
            AsmFramesComputationModeUpdate.Mode.COMPUTE_FRAMES_FOR_ALL_CLASSES
        )

        val instrumentationDelegate = (proxy.instrumentation as AnalyticsEnabledInstrumentation)
            .delegate

        Mockito.verify(instrumentationDelegate, times(1))
            .setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        Mockito.verify(instrumentationDelegate, times(1))
            .setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )
        Mockito.verify(instrumentationDelegate, times(1))
            .setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
            )
        Mockito.verify(instrumentationDelegate, times(1))
            .setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES)
    }

    @Test
    fun instrumentationExcludes() {
        Mockito.`when`(delegate.instrumentation)
            .thenReturn(Mockito.mock(Instrumentation::class.java))
        proxy.instrumentation.excludes
        proxy.instrumentation.excludes

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_VALUE
            }
        ).isEqualTo(2)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.count {
                it.type == VariantPropertiesMethodType.INSTRUMENTATION_EXCLUDES_VALUE
            }
        ).isEqualTo(2)
    }

    @Test
    fun getBuildType() {
        Mockito.`when`(delegate.buildType).thenReturn("BuildTypeFoo")
        Truth.assertThat(proxy.buildType).isEqualTo("BuildTypeFoo")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUILD_TYPE_VALUE)
        Mockito.verify(delegate, times(1))
            .buildType
    }

    @Test
    fun getProductFlavors() {
        Mockito.`when`(delegate.productFlavors).thenReturn(listOf("foo" to "bar"))
        Truth.assertThat(proxy.productFlavors).isEqualTo(listOf("foo" to "bar"))

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.PRODUCT_FLAVORS_VALUE)
        Mockito.verify(delegate, times(1))
            .productFlavors
    }

    @Test
    fun getFlavorName() {
        Mockito.`when`(delegate.flavorName).thenReturn("flavorName")
        Truth.assertThat(proxy.flavorName).isEqualTo("flavorName")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.FLAVOR_NAME_VALUE)
        Mockito.verify(delegate, times(1))
            .flavorName
    }

    @Test
    fun getJavaCompilation() {
        val javaCompilation = Mockito.mock(JavaCompilation::class.java)
        Mockito.`when`(delegate.javaCompilation).thenReturn(javaCompilation)

        val javaCompilationProxy = proxy.javaCompilation
        Truth.assertThat(javaCompilationProxy.javaClass).`is`(AnalyticsEnabledJavaCompilation::class.java)
        Truth.assertThat((javaCompilationProxy as AnalyticsEnabledJavaCompilation).delegate)
            .isEqualTo(javaCompilation)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.JAVA_COMPILATION_OPTIONS_VALUE)
        Mockito.verify(delegate, times(1))
            .javaCompilation
    }

    @Test
    fun getSources() {
        val sources = Mockito.mock(Sources::class.java)
        Mockito.`when`(delegate.sources).thenReturn(sources)

        val sourcesProxy = proxy.sources
        Truth.assertThat(sources.javaClass).`is`(AnalyticsEnabledSources::class.java)
        Truth.assertThat((sourcesProxy as AnalyticsEnabledSources).delegate)
            .isEqualTo(sources)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.COMPONENT_SOURCES_ACCESS_VALUE)
        Mockito.verify(delegate, times(1))
            .sources
    }

    @Test
    fun getCompileClasspath() {
        val compileClasspath = Mockito.mock(FileCollection::class.java)
        Mockito.`when`(delegate.compileClasspath).thenReturn(compileClasspath)
        Truth.assertThat(proxy.compileClasspath).isEqualTo(compileClasspath)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.COMPILE_CLASSPATH_VALUE)
        Mockito.verify(delegate, times(1)).compileClasspath
    }

    @Test
    fun getCompileConfiguration() {
        val compileConfiguration = Mockito.mock(Configuration::class.java)
        Mockito.`when`(delegate.compileConfiguration).thenReturn(compileConfiguration)
        Truth.assertThat(proxy.compileConfiguration).isEqualTo(compileConfiguration)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.COMPILE_CONFIGURATION_VALUE)
        Mockito.verify(delegate, times(1)).compileConfiguration
    }

    @Test
    fun getRuntimeConfiguration() {
        val runtimeConfiguration = Mockito.mock(Configuration::class.java)
        Mockito.`when`(delegate.runtimeConfiguration).thenReturn(runtimeConfiguration)
        Truth.assertThat(proxy.runtimeConfiguration).isEqualTo(runtimeConfiguration)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.RUNTIME_CONFIGURATION_VALUE)
        Mockito.verify(delegate, times(1)).runtimeConfiguration
    }

    @Test
    fun getAnnotationProcessorConfiguration() {
        val annotationProcessorConfiguration = Mockito.mock(Configuration::class.java)
        Mockito.`when`(delegate.annotationProcessorConfiguration)
            .thenReturn(annotationProcessorConfiguration)
        Truth.assertThat(proxy.annotationProcessorConfiguration)
            .isEqualTo(annotationProcessorConfiguration)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ANNOTATION_PROCESSOR_CONFIGURATION_VALUE)
        Mockito.verify(delegate, times(1)).annotationProcessorConfiguration
    }
}
