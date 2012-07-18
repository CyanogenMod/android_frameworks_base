LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        android_fmradio.cpp \
        android_fmradio_Receiver.cpp \
        android_fmradio_Transmitter.cpp

LOCAL_C_INCLUDES += \
        $(JNI_H_INCLUDE)\
        $(TOP)/frameworks/base/fmradio/include \
        $(TOP)/hardware/libhardware/include/hardware

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        libhardware \
        libhardware_legacy \
        libnativehelper \
        libsystem_server \
        libutils \
        libdl \
        libui \
        libmedia \

ifeq ($(TARGET_SIMULATOR),true)
ifeq ($(TARGET_OS),linux)
ifeq ($(TARGET_ARCH),x86)
LOCAL_LDLIBS += -lpthread -ldl -lrt
endif
endif
endif

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
        LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

LOCAL_MODULE:= libanalogradiobroadcasting
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

