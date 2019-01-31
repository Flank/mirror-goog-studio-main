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
package com.android.tools.deployer;

import static com.android.tools.deployer.DeployerException.Error.REDEFINER_ERROR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Errors caused by incompatible changes defined by JVMTI spec. */
public class JvmtiRedefinerException extends DeployerException {

    private static final Map<String, String> ERROR_CODE_TO_MESSAGE =
            ImmutableMap.<String, String>builder()
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED",
                            "adding methods require an app restart.")
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED",
                            "field changes require an app restart.")
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED",
                            "class inheritance changes require an app restart.")
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED",
                            "Method deletions require an app restart.")
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED",
                            "Changes to class modifiers require an app restart.")
                    .put(
                            "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED",
                            "Changes to method modifiers require an app restart.")
                    .build();

    private final List<String> errorCodes;

    public JvmtiRedefinerException(List<String> errorCodes) {
        super(REDEFINER_ERROR, "Code changes were not applied.");
        this.errorCodes = ImmutableList.copyOf(errorCodes);
    }

    @Override
    public String getMessage() {
        String messages =
                errorCodes
                        .stream()
                        .map(code1 -> ERROR_CODE_TO_MESSAGE.get(code1))
                        .collect(Collectors.joining("\n"));
        return super.getMessage() + "\n" + messages;
    }
}
