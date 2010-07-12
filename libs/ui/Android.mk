LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	EGLUtils.cpp \
	EventHub.cpp \
	EventRecurrence.cpp \
	FramebufferNativeWindow.cpp \
	GraphicBuffer.cpp \
	GraphicBufferAllocator.cpp \
	GraphicBufferMapper.cpp \
	KeyLayoutMap.cpp \
	KeyCharacterMap.cpp \
	IOverlay.cpp \
	Overlay.cpp \
	PixelFormat.cpp \
	Rect.cpp \
	Region.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libEGL \
	libbinder \
	libpixelflinger \
	libhardware \
	libhardware_legacy


ifeq ($(BOARD_USES_ECLAIR_LIBCAMERA),true)

LOCAL_SRC_FILES+= \
	../camera/Camera.cpp \
	../camera/CameraParameters.cpp \
	../camera/ICamera.cpp \
	../camera/ICameraClient.cpp \
	../camera/ICameraService.cpp

LOCAL_SRC_FILES+= \
	../surfaceflinger_client/ISurfaceComposer.cpp \
	../surfaceflinger_client/ISurface.cpp \
	../surfaceflinger_client/ISurfaceFlingerClient.cpp \
	../surfaceflinger_client/LayerState.cpp \
	../surfaceflinger_client/SharedBufferStack.cpp \
	../surfaceflinger_client/Surface.cpp \
	../surfaceflinger_client/SurfaceComposerClient.cpp

endif

LOCAL_MODULE:= libui

ifeq ($(TARGET_SIMULATOR),true)
    LOCAL_LDLIBS += -lpthread
endif

include $(BUILD_SHARED_LIBRARY)
