LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	ISensorEventConnection.cpp \
	ISensorServer.cpp \
	ISurfaceTexture.cpp \
	Sensor.cpp \
	SensorChannel.cpp \
	SensorEventQueue.cpp \
	SensorManager.cpp \
	SurfaceTexture.cpp \
	SurfaceTextureClient.cpp \
	ISurfaceComposer.cpp \
	ISurface.cpp \
	ISurfaceComposerClient.cpp \
	IGraphicBufferAlloc.cpp \
	LayerState.cpp \
	Surface.cpp \
	SurfaceComposerClient.cpp \

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libhardware \
	libhardware_legacy \
	libui \
	libEGL \
	libGLESv2

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
LOCAL_SHARED_LIBRARIES += \
        libQcomUI
LOCAL_C_INCLUDES := hardware/qcom/display/libqcomui
LOCAL_CFLAGS += -DQCOM_HARDWARE
ifeq ($(TARGET_QCOM_HDMI_OUT),true)
LOCAL_CFLAGS += -DQCOM_HDMI_OUT
endif
endif

LOCAL_MODULE:= libgui

ifeq ($(TARGET_BOARD_PLATFORM), tegra)
ifneq ($(BOARD_NO_ALLOW_DEQUEUE_CURRENT_BUFFER), true)
	LOCAL_CFLAGS += -DALLOW_DEQUEUE_CURRENT_BUFFER
endif
endif

include $(BUILD_SHARED_LIBRARY)

ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
