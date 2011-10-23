# Copyright (C) 2011 The CyanogenMod Project
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

LOCAL_PATH:= frameworks/base/data/sounds/kang
include $(CLEAR_VARS)

audio_types := $(shell sed -e 's/^\#.*//' $(LOCAL_PATH)/.gitignore)
$(shell mkdir -p $(addprefix $(LOCAL_PATH)/,$(audio_types)))
audio_files := $(foreach type,$(audio_types),$(addprefix $(type),$(notdir $(wildcard $(LOCAL_PATH)/$(type)*))))
audio_pairs := $(foreach file,$(audio_files),$(LOCAL_PATH)/$(file):system/media/audio/$(file))
PRODUCT_COPY_FILES += $(audio_pairs)
