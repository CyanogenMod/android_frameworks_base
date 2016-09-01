LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_MODULE:= libregionalization
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
        Environment.cpp
LOCAL_C_INCLUDES := \
    frameworks/base/include
LOCAL_SHARED_LIBRARIES := \
    liblog \
    libcutils \
    libutils

LOCAL_CFLAGS += -Wall -Werror -Wunused -Wunreachable-code

include $(BUILD_SHARED_LIBRARY)
