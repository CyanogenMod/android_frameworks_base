# 
# Copyright 2008 The Android Open Source Project
#
# Zip alignment tool
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	ZipFile.cpp \
	ZipEntry.cpp \
	ZipAlign.cpp

LOCAL_C_INCLUDES := external/zlib frameworks/base/include/utils

LOCAL_SHARED_LIBRARIES := \
	libz \
	libutils \
	libcutils 

LOCAL_MODULE := zipalign

include $(BUILD_EXECUTABLE)
