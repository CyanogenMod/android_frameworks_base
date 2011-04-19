LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifneq ($(BUILD_WITHOUT_PV),true)
# Set up the OpenCore variables.
include external/opencore/Config.mk
LOCAL_C_INCLUDES := $(PV_INCLUDES)
LOCAL_CFLAGS := $(PV_CFLAGS_MINUS_VISIBILITY)
endif

ifeq ($(TARGET_BOARD_PLATFORM),omap3)
LOCAL_CFLAGS += -DTARGET_OMAP3
endif

ifeq ($(TARGET_BOARD_PLATFORM),msm7x30)
LOCAL_CFLAGS += -DTARGET_7X30
endif

LOCAL_C_INCLUDES += $(JNI_H_INCLUDE)

LOCAL_SRC_FILES:=                     \
	OMX.cpp                       \
        OMXComponentBase.cpp          \
        OMXNodeInstance.cpp           \
        OMXMaster.cpp

ifneq ($(BUILD_WITHOUT_PV),true)
LOCAL_SRC_FILES += \
        OMXPVCodecsPlugin.cpp
else
LOCAL_CFLAGS += -DNO_OPENCORE
endif

LOCAL_C_INCLUDES += $(TOP)/frameworks/base/include/media/stagefright/openmax

LOCAL_SHARED_LIBRARIES :=       \
        libbinder               \
        libmedia                \
        libutils                \
        libui                   \
        libcutils               \
        libstagefright_color_conversion

ifneq ($(BUILD_WITHOUT_PV),true)
LOCAL_SHARED_LIBRARIES += \
        libopencore_common
endif

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread -ldl
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

ifeq ($(BOARD_CAMERA_USE_GETBUFFERINFO),true)
        LOCAL_CFLAGS += -DUSE_GETBUFFERINFO
        LOCAL_C_INCLUDES += $(TOP)/hardware/qcom/media/mm-core/omxcore/inc
endif

LOCAL_MODULE:= libstagefright_omx

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))

