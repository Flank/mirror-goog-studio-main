/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.EXT_BC;
import static com.android.SdkConstants.FN_ANDROIDX_RENDERSCRIPT_PACKAGE;
import static com.android.SdkConstants.FN_ANDROIDX_RS_JAR;
import static com.android.SdkConstants.FN_RENDERSCRIPT_V8_JAR;
import static com.android.SdkConstants.FN_RENDERSCRIPT_V8_PACKAGE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Compiles Renderscript files.
 */
public class RenderScriptProcessor {

    private static final String LIBCLCORE_BC = "libclcore.bc";

    // ABI list, as pairs of (android-ABI, toolchain-ABI)
    private static final class Abi {

        @NonNull
        private final String mDevice;
        @NonNull
        private final String mToolchain;
        @NonNull
        private final BuildToolInfo.PathId mLinker;
        @NonNull
        private final String[] mLinkerArgs;

        Abi(@NonNull String device,
            @NonNull String toolchain,
            @NonNull BuildToolInfo.PathId linker,
            @NonNull String... linkerArgs) {

            mDevice = device;
            mToolchain = toolchain;
            mLinker = linker;
            mLinkerArgs = linkerArgs;
        }
    }

    private static final Abi[] ABIS_32 = {
            new Abi("armeabi-v7a", "armv7-none-linux-gnueabi", BuildToolInfo.PathId.LD_ARM,
                    "-dynamic-linker", "/system/bin/linker", "-X", "-m", "armelf_linux_eabi"),
            new Abi("mips", "mipsel-unknown-linux", BuildToolInfo.PathId.LD_MIPS, "-EL"),
            new Abi("x86", "i686-unknown-linux", BuildToolInfo.PathId.LD_X86, "-m", "elf_i386") };
    private static final Abi[] ABIS_64 = {
            new Abi("arm64-v8a", "aarch64-linux-android", BuildToolInfo.PathId.LD_ARM64, "-X"),
            new Abi("x86_64", "x86_64-unknown-linux", BuildToolInfo.PathId.LD_X86_64, "-m", "elf_x86_64") };


    public static final String RS_DEPS = "rsDeps";

    @NonNull private final Collection<File> mSourceFolders;

    @NonNull private final Collection<File> mImportFolders;

    @NonNull
    private final File mSourceOutputDir;

    @NonNull
    private final File mResOutputDir;

    @NonNull
    private final File mObjOutputDir;

    @NonNull
    private final File mLibOutputDir;

    @NonNull
    private final BuildToolInfo mBuildToolInfo;

    @NonNull
    private final ILogger mLogger;

    private final int mTargetApi;

    private final int mOptimizationLevel;

    private final boolean mNdkMode;

    private final boolean mSupportMode;

    private final boolean mUseAndroidX;

    private final Set<String> mAbiFilters;

    // These indicate whether to compile with ndk for 32 or 64 bits
    private boolean is32Bit;
    private boolean is64Bit;


    private final File mRsLib;
    private final Map<String, File> mLibClCore = Maps.newHashMap();

    public RenderScriptProcessor(
            @NonNull Collection<File> sourceFolders,
            @NonNull Collection<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            @NonNull BuildToolInfo buildToolInfo,
            int targetApi,
            boolean debugBuild,
            int optimizationLevel,
            boolean ndkMode,
            boolean supportMode,
            boolean useAndroidX,
            @Nullable Set<String> abiFilters,
            @NonNull ILogger logger) {
        mSourceFolders = sourceFolders;
        mImportFolders = importFolders;
        mSourceOutputDir = sourceOutputDir;
        mResOutputDir = resOutputDir;
        mObjOutputDir = objOutputDir;
        mLibOutputDir = libOutputDir;
        mBuildToolInfo = buildToolInfo;
        mTargetApi = targetApi;
        mOptimizationLevel = optimizationLevel;
        mNdkMode = ndkMode;
        mSupportMode = supportMode;
        mUseAndroidX = useAndroidX;
        mAbiFilters = abiFilters;
        mLogger = logger;

        if (supportMode) {
            File rs = new File(mBuildToolInfo.getLocation(), "renderscript");
            mRsLib = new File(rs, "lib");
            File bcFolder = new File(mRsLib, "bc");
            for (Abi abi : ABIS_32) {
                File rsClCoreFile = new File(bcFolder, abi.mDevice + File.separatorChar + LIBCLCORE_BC);
                if (rsClCoreFile.exists()) {
                    mLibClCore.put(abi.mDevice, rsClCoreFile);
                }
            }
            for (Abi abi : ABIS_64) {
                File rsClCoreFile = new File(bcFolder, abi.mDevice + File.separatorChar + LIBCLCORE_BC);
                if (rsClCoreFile.exists()) {
                    mLibClCore.put(abi.mDevice, rsClCoreFile);
                }
            }
        } else {
            mRsLib = null;
        }


        // If no abi filters were set, assume compilation for both 32 bit and 64 bit
        if (abiFilters == null || abiFilters.isEmpty()) {
            is32Bit = true;
            is64Bit = true;
        } else {
            // Check if abi filters contains an abi that is 32 bit
            is32Bit =
                    Arrays.stream(ABIS_32)
                            .map((Abi abi) -> abi.mDevice)
                            .anyMatch(abi -> mAbiFilters.contains(abi));

            // Check if abi filters contains an abi that is 64 bit
            is64Bit =
                    Arrays.stream(ABIS_64)
                            .map((Abi abi) -> abi.mDevice)
                            .anyMatch(abi -> mAbiFilters.contains(abi));
        }

        // Api < 21 does not support 64 bit ndk compilation
        if (mTargetApi < 21 && is64Bit && mNdkMode) {
            throw new RuntimeException(
                    "Api version " + mTargetApi + " does not support 64 bit ndk compilation");
        }

    }

    public static File getSupportJar(File buildToolsFolder, boolean useAndroidX) {
        return new File(
                getBaseRenderscriptLibFolder(buildToolsFolder),
                (useAndroidX ? FN_ANDROIDX_RS_JAR : FN_RENDERSCRIPT_V8_JAR));
    }

    public static File getSupportNativeLibFolder(File buildToolsFolder) {
        return new File(getBaseRenderscriptLibFolder(buildToolsFolder), "packaged");
    }

    public static File getSupportBlasLibFolder(File buildToolsFolder) {
        return new File(getBaseRenderscriptLibFolder(buildToolsFolder), "blas");
    }

    private static File getBaseRenderscriptLibFolder(File buildToolsFolder) {
        return new File(buildToolsFolder, "renderscript/lib");
    }

    public void build(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws InterruptedException, ProcessException, IOException {

        List<File> renderscriptFiles = Lists.newArrayList();
        for (File dir : mSourceFolders) {
            DirectoryWalker.builder()
                    .root(dir.toPath())
                    .extensions("rs", "fs")
                    .action((start, path) -> renderscriptFiles.add(path.toFile()))
                    .build()
                    .walk();
        }

        if (renderscriptFiles.isEmpty()) {
            return;
        }

        // get the env var
        Map<String, String> env = Maps.newHashMap();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            env.put("DYLD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            env.put("LD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        }

        doMainCompilation(renderscriptFiles, processExecutor, processOutputHandler, env);

        if (mSupportMode) {
            createSupportFiles(processExecutor, processOutputHandler, env);
        }
    }

    private File getArchSpecificRawFolder(@NonNull String architecture) {
        return FileUtils.join(mResOutputDir, SdkConstants.FD_RES_RAW, "bc" + architecture);
    }

    private File getGenericRawFolder() {
        return new File(mResOutputDir, SdkConstants.FD_RES_RAW);
    }

    private void doMainCompilation(
            @NonNull List<File> inputFiles,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env)
            throws ProcessException {

        ArrayList<String> fixedBuilderArgs = new ArrayList<>();

        String rsPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS);
        String rsClangPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS_CLANG);

        List<String> architectures = new ArrayList();

        if (is32Bit) {
            architectures.add("32");
        }
        if (is64Bit) {
            architectures.add("64");
        }

        // First add all the arguments that are common between the runs

        // add all import paths
        fixedBuilderArgs.add("-I");
        fixedBuilderArgs.add(rsPath);
        fixedBuilderArgs.add("-I");
        fixedBuilderArgs.add(rsClangPath);

        for (File importPath : mImportFolders) {
            if (importPath.isDirectory()) {
                fixedBuilderArgs.add("-I");
                fixedBuilderArgs.add(importPath.getAbsolutePath());
            }
        }

        if (mSupportMode) {
            if (mUseAndroidX) {
                fixedBuilderArgs.add("-rs-package-name=" + FN_ANDROIDX_RENDERSCRIPT_PACKAGE);
            } else {
                fixedBuilderArgs.add("-rs-package-name=" + FN_RENDERSCRIPT_V8_PACKAGE);
            }
        }

        // source output
        fixedBuilderArgs.add("-p");
        fixedBuilderArgs.add(mSourceOutputDir.getAbsolutePath());

        fixedBuilderArgs.add("-target-api");
        int targetApi = mTargetApi < 11 ? 11 : mTargetApi;
        targetApi = (mSupportMode && targetApi < 18) ? 18 : targetApi;
        fixedBuilderArgs.add(Integer.toString(targetApi));

        // input files
        for (File sourceFile : inputFiles) {
            fixedBuilderArgs.add(sourceFile.getAbsolutePath());
        }

        if (mNdkMode) {
            fixedBuilderArgs.add("-reflect-c++");
        }

        fixedBuilderArgs.add("-O");
        fixedBuilderArgs.add(Integer.toString(mOptimizationLevel));

        // Due to a device side bug, let's not enable this at this time.
        //        if (mDebugBuild) {
        //            command.add("-g");
        //        }

        if (mTargetApi >= 21) {
            // Add the arguments that are specific to each run.
            // Then, for each arch specific folder, run the compiler once.
            for (String arch : architectures) {
                ArrayList<String> variableBuilderArgs = new ArrayList<>();

                // res output
                variableBuilderArgs.add("-o");

                // the renderscript compiler doesn't expect the top res folder,
                // but the raw folder directly.
                variableBuilderArgs.add(getArchSpecificRawFolder(arch).getAbsolutePath());

                if (mNdkMode) {
                    variableBuilderArgs.add("-m" + arch);
                }

                ArrayList<String> builderArgs = new ArrayList<>(variableBuilderArgs);
                builderArgs.addAll(fixedBuilderArgs);
                builderArgs.addAll(variableBuilderArgs);

                compileSingleFile(processExecutor, processOutputHandler, env, builderArgs);
            }
        } else {
            ArrayList<String> variableBuilderArgs = new ArrayList<>();
            // Add the rest of the arguments and run the compiler once
            // res output
            variableBuilderArgs.add("-o");

            // the renderscript compiler doesn't expect the top res folder,
            // but the raw folder directly.
            variableBuilderArgs.add(getGenericRawFolder().getAbsolutePath());

            ArrayList<String> builderArgs = new ArrayList<>(variableBuilderArgs);
            builderArgs.addAll(fixedBuilderArgs);
            builderArgs.addAll(variableBuilderArgs);

            compileSingleFile(processExecutor, processOutputHandler, env, builderArgs);
        }
    }

    private void compileSingleFile(
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env,
            @NonNull List<String> builderArgs)
            throws ProcessException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String renderscript = mBuildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException(BuildToolInfo.PathId.LLVM_RS_CC + " is missing");
        }

        // compile all the files in a single pass
        builder.setExecutable(renderscript);
        builder.addEnvironments(env);

        for (String arg : builderArgs) {
            builder.addArgs(arg);
        }

        ProcessResult result =
                processExecutor.execute(builder.createProcess(), processOutputHandler);
        result.rethrowFailure().assertNormalExitValue();
    }

    private void createSupportFiles(
            @NonNull final ProcessExecutor processExecutor,
            @NonNull final ProcessOutputHandler processOutputHandler,
            @NonNull final Map<String, String> env)
            throws IOException, InterruptedException, ProcessException {
        // get the generated BC files.
        int targetApi = mTargetApi < 11 ? 11 : mTargetApi;
        targetApi = (mSupportMode && targetApi < 18) ? 18 : targetApi;
        if (targetApi < 21) {
            File rawFolder = getGenericRawFolder();
            createSupportFilesHelper(rawFolder, ABIS_32, processExecutor, processOutputHandler, env);
        } else {
            File rawFolder32 = getArchSpecificRawFolder("32");
            createSupportFilesHelper(rawFolder32, ABIS_32, processExecutor,
                    processOutputHandler, env);
            File rawFolder64 = getArchSpecificRawFolder("64");
            createSupportFilesHelper(rawFolder64, ABIS_64, processExecutor,
                    processOutputHandler, env);
        }
    }

    private void createSupportFilesHelper(
            @NonNull final File rawFolder,
            @NonNull final Abi[] abis,
            @NonNull final ProcessExecutor processExecutor,
            @NonNull final ProcessOutputHandler processOutputHandler,
            @NonNull final Map<String, String> env)
            throws IOException, InterruptedException, ProcessException {
        WaitableExecutor mExecutor = WaitableExecutor.useGlobalSharedThreadPool();

        Collection<File> files = Lists.newLinkedList();
        DirectoryWalker.builder()
                .root(rawFolder.toPath())
                .extensions(EXT_BC)
                .action((start, path) -> files.add(path.toFile()))
                .build()
                .walk();

        for (final File bcFile : files) {
            String name = bcFile.getName();
            final String objName = name.replaceAll("\\.bc", ".o");
            final String soName = "librs." + name.replaceAll("\\.bc", ".so");

            for (final Abi abi : abis) {
                if (mAbiFilters != null && !mAbiFilters.contains(abi.mDevice)) {
                    continue;
                }
                // only build for the ABIs bundled in Build-Tools.
                if (mLibClCore.get(abi.mDevice) == null) {
                    // warn the user to update Build-Tools if the desired ABI is not found.
                    mLogger.warning("Skipped RenderScript support mode compilation for "
                                    + abi.mDevice
                                    + " : required components not found in Build-Tools "
                                    + mBuildToolInfo.getRevision().toString()
                                    + '\n'
                                    + "Please check and update your BuildTools.");
                    continue;
                }

                // make sure the dest folders exist
                final File objAbiFolder = new File(mObjOutputDir, abi.mDevice);
                if (!objAbiFolder.isDirectory() && !objAbiFolder.mkdirs()) {
                    throw new IOException("Unable to create dir " + objAbiFolder.getAbsolutePath());
                }

                final File libAbiFolder = new File(mLibOutputDir, abi.mDevice);
                if (!libAbiFolder.isDirectory() && !libAbiFolder.mkdirs()) {
                    throw new IOException("Unable to create dir " + libAbiFolder.getAbsolutePath());
                }

                mExecutor.execute(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        File objFile = createSupportObjFile(
                                bcFile,
                                abi,
                                objName,
                                objAbiFolder,
                                processExecutor,
                                processOutputHandler,
                                env);
                        createSupportLibFile(
                                objFile,
                                abi,
                                soName,
                                libAbiFolder,
                                processExecutor,
                                processOutputHandler,
                                env);
                        return null;
                    }
                });
            }
        }

        mExecutor.waitForTasksWithQuickFail(true /*cancelRemaining*/);
    }

    private File createSupportObjFile(
            @NonNull File bcFile,
            @NonNull Abi abi,
            @NonNull String objName,
            @NonNull File objAbiFolder,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env) throws ProcessException {

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(mBuildToolInfo.getPath(BuildToolInfo.PathId.BCC_COMPAT));
        builder.addEnvironments(env);

        builder.addArgs("-O" + Integer.toString(mOptimizationLevel));

        File outFile = new File(objAbiFolder, objName);
        builder.addArgs("-o", outFile.getAbsolutePath());

        builder.addArgs("-fPIC");
        builder.addArgs("-shared");

        builder.addArgs("-rt-path", mLibClCore.get(abi.mDevice).getAbsolutePath());

        builder.addArgs("-mtriple", abi.mToolchain);

        builder.addArgs(bcFile.getAbsolutePath());

        processExecutor.execute(
                builder.createProcess(), processOutputHandler)
                .rethrowFailure().assertNormalExitValue();

        return outFile;
    }

    private void createSupportLibFile(
            @NonNull File objFile,
            @NonNull Abi abi,
            @NonNull String soName,
            @NonNull File libAbiFolder,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull Map<String, String> env) throws ProcessException {

        File intermediatesFolder = new File(mRsLib, "intermediates");
        File intermediatesAbiFolder = new File(intermediatesFolder, abi.mDevice);
        File packagedFolder = new File(mRsLib, "packaged");
        File packagedAbiFolder = new File(packagedFolder, abi.mDevice);

        ProcessInfoBuilder builder = new ProcessInfoBuilder();
        builder.setExecutable(mBuildToolInfo.getPath(abi.mLinker));
        builder.addEnvironments(env);

        builder.addArgs("--eh-frame-hdr")
                .addArgs(abi.mLinkerArgs)
                .addArgs("-shared", "-Bsymbolic", "-z", "noexecstack", "-z", "relro", "-z", "now");

        File outFile = new File(libAbiFolder, soName);
        builder.addArgs("-o", outFile.getAbsolutePath());

        builder.addArgs(
                "-L" + intermediatesAbiFolder.getAbsolutePath(),
                "-L" + packagedAbiFolder.getAbsolutePath(),
                "-soname",
                soName,
                objFile.getAbsolutePath(),
                new File(intermediatesAbiFolder, "libcompiler_rt.a").getAbsolutePath(),
                "-lRSSupport",
                "-lm",
                "-lc");

        processExecutor.execute(
                builder.createProcess(), processOutputHandler)
                .rethrowFailure().assertNormalExitValue();
    }
}
