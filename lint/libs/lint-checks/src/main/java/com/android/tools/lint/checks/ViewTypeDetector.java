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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ANDROID_VIEW_PKG;
import static com.android.SdkConstants.ANDROID_WEBKIT_PKG;
import static com.android.SdkConstants.ANDROID_WIDGET_PREFIX;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.CharSequences;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UBinaryExpressionWithType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Detector for finding inconsistent usage of views and casts
 * <p>
 * TODO: Check findFragmentById
 * <pre>
 * ((ItemListFragment) getSupportFragmentManager()
 *   .findFragmentById(R.id.item_list))
 *   .setActivateOnItemClick(true);
 * </pre>
 * Here we should check the {@code <fragment>} tag pointed to by the id, and
 * check its name or class attributes to make sure the cast is compatible with
 * the named fragment class!
 */
public class ViewTypeDetector extends ResourceXmlDetector implements UastScanner {
    /** Mismatched view types */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE = Issue.create(
            "WrongViewCast",
            "Mismatched view type",
            "Keeps track of the view types associated with ids and if it finds a usage of " +
            "the id in the Java code it ensures that it is treated as the same type.",
            Category.CORRECTNESS,
            9,
            Severity.FATAL,
            new Implementation(
                    ViewTypeDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES),
                    Scope.JAVA_FILE_SCOPE));

    /** Flag used to do no work if we're running in incremental mode in a .java file without
     * a client supporting project resources */
    private Boolean mIgnore = null;

    private final Map<String, Object> mIdToViewTag = new HashMap<>(50);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String view = attribute.getOwnerElement().getTagName();
        String value = attribute.getValue();
        String id = null;
        if (value.startsWith(ID_PREFIX)) {
            id = value.substring(ID_PREFIX.length());
        } else if (value.startsWith(NEW_ID_PREFIX)) {
            id = value.substring(NEW_ID_PREFIX.length());
        } // else: could be @android id

        if (id != null) {
            if (view.equals(VIEW_TAG)) {
                view = attribute.getOwnerElement().getAttribute(ATTR_CLASS);
            }

            Object existing = mIdToViewTag.get(id);
            if (existing == null) {
                mIdToViewTag.put(id, view);
            } else if (existing instanceof String) {
                String existingString = (String) existing;
                if (!existingString.equals(view)) {
                    // Convert to list
                    List<String> list = new ArrayList<>(2);
                    list.add((String) existing);
                    list.add(view);
                    mIdToViewTag.put(id, list);
                }
            } else if (existing instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) existing;
                if (!list.contains(view)) {
                    list.add(view);
                }
            }
        }
    }

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("findViewById");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        LintClient client = context.getClient();
        if (mIgnore == Boolean.TRUE) {
            return;
        } else if (mIgnore == null) {
            mIgnore = !context.getScope().contains(Scope.ALL_RESOURCE_FILES) &&
                    !client.supportsProjectResources();
            if (mIgnore) {
                return;
            }
        }
        assert method.getName().equals("findViewById");
        UElement node = LintUtils.skipParentheses(call);
        if (node != null && node.getUastParent() instanceof UBinaryExpressionWithType) {
            UBinaryExpressionWithType cast = (UBinaryExpressionWithType) node.getUastParent();
            PsiType type = cast.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            PsiClassType castType = (PsiClassType) type;
            String castTypeClass = type.getCanonicalText();

            List<UExpression> args = call.getValueArguments();
            if (args.size() == 1) {
                UExpression first = args.get(0);
                ResourceUrl resourceUrl = ResourceEvaluator.getResource(context.getEvaluator(),
                        first);
                if (resourceUrl != null && resourceUrl.type == ResourceType.ID &&
                        !resourceUrl.framework) {
                    String id = resourceUrl.name;

                    if (client.supportsProjectResources()) {
                        AbstractResourceRepository resources = client
                                .getResourceRepository(context.getMainProject(), true, false);
                        if (resources == null) {
                            return;
                        }

                        List<ResourceItem> items = resources.getResourceItem(ResourceType.ID,
                                id);
                        if (items != null && !items.isEmpty()) {
                            Set<String> compatible = Sets.newHashSet();
                            for (ResourceItem item : items) {
                                Collection<String> tags = getViewTags(context, item);
                                if (tags != null) {
                                   compatible.addAll(tags);
                                }
                            }
                            if (!compatible.isEmpty()) {
                                ArrayList<String> layoutTypes = Lists.newArrayList(compatible);
                                checkCompatible(context, castType, castTypeClass, null,
                                        layoutTypes, cast);
                            }
                        }
                    } else {
                        Object types = mIdToViewTag.get(id);
                        if (types instanceof String) {
                            String layoutType = (String) types;
                            checkCompatible(context, castType, castTypeClass, layoutType, null,
                                    cast);
                        } else if (types instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<String> layoutTypes = (List<String>) types;
                            checkCompatible(context, castType, castTypeClass, null, layoutTypes,
                                    cast);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    protected Collection<String> getViewTags(
            @NonNull Context context,
            @NonNull ResourceItem item) {
        // Check view tag in this file. Can I do it cheaply? Try with
        // an XML pull parser. Or DOM if we have multiple resources looked
        // up?
        ResourceFile source = item.getSource();
        if (source != null) {
            File file = source.getFile();
            Multimap<String,String> map = getIdToTagsIn(context, file);
            if (map != null) {
                return map.get(item.getName());
            }
        }

        return null;
    }


    private Map<File, Multimap<String, String>> fileIdMap;

    @Nullable
    private Multimap<String, String> getIdToTagsIn(@NonNull Context context, @NonNull File file) {
        if (!file.getPath().endsWith(DOT_XML)) {
            return null;
        }
        if (fileIdMap == null) {
            fileIdMap = Maps.newHashMap();
        }
        Multimap<String, String> map = fileIdMap.get(file);
        if (map == null) {
            map = ArrayListMultimap.create();
            fileIdMap.put(file, map);

            CharSequence xml = context.getClient().readFile(file);
            // TODO: Use pull parser instead for better performance!
            // See LayoutInflationDetector#hasLayoutParams for an example
            Document document = CharSequences.parseDocumentSilently(xml, true);
            if (document != null && document.getDocumentElement() != null) {
                addViewTags(map, document.getDocumentElement());
            }
        }
        return map;
    }

    private static void addViewTags(Multimap<String, String> map, Element element) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            id = LintUtils.stripIdPrefix(id);
            if (!map.containsEntry(id, element.getTagName())) {
                map.put(id, element.getTagName());
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                addViewTags(map, (Element) child);
            }
        }
    }

    /** Check if the view and cast type are compatible */
    private static void checkCompatible(
            @NonNull JavaContext context,
            @NonNull PsiClassType castType,
            @NonNull String castTypeClass,
            @Nullable String tag,
            @Nullable List<String> tags,
            @NonNull UBinaryExpressionWithType node) {
        assert tag == null || tags == null; // Should only specify one or the other

        // Common case: they match: quickly check for this and fail if not
        if (castTypeClass.equals(tag) ||
                tags != null && tags.contains(castTypeClass)) {
            return;
        }


        PsiClass castClass = castType.resolve();

        boolean compatible = true;
        if (tag != null) {
            if (!tag.equals(castTypeClass)
                    && !context.getSdkInfo().isSubViewOf(castTypeClass, tag)) {
                compatible = false;
            }
        } else {
            compatible = false;
            assert tags != null;
            for (String type : tags) {
                if (type.equals(castTypeClass)
                        || context.getSdkInfo().isSubViewOf(castTypeClass, type)) {
                    compatible = true;
                    break;
                }
            }
        }

        // Use real classes to handle checks
        if (castClass != null && !compatible) {
            if (tag != null) {
                if (isCompatible(context, castClass, tag)) {
                    return;
                }
            } else {
                for (String t : tags) {
                    if (isCompatible(context, castClass, t)) {
                        return;
                    }
                }
            }
        }

        if (!compatible) {
            if (tag == null) {
                tag = Joiner.on("|").join(tags);
            }
            String message = String.format(
                    "Unexpected cast to `%1$s`: layout tag was `%2$s`",
                    castTypeClass.substring(castTypeClass.lastIndexOf('.') + 1), tag);
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    private static boolean isCompatible(
            @NonNull JavaContext context,
            @NonNull PsiClass castClass,
            @NonNull String tag) {
        PsiClass cls = null;
        if (tag.indexOf('.') == -1) {
            for (String prefix : new String[]{
                    // See framework's PhoneLayoutInflater: these are the prefixes
                    // that don't need fully qualified names in layouts
                    ANDROID_WIDGET_PREFIX,
                    ANDROID_VIEW_PKG,
                    ANDROID_WEBKIT_PKG}) {
                cls = context.getEvaluator().findClass(prefix + tag);
                //noinspection VariableNotUsedInsideIf
                if (cls != null) {
                    break;
                }
            }
        } else {
            cls = context.getEvaluator().findClass(tag);
        }

        if (cls != null) {
            return cls.isInheritor(castClass, true);
        }

        // Didn't find class - just assume it's compatible since we don't want false positives
        return true;
    }
}
