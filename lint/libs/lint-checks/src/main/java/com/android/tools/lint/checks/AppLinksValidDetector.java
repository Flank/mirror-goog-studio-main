/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_EXPORTED;
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_MIME_TYPE;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PATTERN;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_PORT;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_LITERAL;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_PREFIX;
import static com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SIMPLE_GLOB;
import static com.android.tools.lint.detector.api.LintUtils.isDataBindingExpression;
import static com.android.utils.XmlUtils.getFirstSubTag;
import static com.android.utils.XmlUtils.getFirstSubTagTagByName;
import static com.android.utils.XmlUtils.getNextTag;
import static com.android.utils.XmlUtils.getNextTagByName;
import static com.android.utils.XmlUtils.getPreviousTagByName;
import static com.android.utils.XmlUtils.getSubTagsByName;
import static com.android.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.CharSequences;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.QuickfixData;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Checks for invalid app links URLs
 */
public class AppLinksValidDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AppLinksValidDetector.class,
            Scope.MANIFEST_SCOPE);

    /** The main issue discovered by this detector */
    public static final Issue TEST_URL = Issue.create(
            "TestAppLink",
            "Unmatched URLs",

            "Using one or more `tools:validation testUrl=\"some url\"/>` elements "
                    + "in your manifest allows the link attributes in your intent filter to be "
                    + "checked for matches.",

            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            IMPLEMENTATION);

    public static final Issue VALIDATION = Issue.create(
            "AppLinkUrlError",
            "URL not supported by app for Firebase App Indexing",
            "Ensure the URL is supported by your app, to get installs and traffic to "
                    + "your app from Google Search.",
            Category.USABILITY, 5, Severity.ERROR, IMPLEMENTATION)
            .addMoreInfo("https://g.co/AppIndexing/AndroidStudio");

    /**
     * Only used for compatibility issue lookup (the driver suppression check takes
     * an issue, not an id)
     */
    private static final Issue OLD_ISSUE_URL = Issue.create(
            "GoogleAppIndexingUrlError", "?","?",
            Category.USABILITY, 5, Severity.ERROR, IMPLEMENTATION);

    /** Constructs a new {@link AppLinksValidDetector} check */
    public AppLinksValidDetector() {
    }

    // ---- Implements Detector.XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(TAG_INTENT_FILTER);
    }

    private static void reportUrlError(@NonNull XmlContext context, @NonNull Node node,
            @NonNull Location location, @NonNull String message) {
        reportUrlError(context, node, location, message, null);
    }

    private static void reportUrlError(@NonNull XmlContext context, @NonNull Node node,
            @NonNull Location location, @NonNull String message, @Nullable Object quickfixData) {
        // Validation errors were reported here before
        if (context.getDriver().isSuppressed(context, OLD_ISSUE_URL, node)) {
            return;
        }

        context.report(VALIDATION, node, location, message, quickfixData);
    }

    private static void reportTestUrlFailure(@NonNull XmlContext context, @NonNull Node node,
            @NonNull Location location, @NonNull String message) {
        context.report(TEST_URL, node, location, message);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element intent) {
        Element firstData = getFirstSubTagTagByName(intent, TAG_DATA);
        boolean actionView = hasActionView(intent);
        boolean browsable = isBrowsable(intent);

        if (actionView) {
            Node parentNode = intent.getParentNode();
            if (parentNode instanceof Element) {
                Element activity = (Element) parentNode;
                ensureExported(context, activity, intent);
            }
        }

        if (firstData == null) {
            if (actionView && browsable) {
                // If this activity is an ACTION_VIEW action with category BROWSABLE, but doesn't
                // have data node, it's a likely mistake
                reportUrlError(context, intent, context.getLocation(intent),
                        "Missing data element");
            }

            return;
        }

        List<String> schemes = null;
        List<String> hosts = null;
        List<String> ports = null;
        List<AndroidPatternMatcher> paths = null;
        boolean hasMimeType = false;

        for (Element data = firstData;
                data != null;
                data = getNextTagByName(data, TAG_DATA)) {

            Attr mimeType = data.getAttributeNodeNS(ANDROID_URI, ATTR_MIME_TYPE);
            if (mimeType != null) {
                hasMimeType = true;
                if (CharSequences.containsUpperCase(mimeType.getValue())) {
                    reportUrlError(context, mimeType, context.getValueLocation(mimeType),
                            "Mime-type matching is case sensitive and should only "
                                    + "use lower-case characters");
                }
            }

            schemes = addAttribute(context, ATTR_SCHEME, schemes, data);
            hosts = addAttribute(context, ATTR_HOST, hosts, data);
            ports = addAttribute(context, ATTR_PORT, ports, data);

            paths = addMatcher(context, ATTR_PATH, PATTERN_LITERAL, paths, data);
            paths = addMatcher(context, ATTR_PATH_PREFIX, PATTERN_PREFIX, paths, data);
            paths = addMatcher(context, ATTR_PATH_PATTERN, PATTERN_SIMPLE_GLOB, paths, data);
            // Platform also has pathAdvancedPattern using type PATTERN_ADVANCED_GLOB but it
            //  doesn't seem exposed to developers
        }

        if (actionView && browsable && schemes == null && !hasMimeType) {
            // If this activity is an action view, is browsable, but has neither a
            // URL nor mimeType, it may be a mistake and we will report error.
            reportUrlError(context, firstData, context.getLocation(firstData),
                    "Missing URL for the intent filter");
        }

        boolean isHttp = false;
        boolean implicitSchemes = false;
        if (schemes == null) {
            if (hasMimeType) {
                // Per documentation
                //   https://developer.android.com/guide/topics/manifest/data-element.html
                // "If the filter has a data type set (the mimeType attribute) but no scheme, the
                //  content: and file: schemes are assumed."
                schemes = Lists.newArrayList();
                schemes.add("content");
                schemes.add("file");
                implicitSchemes = true;
            }
        } else {
            for (String scheme : schemes) {
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    isHttp = true;
                    break;
                }
            }
        }

        // At least one scheme must be specified
        boolean hasScheme = schemes != null;
        if (!hasScheme && (hosts != null || paths != null || ports != null)) {
            reportUrlError(context, firstData, context.getLocation(firstData),
                    "At least one `scheme` must be specified",
                    new QuickfixData.SetAttribute(ANDROID_URI, ATTR_SCHEME, "http"));
        }

        if (hosts == null && (paths != null || ports != null)) {
            reportUrlError(context, firstData, context.getLocation(firstData),
                    "At least one `host` must be specified",
                    new QuickfixData.SetAttribute(ANDROID_URI, ATTR_HOST, null));
        }

        // If this activity is an ACTION_VIEW action, has a http URL but doesn't have
        // BROWSABLE, it may be a mistake and and we will report warning.
        if (actionView && isHttp && !browsable) {
            reportUrlError(context, intent, context.getLocation(intent),
                    "Activity supporting ACTION_VIEW is not set as BROWSABLE");
        }

        if (actionView && (!hasScheme || implicitSchemes)) {
            reportUrlError(context, intent, context.getLocation(intent),
                    "Missing URL",
                    new QuickfixData.SetAttribute(ANDROID_URI, ATTR_SCHEME, "http"));
        }

        testElements(context, intent, schemes, hosts, ports, paths);
    }

    private static void ensureExported(
            @NonNull XmlContext context,
            @NonNull Element activity,
            @NonNull Element intent) {
        Attr exported = activity.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED);
        if (exported == null) {
            return;
        }
        if (VALUE_TRUE.equals(exported.getValue())) {
            return;
        }

        // Make sure there isn't some *earlier* intent filter for this activity
        // that also reported this; we don't want duplicate warnings
        Element prevIntent = getPreviousTagByName(intent, TAG_INTENT_FILTER);
        while (prevIntent != null) {
            if (hasActionView(prevIntent)) {
                return;
            }
            prevIntent = getNextTagByName(prevIntent, TAG_INTENT_FILTER);
        }

        // Report error if the activity supporting action view is not exported.
        reportUrlError(context, activity,
                context.getLocation(activity),
                "Activity supporting ACTION_VIEW is not exported");
    }

    /**
     * Check if the intent filter supports action view.
     *
     * @param intent the intent filter
     * @return true if it does
     */
    static boolean hasActionView(@NonNull Element intent) {
        for (Element action : getSubTagsByName(intent, NODE_ACTION)) {
            if (action.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                Attr attr = action.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                if (attr.getValue().equals("android.intent.action.VIEW")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the intent filter is browsable.
     *
     * @param intent the intent filter
     * @return true if it does
     */
    private static boolean isBrowsable(@NonNull Element intent) {
        for (Element e : getSubTagsByName(intent, NODE_CATEGORY)) {
            if (e.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                Attr attr = e.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                if (attr.getNodeValue().equals("android.intent.category.BROWSABLE")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Looks for validation nodes in the intent filter and checks them */
    private static void testElements(
            @NonNull XmlContext context,
            @NonNull Element intentFilter,
            @Nullable List<String> schemes,
            @Nullable List<String> hosts,
            @Nullable List<String> ports,
            @Nullable List<AndroidPatternMatcher> paths) {
        Element current = getFirstSubTag(intentFilter);
        while (current != null) {
            if ("validation".equals(current.getLocalName())) {
                if (TOOLS_URI.equals(current.getNamespaceURI())) {
                    testElement(context, current, schemes, hosts, ports, paths);
                } else {
                    reportTestUrlFailure(context, current, context.getNameLocation(current),
                            "Validation nodes should be in the `tools:` namespace to "
                                    + "ensure they are removed from the manifest at build time");
                }
            }
            current = getNextTag(current);
        }
    }

    private static void testElement(
            @NonNull XmlContext context,
            @NonNull Element element,
            @Nullable List<String> schemes,
            @Nullable List<String> hosts,
            @Nullable List<String> ports,
            @Nullable List<AndroidPatternMatcher> paths) {
        Attr testUrlAttr = element.getAttributeNode("testUrl");
        if (testUrlAttr == null) {
            String message = "Expected `testUrl` attribute";
            reportUrlError(context, element, context.getLocation(element), message);
            return;
        }
        String testUrlString = testUrlAttr.getValue();
        URL testUrl;
        try {
            testUrl = new URL(testUrlString);
        } catch (MalformedURLException e) {
            String message = "Invalid test URL: " + e.getLocalizedMessage();
            reportTestUrlFailure(context, testUrlAttr, context.getValueLocation(testUrlAttr),
                    message);
            return;
        }

        // Check schemes
        if (schemes != null) {
            boolean schemeOk = false;
            for (String scheme : schemes) {
                if (scheme.equals(testUrl.getProtocol()) || isDataBindingExpression(scheme)) {
                    schemeOk = true;
                    break;
                }
            }
            if (!schemeOk) {
                reportTestUrlFailure(context, testUrlAttr, context.getValueLocation(testUrlAttr),
                        String.format("Test URL did not match scheme %1$s",
                                Joiner.on(", ").join(schemes)));
                return;
            }
        }

        if (hosts != null) {
            boolean hostOk = false;
            for (String host : hosts) {
                if (matchesHost(testUrl.getHost(), host) || isDataBindingExpression(host)) {
                    hostOk = true;
                    break;
                }
            }
            if (!hostOk) {
                reportTestUrlFailure(context, testUrlAttr, context.getValueLocation(testUrlAttr),
                        String.format("Test URL did not match host %1$s",
                                Joiner.on(", ").join(hosts)));
                return;
            }
        }

        // Port matching:
        boolean portOk = false;
        if (testUrl.getPort() != -1) {
            String testPort = Integer.toString(testUrl.getPort());
            if (ports != null) {
                for (String port : ports) {
                    if (testPort.equals(port) || isDataBindingExpression(port)) {
                        portOk = true;
                        break;
                    }
                }
            }
        } else if (ports == null) {
            portOk = true;
        }
        if (!portOk) {
            String portList = ports == null ? "(empty)" : Joiner.on(", ").join(ports);
            reportTestUrlFailure(context, testUrlAttr, context.getValueLocation(testUrlAttr),
                    String.format("Test URL did not match port %1$s", portList));
            return;
        }

        if (paths != null) {
            String testPath = testUrl.getPath();
            boolean pathOk = false;
            for (AndroidPatternMatcher matcher : paths) {
                if (isDataBindingExpression(matcher.getPath()) || matcher.match(testPath)) {
                    pathOk = true;
                    break;
                }
            }

            if (!pathOk) {
                StringBuilder sb = new StringBuilder();
                for (AndroidPatternMatcher matcher : paths) {
                    sb.append("path ").append(matcher.toString()).append(", ");
                }

                if (CharSequences.endsWith(sb, ", ", true)) {
                    sb.setLength(sb.length() - 2);
                }

                String message = String.format("Test URL did not match %1$s", sb.toString());

                if (containsUpperCase(paths) || CharSequences.containsUpperCase(testPath)) {
                    message += " Note that matching is case sensitive.";
                }

                reportTestUrlFailure(context, testUrlAttr, context.getValueLocation(testUrlAttr),
                        message);
            }
        }
    }

    private static boolean containsUpperCase(@Nullable List<AndroidPatternMatcher> matchers) {
        if (matchers != null) {
            for (AndroidPatternMatcher matcher : matchers) {
                if (CharSequences.containsUpperCase(matcher.getPath())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static List<String> addAttribute(
            @NonNull XmlContext context,
            @NonNull String attributeName,
            @Nullable List<String> current,
            Element data) {
        Attr attribute = data.getAttributeNodeNS(ANDROID_URI, attributeName);
        if (attribute != null) {
            String value = attribute.getValue();
            if (requireNonEmpty(context, attribute, value)) {
                return current;
            }

            if (value.startsWith(PREFIX_RESOURCE_REF)
                    || value.startsWith(PREFIX_THEME_REF)) {
                value = replaceUrlWithValue(context, value);
            }

            if (current == null) {
                current = Lists.newArrayListWithCapacity(4);
            }
            current.add(value);

            if (isDataBindingExpression(value)) {
                return current;
            }

            // Validation
            // See https://developer.android.com/guide/topics/manifest/data-element.html
            switch (attributeName) {
                case ATTR_SCHEME: {
                    if (value.endsWith(":")) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                            "Don't include trailing colon in the `scheme` declaration");
                    } else if (CharSequences.containsUpperCase(value)) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                                "Scheme matching is case sensitive and should only "
                                        + "use lower-case characters");
                    }

                    break;
                }
                case ATTR_HOST: {
                    if (value.lastIndexOf('*') > 0) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                                "The host wildcard (`*`) can only be the first "
                                        + "character");
                    } else if (CharSequences.containsUpperCase(value)) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                                "Host matching is case sensitive and should only "
                                        + "use lower-case characters");
                    }
                    break;
                }

                case ATTR_PORT: {
                    try {
                        int port = Integer.parseInt(value); // might also throw number exc
                        if (port < 1 || port > 65535) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                                "not a valid port number");
                    }

                    // The port *only* takes effect if it's specified on the *same* XML
                    // element as the host (this isn't true for the other attributes,
                    // which can be spread out across separate <data> elements)
                    if (!data.hasAttributeNS(ANDROID_URI, ATTR_HOST)) {
                        reportUrlError(context, attribute, context.getValueLocation(attribute),
                                "The port must be specified in the same `<data>` "
                                        + "element as the `host`");
                    }

                    break;
                }
            }
        }

        return current;
    }

    @Nullable
    private static List<AndroidPatternMatcher> addMatcher(
            @NonNull XmlContext context,
            @NonNull String attributeName,
            int type,
            @Nullable List<AndroidPatternMatcher> current,
            Element data) {
        Attr attribute = data.getAttributeNodeNS(ANDROID_URI, attributeName);
        if (attribute != null) {
            String value = attribute.getValue();
            if (requireNonEmpty(context, attribute, value)) {
                return current;
            }

            if (current == null) {
                current = Lists.newArrayListWithCapacity(4);
            }

            if (value.startsWith(PREFIX_RESOURCE_REF)
                    || value.startsWith(PREFIX_THEME_REF)) {
                value = replaceUrlWithValue(context, value);
            }

            AndroidPatternMatcher matcher = new AndroidPatternMatcher(value, type);
            current.add(matcher);

            if (!value.startsWith("/") && !value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)
                    // Only enforce / for path and prefix; for pattern it seems to
                    // work without
                    && !attributeName.equals(ATTR_PATH_PATTERN)) {
                reportUrlError(context, attribute, context.getValueLocation(attribute),
                        String.format("`%1$s` attribute should start with `/`, but it is `"
                                + value + "`", attribute.getName()),
                        new QuickfixData.ReplaceString(attribute.getValue(), null, "/" + value));
            }
        }

        return current;
    }

    private static boolean requireNonEmpty(@NonNull XmlContext context,
            @NonNull Attr attribute, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            reportUrlError(context, attribute, context.getLocation(attribute),
                    String.format("`%1$s` cannot be empty", attribute.getName()));
            return true;
        }
        return false;
    }

    private static String replaceUrlWithValue(@NonNull XmlContext context,
            @NonNull String str) {
        LintClient client = context.getClient();
        if (!client.supportsProjectResources()) {
            return str;
        }
        ResourceUrl url = ResourceUrl.parse(str);
        if (url == null || url.framework) {
            return str;
        }
        Project project = context.getProject();
        AbstractResourceRepository resources = client.getResourceRepository(project, true, true);
        if (resources == null) {
            return str;
        }
        List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, url.name);
        if (items == null || items.isEmpty()) {
            return str;
        }
        ResourceValue resourceValue = items.get(0).getResourceValue(false);
        if (resourceValue == null) {
            return str;
        }
        return resourceValue.getValue() == null ? str : resourceValue.getValue();
    }

    /**
     * Check whether a given host matches the hostRegex. The hostRegex could be a regular host
     * name, or it could contain only one '*', such as *.example.com, where '*' matches any
     * string whose length is at least 1.
     *
     * @param actualHost The actual host we want to check.
     * @param hostPattern  The criteria host, which could contain a '*'.
     * @return Whether the actualHost matches the hostRegex
     */
    public static boolean matchesHost(@NonNull String actualHost, @NonNull String hostPattern) {
        // Per https://developer.android.com/guide/topics/manifest/data-element.html
        // the asterisk must be the first character
        if (!hostPattern.startsWith("*")) {
            return actualHost.equals(hostPattern);
        }

        try {
            String pattern = ".*" + Pattern.quote(hostPattern.substring(1));
            return actualHost.matches(pattern);
        } catch (Throwable ignore) {
            // Make sure we don't fail to compile the regex, though with the quote call
            // above this really shouldn't happen
            return false;
        }
    }
}
