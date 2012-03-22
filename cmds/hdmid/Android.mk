# Copyright (C) 2008 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License
#

ifeq ($(TARGET_QCOM_HDMI_OUT),true)
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	hdmid_main.cpp \
	HDMIDaemon.cpp \

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt
	endif
endif

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES -DQCOM_HDMI_OUT

ifeq ($(TARGET_QCOM_HDMI_RESOLUTION_AUTO),true)
	LOCAL_CFLAGS += -DFORCE_AUTO_RESOLUTION
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
	libgui \

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_MODULE:= hdmid
LOCAL_MODULE_TAGS:= eng

include $(BUILD_EXECUTABLE)

endif
