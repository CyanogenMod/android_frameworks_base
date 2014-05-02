LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

cards_dir := ../../../../frameworks/opt/cards/res
res_dirs := res $(cards_dir)
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    src/com/android/systemui/EventLogTags.logtags

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := android-opt-cards android-support-v4 android-support-v13 jsr305 acra android-visualizer

LOCAL_PACKAGE_NAME := SystemUI
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.android.cards

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
