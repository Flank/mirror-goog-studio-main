/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.ABS_LIST_VIEW;
import static com.android.SdkConstants.ABS_SEEK_BAR;
import static com.android.SdkConstants.ABS_SPINNER;
import static com.android.SdkConstants.ADAPTER_VIEW;
import static com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CHECKABLE;
import static com.android.SdkConstants.CHECKED_TEXT_VIEW;
import static com.android.SdkConstants.CHECK_BOX;
import static com.android.SdkConstants.COMPOUND_BUTTON;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.EXPANDABLE_LIST_VIEW;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.GALLERY;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.LIST_VIEW;
import static com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW;
import static com.android.SdkConstants.PROGRESS_BAR;
import static com.android.SdkConstants.RADIO_BUTTON;
import static com.android.SdkConstants.RADIO_GROUP;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.SCROLL_VIEW;
import static com.android.SdkConstants.SEEK_BAR;
import static com.android.SdkConstants.SPINNER;
import static com.android.SdkConstants.SURFACE_VIEW;
import static com.android.SdkConstants.SWITCH;
import static com.android.SdkConstants.TABLE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;
import static com.android.SdkConstants.TAB_HOST;
import static com.android.SdkConstants.TAB_WIDGET;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.TOGGLE_BUTTON;
import static com.android.SdkConstants.VIEW;
import static com.android.SdkConstants.VIEW_ANIMATOR;
import static com.android.SdkConstants.VIEW_GROUP;
import static com.android.SdkConstants.VIEW_PKG_PREFIX;
import static com.android.SdkConstants.VIEW_STUB;
import static com.android.SdkConstants.VIEW_SWITCHER;
import static com.android.SdkConstants.WEB_VIEW;
import static com.android.SdkConstants.WIDGET_PKG_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;

/**
 * Default simple implementation of an {@link SdkInfo}
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
class DefaultSdkInfo extends SdkInfo {
    @Override
    @Nullable
    public String getParentViewName(@NonNull String name) {
        name = getRawType(name);
        return getParent(name);
    }

    @Override
    @Nullable
    public String getParentViewClass(@NonNull String fqcn) {
        int index = fqcn.lastIndexOf('.');
        if (index != -1) {
            fqcn = fqcn.substring(index + 1);
        }

        String parent = getParent(fqcn);
        if (parent == null) {
            return null;
        }
        // The map only stores class names internally; correct for full package paths
        if (parent.equals(VIEW) || parent.equals(VIEW_GROUP) || parent.equals(SURFACE_VIEW)) {
            return VIEW_PKG_PREFIX + parent;
        } else {
            return WIDGET_PKG_PREFIX + parent;
        }
    }

    @Override
    public boolean isSubViewOf(@NonNull String parentType, @NonNull String childType) {
        String parent = getRawType(parentType);
        String child = getRawType(childType);

        // Do analysis just on non-fqcn paths
        if (parent.indexOf('.') != -1) {
            parent = parent.substring(parent.lastIndexOf('.') + 1);
        }
        if (child.indexOf('.') != -1) {
            child = child.substring(child.lastIndexOf('.') + 1);
        }

        if (parent.equals(VIEW)) {
            return true;
        }

        while (!child.equals(VIEW)) {
            if (parent.equals(child)) {
                return true;
            }
            if (implementsInterface(child, parent)) {
                return true;
            }
            child = getParent(child);
            if (child == null) {
                // Unknown view - err on the side of caution
                return true;
            }
        }

        return false;
    }

    private static boolean implementsInterface(String className, String interfaceName) {
        return interfaceName.equals(getInterface(className));
    }

    // Strip off type parameters, e.g. AdapterView<?> ⇒ AdapterView
    private static String getRawType(String type) {
        if (type != null) {
            int index = type.indexOf('<');
            if (index != -1) {
                type = type.substring(0, index);
            }
        }

        return type;
    }

    @Override
    public boolean isLayout(@NonNull String tag) {
        // TODO: Read in widgets.txt from the platform install area to look up this information
        // dynamically instead!

        if (super.isLayout(tag)) {
            return true;
        }

        switch (tag) {
            case TAB_HOST:
            case HORIZONTAL_SCROLL_VIEW:
            case VIEW_SWITCHER:
            case TAB_WIDGET:
            case VIEW_ANIMATOR:
            case SCROLL_VIEW:
            case GRID_VIEW:
            case TABLE_ROW:
            case RADIO_GROUP:
            case LIST_VIEW:
            case EXPANDABLE_LIST_VIEW:
            case "MediaController":
            case "DialerFilter":
            case "ViewFlipper":
            case "SlidingDrawer":
            case "StackView":
            case "SearchView":
            case "TextSwitcher":
            case "AdapterViewFlipper":
            case "ImageSwitcher":
                return true;
        }

        return false;
    }

    @Nullable
    private static String getParent(@NonNull String layout) {
        switch (layout) {
            case COMPOUND_BUTTON:
                return BUTTON;
            case ABS_SPINNER:
                return ADAPTER_VIEW;
            case ABS_LIST_VIEW:
                return ADAPTER_VIEW;
            case ABS_SEEK_BAR:
                return ADAPTER_VIEW;
            case ADAPTER_VIEW:
                return VIEW_GROUP;
            case VIEW_GROUP:
                return VIEW;

            case TEXT_VIEW:
                return VIEW;
            case CHECKED_TEXT_VIEW:
                return TEXT_VIEW;
            case RADIO_BUTTON:
                return COMPOUND_BUTTON;
            case SPINNER:
                return ABS_SPINNER;
            case IMAGE_BUTTON:
                return IMAGE_VIEW;
            case IMAGE_VIEW:
                return VIEW;
            case EDIT_TEXT:
                return TEXT_VIEW;
            case PROGRESS_BAR:
                return VIEW;
            case TOGGLE_BUTTON:
                return COMPOUND_BUTTON;
            case VIEW_STUB:
                return VIEW;
            case BUTTON:
                return TEXT_VIEW;
            case SEEK_BAR:
                return ABS_SEEK_BAR;
            case CHECK_BOX:
                return COMPOUND_BUTTON;
            case SWITCH:
                return COMPOUND_BUTTON;
            case GALLERY:
                return ABS_SPINNER;
            case SURFACE_VIEW:
                return VIEW;
            case ABSOLUTE_LAYOUT:
                return VIEW_GROUP;
            case LINEAR_LAYOUT:
                return VIEW_GROUP;
            case RELATIVE_LAYOUT:
                return VIEW_GROUP;
            case LIST_VIEW:
                return ABS_LIST_VIEW;
            case VIEW_SWITCHER:
                return VIEW_ANIMATOR;
            case FRAME_LAYOUT:
                return VIEW_GROUP;
            case HORIZONTAL_SCROLL_VIEW:
                return FRAME_LAYOUT;
            case VIEW_ANIMATOR:
                return FRAME_LAYOUT;
            case TAB_HOST:
                return FRAME_LAYOUT;
            case TABLE_ROW:
                return LINEAR_LAYOUT;
            case RADIO_GROUP:
                return LINEAR_LAYOUT;
            case TAB_WIDGET:
                return LINEAR_LAYOUT;
            case EXPANDABLE_LIST_VIEW:
                return LIST_VIEW;
            case TABLE_LAYOUT:
                return LINEAR_LAYOUT;
            case SCROLL_VIEW:
                return FRAME_LAYOUT;
            case GRID_VIEW:
                return ABS_LIST_VIEW;
            case WEB_VIEW:
                return ABSOLUTE_LAYOUT;
            case AUTO_COMPLETE_TEXT_VIEW:
                return EDIT_TEXT;
            case MULTI_AUTO_COMPLETE_TEXT_VIEW:
                return AUTO_COMPLETE_TEXT_VIEW;

            case "MediaController":
                return FRAME_LAYOUT;
            case "SlidingDrawer":
                return VIEW_GROUP;
            case "DialerFilter":
                return RELATIVE_LAYOUT;
            case "DigitalClock":
                return TEXT_VIEW;
            case "Chronometer":
                return TEXT_VIEW;
            case "ImageSwitcher":
                return VIEW_SWITCHER;
            case "TextSwitcher":
                return VIEW_SWITCHER;
            case "AnalogClock":
                return VIEW;
            case "TwoLineListItem":
                return RELATIVE_LAYOUT;
            case "ZoomControls":
                return LINEAR_LAYOUT;
            case "DatePicker":
                return FRAME_LAYOUT;
            case "TimePicker":
                return FRAME_LAYOUT;
            case "VideoView":
                return SURFACE_VIEW;
            case "ZoomButton":
                return IMAGE_BUTTON;
            case "RatingBar":
                return ABS_SEEK_BAR;
            case "ViewFlipper":
                return VIEW_ANIMATOR;
            case "NumberPicker":
                return LINEAR_LAYOUT;
        }

        return null;
    }

    @Nullable
    private static String getInterface(@NonNull String cls) {
        switch (cls) {
            case CHECKED_TEXT_VIEW:
            case COMPOUND_BUTTON:
                return CHECKABLE;
            default:
                return null;
        }
    }
}
