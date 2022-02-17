/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deployer.model.component;


import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.deployer.DeployerException;
import com.android.tools.manifest.parser.components.ManifestAppComponentInfo;
import com.android.utils.ILogger;
import java.util.Locale;

public class Complication extends WearComponent {

    public static class ShellCommand {

        public static String REMOVE_ALL_INSTANCES_FROM_CURRENT_WF =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-complication --ecn component ";
        // + component name

        // More context go/wear-surface-debug
        static final String ADD_COMPLICATION_TO_WATCH_FACE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication --ecn component '%s' --ecn watchface '%s' --ei slot %d --ei type %d";
    }

    public Complication(
            @NonNull ManifestAppComponentInfo info,
            @NonNull String appId,
            @NonNull IDevice device,
            @NonNull ILogger logger) {
        super(device, appId, info, logger);
    }

    @Override
    public void activate(
            @NonNull String extraFlags,
            @NonNull Mode activationMode,
            @NonNull IShellOutputReceiver receiver)
            throws DeployerException {
        ComplicationParams params = ComplicationParams.parse(extraFlags);
        logger.info(
                "Activating WatchFace '%s' %s",
                info.getQualifiedName(), activationMode.equals(Mode.DEBUG) ? "for debug" : "");

        if (activationMode.equals(Mode.DEBUG)) {
            setUpAmDebugApp();
            setUpDebugSurfaceDebugApp();
        }
        String command = getAddComplicationCommand(params);
        runStartCommand(command, receiver, logger);
    }

    @NonNull
    private String getAddComplicationCommand(ComplicationParams param) {
        return String.format(
                Locale.US,
                ShellCommand.ADD_COMPLICATION_TO_WATCH_FACE,
                getFQEscapedName(),
                AppComponent.getFQEscapedName(param.watchFaceAppId, param.watchFaceName),
                param.slot,
                param.type.getTypeValue());
    }

    public enum ComplicationType {
        SHORT_TEXT(3),
        LONG_TEXT(4),
        RANGED_VALUE(5),
        MONOCHROMATIC_IMAGE(6),
        SMALL_IMAGE(7),
        PHOTO_IMAGE(8);

        private final int typeValue;

        ComplicationType(int typeValue) {
            this.typeValue = typeValue;
        }

        int getTypeValue() {
            return typeValue;
        }
    }

    private static class ComplicationParams {

        static String INCORRECT_FORMAT_ERROR =
                "Incorrect extra flags for Complication `%s`. Expected format `WATCH_FACE_APP_ID WATCH_FACE_FQ_NAME SLOT_NUM COMPLICATION_TYPE`";

        final String watchFaceAppId;

        final String watchFaceName;

        final int slot;

        final ComplicationType type;

        private ComplicationParams(
                @NonNull String watchFaceAppId,
                @NonNull String watchFaceName,
                int slot,
                @NonNull ComplicationType type) {
            this.watchFaceAppId = watchFaceAppId;
            this.watchFaceName = watchFaceName;
            this.slot = slot;
            this.type = type;
        }

        static ComplicationParams parse(String rawParams) throws DeployerException {
            try {
                String[] params = rawParams.split("\\s+");
                String watchfaceAppId = params[0];
                String watchface = params[1];
                int slot = Integer.parseInt(params[2]);
                ComplicationType type = ComplicationType.valueOf(params[3].toUpperCase(Locale.US));
                return new ComplicationParams(watchfaceAppId, watchface, slot, type);
            } catch (Exception e) {
                throw DeployerException.componentActivationException(
                        String.format(INCORRECT_FORMAT_ERROR, rawParams) + ". " + e.getMessage());
            }
        }
    }
}
