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

#define LOG_TAG "Effect-BassBoost"

#include <cutils/log.h>
#include "EffectBassBoost.h"

typedef struct {
	int32_t status;
	uint32_t psize;
	uint32_t vsize;
	int32_t cmd;
	int16_t data;
} reply1x4_1x2_t;

EffectBassBoost::EffectBassBoost()
    : mStrength(0)
{
    refreshStrength();
}

int32_t EffectBassBoost::command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData)
{
    if (cmdCode == EFFECT_CMD_CONFIGURE) {
	int32_t ret = Effect::configure(pCmdData);
	if (ret != 0) {
	    int32_t *replyData = (int32_t *) pReplyData;
	    *replyData = ret;
	    return 0;
	}

	int32_t *replyData = (int32_t *) pReplyData;
	*replyData = 0;
	return 0;
    }

    if (cmdCode == EFFECT_CMD_GET_PARAM) {
	effect_param_t *cep = (effect_param_t *) pCmdData;
        if (cep->psize == 4) {
	    int32_t cmd = ((int32_t *) cep)[3];
	    if (cmd == BASSBOOST_PARAM_STRENGTH_SUPPORTED) {
		reply1x4_1x2_t *replyData = (reply1x4_1x2_t *) pReplyData;
		replyData->status = 0;
		replyData->vsize = 2;
		replyData->data = 1;
		*replySize = sizeof(reply1x4_1x2_t);
		return 0;
	    }
	    if (cmd == BASSBOOST_PARAM_STRENGTH) {
		reply1x4_1x2_t *replyData = (reply1x4_1x2_t *) pReplyData;
		replyData->status = 0;
		replyData->vsize = 2;
		replyData->data = mStrength;
		*replySize = sizeof(reply1x4_1x2_t);
		return 0;
	    }
        }

	effect_param_t *replyData = (effect_param_t *) pReplyData;
	replyData->status = -EINVAL;
	replyData->vsize = 0;
	*replySize = sizeof(effect_param_t);
	return 0;
    }

    if (cmdCode == EFFECT_CMD_SET_PARAM) {
	effect_param_t *cep = (effect_param_t *) pCmdData;
	if (cep->psize == 4) {
	    int32_t cmd = ((int32_t *) cep)[3];
	    if (cmd == BASSBOOST_PARAM_STRENGTH) {
		mStrength = ((int16_t *) cep)[8];
		LOGI("New strength: %d", mStrength);
		refreshStrength();
		int32_t *replyData = (int32_t *) pReplyData;
		*replyData = 0;
		return 0;
	    }
	}

	int32_t *replyData = (int32_t *) pReplyData;
	*replyData = -EINVAL;
	return 0;
    }

    return Effect::command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

void EffectBassBoost::refreshStrength()
{
    /* Q = 0.5 .. 2.0 */
    mBoost.setLowPass(55.0f, mSamplingRate, 0.5f + mStrength / 666.0f);
}

int32_t EffectBassBoost::process(audio_buffer_t* in, audio_buffer_t* out)
{
    for (uint32_t i = 0; i < in->frameCount; i ++) {
        int32_t dryL = read(in, i * 2);
        int32_t dryR = read(in, i * 2 + 1);

	/* Original LVM effect was far more involved than this one.
	 * This effect is mostly a placeholder until I port that, or
	 * something else. LVM process diagram was as follows:
	 *
         * in -> [ HPF ] -+-> [ mono mix ] -> [ BPF ] -> [ compressor ] -> out
	 *                `-->------------------------------>--'
         *
	 * High-pass filter was optional, and seemed to be
         * tuned at 55 Hz and upwards. BPF is probably always tuned
	 * at the same frequency, as this would make sense.
	 *
	 * Additionally, a compressor element was used to limit the
	 * mixing of the boost (only!) to avoid clipping.
         */
	int32_t boost = mBoost.process(dryL + dryR);

        write(out, i * 2, dryL + boost);
        write(out, i * 2 + 1, dryR + boost);
    }

    return mEnable ? 0 : -ENODATA;
}

