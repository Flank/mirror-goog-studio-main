/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.ide.common.resources.AbstractResourceRepository.MAX_RESOURCE_INDIRECTION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import java.util.List;

/**
 * Like {@link ResourceResolver} but for a single item, so it does not need the full resource maps
 * to be resolved up front. Typically used for cases where we may not have fully configured
 * resource maps and we need to look up a specific value, such as in Android Studio where
 * a color reference is found in an XML style file, and we want to resolve it in order to
 * display the final resolved color in the editor margin.
 */
public class ResourceItemResolver extends RenderResources {
    private final FolderConfiguration myConfiguration;
    private final LayoutLog myLogger;
    private final ResourceProvider myResourceProvider;
    private ResourceResolver myResolver;
    private AbstractResourceRepository myFrameworkResources;
    private AbstractResourceRepository myAppResources;
    @Nullable private List<ResourceValue> myLookupChain;

    public ResourceItemResolver(
            @NonNull FolderConfiguration configuration,
            @NonNull ResourceProvider resourceProvider,
            @Nullable LayoutLog logger) {
        myConfiguration = configuration;
        myResourceProvider = resourceProvider;
        myLogger = logger;
        myResolver = resourceProvider.getResolver(false);
    }

    public ResourceItemResolver(
            @NonNull FolderConfiguration configuration,
            @NonNull AbstractResourceRepository frameworkResources,
            @NonNull AbstractResourceRepository appResources,
            @Nullable LayoutLog logger) {
        myConfiguration = configuration;
        myResourceProvider = null;
        myLogger = logger;
        myFrameworkResources = frameworkResources;
        myAppResources = appResources;
    }

    @Override
    @Nullable
    public ResourceValue resolveResValue(@Nullable ResourceValue resValue) {
        if (myResolver != null) {
            return myResolver.resolveResValue(resValue);
        }
        if (myLookupChain != null) {
            myLookupChain.add(resValue);
        }
        return resolveResValue(resValue, 0);
    }

    @Nullable
    private ResourceValue resolveResValue(@Nullable ResourceValue resValue, int depth) {
        if (resValue == null) {
            return null;
        }

        String value = resValue.getValue();
        if (value == null || resValue instanceof ArrayResourceValue) {
            // If there's no value or this an array resource (eg. <string-array>), return.
            return resValue;
        }

        // else attempt to find another ResourceValue referenced by this one.
        ResourceValue resolvedResValue = findResValue(value, resValue.isFramework());

        // if the value did not reference anything, then we simply return the input value
        if (resolvedResValue == null) {
            return resValue;
        }

        // detect potential loop due to mishandled namespace in attributes
        if (resValue == resolvedResValue || depth >= MAX_RESOURCE_INDIRECTION) {
            if (myLogger != null) {
                myLogger.error(
                        LayoutLog.TAG_BROKEN,
                        String.format(
                                "Potential stack overflow trying to resolve '%s': "
                                        + "cyclic resource definitions? Render may not be accurate.",
                                value),
                        null);
            }
            return resValue;
        }

        // Otherwise, we attempt to resolve this new value as well.
        return resolveResValue(resolvedResValue, depth + 1);
    }

    @Override
    @Nullable
    public ResourceValue dereference(@NonNull ResourceValue value) {
        if (myResolver != null) {
            return myResolver.dereference(value);
        }

        if (myLookupChain != null
                && !myLookupChain.isEmpty()
                && !myLookupChain.get(myLookupChain.size() - 1).equals(value)) {
            myLookupChain.add(value);
        }

        String valueText = value.getValue();
        if (valueText == null) {
            return null;
        }

        ResourceUrl url = ResourceUrl.parse(valueText);
        if (url != null && url.hasValidName()) {
            if (url.isTheme()) {
                // Do theme lookup? We can't do that here; requires full global analysis,
                // so just use a real resource resolver.
                ResourceResolver resolver = getFullResolver();
                if (resolver != null) {
                    return resolver.dereference(value);
                } else {
                    return null;
                }
            }

            // TODO(namespaces)
            return findResValue(url);
        }

        // Looks like the value didn't reference anything. Return null.
        return null;
    }

    @Nullable
    private ResourceValue findResValue(ResourceUrl url) {
        // map of ResourceValue for the given type
        // if allowed, search in the project resources first.
        if (!url.isFramework()) {
            if (myAppResources == null) {
                assert myResourceProvider != null;
                myAppResources = myResourceProvider.getAppResources();
                if (myAppResources == null) {
                    return null;
                }
            }
            ResourceValue item;
            item = myAppResources.getConfiguredValue(url.type, url.name, myConfiguration);
            if (item != null) {
                if (myLookupChain != null) {
                    myLookupChain.add(item);
                }
                return item;
            }
        } else {
            if (myFrameworkResources == null) {
                assert myResourceProvider != null;
                myFrameworkResources = myResourceProvider.getFrameworkResources();
                if (myFrameworkResources == null) {
                    return null;
                }
            }
            // Now search in the framework resources.
            List<ResourceItem> items =
                    myFrameworkResources.getResourceItems(
                            ResourceNamespace.ANDROID, url.type, url.name);
            if (!items.isEmpty()) {
                ResourceValue value = items.get(0).getResourceValue();
                if (value != null && myLookupChain != null) {
                    myLookupChain.add(value);
                }
                return value;
            }
        }

        // Didn't find the resource anywhere.
        if (myLogger != null) {
            myLogger.warning(
                    LayoutLog.TAG_RESOURCES_RESOLVE, "Couldn't resolve resource " + url, null, url);
        }
        return null;
    }

    @Override
    @Nullable
    public StyleResourceValue getCurrentTheme() {
        ResourceResolver resolver = getFullResolver();
        if (resolver != null) {
            return resolver.getCurrentTheme();
        }

        return null;
    }

    @Override
    public StyleResourceValue getTheme(String name, boolean frameworkTheme) {
        assert false; // This method shouldn't be called on this resolver.
        return super.getTheme(name, frameworkTheme);
    }

    @Override
    public boolean themeIsParentOf(StyleResourceValue parentTheme, StyleResourceValue childTheme) {
        assert false; // This method shouldn't be called on this resolver
        return super.themeIsParentOf(parentTheme, childTheme);
    }

    @Override
    public ResourceValue findItemInTheme(@NonNull ResourceReference attr) {
        ResourceResolver resolver = getFullResolver();
        return resolver != null ? resolver.findItemInTheme(attr) : null;
    }

    @Override
    @Nullable
    public ResourceValue findItemInStyle(
            @NonNull StyleResourceValue style, @NonNull ResourceReference attr) {
        ResourceResolver resolver = getFullResolver();
        return resolver != null ? resolver.findItemInStyle(style, attr) : null;
    }

    @Override
    public StyleResourceValue getParent(StyleResourceValue style) {
        ResourceResolver resolver = getFullResolver();
        return resolver != null ? resolver.getParent(style) : null;
    }

    @Nullable
    private ResourceResolver getFullResolver() {
        if (myResolver == null) {
            if (myResourceProvider == null) {
                return null;
            }
            myResolver = myResourceProvider.getResolver(true);
            if (myResolver != null) {
                if (myLookupChain != null) {
                    myResolver = myResolver.createRecorder(myLookupChain);
                }
            }

        }
        return myResolver;
    }

    /**
     * Optional method to set a list the resolver should record all value resolutions into. Useful
     * if you want to find out the resolution chain for a resource, e.g.
     * {@code @color/buttonForeground ⇒ @color/foreground ⇒ @android:color/black}.
     *
     * <p>There is no getter. Clients setting this list should look it up themselves. Note also that
     * if this resolver has to delegate to a full resource resolver, e.g. to follow theme
     * attributes, those resolutions will not be recorded.
     *
     * @param lookupChain the list to set, or null
     */
    public void setLookupChainList(@Nullable List<ResourceValue> lookupChain) {
        myLookupChain = lookupChain;
    }

    /** Returns the lookup chain being used by this resolver. */
    @Nullable
    public List<ResourceValue> getLookupChain() {
        return myLookupChain;
    }

    /**
     * Returns a display string for a resource lookup.
     *
     * @param type the resource type
     * @param name the resource name
     * @param isFramework whether the item is in the framework
     * @param lookupChain the list of resolved items to display
     * @return the display string
     */
    public static String getDisplayString(
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework,
            @NonNull List<ResourceValue> lookupChain) {
        String url = ResourceUrl.create(type, name, isFramework).toString();
        return getDisplayString(url, lookupChain);
    }

    /**
     * Returns a display string for a resource lookup.
     *
     * @param url the resource url, such as {@code @string/foo}
     * @param lookupChain the list of resolved items to display
     * @return the display string
     */
    @NonNull
    public static String getDisplayString(
            @NonNull String url, @NonNull List<ResourceValue> lookupChain) {
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        String prev = url;
        for (ResourceValue element : lookupChain) {
            if (element == null) {
                continue;
            }
            String value = element.getValue();
            if (value == null) {
                continue;
            }
            String text = value;
            if (text.equals(prev)) {
                continue;
            }

            sb.append(" => ");

            // Strip paths.
            if (!(text.startsWith(PREFIX_THEME_REF) || text.startsWith(PREFIX_RESOURCE_REF))) {
                int end = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
                if (end != -1) {
                    text = text.substring(end + 1);
                }
            }
            sb.append(text);

            prev = value;
        }

        return sb.toString();
    }

    /**
     * Interface implemented by clients of the {@link ResourceItemResolver} which allows it to
     * lazily look up the project resources, the framework resources and optionally to provide a
     * fully configured resource resolver, if any.
     */
    public interface ResourceProvider {
        @Nullable ResourceResolver getResolver(boolean createIfNecessary);
        @Nullable
        AbstractResourceRepository getFrameworkResources();
        @Nullable AbstractResourceRepository getAppResources();
    }
}
