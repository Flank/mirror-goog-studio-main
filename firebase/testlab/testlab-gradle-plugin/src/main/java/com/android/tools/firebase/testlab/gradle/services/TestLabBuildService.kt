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
import com.android.tools.firebase.testlab.gradle.ManagedDeviceTestRunner.Companion.FtlTestRunResult
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Key
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDevice
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidDeviceList
import com.google.api.services.testing.model.AndroidInstrumentationTest
import com.google.api.services.testing.model.ClientInfo
import com.google.api.services.testing.model.EnvironmentMatrix
import com.google.api.services.testing.model.GoogleCloudStorage
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.TestSpecification
import com.google.api.services.toolresults.ToolResults
import com.google.api.services.toolresults.model.StackTrace
import com.google.firebase.testlab.gradle.ManagedDevice
import com.google.protobuf.util.Timestamps
import com.google.testing.platform.proto.api.core.ErrorProto.Error
import com.google.testing.platform.proto.api.core.IssueProto.Issue
import com.google.testing.platform.proto.api.core.LabelProto.Label
import com.google.testing.platform.proto.api.core.PathProto.Path
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestArtifactProto.ArtifactType
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteMetaData
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
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
        val TAG: String = TestLabBuildService::class.java.simpleName

        const val CHECK_TEST_STATE_WAIT_MS = 10 * 1000L;

        class TestCases : GenericJson() {
            @Key var testCases: List<TestCase>? = null
            @Key var nextPageToken: String? = null
        }

        class TestCase : GenericJson() {
            @Key var testCaseId: String? = null
            @Key var startTime: TimeStamp? = null
            @Key var endTime: TimeStamp? = null
            @Key var stackTraces: List<StackTrace>? = null
            @Key var status: String? = null
            @Key var testCaseReference: TestCaseReference? = null
            @Key var toolOutputs: List<ToolOutputReference>? = null
        }

        class TimeStamp : GenericJson() {
            @Key var seconds: String? = null
            @Key var nanos: Int? = null
        }

        class TestCaseReference : GenericJson() {
            @Key var name: String? = null
            @Key var className: String? = null
            @Key var testSuiteName: String? = null
        }

        class ToolOutputReference: GenericJson() {
            @Key var output: FileReference? = null
        }

        class FileReference: GenericJson() {
            @Key var fileUri: String? = null
        }
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
    ): ArrayList<FtlTestRunResult> {
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
                    testApk = com.google.api.services.testing.model.FileReference().apply {
                        gcsPath = "gs://$defaultBucketName/${testApkStorageObject.name}"
                    }
                    appApk = com.google.api.services.testing.model.FileReference().apply {
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

        val ftlTestRunResults: ArrayList<FtlTestRunResult> = ArrayList()
        resultTestMatrix.testExecutions.forEach { testExecution ->
            if (testExecution.toolResultsStep != null) {
                val executionStep =
                    toolResultsClient.projects().histories().executions().steps().get(
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
                            storageClient.objects()
                                .get(bucketName, objectName)
                                .executeMediaAndDownloadTo(it)
                        }
                    }
                }
            }
            val testSuiteResult = getTestSuiteResult(
                toolResultsClient,
                resultTestMatrix,
                testExecution,
            )

            val testSuitePassed = testSuiteResult.testStatus.isPassedOrSkipped()
            val hasAnyFailedTestCase = testSuiteResult.testResultList.any { testCaseResult ->
                !testCaseResult.testStatus.isPassedOrSkipped()
            }
            val testPassed = testSuitePassed && !hasAnyFailedTestCase && !testSuiteResult.hasPlatformError()
            ftlTestRunResults.add(FtlTestRunResult(testPassed, testSuiteResult))
        }
        return ftlTestRunResults
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

    private fun getTestSuiteResult(
        toolResultsClient: ToolResults,
        testMatrix: TestMatrix,
        testExecution: TestExecution
    ): TestSuiteResult {
        val testSuiteResult = TestSuiteResult.newBuilder()

        val toolResultsStep = testExecution.toolResultsStep
        if (toolResultsStep != null) {
            val projectId = toolResultsStep.projectId
            val historyId = toolResultsStep.historyId
            val executionId = toolResultsStep.executionId
            val stepId = toolResultsStep.stepId
            val step = toolResultsClient.projects().histories().executions().steps().get(
                projectId,
                historyId,
                executionId,
                stepId
            ).execute()

            testSuiteResult.apply {
                testSuiteMetaData = TestSuiteMetaData.newBuilder().apply {
                    testSuiteName = step.name
                    var scheduledTestCount = 0
                    for (testSuiteOverview in step.testExecutionStep.testSuiteOverviews) {
                        scheduledTestCount += testSuiteOverview.totalCount
                    }
                    scheduledTestCaseCount = scheduledTestCount
                }.build()

                testStatus = when (step.outcome.summary) {
                    "success" -> TestStatus.PASSED
                    "failure" -> TestStatus.FAILED
                    "skipped" -> TestStatus.SKIPPED
                    else -> TestStatus.TEST_STATUS_UNSPECIFIED
                }
                addOutputArtifact(
                    Artifact.newBuilder().apply {
                        label = Label.newBuilder().apply {
                            label = "firebase.xmlSource"
                            namespace = "android"
                        }.build()
                        sourcePath = Path.newBuilder().apply {
                            path = step.testExecutionStep.testSuiteOverviews[0].xmlSource.fileUri
                        }.build()
                        type = ArtifactType.TEST_DATA
                    }.build()
                )
            }

            for (log in step.testExecutionStep.toolExecution.toolLogs) {
                testSuiteResult.apply {
                    addOutputArtifact(Artifact.newBuilder().apply {
                        label = Label.newBuilder().apply {
                            label = "firebase.toolLog"
                            namespace = "android"
                        }.build()
                        sourcePath = Path.newBuilder().apply {
                            path = log.fileUri
                        }.build()
                        type = ArtifactType.TEST_DATA
                        mimeType = "text/plain"
                    }.build())
                }
            }
            for (toolOutput in step.testExecutionStep.toolExecution.toolOutputs) {
                val outputArtifact = Artifact.newBuilder().apply {
                    label = Label.newBuilder().apply {
                        label = "firebase.toolOutput"
                        namespace = "android"
                    }.build()
                    sourcePath = Path.newBuilder().apply {
                        path = toolOutput.output.fileUri
                    }.build()
                    type = ArtifactType.TEST_DATA
                }.build()
                if (toolOutput.testCase == null) {
                    testSuiteResult.apply {
                        addOutputArtifact(outputArtifact)
                    }
                }
            }

            val thumbnails =
                toolResultsClient.projects().histories().executions().steps().thumbnails().list(
                    projectId,
                    historyId,
                    executionId,
                    stepId
                ).execute().thumbnails
            if (thumbnails != null) {
                for (thumbnail in thumbnails) {
                    testSuiteResult.apply {
                        addOutputArtifact(
                            Artifact.newBuilder().apply {
                                label = Label.newBuilder().apply {
                                    label = "firebase.thumbnail"
                                    namespace = "android"
                                }.build()
                                sourcePath = Path.newBuilder().apply {
                                    path = thumbnail.sourceImage.output.fileUri
                                }.build()
                                type = ArtifactType.TEST_DATA
                                mimeType = "image/jpeg"
                            })
                    }
                }
            }

            for (testIssue in step.testExecutionStep.testIssues) {
                logger.error(TAG, testIssue.errorMessage)
                testSuiteResult.apply {
                    addIssue(Issue.newBuilder().apply {
                        message = testIssue.errorMessage
                        name = testIssue.get("type").toString()
                        namespace = Label.newBuilder().apply {
                            label = "firebase.issue"
                            namespace = "android"
                        }.build()
                        severity = when (testIssue.get("severity")) {
                            "info" -> Issue.Severity.INFO
                            "suggestion" -> Issue.Severity.SUGGESTION
                            "warning" -> Issue.Severity.WARNING
                            "severe" -> Issue.Severity.SEVERE
                            else -> Issue.Severity.SEVERITY_UNSPECIFIED
                        }
                        code = testIssue.get("type").toString().hashCode()
                    }.build())
                }
            }

            // Need latest version of google-api-client to use
            // toolResultsClient.projects().histories().executions().steps().testCases().list().
            // Manually calling this API until this is available.
            val httpRequestFactory: HttpRequestFactory = GoogleNetHttpTransport.newTrustedTransport().createRequestFactory(httpRequestInitializer)
            val url = "https://toolresults.googleapis.com/toolresults/v1beta3/projects/$projectId/histories/$historyId/executions/$executionId/steps/$stepId/testCases"
            val request = httpRequestFactory.buildGetRequest(GenericUrl(url))
            val parser = JsonObjectParser(Utils.getDefaultJsonFactory())
            request.setParser(parser)
            val response = request.execute()
            response.content.use {
                val testCaseContents = parser.parseAndClose<TestCases>(
                    it, StandardCharsets.UTF_8,
                    TestCases::class.java
                )
                for (case in testCaseContents["testCases"] as List<TestCase>) {
                    testSuiteResult.apply {
                        addTestResult(TestResult.newBuilder().apply {
                            testCase = TestCaseProto.TestCase.newBuilder().apply {
                                var packageName: String = case.testCaseReference!!.className!!
                                val className: String = packageName.split(".").last()
                                testClass = className
                                testPackage = packageName.dropLast(className.length + 1)
                                testMethod = case.testCaseReference!!.name
                                startTime = Timestamps.fromNanos(case.startTime!!.nanos!!.toLong())
                                endTime = Timestamps.fromNanos(case.endTime!!.nanos!!.toLong())
                            }.build()

                            val status = case.status
                            testStatus = when (status) {
                                null -> TestStatus.PASSED
                                "passed" -> TestStatus.PASSED
                                "failed" -> TestStatus.FAILED
                                "error" -> TestStatus.ERROR
                                "skipped" -> TestStatus.SKIPPED
                                else -> TestStatus.TEST_STATUS_UNSPECIFIED
                            }

                            if (status == "failed" || status == "error") {
                                error = Error.newBuilder().apply {
                                    stackTrace = case.stackTraces!![0].exception
                                }.build()
                            }

                            if (case.toolOutputs != null) {
                                for (toolOutput in (case.toolOutputs as List<ToolOutputReference>)) {
                                    addOutputArtifact(Artifact.newBuilder().apply {
                                        Label.newBuilder().apply {
                                            label = "firebase.toolOutput"
                                            namespace = "android"
                                        }.build()
                                        sourcePath = Path.newBuilder().apply {
                                            path = toolOutput.output!!.fileUri
                                        }.build()
                                        type = ArtifactType.TEST_DATA
                                    }.build())
                                }
                            }
                        }.build())
                    }
                }
            }
        }

        if (testMatrix.invalidMatrixDetails?.isNotBlank() == true) {
            testSuiteResult.apply {
                platformErrorBuilder.addErrorsBuilder().apply {
                    summaryBuilder.apply {
                        errorName = testMatrix.invalidMatrixDetails
                        errorCode = testMatrix.invalidMatrixDetails.hashCode()
                        errorMessage = getInvalidMatrixDetailsErrorMessage(testMatrix.invalidMatrixDetails)
                        namespaceBuilder.apply {
                            label = "firebase.invalidMatrixDetails"
                            namespace = "android"
                        }
                    }
                }
            }
        }

        return testSuiteResult.build()
    }

    private fun TestStatus.isPassedOrSkipped(): Boolean {
        return when (this) {
            TestStatus.PASSED,
            TestStatus.IGNORED,
            TestStatus.SKIPPED -> true
            else -> false
        }
    }

    private fun getInvalidMatrixDetailsErrorMessage(invalidMatrixDetailsEnumValue: String): String {
        return when(invalidMatrixDetailsEnumValue) {
            "MALFORMED_APK" -> "The input app APK could not be parsed."
            "MALFORMED_TEST_APK" -> "The input test APK could not be parsed."
            "NO_MANIFEST" -> "The AndroidManifest.xml could not be found."
            "NO_PACKAGE_NAME" -> "The APK manifest does not declare a package name."
            "INVALID_PACKAGE_NAME" -> "The APK application ID (aka package name) is invalid. See also https://developer.android.com/studio/build/application-id"
            "TEST_SAME_AS_APP" -> "The test package and app package are the same."
            "NO_INSTRUMENTATION" -> "The test apk does not declare an instrumentation."
            "NO_SIGNATURE" -> "The input app apk does not have a signature."
            "INSTRUMENTATION_ORCHESTRATOR_INCOMPATIBLE" -> "The test runner class specified by user or in the test APK's manifest file is not compatible with Android Test Orchestrator. Orchestrator is only compatible with AndroidJUnitRunner version 1.1 or higher. Orchestrator can be disabled by using DO_NOT_USE_ORCHESTRATOR OrchestratorOption."
            "NO_TEST_RUNNER_CLASS" -> "The test APK does not contain the test runner class specified by user or in the manifest file. This can be caused by either of the following reasons: - the user provided a runner class name that's incorrect, or - the test runner isn't built into the test APK (might be in the app APK instead)."
            "NO_LAUNCHER_ACTIVITY" -> "A main launcher activity could not be found."
            "FORBIDDEN_PERMISSIONS" -> "The app declares one or more permissions that are not allowed."
            "INVALID_ROBO_DIRECTIVES" -> "There is a conflict in the provided roboDirectives."
            "INVALID_RESOURCE_NAME" -> "There is at least one invalid resource name in the provided robo directives"
            "INVALID_DIRECTIVE_ACTION" -> "Invalid definition of action in the robo directives (e.g. a click or ignore action includes an input text field)"
            "TEST_LOOP_INTENT_FILTER_NOT_FOUND" -> "There is no test loop intent filter, or the one that is given is not formatted correctly."
            "SCENARIO_LABEL_NOT_DECLARED" -> "The request contains a scenario label that was not declared in the manifest."
            "SCENARIO_LABEL_MALFORMED" -> "There was an error when parsing a label's value."
            "SCENARIO_NOT_DECLARED" -> "The request contains a scenario number that was not declared in the manifest."
            "DEVICE_ADMIN_RECEIVER" -> "Device administrator applications are not allowed."
            "MALFORMED_XC_TEST_ZIP" -> "The zipped XCTest was malformed. The zip did not contain a single .xctestrun file and the contents of the DerivedData/Build/Products directory."
            "BUILT_FOR_IOS_SIMULATOR" -> "The zipped XCTest was built for the iOS simulator rather than for a physical device."
            "NO_TESTS_IN_XC_TEST_ZIP" -> "The .xctestrun file did not specify any test targets."
            "USE_DESTINATION_ARTIFACTS" -> "One or more of the test targets defined in the .xctestrun file specifies \"UseDestinationArtifacts\", which is disallowed."
            "TEST_NOT_APP_HOSTED" -> "XC tests which run on physical devices must have \"IsAppHostedTestBundle\" == \"true\" in the xctestrun file."
            "PLIST_CANNOT_BE_PARSED" -> "An Info.plist file in the XCTest zip could not be parsed."
            "MALFORMED_IPA" -> "The input IPA could not be parsed."
            "MISSING_URL_SCHEME" -> "The application doesn't register the game loop URL scheme."
            "MALFORMED_APP_BUNDLE" -> "The iOS application bundle (.app) couldn't be processed."
            "NO_CODE_APK" -> "APK contains no code. See also https://developer.android.com/guide/topics/manifest/application-element.html#code"
            "INVALID_INPUT_APK" -> "Either the provided input APK path was malformed, the APK file does not exist, or the user does not have permission to access the APK file."
            "INVALID_APK_PREVIEW_SDK" -> "APK is built for a preview SDK which is unsupported"
            else -> "The matrix is INVALID, but there are no further details available."
        }
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
