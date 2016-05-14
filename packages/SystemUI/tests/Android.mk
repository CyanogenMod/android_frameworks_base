# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages com.android.systemui:com.android.keyguard
LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    $(call all-java-files-under, ../src) \
    src/com/android/systemui/EventLogTags.logtags

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/base/packages/SystemUI/res \
    frameworks/base/packages/Keyguard/res

LOCAL_JAVA_LIBRARIES := android.test.runner telephony-common

LOCAL_PACKAGE_NAME := SystemUITests

LOCAL_STATIC_JAVA_LIBRARIES := mockito-target Keyguard
LOCAL_STATIC_JAVA_LIBRARIES += org.cyanogenmod.platform.internal \
    android-support-v7-palette \
    android-support-v4 \
    uicommon


# sign this with platform cert, so this test is allowed to inject key events into
# UI it doesn't own. This is necessary to allow screenshots to be taken
LOCAL_CERTIFICATE := platform

include frameworks/base/packages/SettingsLib/common.mk

include $(BUILD_PACKAGE)
