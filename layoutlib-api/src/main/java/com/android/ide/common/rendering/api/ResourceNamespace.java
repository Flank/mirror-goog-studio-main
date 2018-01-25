// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.ide.common.rendering.api;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a namespace used by aapt when processing resources.
 *
 * <p>In "traditional" projects, all resources from local sources and AARs live in the {@link
 * #RES_AUTO} namespace and are processed together by aapt. Framework resources belong in the
 * "android" package name / namespace.
 *
 * <p>In namespace-aware projects, every module and AAR contains resources in a separate namespace
 * that is read from the manifest and corresponds to the {@code package-name}. Framework resources
 * are treated as before.
 *
 * <p>The tools namespace is a special case and is only used by sample data, so it never reaches the
 * aapt stage.
 *
 * <p>This class is serializable to allow passing between Gradle workers.
 */
public class ResourceNamespace implements Serializable {
    public static final ResourceNamespace ANDROID =
            new ResourceNamespace(SdkConstants.ANDROID_NS_NAME);
    public static final ResourceNamespace RES_AUTO = new ResAutoNamespace();
    public static final ResourceNamespace TOOLS = new ToolsNamespace();

    /**
     * Namespace used in code that needs to start keeping track of namespaces. For easy tracking of
     * parts of codebase that need fixing.
     */
    public static final ResourceNamespace TODO = RES_AUTO;

    /**
     * Logic for looking up namespace prefixes defined in some context.
     *
     * @see ResourceNamespace#fromNamespacePrefix(String, ResourceNamespace, Resolver)
     */
    @FunctionalInterface
    public interface Resolver {
        /** Returns the full URI of an XML namespace for a given prefix, if defined. */
        @Nullable
        String prefixToUri(@NonNull String namespacePrefix);

        Resolver EMPTY_RESOLVER = prefix -> null;
    }

    @Nullable private final String packageName;

    /**
     * Constructs a {@link ResourceNamespace} for the given (fully qualified) aapt package name.
     * Note that this is not the string used in XML notation before the colon (at least not in the
     * general case), which can be an alias.
     *
     * <p>This factory method can be used when reading the build system model or for testing, other
     * code most likely needs to resolve the short namespace prefix against XML namespaces defined
     * in the given context.
     *
     * @see #fromNamespacePrefix(String, ResourceNamespace, Resolver)
     */
    @NonNull
    public static ResourceNamespace fromPackageName(@NonNull String packageName) {
        assert !Strings.isNullOrEmpty(packageName);
        if (packageName.equals(SdkConstants.ANDROID_NS_NAME)) {
            // Make sure ANDROID is a singleton, so we can use object identity to check for it.
            return ANDROID;
        } else {
            return new ResourceNamespace(packageName);
        }
    }

    /**
     * Constructs a {@link ResourceNamespace} in code that does not keep track of namespaces yet,
     * only of the boolean `isFramework` flag.
     */
    @NonNull
    @Deprecated
    public static ResourceNamespace fromBoolean(boolean isFramework) {
        return isFramework ? ANDROID : TODO;
    }

    /**
     * Tries to build a {@link ResourceNamespace} from the first part of a {@link
     * com.android.resources.ResourceUrl}, given the context in which the string was used.
     *
     * @param prefix the string to resolve
     * @param defaultNamespace namespace in which this prefix was used. If no prefix is used (it's
     *     null), this is the namespace that will be returned. For example, if an XML file inside
     *     libA (com.lib.a) references "@string/foo", it means the "foo" resource from libA, so the
     *     "com.lib.a" namespace should be passed as the {@code defaultNamespace}.
     * @param resolver strategy for mapping short namespace prefixes to namespace URIs as used in
     *     XML resource files. This should be provided by the XML parser used. For example, if the
     *     source XML document contained snippet such as {@code
     *     xmlns:foo="http://schemas.android.com/apk/res/com.foo"}, it should return {@code
     *     "http://schemas.android.com/apk/res/com.foo"} when applied to argument {@code "foo"}.
     * @see com.android.resources.ResourceUrl#namespace
     */
    @Nullable
    public static ResourceNamespace fromNamespacePrefix(
            @Nullable String prefix,
            @NonNull ResourceNamespace defaultNamespace,
            @NonNull Resolver resolver) {
        if (Strings.isNullOrEmpty(prefix)) {
            return defaultNamespace;
        }

        String uri = resolver.prefixToUri(prefix);
        if (uri != null) {
            if (uri.equals(SdkConstants.AUTO_URI)) {
                return RES_AUTO;
            }
            if (uri.equals(SdkConstants.TOOLS_URI)) {
                return TOOLS;
            }
            if (uri.startsWith(SdkConstants.URI_PREFIX)) {
                // TODO(namespaces): What is considered a good package name by aapt?
                String packageName = uri.substring(SdkConstants.URI_PREFIX.length());
                if (!packageName.isEmpty()) {
                    return new ResourceNamespace(packageName);
                } else {
                    return null;
                }
            }

            // The prefix is mapped to a string/URL we don't understand.
            return null;
        } else {
            // TODO(namespaces): What is considered a good package name by aapt?
            return fromPackageName(prefix);
        }
    }

    private ResourceNamespace(@Nullable String packageName) {
        this.packageName = packageName;
    }

    /**
     * Returns namespace associated with this namespace, or null in the case of {@link #RES_AUTO}.
     *
     * <p>The result value can be used as the namespace part of a {@link
     * com.android.resources.ResourceUrl}.
     */
    @Nullable
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    public String getXmlNamespaceUri() {
        return SdkConstants.URI_PREFIX + packageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResourceNamespace that = (ResourceNamespace) o;
        return Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packageName);
    }

    @Override
    public String toString() {
        return packageName;
    }

    private static class ResAutoNamespace extends ResourceNamespace {
        private ResAutoNamespace() {
            super(null);
        }

        @NonNull
        @Override
        public String getXmlNamespaceUri() {
            return SdkConstants.AUTO_URI;
        }

        @Override
        public String toString() {
            return "res-auto";
        }

    }

    private static class ToolsNamespace extends ResourceNamespace {
        private ToolsNamespace() {
            super(null);
        }

        @NonNull
        @Override
        public String getXmlNamespaceUri() {
            return SdkConstants.TOOLS_URI;
        }

        @Override
        public String toString() {
            return "tools";
        }

        @Override
        public int hashCode() {
            // Try to hit a different bucket from res-auto.
            return 1;
        }
    }
}
