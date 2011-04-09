LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        gl2_basic.cpp

LOCAL_SHARED_LIBRARIES := \
        libcutils \
    libEGL \
    libGLESv2 \
    libui

LOCAL_MODULE:= test-opengl-gl2_basic

LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS := -DGL_GLEXT_PROTOTYPES

include $(BUILD_EXECUTABLE)
