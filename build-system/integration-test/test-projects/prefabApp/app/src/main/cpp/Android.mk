LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libapp
LOCAL_SRC_FILES := app.cpp
LOCAL_SHARED_LIBRARIES := jsoncpp curl
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

$(call import-module,prefab/curl)
$(call import-module,prefab/jsoncpp)