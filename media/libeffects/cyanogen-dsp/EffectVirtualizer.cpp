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
	int32_t ret = Effect::configure(pCmdData);
	if (ret != 0) {
	    int32_t *replyData = (int32_t *) pReplyData;
	    *replyData = ret;
	    return 0;
	}

	mReverbDelayL.setParameters(mSamplingRate, 0.030f);
	mReverbDelayR.setParameters(mSamplingRate, 0.030f);
	/* the -3 dB point is around 650 Hz, giving about 300 us to work with */
	mLocalizationL.setHighShelf(800.0f, mSamplingRate, -11.0f, 0.72f);
	mLocalizationR.setHighShelf(800.0f, mSamplingRate, -11.0f, 0.72f);
	/* Rockbox has a 0.3 ms delay line (13 samples at 44100 Hz), but
	 * I think it makes the whole effect sound pretty bad so I skipped it! */

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

        /* In matrix decoding, center channel is mixed at 0.7 and the main channel at 1.
         * It follows that the sum of them is 1.7, and the proportion of the main channel
         * must be 1 / 1.7, or about 6/10. Assuming it is so, 4/10 is the contribution
         * of center, and when 2 channels are combined, the scaler is 2/10 or 1/5.
         *
         * We could try to dynamically adjust this divisor based on cross-correlation
         * between left/right channels, which would allow us to recover a reasonable
         * estimate of the music's original center channel. */
        int32_t center = (dataL + dataR) / 5;
        int32_t directL = (dataL - center);
        int32_t directR = (dataR - center);

        /* We assume center channel reaches both ears with no coloration required.
         * We could also handle it differently at reverb stage... */

        /* Apply localization filter. */
        int32_t localizedL = mLocalizationL.process(directL);
        int32_t localizedR = mLocalizationR.process(directR);
        
        /* Mix difference between channels. dataX = directX + center. */
        write(out, i * 2, dataL + localizedR);
        write(out, i * 2 + 1, dataR + localizedL);
    }

    return mEnable ? 0 : -ENODATA;
}

