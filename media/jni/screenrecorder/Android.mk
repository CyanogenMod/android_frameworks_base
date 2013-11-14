LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    android_media_ScreenRecorder_ScreenRecorderImpl.cpp

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libnativehelper \
    libstagefright \
    libmedia \
    libutils \
    libbinder \
    libstagefright_foundation \
    libjpeg \
    libgui \
    libcutils \
    liblog

LOCAL_C_INCLUDES := \
    frameworks/av/media/libstagefright \
    frameworks/av/media/libstagefright/include \
    $(TOP)/frameworks/native/include/media/openmax \
    external/jpeg

LOCAL_CFLAGS += -Wno-multichar -pthread

LOCAL_MODULE:= libscreenrecorder

include $(BUILD_SHARED_LIBRARY)
