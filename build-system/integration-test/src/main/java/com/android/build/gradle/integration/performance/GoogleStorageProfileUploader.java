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
import com.android.builder.Version;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/** Uploads profiling data to Google Storage from the gradle performance tests. */
public class GoogleStorageProfileUploader implements ProfileUploader {

    public static final ProfileUploader INSTANCE = new GoogleStorageProfileUploader();

    private GoogleStorageProfileUploader() {}

    private static final String STORAGE_SCOPE =
            "https://www.googleapis.com/auth/devstorage.read_write";

    private static final String STORAGE_BUCKET = "android-gradle-logging-benchmark-results";

    @Override
    public void uploadData(@NonNull List<Logging.GradleBenchmarkResult> benchmarkResults)
            throws IOException {

        if (benchmarkResults.isEmpty()) {
            return;
        }
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
                        .build();

        for (Logging.GradleBenchmarkResult benchmarkResult : benchmarkResults) {

            byte[] bytes = benchmarkResult.toByteArray();

            InputStreamContent content =
                    new InputStreamContent(
                            "application/octet-stream", new ByteArrayInputStream(bytes));

            Instant timestamp =
                    Instant.ofEpochMilli(Timestamps.toMillis(benchmarkResult.getTimestamp()));
            HashCode sha1 = Hashing.sha1().hashBytes(bytes);

            String name = DateTimeFormatter.ISO_INSTANT.format(timestamp) + "_" + sha1.toString();

            storage.objects().insert(STORAGE_BUCKET, null, content).setName(name).execute();
        }
    }
}
