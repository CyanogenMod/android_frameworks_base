LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4

LOCAL_PACKAGE_NAME := DocumentsUI
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
