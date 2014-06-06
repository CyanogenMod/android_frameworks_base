LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    android_media_ImageReader.cpp \
    android_media_MediaCrypto.cpp \
    android_media_MediaCodec.cpp \
    android_media_MediaCodecList.cpp \
    android_media_MediaDrm.cpp \
    android_media_MediaExtractor.cpp \
    android_media_MediaMuxer.cpp \
    android_media_MediaPlayer.cpp \
    android_media_MediaRecorder.cpp \
    android_media_MediaScanner.cpp \
    android_media_MediaMetadataRetriever.cpp \
    android_media_ResampleInputStream.cpp \
    android_media_MediaProfiles.cpp \
    android_media_AmrInputStream.cpp \
    android_media_Utils.cpp \
    android_mtp_MtpDatabase.cpp \
    android_mtp_MtpDevice.cpp \
    android_mtp_MtpServer.cpp \

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
    LOCAL_SRC_FILES += \
    android_media_ExtMediaPlayer.cpp
endif

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libnativehelper \
    libutils \
    libbinder \
    libmedia \
    libskia \
    libui \
    liblog \
    libcutils \
    libgui \
    libstagefright \
    libstagefright_foundation \
    libcamera_client \
    libmtp \
    libusbhost \
    libexif \
    libstagefright_amrnb_common \

LOCAL_REQUIRED_MODULES := \
    libjhead_jni

LOCAL_STATIC_LIBRARIES := \
    libstagefright_amrnbenc

LOCAL_C_INCLUDES += \
    external/libexif/ \
    external/tremor/Tremor \
    frameworks/base/core/jni \
    frameworks/av/media/libmedia \
    frameworks/av/media/libstagefright \
    frameworks/av/media/libstagefright/codecs/amrnb/enc/src \
    frameworks/av/media/libstagefright/codecs/amrnb/common \
    frameworks/av/media/libstagefright/codecs/amrnb/common/include \
    frameworks/av/media/mtp

ifneq ($(TI_CUSTOM_DOMX_PATH),)
LOCAL_C_INCLUDES += $(TI_CUSTOM_DOMX_PATH)/omx_core/inc
else
LOCAL_C_INCLUDES += frameworks/native/include/media/openmax
endif

LOCAL_C_INCLUDES += \
    $(call include-path-for, libhardware)/hardware \
    system/media/camera/include \
    $(PV_INCLUDES) \
    $(JNI_H_INCLUDE) \
    $(call include-path-for, corecg graphics)

LOCAL_CFLAGS +=

LOCAL_LDLIBS := -lpthread

LOCAL_MODULE:= libmedia_jni

include $(BUILD_SHARED_LIBRARY)

# build libsoundpool.so
# build libaudioeffect_jni.so
include $(call all-makefiles-under,$(LOCAL_PATH))
