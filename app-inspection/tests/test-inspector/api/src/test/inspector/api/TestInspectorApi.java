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

public final class TestInspectorApi {
    public enum Reply {
        ERROR((byte) -2),
        SUCCESS((byte) -1);

        private final byte value;

        Reply(byte value) {
            // Use negative numbers so these enums don't compete with other enum oridinal() values.
            assert (value < 0);
            this.value = value;
        }

        @NonNull
        public byte[] toByteArray() {
            return new byte[] {value};
        }
    }
}
