LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_MODULE := FrameworksGraphicsHostTests

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := android.test.runner
#LOCAL_PACKAGE_NAME := FrameworksGraphicsTests

include $(BUILD_STATIC_JAVA_LIBRARY)

#####################################


