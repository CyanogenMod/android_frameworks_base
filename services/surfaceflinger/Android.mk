LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    clz.cpp.arm \
    DisplayHardware/DisplayHardware.cpp \
    DisplayHardware/DisplayHardwareBase.cpp \
    BlurFilter.cpp.arm \
    GLExtensions.cpp \
    Layer.cpp \
    LayerBase.cpp \
    LayerBuffer.cpp \
    LayerBlur.cpp \
    LayerDim.cpp \
    MessageQueue.cpp \
    SurfaceFlinger.cpp \
    TextureManager.cpp \
    Transform.cpp

LOCAL_CFLAGS:= -DLOG_TAG=\"SurfaceFlinger\"
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

ifeq ($(TARGET_BOARD_PLATFORM), omap3)
	LOCAL_CFLAGS += -DNO_RGBX_8888
endif
ifeq ($(BOARD_NO_RGBX_8888), true)
	LOCAL_CFLAGS += -DNO_RGBX_8888
endif
ifeq ($(BOARD_HAS_FLIPPED_SCREEN), true)
	LOCAL_CFLAGS += -DHAS_FLIPPED_SCREEN
endif
ifeq ($(TARGET_BOARD_PLATFORM), s5pc110)
	LOCAL_CFLAGS += -DHAS_CONTEXT_PRIORITY
endif
ifeq ($(BOARD_HAS_LIMITED_EGL), true)
	LOCAL_CFLAGS += -DHAS_LIMITED_EGL
endif

ifeq ($(BOARD_AVOID_DRAW_TEXTURE_EXTENSION), true)
	LOCAL_CFLAGS += -DAVOID_DRAW_TEXTURE
endif

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt -lpthread
	endif
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libEGL \
	libGLESv1_CM \
	libbinder \
	libui \
	libsurfaceflinger_client

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_C_INCLUDES += hardware/libhardware/modules/gralloc

LOCAL_MODULE:= libsurfaceflinger

include $(BUILD_SHARED_LIBRARY)
