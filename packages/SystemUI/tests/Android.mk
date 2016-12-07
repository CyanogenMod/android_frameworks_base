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

LOCAL_JACK_FLAGS := --multi-dex native

LOCAL_PROTOC_OPTIMIZE_TYPE := nano
LOCAL_PROTOC_FLAGS := -I$(LOCAL_PATH)/..
LOCAL_PROTO_JAVA_OUTPUT_PARAMS := optional_field_style=accessors

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages com.android.systemui:com.android.keyguard:android.support.v14.preference:android.support.v7.preference:android.support.v7.appcompat:android.support.v7.recyclerview \
    --extra-packages android.support.v17.leanback

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
    $(call all-Iaidl-files-under, src) \
    $(call all-java-files-under, ../src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/support/v7/preference/res \
    frameworks/support/v14/preference/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/v7/recyclerview/res \
    frameworks/support/v17/leanback/res \
    frameworks/base/packages/SystemUI/res \
    frameworks/base/packages/Keyguard/res

LOCAL_JAVA_LIBRARIES := android.test.runner telephony-common

LOCAL_PACKAGE_NAME := SystemUITests

LOCAL_STATIC_JAVA_LIBRARIES := \
    mockito-target \
    Keyguard \
    android-support-v7-recyclerview \
    android-support-v7-preference \
    android-support-v7-appcompat \
    android-support-v14-preference \
    android-support-v17-leanback \
    android-support-v7-palette \
    android-support-v4 \
    SystemUI-proto-tags \
    org.cyanogenmod.platform.internal \
    uicommon

# sign this with platform cert, so this test is allowed to inject key events into
# UI it doesn't own. This is necessary to allow screenshots to be taken
LOCAL_CERTIFICATE := platform

# Provide jack a list of classes to exclude from code coverage.
# This is needed because the SystemUITests compile SystemUI source directly, rather than using
# LOCAL_INSTRUMENTATION_FOR := SystemUI.
#
# We want to exclude the test classes from code coverage measurements, but they share the same
# package as the rest of SystemUI so they can't be easily filtered by package name.
#
# Generate a comma separated list of patterns based on the test source files under src/
# SystemUI classes are in ../src/ so they won't be excluded.
# Example:
#   Input files: src/com/android/systemui/Test.java src/com/android/systemui/AnotherTest.java
#   Generated exclude list: com.android.systemui.Test*,com.android.systemui.AnotherTest*

# Filter all src files under src/ to just java files
local_java_files := $(filter %.java,$(call all-java-files-under, src))
# Transform java file names into full class names.
# This only works if the class name matches the file name and the directory structure
# matches the package.
local_classes := $(subst /,.,$(patsubst src/%.java,%,$(local_java_files)))
local_comma := ,
local_empty :=
local_space := $(local_empty) $(local_empty)
# Convert class name list to jacoco exclude list
# This appends a * to all classes and replace the space separators with commas.
jacoco_exclude := $(subst $(space),$(comma),$(patsubst %,%*,$(local_classes)))

LOCAL_JACK_COVERAGE_INCLUDE_FILTER := com.android.systemui.*
LOCAL_JACK_COVERAGE_EXCLUDE_FILTER := com.android.systemui.tests.*,$(jacoco_exclude)

include frameworks/base/packages/SettingsLib/common.mk

include $(BUILD_PACKAGE)

# Reset variables
local_java_files :=
local_classes :=
local_comma :=
local_space :=
jacoco_exclude :=
