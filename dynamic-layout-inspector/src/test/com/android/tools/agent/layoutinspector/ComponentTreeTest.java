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

import android.view.WindowManager;
import com.android.tools.agent.layoutinspector.testing.ResourceEntry;
import com.android.tools.agent.layoutinspector.testing.StandardView;
import com.android.tools.agent.layoutinspector.testing.StringTable;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent;
import org.junit.Test;

public class ComponentTreeTest {

    @Test
    public void testProtoBuilder() throws Exception {
        System.loadLibrary("jni-test");
        long event = allocateEvent();

        ComponentTree treeBuilder = new ComponentTree();
        treeBuilder.writeTree(event, StandardView.createLinearLayoutWithTextView());

        ComponentTreeEvent proto = ComponentTreeEvent.parseFrom(toByteArray(event));
        StringTable table = new StringTable(proto.getStringList());
        LayoutInspectorProto.View layout = proto.getRoot();

        assertThat(layout.getDrawId()).isEqualTo(10);
        assertThat(table.get(layout.getViewId()))
                .isEqualTo(new ResourceEntry("id", "pck", "linearLayout1"));
        assertThat(table.get(layout.getLayout()))
                .isEqualTo(new ResourceEntry("layout", "pck", "main_activity"));
        assertThat(layout.getX()).isEqualTo(0);
        assertThat(layout.getY()).isEqualTo(0);
        assertThat(layout.getWidth()).isEqualTo(980);
        assertThat(layout.getHeight()).isEqualTo(2000);
        assertThat(table.get(layout.getClassName())).isEqualTo("RootLinearLayout");
        assertThat(table.get(layout.getPackageName())).isEqualTo("android.widget");
        assertThat(table.get(layout.getTextValue())).isEqualTo("");
        assertThat(layout.getLayoutFlags())
                .isEqualTo(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        assertThat(layout.getSubViewCount()).isEqualTo(1);

        LayoutInspectorProto.View textView = layout.getSubView(0);
        assertThat(textView.getDrawId()).isEqualTo(11);
        assertThat(table.get(textView.getViewId()))
                .isEqualTo(new ResourceEntry("id", "pck", "textView1"));
        assertThat(table.get(textView.getLayout()))
                .isEqualTo(new ResourceEntry("layout", "pck", "main_activity"));
        assertThat(textView.getX()).isEqualTo(100);
        assertThat(textView.getY()).isEqualTo(200);
        assertThat(textView.getWidth()).isEqualTo(400);
        assertThat(textView.getHeight()).isEqualTo(30);
        assertThat(table.get(textView.getClassName())).isEqualTo("TextView");
        assertThat(table.get(textView.getPackageName())).isEqualTo("android.widget");
        assertThat(table.get(textView.getTextValue())).isEqualTo("Hello World!");
        assertThat(textView.getLayoutFlags()).isEqualTo(0);
        assertThat(textView.getSubViewCount()).isEqualTo(0);
    }

    /** Allocates a ComponentTreeEvent protobuf */
    static native long allocateEvent();

    /** Convert a ComponentTreeEvent protobuf into an array of bytes */
    static native byte[] toByteArray(long event);
}
