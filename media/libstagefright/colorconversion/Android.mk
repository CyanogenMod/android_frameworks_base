LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                     \
        ColorConverter.cpp            \
        SoftwareRenderer.cpp

LOCAL_C_INCLUDES := \
        $(TOP)/frameworks/base/include/media/stagefright/openmax

ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_C_INCLUDES += hardware/ti/omx/ducati/domx/system/omx_core/inc
endif

LOCAL_SHARED_LIBRARIES :=       \
        libbinder               \
        libmedia                \
        libutils                \
        libui                   \
        libcutils				\
        libsurfaceflinger_client\
        libcamera_client

ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_CFLAGS += -DTARGET_OMAP4 -DARM_4K_PAGE_SIZE=4096
endif

LOCAL_MODULE:= libstagefright_color_conversion

include $(BUILD_SHARED_LIBRARY)
