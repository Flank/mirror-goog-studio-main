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

package com.example.buildsrc.plugin

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.example.buildsrc.instrumentation.*
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A plugin to configure and register [AnnotationAddingClassVisitorFactory] and
 * [InterfaceAddingClassVisitorFactory] to the android gradle plugin ASM api.
 *
 * The plugin uses [InstrumentationPluginExtension] for configuration which should be set from the
 * tests.
 */
class InstrumentationPlugin : Plugin<Project> {
    private val interfaceVisitorFirst = false
    private var instrumentDependencies = false
    private val instrumentedInterfaceInternalName =
        "com/example/instrumentationlib/instrumentation/InstrumentedInterface"
    private val instrumentedAnnotationDescriptor =
        "Lcom/example/instrumentationlib/instrumentation/InstrumentedAnnotation;"

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            instrumentDependencies = true
        }

        val androidExt = project.extensions.getByType(CommonExtension::class.java)
        val instrumentationExtension =
            project.extensions.create("instrumentation", InstrumentationPluginExtension::class.java)

        if (interfaceVisitorFirst) {
            registerInterfaceAddingVisitorFactory(androidExt, instrumentationExtension)
            registerAnnotationAddingVisitorFactory(androidExt, instrumentationExtension)
        } else {
            registerAnnotationAddingVisitorFactory(androidExt, instrumentationExtension)
            registerInterfaceAddingVisitorFactory(androidExt, instrumentationExtension)
        }
    }

    private fun registerAnnotationAddingVisitorFactory(
        androidExt: CommonExtension<*, *, *, *, *, *, *, *>,
        instrumentationExtension: InstrumentationPluginExtension
    ) {
        androidExt.onVariantProperties {
            transformClassesWith(
                AnnotationAddingClassVisitorFactory::class.java,
                if (instrumentDependencies) InstrumentationScope.ALL
                else InstrumentationScope.PROJECT
            ) { params ->
                params.methodNamesToBeAnnotated.setDisallowChanges(
                    instrumentationExtension.annotationAddingConfig.methodNamesToBeAnnotated
                )
                params.interfacesNamesToBeInstrumented.setDisallowChanges(
                    instrumentationExtension.annotationAddingConfig.interfacesNamesToBeInstrumented
                )
                params.annotationClassDescriptor.setDisallowChanges(
                    instrumentedAnnotationDescriptor
                )
            }
        }

        androidExt.onVariants {
            unitTestProperties {
                transformClassesWith(
                    AnnotationAddingClassVisitorFactory::class.java,
                    InstrumentationScope.PROJECT
                ) { params ->
                    params.methodNamesToBeAnnotated.setDisallowChanges(
                        instrumentationExtension.annotationAddingConfig.methodNamesToBeAnnotated
                    )
                    params.interfacesNamesToBeInstrumented.setDisallowChanges(
                        instrumentationExtension.annotationAddingConfig.interfacesNamesToBeInstrumented
                    )
                    params.annotationClassDescriptor.setDisallowChanges(
                        instrumentedAnnotationDescriptor
                    )
                }
            }
        }
    }

    private fun registerInterfaceAddingVisitorFactory(
        androidExt: CommonExtension<*, *, *, *, *, *, *, *>,
        instrumentationExtension: InstrumentationPluginExtension
    ) {
        androidExt.onVariantProperties.withBuildType("debug") {
            transformClassesWith(
                InterfaceAddingClassVisitorFactory::class.java,
                if (instrumentDependencies) InstrumentationScope.ALL
                else InstrumentationScope.PROJECT
            ) { params ->
                params.enabled.setDisallowChanges(
                    instrumentationExtension.interfaceAddingConfig.enabled
                )
                params.classesToInstrument.setDisallowChanges(
                    instrumentationExtension.interfaceAddingConfig.classesToInstrument
                )
                params.interfaceInternalName.setDisallowChanges(instrumentedInterfaceInternalName)
            }
        }

        androidExt.onVariants {
            unitTestProperties {
                transformClassesWith(
                    InterfaceAddingClassVisitorFactory::class.java,
                    InstrumentationScope.PROJECT
                ) { params ->
                    params.enabled.setDisallowChanges(
                        instrumentationExtension.interfaceAddingConfig.enabled
                    )
                    params.classesToInstrument.setDisallowChanges(
                        instrumentationExtension.interfaceAddingConfig.classesToInstrument
                    )
                    params.interfaceInternalName.setDisallowChanges(
                        instrumentedInterfaceInternalName
                    )
                }
            }
        }
    }

    open class InstrumentationPluginExtension {
        val interfaceAddingConfig = InterfaceAddingConfig()
        val annotationAddingConfig = AnnotationAddingConfig()
    }

    class InterfaceAddingConfig(
        var enabled: Boolean = true,
        val classesToInstrument: MutableList<String> = mutableListOf()
    )

    class AnnotationAddingConfig(
        val methodNamesToBeAnnotated: MutableList<String> = mutableListOf(),
        val interfacesNamesToBeInstrumented: MutableList<String> = mutableListOf()
    )
}
