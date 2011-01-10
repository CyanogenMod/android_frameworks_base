#pragma once

#include <media/EffectEqualizerApi.h>

#include "Biquad.h"
#include "Effect.h"

class EffectEqualizer : public Effect {
    private:
    float mBand[5];
    int64_t mGain;
    Biquad mFilterL[4], mFilterR[4];

    void setBand(int32_t idx, float dB);   
    void refreshBands();

    public:
    EffectEqualizer();
    int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
    int32_t process_effect(audio_buffer_t *in, audio_buffer_t *out);
};
