LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        fillrate.cpp

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libutils \
    libEGL \
    libGLESv1_CM \
    libui

LOCAL_MODULE:= test-opengl-fillrate

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
