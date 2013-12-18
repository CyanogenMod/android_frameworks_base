LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags

LOCAL_MODULE:= services

LOCAL_JAVA_LIBRARIES := android.policy conscrypt telephony-common

include $(BUILD_JAVA_LIBRARY)

#Include the vendor-jars.mk to add dependency frameworks
#core packages by vendor to  LOCAL_JAVA_LIBRARIES
-include frameworks/base/core/vendor-jars.mk

include $(BUILD_DROIDDOC)
