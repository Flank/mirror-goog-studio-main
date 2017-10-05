/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.testutils.TestUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.ExponentialBackOff;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Uploader that pushes profile results to act-d. */
public final class ActdProfileUploader implements ProfileUploader {
    public static final ActdProfileUploader INSTANCE = new ActdProfileUploader();

    /**
     * act-d constants. Includes API endpoints to hit and the project name to use in API requests.
     * When the system eventually moves, or when we want to start sending our data to a different
     * instance / project, we'll need to change these.
     */
    private static final String ACTD_BASE_URL = System.getenv("ACTD_BASE_URL");

    private static final String ACTD_ADD_BUILD_URL = ACTD_BASE_URL + "/apis/addBuild";
    private static final String ACTD_ADD_SAMPLE_URL = ACTD_BASE_URL + "/apis/addSample";
    private static final String ACTD_PROJECT_ID = System.getenv("ACTD_PROJECT_ID");

    /**
     * Informational URL for use in API calls to make the dashboards more useful. Don't use this
     * directly, instead use {@code buildUrl(String)}.
     *
     * <p>This is set from an environment variable. When setting it, remember that it expects the
     * string to contain a %s format string somewhere. This will be substituted with the current
     * buildId.
     */
    private static final String BUILDER_URL = System.getenv("ACTD_BUILDER_URL");

    /**
     * Informational URL for use in API calls to make the dashboards more useful. Don't use this
     * directly, instead use {@code commitUrl(String)}.
     *
     * <p>This is set from an environment variable. When setting it, remember that it expects the
     * string to contain a %s format string somewhere. This will be substituted with the current
     * buildId.
     */
    private static final String COMMIT_URL = System.getenv("ACTD_COMMIT_URL");

    /**
     * Git-specific constants used to ascertain what the current HEAD commit is, which is used to
     * send to the act-d API to make the dashboards more useful.
     *
     * <p>It's quite hacky to shell out to git in this way to get the information we need, so if you
     * feel motivated enough to find a better solution (something something libgit2?) feel free to
     * send the review to samwho@.
     */
    private static final String GIT_BINARY = "/usr/bin/git";

    private static final String[] GIT_CMD = {
        GIT_BINARY,
        "--no-pager",
        "log",
        "-n1",
        "--pretty=format:"
                + "{%n"
                + "  \"hash\": \"%H\",%n"
                + "  \"abbrevHash\": \"%h\",%n"
                + "  \"authorName\": \"%aN\",%n"
                + "  \"authorEmail\": \"%aE\",%n"
                + "  \"subject\": \"%s\"%n"
                + "}%n"
    };

    /** Private because Singleton. See {@code INSTANCE}. */
    private ActdProfileUploader() {}

    /**
     * Returns a stable "series ID" for this benchmark result. These need to be unique per benchmark
     * scenario, e.g. AntennaPod no-op with a specific set of flags.
     */
    private static String seriesId(GradleBenchmarkResult result) {
        return result.getBenchmark()
                + " "
                + result.getBenchmarkMode()
                + " ("
                + Hashing.sha256().hashString(flags(result), Charset.defaultCharset())
                + ")";
    }

    /**
     * Returns a string describing the GradleBenchmarkResult. This is surfaced in the UI, so use it
     * to include vital information about the benchmark (e.g. flags).
     */
    private static String description(GradleBenchmarkResult result) {
        return flags(result);
    }

    /**
     * Returns a stable string representing the flags enabled for this GradleBenchmarkResult. When I
     * say "stable", I mean that the same set of enabled flags will always return the same string,
     * because the flags are first sorted before being serialised to string. This makes the result
     * useful in, for example, the series ID.
     */
    private static String flags(GradleBenchmarkResult result) {
        return result.getFlags()
                .getAllFields()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName()))
                .map(e -> e.getKey().getName() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns a URL that links to a given build ID on the builder tools-perf_master-dev dashboard.
     *
     * <p>This depends on the user setting the ACTD_BUILDER_URL environment variable. If it isn't
     * set, or is empty, this method will return null.
     */
    private static String buildUrl(String buildId) {
        if (BUILDER_URL == null || BUILDER_URL.isEmpty()) {
            return null;
        }

        return String.format(BUILDER_URL, buildId);
    }

    private static String buildUrl(long buildId) {
        return buildUrl(String.valueOf(buildId));
    }

    /**
     * Returns a URL to the commit in tools/base represented by the given hash.
     *
     * <p>This depends on the user setting the ACTD_COMMIT_URL environment variable. If it isn't
     * set, or is empty, this method will return null.
     */
    private static String commitUrl(String hash) {
        if (COMMIT_URL == null || COMMIT_URL.isEmpty()) {
            return null;
        }

        return String.format(COMMIT_URL, hash);
    }

    /**
     * Creates a valid {@code Infos} object for the head of the tools/base repository. This relies
     * on shelling out to git, see {@code lastCommitJson()} for details.
     */
    private static Infos infos() throws IOException {
        Infos infos =
                new Gson()
                        .fromJson(
                                lastCommitJson(TestUtils.getWorkspaceFile("tools/base")),
                                Infos.class);
        infos.url = commitUrl(infos.hash);
        return infos;
    }

    /**
     * Gets information about the most recent commit in a repository (given as a {@code File} as a
     * parameter to this function), in a JSON format suitable for consumption by the {@code Infos}
     * object, which itself is suitable for consumption by the act-d API.
     */
    private static String lastCommitJson(File repo) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(GIT_CMD);
        pb.directory(repo);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();
        BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String json = out.lines().collect(Collectors.joining("\n"));

        try {
            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("timed out waiting for git comment to run");
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        if (proc.exitValue() != 0) {
            throw new IOException("git command returned non-0 status of " + proc.exitValue());
        }

        return json;
    }

    /**
     * Gets the current build ID for the set of results we're working with. There should only be one
     * distinct build ID in the results we've been asked to upload, so this method throws an {@code
     * IllegalArgumentException} if it finds less than or more than one.
     *
     * <p>At the time of writing this (2017-10-05), the build ID is passed in to the process when it
     * is run on the buildbots via an environment variable called BUILDBOT_BUILDNUMBER. If you're
     * running locally, you'll probably want to specify some unique build number.
     */
    private static long getBuildId(List<GradleBenchmarkResult> results) {
        List<Long> buildIds =
                results.stream()
                        .map(GradleBenchmarkResult::getScheduledBuild)
                        .map(GradleBenchmarkResult.ScheduledBuild::getBuildbotBuildNumber)
                        .distinct()
                        .collect(Collectors.toList());

        Preconditions.checkArgument(
                buildIds.size() == 1,
                "incorrect number of distinct builds in data to upload. Expected 1, got "
                        + buildIds.size());

        return Iterables.getOnlyElement(buildIds);
    }

    /**
     * Adds a Build to the act-d dashboard using the act-d API.
     *
     * <p>This Build should have a unique buildId and it is required that a Build exists before any
     * Samples can be added to the dashboard, because Samples have a relationship with Builds.
     *
     * @throws IOException if anything networky goes wrong, or if the API returns an unsuccessful
     *     response.
     */
    private static void addBuild(BuildRequest req) throws IOException {
        jsonPost(ACTD_ADD_BUILD_URL, req);
    }

    /**
     * Adds a Sample to the act-d dashboard using the act-d API.
     *
     * <p>Samples each belong to a Build, and the Build must exist in the act-d API before you can
     * add a Sample. See {@code addBuild(BuildRequest)} for more information.
     *
     * @throws IOException if anything networky goes wrong, or if the API returns an unsuccessful
     *     response.
     */
    private static void addSample(SampleRequest req) throws IOException {
        jsonPost(ACTD_ADD_SAMPLE_URL, req);
    }

    /**
     * POSTs JSON to a given URL. The JSON is created by using {@code Gson} on the {@code req}
     * parameter to this function.
     *
     * <p>Don't use this function directly, instead use {@code addBuild(BuildRequest)} and {@code
     * addSample(SampleRequest)}.
     */
    private static void jsonPost(String url, Object req) throws IOException {
        HttpTransport transport;
        try {
            transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }

        String jsonStr = new Gson().toJson(req);
        ByteArrayInputStream json = new ByteArrayInputStream(jsonStr.getBytes());
        InputStreamContent content = new InputStreamContent("application/json", json);
        GenericUrl gurl = new GenericUrl(url);
        HttpResponse res =
                transport
                        .createRequestFactory()
                        .buildPostRequest(gurl, content)
                        .setNumberOfRetries(3)
                        .setUnsuccessfulResponseHandler(
                                new HttpBackOffUnsuccessfulResponseHandler(
                                        new ExponentialBackOff()))
                        .setFollowRedirects(true)
                        .execute();

        try {
            String resContent =
                    new BufferedReader(new InputStreamReader(res.getContent()))
                            .lines()
                            .collect(Collectors.joining("\n"));

            // The check for the string "successful" is necessitated by the API not always returning
            // a non-200 status code in the event of bad requests.
            if (res.getStatusCode() != 200 || !resContent.contains("successful")) {
                throw new IOException(
                        "unsuccessful response: " + res.getStatusCode() + " -> " + resContent);
            }
        } finally {
            res.disconnect();
        }
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) throws IOException {
        if (ACTD_BASE_URL == null || ACTD_BASE_URL.isEmpty()) {
            System.out.println("not running act-d upload because no ACTD_BASE_URL specified");
            return;
        }
        if (ACTD_PROJECT_ID == null || ACTD_PROJECT_ID.isEmpty()) {
            System.out.println("not running act-d upload because no ACTD_PROJECT_ID specified");
            return;
        }

        long buildId = getBuildId(results);
        Infos infos = infos();

        BuildRequest buildReq = new BuildRequest();
        buildReq.build.buildId = buildId;
        buildReq.build.infos = infos;

        addBuild(buildReq);

        for (GradleBenchmarkResult result : results) {
            SampleRequest sampleReq = new SampleRequest();
            sampleReq.infos = infos;
            sampleReq.serieId = seriesId(result);
            sampleReq.description = description(result);
            sampleReq.sample.buildId = buildId;
            sampleReq.sample.value = result.getProfile().getBuildTime();
            sampleReq.sample.url = buildUrl(buildId);

            addSample(sampleReq);
        }
    }

    /**
     * The following are just value classes for use with Gson to represent JSON requests to the
     * act-d API. All properties should be public and there should not be any methods associated
     * with them.
     */
    static final class Infos {
        String hash;
        String abbrevHash;
        String authorName;
        String authorEmail;
        String subject;
        String url;
    }

    static final class Build {
        long buildId;
        Infos infos;
    }

    static final class Sample {
        long buildId;
        long value;
        String url;
    }

    static final class Benchmark {
        String range = "5%";
        int required = 3;
        String trend = "smaller";
    }

    static final class Analyse {
        Benchmark benchmark = new Benchmark();
    }

    static final class BuildRequest {
        final String projectId = ACTD_PROJECT_ID;
        Build build = new Build();
    }

    static final class SampleRequest {
        final String projectId = ACTD_PROJECT_ID;
        String serieId;
        String description;
        Infos infos;
        Sample sample = new Sample();
        Analyse analyse = new Analyse();
    }
}
