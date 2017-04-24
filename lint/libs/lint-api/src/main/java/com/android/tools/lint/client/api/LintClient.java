/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.CLASS_FOLDER;
import static com.android.SdkConstants.DOT_AAR;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.GEN_FOLDER;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.SdkConstants.RES_FOLDER;
import static com.android.SdkConstants.SRC_FOLDER;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;
import static com.google.common.base.Charsets.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.manifmerger.Actions;
import com.android.prefs.AndroidLocation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import com.android.tools.lint.detector.api.CharSequences;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Information about the tool embedding the lint analyzer. IDEs and other tools
 * implementing lint support will extend this to integrate logging, displaying errors,
 * etc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public abstract class LintClient {
    private static final String PROP_BIN_DIR  = "com.android.tools.lint.bindir";

    protected LintClient(@NonNull String clientName) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        LintClient.clientName = clientName;
    }

    protected LintClient() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        clientName = "unknown";
    }

    /**
     * Returns a configuration for use by the given project. The configuration
     * provides information about which issues are enabled, any customizations
     * to the severity of an issue, etc.
     * <p>
     * By default this method returns a {@link DefaultConfiguration}.
     *
     * @param project the project to obtain a configuration for
     * @param driver the current driver, if any
     * @return a configuration, never null.
     */
    @NonNull
    public Configuration getConfiguration(@NonNull Project project, @Nullable LintDriver driver) {
        return DefaultConfiguration.create(this, project, null);
    }

    /**
     * Report the given issue. This method will only be called if the configuration
     * provided by {@link #getConfiguration(Project, LintDriver)} has reported the corresponding
     * issue as enabled and has not filtered out the issue with its
     * {@link Configuration#ignore(Context, Issue, Location, String)} method.
     * <p>
     *
     * @param context  the context used by the detector when the issue was found
     * @param issue    the issue that was found
     * @param severity the severity of the issue
     * @param location the location of the issue
     * @param message  the associated user message
     * @param format   the format of the description and location descriptions
     * @param fix      an optional set of extra data provided by the detector for this issue; this
     *                 is intended to pass metadata to the IDE to help construct quickfixes without
     *                 having to parse error messages (which is brittle) or worse having to include
     *                 information in the error message (for later parsing) which is required by the
     *                 quickfix but not really helpful in the error message itself (such as the
     *                 maxVersion for a permission tag to be added to the
     */
    public abstract void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @NonNull TextFormat format,
            @Nullable LintFix fix);

    /**
     * Send an exception or error message (with warning severity) to the log
     *
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax, possibly null
     *    (though in that case the exception should not be null)
     * @param args any arguments for the format string
     */
    public void log(
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        log(Severity.WARNING, exception, format, args);
    }

    /**
     * Send an exception or error message to the log
     *
     * @param severity the severity of the warning
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax, possibly null
     *    (though in that case the exception should not be null)
     * @param args any arguments for the format string
     */
    public abstract void log(
            @NonNull Severity severity,
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args);

    /**
     * Returns a {@link XmlParser} to use to parse XML
     *
     * @return a new {@link XmlParser}, or null if this client does not support
     *         XML analysis
     */
    @NonNull
    public abstract XmlParser getXmlParser();

    /**
     * Returns a {@link JavaParser} to use to parse Java
     *
     * @param project the project to parse, if known (this can be used to look up
     *                the class path for type attribution etc, and it can also be used
     *                to more efficiently process a set of files, for example to
     *                perform type attribution for multiple units in a single pass)
     * @return a new {@link JavaParser}, or null if this client does not
     *         support Java analysis
     */
    @Nullable
    public abstract JavaParser getJavaParser(@Nullable Project project);

    /**
     * Returns a {@link JavaParser} to use to parse Java
     *
     * @param project the project to parse, if known (this can be used to look up
     *                the class path for type attribution etc, and it can also be used
     *                to more efficiently process a set of files, for example to
     *                perform type attribution for multiple units in a single pass)
     * @return a new {@link JavaParser}, or null if this client does not
     *         support Java analysis
     */
    @Nullable
    public abstract UastParser getUastParser(@Nullable Project project);

    /**
     * Returns an optimal detector, if applicable. By default, just returns the
     * original detector, but tools can replace detectors using this hook with a version
     * that takes advantage of native capabilities of the tool.
     *
     * @param detectorClass the class of the detector to be replaced
     * @return the new detector class, or just the original detector (not null)
     */
    @NonNull
    public Class<? extends Detector> replaceDetector(
            @NonNull Class<? extends Detector> detectorClass) {
        return detectorClass;
    }

    /**
     * Reads the given text file and returns the content as a string
     *
     * @param file the file to read
     * @return the string to return, never null (will be empty if there is an
     *         I/O error)
     */
    @NonNull
    public abstract CharSequence readFile(@NonNull File file);

    /**
     * Reads the given binary file and returns the content as a byte array.
     * By default this method will read the bytes from the file directly,
     * but this can be customized by a client if for example I/O could be
     * held in memory and not flushed to disk yet.
     *
     * @param file the file to read
     * @return the bytes in the file, never null
     * @throws IOException if the file does not exist, or if the file cannot be
     *             read for some reason
     */
    @NonNull
    public byte[] readBytes(@NonNull File file) throws IOException {
        return Files.toByteArray(file);
    }

    /**
     * Returns the list of source folders for Java source files
     *
     * @param project the project to look up Java source file locations for
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getJavaSourceFolders(@NonNull Project project) {
        return getClassPath(project).getSourceFolders();
    }

    /**
     * Returns the list of generated source folders
     *
     * @param project the project to look up generated source file locations for
     * @return a list of generated source folders to search for source files
     */
    @NonNull
    public List<File> getGeneratedSourceFolders(@NonNull Project project) {
        return getClassPath(project).getGeneratedFolders();
    }

    /**
     * Returns the list of output folders for class files
     *
     * @param project the project to look up class file locations for
     * @return a list of output folders to search for .class files
     */
    @NonNull
    public List<File> getJavaClassFolders(@NonNull Project project) {
        return getClassPath(project).getClassFolders();

    }

    /**
     * Returns the list of Java libraries
     *
     * @param project         the project to look up jar dependencies for
     * @param includeProvided If true, included provided libraries too (libraries that are not
     *                        packaged with the app, but are provided for compilation purposes and
     *                        are assumed to be present in the running environment)
     * @return a list of jar dependencies containing .class files
     */
    @NonNull
    public List<File> getJavaLibraries(@NonNull Project project, boolean includeProvided) {
        return getClassPath(project).getLibraries(includeProvided);
    }

    /**
     * Returns the list of source folders for test source files
     *
     * @param project the project to look up test source file locations for
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getTestSourceFolders(@NonNull Project project) {
        return getClassPath(project).getTestSourceFolders();
    }

    /**
     * Returns the list of libraries needed to compile the test source files
     *
     * @param project the project to look up test source file locations for
     * @return a list of jar files to add to the regular project dependencies when compiling the
     * test sources
     */
    @NonNull
    public List<File> getTestLibraries(@NonNull Project project) {
        return getClassPath(project).getTestLibraries();
    }

    /**
     * Returns the resource folders.
     *
     * @param project the project to look up the resource folder for
     * @return a list of files pointing to the resource folders, possibly empty
     */
    @NonNull
    public List<File> getResourceFolders(@NonNull Project project) {
        File res = new File(project.getDir(), RES_FOLDER);
        if (res.exists()) {
            return Collections.singletonList(res);
        }

        return Collections.emptyList();
    }

    /**
     * Returns the asset folders.
     *
     * @param project the project to look up the asset folder for
     * @return a list of files pointing to the asset folders, possibly empty
     */
    @NonNull
    public List<File> getAssetFolders(@NonNull Project project) {
        File assets = new File(project.getDir(), FD_ASSETS);
        if (assets.exists()) {
            return Collections.singletonList(assets);
        }

        return Collections.emptyList();
    }

    /**
     * Returns the {@link SdkInfo} to use for the given project.
     *
     * @param project the project to look up an {@link SdkInfo} for
     * @return an {@link SdkInfo} for the project
     */
    @NonNull
    public SdkInfo getSdkInfo(@NonNull Project project) {
        // By default no per-platform SDK info
        return new DefaultSdkInfo();
    }

    /**
     * Returns a suitable location for storing cache files. Note that the
     * directory may not exist. You can override the default location
     * using {@code $ANDROID_SDK_CACHE_DIR} (though note that specific
     * lint integrations may not honor that environment variable; for example,
     * in Gradle the cache directory will <b>always</b> be build/intermediates/lint-cache/.)
     *
     * @param create if true, attempt to create the cache dir if it does not
     *            exist
     * @return a suitable location for storing cache files, which may be null if
     *         the create flag was false, or if for some reason the directory
     *         could not be created
     */
    @Nullable
    public File getCacheDir(boolean create) {
        String path = System.getenv("ANDROID_SDK_CACHE_DIR");
        if (path != null) {
            File dir = new File(path);
            if (create && !dir.exists()) {
                if (!dir.mkdirs()) {
                    return null;
                }
            }
            return dir;
        }

        String home = System.getProperty("user.home");
        String relative = ".android" + File.separator + "cache";
        File dir = new File(home, relative);
        if (create && !dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        return dir;
    }

    /**
     * Returns the File corresponding to the system property or the environment variable
     * for {@link #PROP_BIN_DIR}.
     * This property is typically set by the SDK/tools/lint[.bat] wrapper.
     * It denotes the path of the wrapper on disk.
     *
     * @return A new File corresponding to {@link LintClient#PROP_BIN_DIR} or null.
     */
    @Nullable
    private static File getLintBinDir() {
        // First check the Java properties (e.g. set using "java -jar ... -Dname=value")
        String path = System.getProperty(PROP_BIN_DIR);
        if (path == null || path.isEmpty()) {
            // If not found, check environment variables.
            path = System.getenv(PROP_BIN_DIR);
        }
        if (path != null && !path.isEmpty()) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Returns the File pointing to the user's SDK install area. This is generally
     * the root directory containing the lint tool (but also platforms/ etc).
     *
     * @return a file pointing to the user's install area
     */
    @Nullable
    public File getSdkHome() {
        File binDir = getLintBinDir();
        if (binDir != null) {
            assert binDir.getName().equals("tools");

            File root = binDir.getParentFile();
            if (root != null && root.isDirectory()) {
                return root;
            }
        }

        String home = System.getenv("ANDROID_HOME");
        if (home != null) {
            return new File(home);
        }

        return null;
    }

    /**
     * Database moved from platform-tools to SDK in API level 26.
     *
     * This duplicates the constant in {@link LintClient} but that
     * constant is not public (because it's in the API package and I don't
     * want this part of the API surface; it's an implementation optimization.)
     */
    private static final int SDK_DATABASE_MIN_VERSION = 26;

    /**
     * Locates an SDK resource (relative to the SDK root directory).
     * <p>
     * TODO: Consider switching to a {@link URL} return type instead.
     *
     * @param relativePath A relative path (using {@link File#separator} to
     *            separate path components) to the given resource
     * @return a {@link File} pointing to the resource, or null if it does not
     *         exist
     */
    @Nullable
    public File findResource(@NonNull String relativePath) {
        File top = getSdkHome();
        if (top == null) {
            throw new IllegalArgumentException("Lint must be invoked with "
                    + "$ANDROID_HOME set to point to the SDK, or the System property "
                    + PROP_BIN_DIR + " pointing to the ANDROID_SDK tools directory");
        }

        // Files looked up by ExternalAnnotationRepository and ApiLookup, respectively
        boolean isAnnotationZip = "annotations.zip".equals(relativePath);
        boolean isApiDatabase = "api-versions.xml".equals(relativePath);
        if (isAnnotationZip || isApiDatabase) {
            if (isAnnotationZip) {
                // Allow Gradle builds etc to point to a specific location
                String path = System.getenv("SDK_ANNOTATIONS");
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) {
                        return file;
                    }
                }
            }

            // Look for annotations.zip or api-versions.xml: these used to ship with the
            // platform-tools, but were (in API 26) moved over to the API platform.
            // Look for the most recent version, falling back to platform-tools if necessary.
            IAndroidTarget[] targets = getTargets();
            for (int i = targets.length - 1; i >= 0; i--) {
                IAndroidTarget target = targets[i];
                if (target.isPlatform() &&
                        target.getVersion().getFeatureLevel() >= SDK_DATABASE_MIN_VERSION) {
                    File file = new File(target.getFile(IAndroidTarget.DATA), relativePath);
                    if (file.isFile()) {
                        return file;
                    }
                }
            }

            // Fallback to looking in the old location: platform-tools/api/<name> under the SDK
            File file = new File(top, "platform-tools" + File.separator + "api" +
                    File.separator + relativePath);
            if (file.exists()) {
                return file;
            }

            if (isApiDatabase) {
                // AOSP build environment?
                String build = System.getenv("ANDROID_BUILD_TOP");
                if (build != null) {
                    file = new File(build, "development/sdk/api-versions.xml"
                            .replace('/', File.separatorChar));
                    if (file.exists()) {
                        return file;
                    }
                }
            }

            return null;
        }

        File file = new File(top, relativePath);
        if (file.exists()) {
            return file;
        } else {
            return null;
        }
    }

    private Map<Project, ClassPathInfo> projectInfo;

    /**
     * Returns true if this project is a Gradle-based Android project
     *
     * @param project the project to check
     * @return true if this is a Gradle-based project
     */
    public boolean isGradleProject(Project project) {
        // This is not an accurate test; specific LintClient implementations (e.g.
        // IDEs or a gradle-integration of lint) have more context and can perform a more accurate
        // check
        if (new File(project.getDir(), SdkConstants.FN_BUILD_GRADLE).exists()) {
            return true;
        }

        File parent = project.getDir().getParentFile();
        if (parent != null && parent.getName().equals(SdkConstants.FD_SOURCES)) {
            File root = parent.getParentFile();
            if (root != null && new File(root, SdkConstants.FN_BUILD_GRADLE).exists()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Information about class paths (sources, class files and libraries)
     * usually associated with a project.
     */
    protected static class ClassPathInfo {
        private final List<File> classFolders;
        private final List<File> sourceFolders;
        private final List<File> libraries;
        private final List<File> nonProvidedLibraries;
        private final List<File> testFolders;
        private final List<File> testLibraries;
        private final List<File> generatedFolders;

        public ClassPathInfo(
                @NonNull List<File> sourceFolders,
                @NonNull List<File> classFolders,
                @NonNull List<File> libraries,
                @NonNull List<File> nonProvidedLibraries,
                @NonNull List<File> testFolders,
                @NonNull List<File> testLibraries,
                @NonNull List<File> generatedFolders) {
            this.sourceFolders = sourceFolders;
            this.classFolders = classFolders;
            this.libraries = libraries;
            this.nonProvidedLibraries = nonProvidedLibraries;
            this.testFolders = testFolders;
            this.testLibraries = testLibraries;
            this.generatedFolders = generatedFolders;
        }

        @NonNull
        public List<File> getSourceFolders() {
            return sourceFolders;
        }

        @NonNull
        public List<File> getClassFolders() {
            return classFolders;
        }

        @NonNull
        public List<File> getLibraries(boolean includeProvided) {
            return includeProvided ? libraries : nonProvidedLibraries;
        }

        @NonNull
        public List<File> getTestSourceFolders() {
            return testFolders;
        }

        @NonNull
        public List<File> getTestLibraries() {
            return testLibraries;
        }

        @NonNull
        public List<File> getGeneratedFolders() {
            return generatedFolders;
        }
    }

    /**
     * Considers the given project as an Eclipse project and returns class path
     * information for the project - the source folder(s), the output folder and
     * any libraries.
     * <p>
     * Callers will not cache calls to this method, so if it's expensive to compute
     * the classpath info, this method should perform its own caching.
     *
     * @param project the project to look up class path info for
     * @return a class path info object, never null
     */
    @NonNull
    protected ClassPathInfo getClassPath(@NonNull Project project) {
        ClassPathInfo info;
        if (projectInfo == null) {
            projectInfo = Maps.newHashMap();
            info = null;
        } else {
            info = projectInfo.get(project);
        }

        if (info == null) {
            List<File> sources = new ArrayList<>(2);
            List<File> classes = new ArrayList<>(1);
            List<File> generated = new ArrayList<>(1);
            List<File> libraries = new ArrayList<>();
            // No test folders in Eclipse:
            // https://bugs.eclipse.org/bugs/show_bug.cgi?id=224708
            List<File> tests = Collections.emptyList();

            File projectDir = project.getDir();
            File classpathFile = new File(projectDir, ".classpath");
            if (classpathFile.exists()) {
                CharSequence classpathXml = readFile(classpathFile);
                Document document = CharSequences.parseDocumentSilently(classpathXml, false);
                if (document != null) {
                    NodeList tags = document.getElementsByTagName("classpathentry");
                    for (int i = 0, n = tags.getLength(); i < n; i++) {
                        Element element = (Element) tags.item(i);
                        String kind = element.getAttribute("kind");
                        List<File> addTo = null;
                        if (kind.equals("src")) {
                            addTo = sources;
                        } else if (kind.equals("output")) {
                            addTo = classes;
                        } else if (kind.equals("lib")) {
                            addTo = libraries;
                        }
                        if (addTo != null) {
                            String path = element.getAttribute("path");
                            File folder = new File(projectDir, path);
                            if (folder.exists()) {
                                addTo.add(folder);
                            }
                        }
                    }
                }
            }

            // Add in libraries that aren't specified in the .classpath file
            File libs = new File(project.getDir(), LIBS_FOLDER);
            if (libs.isDirectory()) {
                File[] jars = libs.listFiles();
                if (jars != null) {
                    for (File jar : jars) {
                        if (endsWith(jar.getPath(), DOT_JAR)
                                && !libraries.contains(jar)) {
                            libraries.add(jar);
                        }
                    }
                }
            }

            if (classes.isEmpty()) {
                File folder = new File(projectDir, CLASS_FOLDER);
                if (folder.exists()) {
                    classes.add(folder);
                } else {
                    // Maven checks
                    folder = new File(projectDir,
                            "target" + File.separator + "classes");
                    if (folder.exists()) {
                        classes.add(folder);

                        // If it's maven, also correct the source path, "src" works but
                        // it's in a more specific subfolder
                        if (sources.isEmpty()) {
                            File src = new File(projectDir,
                                    "src" + File.separator
                                    + "main" + File.separator
                                    + "java");
                            if (src.exists()) {
                                sources.add(src);
                            } else {
                                src = new File(projectDir, SRC_FOLDER);
                                if (src.exists()) {
                                    sources.add(src);
                                }
                            }

                            File gen = new File(projectDir,
                                    "target" + File.separator
                                    + "generated-sources" + File.separator
                                    + "r");
                            if (gen.exists()) {
                                generated.add(gen);
                            }
                        }
                    }
                }
            }

            // Fallback, in case there is no Eclipse project metadata here
            if (sources.isEmpty()) {
                File src = new File(projectDir, SRC_FOLDER);
                if (src.exists()) {
                    sources.add(src);
                }
                File gen = new File(projectDir, GEN_FOLDER);
                if (gen.exists()) {
                    generated.add(gen);
                }
            }

            info = new ClassPathInfo(sources, classes, libraries, libraries, tests,
                    Collections.emptyList(), generated);
            projectInfo.put(project, info);
        }

        return info;
    }

    /**
     * A map from directory to existing projects, or null. Used to ensure that
     * projects are unique for a directory (in case we process a library project
     * before its including project for example)
     */
    protected Map<File, Project> dirToProject;

    /**
     * Returns a project for the given directory. This should return the same
     * project for the same directory if called repeatedly.
     *
     * @param dir the directory containing the project
     * @param referenceDir See {@link Project#getReferenceDir()}.
     * @return a project, never null
     */
    @NonNull
    public Project getProject(@NonNull File dir, @NonNull File referenceDir) {
        if (dirToProject == null) {
            dirToProject = new HashMap<>();
        }

        File canonicalDir = dir;
        try {
            // Attempt to use the canonical handle for the file, in case there
            // are symlinks etc present (since when handling library projects,
            // we also call getCanonicalFile to compute the result of appending
            // relative paths, which can then resolve symlinks and end up with
            // a different prefix)
            canonicalDir = dir.getCanonicalFile();
        } catch (IOException ioe) {
            // pass
        }

        Project project = dirToProject.get(canonicalDir);
        if (project != null) {
            return project;
        }

        project = createProject(dir, referenceDir);
        dirToProject.put(canonicalDir, project);
        return project;
    }

    /**
     * Returns the list of known projects (projects registered via
     * {@link #getProject(File, File)}
     *
     * @return a collection of projects in any order
     */
    public Collection<Project> getKnownProjects() {
        return dirToProject != null ? dirToProject.values() : Collections.emptyList();
    }

    /**
     * Registers the given project for the given directory. This can
     * be used when projects are initialized outside of the client itself.
     *
     * @param dir the directory of the project, which must be unique
     * @param project the project
     */
    public void registerProject(@NonNull File dir, @NonNull Project project) {
        File canonicalDir = dir;
        try {
            // Attempt to use the canonical handle for the file, in case there
            // are symlinks etc present (since when handling library projects,
            // we also call getCanonicalFile to compute the result of appending
            // relative paths, which can then resolve symlinks and end up with
            // a different prefix)
            canonicalDir = dir.getCanonicalFile();
        } catch (IOException ioe) {
            // pass
        }


        if (dirToProject == null) {
            dirToProject = new HashMap<>();
        } else {
            assert !dirToProject.containsKey(dir) : dir;
        }
        dirToProject.put(canonicalDir, project);
    }

    protected Set<File> projectDirs = Sets.newHashSet();

    /**
     * Create a project for the given directory
     * @param dir the root directory of the project
     * @param referenceDir See {@link Project#getReferenceDir()}.
     * @return a new project
     */
    @NonNull
    protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
        if (projectDirs.contains(dir)) {
            throw new CircularDependencyException(
                "Circular library dependencies; check your project.properties files carefully");
        }
        projectDirs.add(dir);
        return Project.create(this, dir, referenceDir);
    }

    /**
     * Perform any startup initialization of the full set of projects that lint will be
     * run on, if necessary.
     *
     * @param knownProjects the list of projects
     */
    protected void initializeProjects(@NonNull Collection<Project> knownProjects) {

    }

    /**
     * Perform any post-analysis cleaninup of the full set of projects that lint was
     * run on, if necessary.
     *
     * @param knownProjects the list of projects
     */
    protected void disposeProjects(@NonNull Collection<Project> knownProjects) {
    }

    /**
     * Returns the name of the given project
     *
     * @param project the project to look up
     * @return the name of the project
     */
    @NonNull
    public String getProjectName(@NonNull Project project) {
        return project.getDir().getName();
    }

    protected IAndroidTarget[] targets;

    /**
     * Returns all the {@link IAndroidTarget} versions installed in the user's SDK install
     * area.
     *
     * @return all the installed targets
     */
    @NonNull
    public IAndroidTarget[] getTargets() {
        if (targets == null) {
            AndroidSdkHandler sdkHandler = getSdk();
            if (sdkHandler != null) {
                ProgressIndicator logger = getRepositoryLogger();
                Collection<IAndroidTarget> targets = sdkHandler.getAndroidTargetManager(logger)
                        .getTargets(logger);
                this.targets = targets.toArray(new IAndroidTarget[targets.size()]);
            } else {
                targets = new IAndroidTarget[0];
            }
        }

        return targets;
    }

    protected AndroidSdkHandler sdk;

    /**
     * Returns the SDK installation (used to look up platforms etc)
     *
     * @return the SDK if known
     */
    @Nullable
    public AndroidSdkHandler getSdk() {
        if (sdk == null) {
            File sdkHome = getSdkHome();
            if (sdkHome != null) {
                sdk = AndroidSdkHandler.getInstance(sdkHome);
            }
        }

        return sdk;
    }

    /**
     * Returns the compile target to use for the given project
     *
     * @param project the project in question
     *
     * @return the compile target to use to build the given project
     */
    @Nullable
    public IAndroidTarget getCompileTarget(@NonNull Project project) {
        String compileSdkVersion = project.getBuildTargetHash();
        if (compileSdkVersion != null) {
            ProgressIndicator logger = getRepositoryLogger();
            AndroidSdkHandler handler = getSdk();
            if (handler != null) {
                AndroidTargetManager manager = handler.getAndroidTargetManager(logger);
                IAndroidTarget target = manager.getTargetFromHashString(compileSdkVersion,
                                logger);
                if (target != null) {
                    return target;
                }
            }
        }

        int buildSdk = project.getBuildSdk();
        IAndroidTarget[] targets = getTargets();
        for (int i = targets.length - 1; i >= 0; i--) {
            IAndroidTarget target = targets[i];
            if (target.isPlatform() && target.getVersion().getApiLevel() == buildSdk) {
                return target;
            }
        }

        return null;
    }

    /**
     * Returns the highest known API level.
     *
     * @return the highest known API level
     */
    public int getHighestKnownApiLevel() {
        int max = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;

        for (IAndroidTarget target : getTargets()) {
            if (target.isPlatform()) {
                int api = target.getVersion().getApiLevel();
                if (api > max && !target.getVersion().isPreview()) {
                    max = api;
                }
            }
        }

        return max;
    }

    /**
     * Returns the specific version of the build tools being used for the given project, if known
     *
     * @param project the project in question
     *
     * @return the build tools version in use by the project, or null if not known
     */
    @Nullable
    public BuildToolInfo getBuildTools(@NonNull Project project) {
        AndroidSdkHandler sdk = getSdk();
        // Build systems like Eclipse and ant just use the latest available
        // build tools, regardless of project metadata. In Gradle, this
        // method is overridden to use the actual build tools specified in the
        // project.
        if (sdk != null) {
            IAndroidTarget compileTarget = getCompileTarget(project);
            if (compileTarget != null) {
                return compileTarget.getBuildToolInfo();
            }
            return sdk.getLatestBuildTool(getRepositoryLogger(), false);
        }

        return null;
    }

    /**
     * Returns the super class for the given class name, which should be in VM
     * format (e.g. java/lang/Integer, not java.lang.Integer, and using $ rather
     * than . for inner classes). If the super class is not known, returns null.
     * <p>
     * This is typically not necessary, since lint analyzes all the available
     * classes. However, if this lint client is invoking lint in an incremental
     * context (for example, an IDE offering incremental analysis of a single
     * source file), then lint may not see all the classes, and the client can
     * provide its own super class lookup.
     *
     * @param project the project containing the class
     * @param name the fully qualified class name
     * @return the corresponding super class name (in VM format), or null if not
     *         known
     */
    @Nullable
    public String getSuperClass(@NonNull Project project, @NonNull String name) {
        assert name.indexOf('.') == -1 : "Use VM signatures, e.g. java/lang/Integer";

        if ("java/lang/Object".equals(name)) {
            return null;
        }

        String superClass = project.getSuperClassMap().get(name);
        if (superClass != null) {
            return superClass;
        }

        for (Project library : project.getAllLibraries()) {
            superClass = library.getSuperClassMap().get(name);
            if (superClass != null) {
                return superClass;
            }
        }

        return null;
    }

    /**
     * Creates a super class map for the given project. The map maps from
     * internal class name (e.g. java/lang/Integer, not java.lang.Integer) to its
     * corresponding super class name. The root class, java/lang/Object, is not in the map.
     *
     * @param project the project to initialize the super class with; this will include
     *                local classes as well as any local .jar libraries; not transitive
     *                dependencies
     * @return a map from class to its corresponding super class; never null
     */
    @NonNull
    public Map<String, String> createSuperClassMap(@NonNull Project project) {
        List<File> libraries = project.getJavaLibraries(true);
        List<File> classFolders = project.getJavaClassFolders();
        List<ClassEntry> classEntries = ClassEntry.fromClassPath(this, classFolders, true);
        if (libraries.isEmpty()) {
            return ClassEntry.createSuperClassMap(this, classEntries);
        }
        List<ClassEntry> libraryEntries = ClassEntry.fromClassPath(this, libraries, true);
        return ClassEntry.createSuperClassMap(this, libraryEntries, classEntries);
    }

    /**
     * Checks whether the given name is a subclass of the given super class. If
     * the method does not know, it should return null, and otherwise return
     * {@link Boolean#TRUE} or {@link Boolean#FALSE}.
     * <p>
     * Note that the class names are in internal VM format (java/lang/Integer,
     * not java.lang.Integer, and using $ rather than . for inner classes).
     *
     * @param project the project context to look up the class in
     * @param name the name of the class to be checked
     * @param superClassName the name of the super class to compare to
     * @return true if the class of the given name extends the given super class
     */
    @SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
    @Nullable
    public Boolean isSubclassOf(
            @NonNull Project project,
            @NonNull String name,
            @NonNull String superClassName) {
        return null;
    }

    /**
     * Finds any custom lint rule jars that should be included for analysis,
     * regardless of project.
     * <p>
     * The default implementation locates custom lint jars in ~/.android/lint/ and
     * in $ANDROID_LINT_JARS
     *
     * @return a list of rule jars (possibly empty).
     */
    @SuppressWarnings("MethodMayBeStatic") // Intentionally instance method so it can be overridden
    @NonNull
    public List<File> findGlobalRuleJars() {
        // Look for additional detectors registered by the user, via
        // (1) an environment variable (useful for build servers etc), and
        // (2) via jar files in the .android/lint directory
        List<File> files = null;
        try {
            String androidHome = AndroidLocation.getFolder();
            File lint = new File(androidHome + File.separator + "lint");
            if (lint.exists()) {
                File[] list = lint.listFiles();
                if (list != null) {
                    for (File jarFile : list) {
                        if (endsWith(jarFile.getName(), DOT_JAR)) {
                            if (files == null) {
                                files = new ArrayList<>();
                            }
                            files.add(jarFile);
                        }
                    }
                }
            }
        } catch (AndroidLocation.AndroidLocationException e) {
            // Ignore -- no android dir, so no rules to load.
        }

        String lintClassPath = System.getenv("ANDROID_LINT_JARS");
        if (lintClassPath != null && !lintClassPath.isEmpty()) {
            String[] paths = lintClassPath.split(File.pathSeparator);
            for (String path : paths) {
                File jarFile = new File(path);
                if (jarFile.exists()) {
                    if (files == null) {
                        files = new ArrayList<>();
                    } else if (files.contains(jarFile)) {
                        continue;
                    }
                    files.add(jarFile);
                }
            }
        }

        return files != null ? files : Collections.emptyList();
    }

    /**
     * Finds any custom lint rule jars that should be included for analysis
     * in the given project
     *
     * @param project the project to look up rule jars from
     * @return a list of rule jars (possibly empty).
     */
    @SuppressWarnings("MethodMayBeStatic") // Intentionally instance method so it can be overridden
    @NonNull
    public List<File> findRuleJars(@NonNull Project project) {
        if (project.isGradleProject()) {
            if (project.isLibrary()) {
                AndroidLibrary model = project.getGradleLibraryModel();
                if (model != null) {
                    File lintJar = model.getLintJar();
                    if (lintJar.exists()) {
                        return Collections.singletonList(lintJar);
                    }
                }
            } else if (project.getSubset() != null) {
                // Probably just analyzing a single file: we still want to look for custom
                // rules applicable to the file
                List<File> rules = null;
                final Variant variant = project.getCurrentVariant();
                if (variant != null) {
                    Collection<AndroidLibrary> libraries = variant.getMainArtifact()
                        .getDependencies().getLibraries();
                    for (AndroidLibrary library : libraries) {
                        File lintJar = library.getLintJar();
                        if (lintJar.exists()) {
                            if (rules == null) {
                                rules = Lists.newArrayListWithExpectedSize(4);
                            }
                            rules.add(lintJar);
                        }
                    }
                    if (rules != null) {
                        return rules;
                    }
                }
            } else if (project.getDir().getPath().endsWith(DOT_AAR)) {
                File lintJar = new File(project.getDir(), "lint.jar");
                if (lintJar.exists()) {
                    return Collections.singletonList(lintJar);
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Opens a URL connection.
     *
     * Clients such as IDEs can override this to for example consider the user's IDE proxy
     * settings.
     *
     * @param url the URL to read
     * @return a {@link URLConnection} or null
     * @throws IOException if any kind of IO exception occurs
     */
    @Nullable
    public URLConnection openConnection(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    /** Closes a connection previously returned by {@link #openConnection(URL)} */
    public void closeConnection(@NonNull URLConnection connection) throws IOException {
        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection)connection).disconnect();
        }
    }

    /**
     * Returns true if the given directory is a lint project directory.
     * By default, a project directory is the directory containing a manifest file,
     * but in Gradle projects for example it's the root gradle directory.
     *
     * @param dir the directory to check
     * @return true if the directory represents a lint project
     */
    @SuppressWarnings("MethodMayBeStatic") // Intentionally instance method so it can be overridden
    public boolean isProjectDirectory(@NonNull File dir) {
        return LintUtils.isManifestFolder(dir) || Project.isAospFrameworksRelatedProject(dir)
                || new File(dir, FN_BUILD_GRADLE).exists();
    }

    /**
     * Returns whether lint should look for suppress comments. Tools that already do
     * this on their own can return false here to avoid doing unnecessary work.
     */
    public boolean checkForSuppressComments() {
        return true;
    }

    /**
     * Adds in any custom lint rules and returns the result as a new issue registry,
     * or the same one if no custom rules were found
     *
     * @param registry the main registry to add rules to
     * @return a new registry containing the passed in rules plus any custom rules,
     *   or the original registry if no custom rules were found
     */
    public IssueRegistry addCustomLintRules(@NonNull IssueRegistry registry) {
        List<File> jarFiles = findGlobalRuleJars();

        if (!jarFiles.isEmpty()) {
            List<IssueRegistry> registries = Lists.newArrayListWithExpectedSize(jarFiles.size());
            registries.add(registry);
            for (File jarFile : jarFiles) {
                try {
                    registries.add(JarFileIssueRegistry.get(this, jarFile));
                } catch (Throwable e) {
                    log(e, "Could not load custom rule jar file %1$s", jarFile);
                }
            }
            if (registries.size() > 1) { // the first item is the passed in registry itself
                return new CompositeIssueRegistry(registries);
            }
        }

        return registry;
    }

    /**
     * Creates a {@link ClassLoader} which can load in a set of Jar files.
     *
     * @param urls the URLs
     * @param parent the parent class loader
     * @return a new class loader
     */
    public ClassLoader createUrlClassLoader(@NonNull URL[] urls, @NonNull ClassLoader parent) {
        return new URLClassLoader(urls, parent);
    }

    /**
     * Returns the merged manifest of the given project. This may return null
     * if not called on the main project. Note that the file reference
     * in the merged manifest isn't accurate; the merged manifest accumulates
     * information from a wide variety of locations.
     *
     * @return The merged manifest, if available.
     */
    @Nullable
    public Document getMergedManifest(@NonNull Project project) {
        List<File> manifestFiles = project.getManifestFiles();
        if (manifestFiles.size() == 1) {
            File primary = manifestFiles.get(0);
            try {
                String xml = Files.toString(primary, UTF_8);
                return XmlUtils.parseDocumentSilently(xml, true);
            } catch (IOException e) {
                log(Severity.ERROR, e, "Could not read manifest " + primary);
            }
        }

        return null;
    }

    /**
     * Key stashed as user data on merged manifest documents such that we
     * can quickly determine if a node is originally from a merged manifest (this
     * is used to automatically resolve reported errors on the merged manifest
     * back to the corresponding source locations, when possible.)
     */
    private static final String MERGED_MANIFEST = "lint-merged-manifest";

    /**
     * Record that the given document corresponds to a merged manifest file;
     * locations from this document should attempt to resolve back to the original
     * source location
     *
     * @param mergedManifest the document for the merged manifest
     * @param reportFile the manifest merger report file, or the report itself
     */
    @SuppressWarnings("MethodMayBeStatic") // clients can override
    public void resolveMergeManifestSources(@NonNull Document mergedManifest,
            @NonNull Object reportFile) {
        mergedManifest.setUserData(MERGED_MANIFEST, reportFile, null);
    }

    /**
     * Returns true if the given node is part of a merged manifest document
     * (already configured via {@link #resolveMergeManifestSources(Document, Object)})
     *
     * @param node the node to look up
     * @return true if this node is part of a merged manifest document
     */
    @SuppressWarnings("MethodMayBeStatic") // clients can override
    public boolean isMergeManifestNode(@NonNull Node node) {
        Document doc = node.getOwnerDocument();
        return doc != null && doc.getUserData(MERGED_MANIFEST) != null;
    }

    /** Cache used by {@link #findManifestSourceNode(Node)} */
    @Nullable private Map<Object,BlameFile> reportFileCache;

    /** Cache used by {@link #findManifestSourceNode(Node)} */
    @Nullable protected IdentityHashMap<Node,Pair<File,Node>> sourceNodeCache;

    protected static final Pair<File,Node> NOT_FOUND = Pair.of(null, null);

    /**
     * For the given node from a merged manifest, find the corresponding
     * source manifest node, if possible
     *
     * @param mergedNode the node from the merged manifest
     * @return the corresponding manifest node in one of the source files, if possible
     */
    @SuppressWarnings("MethodMayBeStatic") // clients can override
    @Nullable
    public Pair<File,Node> findManifestSourceNode(@NonNull Node mergedNode) {
        if (sourceNodeCache != null) {
            Pair<File,Node> source = sourceNodeCache.get(mergedNode);
            if (source != null) {
                if (source == NOT_FOUND) {
                    return null;
                }
                return source;
            }
        }

        Document doc = mergedNode.getOwnerDocument();
        if (doc == null) {
            return null;
        }
        Object report = doc.getUserData(MERGED_MANIFEST);
        if (report == null) {
            return null;
        }

        BlameFile blameFile = null;
        if (reportFileCache != null) {
            blameFile = reportFileCache.get(report);
        } else {
            reportFileCache = Maps.newHashMap();
        }
        if (blameFile == null) {
            try {
                if (report instanceof File) {
                    File file = (File) report;
                    if (file.getPath().endsWith(DOT_XML)) {
                        // Single manifest file: no manifest merging, passed source document
                        // straight through
                        return Pair.of(file, mergedNode);
                    }
                    blameFile = BlameFile.parse(file);
                } else if (report instanceof String) {
                    List<String> lines = Splitter.on('\n').splitToList((String) report);
                    blameFile = BlameFile.parse(lines);
                } else if (report instanceof Actions) {
                    blameFile = BlameFile.parse((Actions) report);
                } else {
                    assert false : report;
                    blameFile = BlameFile.NONE;
                }
            } catch (IOException ignore) {
                blameFile = BlameFile.NONE;
            }
            reportFileCache.put(report, blameFile);
        }

        Pair<File,Node> source = null;
        if (blameFile != BlameFile.NONE) {
            source = blameFile.findSourceNode(this, mergedNode);
        }

        // Cache for next time
        Pair<File,Node> cacheValue = source != null ? source : NOT_FOUND;
        if (sourceNodeCache == null) {
            sourceNodeCache = Maps.newIdentityHashMap();
        }
        sourceNodeCache.put(mergedNode, cacheValue);

        return source;
    }

    /**
     * Returns the location for a given node from a merged manifest file. Convenience
     * wrapper around {@link #findManifestSourceNode(Node)} and
     * {@link XmlParser#getLocation(File, Node)}
     */
    @Nullable
    public Location findManifestSourceLocation(@NonNull Node mergedNode) {
        Pair<File, Node> source = findManifestSourceNode(mergedNode);
        if (source != null) {
            return getXmlParser().getLocation(source.getFirst(), source.getSecond());
        }

        return null;
    }

    /**
     * Returns true if this client supports project resource repository lookup via
     * {@link #getResourceRepository(Project, boolean, boolean)}
     *
     * @return true if the client can provide project resources
     */
    public boolean supportsProjectResources() {
        return false;
    }

    /**
     * Returns the project resources, if available
     *
     * @param includeDependencies if true, include merged view of all dependencies
     * @return the project resources, or null if not available
     * @deprecated Use {@link #getResourceRepository} instead
     */
    @Deprecated
    @Nullable
    public AbstractResourceRepository getProjectResources(Project project,
            boolean includeDependencies) {
        return getResourceRepository(project, includeDependencies, false);
    }

    /**
     * Returns the project resources, if available
     *
     * @param includeModuleDependencies if true, include merged view of all module dependencies
     * @param includeLibraries          if true, include merged view of all library dependencies
     *                                  (this also requires all module dependencies)
     * @return the project resources, or null if not available
     */
    @Nullable
    public AbstractResourceRepository getResourceRepository(Project project,
            boolean includeModuleDependencies, boolean includeLibraries) {
        return null;
    }

    /**
     * For a lint client which supports resource items (via {@link #supportsProjectResources()})
     * return a handle for a resource item
     *
     * @param item the resource item to look up a location handle for
     * @return a corresponding handle
     */
    @NonNull
    public Location.Handle createResourceItemHandle(@NonNull ResourceItem item) {
        return new Location.ResourceItemHandle(item);
    }

    private ResourceVisibilityLookup.Provider resourceVisibility;

    /**
     * Returns a shared {@link ResourceVisibilityLookup.Provider}
     *
     * @return a shared provider for looking up resource visibility
     */
    @NonNull
    public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
        if (resourceVisibility == null) {
            resourceVisibility = new ResourceVisibilityLookup.Provider();
        }
        return resourceVisibility;
    }

    /**
     * The client name returned by {@link #getClientName()} when running in
     * Android Studio/IntelliJ IDEA
     */
    public static final String CLIENT_STUDIO = "studio";

    /**
     * The client name returned by {@link #getClientName()} when running in
     * Gradle
     */
    public static final String CLIENT_GRADLE = "gradle";

    /**
     * The client name returned by {@link #getClientName()} when running in
     * the CLI (command line interface) version of lint, {@code lint}
     */
    public static final String CLIENT_CLI = "cli";

    /**
     * The client name returned by {@link #getClientName()} when running in
     * some unknown client
     */
    public static final String CLIENT_UNKNOWN = "unknown";

    /** The client name. */
    @NonNull
    private static String clientName = CLIENT_UNKNOWN;

    /**
     * Returns the name of the embedding client. It could be not just
     * {@link #CLIENT_STUDIO}, {@link #CLIENT_GRADLE}, {@link #CLIENT_CLI}
     * etc but other values too as lint is integrated in other embedding contexts.
     *
     * @return the name of the embedding client
     */
    @NonNull
    public static String getClientName() {
        return clientName;
    }


    /** Returns the version number of this lint client, if known */
    @Nullable
    public String getClientRevision() {
        return null;
    }

    /**
     * Returns true if the embedding client currently running lint is Android Studio
     * (or IntelliJ IDEA)
     *
     * @return true if running in Android Studio / IntelliJ IDEA
     */
    public static boolean isStudio() {
        return CLIENT_STUDIO.equals(clientName);
    }

    /**
     * Returns true if the embedding client currently running lint is Gradle
     *
     * @return true if running in Gradle
     */
    public static boolean isGradle() {
        return CLIENT_GRADLE.equals(clientName);
    }

    /**
     * Runs the given runnable under a readlock such that it can access the PSI
     *
     * @param runnable the runnable to be run
     */
    public void runReadAction(@NonNull Runnable runnable) {
        runnable.run();
    }

    /** Returns a repository logger used by this client. */
    @NonNull
    public ProgressIndicator getRepositoryLogger() {
        return new RepoLogger();
    }

    private static final class RepoLogger extends ProgressIndicatorAdapter {
        // Intentionally not logging these: the SDK manager is
        // logging events such as package.xml parsing
        //   Parsing /path/to/sdk//build-tools/19.1.0/package.xml
        //   Parsing /path/to/sdk//build-tools/20.0.0/package.xml
        //   Parsing /path/to/sdk//build-tools/21.0.0/package.xml
        // which we don't want to spam on the console.
        // It's also warning about packages that it's encountering
        // multiple times etc; that's not something we should include
        // in lint command line output.

        @Override
        public void logError(@NonNull String s, @Nullable Throwable e) {
        }

        @Override
        public void logInfo(@NonNull String s) {
        }

        @Override
        public void logWarning(@NonNull String s, @Nullable Throwable e) {
        }
    }
}
