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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.agent.layoutinspector.testing.CompanionSupplierRule;
import com.android.tools.agent.layoutinspector.testing.ResourceEntry;
import com.android.tools.agent.layoutinspector.testing.StandardView;
import com.android.tools.agent.layoutinspector.testing.StringTable;
import com.android.tools.idea.protobuf.InvalidProtocolBufferException;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type;
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.PropertyEvent;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class PropertiesTest {

    @Rule public CompanionSupplierRule supplier = new CompanionSupplierRule();

    @Test
    public void testProtoBuilder() throws InvalidProtocolBufferException {
        System.loadLibrary("jni-test");
        long event = allocateEvent();

        Properties properties = new Properties();
        properties.writeProperties(StandardView.createTextView(), event);
        PropertyChecker checker = new PropertyChecker(PropertyEvent.parseFrom(toByteArray(event)));
        checker.check();
    }

    private static class PropertyChecker {
        private final StringTable myTable;
        private final List<LayoutInspectorProto.Property> myProperties;

        private PropertyChecker(@NonNull PropertyEvent proto) {
            myTable = new StringTable(proto.getStringList());
            myProperties = proto.getPropertyList();
        }

        private void check() {
            int index = 0;
            checkProperty(myProperties.get(index++), "focused", Type.BOOLEAN, 1);
            checkProperty(myProperties.get(index++), "byte", Type.BYTE, 7);
            checkProperty(myProperties.get(index++), "char", Type.CHAR, (int) 'g');
            checkProperty(myProperties.get(index++), "double", Type.DOUBLE, 3.75);
            checkProperty(myProperties.get(index++), "scaleX", Type.FLOAT, 1.75f);
            checkProperty(myProperties.get(index++), "scrollX", Type.INT32, 10);
            checkProperty(myProperties.get(index++), "long", Type.INT64, 7000L);
            checkProperty(myProperties.get(index++), "short", Type.INT16, 70);
            checkProperty(
                    myProperties.get(index++), "transitionName", Type.STRING, "MyTransitionName");
            checkProperty(myProperties.get(index++), "backgroundTint", Type.COLOR, Color.BLUE);
            checkProperty(myProperties.get(index++), "background", Type.COLOR, Color.YELLOW);
            checkProperty(
                    myProperties.get(index++), "outlineSpotShadowColor", Type.COLOR, Color.RED);
            checkProperty(
                    myProperties.get(index++),
                    "foregroundGravity",
                    Type.GRAVITY,
                    ImmutableSet.of("top", "fill_horizontal"));
            checkProperty(myProperties.get(index++), "visibility", Type.INT_ENUM, "invisible");
            checkProperty(
                    myProperties.get(index++),
                    "labelFor",
                    Type.RESOURCE,
                    new ResourceEntry("id", "pck", "other"));
            checkProperty(
                    myProperties.get(index++),
                    "scrollIndicators",
                    Type.INT_FLAG,
                    ImmutableSet.of("left", "bottom"));
            checkProperty(myProperties.get(index++), "text", Type.STRING, "Hello World!");
            checkProperty(myProperties.get(index++), "layout_width", Type.INT_ENUM, "match_parent");
            checkProperty(myProperties.get(index++), "layout_height", Type.INT32, 400);
            checkProperty(myProperties.get(index++), "layout_marginBottom", Type.INT32, 10);
            checkProperty(
                    myProperties.get(index++),
                    "layout_gravity",
                    Type.GRAVITY,
                    ImmutableSet.of("end"));
            assertThat(myProperties.size()).isEqualTo(index);
        }

        private void checkProperty(
                @NonNull Property property,
                @NonNull String name,
                @NonNull Type type,
                @Nullable Object value) {
            assertThat(myTable.get(property.getName())).isEqualTo(name);
            assertThat(property.getType()).isEqualTo(type);
            switch (type) {
                case BOOLEAN:
                case BYTE:
                case CHAR:
                case COLOR:
                case INT16:
                case INT32:
                    assertThat(property.getInt32Value()).isEqualTo(value);
                    break;
                case INT64:
                    assertThat(property.getInt64Value()).isEqualTo(value);
                    break;
                case DOUBLE:
                    assertThat(property.getDoubleValue()).isEqualTo(value);
                    break;
                case FLOAT:
                    assertThat(property.getFloatValue()).isEqualTo(value);
                    break;
                case STRING:
                case INT_ENUM:
                    assertThat(myTable.get(property.getInt32Value())).isEqualTo(value);
                    break;
                case GRAVITY:
                case INT_FLAG:
                    assertThat(
                                    property.getFlagValue()
                                            .getFlagList()
                                            .stream()
                                            .map(myTable::get)
                                            .collect(Collectors.toSet()))
                            .isEqualTo(value);
                    break;
                case RESOURCE:
                    assertThat(myTable.get(property.getResourceValue())).isEqualTo(value);
                    break;
                default:
                    fail("Unmapped value");
            }
        }
    }

    /** Allocates a PropertyEvent protobuf */
    private native long allocateEvent();

    /** Convert a PropertyEvent protobuf into an array of bytes */
    private native byte[] toByteArray(long event);
}
