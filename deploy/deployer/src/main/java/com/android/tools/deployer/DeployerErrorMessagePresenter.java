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

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A class that is responsible for represent the {@link DeployerException} in a user friendly string
 * to be displayed.
 */
public abstract class DeployerErrorMessagePresenter {

    public static DeployerErrorMessagePresenter createInstance() {
        return new JvmtiErrorMessagePresenter()
                .chain(
                        new IncompatibleChangePresenter()
                                .chain(
                                        new InternalErrorMessagePresenter()
                                                .chain(new BasicErrorMessagePresenter())));
    }

    private DeployerErrorMessagePresenter delegate;

    DeployerErrorMessagePresenter chain(DeployerErrorMessagePresenter delegate) {
        this.delegate = delegate;
        return this;
    }

    protected String passToDelegate(DeployerException de) {
        return delegate.present(de);
    }

    public abstract String present(DeployerException de);

    /**
     * Specializes the JVMTI related message. Ideally this should also include parts of the logcat
     * that tell us which exact class / method is the culprit.
     */
    private static class JvmtiErrorMessagePresenter extends DeployerErrorMessagePresenter {
        private static final Map<String, String> ERROR_CODE_TO_MESSAGE =
                ImmutableMap.<String, String>builder()
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED",
                                "Code changes were not applied: adding methods require an app restart.")
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED",
                                "Code changes were not applied: field changes require an app restart.")
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED",
                                "Code changes were not applied: class inheritance changes require an app restart.")
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED",
                                "Code changes were not applied: Method deletions require an app restart.")
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED",
                                "Code changes were not applied: Changes to class modifiers require an app restart.")
                        .put(
                                "JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED",
                                "Code changes were not applied: Changes to method modifiers require an app restart.")
                        .build();

        @Override
        public String present(DeployerException de) {
            if (de instanceof JvmtiRedefinerException) {
                List<String> errorCodes = ((JvmtiRedefinerException) de).getErrorCodes();
                if (errorCodes.isEmpty()) {
                    return passToDelegate(de);
                }
                return errorCodes
                        .stream()
                        .map(code -> ERROR_CODE_TO_MESSAGE.get(code))
                        .collect(Collectors.joining("\n"));
            }
            return passToDelegate(de);
        }
    }

    /**
     * Group of exception that is very unlikely to be seen by the user but might occur due to some
     * error internal to instant run or installation.
     */
    private static class InternalErrorMessagePresenter extends DeployerErrorMessagePresenter {
        @Override
        public String present(DeployerException de) {
            switch (de.getError()) {
                case DUMP_FAILED:
                case DUMP_UNKNOWN_PACKAGE:
                case INVALID_APK:
                case INTERRUPTED:
                    return "Internal Error: " + de.getMessage();
            }
            return passToDelegate(de);
        }
    }

    /** List of incompatable changes that needs restart. */
    private static class IncompatibleChangePresenter extends DeployerErrorMessagePresenter {
        @Override
        public String present(DeployerException de) {
            switch (de.getError()) {
                case CANNOT_SWAP_MANIFEST:
                    return "Changes were not applied: Modifying AndroidManifest.xml files requires an app restart.";
                case CANNOT_SWAP_STATIC_LIB:
                    return "Changes were not applied: Modifying .so files requires an app restart.";
                case CANNOT_SWAP_NEW_CLASS:
                    return "Changes were not applied: Adding a new class requires an app restart.";
            }
            return passToDelegate(de);
        }
    }

    /** The very last one that only prints the message */
    private static final class BasicErrorMessagePresenter extends DeployerErrorMessagePresenter {
        @Override
        public String present(DeployerException de) {
            return de.getMessage();
        }

        @Override
        DeployerErrorMessagePresenter chain(DeployerErrorMessagePresenter delegate) {
            throw new IllegalStateException("Cannot chain beyond BasicErrorMessagePresenter");
        }

        @Override
        protected String passToDelegate(DeployerException de) {
            throw new IllegalStateException("Cannot delegate beyond BasicErrorMessagePresenter");
        }
    }
}
