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
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.AtomicDouble;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import javax.xml.bind.JAXBException;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Utility class that loads {@link Repository}s from {@link RepositorySource}s.
 */
public class RemoteRepoLoaderImpl implements RemoteRepoLoader {
    /**
     * Typical number of repo sources to expect. This sets the concurrency level for parallel
     * downloads.
     */
    private static final int ESTIMATED_SOURCES_NUMBER = 10;

    private static final int FETCH_PACKAGES_WAITING_ITERATION_MINUTES = 1;

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
        Map<String, RemotePackage> result =
                new MapMaker().concurrencyLevel(ESTIMATED_SOURCES_NUMBER).makeMap();
        List<RepositorySource> sources = new ArrayList<>();
        AtomicDouble progressMax = new AtomicDouble(0);
        ExecutorService sourceThreadPool = Executors.newCachedThreadPool();
        for (RepositorySourceProvider provider : mSourceProviders) {
            progressMax.addAndGet(0.1 / mSourceProviders.size());
            sources.addAll(
                    provider.getSources(
                            downloader, progress.createSubProgress(progressMax.get()), false));
        }

        // Progress divided in two halves: the first step is to download the manifest, the second one is to parse it & check.
        double progressIncrement = 0.9 / (sources.size() * 2.);
        for (RepositorySource source : sources) {
            if (!source.isEnabled()) {
                progressMax.addAndGet(2 * progressIncrement);
                progress.setFraction(progressMax.get());
                continue;
            }
            sourceThreadPool.submit(
                    () ->
                            processSource(
                                    progressMax,
                                    progressIncrement,
                                    progress,
                                    downloader,
                                    source,
                                    settings,
                                    result));
        }
        shutdownAndJoin(sourceThreadPool, progress);

        return result;
    }

    private void processSource(
            @NonNull AtomicDouble progressMax,
            double progressIncrement,
            @NonNull ProgressIndicator progress,
            @NonNull Downloader downloader,
            @NonNull RepositorySource source,
            @Nullable SettingsController settings,
            @NonNull Map<String, RemotePackage> result) {
        try {
            progressMax.addAndGet(progressIncrement);
            InputStream repoStream =
                    downloader.downloadAndStream(
                            new URL(source.getUrl()),
                            progress.createSubProgress(progressMax.get()));
            progress.setFraction(progressMax.get());
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
                parsedPackages =
                        mFallback.parseLegacyXml(
                                source,
                                downloader,
                                settings,
                                progress.createSubProgress(
                                        progressMax.addAndGet(progressIncrement)));
                legacy = true;
            }
            progressMax.addAndGet(progressIncrement);
            progress.setFraction(progressMax.get());

            if (parsedPackages != null && !parsedPackages.isEmpty()) {
                for (RemotePackage pkg : parsedPackages) {
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
                progress.logWarning("Still waiting for packages manifests to fetch.");
            }
        } catch (InterruptedException ignored) {
            // ignored
        }
    }
}
