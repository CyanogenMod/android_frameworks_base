LOCAL_PATH:= $(call my-dir)

#
# libcamera
#

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES:=               \
    CameraHardware.cpp      \
    V4L2Camera.cpp  \
    converter.cpp

LOCAL_MODULE:= libcamera

LOCAL_SHARED_LIBRARIES:= \
    libui \
    libutils \
    libbinder \
    libcutils \
    libjpeg \
    libcamera_client \
    libsurfaceflinger_client

ifeq ($(TARGET_SIMULATOR),true)
LOCAL_CFLAGS += -DSINGLE_PROCESS
endif

LOCAL_C_INCLUDES += external/jpeg

#LOCAL_STATIC_LIBRARIES:= \
        libjpeg

ifeq ($(BOARD_CAMERA_USE_GETBUFFERINFO),true)
LOCAL_CFLAGS += -DUSE_GETBUFFERINFO
endif

include $(BUILD_SHARED_LIBRARY)
#include $(BUILD_STATIC_LIBRARY)

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
    libsurfaceflinger_client

LOCAL_MODULE:= libcameraservice

ifeq ($(TARGET_SIMULATOR),true)
LOCAL_CFLAGS += -DSINGLE_PROCESS
endif

#ifeq ($(USE_CAMERA_STUB), true)
#LOCAL_STATIC_LIBRARIES += libcamerastub
#else
LOCAL_SHARED_LIBRARIES += libcamera 
#endif

ifeq ($(BOARD_USE_FROYO_LIBCAMERA), true)
LOCAL_CFLAGS += -DBOARD_USE_FROYO_LIBCAMERA
    ifeq ($(BOARD_USE_REVERSE_FFC), true)
    LOCAL_CFLAGS += -DBOARD_USE_REVERSE_FFC
    endif
endif

ifeq ($(BOARD_CAMERA_USE_GETBUFFERINFO),true)
LOCAL_CFLAGS += -DUSE_GETBUFFERINFO
endif

ifeq ($(BOARD_OVERLAY_FORMAT_YCbCr_420_SP),true)
LOCAL_CFLAGS += -DUSE_OVERLAY_FORMAT_YCbCr_420_SP
LOCAL_C_INCLUDES += hardware/msm7k/libgralloc-qsd8k
endif

ifeq ($(BOARD_USE_CAF_LIBCAMERA), true)
    LOCAL_CFLAGS += -DBOARD_USE_CAF_LIBCAMERA
endif

ifeq ($(BOARD_FIRST_CAMERA_FRONT_FACING),true)
    LOCAL_CFLAGS += -DFIRST_CAMERA_FACING=CAMERA_FACING_FRONT -DFIRST_CAMERA_ORIENTATION=0
endif

include $(BUILD_SHARED_LIBRARY)
