LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	GravitySensor.cpp \
	LinearAccelerationSensor.cpp \
	RotationVectorSensor.cpp \
    SensorService.cpp \
    SensorInterface.cpp \
    SensorDevice.cpp \
    SecondOrderLowPassFilter.cpp

LOCAL_CFLAGS:= -DLOG_TAG=\"SensorService\"

ifeq ($(TARGET_USES_OLD_LIBSENSORS_HAL),true)
    LOCAL_CFLAGS += -DENABLE_SENSORS_COMPAT
endif

ifeq ($(TARGET_SENSORS_NO_OPEN_CHECK),true)
    LOCAL_CFLAGS += -DSENSORS_NO_OPEN_CHECK
endif

ifeq ($(TARGET_HAS_FOXCONN_SENSORS),true)
    LOCAL_CFLAGS += -DFOXCONN_SENSORS
endif

ifneq ($(TARGET_PROXIMITY_SENSOR_LIMIT),)
    LOCAL_CFLAGS += -DPROXIMITY_LIES=$(TARGET_PROXIMITY_SENSOR_LIMIT)
endif

ifneq ($(filter p990 p999 p970, $(TARGET_BOOTLOADER_BOARD_NAME)),)
    LOCAL_CFLAGS += -DUSE_LGE_ALS_DUMMY
    ifeq ($(TARGET_BOOTLOADER_BOARD_NAME),p970)
        LOCAL_CFLAGS += -DUSE_LGE_ALS_OMAP3
    endif
endif

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt -lpthread
	endif
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libbinder \
	libui \
	libgui

LOCAL_PRELINK_MODULE := false

LOCAL_MODULE:= libsensorservice

include $(BUILD_SHARED_LIBRARY)
