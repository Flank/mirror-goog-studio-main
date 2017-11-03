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
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler.BackOffRequired;
import com.google.api.client.http.HttpIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Maps;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Uploader that pushes profile results to act-d. */
public class ActdProfileUploader implements ProfileUploader {
    /**
     * A threshold below which samples will not be uploaded.
     *
     * <p>We seem to have a bunch of timeseries that take 1 or 2 milliseconds, so when they go up or
     * down it counts as a 100% regression/improvement. These are noisy, and we want to ignore them.
     */
    public static final long BENCHMARK_VALUE_THRESHOLD_MILLIS = 50;

    @NonNull private static final String ACTD_PROJECT_ID = "SamProjectTest3";
    @NonNull private static final String ACTD_ADD_BUILD_URL = "/apis/addBuild";
    @NonNull private static final String ACTD_ADD_SERIE_URL = "/apis/addSerie";
    @NonNull private static final String ACTD_ADD_SAMPLE_URL = "/apis/addSample";

    @NonNull private final String actdBaseUrl;
    @NonNull private final String actdProjectId;
    @NonNull private final String buildbotMasterUrl;
    @NonNull private final String buildbotBuilderName;

    private ActdProfileUploader(
            @NonNull String actdBaseUrl,
            @NonNull String actdProjectId,
            @NonNull String buildbotMasterUrl,
            @NonNull String buildbotBuilderName) {
        this.actdBaseUrl = actdBaseUrl;
        this.actdProjectId = actdProjectId;
        this.buildbotMasterUrl = buildbotMasterUrl;
        this.buildbotBuilderName = buildbotBuilderName;
    }

    /**
     * Creates an ActdProfileUploader object. If you're looking to use the act-d uploader, use
     * {@code fromEnvironment()} instead and set the relevant environment variables.
     */
    @VisibleForTesting
    @NonNull
    public static ActdProfileUploader create(
            @NonNull String actdBaseUrl,
            @NonNull String actdProjectId,
            @NonNull String buildbotMasterUrl,
            @NonNull String buildbotBuilderName) {
        return new ActdProfileUploader(
                actdBaseUrl, actdProjectId, buildbotMasterUrl, buildbotBuilderName);
    }

    /**
     * Gets an environment variable or throws an exception if it has not been set.
     *
     * @param name the name of the environment variable to get
     * @throws IllegalStateException if the environment variable has not been set
     * @return the value of the environment variable
     */
    @NonNull
    private static String envRequired(@NonNull String name) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) {
            throw new IllegalStateException("no " + name + " environment variable set");
        }
        return val;
    }

    /**
     * Constructs an ActdProfileUploader from environment variables.
     *
     * <p>Note that environment variables get filtered before being passed to the process that runs
     * this code. Check tools/base/build-system/integration-test/build.gradle for what environment
     * variables get let through.
     *
     * @throws IllegalStateException if any required environment variables have not been set
     */
    @NonNull
    public static ActdProfileUploader fromEnvironment() {
        String actdProjectId = System.getenv("ACTD_PROJECT_ID");
        if (actdProjectId == null || actdProjectId.isEmpty()) {
            actdProjectId = ACTD_PROJECT_ID;
        }

        return new ActdProfileUploader(
                envRequired("ACTD_BASE_URL"),
                actdProjectId,
                envRequired("ACTD_BUILDBOT_MASTER_URL"),
                envRequired("ACTD_BUILDBOT_BUILDER_NAME"));
    }

    /**
     * Returns a stable "series ID" for this benchmark result. These need to be unique per benchmark
     * scenario, e.g. AntennaPod no-op with a specific set of flags.
     */
    @VisibleForTesting
    @NonNull
    public String seriesId(
            @NonNull GradleBenchmarkResult result, @NonNull GradleBuildProfileSpan span) {
        String task = GradleTaskExecutionType.forNumber(span.getTask().getType()).name();
        if (span.getTask().getType() == GradleTaskExecutionType.TRANSFORM_VALUE) {
            String transform =
                    GradleTransformExecutionType.forNumber(span.getTransform().getType()).name();
            task = task + " " + transform;
        }

        return result.getHostname()
                + " "
                + result.getBenchmark()
                + " "
                + result.getBenchmarkMode()
                + " "
                + task
                + " ("
                + flags(result)
                + ")";
    }

    /**
     * Returns a string describing the GradleBenchmarkResult. This is surfaced in the UI, so use it
     * to include vital information about the benchmark (e.g. flags).
     */
    @VisibleForTesting
    @NonNull
    public static String description(
            @NonNull GradleBenchmarkResult result, @NonNull GradleBuildProfileSpan span) {
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

    /**
     * Creates a valid {@code Infos} object for the given buildId. This relies on calling the
     * builbot master API, hence the IOException.
     */
    @VisibleForTesting
    @NonNull
    public Infos infos(long buildId) throws IOException {
        Change change = getChange(buildId);
        Infos infos = new Infos();

        if (change == null) {
            infos.hash = "0000000000000000000000000000000000000000";
            infos.abbrevHash = infos.hash.substring(0, 7);
            infos.authorName = "Manually Triggered";
            infos.authorEmail = "nobody@google.com";
            infos.subject = "Manually triggered build; no commit information";
        } else {
            infos.hash = change.rev;
            infos.abbrevHash = infos.hash.substring(0, 7);
            infos.authorName = change.who;
            infos.authorEmail = change.who;
            infos.subject = change.comments;
        }

        return infos;
    }

    /**
     * POSTs JSON to a given URL. The JSON is created by using {@code Gson} on the {@code req}
     * parameter to this function.
     *
     * <p>Don't use this function directly, instead use {@code addBuild(BuildRequest)} and {@code
     * addSample(SampleRequest)}.
     */
    @NonNull
    private String jsonPost(@NonNull String url, @NonNull Object payload) throws IOException {
        byte[] bytes = new Gson().toJson(payload).getBytes();
        try (ByteArrayInputStream json = new ByteArrayInputStream(bytes)) {
            HttpRequest req =
                    requestFactory()
                            .buildPostRequest(
                                    new GenericUrl(url),
                                    new InputStreamContent("application/json", json));

            return executeAndRead(req);
        }
    }

    @NonNull
    private String buildbotBuildInfoUrl(long buildId) {
        return buildbotMasterUrl + "/json/builders/" + buildbotBuilderName + "/builds/" + buildId;
    }

    @NonNull
    private BuildbotResponse getBuildInfo(long buildId) throws IOException {
        return jsonGet(buildbotBuildInfoUrl(buildId), BuildbotResponse.class);
    }

    /**
     * Gets the change for the current buildId.
     *
     * <p>If the build was triggered manually, this method will return null.
     */
    @Nullable
    private Change getChange(long buildId) throws IOException {
        BuildbotResponse res = getBuildInfo(buildId);

        // When a build is manually triggered, there is no source stamp and thus no changes.
        if (res.sourceStamp.changes.length == 0) {
            return null;
        }

        return res.sourceStamp.changes[0];
    }

    /**
     * Sends a GET request to a given URL, parsing the returning JSON with GSON in to an object of
     * the given type.
     */
    @VisibleForTesting
    @NonNull
    public <T> T jsonGet(@NonNull String url, Class<T> type) throws IOException {
        Gson gson = new GsonBuilder().create();
        HttpRequest req = requestFactory().buildGetRequest(new GenericUrl(url));

        String json = executeAndRead(req);
        return gson.fromJson(json, type);
    }

    /**
     * Takes an HTTP request and executes it, returning the content of the response or null if
     * something went wrong.
     *
     * <p>Before executing the request, this method will attach retry handlers for non-200 responses
     * and transient network problems.
     *
     * @throws IOException if a non-200 status code is received from the request, or some transient
     *     network problem occurs and continues to occur after retrying.
     */
    @NonNull
    private static String executeAndRead(@NonNull HttpRequest request) throws IOException {
        HttpResponse res = attachRetryHandlers(request).execute();
        String content;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getContent()))) {
            content = br.lines().collect(Collectors.joining("\n"));
            if (res.getStatusCode() != 200) {
                throw new IOException(
                        "unsuccessful response: " + res.getStatusCode() + " -> " + content);
            }
        } finally {
            res.disconnect();
        }

        return content;
    }

    @NonNull
    private static HttpRequestFactory requestFactory() throws IOException {
        try {
            return GoogleNetHttpTransport.newTrustedTransport().createRequestFactory();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /**
     * Attaches various retry handlers to a request that do sensible things in the event of
     * unsuccessful requests and network problems. Used by {@code executeAndRead()}, so there's no
     * need to call it elsewhere.
     */
    @NonNull
    private static HttpRequest attachRetryHandlers(@NonNull HttpRequest request) {
        HttpBackOffUnsuccessfulResponseHandler unsuccessfulResponseHandler =
                new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff())
                        .setBackOffRequired(BackOffRequired.ALWAYS);

        HttpIOExceptionHandler ioExceptionHandler =
                new HttpBackOffIOExceptionHandler(new ExponentialBackOff());

        return request.setUnsuccessfulResponseHandler(unsuccessfulResponseHandler)
                .setIOExceptionHandler(ioExceptionHandler)
                .setFollowRedirects(true);
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
    private void addBuild(@NonNull BuildRequest req) throws IOException {
        checkActdResonse(jsonPost(actdBaseUrl + ACTD_ADD_BUILD_URL, req));
    }

    /**
     * Adds a Serie to the act-d dashboard using the act-d API.
     *
     * <p>Serie each belong to a Build, and the Build must exist in the act-d API before you can add
     * a Serie. See {@code addBuild(BuildRequest)} for more information.
     *
     * @throws IOException if anything networky goes wrong, or if the API returns an unsuccessful
     *     response.
     */
    private void addSerie(@NonNull SerieRequest req) throws IOException {
        checkActdResonse(jsonPost(actdBaseUrl + ACTD_ADD_SERIE_URL, req));
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
    private void addSample(@NonNull SampleRequest req) throws IOException {
        checkActdResonse(jsonPost(actdBaseUrl + ACTD_ADD_SAMPLE_URL, req));
    }

    /**
     * act-d doesn't always return a non-200 response for failures, but there are ways of checking
     * for success in the response itself. Make sure you wrap act-d HTTP calls in this method.
     */
    private void checkActdResonse(String response) throws IOException {
        if (!response.contains("success")) {
            throw new IOException("unsuccessful act-d response:" + response);
        }
    }

    private static long getBuildId(@NonNull Collection<GradleBenchmarkResult> results) {
        List<Long> buildIds =
                results.stream()
                        .map(result -> result.getScheduledBuild().getBuildbotBuildNumber())
                        .distinct()
                        .collect(Collectors.toList());

        if (buildIds.size() != 1) {
            throw new IllegalArgumentException(
                    "results list contains more than one distinct build ID");
        }

        return Iterables.getOnlyElement(buildIds);
    }

    /**
     * Because there can be multiple of each task happening per build (e.g. if there are multiple
     * projects), we sum the times for each task and take the overall time spent doing that type of
     * task per build.
     */
    @VisibleForTesting
    @NonNull
    public Collection<SampleRequest> sampleRequests(
            @NonNull Collection<GradleBenchmarkResult> results) throws IOException {
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        long buildId = getBuildId(results);
        Change change = getChange(buildId);
        Map<String, SampleRequest> summedSeriesDurations = Maps.newHashMap();

        for (GradleBenchmarkResult result : results) {
            if (result.getProfile() == null) {
                // Shouldn't happen, but it's worth being defensive.
                continue;
            }

            for (GradleBuildProfileSpan span : result.getProfile().getSpanList()) {
                String seriesId = seriesId(result, span);
                SampleRequest sampleReq =
                        summedSeriesDurations.computeIfAbsent(
                                seriesId,
                                key -> {
                                    SampleRequest req = new SampleRequest();
                                    req.projectId = actdProjectId;
                                    req.serieId = seriesId;
                                    req.sample.buildId = buildId;
                                    req.sample.value = 0;
                                    req.sample.url = change == null ? "" : change.revlink;
                                    return req;
                                });

                sampleReq.sample.value += span.getDurationInMs();
            }
        }

        // filter out low-value, noisy samples before returning a set of samples
        return summedSeriesDurations
                .values()
                .stream()
                .filter(req -> req.sample.value > BENCHMARK_VALUE_THRESHOLD_MILLIS)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    @NonNull
    public BuildRequest buildRequest(long buildId) throws IOException {
        BuildRequest buildReq = new BuildRequest();
        buildReq.projectId = actdProjectId;
        buildReq.build.infos = infos(buildId);
        buildReq.build.buildId = buildId;

        Change change = getChange(buildId);
        buildReq.build.infos.url = change != null ? change.revlink : "";

        return buildReq;
    }

    @VisibleForTesting
    @NonNull
    public Collection<SerieRequest> serieRequests(
            @NonNull Collection<SampleRequest> sampleRequests) {
        Set<String> serieIds =
                sampleRequests
                        .stream()
                        .map(req -> req.serieId)
                        .distinct()
                        .collect(Collectors.toSet());

        List<SerieRequest> serieRequests = Lists.newArrayListWithExpectedSize(serieIds.size());
        for (String serieId : serieIds) {
            SerieRequest serieReq = new SerieRequest();
            serieReq.projectId = actdProjectId;
            serieReq.serieId = serieId;
            serieRequests.add(serieReq);
        }
        return serieRequests;
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) throws IOException {
        long buildId = getBuildId(results);
        BuildRequest br = buildRequest(buildId);
        addBuild(br);

        Collection<SampleRequest> sampleRequests = sampleRequests(results);
        Collection<SerieRequest> serieRequests = serieRequests(sampleRequests);

        System.out.println("found " + serieRequests.size() + " unique series IDs");
        System.out.println("ensuring all series exist");

        // Make sure that all series we're about to upload data for exist in Dana.
        for (SerieRequest serieReq : serieRequests) {
            addSerie(serieReq);
        }

        System.out.println("starting uplaod of " + sampleRequests.size() + " samples");
        for (SampleRequest sampleReq : sampleRequests) {
            addSample(sampleReq);
        }
        System.out.println("successfully uploaded act-d data for build ID " + buildId);
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
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Build {
        public long buildId;
        public Infos infos;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Sample {
        public long buildId;
        public long value;
        public String url;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class Benchmark {
        public String range = "10%";
        public int required = 3;
        public String trend = "smaller";
    }

    @VisibleForTesting
    public static final class Analyse {
        public Benchmark benchmark = new Benchmark();
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class BuildRequest {
        public String projectId;
        public Build build = new Build();
        public boolean override = true;
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class SampleRequest {
        public String projectId;
        public String serieId;
        public Sample sample = new Sample();
    }

    @VisibleForTesting
    @SuppressWarnings("unused") // matches the structure act-d requires, not all params are used
    public static final class SerieRequest {
        public String projectId;
        public String serieId;
        public Analyse analyse = new Analyse();
        public boolean override = true;
    }

    /** The following are objects designed to hold a buildbot build info object. */
    @VisibleForTesting
    public static final class BuildbotResponse {
        public SourceStamp sourceStamp = new SourceStamp();
    }

    @VisibleForTesting
    public static final class SourceStamp {
        public Change[] changes = new Change[] {};
    }

    @VisibleForTesting
    public static final class Change {
        public String comments;
        public String rev;
        public String revlink;
        public String who;
    }
}
