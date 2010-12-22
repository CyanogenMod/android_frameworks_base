LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	CorrectedGyroSensor.cpp \
    Fusion.cpp \
    GravitySensor.cpp \
    LinearAccelerationSensor.cpp \
    OrientationSensor.cpp \
    RotationVectorSensor.cpp \
    SensorDevice.cpp \
    SensorFusion.cpp \
    SensorInterface.cpp \
    SensorService.cpp \


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

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libbinder \
	libui \
	libgui



LOCAL_MODULE:= libsensorservice

include $(BUILD_SHARED_LIBRARY)
