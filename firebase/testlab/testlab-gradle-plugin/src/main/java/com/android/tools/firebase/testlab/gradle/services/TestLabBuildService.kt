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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.testing.Testing
import com.google.api.services.testing.model.AndroidDeviceCatalog
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.gradle.api.file.RegularFileProperty
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
    }

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

    fun catalog(): AndroidDeviceCatalog {
        val testingClient = Testing.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JacksonFactory.getDefaultInstance(),
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
