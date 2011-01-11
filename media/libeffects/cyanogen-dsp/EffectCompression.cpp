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

static int32_t max(int32_t a, int32_t b)
{
    return a > b ? a : b;
}

EffectCompression::EffectCompression()
    : mCompressionRatio(2.0)
{
    for (int i = 0; i < 2; i ++) {
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

	mWeighter.setBandPass(1700, mSamplingRate, sqrtf(2)/2);

	*replyData = 0;
	return 0;
    }	

    if (cmdCode == EFFECT_CMD_SET_VOLUME) {
	LOGI("Setting volumes");
	int32_t ret = Effect::configure(pCmdData);
	if (ret != 0) {
	    return ret;
	}
    
	if (pReplyData != NULL) {
	    int32_t *userVols = (int *) pCmdData;
	    for (uint32_t i = 0; i < cmdSize / 4; i ++) {
		 mUserVolumes[i] = userVols[i];
	    }

	    int32_t *myVols = (int *) pReplyData;
	    for (uint32_t i = 0; i < *replySize / 4; i ++) {
		myVols[i] = 1 << 24; /* Unity gain */
	    }
        } else {
	    /* We don't control volume. */
	    for (int i = 0; i < 2; i ++) {
		mUserVolumes[i] = 1 << 24;
	    }
	}

	return 0;
    }

    return Effect::command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

/* Return fixed point 16.48 */
uint64_t EffectCompression::estimateOneChannelLevel(audio_buffer_t *in, int32_t interleave, int32_t offset)
{
    mWeighter.reset();
    uint64_t power = 0;
    for (uint32_t i = 0; i < in->frameCount; i ++) {
	int32_t tmp = read(in, offset);
	offset += interleave;
        int64_t out = mWeighter.process(tmp);
	/* 2^24 * 2^24 = 48 */
        power += out * out;
    }

    return (power / in->frameCount);
}

int32_t EffectCompression::process_effect(audio_buffer_t *in, audio_buffer_t *out)
{
    /* Analyze both channels separately, pick the maximum power measured. */
    uint64_t maximumPowerSquared = 0;
    for (uint32_t i = 0; i < mChannels; i ++) {
        uint64_t candidatePowerSquared = estimateOneChannelLevel(in, mChannels, i);
        if (candidatePowerSquared > maximumPowerSquared) {
            maximumPowerSquared = candidatePowerSquared;
        }
    }

    /* -100 .. 0 dB. */
    float signalPowerDb = logf(maximumPowerSquared / float(int64_t(1) << 48) + 1e-10f) / logf(10.0f) * 10.0f;
    LOGI("Measured power: %f", signalPowerDb);

    /* target 83 dB SPL, and add 6 dB to compensate for the weighter, whose
     * peak is at -3 dB. */
    signalPowerDb += 96.0f - 83.0f + 6.0f;

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
	/* channel map hack: we support only stereo,
	 * so we don't have to deal with full complexity for now.
	 * 8.24 */
	int32_t desiredLevel = mUserVolumes[i] * correctionFactor >> 24;

	int32_t volAdj = mCurrentLevel[i] - desiredLevel;
	
	/* I want volume adjustments to occur in about 0.1 seconds. 
	 * However, if the input buffer would happen to be longer than
	 * this, I'll just make sure that I am done with the adjustment
	 * by the end of it. */
	int adjLen = mSamplingRate / 10;
	/* Note: this adjustment should probably be piecewise linear
	 * approximation of an exponential to keep perceptibly linear
	 * correction rate. */
	volAdj /= max(adjLen, in->frameCount);

	/* Additionally, I want volume to increase only very slowly.
	 * This biases us against pumping effects and also tends to spare
	 * our ears when some very loud sound begins suddenly. */
	if (volAdj > 0) {
	    volAdj /= 8;
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
