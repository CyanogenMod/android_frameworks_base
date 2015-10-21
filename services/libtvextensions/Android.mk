LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                          \
        jni/TvInputHalFactory.cpp          \

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/base/services/libtvextensions \

LOCAL_CFLAGS += -Wno-multichar

ifeq ($(TARGET_ENABLE_QC_TVINPUT_HAL_EXTENSIONS),true)
       LOCAL_CFLAGS += -DENABLE_TVINPUT_HAL_EXTENSIONS
endif

LOCAL_MODULE:= libTvInputHalExtensions

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_LIBRARY)

