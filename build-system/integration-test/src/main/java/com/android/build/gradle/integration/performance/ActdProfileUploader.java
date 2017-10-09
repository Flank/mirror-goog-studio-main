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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.testutils.TestUtils;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Preconditions;
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
    /** Singleton instance of this class. Use {@code getInstance()} instead. */
    @NonNull private static final ActdProfileUploader INSTANCE = new ActdProfileUploader();

    /**
     * Use {@code buildbotBuildNumber()} to get the value of this constant (it checks validity
     * before returning it to you).
     */
    @Nullable
    private static final String BUILDBOT_BUILDNUMBER = System.getenv("BUILDBOT_BUILDNUMBER");

    /**
     * act-d constants. Includes API endpoints to hit and the project name to use in API requests.
     * When the system eventually moves, or when we want to start sending our data to a different
     * instance / project, we'll need to change these.
     */
    @Nullable private static final String ACTD_BASE_URL = System.getenv("ACTD_BASE_URL");

    @NonNull private static final String ACTD_ADD_BUILD_URL = "/apis/addBuild";
    @NonNull private static final String ACTD_ADD_SAMPLE_URL = "/apis/addSample";
    @Nullable private static final String ACTD_PROJECT_ID = System.getenv("ACTD_PROJECT_ID");

    /**
     * Informational URL for use in API calls to make the dashboards more useful. Don't use this
     * directly, instead use {@code buildUrl()}.
     *
     * <p>This is set from an environment variable. Remember that the environment variable cannot
     * use format strings (e.g. %s) because buildbot uses those for other things and the values
     * won't come out as you expect them. Because of this, we simply append a slash followed by the
     * {@code buildbotBuildNumber()} to get the final URL.
     */
    @Nullable private static final String BUILDER_URL = System.getenv("ACTD_BUILDER_URL");

    /**
     * Informational URL for use in API calls to make the dashboards more useful. Don't use this
     * directly, instead use {@code commitUrl(String)}.
     *
     * <p>This is set from an environment variable. Remember that the environment variable cannot
     * use format strings (e.g. %s) because buildbot uses those for other things and the values
     * won't come out as you expect them. Because of this, we simply append a slash followed by the
     * commit hash to get the final URL.
     */
    @Nullable private static final String COMMIT_URL = System.getenv("ACTD_COMMIT_URL");

    /**
     * Git-specific constants used to ascertain what the current HEAD commit is, which is used to
     * send to the act-d API to make the dashboards more useful.
     *
     * <p>It's quite hacky to shell out to git in this way to get the information we need, so if you
     * feel motivated enough to find a better solution (something something libgit2?) feel free to
     * send the review to samwho@.
     */
    @NonNull private static final String GIT_BINARY = "/usr/bin/git";

    @NonNull
    private static final String[] GIT_LAST_COMMIT_JSON_CMD = {
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

    /**
     * A command the returns the total number of commits in a given git repository. Used as the
     * build ID in the act-d dashboard. The {@code buildbotBuildNumber()} is not used because it
     * does not guarantee monotonicity (i.e. the ID resets occasionally).
     */
    @NonNull
    private static final String[] GIT_NUM_COMMITS_CMD = {
        "/bin/sh", "-c", GIT_BINARY + " --no-pager log --pretty=oneline | wc -l"
    };

    /**
     * Represents the root directory of the git repository being performance tested. Used in a few
     * git commands to get information about the most recent change and number of commits.
     */
    @NonNull private static final File ROOT = TestUtils.getWorkspaceFile("tools/base");

    /**
     * {@code ActdProfileUploader} has no state of its own, but to satisfy the {@code
     * ProfileUploader} interface we must create an instance of it. Because of that, it is a
     * singleton.
     */
    @NonNull
    public static ActdProfileUploader getInstance() {
        return INSTANCE;
    }

    /** Private because Singleton. See {@code getInstance()}. */
    private ActdProfileUploader() {}

    /**
     * Returns a stable "series ID" for this benchmark result. These need to be unique per benchmark
     * scenario, e.g. AntennaPod no-op with a specific set of flags.
     */
    @VisibleForTesting
    @NonNull
    public static String seriesId(@NonNull GradleBenchmarkResult result) {
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
    @VisibleForTesting
    @NonNull
    public static String description(@NonNull GradleBenchmarkResult result) {
        return flags(result);
    }

    /**
     * Returns a stable string representing the flags enabled for this GradleBenchmarkResult. When I
     * say "stable", I mean that the same set of enabled flags will always return the same string,
     * because the flags are first sorted before being serialised to string. This makes the result
     * useful in, for example, the series ID.
     */
    @VisibleForTesting
    @NonNull
    public static String flags(@NonNull GradleBenchmarkResult result) {
        return result.getFlags()
                .getAllFields()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName()))
                .map(e -> e.getKey().getName() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static int buildbotBuildNumber() {
        if (BUILDBOT_BUILDNUMBER == null || BUILDBOT_BUILDNUMBER.isEmpty()) {
            throw new IllegalStateException(
                    "no BUILDBOT_BUILDNUMBER specified as an environment variable");
        }

        return Integer.valueOf(BUILDBOT_BUILDNUMBER);
    }

    /**
     * Returns a URL that links to the current build on the builder tools-perf_master-dev dashboard.
     *
     * <p>This depends on the user setting the ACTD_BUILDER_URL environment variable. If it isn't
     * set, or is empty, this method will return null. Same goes with BUILDBOT_BUILDNUMBER.
     */
    @Nullable
    private static String buildUrl() {
        if (BUILDER_URL == null || BUILDER_URL.isEmpty()) {
            return null;
        }

        return BUILDER_URL + "/" + buildbotBuildNumber();
    }

    /**
     * Returns a URL to the commit in tools/base represented by the given hash.
     *
     * <p>This depends on the user setting the ACTD_COMMIT_URL environment variable. If it isn't
     * set, or is empty, this method will return null.
     */
    @Nullable
    private static String commitUrl(@Nullable String hash) {
        if (COMMIT_URL == null || COMMIT_URL.isEmpty()) {
            return null;
        }

        return COMMIT_URL + "/" + hash;
    }

    /**
     * Creates a valid {@code Infos} object for the head of the tools/base repository. This relies
     * on shelling out to git, see {@code lastCommitJson()} for details.
     */
    @VisibleForTesting
    @NonNull
    public static Infos infos() throws IOException {
        Infos infos = new Gson().fromJson(lastCommitJson(ROOT), Infos.class);
        infos.url = commitUrl(infos.hash);
        return infos;
    }

    /**
     * Gets information about the most recent commit in a repository (given as a {@code File} as a
     * parameter to this function), in a JSON format suitable for consumption by the {@code Infos}
     * object, which itself is suitable for consumption by the act-d API.
     */
    @VisibleForTesting
    @NonNull
    public static String lastCommitJson(@NonNull File repo) throws IOException {
        return runCmd(repo, GIT_LAST_COMMIT_JSON_CMD);
    }

    /**
     * Gets the total number of commits in the given repo. The intention is to use this as the build
     * ID in the act-d dashboard, as the {@code buildbotBuildNumber()} is not guaranteed to be
     * monotonic (ID resets occur).
     */
    @VisibleForTesting
    public static int numCommits(@NonNull File repo) throws IOException {
        return Integer.valueOf(runCmd(repo, GIT_NUM_COMMITS_CMD));
    }

    @VisibleForTesting
    @NonNull
    public static String hostname() throws IOException {
        String hostname;

        hostname = System.getenv("TESTING_SLAVENAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        hostname = System.getenv("BUILDBOT_SLAVENAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        return runCmd(null, new String[] {"hostname"});
    }

    /**
     * Runs a command from a given working directory and returns its stdout.
     *
     * <p>Note: stdout will be buffered entirely in to memory. Don't run command that produce
     * enormous amounts of output, as they might cause the process to OOM.
     *
     * <p>Note: this function will block until the command has finished.
     *
     * @throws IOException if the command takes longer than 60 seconds, returns a non-0 exit status
     *     or we are interrupted while waiting for it to finish.
     * @throws IllegalArgumentException if the given cmd is empty.
     */
    @NonNull
    private static String runCmd(@Nullable File cwd, @NonNull String[] cmd) throws IOException {
        Preconditions.checkArgument(cmd.length > 0, "cmd specified is empty");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) {
            pb.directory(cwd);
        }

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process proc = pb.start();
        String stdout;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            stdout = br.lines().collect(Collectors.joining("\n"));
        }

        try {
            if (!proc.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("timed out waiting for command to run");
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        if (proc.exitValue() != 0) {
            throw new IOException("command returned non-0 status of " + proc.exitValue());
        }

        return stdout;
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
    private static void addBuild(@NonNull BuildRequest req) throws IOException {
        jsonPost(ACTD_BASE_URL + ACTD_ADD_BUILD_URL, req);
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
    private static void addSample(@NonNull SampleRequest req) throws IOException {
        jsonPost(ACTD_BASE_URL + ACTD_ADD_SAMPLE_URL, req);
    }

    /**
     * POSTs JSON to a given URL. The JSON is created by using {@code Gson} on the {@code req}
     * parameter to this function.
     *
     * <p>Don't use this function directly, instead use {@code addBuild(BuildRequest)} and {@code
     * addSample(SampleRequest)}.
     */
    private static void jsonPost(@NonNull String url, @NonNull Object req) throws IOException {
        Preconditions.checkNotNull(url);
        Preconditions.checkNotNull(req);

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

        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getContent()))) {
            String resContent = br.lines().collect(Collectors.joining("\n"));

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

    /**
     * Runs a variety of checks to see if it's possible for this uploader to run with the
     * information that it has. This mostly checks that the plethora of environment variables
     * required are present.
     */
    private static boolean canRun() {
        if (ACTD_BASE_URL == null || ACTD_BASE_URL.isEmpty()) {
            System.out.println("not running act-d upload because no ACTD_BASE_URL specified");
            return false;
        }

        if (ACTD_PROJECT_ID == null || ACTD_PROJECT_ID.isEmpty()) {
            System.out.println("not running act-d upload because no ACTD_PROJECT_ID specified");
            return false;
        }

        return true;
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) throws IOException {
        if (!canRun()) {
            return;
        }

        long buildId = numCommits(ROOT);
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
            sampleReq.sample.url = buildUrl();

            addSample(sampleReq);
        }
    }

    /**
     * The following are just value classes for use with Gson to represent JSON requests to the
     * act-d API. All properties should be public and there should not be any methods associated
     * with them.
     */
    @VisibleForTesting
    public static final class Infos {
        public String hash;
        public String abbrevHash;
        public String authorName;
        public String authorEmail;
        public String subject;
        public String url;
    }

    @VisibleForTesting
    public static final class Build {
        public long buildId;
        public Infos infos;
    }

    @VisibleForTesting
    public static final class Sample {
        public long buildId;
        public long value;
        public String url;
    }

    @VisibleForTesting
    public static final class Benchmark {
        public String range = "5%";
        public int required = 3;
        public String trend = "smaller";
    }

    @VisibleForTesting
    public static final class Analyse {
        public Benchmark benchmark = new Benchmark();
    }

    @VisibleForTesting
    public static final class BuildRequest {
        public final String projectId = ACTD_PROJECT_ID;
        public Build build = new Build();
    }

    @VisibleForTesting
    public static final class SampleRequest {
        public final String projectId = ACTD_PROJECT_ID;
        public String serieId;
        public String description;
        public Infos infos;
        public Sample sample = new Sample();
        public Analyse analyse = new Analyse();
    }
}
