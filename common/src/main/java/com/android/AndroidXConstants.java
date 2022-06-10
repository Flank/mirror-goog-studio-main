/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android;

import com.android.support.AndroidxName;

/**
 * Class containing all the {@link AndroidxName} constants. This is separate from {@link
 * SdkConstants} to avoid users of {@link SdkConstants} from preloading the migration map when not
 * used at all.
 */
public class AndroidXConstants {
    public static final AndroidxName CLASS_DATA_BINDING_COMPONENT =
            AndroidxName.of("android.databinding.", SdkConstants.CLASS_NAME_DATA_BINDING_COMPONENT);
    public static final AndroidxName MULTI_DEX_APPLICATION =
            AndroidxName.of("android.support.multidex.", "MultiDexApplication");

    /* Material Components */
    public static final AndroidxName CLASS_APP_BAR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "AppBarLayout");
    public static final AndroidxName APP_BAR_LAYOUT = CLASS_APP_BAR_LAYOUT;
    public static final AndroidxName CLASS_BOTTOM_NAVIGATION_VIEW =
            AndroidxName.of("android.support.design.widget.", "BottomNavigationView");
    public static final AndroidxName BOTTOM_NAVIGATION_VIEW = CLASS_BOTTOM_NAVIGATION_VIEW;
    public static final AndroidxName CLASS_COORDINATOR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "CoordinatorLayout");

    /* Android Support Tag Constants */
    public static final AndroidxName COORDINATOR_LAYOUT = CLASS_COORDINATOR_LAYOUT;
    public static final AndroidxName CLASS_COLLAPSING_TOOLBAR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "CollapsingToolbarLayout");
    public static final AndroidxName COLLAPSING_TOOLBAR_LAYOUT = CLASS_COLLAPSING_TOOLBAR_LAYOUT;
    public static final AndroidxName CLASS_FLOATING_ACTION_BUTTON =
            AndroidxName.of("android.support.design.widget.", "FloatingActionButton");

    public static final AndroidxName FLOATING_ACTION_BUTTON = CLASS_FLOATING_ACTION_BUTTON;
    public static final AndroidxName CLASS_NAVIGATION_VIEW =
            AndroidxName.of("android.support.design.widget.", "NavigationView");
    public static final AndroidxName NAVIGATION_VIEW = CLASS_NAVIGATION_VIEW;
    public static final AndroidxName CLASS_SNACKBAR =
            AndroidxName.of("android.support.design.widget.", "Snackbar");
    public static final AndroidxName SNACKBAR = CLASS_SNACKBAR;
    public static final AndroidxName CLASS_TAB_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "TabLayout");
    public static final AndroidxName TAB_LAYOUT = CLASS_TAB_LAYOUT;
    public static final AndroidxName CLASS_TAB_ITEM =
            AndroidxName.of("android.support.design.widget.", "TabItem");
    public static final AndroidxName TAB_ITEM = CLASS_TAB_ITEM;
    public static final AndroidxName CLASS_TEXT_INPUT_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "TextInputLayout");
    public static final AndroidxName TEXT_INPUT_LAYOUT = CLASS_TEXT_INPUT_LAYOUT;
    public static final AndroidxName CLASS_TEXT_INPUT_EDIT_TEXT =
            AndroidxName.of("android.support.design.widget.", "TextInputEditText");
    public static final AndroidxName TEXT_INPUT_EDIT_TEXT = CLASS_TEXT_INPUT_EDIT_TEXT;

    /* Android ConstraintLayout Constants */
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT =
            AndroidxName.of("android.support.constraint.", "ConstraintLayout");

    public static final AndroidxName CONSTRAINT_LAYOUT = CLASS_CONSTRAINT_LAYOUT;
    public static final AndroidxName CLASS_MOTION_LAYOUT =
            AndroidxName.of("android.support.constraint.motion.", "MotionLayout");
    public static final AndroidxName MOTION_LAYOUT = CLASS_MOTION_LAYOUT;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_HELPER =
            AndroidxName.of("android.support.constraint.", "ConstraintHelper");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_BARRIER =
            AndroidxName.of("android.support.constraint.", "Barrier");
    public static final AndroidxName CONSTRAINT_LAYOUT_BARRIER = CLASS_CONSTRAINT_LAYOUT_BARRIER;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GROUP =
            AndroidxName.of("android.support.constraint.", "Group");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CHAIN =
            AndroidxName.of("android.support.constraint.", "Chain");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_LAYER =
            AndroidxName.of("android.support.constraint.helper.", "Layer");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_FLOW =
            AndroidxName.of("android.support.constraint.helper.", "Flow");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS =
            AndroidxName.of("android.support.constraint.", "Constraints");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_REFERENCE =
            AndroidxName.of("android.support.constraint.", "Reference");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_PARAMS =
            AndroidxName.of("android.support.constraint.", "ConstraintLayout$LayoutParams");
    public static final AndroidxName CLASS_TABLE_CONSTRAINT_LAYOUT =
            AndroidxName.of("android.support.constraint.", "TableConstraintLayout");
    public static final AndroidxName TABLE_CONSTRAINT_LAYOUT = CLASS_TABLE_CONSTRAINT_LAYOUT;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GUIDELINE =
            AndroidxName.of("android.support.constraint.", "Guideline");
    public static final AndroidxName CONSTRAINT_LAYOUT_GUIDELINE =
            CLASS_CONSTRAINT_LAYOUT_GUIDELINE;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_MOCK_VIEW =
            AndroidxName.of("android.support.constraint.utils.", "MockView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_VIEW =
            AndroidxName.of("android.support.constraint.utils.", "ImageFilterView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_BUTTON =
            AndroidxName.of("android.support.constraint.utils.", "ImageFilterButton");

    public static final AndroidxName CLASS_NESTED_SCROLL_VIEW =
            AndroidxName.of("android.support.v4.widget.", "NestedScrollView");
    public static final AndroidxName NESTED_SCROLL_VIEW = CLASS_NESTED_SCROLL_VIEW;
    public static final AndroidxName CLASS_DRAWER_LAYOUT =
            AndroidxName.of("android.support.v4.widget.", "DrawerLayout");
    public static final AndroidxName DRAWER_LAYOUT = CLASS_DRAWER_LAYOUT;
    public static final AndroidxName CLASS_GRID_LAYOUT_V7 =
            AndroidxName.of("android.support.v7.widget.", "GridLayout");
    public static final AndroidxName GRID_LAYOUT_V7 = CLASS_GRID_LAYOUT_V7;
    public static final AndroidxName CLASS_TOOLBAR_V7 =
            AndroidxName.of("android.support.v7.widget.", "Toolbar");
    public static final AndroidxName TOOLBAR_V7 = CLASS_TOOLBAR_V7;
    public static final AndroidxName CLASS_RECYCLER_VIEW_V7 =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView");
    public static final AndroidxName RECYCLER_VIEW = CLASS_RECYCLER_VIEW_V7;
    public static final AndroidxName CLASS_CARD_VIEW =
            AndroidxName.of("android.support.v7.widget.", "CardView");
    public static final AndroidxName CARD_VIEW = CLASS_CARD_VIEW;
    public static final AndroidxName CLASS_ACTION_MENU_VIEW =
            AndroidxName.of("android.support.v7.widget.", "ActionMenuView");
    public static final AndroidxName ACTION_MENU_VIEW = CLASS_ACTION_MENU_VIEW;
    public static final AndroidxName CLASS_BROWSE_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "BrowseFragment");
    public static final AndroidxName BROWSE_FRAGMENT = CLASS_BROWSE_FRAGMENT;
    public static final AndroidxName CLASS_DETAILS_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "DetailsFragment");
    public static final AndroidxName DETAILS_FRAGMENT = CLASS_DETAILS_FRAGMENT;
    public static final AndroidxName CLASS_PLAYBACK_OVERLAY_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "PlaybackOverlayFragment");
    public static final AndroidxName PLAYBACK_OVERLAY_FRAGMENT = CLASS_PLAYBACK_OVERLAY_FRAGMENT;
    public static final AndroidxName CLASS_SEARCH_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "SearchFragment");
    public static final AndroidxName SEARCH_FRAGMENT = CLASS_SEARCH_FRAGMENT;
    public static final AndroidxName FQCN_GRID_LAYOUT_V7 =
            AndroidxName.of("android.support.v7.widget.", "GridLayout");

    // Annotations
    public static final AndroidxName SUPPORT_ANNOTATIONS_PREFIX =
            AndroidxName.of("android.support.annotation.");
    public static final AndroidxName STRING_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "StringDef");
    public static final AndroidxName LONG_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "LongDef");
    public static final AndroidxName INT_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IntDef");
    public static final AndroidxName DATA_BINDING_PKG = AndroidxName.of("android.databinding.");
    public static final AndroidxName CLASS_DATA_BINDING_BASE_BINDING =
            AndroidxName.of("android.databinding.", "ViewDataBinding");
    public static final AndroidxName CLASS_DATA_BINDING_BINDABLE =
            AndroidxName.of("android.databinding.", "Bindable");
    public static final AndroidxName CLASS_DATA_BINDING_VIEW_STUB_PROXY =
            AndroidxName.of("android.databinding.", "ViewStubProxy");
    public static final AndroidxName BINDING_ADAPTER_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingAdapter");
    public static final AndroidxName BINDING_CONVERSION_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingConversion");
    public static final AndroidxName BINDING_METHODS_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingMethods");
    public static final AndroidxName INVERSE_BINDING_ADAPTER_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingAdapter");
    public static final AndroidxName INVERSE_BINDING_METHOD_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingMethod");
    public static final AndroidxName INVERSE_BINDING_METHODS_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingMethods");
    public static final AndroidxName INVERSE_METHOD_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseMethod");

    public static final AndroidxName CLASS_LIVE_DATA =
            AndroidxName.of("android.arch.lifecycle.", "LiveData");
    public static final AndroidxName CLASS_OBSERVABLE_BOOLEAN =
            AndroidxName.of("android.databinding.", "ObservableBoolean");
    public static final AndroidxName CLASS_OBSERVABLE_BYTE =
            AndroidxName.of("android.databinding.", "ObservableByte");
    public static final AndroidxName CLASS_OBSERVABLE_CHAR =
            AndroidxName.of("android.databinding.", "ObservableChar");
    public static final AndroidxName CLASS_OBSERVABLE_SHORT =
            AndroidxName.of("android.databinding.", "ObservableShort");
    public static final AndroidxName CLASS_OBSERVABLE_INT =
            AndroidxName.of("android.databinding.", "ObservableInt");
    public static final AndroidxName CLASS_OBSERVABLE_LONG =
            AndroidxName.of("android.databinding.", "ObservableLong");
    public static final AndroidxName CLASS_OBSERVABLE_FLOAT =
            AndroidxName.of("android.databinding.", "ObservableFloat");
    public static final AndroidxName CLASS_OBSERVABLE_DOUBLE =
            AndroidxName.of("android.databinding.", "ObservableDouble");
    public static final AndroidxName CLASS_OBSERVABLE_FIELD =
            AndroidxName.of("android.databinding.", "ObservableField");
    public static final AndroidxName CLASS_OBSERVABLE_PARCELABLE =
            AndroidxName.of("android.databinding.", "ObservableParcelable");
    public static final AndroidxName CLASS_RECYCLER_VIEW_LAYOUT_MANAGER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$LayoutManager");
    public static final AndroidxName CLASS_RECYCLER_VIEW_VIEW_HOLDER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$ViewHolder");
    public static final AndroidxName CLASS_VIEW_PAGER =
            AndroidxName.of("android.support.v4.view.", "ViewPager");
    public static final AndroidxName VIEW_PAGER = CLASS_VIEW_PAGER;
    public static final AndroidxName CLASS_V4_FRAGMENT =
            AndroidxName.of("android.support.v4.app.", "Fragment");

    /* Android Support Class Constants */
    public static final AndroidxName CLASS_APP_COMPAT_ACTIVITY =
            AndroidxName.of("android.support.v7.app.", "AppCompatActivity");
    public static final AndroidxName CLASS_MEDIA_ROUTE_ACTION_PROVIDER =
            AndroidxName.of("android.support.v7.app.", "MediaRouteActionProvider");
    public static final AndroidxName CLASS_RECYCLER_VIEW_ADAPTER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$Adapter");

    public static final class PreferenceAndroidX {
        public static final AndroidxName CLASS_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "Preference");
        public static final AndroidxName CLASS_PREFERENCE_GROUP_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "PreferenceGroup");
        public static final AndroidxName CLASS_EDIT_TEXT_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "EditTextPreference");
        public static final AndroidxName CLASS_LIST_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "ListPreference");
        public static final AndroidxName CLASS_MULTI_CHECK_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "MultiCheckPreference");
        public static final AndroidxName CLASS_MULTI_SELECT_LIST_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "MultiSelectListPreference");
        public static final AndroidxName CLASS_PREFERENCE_SCREEN_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "PreferenceScreen");
        public static final AndroidxName CLASS_RINGTONE_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "RingtonePreference");
        public static final AndroidxName CLASS_SEEK_BAR_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "SeekBarPreference");
        public static final AndroidxName CLASS_TWO_STATE_PREFERENCE_ANDROIDX =
                AndroidxName.of("android.support.v7.preference.", "TwoStatePreference");
    }
}
