LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := services.core

LOCAL_SRC_FILES += \
    $(call all-java-files-under,java) \
    java/com/android/server/EventLogTags.logtags \
    java/com/android/server/am/EventLogTags.logtags

LOCAL_JAVA_LIBRARIES := android.policy telephony-common

LOCAL_JAVA_LIBRARIES += services.accessibility

LOCAL_JAVA_LIBRARIES += org.cyanogenmod.platform.sdk

include $(BUILD_STATIC_JAVA_LIBRARY)
