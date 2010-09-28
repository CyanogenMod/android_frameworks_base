/* //device/include/server/AudioFlinger/AudioMixer.cpp
**
** Copyright 2007, The Android Open Source Project
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

#define LOG_TAG "AudioMixer"
//#define LOG_NDEBUG 0

#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>

#include "AudioMixer.h"

namespace android {
// ----------------------------------------------------------------------------

static inline int16_t clamp16(int32_t sample)
{
    if ((sample>>15) ^ (sample>>31))
        sample = 0x7FFF ^ (sample>>31);
    return sample;
}

inline static uint32_t prng() {
    static uint32_t seed = 22222;
    seed = (seed * 196314165) + 907633515;
    return seed >> 20;
}

// ----------------------------------------------------------------------------

AudioMixer::AudioMixer(size_t frameCount, uint32_t sampleRate, AudioDSP& dsp)
    :   mActiveTrack(0), mTrackNames(0), mSampleRate(sampleRate), mDsp(dsp)
{
    mDsp.configure(sampleRate);
    mState.enabledTracks= 0;
    mState.needsChanged = 0;
    mState.frameCount   = frameCount;
    mState.outputTemp   = 0;
    mState.resampleTemp = 0;
    mState.hook         = process__nop;
    mState.dither.errorL = 0;
    mState.dither.errorR = 0;
    for (int i = 0; i < 4; i ++) {
        mState.dither.lipshitzL[i] = 0;
        mState.dither.lipshitzR[i] = 0;
    }
    mState.dither.oldDither = 0;
    track_t* t = mState.tracks;
    for (int i=0 ; i<32 ; i++) {
        t->needs = 0;
        t->volume[0] = UNITY_GAIN;
        t->volume[1] = UNITY_GAIN;
        t->prevVolume[0] = UNITY_GAIN << 16;
        t->prevVolume[1] = UNITY_GAIN << 16;
        t->volumeInc[0] = 0;
        t->volumeInc[1] = 0;
        t->channelCount = 2;
        t->enabled = 0;
        t->format = 16;
        t->buffer.raw = 0;
        t->bufferProvider = 0;
        t->hook = 0;
        t->resampler = 0;
        t->sampleRate = mSampleRate;
        t->in = 0;
        t++;
    }
}

 AudioMixer::~AudioMixer()
 {
     track_t* t = mState.tracks;
     for (int i=0 ; i<32 ; i++) {
         delete t->resampler;
         t++;
     }
     delete [] mState.outputTemp;
     delete [] mState.resampleTemp;
 }

 int AudioMixer::getTrackName()
 {
    uint32_t names = mTrackNames;
    uint32_t mask = 1;
    int n = 0;
    while (names & mask) {
        mask <<= 1;
        n++;
    }
    if (mask) {
        LOGV("add track (%d)", n);
        mTrackNames |= mask;
        return TRACK0 + n;
    }
    return -1;
 }

 void AudioMixer::invalidateState(uint32_t mask)
 {
    if (mask) {
        mState.needsChanged |= mask;
        mState.hook = process__validate;
    }
 }

 void AudioMixer::deleteTrackName(int name)
 {
    name -= TRACK0;
    if (uint32_t(name) < MAX_NUM_TRACKS) {
        LOGV("deleteTrackName(%d)", name);
        track_t& track(mState.tracks[ name ]);
        if (track.enabled != 0) {
            track.enabled = 0;
            invalidateState(1<<name);
        }
        if (track.resampler) {
            // delete  the resampler
            delete track.resampler;
            track.resampler = 0;
            track.sampleRate = mSampleRate;
            invalidateState(1<<name);
        }
        track.volumeInc[0] = 0;
        track.volumeInc[1] = 0;
        mTrackNames &= ~(1<<name);
    }
 }

status_t AudioMixer::enable(int name)
{
    switch (name) {
        case MIXING: {
            if (mState.tracks[ mActiveTrack ].enabled != 1) {
                mState.tracks[ mActiveTrack ].enabled = 1;
                LOGV("enable(%d)", mActiveTrack);
                invalidateState(1<<mActiveTrack);
            }
        } break;
        default:
            return NAME_NOT_FOUND;
    }
    return NO_ERROR;
}

status_t AudioMixer::disable(int name)
{
    switch (name) {
        case MIXING: {
            if (mState.tracks[ mActiveTrack ].enabled != 0) {
                mState.tracks[ mActiveTrack ].enabled = 0;
                LOGV("disable(%d)", mActiveTrack);
                invalidateState(1<<mActiveTrack);
            }
        } break;
        default:
            return NAME_NOT_FOUND;
    }
    return NO_ERROR;
}

status_t AudioMixer::setActiveTrack(int track)
{
    if (uint32_t(track-TRACK0) >= MAX_NUM_TRACKS) {
        return BAD_VALUE;
    }
    mActiveTrack = track - TRACK0;
    return NO_ERROR;
}

status_t AudioMixer::setParameter(int target, int name, int value)
{
    switch (target) {
    case TRACK:
        if (name == CHANNEL_COUNT) {
            if ((uint32_t(value) <= MAX_NUM_CHANNELS) && (value)) {
                if (mState.tracks[ mActiveTrack ].channelCount != value) {
                    mState.tracks[ mActiveTrack ].channelCount = value;
                    LOGV("setParameter(TRACK, CHANNEL_COUNT, %d)", value);
                    invalidateState(1<<mActiveTrack);
                }
                return NO_ERROR;
            }
        }
        break;
    case RESAMPLE:
        if (name == SAMPLE_RATE) {
            if (value > 0) {
                track_t& track = mState.tracks[ mActiveTrack ];
                if (track.setResampler(uint32_t(value), mSampleRate)) {
                    LOGV("setParameter(RESAMPLE, SAMPLE_RATE, %u)",
                            uint32_t(value));
                    invalidateState(1<<mActiveTrack);
                }
                return NO_ERROR;
            }
        }
        break;
    case RAMP_VOLUME:
    case VOLUME:
        if ((uint32_t(name-VOLUME0) < MAX_NUM_CHANNELS)) {
            track_t& track = mState.tracks[ mActiveTrack ];
            if (track.volume[name-VOLUME0] != value) {
                track.volume[name-VOLUME0] = value;
                if (target == VOLUME) {
                    track.prevVolume[name-VOLUME0] = value << 16;
                    track.volumeInc[name-VOLUME0] = 0;
                }
                invalidateState(1<<mActiveTrack);
            }
            return NO_ERROR;
        }
        break;
    }
    return BAD_VALUE;
}

bool AudioMixer::track_t::setResampler(uint32_t value, uint32_t devSampleRate)
{
    if (value!=devSampleRate || resampler) {
        if (sampleRate != value) {
            sampleRate = value;
            if (resampler == 0) {
                resampler = AudioResampler::create(
                        format, channelCount, devSampleRate);
            }
            return true;
        }
    }
    return false;
}

bool AudioMixer::track_t::doesResample() const
{
    return resampler != 0;
}

void AudioMixer::track_t::adjustVolumeRamp(AudioDSP& dsp, size_t frames)
{
    int32_t dynamicRangeCompressionFactor = dsp.estimateLevel(
        static_cast<const int16_t*>(in), int32_t(frames), channelCount
    );

    for (int i = 0; i < 2; i ++) {
        /* Ramp from current to new volume level if necessary */
        int32_t desiredVolume = volume[i] * dynamicRangeCompressionFactor;
        int32_t d = desiredVolume - prevVolume[i];

        /* limit change rate to smooth the compressor. */
        int32_t volChangeLimit = (prevVolume[i] >> 9);

        volChangeLimit -= 1;
        int32_t volInc = d / int32_t(frames);
        if (volInc < -(volChangeLimit)) {
            volInc = -(volChangeLimit);
        }

        /* Make ramps up slower, but ramps down fast. */
        volChangeLimit >>= 4;
        volChangeLimit += 1;
        if (volInc > volChangeLimit) {
            volInc = volChangeLimit;
        }

        volumeInc[i] = volInc;
    }
}


status_t AudioMixer::setBufferProvider(AudioBufferProvider* buffer)
{
    mState.tracks[ mActiveTrack ].bufferProvider = buffer;
    return NO_ERROR;
}



void AudioMixer::process(void* output)
{
    mState.hook(&mState, output, mDsp);
}


void AudioMixer::process__validate(state_t* state, void* output, AudioDSP& dsp)
{
    LOGW_IF(!state->needsChanged,
        "in process__validate() but nothing's invalid");

    uint32_t changed = state->needsChanged;
    state->needsChanged = 0; // clear the validation flag

    // recompute which tracks are enabled / disabled
    uint32_t enabled = 0;
    uint32_t disabled = 0;
    while (changed) {
        const int i = 31 - __builtin_clz(changed);
        const uint32_t mask = 1<<i;
        changed &= ~mask;
        track_t& t = state->tracks[i];
        (t.enabled ? enabled : disabled) |= mask;
    }
    state->enabledTracks &= ~disabled;
    state->enabledTracks |=  enabled;

    // compute everything we need...
    int countActiveTracks = 0;
    int all16BitsStereoNoResample = 1;
    int resampling = 0;
    int volumeRamp = 0;
    uint32_t en = state->enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);

        countActiveTracks++;
        track_t& t = state->tracks[i];
        uint32_t n = 0;
        n |= NEEDS_CHANNEL_1 + t.channelCount - 1;
        n |= NEEDS_FORMAT_16;
        n |= t.doesResample() ? NEEDS_RESAMPLE_ENABLED : NEEDS_RESAMPLE_DISABLED;
       
        if (t.volumeInc[0]|t.volumeInc[1]) {
            volumeRamp = 1;
        } else if (!t.doesResample() && t.volumeRL == 0) {
            n |= NEEDS_MUTE_ENABLED;
        }
        t.needs = n;

        if ((n & NEEDS_MUTE__MASK) == NEEDS_MUTE_ENABLED) {
            t.hook = track__nop;
        } else {
            if ((n & NEEDS_RESAMPLE__MASK) == NEEDS_RESAMPLE_ENABLED) {
                all16BitsStereoNoResample = 0;
                resampling = 1;
                t.hook = track__genericResample;
            } else {
                if ((n & NEEDS_CHANNEL_COUNT__MASK) == NEEDS_CHANNEL_1){
                    t.hook = track__16BitsMono;
                    all16BitsStereoNoResample = 0;
                }
                if ((n & NEEDS_CHANNEL_COUNT__MASK) == NEEDS_CHANNEL_2){
                    t.hook = track__16BitsStereo;
                }
            }
        }
    }

    // select the processing hooks
    state->hook = process__nop;
    if (countActiveTracks) {
        if (resampling) {
            if (!state->outputTemp) {
                state->outputTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            if (!state->resampleTemp) {
                state->resampleTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            state->hook = process__genericResampling;
        } else {
            if (state->outputTemp) {
                delete [] state->outputTemp;
                state->outputTemp = 0;
            }
            if (state->resampleTemp) {
                delete [] state->resampleTemp;
                state->resampleTemp = 0;
            }
            state->hook = process__genericNoResampling;
        }
    }

    LOGV("mixer configuration change: %d activeTracks (%08x) "
        "all16BitsStereoNoResample=%d, resampling=%d, volumeRamp=%d",
        countActiveTracks, state->enabledTracks,
        all16BitsStereoNoResample, resampling, volumeRamp);

   state->hook(state, output, dsp);

   // Now that the volume ramp has been done, set optimal state and
   // track hooks for subsequent mixer process
   if (countActiveTracks) {
       int allMuted = 1;
       uint32_t en = state->enabledTracks;
       while (en) {
           const int i = 31 - __builtin_clz(en);
           en &= ~(1<<i);
           track_t& t = state->tracks[i];
           if (!t.doesResample() && t.volumeRL == 0)
           {
               t.needs |= NEEDS_MUTE_ENABLED;
               t.hook = track__nop;
           } else {
               allMuted = 0;
           }
       }
       if (allMuted) {
           state->hook = process__nop;
       }
   }
}

static inline
int32_t mulAdd(int16_t in, int16_t v, int32_t a)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    asm( "smlabb %[out], %[in], %[v], %[a] \n"
         : [out]"=r"(out)
         : [in]"%r"(in), [v]"r"(v), [a]"r"(a)
         : );
    return out;
#else
    return a + in * int32_t(v);
#endif
}

static inline
int32_t mul(int16_t in, int16_t v)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    asm( "smulbb %[out], %[in], %[v] \n"
         : [out]"=r"(out)
         : [in]"%r"(in), [v]"r"(v)
         : );
    return out;
#else
    return in * int32_t(v);
#endif
}

static inline
int32_t mulAddRL(int left, uint32_t inRL, uint32_t vRL, int32_t a)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    if (left) {
        asm( "smlabb %[out], %[inRL], %[vRL], %[a] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL), [a]"r"(a)
             : );
    } else {
        asm( "smlatt %[out], %[inRL], %[vRL], %[a] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL), [a]"r"(a)
             : );
    }
    return out;
#else
    if (left) {
        return a + int16_t(inRL&0xFFFF) * int16_t(vRL&0xFFFF);
    } else {
        return a + int16_t(inRL>>16) * int16_t(vRL>>16);
    }
#endif
}

static inline
int32_t mulRL(int left, uint32_t inRL, uint32_t vRL)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    if (left) {
        asm( "smulbb %[out], %[inRL], %[vRL] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL)
             : );
    } else {
        asm( "smultt %[out], %[inRL], %[vRL] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL)
             : );
    }
    return out;
#else
    if (left) {
        return int16_t(inRL&0xFFFF) * int16_t(vRL&0xFFFF);
    } else {
        return int16_t(inRL>>16) * int16_t(vRL>>16);
    }
#endif
}


void AudioMixer::track__genericResample(track_t* t, int32_t* out, size_t outFrameCount, int32_t* temp, AudioDSP& dsp)
{
    t->resampler->setSampleRate(t->sampleRate);

    // ramp gain - resample to temp buffer and scale/mix in 2nd step
    if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
        t->resampler->setVolume(UNITY_GAIN, UNITY_GAIN);
        memset(temp, 0, outFrameCount * MAX_NUM_CHANNELS * sizeof(int32_t));
        t->resampler->resample(temp, outFrameCount, t->bufferProvider);
        volumeRampStereo(t, out, outFrameCount, temp, dsp);
    }

    // constant gain
    else {
        t->resampler->setVolume(t->volume[0], t->volume[1]);
        t->resampler->resample(out, outFrameCount, t->bufferProvider);
    }
}

void AudioMixer::track__nop(track_t* t, int32_t* out, size_t outFrameCount, int32_t* temp, AudioDSP& dsp)
{
}

void AudioMixer::volumeRampStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, AudioDSP& dsp)
{
    t->adjustVolumeRamp(dsp, frameCount);
    int32_t vl = t->prevVolume[0];
    int32_t vr = t->prevVolume[1];
    const int32_t vlInc = t->volumeInc[0];
    const int32_t vrInc = t->volumeInc[1];

    //LOGD("[0] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
    //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
    //       (vl + vlInc*frameCount)/65536.0f, frameCount);
   
    // ramp volume
    do {
        *out++ += (vl >> 16) * (*temp++ >> 12);
        *out++ += (vr >> 16) * (*temp++ >> 12);
        vl += vlInc;
        vr += vrInc;
    } while (--frameCount);

    t->prevVolume[0] = vl;
    t->prevVolume[1] = vr;
}

void AudioMixer::track__16BitsStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, AudioDSP& dsp)
{
    int16_t const *in = static_cast<int16_t const *>(t->in);

    // ramp gain
    t->adjustVolumeRamp(dsp, frameCount);
    if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
        int32_t vl = t->prevVolume[0];
        int32_t vr = t->prevVolume[1];
        const int32_t vlInc = t->volumeInc[0];
        const int32_t vrInc = t->volumeInc[1];

        // LOGD("[1] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
        //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
        //        (vl + vlInc*frameCount)/65536.0f, frameCount);

        do {
            *out++ += (vl >> 16) * (int32_t) *in++;
            *out++ += (vr >> 16) * (int32_t) *in++;
            vl += vlInc;
            vr += vrInc;
        } while (--frameCount);
       
        t->prevVolume[0] = vl;
        t->prevVolume[1] = vr;
    }

    // constant gain
    else {
        const uint32_t vrl = t->volumeRL;
        do {
            uint32_t rl = *reinterpret_cast<uint32_t const *>(in);
            in += 2;
            out[0] = mulAddRL(1, rl, vrl, out[0]);
            out[1] = mulAddRL(0, rl, vrl, out[1]);
            out += 2;
        } while (--frameCount);
    }
    t->in = in;
}

void AudioMixer::track__16BitsMono(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, AudioDSP& dsp)
{
    int16_t const *in = static_cast<int16_t const *>(t->in);

    // ramp gain
    t->adjustVolumeRamp(dsp, frameCount);
    if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
        int32_t vl = t->prevVolume[0];
        int32_t vr = t->prevVolume[1];
        const int32_t vlInc = t->volumeInc[0];
        const int32_t vrInc = t->volumeInc[1];

        // LOGD("[2] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
        //         t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
        //         (vl + vlInc*frameCount)/65536.0f, frameCount);

        do {
            int32_t l = *in++;
            *out++ += (vl >> 16) * l;
            *out++ += (vr >> 16) * l;
            vl += vlInc;
            vr += vrInc;
        } while (--frameCount);
       
        t->prevVolume[0] = vl;
        t->prevVolume[1] = vr;
    }
    // constant gain
    else {
        const int16_t vl = t->volume[0];
        const int16_t vr = t->volume[1];
        do {
            int16_t l = *in++;
            out[0] = mulAdd(l, vl, out[0]);
            out[1] = mulAdd(l, vr, out[1]);
            out += 2;
        } while (--frameCount);
    }
    t->in = in;
}

int32_t AudioMixer::lipshitz(int32_t* state, int32_t input)
{
#define COEFF(x) int32_t(x * 4096.0f + 0.5f)
    int32_t output =
          COEFF(-2.033f) * input
        + COEFF(+2.165f) * state[0]
        + COEFF(-1.959f) * state[1]
        + COEFF(+1.590f) * state[2]
        + COEFF(-0.6149f) * state[3];
#undef COEFF

    state[3] = state[2];
    state[2] = state[1];
    state[1] = state[0];
    state[0] = input;

    return output >> 12;
}


void AudioMixer::ditherAndClamp(dither_t* state, int32_t* out, int32_t const *sums, size_t c)
{
    for (size_t i=0 ; i<c ; i++) {
        int32_t l = *sums++;
        int32_t r = *sums++;

        /* Noise-shaped dither function. */

        /* High-passed Triangular PDF according to
         * "A Theory of Nonsubtractive Dither" by Robert Wannamaker et al.
         * Other software seems to prefer (prng() + prng()) >> 1 as the
         * random source, which they highpass, but that distribution is not
         * triangular. */
        int32_t newDither = prng();
        int32_t dithering = newDither - state->oldDither;
        state->oldDither = newDither;

        l += lipshitz(state->lipshitzL, state->errorL) + dithering;
        r += lipshitz(state->lipshitzR, state->errorR) + dithering;
        state->errorL = l & 0xfff;
        state->errorR = r & 0xfff;

        l = clamp16(l >> 12);
        r = clamp16(r >> 12);

        *out++ = (r<<16) | (l & 0xFFFF);
    }
}

// no-op case
void AudioMixer::process__nop(state_t* state, void* output, AudioDSP& dsp)
{
    // this assumes output 16 bits stereo, no resampling
    memset(output, 0, state->frameCount*4);
    uint32_t en = state->enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);
        track_t& t = state->tracks[i];
        size_t outFrames = state->frameCount;
        while (outFrames) {
            t.buffer.frameCount = outFrames;
            t.bufferProvider->getNextBuffer(&t.buffer);
            if (!t.buffer.raw) break;
            outFrames -= t.buffer.frameCount;
            t.bufferProvider->releaseBuffer(&t.buffer);
        }
    }
}

// generic code without resampling
void AudioMixer::process__genericNoResampling(state_t* state, void* output, AudioDSP& dsp)
{
    int32_t outTemp[BLOCKSIZE * MAX_NUM_CHANNELS] __attribute__((aligned(32)));

    // acquire each track's buffer
    uint32_t enabledTracks = state->enabledTracks;
    uint32_t en = enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.buffer.frameCount = state->frameCount;
        t.bufferProvider->getNextBuffer(&t.buffer);
        t.frameCount = t.buffer.frameCount;
        t.in = t.buffer.raw;
        // t.in == NULL can happen if the track was flushed just after having
        // been enabled for mixing.
        if (t.in == NULL)
            enabledTracks &= ~(1<<i);
    }

    // this assumes output 16 bits stereo, no resampling
    int32_t* out = static_cast<int32_t*>(output);
    size_t numFrames = state->frameCount;
    do {
        memset(outTemp, 0, sizeof(outTemp));

        en = enabledTracks;
        while (en) {
            const int i = 31 - __builtin_clz(en);
            en &= ~(1<<i);
            track_t& t = state->tracks[i];
            size_t outFrames = BLOCKSIZE;
           
            while (outFrames) {
                size_t inFrames = (t.frameCount > outFrames)?outFrames:t.frameCount;
                if (inFrames) {
                    (t.hook)(&t, outTemp + (BLOCKSIZE-outFrames)*MAX_NUM_CHANNELS, inFrames, state->resampleTemp, dsp);
                    t.frameCount -= inFrames;
                    outFrames -= inFrames;
                }
                if (t.frameCount == 0 && outFrames) {
                    t.bufferProvider->releaseBuffer(&t.buffer);
                    t.buffer.frameCount = numFrames - (BLOCKSIZE - outFrames);
                    t.bufferProvider->getNextBuffer(&t.buffer);
                    t.in = t.buffer.raw;
                    if (t.in == NULL) {
                        enabledTracks &= ~(1<<i);
                        break;
                    }
                    t.frameCount = t.buffer.frameCount;
                 }
            }
        }

        dsp.process(outTemp, BLOCKSIZE);
        ditherAndClamp(&state->dither, out, outTemp, BLOCKSIZE);
        out += BLOCKSIZE;
        numFrames -= BLOCKSIZE;
    } while (numFrames);


    // release each track's buffer
    en = enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.bufferProvider->releaseBuffer(&t.buffer);
    }
}

// generic code with resampling
void AudioMixer::process__genericResampling(state_t* state, void* output, AudioDSP& dsp)
{
    int32_t* const outTemp = state->outputTemp;
    const size_t size = sizeof(int32_t) * MAX_NUM_CHANNELS * state->frameCount;
    memset(outTemp, 0, size);

    int32_t* out = static_cast<int32_t*>(output);
    size_t numFrames = state->frameCount;

    uint32_t en = state->enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);
        track_t& t = state->tracks[i];

        // this is a little goofy, on the resampling case we don't
        // acquire/release the buffers because it's done by
        // the resampler.
        if ((t.needs & NEEDS_RESAMPLE__MASK) == NEEDS_RESAMPLE_ENABLED) {
            (t.hook)(&t, outTemp, numFrames, state->resampleTemp, dsp);
        } else {

            size_t outFrames = numFrames;
           
            while (outFrames) {
                t.buffer.frameCount = outFrames;
                t.bufferProvider->getNextBuffer(&t.buffer);
                t.in = t.buffer.raw;
                // t.in == NULL can happen if the track was flushed just after having
                // been enabled for mixing.
                if (t.in == NULL) break;

                (t.hook)(&t, outTemp + (numFrames-outFrames)*MAX_NUM_CHANNELS, t.buffer.frameCount, state->resampleTemp, dsp);
                outFrames -= t.buffer.frameCount;
                t.bufferProvider->releaseBuffer(&t.buffer);
            }
        }
    }

    dsp.process(outTemp, numFrames);
    ditherAndClamp(&state->dither, out, outTemp, numFrames);
}

// ----------------------------------------------------------------------------
}; // namespace android

