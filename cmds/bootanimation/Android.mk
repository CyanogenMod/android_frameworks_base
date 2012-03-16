LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	bootanimation_main.cpp \
	BootAnimation.cpp

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

ifeq ($(BOARD_CACHE_BOOTANIM),true)
LOCAL_CFLAGS += -DCACHE_BOOTANIM
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
    libui \
	libskia \
    libEGL \
    libGLESv1_CM \
    libgui

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_MODULE:= bootanimation


include $(BUILD_EXECUTABLE)
