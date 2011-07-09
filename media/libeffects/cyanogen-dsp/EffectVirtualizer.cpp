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

#define LOG_TAG "Effect-Virtualizer"

#include <cutils/log.h>
#include <math.h>

#include "EffectVirtualizer.h"

typedef struct {
	int32_t status;
	uint32_t psize;
	uint32_t vsize;
	int32_t cmd;
	int16_t data;
} reply1x4_1x2_t;

EffectVirtualizer::EffectVirtualizer()
    : mStrength(0)
{
    refreshStrength();
}

int32_t EffectVirtualizer::command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData)
{
    if (cmdCode == EFFECT_CMD_CONFIGURE) {
	/* Preliminary design -- needs more work.
	 * NOT ACTUALLY USED. */
	float fir44100[16] = {
		0.584375000000000,
		0.080172281918782,
		0.049463834764832,
		-0.015690873571518,
		-0.065625000000000,
		-0.045552813136128,
		0.031786165235168,
		0.106071404788864,
		0.134375000000000,
		0.106071404788864,
		0.031786165235168,
		-0.045552813136128,
		-0.065625000000000,
		-0.015690873571518,
		0.049463834764832,
		0.080172281918782
	};

	int32_t ret = Effect::configure(pCmdData);
	if (ret != 0) {
	    int32_t *replyData = (int32_t *) pReplyData;
	    *replyData = ret;
	    return 0;
	}

	/* Haas effect delay */
	mReverbDelayL.setParameters(mSamplingRate, 0.025f);
	mReverbDelayR.setParameters(mSamplingRate, 0.025f);
	/* Center channel forward direction adjustment filter. */
	mColorization.setParameters(fir44100);
	/* the -3 dB point is around 650 Hz, giving about 300 us to work with */
	mLocalization.setHighShelf(800.0f, mSamplingRate, -11.0f, 0.72f);

	mDelayDataL = 0;
	mDelayDataR = 0;

	int32_t *replyData = (int32_t *) pReplyData;
	*replyData = 0;
	return 0;
    }

    if (cmdCode == EFFECT_CMD_GET_PARAM) {
	effect_param_t *cep = (effect_param_t *) pCmdData;
        if (cep->psize == 4) {
	    int32_t cmd = ((int32_t *) cep)[3];
	    if (cmd == VIRTUALIZER_PARAM_STRENGTH_SUPPORTED) {
		reply1x4_1x2_t *replyData = (reply1x4_1x2_t *) pReplyData;
		replyData->status = 0;
		replyData->vsize = 2;
		replyData->data = 1;
		*replySize = sizeof(reply1x4_1x2_t);
		return 0;
	    }
	    if (cmd == VIRTUALIZER_PARAM_STRENGTH) {
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
	    if (cmd == VIRTUALIZER_PARAM_STRENGTH) {
		mStrength = ((int16_t *) cep)[8];
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

void EffectVirtualizer::refreshStrength()
{
    mDeep = mStrength != 0;
    mWide = mStrength >= 500;

    /* -15 .. -5 dB */
    float roomEcho = powf(10.0f, (mStrength / 100.0f - 15.0f) / 20.0f);
    mLevel = int64_t(roomEcho * (int64_t(1) << 32));
}

int32_t EffectVirtualizer::process(audio_buffer_t* in, audio_buffer_t* out)
{
    for (uint32_t i = 0; i < in->frameCount; i ++) {
        /* calculate reverb wet into dataL, dataR */
        int32_t dryL = read(in, i * 2);
        int32_t dryR = read(in, i * 2 + 1);
        int32_t dataL = dryL;
        int32_t dataR = dryR;
        
        if (mDeep) {
            /* Note: a pinking filter here would be good. */
            dataL += mDelayDataR;
            dataR += mDelayDataL;
        }

        dataL = mReverbDelayL.process(dataL);
        dataR = mReverbDelayR.process(dataR);

        if (mWide) {
            dataR = -dataR;
        }

        dataL = dataL * mLevel >> 32;
        dataR = dataR * mLevel >> 32;

        mDelayDataL = dataL;
        mDelayDataR = dataR;

        /* Reverb wet done; mix with dry and do headphone virtualization */
        dataL += dryL;
        dataR += dryR;

	/* Center channel. */
	int32_t center  = (dataL + dataR) >> 1;
	/* Direct radiation components. */
	int32_t side = (dataL - dataR) >> 1;

	/* Adjust derived center channel coloration to emphasize forward
	 * direction impression. (XXX: disabled until configurable). */
	//center = mColorization.process(center);
	/* Sound reaching ear from the opposite speaker */
	side -= mLocalization.process(side);
        
        write(out, i * 2, center + side);
        write(out, i * 2 + 1, center - side);
    }

    return mEnable ? 0 : -ENODATA;
}

