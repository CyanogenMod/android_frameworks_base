LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        screencap.cpp

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libutils \
        libbinder \
    libui \
    libsurfaceflinger_client

LOCAL_MODULE:= screencap

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
