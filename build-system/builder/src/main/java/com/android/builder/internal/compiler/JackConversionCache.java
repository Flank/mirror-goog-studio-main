/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.builder.core.ErrorReporter;
import com.android.builder.core.JackProcessOptions;
import com.android.builder.core.JackToolchain;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

/**
 * Cache for jar -> jack conversion, using the Jack --import tool.
 *
 * <p>Since we cannot yet have a single task for each library that needs to be run through Jack
 * (because there is no task-level parallelization), this class allows reusing the output of the
 * Jack process for a library in a project in other projects.
 *
 * <p>Because different project could use different build-tools, both the library to be converted
 * and the version of the build tools are used as keys in the cache.
 *
 * <p>The API is fairly simple, just call {@link #convertLibrary(File, File, JackProcessOptions,
 * boolean, BuildToolInfo, ILogger, ErrorReporter, JavaProcessExecutor)}
 *
 * <p>The call will be blocking until the conversion happened, either through actually running Jack
 * or through copying the output of a previous Jack run.
 *
 * <p>After a build a call to {@link #clear(java.io.File, com.android.utils.ILogger)} with a file
 * will allow saving the known converted libraries for future reuse.
 */
public class JackConversionCache extends PreProcessCache<JackDexKey> {

    private static final JackConversionCache sSingleton = new JackConversionCache();

    // If Jack has been used for conversion
    private static final String JACK_USED = "jack";
    // If Jill has been used for conversion
    private static final String JILL_USED = "jill";

    public static JackConversionCache getCache() {
        return sSingleton;
    }

    @NonNull
    @Override
    protected KeyFactory<JackDexKey> getKeyFactory() {
        return JackDexKey.FACTORY;
    }

    /**
     * Converts a given library to a given output with Jack, using a specific version of the
     * build-tools.
     *
     * @throws ProcessException if it fails
     */
    public void convertLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
            @NonNull JackProcessOptions options,
            boolean isJackInProcess,
            @NonNull BuildToolInfo buildToolInfo,
            @NonNull ILogger logger,
            @NonNull ErrorReporter errorReporter,
            @NonNull JavaProcessExecutor javaProcessExecutor)
            throws ProcessException, JackToolchain.ToolchainException, ClassNotFoundException,
                    InterruptedException, IOException {
        JackDexKey itemKey =
                JackDexKey.of(
                        inputFile,
                        buildToolInfo.getRevision(),
                        options.getJumboMode(),
                        options.getDexOptimize(),
                        options.getMinSdkVersion().getApiString(),
                        options.getUseJill() ? JILL_USED : JACK_USED,
                        options.getAdditionalParameters());

        Pair<PreProcessCache.Item, Boolean> pair = getItem(logger, itemKey);
        Item item = pair.getFirst();

        // if this is a new item
        if (pair.getSecond()) {
            try {
                // haven't process this file yet so do it and record it.
                JackToolchain toolchain = new JackToolchain(buildToolInfo, logger, errorReporter);
                toolchain.convert(options, javaProcessExecutor, isJackInProcess);


                item.getOutputFiles().add(outFile);

                incrementMisses();
            } catch (Exception exception) {
                // in case of error, delete (now obsolete) output file
                //noinspection ResultOfMethodCallIgnored - we are throwing an error anyway.
                outFile.delete();
                // and rethrow the error
                throw exception;
            } finally {
                // enable other threads to use the output of this pre-dex.
                // if something was thrown they'll handle the missing output file.
                item.getLatch().countDown();
            }
        } else {
            // wait until the file is pre-dexed by the first thread.
            item.getLatch().await();

            // check that the generated file actually exists
            // while the api allow for 2+ files, there's only ever one in this case.
            File fromFile = item.getOutputFiles().get(0);

            if (!fromFile.getCanonicalPath().equals(outFile.getCanonicalPath())
                    && fromFile.isFile()) {
                // file already pre-dex, just copy the output.
                // while the api allow for 2+ files, there's only ever one in this case.
                FileUtils.mkdirs(outFile.getParentFile());
                Files.copy(fromFile, outFile);
                incrementHits();
            }
        }
    }
}
