
#include "AudioCodec.h"

extern "C" {
#include <speex/speex.h>
}

namespace {

class SpeexCodec : public AudioCodec
{
public:
    SpeexCodec() {
		speex_bits_init(&ebits);
		speex_bits_init(&dbits);
		enc_state = speex_encoder_init(&speex_nb_mode);
		dec_state = speex_decoder_init(&speex_nb_mode);
    }

    ~SpeexCodec() {
		speex_bits_destroy(&ebits);
		speex_bits_destroy(&dbits);
		speex_decoder_destroy(dec_state); 
		speex_encoder_destroy(enc_state); 
    }

    int set(int sampleRate, const char *fmtp) {
		int compression = 3;
		
		speex_encoder_ctl(enc_state, SPEEX_SET_QUALITY, &compression);
		speex_encoder_ctl(enc_state, SPEEX_SET_SAMPLING_RATE, &sampleRate);
		speex_decoder_ctl(dec_state, SPEEX_SET_SAMPLING_RATE, &sampleRate);
		sampleCount = sampleRate * 20 / 1000;
		return sampleCount;
    }

    int encode(void *payload, int16_t *samples);
    int decode(int16_t *samples, void *payload, int length);

private:
    SpeexBits ebits, dbits;
	void *enc_state;
	void *dec_state;
	int sampleCount;
};

int SpeexCodec::encode(void *payload, int16_t *samples)
{
	int encoded_count;
	speex_bits_reset(&ebits);
	speex_encode_int(enc_state, samples, &ebits);
	encoded_count = speex_bits_write(&ebits, (char *)payload, sampleCount);
	return encoded_count;
}

int SpeexCodec::decode(int16_t *samples, void *payload, int length)
{
	speex_bits_read_from(&dbits, (char *)payload, length);
	speex_decode_int(dec_state, &dbits, samples);
	return sampleCount;
}
} // namespace

AudioCodec *newSpeexCodec()
{
    return new SpeexCodec;
}