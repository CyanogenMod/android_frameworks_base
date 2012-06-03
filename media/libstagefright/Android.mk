LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
ifeq ($(TARGET_BOARD_PLATFORM),msm7x27a)
    LOCAL_CFLAGS += -DUSE_AAC_HW_DEC
endif

ifeq ($(TARGET_BOARD_PLATFORM),msm7x27)
    LOCAL_CFLAGS += -DTARGET7x27
endif
ifeq ($(TARGET_BOARD_PLATFORM),msm7x27a)
    LOCAL_CFLAGS += -DTARGET7x27A
endif
ifeq ($(TARGET_BOARD_PLATFORM),msm7x30)
    LOCAL_CFLAGS += -DTARGET7x30
endif
ifeq ($(TARGET_BOARD_PLATFORM),qsd8k)
    LOCAL_CFLAGS += -DTARGET8x50
endif
ifeq ($(TARGET_BOARD_PLATFORM),msm8660)
    LOCAL_CFLAGS += -DTARGET8x60
endif
ifeq ($(TARGET_BOARD_PLATFORM),msm8960)
    LOCAL_CFLAGS += -DTARGET8x60
endif
ifeq ($(BOARD_CAMERA_USE_MM_HEAP),true)
    LOCAL_CFLAGS += -DCAMERA_MM_HEAP
endif
endif
include frameworks/base/media/libstagefright/codecs/common/Config.mk

LOCAL_SRC_FILES:=                         \
        ACodec.cpp                        \
        AACExtractor.cpp                  \
        AACWriter.cpp                     \
        AMRExtractor.cpp                  \
        AMRWriter.cpp                     \
        AudioPlayer.cpp                   \
        AudioSource.cpp                   \
        AwesomePlayer.cpp                 \
        CameraSource.cpp                  \
        CameraSourceTimeLapse.cpp         \
        VideoSourceDownSampler.cpp        \
        DataSource.cpp                    \
        DRMExtractor.cpp                  \
        ESDS.cpp                          \
        FileSource.cpp                    \
        FLACExtractor.cpp                 \
        HTTPBase.cpp                      \
        JPEGSource.cpp                    \
        MP3Extractor.cpp                  \
        MPEG2TSWriter.cpp                 \
        MPEG4Extractor.cpp                \
        MPEG4Writer.cpp                   \
        MediaBuffer.cpp                   \
        MediaBufferGroup.cpp              \
        MediaDefs.cpp                     \
        MediaExtractor.cpp                \
        MediaSource.cpp                   \
        MediaSourceSplitter.cpp           \
        MetaData.cpp                      \
        NuCachedSource2.cpp               \
        OMXClient.cpp                     \
        OMXCodec.cpp                      \
        OggExtractor.cpp                  \
        SampleIterator.cpp                \
        SampleTable.cpp                   \
        StagefrightMediaScanner.cpp       \
        StagefrightMetadataRetriever.cpp  \
        SurfaceMediaSource.cpp            \
        ThrottledSource.cpp               \
        TimeSource.cpp                    \
        TimedEventQueue.cpp               \
        Utils.cpp                         \
        VBRISeeker.cpp                    \
        WAVExtractor.cpp                  \
        WVMExtractor.cpp                  \
        XINGSeeker.cpp                    \
        avc_utils.cpp                     \

ifeq ($(OMAP_ENHANCEMENT), true)
LOCAL_SRC_FILES += ASFExtractor.cpp
LOCAL_SRC_FILES += AVIExtractor.cpp
endif
ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
        LOCAL_SRC_FILES += ExtendedExtractor.cpp
        LOCAL_SRC_FILES += ExtendedWriter.cpp
	LOCAL_C_INCLUDES += $(TOP)/hardware/qcom/display/libqcomui
endif

ifeq ($(TARGET_USES_QCOM_LPA),true)
ifeq ($(BOARD_USES_ALSA_AUDIO),true)
	LOCAL_SRC_FILES += LPAPlayerALSA.cpp
	LOCAL_C_INCLUDES += $(TARGET_OUT_HEADERS)/mm-audio/libalsa-intf
	LOCAL_C_INCLUDES += $(TOP)/hardware/libhardware_legacy/include
	LOCAL_SHARED_LIBRARIES += libalsa-intf
	LOCAL_SHARED_LIBRARIES += libhardware_legacy
	LOCAL_SHARED_LIBRARIES += libpowermanager
else
	LOCAL_SRC_FILES += LPAPlayer.cpp
ifeq ($(TARGET_USES_ION_AUDIO),true)
	LOCAL_SRC_FILES += LPAPlayerION.cpp
else
	LOCAL_SRC_FILES += LPAPlayerPMEM.cpp
endif
endif
endif

LOCAL_C_INCLUDES+= \
	$(JNI_H_INCLUDE) \
        $(TOP)/frameworks/base/include/media/stagefright/openmax \
        $(TOP)/external/flac/include \
        $(TOP)/external/tremolo \
        $(TOP)/external/openssl/include \

ifeq ($(OMAP_ENHANCEMENT), true)
LOCAL_C_INCLUDES += $(TOP)/hardware/ti/omap4xxx/domx/omx_core/inc
endif

LOCAL_SHARED_LIBRARIES += \
        libbinder         \
        libmedia          \
        libutils          \
        libcutils         \
        libui             \
        libsonivox        \
        libvorbisidec     \
        libstagefright_yuv \
        libcamera_client \
        libdrmframework  \
        libcrypto        \
        libssl           \
        libgui           \

LOCAL_STATIC_LIBRARIES := \
        libstagefright_color_conversion \
        libstagefright_aacenc \
        libstagefright_amrnbenc \
        libstagefright_amrwbenc \
        libstagefright_avcenc \
        libstagefright_m4vh263enc \
        libstagefright_matroska \
        libstagefright_timedtext \
        libvpx \
        libstagefright_mpeg2ts \
        libstagefright_httplive \
        libstagefright_id3 \
        libFLAC \

ifeq ($(TARGET_USES_QCOM_LPA),true)
LOCAL_STATIC_LIBRARIES += \
		libstagefright_aacdec \
	    libstagefright_mp3dec
endif

ifeq ($(BOARD_HAVE_CODEC_SUPPORT),SAMSUNG_CODEC_SUPPORT)
LOCAL_CFLAGS     += -DSAMSUNG_CODEC_SUPPORT
endif

ifeq ($(BOARD_USES_PROPRIETARY_OMX),SAMSUNG)
LOCAL_CFLAGS     += -DSAMSUNG_OMX
endif

################################################################################

# The following was shamelessly copied from external/webkit/Android.mk and
# currently must follow the same logic to determine how webkit was built and
# if it's safe to link against libchromium.net

# V8 also requires an ARMv7 CPU, and since we must use jsc, we cannot
# use the Chrome http stack either.
ifneq ($(strip $(ARCH_ARM_HAVE_ARMV7A)),true)
  USE_ALT_HTTP := true
endif

# See if the user has specified a stack they want to use
HTTP_STACK = $(HTTP)
# We default to the Chrome HTTP stack.
DEFAULT_HTTP = chrome
ALT_HTTP = android

ifneq ($(HTTP_STACK),chrome)
  ifneq ($(HTTP_STACK),android)
    # No HTTP stack is specified, pickup the one we want as default.
    ifeq ($(USE_ALT_HTTP),true)
      HTTP_STACK = $(ALT_HTTP)
    else
      HTTP_STACK = $(DEFAULT_HTTP)
    endif
  endif
endif

ifeq ($(HTTP_STACK),chrome)

LOCAL_SHARED_LIBRARIES += \
        liblog           \
        libicuuc         \
        libicui18n       \
        libz             \
        libdl            \

LOCAL_STATIC_LIBRARIES += \
        libstagefright_chromium_http

LOCAL_SHARED_LIBRARIES += libstlport libchromium_net
include external/stlport/libstlport.mk

LOCAL_CPPFLAGS += -DCHROMIUM_AVAILABLE=1

endif  # ifeq ($(HTTP_STACK),chrome)

################################################################################

LOCAL_SHARED_LIBRARIES += \
        libstagefright_amrnb_common \
        libstagefright_enc_common \
        libstagefright_avc_common \
        libstagefright_foundation \
        libdl

LOCAL_CFLAGS += -Wno-multichar

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
        LOCAL_C_INCLUDES += $(TOP)/hardware/qcom/display/libgralloc
        LOCAL_C_INCLUDES += $(TOP)/vendor/qcom/opensource/omx/mm-core/omxcore/inc
        LOCAL_C_INCLUDES += $(TOP)/system/core/include
        LOCAL_C_INCLUDES += $(TOP)/hardware/libhardware_legacy/include
endif

LOCAL_MODULE:= libstagefright

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
