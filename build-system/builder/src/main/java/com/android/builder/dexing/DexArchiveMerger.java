package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.dx.command.dexer.DxContext;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

/**
 * A dex archive merger that can merge dex files from multiple dex archives into one or more dex
 * files.
 */
public interface DexArchiveMerger {

    /** Creates an instance of dex archive merger that is using dx to merge dex files. */
    @NonNull
    static DexArchiveMerger createDxDexMerger(
            @NonNull DxContext dxContext, @NonNull ForkJoinPool executor) {
        return new DxDexArchiveMerger(dxContext, executor);
    }

    /**
     * Merges the specified dex archive to final dex file(s). Full paths to the dex archives must be
     * specified, and the merged dex files are written to the specified directory. Dexing type
     * specifies how files will be merged:
     *
     * <ul>
     *   <li>if it is {@link DexingType#MONO_DEX}, a single dex file is written, named classes.dex
     *   <li>if it is {@link DexingType#LEGACY_MULTIDEX}, there can be more than 1 dex files. Files
     *       are named classes.dex, classes2.dex, classes3.dex etc. In this mode, path to a file
     *       containing the list of classes to be placed in the main dex file must be specified.
     *   <li>if it is {@link DexingType#NATIVE_MULTIDEX}, there can be 1 or more dex files.
     * </ul>
     *
     * @param inputs paths to the dex archives that should be merged
     * @param outputDir directory where merged dex file(s) will be written, must exist
     * @param mainDexClasses file containing list of classes to be merged in the main dex file
     * @param dexingType specifies how to merge dex files
     */
    void mergeDexArchives(
            @NonNull Iterable<Path> inputs,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException;
}
