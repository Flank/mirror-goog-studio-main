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

import com.android.tools.deploy.proto.Deploy;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;

/**
 * Represents a failed deployment. When installing, apply changes or apply code changes failed, this
 * will be raised containing the information needed by the UI (IDE or command line) to surface the
 * error and the possible actions to the user.
 */
public class DeployerException extends Exception {

    // TODO(b/117673388): Add "Learn More" hyperlink/call to action when we finally have the webpage up.
    public enum ResolutionAction {
        // No possible resolution action exists.
        NONE,
        // Install and re-run the application.
        RUN_APP,
        // Apply changes to the application.
        APPLY_CHANGES,
        // Retry the previously attempted action.
        RETRY,
    }

    /**
     * The ordinal of this enum is used as the return code for the command line runners, the first
     * value NO_ERROR is not used as is zero and represents no error on the command line.
     */
    public enum Error {
        NO_ERROR("", "", "", ResolutionAction.NONE), // Should not be used

        CANNOT_SWAP_BEFORE_API_26(
                "Apply Changes is only supported on API 26 or newer",
                "",
                "",
                ResolutionAction.NONE),

        // Specific errors that can occur before the swap process.

        DUMP_UNKNOWN_PACKAGE(
                "Package not found on device.",
                "The package '%s' was not found on the device. Is the app installed?",
                "Install and run app",
                ResolutionAction.RUN_APP),

        DUMP_UNKNOWN_PROCESS(
                "No running app process found.", "", "Run app", ResolutionAction.RUN_APP),

        REMOTE_APK_NOT_FOUND_IN_DB(
                "Android Studio was unable to recognize the APK(s) currently installed on the device.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        DIFFERENT_NUMBER_OF_APKS(
                "A different number of APKs were found on the device than on the host.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        DIFFERENT_APK_NAMES(
                "The naming scheme of APKs on the device differ from the APKs on the host.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        // Errors pertaining to un-swappable changes.

        CLASS_NOT_FOUND(
                "Class not found: %s",
                "Class '%s' was not found during swap.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_STATIC_LIB(
                "Modifications to shared libraries require an app restart.",
                "File '%s' was modified.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_MANIFEST(
                "Modifications to AndroidManifest.xml require an app restart.",
                "Manifest '%s' was modified.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_SWAP_RESOURCE(
                "Modifying resources requires an activity restart.",
                "Resource '%s' was modified.",
                "Apply changes and restart activity",
                ResolutionAction.APPLY_CHANGES),

        CANNOT_SWAP_CRASHLYTICS_PROPERTY(
                "Crashlytics modified your build ID, which requires an activity restart. "
                        + "<a href=\"https://d.android.com/r/studio-ui/apply-changes-crashlytics-buildid\""
                        + ">See here</a>",
                "Resource '%s' was modified.",
                "Apply changes and restart activity",
                ResolutionAction.APPLY_CHANGES),

        CANNOT_ADD_RESOURCE(
                "Adding or renaming a resource requires an application restart.",
                "Resource '%s' (%s) was added.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_REMOVE_RESOURCE(
                "Removing a resource requires an application restart.",
                "Resource '%s' (%s) was removed.",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        // Errors that are reported to us by jvmti.

        CANNOT_ADD_METHOD(
                "Adding a new method requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_MODIFY_FIELDS(
                "Adding or removing a field requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_INHERITANCE(
                "Changes to class inheritance require an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_DELETE_METHOD(
                "Removing a method requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_CLASS_MODIFIERS(
                "Changing class modifiers requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        CANNOT_CHANGE_METHOD_MODIFIERS(
                "Changing method modifiers requires an app restart.",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        VERIFICATION_ERROR(
                "New code fails verification",
                "",
                "Reinstall and restart app",
                ResolutionAction.RUN_APP),

        JVMTI_ERROR("JVMTI error: %s", "", "Reinstall and restart app", ResolutionAction.RUN_APP),

        // Catch-all errors for when an arbitrary failure may occur.

        DUMP_FAILED(
                "We were unable to deploy your changes.", "%s", "Retry", ResolutionAction.RETRY),

        DUMP_MIXED_ARCH(
                "Application with process in both 32 and 64 bit mode.",
                "%s",
                "Retry",
                ResolutionAction.RETRY),

        PREINSTALL_FAILED(
                "The application could not be installed.", "%s", "Retry", ResolutionAction.RETRY),

        INSTALL_FAILED(
                "The application could not be installed%s", "%s", "Retry", ResolutionAction.RETRY),

        SWAP_FAILED(
                "We were unable to deploy your changes%s", "%s", "Retry", ResolutionAction.RETRY),

        PREINSTALL_APPID_CHANGED(
                "Cannot preinstall: apks have different package name (%s and %s)",
                "%s", "Retry", ResolutionAction.RETRY),

        AGENT_SWAP_FAILED(
                "We were unable to deploy your changes%s", "%s", "Retry", ResolutionAction.RETRY),

        PARSE_FAILED(
                "We were unable to deploy your changes.", "%s", "Retry", ResolutionAction.RETRY),

        SWAP_MULTIPLE_PACKAGES(
                "Cannot swap multiple packages", "", "Retry", ResolutionAction.RETRY),

        INSTALLER_IO_EXCEPTION(
                "IOException occurred within Installer", "%s", "Retry", ResolutionAction.RETRY),

        UNKNOWN_JVMTI_ERROR("Invalid error code %s", "", "Retry", ResolutionAction.RETRY),

        JDWP_REDEFINE_CLASSES_EXCEPTION(
                "Exception during VM RedfineClasses", "%s", "Retry", ResolutionAction.RETRY),

        ABIS_FIELD_NOT_FOUND(
                "android.os.Build does not contain the expected ABI fields",
                "",
                "Retry",
                ResolutionAction.RETRY),

        ATTACHAGENT_NOT_FOUND(
                "dalvik.system.VMDebug does not contain proper attachAgent method",
                "",
                "Retry",
                ResolutionAction.RETRY),

        ATTACHAGENT_EXCEPTION(
                "Debugger attachAgent invocation failed due to %s",
                "%s", "Retry", ResolutionAction.RETRY),

        JDI_INVAlID_STATE("Invalid Redefinition State.", "", "", ResolutionAction.RETRY),

        INTERRUPTED("Deployment was interrupted.", "%s", "Retry", ResolutionAction.RETRY),

        OPERATION_NOT_SUPPORTED("Operation not supported.", "%s", "", ResolutionAction.NONE);

        private final String message;
        private final String details;

        private final String callToAction;
        private final ResolutionAction action;

        Error(String message, String details, String callToAction, ResolutionAction action) {
            this.message = message;
            this.details = details;
            this.callToAction = callToAction;
            this.action = action;
        }

        public String getCallToAction() {
            return callToAction;
        }

        public ResolutionAction getResolution() {
            return action;
        }
    }

    private Error error;
    private String code;
    private String details;

    private static String[] NO_ARGS = {};

    private DeployerException(Error error) {
        this(error, null, NO_ARGS, NO_ARGS);
    }

    private DeployerException(Error error, String[] messageArgs, String... detailArgs) {
        this(error, null, messageArgs, detailArgs);
    }

    private DeployerException(Error error, Enum code, String[] messageArgs, String... detailArgs) {
        super(String.format(error.message, (Object[]) messageArgs));
        this.error = error;
        this.code = code == null ? error.name() : error.name() + "." + code.name();
        this.details = String.format(error.details, (Object[]) detailArgs);
    }

    public Error getError() {
        return error;
    }

    public String getId() {
        return code;
    }

    public String getDetails() {
        return details;
    }

    public static DeployerException unknownPackage(String packageName) {
        return new DeployerException(Error.DUMP_UNKNOWN_PACKAGE, NO_ARGS, packageName);
    }

    // TODO: Make this package-aware.
    public static DeployerException unknownProcess() {
        return new DeployerException(Error.DUMP_UNKNOWN_PROCESS);
    }

    public static DeployerException remoteApkNotFound() {
        return new DeployerException(Error.REMOTE_APK_NOT_FOUND_IN_DB);
    }

    public static DeployerException apkCountMismatch() {
        return new DeployerException(Error.DIFFERENT_NUMBER_OF_APKS);
    }

    public static DeployerException apkNameMismatch() {
        return new DeployerException(Error.DIFFERENT_APK_NAMES);
    }

    public static DeployerException changedSharedObject(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_STATIC_LIB, NO_ARGS, filePath);
    }

    public static DeployerException changedManifest(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_MANIFEST, NO_ARGS, filePath);
    }

    public static DeployerException changedResources(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_RESOURCE, NO_ARGS, filePath);
    }

    public static DeployerException changedCrashlyticsBuildId(String filePath) {
        return new DeployerException(Error.CANNOT_SWAP_CRASHLYTICS_PROPERTY, NO_ARGS, filePath);
    }

    public static DeployerException addedResources(String name, String type) {
        return new DeployerException(Error.CANNOT_ADD_RESOURCE, NO_ARGS, name, type);
    }

    public static DeployerException removedResources(String name, String type) {
        return new DeployerException(Error.CANNOT_REMOVE_RESOURCE, NO_ARGS, name, type);
    }

    public static DeployerException classNotFound(String className) {
        return new DeployerException(Error.CLASS_NOT_FOUND, new String[] {className}, className);
    }

    // JVMTI error codes for which we have specific error messages.
    private static final ImmutableMap<JvmtiErrorCode, Error> ERROR_CODE_TO_ERROR =
            ImmutableMap.<JvmtiErrorCode, Error>builder()
                    .put(
                            JvmtiErrorCode.JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED,
                            Error.CANNOT_ADD_METHOD)
                    .put(
                            JvmtiErrorCode.JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED,
                            Error.CANNOT_MODIFY_FIELDS)
                    .put(
                            JvmtiErrorCode.JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED,
                            Error.CANNOT_CHANGE_INHERITANCE)
                    .put(
                            JvmtiErrorCode.JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED,
                            Error.CANNOT_DELETE_METHOD)
                    .put(
                            JvmtiErrorCode
                                    .JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED,
                            Error.CANNOT_CHANGE_CLASS_MODIFIERS)
                    .put(
                            JvmtiErrorCode
                                    .JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED,
                            Error.CANNOT_CHANGE_METHOD_MODIFIERS)
                    .put(JvmtiErrorCode.JVMTI_ERROR_FAILS_VERIFICATION, Error.VERIFICATION_ERROR)
                    .build();

    public static DeployerException jvmtiError(JvmtiErrorCode code) {
        if (ERROR_CODE_TO_ERROR.containsKey(code)) {
            return new DeployerException(ERROR_CODE_TO_ERROR.get(code));
        }
        return new DeployerException(Error.JVMTI_ERROR, code, new String[] {code.name()});
    }

    public static DeployerException dumpFailed(String reason) {
        return new DeployerException(Error.DUMP_FAILED, NO_ARGS, reason);
    }

    public static DeployerException dumpMixedArch(String reason) {
        return new DeployerException(Error.DUMP_MIXED_ARCH, NO_ARGS, reason);
    }

    public static DeployerException parseFailed(String reason) {
        return new DeployerException(Error.PARSE_FAILED, NO_ARGS, reason);
    }

    public static DeployerException preinstallFailed(String reason) {
        return new DeployerException(Error.PREINSTALL_FAILED, NO_ARGS, reason);
    }

    public static DeployerException installFailed(InstallStatus code, String reason) {
        String suffix = code != InstallStatus.UNKNOWN_ERROR ? ": " + code.name() : ".";
        return new DeployerException(Error.INSTALL_FAILED, code, new String[] {suffix}, reason);
    }

    public static DeployerException swapFailed(Deploy.SwapResponse.Status code) {
        String suffix = code != Deploy.SwapResponse.Status.UNKNOWN ? ": " + code.name() : ".";
        return new DeployerException(Error.SWAP_FAILED, code, new String[] {suffix}, "");
    }

    public static DeployerException agentSwapFailed(Deploy.AgentSwapResponse.Status code) {
        String suffix = code != Deploy.AgentSwapResponse.Status.UNKNOWN ? ": " + code.name() : ".";
        return new DeployerException(Error.AGENT_SWAP_FAILED, code, new String[] {suffix}, "");
    }
    
    public static DeployerException appIdChanged(String before, String after) {
        return new DeployerException(
                Error.PREINSTALL_APPID_CHANGED, new String[] {before, after}, "");
    }

    public static DeployerException swapMultiplePackages() {
        return new DeployerException(Error.SWAP_MULTIPLE_PACKAGES, NO_ARGS, NO_ARGS);
    }

    public static DeployerException installerIoException(IOException e) {
        return new DeployerException(Error.INSTALLER_IO_EXCEPTION, NO_ARGS, e.getMessage());
    }

    public static DeployerException unknownJvmtiError(String type) {
        return new DeployerException(Error.UNKNOWN_JVMTI_ERROR, new String[] {type}, NO_ARGS);
    }

    public static DeployerException jdwpRedefineClassesException(Throwable t) {
        return new DeployerException(
                Error.JDWP_REDEFINE_CLASSES_EXCEPTION, NO_ARGS, t.getMessage());
    }

    public static DeployerException attachAgentNotFound() {
        return new DeployerException(Error.ATTACHAGENT_NOT_FOUND, NO_ARGS, NO_ARGS);
    }

    public static DeployerException abisFieldNotFound() {
        return new DeployerException(Error.ABIS_FIELD_NOT_FOUND, NO_ARGS, NO_ARGS);
    }

    public static DeployerException attachAgentException(Exception e) {
        return new DeployerException(
                Error.ATTACHAGENT_EXCEPTION,
                new String[] {e.getClass().getSimpleName()},
                e.getMessage());
    }

    public static DeployerException jdiInvalidState() {
        return new DeployerException(Error.JDI_INVAlID_STATE, NO_ARGS, NO_ARGS);
    }

    public static DeployerException interrupted(String reason) {
        return new DeployerException(Error.INTERRUPTED, NO_ARGS, reason);
    }

    public static DeployerException operationNotSupported(String reason) {
        return new DeployerException(Error.OPERATION_NOT_SUPPORTED, NO_ARGS, reason);
    }

    public static DeployerException apiNotSupported() {
        return new DeployerException(Error.CANNOT_SWAP_BEFORE_API_26, NO_ARGS, NO_ARGS);
    }
}
