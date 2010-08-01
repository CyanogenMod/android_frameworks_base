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
    int16_t mA1, mA2, mB0, mB1, mB2;
    int16_t mY1, mY2, mX1, mX2, mY0;

    void setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2);

    public:
    Biquad();
    void setRC(float cf, float sf);
    void setPeakingEqualizer(float cf, float sf, float gain, float bw);
    void setLowShelf(float cf, float sf, float gain, float slope);
    void setHighShelf(float cf, float sf, float gain, float slope);
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
    float mOldCorrectionDb;
    float mCompressionRatio;

    public:
    EffectCompression();
    ~EffectCompression();
    void setRatio(float compressionRatio);
    void process(int32_t* inout, int32_t frames);
    int32_t estimateLevel(const int16_t* audiodata, int32_t samples);
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
    Allpass mAllpassL[4], mAllpassR[4];
    Biquad mLowpassL, mLowpassR;

    public:
    ~EffectHeadphone();
    void configure(const float samplingFrequency);
    void process(int32_t* inout, int32_t frames);
};

class AudioDSP {
    bool mCompressionEnable;
    EffectCompression mCompression;

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
    int32_t estimateLevel(const int16_t* audiodata, int32_t samples);
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
};

}

#endif
