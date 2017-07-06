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
import com.android.annotations.Nullable;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;
import com.android.dx.util.ByteArray;
import com.android.dx.util.ByteArrayAnnotatedOutput;
import com.android.utils.PathUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class DxDexArchiveBuilder extends DexArchiveBuilder {

    private static final Logger LOGGER = Logger.getLogger(DxDexArchiveBuilder.class.getName());

    private final DexArchiveBuilderConfig config;
    @Nullable private byte[] inStorage;
    @Nullable private DexFile.Storage outStorage;

    public DxDexArchiveBuilder(DexArchiveBuilderConfig config) {
        this.config = config;
        outStorage =
                config.getOutBufferSize() > 0
                        ? new DexFile.Storage(new byte[config.getOutBufferSize()])
                        : null;
    }

    @Override
    public void convert(@NonNull Stream<ClassFileEntry> entries, @NonNull DexArchive output)
            throws DexArchiveBuilderException {
        inStorage = config.getInBufferSize() > 0 ? new byte[config.getInBufferSize()] : null;
        try {
            entries.forEach(
                    classFileEntry -> {
                        try {
                            ByteArray byteArray;
                            if (inStorage != null) {
                                if (classFileEntry.getSize() > inStorage.length) {
                                    if (LOGGER.isLoggable(Level.FINER)) {
                                        LOGGER.log(
                                                Level.FINER,
                                                "File too big "
                                                        + classFileEntry.getSize()
                                                        + " : "
                                                        + classFileEntry.getRelativePath()
                                                        + " vs "
                                                        + inStorage.length);
                                    }
                                    inStorage = new byte[(int) classFileEntry.getSize()];
                                }
                                int readBytes = classFileEntry.readAllBytes(inStorage);
                                byteArray = new ByteArray(inStorage, 0, readBytes);
                            } else {
                                byteArray = new ByteArray(classFileEntry.readAllBytes());
                            }
                            dex(classFileEntry.getRelativePath(), byteArray, output);

                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.SEVERE,
                                    String.format(
                                            "Error while processing %s",
                                            classFileEntry.getRelativePath()),
                                    e);
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            throw DexArchiveBuilderException.wrap(e);
        }
    }

    public void dex(Path relativePath, ByteArray classBytes, DexArchive output) throws IOException {

        // parses the class file
        String unixClassFile = PathUtils.toSystemIndependentPath(relativePath);

        // Copied from dx, from com.android.dx.command.dexer.Main
        DirectClassFile cf = new DirectClassFile(classBytes, unixClassFile, true);
        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
        cf.getMagic(); // triggers the actual parsing

        // starts the actual translation and writes the content to the dex file
        // specified
        DexFile dexFile = new DexFile(config.getDexOptions());

        // Copied from dx, from com.android.dx.command.dexer.Main
        ClassDefItem classDefItem =
                CfTranslator.translate(
                        config.getDxContext(),
                        cf,
                        null,
                        config.getCfOptions(),
                        config.getDexOptions(),
                        dexFile);
        dexFile.add(classDefItem);

        if (outStorage != null) {
            ByteArrayAnnotatedOutput byteArrayAnnotatedOutput = dexFile.writeTo(outStorage);
            output.addFile(
                    ClassFileEntry.withDexExtension(relativePath),
                    byteArrayAnnotatedOutput.getArray(),
                    0,
                    byteArrayAnnotatedOutput.getCursor());
        } else {
            byte[] bytes = dexFile.toDex(null, false);
            output.addFile(ClassFileEntry.withDexExtension(relativePath), bytes, 0, bytes.length);
        }
    }
}
