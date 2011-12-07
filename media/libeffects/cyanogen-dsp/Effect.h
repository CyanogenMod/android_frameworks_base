#pragma once

#include <stdint.h>
#include <system/audio.h>
#include <hardware/audio_effect.h>

static inline uint16_t prng() {
    static uint32_t seed;
    seed = seed * 1664525 + 1013904223;
    return seed >> 14;
}

class Effect {
    private:
    effect_buffer_access_e mAccessMode;

    protected:
    bool mEnable;
    float mSamplingRate;
    uint16_t mPreviousRandom;

    /* High-passed triangular probability density function. */
    inline int32_t triangularDither16() {
        uint16_t newRandom = prng();
        int32_t rnd = mPreviousRandom - newRandom;
        mPreviousRandom = newRandom;
        return rnd;
    }

    /* AudioFlinger only gives us 16-bit PCM for now. */
    inline int32_t read(audio_buffer_t *in, int32_t idx) {
        return in->s16[idx] << 8;
    }

    /* AudioFlinger only expects 16-bit PCM for now. */
    inline void write(audio_buffer_t *out, int32_t idx, int32_t sample) {
        if (mAccessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE) {
            sample += out->s16[idx] << 8;
        }
        out->s16[idx] = (sample + (triangularDither16() >> 8)) >> 8;
    }

    int32_t configure(void *pCmdData);

    public:
    Effect();
    virtual ~Effect();
    virtual int32_t process(audio_buffer_t *in, audio_buffer_t *out) = 0;
    virtual int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData) = 0;
};
