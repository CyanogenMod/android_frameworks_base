LOCAL_PATH:= $(call my-dir)

# Visualizer library
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	EffectVisualizer.cpp

# visualizer can't be built without optimizations, so we enforce -O2 if no
# other optimization flag is set - but we don't override what the global
# flags are saying if something else is given (-Os or -O3 are useful)
ifeq ($(findstring -O, $(TARGET_GLOBAL_CFLAGS)),)
LOCAL_CFLAGS += -O2
endif
ifneq ($(findstring -O0, $(TARGET_GLOBAL_CFLAGS)),)
LOCAL_CFLAGS += -O2
endif

LOCAL_CFLAGS+= -fno-strict-aliasing

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libdl

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/soundfx
LOCAL_MODULE:= libvisualizer

LOCAL_C_INCLUDES := \
	$(call include-path-for, graphics corecg) \
	system/media/audio_effects/include


include $(BUILD_SHARED_LIBRARY)
