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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * A <b>description</b> of a quickfix for a lint warning, which provides
 * structured data for use by the IDE to create an actual fix implementation.
 * For example, a {@linkplain LintFix} can state that it aims to set a given
 * attribute to a given value. When lint is running in the IDE, the quickfix
 * machinery will look at the {@linkplain LintFix} objects and add an actual
 * implementation which sets the attribute.
 * <p>
 * The set of operations is quite limited at the moment; more will be
 * added over time.
 */
public class LintFix {
    @Nls @Nullable public final String displayName;

    protected LintFix(@Nullable String displayName) {
        this.displayName = displayName;
    }

    /** Creates a new Quickfix Builder */
    @NonNull
    public static Builder create() {
        return new Builder();
    }

    /** Return display name */
    @Nls @Nullable
    public String getDisplayName() {
        return displayName;
    }

    /** Builder for creating various types of fixes */
    public static class Builder {
        @Nls protected String displayName;

        private Builder() {
        }

        /**
         * Sets display name. If not supplied a default will be created based on the
         * type of quickfix.
         *
         * @param displayName the display name
         * @return this
         */
        public Builder name(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Creates a group of fixes
         */
        @SuppressWarnings("MethodMayBeStatic")
        public GroupBuilder group() {
            return new GroupBuilder();
        }

        /**
         * Creates a fix list from a set of lint fixes. The IDE will show all of these
         * as separate options.
         *
         * @param fixes fixes to combine
         * @return a fix representing the list
         */
        @SuppressWarnings("MethodMayBeStatic")
        public LintFix group(LintFix... fixes) {
            return new GroupBuilder().join(fixes).build();
        }

        /**
         * Replace a string or regular expression
         * @return a string replace builder
         */
        public ReplaceStringBuilder replace() {
            return new ReplaceStringBuilder(displayName);
        }

        /**
         * Set or clear an attribute
         * @return a set attribute builder
         */
        public SetAttributeBuilder set() {
            return new SetAttributeBuilder(displayName);
        }

        public SetAttributeBuilder set(@Nullable String namespace, @NonNull String attribute,
                @Nullable String value) {
            return new SetAttributeBuilder(displayName).namespace(namespace)
                    .attribute(attribute).value(value);
        }

        /**
         * Provides a map with details for the quickfix implementation
         */
        public FixMapBuilder map() { return new FixMapBuilder(displayName); }

        /**
         * Provides a map with details for the quickfix implementation, pre-initialized
         * with the given objects
         */
        @NonNull
        public FixMapBuilder map(@NonNull Object... args) {
            FixMapBuilder builder = map();

            for (Object arg : args) {
                builder.put(arg);
            }

            return builder;
        }
    }

    /** Builder for constructing a group of fixes */
    public static class GroupBuilder {
        private List<LintFix> list = Lists.newArrayListWithExpectedSize(4);

        /** Constructed from {@link Builder#set()} */
        private GroupBuilder() { }

        /** Adds the given fixes to this group */
        public GroupBuilder join(@NonNull LintFix... fixes) {
            list.addAll(Arrays.asList(fixes));
            return this;
        }

        /** Adds the given fix to this group */
        public GroupBuilder add(@NonNull LintFix fix) {
            list.add(fix);
            return this;
        }

        /** Construct a {@link LintFix} for this group of fixes */
        @NonNull
        public LintFix build() {
            assert !list.isEmpty();
            return new LintFixGroup(list);
        }
    }

    /** A builder for replacing strings */
    public static class ReplaceStringBuilder {
        @Nls protected String displayName;
        private String newText;
        private String oldText;
        @Language("RegExp") private String oldPattern;

        /** Constructed from {@link Builder#replace()} */
        private ReplaceStringBuilder(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets display name. If not supplied a default will be created based on the
         * type of quickfix.
         *
         * @param displayName the display name
         * @return this
         */
        public ReplaceStringBuilder name(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Replaces the given pattern match (or the first group within it, if any) */
        public ReplaceStringBuilder pattern(@Language("RegExp") String oldPattern) {
            assert this.oldText == null;
            assert this.oldPattern == null;

            if (oldPattern.indexOf('(') == -1) {
                oldPattern = "(" + oldPattern + ")";
            }
            this.oldPattern = oldPattern;
            return this;
        }

        /** Replaces the given literal text */
        public ReplaceStringBuilder text(String oldText) {
            assert this.oldText == null;
            assert this.oldPattern == null;
            this.oldText = oldText;
            return this;
        }

        /** Replaces this entire range */
        public ReplaceStringBuilder all() {
            return this;
        }

        /** The text to replace the old text or pattern with */
        public ReplaceStringBuilder with(String newText) {
            assert this.newText == null;
            this.newText = newText;
            return this;
        }

        /** Constructs a {@link LintFix} for this string replacement */
        @NonNull
        public LintFix build() {
            return new ReplaceString(displayName, oldText, oldPattern, newText);
        }
    }

    public static class SetAttributeBuilder {
        @Nls protected String displayName;
        private String attribute;
        private String namespace;
        private String value = "";
        private int mark = Integer.MIN_VALUE;
        private int dot = Integer.MIN_VALUE;

        /** Constructed from {@link Builder#set()} */
        private SetAttributeBuilder(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Sets display name. If not supplied a default will be created based on the
         * type of quickfix.
         *
         * @param displayName the display name
         * @return this
         */
        public SetAttributeBuilder name(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets the namespace to the Android namespace (shortcut for {@link
         * #namespace(String)} passing in {@link SdkConstants#ANDROID_URI}
         */
        public SetAttributeBuilder android() {
            assert this.namespace == null;
            this.namespace = ANDROID_URI;
            return this;
        }

        /** Sets the namespace to the given namespace */
        public SetAttributeBuilder namespace(@Nullable String namespace) {
            assert this.namespace == null;
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the value to the given value. Null means delete (though it's
         * more natural to call {@link #remove(String)}
         */
        public SetAttributeBuilder value(@Nullable String value) {
            this.value = value;
            if (value != null && value.isEmpty()) {
                caret(0); // Setting to empty attribute normally means "let the user edit"
            }
            return this;
        }

        /** Sets the attribute name. Should not include the prefix. */
        public SetAttributeBuilder attribute(@NonNull String attribute) {
            assert attribute.indexOf(':') == -1 : attribute;
            assert this.attribute == null;
            this.attribute = attribute;
            return this;
        }

        /** Removes the given attribute */
        public SetAttributeBuilder remove(@NonNull String attribute) {
            assert this.attribute == null;
            assert attribute.indexOf(':') == -1 : attribute;
            this.attribute = attribute;
            this.value = null;
            return this;
        }

        /** Selects the newly inserted value */
        public SetAttributeBuilder selectAll() {
            assert value != null; // must be set first
            this.mark = 0;
            this.dot = value.length();
            return this;
        }

        /**
         * Sets the value to TＯDＯ meant for values that aren't optional.
         * You can also supply a prefix and/or a suffix.
         *
         * @param prefix optional prefix to add before the TＯDＯ marker
         * @param suffix optional suffix to add after the TＯDＯ marker
         * @return a builder for TＯDＯ edits
         */
        public SetAttributeBuilder todo(@Nullable String namespace, @NonNull String attribute,
                @Nullable String prefix, @Nullable String suffix) {
            namespace(namespace);
            attribute(attribute);
            StringBuilder sb = new StringBuilder();
            if (prefix != null) {
                sb.append(prefix);
            }
            int start = sb.length();
            sb.append("TODO");
            int end = sb.length();
            if (suffix != null) {
                sb.append(suffix);
            }
            value(sb.toString());
            select(start, end);
            return this;
        }

        /**
         * Sets the value to TＯDＯ meant for values that aren't optional.
         */
        public SetAttributeBuilder todo(@Nullable String namespace, @NonNull String attribute) {
            return todo(namespace, attribute, null, null);
        }

        /** Selects the value in the offset range (relative to value start */
        public SetAttributeBuilder select(int start, int end) {
            assert value != null; // must be set first
            this.mark = Math.min(start, end);
            this.dot = Math.max(start, end);
            return this;
        }

        /**
         * Moves the caret to the given offset (relative to the position
         * of the value text; can be negative ({@link Integer#MIN_VALUE means not set}
         */
        public SetAttributeBuilder caret(int valueStartDelta) {
            this.mark = this.dot = valueStartDelta;
            return this;
        }

        /**
         * Moves the caret to the beginning of the value after applying the new attribute
         */
        public SetAttributeBuilder caretBegin() {
            return caret(0);
        }


        /**
         * Moves the caret to the end of the value after applying the new attribute
         */
        public SetAttributeBuilder caretEnd() {
            assert value != null; // must be set first
            return caret(value.length());
        }

        /** Constructs a {@link LintFix} for this attribute operation */
        @NonNull
        public LintFix build() {
            return new SetAttribute(displayName, namespace, attribute, value,
                    dot, mark);
        }
    }

    public static class FixMapBuilder {
        @Nls protected String displayName;

        /** Constructed from {@link Builder#map()} */
        private FixMapBuilder(String displayName) {
            this.displayName = displayName;
        }

        private final Map<Object, Object> map = Maps.newHashMapWithExpectedSize(4);

        /** Puts the given value into the map using its class as the key */
        public <T> FixMapBuilder put(@Nullable T value) {
            if (value == null) {
                return this;
            }
            Class<?> key = value.getClass();
            assert !map.containsKey(key);
            map.put(key, value);
            return this;
        }

        /** Puts the given value into the map using the given key */
        public FixMapBuilder put(@NonNull String key, @Nullable Object value) {
            if (value == null) {
                return this;
            }
            assert !map.containsKey(key);
            map.put(key, value);
            return this;
        }

        /** Constructs a {@link LintFix} with this map data */
        @NonNull
        public LintFix build() {
            return new DataMap(displayName, map);
        }
    }

    /**
     * General map storage for quickfix data; clients can look up via map keys or
     * types of values
     * <p>
     * This class/API is <b>only</b> intended for IDE use. Lint checks should be
     * accessing the builder class instead - {@link #create()}.
     */
    public static class DataMap extends LintFix implements Iterable {
        private final Map<Object, Object> map;

        private DataMap(@Nullable String displayName, Map<Object, Object> map) {
            super(displayName);
            this.map = map;
        }

        /** Returns the value for the given class key */
        @Nullable
        public <T> T get(@NonNull Class<T> key) {
            //noinspection unchecked
            return (T) map.get(key);
        }

        /** Returns the value for the given String key */
        @Nullable
        public Object get(@NonNull String key) {
            //noinspection unchecked
            return map.get(key);
        }

        @NotNull
        @Override
        public Iterator iterator() {
            return map.values().iterator();
        }

        @Override
        public String toString() {
            return map.toString();
        }
    }

    /**
     * A list of quickfixes
     * <p>
     * This class/API is <b>only</b> intended for IDE use. Lint checks should be
     * accessing the builder class instead - {@link #create()}.
     */
    public static class LintFixGroup extends LintFix {
        /** A list of fixes */
        public final List<LintFix> fixes;

        public LintFixGroup(List<LintFix> fixes) {
            super(null);
            this.fixes = fixes;
        }
    }

    /**
     * Convenience class for the common scenario of suggesting a fix which involves
     * setting an XML attribute.
     * <p>
     * This class/API is <b>only</b> intended for IDE use. Lint checks should be
     * accessing the builder class instead - {@link #create()}.
     */
    public static class SetAttribute extends LintFix {
        /** The namespace */
        @Nullable public final String namespace;

        /** The local attribute name */
        @NonNull public final String attribute;

        /** The value (or null to delete the attribute) */
        @Nullable public final String value;

        /** The caret location to show, OR {@link Integer#MIN_VALUE} if not set.
         * If {@link #mark} is set, the end of the selection too. */
        public final int dot;

        /** The selection anchor, OR {@link Integer#MIN_VALUE} if not set */
        public final int mark;

        /**
         * Set or reset the given attribute
         *
         * @param displayName the displayName
         * @param namespace   optional name space
         * @param attribute   attribute name
         * @param value       value, or null to delete (if already set) or to edit (if already set)
         * @param dot   the caret position
         * @param mark  the selection end point (dot is the other)
         */
        private SetAttribute(
                @Nullable String displayName,
                @Nullable String namespace, @NonNull String attribute,
                @Nullable String value, int dot, int mark) {
            super(displayName);
            this.namespace = namespace;
            this.attribute = attribute;
            this.value = value;
            this.dot = dot;
            this.mark = mark;
        }

        /** Return display name */
        @NonNull
        @Override
        public String getDisplayName() {
            if (displayName != null) {
                return displayName;
            } else if (value != null) {
                if (value.isEmpty() || dot > 0) { // dot > 0: value is partial?
                    return "Set " + attribute;
                } else {
                    return "Set " + attribute + "=\"" + value + "\"";
                }
            } else {
                return "Delete " + attribute;
            }
        }
    }

    /**
     * Convenience class for the common scenario of suggesting a fix which involves
     * replacing a static string or regular expression with a replacement string
     * <p>
     * This class/API is <b>only</b> intended for IDE use. Lint checks should be
     * accessing the builder class instead - {@link #create()}.
     */
    public static class ReplaceString extends LintFix {
        /** The string literal to replace. */
        @Nullable public final String oldString;
        /** The regex to replace. Will always have at least one group, which should
         * be the replacement range. */
        @Nullable public final String oldPattern;
        /** The replacement string. */
        @NonNull public final String replacement;

        /**
         * Replace the given string within the range of the element this warning is marked on
         *
         * @param displayName the displayName
         * @param oldString   the literal string to replace
         * @param oldPattern  the regular expression to replace (provided as a string such that it
         *                    only needs to be compiled if actually referenced by the IDE.
         *                    If there is a group in the regexp, the substitution will be placed
         *                    within the group.
         * @param replacement the replacement literal string
         */
        private ReplaceString(
                @NonNull String displayName,
                @Nullable String oldString, @Nullable String oldPattern,
                @NonNull String replacement) {
            super(displayName);
            this.oldString = oldString;
            this.oldPattern = oldPattern;
            this.replacement = replacement;
        }

        /** Return display name */
        @NonNull
        @Override
        public String getDisplayName() {
            if (displayName != null) {
                return displayName;
            } else {
                return "Replace with " + replacement;
            }
        }
    }
}
