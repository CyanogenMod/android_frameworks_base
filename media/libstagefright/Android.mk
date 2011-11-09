LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

include frameworks/base/media/libstagefright/codecs/common/Config.mk
ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_CFLAGS += -DTARGET_OMAP4 -DARM_4K_PAGE_SIZE=4096
endif

# Enable this flag to debug Stagefright
# LOCAL_CFLAGS += -DDEBUG_OMX

LOCAL_SRC_FILES:=                         \
        AMRExtractor.cpp                  \
        AMRWriter.cpp                     \
        AudioPlayer.cpp                   \
        AudioSource.cpp                   \
        AwesomePlayer.cpp                 \
        CameraSource.cpp                  \
        DataSource.cpp                    \
        ESDS.cpp                          \
        FileSource.cpp                    \
        HTTPStream.cpp                    \
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
        MetaData.cpp                      \
        NuCachedSource2.cpp               \
        NuHTTPDataSource.cpp              \
        OMXClient.cpp                     \
        OMXCodec.cpp                      \
        OggExtractor.cpp                  \
        SampleIterator.cpp                \
        SampleTable.cpp                   \
        ShoutcastSource.cpp               \
        StagefrightMediaScanner.cpp       \
        StagefrightMetadataRetriever.cpp  \
        ThreadedSource.cpp                \
        ThrottledSource.cpp               \
        TimeSource.cpp                    \
        TimedEventQueue.cpp               \
        Utils.cpp                         \
        WAVExtractor.cpp                  \
        avc_utils.cpp                     \
        string.cpp

ifeq ($(OMAP_ENHANCEMENT),true)
LOCAL_SRC_FILES += \
    ASFExtractor.cpp
endif

ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_SRC_FILES +=               \
        TIVideoConfigParser.cpp  \
        TISEIMessagesParser.cpp
endif
LOCAL_C_INCLUDES:= \
	$(JNI_H_INCLUDE) \
        $(TOP)/frameworks/base/include/media/stagefright/openmax \
        $(TOP)/external/tremolo \
        $(TOP)/external/flac/include \
        $(TOP)/frameworks/base/media/libstagefright/rtsp
ifeq ($(OMAP_ENHANCEMENT),true)
LOCAL_C_INCLUDES += \
        $(TOP)/hardware/ti/omap3/liboverlay
endif

ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_C_INCLUDES +=					\
	hardware/ti/omx/ducati/domx/system/omx_core/inc \
	hardware/ti/omap3/camera-omap4/inc
endif
ifeq ($(OMAP_ENHANCEMENT),true)
LOCAL_C_INCLUDES += $(TOP)/hardware/ti/omap3/liboverlay
endif
LOCAL_SHARED_LIBRARIES := \
        libbinder         \
        libmedia          \
        libutils          \
        libcutils         \
        libui             \
        libsonivox        \
        libvorbisidec     \
        libsurfaceflinger_client \
        libcamera_client  \
        libFLAC

ifeq ($(TARGET_BOARD_PLATFORM),omap4)
LOCAL_SHARED_LIBRARIES += \
	libcamera
endif

LOCAL_STATIC_LIBRARIES := \
        libstagefright_aacdec \
        libstagefright_aacenc \
        libstagefright_amrnbdec \
        libstagefright_amrnbenc \
        libstagefright_amrwbdec \
        libstagefright_amrwbenc \
        libstagefright_avcdec \
        libstagefright_avcenc \
        libstagefright_m4vh263dec \
        libstagefright_m4vh263enc \
        libstagefright_mp3dec \
        libstagefright_vorbisdec \
        libstagefright_matroska \
        libstagefright_vpxdec \
        libvpx \
        libstagefright_mpeg2ts \
        libstagefright_httplive \
        libstagefright_rtsp \
        libstagefright_id3 \
        libstagefright_g711dec \

LOCAL_SHARED_LIBRARIES += \
        libstagefright_amrnb_common \
        libstagefright_enc_common \
        libstagefright_avc_common \
        libstagefright_foundation \
        libstagefright_color_conversion

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread -ldl
        LOCAL_SHARED_LIBRARIES += libdvm
        LOCAL_CPPFLAGS += -DANDROID_SIMULATOR
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

ifneq ($(filter qsd8k msm7k msm7625 msm7x30, $(TARGET_BOARD_PLATFORM)),)
        LOCAL_CFLAGS += -DUSE_QCOM_OMX_FIX
endif

ifeq ($(BOARD_USE_YUV422I_DEFAULT_COLORFORMAT),true)
	LOCAL_CFLAGS += -DUSE_YUV422I_DEFAULT_COLORFORMAT
endif

ifeq ($(BOARD_CAMERA_USE_GETBUFFERINFO),true)
        LOCAL_CFLAGS += -DUSE_GETBUFFERINFO
        LOCAL_C_INCLUDES += $(TOP)/hardware/qcom/media/mm-core/omxcore/inc
endif

ifeq ($(TARGET_USE_SOFTWARE_AUDIO_AAC),true)
	LOCAL_CFLAGS += -DUSE_SOFTWARE_AUDIO_AAC
endif

ifeq ($(TARGET_DONT_SET_AUDIO_AAC_FORMAT),true)
        LOCAL_CFLAGS += -DDONT_SET_AUDIO_AAC_FORMAT
endif

ifeq ($(TARGET_USE_OMAP_COMPAT),true)
        LOCAL_CFLAGS += -DOMAP_COMPAT
endif

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread
endif

LOCAL_CFLAGS += -Wno-multichar

LOCAL_MODULE:= libstagefright

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
