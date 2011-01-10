#pragma once

#include <stdint.h>
#include <media/EffectApi.h>

class Effect {
    private:
    audio_format_e mFormatIn;
    audio_format_e mFormatOut;
    effect_buffer_access_e mAccessMode;

    protected:
    bool enable;
    float mSamplingRate;
    uint32_t mChannels;
   
    inline int32_t read(audio_buffer_t *in, int32_t idx) {
	switch (mFormatIn) {
	case SAMPLE_FORMAT_PCM_S15:
	    return in->s16[idx] << 8;

	case SAMPLE_FORMAT_PCM_U8:
	    return (in->u8[idx] - 128) << 16;

	case SAMPLE_FORMAT_PCM_S7_24:
	    return in->s32[idx];

	default:
            /* Uh, what to do ... */
	    return 0;
	}
    }

    inline void write(audio_buffer_t *out, int32_t idx, int32_t sample) {
        if (mAccessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE) {
	    switch (mFormatOut) {
	    case SAMPLE_FORMAT_PCM_S15:
	        sample += out->s16[idx] << 8;
		break;

	    case SAMPLE_FORMAT_PCM_U8:
	        sample += (out->u8[idx] - 128) << 16;
		break;

	    case SAMPLE_FORMAT_PCM_S7_24:
	        sample += out->s32[idx];
		break;

            default:
		break;
	    }
	}

	/* I should probably apply dithering for S15 / U8. */
	switch (mFormatOut) {
	case SAMPLE_FORMAT_PCM_S15:
	    sample >>= 8;
	    if (sample > 32767) {
		sample = 32767;
	    }
	    if (sample < -32768) {
		sample = -32768;
	    }
	    out->s16[idx] = sample;
	    break;

	case SAMPLE_FORMAT_PCM_U8:
	    sample >>= 16;
	    sample += 128;
	    if (sample > 255) {
		sample = 255;
	    }
	    if (sample < 0) {
		sample = 0;
	    }
	    out->u8[idx] = sample;
	    break;

	case SAMPLE_FORMAT_PCM_S7_24:
	    out->s32[idx] = sample;
	    break;

        default:
	    /* Uh, what to do ... */
	    break;
	}
    }
 
    int32_t configure(void *pCmdData);

    public:
    Effect();
    virtual ~Effect();
    int32_t process(audio_buffer_t *in, audio_buffer_t *out);
    virtual int32_t command(uint32_t cmdCode, uint32_t cmdSize, void* pCmdData, uint32_t* replySize, void* pReplyData) = 0;
    virtual int32_t process_effect(audio_buffer_t *in, audio_buffer_t *out) = 0;
};
