LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

FrameworkServicesTests_BUILD_PACKAGE := $(LOCAL_PATH)/FrameworkServicesTests_apk.mk

# build sub packages
include $(call all-makefiles-under,$(LOCAL_PATH))
