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
#include <jni.h>
#include "agent/agent.h"

using layoutinspector::ComponentTreeEvent;
using layoutinspector::Configuration;
using layoutinspector::Locale;
using layoutinspector::Resource;
using layoutinspector::ResourceConfiguration;

/**
 * Native calls for loading the configuration into an ResourceConfiguration
 * protobuf.
 */
extern "C" {

extern void saveResource(Resource *resource, jint jnamespace, jint jtype,
                         jint jname);

/*
 * Adds a theme resource value to a ResourceConfiguration protobuf.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ResourceConfiguration_addAppData(
    JNIEnv *env, jclass clazz, jlong jevent, jint sdkVersion, jint sdkCodename,
    jint packageName) {
  ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
  ResourceConfiguration *resources = event->mutable_resources();
  resources->set_api_level(sdkVersion);
  resources->set_api_code_name(sdkCodename);
  resources->set_app_package_name(packageName);
}

/*
 * Add a theme resource value to a ResourceConfiguration protobuf.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ResourceConfiguration_addTheme(
    JNIEnv *env, jclass clazz, jlong jevent, jint jnamespace, jint jtype,
    jint jname) {
  ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
  ResourceConfiguration *resources = event->mutable_resources();
  saveResource(resources->mutable_theme(), jnamespace, jtype, jname);
}

/*
 * Add the configuration values to a ResourceConfiguration protobuf.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ResourceConfiguration_addConfiguration(
    JNIEnv *env, jclass clazz, jlong jevent, jfloat fontScale, jint mcc,
    jint mnc, jint screenLayout, jint colorMode, jint touchScreen,
    jint keyboard, jint keyboardHidden, jint hardKeyboardHidden,
    jint navigation, jint navigationHidden, jint uiMode,
    jint smallestScreenWidth, jint density, jint orientation, jint screenWidth,
    jint screenHeight) {
  ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
  ResourceConfiguration *resources = event->mutable_resources();
  Configuration *configuration = resources->mutable_configuration();
  configuration->set_font_scale(fontScale);
  configuration->set_country_code(mcc);
  configuration->set_network_code(mnc);
  configuration->set_screen_layout(screenLayout);
  configuration->set_color_mode(colorMode);
  configuration->set_touch_screen(touchScreen);
  configuration->set_keyboard(keyboard);
  configuration->set_keyboard_hidden(keyboardHidden);
  configuration->set_hard_keyboard_hidden(hardKeyboardHidden);
  configuration->set_navigation(navigation);
  configuration->set_navigation_hidden(navigationHidden);
  configuration->set_ui_mode(uiMode);
  configuration->set_smallest_screen_width(smallestScreenWidth);
  configuration->set_density(density);
  configuration->set_orientation(orientation);
  configuration->set_screen_width(screenWidth);
  configuration->set_screen_height(screenHeight);
}

/*
 * Add the default locale values to a ResourceConfiguration protobuf.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ResourceConfiguration_addLocale(
    JNIEnv *env, jclass clazz, jlong jevent, jint language, jint country,
    jint variant, jint script) {
  ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
  ResourceConfiguration *resources = event->mutable_resources();
  Configuration *configuration = resources->mutable_configuration();
  Locale *locale = configuration->mutable_locale();
  locale->set_language(language);
  locale->set_country(country);
  locale->set_variant(variant);
  locale->set_script(script);
}
}
