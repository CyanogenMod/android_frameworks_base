LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := signed_media

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_CERTIFICATE := media

include $(FrameworkCoreTests_BUILD_PACKAGE)
