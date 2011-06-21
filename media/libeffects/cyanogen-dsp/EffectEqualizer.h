#pragma once

#include <media/EffectEqualizerApi.h>

#include "Biquad.h"
#include "Effect.h"

#define CUSTOM_EQ_PARAM_LOUDNESS_CORRECTION 1000

class EffectEqualizer : public Effect {
    private:
    /* Equalizer */
    float mBand[5];

    int64_t mGain;
    Biquad mFilterL[4], mFilterR[4];

    /* Automatic equalizer */
    float mLoudnessAdjustment;

    Biquad mWeigher;
    float mLoudness;
    int32_t mNextUpdate;
    int32_t mNextUpdateInterval;
    int64_t mPowerSquared;

    void setBand(int32_t idx, float dB);   
    float getAdjustedBand(int32_t idx);
    void refreshBands();

    public:
    EffectEqualizer();
    int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
    int32_t process_effect(audio_buffer_t *in, audio_buffer_t *out);
};
