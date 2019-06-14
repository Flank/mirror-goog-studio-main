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

import static com.android.utils.FileUtils.mkdirs;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.WorkLimiter;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import proguard.ClassPath;
import proguard.ClassPathEntry;
import proguard.ClassSpecification;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.KeepClassSpecification;
import proguard.ParseException;
import proguard.ProGuard;
import proguard.classfile.util.ClassUtil;
import proguard.util.ListUtil;

/** ProGuard support as a transform */
public class ProGuardTransform extends ProguardConfigurable {

    protected static final List<String> JAR_FILTER = ImmutableList.of("!META-INF/MANIFEST.MF");
    /** This constant replaces that in now-deleted SimpleWorkQueue */
    private static final int PROGUARD_CONCURRENCY_LIMIT = 4;

    @GuardedBy("ProGuardTransform.class")
    @Nullable
    private static WorkLimiter proguardWorkLimiter;

    private static final Logger LOG = Logging.getLogger(ProGuardTransform.class);
    protected final Configuration configuration = new Configuration();

    private final VariantScope variantScope;
    // keep hold of the file that are added as inputs, to avoid duplicates. This is mainly because
    // of the handling of local jars for library projects where they show up both in the LOCAL_DEPS
    // and the EXTERNAL stream
    ListMultimap<File, List<String>> fileToFilter = ArrayListMultimap.create();
    private Property<RegularFile> printMapping;

    private File testedMappingFile = null;
    private FileCollection testMappingConfiguration = null;

    public ProGuardTransform(@NonNull VariantScope variantScope) {
        super(
                variantScope.getGlobalScope().getProject().files(),
                variantScope.getVariantData().getType(),
                variantScope.consumesFeatureJars());
        configuration.useMixedCaseClassNames = false;
        configuration.programJars = new ClassPath();
        configuration.libraryJars = new ClassPath();

        this.variantScope = variantScope;
    }

    private static String fileDescription(String fileName) {
        return "file '" + fileName + "'";
    }

    @Override
    public void setOutputFile(@NonNull Property<RegularFile> file) {
        printMapping = file;
    }

    @NonNull
    private static synchronized WorkLimiter getWorkLimiter() {
        if (proguardWorkLimiter == null) {
            proguardWorkLimiter = new WorkLimiter(PROGUARD_CONCURRENCY_LIMIT);
        }
        return proguardWorkLimiter;
    }

    public void applyTestedMapping(@Nullable FileCollection testMappingConfiguration) {
        this.testMappingConfiguration = testMappingConfiguration;
    }

    @NonNull
    @Override
    public String getName() {
        return "proguard";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        final List<SecondaryFile> files = Lists.newArrayList();

        if (testedMappingFile != null && testedMappingFile.isFile()) {
            files.add(SecondaryFile.nonIncremental(testedMappingFile));
        } else if (testMappingConfiguration != null) {
            files.add(SecondaryFile.nonIncremental(testMappingConfiguration));
        }

        // the config files
        files.add(SecondaryFile.nonIncremental(getAllConfigurationFiles()));

        return files;
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        File printMappingFile = printMapping.get().getAsFile();
        File proguardOut = printMappingFile.getParentFile();
        File printSeeds = new File(proguardOut, "seeds.txt");
        File printUsage = new File(proguardOut, "usage.txt");
        return ImmutableList.of(printMappingFile, printSeeds, printUsage);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of(
                "shrink", configuration.shrink,
                "obfuscate", configuration.obfuscate,
                "optimize", configuration.optimize);
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
    public void transform(@NonNull final TransformInvocation invocation) throws TransformException {
        // only run PROGUARD_CONCURRENCY_LIMIT proguard invocations at a time (across projects)
        try {
            if (printMapping == null) {
                throw new RuntimeException("printMapping not initialized");
            }
            File printMappingFile = printMapping.get().getAsFile();
            getWorkLimiter()
                    .limit(
                            () -> {
                                doMinification(
                                        invocation.getInputs(),
                                        invocation.getReferencedInputs(),
                                        invocation.getOutputProvider());

                                // make sure the mapping file is always created. Since the file is
                                // always published as
                                // an artifact, it's important that it is always present even if
                                // empty so that it
                                // can be published to a repo.
                                if (!printMappingFile.isFile()) {
                                    Files.asCharSink(printMappingFile, Charsets.UTF_8).write("");
                                }
                                return null;
                            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void applyRuleFile(
            @NonNull String jarName, @NonNull String ruleFileName, @NonNull String rules) {
        try {
            applyConfigurationText(rules, jarName + File.separator + ruleFileName);
        } catch (IOException | ParseException ex) {
            throw new UncheckedIOException(
                    "Failed to apply proguard rules for '" + ruleFileName + "' in '" + jarName, ex);
        }
    }

    private void doMinification(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider output)
            throws IOException {
        try {
            checkNotNull(output, "Missing output object for transform " + getName());
            Set<ContentType> outputTypes = getOutputTypes();
            Set<? super Scope> scopes = getScopes();
            File outFile =
                    output.getContentLocation(
                            "combined_res_and_classes", outputTypes, scopes, Format.JAR);
            mkdirs(outFile.getParentFile());

            // set the mapping file if there is one.
            File testedMappingFile = computeMappingFile();
            if (testedMappingFile != null) {
                applyMapping(testedMappingFile);
            }

            // --- InJars / LibraryJars ---
            addInputsToConfiguration(inputs, false);
            addInputsToConfiguration(referencedInputs, true);

            // libraryJars: the runtime jars, with all optional libraries.
            variantScope.getBootClasspath().forEach(this::libraryJar);
            variantScope.getGlobalScope().getFullBootClasspath().forEach(this::libraryJar);

            // --- Out files ---
            outJar(outFile);

            // proguard doesn't verify that the seed/mapping/usage folders exist and will fail
            // if they don't so create them.
            File printMappingFile = printMapping.get().getAsFile();
            File proguardOut = printMappingFile.getParentFile();
            FileUtils.cleanOutputDir(proguardOut);

            for (File configFile : getAllConfigurationFiles()) {
                LOG.info("Applying ProGuard configuration file {}", configFile);
                applyConfigurationFile(configFile);
            }

            File printSeeds = new File(proguardOut, "seeds.txt");
            File printUsage = new File(proguardOut, "usage.txt");
            configuration.printMapping = printMappingFile;
            configuration.printSeeds = printSeeds;
            configuration.printUsage = printUsage;

            forceprocessing();
            runProguard();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException(e);
        }
    }

    private void addInputsToConfiguration(
            @NonNull Collection<TransformInput> inputs, boolean referencedOnly) {
        ClassPath classPath;
        List<String> baseFilter;

        if (referencedOnly) {
            classPath = configuration.libraryJars;
            baseFilter = JAR_FILTER;
        } else {
            classPath = configuration.programJars;
            baseFilter = null;
        }

        for (TransformInput transformInput : inputs) {
            for (JarInput jarInput : transformInput.getJarInputs()) {
                handleQualifiedContent(classPath, jarInput, baseFilter);
            }

            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                handleQualifiedContent(classPath, directoryInput, baseFilter);
            }
        }
    }

    private void handleQualifiedContent(
            @NonNull ClassPath classPath,
            @NonNull QualifiedContent content,
            @Nullable List<String> baseFilter) {
        List<String> filter = baseFilter;

        if (!content.getContentTypes().contains(DefaultContentType.CLASSES)) {
            // if the content is not meant to contain classes, we ignore them
            // in case they are present.
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            if (filter != null) {
                builder.addAll(filter);
            }
            builder.add("!**.class");
            filter = builder.build();
        } else if (!content.getContentTypes().contains(DefaultContentType.RESOURCES)) {
            // if the content is not meant to contain resources, we ignore them
            // in case they are present (by accepting only classes.)
            filter = ImmutableList.of("**.class");
        }

        inputJar(classPath, content.getFile(), filter);
    }

    @Nullable
    private File computeMappingFile() {
        if (testedMappingFile != null && testedMappingFile.isFile()) {
            return testedMappingFile;
        } else if (testMappingConfiguration != null
                && testMappingConfiguration.getSingleFile().isFile()) {
            return testMappingConfiguration.getSingleFile();
        }

        return null;
    }

    @Override
    public void keep(@NonNull String keep) {
        if (configuration.keep == null) {
            configuration.keep = Lists.newArrayList();
        }

        ClassSpecification classSpecification;
        try {
            ConfigurationParser parser = new ConfigurationParser(new String[] {keep}, null);
            classSpecification = parser.parseClassSpecificationArguments();
        } catch (IOException e) {
            // No IO happens when parsing in-memory strings.
            throw new AssertionError(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        //noinspection unchecked
        configuration.keep.add(
                new KeepClassSpecification(
                        true, false, false, false, false, false, false, null, classSpecification));
    }

    public void runProguard() throws IOException {
        new ProGuard(configuration).execute();
        fileToFilter.clear();
    }

    @Override
    public void keepattributes() {
        configuration.keepAttributes = Lists.newArrayListWithExpectedSize(0);
    }

    @Override
    public void dontwarn(@NonNull String dontwarn) {
        if (configuration.warn == null) {
            configuration.warn = Lists.newArrayList();
        }

        dontwarn = ClassUtil.internalClassName(dontwarn);

        //noinspection unchecked
        configuration.warn.addAll(ListUtil.commaSeparatedList(dontwarn));
    }

    @Override
    public void setActions(@NonNull PostprocessingFeatures actions) {
        configuration.obfuscate = actions.isObfuscate();
        configuration.optimize = actions.isOptimize();
        configuration.shrink = actions.isRemoveUnusedCode();
    }

    public void forceprocessing() {
        configuration.lastModified = Long.MAX_VALUE;
    }

    public void applyConfigurationFile(@NonNull File file) throws IOException, ParseException {
        // file might not actually exist if it comes from a sub-module library where publication
        // happen whether the file is there or not.
        if (!file.isFile()) {
            return;
        }

        applyConfigurationText(
                Files.asCharSource(file, Charsets.UTF_8).read(),
                fileDescription(file.getPath()),
                file.getParentFile());
    }

    private void applyConfigurationText(@NonNull String lines, String description, File baseDir)
            throws IOException, ParseException {
        ConfigurationParser parser =
                new ConfigurationParser(lines, description, baseDir, System.getProperties());
        try {
            parser.parse(configuration);
        } finally {
            parser.close();
        }
    }

    protected void applyConfigurationText(@NonNull String lines, String fileName)
            throws IOException, ParseException {
        applyConfigurationText(lines, fileDescription(fileName), null);
    }

    protected void applyMapping(@NonNull File testedMappingFile) {
        configuration.applyMapping = testedMappingFile;
    }

    protected void outJar(@NonNull File file) {
        ClassPathEntry classPathEntry = new ClassPathEntry(file, true /*output*/);
        configuration.programJars.add(classPathEntry);
    }

    protected void libraryJar(@NonNull File jarFile) {
        inputJar(configuration.libraryJars, jarFile, null);
    }

    protected void inputJar(
            @NonNull ClassPath classPath, @NonNull File file, @Nullable List<String> filter) {

        if (!file.exists() || fileToFilter.containsEntry(file, filter)) {
            return;
        }

        fileToFilter.put(file, filter);

        ClassPathEntry classPathEntry = new ClassPathEntry(file, false /*output*/);

        if (filter != null) {
            classPathEntry.setFilter(filter);
        }

        classPath.add(classPathEntry);
    }
}
