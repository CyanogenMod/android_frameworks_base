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

#define LOG_TAG "Effect-DRC"

#include <cutils/log.h>
#include "EffectCompression.h"

#include <math.h>

typedef struct {
        effect_param_t ep;
        uint32_t code;
        uint16_t value;
} cmd1x4_1x2_t;

static int32_t max(int32_t a, int32_t b)
{
    return a > b ? a : b;
}

EffectCompression::EffectCompression()
    : mCompressionRatio(2.0)
{
    for (int32_t i = 0; i < 2; i ++) {
	mCurrentLevel[i] = 0;
	mUserVolumes[i] = 1 << 24;
    }
}

int32_t EffectCompression::command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData)
{
    if (cmdCode == EFFECT_CMD_CONFIGURE) {
	int32_t *replyData = (int32_t *) pReplyData;
	int32_t ret = Effect::configure(pCmdData);
	if (ret != 0) {
	    *replyData = ret;
	    return 0;
	}

        /* Together, these two filters realize a reasonably good
         * approximation of the ITU-R 468 equal loudness contour for
         * sampling rates of 44100 and 48000. The 10000 Hz filter
         * must be moved lower for higher sampling rates. */
	mWeigherLP[0].setLowPass(10000, mSamplingRate, 0.71);
	mWeigherLP[1].setLowPass(10000, mSamplingRate, 0.71);
	mWeigherBP[0].setBandPass(6300, mSamplingRate, 0.6);
	mWeigherBP[1].setBandPass(6300, mSamplingRate, 0.6);
        /* Gain factor of 17 dB is applied when converting the results to dB */

	*replyData = 0;
	return 0;
    }	

    if (cmdCode == EFFECT_CMD_SET_PARAM) {
        effect_param_t *cep = (effect_param_t *) pCmdData;
        if (cep->psize == 6 && *replySize == 4) {
	    int32_t *replyData = (int32_t *) pReplyData;
            cmd1x4_1x2_t *strength = (cmd1x4_1x2_t *) pCmdData;
            if (strength->code == 0) {
                /* 1.0 .. 11.0 */
                mCompressionRatio = 1.f + strength->value / 100.f;
                LOGI("Compression factor set to: %f", mCompressionRatio);
                *replyData = 0;
                return 0;
            }
        }

        LOGI("Unrecognized EFFECT_CMD_SET_PARAM: %d in, %d out requested", cmdSize, *replySize);
        return -1;
    }

    if (cmdCode == EFFECT_CMD_SET_VOLUME) {
	LOGI("Setting volumes");

	if (pReplyData != NULL) {
	    int32_t *userVols = (int32_t *) pCmdData;
	    for (uint32_t i = 0; i < cmdSize / 4; i ++) {
                LOGI("user volume on channel %d: %d", i, userVols[i]);
		mUserVolumes[i] = userVols[i];
	    }

	    int32_t *myVols = (int32_t *) pReplyData;
	    for (uint32_t i = 0; i < *replySize / 4; i ++) {
                LOGI("Returning unity for our pre-requested volume on channel %d", i);
		myVols[i] = 1 << 24; /* Unity gain */
	    }
        } else {
	    /* We don't control volume. */
	    for (int32_t i = 0; i < 2; i ++) {
		mUserVolumes[i] = 1 << 24;
	    }
	}

	return 0;
    }

    return Effect::command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

/* Return fixed point 16.48 */
uint64_t EffectCompression::estimateOneChannelLevel(audio_buffer_t *in, int32_t interleave, int32_t offset, Biquad& weigherLP, Biquad& weigherBP)
{
    uint64_t power = 0;
    for (uint32_t i = 0; i < in->frameCount; i ++) {
	int32_t tmp = read(in, offset);
        tmp = weigherLP.process(tmp);
        tmp = weigherBP.process(tmp);

	/* 2^24 * 2^24 = 48 */
        power += int64_t(tmp) * int64_t(tmp);
	offset += interleave;
    }

    return (power / in->frameCount);
}

int32_t EffectCompression::process_effect(audio_buffer_t *in, audio_buffer_t *out)
{
    /* Analyze both channels separately, pick the maximum power measured. */
    uint64_t maximumPowerSquared = 0;
    for (uint32_t i = 0; i < mChannels; i ++) {
        uint64_t candidatePowerSquared = estimateOneChannelLevel(in, mChannels, i, mWeigherLP[i], mWeigherBP[i]);
        if (candidatePowerSquared > maximumPowerSquared) {
            maximumPowerSquared = candidatePowerSquared;
        }
    }

    /* -100 .. 0 dB. */
    float signalPowerDb = logf(maximumPowerSquared / float(int64_t(1) << 48) + 1e-10f) / logf(10.0f) * 10.0f;

    /* Target 83 dB SPL */
    signalPowerDb += 96.0f - 83.0f + 17.0f;

    /* now we have an estimate of the signal power, with 0 level around 83 dB.
     * we now select the level to boost to. */
    float desiredLevelDb = signalPowerDb / mCompressionRatio;

    /* turn back to multiplier */
    float correctionDb = desiredLevelDb - signalPowerDb;
    
    /* Reduce extreme boost by a smooth ramp.
     * New range -50 .. 0 dB */
    correctionDb -= powf(correctionDb/100, 2.0f) * (100.0f / 2.0f);

    /* 40.24 */
    int64_t correctionFactor = (1 << 24) * powf(10.0f, correctionDb / 20.0f);

    /* Now we have correction factor and user-desired sound level. */
    for (uint32_t i = 0; i < mChannels; i ++) {
	 /* 8.24 */
	int32_t desiredLevel = mUserVolumes[i] * correctionFactor >> 24;

        /* 8.24 */
	int32_t volAdj = desiredLevel - mCurrentLevel[i];
	
	/* I want volume adjustments to occur in about 0.025 seconds. 
	 * However, if the input buffer would happen to be longer than
	 * this, I'll just make sure that I am done with the adjustment
	 * by the end of it. */
	int32_t adjLen = mSamplingRate / 40; // in practice, about 1100 frames
        /* This formulation results in piecewise linear approximation of
         * exponential because the rate of adjustment decreases from granule
         * to granule. */
	volAdj /= max(adjLen, in->frameCount);

	/* Additionally, I want volume to increase only very slowly.
	 * This biases us against pumping effects and also tends to spare
	 * our ears when some very loud sound begins suddenly. */
	if (volAdj > 0) {
	    volAdj >>= 4;
	}

	for (uint32_t j = 0; j < in->frameCount; j ++) {
	     int32_t value = read(in, j * mChannels + i);
	     value = int64_t(value) * mCurrentLevel[i] >> 24;
	     write(out, j * mChannels + i, value);
	     mCurrentLevel[i] += volAdj;
	}
    }

    return 0;
}
