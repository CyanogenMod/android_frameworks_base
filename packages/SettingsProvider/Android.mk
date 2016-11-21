LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files) \
    src/com/android/providers/settings/EventLogTags.logtags

LOCAL_JAVA_LIBRARIES := telephony-common ims-common
LOCAL_STATIC_JAVA_LIBRARIES := org.cyanogenmod.platform.sdk

LOCAL_PACKAGE_NAME := SettingsProvider
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)

########################
include $(call all-makefiles-under,$(LOCAL_PATH))
