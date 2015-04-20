#
# Copyright (C) 2015 The CyanogenMod Project
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

LOCAL_MODULE:= org.cyanogenmod.platform
LOCAL_MODULE_TAGS := optional

cyanogenmod_app_src := ../java/cyanogenmod/app

LOCAL_SRC_FILES := \
            $(call find-other-java-files, $(cyanogenmod_app_src)) \
            $(call all-subdir-java-files)

$(info $(LOCAL_SRC_FILES))

LOCAL_SRC_FILES += \
            $(call all-aidl-files-under, java) \

$(info $(LOCAL_SRC_FILES))

# Included aidl files from cyanogenmod.app namespace
LOCAL_AIDL_INCLUDES := $(call find-other-aidl-files, $(cyanogenmod_app_src))

$(info $(LOCAL_AIDL_INCLUDES))

include $(BUILD_STATIC_JAVA_LIBRARY)
