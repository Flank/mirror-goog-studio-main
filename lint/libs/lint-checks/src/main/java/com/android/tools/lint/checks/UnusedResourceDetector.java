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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_DISCARD;
import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_SHRINK_MODE;
import static com.android.SdkConstants.ATTR_VIEW_BINDING_IGNORE;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_KT;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.android.utils.XmlUtils.getFirstSubTagByName;
import static com.android.utils.XmlUtils.getNextTagByName;
import static com.google.common.base.Charsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelResourceField;
import com.android.tools.lint.model.LintModelSourceProvider;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.XmlUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallableReferenceExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Finds unused resources. */
public class UnusedResourceDetector extends ResourceXmlDetector
        implements SourceCodeScanner, BinaryResourceScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION;

    public static final String EXCLUDE_TESTS_PROPERTY = "lint.unused-resources.exclude-tests";
    public static final String INCLUDE_TESTS_PROPERTY = "lint.unused-resources.include-tests";

    static {
        EnumSet<Scope> scopeSet =
                EnumSet.of(
                        Scope.MANIFEST,
                        Scope.ALL_RESOURCE_FILES,
                        Scope.ALL_JAVA_FILES,
                        Scope.BINARY_RESOURCE_FILE);

        // Whether to include test sources in the scope. Currently true but controllable
        // with a couple of flags.
        if (VALUE_TRUE.equals(System.getProperty(INCLUDE_TESTS_PROPERTY))
                || !VALUE_FALSE.equals(System.getProperty(EXCLUDE_TESTS_PROPERTY))) {
            scopeSet.add(Scope.TEST_SOURCES);
        }

        IMPLEMENTATION = new Implementation(UnusedResourceDetector.class, scopeSet);
    }

    private static final String EXCLUDING_TESTS_EXPLANATION =
            ""
                    + "The unused resource check can ignore tests. If you want to include "
                    + "resources that are only referenced from tests, consider packaging them "
                    + "in a test source set instead.\n"
                    + "\n"
                    + "You can include test sources in the unused resource check by setting "
                    + "the system property "
                    + INCLUDE_TESTS_PROPERTY
                    + "=true, and to "
                    + "exclude them (usually for performance reasons), use "
                    + EXCLUDE_TESTS_PROPERTY
                    + "=true.";

    /** Unused resources (other than ids). */
    public static final Issue ISSUE =
            Issue.create(
                    "UnusedResources",
                    "Unused resources",
                    "Unused resources make applications larger and slow down builds.\n"
                            + "\n"
                            + EXCLUDING_TESTS_EXPLANATION,
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Unused id's */
    public static final Issue ISSUE_IDS =
            Issue.create(
                            "UnusedIds",
                            "Unused id",
                            "This resource id definition appears not to be needed since it is not referenced "
                                    + "from anywhere. Having id definitions, even if unused, is not necessarily a bad "
                                    + "idea since they make working on layouts and menus easier, so there is not a "
                                    + "strong reason to delete these.\n"
                                    + "\n"
                                    + EXCLUDING_TESTS_EXPLANATION,
                            Category.PERFORMANCE,
                            1,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setEnabledByDefault(false);

    private final UnusedResourceDetectorUsageModel model = new UnusedResourceDetectorUsageModel();

    private boolean projectUsesViewBinding = false;
    /**
     * Map of data binding / view binding Binding classes (simple names, not fully qualified names)
     * to corresponding layout resource names (e.g. ActivityMainBinding -> "activity_main.xml")
     *
     * <p>This map is created lazily only once it encounters a relevant layout file, since a
     * significant enough number of modules don't use data binding or view binding.
     */
    @Nullable private Map<String, String> bindingClasses;

    /**
     * Whether the resource detector will look for inactive resources (e.g. resource and code
     * references in source sets that are not the primary/active variant)
     */
    public static boolean sIncludeInactiveReferences = true;

    /** Constructs a new {@link UnusedResourceDetector} */
    public UnusedResourceDetector() {}

    private void addDynamicResources(@NonNull Context context) {
        Project project = context.getProject();
        LintModelVariant variant = project.getBuildVariant();
        if (variant != null) {
            recordManifestPlaceHolderUsages(variant.getManifestPlaceholders());
            addDynamicResources(project, variant.getResValues());
        }
    }

    private void recordManifestPlaceHolderUsages(Map<String, String> manifestPlaceholders) {
        for (String value : manifestPlaceholders.values()) {
            Resource resource = model.getResourceFromUrl(value);
            ResourceUsageModel.markReachable(resource);
        }
    }

    private void addDynamicResources(
            @NonNull Project project, @NonNull Map<String, LintModelResourceField> resValues) {
        Set<String> keys = resValues.keySet();
        if (!keys.isEmpty()) {
            Location location = Lint.guessGradleLocation(project);
            for (String name : keys) {
                LintModelResourceField field = resValues.get(name);
                ResourceType type = ResourceType.fromClassName(field.getType());
                if (type == null) {
                    // Highly unlikely. This would happen if in the future we add
                    // some new ResourceType, that the Gradle plugin (and the user's
                    // Gradle file is creating) and it's an older version of Studio which
                    // doesn't yet have this ResourceType in its enum.
                    continue;
                }
                LintResource resource = (LintResource) model.declareResource(type, name, null);
                resource.recordLocation(location);
            }
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        projectUsesViewBinding = false;
        LintModelVariant variant = context.getProject().getBuildVariant();
        if (variant != null) {
            projectUsesViewBinding = variant.getBuildFeatures().getViewBinding();
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.getMainProject().isLibrary() && LintClient.isGradle()) {
            // In Gradle, don't report unused resources for a library project;
            // these are usually processed separately (without the main app
            // module) and results in a lot of false positives. To get unused
            // resources accounted correctly, run the check on the app project,
            // preferably with checkDependencies true.
            return;
        }

        if (context.getPhase() == 1) {
            Project project = context.getProject();

            // Look for source sets that aren't part of the active variant;
            // we need to make sure we find references in those source sets as well
            // such that we don't incorrectly remove resources that are
            // used by some other source set.
            // In Gradle etc we don't need to do this (and in large projects it's expensive)
            if (sIncludeInactiveReferences && !project.isLibrary() && LintClient.isStudio()) {
                LintModelVariant variant = project.getBuildVariant();
                if (variant != null) {
                    addInactiveReferences(variant);
                }
            }

            addDynamicResources(context);
            model.processToolsAttributes();

            List<Resource> unusedResources = model.findUnused();
            Set<Resource> unused = Sets.newHashSetWithExpectedSize(unusedResources.size());
            for (Resource resource : unusedResources) {
                if (resource.isDeclared()
                        && !resource.isPublic()
                        && resource.type != ResourceType.PUBLIC) {
                    unused.add(resource);
                }
            }

            // Remove id's if the user has disabled reporting issue ids
            if (!unused.isEmpty() && !context.isEnabled(ISSUE_IDS)) {
                // Remove all R.id references
                List<Resource> ids = Lists.newArrayList();
                for (Resource resource : unused) {
                    if (resource.type == ResourceType.ID) {
                        ids.add(resource);
                    }
                }
                unused.removeAll(ids);
            }

            if (!unused.isEmpty()) {
                model.unused = unused;

                // Request another pass, and in the second pass we'll gather location
                // information for all declaration locations we've found
                context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
        } else {
            assert context.getPhase() == 2;

            // Report any resources that we (for some reason) could not find a declaration
            // location for
            Collection<Resource> unused = model.unused;
            if (!unused.isEmpty()) {
                // Final pass: we may have marked a few resource declarations with
                // tools:ignore; we don't check that on every single element, only those
                // first thought to be unused. We don't just remove the elements explicitly
                // marked as unused, we revisit everything transitively such that resources
                // referenced from the ignored/kept resource are also kept.
                unused = model.findUnused(Lists.newArrayList(unused));
                if (unused.isEmpty()) {
                    return;
                }

                // Fill in locations for files that we didn't encounter in other ways
                for (Resource r : unused) {
                    LintResource resource = (LintResource) r;
                    Location location = resource.locations;
                    //noinspection VariableNotUsedInsideIf
                    if (location != null) {
                        continue;
                    }

                    // Try to figure out the file if it's a file based resource (such as R.layout);
                    // in that case we can figure out the filename since it has a simple mapping
                    // from the resource name (though the presence of qualifiers like -land etc
                    // makes it a little tricky if there's no base file provided)
                    ResourceType type = resource.type;
                    if (type != null && Lint.isFileBasedResourceType(type)) {
                        String name = resource.name;

                        List<File> folders = Lists.newArrayList();
                        List<File> resourceFolders = context.getProject().getResourceFolders();
                        for (File res : resourceFolders) {
                            File[] f = res.listFiles();
                            if (f != null) {
                                folders.addAll(Arrays.asList(f));
                            }
                        }
                        if (!folders.isEmpty()) {
                            // Process folders in alphabetical order such that we process
                            // based folders first: we want the locations in base folder
                            // order
                            folders.sort(Comparator.comparing(File::getName));
                            for (File folder : folders) {
                                if (folder.getName().startsWith(type.getName())) {
                                    File[] files = folder.listFiles();
                                    if (files != null) {
                                        Arrays.sort(files);
                                        for (File file : files) {
                                            String fileName = file.getName();
                                            if (fileName.startsWith(name)
                                                    && fileName.startsWith(".", name.length())) {
                                                resource.recordLocation(Location.create(file));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                List<Resource> sorted = Lists.newArrayList(unused);
                Collections.sort(sorted);

                Boolean skippedLibraries = null;

                for (Resource resource : sorted) {
                    Location location = ((LintResource) resource).locations;
                    if (location != null) {
                        // We were prepending locations, but we want to prefer the base folders
                        location = Location.reverse(location);
                    }

                    if (location == null) {
                        if (skippedLibraries == null) {
                            skippedLibraries = false;
                            for (Project project : context.getDriver().getProjects()) {
                                if (!project.getReportIssues()) {
                                    skippedLibraries = true;
                                    break;
                                }
                            }
                        }
                        if (skippedLibraries) {
                            // Skip this resource if we don't have a location, and one or
                            // more library projects were skipped; the resource was very
                            // probably defined in that library project and only encountered
                            // in the main project's java R file
                            continue;
                        }
                    }

                    String field = resource.getField();
                    String message =
                            String.format("The resource `%1$s` appears to be unused", field);
                    if (location == null) {
                        location = Location.create(context.getProject().getDir());
                    }
                    LintFix fix = fix().data(field);
                    context.report(getIssue(resource), location, message, fix);
                }
            }
        }
    }

    private void recordInactiveJavaReferences(@NonNull File resDir) {
        File[] files = resDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    recordInactiveJavaReferences(file);
                } else {
                    String path = file.getPath();
                    boolean isJava = path.endsWith(DOT_JAVA);
                    if (isJava || path.endsWith(DOT_KT)) {
                        try {
                            String code = Files.asCharSource(file, UTF_8).read();
                            if (isJava) {
                                model.tokenizeJavaCode(code);
                            } else {
                                model.tokenizeKotlinCode(code);
                            }
                        } catch (Throwable ignore) {
                            // Tolerate parsing errors etc in these files; they're user
                            // sources, and this is even for inactive source sets.
                        }
                    }
                }
            }
        }
    }

    private void recordInactiveXmlResources(@NonNull File resDir) {
        File[] resourceFolders = resDir.listFiles();
        if (resourceFolders != null) {
            for (File folder : resourceFolders) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
                if (folderType != null) {
                    recordInactiveXmlResources(folderType, folder);
                }
            }
        }
    }

    // Used for traversing resource folders *outside* of the normal Gradle variant
    // folders: these are not necessarily on the project path, so we don't have PSI files
    // for them
    private void recordInactiveXmlResources(
            @NonNull ResourceFolderType folderType, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String path = file.getPath();
                boolean isXml = endsWithIgnoreCase(path, DOT_XML);
                try {
                    if (isXml) {
                        String xml = Files.asCharSource(file, UTF_8).read();
                        Document document = XmlUtils.parseDocument(xml, true);
                        model.visitXmlDocument(file, folderType, document);
                    } else {
                        model.visitBinaryResource(folderType, file);
                    }
                } catch (Throwable ignore) {
                    // Tolerate parsing errors etc in these files; they're user
                    // sources, and this is even for inactive source sets.
                }
            }
        }
    }

    private void addInactiveReferences(@NonNull LintModelVariant active) {
        LintModelModule module = active.getModule();
        for (LintModelSourceProvider provider : module.getInactiveSourceProviders(active)) {
            for (File res : provider.getResDirectories()) {
                // Scan resource directory
                if (res.isDirectory()) {
                    recordInactiveXmlResources(res);
                }
            }
            for (File file : provider.getJavaDirectories()) {
                // Scan Java directory
                if (file.isDirectory()) {
                    recordInactiveJavaReferences(file);
                }
            }
        }
    }

    private static Issue getIssue(@NonNull Resource resource) {
        return resource.type != ResourceType.ID ? ISSUE : ISSUE_IDS;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        model.context = context;
        try {
            model.visitBinaryResource(context.getResourceFolderType(), context.file);
        } finally {
            model.context = null;
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        model.context = model.xmlContext = context;
        try {
            ResourceFolderType folderType = context.getResourceFolderType();
            model.visitXmlDocument(context.file, folderType, document);

            // Data binding layout? If so look for usages of the binding class too
            Element root = document.getDocumentElement();
            if (root != null && folderType == ResourceFolderType.LAYOUT) {
                // Data Binding layouts have a root <layout> tag
                if (TAG_LAYOUT.equals(root.getTagName())) {
                    if (bindingClasses == null) {
                        bindingClasses = Maps.newHashMap();
                    }

                    // By default, a data binding class name is derived from the name of the XML
                    // file, but this can be overridden with a custom name using the
                    // {@code <data class="..." />} attribute.
                    String fileName = context.file.getName();
                    String resourceName = Lint.getBaseName(fileName);
                    Element data = getFirstSubTagByName(root, TAG_DATA);
                    String bindingClass = null;
                    while (data != null) {
                        bindingClass = data.getAttribute(ATTR_CLASS);
                        if (bindingClass != null && !bindingClass.isEmpty()) {
                            int dot = bindingClass.lastIndexOf('.');
                            bindingClass = bindingClass.substring(dot + 1);
                            break;
                        }
                        data = getNextTagByName(data, TAG_DATA);
                    }
                    if (bindingClass == null || bindingClass.isEmpty()) {
                        // See ResourceBundle#getFullBindingClass
                        bindingClass = toClassName(resourceName) + "Binding";
                    }
                    bindingClasses.put(bindingClass, resourceName);
                } else if (projectUsesViewBinding) {
                    // ViewBinding always derives its name from the layout file. However, a layout
                    // file should be skipped if the root tag contains the "viewBindingIgnore=true"
                    // attribute.
                    String ignoreAttribute =
                            root.getAttributeNS(TOOLS_URI, ATTR_VIEW_BINDING_IGNORE);
                    if (!VALUE_TRUE.equals(ignoreAttribute)) {
                        if (bindingClasses == null) {
                            bindingClasses = Maps.newHashMap();
                        }
                        String fileName = context.file.getName();
                        String resourceName = Lint.getBaseName(fileName);
                        String bindingClass = toClassName(resourceName) + "Binding";

                        bindingClasses.put(bindingClass, resourceName);
                    }
                }
            }
        } finally {
            model.context = model.xmlContext = null;
        }
    }

    // Copy from android.databinding.tool.util.ParserHelper:
    public static String toClassName(String name) {
        StringBuilder builder = new StringBuilder();
        for (String item : name.split("[_-]")) {
            builder.append(capitalize(item));
        }
        return builder.toString();
    }

    // Copy from android.databinding.tool.util.StringUtils: using
    // this instead of IntelliJ's more flexible method to ensure
    // we compute the same names as data-binding generated code
    private static String capitalize(String string) {
        if (Strings.isNullOrEmpty(string)) {
            return string;
        }
        char ch = string.charAt(0);
        if (Character.isTitleCase(ch)) {
            return string;
        }
        return Character.toTitleCase(ch) + string.substring(1);
    }

    // ---- implements SourceCodeScanner ----

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        if (!isFramework) {
            ResourceUsageModel.markReachable(model.addResource(type, name, null));
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                USimpleNameReferenceExpression.class, UCallableReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        // If using data binding / view binding, we also have to look for references to the
        // Binding classes which could be implicit usages of layout resources
        if (bindingClasses == null) {
            return null;
        }

        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {

                String name = expression.getIdentifier();
                String resourceName = bindingClasses.get(name);
                if (resourceName != null) {
                    // Make sure it's really a binding class
                    PsiElement resolved = expression.resolve();
                    if (resolved instanceof PsiClass) {
                        JavaEvaluator evaluator = context.getEvaluator();
                        PsiClass binding = (PsiClass) resolved;
                        if (isBindingClass(evaluator, binding)) {
                            ResourceUsageModel.markReachable(
                                    model.getResource(ResourceType.LAYOUT, resourceName));
                        }
                    }
                }
            }

            private boolean isBindingClass(JavaEvaluator evaluator, PsiClass binding) {
                return evaluator.extendsClass(binding, "android.databinding.ViewDataBinding", true)
                        || evaluator.extendsClass(
                                binding, "androidx.databinding.ViewDataBinding", true)
                        || evaluator.extendsClass(
                                binding, "androidx.viewbinding.ViewBinding", true);
            }

            @Override
            public void visitCallableReferenceExpression(
                    @NonNull UCallableReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (resolved instanceof PsiMember) {
                    PsiClass psiClass = ((PsiMember) resolved).getContainingClass();
                    if (psiClass != null) {
                        String resourceName = bindingClasses.get(psiClass.getName());
                        if (resourceName != null
                                && isBindingClass(context.getEvaluator(), psiClass)) {
                            ResourceUsageModel.markReachable(
                                    model.getResource(ResourceType.LAYOUT, resourceName));
                        }
                    }
                }
            }
        };
    }

    private static class LintResource extends Resource {
        /** Chained list of declaration locations */
        public Location locations;

        public LintResource(ResourceType type, String name, int value) {
            super(type, name, value);
        }

        public void recordLocation(@NonNull Location location) {
            Location oldLocation = this.locations;
            if (oldLocation != null) {
                location.setSecondary(oldLocation);
            }
            this.locations = location;
        }
    }

    private static class UnusedResourceDetectorUsageModel extends ResourceUsageModel {
        public XmlContext xmlContext;
        public Context context;
        public Set<Resource> unused = Sets.newHashSet();

        @NonNull
        @Override
        protected Resource createResource(
                @NonNull ResourceType type, @NonNull String name, int realValue) {
            return new LintResource(type, name, realValue);
        }

        @NonNull
        @Override
        protected String readText(@NonNull File file) {
            if (context != null) {
                return context.getClient().readFile(file).toString();
            }
            return super.readText(file);
        }

        @Override
        protected Resource declareResource(ResourceType type, String name, Node node) {
            if (name.isEmpty()) {
                return null;
            }
            LintResource resource = (LintResource) super.declareResource(type, name, node);
            if (context != null) {
                resource.setDeclared(context.getProject().getReportIssues());
                if (context.getPhase() == 2 && unused.contains(resource)) {
                    if (xmlContext != null
                            && xmlContext
                                    .getDriver()
                                    .isSuppressed(xmlContext, getIssue(resource), node)) {
                        resource.setKeep(true);
                    } else {
                        // For positions we try to use the name node rather than the
                        // whole declaration element
                        if (node == null || xmlContext == null) {
                            resource.recordLocation(Location.create(context.file));
                        } else {
                            if (node instanceof Element) {
                                Node attribute = ((Element) node).getAttributeNode(ATTR_NAME);
                                if (attribute != null) {
                                    node = attribute;
                                }
                            }
                            resource.recordLocation(xmlContext.getLocation(node));
                        }
                    }
                }

                if (type == ResourceType.RAW && isKeepFile(name, xmlContext)) {
                    // Don't flag raw.keep: these are used for resource shrinking
                    // keep lists
                    //    https://developer.android.com/studio/build/shrink-code.html
                    resource.setReachable(true);
                }
            }

            return resource;
        }

        private static boolean isKeepFile(@NonNull String name, @Nullable XmlContext xmlContext) {
            if ("keep".equals(name)) {
                return true;
            }

            if (xmlContext != null && xmlContext.document != null) {
                Element element = xmlContext.document.getDocumentElement();
                if (element != null && element.getFirstChild() == null) {
                    NamedNodeMap attributes = element.getAttributes();
                    boolean found = false;
                    for (int i = 0, n = attributes.getLength(); i < n; i++) {
                        Node attr = attributes.item(i);
                        String nodeName = attr.getNodeName();
                        if (!nodeName.startsWith(XMLNS_PREFIX)
                                && !nodeName.startsWith(TOOLS_PREFIX)
                                && !TOOLS_URI.equals(attr.getNamespaceURI())) {
                            return false;
                        } else if (nodeName.endsWith(ATTR_SHRINK_MODE)
                                || nodeName.endsWith(ATTR_DISCARD)
                                || nodeName.endsWith(ATTR_KEEP)) {
                            found = true;
                        }
                    }

                    return found;
                }
            }

            return false;
        }
    }
}
