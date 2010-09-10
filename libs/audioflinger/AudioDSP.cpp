/* //device/include/server/AudioFlinger/AudioDSP.cpp
**
** Copyright 2010, Antti S. Lankila
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <math.h>
#include <stdio.h>

#include "AudioDSP.h"

namespace android {

#if defined(__ARM_HAVE_VFP)
inline static sample_t toFixedPoint(float x) {
    return x;
}
inline static sample_t multiply(sample_t a, sample_t b) {
    return a * b;
}
#else
inline static sample_t toFixedPoint(float x) {
    return sample_t(x * (1 << 12) + 0.5f);
}
inline static sample_t multiply(sample_t a, sample_t b) {
    return (a >> 12) * b;
}
#endif

/***************************************************************************
 * Delay                                                                   *
 ***************************************************************************/
Delay::Delay()
    : mState(0), mIndex(0), mLength(0)
{
}

Delay::~Delay()
{
    if (mState != 0) {
        delete[] mState;
        mState = 0;
    }
}

void Delay::setParameters(float samplingFrequency, float time)
{
    mLength = int32_t(time * samplingFrequency + 0.5f);
    if (mState != 0) {
        delete[] mState;
    }
    mState = new sample_t[mLength];
    memset(mState, 0, mLength * sizeof(sample_t));
    mIndex = 0;
}

inline sample_t Delay::process(sample_t x0)
{
    sample_t y0 = mState[mIndex];
    mState[mIndex] = x0;
    mIndex = (mIndex + 1) % mLength;
    return y0;
}


/***************************************************************************
 * Allpass                                                                 *
 ***************************************************************************/
Allpass::Allpass()
    : mK(0), mState(0), mIndex(0), mLength(0)
{
}

Allpass::~Allpass()
{
    if (mState != 0) {
        delete[] mState;
        mState = 0;
    }
}

void Allpass::setParameters(float samplingFrequency, float k, float time)
{
    mK = toFixedPoint(k);
    mLength = int32_t(time * samplingFrequency + 0.5f);
    if (mState != 0) {
        delete[] mState;
    }
    mState = new sample_t[mLength];
    memset(mState, 0, mLength * sizeof(sample_t));
    mIndex = 0;
}

inline sample_t Allpass::process(sample_t x0)
{
    sample_t tmp = x0 - multiply(mState[mIndex], mK);
    sample_t y0 = mState[mIndex] + multiply(tmp, mK);
    mState[mIndex] = tmp;
    mIndex = (mIndex + 1) % mLength;
    return y0;
}


/***************************************************************************
 * Biquad                                                                  *
 ***************************************************************************/
BiquadBase::~BiquadBase()
{
}

BiquadInt::BiquadInt()
    : mY0(0)
{
    state.i32.mX = 0;
    state.i32.mY = 0;
}

BiquadFloat::BiquadFloat()
    : mX1(0), mX2(0), mY0(0), mY1(0), mY2(0)
{
}

void BiquadInt::setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2)
{
    state.i16.mA1 = -toFixedPoint(a1/a0);
    state.i16.mA2 = -toFixedPoint(a2/a0);
    mB0 = toFixedPoint(b0/a0);
    state.i16.mB1 = toFixedPoint(b1/a0);
    state.i16.mB2 = toFixedPoint(b2/a0);
}

void BiquadFloat::setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2)
{
    mA1 = -a1/a0;
    mA2 = -a2/a0;
    mB0 = b0/a0;
    mB1 = b1/a0;
    mB2 = b2/a0;
}

void BiquadInt::reset()
{
    mY0 = 0;
    state.i32.mX = 0;
    state.i32.mY = 0;
}

void BiquadFloat::reset()
{
    mX1 = mX2 = 0;
    mY0 = mY1 = mY2 = 0;
}

void BiquadBase::setRC(float center_frequency, float sampling_frequency)
{
    float DT_div_RC = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float b0 = DT_div_RC / (1 + DT_div_RC);
    float a1 = -1 + b0;

    setCoefficients(1, a1, 0, b0, 0, 0);
}

/*
 * Peaking equalizer, low shelf and high shelf are taken from
 * the good old Audio EQ Cookbook by Robert Bristow-Johnson.
 */
void BiquadBase::setPeakingEqualizer(float center_frequency, float sampling_frequency, float db_gain, float bandwidth)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);

    float alpha = sinf(w0)/2 * sinhf( logf(2)/2 * bandwidth * w0/sinf(w0) );
    float b0 =   1 + alpha*A;
    float b1 =  -2*cosf(w0);
    float b2 =   1 - alpha*A;
    float a0 =   1 + alpha/A;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha/A;
    
    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void BiquadBase::setLowShelf(float center_frequency, float sampling_frequency, float db_gain, float slope)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 =  2*A*( (A-1) - (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =   -2*( (A-1) + (A+1)*cosf(w0)                   );
    float a2 =        (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void BiquadBase::setHighShelf(float center_frequency, float sampling_frequency, float db_gain, float slope)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 = -2*A*( (A-1) + (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =    2*( (A-1) - (A+1)*cosf(w0)                   );
    float a2 =        (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void BiquadBase::setHighShelf1(float center_frequency, float sampling_frequency, float db_gain)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);

    float b0 = sinf(w0 / 2) + cosf(w0 / 2) * A;
    float b1 = sinf(w0 / 2) - cosf(w0 / 2) * A;
    float a0 = sinf(w0 / 2) + cosf(w0 / 2) / A;
    float a1 = sinf(w0 / 2) - cosf(w0 / 2) / A;

    setCoefficients(a0, a1, 0, b0, b1, 0);
}

void BiquadBase::setBandPass(float center_frequency, float sampling_frequency, float resonance)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float alpha = sinf(w0) / (2*resonance);

    float b0 =   sinf(w0)/2;
    float b1 =   0;
    float b2 =  -sinf(w0)/2;
    float a0 =   1 + alpha;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

inline int32_t BiquadInt::process(int32_t x0)
{
    x0 >>= 12;

    /* mY0 holds error from previous integer truncation. */
    int32_t y0 = mY0 + mB0 * x0;

#if defined(__arm__) && !defined(__thumb__)
    asm(
        "smlatt %[y0], %[i], %[j], %[y0]\n"
        "smlabb %[y0], %[i], %[j], %[y0]\n"
        "smlatt %[y0], %[k], %[l], %[y0]\n"
        "smlabb %[y0], %[k], %[l], %[y0]\n"
         : [y0]"+r"(y0)
         : [i]"r"(state.i32.mA), [j]"r"(state.i32.mY),
           [k]"r"(state.i32.mB), [l]"r"(state.i32.mX)
         : );

    /* GCC is going to issue loads for the state.i16, so I do it
     * like this because the state.i32 is already in registers.
     * ARM appears to have instructions that can handle these
     * bit manipulations well, such as "orr r0, r0, r1, lsl #16".
     */
    state.i32.mY = (state.i32.mY << 16) | ((y0 >> 12) & 0xffff);
    state.i32.mX = (state.i32.mX << 16) | (x0 & 0xffff);
#else
    y0 += state.i16.mB1 * state.i16.mX1
        + state.i16.mB2 * state.i16.mX2
        + state.i16.mY1 * state.i16.mA1
        + state.i16.mY2 * state.i16.mA2;

    state.i16.mY2 = state.i16.mY1;
    state.i16.mY1 = y0 >> 12;

    state.i16.mX2 = state.i16.mX1;
    state.i16.mX1 = x0;
#endif

    mY0 = y0 & 0xfff;
    return y0;
}

inline float BiquadFloat::process(float x0)
{
    float y0 = mB0 * x0 + mB1 * mX1 + mB2 * mX2;
    y0 += mA1 * mY1 + mA2 * mY2;

    mX2 = mX1;
    mX1 = x0;

    mY2 = mY1;
    mY1 = y0;

    return y0;
}

/***************************************************************************
 * Effect                                                                  *
 ***************************************************************************/
Effect::Effect()
{
    configure(44100);
}

Effect::~Effect() {
}

void Effect::configure(const float samplingFrequency) {
    mSamplingFrequency = samplingFrequency;
}


EffectCompressionBase::EffectCompressionBase()
    : mCompressionRatio(2.0)
{
}

EffectCompressionBase::~EffectCompressionBase()
{
}

void EffectCompressionInt::configure(const float samplingFrequency)
{
    Effect::configure(samplingFrequency);
    mWeighter.setBandPass(1700, samplingFrequency, sqrtf(2)/2);
}

void EffectCompressionFloat::configure(const float samplingFrequency)
{
    Effect::configure(samplingFrequency);
    mWeighter.setBandPass(1700, samplingFrequency, sqrtf(2)/2);
}

void EffectCompressionBase::setRatio(float compressionRatio)
{
    mCompressionRatio = compressionRatio;
}

void EffectCompressionBase::process(sample_t* inout, int32_t frames)
{
}

float EffectCompressionInt::estimateLevel(const int16_t* audioData, int32_t frames, int32_t samplesPerFrame)
{
    mWeighter.reset();
    uint32_t power = 0;
    uint32_t powerFraction = 0;
    for (int32_t i = 0; i < frames; i ++) {
        int32_t tmp = *audioData << 12;
        audioData += samplesPerFrame;

        int32_t out = mWeighter.process(tmp) >> 12;
        powerFraction += out * out;
        power += powerFraction >> 16;
        powerFraction &= 0xffff;
    }

    /* peak-to-peak is -32768 to 32767, but we are squared here. */
    return (65536.0f * power + powerFraction) / (32768.0f * 32768.0f) / frames;
}

float EffectCompressionFloat::estimateLevel(const int16_t* audioData, int32_t frames, int32_t samplesPerFrame)
{
    mWeighter.reset();
    float power = 0;
    for (int32_t i = 0; i < frames; i ++) {
        float tmp = *audioData;
        audioData += samplesPerFrame;

        float out = mWeighter.process(tmp);
        power += out * out;
    }

    /* peak-to-peak is -32768 to 32767, but we are squared here. */
    return power / (32768.0f * 32768.0f) / frames;
}


EffectTone::EffectTone()
{
    for (int32_t i = 0; i < 5; i ++) {
        setBand(i, 0);
    }
}

EffectTone::~EffectTone() {
}

void EffectTone::configure(const float samplingFrequency) {
    Effect::configure(samplingFrequency);
    for (int i = 0; i < 5; i ++) {
        mBand[i] = 0;
    }
    refreshBands();
}
 
void EffectTone::setBand(int32_t band, float dB)
{
    mBand[band] = dB;
    refreshBands();
}

void EffectTone::refreshBands()
{
    mGain = toFixedPoint(powf(10.0f, mBand[0] / 20.0f));
    for (int band = 0; band < 4; band ++) {
        float centerFrequency = 62.5f * powf(4, band);
        float dB = mBand[band+1] - mBand[band];

        mFilterL[band].setHighShelf(centerFrequency * 2.0f, mSamplingFrequency, dB, 1.0f);
        mFilterR[band].setHighShelf(centerFrequency * 2.0f, mSamplingFrequency, dB, 1.0f);
    }
}

void EffectTone::process(sample_t* inout, int32_t frames)
{
    for (int32_t i = 0; i < frames; i ++) {
        sample_t tmpL = inout[0];
        sample_t tmpR = inout[1];
     
        /* first "shelve" is just gain */ 
        tmpL = multiply(tmpL, mGain);
        tmpR = multiply(tmpR, mGain);
 
        /* evaluate the other filters. */
        for (int32_t j = 0; j < 4; j ++) {
            tmpL = mFilterL[j].process(tmpL);
            tmpR = mFilterR[j].process(tmpR);
        }

        inout[0] = tmpL;
        inout[1] = tmpR;
        inout += 2;
    }
}

EffectHeadphone::EffectHeadphone()
    : mDeep(true), mWide(true),
      mDelayDataL(0), mDelayDataR(0)
{
    setLevel(0);
}

EffectHeadphone::~EffectHeadphone()
{
}

void EffectHeadphone::configure(const float samplingFrequency) {
    Effect::configure(samplingFrequency);

    mReverbDelayL.setParameters(mSamplingFrequency, 0.030f);
    mReverbDelayR.setParameters(mSamplingFrequency, 0.030f);
    /* the -3 dB point is around 650 Hz, giving about 300 us to work with */
    mLocalizationL.setHighShelf(800.0f, mSamplingFrequency, -11.0f, 0.72f);
    mLocalizationR.setHighShelf(800.0f, mSamplingFrequency, -11.0f, 0.72f);
    /* Rockbox has a 0.3 ms delay line (13 samples at 44100 Hz), but
     * I think it makes the whole effect sound pretty bad so I skipped it! */
}

void EffectHeadphone::setDeep(bool deep)
{
    mDeep = deep;
}

void EffectHeadphone::setWide(bool wide)
{
    mWide = wide;
}

void EffectHeadphone::setLevel(float level)
{
    mLevel = toFixedPoint(powf(10, (level - 15.0f) / 20.0f));
}

void EffectHeadphone::process(sample_t* inout, int32_t frames)
{
    for (int32_t i = 0; i < frames; i ++) {
        /* calculate reverb wet into dataL, dataR */
        sample_t dryL = inout[0];
        sample_t dryR = inout[1];
        sample_t dataL = dryL;
        sample_t dataR = dryR;
        
        if (mDeep) {
            /* Note: a pinking filter here would be good. */
            dataL += mDelayDataR;
            dataR += mDelayDataL;
        }

        dataL = mReverbDelayL.process(dataL);
        dataR = mReverbDelayR.process(dataR);

        if (mWide) {
            dataR = -dataR;
        }

        dataL = multiply(dataL, mLevel);
        dataR = multiply(dataR, mLevel);

        mDelayDataL = dataL;
        mDelayDataR = dataR;

        /* Reverb wet done; mix with dry and do headphone virtualization */
        dataL += dryL;
        dataR += dryR;

        /* In matrix decoding, center channel is mixed at 0.7 and the main channel at 1.
         * It follows that the sum of them is 1.7, and the proportion of the main channel
         * must be 1 / 1.7, or about 6/10. Assuming it is so, 4/10 is the contribution
         * of center, and when 2 channels are combined, the scaler is 2/10 or 1/5.
         *
         * We could try to dynamically adjust this divisor based on cross-correlation
         * between left/right channels, which would allow us to recover a reasonable
         * estimate of the music's original center channel. */
        sample_t center = (dataL + dataR) / 5;
        sample_t directL = (dataL - center);
        sample_t directR = (dataR - center);

        /* We assume center channel reaches both ears with no coloration required.
         * We could also handle it differently at reverb stage... */

        /* Apply localization filter. */
        sample_t localizedL = mLocalizationL.process(directL);
        sample_t localizedR = mLocalizationR.process(directR);
        
        /* Mix difference between channels. dataX = directX + center. */
        inout[0] = dataL + localizedR;
        inout[1] = dataR + localizedL;
        inout += 2;
    }
}


/***************************************************************************
 * AudioDSP                                                                *
 ***************************************************************************/
const String8 AudioDSP::keyCompressionEnable = String8("dsp.compression.enable");
const String8 AudioDSP::keyCompressionRatio = String8("dsp.compression.ratio");

const String8 AudioDSP::keyToneEnable = String8("dsp.tone.enable");
const String8 AudioDSP::keyToneEq1 = String8("dsp.tone.eq1");
const String8 AudioDSP::keyToneEq2 = String8("dsp.tone.eq2");
const String8 AudioDSP::keyToneEq3 = String8("dsp.tone.eq3");
const String8 AudioDSP::keyToneEq4 = String8("dsp.tone.eq4");
const String8 AudioDSP::keyToneEq5 = String8("dsp.tone.eq5");

const String8 AudioDSP::keyHeadphoneEnable = String8("dsp.headphone.enable");
const String8 AudioDSP::keyHeadphoneDeep = String8("dsp.headphone.deep");
const String8 AudioDSP::keyHeadphoneWide = String8("dsp.headphone.wide");
const String8 AudioDSP::keyHeadphoneLevel = String8("dsp.headphone.level");

AudioDSP::AudioDSP()
    : mCompressionEnable(false), mToneEnable(false),  mHeadphoneEnable(false)
{
}

AudioDSP::~AudioDSP()
{
}

void AudioDSP::configure(const float samplingRate)
{
    mCompression.configure(samplingRate);
    mTone.configure(samplingRate);
    mHeadphone.configure(samplingRate);
}

void AudioDSP::setParameters(const String8& keyValuePairs)
{
    int intValue;
    float floatValue;
    status_t result;
    AudioParameter param = AudioParameter(keyValuePairs);

    result = param.getInt(keyCompressionEnable, intValue);
    if (result == NO_ERROR) {
       mCompressionEnable = intValue != 0;
    }
    result = param.getFloat(keyCompressionRatio, floatValue);
    if (result == NO_ERROR) {
        mCompression.setRatio(floatValue);
    }

    result = param.getInt(keyToneEnable, intValue);
    if (result == NO_ERROR) {
        mToneEnable = intValue != 0;
    }
    result = param.getFloat(keyToneEq1, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(0, floatValue);
    }
    result = param.getFloat(keyToneEq2, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(1, floatValue);
    }
    result = param.getFloat(keyToneEq3, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(2, floatValue);
    }
    result = param.getFloat(keyToneEq4, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(3, floatValue);
    }
    result = param.getFloat(keyToneEq5, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(4, floatValue);
    }
    
    result = param.getInt(keyHeadphoneEnable, intValue);
    if (result == NO_ERROR) {
        mHeadphoneEnable = intValue != 0;
    }
    result = param.getInt(keyHeadphoneDeep, intValue);
    if (result == NO_ERROR) {
        mHeadphone.setDeep(intValue != 0);
    }
    result = param.getInt(keyHeadphoneWide, intValue);
    if (result == NO_ERROR) {
        mHeadphone.setWide(intValue != 0);
    }
    result = param.getFloat(keyHeadphoneLevel, floatValue);
    if (result == NO_ERROR) {
        mHeadphone.setLevel(floatValue);
    }
}

int32_t AudioDSP::estimateLevel(const int16_t* input, int32_t frames, int32_t samplesPerFrame)
{
    if (! mCompressionEnable) {
        return 65536;
    }

    /* Analyze both channels separately, pick the maximum power measured. */
    float maximumPowerSquared = 0;
    for (int channel = 0; channel < samplesPerFrame; channel ++) {
        float candidatePowerSquared = mCompression.estimateLevel(input + channel, frames, samplesPerFrame);
        if (candidatePowerSquared > maximumPowerSquared) {
            maximumPowerSquared = candidatePowerSquared;
        }
    }

    /* -100 .. 0 dB. */
    float signalPowerDb = logf(maximumPowerSquared + 1e-10f) / logf(10.0f) * 10.0f;

    /* target 83 dB SPL, and add 6 dB to compensate for the weighter, whose
     * peak is at -3 dB. */
    signalPowerDb += 96.0f - 83.0f + 6.0f;

    /* now we have an estimate of the signal power, with 0 level around 83 dB.
     * we now select the level to boost to. */
    float desiredLevelDb = signalPowerDb / mCompression.mCompressionRatio;

    /* turn back to multiplier */
    float correctionDb = desiredLevelDb - signalPowerDb;
    
    /* Reduce extreme boost by a smooth ramp.
     * New range -50 .. 0 dB */
    correctionDb -= powf(correctionDb/100, 2.0f) * (100.0f / 2.0f);

    return int32_t(65536.0f * powf(10.0f, correctionDb / 20.0f));
}

/* input is 28-bit interleaved stereo in integer format */
void AudioDSP::process(int32_t* audioData, int32_t frames)
{
#if defined(__ARM_HAVE_VFP)
    while (frames > 0) {
#define AUDIO_BLOCK 64
        /* Process audio a small chunk at a time */
        sample_t audioDataProc[AUDIO_BLOCK];
        int32_t samples = frames << 1;
        if (samples > AUDIO_BLOCK) {
            samples = AUDIO_BLOCK;
        }
        /* int32 -> float */
        for (int32_t i = 0; i < samples; i ++) {
            audioDataProc[i] = audioData[i];
        }

        if (mToneEnable) {
            mTone.process(audioDataProc, samples >> 1);
        }

        if (mHeadphoneEnable) {
            mHeadphone.process(audioDataProc, samples >> 1);
        }

        /* float -> int32 */
        for (int32_t i = 0; i < samples; i ++) {
            audioData[i] = audioDataProc[i];
        }

        audioData += samples;
        frames -= samples >> 1;
#undef AUDIO_BLOCK
    }
#else
    if (mToneEnable) {
        mTone.process(audioData, frames);
    }

    if (mHeadphoneEnable) {
        mHeadphone.process(audioData, frames);
    }
#endif
}

}
