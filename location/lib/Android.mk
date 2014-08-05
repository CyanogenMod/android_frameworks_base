#
# Copyright (C) 2010 The Android Open Source Project
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
#
LOCAL_PATH := $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE:= com.android.location.provider
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
            $(call all-subdir-java-files) \
            $(call all-aidl-files-under, java)

LOCAL_MAVEN_ARTIFACT := framework-location
LOCAL_MAVEN_GROUPID := com.cyngn
LOCAL_MAVEN_VERSION := 11.0.0-SNAPSHOT            

include $(BUILD_JAVA_LIBRARY)


# ====  com.google.location.xml lib def  ========================
include $(CLEAR_VARS)

LOCAL_MODULE := com.android.location.provider.xml
LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_CLASS := ETC

# This will install the file in /system/etc/permissions
#
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

LOCAL_SRC_FILES := $(LOCAL_MODULE)

include $(BUILD_PREBUILT)
