#pragma once

#include "Biquad.h"
#include "Effect.h"

class EffectCompression : public Effect {
    private:
    int32_t mUserVolumes[32];
    float mCompressionRatio;
    
    int32_t mCurrentLevel[2];

    Biquad mWeigherLP[2];
    Biquad mWeigherBP[2];

    uint64_t estimateOneChannelLevel(audio_buffer_t *in, int32_t interleave, int32_t offset, Biquad& weigherLP, Biquad& WeigherBP);

    public:
    EffectCompression();
    int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData);
    int32_t process_effect(audio_buffer_t *in, audio_buffer_t *out);
};
