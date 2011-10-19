LOCAL_PATH:= $(call my-dir)

#
# libmediaplayerservice
#

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=               \
    MediaRecorderClient.cpp     \
    MediaPlayerService.cpp      \
    MetadataRetrieverClient.cpp \
    TestPlayerStub.cpp          \
    FLACPlayer.cpp              \
    MidiMetadataRetriever.cpp   \
    MidiFile.cpp                \
    StagefrightPlayer.cpp       \
    StagefrightRecorder.cpp

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
LOCAL_LDLIBS += -ldl -lpthread
endif

LOCAL_SHARED_LIBRARIES :=     		\
	libcutils             			\
	libutils              			\
	libbinder             			\
        libFLAC                                 \
	libvorbisidec         			\
	libsonivox            			\
	libmedia              			\
	libcamera_client      			\
	libandroid_runtime    			\
	libstagefright        			\
	libstagefright_omx    			\
	libstagefright_color_conversion         \
	libstagefright_foundation               \
	libsurfaceflinger_client

LOCAL_STATIC_LIBRARIES := \
        libstagefright_rtsp

ifneq ($(BUILD_WITHOUT_PV),true)
LOCAL_SHARED_LIBRARIES += \
	libopencore_player    \
	libopencore_author
else
LOCAL_CFLAGS += -DNO_OPENCORE
endif

# CFLAGS for StagefrightRecorder includes
ifeq ($(TARGET_USE_OMAP_COMPAT),true)
	LOCAL_CFLAGS += -DOMAP_COMPAT
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

LOCAL_C_INCLUDES :=                                                 \
	$(JNI_H_INCLUDE)                                                \
	$(call include-path-for, graphics corecg)                       \
	$(TOP)/frameworks/base/include/media/stagefright/openmax \
	$(TOP)/frameworks/base/media/libstagefright/include             \
	$(TOP)/frameworks/base/media/libstagefright/rtsp                \
        $(TOP)/external/flac/include                                    \
        $(TOP)/external/tremolo/Tremolo

ifeq ($(strip $(BOARD_USES_HW_MEDIARECORDER)),true)
    LOCAL_SHARED_LIBRARIES += libhwmediarecorder
    LOCAL_CFLAGS += -DUSE_BOARD_MEDIARECORDER
endif

ifeq ($(strip $(BOARD_USES_HW_MEDIAPLUGINS)),true)
    LOCAL_SHARED_LIBRARIES += libhwmediaplugin
    LOCAL_CFLAGS += -DUSE_BOARD_MEDIAPLUGIN
endif

ifeq ($(OMAP_ENHANCEMENT),true)

LOCAL_C_INCLUDES += $(TOP)/hardware/ti/omap3/liboverlay

LOCAL_SHARED_LIBRARIES += libui

endif

LOCAL_MODULE:= libmediaplayerservice

include $(BUILD_SHARED_LIBRARY)

