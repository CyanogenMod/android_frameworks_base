LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    AudioFlinger.cpp            \
    AudioMixer.cpp.arm          \
    AudioResampler.cpp.arm      \
    AudioResamplerSinc.cpp.arm  \
    AudioResamplerCubic.cpp.arm \
    AudioPolicyService.cpp

LOCAL_C_INCLUDES := \
    system/media/audio_effects/include

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    libbinder \
    libmedia \
    libhardware \
    libhardware_legacy \
    libeffects \
    libdl \
    libpowermanager

LOCAL_STATIC_LIBRARIES := \
    libcpustats \
    libmedia_helper

LOCAL_MODULE:= libaudioflinger

ifeq ($(BOARD_USE_MOTO_DOCK_HACK),true)
   LOCAL_CFLAGS += -DMOTO_DOCK_HACK
endif

ifeq ($(BOARD_HAS_SAMSUNG_VOLUME_BUG),true)
   LOCAL_CFLAGS += -DHAS_SAMSUNG_VOLUME_BUG
endif

ifeq ($(ARCH_ARM_HAVE_NEON),true)
   LOCAL_CFLAGS += -D__ARM_HAVE_NEON
endif

include $(BUILD_SHARED_LIBRARY)
