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

#ifndef ANDROID_IAUDIOFLINGER_H
#define ANDROID_IAUDIOFLINGER_H

#include <stdint.h>
#include <sys/types.h>
#include <unistd.h>

#include <utils/RefBase.h>
#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <media/IAudioTrack.h>
#include <media/IAudioRecord.h>
#include <media/IAudioFlingerClient.h>
#include <media/EffectApi.h>
#include <media/IEffect.h>
#include <media/IEffectClient.h>
#include <utils/String8.h>

namespace android {

// ----------------------------------------------------------------------------

class IAudioFlinger : public IInterface
{
public:
    DECLARE_META_INTERFACE(AudioFlinger);

    /* create an audio track and registers it with AudioFlinger.
     * return null if the track cannot be created.
     */
    virtual sp<IAudioTrack> createTrack(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int frameCount,
                                uint32_t flags,
                                const sp<IMemory>& sharedBuffer,
                                int output,
                                int *sessionId,
                                status_t *status) = 0;

    virtual sp<IAudioRecord> openRecord(
                                pid_t pid,
                                int input,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int frameCount,
                                uint32_t flags,
                                int *sessionId,
                                status_t *status) = 0;

    /* query the audio hardware state. This state never changes,
     * and therefore can be cached.
     */
    virtual     uint32_t    sampleRate(int output) const = 0;
    virtual     int         channelCount(int output) const = 0;
    virtual     int         format(int output) const = 0;
    virtual     size_t      frameCount(int output) const = 0;
    virtual     uint32_t    latency(int output) const = 0;

    /* set/get the audio hardware state. This will probably be used by
     * the preference panel, mostly.
     */
    virtual     status_t    setMasterVolume(float value) = 0;
    virtual     status_t    setMasterMute(bool muted) = 0;

    virtual     float       masterVolume() const = 0;
    virtual     bool        masterMute() const = 0;

    /* set/get stream type state. This will probably be used by
     * the preference panel, mostly.
     */
    virtual     status_t    setStreamVolume(int stream, float value, int output) = 0;
    virtual     status_t    setStreamMute(int stream, bool muted) = 0;
#ifdef OMAP_ENHANCEMENT
    /* This will tell playback thread that FM Rx is active
     * and device will not go to standby
     */
    virtual     status_t    setFMRxActive(bool state)=0;
#endif
    virtual     float       streamVolume(int stream, int output) const = 0;
    virtual     bool        streamMute(int stream) const = 0;

    // set audio mode
    virtual     status_t    setMode(int mode) = 0;

    // mic mute/state
    virtual     status_t    setMicMute(bool state) = 0;
    virtual     bool        getMicMute() const = 0;

    // is any track active on this stream?
    virtual     bool        isStreamActive(int stream) const = 0;

    virtual     status_t    setParameters(int ioHandle, const String8& keyValuePairs) = 0;
    virtual     String8     getParameters(int ioHandle, const String8& keys) = 0;

    // register a current process for audio output change notifications
    virtual void registerClient(const sp<IAudioFlingerClient>& client) = 0;

    // retrieve the audio recording buffer size
    virtual size_t getInputBufferSize(uint32_t sampleRate, int format, int channelCount) = 0;

    virtual int openOutput(uint32_t *pDevices,
                                    uint32_t *pSamplingRate,
                                    uint32_t *pFormat,
                                    uint32_t *pChannels,
                                    uint32_t *pLatencyMs,
                                    uint32_t flags) = 0;
    virtual int openDuplicateOutput(int output1, int output2) = 0;
    virtual status_t closeOutput(int output) = 0;
    virtual status_t suspendOutput(int output) = 0;
    virtual status_t restoreOutput(int output) = 0;

    virtual int openInput(uint32_t *pDevices,
                                    uint32_t *pSamplingRate,
                                    uint32_t *pFormat,
                                    uint32_t *pChannels,
                                    uint32_t acoustics) = 0;
    virtual status_t closeInput(int input) = 0;

    virtual status_t setStreamOutput(uint32_t stream, int output) = 0;

    virtual status_t setVoiceVolume(float volume) = 0;

    virtual status_t getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames, int output) = 0;

    virtual unsigned int  getInputFramesLost(int ioHandle) = 0;

    virtual int newAudioSessionId() = 0;

    virtual status_t loadEffectLibrary(const char *libPath, int *handle) = 0;

    virtual status_t unloadEffectLibrary(int handle) = 0;

    virtual status_t queryNumberEffects(uint32_t *numEffects) = 0;

    virtual status_t queryEffect(uint32_t index, effect_descriptor_t *pDescriptor) = 0;

    virtual status_t getEffectDescriptor(effect_uuid_t *pEffectUUID, effect_descriptor_t *pDescriptor) = 0;

    virtual sp<IEffect> createEffect(pid_t pid,
                                    effect_descriptor_t *pDesc,
                                    const sp<IEffectClient>& client,
                                    int32_t priority,
                                    int output,
                                    int sessionId,
                                    status_t *status,
                                    int *id,
                                    int *enabled) = 0;

    virtual status_t moveEffects(int session, int srcOutput, int dstOutput) = 0;

#ifdef HAVE_FM_RADIO
    virtual status_t setFmVolume(float volume) = 0;
#endif
};


// ----------------------------------------------------------------------------

class BnAudioFlinger : public BnInterface<IAudioFlinger>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IAUDIOFLINGER_H
