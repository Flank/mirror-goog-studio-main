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

package com.android.tools.agent.layoutinspector;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Configuration;
import com.android.tools.agent.layoutinspector.testing.StandardView;
import com.android.tools.agent.layoutinspector.testing.StringTable;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent;
import org.junit.Test;

public class ResourceConfigurationTest {

    @Test
    public void testProtoBuilder() throws Exception {
        System.loadLibrary("jni-test");
        long event = ComponentTreeTest.allocateEvent();

        ComponentTree treeBuilder = new ComponentTree();
        treeBuilder.writeTree(event, StandardView.createLinearLayoutWithTextView());

        ComponentTreeEvent proto =
                ComponentTreeEvent.parseFrom(ComponentTreeTest.toByteArray(event));
        StringTable table = new StringTable(proto.getStringList());
        LayoutInspectorProto.ResourceConfiguration resources = proto.getResources();
        LayoutInspectorProto.Configuration configuration = resources.getConfiguration();

        assertThat(table.get(resources.getAppPackageName())).isEqualTo(StandardView.PACKAGE_NAME);
        assertThat(configuration.getFontScale()).isEqualTo(1.5f);
        assertThat(configuration.getCountryCode()).isEqualTo(310);
        assertThat(configuration.getNetworkCode()).isEqualTo(4);
        assertThat(configuration.getScreenLayout())
                .isEqualTo(
                        Configuration.SCREENLAYOUT_SIZE_LARGE
                                | Configuration.SCREENLAYOUT_LONG_NO
                                | Configuration.SCREENLAYOUT_LAYOUTDIR_RTL);
        assertThat(configuration.getColorMode())
                .isEqualTo(
                        Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_NO
                                | Configuration.COLOR_MODE_HDR_YES);
        assertThat(configuration.getTouchScreen()).isEqualTo(Configuration.TOUCHSCREEN_FINGER);
        assertThat(configuration.getKeyboard()).isEqualTo(Configuration.KEYBOARD_QWERTY);
        assertThat(configuration.getKeyboardHidden()).isEqualTo(Configuration.KEYBOARDHIDDEN_YES);
        assertThat(configuration.getHardKeyboardHidden())
                .isEqualTo(Configuration.HARDKEYBOARDHIDDEN_NO);
        assertThat(configuration.getNavigation()).isEqualTo(Configuration.NAVIGATION_WHEEL);
        assertThat(configuration.getNavigationHidden())
                .isEqualTo(Configuration.NAVIGATIONHIDDEN_YES);
        assertThat(configuration.getUiMode())
                .isEqualTo(Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES);
        assertThat(configuration.getSmallestScreenWidth())
                .isEqualTo(Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED);
        assertThat(configuration.getDensity()).isEqualTo(367);
        assertThat(configuration.getOrientation()).isEqualTo(Configuration.ORIENTATION_PORTRAIT);
        assertThat(configuration.getScreenWidth()).isEqualTo(1080);
        assertThat(configuration.getScreenHeight()).isEqualTo(2280);
    }
}
