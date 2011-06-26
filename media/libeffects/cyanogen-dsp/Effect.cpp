/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "DSP-Effect"

#include <cutils/log.h>
#include "Effect.h"

Effect::Effect()
    : mSamplingRate(44100)
{
}

Effect::~Effect() {
}

/* Configure a bunch of general parameters. */
int32_t Effect::configure(void* pCmdData) {
    /* Sigh. I'm just going to assume in and output configs are identical.
     * If they aren't, this api is mad. */

    effect_config_t *cfg = (effect_config_t *) pCmdData;
    buffer_config_t in = cfg->inputCfg;
    buffer_config_t out = cfg->outputCfg;

    if (in.mask & EFFECT_CONFIG_SMP_RATE) {
	mSamplingRate = in.samplingRate;
    }
    if (in.mask & EFFECT_CONFIG_CHANNELS) {
	/* We can only deal with stereo. */
	if (in.channels != (CHANNEL_FRONT_LEFT | CHANNEL_FRONT_RIGHT)) {
	    LOGE("Invalid channel setup: %x", in.channels);
	    return -EINVAL;
	}
	mChannels = 2;
    }
    if (in.mask & EFFECT_CONFIG_FORMAT) {
	mFormatIn = (audio_format_e) in.format;
    }
    if (out.mask & EFFECT_CONFIG_FORMAT) {
	mFormatOut = (audio_format_e) in.format;
    }
    if (out.mask & EFFECT_CONFIG_ACC_MODE) {
	mAccessMode = (effect_buffer_access_e) out.accessMode;
    }

    return 0;
}

int32_t Effect::command(uint32_t cmdCode, uint32_t cmdSize, void *pCmdData, uint32_t *replySize, void* pReplyData)
{
    switch (cmdCode) {
    case EFFECT_CMD_ENABLE:
    case EFFECT_CMD_DISABLE: {
	mEnable = cmdCode == EFFECT_CMD_ENABLE;
	int32_t *replyData = (int32_t *) pReplyData;
	*replyData = 0;
	break;
    }

    case EFFECT_CMD_INIT:
    case EFFECT_CMD_CONFIGURE:
    case EFFECT_CMD_SET_PARAM:
    case EFFECT_CMD_SET_PARAM_COMMIT: {
	int32_t *replyData = (int32_t *) pReplyData;
	*replyData = 0;
	break;
    }

    case EFFECT_CMD_RESET:
    case EFFECT_CMD_SET_PARAM_DEFERRED:
    case EFFECT_CMD_SET_DEVICE:
    case EFFECT_CMD_SET_AUDIO_MODE:
	break;

    case EFFECT_CMD_GET_PARAM: {
	effect_param_t *rep = (effect_param_t *) pReplyData;
	rep->status = -EINVAL;
	rep->psize = 0;
	rep->vsize = 0;
	*replySize = 12;
	break;
    }

    case EFFECT_CMD_SET_VOLUME:
	if (pReplyData != NULL) {
	    int32_t *replyData = (int32_t *) pReplyData;
	    for (uint32_t i = 0; i < *replySize / 4; i ++) {
		replyData[i] = 1 << 24;
	    }
	}
	break;
    }

    return 0;
}
