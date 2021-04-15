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
package com.android.ddmlib.logcat;

import com.android.annotations.NonNull;
import com.android.ddmlib.Log.LogLevel;
import java.time.Instant;
import java.util.List;
import junit.framework.TestCase;

public class LogCatFilterTest extends TestCase {

    public void testFilterByLogLevel() {
        LogCatFilter filter = new LogCatFilter("", "", "", "", "", LogLevel.DEBUG);

        /* filter message below filter's log level */
        LogCatMessage msg = new MessageBuilder().setLevel(LogLevel.VERBOSE).build();
        assertFalse(filter.matches(msg));

        /* do not filter message above filter's log level */
        msg = new MessageBuilder().setLevel(LogLevel.ERROR).build();
        assertTrue(filter.matches(msg));
    }

    public void testFilterByPid() {
        LogCatFilter filter = new LogCatFilter("", "", "", "123", "", LogLevel.VERBOSE);

        /* show message with pid matching filter */
        LogCatMessage msg = new MessageBuilder().setPid(123).build();
        assertTrue(filter.matches(msg));

        /* don't show message with pid not matching filter */
        msg = new MessageBuilder().setPid(12).build();
        assertFalse(filter.matches(msg));
    }

    public void testFilterByAppNameRegex() {
        LogCatFilter filter = new LogCatFilter("", "", "", "", "dalvik.*", LogLevel.VERBOSE);

        /* show message with process name matching filter */
        LogCatMessage msg = new MessageBuilder().setAppName("dalvikvm1").build();
        assertTrue(filter.matches(msg));

        /* don't show message with process name not matching filter */
        msg = new MessageBuilder().setAppName("system").build();
        assertFalse(filter.matches(msg));
    }

    public void testFilterByTagRegex() {
        LogCatFilter filter = new LogCatFilter("", "tag.*", "", "", "", LogLevel.VERBOSE);

        /* show message with tag matching filter */
        LogCatMessage msg = new MessageBuilder().setTag("tag123").build();
        assertTrue(filter.matches(msg));

        msg = new MessageBuilder().setTag("ta123").build();
        assertFalse(filter.matches(msg));
    }

    public void testFilterByTextRegex() {
        LogCatFilter filter = new LogCatFilter("", "", "text.*", "", "", LogLevel.VERBOSE);

        /* show message with text matching filter */
        LogCatMessage msg = new MessageBuilder().setMessage("text123").build();
        assertTrue(filter.matches(msg));

        msg = new MessageBuilder().setMessage("te123").build();
        assertFalse(filter.matches(msg));
    }

    public void testMatchingText() {
        LogCatMessage msg = new MessageBuilder().setMessage("message with word1 and word2").build();
        assertTrue(search("word1 with", msg));
        assertTrue(search("text:w.* ", msg));
        assertFalse(search("absent", msg));
    }

    public void testTagKeyword() {
        LogCatMessage msg = new MessageBuilder().setTag("tag").setMessage("sample message").build();
        assertFalse(search("t.*", msg));
        assertTrue(search("tag:t.*", msg));
    }

    public void testPidKeyword() {
        LogCatMessage msg = new MessageBuilder().setPid(123).setMessage("sample message").build();
        assertFalse(search("123", msg));
        assertTrue(search("pid:123", msg));
    }

    public void testAppNameKeyword() {
        LogCatMessage msg = new MessageBuilder().setAppName("dalvik").setMessage("sample message")
                .build();
        assertFalse(search("dalv.*", msg));
        assertTrue(search("app:dal.*k", msg));
    }

    public void testCaseSensitivity() {
        LogCatMessage msg = new MessageBuilder().setMessage("Sample message").build();

        // if regex has an upper case character, it should be
        // treated as a case sensitive search
        assertFalse(search("Message", msg));

        // if regex is all lower case, then it should be a
        // case insensitive search
        assertTrue(search("sample", msg));
    }

    /**
     * Helper method: search if the query string matches the message.
     *
     * @param query words to search for
     * @param message text to search in
     * @return true if the encoded query is present in message
     */
    private static boolean search(String query, LogCatMessage message) {
        List<LogCatFilter> filters = LogCatFilter.fromString(query, LogLevel.VERBOSE);

        /* all filters have to match for the query to match */
        for (LogCatFilter f : filters) {
            if (!f.matches(message)) {
                return false;
            }
        }
        return true;
    }

    private static class MessageBuilder {

        @NonNull private LogLevel mLevel = LogLevel.VERBOSE;

        private int mPid = -1;

        private int mTid = -1;

        @NonNull private String mAppName = "";

        @NonNull private String mTagName = "";

        @NonNull private Instant mTimestamp = Instant.EPOCH;

        @NonNull private String mMessage = "";

        public MessageBuilder setLevel(@NonNull LogLevel level) {
            mLevel = level;
            return this;
        }

        public MessageBuilder setPid(int pid) {
            mPid = pid;
            return this;
        }

        public MessageBuilder setTid(int tid) {
            mTid = tid;
            return this;
        }

        public MessageBuilder setAppName(@NonNull String appName) {
            mAppName = appName;
            return this;
        }

        public MessageBuilder setTag(@NonNull String tagName) {
            mTagName = tagName;
            return this;
        }

        public MessageBuilder setTimestamp(@NonNull Instant timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        public MessageBuilder setMessage(@NonNull String message) {
            mMessage = message;
            return this;
        }

        public LogCatMessage build() {
            return new LogCatMessage(
                    new LogCatHeader(mLevel, mPid, mTid, mAppName, mTagName, mTimestamp), mMessage);
        }
    }
}
