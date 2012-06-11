/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_AUDIO_RESAMPLER_H
#define ANDROID_AUDIO_RESAMPLER_H

#include <stdint.h>
#include <sys/types.h>

#include "AudioBufferProvider.h"

#if __cplusplus < 201103L && !defined(__GXX_EXPERIMENTAL_CXX0X__) && !defined(constexpr)
#define constexpr const
#endif

namespace android {
// ----------------------------------------------------------------------------

class AudioResampler {
public:
    // Determines quality of SRC.
    //  LOW_QUALITY: linear interpolator (1st order)
    //  MED_QUALITY: cubic interpolator (3rd order)
    //  HIGH_QUALITY: fixed multi-tap FIR (e.g. 48KHz->44.1KHz)
    // NOTE: high quality SRC will only be supported for
    // certain fixed rate conversions. Sample rate cannot be
    // changed dynamically. 
    enum src_quality {
        DEFAULT=0,
        LOW_QUALITY=1,
        MED_QUALITY=2,
        HIGH_QUALITY=3
    };

    static AudioResampler* create(int bitDepth, int inChannelCount,
            int32_t sampleRate, int quality=DEFAULT);

    virtual ~AudioResampler();

    virtual void init() = 0;
    virtual void setSampleRate(int32_t inSampleRate);
    virtual void setVolume(int16_t left, int16_t right);

    virtual void resample(int32_t* out, size_t outFrameCount,
            AudioBufferProvider* provider) = 0;

    virtual void reset();
    virtual size_t getUnreleasedFrames() { return mInputIndex; }

protected:
    // number of bits for phase fraction - 30 bits allows nearly 2x downsampling
    static constexpr int kNumPhaseBits = 30;

    // phase mask for fraction
    static constexpr uint32_t kPhaseMask = (1LU<<kNumPhaseBits)-1;

    // multiplier to calculate fixed point phase increment
    static constexpr double kPhaseMultiplier = 1L << kNumPhaseBits;

    enum format {MONO_16_BIT, STEREO_16_BIT};
    AudioResampler(int bitDepth, int inChannelCount, int32_t sampleRate);

    // prevent copying
    AudioResampler(const AudioResampler&);
    AudioResampler& operator=(const AudioResampler&);

    int32_t mBitDepth;
    int32_t mChannelCount;
    int32_t mSampleRate;
    int32_t mInSampleRate;
    AudioBufferProvider::Buffer mBuffer;
    union {
        int16_t mVolume[2];
        uint32_t mVolumeRL;
    };
    int16_t mTargetVolume[2];
    format mFormat;
    size_t mInputIndex;
    int32_t mPhaseIncrement;
    uint32_t mPhaseFraction;
};

// ----------------------------------------------------------------------------
}
; // namespace android

#endif // ANDROID_AUDIO_RESAMPLER_H
