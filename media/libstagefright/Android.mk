LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
        ESDS.cpp                  \
        MediaBuffer.cpp           \
        MediaBufferGroup.cpp      \
        MediaDefs.cpp             \
        MediaSource.cpp           \
        MetaData.cpp              \
        OMXCodec.cpp              \
        Utils.cpp                 \
        OMXClient.cpp

ifeq ($(BUILD_WITH_FULL_STAGEFRIGHT),true)

LOCAL_SRC_FILES +=                \
        AMRExtractor.cpp          \
        AMRWriter.cpp             \
        AudioPlayer.cpp           \
        AudioSource.cpp           \
        AwesomePlayer.cpp         \
        CachingDataSource.cpp     \
        CameraSource.cpp          \
        DataSource.cpp            \
        FileSource.cpp            \
        HTTPDataSource.cpp        \
        HTTPStream.cpp            \
        JPEGSource.cpp            \
        MP3Extractor.cpp          \
        MPEG4Extractor.cpp        \
        MPEG4Writer.cpp           \
        MediaExtractor.cpp        \
        OggExtractor.cpp          \
        Prefetcher.cpp            \
        SampleIterator.cpp        \
        SampleTable.cpp           \
        ShoutcastSource.cpp       \
        StagefrightMediaScanner.cpp \
        StagefrightMetadataRetriever.cpp \
        TimeSource.cpp            \
        TimedEventQueue.cpp       \
        WAVExtractor.cpp          \
        string.cpp

LOCAL_CFLAGS += -DBUILD_WITH_FULL_STAGEFRIGHT
endif

LOCAL_C_INCLUDES:= \
        $(JNI_H_INCLUDE) \
        $(TOP)/external/opencore/extern_libs_v2/khronos/openmax/include \
        $(TOP)/external/opencore/android \
        $(TOP)/external/tremolo \
        $(TOP)/external/flac/include

LOCAL_SHARED_LIBRARIES := \
        libbinder         \
        libmedia          \
        libutils          \
        libcutils         \
        libui             \
        libsonivox        \
        libvorbisidec     \
        libFLAC

ifneq ($(BOARD_USES_ECLAIR_LIBCAMERA),true)
    LOCAL_SHARED_LIBRARIES += \
    	libsurfaceflinger_client \
    	libcamera_client
endif

LOCAL_STATIC_LIBRARIES := \
        libstagefright_aacdec \
        libstagefright_amrnbdec \
        libstagefright_amrnbenc \
        libstagefright_amrwbdec \
        libstagefright_avcdec \
        libstagefright_m4vh263dec \
        libstagefright_mp3dec \
        libstagefright_vorbisdec

LOCAL_SHARED_LIBRARIES += \
        libstagefright_amrnb_common \
        libstagefright_avc_common

ifeq ($(BUILD_WITH_FULL_STAGEFRIGHT),true)

LOCAL_STATIC_LIBRARIES += \
        libstagefright_id3

LOCAL_SHARED_LIBRARIES += \
        libstagefright_color_conversion

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread -ldl
        LOCAL_SHARED_LIBRARIES += libdvm
        LOCAL_CPPFLAGS += -DANDROID_SIMULATOR
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

endif

ifneq ($(filter qsd8k msm7k msm7625, $(TARGET_BOARD_PLATFORM)),)
        LOCAL_CFLAGS += -DUSE_QCOM_OMX_FIX
endif
ifneq ($(filter glacier, $(TARGET_DEVICE)),)
        LOCAL_CFLAGS += -DUSE_QCOM_OMX_FIX
endif

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread
endif

LOCAL_CFLAGS += -Wno-multichar

LOCAL_MODULE:= libstagefright

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
