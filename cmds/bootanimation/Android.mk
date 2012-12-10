LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	bootanimation_main.cpp \
	BootAnimation.cpp

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libandroidfw \
	libutils \
	libbinder \
    libui \
	libskia \
    libEGL \
    libGLESv1_CM \
    libgui

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

ifeq ($(TARGET_BOOTANIMATION_USE_RGB565),true)
    LOCAL_CFLAGS += -DUSE_565
endif

LOCAL_MODULE:= bootanimation


include $(BUILD_EXECUTABLE)
