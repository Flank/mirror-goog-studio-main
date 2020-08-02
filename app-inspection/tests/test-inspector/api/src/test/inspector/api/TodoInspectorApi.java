/*
 * Copyright (C) 2020 The Android Open Source Project
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

package test.inspector.api;

import androidx.annotation.NonNull;

public final class TodoInspectorApi {
    public enum Command {
        UNKNOWN,

        COUNT_TODO_GROUPS,
        COUNT_TODO_ITEMS;

        @NonNull
        public byte[] toByteArray() {
            return new byte[] {(byte) ordinal()};
        }
    }

    public enum Event {
        UNKNOWN,

        TODO_GROUP_CREATING,
        TODO_GROUP_CREATED,
        TODO_NAMED_GROUP_CREATING,
        TODO_NAMED_GROUP_CREATED,
        TODO_ITEM_CREATING,
        TODO_ITEM_CREATED,

        /** Should be sent with the index of the group being removed. */
        TODO_GROUP_REMOVING,

        TODO_GOT_ITEMS_COUNT,
        TODO_GOT_BYTE_ITEMS_COUNT,
        TODO_GOT_SHORT_ITEMS_COUNT,
        TODO_GOT_LONG_ITEMS_COUNT,

        TODO_GOT_GROUP_TRAILING_CHAR,

        TODO_GOT_AVERAGE_ITEMS_COUNT,
        TODO_GOT_DOUBLE_AVERAGE_ITEMS_COUNT,

        TODO_CLEARED_ALL_ITEMS,
        TODO_HAS_EMPTY_TODO_LIST,

        TODO_ITEMS_PREFILLING,
        TODO_ITEMS_PREFILLED,
        TODO_LOGGING_ITEM,

        TODO_LAST_ITEM_SELECTING,
        TODO_LAST_ITEM_SELECTED,

        TODO_ECHOING,
        TODO_ECHOED;

        @NonNull
        public byte[] toByteArray() {
            return new byte[] {(byte) ordinal()};
        }

        @NonNull
        public byte[] toByteArrayWithArg(byte arg) {
            return new byte[] {(byte) ordinal(), arg};
        }

        @NonNull
        public byte[] toByteArrayWithArg(byte[] arg) {
            byte[] result = new byte[arg.length + 1];
            result[0] = (byte) ordinal();
            System.arraycopy(arg, 0, result, 1, arg.length);
            return result;
        }
    }
}
