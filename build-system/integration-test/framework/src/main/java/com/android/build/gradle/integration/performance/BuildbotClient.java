/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.ExponentialBackOff;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class BuildbotClient {
    /**
     * Holds the contents of a JSON response from the buildbot master. In reality, it has a lot more
     * fields than this but we only specify the one that we need and GSON does the right thing.
     */
    private static final class BuildbotResponse {
        public SourceStamp sourceStamp = new SourceStamp();
    }

    private static final class SourceStamp {
        public Change[] changes = new Change[] {};
    }

    /** Holds the content of a change as buildbot's JSON returns it. */
    public static final class Change {
        public String at;
        public String comments;
        public String revision;
        public String revlink;
        public String who;
    }

    /**
     * A date parser able to take the format given by the buildbot API in changes and convert it in
     * to something Java can work with.
     *
     * <p>Example of format given: "Fri 26 Jan 2018 05:34:29".
     */
    private static final DateTimeFormatter DATE_PARSER =
            DateTimeFormatter.ofPattern("E d MMM yyyy HH:mm:ss");

    /**
     * If you're testing on your workstation and can't directly talk to a build master, set up a
     * port forward like so:
     *
     * <pre>
     *   $ ssh -L 9001:localhost:<master_port> valid_user@master.url
     * </pre>
     *
     * And use this BuildbotClient.
     */
    public static final BuildbotClient LOCAL =
            new BuildbotClient("http://localhost:9001", "tools-perf_tools-perf");

    /**
     * Constructs a BuildbotClient from environment variables.
     *
     * <pre>
     *     BUILDBOT_MASTER_URL - the master URL to query
     *     BUILDBOT_BUILDER_NAME - the builder to query
     * </pre>
     *
     * @throws IllegalArgumentException if either of the environment variables are not present.
     */
    public static BuildbotClient fromEnvironment() {
        String masterUrl = System.getenv("BUILDBOT_MASTER_URL");
        if (Strings.isNullOrEmpty(masterUrl)) {
            throw new IllegalArgumentException(
                    "couldn't find a BUILDBOT_MASTER_URL environment variable");
        }

        String builderName = System.getenv("BUILDBOT_BUILDER_NAME");
        if (Strings.isNullOrEmpty(masterUrl)) {
            throw new IllegalArgumentException(
                    "couldn't find a BUILDBOT_BUILDER_NAME environment variable");
        }

        return new BuildbotClient(masterUrl, builderName);
    }

    /**
     * Returns a fake BuildbotClient that doesn't hit buildbot at all, instead returns the changes
     * passed in to this function for all calls to getChanges.
     */
    @VisibleForTesting
    public static BuildbotClient forTesting(List<Change> changes) {
        return new BuildbotClient("", "") {
            @Override
            public List<Change> getChanges(long buildId) {
                return changes;
            }
        };
    }

    /**
     * Parses and returns a ZonedDateTime from a Change's "at" field. It is assumed that these dates
     * are in the America/Los_Angeles timezone.
     */
    public static ZonedDateTime dateFromChange(Change change) {
        return LocalDateTime.from(DATE_PARSER.parse(change.at))
                .atZone(ZoneId.of("America/Los_Angeles"));
    }

    /**
     * Caches the responses we get back from buildbot. They should never change once produced, so
     * this should be a safe thing to do.
     */
    private static final Cache<String, BuildbotResponse> BUILD_CACHE =
            CacheBuilder.newBuilder().initialCapacity(16).maximumSize(16).build();

    private final String masterUrl;
    private final String builderName;

    @VisibleForTesting
    protected BuildbotClient(String masterUrl, String builderName) {
        this.masterUrl = masterUrl;
        this.builderName = builderName;
    }

    /**
     * Gets changes linked to the given buildId.
     *
     * <p>If the build was triggered manually, this method will return null.
     */
    @Nullable
    public List<Change> getChanges(long buildId) throws ExecutionException {
        BuildbotResponse res = getBuildInfo(buildId);

        // When a build is manually triggered, there is no source stamp and thus no changes.
        if (res.sourceStamp.changes.length == 0) {
            return null;
        }

        return Arrays.asList(res.sourceStamp.changes);
    }

    @NonNull
    private BuildbotResponse getBuildInfo(long buildId) throws ExecutionException {
        String url = buildbotBuildInfoUrl(buildId);
        System.out.println(url);
        return BUILD_CACHE.get(url, () -> jsonGet(url, BuildbotResponse.class));
    }

    @NonNull
    private String buildbotBuildInfoUrl(long buildId) {
        return masterUrl + "/json/builders/" + builderName + "/builds/" + buildId;
    }

    /**
     * Sends a GET request to a given URL, parsing the returning JSON with GSON in to an object of
     * the given type.
     */
    @NonNull
    private <T> T jsonGet(@NonNull String url, Class<T> type) throws IOException {
        Gson gson = new GsonBuilder().create();
        HttpRequest req = requestFactory().buildGetRequest(new GenericUrl(url));

        String json = executeAndRead(req);
        return gson.fromJson(json, type);
    }

    @NonNull
    private static HttpRequestFactory requestFactory() throws IOException {
        NetHttpTransport.Builder builder = new NetHttpTransport.Builder();

        try {
            builder.trustCertificates(GoogleUtils.getCertificateTrustStore());
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }

        return builder.build().createRequestFactory();
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

    /**
     * Attaches various retry handlers to a request that do sensible things in the event of
     * unsuccessful requests and network problems. Used by {@code executeAndRead()}, so there's no
     * need to call it elsewhere.
     */
    @NonNull
    private static HttpRequest attachRetryHandlers(@NonNull HttpRequest request) {
        HttpBackOffUnsuccessfulResponseHandler unsuccessfulResponseHandler =
                new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff())
                        .setBackOffRequired(
                                HttpBackOffUnsuccessfulResponseHandler.BackOffRequired
                                        .ON_SERVER_ERROR);

        HttpIOExceptionHandler ioExceptionHandler =
                new HttpBackOffIOExceptionHandler(new ExponentialBackOff());

        return request.setUnsuccessfulResponseHandler(unsuccessfulResponseHandler)
                .setIOExceptionHandler(ioExceptionHandler)
                .setFollowRedirects(true);
    }
}
