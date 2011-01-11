#pragma once

#include <media/EffectVirtualizerApi.h>

#include "Biquad.h"
#include "Delay.h"
#include "Effect.h"

class EffectVirtualizer : public Effect {
    private:
    int16_t mStrength;

    bool mDeep, mWide;
    int64_t mLevel;

    Delay mReverbDelayL, mReverbDelayR;
    int64_t mDelayDataL, mDelayDataR;
    Biquad mLocalizationL, mLocalizationR;

    void refreshStrength();

    public:
    EffectVirtualizer();

    int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
    int32_t process_effect(audio_buffer_t *in, audio_buffer_t *out);
};
