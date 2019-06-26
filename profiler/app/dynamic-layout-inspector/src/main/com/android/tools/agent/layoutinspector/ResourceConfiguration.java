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

package com.android.tools.agent.layoutinspector;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;
import com.android.tools.agent.layoutinspector.common.Resource;
import com.android.tools.agent.layoutinspector.common.StringTable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/** Services for writing the Configuration into a protobuf. */
class ResourceConfiguration {
    private final StringTable mStringTable;

    public ResourceConfiguration(StringTable stringTable) {
        mStringTable = stringTable;
    }

    public void writeConfiguration(long event, View view) {
        Context context = view.getContext();
        addAppData(
                event,
                Build.VERSION.SDK_INT,
                toInt(Build.VERSION.CODENAME),
                toInt(context.getPackageName()));

        Resource theme = Resource.fromResourceId(view, getThemeResId(context));
        if (theme != null) {
            addTheme(
                    event,
                    toInt(theme.getNamespace()),
                    toInt(theme.getType()),
                    toInt(theme.getName()));
        }

        Configuration config = context.getResources().getConfiguration();
        addConfiguration(
                event,
                config.fontScale,
                config.mcc,
                config.mnc,
                config.screenLayout,
                config.colorMode,
                config.touchscreen,
                config.keyboard,
                config.keyboardHidden,
                config.hardKeyboardHidden,
                config.navigation,
                config.navigationHidden,
                config.uiMode,
                config.smallestScreenWidthDp,
                config.densityDpi,
                config.orientation,
                config.screenWidthDp,
                config.screenHeightDp);

        LocaleList locales = config.getLocales();
        if (locales.size() > 0) {
            Locale locale = locales.get(0);
            addLocale(
                    event,
                    toInt(locale.getLanguage()),
                    toInt(locale.getCountry()),
                    toInt(locale.getVariant()),
                    toInt(locale.getScript()));
        }
    }

    private int getThemeResId(Context context) {
        try {
            // This method is hidden:
            Method method = Context.class.getDeclaredMethod("getThemeResId");
            Integer themeId = (Integer) method.invoke(context);
            return themeId != null ? themeId : 0;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return 0;
        }
    }

    private int toInt(String value) {
        return mStringTable.generateStringId(value);
    }

    /** Adds version and app package name to a ResourceConfiguration protobuf */
    private native void addAppData(long event, int sdkVersion, int sdkCodename, int packageName);

    /** Add a theme resource value to a ResourceConfiguration protobuf */
    private native void addTheme(long config, int namespace, int type, int name);

    /** Add the configuration values to a ResourceConfiguration protobuf */
    private native void addConfiguration(
            long config,
            float fontScale,
            int mcc,
            int mnc,
            int screenLayout,
            int colorMode,
            int touchscreen,
            int keyboard,
            int keyboardHidden,
            int hardKeyboardHidden,
            int navigation,
            int navigationHidden,
            int uiMode,
            int smallestScreenWidthDp,
            int densityDpi,
            int orientation,
            int screenWidthDp,
            int screenHeightDp);

    /** Add the default locale values to a ResourceConfiguration protobuf */
    private native void addLocale(long config, int language, int country, int variant, int script);
}
