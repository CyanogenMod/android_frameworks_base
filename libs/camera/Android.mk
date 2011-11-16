LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	Camera.cpp \
	CameraParameters.cpp \
	ICamera.cpp \
	ICameraClient.cpp \
	ICameraService.cpp \
	ICameraRecordingProxy.cpp \
	ICameraRecordingProxyListener.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libhardware \
	libui \
	libgui

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
	LOCAL_CFLAGS += -DQCOM_HARDWARE
endif


LOCAL_MODULE:= libcamera_client

include $(BUILD_SHARED_LIBRARY)
