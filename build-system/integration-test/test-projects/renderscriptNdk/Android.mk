# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This is the shared library included by the JNI test app.
#
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

RENDERSCRIPT_OUTPUT_FOLDER := \
    build/generated/renderscript_source_output_dir/debug/out

RENDERSCRIPT_TOOLCHAIN_PREBUILT_ROOT := \
    $(NDK_ROOT)/toolchains/renderscript/prebuilt/$(HOST_TAG64)

LOCAL_C_INCLUDES := \
    $(RENDERSCRIPT_OUTPUT_FOLDER) \
    $(RENDERSCRIPT_TOOLCHAIN_PREBUILT_ROOT)/platform/rs \
    $(RENDERSCRIPT_TOOLCHAIN_PREBUILT_ROOT)/platform/rs/cpp

LOCAL_CFLAGS += -std=c++14 -fno-rtti -DANDROID

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog

LOCAL_SRC_FILES := \
    src/main/jni/addint.cc \
    $(RENDERSCRIPT_OUTPUT_FOLDER)/ScriptC_addint.cpp

LOCAL_MODULE    := addint

LOCAL_STATIC_LIBRARIES := RScpp_static

include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/renderscript)