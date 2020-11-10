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

package com.android.tools.deploy.instrument;

import java.lang.reflect.Field;

public class LiveLiteralSupport {
    public static final String LIVE_LITERAL_KT = "androidx.compose.runtime.internal.LiveLiteralKt";

    /**
     * For performance reasons, the runtime will start checking for literal update request until
     * isLiveLiteralsEnabled is set to true.
     *
     * @param liveLiteralKtClass androidx.compose.runtime.internal.LiveLiteralKt needs to be passed
     *     in from a JVMTI class search because this class will be loaded in the boot classloader
     *     and will not be able to find the application classes.
     */
    public static void enable(Class<?> liveLiteralKtClass) {
        try {
            Field enabled = liveLiteralKtClass.getDeclaredField("isLiveLiteralsEnabled");
            if (!liveLiteralKtClass.getName().equals(LIVE_LITERAL_KT)) {
                throw new IllegalArgumentException(
                        "Expecting androidx.compose.runtime.internal.LiveLiteralKt but got "
                                + liveLiteralKtClass.getName()
                                + " class during LiveLiteralSupport.enable()");
            }
            enabled.setAccessible(true);
            enabled.setBoolean(liveLiteralKtClass, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
