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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

/** Regression test for b/232075280. */
class CyclicPomDependencyTest: ModelComparator() {

    private val buildFileTemplate = { libName: String, dependencyName: String ->
        """
            group = "com.foo"
            version = "1.0"

            Configuration customPublishing  = configurations.create("customPublishing")
            customPublishing.setCanBeConsumed(true)
            customPublishing.setCanBeResolved(false)
            customPublishing.attributes.attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM)
            )
            dependencies.add("customPublishing", 'com.foo:$dependencyName:1.0')
            customPublishing.outgoing.artifact(tasks.getByName("jar"))

            abstract class FactoryAccessor {
                @javax.inject.Inject
                abstract SoftwareComponentFactory getFactory();
            }
            FactoryAccessor factoryAccessor = project.objects.newInstance(FactoryAccessor.class)
            AdhocComponentWithVariants component = factoryAccessor.getFactory().adhoc("custom")
            component.addVariantsFromConfiguration(customPublishing) {}
            project.components.add(component)

            tasks.withType(GenerateModuleMetadata) {
                enabled = false
            }

            publishing {
                repositories {
                    maven { url = '../repo' }
                }
                publications {
                    mavenJava(MavenPublication) {
                        artifactId = "$libName"
                        from components.custom
                    }
                }
            }
        """.trimIndent()
    }

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile {
                """
                    repositories {
                        maven {
                            url { '../repo' }
                        }
                    }
                    dependencies {
                        implementation("com.foo:bar1:1.0")
                    }
                """.trimIndent()
            }
        }
        subProject(":bar1") {
            plugins.add(PluginType.JAVA_LIBRARY)
            plugins.add(PluginType.MAVEN_PUBLISH)
            appendToBuildFile {
                buildFileTemplate("bar1", "bar2")
            }
        }
        subProject(":bar2") {
            plugins.add(PluginType.JAVA_LIBRARY)
            plugins.add(PluginType.MAVEN_PUBLISH)
            appendToBuildFile {
                buildFileTemplate("bar2", "bar1")
            }
        }
    }

    @Test
    fun `test models`() {
        project.executor().run(":bar1:publish", ":bar2:publish")
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}
