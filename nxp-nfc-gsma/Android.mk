LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_MODULE:= com.gsma.services.nfc
LOCAL_MODULE_TAGS := optional

include $(BUILD_JAVA_LIBRARY)



