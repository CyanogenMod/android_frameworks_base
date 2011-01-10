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

#define LOG_TAG "Effect-Equalizer"

#include <cutils/log.h>
#include "EffectEqualizer.h"

#include <math.h>

typedef struct {
	int32_t status;
	uint32_t psize;
	uint32_t vsize;
	int32_t cmd;
	int16_t data;
} reply1x4_1x2_t;

typedef struct {
	int32_t status;
	uint32_t psize;
	uint32_t vsize;
	int32_t cmd;
	int16_t data1;
	int16_t data2;
} reply1x4_2x2_t;

typedef struct {
	int32_t status;
	uint32_t psize;
	uint32_t vsize;
	int32_t cmd;
	int32_t arg;
	int16_t data;
} reply2x4_1x2_t;

static int64_t toFixedPoint(float in) {
    return (int64_t) (0.5 + in * ((int64_t) 1 << 32));
}

EffectEqualizer::EffectEqualizer()
{
    for (int32_t i = 0; i < 5; i ++) {
        mBand[i] = 0;
    }
    refreshBands();
}

int32_t EffectEqualizer::command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData)
{
	if (cmdCode == EFFECT_CMD_CONFIGURE) {
		LOGI("EFFECT_CMD_CONFIGURE");
		int32_t ret = Effect::configure(pCmdData);
		if (ret != 0) {
			LOGE("EFFECT_CMD_CONFIGURE failed");
			int32_t *replyData = (int32_t *) pReplyData;
			*replyData = ret;
			return 0;
		}

		refreshBands();

		int32_t *replyData = (int32_t *) pReplyData;
		*replyData = 0;
		LOGI("EFFECT_CMD_CONFIGURE OK");
		return 0;
    }

    if (cmdCode == EFFECT_CMD_GET_PARAM) {
		effect_param_t *cep = (effect_param_t *) pCmdData;
		LOGI("EFFECT_CMD_GET_PARAM + %d bytes of param", cep->psize);
		if (cep->psize == 4) {
			int cmd = ((int *) cep)[3];
			if (cmd == EQ_PARAM_NUM_BANDS) {
				LOGI("Requested param: EQ_PARAM_NUM_BANDS");
				reply1x4_1x2_t *replyData = (reply1x4_1x2_t *) pReplyData;
				replyData->status = 0;
				replyData->vsize = 2;
				replyData->data = 5;
				*replySize = sizeof(reply1x4_1x2_t);
				LOGI("EQ_PARAM_NUM_BANDS OK");
				return 0;
			}
			if (cmd == EQ_PARAM_LEVEL_RANGE) {
				LOGI("Requested param: EQ_PARAM_LEVEL_RANGE");
				reply1x4_2x2_t *replyData = (reply1x4_2x2_t *) pReplyData;
				replyData->status = 0;
				replyData->vsize = 4;
				replyData->data1 = -1000;
				replyData->data2 = 1000;
				*replySize = sizeof(reply1x4_2x2_t);
				LOGI("EQ_PARAM_LEVEL_RANGE OK");
				return 0;
			}
			if (cmd == EQ_PARAM_GET_NUM_OF_PRESETS) {
				LOGI("Requested param: EQ_PARAM_GET_NUM_OF_PRESETS");
				reply1x4_1x2_t *replyData = (reply1x4_1x2_t *) pReplyData;
				replyData->status = 0;
				replyData->vsize = 2;
				replyData->data = 0;
				*replySize = sizeof(reply1x4_1x2_t);
				LOGI("EQ_PARAM_GET_NUM_OF_PRESETS OK");
				return 0;
			}
		} else if (cep->psize == 8) {
			int cmd = ((int *) cep)[3];
			int arg = ((int *) cep)[4];
			LOGI("Requested param: %d, %d", cmd, arg);
			if (cmd == EQ_PARAM_BAND_LEVEL && arg >= 0 && arg < 5) {
				reply2x4_1x2_t *replyData = (reply2x4_1x2_t *) pReplyData;
				replyData->status = 0;
				replyData->vsize = 2;
				replyData->data = int16_t(mBand[arg] * 100 + 0.5f);
				*replySize = sizeof(reply2x4_1x2_t);
				return 0;
			}
			if (cmd == EQ_PARAM_CENTER_FREQ && arg >= 0 && arg < 5) {
				float centerFrequency = 62.5f * powf(4, arg);
				reply2x4_1x2_t *replyData = (reply2x4_1x2_t *) pReplyData;
				replyData->status = 0;
				replyData->vsize = 2;
				replyData->data = centerFrequency;
				*replySize = sizeof(reply2x4_1x2_t);
				return 0;
			}
		}

		/* Didn't support this command. We'll just set error status. */
		LOGE("Unknown EFFECT_CMD_GET_PARAM size %d, returning empty value.", cep->psize);
		effect_param_t *replyData = (effect_param_t *) pReplyData;
		replyData->status = -EINVAL;
		replyData->vsize = 0;
		*replySize = sizeof(effect_param_t);
		return 0;
	}

	if (cmdCode == EFFECT_CMD_SET_PARAM) {
		effect_param_t *cep = (effect_param_t *) pCmdData;
		LOGI("EFFECT_CMD_SET_PARAM, %d", cep->psize);
		int32_t *replyData = (int32_t *) pReplyData;

		if (cep->psize == 8) {
			int32_t cmd = ((int32_t *) cep)[3];
			int32_t arg = ((int32_t *) cep)[4];

			if (cmd == EQ_PARAM_BAND_LEVEL && arg >= 0 && arg < 5) {
				LOGI("Setting band %d to %d", cmd, arg);
				*replyData = 0;
				int16_t value = ((int16_t *) cep)[10];
				mBand[arg] = value / 100.0f;
				refreshBands();
				return 0;
			}
		}

		LOGE("Unknown EFFECT_CMD_SET_PARAM size %d, returning empty value.", cep->psize);
		*replyData = -EINVAL;
		return 0;
	}

     return Effect::command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

void EffectEqualizer::refreshBands()
{
    mGain = toFixedPoint(powf(10.0f, mBand[0] / 20.0f));
    for (int band = 0; band < 4; band ++) {
        float centerFrequency = 62.5f * powf(4, band);
        float dB = mBand[band+1] - mBand[band];

        mFilterL[band].setHighShelf(centerFrequency * 2.0f, mSamplingRate, dB, 1.0f);
        mFilterR[band].setHighShelf(centerFrequency * 2.0f, mSamplingRate, dB, 1.0f);
    }
}

int32_t EffectEqualizer::process_effect(audio_buffer_t *in, audio_buffer_t *out)
{
    for (uint32_t i = 0; i < in->frameCount; i ++) {
        int32_t tmpL = read(in, i * 2);
        int32_t tmpR = read(in, i * 2 + 1);
     
        /* first "shelve" is just gain */ 
        tmpL = tmpL * mGain >> 32;
        tmpR = tmpR * mGain >> 32;
 
        /* evaluate the other filters. */
        for (int32_t j = 0; j < 4; j ++) {
            tmpL = mFilterL[j].process(tmpL);
            tmpR = mFilterR[j].process(tmpR);
        }

        write(out, i * 2, tmpL);
        write(out, i * 2 + 1, tmpR);
    }

    return 0;
}

