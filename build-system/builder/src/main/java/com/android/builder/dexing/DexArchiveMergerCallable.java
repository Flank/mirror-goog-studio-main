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
import com.android.dex.Dex;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;
import com.google.common.base.Verify;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * Helper class used to merge specified DEX files to a location. To accomplish that, it directly
 * invokes {@link DexMerger} from the dx.
 */
public class DexArchiveMergerCallable implements Callable<Void> {

    @NonNull private final Collection<Dex> dexesToMerge;
    @NonNull private final Path outputDex;
    @NonNull private final DxContext dxContext;

    public DexArchiveMergerCallable(
            @NonNull Collection<Dex> dexesToMerge,
            @NonNull Path outputDex,
            @NonNull DxContext dxContext) {
        this.dexesToMerge = dexesToMerge;
        this.outputDex = outputDex;
        this.dxContext = dxContext;
    }

    @Override
    public Void call() throws Exception {
        Dex mergedDex = mergeDexes(dexesToMerge);
        Files.write(outputDex, mergedDex.getBytes());

        return null;
    }

    @NonNull
    private Dex mergeDexes(@NonNull Collection<Dex> dexes) throws IOException {
        DexMerger dexMerger =
                new DexMerger(
                        dexes.toArray(new Dex[dexes.size()]), CollisionPolicy.FAIL, dxContext);

        Dex mergedDex = dexMerger.merge();
        Verify.verifyNotNull(
                mergedDex,
                String.format("Merged dex is null. We tried to merge %d DEX files", dexes.size()));
        return mergedDex;
    }
}
