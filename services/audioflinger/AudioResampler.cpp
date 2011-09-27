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

#define LOG_TAG "AudioResampler"
//#define LOG_NDEBUG 0

#include <stdint.h>
#include <stdlib.h>
#include <sys/types.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include "AudioResampler.h"
#include "AudioResamplerSinc.h"
#include "AudioResamplerCubic.h"

#ifdef __arm__
#include <machine/cpu-features.h>
#endif

namespace android {

#ifdef __ARM_HAVE_HALFWORD_MULTIPLY // optimized asm option
    #define ASM_ARM_RESAMP1 // enable asm optimisation for ResamplerOrder1
#endif // __ARM_HAVE_HALFWORD_MULTIPLY
// ----------------------------------------------------------------------------

class AudioResamplerOrder1 : public AudioResampler {
public:
    AudioResamplerOrder1(int bitDepth, int inChannelCount, int32_t sampleRate) :
        AudioResampler(bitDepth, inChannelCount, sampleRate), mX0L(0), mX0R(0) {
    }
    virtual void resample(int32_t* out, size_t outFrameCount,
            AudioBufferProvider* provider);
private:
    // number of bits used in interpolation multiply - 15 bits avoids overflow
    static const int kNumInterpBits = 15;

    // bits to shift the phase fraction down to avoid overflow
    static const int kPreInterpShift = kNumPhaseBits - kNumInterpBits;

    void init() {}
    void resampleMono16(int32_t* out, size_t outFrameCount,
            AudioBufferProvider* provider);
    void resampleStereo16(int32_t* out, size_t outFrameCount,
            AudioBufferProvider* provider);
#ifdef ASM_ARM_RESAMP1  // asm optimisation for ResamplerOrder1
    void AsmMono16Loop(int16_t *in, int32_t* maxOutPt, int32_t maxInIdx,
            size_t &outputIndex, int32_t* out, size_t &inputIndex, int32_t vl, int32_t vr,
            uint32_t &phaseFraction, uint32_t phaseIncrement);
    void AsmStereo16Loop(int16_t *in, int32_t* maxOutPt, int32_t maxInIdx,
            size_t &outputIndex, int32_t* out, size_t &inputIndex, int32_t vl, int32_t vr,
            uint32_t &phaseFraction, uint32_t phaseIncrement);
#endif  // ASM_ARM_RESAMP1

    static inline int32_t Interp(int32_t x0, int32_t x1, uint32_t f) {
        return x0 + (((x1 - x0) * (int32_t)(f >> kPreInterpShift)) >> kNumInterpBits);
    }
    static inline void Advance(size_t* index, uint32_t* frac, uint32_t inc) {
        *frac += inc;
        *index += (size_t)(*frac >> kNumPhaseBits);
        *frac &= kPhaseMask;
    }
    int mX0L;
    int mX0R;
};

// ----------------------------------------------------------------------------
AudioResampler* AudioResampler::create(int bitDepth, int inChannelCount,
        int32_t sampleRate, int quality) {

    // can only create low quality resample now
    AudioResampler* resampler;

    char value[PROPERTY_VALUE_MAX];
    if (property_get("af.resampler.quality", value, 0)) {
        quality = atoi(value);
        LOGD("forcing AudioResampler quality to %d", quality);
    }

    if (quality == DEFAULT)
        quality = LOW_QUALITY;

    switch (quality) {
    default:
    case LOW_QUALITY:
        LOGV("Create linear Resampler");
        resampler = new AudioResamplerOrder1(bitDepth, inChannelCount, sampleRate);
        break;
    case MED_QUALITY:
        LOGV("Create cubic Resampler");
        resampler = new AudioResamplerCubic(bitDepth, inChannelCount, sampleRate);
        break;
    case HIGH_QUALITY:
        LOGV("Create sinc Resampler");
        resampler = new AudioResamplerSinc(bitDepth, inChannelCount, sampleRate);
        break;
    }

    // initialize resampler
    resampler->init();
    return resampler;
}

AudioResampler::AudioResampler(int bitDepth, int inChannelCount,
        int32_t sampleRate) :
    mBitDepth(bitDepth), mChannelCount(inChannelCount),
            mSampleRate(sampleRate), mInSampleRate(sampleRate), mInputIndex(0),
            mPhaseFraction(0) {
    // sanity check on format
    if ((bitDepth != 16) ||(inChannelCount < 1) || (inChannelCount > 2)) {
        LOGE("Unsupported sample format, %d bits, %d channels", bitDepth,
                inChannelCount);
        // LOG_ASSERT(0);
    }

    // initialize common members
    mVolume[0] = mVolume[1] = 0;
    mBuffer.frameCount = 0;

    // save format for quick lookup
    if (inChannelCount == 1) {
        mFormat = MONO_16_BIT;
    } else {
        mFormat = STEREO_16_BIT;
    }
}

AudioResampler::~AudioResampler() {
}

void AudioResampler::setSampleRate(int32_t inSampleRate) {
    mInSampleRate = inSampleRate;
    mPhaseIncrement = (uint32_t)((kPhaseMultiplier * inSampleRate) / mSampleRate);
}

void AudioResampler::setVolume(int16_t left, int16_t right) {
    // TODO: Implement anti-zipper filter
    mVolume[0] = left;
    mVolume[1] = right;
}

// ----------------------------------------------------------------------------

void AudioResamplerOrder1::resample(int32_t* out, size_t outFrameCount,
        AudioBufferProvider* provider) {

    // should never happen, but we overflow if it does
    // LOG_ASSERT(outFrameCount < 32767);

    // select the appropriate resampler
    switch (mChannelCount) {
    case 1:
        resampleMono16(out, outFrameCount, provider);
        break;
    case 2:
        resampleStereo16(out, outFrameCount, provider);
        break;
    }
}

void AudioResamplerOrder1::resampleStereo16(int32_t* out, size_t outFrameCount,
        AudioBufferProvider* provider) {

    int32_t vl = mVolume[0];
    int32_t vr = mVolume[1];

    size_t inputIndex = mInputIndex;
    uint32_t phaseFraction = mPhaseFraction;
    uint32_t phaseIncrement = mPhaseIncrement;
    size_t outputIndex = 0;
    size_t outputSampleCount = outFrameCount * 2;
    size_t inFrameCount = (outFrameCount*mInSampleRate)/mSampleRate;

    // LOGE("starting resample %d frames, inputIndex=%d, phaseFraction=%d, phaseIncrement=%d\n",
    //      outFrameCount, inputIndex, phaseFraction, phaseIncrement);

    while (outputIndex < outputSampleCount) {

        // buffer is empty, fetch a new one
        while (mBuffer.frameCount == 0) {
            mBuffer.frameCount = inFrameCount;
            provider->getNextBuffer(&mBuffer);
            if (mBuffer.raw == NULL) {
                goto resampleStereo16_exit;
            }

            // LOGE("New buffer fetched: %d frames\n", mBuffer.frameCount);
            if (mBuffer.frameCount > inputIndex) break;

            inputIndex -= mBuffer.frameCount;
            mX0L = mBuffer.i16[mBuffer.frameCount*2-2];
            mX0R = mBuffer.i16[mBuffer.frameCount*2-1];
            provider->releaseBuffer(&mBuffer);
             // mBuffer.frameCount == 0 now so we reload a new buffer
        }

        int16_t *in = mBuffer.i16;

        // handle boundary case
        while (inputIndex == 0) {
            // LOGE("boundary case\n");
            out[outputIndex++] += vl * Interp(mX0L, in[0], phaseFraction);
            out[outputIndex++] += vr * Interp(mX0R, in[1], phaseFraction);
            Advance(&inputIndex, &phaseFraction, phaseIncrement);
            if (outputIndex == outputSampleCount)
                break;
        }

        // process input samples
        // LOGE("general case\n");

#ifdef ASM_ARM_RESAMP1  // asm optimisation for ResamplerOrder1
        if (inputIndex + 2 < mBuffer.frameCount) {
            int32_t* maxOutPt;
            int32_t maxInIdx;

            maxOutPt = out + (outputSampleCount - 2);   // 2 because 2 frames per loop
            maxInIdx = mBuffer.frameCount - 2;
            AsmStereo16Loop(in, maxOutPt, maxInIdx, outputIndex, out, inputIndex, vl, vr,
                    phaseFraction, phaseIncrement);
        }
#endif  // ASM_ARM_RESAMP1

        while (outputIndex < outputSampleCount && inputIndex < mBuffer.frameCount) {
            out[outputIndex++] += vl * Interp(in[inputIndex*2-2],
                    in[inputIndex*2], phaseFraction);
            out[outputIndex++] += vr * Interp(in[inputIndex*2-1],
                    in[inputIndex*2+1], phaseFraction);
            Advance(&inputIndex, &phaseFraction, phaseIncrement);
        }

        // LOGE("loop done - outputIndex=%d, inputIndex=%d\n", outputIndex, inputIndex);

        // if done with buffer, save samples
        if (inputIndex >= mBuffer.frameCount) {
            inputIndex -= mBuffer.frameCount;

            // LOGE("buffer done, new input index %d", inputIndex);

            mX0L = mBuffer.i16[mBuffer.frameCount*2-2];
            mX0R = mBuffer.i16[mBuffer.frameCount*2-1];
            provider->releaseBuffer(&mBuffer);

            // verify that the releaseBuffer resets the buffer frameCount
            // LOG_ASSERT(mBuffer.frameCount == 0);
        }
    }

    // LOGE("output buffer full - outputIndex=%d, inputIndex=%d\n", outputIndex, inputIndex);

resampleStereo16_exit:
    // save state
    mInputIndex = inputIndex;
    mPhaseFraction = phaseFraction;
}

void AudioResamplerOrder1::resampleMono16(int32_t* out, size_t outFrameCount,
        AudioBufferProvider* provider) {

    int32_t vl = mVolume[0];
    int32_t vr = mVolume[1];

    size_t inputIndex = mInputIndex;
    uint32_t phaseFraction = mPhaseFraction;
    uint32_t phaseIncrement = mPhaseIncrement;
    size_t outputIndex = 0;
    size_t outputSampleCount = outFrameCount * 2;
    size_t inFrameCount = (outFrameCount*mInSampleRate)/mSampleRate;

    // LOGE("starting resample %d frames, inputIndex=%d, phaseFraction=%d, phaseIncrement=%d\n",
    //      outFrameCount, inputIndex, phaseFraction, phaseIncrement);
    while (outputIndex < outputSampleCount) {
        // buffer is empty, fetch a new one
        while (mBuffer.frameCount == 0) {
            mBuffer.frameCount = inFrameCount;
            provider->getNextBuffer(&mBuffer);
            if (mBuffer.raw == NULL) {
                mInputIndex = inputIndex;
                mPhaseFraction = phaseFraction;
                goto resampleMono16_exit;
            }
            // LOGE("New buffer fetched: %d frames\n", mBuffer.frameCount);
            if (mBuffer.frameCount >  inputIndex) break;

            inputIndex -= mBuffer.frameCount;
            mX0L = mBuffer.i16[mBuffer.frameCount-1];
            provider->releaseBuffer(&mBuffer);
            // mBuffer.frameCount == 0 now so we reload a new buffer
        }
        int16_t *in = mBuffer.i16;

        // handle boundary case
        while (inputIndex == 0) {
            // LOGE("boundary case\n");
            int32_t sample = Interp(mX0L, in[0], phaseFraction);
            out[outputIndex++] += vl * sample;
            out[outputIndex++] += vr * sample;
            Advance(&inputIndex, &phaseFraction, phaseIncrement);
            if (outputIndex == outputSampleCount)
                break;
        }

        // process input samples
        // LOGE("general case\n");

#ifdef ASM_ARM_RESAMP1  // asm optimisation for ResamplerOrder1
        if (inputIndex + 2 < mBuffer.frameCount) {
            int32_t* maxOutPt;
            int32_t maxInIdx;

            maxOutPt = out + (outputSampleCount - 2);
            maxInIdx = (int32_t)mBuffer.frameCount - 2;
                AsmMono16Loop(in, maxOutPt, maxInIdx, outputIndex, out, inputIndex, vl, vr,
                        phaseFraction, phaseIncrement);
        }
#endif  // ASM_ARM_RESAMP1

        while (outputIndex < outputSampleCount && inputIndex < mBuffer.frameCount) {
            int32_t sample = Interp(in[inputIndex-1], in[inputIndex],
                    phaseFraction);
            out[outputIndex++] += vl * sample;
            out[outputIndex++] += vr * sample;
            Advance(&inputIndex, &phaseFraction, phaseIncrement);
        }


        // LOGE("loop done - outputIndex=%d, inputIndex=%d\n", outputIndex, inputIndex);

        // if done with buffer, save samples
        if (inputIndex >= mBuffer.frameCount) {
            inputIndex -= mBuffer.frameCount;

            // LOGE("buffer done, new input index %d", inputIndex);

            mX0L = mBuffer.i16[mBuffer.frameCount-1];
            provider->releaseBuffer(&mBuffer);

            // verify that the releaseBuffer resets the buffer frameCount
            // LOG_ASSERT(mBuffer.frameCount == 0);
        }
    }

    // LOGE("output buffer full - outputIndex=%d, inputIndex=%d\n", outputIndex, inputIndex);

resampleMono16_exit:
    // save state
    mInputIndex = inputIndex;
    mPhaseFraction = phaseFraction;
}

#ifdef ASM_ARM_RESAMP1  // asm optimisation for ResamplerOrder1

/*******************************************************************
*
*   AsmMono16Loop
*   asm optimized monotonic loop version; one loop is 2 frames
*   Input:
*       in : pointer on input samples
*       maxOutPt : pointer on first not filled
*       maxInIdx : index on first not used
*       outputIndex : pointer on current output index
*       out : pointer on output buffer
*       inputIndex : pointer on current input index
*       vl, vr : left and right gain
*       phaseFraction : pointer on current phase fraction
*       phaseIncrement
*   Ouput:
*       outputIndex :
*       out : updated buffer
*       inputIndex : index of next to use
*       phaseFraction : phase fraction for next interpolation
*
*******************************************************************/
void AudioResamplerOrder1::AsmMono16Loop(int16_t *in, int32_t* maxOutPt, int32_t maxInIdx,
            size_t &outputIndex, int32_t* out, size_t &inputIndex, int32_t vl, int32_t vr,
            uint32_t &phaseFraction, uint32_t phaseIncrement)
{
#define MO_PARAM5   "36"        // offset of parameter 5 (outputIndex)

    asm(
        "stmfd  sp!, {r4, r5, r6, r7, r8, r9, r10, r11, lr}\n"
        // get parameters
        "   ldr r6, [sp, #" MO_PARAM5 " + 20]\n"    // &phaseFraction
        "   ldr r6, [r6]\n"                         // phaseFraction
        "   ldr r7, [sp, #" MO_PARAM5 " + 8]\n"     // &inputIndex
        "   ldr r7, [r7]\n"                         // inputIndex
        "   ldr r8, [sp, #" MO_PARAM5 " + 4]\n"     // out
        "   ldr r0, [sp, #" MO_PARAM5 " + 0]\n"     // &outputIndex
        "   ldr r0, [r0]\n"                         // outputIndex
        "   add r8, r0, asl #2\n"                   // curOut
        "   ldr r9, [sp, #" MO_PARAM5 " + 24]\n"    // phaseIncrement
        "   ldr r10, [sp, #" MO_PARAM5 " + 12]\n"   // vl
        "   ldr r11, [sp, #" MO_PARAM5 " + 16]\n"   // vr

        // r0 pin, x0, Samp

        // r1 in
        // r2 maxOutPt
        // r3 maxInIdx

        // r4 x1, i1, i3, Out1
        // r5 out0

        // r6 frac
        // r7 inputIndex
        // r8 curOut

        // r9 inc
        // r10 vl
        // r11 vr

        // r12
        // r13 sp
        // r14

        // the following loop works on 2 frames

        ".Y4L01:\n"
        "   cmp r8, r2\n"                   // curOut - maxCurOut
        "   bcs .Y4L02\n"

#define MO_ONE_FRAME \
    "   add r0, r1, r7, asl #1\n"       /* in + inputIndex */\
    "   ldrsh r4, [r0]\n"               /* in[inputIndex] */\
    "   ldr r5, [r8]\n"                 /* out[outputIndex] */\
    "   ldrsh r0, [r0, #-2]\n"          /* in[inputIndex-1] */\
    "   bic r6, r6, #0xC0000000\n"      /* phaseFraction & ... */\
    "   sub r4, r4, r0\n"               /* in[inputIndex] - in[inputIndex-1] */\
    "   mov r4, r4, lsl #2\n"           /* <<2 */\
    "   smulwt r4, r4, r6\n"            /* (x1-x0)*.. */\
    "   add r6, r6, r9\n"               /* phaseFraction + phaseIncrement */\
    "   add r0, r0, r4\n"               /* x0 - (..) */\
    "   mla r5, r0, r10, r5\n"          /* vl*interp + out[] */\
    "   ldr r4, [r8, #4]\n"             /* out[outputIndex+1] */\
    "   str r5, [r8], #4\n"             /* out[outputIndex++] = ... */\
    "   mla r4, r0, r11, r4\n"          /* vr*interp + out[] */\
    "   add r7, r7, r6, lsr #30\n"      /* inputIndex + phaseFraction>>30 */\
    "   str r4, [r8], #4\n"             /* out[outputIndex++] = ... */

        MO_ONE_FRAME    // frame 1
        MO_ONE_FRAME    // frame 2

        "   cmp r7, r3\n"                   // inputIndex - maxInIdx
        "   bcc .Y4L01\n"
        ".Y4L02:\n"

        "   bic r6, r6, #0xC0000000\n"             // phaseFraction & ...
        // save modified values
        "   ldr r0, [sp, #" MO_PARAM5 " + 20]\n"    // &phaseFraction
        "   str r6, [r0]\n"                         // phaseFraction
        "   ldr r0, [sp, #" MO_PARAM5 " + 8]\n"     // &inputIndex
        "   str r7, [r0]\n"                         // inputIndex
        "   ldr r0, [sp, #" MO_PARAM5 " + 4]\n"     // out
        "   sub r8, r0\n"                           // curOut - out
        "   asr r8, #2\n"                           // new outputIndex
        "   ldr r0, [sp, #" MO_PARAM5 " + 0]\n"     // &outputIndex
        "   str r8, [r0]\n"                         // save outputIndex

        "   ldmfd   sp!, {r4, r5, r6, r7, r8, r9, r10, r11, pc}\n"
    );
}

/*******************************************************************
*
*   AsmStereo16Loop
*   asm optimized stereo loop version; one loop is 2 frames
*   Input:
*       in : pointer on input samples
*       maxOutPt : pointer on first not filled
*       maxInIdx : index on first not used
*       outputIndex : pointer on current output index
*       out : pointer on output buffer
*       inputIndex : pointer on current input index
*       vl, vr : left and right gain
*       phaseFraction : pointer on current phase fraction
*       phaseIncrement
*   Ouput:
*       outputIndex :
*       out : updated buffer
*       inputIndex : index of next to use
*       phaseFraction : phase fraction for next interpolation
*
*******************************************************************/
void AudioResamplerOrder1::AsmStereo16Loop(int16_t *in, int32_t* maxOutPt, int32_t maxInIdx,
            size_t &outputIndex, int32_t* out, size_t &inputIndex, int32_t vl, int32_t vr,
            uint32_t &phaseFraction, uint32_t phaseIncrement)
{
#define ST_PARAM5    "40"     // offset of parameter 5 (outputIndex)
    asm(
        "stmfd  sp!, {r4, r5, r6, r7, r8, r9, r10, r11, r12, lr}\n"
        // get parameters
        "   ldr r6, [sp, #" ST_PARAM5 " + 20]\n"    // &phaseFraction
        "   ldr r6, [r6]\n"                         // phaseFraction
        "   ldr r7, [sp, #" ST_PARAM5 " + 8]\n"     // &inputIndex
        "   ldr r7, [r7]\n"                         // inputIndex
        "   ldr r8, [sp, #" ST_PARAM5 " + 4]\n"     // out
        "   ldr r0, [sp, #" ST_PARAM5 " + 0]\n"     // &outputIndex
        "   ldr r0, [r0]\n"                         // outputIndex
        "   add r8, r0, asl #2\n"                   // curOut
        "   ldr r9, [sp, #" ST_PARAM5 " + 24]\n"    // phaseIncrement
        "   ldr r10, [sp, #" ST_PARAM5 " + 12]\n"   // vl
        "   ldr r11, [sp, #" ST_PARAM5 " + 16]\n"   // vr

        // r0 pin, x0, Samp

        // r1 in
        // r2 maxOutPt
        // r3 maxInIdx

        // r4 x1, i1, i3, out1
        // r5 out0

        // r6 frac
        // r7 inputIndex
        // r8 curOut

        // r9 inc
        // r10 vl
        // r11 vr

        // r12 temporary
        // r13 sp
        // r14

        ".Y5L01:\n"
        "   cmp r8, r2\n"                   // curOut - maxCurOut
        "   bcs .Y5L02\n"

#define ST_ONE_FRAME \
    "   bic r6, r6, #0xC0000000\n"      /* phaseFraction & ... */\
\
    "   add r0, r1, r7, asl #2\n"       /* in + 2*inputIndex */\
\
    "   ldrsh r4, [r0]\n"               /* in[2*inputIndex] */\
    "   ldr r5, [r8]\n"                 /* out[outputIndex] */\
    "   ldrsh r12, [r0, #-4]\n"         /* in[2*inputIndex-2] */\
    "   sub r4, r4, r12\n"              /* in[2*InputIndex] - in[2*InputIndex-2] */\
    "   mov r4, r4, lsl #2\n"           /* <<2 */\
    "   smulwt r4, r4, r6\n"            /* (x1-x0)*.. */\
    "   add r12, r12, r4\n"             /* x0 - (..) */\
    "   mla r5, r12, r10, r5\n"         /* vl*interp + out[] */\
    "   ldr r4, [r8, #4]\n"             /* out[outputIndex+1] */\
    "   str r5, [r8], #4\n"             /* out[outputIndex++] = ... */\
\
    "   ldrsh r12, [r0, #+2]\n"         /* in[2*inputIndex+1] */\
    "   ldrsh r0, [r0, #-2]\n"          /* in[2*inputIndex-1] */\
    "   sub r12, r12, r0\n"             /* in[2*InputIndex] - in[2*InputIndex-2] */\
    "   mov r12, r12, lsl #2\n"         /* <<2 */\
    "   smulwt r12, r12, r6\n"          /* (x1-x0)*.. */\
    "   add r12, r0, r12\n"             /* x0 - (..) */\
    "   mla r4, r12, r11, r4\n"         /* vr*interp + out[] */\
    "   str r4, [r8], #4\n"             /* out[outputIndex++] = ... */\
\
    "   add r6, r6, r9\n"               /* phaseFraction + phaseIncrement */\
    "   add r7, r7, r6, lsr #30\n"      /* inputIndex + phaseFraction>>30 */

    ST_ONE_FRAME    // frame 1
    ST_ONE_FRAME    // frame 1

        "   cmp r7, r3\n"                       // inputIndex - maxInIdx
        "   bcc .Y5L01\n"
        ".Y5L02:\n"

        "   bic r6, r6, #0xC0000000\n"              // phaseFraction & ...
        // save modified values
        "   ldr r0, [sp, #" ST_PARAM5 " + 20]\n"    // &phaseFraction
        "   str r6, [r0]\n"                         // phaseFraction
        "   ldr r0, [sp, #" ST_PARAM5 " + 8]\n"     // &inputIndex
        "   str r7, [r0]\n"                         // inputIndex
        "   ldr r0, [sp, #" ST_PARAM5 " + 4]\n"     // out
        "   sub r8, r0\n"                           // curOut - out
        "   asr r8, #2\n"                           // new outputIndex
        "   ldr r0, [sp, #" ST_PARAM5 " + 0]\n"     // &outputIndex
        "   str r8, [r0]\n"                         // save outputIndex

        "   ldmfd   sp!, {r4, r5, r6, r7, r8, r9, r10, r11, r12, pc}\n"
    );
}

#endif  // ASM_ARM_RESAMP1


// ----------------------------------------------------------------------------
}
; // namespace android

