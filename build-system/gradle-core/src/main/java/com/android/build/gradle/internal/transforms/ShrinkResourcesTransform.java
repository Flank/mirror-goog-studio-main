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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.MultiOutputPolicy;
import com.android.build.gradle.tasks.ResourceUsageAnalyzer;
import com.android.builder.core.VariantType;
import com.android.ide.common.build.ApkInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.xml.sax.SAXException;

/**
 * Implementation of Resource Shrinking as a transform.
 *
 * Since this transform only reads the data from the stream but does not output anything
 * back into the stream, it is a no-op transform, asking only for referenced scopes, and not
 * "consumed" scopes.
 * <p>
 * To run the tests specifically related to resource shrinking:
 * <pre>
 * ./gradlew :base:int:test -Dtest.single=ShrinkResourcesTest
 * </pre>
 */
public class ShrinkResourcesTransform extends Transform {

    /** Whether we've already warned about how to turn off shrinking. Used to avoid
     * repeating the same multi-line message for every repeated abi split. */
    private static boolean ourWarned = true; // Logging disabled until shrinking is on by default.

    /**
     * Associated variant data that the strip task will be run against. Used to locate not only
     * locations the task needs (e.g. for resources and generated R classes) but also to obtain the
     * resource merging task, since we will run it a second time here to generate a new .ap_ file
     * with fewer resources
     */
    @NonNull private final BaseVariantData variantData;

    @NonNull private final Logger logger;

    @NonNull private final BuildableArtifact sourceDir;
    @NonNull private final BuildableArtifact resourceDir;
    @Nullable private final BuildableArtifact mappingFileSrc;
    @NonNull private final BuildableArtifact mergedManifests;
    @NonNull private final BuildableArtifact uncompressedResources;

    @NonNull private final AaptOptions aaptOptions;
    @NonNull private final VariantType variantType;
    private final boolean isDebuggableBuildType;
    @NonNull private final MultiOutputPolicy multiOutputPolicy;

    @NonNull private final File compressedResources;

    public ShrinkResourcesTransform(
            @NonNull BaseVariantData variantData,
            @NonNull BuildableArtifact uncompressedResources,
            @NonNull File compressedResources,
            @NonNull Logger logger) {
        VariantScope variantScope = variantData.getScope();
        GlobalScope globalScope = variantScope.getGlobalScope();
        GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();

        this.variantData = variantData;
        this.logger = logger;

        BuildArtifactsHolder artifacts = variantScope.getArtifacts();
        this.sourceDir =
                artifacts.getFinalArtifactFiles(
                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES);
        this.resourceDir = variantScope.getArtifacts().getFinalArtifactFiles(
                InternalArtifactType.MERGED_NOT_COMPILED_RES);
        this.mappingFileSrc =
                variantScope.getArtifacts().hasArtifact(InternalArtifactType.APK_MAPPING)
                        ? variantScope
                                .getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.APK_MAPPING)
                        : null;
        this.mergedManifests =
                artifacts.getFinalArtifactFiles(InternalArtifactType.MERGED_MANIFESTS);
        this.uncompressedResources = uncompressedResources;

        this.aaptOptions = globalScope.getExtension().getAaptOptions();
        this.variantType = variantData.getType();
        this.isDebuggableBuildType = variantConfig.getBuildType().isDebuggable();
        this.multiOutputPolicy = variantData.getMultiOutputPolicy();

        this.compressedResources = compressedResources;
    }

    @NonNull
    @Override
    public String getName() {
        return "shrinkRes";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        // When R8 produces dex files, this transform analyzes them. If R8 or Proguard produce
        // class files, this transform will analyze those. That is why both types are specified.
        return ImmutableSet.of(ExtendedContentType.DEX, DefaultContentType.CLASSES);
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of();
    }

    @NonNull
    @Override
    public Set<Scope> getScopes() {
        return TransformManager.EMPTY_SCOPES;
    }

    @NonNull
    @Override
    public Set<Scope> getReferencedScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        Collection<SecondaryFile> secondaryFiles = Lists.newLinkedList();

        // FIXME use Task output to get FileCollection for sourceDir/resourceDir
        secondaryFiles.add(SecondaryFile.nonIncremental(sourceDir));
        secondaryFiles.add(SecondaryFile.nonIncremental(resourceDir));

        if (mappingFileSrc != null) {
            secondaryFiles.add(SecondaryFile.nonIncremental(mappingFileSrc));
        }

        secondaryFiles.add(SecondaryFile.nonIncremental(mergedManifests));
        secondaryFiles.add(SecondaryFile.nonIncremental(uncompressedResources));

        return secondaryFiles;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMapWithExpectedSize(7);
        params.put(
                "aaptOptions",
                Joiner.on(";")
                        .join(
                                aaptOptions.getIgnoreAssetsPattern() != null
                                        ? aaptOptions.getIgnoreAssetsPattern()
                                        : "",
                                aaptOptions.getNoCompress() != null
                                        ? Joiner.on(":").join(aaptOptions.getNoCompress())
                                        : "",
                                aaptOptions.getFailOnMissingConfigEntry(),
                                aaptOptions.getAdditionalParameters() != null
                                        ? Joiner.on(":").join(aaptOptions.getAdditionalParameters())
                                        : "",
                                aaptOptions.getCruncherProcesses()));
        params.put("variantType", variantType.getName());
        params.put("isDebuggableBuildType", isDebuggableBuildType);
        params.put("splitHandlingPolicy", multiOutputPolicy);

        return params;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(compressedResources);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        BuildElements mergedManifestsOutputs =
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, mergedManifests);

        ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, uncompressedResources)
                .transform(
                        (ApkInfo apkInfo, File buildInput) ->
                                splitAction(
                                        apkInfo, buildInput, mergedManifestsOutputs, invocation))
                .into(InternalArtifactType.SHRUNK_PROCESSED_RES, compressedResources);

    }

    @Nullable
    public File splitAction(
            @NonNull ApkInfo apkInfo,
            @Nullable File uncompressedResourceFile,
            @NonNull BuildElements mergedManifests,
            TransformInvocation invocation) {

        if (uncompressedResourceFile == null) {
            return null;
        }

        Collection<TransformInput> referencedInputs = invocation.getReferencedInputs();
        List<File> classes = new ArrayList<>();
        for (TransformInput transformInput : referencedInputs) {
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                classes.add(directoryInput.getFile());
            }
            for (JarInput jarInput : transformInput.getJarInputs()) {
                classes.add(jarInput.getFile());
            }
        }

        File reportFile = null;
        File mappingFile =
                mappingFileSrc != null ? BuildableArtifactUtil.singleFile(mappingFileSrc) : null;
        if (mappingFile != null) {
            File logDir = mappingFile.getParentFile();
            if (logDir != null) {
                reportFile = new File(logDir, "resources.txt");
            }
        }

        File compressedResourceFile =
                new File(
                        compressedResources,
                        "resources-" + apkInfo.getBaseName() + "-stripped.ap_");
        FileUtils.mkdirs(compressedResourceFile.getParentFile());

        BuildOutput mergedManifest = mergedManifests.element(apkInfo);
        if (mergedManifest == null) {
            try {
                FileUtils.copyFile(uncompressedResourceFile, compressedResourceFile);
            } catch (IOException e) {
                logger.error("Failed to copy uncompressed resource file :", e);
                throw new RuntimeException("Failed to copy uncompressed resource file", e);
            }
            return compressedResourceFile;
        }

        // Analyze resources and usages and strip out unused
        ResourceUsageAnalyzer analyzer =
                new ResourceUsageAnalyzer(
                        Iterables.getOnlyElement(sourceDir.getFiles()),
                        classes,
                        mergedManifest.getOutputFile(),
                        mappingFile,
                        BuildableArtifactUtil.singleFile(resourceDir),
                        reportFile,
                        ResourceUsageAnalyzer.ApkFormat.BINARY);
        try {
            analyzer.setVerbose(logger.isEnabled(LogLevel.INFO));
            analyzer.setDebug(logger.isEnabled(LogLevel.DEBUG));
            try {
                analyzer.analyze();
            } catch (IOException | ParserConfigurationException | SAXException e) {
                throw new RuntimeException(e);
            }

            // Just rewrite the .ap_ file to strip out the res/ files for unused resources
            try {
                analyzer.rewriteResourceZip(uncompressedResourceFile, compressedResourceFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Dump some stats
            int unused = analyzer.getUnusedResourceCount();
            if (unused > 0) {
                StringBuilder sb = new StringBuilder(200);
                sb.append("Removed unused resources");

                // This is a bit misleading until we can strip out all resource types:
                //int total = analyzer.getTotalResourceCount()
                //sb.append("(" + unused + "/" + total + ")")

                long before = uncompressedResourceFile.length();
                long after = compressedResourceFile.length();
                long percent = (int) ((before - after) * 100 / before);
                sb.append(": Binary resource data reduced from ").
                        append(toKbString(before)).
                        append("KB to ").
                        append(toKbString(after)).
                        append("KB: Removed ").append(percent).append("%");
                if (!ourWarned) {
                    ourWarned = true;
                    String name = variantData.getVariantConfiguration().getBuildType().getName();
                    sb.append("\n")
                            .append(
                                    "Note: If necessary, you can disable resource shrinking by adding\n")
                            .append("android {\n")
                            .append("    buildTypes {\n")
                            .append("        ")
                            .append(name)
                            .append(" {\n")
                            .append("            shrinkResources false\n")
                            .append("        }\n")
                            .append("    }\n")
                            .append("}");
                }

                System.out.println(sb.toString());
            }
        } finally {
            analyzer.dispose();
        }
        return compressedResourceFile;
    }

    private static String toKbString(long size) {
        return Integer.toString((int)size/1024);
    }
}
