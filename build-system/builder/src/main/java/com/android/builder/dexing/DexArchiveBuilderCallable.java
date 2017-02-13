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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;
import com.android.utils.FileUtils;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Callable helper class used to convert a single CLASS file to a DEX file. It produces an {@link
 * DexArchiveEntry} which represents a single DEX file in a {@link DexArchive}. This DEX file will
 * contain exactly one class.
 *
 * <p>In order to perform the conversion, it invokes dx directly to parse and translate the content
 * of a class file.
 */
public class DexArchiveBuilderCallable implements Callable<DexArchiveEntry> {

    @NonNull private final Path relativePath;
    @NonNull private final byte[] classFileContent;

    @NonNull private final DxContext dxContext;
    @NonNull private final DexOptions dexOptions;
    @NonNull private final CfOptions cfOptions;

    public DexArchiveBuilderCallable(
            @NonNull Path relativePath,
            @NonNull byte[] classFileContent,
            @NonNull DxContext dxContext,
            @NonNull DexOptions dexOptions,
            @NonNull CfOptions cfOptions) {
        this.relativePath = relativePath;
        this.classFileContent = classFileContent;
        this.dxContext = dxContext;
        this.dexOptions = dexOptions;
        this.cfOptions = cfOptions;
    }

    @Override
    public DexArchiveEntry call() throws Exception {
        // parses the class file
        String unixClassFile = FileUtils.toSystemIndependentPath(relativePath);
        DirectClassFile directClassFile = parseClass(unixClassFile, classFileContent);

        DexFile dexFile = new DexFile(dexOptions);
        // starts the actual translation and writes the content to the dex file specified
        translateClass(classFileContent, directClassFile, dexFile);

        byte[] dexClassContent = dexFile.toDex(null, false);
        return new DexArchiveEntry(dexClassContent, relativePath);
    }

    /** Copied from dx, from {@link com.android.dx.command.dexer.Main}. */
    private static DirectClassFile parseClass(@NonNull String name, byte[] bytes) {
        DirectClassFile cf = new DirectClassFile(bytes, name, true);
        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        cf.getMagic(); // triggers the actual parsing
        return cf;
    }

    /** Copied from dx, from {@link com.android.dx.command.dexer.Main}. */
    private void translateClass(byte[] bytes, @NonNull DirectClassFile cf, DexFile dexFile) {
        ClassDefItem classDefItem =
                CfTranslator.translate(dxContext, cf, bytes, cfOptions, dexOptions, dexFile);
        dexFile.add(classDefItem);
    }
}
