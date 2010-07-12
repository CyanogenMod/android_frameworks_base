LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= CameraServiceTest.cpp

LOCAL_MODULE:= CameraServiceTest

LOCAL_MODULE_TAGS := eng tests

LOCAL_C_INCLUDES += \
                frameworks/base/libs

LOCAL_CFLAGS :=

LOCAL_SHARED_LIBRARIES += \
		libbinder \
                libcutils \
                libutils \
                libui

ifneq ($(BOARD_USES_ECLAIR_LIBCAMERA),true)
    LOCAL_SHARED_LIBRARIES += \
        libcamera_client \
        libsurfaceflinger_client
endif

include $(BUILD_EXECUTABLE)
