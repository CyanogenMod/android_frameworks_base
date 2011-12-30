LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_mediaserver.cpp 

LOCAL_SHARED_LIBRARIES := \
	libaudioflinger \
	libcameraservice \
	libmediaplayerservice \
	libutils \
	libbinder

ifeq ($(BOARD_USE_YAMAHAPLAYER),true)
    LOCAL_CFLAGS += -DYAMAHAPLAYER
    LOCAL_SHARED_LIBRARIES += libmediayamahaservice
endif

ifeq ($(BOARD_USE_SECTVOUT),true)
    LOCAL_CFLAGS += -DSECTVOUT
	LOCAL_SHARED_LIBRARIES += libTVOut
endif

base := $(LOCAL_PATH)/../..

LOCAL_C_INCLUDES := \
    $(base)/services/audioflinger \
    $(base)/services/camera/libcameraservice \
    $(base)/media/libmediaplayerservice

LOCAL_MODULE:= mediaserver

include $(BUILD_EXECUTABLE)
