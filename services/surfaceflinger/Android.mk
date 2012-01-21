LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    Layer.cpp 								\
    LayerBase.cpp 							\
    LayerDim.cpp 							\
    LayerScreenshot.cpp						\
    DdmConnection.cpp						\
    DisplayHardware/DisplayHardware.cpp 	\
    DisplayHardware/DisplayHardwareBase.cpp \
    DisplayHardware/HWComposer.cpp 			\
    GLExtensions.cpp 						\
    MessageQueue.cpp 						\
    SurfaceFlinger.cpp 						\
    SurfaceTextureLayer.cpp 				\
    Transform.cpp 							\
    

LOCAL_CFLAGS:= -DLOG_TAG=\"SurfaceFlinger\"
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

ifeq ($(TARGET_BOARD_PLATFORM), omap3)
	LOCAL_CFLAGS += -DNO_RGBX_8888
endif
ifeq ($(TARGET_BOARD_PLATFORM), omap4)
	LOCAL_CFLAGS += -DHAS_CONTEXT_PRIORITY
endif
ifeq ($(TARGET_BOARD_PLATFORM), s5pc110)
	LOCAL_CFLAGS += -DHAS_CONTEXT_PRIORITY -DNEVER_DEFAULT_TO_ASYNC_MODE -DSURFACEFLINGER_FORCE_SCREEN_RELEASE
	LOCAL_CFLAGS += -DREFRESH_RATE=56
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libEGL \
	libGLESv1_CM \
	libbinder \
	libui \
	libgui

# this is only needed for DDMS debugging
LOCAL_SHARED_LIBRARIES += libdvm libandroid_runtime

ifeq ($(BOARD_USES_LGE_HDMI_ROTATION),true)
LOCAL_CFLAGS += -DUSE_LGE_HDMI
LOCAL_SHARED_LIBRARIES += \
	libnvdispmgr_d
endif

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_C_INCLUDES += hardware/libhardware/modules/gralloc

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
LOCAL_SHARED_LIBRARIES += \
	libQcomUI
LOCAL_C_INCLUDES += hardware/qcom/display/libqcomui
LOCAL_CFLAGS += -DQCOM_HARDWARE
ifeq ($(TARGET_QCOM_HDMI_OUT),true)
LOCAL_CFLAGS += -DQCOM_HDMI_OUT
endif
endif

LOCAL_MODULE:= libsurfaceflinger

include $(BUILD_SHARED_LIBRARY)
