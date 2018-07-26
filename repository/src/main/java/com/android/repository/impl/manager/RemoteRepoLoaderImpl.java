/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.*;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.util.InstallerUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Utility class that loads {@link Repository}s from {@link RepositorySource}s.
 */
public class RemoteRepoLoaderImpl implements RemoteRepoLoader {
    /**
     * Timeout to wait for the packages to be fetched, in terms of terminating the downloads thread
     * pool. Each time the timeout is reached, a warning will be logged but waiting for the thread
     * pool termination will continue. It is expected that network operations will eventually time
     * out on their own and/or throw exception in the worst case, leading to the thread pool
     * termination anyway.
     */
    private static final int FETCH_PACKAGES_WAITING_ITERATION_MINUTES = 1;

    /**
     * A lock object for a fast critical section in the sources processing worker threads to secure
     * the resulting packages resolution when there are multiple packages with same name but from
     * different sources and/or with different versions.
     */
    private final Object myPackagesResolutionLock = new Object();

    /** Resource resolver to use for finding imported XSDs. */
    private final LSResourceResolver mResourceResolver;

    /** {@link FallbackRemoteRepoLoader} to use if we get an XML file we can't parse. */
    private FallbackRemoteRepoLoader mFallback;

    /** The {@link RepositorySourceProvider}s to load from. */
    private final Collection<RepositorySourceProvider> mSourceProviders;

    /**
     * Constructor
     *
     * @param sources The {@link RepositorySourceProvider}s to get the {@link RepositorySource}s to
     *     load from.
     * @param resourceResolver The resolver to use to find imported XSDs, if necessary for the
     *     {@link SchemaModule}s used by the {@link RepositorySource}s.
     * @param fallback The {@link FallbackRemoteRepoLoader} to use if we can't parse an XML file.
     */
    public RemoteRepoLoaderImpl(
            @NonNull Collection<RepositorySourceProvider> sources,
            @Nullable LSResourceResolver resourceResolver,
            @Nullable FallbackRemoteRepoLoader fallback) {
        mResourceResolver = resourceResolver;
        mSourceProviders = sources;
        mFallback = fallback;
    }

    @Override
    @NonNull
    public Map<String, RemotePackage> fetchPackages(
            @NonNull ProgressIndicator progress,
            @NonNull Downloader downloader,
            @Nullable SettingsController settings) {
        Map<String, RemotePackage> result = Maps.newHashMap();
        List<RepositorySource> sources = Lists.newArrayList();

        double progressMax = 0;
        for (RepositorySourceProvider provider : mSourceProviders) {
            progressMax += 0.1 / mSourceProviders.size();
            sources.addAll(
                    provider.getSources(
                            downloader, progress.createSubProgress(progressMax), false));
        }

        // In this context we are not concerned that much about the precise progress reporting, because
        // the manifests downloading is about a dozen of tiny files, so it's expected to be relatively fast
        // and OTOH a lock-based thread-safe ProgressIndicator implementation would even likely be a bottleneck
        // (presumably, probability of that is linearly proportional to how often the progress gets reported
        // by the downloads).
        boolean wasIndeterminate = progress.isIndeterminate();
        progress.setIndeterminate(true);
        LoggingOnlyProgressIndicator loggingOnlyProgress =
                new LoggingOnlyProgressIndicator(progress);

        ExecutorService sourceThreadPool = Executors.newCachedThreadPool();
        try {
            for (RepositorySource source : sources) {
                if (!source.isEnabled()) {
                    continue;
                }
                sourceThreadPool.submit(
                        () ->
                                processSource(
                                        loggingOnlyProgress, downloader, source, settings, result));
            }
        } finally {
            shutdownAndJoin(sourceThreadPool, progress);
            progress.setIndeterminate(wasIndeterminate);
        }

        return result;
    }

    private void processSource(
            @NonNull ProgressIndicator progress,
            @NonNull Downloader downloader,
            @NonNull RepositorySource source,
            @Nullable SettingsController settings,
            @NonNull Map<String, RemotePackage> result) {
        try {
            InputStream repoStream =
                    downloader.downloadAndStream(new URL(source.getUrl()), progress);

            final List<String> errors = Lists.newArrayList();

            // Don't show the errors, in case the fallback loader can read it. But keep
            // track of them to show later in case not.
            ProgressIndicator unmarshalProgress =
                    new ProgressIndicatorAdapter() {
                        @Override
                        public void logWarning(@NonNull String s, Throwable e) {
                            errors.add(s);
                            if (e != null) {
                                errors.add(e.toString());
                            }
                        }

                        @Override
                        public void logError(@NonNull String s, Throwable e) {
                            errors.add(s);
                            if (e != null) {
                                errors.add(e.toString());
                            }
                        }
                    };

            Repository repo = null;
            try {
                repo =
                        (Repository)
                                SchemaModuleUtil.unmarshal(
                                        repoStream,
                                        source.getPermittedModules(),
                                        mResourceResolver,
                                        true,
                                        unmarshalProgress);
            } catch (JAXBException e) {
                errors.add(e.toString());
            }

            Collection<? extends RemotePackage> parsedPackages = null;
            boolean legacy = false;
            if (repo != null) {
                parsedPackages = repo.getRemotePackage();
            } else if (mFallback != null) {
                // TODO: don't require downloading again
                parsedPackages = mFallback.parseLegacyXml(source, downloader, settings, progress);
                legacy = true;
            }

            if (parsedPackages != null && !parsedPackages.isEmpty()) {
                for (RemotePackage pkg : parsedPackages) {
                    // This has to be synchronized because we update the resulting collection from multiple
                    // threads and the update logic depends on the collection content, so just the collection
                    // being concurrent wouldn't be enough.
                    // These operations do not involve any high-latency parts like network connections,
                    // so it's OK for this to be a synchronized section (i.e., a lock-free approach involving
                    // a concurrent multi-map and the likes would only complicate the code for little to no
                    // reason).
                    synchronized (myPackagesResolutionLock) {
                        RemotePackage existing = result.get(pkg.getPath());
                        if (existing != null) {
                            int compare = existing.getVersion().compareTo(pkg.getVersion());
                            if (compare > 0) {
                                // If there are multiple versions of the same package available,
                                // pick the latest.
                                continue;
                            }
                            if (compare == 0) {
                                if (legacy) {
                                    // If legacy and non-legacy packages are available with the
                                    // same version, pick the non-legacy one.
                                    continue;
                                }
                                URL existingUrl =
                                        InstallerUtil.resolveCompleteArchiveUrl(existing, progress);
                                if (existingUrl != null) {
                                    String existingProtocol = existingUrl.getProtocol();
                                    if (existingProtocol.equals("file")) {
                                        // If the existing package is local, use it.
                                        continue;
                                    }
                                }
                        }
                        }
                        Channel settingsChannel =
                                settings == null || settings.getChannel() == null
                                        ? Channel.DEFAULT
                                        : settings.getChannel();

                        if (pkg.getArchive() != null
                                && pkg.getChannel().compareTo(settingsChannel) <= 0) {
                            pkg.setSource(source);
                            result.put(pkg.getPath(), pkg);
                        }
                    }
                    source.setFetchError(null);
                }
            } else {
                progress.logWarning("Errors during XML parse:");
                for (String error : errors) {
                    progress.logWarning(error);
                }
                //noinspection VariableNotUsedInsideIf
                if (mFallback != null) {
                    progress.logWarning(
                            "Additionally, the fallback loader failed to parse the XML.");
                }
                source.setFetchError(errors.isEmpty() ? "unknown error" : errors.get(0));
            }
        } catch (MalformedURLException e) {
            source.setFetchError("Malformed URL");
            progress.logWarning(e.toString());
        } catch (IOException e) {
            source.setFetchError(e.getMessage());
            progress.logWarning(e.toString());
        }
    }

    private static void shutdownAndJoin(
            @NonNull ExecutorService threadPool, @NonNull ProgressIndicator progress) {
        threadPool.shutdown();

        try {
            while (!threadPool.awaitTermination(
                    FETCH_PACKAGES_WAITING_ITERATION_MINUTES, TimeUnit.MINUTES)) {
                progress.logWarning("Still waiting for package manifests to be fetched remotely.");
            }
        } catch (InterruptedException ignored) {
            // ignored
        }
    }

    /**
     * A thread-safe implementation of {@link DelegatingProgressIndicator} which does not report the
     * fraction, but preserves the ability to report the errors/warnings, as most underlying logging
     * implementations are thread-safe.
     */
    private static class LoggingOnlyProgressIndicator extends DelegatingProgressIndicator {
        LoggingOnlyProgressIndicator(@NonNull ProgressIndicator progress) {
            super(progress);
        }

        @Override
        public void setFraction(double fraction) {}

        @Override
        public double getFraction() {
            return 0;
        }

        @Override
        public void setText(@Nullable String text) {}

        @Override
        public void setSecondaryText(@Nullable String text) {}

        @Override
        public ProgressIndicator createSubProgress(double max) {
            return this;
        }
    }
}
