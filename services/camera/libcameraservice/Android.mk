LOCAL_PATH:= $(call my-dir)

#
# libcameraservice
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    CameraService.cpp

LOCAL_SHARED_LIBRARIES:= \
    libui \
    libutils \
    libbinder \
    libcutils \
    libmedia \
    libcamera_client \
    libgui \
    libhardware

ifeq ($(BOARD_USE_SEC_CAMERA_CORE), true)
    LOCAL_SHARED_LIBRARIES += libseccameracore
    LOCAL_CFLAGS += -DUSE_SEC_CAMERA_CORE
endif

LOCAL_MODULE:= libcameraservice

include $(BUILD_SHARED_LIBRARY)
