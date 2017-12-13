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
import com.android.utils.Pair;
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
import com.google.api.client.util.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Uploader that pushes profile results to act-d. */
public class ActdProfileUploader implements ProfileUploader {
    public enum Mode {
        NORMAL,
        BACKFILL
    }

    /**
     * A threshold below which samples will not be uploaded.
     *
     * <p>We seem to have a bunch of timeseries that take 1 or 2 milliseconds, so when they go up or
     * down it counts as a 100% regression/improvement. These are noisy, and we want to ignore them.
     */
    public static final long BENCHMARK_VALUE_THRESHOLD_MILLIS = 50;

    /**
     * Caches the responses we get back from buildbot. They should never change once produced, so
     * this should be a safe thing to do.
     */
    private static final Cache<String, BuildbotResponse> BUILD_CACHE =
            CacheBuilder.newBuilder().initialCapacity(16).maximumSize(16).build();

    @NonNull private static final String ACTD_PROJECT_ID = "SamProjectTest3";
    @NonNull private static final String ACTD_ADD_BUILD_URL = "/apis/addBuild";
    @NonNull private static final String ACTD_ADD_SERIE_URL = "/apis/addSerie";
    @NonNull private static final String ACTD_ADD_SAMPLE_URL = "/apis/addSample";

    /**
     * This main method is for doing data backfill.
     *
     * <p>There are a bunch of things you need to keep in mind if you want to run this main method,
     * its usage is extremely tricky.
     *
     * <p>1) You'll need to acquire the GradleBenchmarkResult data in a format fit for consumption.
     * The format in question is a directory filled with files, one proto per file.
     *
     * <p>2) You'll need to give the process quite a lot of RAM. I recommend -Xms64g -Xmx64g.
     *
     * <p>3) If you want to backfill all of the data, you'll need to split it into chunks. I
     * recommend one month's worth of data at a time.
     */
    public static void main(String... args) throws IOException {
        ActdProfileUploader uploader = ActdProfileUploader.fromEnvironment();

        if (args.length == 0) {
            System.err.println("you must supply directories to read protos from as arguments");
            System.exit(1);
        }

        for (String arg : args) {
            Path protoDir = Paths.get(arg);

            if (Files.notExists(protoDir)) {
                System.err.println("directory " + protoDir + " does not exist");
                System.exit(1);
            }

            uploader.uploadDataFromDirectory(protoDir);
        }
    }

    @NonNull private final String actdBaseUrl;
    @NonNull private final String actdProjectId;
    @NonNull private final String buildbotMasterUrl;
    @NonNull private final String buildbotBuilderName;
    private final Mode mode;

    private ActdProfileUploader(
            @NonNull String actdBaseUrl,
            @NonNull String actdProjectId,
            @NonNull String buildbotMasterUrl,
            @NonNull String buildbotBuilderName,
            @NonNull Mode mode) {
        this.actdBaseUrl = actdBaseUrl;
        this.actdProjectId = actdProjectId;
        this.buildbotMasterUrl = buildbotMasterUrl;
        this.buildbotBuilderName = buildbotBuilderName;
        this.mode = mode;
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
            @NonNull String buildbotBuilderName,
            @NonNull Mode mode) {
        return new ActdProfileUploader(
                actdBaseUrl, actdProjectId, buildbotMasterUrl, buildbotBuilderName, mode);
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
                envRequired("ACTD_BUILDBOT_BUILDER_NAME"),
                Mode.NORMAL);
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
     *
     * <p>There is a tremendous amount of subtlety here. The default set of flags, when passed
     * through this function, will return an empty string. This is an implementation detail of how
     * protos treat enums, but works well for us by not invalidating all series when a new flag is
     * introduced. If we instead output all flag values including defaults, we would invalidate all
     * series at the point the flag was introduced because literally all series IDs would change. We
     * probably don't want that, but there are some arguments either way.
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
    public Infos infos(long buildId) throws ExecutionException {
        Infos infos = new Infos();

        if (mode == Mode.BACKFILL) {
            infos.hash = "0000000000000000000000000000000000000000";
            infos.abbrevHash = infos.hash.substring(0, 7);
            infos.authorName = "No commit info";
            infos.authorEmail = "nobody@google.com";
            infos.subject = "Sorry, no commit info available";
            return infos;
        }

        Change change = getChange(buildId);

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
    private BuildbotResponse getBuildInfo(long buildId) throws ExecutionException {
        String url = buildbotBuildInfoUrl(buildId);
        return BUILD_CACHE.get(url, () -> jsonGet(url, BuildbotResponse.class));
    }

    @VisibleForTesting
    public static void clearCache() {
        BUILD_CACHE.invalidateAll();
    }

    /**
     * Gets the change for the current buildId.
     *
     * <p>If the build was triggered manually, this method will return null.
     */
    @Nullable
    private Change getChange(long buildId) throws ExecutionException {
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

    @VisibleForTesting
    @NonNull
    public Collection<SampleRequest> sampleRequests(
            @NonNull Collection<GradleBenchmarkResult> results) {
        return sampleRequests(results.stream());
    }

    @VisibleForTesting
    @NonNull
    public Collection<SampleRequest> sampleRequests(
            @NonNull Stream<GradleBenchmarkResult> results) {
        return results.parallel()
                .filter(Objects::nonNull)
                .filter(r -> r.getScheduledBuild() != null)
                .filter(r -> r.getProfile() != null)
                .flatMap(
                        result ->
                                result.getProfile()
                                        .getSpanList()
                                        .stream()
                                        .map(
                                                span -> {
                                                    Sample sample = new Sample();
                                                    sample.buildId =
                                                            result.getScheduledBuild()
                                                                    .getBuildbotBuildNumber();
                                                    sample.value = span.getDurationInMs();

                                                    Change change;
                                                    try {
                                                        change = getChange(sample.buildId);
                                                    } catch (ExecutionException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    sample.url =
                                                            change == null ? "" : change.revlink;

                                                    return Pair.of(seriesId(result, span), sample);
                                                }))
                .collect(Collectors.groupingBy(Pair::getFirst)) // grouping by seriesId
                .entrySet()
                .stream()
                .parallel()
                .map(
                        entry -> {
                            Collection<Sample> samples =
                                    entry.getValue()
                                            .stream()
                                            .parallel()
                                            .map(Pair::getSecond)
                                            .collect(Collectors.toList());
                            samples = mergeSameBuildId(samples);
                            samples =
                                    samples.stream()
                                            .parallel()
                                            .filter(
                                                    sample ->
                                                            sample.value
                                                                    > BENCHMARK_VALUE_THRESHOLD_MILLIS)
                                            .collect(Collectors.toList());

                            SampleRequest req = new SampleRequest();
                            req.projectId = actdProjectId;
                            req.serieId = entry.getKey();
                            req.samples = samples;

                            // If backfillMode is on, we want to skip analysis and override what's already
                            // there.
                            req.override = mode == Mode.BACKFILL;
                            req.skipAnalysis = mode == Mode.BACKFILL;

                            return req;
                        })
                .filter(req -> !req.samples.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Because we can have multiple samples for a given series and build ID (maybe we have multiple
     * projects running the same task multiple times), and we don't want these samples to overwrite
     * each other, we instead sum them.
     *
     * <p>This function assumes that you're passing in a collection of samples that all share a
     * series ID.
     */
    @NonNull
    Collection<Sample> mergeSameBuildId(@NonNull Collection<Sample> samples) {
        Map<Long, Sample> byBuildId = new LinkedHashMap<>();

        for (Sample sample : samples) {
            byBuildId.merge(
                    sample.buildId,
                    sample,
                    (a, b) -> {
                        /*
                         * But Sam, these are longs! How likely is it that they'll ever overflow?
                         *
                         * Good question, Timmy. In reality? Probably never. But the way we test
                         * this code is by throwing lots of random data at it, and in these tests
                         * it's possible to find overflows. When you do, it's not immediately
                         * obvious that the problem is an overflow problem. So these cycles we're
                         * spending to do an exact add are to save time debugging when tests go
                         * wrong.
                         */
                        a.value = Math.addExact(a.value, b.value);
                        Preconditions.checkArgument(a.value >= 0, "negative duration found");

                        return a;
                    });
        }

        return byBuildId.values();
    }

    @VisibleForTesting
    @NonNull
    public BuildRequest buildRequest(long buildId) throws ExecutionException {
        BuildRequest buildReq = new BuildRequest();
        buildReq.projectId = actdProjectId;
        buildReq.build.infos = infos(buildId);
        buildReq.build.buildId = buildId;

        if (mode == Mode.BACKFILL) {
            buildReq.build.infos.url = "";
        } else {
            Change change = getChange(buildId);
            buildReq.build.infos.url = change == null ? "" : change.revlink;
        }

        return buildReq;
    }

    @VisibleForTesting
    @NonNull
    public Collection<SerieRequest> serieRequests(
            @NonNull Collection<SampleRequest> sampleRequests) {
        Preconditions.checkNotNull(sampleRequests);
        Preconditions.checkArgument(
                !sampleRequests.isEmpty(), "got an empty collection of sample requests");

        return sampleRequests
                .stream()
                .parallel()
                .map(req -> req.serieId)
                .distinct()
                .map(
                        serieId -> {
                            SerieRequest serieReq = new SerieRequest();
                            serieReq.projectId = actdProjectId;
                            serieReq.serieId = serieId;
                            return serieReq;
                        })
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    @NonNull
    public Collection<BuildRequest> buildRequests(
            @NonNull Collection<SampleRequest> sampleRequests) {
        Preconditions.checkNotNull(sampleRequests);
        Preconditions.checkArgument(
                !sampleRequests.isEmpty(), "got an empty collection of sample requests");

        return sampleRequests
                .stream()
                .parallel()
                .flatMap(req -> req.samples.stream())
                .map(sample -> sample.buildId)
                .distinct()
                .map(
                        buildId -> {
                            try {
                                return buildRequest(buildId);
                            } catch (ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .collect(Collectors.toList());
    }

    @NonNull
    private Stream<GradleBenchmarkResult> resultsFromDir(@NonNull Path dir) throws IOException {
        return Files.walk(dir)
                .parallel()
                .filter(Files::isRegularFile)
                .map(
                        path -> {
                            try {
                                return Files.readAllBytes(path);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .map(
                        bytes -> {
                            try {
                                return GradleBenchmarkResult.parseFrom(bytes);
                            } catch (InvalidProtocolBufferException e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Override
    public void uploadData(@NonNull List<GradleBenchmarkResult> results) {
        Preconditions.checkNotNull(results);
        Preconditions.checkArgument(!results.isEmpty(), "got an empty list of results");

        uploadFromSamples(sampleRequests(results.stream()));
    }

    private void uploadDataFromDirectory(@NonNull Path dir) throws IOException {
        uploadFromSamples(sampleRequests(resultsFromDir(dir)));
    }

    private void uploadFromSamples(@NonNull Collection<SampleRequest> sampleRequests) {
        Preconditions.checkNotNull(sampleRequests);
        Preconditions.checkArgument(
                !sampleRequests.isEmpty(), "got an empty collection of sample requests");

        Collection<SerieRequest> serieRequests = serieRequests(sampleRequests);
        Collection<BuildRequest> buildRequests = buildRequests(sampleRequests);

        buildRequests
                .stream()
                .parallel()
                .forEach(
                        buildRequest -> {
                            try {
                                addBuild(buildRequest);
                            } catch (IOException e) {
                                String req =
                                        new GsonBuilder()
                                                .setPrettyPrinting()
                                                .create()
                                                .toJson(buildRequest);
                                throw new UncheckedIOException("failed request to Dana: " + req, e);
                            }
                        });

        serieRequests
                .stream()
                .parallel()
                .forEach(
                        serieRequest -> {
                            try {
                                addSerie(serieRequest);
                            } catch (IOException e) {
                                String req =
                                        new GsonBuilder()
                                                .setPrettyPrinting()
                                                .create()
                                                .toJson(serieRequest);
                                throw new UncheckedIOException("failed request to Dana: " + req, e);
                            }
                        });

        sampleRequests
                .stream()
                .parallel()
                .forEach(
                        sampleRequest -> {
                            try {
                                addSample(sampleRequest);
                            } catch (IOException e) {
                                String req =
                                        new GsonBuilder()
                                                .setPrettyPrinting()
                                                .create()
                                                .toJson(sampleRequest);
                                throw new UncheckedIOException("failed request to Dana: " + req, e);
                            }
                        });
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
        public Collection<Sample> samples = new LinkedList<>();
        public boolean override = false;
        public boolean skipAnalysis = false;
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
