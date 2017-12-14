/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import com.android.annotations.NonNull;
import com.android.builder.model.Version;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.services.storage.Storage;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/** Uploads profiling data to Google Storage from the gradle performance tests. */
public class GoogleStorageProfileUploader implements ProfileUploader {
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(5);

    private static GoogleStorageProfileUploader INSTANCE;

    private static final String STORAGE_SCOPE =
            "https://www.googleapis.com/auth/devstorage.read_write";

    private static final String STORAGE_BUCKET = "android-gradle-logging-benchmark-results";

    public static GoogleStorageProfileUploader getInstance() {
        if (INSTANCE == null) {
            synchronized (GoogleStorageProfileUploader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new GoogleStorageProfileUploader();
                }
            }
        }

        return INSTANCE;
    }

    private GoogleStorageProfileUploader() {}

    @Override
    public void uploadData(@NonNull List<Logging.GradleBenchmarkResult> benchmarkResults)
            throws IOException {
        Preconditions.checkNotNull(benchmarkResults);
        Preconditions.checkArgument(!benchmarkResults.isEmpty(), "got an empty list of results");

        GoogleCredential credential;
        try {
            credential =
                    GoogleCredential.getApplicationDefault()
                            .createScoped(Collections.singleton(STORAGE_SCOPE));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Authentication failed:\n "
                            + "see https://cloud.google.com/storage/docs/xml-api/java-samples"
                            + "#setup-env\n"
                            + "And run $ gcloud beta auth application-default login",
                    e);
        }

        HttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        Storage storage =
                new Storage.Builder(httpTransport, jsonFactory, credential)
                        .setApplicationName(
                                "Android-Gradle-Plugin-Performance-Test-Upload/"
                                        + Version.ANDROID_GRADLE_PLUGIN_VERSION)
                        .setHttpRequestInitializer(
                                httpRequest -> {
                                    // Increase the default timeouts
                                    httpRequest.setConnectTimeout((int) UPLOAD_TIMEOUT.toMillis());
                                    httpRequest.setReadTimeout((int) UPLOAD_TIMEOUT.toMillis());
                                    // As calling setHttpRequestInitializer *replaces* the default
                                    // initializer, which is implemented by the credential, we need
                                    // to manually call the credential initializer.
                                    credential.initialize(httpRequest);
                                })
                        .build();

        for (Logging.GradleBenchmarkResult result : benchmarkResults) {
            InputStreamContent content =
                    new InputStreamContent(
                            "application/octet-stream",
                            new ByteArrayInputStream(result.toByteArray()));
            storage.objects()
                    .insert(STORAGE_BUCKET, null, content)
                    .setName(ProfileUtils.filename(result))
                    .execute();
        }
    }
}
