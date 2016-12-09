LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := services.usage

LOCAL_SRC_FILES += \
      $(call all-java-files-under,java)

LOCAL_JAVA_LIBRARIES := services.core

LOCAL_JAVA_LIBRARIES += org.cyanogenmod.platform.internal

include $(BUILD_STATIC_JAVA_LIBRARY)
