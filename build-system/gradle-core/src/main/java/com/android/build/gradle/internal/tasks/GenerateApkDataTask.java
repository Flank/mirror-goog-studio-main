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

package com.android.build.gradle.internal.tasks;

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.SdkConstants.FD_RES_XML;
import static com.android.build.gradle.internal.scope.InternalArtifactType.APK;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.res.Aapt2MavenUtils;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.ProcessException;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** Task to generate micro app data res file. */
public class GenerateApkDataTask extends NonIncrementalTask {

    @Nullable private FileCollection apkDirectoryFileCollection;

    private File resOutputDir;

    private File manifestFile;

    private Supplier<String> mainPkgName;

    private int minSdkVersion;

    private int targetSdkVersion;

    private FileCollection aapt2Executable;

    @Override
    protected void doTaskAction() throws IOException, ProcessException {
        // if the FileCollection contains no file, then there's nothing to do just abort.
        File apkDirectory = null;
        if (apkDirectoryFileCollection != null) {
            Set<File> files = apkDirectoryFileCollection.getFiles();
            if (files.isEmpty()) {
                return;
            }

            if (files.size() > 1) {
                throw new IllegalStateException(
                        "Wear App dependency resolve to more than one file: " + files);
            }

            apkDirectory = Iterables.getOnlyElement(files);

            if (!apkDirectory.isDirectory()) {
                throw new IllegalStateException(
                        "Wear App dependency does not resolve to a directory: " + files);
            }
        }

        // always empty output dir.
        File outDir = getResOutputDir();
        FileUtils.cleanOutputDir(outDir);

        if (apkDirectory != null) {
            BuildElements apks = ExistingBuildElements.from(APK, apkDirectory);

            if (apks.isEmpty()) {
                throw new IllegalStateException("Wear App dependency resolve to zero APK");
            }

            if (apks.size() > 1) {
                throw new IllegalStateException(
                        "Wear App dependency resolve to more than one APK: "
                                + apks.stream()
                                        .map(BuildOutput::getOutputFile)
                                        .collect(Collectors.toList()));
            }

            File apk = Iterables.getOnlyElement(apks).getOutputFile();

            // copy the file into the destination, by sanitizing the name first.
            File rawDir = new File(outDir, FD_RES_RAW);
            FileUtils.mkdirs(rawDir);

            File to = new File(rawDir, ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE);
            Files.copy(apk, to);

            generateApkData(apk, outDir, getMainPkgName(), aapt2Executable.getSingleFile());
        } else {
            generateUnbundledWearApkData(outDir, getMainPkgName());
        }

        generateApkDataEntryInManifest(minSdkVersion, targetSdkVersion, manifestFile);
    }

    private void generateApkData(
            @NonNull File apkFile,
            @NonNull File outResFolder,
            @NonNull String mainPkgName,
            @NonNull File aapt2Executable)
            throws ProcessException, IOException {

        ApkInfoParser parser =
                new ApkInfoParser(aapt2Executable, new GradleProcessExecutor(getProject()));
        ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apkFile);

        if (!apkInfo.getPackageName().equals(mainPkgName)) {
            throw new RuntimeException(
                    "The main and the micro apps do not have the same package name.");
        }

        String content =
                String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<wearableApp package=\"%1$s\">\n"
                                + "    <versionCode>%2$s</versionCode>\n"
                                + "    <versionName>%3$s</versionName>\n"
                                + "    <rawPathResId>%4$s</rawPathResId>\n"
                                + "</wearableApp>",
                        apkInfo.getPackageName(),
                        apkInfo.getVersionCode(),
                        apkInfo.getVersionName(),
                        ANDROID_WEAR_MICRO_APK);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    private static void generateUnbundledWearApkData(
            @NonNull File outResFolder, @NonNull String mainPkgName) throws IOException {

        String content =
                String.format(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<wearableApp package=\"%1$s\">\n"
                                + "    <unbundled />\n"
                                + "</wearableApp>",
                        mainPkgName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    private static void generateApkDataEntryInManifest(
            int minSdkVersion, int targetSdkVersion, @NonNull File manifestFile)
            throws IOException {

        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                .append("    package=\"${packageName}\">\n")
                .append("    <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion)
                .append("\"");
        if (targetSdkVersion != -1) {
            content.append(" android:targetSdkVersion=\"").append(targetSdkVersion).append("\"");
        }
        content.append("/>\n");
        content.append("    <application>\n")
                .append("        <meta-data android:name=\"" + ANDROID_WEAR + "\"\n")
                .append("                   android:resource=\"@xml/" + ANDROID_WEAR_MICRO_APK)
                .append("\" />\n")
                .append("   </application>\n")
                .append("</manifest>\n");

        Files.asCharSink(manifestFile, Charsets.UTF_8).write(content);
    }


    @OutputDirectory
    public File getResOutputDir() {
        return resOutputDir;
    }

    public void setResOutputDir(File resOutputDir) {
        this.resOutputDir = resOutputDir;
    }

    @InputFiles
    @Optional
    public FileCollection getApkFileCollection() {
        return apkDirectoryFileCollection;
    }

    @Input
    public String getMainPkgName() {
        return mainPkgName.get();
    }

    @Input
    public int getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    @Input
    public int getTargetSdkVersion() {
        return targetSdkVersion;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public FileCollection getAapt2Executable() {
        return aapt2Executable;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    @OutputFile
    public File getManifestFile() {
        return manifestFile;
    }

    public static class CreationAction extends VariantTaskCreationAction<GenerateApkDataTask> {

        @Nullable private FileCollection apkFileCollection;

        public CreationAction(
                @NonNull VariantScope scope, @Nullable FileCollection apkFileCollection) {
            super(scope);
            this.apkFileCollection = apkFileCollection;
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("handle", "MicroApk");
        }

        @Override
        @NonNull
        public Class<GenerateApkDataTask> getType() {
            return GenerateApkDataTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends GenerateApkDataTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope().getTaskContainer().setMicroApkTask(taskProvider);
            getVariantScope().getTaskContainer().setGenerateApkDataTask(taskProvider);
        }

        @Override
        public void configure(@NonNull GenerateApkDataTask task) {
            super.configure(task);

            VariantScope scope = getVariantScope();

            final ApkVariantData variantData = (ApkVariantData) scope.getVariantData();
            final GradleVariantConfiguration variantConfiguration =
                    variantData.getVariantConfiguration();

            task.setResOutputDir(scope.getMicroApkResDirectory());

            task.apkDirectoryFileCollection = apkFileCollection;

            task.manifestFile = scope.getMicroApkManifestFile();
            task.mainPkgName = variantConfiguration::getApplicationId;
            task.minSdkVersion = variantConfiguration.getMinSdkVersion().getApiLevel();
            task.targetSdkVersion = variantConfiguration.getTargetSdkVersion().getApiLevel();

            task.aapt2Executable = Aapt2MavenUtils.getAapt2FromMaven(scope.getGlobalScope());
        }
    }
}
