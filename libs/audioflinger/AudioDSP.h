/* //device/include/server/AudioFlinger/AudioDSP.h
**
** Copyright 2010, Antti S Lankila
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

#ifndef ANDROID_AUDIODSP_H
#define ANDROID_AUDIODSP_H 1

#include <media/AudioSystem.h>
#include <utils/String8.h>

namespace android {

class EffectCompressionInt;
class EffectCompressionFloat;
class BiquadFloat;
class BiquadInt;

/* Select appropriate types and implementations of primitives. */
#if defined(__ARM_HAVE_VFP)
typedef BiquadFloat Biquad;
typedef EffectCompressionFloat EffectCompression;
typedef float sample_t;
#else
typedef BiquadInt Biquad;
typedef EffectCompressionInt EffectCompression;
typedef int32_t sample_t;
#endif

/* Trickery with sample_t suffices; no separate float/int implementations. */
class Delay {
    sample_t* mState;
    int32_t mIndex;
    int32_t mLength;

    public:
    Delay();
    ~Delay();
    void setParameters(float rate, float time);
    sample_t process(sample_t x0);
};

/* Trickery with sample_t suffices; no separate float/int implementations. */
class Allpass {
    sample_t mK;
    sample_t* mState;
    int32_t mIndex;
    int32_t mLength;

    public:
    Allpass();
    ~Allpass();
    void setParameters(float rate, float k, float time);
    sample_t process(sample_t x0);
};

/* Separate implementations for float, int and arm assembly. */
class BiquadBase {
    protected:
    virtual ~BiquadBase() = 0;
    virtual void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2) = 0;

    public:
    void setRC(float cf, float sf);
    void setPeakingEqualizer(float cf, float sf, float gain, float bw);
    void setBandPass(float cf, float sf, float resonance);
    void setLowShelf(float cf, float sf, float gain, float slope);
    void setHighShelf(float cf, float sf, float gain, float slope);
    void setHighShelf1(float cf, float sf, float gain);
    virtual void reset() = 0;
};

class BiquadInt : public BiquadBase {
    private:
    union {
        struct {
            int32_t mA, mB;
            int32_t mX, mY;
        } i32;
        struct {
            int16_t mA1, mA2, mB1, mB2;
            int16_t mX1, mX2, mY1, mY2;
        } i16;
    } state;
    int16_t mB0, mY0;

    protected:
    void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    BiquadInt();
    int32_t process(int32_t x0);
    void reset();
};

class BiquadFloat : public BiquadBase {
    private:
    float mA1, mA2, mB0, mB1, mB2;
    float mX1, mX2, mY0, mY1, mY2;
    
    protected:
    void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    BiquadFloat();
    float process(float x0);
    void reset();
};

class Effect {
    protected:
    float mSamplingFrequency;
    
    public:
    Effect();
    virtual ~Effect();
    virtual void configure(const float samplingFrequency);
    virtual void process(sample_t* inout, int32_t frames) = 0;
};

/* Separate implementations for float and int */
class EffectCompressionBase : public Effect {
    public:
    float mCompressionRatio;

    EffectCompressionBase();
    ~EffectCompressionBase();
    void setRatio(float compressionRatio);
    void process(sample_t* inout, int32_t frames);
    
    virtual void configure(const float samplingFrequency) = 0;
    virtual float estimateLevel(const int16_t* audiodata, int32_t frames, int32_t framesPerSample) = 0;
};

class EffectCompressionInt : public EffectCompressionBase {
    private:
    BiquadInt mWeighter;

    public:
    void configure(const float samplingFrequency);
    float estimateLevel(const int16_t* audiodata, int32_t frames, int32_t framesPerSample);
};

class EffectCompressionFloat : public EffectCompressionBase {
    private:
    BiquadFloat mWeighter;

    public:
    void configure(const float samplingFrequency);
    float estimateLevel(const int16_t* audiodata, int32_t frames, int32_t framesPerSample);
};

/* Trickery with sample_t and Biquad suffices. */
class EffectTone : public Effect {
    float mBand[5];
    sample_t mGain;
    Biquad mFilterL[4], mFilterR[4];

    void refreshBands();

    public:
    EffectTone();
    ~EffectTone();
    void configure(const float samplingFrequency);
    void setBand(int32_t idx, float dB);
    void process(sample_t* inout, int32_t frames);
};

/* Trickery with sample_t and Biquad suffices. */
class EffectHeadphone : public Effect {
    bool mDeep, mWide;
    sample_t mLevel;

    Delay mReverbDelayL, mReverbDelayR;
    sample_t mDelayDataL, mDelayDataR;
    Biquad mLocalizationL, mLocalizationR;

    public:
    EffectHeadphone();
    ~EffectHeadphone();
    void configure(const float samplingFrequency);
    void setDeep(bool enable);
    void setWide(bool enable);
    void setLevel(float level);
    void process(sample_t* inout, int32_t frames);
};

class AudioDSP {
    bool mCompressionEnable;
    EffectCompression mCompression;
    
    bool mToneEnable;
    EffectTone mTone;

    bool mHeadphoneEnable;
    EffectHeadphone mHeadphone;

    public:
    AudioDSP();
    ~AudioDSP();

    void configure(float samplingRate);
    void setParameters(const String8& keyValuePairs);
    int32_t estimateLevel(const int16_t* audiodata, int32_t frames, int32_t samplesPerFrame);
    void process(int32_t* inputInterleaved, int32_t frames);

    static const String8 keyCompressionEnable;
    static const String8 keyCompressionRatio;

    static const String8 keyToneEnable;
    static const String8 keyToneEq1;
    static const String8 keyToneEq2;
    static const String8 keyToneEq3;
    static const String8 keyToneEq4;
    static const String8 keyToneEq5;
    
    static const String8 keyHeadphoneEnable;
    static const String8 keyHeadphoneDeep;
    static const String8 keyHeadphoneWide;
    static const String8 keyHeadphoneLevel;
};

}

#endif
