/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_ANDROID_TEST;
import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_MAIN;
import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_UNIT_TEST;
import static com.android.utils.ImmutableCollectors.toImmutableList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.AndroidProjectTypes;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.LintOptions;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.android.ide.common.gradle.model.IdeAaptOptions;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeApiVersion;
import com.android.ide.common.gradle.model.IdeBuildType;
import com.android.ide.common.gradle.model.IdeBuildTypeContainer;
import com.android.ide.common.gradle.model.IdeClassField;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeJavaArtifact;
import com.android.ide.common.gradle.model.IdeJavaCompileOptions;
import com.android.ide.common.gradle.model.IdeLibrary;
import com.android.ide.common.gradle.model.IdeLintOptions;
import com.android.ide.common.gradle.model.IdeProductFlavor;
import com.android.ide.common.gradle.model.IdeProductFlavorContainer;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.android.ide.common.gradle.model.IdeSourceProviderContainer;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions;
import com.android.ide.common.gradle.model.IdeViewBindingOptions;
import com.android.ide.common.gradle.model.impl.IdeAndroidLibrary;
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryCore;
import com.android.ide.common.gradle.model.impl.IdeDependenciesImpl;
import com.android.ide.common.gradle.model.impl.IdeJavaLibrary;
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryCore;
import com.android.ide.common.gradle.model.impl.IdeLibraryDelegate;
import com.android.ide.common.gradle.model.impl.IdeLintOptionsImpl;
import com.android.ide.common.gradle.model.impl.IdeModuleLibrary;
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryCore;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.model.LintModelFactory;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import junit.framework.TestCase;
import kotlin.text.StringsKt;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * A utility class which builds mocks for the Gradle builder-model API, by loosely interpreting
 * .gradle files and building models based on recognizing common patterns there.
 *
 * <p>TODO: Clean way to configure whether build dep cache is enabled TODO: Handle scopes (test
 * dependencies etc)
 */
public class GradleModelMocker {
    /**
     * Extension to {@link AndroidProjectTypes} for non-Android project types, consumed in {@link
     * LintModelFactory}
     */
    public static final int PROJECT_TYPE_JAVA_LIBRARY = 999;

    private static Pattern configurationPattern =
            Pattern.compile(
                    "^dependencies\\.(|test|androidTest)([Cc]ompile|[Ii]mplementation)[ (].*");

    private IdeAndroidProject project;
    private IdeVariant variant;

    private List<IdeVariant> variants = new ArrayList<IdeVariant>();
    private GlobalLibraryMap globalLibraryMap;
    private final Map<IdeLibrary, IdeLibrary> libraryMocks = new HashMap<>();
    private final List<IdeBuildType> buildTypes = Lists.newArrayList();
    private final List<IdeLibrary> androidLibraries = Lists.newArrayList();
    private final List<IdeLibrary> javaLibraries = Lists.newArrayList();
    private final List<IdeLibrary> moduleLibraries = Lists.newArrayList();
    private final List<IdeLibrary> testAndroidLibraries = Lists.newArrayList();
    private final List<IdeLibrary> testJavaLibraries = Lists.newArrayList();
    private final List<IdeLibrary> testModuleLibraries = Lists.newArrayList();
    private final List<IdeLibrary> androidTestAndroidLibraries = Lists.newArrayList();
    private final List<IdeLibrary> androidTestJavaLibraries = Lists.newArrayList();
    private final List<IdeLibrary> androidTestModuleLibraries = Lists.newArrayList();
    private final List<IdeLibrary> allJavaLibraries = Lists.newArrayList();
    private IdeProductFlavor mergedFlavor;
    private IdeProductFlavor defaultFlavor;
    private IdeLintOptions lintOptions;
    private final HashMap<String, Integer> severityOverrides = new HashMap();
    private final LintCliFlags flags = new LintCliFlags();
    private File projectDir = new File("");
    private final List<IdeProductFlavor> productFlavors = Lists.newArrayList();
    private final Multimap<String, String> splits = ArrayListMultimap.create();
    private ILogger logger;
    private boolean initialized;

    private final Map<String, String> ext = new HashMap<>();

    @Language("Groovy")
    private final String gradle;

    private GradleVersion modelVersion = GradleVersion.parse("2.2.2");
    private final Map<String, Dep> graphs = Maps.newHashMap();
    private boolean useBuildCache;
    private IdeVectorDrawablesOptions vectorDrawablesOptions;
    private IdeAaptOptions aaptOptions;
    private boolean allowUnrecognizedConstructs;
    private boolean fullDependencies;
    private IdeJavaCompileOptions compileOptions;
    private boolean javaPlugin;
    private boolean javaLibraryPlugin;

    public GradleModelMocker(@Language("Groovy") String gradle) {
        this.gradle = gradle;
        this.flags.setSeverityOverrides(new HashMap<>());
    }

    @NonNull
    public GradleModelMocker withLogger(@Nullable ILogger logger) {
        this.logger = logger;
        return this;
    }

    @NonNull
    public GradleModelMocker withModelVersion(@NonNull String modelVersion) {
        this.modelVersion = GradleVersion.parse(modelVersion);
        return this;
    }

    @NonNull
    public GradleModelMocker withProjectDir(@NonNull File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    @NonNull
    public GradleModelMocker withDependencyGraph(@NonNull String graph) {
        parseDependencyGraph(graph, graphs);
        return this;
    }

    public GradleModelMocker allowUnrecognizedConstructs() {
        this.allowUnrecognizedConstructs = true;
        return this;
    }

    public GradleModelMocker withBuildCache(boolean useBuildCache) {
        this.useBuildCache = useBuildCache;
        return this;
    }

    /**
     * If true, model a full/deep dependency graph in {@link
     * com.android.builder.model.level2.DependencyGraphs}; the default is flat. (This is normally
     * controlled by sync/model builder flag {@link
     * AndroidProject#PROPERTY_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES}.)
     */
    public GradleModelMocker withFullDependencies(boolean fullDependencies) {
        this.fullDependencies = fullDependencies;
        return this;
    }

    private void warn(String message) {
        if (!allowUnrecognizedConstructs) {
            error(message);
            return;
        }

        if (logger != null) {
            logger.warning(message);
        } else {
            System.err.println(message);
        }
    }

    private void error(String message) {
        if (logger != null) {
            logger.error(null, message);
        } else {
            System.err.println(message);
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialized = true;
            initialize();
        }
    }

    /** Whether the Gradle file applied the java plugin */
    public boolean hasJavaPlugin() {
        return javaPlugin;
    }

    /** Whether the Gradle file applied the java-library plugin */
    public boolean hasJavaLibraryPlugin() {
        return javaLibraryPlugin;
    }

    public boolean isLibrary() {
        return project.getProjectType() == AndroidProjectTypes.PROJECT_TYPE_LIBRARY
                || project.getProjectType() == PROJECT_TYPE_JAVA_LIBRARY;
    }

    /** Whether the Gradle file applied the java-library plugin */
    public boolean hasAndroidLibraryPlugin() {
        return javaLibraryPlugin;
    }

    public IdeAndroidProject getProject() {
        ensureInitialized();
        return project;
    }

    public IdeVariant getVariant() {
        ensureInitialized();
        return variant;
    }

    public Collection<IdeVariant> getVariants() {
        ensureInitialized();
        return variants;
    }

    @Nullable
    public GlobalLibraryMap getGlobalLibraryMap() {
        ensureInitialized();
        return globalLibraryMap;
    }

    public void syncFlagsTo(LintCliFlags to) {
        ensureInitialized();
        to.getSuppressedIds().clear();
        to.getSuppressedIds().addAll(flags.getSuppressedIds());
        to.getEnabledIds().clear();
        to.getEnabledIds().addAll(flags.getEnabledIds());
        to.setExactCheckedIds(flags.getExactCheckedIds());
        to.setSetExitCode(flags.isSetExitCode());
        to.setFullPath(flags.isFullPath());
        to.setShowSourceLines(flags.isShowSourceLines());
        to.setQuiet(flags.isQuiet());
        to.setCheckAllWarnings(flags.isCheckAllWarnings());
        to.setIgnoreWarnings(flags.isIgnoreWarnings());
        to.setWarningsAsErrors(flags.isWarningsAsErrors());
        to.setCheckTestSources(flags.isCheckTestSources());
        to.setCheckDependencies(flags.isCheckDependencies());
        to.setCheckGeneratedSources(flags.isCheckGeneratedSources());
        to.setShowEverything(flags.isShowEverything());
        to.setDefaultConfiguration(flags.getDefaultConfiguration());
        to.setExplainIssues(flags.isExplainIssues());
        to.setBaselineFile(flags.getBaselineFile());
        ;
    }

    private void initialize() {
        project = mock(IdeAndroidProject.class);

        Map<String, Object> clientProperties = new HashMap<>();
        when(project.getClientProperty(anyString()))
                .thenAnswer(
                        invocation -> {
                            String key = invocation.getArgument(0);
                            return clientProperties.get(key);
                        });
        when(project.putClientProperty(anyString(), any()))
                .thenAnswer(
                        (Answer<Void>)
                                invocation -> {
                                    String key = invocation.getArgument(0);
                                    Object value = invocation.getArgument(1);
                                    clientProperties.put(key, value);
                                    return null;
                                });

        when(project.getModelVersion()).thenReturn(modelVersion.toString());
        int apiVersion = modelVersion.getMajor() >= 2 ? 3 : 2;
        when(project.getApiVersion()).thenReturn(apiVersion);
        when(project.getFlavorDimensions()).thenReturn(Lists.newArrayList());
        when(project.getName()).thenReturn("test_project");
        when(project.getCompileTarget()).thenReturn("android-" + SdkVersionInfo.HIGHEST_KNOWN_API);

        variant = mock(IdeVariant.class);

        lintOptions = new IdeLintOptionsImpl();
        when(project.getLintOptions()).thenAnswer(invocation -> lintOptions);

        compileOptions = mock(IdeJavaCompileOptions.class);
        when(compileOptions.getSourceCompatibility()).thenReturn("1.7");
        when(compileOptions.getTargetCompatibility()).thenReturn("1.7");
        when(compileOptions.getEncoding()).thenReturn("UTF-8");
        when(project.getJavaCompileOptions()).thenReturn(compileOptions);

        // built-in build-types
        getBuildType("debug", true);
        getBuildType("release", true);

        defaultFlavor = getProductFlavor("defaultConfig", true);
        when(defaultFlavor.getVersionCode())
                .thenReturn(null); // don't default to Integer.valueOf(0) !

        IdeDependencies dependencies = mock(IdeDependencies.class);
        IdeDependencies testDependencies = mock(IdeDependencies.class);
        IdeDependencies androidTestDependencies = mock(IdeDependencies.class);

        when(dependencies.getAndroidLibraries()).thenReturn(androidLibraries);

        addLocalLibs(new File(projectDir, "libs"));

        when(testDependencies.getAndroidLibraries()).thenReturn(testAndroidLibraries);
        when(androidTestDependencies.getAndroidLibraries()).thenReturn(androidTestAndroidLibraries);

        when(dependencies.getJavaLibraries()).thenReturn(javaLibraries);
        when(testDependencies.getJavaLibraries()).thenReturn(testJavaLibraries);
        when(androidTestDependencies.getJavaLibraries()).thenReturn(androidTestJavaLibraries);

        when(dependencies.getModuleDependencies()).thenReturn(moduleLibraries);
        when(testDependencies.getModuleDependencies()).thenReturn(testModuleLibraries);
        when(androidTestDependencies.getModuleDependencies())
                .thenReturn(androidTestModuleLibraries);

        mergedFlavor = getProductFlavor("mergedFlavor", true);
        productFlavors.remove(mergedFlavor); // create mock but don't store as a separate flavor
        when(variant.getMergedFlavor()).thenReturn(mergedFlavor);
        getVectorDrawableOptions(); // ensure initialized
        getAaptOptions(); // ensure initialized

        scan(gradle, "");

        List<IdeBuildTypeContainer> containers = Lists.newArrayList();
        for (IdeBuildType buildType : buildTypes) {
            IdeBuildTypeContainer container = mock(IdeBuildTypeContainer.class);
            when(container.getBuildType()).thenReturn(buildType);
            containers.add(container);

            IdeSourceProvider provider = createSourceProvider(projectDir, buildType.getName());
            when(container.getSourceProvider()).thenReturn(provider);
        }

        when(project.getBuildTypes()).thenReturn(containers);
        IdeProductFlavorContainer defaultContainer = mock(IdeProductFlavorContainer.class);
        when(defaultContainer.getProductFlavor()).thenReturn(defaultFlavor);
        when(defaultContainer.toString()).thenReturn("defaultConfig");
        when(project.getDefaultConfig()).thenReturn(defaultContainer);
        IdeSourceProvider mainProvider = createSourceProvider(projectDir, "main");
        when(defaultContainer.getSourceProvider()).thenReturn(mainProvider);

        IdeSourceProviderContainer androidTestProvider = mock(IdeSourceProviderContainer.class);
        when(androidTestProvider.getArtifactName())
                .thenReturn(AndroidProject.ARTIFACT_ANDROID_TEST);
        IdeSourceProvider androidSourceProvider = createSourceProvider(projectDir, "androidTest");
        when(androidTestProvider.getSourceProvider()).thenReturn(androidSourceProvider);
        IdeSourceProviderContainer unitTestProvider = mock(IdeSourceProviderContainer.class);
        when(unitTestProvider.getArtifactName()).thenReturn(AndroidProject.ARTIFACT_UNIT_TEST);
        IdeSourceProvider unitSourceProvider = createSourceProvider(projectDir, "test");
        when(unitTestProvider.getSourceProvider()).thenReturn(unitSourceProvider);

        List<IdeSourceProviderContainer> extraProviders =
                Lists.newArrayList(androidTestProvider, unitTestProvider);
        when(defaultContainer.getExtraSourceProviders()).thenReturn(extraProviders);

        List<IdeProductFlavorContainer> flavorContainers = Lists.newArrayList();
        flavorContainers.add(defaultContainer);
        for (IdeProductFlavor flavor : productFlavors) {
            if (flavor == defaultFlavor) {
                continue;
            }
            IdeProductFlavorContainer container = mock(IdeProductFlavorContainer.class);
            String flavorName = flavor.getName();
            IdeSourceProvider flavorSourceProvider = createSourceProvider(projectDir, flavorName);
            when(container.getSourceProvider()).thenReturn(flavorSourceProvider);
            when(container.getProductFlavor()).thenReturn(flavor);
            when(container.toString()).thenReturn(flavorName);
            flavorContainers.add(container);
        }
        when(project.getProductFlavors()).thenReturn(flavorContainers);

        // Artifacts
        IdeAndroidArtifact artifact = mock(IdeAndroidArtifact.class);
        IdeJavaArtifact testArtifact = mock(IdeJavaArtifact.class);
        IdeAndroidArtifact androidTestArtifact = mock(IdeAndroidArtifact.class);

        String applicationId = project.getDefaultConfig().getProductFlavor().getApplicationId();
        if (applicationId == null) {
            applicationId = "test.pkg";
        }
        when(artifact.getApplicationId()).thenReturn(applicationId);
        when(androidTestArtifact.getApplicationId()).thenReturn(applicationId);

        List<IdeJavaArtifact> extraJavaArtifacts = Collections.singletonList(testArtifact);
        List<IdeAndroidArtifact> extraAndroidArtifacts =
                Collections.singletonList(androidTestArtifact);

        //noinspection deprecation
        when(artifact.getLevel2Dependencies()).thenReturn(dependencies);
        when(testArtifact.getLevel2Dependencies()).thenReturn(testDependencies);
        when(androidTestArtifact.getLevel2Dependencies()).thenReturn(androidTestDependencies);

        when(variant.getMainArtifact()).thenReturn(artifact);
        when(variant.getExtraJavaArtifacts()).thenReturn(extraJavaArtifacts);
        when(variant.getExtraAndroidArtifacts()).thenReturn(extraAndroidArtifacts);

        /*
        if (modelVersion.isAtLeast(2, 5, 0, "alpha", 1, false)) {
            DependencyGraphs graphs = createDependencyGraphs();
            when(artifact.getDependencyGraphs()).thenReturn(graphs);
        } else {
            // Should really throw org.gradle.tooling.model.UnsupportedMethodException here!
            when(artifact.getDependencyGraphs()).thenThrow(new RuntimeException());
        }
        */

        when(project.getBuildFolder()).thenReturn(new File(projectDir, "build"));

        List<IdeAndroidArtifactOutput> outputs = Lists.newArrayList();
        outputs.add(createAndroidArtifactOutput("", ""));
        for (Map.Entry<String, String> entry : splits.entries()) {
            outputs.add(createAndroidArtifactOutput(entry.getKey(), entry.getValue()));
        }
        // outputs.add(createAndroidArtifactOutput("DENSITY", "mdpi"));
        // outputs.add(createAndroidArtifactOutput("DENSITY", "hdpi"));
        when(artifact.getOutputs()).thenReturn(outputs);

        Set<String> seenDimensions = Sets.newHashSet();
        IdeBuildType defaultBuildType = buildTypes.get(0);
        String defaultBuildTypeName = defaultBuildType.getName();
        StringBuilder variantNameSb = new StringBuilder();
        Collection<String> flavorDimensions = project.getFlavorDimensions();
        for (String dimension : flavorDimensions) {
            for (IdeProductFlavor flavor : productFlavors) {
                if (flavor != defaultFlavor && dimension.equals(flavor.getDimension())) {
                    if (seenDimensions.contains(dimension)) {
                        continue;
                    }
                    seenDimensions.add(dimension);
                    String name = flavor.getName();
                    if (variantNameSb.length() == 0) {
                        variantNameSb.append(name);
                    } else {
                        variantNameSb.append(StringsKt.capitalize(name));
                    }
                }
            }
        }
        for (IdeProductFlavor flavor : productFlavors) {
            if (flavor != defaultFlavor && flavor.getDimension() == null) {
                String name = flavor.getName();
                if (variantNameSb.length() == 0) {
                    variantNameSb.append(name);
                } else {
                    variantNameSb.append(StringsKt.capitalize(name));
                }
                break;
            }
        }

        if (flavorContainers.size() >= 2) {
            IdeSourceProvider multiVariantSourceSet =
                    createSourceProvider(projectDir, variantNameSb.toString());
            when(artifact.getMultiFlavorSourceProvider()).thenReturn(multiVariantSourceSet);
        }
        if (variantNameSb.length() == 0) {
            variantNameSb.append(defaultBuildTypeName);
        } else {
            variantNameSb.append(StringsKt.capitalize(defaultBuildTypeName));
        }
        String defaultVariantName = variantNameSb.toString();
        if (productFlavors.isEmpty()) {
            IdeSourceProvider variantSourceSet =
                    createSourceProvider(projectDir, defaultVariantName);
            when(artifact.getVariantSourceProvider()).thenReturn(variantSourceSet);
        }
        setVariantName(defaultVariantName);

        when(artifact.getName()).thenReturn(ARTIFACT_NAME_MAIN);
        when(testArtifact.getName()).thenReturn(ARTIFACT_NAME_UNIT_TEST);
        when(androidTestArtifact.getName()).thenReturn(ARTIFACT_NAME_ANDROID_TEST);
        when(artifact.getClassesFolder())
                .thenReturn(
                        new File(
                                projectDir,
                                "build/intermediates/javac/" + defaultVariantName + "/classes"));
        when(artifact.getAdditionalClassesFolders())
                .thenReturn(
                        Collections.singleton(
                                new File(
                                        projectDir,
                                        "build/tmp/kotlin-classes/" + defaultVariantName)));
        when(testArtifact.getClassesFolder()).thenReturn(new File(projectDir, "test-classes"));
        when(androidTestArtifact.getClassesFolder())
                .thenReturn(new File(projectDir, "instrumentation-classes"));

        // Generated sources: Special test support under folder "generated" instead of "src"
        File generated = new File(projectDir, "generated");
        if (generated.exists()) {
            File generatedRes = new File(generated, "res");
            if (generatedRes.exists()) {
                List<File> generatedResources = Collections.singletonList(generatedRes);
                when(artifact.getGeneratedResourceFolders()).thenReturn(generatedResources);
            }

            File generatedJava = new File(generated, "java");
            if (generatedJava.exists()) {
                List<File> generatedSources = Collections.singletonList(generatedJava);
                when(artifact.getGeneratedSourceFolders()).thenReturn(generatedSources);
            }
        }

        // Merge values into mergedFlavor
        IdeApiVersion minSdkVersion = defaultFlavor.getMinSdkVersion();
        IdeApiVersion targetSdkVersion = defaultFlavor.getTargetSdkVersion();
        Integer versionCode = defaultFlavor.getVersionCode();
        String versionName = defaultFlavor.getVersionName();
        Map<String, String> manifestPlaceholders =
                new HashMap<>(defaultFlavor.getManifestPlaceholders());
        Map<String, IdeClassField> resValues = new HashMap<>(defaultFlavor.getResValues());
        Collection<String> resourceConfigurations =
                new HashSet<>(defaultFlavor.getResourceConfigurations());
        for (IdeProductFlavorContainer container : flavorContainers) {
            IdeProductFlavor flavor = container.getProductFlavor();
            manifestPlaceholders.putAll(flavor.getManifestPlaceholders());
            resValues.putAll(flavor.getResValues());
            resourceConfigurations.addAll(flavor.getResourceConfigurations());
        }
        manifestPlaceholders.putAll(defaultBuildType.getManifestPlaceholders());
        resValues.putAll(defaultBuildType.getResValues());

        when(mergedFlavor.getMinSdkVersion()).thenReturn(minSdkVersion);
        when(mergedFlavor.getTargetSdkVersion()).thenReturn(targetSdkVersion);
        when(mergedFlavor.getApplicationId()).thenReturn(applicationId);
        when(mergedFlavor.getVersionCode()).thenReturn(versionCode);
        when(mergedFlavor.getVersionName()).thenReturn(versionName);
        when(mergedFlavor.getManifestPlaceholders()).thenReturn(manifestPlaceholders);
        when(mergedFlavor.getResValues()).thenReturn(resValues);
        when(mergedFlavor.getResourceConfigurations()).thenReturn(resourceConfigurations);

        // Attempt to make additional variants?
        variants.add(variant);
        for (IdeBuildType buildType : buildTypes) {
            String buildTypeName = buildType.getName();

            for (IdeProductFlavor flavor : productFlavors) {
                if (flavor == defaultFlavor) {
                    continue;
                }
                String variantName = flavor.getName() + StringsKt.capitalize(buildType.getName());
                System.out.println();
                if (!variantName.equals(variant.getName())) {
                    IdeVariant newVariant = mock(IdeVariant.class, Mockito.RETURNS_SMART_NULLS);
                    when(newVariant.getName()).thenReturn(variantName);
                    when(newVariant.getBuildType()).thenReturn(buildTypeName);
                    List<String> productFlavorNames = Collections.singletonList(flavor.getName());
                    when(newVariant.getProductFlavors()).thenReturn(productFlavorNames);

                    when(mergedFlavor.getApplicationId()).thenReturn(applicationId);
                    minSdkVersion = mergedFlavor.getMinSdkVersion();
                    targetSdkVersion = mergedFlavor.getTargetSdkVersion();
                    String flavorName = mergedFlavor.getName();
                    IdeVectorDrawablesOptions vectorDrawables = mergedFlavor.getVectorDrawables();

                    IdeProductFlavor variantFlavor = mock(IdeProductFlavor.class);
                    when(variantFlavor.getMinSdkVersion()).thenReturn(minSdkVersion);
                    when(variantFlavor.getTargetSdkVersion()).thenReturn(targetSdkVersion);
                    when(variantFlavor.getName()).thenReturn(flavorName);
                    when(variantFlavor.getVectorDrawables()).thenReturn(vectorDrawables);
                    when(variantFlavor.getResourceConfigurations())
                            .thenReturn(Collections.emptyList());
                    when(variantFlavor.getResValues()).thenReturn(Collections.emptyMap());
                    when(variantFlavor.getManifestPlaceholders())
                            .thenReturn(Collections.emptyMap());

                    when(newVariant.getMergedFlavor()).thenReturn(variantFlavor);

                    IdeAndroidArtifact mainArtifact = variant.getMainArtifact();
                    when(newVariant.getMainArtifact()).thenReturn(mainArtifact);

                    // Customize artifacts instead of just pointing to the main one
                    // to avoid really redundant long dependency lists
                    artifact = mock(IdeAndroidArtifact.class);
                    when(artifact.getName()).thenReturn(ARTIFACT_NAME_MAIN);
                    when(artifact.getClassesFolder())
                            .thenReturn(
                                    new File(
                                            projectDir,
                                            "build/intermediates/javac/"
                                                    + variantName
                                                    + "/classes"));
                    when(artifact.getAdditionalClassesFolders())
                            .thenReturn(
                                    Collections.singleton(
                                            new File(
                                                    projectDir,
                                                    "build/tmp/kotlin-classes/" + variantName)));
                    when(artifact.getApplicationId()).thenReturn(applicationId);
                    dependencies = mock(IdeDependencies.class);
                    when(dependencies.getAndroidLibraries()).thenReturn(Collections.emptyList());
                    when(artifact.getLevel2Dependencies()).thenReturn(dependencies);
                    when(newVariant.getMainArtifact()).thenReturn(artifact);
                    when(newVariant.getExtraJavaArtifacts()).thenReturn(Collections.emptyList());
                    when(newVariant.getExtraAndroidArtifacts()).thenReturn(Collections.emptyList());

                    variants.add(newVariant);
                }
            }
        }
    }

    private static int libraryVersion = 0;

    private void addLocalLibs(File libsDir) {
        File[] libs = libsDir.listFiles();
        if (libs != null) {
            for (File lib : libs) {
                if (lib.isDirectory()) {
                    addLocalLibs(lib);
                } else {
                    String path = lib.getPath();
                    if (path.endsWith(DOT_JAR)) {
                        String name = StringsKt.removeSuffix(lib.getName(), DOT_JAR);
                        String coordinateString = "locallibs:" + name + ":" + libraryVersion++;

                        // See if this might be an Android library instead of a Java library
                        int index = path.indexOf("exploded-aar");
                        if (index != -1) {
                            int jars = path.indexOf("jars");
                            if (jars != -1) {
                                coordinateString =
                                        path.substring(index + 13, jars - 1)
                                                .replace("/", ":")
                                                .replace("\\", ":");
                                IdeLibrary library =
                                        createAndroidLibrary(coordinateString, null, false, lib);
                                androidLibraries.add(library);
                                return;
                            }
                        }
                        index = path.indexOf(".aar/");
                        if (index == -1) {
                            index = path.indexOf(".aar\\");
                        }
                        if (index != -1) {
                            IdeLibrary library =
                                    createAndroidLibrary(coordinateString, null, false, lib);
                            androidLibraries.add(library);
                            return;
                        }
                        IdeLibrary library = createJavaLibrary(coordinateString, null, false, lib);
                        javaLibraries.add(library);
                    }
                }
            }
        }
    }
    /*

    @NonNull
    private DependencyGraphs createDependencyGraphs() {
        DependencyGraphs graphs = mock(DependencyGraphs.class);
        List<GraphItem> compileItems = Lists.newArrayList();
        Map<String, com.android.builder.model.level2.Library> globalMap = Maps.newHashMap();

        when(graphs.getCompileDependencies()).thenReturn(compileItems);
        when(graphs.getPackageDependencies()).thenReturn(compileItems);
        when(graphs.getProvidedLibraries()).thenReturn(Collections.emptyList());
        when(graphs.getSkippedLibraries()).thenReturn(Collections.emptyList());

        HashSet<String> seen = Sets.newHashSet();
        addGraphItems(compileItems, globalMap, seen, androidLibraries);
        addGraphItems(compileItems, globalMap, seen, javaLibraries);

        // Java libraries aren't available from the AndroidLibraries themselves;
        // stored in a separate global map during initialization
        for (JavaLibrary library : allJavaLibraries) {
            com.android.builder.model.level2.Library lib = createLevel2Library(library);
            globalMap.put(lib.getArtifactAddress(), lib);
        }

        globalLibraryMap = mock(GlobalLibraryMap.class);
        when(globalLibraryMap.getLibraries()).thenReturn(globalMap);

        return graphs;
    }
    */

    private void addGraphItems(
            List<GraphItem> result,
            Map<String, com.android.builder.model.level2.Library> globalMap,
            Set<String> seen,
            Collection<? extends Library> libraries) {
        for (Library library : libraries) {
            MavenCoordinates coordinates = library.getResolvedCoordinates();
            String name =
                    coordinates.getGroupId()
                            + ':'
                            + coordinates.getArtifactId()
                            + ':'
                            + coordinates.getVersion()
                            + '@'
                            + coordinates.getPackaging();
            if (fullDependencies || !seen.contains(name)) {
                seen.add(name);

                GraphItem item = mock(GraphItem.class);
                result.add(item);
                when(item.getArtifactAddress()).thenReturn(name);
                when(item.getRequestedCoordinates()).thenReturn(name);
                when(item.getDependencies()).thenReturn(Lists.newArrayList());

                if (library instanceof AndroidLibrary) {
                    AndroidLibrary androidLibrary = (AndroidLibrary) library;
                    addGraphItems(
                            fullDependencies ? item.getDependencies() : result,
                            globalMap,
                            seen,
                            androidLibrary.getLibraryDependencies());
                } else if (library instanceof JavaLibrary) {
                    JavaLibrary javaLibrary = (JavaLibrary) library;
                    addGraphItems(
                            fullDependencies ? item.getDependencies() : result,
                            globalMap,
                            seen,
                            javaLibrary.getDependencies());
                }
            }

            globalMap.put(name, createLevel2Library(library));
        }
    }

    @NonNull
    private com.android.builder.model.level2.Library createLevel2Library(Library library) {
        com.android.builder.model.level2.Library lib =
                mock(com.android.builder.model.level2.Library.class);

        MavenCoordinates coordinates = library.getResolvedCoordinates();
        String name =
                coordinates.getGroupId()
                        + ':'
                        + coordinates.getArtifactId()
                        + ':'
                        + coordinates.getVersion()
                        + '@'
                        + coordinates.getPackaging();
        when(lib.getArtifactAddress()).thenReturn(name);
        if (library instanceof AndroidLibrary) {
            AndroidLibrary androidLibrary = (AndroidLibrary) library;
            File folder = androidLibrary.getFolder();
            when(lib.getType())
                    .thenReturn(com.android.builder.model.level2.Library.LIBRARY_ANDROID);
            when(lib.getFolder()).thenReturn(folder);
            when(lib.getLintJar()).thenReturn("lint.jar");
            when(lib.getLocalJars()).thenReturn(Collections.emptyList());
            when(lib.getExternalAnnotations()).thenReturn(FN_ANNOTATIONS_ZIP);
            when(lib.getJarFile()).thenReturn("jars/" + FN_CLASSES_JAR);
            File jar = new File(folder, "jars/" + FN_CLASSES_JAR);
            if (!jar.exists()) {
                createEmptyJar(jar);
            }
            // when(l2.isProvided).thenReturn(androidLibrary.isProvided());
        } else if (library instanceof JavaLibrary) {
            JavaLibrary javaLibrary = (JavaLibrary) library;
            when(lib.getType()).thenReturn(com.android.builder.model.level2.Library.LIBRARY_JAVA);
            List<String> jars = Lists.newArrayList();
            when(lib.getLocalJars()).thenReturn(jars);
            File jarFile = javaLibrary.getJarFile();
            when(lib.getArtifact()).thenReturn(jarFile);
            when(lib.getFolder()).thenThrow(new UnsupportedOperationException());
        }
        return lib;
    }

    private void createEmptyJar(@NonNull File jar) {
        if (!jar.exists()) {
            File parentFile = jar.getParentFile();
            if (parentFile != null && !parentFile.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parentFile.mkdirs();
            }

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

            try (JarOutputStream jarOutputStream =
                    new JarOutputStream(
                            new BufferedOutputStream(new FileOutputStream(jar)), manifest)) {
                jarOutputStream.putNextEntry(new ZipEntry("sample.txt"));
                ByteStreams.copy(
                        new ByteArrayInputStream("Sample".getBytes(Charsets.UTF_8)),
                        jarOutputStream);
                jarOutputStream.closeEntry();
            } catch (IOException e) {
                error(e.getMessage());
            }
        }
    }

    @NonNull
    private static String normalize(@NonNull String line) {
        line = line.trim();
        int commentIndex = line.indexOf("//");
        if (commentIndex != -1) {
            line = line.substring(0, commentIndex).trim();
        }
        while (true) {
            // Strip out embedded comment markers, if any (there could be multiple)
            commentIndex = line.indexOf("/*");
            if (commentIndex == -1) {
                break;
            }
            int commentEnd = line.indexOf("*/", commentIndex + 2);
            if (commentEnd == -1) {
                break;
            }
            line = line.substring(0, commentIndex) + line.substring(commentEnd + 2);
        }

        return line.replaceAll("\\s+", " ").replace('"', '\'').replace(" = ", " ");
    }

    private static char findNonSpaceCharacterBackwards(@NonNull String s, int index) {
        int curr = index;
        while (curr > 0) {
            char c = s.charAt(curr);
            if (!Character.isWhitespace(c)) {
                return c;
            }
            curr--;
        }

        return 0;
    }

    private void scan(@Language("Groovy") String gradle, @NonNull String context) {
        int start = 0;
        int end = gradle.length();
        while (start < end) {
            // Iterate line by line, but as soon as a line has an imbalance of {}'s
            // then report the block instead
            int lineEnd = gradle.indexOf('\n', start);

            // Join comma statements
            while (true) {
                if (findNonSpaceCharacterBackwards(gradle, lineEnd) == ',') {
                    lineEnd = gradle.indexOf('\n', lineEnd + 1);
                } else {
                    if (lineEnd == -1) {
                        lineEnd = end;
                    }
                    break;
                }
            }

            int balance = 0;
            for (int i = start; i < lineEnd; i++) {
                char c = gradle.charAt(i);
                if (c == '{') {
                    balance++;
                } else if (c == '}') {
                    balance--;
                }
            }

            if (balance == 0) {
                String line = gradle.substring(start, lineEnd).trim();
                int index = line.indexOf('{');
                if (line.endsWith("}") && index != -1) {
                    // Single line block?
                    String name = line.substring(0, index).trim();
                    @Language("Groovy")
                    String blockBody = line.substring(index + 1, line.length() - 1);
                    block(name, blockBody, context);
                } else {
                    line(line, context);
                }
                start = lineEnd + 1;
            } else {
                // Find end of block
                int nameEnd = gradle.indexOf('{', start);
                String name = gradle.substring(start, nameEnd).trim();
                start = lineEnd + 1;
                for (int i = lineEnd; i < end; i++) {
                    char c = gradle.charAt(i);
                    if (c == '{') {
                        balance++;
                    } else if (c == '}') {
                        balance--;
                        if (balance == 0) {
                            // Found the end
                            @Language("Groovy")
                            String block = gradle.substring(nameEnd + 1, i);
                            block(name, block, context);
                            start = i + 1;
                            break;
                        }
                    }
                }
            }
        }
    }

    @NonNull
    private String getUnquotedValue(String key) {
        String value = key;
        int index = key.indexOf('\'');
        if (index != -1) {
            value = key.substring(index + 1, key.indexOf('\'', index + 1));
        } else if ((index = key.indexOf('"')) != -1) {
            value = key.substring(index + 1, key.indexOf('"', index + 1));
        } else if ((index = key.indexOf('=')) != -1) {
            value = key.substring(index + 1);
        } else if ((index = key.indexOf(' ')) != -1) {
            value = key.substring(index + 1);
        }
        return value.indexOf('$') == -1 ? value : doInterpolations(value);
    }

    @NonNull
    private String doInterpolations(String value) {
        StringBuilder sb = new StringBuilder();
        int lastIndex = 0;
        int index;
        while ((index = value.indexOf('$', lastIndex)) != -1) {
            sb.append(value, lastIndex, index);
            int end = value.indexOf(' ', index);
            if (end == -1) end = value.length();
            String name = value.substring(index + 1, end);
            if (ext.containsKey(name)) {
                sb.append(ext.get(name));
            } else {
                sb.append("$" + name);
            }
            lastIndex = end;
        }
        sb.append(value, lastIndex, value.length());
        return sb.toString();
    }

    private void line(@NonNull String line, @NonNull String context) {
        line = normalize(line);
        if (line.isEmpty()) {
            return;
        }

        if (line.equals("apply plugin: 'com.android.library'")
                || line.equals("apply plugin: 'android-library'")) {
            when(project.getProjectType()).thenReturn(AndroidProjectTypes.PROJECT_TYPE_LIBRARY);
            return;
        } else if (line.equals("apply plugin: 'com.android.application'")
                || line.equals("apply plugin: 'android'")) {
            when(project.getProjectType()).thenReturn(AndroidProjectTypes.PROJECT_TYPE_APP);
            return;
        } else if (line.equals("apply plugin: 'com.android.feature'")) {
            when(project.getProjectType()).thenReturn(AndroidProjectTypes.PROJECT_TYPE_FEATURE);
            return;
        } else if (line.equals("apply plugin: 'com.android.instantapp'")) {
            when(project.getProjectType()).thenReturn(AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP);
            return;
        } else if (line.equals("apply plugin: 'java'")) {
            when(project.getProjectType()).thenReturn(PROJECT_TYPE_JAVA_LIBRARY);
            javaPlugin = true;
            return;
        } else if (line.equals("apply plugin: 'java-library'")) {
            when(project.getProjectType()).thenReturn(PROJECT_TYPE_JAVA_LIBRARY);
            javaLibraryPlugin = true;
            return;
        } else if (context.equals("buildscript.repositories")
                || context.equals("allprojects.repositories")) {
            // Plugins not modeled in the builder model
            return;
        } else if (line.startsWith("apply plugin: ")) {
            // Some other plugin not relevant to the builder-model
            return;
        }

        String key = context.isEmpty() ? line : context + "." + line;
        Matcher m = configurationPattern.matcher(key);
        if (key.startsWith("ext.")) {
            String name = key.substring(4, key.indexOf(' '));
            ext.put(name, getUnquotedValue(key));
        } else if (m.matches()) {
            String artifactName = m.group(1);
            String declaration = getUnquotedValue(key);
            if (GradleCoordinate.parseCoordinateString(declaration) != null) {
                addDependency(declaration, artifactName, false);
                return;
            } else {
                // Group/artifact/version syntax?
                if (line.contains("group:")
                        && line.contains("name:")
                        && line.contains("version:")) {
                    String group = null;
                    String artifact = null;
                    String version = null;
                    for (String part :
                            Splitter.on(',')
                                    .trimResults()
                                    .omitEmptyStrings()
                                    .split(line.substring(line.indexOf(' ') + 1))) {
                        if (part.startsWith("group:")) {
                            group = getUnquotedValue(part);
                        } else if (part.startsWith("name:")) {
                            artifact = getUnquotedValue(part);
                        } else if (part.startsWith("version:")) {
                            version = getUnquotedValue(part);
                        }
                    }
                    if (group != null && artifact != null && version != null) {
                        declaration = group + ':' + artifact + ':' + version;
                        addDependency(declaration, artifactName, false);
                        return;
                    }
                }
            }
            warn("Ignored unrecognized dependency " + line);
        } else if (key.startsWith("dependencies.provided '") && key.endsWith("'")) {
            String declaration = getUnquotedValue(key);
            addDependency(declaration, null, true);
        } else if (line.startsWith("applicationId ") || line.startsWith("packageName ")) {
            String id = getUnquotedValue(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getApplicationId()).thenReturn(id);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (line.startsWith("minSdkVersion ")) {
            IdeApiVersion apiVersion = createApiVersion(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getMinSdkVersion()).thenReturn(apiVersion);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (line.startsWith("targetSdkVersion ")) {
            IdeApiVersion version = createApiVersion(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getTargetSdkVersion()).thenReturn(version);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (line.startsWith("versionCode ")) {
            String value = key.substring(key.indexOf(' ') + 1).trim();
            if (Character.isDigit(value.charAt(0))) {
                int number = Integer.decode(value);
                IdeProductFlavor flavor = getFlavorFromContext(context);
                if (flavor != null) {
                    when(flavor.getVersionCode()).thenReturn(number);
                } else {
                    error("Unexpected flavor context " + context);
                }
            } else {
                warn("Ignoring unrecognized versionCode token: " + value);
            }
        } else if (line.startsWith("versionName ")) {
            String name = getUnquotedValue(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getVersionName()).thenReturn(name);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (line.startsWith("versionNameSuffix ")) {
            String name = getUnquotedValue(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getVersionNameSuffix()).thenReturn(name);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (line.startsWith("applicationIdSuffix ")) {
            String name = getUnquotedValue(key);
            IdeProductFlavor flavor = getFlavorFromContext(context);
            if (flavor != null) {
                when(flavor.getApplicationIdSuffix()).thenReturn(name);
            } else {
                error("Unexpected flavor context " + context);
            }
        } else if (key.startsWith("android.resourcePrefix ")) {
            String value = getUnquotedValue(key);
            when(project.getResourcePrefix()).thenReturn(value);
        } else if (key.startsWith("group=")) {
            String value = getUnquotedValue(key);
            when(project.getGroupId()).thenReturn(value);
        } else if (key.startsWith("android.buildToolsVersion ")) {
            String value = getUnquotedValue(key);
            when(project.getBuildToolsVersion()).thenReturn(value);
        } else if (line.startsWith("minifyEnabled ") && key.startsWith("android.buildTypes.")) {
            String name =
                    key.substring("android.buildTypes.".length(), key.indexOf(".minifyEnabled"));
            IdeBuildType buildType = getBuildType(name, true);
            String value = getUnquotedValue(line);
            when(buildType.isMinifyEnabled()).thenReturn(VALUE_TRUE.equals(value));
        } else if (key.startsWith("android.compileSdkVersion ")) {
            String value = getUnquotedValue(key);
            when(project.getCompileTarget())
                    .thenReturn(Character.isDigit(value.charAt(0)) ? "android-" + value : value);
        } else if (line.startsWith("resConfig")) { // and resConfigs
            IdeProductFlavor flavor;
            if (context.startsWith("android.productFlavors.")) {
                String flavorName = context.substring("android.productFlavors.".length());
                flavor = getProductFlavor(flavorName, true);
            } else if (context.equals("android.defaultConfig")) {
                flavor = defaultFlavor;
            } else {
                error("Unexpected flavor " + context);
                return;
            }
            Collection<String> configs = flavor.getResourceConfigurations();
            for (String s :
                    Splitter.on(",").trimResults().split(line.substring(line.indexOf(' ') + 1))) {
                if (!configs.contains(s)) {
                    configs.add(getUnquotedValue(s));
                }
            }
        } else if (key.startsWith("android.defaultConfig.vectorDrawables.useSupportLibrary ")) {
            String value = getUnquotedValue(key);
            if (VALUE_TRUE.equals(value)) {
                IdeVectorDrawablesOptions options = getVectorDrawableOptions();
                when(options.getUseSupportLibrary()).thenReturn(true);
            }
        } else if (key.startsWith(
                "android.compileOptions.sourceCompatibility JavaVersion.VERSION_")) {
            String s =
                    key.substring(key.indexOf("VERSION_") + "VERSION_".length()).replace('_', '.');
            when(compileOptions.getSourceCompatibility()).thenReturn(s);
        } else if (key.startsWith(
                "android.compileOptions.targetCompatibility JavaVersion.VERSION_")) {
            String s =
                    key.substring(key.indexOf("VERSION_") + "VERSION_".length()).replace('_', '.');
            when(compileOptions.getTargetCompatibility()).thenReturn(s);
        } else if (key.startsWith("buildscript.dependencies.classpath ")) {
            if (key.contains("'com.android.tools.build:gradle:")) {
                String value = getUnquotedValue(key);
                GradleCoordinate gc = GradleCoordinate.parseCoordinateString(value);
                if (gc != null) {
                    modelVersion = GradleVersion.parse(gc.getRevision());
                    when(project.getModelVersion()).thenReturn(gc.getRevision());
                }
            } // else ignore other class paths
        } else if (key.startsWith("android.defaultConfig.testInstrumentationRunner ")
                || key.contains(".proguardFiles ")
                || key.equals("dependencies.compile fileTree(dir: 'libs', include: ['*.jar'])")
                || key.startsWith("dependencies.androidTestCompile('")) {
            // Ignored for now
        } else if (line.startsWith("manifestPlaceholders [")
                && key.startsWith("android.")
                && line.endsWith("]")) {
            // Example:
            // android.defaultConfig.manifestPlaceholders [
            // localApplicationId:'com.example.manifest_merger_example']
            Map<String, String> manifestPlaceholders;
            if (context.startsWith("android.buildTypes.")) {
                String name = context.substring("android.buildTypes.".length());
                IdeBuildType buildType = getBuildType(name, false);
                if (buildType != null) {
                    manifestPlaceholders = buildType.getManifestPlaceholders();
                } else {
                    error("Couldn't find flavor " + name + "; ignoring " + key);
                    return;
                }
            } else if (context.startsWith("android.productFlavors.")) {
                String name = context.substring("android.productFlavors.".length());
                IdeProductFlavor flavor = getProductFlavor(name, false);
                if (flavor != null) {
                    manifestPlaceholders = flavor.getManifestPlaceholders();
                } else {
                    error("Couldn't find flavor " + name + "; ignoring " + key);
                    return;
                }
            } else {
                manifestPlaceholders = defaultFlavor.getManifestPlaceholders();
            }

            String mapString = key.substring(key.indexOf('[') + 1, key.indexOf(']')).trim();

            // TODO: Support one than one more entry in the map? Comma separated list
            int index = mapString.indexOf(':');
            assert index != -1 : mapString;
            String mapKey = mapString.substring(0, index).trim();
            mapKey = getUnquotedValue(mapKey);
            String mapValue = mapString.substring(index + 1).trim();
            mapValue = getUnquotedValue(mapValue);
            manifestPlaceholders.put(mapKey, mapValue);
        } else if (key.startsWith("android.flavorDimensions ")) {
            String value = key.substring("android.flavorDimensions ".length());
            Collection<String> flavorDimensions = project.getFlavorDimensions();
            for (String s : Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
                String dimension = getUnquotedValue(s);
                if (!flavorDimensions.contains(dimension)) {
                    flavorDimensions.add(dimension);
                }
            }
        } else if (line.startsWith("dimension ") && key.startsWith("android.productFlavors.")) {
            String name =
                    key.substring("android.productFlavors.".length(), key.indexOf(".dimension"));
            IdeProductFlavor productFlavor = getProductFlavor(name, true);
            String dimension = getUnquotedValue(line);
            when(productFlavor.getDimension()).thenReturn(dimension);
        } else if (key.startsWith("android.") && line.startsWith("resValue ")) {
            // Example:
            // android.defaultConfig.resValue 'string', 'defaultConfigName', 'Some DefaultConfig
            // Data'
            int index = key.indexOf(".resValue ");
            String name = key.substring("android.".length(), index);

            Map<String, IdeClassField> resValues;
            if (name.startsWith("buildTypes.")) {
                name = name.substring("buildTypes.".length());
                IdeBuildType buildType = getBuildType(name, false);
                if (buildType != null) {
                    resValues = buildType.getResValues();
                } else {
                    error("Couldn't find flavor " + name + "; ignoring " + key);
                    return;
                }
            } else if (name.startsWith("productFlavors.")) {
                name = name.substring("productFlavors.".length());
                IdeProductFlavor flavor = getProductFlavor(name, false);
                if (flavor != null) {
                    resValues = flavor.getResValues();
                } else {
                    error("Couldn't find flavor " + name + "; ignoring " + key);
                    return;
                }
            } else {
                assert name.indexOf('.') == -1 : name;
                resValues = defaultFlavor.getResValues();
            }

            String fieldName = null;
            String value = null;
            String type = null;
            String declaration = key.substring(index + ".resValue ".length());
            Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
            int resIndex = 0;
            for (String component : splitter.split(declaration)) {
                component = getUnquotedValue(component);
                switch (resIndex) {
                    case 0:
                        type = component;
                        break;
                    case 1:
                        fieldName = component;
                        break;
                    case 2:
                        value = component;
                        break;
                }

                resIndex++;
            }

            IdeClassField field = mock(IdeClassField.class);
            when(field.getName()).thenReturn(fieldName);
            when(field.getType()).thenReturn(type);
            when(field.getValue()).thenReturn(value);
            resValues.put(fieldName, field);
        } else if (context.startsWith("android.splits.")
                && context.indexOf('.', "android.splits.".length()) == -1) {
            String type = context.substring("android.splits.".length()).toUpperCase(Locale.ROOT);

            if (line.equals("reset")) {
                splits.removeAll(type);
            } else if (line.startsWith("include ")) {
                String value = line.substring("include ".length());
                for (String s : Splitter.on(',').trimResults().omitEmptyStrings().split(value)) {
                    s = getUnquotedValue(s);
                    splits.put(type, s);
                }
            } else if (line.startsWith("exclude ")) {
                warn("Warning: Split exclude not supported for mocked builder model yet");
            }
        } else if (key.startsWith("android.aaptOptions.namespaced ")) {
            String value = getUnquotedValue(key);
            if (VALUE_TRUE.equals(value)) {
                IdeAaptOptions options = getAaptOptions();
                when(options.getNamespacing()).thenReturn(IdeAaptOptions.Namespacing.REQUIRED);
            }
        } else if (key.startsWith("groupId ")) {
            String groupId = getUnquotedValue(key);
            when(project.getGroupId()).thenReturn(groupId);
        } else if (key.startsWith("android.lintOptions.")) {
            key = key.substring("android.lintOptions.".length());
            int argIndex = key.indexOf(' ');
            if (argIndex == -1) {
                error("No value supplied for lint option " + key);
                return;
            }
            String arg = key.substring(argIndex).trim();
            key = key.substring(0, argIndex);

            switch (key) {
                case "quiet":
                    flags.setQuiet(toBoolean(arg));
                    break;
                case "abortOnError":
                    flags.setSetExitCode(toBoolean(arg));
                    break;
                case "checkReleaseBuilds":
                    error("Test framework doesn't support lint DSL flag checkReleaseBuilds");
                    break;
                case "ignoreWarnings":
                    flags.setIgnoreWarnings(toBoolean(arg));
                    break;
                case "absolutePaths":
                    flags.setFullPath(toBoolean(arg));
                    break;
                case "checkAllWarnings":
                    flags.setCheckAllWarnings(toBoolean(arg));
                    break;
                case "warningsAsErrors":
                    flags.setWarningsAsErrors(toBoolean(arg));
                    break;
                case "noLines":
                    flags.setShowSourceLines(!toBoolean(arg));
                    break;
                case "showAll":
                    flags.setShowEverything(toBoolean(arg));
                    break;
                case "explainIssues":
                    flags.setExplainIssues(toBoolean("explainIssues"));
                    break;
                case "textReport":
                    error("Test framework doesn't support lint DSL flag textReport");
                    break;
                case "xmlReport":
                    error("Test framework doesn't support lint DSL flag xmlReport");
                    break;
                case "htmlReport":
                    error("Test framework doesn't support lint DSL flag htmlReport");
                    break;
                case "checkTestSources":
                    {
                        boolean checkTests = toBoolean(arg);
                        flags.setCheckTestSources(checkTests);
                        updateLintOptions(null, null, null, checkTests, null);
                        break;
                    }
                case "checkDependencies":
                    {
                        boolean checkDependencies = toBoolean(arg);
                        flags.setCheckDependencies(checkDependencies);
                        updateLintOptions(null, null, null, null, checkDependencies);
                        break;
                    }
                case "checkGeneratedSources":
                    flags.setCheckGeneratedSources(toBoolean(arg));
                    break;
                case "enable":
                    {
                        Set<String> ids = parseListDsl(arg);
                        flags.getEnabledIds().addAll(ids);
                        setLintSeverity(ids, Severity.WARNING);
                        break;
                    }
                case "disable":
                    {
                        Set<String> ids = parseListDsl(arg);
                        flags.getSuppressedIds().addAll(ids);
                        setLintSeverity(ids, Severity.IGNORE);
                        break;
                    }
                case "check":
                    flags.setExactCheckedIds(parseListDsl(arg));
                    break;
                case "fatal":
                    parseSeverityOverrideDsl(Severity.FATAL, arg);
                    break;
                case "error":
                    parseSeverityOverrideDsl(Severity.ERROR, arg);
                    break;
                case "warning":
                    parseSeverityOverrideDsl(Severity.WARNING, arg);
                    break;
                case "informational":
                    parseSeverityOverrideDsl(Severity.INFORMATIONAL, arg);
                    break;
                case "ignore":
                    parseSeverityOverrideDsl(Severity.IGNORE, arg);
                    break;
                case "lintConfig":
                    {
                        File file = file(arg, true);
                        flags.setDefaultConfiguration(file);
                        updateLintOptions(null, file, null, null, null);
                        break;
                    }
                case "textOutput":
                    error("Test framework doesn't support lint DSL flag textOutput");
                    break;
                case "xmlOutput":
                    error("Test framework doesn't support lint DSL flag xmlOutput");
                    break;
                case "htmlOutput":
                    error("Test framework doesn't support lint DSL flag htmlOutput");
                    break;
                case "baseline":
                    {
                        File file = file(arg, true);
                        flags.setBaselineFile(file);
                        updateLintOptions(file, null, null, null, null);
                        break;
                    }
            }
        } else if (key.startsWith("android.buildFeatures.")) {
            key = key.substring("android.buildFeatures.".length());
            int argIndex = key.indexOf(' ');
            if (argIndex == -1) {
                error("No value supplied for build feature: " + key);
                return;
            }
            String arg = key.substring(argIndex).trim();
            key = key.substring(0, argIndex);

            switch (key) {
                case "viewBinding":
                    IdeViewBindingOptions viewBindingOptions = mock(IdeViewBindingOptions.class);
                    when(viewBindingOptions.getEnabled()).thenReturn(toBoolean(arg));
                    when(project.getViewBindingOptions()).thenReturn(viewBindingOptions);
                    break;
            }
        } else {
            warn("ignored line: " + line + ", context=" + context);
        }
    }

    private static boolean toBoolean(String string) {
        if (string.equalsIgnoreCase("true")) {
            return true;
        }
        if (string.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("String " + string + " should be 'true' or 'false'");
    }

    private void parseSeverityOverrideDsl(Severity severity, String dsl) {
        for (String s : Splitter.on(',').trimResults().omitEmptyStrings().split(dsl)) {
            String id = stripQuotes(s, true);
            setLintSeverity(id, severity);
        }
    }

    private void setLintSeverity(Set<String> ids, Severity severity) {
        for (String id : ids) {
            setLintSeverity(id, severity);
        }
    }

    private void setLintSeverity(String id, Severity severity) {
        flags.getSeverityOverrides().put(id, severity);
        int severityValue;
        switch (severity) {
            case FATAL:
                severityValue = LintOptions.SEVERITY_FATAL;
                break;
            case ERROR:
                severityValue = LintOptions.SEVERITY_ERROR;
                break;
            case WARNING:
                severityValue = LintOptions.SEVERITY_WARNING;
                break;
            case INFORMATIONAL:
                severityValue = LintOptions.SEVERITY_INFORMATIONAL;
                break;
            case IGNORE:
                severityValue = LintOptions.SEVERITY_IGNORE;
                break;
            default:
                severityValue = LintOptions.SEVERITY_DEFAULT_ENABLED;
                break;
        }
        severityOverrides.put(id, severityValue);

        updateLintOptions(null, null, severityOverrides, null, null);
        when(project.getLintOptions()).thenReturn(lintOptions);
    }

    private void updateLintOptions(
            File baseline,
            File lintConfig,
            Map<String, Integer> severities,
            Boolean tests,
            Boolean dependencies) {
        // No mocking IdeLintOptions; it's final
        lintOptions =
                new IdeLintOptionsImpl(
                        baseline != null ? baseline : lintOptions.getBaselineFile(),
                        lintConfig != null ? lintConfig : lintOptions.getLintConfig(),
                        severities != null ? severities : severityOverrides,
                        tests != null ? tests : lintOptions.isCheckTestSources(),
                        dependencies != null ? dependencies : lintOptions.isCheckDependencies(),

                        // TODO: Allow these to be customized by model mocker
                        lintOptions.getEnable(),
                        lintOptions.getDisable(),
                        lintOptions.getCheck(),
                        lintOptions.isAbortOnError(),
                        lintOptions.isAbsolutePaths(),
                        lintOptions.isNoLines(),
                        lintOptions.isQuiet(),
                        lintOptions.isCheckAllWarnings(),
                        lintOptions.isIgnoreWarnings(),
                        lintOptions.isWarningsAsErrors(),
                        lintOptions.isIgnoreTestSources(),
                        lintOptions.isCheckGeneratedSources(),
                        lintOptions.isCheckReleaseBuilds(),
                        lintOptions.isExplainIssues(),
                        lintOptions.isShowAll(),
                        lintOptions.getTextReport(),
                        lintOptions.getTextOutput(),
                        lintOptions.getHtmlReport(),
                        lintOptions.getHtmlOutput(),
                        lintOptions.getXmlReport(),
                        lintOptions.getXmlOutput());
        when(project.getLintOptions()).thenReturn(lintOptions);
    }

    private Set<String> parseListDsl(String dsl) {
        Set<String> updates = new LinkedHashSet<>();
        for (String s : Splitter.on(',').trimResults().omitEmptyStrings().split(dsl)) {
            updates.add(stripQuotes(s, true));
        }
        return updates;
    }

    @NonNull
    private File file(String gradle, boolean reportError) {
        if (gradle.startsWith("file(\"") && gradle.endsWith("\")")
                || gradle.startsWith("file('") && gradle.endsWith("')")) {
            String path = gradle.substring(6, gradle.length() - 2);
            return new File(projectDir, path);
        }
        gradle = stripQuotes(gradle, true);
        if (gradle.equals("stdout") || gradle.equals("stderr")) {
            return new File(gradle);
        }
        if (reportError) {
            error("Only support file(\"\") paths in gradle mocker");
        }
        return new File(gradle);
    }

    private String stripQuotes(String string, boolean reportError) {
        if (string.startsWith("'") && string.endsWith("'") && string.length() >= 2) {
            return string.substring(1, string.length() - 1);
        }
        if (string.startsWith("\"") && string.endsWith("\"") && string.length() >= 2) {
            return string.substring(1, string.length() - 1);
        }
        if (reportError) {
            error("Expected quotes around " + string);
        }
        return string;
    }

    @Nullable
    private IdeProductFlavor getFlavorFromContext(@NonNull String context) {
        if (context.equals("android.defaultConfig")) {
            return defaultFlavor;
        } else if (context.startsWith("android.productFlavors.")) {
            String name = context.substring("android.productFlavors.".length());
            return getProductFlavor(name, true);
        } else {
            return null;
        }
    }

    private IdeVectorDrawablesOptions getVectorDrawableOptions() {
        if (vectorDrawablesOptions == null) {
            vectorDrawablesOptions = mock(IdeVectorDrawablesOptions.class);
            when(mergedFlavor.getVectorDrawables()).thenReturn(vectorDrawablesOptions);
        }
        return vectorDrawablesOptions;
    }

    private IdeAaptOptions getAaptOptions() {
        if (aaptOptions == null) {
            aaptOptions = mock(IdeAaptOptions.class);
            when(project.getAaptOptions()).thenReturn(aaptOptions);
            when(aaptOptions.getNamespacing()).thenReturn(IdeAaptOptions.Namespacing.DISABLED);
        }
        return aaptOptions;
    }

    @Contract("_,true -> !null")
    @Nullable
    private IdeBuildType getBuildType(@NonNull String name, boolean create) {
        for (IdeBuildType type : buildTypes) {
            if (type.getName().equals(name)) {
                return type;
            }
        }

        if (create) {
            return createBuildType(name);
        }

        return null;
    }

    @NonNull
    private IdeBuildType createBuildType(@NonNull String name) {
        IdeBuildType buildType = mock(IdeBuildType.class);
        when(buildType.getName()).thenReturn(name);
        when(buildType.toString()).thenReturn(name);
        when(buildType.isDebuggable()).thenReturn(name.startsWith("debug"));
        buildTypes.add(buildType);
        // Creating mutable map here which we can add to later
        when(buildType.getResValues()).thenReturn(Maps.newHashMap());
        when(buildType.getManifestPlaceholders()).thenReturn(Maps.newHashMap());

        return buildType;
    }

    private void block(
            @NonNull String name,
            @Language("Groovy") @NonNull String blockBody,
            @NonNull String context) {
        if ("android.productFlavors".equals(context)
                && buildTypes.stream().noneMatch(flavor -> flavor.getName().equals(name))) {
            // Defining new product flavors
            createProductFlavor(name);
        }
        if ("android.buildTypes".equals(context)
                && buildTypes.stream().noneMatch(buildType -> buildType.getName().equals(name))) {
            // Defining new build types
            createBuildType(name);
        }

        scan(blockBody, context.isEmpty() ? name : context + "." + name);
    }

    @Contract("_,true -> !null")
    private IdeProductFlavor getProductFlavor(@NonNull String name, boolean create) {
        for (IdeProductFlavor flavor : productFlavors) {
            if (flavor.getName().equals(name)) {
                return flavor;
            }
        }

        if (create) {
            return createProductFlavor(name);
        }

        return null;
    }

    @NonNull
    private IdeProductFlavor createProductFlavor(@NonNull String name) {
        IdeProductFlavor flavor = mock(IdeProductFlavor.class);
        when(flavor.getName()).thenReturn(name);
        when(flavor.toString()).thenReturn(name);
        // Creating mutable map here which we can add to later
        when(flavor.getResValues()).thenReturn(Maps.newHashMap());
        when(flavor.getManifestPlaceholders()).thenReturn(Maps.newHashMap());
        // Creating mutable list here which we can add to later
        when(flavor.getResourceConfigurations()).thenReturn(Lists.newArrayList());

        productFlavors.add(flavor);
        return flavor;
    }

    @NonNull
    private static IdeAndroidArtifactOutput createAndroidArtifactOutput(
            @NonNull String filterType, @NonNull String identifier) {
        IdeAndroidArtifactOutput artifactOutput = mock(IdeAndroidArtifactOutput.class);

        if (filterType.isEmpty()) {
            when(artifactOutput.getFilters()).thenReturn(Collections.emptyList());
        } else {
            List<FilterData> filters = Lists.newArrayList();
            FilterData filter = mock(FilterData.class);
            when(filter.getFilterType()).thenReturn(filterType);
            when(filter.getIdentifier()).thenReturn(identifier);
            filters.add(filter);
            when(artifactOutput.getFilters()).thenReturn(filters);
        }

        return artifactOutput;
    }

    @NonNull
    private static IdeSourceProvider createSourceProvider(
            @NonNull File root, @NonNull String name) {
        IdeSourceProvider provider = mock(IdeSourceProvider.class);
        when(provider.getName()).thenReturn(name);
        when(provider.getManifestFile())
                .thenReturn(new File(root, "src/" + name + "/" + ANDROID_MANIFEST_XML));
        List<File> resDirectories = Lists.newArrayListWithCapacity(2);
        List<File> javaDirectories = Lists.newArrayListWithCapacity(2);
        List<File> assetsDirectories = Lists.newArrayListWithCapacity(1);
        resDirectories.add(new File(root, "src/" + name + "/res"));
        javaDirectories.add(new File(root, "src/" + name + "/java"));
        javaDirectories.add(new File(root, "src/" + name + "/kotlin"));
        assetsDirectories.add(new File(root, "src/" + name + "/assets"));

        when(provider.getResDirectories()).thenReturn(resDirectories);
        when(provider.getJavaDirectories()).thenReturn(javaDirectories);
        when(provider.getAssetsDirectories()).thenReturn(assetsDirectories);

        // TODO: other file types
        return provider;
    }

    @NonNull
    private IdeApiVersion createApiVersion(@NonNull String value) {
        IdeApiVersion version = mock(IdeApiVersion.class);
        String s = value.substring(value.indexOf(' ') + 1);
        if (s.startsWith("'")) {
            String codeName = getUnquotedValue(s);
            AndroidVersion sdkVersion = SdkVersionInfo.getVersion(codeName, null);
            if (sdkVersion != null) {
                when(version.getCodename()).thenReturn(sdkVersion.getCodename());
                when(version.getApiString()).thenReturn(sdkVersion.getApiString());
                when(version.getApiLevel()).thenReturn(sdkVersion.getApiLevel());
            }
        } else {
            when(version.getApiString()).thenReturn(s);
            when(version.getCodename()).thenReturn(null);
            when(version.getApiLevel()).thenReturn(Integer.parseInt(s));
        }
        return version;
    }

    private void addDependency(String declaration, String artifact, boolean isProvided) {
        // If it's one of the common libraries, built up the full dependency graph
        // that we know will actually be used
        //
        // To compute these graphs, put the dependency you're interested into
        // a test project and then run ./gradlew app:dependencies
        if (declaration.startsWith("com.android.support:appcompat-v7:")) {
            String version = declaration.substring("com.android.support:appcompat-v7:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.android.support:appcompat-v7:VERSION\n"
                                    + "|    +--- com.android.support:support-v4:VERSION\n"
                                    + "|    |    +--- com.android.support:support-compat:VERSION\n"
                                    + "|    |    |    \\--- com.android.support:support-annotations:VERSION\n"
                                    + "|    |    +--- com.android.support:support-media-compat:VERSION\n"
                                    + "|    |    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    |    +--- com.android.support:support-core-utils:VERSION\n"
                                    + "|    |    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    |    +--- com.android.support:support-core-ui:VERSION\n"
                                    + "|    |    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    |    \\--- com.android.support:support-fragment:VERSION\n"
                                    + "|    |         +--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    |         +--- com.android.support:support-media-compat:VERSION (*)\n"
                                    + "|    |         +--- com.android.support:support-core-ui:VERSION (*)\n"
                                    + "|    |         \\--- com.android.support:support-core-utils:VERSION (*)\n"
                                    + "|    +--- com.android.support:support-vector-drawable:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    \\--- com.android.support:animated-vector-drawable:VERSION\n"
                                    + "|         \\--- com.android.support:support-vector-drawable:VERSION (*)\n")
                            .replace("VERSION", version),
                    artifact);

        } else if (declaration.startsWith("com.android.support:support-v4:")) {
            String version = declaration.substring("com.android.support:support-v4:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.android.support:support-v4:VERSION\n"
                                    + "|    +--- com.android.support:support-compat:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-annotations:VERSION\n"
                                    + "|    +--- com.android.support:support-media-compat:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    +--- com.android.support:support-core-utils:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    +--- com.android.support:support-core-ui:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|    \\--- com.android.support:support-fragment:VERSION\n"
                                    + "|         +--- com.android.support:support-compat:VERSION (*)\n"
                                    + "|         +--- com.android.support:support-media-compat:VERSION (*)\n"
                                    + "|         +--- com.android.support:support-core-ui:VERSION (*)\n"
                                    + "|         \\--- com.android.support:support-core-utils:VERSION (*)\n")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.android.support.constraint:constraint-layout:")) {
            String version =
                    declaration.substring(
                            "com.android.support.constraint:constraint-layout:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.android.support.constraint:constraint-layout:VERSION\n"
                                    + "     \\--- com.android.support.constraint:constraint-layout-solver:VERSION\n")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.firebase:firebase-client-android:")) {
            String version =
                    declaration.substring("com.firebase:firebase-client-android:".length());
            addTransitiveLibrary(
                    (""
                                    + "\\--- com.firebase:firebase-client-android:VERSION\n"
                                    + "     \\--- com.firebase:firebase-client-jvm:VERSION\n"
                                    + "          +--- com.fasterxml.jackson.core:jackson-databind:2.2.2\n"
                                    + "          |    +--- com.fasterxml.jackson.core:jackson-annotations:2.2.2\n"
                                    + "          |    \\--- com.fasterxml.jackson.core:jackson-core:2.2.2\n"
                                    + "          \\--- com.firebase:tubesock:0.0.12")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.android.support:design:")) {
            // Design library
            String version = declaration.substring("com.android.support:design:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.android.support:design:VERSION\n"
                                    + "|    +--- com.android.support:recyclerview-v7:VERSION\n"
                                    + "|    |    +--- com.android.support:support-annotations:VERSION\n"
                                    + "|    |    \\--- com.android.support:support-v4:VERSION (*)\n"
                                    + "|    +--- com.android.support:appcompat-v7:VERSION (*)\n"
                                    + "|    \\--- com.android.support:support-v4:VERSION (*)")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.google.android.gms:play-services-analytics:")) {
            // Analytics
            String version =
                    declaration.substring(
                            "com.google.android.gms:play-services-analytics:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.google.android.gms:play-services-analytics:VERSION\n"
                                    + "|    \\--- com.google.android.gms:play-services-basement:VERSION\n"
                                    + "|         \\--- com.android.support:support-v4:23.0.0 -> 23.4.0\n"
                                    + "|              \\--- com.android.support:support-annotations:23.4.0")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.google.android.gms:play-services-gcm:")) {
            // GMS
            String version =
                    declaration.substring("com.google.android.gms:play-services-gcm:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.google.android.gms:play-services-gcm:VERSION\n"
                                    + "|    +--- com.google.android.gms:play-services-base:VERSION (*)\n"
                                    + "|    \\--- com.google.android.gms:play-services-measurement:VERSION\n"
                                    + "|         \\--- com.google.android.gms:play-services-basement:VERSION (*)")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("com.google.android.gms:play-services-appindexing:")) {
            // App Indexing
            String version =
                    declaration.substring(
                            "com.google.android.gms:play-services-appindexing:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- com.google.android.gms:play-services-appindexing:VERSION\n"
                                    + "|    \\--- com.google.android.gms:play-services-base:VERSION\n"
                                    + "|         \\--- com.google.android.gms:play-services-basement:VERSION (*)")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk7:")) {
            // Kotlin
            String version =
                    declaration.substring("org.jetbrains.kotlin:kotlin-stdlib-jdk7:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION\n"
                                    + "|    \\--- org.jetbrains.kotlin:kotlin-stdlib:VERSION\n"
                                    + "|         +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION\n"
                                    + "|         \\--- org.jetbrains:annotations:13.0\n"
                                    + "+--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)\n"
                                    + "+--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION")
                            .replace("VERSION", version),
                    artifact);
        } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-stdlib-jdk8:")) {
            // Kotlin
            String version =
                    declaration.substring("org.jetbrains.kotlin:kotlin-stdlib-jdk8:".length());
            addTransitiveLibrary(
                    (""
                                    + "+--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:VERSION\n"
                                    + "|    +--- org.jetbrains.kotlin:kotlin-stdlib:VERSION\n"
                                    + "|    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:VERSION\n"
                                    + "|    |    \\--- org.jetbrains:annotations:13.0\n"
                                    + "|    \\--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:VERSION\n"
                                    + "|         \\--- org.jetbrains.kotlin:kotlin-stdlib:VERSION (*)")
                            .replace("VERSION", version),
                    artifact);
        } else {
            // Look for the library in the dependency graph provided
            Dep dep = graphs.get(declaration);
            if (dep != null) {
                addLibrary(dep, artifact);
            } else if (isJavaLibrary(declaration)) {
                // Not found in dependency graphs: create a single Java library
                IdeLibrary library = createJavaLibrary(declaration, isProvided);
                if (artifact == null || artifact.isEmpty()) {
                    javaLibraries.add(library);
                } else if (artifact.equals("test")) {
                    testJavaLibraries.add(library);
                } else if (artifact.equals("androidTest")) {
                    androidTestJavaLibraries.add(library);
                } else {
                    error("Unrecognized artifact name: " + artifact);
                }
            } else {
                // Not found in dependency graphs: create a single Android library
                IdeLibrary library = createAndroidLibrary(declaration, isProvided);
                if (artifact == null || artifact.isEmpty()) {
                    androidLibraries.add(library);
                } else if (artifact.equals("test")) {
                    testAndroidLibraries.add(library);
                } else if (artifact.equals("androidTest")) {
                    androidTestAndroidLibraries.add(library);
                } else {
                    error("Unrecognized artifact name: " + artifact);
                }
            }
        }
    }

    private void addTransitiveLibrary(@NonNull String graph, String artifact) {
        for (Dep dep : parseDependencyGraph(graph)) {
            addLibrary(dep, artifact);
        }
    }

    private void addLibrary(@NonNull Dep dep, String artifact) {
        List<IdeLibrary> androidLibraries;
        List<IdeLibrary> javaLibraries;
        List<IdeLibrary> moduleLibraries;
        if (artifact == null || artifact.isEmpty()) {
            androidLibraries = this.androidLibraries;
            javaLibraries = this.javaLibraries;
            moduleLibraries = this.moduleLibraries;
        } else if (artifact.equals("test")) {
            androidLibraries = this.testAndroidLibraries;
            javaLibraries = this.testJavaLibraries;
            moduleLibraries = this.testModuleLibraries;
        } else if (artifact.equals("androidTest")) {
            androidLibraries = this.androidTestAndroidLibraries;
            javaLibraries = this.androidTestJavaLibraries;
            moduleLibraries = this.androidTestModuleLibraries;
        } else {
            error("Unrecognized artifact name: " + artifact);
            return;
        }

        Collection<IdeLibrary> libraries = dep.createLibrary();
        for (IdeLibrary library : libraries) {
            if (library.getType() == IdeLibrary.LibraryType.LIBRARY_ANDROID) {
                if (!androidLibraries.contains(library)) {
                    androidLibraries.add(library);
                }
            } else if (library.getType() == IdeLibrary.LibraryType.LIBRARY_JAVA) {
                if (!javaLibraries.contains(library)) {
                    javaLibraries.add(library);
                }
            } else {
                if (!moduleLibraries.contains(library)) {
                    moduleLibraries.add(library);
                }
            }
        }
    }

    /**
     * Returns whether a library declaration is a plain Java library instead of an Android library.
     * There is no way to tell from the Gradle description; it involves looking at the actual Maven
     * artifacts. For mocking purposes we have a hardcoded list.
     */
    private static boolean isJavaLibrary(@NonNull String declaration) {
        if (declaration.startsWith("com.android.support:support-annotations:")) {
            return true;
        } else if (declaration.startsWith("com.android.support:support-v4:")
                || declaration.startsWith("com.android.support:support-v13:")) {
            // Jar prior to to v20
            return declaration.contains(":13")
                    || declaration.contains(":18")
                    || declaration.contains(":19");
        } else if (declaration.startsWith("com.google.guava:guava:")) {
            return true;
        } else if (declaration.startsWith("com.google.android.wearable:wearable:")) {
            return true;
        } else if (declaration.startsWith(
                "com.android.support.constraint:constraint-layout-solver:")) {
            return true;
        } else if (declaration.startsWith("junit:junit:")) {
            return true;
        } else if (declaration.startsWith("org.jetbrains.kotlin:kotlin-")
                || declaration.startsWith("org.jetbrains:annotations")) {
            return true;
        }
        return false;
    }

    @NonNull
    private IdeLibrary createAndroidLibrary(String coordinateString, boolean isProvided) {
        return createAndroidLibrary(coordinateString, null, isProvided, null);
    }

    private IdeLibrary createAndroidLibrary(
            String coordinateString,
            @Nullable String promotedTo,
            boolean isProvided,
            @Nullable File jar) {

        GradleCoordinate coordinate =
                getCoordinate(coordinateString, promotedTo, GradleCoordinate.ArtifactType.AAR);
        File dir;
        if (useBuildCache) {
            // Not what build cache uses, but we just want something stable and unique
            // for tests
            String hash =
                    Hashing.sha1().hashString(coordinate.toString(), Charsets.UTF_8).toString();
            dir =
                    new File(
                            FileUtils.join(
                                    System.getProperty("user.home"),
                                    ".android",
                                    "build-cache",
                                    hash,
                                    "output"));
        } else {
            dir =
                    new File(
                            projectDir,
                            "build/intermediates/exploded-aar/"
                                    + coordinate.getGroupId()
                                    + "/"
                                    + coordinate.getArtifactId()
                                    + "/"
                                    + coordinate.getRevision());
        }
        if (jar == null) {
            jar = new File(dir, "jars/" + FN_CLASSES_JAR);
        }
        if (!jar.exists()) {
            createEmptyJar(jar);
        }
        return createLibraryMock(
                new IdeAndroidLibrary(
                        new IdeAndroidLibraryCore(
                                coordinate.toString(),
                                dir,
                                FN_ANDROID_MANIFEST_XML,
                                jar.getPath(), // non relative path is fine here too.
                                jar.getPath(), // non relative path is fine here too.
                                "res",
                                null,
                                "assets",
                                Collections.emptyList(),
                                "jni",
                                "aidl",
                                "rs",
                                "proguard.pro",
                                "lint.jar",
                                FN_ANNOTATIONS_ZIP,
                                "public.txt",
                                "../lib.aar",
                                "R.txt"),
                        isProvided));
    }

    @NonNull
    private IdeLibrary createJavaLibrary(@NonNull String coordinateString, boolean isProvided) {
        return createJavaLibrary(coordinateString, null, isProvided, null);
    }

    @NonNull
    private IdeLibrary createJavaLibrary(
            @NonNull String coordinateString,
            @Nullable String promotedTo,
            boolean isProvided,
            @Nullable File jar) {

        GradleCoordinate coordinate =
                getCoordinate(coordinateString, promotedTo, GradleCoordinate.ArtifactType.JAR);
        if (jar == null) {
            jar =
                    new File(
                            projectDir,
                            "caches/modules-2/files-2.1/"
                                    + coordinate.getGroupId()
                                    + "/"
                                    + coordinate.getArtifactId()
                                    + "/"
                                    + coordinate.getRevision()
                                    +
                                    // Usually some hex string here, but keep same to keep test
                                    // behavior stable
                                    "9c6ef172e8de35fd8d4d8783e4821e57cdef7445/"
                                    + coordinate.getArtifactId()
                                    + "-"
                                    + coordinate.getRevision()
                                    + DOT_JAR);
            if (!jar.exists()) {
                createEmptyJar(jar);
            }
        }

        return createLibraryMock(
                new IdeJavaLibrary(new IdeJavaLibraryCore(coordinate.toString(), jar), isProvided));
    }

    @NonNull
    private IdeLibrary createModuleLibrary(@NonNull String name) {
        return createLibraryMock(
                new IdeModuleLibrary(
                        new IdeModuleLibraryCore(name, "artifacts:" + name, null), false));
    }

    @NonNull
    private IdeLibrary createLibraryMock(@NonNull IdeLibrary library) {
        return libraryMocks.computeIfAbsent(library, it -> spy(new IdeLibraryDelegate(library)));
    }

    @NonNull
    private GradleCoordinate getCoordinate(
            @NonNull String coordinateString,
            @Nullable String promotedTo,
            @NonNull GradleCoordinate.ArtifactType type) {
        GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateString);
        coordinate =
                new GradleCoordinate(
                        coordinate.getGroupId(),
                        coordinate.getArtifactId(),
                        GradleCoordinate.parseRevisionNumber(
                                promotedTo != null ? promotedTo : coordinate.getRevision()),
                        coordinate.getArtifactType() != null ? coordinate.getArtifactType() : type);
        coordinateString = coordinate.toString();
        TestCase.assertNotNull(coordinateString, coordinate);
        return coordinate;
    }

    @NonNull
    public File getProjectDir() {
        return projectDir;
    }

    public void setVariantName(@NonNull String variantName) {
        ensureInitialized();

        // For something like debugFreeSubscription, set the variant's build type
        // to "debug", and the flavor set to ["free", "subscription"]
        when(variant.getName()).thenReturn(variantName);
        Splitter splitter = Splitter.on('_');
        List<String> flavors = Lists.newArrayList();
        for (String s : splitter.split(SdkVersionInfo.camelCaseToUnderlines(variantName))) {
            IdeBuildType buildType = getBuildType(s, false);
            //noinspection VariableNotUsedInsideIf
            if (buildType != null) {
                when(variant.getBuildType()).thenReturn(s);
            } else {
                IdeProductFlavor flavor = getProductFlavor(s, false);
                //noinspection VariableNotUsedInsideIf
                if (flavor != null) {
                    flavors.add(s);
                }
            }
        }

        when(variant.getProductFlavors()).thenReturn(flavors);
    }

    /**
     * Given a dependency graph, returns a populated {@link Dependencies} object. You can generate
     * Gradle dependency graphs by running for example:
     *
     * <pre>
     *     $ ./gradlew :app:dependencies
     * </pre>
     *
     * <p>Sample graph:
     *
     * <pre>
     * \--- com.android.support.test.espresso:espresso-core:2.2.2
     *      +--- com.squareup:javawriter:2.1.1
     *      +--- com.android.support.test:rules:0.5
     *      |    \--- com.android.support.test:runner:0.5
     *      |         +--- junit:junit:4.12
     *      |         |    \--- org.hamcrest:hamcrest-core:1.3
     *      |         \--- com.android.support.test:exposed-instrumentation-api-publish:0.5
     *      +--- com.android.support.test:runner:0.5 (*)
     *      +--- javax.inject:javax.inject:1
     *      +--- org.hamcrest:hamcrest-library:1.3
     *      |    \--- org.hamcrest:hamcrest-core:1.3
     *      +--- com.android.support.test.espresso:espresso-idling-resource:2.2.2
     *      +--- org.hamcrest:hamcrest-integration:1.3
     *      |    \--- org.hamcrest:hamcrest-library:1.3 (*)
     *      +--- com.google.code.findbugs:jsr305:2.0.1
     *      \--- javax.annotation:javax.annotation-api:1.2
     * </pre>
     *
     * @param graph the graph
     * @return the corresponding dependencies
     */
    @VisibleForTesting
    @NonNull
    IdeDependencies createDependencies(@NonNull String graph) {
        List<Dep> deps = parseDependencyGraph(graph);
        return createDependencies(deps);
    }

    @NonNull
    List<Dep> parseDependencyGraph(@NonNull String graph) {
        return parseDependencyGraph(graph, Maps.newHashMap());
    }

    @NonNull
    List<Dep> parseDependencyGraph(@NonNull String graph, @NonNull Map<String, Dep> map) {
        String[] lines = graph.split("\n");
        // TODO: Check that it's using the expected graph format - e.g. indented to levels
        // that are multiples of 5
        if (lines.length == 0) {
            return Collections.emptyList();
        }

        Dep root = new Dep("", 0);
        Deque<Dep> stack = new ArrayDeque<>();
        stack.push(root);
        Dep parent = root;
        for (String line : lines) {
            int depth = getDepth(line);
            Dep dep = new Dep(line.substring(getIndent(line)), depth);
            map.put(dep.coordinateString, dep);
            if (depth == parent.depth + 1) {
                // Just to append to parent
                parent.add(dep);
            } else if (depth == parent.depth + 2) {
                Dep lastChild = parent.getLastChild();
                if (lastChild != null) {
                    lastChild.add(dep);
                    stack.push(lastChild);
                    parent = lastChild;
                } else {
                    parent.add(dep);
                }
            } else {
                while (true) {
                    stack.pop();
                    parent = stack.peek();
                    if (parent.depth == depth - 1) {
                        parent.add(dep);
                        break;
                    }
                }
            }
        }

        return root.children;
    }

    private static int getDepth(@NonNull String line) {
        return getIndent(line) / 5;
    }

    private static int getIndent(@NonNull String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isLetter(c)) {
                return i;
            }
        }
        return line.length();
    }

    @NonNull
    private IdeDependencies createDependencies(@NonNull List<Dep> deps) {
        Collection<IdeLibrary> result = new LinkedHashSet<>();
        for (Dep dep : deps) {
            Collection<IdeLibrary> androidLibrary = dep.createAndroidLibrary();
            result.addAll(androidLibrary);
        }

        return new IdeDependenciesImpl(
                result.stream()
                        .filter(it -> it.getType() == IdeLibrary.LibraryType.LIBRARY_ANDROID)
                        .collect(toImmutableList()),
                result.stream()
                        .filter(it -> it.getType() == IdeLibrary.LibraryType.LIBRARY_JAVA)
                        .collect(toImmutableList()),
                result.stream()
                        .filter(it -> it.getType() == IdeLibrary.LibraryType.LIBRARY_MODULE)
                        .collect(toImmutableList()),
                ImmutableList.of());
    }

    /** Dependency graph node */
    private class Dep {
        public final GradleCoordinate coordinate;
        public final String coordinateString;
        public final String promotedTo;
        public final List<Dep> children = Lists.newArrayList();
        public final int depth;

        public Dep(String coordinateString, int depth) {
            int promoted = coordinateString.indexOf(" -> ");
            String aPromotedTo;
            if (promoted != -1) {
                aPromotedTo = coordinateString.substring(promoted + 4);
                coordinateString = coordinateString.substring(0, promoted);
            } else {
                aPromotedTo = null;
            }
            coordinateString = trimStars(coordinateString);
            if (aPromotedTo != null) {
                aPromotedTo = trimStars(aPromotedTo);
            }
            this.promotedTo = aPromotedTo;
            this.coordinateString = coordinateString;
            this.coordinate =
                    !coordinateString.isEmpty()
                            ? GradleCoordinate.parseCoordinateString(coordinateString)
                            : null;
            this.depth = depth;
        }

        @NonNull
        private String trimStars(String coordinateString) {
            if (coordinateString.endsWith(" (*)")) {
                coordinateString =
                        coordinateString.substring(0, coordinateString.length() - " (*)".length());
            }
            return coordinateString;
        }

        private void add(Dep child) {
            children.add(child);
        }

        public boolean isJavaLibrary() {
            return GradleModelMocker.isJavaLibrary(coordinateString);
        }

        public boolean isProject() {
            return coordinate == null && coordinateString.startsWith("project ");
        }

        @NonNull
        Collection<IdeLibrary> createLibrary() {
            if (isJavaLibrary()) {
                return createJavaLibrary();
            } else {
                return createAndroidLibrary();
            }
        }

        private Collection<IdeLibrary> createAndroidLibrary() {
            Collection<IdeLibrary> result = new LinkedHashSet<>();
            if (isProject()) {
                String name = coordinateString.substring("project ".length());
                result.add(GradleModelMocker.this.createModuleLibrary(name));
            } else {
                result.add(
                        GradleModelMocker.this.createAndroidLibrary(
                                coordinateString, promotedTo, false, null));
            }
            if (!children.isEmpty()) {
                for (Dep dep : children) {
                    result.addAll(dep.createLibrary());
                }
            }
            return result;
        }

        private Collection<IdeLibrary> createJavaLibrary() {
            Collection<IdeLibrary> result = new LinkedHashSet<>();
            if (isProject()) {
                String name = coordinateString.substring("project ".length());
                result.add(GradleModelMocker.this.createModuleLibrary(name));
            } else {
                result.add(
                        GradleModelMocker.this.createJavaLibrary(
                                coordinateString, promotedTo, false, null));
            }
            if (!children.isEmpty()) {
                for (Dep dep : children) {
                    result.addAll(dep.createLibrary());
                }
            }
            return result;
        }

        @Nullable
        private Dep getLastChild() {
            return children.isEmpty() ? null : children.get(children.size() - 1);
        }

        @Override
        public String toString() {
            return coordinate + ":" + depth;
        }

        @SuppressWarnings("unused") // For debugging
        public void printTree(int indent, PrintStream writer) {
            for (int i = 0; i < indent; i++) {
                writer.print("    ");
            }
            writer.println(coordinate);
            for (Dep child : children) {
                child.printTree(indent + 1, writer);
            }
        }
    }
}
