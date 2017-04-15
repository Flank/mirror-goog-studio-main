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

package com.android.tools.lint.detector.api;

import static com.android.tools.lint.detector.api.CharSequences.indexOf;
import static com.android.tools.lint.detector.api.CharSequences.lastIndexOf;
import static com.android.tools.lint.detector.api.CharSequences.startsWith;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.google.common.annotations.Beta;
import com.intellij.psi.PsiElement;
import java.io.File;

/**
 * Location information for a warning
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Location {
    private static final String SUPER_KEYWORD = "super";

    private final File file;
    private final Position start;
    private final Position end;
    private String message;
    private Location secondary;
    private Object clientData;
    private boolean visible = true;
    private boolean selfExplanatory = true;
    private Object source;

    /**
     * Special marker location which means location not available, or not applicable, or filtered
     * out, etc. For example, the infrastructure may return {@link #NONE} if you ask {@link
     * JavaParser#getLocation(JavaContext, PsiElement)} for an element which is not in the current
     * file during an incremental lint run in a single file.
     */
    public static final Location NONE = new Location(new File("NONE"), null, null) {
        @NonNull
        @Override
        public Location setVisible(boolean visible) {
            return this;
        }

        @Override
        public Location setMessage(@NonNull String message, boolean selfExplanatory) {
            return this;
        }

        @Override
        public Location setClientData(@Nullable Object clientData) {
            return this;
        }

        @NonNull
        @Override
        public Location setSelfExplanatory(boolean selfExplanatory) {
            return this;
        }

        @Override
        public Location setSecondary(@Nullable Location secondary) {
            return this;
        }
    };

    /**
     * (Private constructor, use one of the factory methods
     * {@link Location#create(File)},
     * {@link Location#create(File, Position, Position)}, or
     * {@link Location#create(File, CharSequence, int, int)}.
     * <p>
     * Constructs a new location range for the given file, from start to end. If
     * the length of the range is not known, end may be null.
     *
     * @param file the associated file (but see the documentation for
     *            {@link #getFile()} for more information on what the file
     *            represents)
     * @param start the starting position, or null
     * @param end the ending position, or null
     */
    protected Location(@NonNull File file, @Nullable Position start, @Nullable Position end) {
        super();
        this.file = file;
        this.start = start;
        this.end = end;
    }

    /**
     * Whether this location should be visible on its own. "Visible" here refers to whether
     * the location is shown in the IDE if the user navigates to the given location.
     * <p>
     * For visible locations, especially those that appear far away from the primary
     * location, it's important that the error message make sense on its own.
     * For example, for duplicate declarations, usually the primary error message says
     * something like "foo has already been defined", and the secondary error message
     * says "previous definition here". In something like a text or HTML report, this
     * makes sense -- you see the "foo has already been defined" error message, and
     * it also reports the locations of the previous error message. But if the secondary
     * error message is visible, the user may encounter that error first, and if that
     * error message just says "previous definition here", that doesn't make a lot of
     * sense.
     * <p>
     * This attribute is ignored for the primary location for an issue (e.g. the location
     * passed to
     * {@link LintClient#report(Context, Issue, Severity, Location, String, TextFormat, LintFix)},
     * and it applies for all the secondary locations linked from that location.
     *
     * @return whether this secondary location should be shown on its own in the editor.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this location should be visible on its own. See {@link #isVisible()}.
     *
     * @param visible whether this location should be visible
     * @return this, for constructor chaining
     */
    @NonNull
    public Location setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Returns the file containing the warning. Note that the file *itself* may
     * not yet contain the error. When editing a file in the IDE for example,
     * the tool could generate warnings in the background even before the
     * document is saved. However, the file is used as a identifying token for
     * the document being edited, and the IDE integration can map this back to
     * error locations in the editor source code.
     *
     * @return the file handle for the location
     */
    @NonNull
    public File getFile() {
        return file;
    }

    /**
     * The start position of the range
     *
     * @return the start position of the range, or null
     */
    @Nullable
    public Position getStart() {
        return start;
    }

    /**
     * The end position of the range
     *
     * @return the start position of the range, may be null for an empty range
     */
    @Nullable
    public Position getEnd() {
        return end;
    }

    /**
     * Returns a secondary location associated with this location (if
     * applicable), or null.
     *
     * @return a secondary location or null
     */
    @Nullable
    public Location getSecondary() {
        return secondary;
    }

    /**
     * Sets a secondary location for this location.
     *
     * @param secondary a secondary location associated with this location
     * @return this, for constructor chaining
     */
    public Location setSecondary(@Nullable Location secondary) {
        this.secondary = secondary;
        return this;
    }

    /**
     * Sets a secondary location with the given message and returns the current location
     * updated with the given secondary location.
     *
     * @param secondary a secondary location associated with this location
     * @param message a message to be set on the secondary location
     * @return current location updated with the secondary location
     */
    @NonNull
    public Location withSecondary(@NonNull Location secondary, @NonNull String message) {
        return withSecondary(secondary, message, false);
    }

    /**
     * Sets a secondary location with the given message and returns the current location
     * updated with the given secondary location.
     *
     * @param secondary       a secondary location associated with this location
     * @param message         a message to be set on the secondary location
     * @param selfExplanatory if true, the message is itself self-explanatory; see
     *                        {@link #isSelfExplanatory()}}
     * @return current location updated with the secondary location
     */
    @NonNull
    public Location withSecondary(@NonNull Location secondary, @NonNull String message,
                                  boolean selfExplanatory) {
        secondary.setMessage(message, selfExplanatory);
        setSecondary(secondary);
        return this;
    }

    /**
     * Returns the source element for this location, if applicable
     *
     * @return the source element or null
     */
    @Nullable
    public Object getSource() {
        return source;
    }

    /**
     * Returns the source element for this location provided it's of the given type, if applicable
     *
     * @param clz the type of the source
     * @return the source element or null
     */
    @Nullable
    public <T> T getSource(@NonNull Class<T> clz) {
        //if (source instanceof T) {
        if (source != null && clz.isAssignableFrom(source.getClass())) {
            //noinspection unchecked
            return (T) source;
        }

        return null;
    }

    /**
     * Sets the source element applicable for this location, if any
     *
     * @param source the source
     * @return this, for constructor chaining
     */
    public Location setSource(@Nullable Object source) {
        this.source = source;
        return this;
    }

    /**
     * Sets a custom message for this location. This is typically used for
     * secondary locations, to describe the significance of this alternate
     * location. For example, for a duplicate id warning, the primary location
     * might say "This is a duplicate id", pointing to the second occurrence of
     * id declaration, and then the secondary location could point to the
     * original declaration with the custom message "Originally defined here".
     *
     * @param message the message to apply to this location
     * @return this, for constructor chaining
     */
    public Location setMessage(@NonNull String message) {
        return setMessage(message, false);
    }

    /**
     * Sets a custom message for this location. This is typically used for
     * secondary locations, to describe the significance of this alternate
     * location. For example, for a duplicate id warning, the primary location
     * might say "This is a duplicate id", pointing to the second occurrence of
     * id declaration, and then the secondary location could point to the
     * original declaration with the custom message "Originally defined here".
     *
     * @param message         the message to apply to this location
     * @param selfExplanatory if true, the message is itself self-explanatory;
     *                        if false, it's just describing this particular
     *                        location and the primary error message is
     *                        necessary. Controls whether (for example) the
     *                        IDE will include the original error message along
     *                        with this location when showing the message.
     * @return this, for constructor chaining
     */
    public Location setMessage(@NonNull String message, boolean selfExplanatory) {
        this.message = message;
        setSelfExplanatory(selfExplanatory);
        return this;
    }

    /**
     * Whether this message is self-explanatory. If false, it's just describing this particular
     * location and the primary error message is necessary. Controls whether (for example) the
     * IDE will include the original error message along with this location when showing the
     * message.
     *
     * @@return whether this message is self explanatory.
     */
    public boolean isSelfExplanatory() {
        return selfExplanatory;
    }

    /**
     * Sets whether this message is self-explanatory. See {@link #isSelfExplanatory()}.
     * @param selfExplanatory whether this message is self explanatory.
     * @return this, for constructor chaining
     */
    @NonNull
    public Location setSelfExplanatory(boolean selfExplanatory) {
        this.selfExplanatory = selfExplanatory;
        return this;
    }

    /**
     * Returns the custom message for this location, if any. This is typically
     * used for secondary locations, to describe the significance of this
     * alternate location. For example, for a duplicate id warning, the primary
     * location might say "This is a duplicate id", pointing to the second
     * occurrence of id declaration, and then the secondary location could point
     * to the original declaration with the custom message
     * "Originally defined here".
     *
     * @return the custom message for this location, or null
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * Sets the client data associated with this location. This is an optional
     * field which can be used by the creator of the {@link Location} to store
     * temporary state associated with the location.
     *
     * @param clientData the data to store with this location
     * @return this, for constructor chaining
     */
    public Location setClientData(@Nullable Object clientData) {
        this.clientData = clientData;
        return this;
    }

    /**
     * Returns the client data associated with this location - an optional field
     * which can be used by the creator of the {@link Location} to store
     * temporary state associated with the location.
     *
     * @return the data associated with this location
     */
    @Nullable
    public Object getClientData() {
        return clientData;
    }

    @Override
    public String toString() {
        return "Location [file=" + file + ", start=" + start + ", end=" + end + ", message="
                + message + ']';
    }

    /**
     * Creates a new location for the given file
     *
     * @param file the file to create a location for
     * @return a new location
     */
    @NonNull
    public static Location create(@NonNull File file) {
        return new Location(file, null /*start*/, null /*end*/);
    }

    /**
     * Creates a new location for the given file and SourcePosition.
     *
     * @param file the file containing the positions
     * @param position the source position
     * @return a new location
     */
    @NonNull
    public static Location create(
            @NonNull File file,
            @NonNull SourcePosition position) {
        if (position.equals(SourcePosition.UNKNOWN)) {
            return new Location(file, null /*start*/, null /*end*/);
        }
        return new Location(file,
                new DefaultPosition(
                        position.getStartLine(),
                        position.getStartColumn(),
                        position.getStartOffset()),
                new DefaultPosition(
                        position.getEndLine(),
                        position.getEndColumn(),
                        position.getEndOffset()));
    }

    /**
     * Creates a new location for the given file and starting and ending
     * positions.
     *
     * @param file the file containing the positions
     * @param start the starting position
     * @param end the ending position
     * @return a new location
     */
    @NonNull
    public static Location create(
            @NonNull File file,
            @NonNull Position start,
            @Nullable Position end) {
        return new Location(file, start, end);
    }

    /**
     * Creates a new location for the given file, with the given contents, for
     * the given offset range.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param startOffset the starting offset
     * @param endOffset the ending offset
     * @return a new location
     */
    @NonNull
    public static Location create(
            @NonNull File file,
            @Nullable CharSequence contents,
            int startOffset,
            int endOffset) {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid offsets");
        }

        if (contents == null) {
            return new Location(file,
                    new DefaultPosition(-1, -1, startOffset),
                    new DefaultPosition(-1, -1, endOffset));
        }

        int size = contents.length();
        endOffset = Math.min(endOffset, size);
        startOffset = Math.min(startOffset, endOffset);
        Position start = null;
        int line = 0;
        int lineOffset = 0;
        char prev = 0;
        for (int offset = 0; offset <= size; offset++) {
            if (offset == startOffset) {
                start = new DefaultPosition(line, offset - lineOffset, offset);
            }
            if (offset == endOffset) {
                Position end = new DefaultPosition(line, offset - lineOffset, offset);
                return new Location(file, start, end);
            }
            char c = contents.charAt(offset);
            if (c == '\n') {
                lineOffset = offset + 1;
                if (prev != '\r') {
                    line++;
                }
            } else if (c == '\r') {
                line++;
                lineOffset = offset + 1;
            }
            prev = c;
        }
        return create(file);
    }

    /**
     * Creates a new location for the given file, with the given contents, for
     * the given line number.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param line the line number (0-based) for the position
     * @return a new location
     */
    @NonNull
    public static Location create(@NonNull File file, @NonNull String contents, int line) {
        return create(file, contents, line, null, null, null);
    }

    /**
     * Creates a new location for the given file, with the given contents, for
     * the given line number.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param line the line number (0-based) for the position
     * @param patternStart an optional pattern to search for from the line
     *            match; if found, adjust the column and offsets to begin at the
     *            pattern start
     * @param patternEnd an optional pattern to search for behind the start
     *            pattern; if found, adjust the end offset to match the end of
     *            the pattern
     * @param hints optional additional information regarding the pattern search
     * @return a new location
     */
    @NonNull
    public static Location create(@NonNull File file, @NonNull CharSequence contents, int line,
            @Nullable String patternStart, @Nullable String patternEnd,
            @Nullable SearchHints hints) {
        int currentLine = 0;
        int offset = 0;
        while (currentLine < line) {
            offset = indexOf(contents, '\n', offset);
            if (offset == -1) {
                return create(file);
            }
            currentLine++;
            offset++;
        }

        if (line == currentLine) {
            if (patternStart != null) {
                SearchDirection direction = SearchDirection.NEAREST;
                if (hints != null) {
                    direction = hints.direction;
                }

                int index;
                if (direction == SearchDirection.BACKWARD) {
                    index = findPreviousMatch(contents, offset, patternStart, hints);
                    line = adjustLine(contents, line, offset, index);
                } else if (direction == SearchDirection.EOL_BACKWARD) {
                    int lineEnd = indexOf(contents, '\n', offset);
                    if (lineEnd == -1) {
                        lineEnd = contents.length();
                    }

                    index = findPreviousMatch(contents, lineEnd, patternStart, hints);
                    line = adjustLine(contents, line, offset, index);
                } else if (direction == SearchDirection.FORWARD) {
                    index = findNextMatch(contents, offset, patternStart, hints);
                    line = adjustLine(contents, line, offset, index);
                } else {
                    assert direction == SearchDirection.NEAREST ||
                            direction == SearchDirection.EOL_NEAREST;

                    int lineEnd = indexOf(contents, '\n', offset);
                    if (lineEnd == -1) {
                        lineEnd = contents.length();
                    }
                    offset = lineEnd;

                    int before = findPreviousMatch(contents, offset, patternStart, hints);
                    int after = findNextMatch(contents, offset, patternStart, hints);

                    if (before == -1) {
                        index = after;
                        line = adjustLine(contents, line, offset, index);
                    } else if (after == -1) {
                        index = before;
                        line = adjustLine(contents, line, offset, index);
                    } else {
                        int newLinesBefore = 0;
                        for (int i = before; i < offset; i++) {
                            if (contents.charAt(i) == '\n') {
                                newLinesBefore++;
                            }
                        }
                        int newLinesAfter = 0;
                        for (int i = offset; i < after; i++) {
                            if (contents.charAt(i) == '\n') {
                                newLinesAfter++;
                            }
                        }
                        if (newLinesBefore < newLinesAfter || newLinesBefore == newLinesAfter
                                && offset - before < after - offset) {
                            index = before;
                            line = adjustLine(contents, line, offset, index);
                        } else {
                            index = after;
                            line = adjustLine(contents, line, offset, index);
                        }
                    }
                }

                if (index != -1) {
                    int lineStart = lastIndexOf(contents, '\n', index);
                    if (lineStart == -1) {
                        lineStart = 0;
                    } else {
                        lineStart++; // was pointing to the previous line's CR, not line start
                    }
                    int column = index - lineStart;
                    if (patternEnd != null) {
                        int end = indexOf(contents, patternEnd, offset + patternStart.length());
                        if (end != -1) {
                            return new Location(file, new DefaultPosition(line, column, index),
                                    new DefaultPosition(line, -1, end + patternEnd.length()));
                        }
                    } else if (hints != null && (hints.isJavaSymbol() || hints.isWholeWord())) {
                        if (hints.isConstructor() && startsWith(contents, SUPER_KEYWORD, index)) {
                            patternStart = SUPER_KEYWORD;
                        }
                        return new Location(file, new DefaultPosition(line, column, index),
                                new DefaultPosition(line, column + patternStart.length(),
                                        index + patternStart.length()));
                    }
                    return new Location(file, new DefaultPosition(line, column, index),
                            new DefaultPosition(line, column, index + patternStart.length()));
                }
            }

            Position position = new DefaultPosition(line, -1, offset);
            return new Location(file, position, position);
        }

        return create(file);
    }

    private static int findPreviousMatch(@NonNull CharSequence contents, int offset, String pattern,
            @Nullable SearchHints hints) {
        int loopDecrement = Math.max(1, pattern.length());
        while (true) {
            int index = lastIndexOf(contents, pattern, offset);
            if (index == -1) {
                return -1;
            } else {
                if (isMatch(contents, index, pattern, hints)) {
                    return index;
                } else {
                    offset = index - loopDecrement;
                }
            }
        }
    }

    private static int findNextMatch(@NonNull CharSequence contents, int offset, String pattern,
            @Nullable SearchHints hints) {
        int constructorIndex = -1;
        if (hints != null && hints.isConstructor()) {
            // Special condition: See if the call is referenced as "super" instead.
            assert hints.isWholeWord();
            int index = indexOf(contents, SUPER_KEYWORD, offset);
            if (index != -1 && isMatch(contents, index, SUPER_KEYWORD, hints)) {
                constructorIndex = index;
            }
        }

        int loopIncrement = Math.max(1, pattern.length());
        while (true) {
            int index = indexOf(contents, pattern, offset);
            if (index == -1 || index == contents.length()) {
                return constructorIndex;
            } else {
                if (isMatch(contents, index, pattern, hints)) {
                    if (constructorIndex != -1) {
                        return Math.min(constructorIndex, index);
                    }
                    return index;
                } else {
                    offset = index + loopIncrement;
                }
            }
        }
    }

    private static boolean isMatch(@NonNull CharSequence contents, int offset, String pattern,
            @Nullable SearchHints hints) {
        if (!startsWith(contents, pattern, offset)) {
            return false;
        }

        if (hints != null) {
            char prevChar = offset > 0 ? contents.charAt(offset - 1) : 0;
            int lastIndex = offset + pattern.length() - 1;
            char nextChar = lastIndex < contents.length() - 1 ? contents.charAt(lastIndex + 1) : 0;

            if (hints.isWholeWord() && (Character.isLetter(prevChar)
                    || Character.isLetter(nextChar))) {
                return false;

            }

            if (hints.isJavaSymbol()) {
                if (Character.isJavaIdentifierPart(prevChar)
                        || Character.isJavaIdentifierPart(nextChar)) {
                    return false;
                }

                if (prevChar == '"') {
                    return false;
                }

                // TODO: Additional validation to see if we're in a comment, string, etc.
                // This will require lexing from the beginning of the buffer.
            }

            if (hints.isConstructor() && SUPER_KEYWORD.equals(pattern)) {
                // Only looking for super(), not super.x, so assert that the next
                // non-space character is (
                int index = lastIndex + 1;
                while (index < contents.length() - 1) {
                    char c = contents.charAt(index);
                    if (c == '(') {
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        return false;
                    }
                    index++;
                }
            }
        }

        return true;
    }

    private static int adjustLine(CharSequence doc, int line, int offset, int newOffset) {
        if (newOffset == -1) {
            return line;
        }

        if (newOffset < offset) {
            return line - countLines(doc, newOffset, offset);
        } else {
            return line + countLines(doc, offset, newOffset);
        }
    }

    private static int countLines(CharSequence doc, int start, int end) {
        int lines = 0;
        for (int offset = start; offset < end; offset++) {
            char c = doc.charAt(offset);
            if (c == '\n') {
                lines++;
            }
        }

        return lines;
    }

    /**
     * Reverses the secondary location list initiated by the given location
     *
     * @param location the first location in the list
     * @return the first location in the reversed list
     */
    public static Location reverse(@NonNull Location location) {
        Location next = location.getSecondary();
        location.setSecondary(null);
        while (next != null) {
            Location nextNext = next.getSecondary();
            next.setSecondary(location);
            location = next;
            next = nextNext;
        }

        return location;
    }

    /**
     * A {@link Handle} is a reference to a location. The point of a location
     * handle is to be able to create them cheaply, and then resolve them into
     * actual locations later (if needed). This makes it possible to for example
     * delay looking up line numbers, for locations that are offset based.
     */
    public interface Handle {
        /**
         * Compute a full location for the given handle
         *
         * @return create a location for this handle
         */
        @NonNull
        Location resolve();

        /**
         * Sets the client data associated with this location. This is an optional
         * field which can be used by the creator of the {@link Location} to store
         * temporary state associated with the location.
         *
         * @param clientData the data to store with this location
         */
        void setClientData(@Nullable Object clientData);

        /**
         * Returns the client data associated with this location - an optional field
         * which can be used by the creator of the {@link Location} to store
         * temporary state associated with the location.
         *
         * @return the data associated with this location
         */
        @Nullable
        Object getClientData();
    }

    /** A default {@link Handle} implementation for simple file offsets */
    public static class DefaultLocationHandle implements Handle {
        private final File file;
        private final CharSequence contents;
        private final int startOffset;
        private final int endOffset;
        private Object clientData;

        /**
         * Constructs a new {@link DefaultLocationHandle}
         *
         * @param context the context pointing to the file and its contents
         * @param startOffset the start offset within the file
         * @param endOffset the end offset within the file
         */
        public DefaultLocationHandle(@NonNull Context context, int startOffset, int endOffset) {
            file = context.file;
            contents = context.getContents();
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        @Override
        @NonNull
        public Location resolve() {
            return create(file, contents, startOffset, endOffset);
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
            this.clientData = clientData;
        }

        @Override
        @Nullable
        public Object getClientData() {
            return clientData;
        }
    }

    public static class ResourceItemHandle implements Handle {
        private final ResourceItem item;

        public ResourceItemHandle(@NonNull ResourceItem item) {
            this.item = item;
        }
        @NonNull
        @Override
        public Location resolve() {
            // TODO: Look up the exact item location more
            // closely
            ResourceFile source = item.getSource();
            assert source != null : item;
            return create(source.getFile());
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
        }

        @Nullable
        @Override
        public Object getClientData() {
            return null;
        }
    }

    /**
     * Whether to look forwards, or backwards, or in both directions, when
     * searching for a pattern in the source code to determine the right
     * position range for a given symbol.
     * <p>
     * When dealing with bytecode for example, there are only line number entries
     * within method bodies, so when searching for the method declaration, we should only
     * search backwards from the first line entry in the method.
     */
    public enum SearchDirection {
        /** Only search forwards */
        FORWARD,

        /** Only search backwards */
        BACKWARD,

        /** Search backwards from the current end of line (normally it's the beginning of
         * the current line) */
        EOL_BACKWARD,

        /**
         * Search both forwards and backwards from the given line, and prefer
         * the match that is closest
         */
        NEAREST,

        /**
         * Search both forwards and backwards from the end of the given line, and prefer
         * the match that is closest
         */
        EOL_NEAREST,
    }

    /**
     * Extra information pertaining to finding a symbol in a source buffer,
     * used by {@link Location#create(File, CharSequence, int, String, String, SearchHints)}
     */
    public static class SearchHints {
        /**
         * the direction to search for the nearest match in (provided
         * {@code patternStart} is non null)
         */
        @NonNull
        private final SearchDirection direction;

        /** Whether the matched pattern should be a whole word */
        private boolean wholeWord;

        /**
         * Whether the matched pattern should be a Java symbol (so for example,
         * a match inside a comment or string literal should not be used)
         */
        private boolean javaSymbol;

        /**
         * Whether the matched pattern corresponds to a constructor; if so, look for
         * some other possible source aliases too, such as "super".
         */
        private boolean constructor;

        private SearchHints(@NonNull SearchDirection direction) {
            super();
            this.direction = direction;
        }

        /**
         * Constructs a new {@link SearchHints} object
         *
         * @param direction the direction to search in for the pattern
         * @return a new @link SearchHints} object
         */
        @NonNull
        public static SearchHints create(@NonNull SearchDirection direction) {
            return new SearchHints(direction);
        }

        /**
         * Indicates that pattern matches should apply to whole words only

         * @return this, for constructor chaining
         */
        @NonNull
        public SearchHints matchWholeWord() {
            wholeWord = true;

            return this;
        }

        /** @return true if the pattern match should be for whole words only */
        public boolean isWholeWord() {
            return wholeWord;
        }

        /**
         * Indicates that pattern matches should apply to Java symbols only
         *
         * @return this, for constructor chaining
         */
        @NonNull
        public SearchHints matchJavaSymbol() {
            javaSymbol = true;
            wholeWord = true;

            return this;
        }

        /** @return true if the pattern match should be for Java symbols only */
        public boolean isJavaSymbol() {
            return javaSymbol;
        }

        /**
         * Indicates that pattern matches should apply to constructors. If so, look for
         * some other possible source aliases too, such as "super".
         *
         * @return this, for constructor chaining
         */
        @NonNull
        public SearchHints matchConstructor() {
            constructor = true;
            wholeWord = true;
            javaSymbol = true;

            return this;
        }

        /** @return true if the pattern match should be for a constructor */
        public boolean isConstructor() {
            return constructor;
        }
    }
}
