/*
 * Copyright (C) 2019 The Android Open Source Project
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

package test.inspector;

import androidx.annotation.NonNull;
import androidx.inspection.Connection;
import java.util.Arrays;
import test.inspector.api.PayloadInspectorApi;
import test.inspector.api.TestInspectorApi;

/**
 * An inspector that sends large responses and events, which should, as a result, get chunked up.
 */
public final class PayloadInspector extends TestInspector {

    private final byte[] LARGE_PAYLOAD;

    PayloadInspector(@NonNull Connection connection) {
        super(connection);

        LARGE_PAYLOAD = new byte[1024 * 1024];
        for (int i = 0; i < LARGE_PAYLOAD.length; i++) {
            LARGE_PAYLOAD[i] = (byte) i;
        }
    }

    @NonNull
    @Override
    protected byte[] handleReceiveCommand(@NonNull byte[] bytes) {
        for (PayloadInspectorApi.Command command : PayloadInspectorApi.Command.values()) {
            if (Arrays.equals(command.toByteArray(), bytes)) {
                switch (command) {
                    case SEND_LARGE_RESPONSE:
                        return LARGE_PAYLOAD;
                    case SEND_LARGE_EVENT:
                        getConnection().sendEvent(LARGE_PAYLOAD);
                        return TestInspectorApi.Reply.SUCCESS.toByteArray();
                }
                break;
            }
        }

        return TestInspectorApi.Reply.ERROR.toByteArray();
    }
}
