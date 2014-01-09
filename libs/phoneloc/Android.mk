# Copyright (C) 2013 The MoKee Open Source Project
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

include $(CLEAR_VARS)

LOCAL_CFLAGS := -O3
LOCAL_CXXFLAGS := -O3
LOCAL_MODULE    := libmokee-phoneloc-jni
LOCAL_SRC_FILES := Global.cpp Mps.cpp phoneloc.c androidjni.c
LOCAL_C_INCLUDES += $(JNI_H_INCLUDE)
LOCAL_PRELINK_MODULE := false
LOCAL_LDLIBS := -L${SYSROOT}/usr/f -llog
LOCAL_MODULE_TAGS := optional


include $(BUILD_SHARED_LIBRARY)
