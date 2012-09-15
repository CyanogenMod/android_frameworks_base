LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	CorrectedGyroSensor.cpp \
    Fusion.cpp \
    GravitySensor.cpp \
    LinearAccelerationSensor.cpp \
    OrientationSensor.cpp \
    RotationVectorSensor.cpp \
    RotationVectorSensor2.cpp \
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

ifneq ($(BOARD_SYSFS_LIGHT_SENSOR),)
    LOCAL_CFLAGS += -DSYSFS_LIGHT_SENSOR=\"$(BOARD_SYSFS_LIGHT_SENSOR)\"
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
