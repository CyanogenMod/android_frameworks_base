LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifneq ($(HAVE_2_3_DSP), 1)

LOCAL_MODULE := libcyanogen-dsp

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/soundfx

LOCAL_PRELINK_MODULE := false

LOCAL_ARM_MODE := arm

LOCAL_SRC_FILES := \
	cyanogen-dsp.cpp \
	Biquad.cpp \
	Delay.cpp \
	Effect.cpp \
	EffectBassBoost.cpp \
	EffectCompression.cpp \
	EffectEqualizer.cpp \
	EffectVirtualizer.cpp \
	FIR16.cpp \
# terminator

LOCAL_C_INCLUDES += \
	frameworks/base/include \
# terminator

LOCAL_SHARED_LIBRARIES := \
	libcutils

include $(BUILD_SHARED_LIBRARY)

endif
