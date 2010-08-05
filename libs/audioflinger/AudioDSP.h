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

class Delay {
    int32_t* mState;
    int32_t mIndex;
    int32_t mLength;

    public:
    Delay();
    ~Delay();
    void setParameters(float rate, float time);
    int32_t process(int32_t x0);
};

class Allpass {
    int32_t mK;
    int32_t* mState;
    int32_t mIndex;
    int32_t mLength;

    public:
    Allpass();
    ~Allpass();
    void setParameters(float rate, float k, float time);
    int32_t process(int32_t x0);
};

class Biquad {
    union {
        struct {
            int32_t mA, mB, mY, mX;
        } i32;
        struct {
            int16_t mA1, mA2, mB1, mB2, mY1, mY2, mX1, mX2;
        } i16;
    } state;
    int16_t mB0, mY0;

    void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    Biquad();
    void setRC(float cf, float sf);
    void setPeakingEqualizer(float cf, float sf, float gain, float bw);
    void setBandPass(float cf, float sf, float resonance);
    void setLowShelf(float cf, float sf, float gain, float slope);
    void setHighShelf(float cf, float sf, float gain, float slope);
    void reset();
    int32_t process(int16_t x0);
};

class Effect {
    protected:
    float mSamplingFrequency;
    
    public:
    Effect();
    virtual ~Effect();
    virtual void configure(const float samplingFrequency);
    virtual void process(int32_t* inout, int32_t frames) = 0;
};

class EffectCompression : public Effect {
    private:
    Biquad mWeighter;

    public:
    float mCompressionRatio;

    EffectCompression();
    ~EffectCompression();
    void configure(const float samplingFrequency);
    void setRatio(float compressionRatio);
    void process(int32_t* inout, int32_t frames);
    float estimateLevel(const int16_t* audiodata, int32_t frames, int32_t framesPerSample);
};

class EffectReverb : public Effect {
    Delay mDelayL, mDelayR;
    bool mDeep, mWide;
    int32_t mLevel, mDelayDataL, mDelayDataR;

    public:
    EffectReverb();
    ~EffectReverb();
    void configure(const float samplingFrequency);
    void setDeep(bool enable);
    void setWide(bool enable);
    void setLevel(float level);
    void process(int32_t* inout, int32_t frames);
};

class EffectTone : public Effect {
    float mBand[5];
    int32_t mGain;
    Biquad mFilterL[4], mFilterR[4];

    void refreshBands();

    public:
    EffectTone();
    ~EffectTone();
    void configure(const float samplingFrequency);
    void setBand(int32_t idx, float dB);
    void process(int32_t* inout, int32_t frames);
};

class EffectHeadphone : public Effect {
    Delay mDelayL, mDelayR;
    Allpass mAllpassL[3], mAllpassR[3];
    Biquad mLowpassL, mLowpassR;

    public:
    ~EffectHeadphone();
    void configure(const float samplingFrequency);
    void process(int32_t* inout, int32_t frames);
};

class AudioDSP {
    bool mCompressionEnable;
    EffectCompression mCompression;
    
    bool mReverbEnable;
    EffectReverb mReverb;

    bool mToneEnable;
    EffectTone mTone;

    bool mHeadphoneEnable;
    EffectHeadphone mHeadphone;

    int32_t mDitherValue;

    public:
    AudioDSP();
    ~AudioDSP();

    void configure(float samplingRate);
    void setParameters(const String8& keyValuePairs);
    int32_t estimateLevel(const int16_t* audiodata, int32_t frames, int32_t samplesPerFrame);
    void process(int32_t* inputInterleaved, int32_t frames);

    static const String8 keyCompressionEnable;
    static const String8 keyCompressionRatio;

    static const String8 keyReverbEnable;
    static const String8 keyReverbDeep;
    static const String8 keyReverbWide;
    static const String8 keyReverbLevel;

    static const String8 keyToneEnable;
    static const String8 keyToneEq1;
    static const String8 keyToneEq2;
    static const String8 keyToneEq3;
    static const String8 keyToneEq4;
    static const String8 keyToneEq5;
    
    static const String8 keyHeadphoneEnable;
};

}

#endif
