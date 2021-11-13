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

package com.android.build.gradle.internal.core

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.dsl.TestedExtension
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.manifest.ManifestData
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.android.build.gradle.internal.variant.Container
import com.android.build.gradle.internal.variant.ContainerImpl
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.DexingType
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito

class VariantDslInfoTest2 :
    AbstractBuildGivenBuildExpectTest<
            VariantDslInfoTest2.GivenData,
            VariantDslInfoTest2.ResultData>() {

    @Test
    fun `versionCode from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData {  }

            defaultConfig {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode from manifest`() {
        given {
            manifestData {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode defaultConfig overrides manifest`() {
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
        }

        expect {
            versionCode = 13
        }
    }

    @Test
    fun `versionCode from flavor overrides all`() {
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
            productFlavors {
                create("higherPriority") {
                    versionCode = 20
                }
                create("lowerPriority") {
                    versionCode = 14
                }
            }
        }

        expect {
            versionCode = 20
        }
    }

    @Test
    fun `versionName from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            defaultConfig {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName from manifest`() {
        given {
            manifestData {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName defaultConfig overrides manifest`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar"
            }
        }

        expect {
            versionName = "bar"
        }
    }

    @Test
    fun `versionName from flavor overrides all`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    versionName = "bar1"
                }
                create("lowerPriority") {
                    versionName = "bar2"
                }
            }
        }

        expect {
            versionName = "bar1"
        }
    }

    @Test
    fun `versionName from manifest with suffix from defaultConfig`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar"
            }
        }

        expect {
            versionName = "foo-bar"
        }
    }

    @Test
    fun `versionName from manifest with full suffix`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar1"
            }
            productFlavors {
                create("higherPriority") {
                    versionNameSuffix = "-bar3"
                }
                create("lowerPriority") {
                    versionNameSuffix = "-bar2"
                }
            }

            buildType {
                versionNameSuffix = "-bar4"
            }
        }

        expect {
            versionName = "foo-bar1-bar3-bar2-bar4"
        }
    }

    @Test
    fun `instrumentationRunner defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "android.test.InstrumentationTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner defaults with legacy multidex`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                minSdk = 20
                multiDexEnabled = true
            }

            dexingType = DexingType.LEGACY_MULTIDEX
        }

        expect {
            instrumentationRunner = "com.android.test.runner.MultiDexTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to only call VariantDslInfo.instrumentationRunner
        // as it's not normally called for non-test variant type.
        convertToResult {
            instrumentationRunner = it.getInstrumentationRunner(DexingType.NATIVE_MULTIDEX).orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("instrumentationRunner is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `instrumentationRunner from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "foo"
            }
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner from manifest`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner defaultConfig overrides manifest`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar"
            }
        }

        expect {
            instrumentationRunner = "bar"
        }
    }

    @Test
    fun `instrumentationRunner from flavor overrides all`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    testInstrumentationRunner = "bar1"
                }
                create("lowerPriority") {
                    testInstrumentationRunner = "bar2"
                }
            }
        }

        expect {
            instrumentationRunner = "bar1"
        }
    }

    @Test
    fun `handleProfiling defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to call VariantDslInfo.handleProfiling
        // even though this is not a test VariantType
        convertToResult {
            handleProfiling = it.handleProfiling.orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("handleProfiling is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `handleProfiling from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling from manifest`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling defaultConfig overrides manifest`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = false
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling from flavor overrides all`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
            productFlavors {
                create("higherPriority") {
                    testHandleProfiling = false
                }
                create("lowerPriority") {
                    testHandleProfiling = false
                }
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `functionalTest defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to call VariantDslInfo.functionalTest
        // even though this is not a test VariantType
        convertToResult {
            functionalTest = it.functionalTest.orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("functionalTest is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `functionalTest from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest from manifest`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest defaultConfig overrides manifest`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = false
            }
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest from flavor overrides all`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
            productFlavors {
                create("higherPriority") {
                    testFunctionalTest = false
                }
                create("lowerPriority") {
                    testFunctionalTest = false
                }
            }
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `namespace from DSL overrides manifest`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }

            namespace = "com.example.fromDsl"
        }

        expect {
            namespace = "com.example.fromDsl"
        }
    }

    @Test
    fun `testNamespace from DSL overrides namespace and manifest`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            namespace = "com.example.namespace"
            testNamespace = "com.example.testNamespace"
        }

        expect {
            namespace = "com.example.testNamespace"
            namespaceForR = "com.example.testNamespace"
        }
    }

    @Test
    fun `testNamespace derived from namespace`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            namespace = "com.example.namespace"
        }

        expect {
            namespace = "com.example.namespace.test"
            namespaceForR = "com.example.namespace.test"
        }
    }

    @Test
    fun `testNamespace derived from manifest`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            namespace = "com.example.fromManifest.test"
            namespaceForR = "com.example.fromManifest.test"
        }
    }

    @Test
    fun `namespaceForR from single appId`() {
        given {
            // no specific manifest info
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                applicationId = "com.applicationId"
            }
        }

        expect {
            namespace = "com.example.fromManifest.test"
            namespaceForR = "com.applicationId.test"
        }
    }

    @Test
    fun `namespaceForR from namespace with single appId`() {
        given {
            // no specific manifest info
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            namespace = "com.example.namespace"

            defaultConfig {
                applicationId = "com.applicationId"
            }
        }

        expect {
            namespace = "com.example.namespace.test"
            namespaceForR = "com.example.namespace.test"
        }
    }

    @Test
    fun `namespaceForR with several appId`() {
        given {
            // no specific manifest info
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            productFlavors {
                create("flavor1") {
                    applicationId = "com.example.flavor1"
                }
                create("flavor2") {
                    applicationId = "com.example.flavor2"
                }
            }
        }

        expect {
            namespace = "com.example.fromManifest.test"
            namespaceForR = "com.example.fromManifest.test"
        }
    }

    @Test
    fun `namespaceForR with mixed appId`() {
        given {
            // no specific manifest info
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            productFlavors {
                create("flavor1") {
                    applicationId = "com.example.flavor1"
                }
                create("flavor2") {
                }
            }
        }

        expect {
            namespace = "com.example.fromManifest.test"
            namespaceForR = "com.example.fromManifest.test"
        }
    }

    @Test
    fun `namespaceForR with appIdSuffix`() {
        given {
            // no specific manifest info
            manifestData {
                packageName = "com.example.fromManifest"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            productFlavors {
                create("flavor1") {
                    applicationIdSuffix = "flavor1"
                }
                create("flavor2") {
                }
            }
        }

        expect {
            namespace = "com.example.fromManifest.test"
            namespaceForR = "com.example.fromManifest.test"
        }
    }


    // ---------------------------------------------------------------------------------------------

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    private val projectServices = createProjectServices()
    private val services = createVariantPropertiesApiServices(projectServices)
    private val dslServices: DslServices = createDslServices(projectServices)
    private val buildDirectory: DirectoryProperty = Mockito.mock(DirectoryProperty::class.java)

    override fun instantiateGiven() = GivenData(dslServices)
    override fun instantiateResult() = ResultData()

    interface TestedFullExtension: CommonExtension<
            BuildFeatures,
            com.android.build.api.dsl.BuildType,
            com.android.build.api.dsl.DefaultConfig,
            com.android.build.api.dsl.ProductFlavor
            >, TestedExtension

    override fun defaultWhen(given: GivenData): ResultData? {
        val componentIdentity = Mockito.mock(ComponentIdentity::class.java)
        Mockito.`when`(componentIdentity.name).thenReturn("compIdName")

        val extension =  if (given.variantType.isTestComponent) {
            Mockito.mock(TestedFullExtension::class.java).also {
                Mockito.`when`(it.namespace).thenReturn(given.namespace)
                Mockito.`when`(it.testNamespace).thenReturn(given.testNamespace)
            }
        } else {
            Mockito.mock(CommonExtension::class.java).also {
                Mockito.`when`(it.namespace).thenReturn(given.namespace)
            }
        }

        // this does not quite test what VariantManager does because this only checks
        // for the product flavors of that one variant, while VariantManager looks
        // at all of them, but this is good enough to simulate and check the result.
        val inconsistentTestAppId = VariantManager.checkInconsistentTestAppId(
            given.flavors
        )

        val parentVariant =
            if (given.variantType.isNestedComponent) {
                VariantDslInfoImpl(
                    componentIdentity = componentIdentity,
                    variantType = given.testedVariantType,
                    defaultConfig = given.defaultConfig,
                    buildTypeObj = given.buildType,
                    productFlavorList = given.flavors,
                    signingConfigOverride = null,
                    productionVariant = null,
                    dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
                    dslServices = dslServices,
                    services = services,
                    buildDirectory = buildDirectory,
                    nativeBuildSystem = null,
                    publishInfo = null,
                    experimentalProperties = mapOf(),
                    inconsistentTestAppId = false,
                    extension = extension
                )
            } else { null }

        val variantDslInfo = VariantDslInfoImpl(
            componentIdentity = componentIdentity,
            variantType = given.variantType,
            defaultConfig = given.defaultConfig,
            buildTypeObj = given.buildType,
            productFlavorList = given.flavors,
            signingConfigOverride = null,
            productionVariant = parentVariant,
            dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
            dslServices = dslServices,
            services = services,
            buildDirectory = buildDirectory,
            nativeBuildSystem = null,
            experimentalProperties = mapOf(),
            publishInfo = null,
            inconsistentTestAppId = inconsistentTestAppId,
            extension = extension
        )

        return instantiateResult().also {
            if (convertAction != null) {
                convertAction?.invoke(it, variantDslInfo)
            } else {
                it.versionCode = variantDslInfo.versionCode.orNull
                it.versionName = variantDslInfo.versionName.orNull
                // only query these if this is not a test.
                if (given.variantType.isForTesting) {
                    it.instrumentationRunner = variantDslInfo.getInstrumentationRunner(given.dexingType).orNull
                    it.handleProfiling = variantDslInfo.handleProfiling.get()
                    it.functionalTest = variantDslInfo.functionalTest.get()
                }
                try {
                    it.namespace = variantDslInfo.namespace.orNull
                } catch (e: RuntimeException) {
                    // RuntimeException can be thrown when ManifestData.packageName is null
                    it.namespace = null
                }
                try {
                    it.namespaceForR = variantDslInfo.namespaceForR.orNull
                } catch (e: RuntimeException) {
                    // RuntimeException can be thrown when ManifestData.packageName is null
                    it.namespaceForR = null
                }
            }
        }
    }

    override fun initResultDefaults(given: GivenData, result: ResultData) {
        // if the variant type is a test, then make sure that the result is initialized
        // with the right defaults.
        if (given.variantType.isForTesting) {
            result.instrumentationRunner = "android.test.InstrumentationTestRunner" // DEFAULT_TEST_RUNNER
            result.handleProfiling = false // DEFAULT_HANDLE_PROFILING
            result.functionalTest = false //DEFAULT_FUNCTIONAL_TEST
        }
    }

    /** optional conversion action from variantDslInfo to result Builder. */
    private var convertAction: (ResultData.(variantInfo: VariantDslInfo) -> Unit)? = null

    /**
     * registers a custom conversion from variantDslInfo to ResultBuilder.
     * This avoid having to use when {} which requires implementing all that defaultWhen()
     * does.
     */
    private fun convertToResult(action: ResultData.(variantInfo: VariantDslInfo) -> Unit) {
        convertAction = action
    }

    class GivenData(private val dslServices: DslServices) {
        /** the manifest data that represents values coming from the manifest file */
        val manifestData = ManifestData()

        /** Configures the manifest data. */
        fun manifestData(action: ManifestData.() -> Unit) {
            action(manifestData)
        }

        /** Variant type for the test */
        var variantType = VariantTypeImpl.BASE_APK
        /** Variant type for the tested component */
        var testedVariantType = VariantTypeImpl.BASE_APK

        var dexingType = DexingType.NATIVE_MULTIDEX

        var namespace: String? = null
        var testNamespace: String? = null

        /** default Config values */
        val defaultConfig: DefaultConfig = dslServices.newDecoratedInstance(DefaultConfig::class.java, BuilderConstants.MAIN, dslServices)

        /** configures the default config */
        fun defaultConfig(action: DefaultConfig.() -> Unit) {
            action(defaultConfig)
        }

        val buildType: BuildType = dslServices.newDecoratedInstance(BuildType::class.java, "Build-Type", dslServices)

        fun buildType(action: BuildType.() -> Unit) {
            action(buildType)
        }

        private val productFlavors: ContainerImpl<ProductFlavor> = ContainerImpl { name ->
            dslServices.newDecoratedInstance(ProductFlavor::class.java, name, dslServices)
        }
        val flavors: List<ProductFlavor>
            get() = productFlavors.values.toList()

        /**
         * add/configures flavors. The earlier items have higher priority over the later ones.
         */
        fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
            action(productFlavors)
        }
    }

    class ResultData(
        var versionCode: Int? = null,
        var versionName: String? = null,
        var instrumentationRunner: String? = null,
        var handleProfiling: Boolean? = null,
        var functionalTest: Boolean? = null,
        var namespace: String? = null,
        var namespaceForR: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResultData

            if (versionCode != other.versionCode) return false
            if (versionName != other.versionName) return false
            if (instrumentationRunner != other.instrumentationRunner) return false
            if (handleProfiling != other.handleProfiling) return false
            if (functionalTest != other.functionalTest) return false
            if (namespace != other.namespace) return false
            if (namespaceForR != other.namespaceForR) return false

            return true
        }

        override fun hashCode(): Int {
            var result = versionCode ?: 0
            result = 31 * result + (versionName?.hashCode() ?: 0)
            result = 31 * result + (instrumentationRunner?.hashCode() ?: 0)
            result = 31 * result + (handleProfiling?.hashCode() ?: 0)
            result = 31 * result + (functionalTest?.hashCode() ?: 0)
            result = 31 * result + (namespace?.hashCode() ?: 0)
            result = 31 * result + (namespaceForR?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "ResultData(versionCode=$versionCode, versionName=$versionName, instrumentationRunner=$instrumentationRunner, handleProfiling=$handleProfiling, functionalTest=$functionalTest, namespace=$namespace, namespaceForR=$namespaceForR)"
        }
    }

    /**
     * Use the ManifestData provider in the given as a ManifestDataProvider in order to
     * instantiate the ManifestBackedVariantValues object.
     */
    class DirectManifestDataProvider(data: ManifestData, projectServices: ProjectServices) :
        ManifestDataProvider {

        override val manifestData: Provider<ManifestData> =
            projectServices.providerFactory.provider { data }

        override val manifestLocation: String
            get() = "manifest-location"
    }
}
