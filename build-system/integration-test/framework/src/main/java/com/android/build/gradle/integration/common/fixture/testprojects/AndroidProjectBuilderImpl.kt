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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.CompileOptions
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.base.Charsets
import org.gradle.api.JavaVersion

internal class AndroidProjectBuilderImpl(
    internal val subProject: SubProjectBuilderImpl,
    override var packageName: String
): AndroidProjectBuilder {

    override var applicationId: String? = null
    override var buildToolsRevision: String? = null
    override var namespace: String? = null
    override var compileSdk: Int? = null
    override var minSdk: Int? = null
    override var minSdkPreview: String? = null
    override var targetProjectPath: String? = null
    override var renderscriptTargetApi: Int? = null
    override var renderscriptSupportModeEnabled: Boolean? = null

    override val dynamicFeatures: MutableSet<String> = mutableSetOf()

    private val buildFeatures = BuildFeaturesBuilderImpl()
    private val testFixtures = TestFixturesBuilderImpl()
    private val main: Config? = ConfigImpl(this, "main")
    private val debug: Config? = ConfigImpl(this, "debug")
    private val release: Config? = ConfigImpl(this, "release")
    private val buildTypes = BuildTypeContainerBuilderImpl()

    private val flavors = ProductFlavorContainerBuilderImpl()

    private var aarMetadata: AarMetadataBuilderImpl? = null
    private var androidResources: AndroidResourcesImpl? = null
    private var compileOptions: CompileOptionsImpl? = null
    private var prefabContainer = PrefabContainerBuilderImpl()
    private var sourceSetsContainer = SourceSetsContainerBuilderImpl()

    private val useLibraries = mutableListOf<String>()

    override fun defaultCompileSdk() {
        compileSdk = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION.toInt()
    }

    override fun aarMetadata(action: AarMetadataBuilder.() -> Unit) {
        action(aarMetadata ?: AarMetadataBuilderImpl().also {aarMetadata = it})
    }

    override fun buildFeatures(action: BuildFeaturesBuilder.() -> Unit) {
        action(buildFeatures)
    }

    override fun testFixtures(action: TestFixturesBuilder.() -> Unit) {
        action(testFixtures)
    }

    override fun buildTypes(action: ContainerBuilder<BuildTypeBuilder>.() -> Unit) {
        action(buildTypes)
    }

    override fun productFlavors(action: ContainerBuilder<ProductFlavorBuilder>.() -> Unit) {
        action(flavors)
    }

    override fun prefab(action: ContainerBuilder<PrefabBuilder>.() -> Unit) {
        action(prefabContainer)
    }

    override fun sourceSets(action: ContainerBuilder<SourceSetsBuilder>.() -> Unit) {
        action(sourceSetsContainer)
    }

    override fun addFile(relativePath: String, content: String) {
        subProject.addFile(relativePath, content)
    }

    override fun androidResources(action: AndroidResources.() -> Unit) {
        val res = androidResources ?: AndroidResourcesImpl().also { androidResources = it }

        action(res)
    }

    override fun compileOptions(action: CompileOptions.() -> Unit) {
        val options = compileOptions ?: CompileOptionsImpl().also { compileOptions = it}

        action(options)
    }

    override fun useLibrary(name: String) {
        useLibraries.add(name)
    }

    internal fun prepareForWriting() {
        // check if the manifest is there
        if (subProject.fileAtOrNull("src/main/AndroidManifest.xml") == null) {
            subProject.addFile("src/main/AndroidManifest.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                      package="$packageName">
                    <application />
                </manifest>
            """.trimIndent()
            )
        }
    }

    fun writeBuildFile(sb: StringBuilder, appliedPlugins: Collection<PluginType>) {

        sb.append("android {\n")

        namespace?.let {
            sb.append("    namespace = \"$it\"\n")
        }
        if (!appliedPlugins.contains(PluginType.FUSED_LIBRARY)) {
            compileSdk?.let {
                sb.append("  compileSdk = $it\n")
            }
        }
        buildToolsRevision?.let {
            sb.append("  buildToolsVersion = \"$it\"\n")
        }
        // Fused libraries currently support limited dsl options, so only options common to all
        // plugin android blocks should be added above.
        if (appliedPlugins.contains(PluginType.FUSED_LIBRARY) ||
                appliedPlugins.contains(PluginType.PRIVACY_SANDBOX_SDK)) {
            sb.append("  minSdk = $minSdk\n")
            sb.append("}\n")
            return
        }

        sb.append("  defaultConfig {\n")
        if (minSdk != null) {
            sb.append("  minSdk = $minSdk\n")
        } else if (minSdkPreview != null) {
            sb.append("  minSdkPreview = $minSdkPreview\n")
        }

        applicationId?.let {
            sb.append("    applicationId = \"$it\"\n")
        }

        aarMetadata?.let { aarMetadata ->
            sb.append("  aarMetadata{\n")
            aarMetadata.minCompileSdk?.let {
                sb.append("    minCompileSdk $it\n")
            }
            aarMetadata.minAgpVersion?.let {
                sb.append("    minAgpVersion '$it'\n")
            }
            aarMetadata.minCompileSdkExtension?.let {
                sb.append("    minCompileSdkExtension $it\n")
            }
            sb.append("  }\n") // aarMetadata
        }
        renderscriptSupportModeEnabled?.let {
            sb.append("    renderscriptSupportModeEnabled=$it\n")
        }
        renderscriptTargetApi?.let {
            sb.append("    renderscriptTargetApi=$it\n")
        }
        if (prefabContainer.items.isNotEmpty()) {
            sb.append("    prefab {\n")
            for (item in prefabContainer.items) {
                sb.append("        ${item.key} {\n")
                sb.append("            headers = \"${item.value.headers}\"\n")
                sb.append("        }\n")
            }
            sb.append("    }\n")
        }
        sb.append("  }\n") // DEFAULT-CONFIG

        if (buildFeatures.hasNonDefaultValue()) {
            sb.append("  buildFeatures {\n")
            buildFeatures.aidl?.let { sb.append("    aidl = $it\n") }
            buildFeatures.buildConfig?.let { sb.append("    buildConfig = $it\n") }
            buildFeatures.prefab?.let { sb.append("    prefab = $it\n") }
            buildFeatures.prefabPublishing?.let { sb.append("    prefabPublishing = $it\n") }
            buildFeatures.renderScript?.let { sb.append("    renderScript = $it\n") }
            buildFeatures.resValues?.let { sb.append("    resValues = $it\n") }
            buildFeatures.shaders?.let { sb.append("    shaders = $it\n") }
            buildFeatures.androidResources?.let { sb.append("    androidResources = $it\n") }
            buildFeatures.mlModelBinding?.let { sb.append("    mlModelBinding = $it\n") }
            sb.append("  }\n") // BUILD-FEATURES
        }

        if (testFixtures.enable != null) {
            sb.append("  testFixtures {\n")
            testFixtures.enable?.let { sb.append("    enable = $it\n") }
            sb.append("  }\n")
        }

        androidResources?.let { res ->
            sb.append("  androidResources {\n")
            if (res.namespaced) {
                sb.append("    namespaced = true\n")
            }
            sb.append("  }\n") // ANDROID-RESOURCES
        }

        compileOptions?.let { options ->
            sb.append("  compileOptions {\n")
            if (options.isCoreLibraryDesugaringEnabled) {
                sb.append("    coreLibraryDesugaringEnabled = true")
            }
            sb.append("  }\n") // COMPILE-OPTIONS
        }

        if (buildTypes.items.isNotEmpty()) {
            sb.append("  buildTypes {\n")
            for (item in buildTypes.items.values) {
                sb.append("    ${item.name} {\n")
                item.isDefault?.let {
                    sb.append("      isDefault = $it\n")
                }
                item.testCoverageEnabled?.let {
                    sb.append("      testCoverageEnabled = $it\n")
                }
                item.ndk?.let { ndk ->
                    if (ndk.abiFilters.isNotEmpty()) {
                        sb.append("ndk {\n")
                        sb.append(ndk.abiFilters.joinToString(", "))
                        sb.appendLine()
                        sb.append("}\n")
                    }
                }
                item.resValues.forEach {
                    sb.append("      resValue(\"${it.first}\", \"${it.second}\", \"${it.third}\")\n")
                }
                sb.append("    }\n")
            }
            sb.append("  }\n") // BUILD-TYPES
        }

        if (flavors.items.isNotEmpty()) {
            // fix me, we need proper ordering
            val dimensions = flavors.items.values.map { "\"${it.dimension}\"" }.toSet()

            sb.append("  flavorDimensions ${dimensions.joinToString(separator = ",")}\n")
            sb.append("  productFlavors {\n")
            for (item in flavors.items.values) {
                sb.append("    ${item.name} {\n")
                sb.append("      dimension = \"${item.dimension}\"\n")

                item.isDefault?.let {
                    sb.append("      isDefault = $it\n")
                }
                sb.append("    }\n")
            }
            sb.append("  }\n") // FLAVORS
        }

        targetProjectPath?.let {
            sb.append("    targetProjectPath = \"$it\"\n")
        }

        if (dynamicFeatures.isNotEmpty()) {
            if (!appliedPlugins.contains(PluginType.ANDROID_APP)) {
                throw RuntimeException("Dynamic Features declared in project with plugins: ${appliedPlugins.joinToString { it.id }}")
            }

            sb.append("    dynamicFeatures += [${dynamicFeatures.joinToString(
                separator = ",",
                prefix = "\"",
                postfix = "\"",
            )}]\n")
        }

        if (sourceSetsContainer.items.isNotEmpty()) {
            sb.append("    sourceSets {\n")
            sourceSetsContainer.items.forEach { (sourceSetName, sourceSet) ->
                sb.append("        $sourceSetName {\n")
                sourceSet.manifestSrcFile?.let { sb.append("            manifest.srcFile = '$it'") }
                val toGroovyStrArray: (list: List<String>) -> String = { list ->
                    list.joinToString(prefix = "[", postfix = "]", separator = ",") { "'$it'" }
                }
                if (sourceSet.javaSrcDirs.isNotEmpty()) {
                    sb.append("            java.srcDirs = ${toGroovyStrArray(sourceSet.javaSrcDirs)}\n")
                }
                if (sourceSet.resSrcDirs.isNotEmpty()) {
                    sb.append("            res.srcDirs = ${toGroovyStrArray(sourceSet.resSrcDirs)}\n")
                }
                if (sourceSet.resourcesSrcDirs.isNotEmpty()) {
                    sb.append("            resources.srcDirs = ${toGroovyStrArray(sourceSet.resourcesSrcDirs)}\n")
                }
                sb.append("        }\n") // sourceSetName
            }
            sb.append("    }\n") // sourceSets
        }

        for (library in useLibraries) {
            sb.append("    useLibrary(\"$library\")\n")
        }

        sb.append("}\n") // ANDROID
    }
}

internal class ConfigImpl(
    private val android: AndroidProjectBuilderImpl,
    private val name: String
): Config {
    override var manifest: String
        get() = android.subProject.fileAt("src/$name/AndroidManifest.xml").content
        set(value) {
            android.subProject.addFile("src/$name/AndroidManifest.xml", value)
        }
    override var dependencies: MutableList<String> = mutableListOf()
}
internal class AarMetadataBuilderImpl : AarMetadataBuilder {
    override var minAgpVersion: String? = null
    override var minCompileSdk: Int? = null
    override var minCompileSdkExtension: Int? = null
}

internal class BuildFeaturesBuilderImpl: BuildFeaturesBuilder {
    override var aidl: Boolean? = null
    override var buildConfig: Boolean? = null
    override var prefab: Boolean? = null
    override var prefabPublishing: Boolean? = null
    override var renderScript: Boolean? = null
    override var resValues: Boolean? = null
    override var shaders: Boolean? = null
    override var androidResources: Boolean? = null
    override var mlModelBinding: Boolean? = null

    fun hasNonDefaultValue(): Boolean {
        return aidl != null
                || buildConfig != null
                || prefab != null
                || renderScript != null
                || resValues != null
                || shaders != null
                || androidResources != null
                || mlModelBinding != null
    }
}

internal class TestFixturesBuilderImpl: TestFixturesBuilder {
    override var enable: Boolean? = null
}

internal class BuildTypeContainerBuilderImpl: ContainerBuilder<BuildTypeBuilder> {
    internal val items = mutableMapOf<String, BuildTypeBuilder>()
    override fun named(name: String, action: BuildTypeBuilder.() -> Unit) {
        val newItem = items.computeIfAbsent(name) { BuildTypeBuilderImpl(name) }
        action(newItem)
    }
}

internal class ProductFlavorContainerBuilderImpl: ContainerBuilder<ProductFlavorBuilder> {
    internal val items = mutableMapOf<String, ProductFlavorBuilder>()
    override fun named(name: String, action: ProductFlavorBuilder.() -> Unit) {
        val newItem = items.computeIfAbsent(name) { ProductFlavorBuilderImpl(name) }
        action(newItem)
    }
}

internal class BuildTypeBuilderImpl(override val name: String): BuildTypeBuilder {
    override var isDefault: Boolean? = null
    override var testCoverageEnabled: Boolean? = null
    override var ndk: NdkBuilder? = null
    override val resValues: MutableList<Triple<String, String, String>> = mutableListOf()

    override fun ndk(action: NdkBuilder.() -> Unit) {
        val ndkBuilderImpl = ndk ?: NdkBuilderImpl().also { ndk = it}
        action(ndkBuilderImpl)
    }

    override fun resValue(type: String, name: String, value: String) {
        resValues.add(Triple(type, name, value))
    }
}

internal class PrefabContainerBuilderImpl: ContainerBuilder<PrefabBuilder> {
    internal val items = mutableMapOf<String, PrefabBuilder>()
    override fun named(name: String, action: PrefabBuilder.() -> Unit) {
        val newItem = items.computeIfAbsent(name) { PrefabBuilderImpl(name) }
        action(newItem)
    }
}
internal class SourceSetsContainerBuilderImpl : ContainerBuilder<SourceSetsBuilder> {
    internal val items = mutableMapOf<String, SourceSetsBuilder>()

    override fun named(name: String, action: SourceSetsBuilder.() -> Unit) {
        val newItem = items.computeIfAbsent(name) {SourceSetsBuilderImpl(name)}
        action(newItem)
    }

}
internal class ProductFlavorBuilderImpl(override val name: String): ProductFlavorBuilder {
    override var isDefault: Boolean? = null
    override var dimension: String? = null
}

internal class PrefabBuilderImpl(override val name: String): PrefabBuilder {

    override var headers: String? = null
}

internal class NdkBuilderImpl: NdkBuilder {

    override var abiFilters: List<String> = emptyList()
}

internal class AndroidResourcesImpl : AndroidResources {

    override var ignoreAssetsPattern: String?
        get() =     throw RuntimeException("Not yet implemented")
        set(value) {    throw RuntimeException("Not yet implemented") }
    override val ignoreAssetsPatterns: MutableCollection<String> = mutableListOf()
    override val noCompress: MutableCollection<String> = mutableListOf()

    override fun noCompress(noCompress: String) {
        throw RuntimeException("Not yet implemented")
    }

    override fun noCompress(vararg noCompress: String) {
        throw RuntimeException("Not yet implemented")
    }

    override var failOnMissingConfigEntry: Boolean = false

    override val additionalParameters: MutableList<String> = mutableListOf()

    override fun additionalParameters(params: String) {
        throw RuntimeException("Not yet implemented")
    }

    override fun additionalParameters(vararg params: String) {
        throw RuntimeException("Not yet implemented")
    }

    override var namespaced: Boolean = false
}

internal class CompileOptionsImpl: CompileOptions {

    override var isCoreLibraryDesugaringEnabled: Boolean = false

    override var encoding: String = Charsets.UTF_8.name()

    override var sourceCompatibility: JavaVersion = JavaVersion.VERSION_1_8

    override var targetCompatibility: JavaVersion = JavaVersion.VERSION_1_8

    override fun sourceCompatibility(sourceCompatibility: Any) {
        throw RuntimeException("Not yet implemented")
    }

    override fun targetCompatibility(targetCompatibility: Any) {
        throw RuntimeException("Not yet implemented")
    }
}

internal class SourceSetsBuilderImpl(override val name: String) : SourceSetsBuilder {

    override var manifestSrcFile: String? = null
    override var javaSrcDirs: List<String> = emptyList()
    override var resSrcDirs: List<String> = emptyList()
    override var resourcesSrcDirs: List<String> = emptyList()
}
