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

# ===========================================================
# Common Droiddoc vars
cmplat.docs.src_files := \
    $(call all-java-files-under, src) \
    $(call find-other-java-files, $(cyanogenmod_app_src)) \
    $(call all-html-files-under, src)
cmplat.docs.java_libraries := \
    org.cyanogenmod.platform

# Documentation
# ===========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := org.cyanogenmod.platform
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_MODULE_TAGS := optional

intermediates.COMMON := $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS), org.cyanogenmod.platform,,COMMON)

LOCAL_SRC_FILES := $(cmplat.docs.src_files)
LOCAL_ADDITONAL_JAVA_DIR := $(intermediates.COMMON)/src

LOCAL_SDK_VERSION := 21
LOCAL_IS_HOST_MODULE := false
LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR := build/tools/droiddoc/templates-sdk

LOCAL_JAVA_LIBRARIES := $(cmplat.docs.java_libraries)

LOCAL_DROIDDOC_OPTIONS := \
    -offlinemode \
    -hdf android.whichdoc offline \
    -federate Android http://developer.android.com \
    -federationapi Android prebuilts/sdk/api/21.txt

include $(BUILD_DROIDDOC)

# Cleanup temp vars
# ===========================================================
cmplat.docs.src_files :=
cmplat.docs.java_libraries :=
intermediates.COMMON :=
