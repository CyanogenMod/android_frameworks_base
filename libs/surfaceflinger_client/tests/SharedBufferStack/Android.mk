LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        SharedBufferStackTest.cpp

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libutils \
    libui \
    libsurfaceflinger_client

LOCAL_MODULE:= test-sharedbufferstack

LOCAL_MODULE_TAGS := tests

include $(BUILD_EXECUTABLE)
