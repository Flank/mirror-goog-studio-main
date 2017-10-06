/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.vectordrawable;

/** Class to represent SVG gradient stops or Android's GradientColorItem. */
public class GradientStop {

    private String color;
    private String offset;
    private String opacity = "";

    GradientStop(String color, String offset) {
        this.color = color;
        this.offset = offset;
    }

    void formatStopAttributes() {
        if (color.startsWith("rgb")) {
            color = SvgLeafNode.convertRGBToHex(color.substring(3));
        }
    }

    String getColor() {
        return color;
    }

    String getOffset() {
        return offset;
    }

    String getOpacity() {
        return opacity;
    }

    protected void setOpacity(String opacity) {
        this.opacity = opacity;
    }
}
