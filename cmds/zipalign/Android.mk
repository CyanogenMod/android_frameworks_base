# 
# Copyright 2008 The Android Open Source Project
#
# Zip alignment tool
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	../../../../build/tools/zipalign/ZipFile.cpp \
	../../../../build/tools/zipalign/ZipEntry.cpp \
	../../../../build/tools/zipalign/ZipAlign.cpp

LOCAL_C_INCLUDES := external/zlib build/tools/zipalign

LOCAL_SHARED_LIBRARIES := \
	libz \
	libutils \
	libcutils 

LOCAL_MODULE := zipalign

include $(BUILD_EXECUTABLE)

