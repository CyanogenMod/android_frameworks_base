LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(TARGET_OVERLAY_ALWAYS_DETERMINES_FORMAT),true)
    LOCAL_CFLAGS += -DOVERLAY_ALWAYS_DEFAULT
endif

LOCAL_SRC_FILES:= \
	ISurfaceComposer.cpp \
	ISurface.cpp \
	ISurfaceComposerClient.cpp \
	LayerState.cpp \
	SharedBufferStack.cpp \
	Surface.cpp \
	SurfaceComposerClient.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libhardware \
	libui

LOCAL_MODULE:= libsurfaceflinger_client

ifeq ($(TARGET_SIMULATOR),true)
    LOCAL_LDLIBS += -lpthread
endif

include $(BUILD_SHARED_LIBRARY)
