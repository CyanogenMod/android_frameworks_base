# 
# Copyright 2006 The Android Open Source Project
#
# Android Asset Packaging Tool
#

LOCAL_PATH:= $(call my-dir)

commonSources:= \
	AaptAssets.cpp \
	Command.cpp \
	Main.cpp \
	Package.cpp \
	StringPool.cpp \
	XMLNode.cpp \
	ResourceTable.cpp \
	Images.cpp \
	Resource.cpp \
  	SourcePos.cpp


# For the host
# =====================================================

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(commonSources)

LOCAL_CFLAGS += -DHOST_LIB=1
LOCAL_CFLAGS += -DLIBUTILS_NATIVE=1 $(TOOL_CFLAGS)
LOCAL_CFLAGS += -Wno-format-y2k

LOCAL_C_INCLUDES += external/expat/lib
LOCAL_C_INCLUDES += external/libpng
LOCAL_C_INCLUDES += external/zlib
LOCAL_C_INCLUDES += build/libs/host/include

#LOCAL_WHOLE_STATIC_LIBRARIES := 
LOCAL_STATIC_LIBRARIES := \
	libhost \
	libutils \
	libcutils \
	libexpat \
	libpng

LOCAL_LDLIBS := -lz

ifeq ($(HOST_OS),linux)
LOCAL_LDLIBS += -lrt
endif

ifeq ($(HOST_OS),windows)
ifeq ($(strip $(USE_CYGWIN),),)
LOCAL_LDLIBS += -lws2_32
endif
endif

LOCAL_MODULE := aapt

include $(BUILD_HOST_EXECUTABLE)


# For the device
# =====================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= $(commonSources)

ifeq ($(TARGET_OS),linux)
# Use the futex based mutex and condition variable
# implementation from android-arm because it's shared mem safe
LOCAL_LDLIBS += -lrt -ldl
endif

LOCAL_C_INCLUDES += external/expat/lib
LOCAL_C_INCLUDES += external/libpng
LOCAL_C_INCLUDES += external/zlib
LOCAL_C_INCLUDES += external/icu4c/common

LOCAL_SHARED_LIBRARIES := \
        libz \
        libutils \
        libcutils \
        libexpat \
        libsgl

ifneq ($(TARGET_SIMULATOR),true)
ifeq ($(TARGET_OS)-$(TARGET_ARCH),linux-x86)
# This is needed on x86 to bring in dl_iterate_phdr for CallStack.cpp
LOCAL_SHARED_LIBRARIES += \
        libdl
endif # linux-x86
endif # sim

LOCAL_MODULE := aapt

include $(BUILD_EXECUTABLE)
