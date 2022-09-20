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

package com.android.tools.firebase.testlab.gradle.services

import com.android.build.api.instrumentation.StaticTestData
import com.android.builder.testing.api.DeviceConfigProvider
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDevice
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidDeviceList
import com.google.api.services.testing.model.AndroidInstrumentationTest
import com.google.api.services.testing.model.ClientInfo
import com.google.api.services.testing.model.EnvironmentMatrix
import com.google.api.services.testing.model.FileReference
import com.google.api.services.testing.model.GoogleCloudStorage
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.TestSpecification
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.toolresults.ToolResults
import com.google.firebase.testlab.gradle.ManagedDevice
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

/**
 * A Gradle Build service that provides APIs to talk to the Firebase Test Lab backend server.
 */
abstract class TestLabBuildService : BuildService<TestLabBuildService.Parameters> {

    companion object {
        const val clientApplicationName: String = "Firebase TestLab Gradle Plugin"
        const val xGoogUserProjectHeaderKey: String = "X-Goog-User-Project"
        val cloudStorageUrlRegex = Regex("""gs://(.*?)/(.*)""")

        const val CHECK_TEST_STATE_WAIT_MS = 10 * 1000L;
    }

    private val logger = Logging.getLogger(this.javaClass)

    /**
     * Parameters of [TestLabBuildService].
     */
    interface Parameters : BuildServiceParameters {
        val quotaProjectName: Property<String>
        val credentialFile: RegularFileProperty
    }

    private val credential = parameters.credentialFile.get().asFile.inputStream().use {
        GoogleCredential.fromStream(it)
    }

    private val httpRequestInitializer: HttpRequestInitializer = HttpRequestInitializer { request ->
        credential.initialize(request)
        request.headers[xGoogUserProjectHeaderKey] = parameters.quotaProjectName.get()
    }

    private val jacksonFactory: JacksonFactory
        get() = JacksonFactory.getDefaultInstance()

    fun runTestsOnDevice(
        device: ManagedDevice,
        testData: StaticTestData,
        resultsOutDir: File,
    ) {
        val projectName = parameters.quotaProjectName.get()
        val requestId = UUID.randomUUID().toString()

        val toolResultsClient = ToolResults.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val defaultBucketName = toolResultsClient.projects().initializeSettings(projectName)
            .execute().defaultBucket

        val storageClient = Storage.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val testApkStorageObject = uploadToCloudStorage(
            testData.testApk, requestId, storageClient, defaultBucketName
        )

        val deviceId = device.device
        val deviceApiLevel = device.apiLevel
        val deviceLocale = Locale.forLanguageTag(device.locale)
        val deviceOrientation = device.orientation

        val configProvider = createConfigProvider(
            deviceId, deviceLocale, deviceApiLevel
        )
        val appApkStorageObject = uploadToCloudStorage(
            testData.testedApkFinder(configProvider).first(),
            requestId,
            storageClient,
            defaultBucketName
        )

        val testingClient = Testing.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jacksonFactory,
            httpRequestInitializer
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val testMatricesClient = testingClient.projects().testMatrices()

        val testMatrix = TestMatrix().apply {
            projectId = projectName
            clientInfo = ClientInfo().apply {
                name = clientApplicationName
            }
            testSpecification = TestSpecification().apply {
                androidInstrumentationTest = AndroidInstrumentationTest().apply {
                    testApk = FileReference().apply {
                        gcsPath = "gs://$defaultBucketName/${testApkStorageObject.name}"
                    }
                    appApk = FileReference().apply {
                        gcsPath = "gs://$defaultBucketName/${appApkStorageObject.name}"
                    }
                    appPackageId = testData.testedApplicationId
                    testPackageId = testData.applicationId
                    testRunnerClass = testData.instrumentationRunner
                }
                environmentMatrix = EnvironmentMatrix().apply {
                    androidDeviceList = AndroidDeviceList().apply {
                        androidDevices = listOf(
                            AndroidDevice().apply {
                                androidModelId = deviceId
                                androidVersionId = deviceApiLevel.toString()
                                locale = deviceLocale.toString()
                                orientation = deviceOrientation.toString().lowercase()
                            }
                        )
                    }
                }
                resultStorage = ResultStorage().apply {
                    googleCloudStorage = GoogleCloudStorage().apply {
                        gcsPath = "gs://$defaultBucketName/$requestId/results"
                    }
                }
            }
        }
        val updatedTestMatrix = testMatricesClient.create(projectName, testMatrix).apply {
            this.requestId = requestId
        }.execute()

        lateinit var resultTestMatrix: TestMatrix
        while (true) {
            val latestTestMatrix = testMatricesClient.get(
                projectName, updatedTestMatrix.testMatrixId).execute()
            val testFinished = when (latestTestMatrix.state) {
                "VALIDATING", "PENDING", "RUNNING" -> false
                else -> true
            }
            logger.info("Test execution: ${latestTestMatrix.state}")
            if (testFinished) {
                resultTestMatrix = latestTestMatrix
                break
            }
            Thread.sleep (CHECK_TEST_STATE_WAIT_MS)
        }

        resultTestMatrix.testExecutions.forEach { testExecution ->
            val executionStep = toolResultsClient.projects().histories().executions().steps().get(
                testExecution.toolResultsStep.projectId,
                testExecution.toolResultsStep.historyId,
                testExecution.toolResultsStep.executionId,
                testExecution.toolResultsStep.stepId
            ).execute()
            executionStep.testExecutionStep.testSuiteOverviews?.forEach { suiteOverview ->
                val matchResult = cloudStorageUrlRegex.find(suiteOverview.xmlSource.fileUri)
                if (matchResult != null) {
                    val (bucketName, objectName) = matchResult.destructured
                    File(resultsOutDir, "TEST-${objectName.replace("/", "_")}").apply {
                        parentFile.mkdirs()
                        createNewFile()
                    }.outputStream().use {
                        storageClient.objects().get(bucketName, objectName).executeMediaAndDownloadTo(it)
                    }
                }
            }
        }
    }

    fun catalog(): AndroidDeviceCatalog {
        val testingClient = Testing.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jacksonFactory,
            httpRequestInitializer,
        ).apply {
            applicationName = clientApplicationName
        }.build()

        val catalog = testingClient.testEnvironmentCatalog().get("ANDROID").apply {
            projectId = parameters.quotaProjectName.get()
        }.execute()

        return catalog.androidDeviceCatalog
    }

    /**
     * Uploads the given file to cloud storage.
     *
     * @param file the file to be uploaded
     * @param runId a uuid that is unique to this test run.
     * @param storageClient the storage connection for the file to be written to.
     * @param bucketName a unique id for the set of files to be associated with this test.
     * @return a handle to the Storage object in the cloud.
     */
    private fun uploadToCloudStorage(
        file: File, runId: String, storageClient: Storage, bucketName: String
    ): StorageObject =
        FileInputStream(file).use { fileInputStream ->
            storageClient.objects().insert(
                bucketName,
                StorageObject(),
                InputStreamContent("application/octet-stream", fileInputStream).apply {
                    length = file.length()
                }
            ).apply {
                name = "${runId}_${file.name}"
            }.execute()
        }

    private fun createConfigProvider(
        deviceId: String, locale: Locale, apiLevel: Int
    ): DeviceConfigProvider {
        val deviceModel = catalog().models.firstOrNull {
            it.id == deviceId
        } ?: error("Could not find device: $deviceId")

        if (!deviceModel.supportedVersionIds.contains(apiLevel.toString())) {
            error("""
                $apiLevel is not supported by $deviceModel. Available Api levels are:
                ${deviceModel.supportedVersionIds}
            """.trimIndent())
        }
        return object : DeviceConfigProvider {
            override fun getConfigFor(abi: String?): String {
                return requireNotNull(abi)
            }

            override fun getDensity(): Int = deviceModel.screenDensity

            override fun getLanguage(): String {
                return locale.language
            }

            override fun getRegion(): String? {
                return locale.country
            }

            override fun getAbis() = deviceModel.supportedAbis

            override fun getApiLevel() = apiLevel
        }
    }

    /**
     * An action to register TestLabBuildService to a project.
     */
    class RegistrationAction(
        private val findCredentialFileFunc: () -> File = ::getGcloudCredentialsFile,
    ) {
        companion object {
            /**
             * Get build service name that works even if build service types come from different
             * class loaders. If the service name is the same, and some type T is defined in two
             * class loaders L1 and L2. E.g. this is true for composite builds and other project
             * setups (see b/154388196).
             *
             * Registration of service may register (T from L1) or (T from L2). This means that
             * querying it with T from other class loader will fail at runtime. This method makes
             * sure both T from L1 and T from L2 will successfully register build services.
             *
             * Copied from
             * com.android.build.gradle.internal.services.BuildServicesKt.getBuildServiceName.
             */
            private fun getBuildServiceName(type: Class<*>): String {
                return type.name + "_" + perClassLoaderConstant
            }

            /**
             *  Used to get unique build service name. Each class loader will initialize its own
             *  version.
             */
            private val perClassLoaderConstant = UUID.randomUUID().toString()

            private const val WELL_KNOWN_CREDENTIALS_FILE = "application_default_credentials.json"
            private const val CLOUDSDK_CONFIG_DIRECTORY = "gcloud"

            private fun getGcloudCredentialsFile(): File {
                val os = System.getProperty("os.name", "").lowercase(Locale.US)
                val envPath = System.getenv("CLOUDSDK_CONFIG") ?: ""
                val cloudConfigPath = if (envPath.isNotBlank()) {
                    File(envPath)
                } else if (os.indexOf("windows") >= 0) {
                    val appDataPath = File(System.getenv("APPDATA"))
                    File(appDataPath, CLOUDSDK_CONFIG_DIRECTORY)
                } else {
                    val configPath = File(System.getProperty("user.home", ""), ".config")
                    File(configPath, CLOUDSDK_CONFIG_DIRECTORY)
                }
                return File(cloudConfigPath, WELL_KNOWN_CREDENTIALS_FILE)
            }

            private fun getQuotaProjectName(credentialFile: File): String {
                val quotaProjectName = credentialFile.inputStream().use {
                    val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
                    val fileContents = parser.parseAndClose<GenericJson>(
                        it, StandardCharsets.UTF_8,
                        GenericJson::class.java
                    )
                    fileContents["quota_project_id"] as? String
                }
                if (quotaProjectName.isNullOrBlank()) {
                    throwCredentialNotFoundError()
                }
                return quotaProjectName
            }

            private fun throwCredentialNotFoundError(): Nothing {
                error("""
                    Unable to find the application-default credentials to send a request to
                    Firebase TestLab. Please initialize your credentials using gcloud CLI.
                    Examples:
                      gcloud config set project ${"$"}YOUR_PROJECT_ID
                      gcloud auth application-default login
                      gcloud auth application-default set-quota-project ${"$"}YOUR_PROJECT_ID
                    Please read https://cloud.google.com/sdk/gcloud for details.
                """.trimIndent())
            }
        }

        /**
         * Register [TestLabBuildService] to a registry if absent.
         */
        fun registerIfAbsent(registry: BuildServiceRegistry): Provider<TestLabBuildService> {
            return registry.registerIfAbsent(
                getBuildServiceName(TestLabBuildService::class.java),
                TestLabBuildService::class.java,
            ) { buildServiceSpec ->
                configure(buildServiceSpec.parameters)
            }
        }

        private fun configure(params: Parameters) {
            val credentialFile = findCredentialFileFunc()
            if (!credentialFile.isFile) {
                throwCredentialNotFoundError()
            }
            val quotaProjectName = getQuotaProjectName(credentialFile)
            params.credentialFile.set(credentialFile)
            params.quotaProjectName.set(quotaProjectName)
        }
    }
}
