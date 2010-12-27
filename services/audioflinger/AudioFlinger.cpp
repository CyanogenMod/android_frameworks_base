/* //device/include/server/AudioFlinger/AudioFlinger.cpp
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


#define LOG_TAG "AudioFlinger"
//#define LOG_NDEBUG 0

#include <math.h>
#include <signal.h>
#include <sys/time.h>
#include <sys/resource.h>

#include <binder/IServiceManager.h>
#include <utils/Log.h>
#include <binder/Parcel.h>
#include <binder/IPCThreadState.h>
#include <utils/String16.h>
#include <utils/threads.h>

#include <cutils/properties.h>

#include <media/AudioTrack.h>
#include <media/AudioRecord.h>

#include <private/media/AudioTrackShared.h>
#include <private/media/AudioEffectShared.h>
#include <hardware_legacy/AudioHardwareInterface.h>

#include "AudioMixer.h"
#include "AudioFlinger.h"

#ifdef WITH_A2DP
#include "A2dpAudioInterface.h"
#endif

#ifdef LVMX
#include "lifevibes.h"
#endif

#include <media/EffectsFactoryApi.h>
#include <media/EffectVisualizerApi.h>

// ----------------------------------------------------------------------------
// the sim build doesn't have gettid

#ifndef HAVE_GETTID
# define gettid getpid
#endif

// ----------------------------------------------------------------------------

extern const char * const gEffectLibPath;

namespace android {

static const char* kDeadlockedString = "AudioFlinger may be deadlocked\n";
static const char* kHardwareLockedString = "Hardware lock is taken\n";

//static const nsecs_t kStandbyTimeInNsecs = seconds(3);
static const float MAX_GAIN = 4096.0f;
static const float MAX_GAIN_INT = 0x1000;

// retry counts for buffer fill timeout
// 50 * ~20msecs = 1 second
static const int8_t kMaxTrackRetries = 50;
static const int8_t kMaxTrackStartupRetries = 50;
// allow less retry attempts on direct output thread.
// direct outputs can be a scarce resource in audio hardware and should
// be released as quickly as possible.
static const int8_t kMaxTrackRetriesDirect = 2;

static const int kDumpLockRetries = 50;
static const int kDumpLockSleep = 20000;

static const nsecs_t kWarningThrottle = seconds(5);


#define AUDIOFLINGER_SECURITY_ENABLED 1

// ----------------------------------------------------------------------------

static bool recordingAllowed() {
#ifndef HAVE_ANDROID_OS
    return true;
#endif
#if AUDIOFLINGER_SECURITY_ENABLED
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16("android.permission.RECORD_AUDIO"));
    if (!ok) LOGE("Request requires android.permission.RECORD_AUDIO");
    return ok;
#else
    if (!checkCallingPermission(String16("android.permission.RECORD_AUDIO")))
        LOGW("WARNING: Need to add android.permission.RECORD_AUDIO to manifest");
    return true;
#endif
}

static bool settingsAllowed() {
#ifndef HAVE_ANDROID_OS
    return true;
#endif
#if AUDIOFLINGER_SECURITY_ENABLED
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16("android.permission.MODIFY_AUDIO_SETTINGS"));
    if (!ok) LOGE("Request requires android.permission.MODIFY_AUDIO_SETTINGS");
    return ok;
#else
    if (!checkCallingPermission(String16("android.permission.MODIFY_AUDIO_SETTINGS")))
        LOGW("WARNING: Need to add android.permission.MODIFY_AUDIO_SETTINGS to manifest");
    return true;
#endif
}

// ----------------------------------------------------------------------------
AudioFlinger::AudioFlinger()
    : BnAudioFlinger(),
#ifdef HAVE_FM_RADIO
        mAudioHardware(0), mMasterVolume(1.0f), mMasterMute(false), mNextUniqueId(1),  mFmOn(false)
#else
        mAudioHardware(0), mMasterVolume(1.0f), mMasterMute(false), mNextUniqueId(1)
#endif
{
    mHardwareStatus = AUDIO_HW_IDLE;

    mAudioHardware = AudioHardwareInterface::create();

    mHardwareStatus = AUDIO_HW_INIT;
    if (mAudioHardware->initCheck() == NO_ERROR) {
        // open 16-bit output stream for s/w mixer
        mMode = AudioSystem::MODE_NORMAL;
        setMode(mMode);

        setMasterVolume(1.0f);
        setMasterMute(false);
    } else {
        LOGE("Couldn't even initialize the stubbed audio hardware!");
    }
#ifdef LVMX
    LifeVibes::init();
    mLifeVibesClientPid = -1;
#endif
}

AudioFlinger::~AudioFlinger()
{
    while (!mRecordThreads.isEmpty()) {
        // closeInput() will remove first entry from mRecordThreads
        closeInput(mRecordThreads.keyAt(0));
    }
    while (!mPlaybackThreads.isEmpty()) {
        // closeOutput() will remove first entry from mPlaybackThreads
        closeOutput(mPlaybackThreads.keyAt(0));
    }
    if (mAudioHardware) {
        delete mAudioHardware;
    }
}



status_t AudioFlinger::dumpClients(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    result.append("Clients:\n");
    for (size_t i = 0; i < mClients.size(); ++i) {
        wp<Client> wClient = mClients.valueAt(i);
        if (wClient != 0) {
            sp<Client> client = wClient.promote();
            if (client != 0) {
                snprintf(buffer, SIZE, "  pid: %d\n", client->pid());
                result.append(buffer);
            }
        }
    }
    write(fd, result.string(), result.size());
    return NO_ERROR;
}


status_t AudioFlinger::dumpInternals(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    int hardwareStatus = mHardwareStatus;

    snprintf(buffer, SIZE, "Hardware status: %d\n", hardwareStatus);
    result.append(buffer);
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

status_t AudioFlinger::dumpPermissionDenial(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    snprintf(buffer, SIZE, "Permission Denial: "
            "can't dump AudioFlinger from pid=%d, uid=%d\n",
            IPCThreadState::self()->getCallingPid(),
            IPCThreadState::self()->getCallingUid());
    result.append(buffer);
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

static bool tryLock(Mutex& mutex)
{
    bool locked = false;
    for (int i = 0; i < kDumpLockRetries; ++i) {
        if (mutex.tryLock() == NO_ERROR) {
            locked = true;
            break;
        }
        usleep(kDumpLockSleep);
    }
    return locked;
}

status_t AudioFlinger::dump(int fd, const Vector<String16>& args)
{
    if (checkCallingPermission(String16("android.permission.DUMP")) == false) {
        dumpPermissionDenial(fd, args);
    } else {
        // get state of hardware lock
        bool hardwareLocked = tryLock(mHardwareLock);
        if (!hardwareLocked) {
            String8 result(kHardwareLockedString);
            write(fd, result.string(), result.size());
        } else {
            mHardwareLock.unlock();
        }

        bool locked = tryLock(mLock);

        // failed to lock - AudioFlinger is probably deadlocked
        if (!locked) {
            String8 result(kDeadlockedString);
            write(fd, result.string(), result.size());
        }

        dumpClients(fd, args);
        dumpInternals(fd, args);

        // dump playback threads
        for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
            mPlaybackThreads.valueAt(i)->dump(fd, args);
        }

        // dump record threads
        for (size_t i = 0; i < mRecordThreads.size(); i++) {
            mRecordThreads.valueAt(i)->dump(fd, args);
        }

        if (mAudioHardware) {
            mAudioHardware->dumpState(fd, args);
        }
        if (locked) mLock.unlock();
    }
    return NO_ERROR;
}


// IAudioFlinger interface


sp<IAudioTrack> AudioFlinger::createTrack(
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
        status_t *status)
{
    sp<PlaybackThread::Track> track;
    sp<TrackHandle> trackHandle;
    sp<Client> client;
    wp<Client> wclient;
    status_t lStatus;
    int lSessionId;

    if (streamType >= AudioSystem::NUM_STREAM_TYPES) {
        LOGE("invalid stream type");
        lStatus = BAD_VALUE;
        goto Exit;
    }

    {
        Mutex::Autolock _l(mLock);
        PlaybackThread *thread = checkPlaybackThread_l(output);
        PlaybackThread *effectThread = NULL;
        if (thread == NULL) {
            LOGE("unknown output thread");
            lStatus = BAD_VALUE;
            goto Exit;
        }

        wclient = mClients.valueFor(pid);

        if (wclient != NULL) {
            client = wclient.promote();
        } else {
            client = new Client(this, pid);
            mClients.add(pid, client);
        }

        LOGV("createTrack() sessionId: %d", (sessionId == NULL) ? -2 : *sessionId);
        if (sessionId != NULL && *sessionId != AudioSystem::SESSION_OUTPUT_MIX) {
            for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
                sp<PlaybackThread> t = mPlaybackThreads.valueAt(i);
                if (mPlaybackThreads.keyAt(i) != output) {
                    // prevent same audio session on different output threads
                    uint32_t sessions = t->hasAudioSession(*sessionId);
                    if (sessions & PlaybackThread::TRACK_SESSION) {
                        lStatus = BAD_VALUE;
                        goto Exit;
                    }
                    // check if an effect with same session ID is waiting for a track to be created
                    if (sessions & PlaybackThread::EFFECT_SESSION) {
                        effectThread = t.get();
                    }
                }
            }
            lSessionId = *sessionId;
        } else {
            // if no audio session id is provided, create one here
            lSessionId = nextUniqueId();
            if (sessionId != NULL) {
                *sessionId = lSessionId;
            }
        }
        LOGV("createTrack() lSessionId: %d", lSessionId);

        track = thread->createTrack_l(client, streamType, sampleRate, format,
                channelCount, frameCount, sharedBuffer, lSessionId, &lStatus);

        // move effect chain to this output thread if an effect on same session was waiting
        // for a track to be created
        if (lStatus == NO_ERROR && effectThread != NULL) {
            Mutex::Autolock _dl(thread->mLock);
            Mutex::Autolock _sl(effectThread->mLock);
            moveEffectChain_l(lSessionId, effectThread, thread, true);
        }
    }
    if (lStatus == NO_ERROR) {
        trackHandle = new TrackHandle(track);
    } else {
        // remove local strong reference to Client before deleting the Track so that the Client
        // destructor is called by the TrackBase destructor with mLock held
        client.clear();
        track.clear();
    }

Exit:
    if(status) {
        *status = lStatus;
    }
    return trackHandle;
}

uint32_t AudioFlinger::sampleRate(int output) const
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread == NULL) {
        LOGW("sampleRate() unknown thread %d", output);
        return 0;
    }
    return thread->sampleRate();
}

int AudioFlinger::channelCount(int output) const
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread == NULL) {
        LOGW("channelCount() unknown thread %d", output);
        return 0;
    }
    return thread->channelCount();
}

int AudioFlinger::format(int output) const
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread == NULL) {
        LOGW("format() unknown thread %d", output);
        return 0;
    }
    return thread->format();
}

size_t AudioFlinger::frameCount(int output) const
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread == NULL) {
        LOGW("frameCount() unknown thread %d", output);
        return 0;
    }
    return thread->frameCount();
}

uint32_t AudioFlinger::latency(int output) const
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread == NULL) {
        LOGW("latency() unknown thread %d", output);
        return 0;
    }
    return thread->latency();
}

status_t AudioFlinger::setMasterVolume(float value)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    // when hw supports master volume, don't scale in sw mixer
    AutoMutex lock(mHardwareLock);
    mHardwareStatus = AUDIO_HW_SET_MASTER_VOLUME;
    if (mAudioHardware->setMasterVolume(value) == NO_ERROR) {
        value = 1.0f;
    }
    mHardwareStatus = AUDIO_HW_IDLE;

    mMasterVolume = value;
    for (uint32_t i = 0; i < mPlaybackThreads.size(); i++)
       mPlaybackThreads.valueAt(i)->setMasterVolume(value);

    return NO_ERROR;
}

status_t AudioFlinger::setMode(int mode)
{
    status_t ret;

    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }
    if ((mode < 0) || (mode >= AudioSystem::NUM_MODES)) {
        LOGW("Illegal value: setMode(%d)", mode);
        return BAD_VALUE;
    }

    { // scope for the lock
        AutoMutex lock(mHardwareLock);
        mHardwareStatus = AUDIO_HW_SET_MODE;
        ret = mAudioHardware->setMode(mode);
        mHardwareStatus = AUDIO_HW_IDLE;
    }

    if (NO_ERROR == ret) {
        Mutex::Autolock _l(mLock);
        mMode = mode;
        for (uint32_t i = 0; i < mPlaybackThreads.size(); i++)
           mPlaybackThreads.valueAt(i)->setMode(mode);
#ifdef LVMX
        LifeVibes::setMode(mode);
#endif
    }

    return ret;
}

status_t AudioFlinger::setMicMute(bool state)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    AutoMutex lock(mHardwareLock);
    mHardwareStatus = AUDIO_HW_SET_MIC_MUTE;
    status_t ret = mAudioHardware->setMicMute(state);
    mHardwareStatus = AUDIO_HW_IDLE;
    return ret;
}

bool AudioFlinger::getMicMute() const
{
    bool state = AudioSystem::MODE_INVALID;
    mHardwareStatus = AUDIO_HW_GET_MIC_MUTE;
    mAudioHardware->getMicMute(&state);
    mHardwareStatus = AUDIO_HW_IDLE;
    return state;
}

status_t AudioFlinger::setMasterMute(bool muted)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    mMasterMute = muted;
    for (uint32_t i = 0; i < mPlaybackThreads.size(); i++)
       mPlaybackThreads.valueAt(i)->setMasterMute(muted);

    return NO_ERROR;
}

float AudioFlinger::masterVolume() const
{
    return mMasterVolume;
}

bool AudioFlinger::masterMute() const
{
    return mMasterMute;
}

status_t AudioFlinger::setStreamVolume(int stream, float value, int output)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    if (stream < 0 || uint32_t(stream) >= AudioSystem::NUM_STREAM_TYPES) {
        return BAD_VALUE;
    }

    AutoMutex lock(mLock);
    PlaybackThread *thread = NULL;
    if (output) {
        thread = checkPlaybackThread_l(output);
        if (thread == NULL) {
            return BAD_VALUE;
        }
    }

    mStreamTypes[stream].volume = value;

    if (thread == NULL) {
        for (uint32_t i = 0; i < mPlaybackThreads.size(); i++) {
           mPlaybackThreads.valueAt(i)->setStreamVolume(stream, value);
        }
    } else {
        thread->setStreamVolume(stream, value);
    }

    return NO_ERROR;
}

status_t AudioFlinger::setStreamMute(int stream, bool muted)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    if (stream < 0 || uint32_t(stream) >= AudioSystem::NUM_STREAM_TYPES ||
        uint32_t(stream) == AudioSystem::ENFORCED_AUDIBLE) {
        return BAD_VALUE;
    }

    mStreamTypes[stream].mute = muted;
    for (uint32_t i = 0; i < mPlaybackThreads.size(); i++)
       mPlaybackThreads.valueAt(i)->setStreamMute(stream, muted);

    return NO_ERROR;
}

float AudioFlinger::streamVolume(int stream, int output) const
{
    if (stream < 0 || uint32_t(stream) >= AudioSystem::NUM_STREAM_TYPES) {
        return 0.0f;
    }

    AutoMutex lock(mLock);
    float volume;
    if (output) {
        PlaybackThread *thread = checkPlaybackThread_l(output);
        if (thread == NULL) {
            return 0.0f;
        }
        volume = thread->streamVolume(stream);
    } else {
        volume = mStreamTypes[stream].volume;
    }

    return volume;
}

bool AudioFlinger::streamMute(int stream) const
{
    if (stream < 0 || stream >= (int)AudioSystem::NUM_STREAM_TYPES) {
        return true;
    }

    return mStreamTypes[stream].mute;
}

bool AudioFlinger::isStreamActive(int stream) const
{
    Mutex::Autolock _l(mLock);
    for (uint32_t i = 0; i < mPlaybackThreads.size(); i++) {
        if (mPlaybackThreads.valueAt(i)->isStreamActive(stream)) {
            return true;
        }
    }
#ifdef HAVE_FM_RADIO
    if (mFmOn && stream == AudioSystem::MUSIC) {
        return true;
    }
#endif
    return false;
}

status_t AudioFlinger::setParameters(int ioHandle, const String8& keyValuePairs)
{
    status_t result;

    LOGD("setParameters(): io %d, keyvalue %s, tid %d, calling tid %d",
            ioHandle, keyValuePairs.string(), gettid(), IPCThreadState::self()->getCallingPid());
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

#ifdef LVMX
    AudioParameter param = AudioParameter(keyValuePairs);
    LifeVibes::setParameters(ioHandle,keyValuePairs);
    String8 key = String8(AudioParameter::keyRouting);
    int device;
    if (NO_ERROR != param.getInt(key, device)) {
        device = -1;
    }

    key = String8(LifevibesTag);
    String8 value;
    int musicEnabled = -1;
    if (NO_ERROR == param.get(key, value)) {
        if (value == LifevibesEnable) {
            mLifeVibesClientPid = IPCThreadState::self()->getCallingPid();
            musicEnabled = 1;
        } else if (value == LifevibesDisable) {
            mLifeVibesClientPid = -1;
            musicEnabled = 0;
        }
    }
#endif
#ifdef HAVE_FM_RADIO
    AudioParameter param = AudioParameter(keyValuePairs);
    String8 key = String8(AudioParameter::keyRouting);
    int device;
    if (param.getInt(key, device) == NO_ERROR) {
        if((device & AudioSystem::DEVICE_OUT_FM_ALL) && mFmOn == false){
            mFmOn = true;
         } else if (mFmOn == true && !(device & AudioSystem::DEVICE_OUT_FM_ALL)){
            mFmOn = false;
         }
    }

    String8 fmOnKey = String8(AudioParameter::keyFmOn);
    String8 fmOffKey = String8(AudioParameter::keyFmOff);
    if (param.getInt(fmOnKey, device) == NO_ERROR) {
        mFmOn = true;
        // Call hardware to switch FM on/off
        mAudioHardware->setParameters(keyValuePairs);
    } else if (param.getInt(fmOffKey, device) == NO_ERROR) {
        mFmOn = false;
        // Call hardware to switch FM on/off
        mAudioHardware->setParameters(keyValuePairs);
    }
#endif
    // ioHandle == 0 means the parameters are global to the audio hardware interface
    if (ioHandle == 0) {
        AutoMutex lock(mHardwareLock);
        mHardwareStatus = AUDIO_SET_PARAMETER;
        result = mAudioHardware->setParameters(keyValuePairs);
#ifdef LVMX
        if (musicEnabled != -1) {
            LifeVibes::enableMusic((bool) musicEnabled);
        }
#endif
        mHardwareStatus = AUDIO_HW_IDLE;
        return result;
    }

    // hold a strong ref on thread in case closeOutput() or closeInput() is called
    // and the thread is exited once the lock is released
    sp<ThreadBase> thread;
    {
        Mutex::Autolock _l(mLock);
        thread = checkPlaybackThread_l(ioHandle);
        if (thread == NULL) {
            thread = checkRecordThread_l(ioHandle);
        }
    }
    if (thread != NULL) {
        result = thread->setParameters(keyValuePairs);
#ifdef LVMX
        if ((NO_ERROR == result) && (device != -1)) {
            LifeVibes::setDevice(LifeVibes::threadIdToAudioOutputType(thread->id()), device);
        }
#endif
        return result;
    }
    return BAD_VALUE;
}

String8 AudioFlinger::getParameters(int ioHandle, const String8& keys)
{
//    LOGV("getParameters() io %d, keys %s, tid %d, calling tid %d",
//            ioHandle, keys.string(), gettid(), IPCThreadState::self()->getCallingPid());

    if (ioHandle == 0) {
        return mAudioHardware->getParameters(keys);
    }

    Mutex::Autolock _l(mLock);

    PlaybackThread *playbackThread = checkPlaybackThread_l(ioHandle);
    if (playbackThread != NULL) {
        return playbackThread->getParameters(keys);
    }
    RecordThread *recordThread = checkRecordThread_l(ioHandle);
    if (recordThread != NULL) {
        return recordThread->getParameters(keys);
    }
    return String8("");
}

size_t AudioFlinger::getInputBufferSize(uint32_t sampleRate, int format, int channelCount)
{
    return mAudioHardware->getInputBufferSize(sampleRate, format, channelCount);
}

unsigned int AudioFlinger::getInputFramesLost(int ioHandle)
{
    if (ioHandle == 0) {
        return 0;
    }

    Mutex::Autolock _l(mLock);

    RecordThread *recordThread = checkRecordThread_l(ioHandle);
    if (recordThread != NULL) {
        return recordThread->getInputFramesLost();
    }
    return 0;
}

status_t AudioFlinger::setVoiceVolume(float value)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    AutoMutex lock(mHardwareLock);
    mHardwareStatus = AUDIO_SET_VOICE_VOLUME;
    status_t ret = mAudioHardware->setVoiceVolume(value);
    mHardwareStatus = AUDIO_HW_IDLE;

    return ret;
}

status_t AudioFlinger::getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames, int output)
{
    status_t status;

    Mutex::Autolock _l(mLock);

    PlaybackThread *playbackThread = checkPlaybackThread_l(output);
    if (playbackThread != NULL) {
        return playbackThread->getRenderPosition(halFrames, dspFrames);
    }

    return BAD_VALUE;
}

#ifdef HAVE_FM_RADIO
#ifdef USE_BROADCOM_FM_VOLUME_HACK
/*
 * NASTY HACK: Send raw HCI data to adjust the FM volume.
 *
 * Normally we would do this in libaudio, but this is for the case where
 * we have a prebuilt libaudio and cannot modify it.
 */
static status_t set_volume_fm(uint32_t volume)
{
    int returnval = 0;
    float ratio = 2.5;

     char s1[100] = "hcitool cmd 0x3f 0xa 0x5 0xc0 0x41 0xf 0 0x20 0 0 0";
     char s2[100] = "hcitool cmd 0x3f 0xa 0x5 0xe4 0x41 0xf 0 0x00 0 0 0";
     char s3[100] = "hcitool cmd 0x3f 0xa 0x5 0xe0 0x41 0xf 0 ";

     char stemp[10] = "";
     char *pstarget = s3;

     volume = (unsigned int)(volume * ratio);

     sprintf(stemp, "0x%x ", volume);
     pstarget = strcat(s3, stemp);
     pstarget = strcat(s3, "0 0 0");

     system(s1);
     system(s2);
     system(s3);

     return returnval;
}
#endif

status_t AudioFlinger::setFmVolume(float value)
{
	status_t ret;

    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    AutoMutex lock(mHardwareLock);
    mHardwareStatus = AUDIO_SET_FM_VOLUME;
#ifdef USE_BROADCOM_FM_VOLUME_HACK
    int vol = AudioSystem::logToLinear(value);
    LOGI("setFmVolume %d", vol);
    ret = set_volume_fm(vol);
#else
    ret = mAudioHardware->setFmVolume(value);
#endif
    mHardwareStatus = AUDIO_HW_IDLE;

    return ret;
}
#endif

void AudioFlinger::registerClient(const sp<IAudioFlingerClient>& client)
{

    Mutex::Autolock _l(mLock);

    int pid = IPCThreadState::self()->getCallingPid();
    if (mNotificationClients.indexOfKey(pid) < 0) {
        sp<NotificationClient> notificationClient = new NotificationClient(this,
                                                                            client,
                                                                            pid);
        LOGV("registerClient() client %p, pid %d", notificationClient.get(), pid);

        mNotificationClients.add(pid, notificationClient);

        sp<IBinder> binder = client->asBinder();
        binder->linkToDeath(notificationClient);

        // the config change is always sent from playback or record threads to avoid deadlock
        // with AudioSystem::gLock
        for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
            mPlaybackThreads.valueAt(i)->sendConfigEvent(AudioSystem::OUTPUT_OPENED);
        }

        for (size_t i = 0; i < mRecordThreads.size(); i++) {
            mRecordThreads.valueAt(i)->sendConfigEvent(AudioSystem::INPUT_OPENED);
        }
    }
}

void AudioFlinger::removeNotificationClient(pid_t pid)
{
    Mutex::Autolock _l(mLock);

    int index = mNotificationClients.indexOfKey(pid);
    if (index >= 0) {
        sp <NotificationClient> client = mNotificationClients.valueFor(pid);
        LOGV("removeNotificationClient() %p, pid %d", client.get(), pid);
#ifdef LVMX
        if (pid == mLifeVibesClientPid) {
            LOGV("Disabling lifevibes");
            LifeVibes::enableMusic(false);
            mLifeVibesClientPid = -1;
        }
#endif
        mNotificationClients.removeItem(pid);
    }
}

// audioConfigChanged_l() must be called with AudioFlinger::mLock held
void AudioFlinger::audioConfigChanged_l(int event, int ioHandle, void *param2)
{
    size_t size = mNotificationClients.size();
    for (size_t i = 0; i < size; i++) {
        mNotificationClients.valueAt(i)->client()->ioConfigChanged(event, ioHandle, param2);
    }
}

// removeClient_l() must be called with AudioFlinger::mLock held
void AudioFlinger::removeClient_l(pid_t pid)
{
    LOGV("removeClient_l() pid %d, tid %d, calling tid %d", pid, gettid(), IPCThreadState::self()->getCallingPid());
    mClients.removeItem(pid);
}


// ----------------------------------------------------------------------------

AudioFlinger::ThreadBase::ThreadBase(const sp<AudioFlinger>& audioFlinger, int id)
    :   Thread(false),
        mAudioFlinger(audioFlinger), mSampleRate(0), mFrameCount(0), mChannelCount(0),
        mFrameSize(1), mFormat(0), mStandby(false), mId(id), mExiting(false)
{
}

AudioFlinger::ThreadBase::~ThreadBase()
{
    mParamCond.broadcast();
    mNewParameters.clear();
}

void AudioFlinger::ThreadBase::exit()
{
    // keep a strong ref on ourself so that we wont get
    // destroyed in the middle of requestExitAndWait()
    sp <ThreadBase> strongMe = this;

    LOGV("ThreadBase::exit");
    {
        AutoMutex lock(&mLock);
        mExiting = true;
        requestExit();
        mWaitWorkCV.signal();
    }
    requestExitAndWait();
}

uint32_t AudioFlinger::ThreadBase::sampleRate() const
{
    return mSampleRate;
}

int AudioFlinger::ThreadBase::channelCount() const
{
    return (int)mChannelCount;
}

int AudioFlinger::ThreadBase::format() const
{
    return mFormat;
}

size_t AudioFlinger::ThreadBase::frameCount() const
{
    return mFrameCount;
}

status_t AudioFlinger::ThreadBase::setParameters(const String8& keyValuePairs)
{
    status_t status;

    LOGV("ThreadBase::setParameters() %s", keyValuePairs.string());
    Mutex::Autolock _l(mLock);

    mNewParameters.add(keyValuePairs);
    mWaitWorkCV.signal();
    // wait condition with timeout in case the thread loop has exited
    // before the request could be processed
    if (mParamCond.waitRelative(mLock, seconds(2)) == NO_ERROR) {
        status = mParamStatus;
        mWaitWorkCV.signal();
    } else {
        status = TIMED_OUT;
    }
    return status;
}

void AudioFlinger::ThreadBase::sendConfigEvent(int event, int param)
{
    Mutex::Autolock _l(mLock);
    sendConfigEvent_l(event, param);
}

// sendConfigEvent_l() must be called with ThreadBase::mLock held
void AudioFlinger::ThreadBase::sendConfigEvent_l(int event, int param)
{
    ConfigEvent *configEvent = new ConfigEvent();
    configEvent->mEvent = event;
    configEvent->mParam = param;
    mConfigEvents.add(configEvent);
    LOGV("sendConfigEvent() num events %d event %d, param %d", mConfigEvents.size(), event, param);
    mWaitWorkCV.signal();
}

void AudioFlinger::ThreadBase::processConfigEvents()
{
    mLock.lock();
    while(!mConfigEvents.isEmpty()) {
        LOGV("processConfigEvents() remaining events %d", mConfigEvents.size());
        ConfigEvent *configEvent = mConfigEvents[0];
        mConfigEvents.removeAt(0);
        // release mLock before locking AudioFlinger mLock: lock order is always
        // AudioFlinger then ThreadBase to avoid cross deadlock
        mLock.unlock();
        mAudioFlinger->mLock.lock();
        audioConfigChanged_l(configEvent->mEvent, configEvent->mParam);
        mAudioFlinger->mLock.unlock();
        delete configEvent;
        mLock.lock();
    }
    mLock.unlock();
}

status_t AudioFlinger::ThreadBase::dumpBase(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    bool locked = tryLock(mLock);
    if (!locked) {
        snprintf(buffer, SIZE, "thread %p maybe dead locked\n", this);
        write(fd, buffer, strlen(buffer));
    }

    snprintf(buffer, SIZE, "standby: %d\n", mStandby);
    result.append(buffer);
    snprintf(buffer, SIZE, "Sample rate: %d\n", mSampleRate);
    result.append(buffer);
    snprintf(buffer, SIZE, "Frame count: %d\n", mFrameCount);
    result.append(buffer);
    snprintf(buffer, SIZE, "Channel Count: %d\n", mChannelCount);
    result.append(buffer);
    snprintf(buffer, SIZE, "Format: %d\n", mFormat);
    result.append(buffer);
    snprintf(buffer, SIZE, "Frame size: %d\n", mFrameSize);
    result.append(buffer);

    snprintf(buffer, SIZE, "\nPending setParameters commands: \n");
    result.append(buffer);
    result.append(" Index Command");
    for (size_t i = 0; i < mNewParameters.size(); ++i) {
        snprintf(buffer, SIZE, "\n %02d    ", i);
        result.append(buffer);
        result.append(mNewParameters[i]);
    }

    snprintf(buffer, SIZE, "\n\nPending config events: \n");
    result.append(buffer);
    snprintf(buffer, SIZE, " Index event param\n");
    result.append(buffer);
    for (size_t i = 0; i < mConfigEvents.size(); i++) {
        snprintf(buffer, SIZE, " %02d    %02d    %d\n", i, mConfigEvents[i]->mEvent, mConfigEvents[i]->mParam);
        result.append(buffer);
    }
    result.append("\n");

    write(fd, result.string(), result.size());

    if (locked) {
        mLock.unlock();
    }
    return NO_ERROR;
}


// ----------------------------------------------------------------------------

AudioFlinger::PlaybackThread::PlaybackThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output, int id, uint32_t device)
    :   ThreadBase(audioFlinger, id),
        mMixBuffer(0), mSuspended(0), mBytesWritten(0), mOutput(output),
        mLastWriteTime(0), mNumWrites(0), mNumDelayedWrites(0), mInWrite(false),
        mDevice(device)
{
    readOutputParameters();

    mMasterVolume = mAudioFlinger->masterVolume();
    mMasterMute = mAudioFlinger->masterMute();

    for (int stream = 0; stream < AudioSystem::NUM_STREAM_TYPES; stream++) {
        mStreamTypes[stream].volume = mAudioFlinger->streamVolumeInternal(stream);
        mStreamTypes[stream].mute = mAudioFlinger->streamMute(stream);
    }
}

AudioFlinger::PlaybackThread::~PlaybackThread()
{
    delete [] mMixBuffer;
}

status_t AudioFlinger::PlaybackThread::dump(int fd, const Vector<String16>& args)
{
    dumpInternals(fd, args);
    dumpTracks(fd, args);
    dumpEffectChains(fd, args);
    return NO_ERROR;
}

status_t AudioFlinger::PlaybackThread::dumpTracks(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    snprintf(buffer, SIZE, "Output thread %p tracks\n", this);
    result.append(buffer);
    result.append("   Name  Clien Typ Fmt Chn Session Buf  S M F SRate LeftV RighV  Serv       User       Main buf   Aux Buf\n");
    for (size_t i = 0; i < mTracks.size(); ++i) {
        sp<Track> track = mTracks[i];
        if (track != 0) {
            track->dump(buffer, SIZE);
            result.append(buffer);
        }
    }

    snprintf(buffer, SIZE, "Output thread %p active tracks\n", this);
    result.append(buffer);
    result.append("   Name  Clien Typ Fmt Chn Session Buf  S M F SRate LeftV RighV  Serv       User       Main buf   Aux Buf\n");
    for (size_t i = 0; i < mActiveTracks.size(); ++i) {
        wp<Track> wTrack = mActiveTracks[i];
        if (wTrack != 0) {
            sp<Track> track = wTrack.promote();
            if (track != 0) {
                track->dump(buffer, SIZE);
                result.append(buffer);
            }
        }
    }
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

status_t AudioFlinger::PlaybackThread::dumpEffectChains(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    snprintf(buffer, SIZE, "\n- %d Effect Chains:\n", mEffectChains.size());
    write(fd, buffer, strlen(buffer));

    for (size_t i = 0; i < mEffectChains.size(); ++i) {
        sp<EffectChain> chain = mEffectChains[i];
        if (chain != 0) {
            chain->dump(fd, args);
        }
    }
    return NO_ERROR;
}

status_t AudioFlinger::PlaybackThread::dumpInternals(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    snprintf(buffer, SIZE, "\nOutput thread %p internals\n", this);
    result.append(buffer);
    snprintf(buffer, SIZE, "last write occurred (msecs): %llu\n", ns2ms(systemTime() - mLastWriteTime));
    result.append(buffer);
    snprintf(buffer, SIZE, "total writes: %d\n", mNumWrites);
    result.append(buffer);
    snprintf(buffer, SIZE, "delayed writes: %d\n", mNumDelayedWrites);
    result.append(buffer);
    snprintf(buffer, SIZE, "blocked in write: %d\n", mInWrite);
    result.append(buffer);
    snprintf(buffer, SIZE, "suspend count: %d\n", mSuspended);
    result.append(buffer);
    snprintf(buffer, SIZE, "mix buffer : %p\n", mMixBuffer);
    result.append(buffer);
    write(fd, result.string(), result.size());

    dumpBase(fd, args);

    return NO_ERROR;
}

// Thread virtuals
status_t AudioFlinger::PlaybackThread::readyToRun()
{
    if (mSampleRate == 0) {
        LOGE("No working audio driver found.");
        return NO_INIT;
    }
    LOGI("AudioFlinger's thread %p ready to run", this);
    return NO_ERROR;
}

void AudioFlinger::PlaybackThread::onFirstRef()
{
    const size_t SIZE = 256;
    char buffer[SIZE];

    snprintf(buffer, SIZE, "Playback Thread %p", this);

    run(buffer, ANDROID_PRIORITY_URGENT_AUDIO);
}

// PlaybackThread::createTrack_l() must be called with AudioFlinger::mLock held
sp<AudioFlinger::PlaybackThread::Track>  AudioFlinger::PlaybackThread::createTrack_l(
        const sp<AudioFlinger::Client>& client,
        int streamType,
        uint32_t sampleRate,
        int format,
        int channelCount,
        int frameCount,
        const sp<IMemory>& sharedBuffer,
        int sessionId,
        status_t *status)
{
    sp<Track> track;
    status_t lStatus;

    if (mType == DIRECT) {
        if (sampleRate != mSampleRate || format != mFormat || channelCount != (int)mChannelCount) {
            LOGE("createTrack_l() Bad parameter:  sampleRate %d format %d, channelCount %d for output %p",
                 sampleRate, format, channelCount, mOutput);
            lStatus = BAD_VALUE;
            goto Exit;
        }
    } else {
        // Resampler implementation limits input sampling rate to 2 x output sampling rate.
        if (sampleRate > mSampleRate*2) {
            LOGE("Sample rate out of range: %d mSampleRate %d", sampleRate, mSampleRate);
            lStatus = BAD_VALUE;
            goto Exit;
        }
    }

    if (mOutput == 0) {
        LOGE("Audio driver not initialized.");
        lStatus = NO_INIT;
        goto Exit;
    }

    { // scope for mLock
        Mutex::Autolock _l(mLock);

        // all tracks in same audio session must share the same routing strategy otherwise
        // conflicts will happen when tracks are moved from one output to another by audio policy
        // manager
        uint32_t strategy =
                AudioSystem::getStrategyForStream((AudioSystem::stream_type)streamType);
        for (size_t i = 0; i < mTracks.size(); ++i) {
            sp<Track> t = mTracks[i];
            if (t != 0) {
                if (sessionId == t->sessionId() &&
                        strategy != AudioSystem::getStrategyForStream((AudioSystem::stream_type)t->type())) {
                    lStatus = BAD_VALUE;
                    goto Exit;
                }
            }
        }

        track = new Track(this, client, streamType, sampleRate, format,
                channelCount, frameCount, sharedBuffer, sessionId);
        if (track->getCblk() == NULL || track->name() < 0) {
            lStatus = NO_MEMORY;
            goto Exit;
        }
        mTracks.add(track);

        sp<EffectChain> chain = getEffectChain_l(sessionId);
        if (chain != 0) {
            LOGV("createTrack_l() setting main buffer %p", chain->inBuffer());
            track->setMainBuffer(chain->inBuffer());
            chain->setStrategy(AudioSystem::getStrategyForStream((AudioSystem::stream_type)track->type()));
        }
    }
    lStatus = NO_ERROR;

Exit:
    if(status) {
        *status = lStatus;
    }
    return track;
}

uint32_t AudioFlinger::PlaybackThread::latency() const
{
    if (mOutput) {
        return mOutput->latency();
    }
    else {
        return 0;
    }
}

status_t AudioFlinger::PlaybackThread::setMasterVolume(float value)
{
#ifdef LVMX
    int audioOutputType = LifeVibes::getMixerType(mId, mType);
    if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType)) {
        LifeVibes::setMasterVolume(audioOutputType, value);
    }
#endif
    mMasterVolume = value;
    return NO_ERROR;
}

status_t AudioFlinger::PlaybackThread::setMasterMute(bool muted)
{
#ifdef LVMX
    int audioOutputType = LifeVibes::getMixerType(mId, mType);
    if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType)) {
        LifeVibes::setMasterMute(audioOutputType, muted);
    }
#endif
    mMasterMute = muted;
    return NO_ERROR;
}

float AudioFlinger::PlaybackThread::masterVolume() const
{
    return mMasterVolume;
}

bool AudioFlinger::PlaybackThread::masterMute() const
{
    return mMasterMute;
}

status_t AudioFlinger::PlaybackThread::setStreamVolume(int stream, float value)
{
#ifdef LVMX
    int audioOutputType = LifeVibes::getMixerType(mId, mType);
    if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType)) {
        LifeVibes::setStreamVolume(audioOutputType, stream, value);
    }
#endif
    mStreamTypes[stream].volume = value;
    return NO_ERROR;
}

status_t AudioFlinger::PlaybackThread::setStreamMute(int stream, bool muted)
{
#ifdef LVMX
    int audioOutputType = LifeVibes::getMixerType(mId, mType);
    if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType)) {
        LifeVibes::setStreamMute(audioOutputType, stream, muted);
    }
#endif
    mStreamTypes[stream].mute = muted;
    return NO_ERROR;
}

float AudioFlinger::PlaybackThread::streamVolume(int stream) const
{
    return mStreamTypes[stream].volume;
}

bool AudioFlinger::PlaybackThread::streamMute(int stream) const
{
    return mStreamTypes[stream].mute;
}

bool AudioFlinger::PlaybackThread::isStreamActive(int stream) const
{
    Mutex::Autolock _l(mLock);
    size_t count = mActiveTracks.size();
    for (size_t i = 0 ; i < count ; ++i) {
        sp<Track> t = mActiveTracks[i].promote();
        if (t == 0) continue;
        Track* const track = t.get();
        if (t->type() == stream)
            return true;
    }
    return false;
}

// addTrack_l() must be called with ThreadBase::mLock held
status_t AudioFlinger::PlaybackThread::addTrack_l(const sp<Track>& track)
{
    status_t status = ALREADY_EXISTS;

    // set retry count for buffer fill
    track->mRetryCount = kMaxTrackStartupRetries;
    if (mActiveTracks.indexOf(track) < 0) {
        // the track is newly added, make sure it fills up all its
        // buffers before playing. This is to ensure the client will
        // effectively get the latency it requested.
        track->mFillingUpStatus = Track::FS_FILLING;
        track->mResetDone = false;
        mActiveTracks.add(track);
        if (track->mainBuffer() != mMixBuffer) {
            sp<EffectChain> chain = getEffectChain_l(track->sessionId());
            if (chain != 0) {
                LOGV("addTrack_l() starting track on chain %p for session %d", chain.get(), track->sessionId());
                chain->startTrack();
            }
        }

        status = NO_ERROR;
    }

    LOGV("mWaitWorkCV.broadcast");
    mWaitWorkCV.broadcast();

    return status;
}

// destroyTrack_l() must be called with ThreadBase::mLock held
void AudioFlinger::PlaybackThread::destroyTrack_l(const sp<Track>& track)
{
    track->mState = TrackBase::TERMINATED;
    if (mActiveTracks.indexOf(track) < 0) {
        mTracks.remove(track);
        deleteTrackName_l(track->name());
    }
}

String8 AudioFlinger::PlaybackThread::getParameters(const String8& keys)
{
    return mOutput->getParameters(keys);
}

// destroyTrack_l() must be called with AudioFlinger::mLock held
void AudioFlinger::PlaybackThread::audioConfigChanged_l(int event, int param) {
    AudioSystem::OutputDescriptor desc;
    void *param2 = 0;

    LOGV("PlaybackThread::audioConfigChanged_l, thread %p, event %d, param %d", this, event, param);

    switch (event) {
    case AudioSystem::OUTPUT_OPENED:
    case AudioSystem::OUTPUT_CONFIG_CHANGED:
        desc.channels = mChannels;
        desc.samplingRate = mSampleRate;
        desc.format = mFormat;
        desc.frameCount = mFrameCount;
        desc.latency = latency();
        param2 = &desc;
        break;

    case AudioSystem::STREAM_CONFIG_CHANGED:
        param2 = &param;
    case AudioSystem::OUTPUT_CLOSED:
    default:
        break;
    }
    mAudioFlinger->audioConfigChanged_l(event, mId, param2);
}

void AudioFlinger::PlaybackThread::readOutputParameters()
{
    mSampleRate = mOutput->sampleRate();
    mChannels = mOutput->channels();
    mChannelCount = (uint16_t)AudioSystem::popCount(mChannels);
    mFormat = mOutput->format();
    mFrameSize = (uint16_t)mOutput->frameSize();
    mFrameCount = mOutput->bufferSize() / mFrameSize;

    // FIXME - Current mixer implementation only supports stereo output: Always
    // Allocate a stereo buffer even if HW output is mono.
    if (mMixBuffer != NULL) delete[] mMixBuffer;
    mMixBuffer = new int16_t[mFrameCount * 2];
    memset(mMixBuffer, 0, mFrameCount * 2 * sizeof(int16_t));

    // force reconfiguration of effect chains and engines to take new buffer size and audio
    // parameters into account
    // Note that mLock is not held when readOutputParameters() is called from the constructor
    // but in this case nothing is done below as no audio sessions have effect yet so it doesn't
    // matter.
    // create a copy of mEffectChains as calling moveEffectChain_l() can reorder some effect chains
    Vector< sp<EffectChain> > effectChains = mEffectChains;
    for (size_t i = 0; i < effectChains.size(); i ++) {
        mAudioFlinger->moveEffectChain_l(effectChains[i]->sessionId(), this, this, false);
    }
}

status_t AudioFlinger::PlaybackThread::getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames)
{
    if (halFrames == 0 || dspFrames == 0) {
        return BAD_VALUE;
    }
    if (mOutput == 0) {
        return INVALID_OPERATION;
    }
    *halFrames = mBytesWritten/mOutput->frameSize();

    return mOutput->getRenderPosition(dspFrames);
}

uint32_t AudioFlinger::PlaybackThread::hasAudioSession(int sessionId)
{
    Mutex::Autolock _l(mLock);
    uint32_t result = 0;
    if (getEffectChain_l(sessionId) != 0) {
        result = EFFECT_SESSION;
    }

    for (size_t i = 0; i < mTracks.size(); ++i) {
        sp<Track> track = mTracks[i];
        if (sessionId == track->sessionId() &&
                !(track->mCblk->flags & CBLK_INVALID_MSK)) {
            result |= TRACK_SESSION;
            break;
        }
    }

    return result;
}

uint32_t AudioFlinger::PlaybackThread::getStrategyForSession_l(int sessionId)
{
    // session AudioSystem::SESSION_OUTPUT_MIX is placed in same strategy as MUSIC stream so that
    // it is moved to correct output by audio policy manager when A2DP is connected or disconnected
    if (sessionId == AudioSystem::SESSION_OUTPUT_MIX) {
        return AudioSystem::getStrategyForStream(AudioSystem::MUSIC);
    }
    for (size_t i = 0; i < mTracks.size(); i++) {
        sp<Track> track = mTracks[i];
        if (sessionId == track->sessionId() &&
                !(track->mCblk->flags & CBLK_INVALID_MSK)) {
            return AudioSystem::getStrategyForStream((AudioSystem::stream_type) track->type());
        }
    }
    return AudioSystem::getStrategyForStream(AudioSystem::MUSIC);
}

sp<AudioFlinger::EffectChain> AudioFlinger::PlaybackThread::getEffectChain(int sessionId)
{
    Mutex::Autolock _l(mLock);
    return getEffectChain_l(sessionId);
}

sp<AudioFlinger::EffectChain> AudioFlinger::PlaybackThread::getEffectChain_l(int sessionId)
{
    sp<EffectChain> chain;

    size_t size = mEffectChains.size();
    for (size_t i = 0; i < size; i++) {
        if (mEffectChains[i]->sessionId() == sessionId) {
            chain = mEffectChains[i];
            break;
        }
    }
    return chain;
}

void AudioFlinger::PlaybackThread::setMode(uint32_t mode)
{
    Mutex::Autolock _l(mLock);
    size_t size = mEffectChains.size();
    for (size_t i = 0; i < size; i++) {
        mEffectChains[i]->setMode_l(mode);
    }
}

// ----------------------------------------------------------------------------

AudioFlinger::MixerThread::MixerThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output, int id, uint32_t device)
    :   PlaybackThread(audioFlinger, output, id, device),
        mAudioMixer(0)
{
    mType = PlaybackThread::MIXER;
    mAudioMixer = new AudioMixer(mFrameCount, mSampleRate);

    // FIXME - Current mixer implementation only supports stereo output
    if (mChannelCount == 1) {
        LOGE("Invalid audio hardware channel count");
    }
}

AudioFlinger::MixerThread::~MixerThread()
{
    delete mAudioMixer;
}

bool AudioFlinger::MixerThread::threadLoop()
{
    Vector< sp<Track> > tracksToRemove;
    uint32_t mixerStatus = MIXER_IDLE;
    nsecs_t standbyTime = systemTime();
    size_t mixBufferSize = mFrameCount * mFrameSize;
    // FIXME: Relaxed timing because of a certain device that can't meet latency
    // Should be reduced to 2x after the vendor fixes the driver issue
    nsecs_t maxPeriod = seconds(mFrameCount) / mSampleRate * 3;
    nsecs_t lastWarning = 0;
    bool longStandbyExit = false;
    uint32_t activeSleepTime = activeSleepTimeUs();
    uint32_t idleSleepTime = idleSleepTimeUs();
    uint32_t sleepTime = idleSleepTime;
    Vector< sp<EffectChain> > effectChains;

    while (!exitPending())
    {
        processConfigEvents();

        mixerStatus = MIXER_IDLE;
        { // scope for mLock

            Mutex::Autolock _l(mLock);

            if (checkForNewParameters_l()) {
                mixBufferSize = mFrameCount * mFrameSize;
                // FIXME: Relaxed timing because of a certain device that can't meet latency
                // Should be reduced to 2x after the vendor fixes the driver issue
                maxPeriod = seconds(mFrameCount) / mSampleRate * 3;
                activeSleepTime = activeSleepTimeUs();
                idleSleepTime = idleSleepTimeUs();
            }

            const SortedVector< wp<Track> >& activeTracks = mActiveTracks;

            // put audio hardware into standby after short delay
            if UNLIKELY((!activeTracks.size() && systemTime() > standbyTime) ||
                        mSuspended) {
                if (!mStandby) {
                    LOGV("Audio hardware entering standby, mixer %p, mSuspended %d\n", this, mSuspended);
                    mOutput->standby();
                    mStandby = true;
                    mBytesWritten = 0;
                }

                if (!activeTracks.size() && mConfigEvents.isEmpty()) {
                    // we're about to wait, flush the binder command buffer
                    IPCThreadState::self()->flushCommands();

                    if (exitPending()) break;

                    // wait until we have something to do...
                    LOGV("MixerThread %p TID %d going to sleep\n", this, gettid());
                    mWaitWorkCV.wait(mLock);
                    LOGV("MixerThread %p TID %d waking up\n", this, gettid());

                    if (mMasterMute == false) {
                        char value[PROPERTY_VALUE_MAX];
                        property_get("ro.audio.silent", value, "0");
                        if (atoi(value)) {
                            LOGD("Silence is golden");
                            setMasterMute(true);
                        }
                    }

                    standbyTime = systemTime() + kStandbyTimeInNsecs;
                    sleepTime = idleSleepTime;
                    continue;
                }
            }

            mixerStatus = prepareTracks_l(activeTracks, &tracksToRemove);

            // prevent any changes in effect chain list and in each effect chain
            // during mixing and effect process as the audio buffers could be deleted
            // or modified if an effect is created or deleted
            lockEffectChains_l(effectChains);
       }

        if (LIKELY(mixerStatus == MIXER_TRACKS_READY)) {
            // mix buffers...
            mAudioMixer->process();
            sleepTime = 0;
            standbyTime = systemTime() + kStandbyTimeInNsecs;
            //TODO: delay standby when effects have a tail
        } else {
            // If no tracks are ready, sleep once for the duration of an output
            // buffer size, then write 0s to the output
            if (sleepTime == 0) {
                if (mixerStatus == MIXER_TRACKS_ENABLED) {
                    sleepTime = activeSleepTime;
                } else {
                    sleepTime = idleSleepTime;
                }
            } else if (mBytesWritten != 0 ||
                       (mixerStatus == MIXER_TRACKS_ENABLED && longStandbyExit)) {
                memset (mMixBuffer, 0, mixBufferSize);
                sleepTime = 0;
                LOGV_IF((mBytesWritten == 0 && (mixerStatus == MIXER_TRACKS_ENABLED && longStandbyExit)), "anticipated start");
            }
            // TODO add standby time extension fct of effect tail
        }

        if (mSuspended) {
            sleepTime = suspendSleepTimeUs();
        }
        // sleepTime == 0 means we must write to audio hardware
        if (sleepTime == 0) {
             for (size_t i = 0; i < effectChains.size(); i ++) {
                 effectChains[i]->process_l();
             }
             // enable changes in effect chain
             unlockEffectChains(effectChains);
#ifdef LVMX
            int audioOutputType = LifeVibes::getMixerType(mId, mType);
            if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType)) {
               LifeVibes::process(audioOutputType, mMixBuffer, mixBufferSize);
            }
#endif
            mLastWriteTime = systemTime();
            mInWrite = true;
            mBytesWritten += mixBufferSize;

            int bytesWritten = (int)mOutput->write(mMixBuffer, mixBufferSize);
            if (bytesWritten < 0) mBytesWritten -= mixBufferSize;
            mNumWrites++;
            mInWrite = false;
            nsecs_t now = systemTime();
            nsecs_t delta = now - mLastWriteTime;
            if (delta > maxPeriod) {
                mNumDelayedWrites++;
                if ((now - lastWarning) > kWarningThrottle) {
                    LOGW("write blocked for %llu msecs, %d delayed writes, thread %p",
                            ns2ms(delta), mNumDelayedWrites, this);
                    lastWarning = now;
                }
                if (mStandby) {
                    longStandbyExit = true;
                }
            }
            mStandby = false;
        } else {
            // enable changes in effect chain
            unlockEffectChains(effectChains);
            usleep(sleepTime);
        }

        // finally let go of all our tracks, without the lock held
        // since we can't guarantee the destructors won't acquire that
        // same lock.
        tracksToRemove.clear();

        // Effect chains will be actually deleted here if they were removed from
        // mEffectChains list during mixing or effects processing
        effectChains.clear();
    }

    if (!mStandby) {
        mOutput->standby();
    }

    LOGV("MixerThread %p exiting", this);
    return false;
}

// prepareTracks_l() must be called with ThreadBase::mLock held
uint32_t AudioFlinger::MixerThread::prepareTracks_l(const SortedVector< wp<Track> >& activeTracks, Vector< sp<Track> > *tracksToRemove)
{

    uint32_t mixerStatus = MIXER_IDLE;
    // find out which tracks need to be processed
    size_t count = activeTracks.size();
    size_t mixedTracks = 0;
    size_t tracksWithEffect = 0;

    float masterVolume = mMasterVolume;
    bool  masterMute = mMasterMute;

    if (masterMute) {
        masterVolume = 0;
    }
#ifdef LVMX
    bool tracksConnectedChanged = false;
    bool stateChanged = false;

    int audioOutputType = LifeVibes::getMixerType(mId, mType);
    if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType))
    {
        int activeTypes = 0;
        for (size_t i=0 ; i<count ; i++) {
            sp<Track> t = activeTracks[i].promote();
            if (t == 0) continue;
            Track* const track = t.get();
            int iTracktype=track->type();
            activeTypes |= 1<<track->type();
        }
        LifeVibes::computeVolumes(audioOutputType, activeTypes, tracksConnectedChanged, stateChanged, masterVolume, masterMute);
    }
#endif
    // Delegate master volume control to effect in output mix effect chain if needed
    sp<EffectChain> chain = getEffectChain_l(AudioSystem::SESSION_OUTPUT_MIX);
    if (chain != 0) {
        uint32_t v = (uint32_t)(masterVolume * (1 << 24));
        chain->setVolume_l(&v, &v);
        masterVolume = (float)((v + (1 << 23)) >> 24);
        chain.clear();
    }

    for (size_t i=0 ; i<count ; i++) {
        sp<Track> t = activeTracks[i].promote();
        if (t == 0) continue;

        Track* const track = t.get();
        audio_track_cblk_t* cblk = track->cblk();

        // The first time a track is added we wait
        // for all its buffers to be filled before processing it
        mAudioMixer->setActiveTrack(track->name());
        if (cblk->framesReady() && track->isReady() &&
                !track->isPaused() && !track->isTerminated())
        {
            //LOGV("track %d u=%08x, s=%08x [OK] on thread %p", track->name(), cblk->user, cblk->server, this);

            mixedTracks++;

            // track->mainBuffer() != mMixBuffer means there is an effect chain
            // connected to the track
            chain.clear();
            if (track->mainBuffer() != mMixBuffer) {
                chain = getEffectChain_l(track->sessionId());
                // Delegate volume control to effect in track effect chain if needed
                if (chain != 0) {
                    tracksWithEffect++;
                } else {
                    LOGW("prepareTracks_l(): track %08x attached to effect but no chain found on session %d",
                            track->name(), track->sessionId());
                }
            }


            int param = AudioMixer::VOLUME;
            if (track->mFillingUpStatus == Track::FS_FILLED) {
                // no ramp for the first volume setting
                track->mFillingUpStatus = Track::FS_ACTIVE;
                if (track->mState == TrackBase::RESUMING) {
                    track->mState = TrackBase::ACTIVE;
                    param = AudioMixer::RAMP_VOLUME;
                }
            } else if (cblk->server != 0) {
                // If the track is stopped before the first frame was mixed,
                // do not apply ramp
                param = AudioMixer::RAMP_VOLUME;
            }

            // compute volume for this track
            uint32_t vl, vr, va;
            if (track->isMuted() || track->isPausing() ||
                mStreamTypes[track->type()].mute) {
                vl = vr = va = 0;
                if (track->isPausing()) {
                    track->setPaused();
                }
            } else {

                // read original volumes with volume control
                float typeVolume = mStreamTypes[track->type()].volume;
#ifdef LVMX
                bool streamMute=false;
                // read the volume from the LivesVibes audio engine.
                if (LifeVibes::audioOutputTypeIsLifeVibes(audioOutputType))
                {
                    LifeVibes::getStreamVolumes(audioOutputType, track->type(), &typeVolume, &streamMute);
                    if (streamMute) {
                        typeVolume = 0;
                    }
                }
#endif
                float v = masterVolume * typeVolume;
                vl = (uint32_t)(v * cblk->volume[0]) << 12;
                vr = (uint32_t)(v * cblk->volume[1]) << 12;

                va = (uint32_t)(v * cblk->sendLevel);
            }
            // Delegate volume control to effect in track effect chain if needed
            if (chain != 0 && chain->setVolume_l(&vl, &vr)) {
                // Do not ramp volume if volume is controlled by effect
                param = AudioMixer::VOLUME;
                track->mHasVolumeController = true;
            } else {
                // force no volume ramp when volume controller was just disabled or removed
                // from effect chain to avoid volume spike
                if (track->mHasVolumeController) {
                    param = AudioMixer::VOLUME;
                }
                track->mHasVolumeController = false;
            }

            // Convert volumes from 8.24 to 4.12 format
            int16_t left, right, aux;
            uint32_t v_clamped = (vl + (1 << 11)) >> 12;
            if (v_clamped > MAX_GAIN_INT) v_clamped = MAX_GAIN_INT;
            left = int16_t(v_clamped);
            v_clamped = (vr + (1 << 11)) >> 12;
            if (v_clamped > MAX_GAIN_INT) v_clamped = MAX_GAIN_INT;
            right = int16_t(v_clamped);

            if (va > MAX_GAIN_INT) va = MAX_GAIN_INT;
            aux = int16_t(va);

#ifdef LVMX
            if ( tracksConnectedChanged || stateChanged )
            {
                 // only do the ramp when the volume is changed by the user / application
                 param = AudioMixer::VOLUME;
            }
#endif

            // XXX: these things DON'T need to be done each time
            mAudioMixer->setBufferProvider(track);
            mAudioMixer->enable(AudioMixer::MIXING);

            mAudioMixer->setParameter(param, AudioMixer::VOLUME0, (void *)left);
            mAudioMixer->setParameter(param, AudioMixer::VOLUME1, (void *)right);
            mAudioMixer->setParameter(param, AudioMixer::AUXLEVEL, (void *)aux);
            mAudioMixer->setParameter(
                AudioMixer::TRACK,
                AudioMixer::FORMAT, (void *)track->format());
            mAudioMixer->setParameter(
                AudioMixer::TRACK,
                AudioMixer::CHANNEL_COUNT, (void *)track->channelCount());
            mAudioMixer->setParameter(
                AudioMixer::RESAMPLE,
                AudioMixer::SAMPLE_RATE,
                (void *)(cblk->sampleRate));
            mAudioMixer->setParameter(
                AudioMixer::TRACK,
                AudioMixer::MAIN_BUFFER, (void *)track->mainBuffer());
            mAudioMixer->setParameter(
                AudioMixer::TRACK,
                AudioMixer::AUX_BUFFER, (void *)track->auxBuffer());

            // reset retry count
            track->mRetryCount = kMaxTrackRetries;
            mixerStatus = MIXER_TRACKS_READY;
        } else {
            //LOGV("track %d u=%08x, s=%08x [NOT READY] on thread %p", track->name(), cblk->user, cblk->server, this);
            if (track->isStopped()) {
                track->reset();
            }
            if (track->isTerminated() || track->isStopped() || track->isPaused()) {
                // We have consumed all the buffers of this track.
                // Remove it from the list of active tracks.
                tracksToRemove->add(track);
            } else {
                // No buffers for this track. Give it a few chances to
                // fill a buffer, then remove it from active list.
                if (--(track->mRetryCount) <= 0) {
                    LOGV("BUFFER TIMEOUT: remove(%d) from active list on thread %p", track->name(), this);
                    tracksToRemove->add(track);
                    // indicate to client process that the track was disabled because of underrun
                    cblk->flags |= CBLK_DISABLED_ON;
                } else if (mixerStatus != MIXER_TRACKS_READY) {
                    mixerStatus = MIXER_TRACKS_ENABLED;
                }
            }
            mAudioMixer->disable(AudioMixer::MIXING);
        }
    }

    // remove all the tracks that need to be...
    count = tracksToRemove->size();
    if (UNLIKELY(count)) {
        for (size_t i=0 ; i<count ; i++) {
            const sp<Track>& track = tracksToRemove->itemAt(i);
            mActiveTracks.remove(track);
            if (track->mainBuffer() != mMixBuffer) {
                chain = getEffectChain_l(track->sessionId());
                if (chain != 0) {
                    LOGV("stopping track on chain %p for session Id: %d", chain.get(), track->sessionId());
                    chain->stopTrack();
                }
            }
            if (track->isTerminated()) {
                mTracks.remove(track);
                deleteTrackName_l(track->mName);
            }
        }
    }

    // mix buffer must be cleared if all tracks are connected to an
    // effect chain as in this case the mixer will not write to
    // mix buffer and track effects will accumulate into it
    if (mixedTracks != 0 && mixedTracks == tracksWithEffect) {
        memset(mMixBuffer, 0, mFrameCount * mChannelCount * sizeof(int16_t));
    }

    return mixerStatus;
}

void AudioFlinger::MixerThread::invalidateTracks(int streamType)
{
    LOGV ("MixerThread::invalidateTracks() mixer %p, streamType %d, mTracks.size %d",
            this,  streamType, mTracks.size());
    Mutex::Autolock _l(mLock);

    size_t size = mTracks.size();
    for (size_t i = 0; i < size; i++) {
        sp<Track> t = mTracks[i];
        if (t->type() == streamType) {
            t->mCblk->lock.lock();
            t->mCblk->flags |= CBLK_INVALID_ON;
            t->mCblk->cv.signal();
            t->mCblk->lock.unlock();
        }
    }
}


// getTrackName_l() must be called with ThreadBase::mLock held
int AudioFlinger::MixerThread::getTrackName_l()
{
    return mAudioMixer->getTrackName();
}

// deleteTrackName_l() must be called with ThreadBase::mLock held
void AudioFlinger::MixerThread::deleteTrackName_l(int name)
{
    LOGV("remove track (%d) and delete from mixer", name);
    mAudioMixer->deleteTrackName(name);
}

// checkForNewParameters_l() must be called with ThreadBase::mLock held
bool AudioFlinger::MixerThread::checkForNewParameters_l()
{
    bool reconfig = false;

    while (!mNewParameters.isEmpty()) {
        status_t status = NO_ERROR;
        String8 keyValuePair = mNewParameters[0];
        AudioParameter param = AudioParameter(keyValuePair);
        int value;

        if (param.getInt(String8(AudioParameter::keySamplingRate), value) == NO_ERROR) {
            reconfig = true;
        }
        if (param.getInt(String8(AudioParameter::keyFormat), value) == NO_ERROR) {
            if (value != AudioSystem::PCM_16_BIT) {
                status = BAD_VALUE;
            } else {
                reconfig = true;
            }
        }
        if (param.getInt(String8(AudioParameter::keyChannels), value) == NO_ERROR) {
            if (value != AudioSystem::CHANNEL_OUT_STEREO) {
                status = BAD_VALUE;
            } else {
                reconfig = true;
            }
        }
        if (param.getInt(String8(AudioParameter::keyFrameCount), value) == NO_ERROR) {
            // do not accept frame count changes if tracks are open as the track buffer
            // size depends on frame count and correct behavior would not be garantied
            // if frame count is changed after track creation
            if (!mTracks.isEmpty()) {
                status = INVALID_OPERATION;
            } else {
                reconfig = true;
            }
        }
        if (param.getInt(String8(AudioParameter::keyRouting), value) == NO_ERROR) {
            // forward device change to effects that have requested to be
            // aware of attached audio device.
            mDevice = (uint32_t)value;
            for (size_t i = 0; i < mEffectChains.size(); i++) {
                mEffectChains[i]->setDevice_l(mDevice);
            }
        }

        if (status == NO_ERROR) {
            status = mOutput->setParameters(keyValuePair);
            if (!mStandby && status == INVALID_OPERATION) {
               mOutput->standby();
               mStandby = true;
               mBytesWritten = 0;
               status = mOutput->setParameters(keyValuePair);
            }
            if (status == NO_ERROR && reconfig) {
                delete mAudioMixer;
                readOutputParameters();
                mAudioMixer = new AudioMixer(mFrameCount, mSampleRate);
                for (size_t i = 0; i < mTracks.size() ; i++) {
                    int name = getTrackName_l();
                    if (name < 0) break;
                    mTracks[i]->mName = name;
                    // limit track sample rate to 2 x new output sample rate
                    if (mTracks[i]->mCblk->sampleRate > 2 * sampleRate()) {
                        mTracks[i]->mCblk->sampleRate = 2 * sampleRate();
                    }
                }
                sendConfigEvent_l(AudioSystem::OUTPUT_CONFIG_CHANGED);
            }
        }

        mNewParameters.removeAt(0);

        mParamStatus = status;
        mParamCond.signal();
        mWaitWorkCV.wait(mLock);
    }
    return reconfig;
}

status_t AudioFlinger::MixerThread::dumpInternals(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    PlaybackThread::dumpInternals(fd, args);

    snprintf(buffer, SIZE, "AudioMixer tracks: %08x\n", mAudioMixer->trackNames());
    result.append(buffer);
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

uint32_t AudioFlinger::MixerThread::activeSleepTimeUs()
{
    return (uint32_t)(mOutput->latency() * 1000) / 2;
}

uint32_t AudioFlinger::MixerThread::idleSleepTimeUs()
{
    return (uint32_t)(((mFrameCount * 1000) / mSampleRate) * 1000) / 2;
}

uint32_t AudioFlinger::MixerThread::suspendSleepTimeUs()
{
    return (uint32_t)(((mFrameCount * 1000) / mSampleRate) * 1000);
}

// ----------------------------------------------------------------------------
AudioFlinger::DirectOutputThread::DirectOutputThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output, int id, uint32_t device)
    :   PlaybackThread(audioFlinger, output, id, device)
{
    mType = PlaybackThread::DIRECT;
}

AudioFlinger::DirectOutputThread::~DirectOutputThread()
{
}


static inline int16_t clamp16(int32_t sample)
{
    if ((sample>>15) ^ (sample>>31))
        sample = 0x7FFF ^ (sample>>31);
    return sample;
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

void AudioFlinger::DirectOutputThread::applyVolume(uint16_t leftVol, uint16_t rightVol, bool ramp)
{
    // Do not apply volume on compressed audio
    if (!AudioSystem::isLinearPCM(mFormat)) {
        return;
    }

    // convert to signed 16 bit before volume calculation
    if (mFormat == AudioSystem::PCM_8_BIT) {
        size_t count = mFrameCount * mChannelCount;
        uint8_t *src = (uint8_t *)mMixBuffer + count-1;
        int16_t *dst = mMixBuffer + count-1;
        while(count--) {
            *dst-- = (int16_t)(*src--^0x80) << 8;
        }
    }

    size_t frameCount = mFrameCount;
    int16_t *out = mMixBuffer;
    if (ramp) {
        if (mChannelCount == 1) {
            int32_t d = ((int32_t)leftVol - (int32_t)mLeftVolShort) << 16;
            int32_t vlInc = d / (int32_t)frameCount;
            int32_t vl = ((int32_t)mLeftVolShort << 16);
            do {
                out[0] = clamp16(mul(out[0], vl >> 16) >> 12);
                out++;
                vl += vlInc;
            } while (--frameCount);

        } else {
            int32_t d = ((int32_t)leftVol - (int32_t)mLeftVolShort) << 16;
            int32_t vlInc = d / (int32_t)frameCount;
            d = ((int32_t)rightVol - (int32_t)mRightVolShort) << 16;
            int32_t vrInc = d / (int32_t)frameCount;
            int32_t vl = ((int32_t)mLeftVolShort << 16);
            int32_t vr = ((int32_t)mRightVolShort << 16);
            do {
                out[0] = clamp16(mul(out[0], vl >> 16) >> 12);
                out[1] = clamp16(mul(out[1], vr >> 16) >> 12);
                out += 2;
                vl += vlInc;
                vr += vrInc;
            } while (--frameCount);
        }
    } else {
        if (mChannelCount == 1) {
            do {
                out[0] = clamp16(mul(out[0], leftVol) >> 12);
                out++;
            } while (--frameCount);
        } else {
            do {
                out[0] = clamp16(mul(out[0], leftVol) >> 12);
                out[1] = clamp16(mul(out[1], rightVol) >> 12);
                out += 2;
            } while (--frameCount);
        }
    }

    // convert back to unsigned 8 bit after volume calculation
    if (mFormat == AudioSystem::PCM_8_BIT) {
        size_t count = mFrameCount * mChannelCount;
        int16_t *src = mMixBuffer;
        uint8_t *dst = (uint8_t *)mMixBuffer;
        while(count--) {
            *dst++ = (uint8_t)(((int32_t)*src++ + (1<<7)) >> 8)^0x80;
        }
    }

    mLeftVolShort = leftVol;
    mRightVolShort = rightVol;
}

bool AudioFlinger::DirectOutputThread::threadLoop()
{
    uint32_t mixerStatus = MIXER_IDLE;
    sp<Track> trackToRemove;
    sp<Track> activeTrack;
    nsecs_t standbyTime = systemTime();
    int8_t *curBuf;
    size_t mixBufferSize = mFrameCount*mFrameSize;
    uint32_t activeSleepTime = activeSleepTimeUs();
    uint32_t idleSleepTime = idleSleepTimeUs();
    uint32_t sleepTime = idleSleepTime;
    // use shorter standby delay as on normal output to release
    // hardware resources as soon as possible
    nsecs_t standbyDelay = microseconds(activeSleepTime*2);

    while (!exitPending())
    {
        bool rampVolume;
        uint16_t leftVol;
        uint16_t rightVol;
        Vector< sp<EffectChain> > effectChains;

        processConfigEvents();

        mixerStatus = MIXER_IDLE;

        { // scope for the mLock

            Mutex::Autolock _l(mLock);

            if (checkForNewParameters_l()) {
                mixBufferSize = mFrameCount*mFrameSize;
                activeSleepTime = activeSleepTimeUs();
                idleSleepTime = idleSleepTimeUs();
                standbyDelay = microseconds(activeSleepTime*2);
            }

            // put audio hardware into standby after short delay
            if UNLIKELY((!mActiveTracks.size() && systemTime() > standbyTime) ||
                        mSuspended) {
                // wait until we have something to do...
                if (!mStandby) {
                    LOGV("Audio hardware entering standby, mixer %p\n", this);
                    mOutput->standby();
                    mStandby = true;
                    mBytesWritten = 0;
                }

                if (!mActiveTracks.size() && mConfigEvents.isEmpty()) {
                    // we're about to wait, flush the binder command buffer
                    IPCThreadState::self()->flushCommands();

                    if (exitPending()) break;

                    LOGV("DirectOutputThread %p TID %d going to sleep\n", this, gettid());
                    mWaitWorkCV.wait(mLock);
                    LOGV("DirectOutputThread %p TID %d waking up in active mode\n", this, gettid());

                    if (mMasterMute == false) {
                        char value[PROPERTY_VALUE_MAX];
                        property_get("ro.audio.silent", value, "0");
                        if (atoi(value)) {
                            LOGD("Silence is golden");
                            setMasterMute(true);
                        }
                    }

                    standbyTime = systemTime() + standbyDelay;
                    sleepTime = idleSleepTime;
                    continue;
                }
            }

            effectChains = mEffectChains;

            // find out which tracks need to be processed
            if (mActiveTracks.size() != 0) {
                sp<Track> t = mActiveTracks[0].promote();
                if (t == 0) continue;

                Track* const track = t.get();
                audio_track_cblk_t* cblk = track->cblk();

                // The first time a track is added we wait
                // for all its buffers to be filled before processing it
                if (cblk->framesReady() && track->isReady() &&
                        !track->isPaused() && !track->isTerminated())
                {
                    //LOGV("track %d u=%08x, s=%08x [OK]", track->name(), cblk->user, cblk->server);

                    if (track->mFillingUpStatus == Track::FS_FILLED) {
                        track->mFillingUpStatus = Track::FS_ACTIVE;
                        mLeftVolFloat = mRightVolFloat = 0;
                        mLeftVolShort = mRightVolShort = 0;
                        if (track->mState == TrackBase::RESUMING) {
                            track->mState = TrackBase::ACTIVE;
                            rampVolume = true;
                        }
                    } else if (cblk->server != 0) {
                        // If the track is stopped before the first frame was mixed,
                        // do not apply ramp
                        rampVolume = true;
                    }
                    // compute volume for this track
                    float left, right;
                    if (track->isMuted() || mMasterMute || track->isPausing() ||
                        mStreamTypes[track->type()].mute) {
                        left = right = 0;
                        if (track->isPausing()) {
                            track->setPaused();
                        }
                    } else {
                        float typeVolume = mStreamTypes[track->type()].volume;
                        float v = mMasterVolume * typeVolume;
                        float v_clamped = v * cblk->volume[0];
                        if (v_clamped > MAX_GAIN) v_clamped = MAX_GAIN;
                        left = v_clamped/MAX_GAIN;
                        v_clamped = v * cblk->volume[1];
                        if (v_clamped > MAX_GAIN) v_clamped = MAX_GAIN;
                        right = v_clamped/MAX_GAIN;
                    }

                    if (left != mLeftVolFloat || right != mRightVolFloat) {
                        mLeftVolFloat = left;
                        mRightVolFloat = right;

                        // If audio HAL implements volume control,
                        // force software volume to nominal value
                        if (mOutput->setVolume(left, right) == NO_ERROR) {
                            left = 1.0f;
                            right = 1.0f;
                        }

                        // Convert volumes from float to 8.24
                        uint32_t vl = (uint32_t)(left * (1 << 24));
                        uint32_t vr = (uint32_t)(right * (1 << 24));

                        // Delegate volume control to effect in track effect chain if needed
                        // only one effect chain can be present on DirectOutputThread, so if
                        // there is one, the track is connected to it
                        if (!effectChains.isEmpty()) {
                            // Do not ramp volume if volume is controlled by effect
                            if(effectChains[0]->setVolume_l(&vl, &vr)) {
                                rampVolume = false;
                            }
                        }

                        // Convert volumes from 8.24 to 4.12 format
                        uint32_t v_clamped = (vl + (1 << 11)) >> 12;
                        if (v_clamped > MAX_GAIN_INT) v_clamped = MAX_GAIN_INT;
                        leftVol = (uint16_t)v_clamped;
                        v_clamped = (vr + (1 << 11)) >> 12;
                        if (v_clamped > MAX_GAIN_INT) v_clamped = MAX_GAIN_INT;
                        rightVol = (uint16_t)v_clamped;
                    } else {
                        leftVol = mLeftVolShort;
                        rightVol = mRightVolShort;
                        rampVolume = false;
                    }

                    // reset retry count
                    track->mRetryCount = kMaxTrackRetriesDirect;
                    activeTrack = t;
                    mixerStatus = MIXER_TRACKS_READY;
                } else {
                    //LOGV("track %d u=%08x, s=%08x [NOT READY]", track->name(), cblk->user, cblk->server);
                    if (track->isStopped()) {
                        track->reset();
                    }
                    if (track->isTerminated() || track->isStopped() || track->isPaused()) {
                        // We have consumed all the buffers of this track.
                        // Remove it from the list of active tracks.
                        trackToRemove = track;
                    } else {
                        // No buffers for this track. Give it a few chances to
                        // fill a buffer, then remove it from active list.
                        if (--(track->mRetryCount) <= 0) {
                            LOGV("BUFFER TIMEOUT: remove(%d) from active list", track->name());
                            trackToRemove = track;
                        } else {
                            mixerStatus = MIXER_TRACKS_ENABLED;
                        }
                    }
                }
            }

            // remove all the tracks that need to be...
            if (UNLIKELY(trackToRemove != 0)) {
                mActiveTracks.remove(trackToRemove);
                if (!effectChains.isEmpty()) {
                    LOGV("stopping track on chain %p for session Id: %d", effectChains[0].get(),
                            trackToRemove->sessionId());
                    effectChains[0]->stopTrack();
                }
                if (trackToRemove->isTerminated()) {
                    mTracks.remove(trackToRemove);
                    deleteTrackName_l(trackToRemove->mName);
                }
            }

            lockEffectChains_l(effectChains);
       }

        if (LIKELY(mixerStatus == MIXER_TRACKS_READY)) {
            AudioBufferProvider::Buffer buffer;
            size_t frameCount = mFrameCount;
            curBuf = (int8_t *)mMixBuffer;
            // output audio to hardware
            while (frameCount) {
                buffer.frameCount = frameCount;
                activeTrack->getNextBuffer(&buffer);
                if (UNLIKELY(buffer.raw == 0)) {
                    memset(curBuf, 0, frameCount * mFrameSize);
                    break;
                }
                memcpy(curBuf, buffer.raw, buffer.frameCount * mFrameSize);
                frameCount -= buffer.frameCount;
                curBuf += buffer.frameCount * mFrameSize;
                activeTrack->releaseBuffer(&buffer);
            }
            sleepTime = 0;
            standbyTime = systemTime() + standbyDelay;
        } else {
            if (sleepTime == 0) {
                if (mixerStatus == MIXER_TRACKS_ENABLED) {
                    sleepTime = activeSleepTime;
                } else {
                    sleepTime = idleSleepTime;
                }
            } else if (mBytesWritten != 0 && AudioSystem::isLinearPCM(mFormat)) {
                memset (mMixBuffer, 0, mFrameCount * mFrameSize);
                sleepTime = 0;
            }
        }

        if (mSuspended) {
            sleepTime = suspendSleepTimeUs();
        }
        // sleepTime == 0 means we must write to audio hardware
        if (sleepTime == 0) {
            if (mixerStatus == MIXER_TRACKS_READY) {
                applyVolume(leftVol, rightVol, rampVolume);
            }
            for (size_t i = 0; i < effectChains.size(); i ++) {
                effectChains[i]->process_l();
            }
            unlockEffectChains(effectChains);

            mLastWriteTime = systemTime();
            mInWrite = true;
            mBytesWritten += mixBufferSize;
            int bytesWritten = (int)mOutput->write(mMixBuffer, mixBufferSize);
            if (bytesWritten < 0) mBytesWritten -= mixBufferSize;
            mNumWrites++;
            mInWrite = false;
            mStandby = false;
        } else {
            unlockEffectChains(effectChains);
            usleep(sleepTime);
        }

        // finally let go of removed track, without the lock held
        // since we can't guarantee the destructors won't acquire that
        // same lock.
        trackToRemove.clear();
        activeTrack.clear();

        // Effect chains will be actually deleted here if they were removed from
        // mEffectChains list during mixing or effects processing
        effectChains.clear();
    }

    if (!mStandby) {
        mOutput->standby();
    }

    LOGV("DirectOutputThread %p exiting", this);
    return false;
}

// getTrackName_l() must be called with ThreadBase::mLock held
int AudioFlinger::DirectOutputThread::getTrackName_l()
{
    return 0;
}

// deleteTrackName_l() must be called with ThreadBase::mLock held
void AudioFlinger::DirectOutputThread::deleteTrackName_l(int name)
{
}

// checkForNewParameters_l() must be called with ThreadBase::mLock held
bool AudioFlinger::DirectOutputThread::checkForNewParameters_l()
{
    bool reconfig = false;

    while (!mNewParameters.isEmpty()) {
        status_t status = NO_ERROR;
        String8 keyValuePair = mNewParameters[0];
        AudioParameter param = AudioParameter(keyValuePair);
        int value;

        if (param.getInt(String8(AudioParameter::keyFrameCount), value) == NO_ERROR) {
            // do not accept frame count changes if tracks are open as the track buffer
            // size depends on frame count and correct behavior would not be garantied
            // if frame count is changed after track creation
            if (!mTracks.isEmpty()) {
                status = INVALID_OPERATION;
            } else {
                reconfig = true;
            }
        }
        if (status == NO_ERROR) {
            status = mOutput->setParameters(keyValuePair);
            if (!mStandby && status == INVALID_OPERATION) {
               mOutput->standby();
               mStandby = true;
               mBytesWritten = 0;
               status = mOutput->setParameters(keyValuePair);
            }
            if (status == NO_ERROR && reconfig) {
                readOutputParameters();
                sendConfigEvent_l(AudioSystem::OUTPUT_CONFIG_CHANGED);
            }
        }

        mNewParameters.removeAt(0);

        mParamStatus = status;
        mParamCond.signal();
        mWaitWorkCV.wait(mLock);
    }
    return reconfig;
}

uint32_t AudioFlinger::DirectOutputThread::activeSleepTimeUs()
{
    uint32_t time;
    if (AudioSystem::isLinearPCM(mFormat)) {
        time = (uint32_t)(mOutput->latency() * 1000) / 2;
    } else {
        time = 10000;
    }
    return time;
}

uint32_t AudioFlinger::DirectOutputThread::idleSleepTimeUs()
{
    uint32_t time;
    if (AudioSystem::isLinearPCM(mFormat)) {
        time = (uint32_t)(((mFrameCount * 1000) / mSampleRate) * 1000) / 2;
    } else {
        time = 10000;
    }
    return time;
}

uint32_t AudioFlinger::DirectOutputThread::suspendSleepTimeUs()
{
    uint32_t time;
    if (AudioSystem::isLinearPCM(mFormat)) {
        time = (uint32_t)(((mFrameCount * 1000) / mSampleRate) * 1000);
    } else {
        time = 10000;
    }
    return time;
}


// ----------------------------------------------------------------------------

AudioFlinger::DuplicatingThread::DuplicatingThread(const sp<AudioFlinger>& audioFlinger, AudioFlinger::MixerThread* mainThread, int id)
    :   MixerThread(audioFlinger, mainThread->getOutput(), id, mainThread->device()), mWaitTimeMs(UINT_MAX)
{
    mType = PlaybackThread::DUPLICATING;
    addOutputTrack(mainThread);
}

AudioFlinger::DuplicatingThread::~DuplicatingThread()
{
    for (size_t i = 0; i < mOutputTracks.size(); i++) {
        mOutputTracks[i]->destroy();
    }
    mOutputTracks.clear();
}

bool AudioFlinger::DuplicatingThread::threadLoop()
{
    Vector< sp<Track> > tracksToRemove;
    uint32_t mixerStatus = MIXER_IDLE;
    nsecs_t standbyTime = systemTime();
    size_t mixBufferSize = mFrameCount*mFrameSize;
    SortedVector< sp<OutputTrack> > outputTracks;
    uint32_t writeFrames = 0;
    uint32_t activeSleepTime = activeSleepTimeUs();
    uint32_t idleSleepTime = idleSleepTimeUs();
    uint32_t sleepTime = idleSleepTime;
    Vector< sp<EffectChain> > effectChains;

    while (!exitPending())
    {
        processConfigEvents();

        mixerStatus = MIXER_IDLE;
        { // scope for the mLock

            Mutex::Autolock _l(mLock);

            if (checkForNewParameters_l()) {
                mixBufferSize = mFrameCount*mFrameSize;
                updateWaitTime();
                activeSleepTime = activeSleepTimeUs();
                idleSleepTime = idleSleepTimeUs();
            }

            const SortedVector< wp<Track> >& activeTracks = mActiveTracks;

            for (size_t i = 0; i < mOutputTracks.size(); i++) {
                outputTracks.add(mOutputTracks[i]);
            }

            // put audio hardware into standby after short delay
            if UNLIKELY((!activeTracks.size() && systemTime() > standbyTime) ||
                         mSuspended) {
                if (!mStandby) {
                    for (size_t i = 0; i < outputTracks.size(); i++) {
                        outputTracks[i]->stop();
                    }
                    mStandby = true;
                    mBytesWritten = 0;
                }

                if (!activeTracks.size() && mConfigEvents.isEmpty()) {
                    // we're about to wait, flush the binder command buffer
                    IPCThreadState::self()->flushCommands();
                    outputTracks.clear();

                    if (exitPending()) break;

                    LOGV("DuplicatingThread %p TID %d going to sleep\n", this, gettid());
                    mWaitWorkCV.wait(mLock);
                    LOGV("DuplicatingThread %p TID %d waking up\n", this, gettid());
                    if (mMasterMute == false) {
                        char value[PROPERTY_VALUE_MAX];
                        property_get("ro.audio.silent", value, "0");
                        if (atoi(value)) {
                            LOGD("Silence is golden");
                            setMasterMute(true);
                        }
                    }

                    standbyTime = systemTime() + kStandbyTimeInNsecs;
                    sleepTime = idleSleepTime;
                    continue;
                }
            }

            mixerStatus = prepareTracks_l(activeTracks, &tracksToRemove);

            // prevent any changes in effect chain list and in each effect chain
            // during mixing and effect process as the audio buffers could be deleted
            // or modified if an effect is created or deleted
            lockEffectChains_l(effectChains);
        }

        if (LIKELY(mixerStatus == MIXER_TRACKS_READY)) {
            // mix buffers...
            if (outputsReady(outputTracks)) {
                mAudioMixer->process();
            } else {
                memset(mMixBuffer, 0, mixBufferSize);
            }
            sleepTime = 0;
            writeFrames = mFrameCount;
        } else {
            if (sleepTime == 0) {
                if (mixerStatus == MIXER_TRACKS_ENABLED) {
                    sleepTime = activeSleepTime;
                } else {
                    sleepTime = idleSleepTime;
                }
            } else if (mBytesWritten != 0) {
                // flush remaining overflow buffers in output tracks
                for (size_t i = 0; i < outputTracks.size(); i++) {
                    if (outputTracks[i]->isActive()) {
                        sleepTime = 0;
                        writeFrames = 0;
                        memset(mMixBuffer, 0, mixBufferSize);
                        break;
                    }
                }
            }
        }

        if (mSuspended) {
            sleepTime = suspendSleepTimeUs();
        }
        // sleepTime == 0 means we must write to audio hardware
        if (sleepTime == 0) {
            for (size_t i = 0; i < effectChains.size(); i ++) {
                effectChains[i]->process_l();
            }
            // enable changes in effect chain
            unlockEffectChains(effectChains);

            standbyTime = systemTime() + kStandbyTimeInNsecs;
            for (size_t i = 0; i < outputTracks.size(); i++) {
                outputTracks[i]->write(mMixBuffer, writeFrames);
            }
            mStandby = false;
            mBytesWritten += mixBufferSize;
        } else {
            // enable changes in effect chain
            unlockEffectChains(effectChains);
            usleep(sleepTime);
        }

        // finally let go of all our tracks, without the lock held
        // since we can't guarantee the destructors won't acquire that
        // same lock.
        tracksToRemove.clear();
        outputTracks.clear();

        // Effect chains will be actually deleted here if they were removed from
        // mEffectChains list during mixing or effects processing
        effectChains.clear();
    }

    return false;
}

void AudioFlinger::DuplicatingThread::addOutputTrack(MixerThread *thread)
{
    int frameCount = (3 * mFrameCount * mSampleRate) / thread->sampleRate();
    OutputTrack *outputTrack = new OutputTrack((ThreadBase *)thread,
                                            this,
                                            mSampleRate,
                                            mFormat,
                                            mChannelCount,
                                            frameCount);
    if (outputTrack->cblk() != NULL) {
        thread->setStreamVolume(AudioSystem::NUM_STREAM_TYPES, 1.0f);
        mOutputTracks.add(outputTrack);
        LOGV("addOutputTrack() track %p, on thread %p", outputTrack, thread);
        updateWaitTime();
    }
}

void AudioFlinger::DuplicatingThread::removeOutputTrack(MixerThread *thread)
{
    Mutex::Autolock _l(mLock);
    for (size_t i = 0; i < mOutputTracks.size(); i++) {
        if (mOutputTracks[i]->thread() == (ThreadBase *)thread) {
            mOutputTracks[i]->destroy();
            mOutputTracks.removeAt(i);
            updateWaitTime();
            return;
        }
    }
    LOGV("removeOutputTrack(): unkonwn thread: %p", thread);
}

void AudioFlinger::DuplicatingThread::updateWaitTime()
{
    mWaitTimeMs = UINT_MAX;
    for (size_t i = 0; i < mOutputTracks.size(); i++) {
        sp<ThreadBase> strong = mOutputTracks[i]->thread().promote();
        if (strong != NULL) {
            uint32_t waitTimeMs = (strong->frameCount() * 2 * 1000) / strong->sampleRate();
            if (waitTimeMs < mWaitTimeMs) {
                mWaitTimeMs = waitTimeMs;
            }
        }
    }
}


bool AudioFlinger::DuplicatingThread::outputsReady(SortedVector< sp<OutputTrack> > &outputTracks)
{
    for (size_t i = 0; i < outputTracks.size(); i++) {
        sp <ThreadBase> thread = outputTracks[i]->thread().promote();
        if (thread == 0) {
            LOGW("DuplicatingThread::outputsReady() could not promote thread on output track %p", outputTracks[i].get());
            return false;
        }
        PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
        if (playbackThread->standby() && !playbackThread->isSuspended()) {
            LOGV("DuplicatingThread output track %p on thread %p Not Ready", outputTracks[i].get(), thread.get());
            return false;
        }
    }
    return true;
}

uint32_t AudioFlinger::DuplicatingThread::activeSleepTimeUs()
{
    return (mWaitTimeMs * 1000) / 2;
}

// ----------------------------------------------------------------------------

// TrackBase constructor must be called with AudioFlinger::mLock held
AudioFlinger::ThreadBase::TrackBase::TrackBase(
            const wp<ThreadBase>& thread,
            const sp<Client>& client,
            uint32_t sampleRate,
            int format,
            int channelCount,
            int frameCount,
            uint32_t flags,
            const sp<IMemory>& sharedBuffer,
            int sessionId)
    :   RefBase(),
        mThread(thread),
        mClient(client),
        mCblk(0),
        mFrameCount(0),
        mState(IDLE),
        mClientTid(-1),
        mFormat(format),
        mFlags(flags & ~SYSTEM_FLAGS_MASK),
        mSessionId(sessionId)
{
    LOGV_IF(sharedBuffer != 0, "sharedBuffer: %p, size: %d", sharedBuffer->pointer(), sharedBuffer->size());

    // LOGD("Creating track with %d buffers @ %d bytes", bufferCount, bufferSize);
   size_t size = sizeof(audio_track_cblk_t);
   size_t bufferSize = frameCount*channelCount*sizeof(int16_t);
   if (sharedBuffer == 0) {
       size += bufferSize;
   }

   if (client != NULL) {
        mCblkMemory = client->heap()->allocate(size);
        if (mCblkMemory != 0) {
            mCblk = static_cast<audio_track_cblk_t *>(mCblkMemory->pointer());
            if (mCblk) { // construct the shared structure in-place.
                new(mCblk) audio_track_cblk_t();
                // clear all buffers
                mCblk->frameCount = frameCount;
                mCblk->sampleRate = sampleRate;
                mCblk->channelCount = (uint8_t)channelCount;
                if (sharedBuffer == 0) {
                    mBuffer = (char*)mCblk + sizeof(audio_track_cblk_t);
                    memset(mBuffer, 0, frameCount*channelCount*sizeof(int16_t));
                    // Force underrun condition to avoid false underrun callback until first data is
                    // written to buffer (other flags are cleared)
                    mCblk->flags = CBLK_UNDERRUN_ON;
                } else {
                    mBuffer = sharedBuffer->pointer();
                }
                mBufferEnd = (uint8_t *)mBuffer + bufferSize;
            }
        } else {
            LOGE("not enough memory for AudioTrack size=%u", size);
            client->heap()->dump("AudioTrack");
            return;
        }
   } else {
       mCblk = (audio_track_cblk_t *)(new uint8_t[size]);
       if (mCblk) { // construct the shared structure in-place.
           new(mCblk) audio_track_cblk_t();
           // clear all buffers
           mCblk->frameCount = frameCount;
           mCblk->sampleRate = sampleRate;
           mCblk->channelCount = (uint8_t)channelCount;
           mBuffer = (char*)mCblk + sizeof(audio_track_cblk_t);
           memset(mBuffer, 0, frameCount*channelCount*sizeof(int16_t));
           // Force underrun condition to avoid false underrun callback until first data is
           // written to buffer (other flags are cleared)
           mCblk->flags = CBLK_UNDERRUN_ON;
           mBufferEnd = (uint8_t *)mBuffer + bufferSize;
       }
   }
}

AudioFlinger::ThreadBase::TrackBase::~TrackBase()
{
    if (mCblk) {
        mCblk->~audio_track_cblk_t();   // destroy our shared-structure.
        if (mClient == NULL) {
            delete mCblk;
        }
    }
    mCblkMemory.clear();            // and free the shared memory
    if (mClient != NULL) {
        Mutex::Autolock _l(mClient->audioFlinger()->mLock);
        mClient.clear();
    }
}

void AudioFlinger::ThreadBase::TrackBase::releaseBuffer(AudioBufferProvider::Buffer* buffer)
{
    buffer->raw = 0;
    mFrameCount = buffer->frameCount;
    step();
    buffer->frameCount = 0;
}

bool AudioFlinger::ThreadBase::TrackBase::step() {
    bool result;
    audio_track_cblk_t* cblk = this->cblk();

    result = cblk->stepServer(mFrameCount);
    if (!result) {
        LOGV("stepServer failed acquiring cblk mutex");
        mFlags |= STEPSERVER_FAILED;
    }
    return result;
}

void AudioFlinger::ThreadBase::TrackBase::reset() {
    audio_track_cblk_t* cblk = this->cblk();

    cblk->user = 0;
    cblk->server = 0;
    cblk->userBase = 0;
    cblk->serverBase = 0;
    mFlags &= (uint32_t)(~SYSTEM_FLAGS_MASK);
    LOGV("TrackBase::reset");
}

sp<IMemory> AudioFlinger::ThreadBase::TrackBase::getCblk() const
{
    return mCblkMemory;
}

int AudioFlinger::ThreadBase::TrackBase::sampleRate() const {
    return (int)mCblk->sampleRate;
}

int AudioFlinger::ThreadBase::TrackBase::channelCount() const {
    return (int)mCblk->channelCount;
}

void* AudioFlinger::ThreadBase::TrackBase::getBuffer(uint32_t offset, uint32_t frames) const {
    audio_track_cblk_t* cblk = this->cblk();
    int8_t *bufferStart = (int8_t *)mBuffer + (offset-cblk->serverBase)*cblk->frameSize;
    int8_t *bufferEnd = bufferStart + frames * cblk->frameSize;

    // Check validity of returned pointer in case the track control block would have been corrupted.
    if (bufferStart < mBuffer || bufferStart > bufferEnd || bufferEnd > mBufferEnd ||
        ((unsigned long)bufferStart & (unsigned long)(cblk->frameSize - 1))) {
        LOGE("TrackBase::getBuffer buffer out of range:\n    start: %p, end %p , mBuffer %p mBufferEnd %p\n    \
                server %d, serverBase %d, user %d, userBase %d, channelCount %d",
                bufferStart, bufferEnd, mBuffer, mBufferEnd,
                cblk->server, cblk->serverBase, cblk->user, cblk->userBase, cblk->channelCount);
        return 0;
    }

    return bufferStart;
}

// ----------------------------------------------------------------------------

// Track constructor must be called with AudioFlinger::mLock and ThreadBase::mLock held
AudioFlinger::PlaybackThread::Track::Track(
            const wp<ThreadBase>& thread,
            const sp<Client>& client,
            int streamType,
            uint32_t sampleRate,
            int format,
            int channelCount,
            int frameCount,
            const sp<IMemory>& sharedBuffer,
            int sessionId)
    :   TrackBase(thread, client, sampleRate, format, channelCount, frameCount, 0, sharedBuffer, sessionId),
    mMute(false), mSharedBuffer(sharedBuffer), mName(-1), mMainBuffer(NULL), mAuxBuffer(NULL),
    mAuxEffectId(0), mHasVolumeController(false)
{
    if (mCblk != NULL) {
        sp<ThreadBase> baseThread = thread.promote();
        if (baseThread != 0) {
            PlaybackThread *playbackThread = (PlaybackThread *)baseThread.get();
            mName = playbackThread->getTrackName_l();
            mMainBuffer = playbackThread->mixBuffer();
        }
        LOGV("Track constructor name %d, calling thread %d", mName, IPCThreadState::self()->getCallingPid());
        if (mName < 0) {
            LOGE("no more track names available");
        }
        mVolume[0] = 1.0f;
        mVolume[1] = 1.0f;
        mStreamType = streamType;
        // NOTE: audio_track_cblk_t::frameSize for 8 bit PCM data is based on a sample size of
        // 16 bit because data is converted to 16 bit before being stored in buffer by AudioTrack
        mCblk->frameSize = AudioSystem::isLinearPCM(format) ? channelCount * sizeof(int16_t) : sizeof(int8_t);
    }
}

AudioFlinger::PlaybackThread::Track::~Track()
{
    LOGV("PlaybackThread::Track destructor");
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        Mutex::Autolock _l(thread->mLock);
        mState = TERMINATED;
    }
}

void AudioFlinger::PlaybackThread::Track::destroy()
{
    // NOTE: destroyTrack_l() can remove a strong reference to this Track
    // by removing it from mTracks vector, so there is a risk that this Tracks's
    // desctructor is called. As the destructor needs to lock mLock,
    // we must acquire a strong reference on this Track before locking mLock
    // here so that the destructor is called only when exiting this function.
    // On the other hand, as long as Track::destroy() is only called by
    // TrackHandle destructor, the TrackHandle still holds a strong ref on
    // this Track with its member mTrack.
    sp<Track> keep(this);
    { // scope for mLock
        sp<ThreadBase> thread = mThread.promote();
        if (thread != 0) {
            if (!isOutputTrack()) {
                if (mState == ACTIVE || mState == RESUMING) {
                    AudioSystem::stopOutput(thread->id(),
                                            (AudioSystem::stream_type)mStreamType,
                                            mSessionId);
                }
                AudioSystem::releaseOutput(thread->id());
            }
            Mutex::Autolock _l(thread->mLock);
            PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
            playbackThread->destroyTrack_l(this);
        }
    }
}

void AudioFlinger::PlaybackThread::Track::dump(char* buffer, size_t size)
{
    snprintf(buffer, size, "   %05d %05d %03u %03u %03u %05u   %04u %1d %1d %1d %05u %05u %05u  0x%08x 0x%08x 0x%08x 0x%08x\n",
            mName - AudioMixer::TRACK0,
            (mClient == NULL) ? getpid() : mClient->pid(),
            mStreamType,
            mFormat,
            mCblk->channelCount,
            mSessionId,
            mFrameCount,
            mState,
            mMute,
            mFillingUpStatus,
            mCblk->sampleRate,
            mCblk->volume[0],
            mCblk->volume[1],
            mCblk->server,
            mCblk->user,
            (int)mMainBuffer,
            (int)mAuxBuffer);
}

status_t AudioFlinger::PlaybackThread::Track::getNextBuffer(AudioBufferProvider::Buffer* buffer)
{
     audio_track_cblk_t* cblk = this->cblk();
     uint32_t framesReady;
     uint32_t framesReq = buffer->frameCount;

     // Check if last stepServer failed, try to step now
     if (mFlags & TrackBase::STEPSERVER_FAILED) {
         if (!step())  goto getNextBuffer_exit;
         LOGV("stepServer recovered");
         mFlags &= ~TrackBase::STEPSERVER_FAILED;
     }

     framesReady = cblk->framesReady();

     if (LIKELY(framesReady)) {
        uint32_t s = cblk->server;
        uint32_t bufferEnd = cblk->serverBase + cblk->frameCount;

        bufferEnd = (cblk->loopEnd < bufferEnd) ? cblk->loopEnd : bufferEnd;
        if (framesReq > framesReady) {
            framesReq = framesReady;
        }
        if (s + framesReq > bufferEnd) {
            framesReq = bufferEnd - s;
        }

         buffer->raw = getBuffer(s, framesReq);
         if (buffer->raw == 0) goto getNextBuffer_exit;

         buffer->frameCount = framesReq;
        return NO_ERROR;
     }

getNextBuffer_exit:
     buffer->raw = 0;
     buffer->frameCount = 0;
     LOGV("getNextBuffer() no more data for track %d on thread %p", mName, mThread.unsafe_get());
     return NOT_ENOUGH_DATA;
}

bool AudioFlinger::PlaybackThread::Track::isReady() const {
    if (mFillingUpStatus != FS_FILLING || isStopped() || isPausing()) return true;

    if (mCblk->framesReady() >= mCblk->frameCount ||
            (mCblk->flags & CBLK_FORCEREADY_MSK)) {
        mFillingUpStatus = FS_FILLED;
        mCblk->flags &= ~CBLK_FORCEREADY_MSK;
        return true;
    }
    return false;
}

status_t AudioFlinger::PlaybackThread::Track::start()
{
    status_t status = NO_ERROR;
    LOGV("start(%d), calling thread %d session %d",
            mName, IPCThreadState::self()->getCallingPid(), mSessionId);
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        Mutex::Autolock _l(thread->mLock);
        int state = mState;
        // here the track could be either new, or restarted
        // in both cases "unstop" the track
        if (mState == PAUSED) {
            mState = TrackBase::RESUMING;
            LOGV("PAUSED => RESUMING (%d) on thread %p", mName, this);
        } else {
            mState = TrackBase::ACTIVE;
            LOGV("? => ACTIVE (%d) on thread %p", mName, this);
        }

        if (!isOutputTrack() && state != ACTIVE && state != RESUMING) {
            thread->mLock.unlock();
            status = AudioSystem::startOutput(thread->id(),
                                              (AudioSystem::stream_type)mStreamType,
                                              mSessionId);
            thread->mLock.lock();
        }
        if (status == NO_ERROR) {
            PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
            playbackThread->addTrack_l(this);
        } else {
            mState = state;
        }
    } else {
        status = BAD_VALUE;
    }
    return status;
}

void AudioFlinger::PlaybackThread::Track::stop()
{
    LOGV("stop(%d), calling thread %d", mName, IPCThreadState::self()->getCallingPid());
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        Mutex::Autolock _l(thread->mLock);
        int state = mState;
        if (mState > STOPPED) {
            mState = STOPPED;
            // If the track is not active (PAUSED and buffers full), flush buffers
            PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
            if (playbackThread->mActiveTracks.indexOf(this) < 0) {
                reset();
            }
            LOGV("(> STOPPED) => STOPPED (%d) on thread %p", mName, playbackThread);
        }
        if (!isOutputTrack() && (state == ACTIVE || state == RESUMING)) {
            thread->mLock.unlock();
            AudioSystem::stopOutput(thread->id(),
                                    (AudioSystem::stream_type)mStreamType,
                                    mSessionId);
            thread->mLock.lock();
        }
    }
}

void AudioFlinger::PlaybackThread::Track::pause()
{
    LOGV("pause(%d), calling thread %d", mName, IPCThreadState::self()->getCallingPid());
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        Mutex::Autolock _l(thread->mLock);
        if (mState == ACTIVE || mState == RESUMING) {
            mState = PAUSING;
            LOGV("ACTIVE/RESUMING => PAUSING (%d) on thread %p", mName, thread.get());
            if (!isOutputTrack()) {
                thread->mLock.unlock();
                AudioSystem::stopOutput(thread->id(),
                                        (AudioSystem::stream_type)mStreamType,
                                        mSessionId);
                thread->mLock.lock();
            }
        }
    }
}

void AudioFlinger::PlaybackThread::Track::flush()
{
    LOGV("flush(%d)", mName);
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        Mutex::Autolock _l(thread->mLock);
        if (mState != STOPPED && mState != PAUSED && mState != PAUSING) {
            return;
        }
        // No point remaining in PAUSED state after a flush => go to
        // STOPPED state
        mState = STOPPED;

        mCblk->lock.lock();
        // NOTE: reset() will reset cblk->user and cblk->server with
        // the risk that at the same time, the AudioMixer is trying to read
        // data. In this case, getNextBuffer() would return a NULL pointer
        // as audio buffer => the AudioMixer code MUST always test that pointer
        // returned by getNextBuffer() is not NULL!
        reset();
        mCblk->lock.unlock();
    }
}

void AudioFlinger::PlaybackThread::Track::reset()
{
    // Do not reset twice to avoid discarding data written just after a flush and before
    // the audioflinger thread detects the track is stopped.
    if (!mResetDone) {
        TrackBase::reset();
        // Force underrun condition to avoid false underrun callback until first data is
        // written to buffer
        mCblk->flags |= CBLK_UNDERRUN_ON;
        mCblk->flags &= ~CBLK_FORCEREADY_MSK;
        mFillingUpStatus = FS_FILLING;
        mResetDone = true;
    }
}

void AudioFlinger::PlaybackThread::Track::mute(bool muted)
{
    mMute = muted;
}

void AudioFlinger::PlaybackThread::Track::setVolume(float left, float right)
{
    mVolume[0] = left;
    mVolume[1] = right;
}

status_t AudioFlinger::PlaybackThread::Track::attachAuxEffect(int EffectId)
{
    status_t status = DEAD_OBJECT;
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
       PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
       status = playbackThread->attachAuxEffect(this, EffectId);
    }
    return status;
}

void AudioFlinger::PlaybackThread::Track::setAuxBuffer(int EffectId, int32_t *buffer)
{
    mAuxEffectId = EffectId;
    mAuxBuffer = buffer;
}

// ----------------------------------------------------------------------------

// RecordTrack constructor must be called with AudioFlinger::mLock held
AudioFlinger::RecordThread::RecordTrack::RecordTrack(
            const wp<ThreadBase>& thread,
            const sp<Client>& client,
            uint32_t sampleRate,
            int format,
            int channelCount,
            int frameCount,
            uint32_t flags,
            int sessionId)
    :   TrackBase(thread, client, sampleRate, format,
                  channelCount, frameCount, flags, 0, sessionId),
        mOverflow(false)
{
    if (mCblk != NULL) {
       LOGV("RecordTrack constructor, size %d", (int)mBufferEnd - (int)mBuffer);
       if (format == AudioSystem::PCM_16_BIT) {
           mCblk->frameSize = channelCount * sizeof(int16_t);
       } else if (format == AudioSystem::PCM_8_BIT) {
           mCblk->frameSize = channelCount * sizeof(int8_t);
       } else {
           mCblk->frameSize = sizeof(int8_t);
       }
    }
}

AudioFlinger::RecordThread::RecordTrack::~RecordTrack()
{
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        AudioSystem::releaseInput(thread->id());
    }
}

status_t AudioFlinger::RecordThread::RecordTrack::getNextBuffer(AudioBufferProvider::Buffer* buffer)
{
    audio_track_cblk_t* cblk = this->cblk();
    uint32_t framesAvail;
    uint32_t framesReq = buffer->frameCount;

     // Check if last stepServer failed, try to step now
    if (mFlags & TrackBase::STEPSERVER_FAILED) {
        if (!step()) goto getNextBuffer_exit;
        LOGV("stepServer recovered");
        mFlags &= ~TrackBase::STEPSERVER_FAILED;
    }

    framesAvail = cblk->framesAvailable_l();

    if (LIKELY(framesAvail)) {
        uint32_t s = cblk->server;
        uint32_t bufferEnd = cblk->serverBase + cblk->frameCount;

        if (framesReq > framesAvail) {
            framesReq = framesAvail;
        }
        if (s + framesReq > bufferEnd) {
            framesReq = bufferEnd - s;
        }

        buffer->raw = getBuffer(s, framesReq);
        if (buffer->raw == 0) goto getNextBuffer_exit;

        buffer->frameCount = framesReq;
        return NO_ERROR;
    }

getNextBuffer_exit:
    buffer->raw = 0;
    buffer->frameCount = 0;
    return NOT_ENOUGH_DATA;
}

status_t AudioFlinger::RecordThread::RecordTrack::start()
{
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        RecordThread *recordThread = (RecordThread *)thread.get();
        return recordThread->start(this);
    } else {
        return BAD_VALUE;
    }
}

void AudioFlinger::RecordThread::RecordTrack::stop()
{
    sp<ThreadBase> thread = mThread.promote();
    if (thread != 0) {
        RecordThread *recordThread = (RecordThread *)thread.get();
        recordThread->stop(this);
        TrackBase::reset();
        // Force overerrun condition to avoid false overrun callback until first data is
        // read from buffer
        mCblk->flags |= CBLK_UNDERRUN_ON;
    }
}

void AudioFlinger::RecordThread::RecordTrack::dump(char* buffer, size_t size)
{
    snprintf(buffer, size, "   %05d %03u %03u %05d   %04u %01d %05u  %08x %08x\n",
            (mClient == NULL) ? getpid() : mClient->pid(),
            mFormat,
            mCblk->channelCount,
            mSessionId,
            mFrameCount,
            mState,
            mCblk->sampleRate,
            mCblk->server,
            mCblk->user);
}


// ----------------------------------------------------------------------------

AudioFlinger::PlaybackThread::OutputTrack::OutputTrack(
            const wp<ThreadBase>& thread,
            DuplicatingThread *sourceThread,
            uint32_t sampleRate,
            int format,
            int channelCount,
            int frameCount)
    :   Track(thread, NULL, AudioSystem::NUM_STREAM_TYPES, sampleRate, format, channelCount, frameCount, NULL, 0),
    mActive(false), mSourceThread(sourceThread)
{

    PlaybackThread *playbackThread = (PlaybackThread *)thread.unsafe_get();
    if (mCblk != NULL) {
        mCblk->flags |= CBLK_DIRECTION_OUT;
        mCblk->buffers = (char*)mCblk + sizeof(audio_track_cblk_t);
        mCblk->volume[0] = mCblk->volume[1] = 0x1000;
        mOutBuffer.frameCount = 0;
        playbackThread->mTracks.add(this);
        LOGV("OutputTrack constructor mCblk %p, mBuffer %p, mCblk->buffers %p, mCblk->frameCount %d, mCblk->sampleRate %d, mCblk->channelCount %d mBufferEnd %p",
                mCblk, mBuffer, mCblk->buffers, mCblk->frameCount, mCblk->sampleRate, mCblk->channelCount, mBufferEnd);
    } else {
        LOGW("Error creating output track on thread %p", playbackThread);
    }
}

AudioFlinger::PlaybackThread::OutputTrack::~OutputTrack()
{
    clearBufferQueue();
}

status_t AudioFlinger::PlaybackThread::OutputTrack::start()
{
    status_t status = Track::start();
    if (status != NO_ERROR) {
        return status;
    }

    mActive = true;
    mRetryCount = 127;
    return status;
}

void AudioFlinger::PlaybackThread::OutputTrack::stop()
{
    Track::stop();
    clearBufferQueue();
    mOutBuffer.frameCount = 0;
    mActive = false;
}

bool AudioFlinger::PlaybackThread::OutputTrack::write(int16_t* data, uint32_t frames)
{
    Buffer *pInBuffer;
    Buffer inBuffer;
    uint32_t channelCount = mCblk->channelCount;
    bool outputBufferFull = false;
    inBuffer.frameCount = frames;
    inBuffer.i16 = data;

    uint32_t waitTimeLeftMs = mSourceThread->waitTimeMs();

    if (!mActive && frames != 0) {
        start();
        sp<ThreadBase> thread = mThread.promote();
        if (thread != 0) {
            MixerThread *mixerThread = (MixerThread *)thread.get();
            if (mCblk->frameCount > frames){
                if (mBufferQueue.size() < kMaxOverFlowBuffers) {
                    uint32_t startFrames = (mCblk->frameCount - frames);
                    pInBuffer = new Buffer;
                    pInBuffer->mBuffer = new int16_t[startFrames * channelCount];
                    pInBuffer->frameCount = startFrames;
                    pInBuffer->i16 = pInBuffer->mBuffer;
                    memset(pInBuffer->raw, 0, startFrames * channelCount * sizeof(int16_t));
                    mBufferQueue.add(pInBuffer);
                } else {
                    LOGW ("OutputTrack::write() %p no more buffers in queue", this);
                }
            }
        }
    }

    while (waitTimeLeftMs) {
        // First write pending buffers, then new data
        if (mBufferQueue.size()) {
            pInBuffer = mBufferQueue.itemAt(0);
        } else {
            pInBuffer = &inBuffer;
        }

        if (pInBuffer->frameCount == 0) {
            break;
        }

        if (mOutBuffer.frameCount == 0) {
            mOutBuffer.frameCount = pInBuffer->frameCount;
            nsecs_t startTime = systemTime();
            if (obtainBuffer(&mOutBuffer, waitTimeLeftMs) == (status_t)AudioTrack::NO_MORE_BUFFERS) {
                LOGV ("OutputTrack::write() %p thread %p no more output buffers", this, mThread.unsafe_get());
                outputBufferFull = true;
                break;
            }
            uint32_t waitTimeMs = (uint32_t)ns2ms(systemTime() - startTime);
            if (waitTimeLeftMs >= waitTimeMs) {
                waitTimeLeftMs -= waitTimeMs;
            } else {
                waitTimeLeftMs = 0;
            }
        }

        uint32_t outFrames = pInBuffer->frameCount > mOutBuffer.frameCount ? mOutBuffer.frameCount : pInBuffer->frameCount;
        memcpy(mOutBuffer.raw, pInBuffer->raw, outFrames * channelCount * sizeof(int16_t));
        mCblk->stepUser(outFrames);
        pInBuffer->frameCount -= outFrames;
        pInBuffer->i16 += outFrames * channelCount;
        mOutBuffer.frameCount -= outFrames;
        mOutBuffer.i16 += outFrames * channelCount;

        if (pInBuffer->frameCount == 0) {
            if (mBufferQueue.size()) {
                mBufferQueue.removeAt(0);
                delete [] pInBuffer->mBuffer;
                delete pInBuffer;
                LOGV("OutputTrack::write() %p thread %p released overflow buffer %d", this, mThread.unsafe_get(), mBufferQueue.size());
            } else {
                break;
            }
        }
    }

    // If we could not write all frames, allocate a buffer and queue it for next time.
    if (inBuffer.frameCount) {
        sp<ThreadBase> thread = mThread.promote();
        if (thread != 0 && !thread->standby()) {
            if (mBufferQueue.size() < kMaxOverFlowBuffers) {
                pInBuffer = new Buffer;
                pInBuffer->mBuffer = new int16_t[inBuffer.frameCount * channelCount];
                pInBuffer->frameCount = inBuffer.frameCount;
                pInBuffer->i16 = pInBuffer->mBuffer;
                memcpy(pInBuffer->raw, inBuffer.raw, inBuffer.frameCount * channelCount * sizeof(int16_t));
                mBufferQueue.add(pInBuffer);
                LOGV("OutputTrack::write() %p thread %p adding overflow buffer %d", this, mThread.unsafe_get(), mBufferQueue.size());
            } else {
                LOGW("OutputTrack::write() %p thread %p no more overflow buffers", mThread.unsafe_get(), this);
            }
        }
    }

    // Calling write() with a 0 length buffer, means that no more data will be written:
    // If no more buffers are pending, fill output track buffer to make sure it is started
    // by output mixer.
    if (frames == 0 && mBufferQueue.size() == 0) {
        if (mCblk->user < mCblk->frameCount) {
            frames = mCblk->frameCount - mCblk->user;
            pInBuffer = new Buffer;
            pInBuffer->mBuffer = new int16_t[frames * channelCount];
            pInBuffer->frameCount = frames;
            pInBuffer->i16 = pInBuffer->mBuffer;
            memset(pInBuffer->raw, 0, frames * channelCount * sizeof(int16_t));
            mBufferQueue.add(pInBuffer);
        } else if (mActive) {
            stop();
        }
    }

    return outputBufferFull;
}

status_t AudioFlinger::PlaybackThread::OutputTrack::obtainBuffer(AudioBufferProvider::Buffer* buffer, uint32_t waitTimeMs)
{
    int active;
    status_t result;
    audio_track_cblk_t* cblk = mCblk;
    uint32_t framesReq = buffer->frameCount;

//    LOGV("OutputTrack::obtainBuffer user %d, server %d", cblk->user, cblk->server);
    buffer->frameCount  = 0;

    uint32_t framesAvail = cblk->framesAvailable();


    if (framesAvail == 0) {
        Mutex::Autolock _l(cblk->lock);
        goto start_loop_here;
        while (framesAvail == 0) {
            active = mActive;
            if (UNLIKELY(!active)) {
                LOGV("Not active and NO_MORE_BUFFERS");
                return AudioTrack::NO_MORE_BUFFERS;
            }
            result = cblk->cv.waitRelative(cblk->lock, milliseconds(waitTimeMs));
            if (result != NO_ERROR) {
                return AudioTrack::NO_MORE_BUFFERS;
            }
            // read the server count again
        start_loop_here:
            framesAvail = cblk->framesAvailable_l();
        }
    }

//    if (framesAvail < framesReq) {
//        return AudioTrack::NO_MORE_BUFFERS;
//    }

    if (framesReq > framesAvail) {
        framesReq = framesAvail;
    }

    uint32_t u = cblk->user;
    uint32_t bufferEnd = cblk->userBase + cblk->frameCount;

    if (u + framesReq > bufferEnd) {
        framesReq = bufferEnd - u;
    }

    buffer->frameCount  = framesReq;
    buffer->raw         = (void *)cblk->buffer(u);
    return NO_ERROR;
}


void AudioFlinger::PlaybackThread::OutputTrack::clearBufferQueue()
{
    size_t size = mBufferQueue.size();
    Buffer *pBuffer;

    for (size_t i = 0; i < size; i++) {
        pBuffer = mBufferQueue.itemAt(i);
        delete [] pBuffer->mBuffer;
        delete pBuffer;
    }
    mBufferQueue.clear();
}

// ----------------------------------------------------------------------------

AudioFlinger::Client::Client(const sp<AudioFlinger>& audioFlinger, pid_t pid)
    :   RefBase(),
        mAudioFlinger(audioFlinger),
        mMemoryDealer(new MemoryDealer(1024*1024, "AudioFlinger::Client")),
        mPid(pid)
{
    // 1 MB of address space is good for 32 tracks, 8 buffers each, 4 KB/buffer
}

// Client destructor must be called with AudioFlinger::mLock held
AudioFlinger::Client::~Client()
{
    mAudioFlinger->removeClient_l(mPid);
}

const sp<MemoryDealer>& AudioFlinger::Client::heap() const
{
    return mMemoryDealer;
}

// ----------------------------------------------------------------------------

AudioFlinger::NotificationClient::NotificationClient(const sp<AudioFlinger>& audioFlinger,
                                                     const sp<IAudioFlingerClient>& client,
                                                     pid_t pid)
    : mAudioFlinger(audioFlinger), mPid(pid), mClient(client)
{
}

AudioFlinger::NotificationClient::~NotificationClient()
{
    mClient.clear();
}

void AudioFlinger::NotificationClient::binderDied(const wp<IBinder>& who)
{
    sp<NotificationClient> keep(this);
    {
        mAudioFlinger->removeNotificationClient(mPid);
    }
}

// ----------------------------------------------------------------------------

AudioFlinger::TrackHandle::TrackHandle(const sp<AudioFlinger::PlaybackThread::Track>& track)
    : BnAudioTrack(),
      mTrack(track)
{
}

AudioFlinger::TrackHandle::~TrackHandle() {
    // just stop the track on deletion, associated resources
    // will be freed from the main thread once all pending buffers have
    // been played. Unless it's not in the active track list, in which
    // case we free everything now...
    mTrack->destroy();
}

status_t AudioFlinger::TrackHandle::start() {
    return mTrack->start();
}

void AudioFlinger::TrackHandle::stop() {
    mTrack->stop();
}

void AudioFlinger::TrackHandle::flush() {
    mTrack->flush();
}

void AudioFlinger::TrackHandle::mute(bool e) {
    mTrack->mute(e);
}

void AudioFlinger::TrackHandle::pause() {
    mTrack->pause();
}

void AudioFlinger::TrackHandle::setVolume(float left, float right) {
    mTrack->setVolume(left, right);
}

sp<IMemory> AudioFlinger::TrackHandle::getCblk() const {
    return mTrack->getCblk();
}

status_t AudioFlinger::TrackHandle::attachAuxEffect(int EffectId)
{
    return mTrack->attachAuxEffect(EffectId);
}

status_t AudioFlinger::TrackHandle::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    return BnAudioTrack::onTransact(code, data, reply, flags);
}

// ----------------------------------------------------------------------------

sp<IAudioRecord> AudioFlinger::openRecord(
        pid_t pid,
        int input,
        uint32_t sampleRate,
        int format,
        int channelCount,
        int frameCount,
        uint32_t flags,
        int *sessionId,
        status_t *status)
{
    sp<RecordThread::RecordTrack> recordTrack;
    sp<RecordHandle> recordHandle;
    sp<Client> client;
    wp<Client> wclient;
    status_t lStatus;
    RecordThread *thread;
    size_t inFrameCount;
    int lSessionId;

    // check calling permissions
    if (!recordingAllowed()) {
        lStatus = PERMISSION_DENIED;
        goto Exit;
    }

    // add client to list
    { // scope for mLock
        Mutex::Autolock _l(mLock);
        thread = checkRecordThread_l(input);
        if (thread == NULL) {
            lStatus = BAD_VALUE;
            goto Exit;
        }

        wclient = mClients.valueFor(pid);
        if (wclient != NULL) {
            client = wclient.promote();
        } else {
            client = new Client(this, pid);
            mClients.add(pid, client);
        }

        // If no audio session id is provided, create one here
        if (sessionId != NULL && *sessionId != AudioSystem::SESSION_OUTPUT_MIX) {
            lSessionId = *sessionId;
        } else {
            lSessionId = nextUniqueId();
            if (sessionId != NULL) {
                *sessionId = lSessionId;
            }
        }
        // create new record track. The record track uses one track in mHardwareMixerThread by convention.
        recordTrack = new RecordThread::RecordTrack(thread, client, sampleRate,
                                                   format, channelCount, frameCount, flags, lSessionId);
    }
    if (recordTrack->getCblk() == NULL) {
        // remove local strong reference to Client before deleting the RecordTrack so that the Client
        // destructor is called by the TrackBase destructor with mLock held
        client.clear();
        recordTrack.clear();
        lStatus = NO_MEMORY;
        goto Exit;
    }

    // return to handle to client
    recordHandle = new RecordHandle(recordTrack);
    lStatus = NO_ERROR;

Exit:
    if (status) {
        *status = lStatus;
    }
    return recordHandle;
}

// ----------------------------------------------------------------------------

AudioFlinger::RecordHandle::RecordHandle(const sp<AudioFlinger::RecordThread::RecordTrack>& recordTrack)
    : BnAudioRecord(),
    mRecordTrack(recordTrack)
{
}

AudioFlinger::RecordHandle::~RecordHandle() {
    stop();
}

status_t AudioFlinger::RecordHandle::start() {
    LOGV("RecordHandle::start()");
    return mRecordTrack->start();
}

void AudioFlinger::RecordHandle::stop() {
    LOGV("RecordHandle::stop()");
    mRecordTrack->stop();
}

sp<IMemory> AudioFlinger::RecordHandle::getCblk() const {
    return mRecordTrack->getCblk();
}

status_t AudioFlinger::RecordHandle::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    return BnAudioRecord::onTransact(code, data, reply, flags);
}

// ----------------------------------------------------------------------------

AudioFlinger::RecordThread::RecordThread(const sp<AudioFlinger>& audioFlinger, AudioStreamIn *input, uint32_t sampleRate, uint32_t channels, int id) :
    ThreadBase(audioFlinger, id),
    mInput(input), mResampler(0), mRsmpOutBuffer(0), mRsmpInBuffer(0)
{
    mReqChannelCount = AudioSystem::popCount(channels);
    mReqSampleRate = sampleRate;
    readInputParameters();
}


AudioFlinger::RecordThread::~RecordThread()
{
    delete[] mRsmpInBuffer;
    if (mResampler != 0) {
        delete mResampler;
        delete[] mRsmpOutBuffer;
    }
}

void AudioFlinger::RecordThread::onFirstRef()
{
    const size_t SIZE = 256;
    char buffer[SIZE];

    snprintf(buffer, SIZE, "Record Thread %p", this);

    run(buffer, PRIORITY_URGENT_AUDIO);
}

bool AudioFlinger::RecordThread::threadLoop()
{
    AudioBufferProvider::Buffer buffer;
    sp<RecordTrack> activeTrack;

    nsecs_t lastWarning = 0;

    // start recording
    while (!exitPending()) {

        processConfigEvents();

        { // scope for mLock
            Mutex::Autolock _l(mLock);
            checkForNewParameters_l();
            if (mActiveTrack == 0 && mConfigEvents.isEmpty()) {
                if (!mStandby) {
                    mInput->standby();
                    mStandby = true;
                }

                if (exitPending()) break;

                LOGV("RecordThread: loop stopping");
                // go to sleep
                mWaitWorkCV.wait(mLock);
                LOGV("RecordThread: loop starting");
                continue;
            }
            if (mActiveTrack != 0) {
                if (mActiveTrack->mState == TrackBase::PAUSING) {
                    if (!mStandby) {
                        mInput->standby();
                        mStandby = true;
                    }
                    mActiveTrack.clear();
                    mStartStopCond.broadcast();
                } else if (mActiveTrack->mState == TrackBase::RESUMING) {
                    if (mReqChannelCount != mActiveTrack->channelCount()) {
                        mActiveTrack.clear();
                        mStartStopCond.broadcast();
                    } else if (mBytesRead != 0) {
                        // record start succeeds only if first read from audio input
                        // succeeds
                        if (mBytesRead > 0) {
                            mActiveTrack->mState = TrackBase::ACTIVE;
                        } else {
                            mActiveTrack.clear();
                        }
                        mStartStopCond.broadcast();
                    }
                    mStandby = false;
                }
            }
        }

        if (mActiveTrack != 0) {
            if (mActiveTrack->mState != TrackBase::ACTIVE &&
                mActiveTrack->mState != TrackBase::RESUMING) {
                usleep(5000);
                continue;
            }
            buffer.frameCount = mFrameCount;
            if (LIKELY(mActiveTrack->getNextBuffer(&buffer) == NO_ERROR)) {
                size_t framesOut = buffer.frameCount;
                if (mResampler == 0) {
                    // no resampling
                    while (framesOut) {
                        size_t framesIn = mFrameCount - mRsmpInIndex;
                        if (framesIn) {
                            int8_t *src = (int8_t *)mRsmpInBuffer + mRsmpInIndex * mFrameSize;
                            int8_t *dst = buffer.i8 + (buffer.frameCount - framesOut) * mActiveTrack->mCblk->frameSize;
                            if (framesIn > framesOut)
                                framesIn = framesOut;
                            mRsmpInIndex += framesIn;
                            framesOut -= framesIn;
                            if ((int)mChannelCount == mReqChannelCount ||
                                mFormat != AudioSystem::PCM_16_BIT) {
                                memcpy(dst, src, framesIn * mFrameSize);
                            } else {
                                int16_t *src16 = (int16_t *)src;
                                int16_t *dst16 = (int16_t *)dst;
                                if (mChannelCount == 1) {
                                    while (framesIn--) {
                                        *dst16++ = *src16;
                                        *dst16++ = *src16++;
                                    }
                                } else {
                                    while (framesIn--) {
                                        *dst16++ = (int16_t)(((int32_t)*src16 + (int32_t)*(src16 + 1)) >> 1);
                                        src16 += 2;
                                    }
                                }
                            }
                        }
                        if (framesOut && mFrameCount == mRsmpInIndex) {
                            if (framesOut == mFrameCount &&
                                ((int)mChannelCount == mReqChannelCount || mFormat != AudioSystem::PCM_16_BIT)) {
                                mBytesRead = mInput->read(buffer.raw, mInputBytes);
                                framesOut = 0;
                            } else {
                                mBytesRead = mInput->read(mRsmpInBuffer, mInputBytes);
                                mRsmpInIndex = 0;
                            }
                            if (mBytesRead < 0) {
                                LOGE("Error reading audio input");
                                if (mActiveTrack->mState == TrackBase::ACTIVE) {
                                    // Force input into standby so that it tries to
                                    // recover at next read attempt
                                    mInput->standby();
                                    usleep(5000);
                                }
                                mRsmpInIndex = mFrameCount;
                                framesOut = 0;
                                buffer.frameCount = 0;
                            }
                        }
                    }
                } else {
                    // resampling

                    memset(mRsmpOutBuffer, 0, framesOut * 2 * sizeof(int32_t));
                    // alter output frame count as if we were expecting stereo samples
                    if (mChannelCount == 1 && mReqChannelCount == 1) {
                        framesOut >>= 1;
                    }
                    mResampler->resample(mRsmpOutBuffer, framesOut, this);
                    // ditherAndClamp() works as long as all buffers returned by mActiveTrack->getNextBuffer()
                    // are 32 bit aligned which should be always true.
                    if (mChannelCount == 2 && mReqChannelCount == 1) {
                        AudioMixer::ditherAndClamp(mRsmpOutBuffer, mRsmpOutBuffer, framesOut);
                        // the resampler always outputs stereo samples: do post stereo to mono conversion
                        int16_t *src = (int16_t *)mRsmpOutBuffer;
                        int16_t *dst = buffer.i16;
                        while (framesOut--) {
                            *dst++ = (int16_t)(((int32_t)*src + (int32_t)*(src + 1)) >> 1);
                            src += 2;
                        }
                    } else {
                        AudioMixer::ditherAndClamp((int32_t *)buffer.raw, mRsmpOutBuffer, framesOut);
                    }

                }
                mActiveTrack->releaseBuffer(&buffer);
                mActiveTrack->overflow();
            }
            // client isn't retrieving buffers fast enough
            else {
                if (!mActiveTrack->setOverflow()) {
                    nsecs_t now = systemTime();
                    if ((now - lastWarning) > kWarningThrottle) {
                        LOGW("RecordThread: buffer overflow");
                        lastWarning = now;
                    }
                }
                // Release the processor for a while before asking for a new buffer.
                // This will give the application more chance to read from the buffer and
                // clear the overflow.
                usleep(5000);
            }
        }
    }

    if (!mStandby) {
        mInput->standby();
    }
    mActiveTrack.clear();

    mStartStopCond.broadcast();

    LOGV("RecordThread %p exiting", this);
    return false;
}

status_t AudioFlinger::RecordThread::start(RecordThread::RecordTrack* recordTrack)
{
    LOGV("RecordThread::start");
    sp <ThreadBase> strongMe = this;
    status_t status = NO_ERROR;
    {
        AutoMutex lock(&mLock);
        if (mActiveTrack != 0) {
            if (recordTrack != mActiveTrack.get()) {
                status = -EBUSY;
            } else if (mActiveTrack->mState == TrackBase::PAUSING) {
                mActiveTrack->mState = TrackBase::ACTIVE;
            }
            return status;
        }

        recordTrack->mState = TrackBase::IDLE;
        mActiveTrack = recordTrack;
        mLock.unlock();
        status_t status = AudioSystem::startInput(mId);
        mLock.lock();
        if (status != NO_ERROR) {
            mActiveTrack.clear();
            return status;
        }
        mActiveTrack->mState = TrackBase::RESUMING;
        mRsmpInIndex = mFrameCount;
        mBytesRead = 0;
        // signal thread to start
        LOGV("Signal record thread");
        mWaitWorkCV.signal();
        // do not wait for mStartStopCond if exiting
        if (mExiting) {
            mActiveTrack.clear();
            status = INVALID_OPERATION;
            goto startError;
        }
        mStartStopCond.wait(mLock);
        if (mActiveTrack == 0) {
            LOGV("Record failed to start");
            status = BAD_VALUE;
            goto startError;
        }
        LOGV("Record started OK");
        return status;
    }
startError:
    AudioSystem::stopInput(mId);
    return status;
}

void AudioFlinger::RecordThread::stop(RecordThread::RecordTrack* recordTrack) {
    LOGV("RecordThread::stop");
    sp <ThreadBase> strongMe = this;
    {
        AutoMutex lock(&mLock);
        if (mActiveTrack != 0 && recordTrack == mActiveTrack.get()) {
            mActiveTrack->mState = TrackBase::PAUSING;
            // do not wait for mStartStopCond if exiting
            if (mExiting) {
                return;
            }
            mStartStopCond.wait(mLock);
            // if we have been restarted, recordTrack == mActiveTrack.get() here
            if (mActiveTrack == 0 || recordTrack != mActiveTrack.get()) {
                mLock.unlock();
                AudioSystem::stopInput(mId);
                mLock.lock();
                LOGV("Record stopped OK");
            }
        }
    }
}

status_t AudioFlinger::RecordThread::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    pid_t pid = 0;

    snprintf(buffer, SIZE, "\nInput thread %p internals\n", this);
    result.append(buffer);

    if (mActiveTrack != 0) {
        result.append("Active Track:\n");
        result.append("   Clien Fmt Chn Session Buf  S SRate  Serv     User\n");
        mActiveTrack->dump(buffer, SIZE);
        result.append(buffer);

        snprintf(buffer, SIZE, "In index: %d\n", mRsmpInIndex);
        result.append(buffer);
        snprintf(buffer, SIZE, "In size: %d\n", mInputBytes);
        result.append(buffer);
        snprintf(buffer, SIZE, "Resampling: %d\n", (mResampler != 0));
        result.append(buffer);
        snprintf(buffer, SIZE, "Out channel count: %d\n", mReqChannelCount);
        result.append(buffer);
        snprintf(buffer, SIZE, "Out sample rate: %d\n", mReqSampleRate);
        result.append(buffer);


    } else {
        result.append("No record client\n");
    }
    write(fd, result.string(), result.size());

    dumpBase(fd, args);

    return NO_ERROR;
}

status_t AudioFlinger::RecordThread::getNextBuffer(AudioBufferProvider::Buffer* buffer)
{
    size_t framesReq = buffer->frameCount;
    size_t framesReady = mFrameCount - mRsmpInIndex;
    int channelCount;

    if (framesReady == 0) {
        mBytesRead = mInput->read(mRsmpInBuffer, mInputBytes);
        if (mBytesRead < 0) {
            LOGE("RecordThread::getNextBuffer() Error reading audio input");
            if (mActiveTrack->mState == TrackBase::ACTIVE) {
                // Force input into standby so that it tries to
                // recover at next read attempt
                mInput->standby();
                usleep(5000);
            }
            buffer->raw = 0;
            buffer->frameCount = 0;
            return NOT_ENOUGH_DATA;
        }
        mRsmpInIndex = 0;
        framesReady = mFrameCount;
    }

    if (framesReq > framesReady) {
        framesReq = framesReady;
    }

    if (mChannelCount == 1 && mReqChannelCount == 2) {
        channelCount = 1;
    } else {
        channelCount = 2;
    }
    buffer->raw = mRsmpInBuffer + mRsmpInIndex * channelCount;
    buffer->frameCount = framesReq;
    return NO_ERROR;
}

void AudioFlinger::RecordThread::releaseBuffer(AudioBufferProvider::Buffer* buffer)
{
    mRsmpInIndex += buffer->frameCount;
    buffer->frameCount = 0;
}

bool AudioFlinger::RecordThread::checkForNewParameters_l()
{
    bool reconfig = false;

    while (!mNewParameters.isEmpty()) {
        status_t status = NO_ERROR;
        String8 keyValuePair = mNewParameters[0];
        AudioParameter param = AudioParameter(keyValuePair);
        int value;
        int reqFormat = mFormat;
        int reqSamplingRate = mReqSampleRate;
        int reqChannelCount = mReqChannelCount;

        if (param.getInt(String8(AudioParameter::keySamplingRate), value) == NO_ERROR) {
            reqSamplingRate = value;
            reconfig = true;
        }
        if (param.getInt(String8(AudioParameter::keyFormat), value) == NO_ERROR) {
            reqFormat = value;
            reconfig = true;
        }
        if (param.getInt(String8(AudioParameter::keyChannels), value) == NO_ERROR) {
            reqChannelCount = AudioSystem::popCount(value);
            reconfig = true;
        }
        if (param.getInt(String8(AudioParameter::keyFrameCount), value) == NO_ERROR) {
            // do not accept frame count changes if tracks are open as the track buffer
            // size depends on frame count and correct behavior would not be garantied
            // if frame count is changed after track creation
            if (mActiveTrack != 0) {
                status = INVALID_OPERATION;
            } else {
                reconfig = true;
            }
        }
        if (status == NO_ERROR) {
            status = mInput->setParameters(keyValuePair);
            if (status == INVALID_OPERATION) {
               mInput->standby();
               status = mInput->setParameters(keyValuePair);
            }
            if (reconfig) {
                if (status == BAD_VALUE &&
                    reqFormat == mInput->format() && reqFormat == AudioSystem::PCM_16_BIT &&
                    ((int)mInput->sampleRate() <= 2 * reqSamplingRate) &&
                    (AudioSystem::popCount(mInput->channels()) < 3) && (reqChannelCount < 3)) {
                    status = NO_ERROR;
                }
                if (status == NO_ERROR) {
                    readInputParameters();
                    sendConfigEvent_l(AudioSystem::INPUT_CONFIG_CHANGED);
                }
            }
        }

        mNewParameters.removeAt(0);

        mParamStatus = status;
        mParamCond.signal();
        mWaitWorkCV.wait(mLock);
    }
    return reconfig;
}

String8 AudioFlinger::RecordThread::getParameters(const String8& keys)
{
    return mInput->getParameters(keys);
}

void AudioFlinger::RecordThread::audioConfigChanged_l(int event, int param) {
    AudioSystem::OutputDescriptor desc;
    void *param2 = 0;

    switch (event) {
    case AudioSystem::INPUT_OPENED:
    case AudioSystem::INPUT_CONFIG_CHANGED:
        desc.channels = mChannels;
        desc.samplingRate = mSampleRate;
        desc.format = mFormat;
        desc.frameCount = mFrameCount;
        desc.latency = 0;
        param2 = &desc;
        break;

    case AudioSystem::INPUT_CLOSED:
    default:
        break;
    }
    mAudioFlinger->audioConfigChanged_l(event, mId, param2);
}

void AudioFlinger::RecordThread::readInputParameters()
{
    if (mRsmpInBuffer) delete mRsmpInBuffer;
    if (mRsmpOutBuffer) delete mRsmpOutBuffer;
    if (mResampler) delete mResampler;
    mResampler = 0;

    mSampleRate = mInput->sampleRate();
    mChannels = mInput->channels();
    mChannelCount = (uint16_t)AudioSystem::popCount(mChannels);
    mFormat = mInput->format();
    mFrameSize = (uint16_t)mInput->frameSize();
    mInputBytes = mInput->bufferSize();
    mFrameCount = mInputBytes / mFrameSize;
    mRsmpInBuffer = new int16_t[mFrameCount * mChannelCount];

    if (mSampleRate != mReqSampleRate && mChannelCount < 3 && mReqChannelCount < 3)
    {
        int channelCount;
         // optmization: if mono to mono, use the resampler in stereo to stereo mode to avoid
         // stereo to mono post process as the resampler always outputs stereo.
        if (mChannelCount == 1 && mReqChannelCount == 2) {
            channelCount = 1;
        } else {
            channelCount = 2;
        }
        mResampler = AudioResampler::create(16, channelCount, mReqSampleRate);
        mResampler->setSampleRate(mSampleRate);
        mResampler->setVolume(AudioMixer::UNITY_GAIN, AudioMixer::UNITY_GAIN);
        mRsmpOutBuffer = new int32_t[mFrameCount * 2];

        // optmization: if mono to mono, alter input frame count as if we were inputing stereo samples
        if (mChannelCount == 1 && mReqChannelCount == 1) {
            mFrameCount >>= 1;
        }

    }
    mRsmpInIndex = mFrameCount;
}

unsigned int AudioFlinger::RecordThread::getInputFramesLost()
{
    return mInput->getInputFramesLost();
}

// ----------------------------------------------------------------------------

int AudioFlinger::openOutput(uint32_t *pDevices,
                                uint32_t *pSamplingRate,
                                uint32_t *pFormat,
                                uint32_t *pChannels,
                                uint32_t *pLatencyMs,
                                uint32_t flags)
{
    status_t status;
    PlaybackThread *thread = NULL;
    mHardwareStatus = AUDIO_HW_OUTPUT_OPEN;
    uint32_t samplingRate = pSamplingRate ? *pSamplingRate : 0;
    uint32_t format = pFormat ? *pFormat : 0;
    uint32_t channels = pChannels ? *pChannels : 0;
    uint32_t latency = pLatencyMs ? *pLatencyMs : 0;

    LOGV("openOutput(), Device %x, SamplingRate %d, Format %d, Channels %x, flags %x",
            pDevices ? *pDevices : 0,
            samplingRate,
            format,
            channels,
            flags);

    if (pDevices == NULL || *pDevices == 0) {
        return 0;
    }
    Mutex::Autolock _l(mLock);

    AudioStreamOut *output = mAudioHardware->openOutputStream(*pDevices,
                                                             (int *)&format,
                                                             &channels,
                                                             &samplingRate,
                                                             &status);
    LOGV("openOutput() openOutputStream returned output %p, SamplingRate %d, Format %d, Channels %x, status %d",
            output,
            samplingRate,
            format,
            channels,
            status);

    mHardwareStatus = AUDIO_HW_IDLE;
    if (output != 0) {
        int id = nextUniqueId();
        if ((flags & AudioSystem::OUTPUT_FLAG_DIRECT) ||
            (format != AudioSystem::PCM_16_BIT) ||
            (channels != AudioSystem::CHANNEL_OUT_STEREO)) {
            thread = new DirectOutputThread(this, output, id, *pDevices);
            LOGV("openOutput() created direct output: ID %d thread %p", id, thread);
        } else {
            thread = new MixerThread(this, output, id, *pDevices);
            LOGV("openOutput() created mixer output: ID %d thread %p", id, thread);

#ifdef LVMX
            unsigned bitsPerSample =
                (format == AudioSystem::PCM_16_BIT) ? 16 :
                    ((format == AudioSystem::PCM_8_BIT) ? 8 : 0);
            unsigned channelCount = (channels == AudioSystem::CHANNEL_OUT_STEREO) ? 2 : 1;
            int audioOutputType = LifeVibes::threadIdToAudioOutputType(thread->id());

            LifeVibes::init_aot(audioOutputType, samplingRate, bitsPerSample, channelCount);
            LifeVibes::setDevice(audioOutputType, *pDevices);
#endif

        }
        mPlaybackThreads.add(id, thread);

        if (pSamplingRate) *pSamplingRate = samplingRate;
        if (pFormat) *pFormat = format;
        if (pChannels) *pChannels = channels;
        if (pLatencyMs) *pLatencyMs = thread->latency();

        // notify client processes of the new output creation
        thread->audioConfigChanged_l(AudioSystem::OUTPUT_OPENED);
        return id;
    }

    return 0;
}

int AudioFlinger::openDuplicateOutput(int output1, int output2)
{
    Mutex::Autolock _l(mLock);
    MixerThread *thread1 = checkMixerThread_l(output1);
    MixerThread *thread2 = checkMixerThread_l(output2);

    if (thread1 == NULL || thread2 == NULL) {
        LOGW("openDuplicateOutput() wrong output mixer type for output %d or %d", output1, output2);
        return 0;
    }

    int id = nextUniqueId();
    DuplicatingThread *thread = new DuplicatingThread(this, thread1, id);
    thread->addOutputTrack(thread2);
    mPlaybackThreads.add(id, thread);
    // notify client processes of the new output creation
    thread->audioConfigChanged_l(AudioSystem::OUTPUT_OPENED);
    return id;
}

status_t AudioFlinger::closeOutput(int output)
{
    // keep strong reference on the playback thread so that
    // it is not destroyed while exit() is executed
    sp <PlaybackThread> thread;
    {
        Mutex::Autolock _l(mLock);
        thread = checkPlaybackThread_l(output);
        if (thread == NULL) {
            return BAD_VALUE;
        }

        LOGV("closeOutput() %d", output);

        if (thread->type() == PlaybackThread::MIXER) {
            for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
                if (mPlaybackThreads.valueAt(i)->type() == PlaybackThread::DUPLICATING) {
                    DuplicatingThread *dupThread = (DuplicatingThread *)mPlaybackThreads.valueAt(i).get();
                    dupThread->removeOutputTrack((MixerThread *)thread.get());
                }
            }
        }
        void *param2 = 0;
        audioConfigChanged_l(AudioSystem::OUTPUT_CLOSED, output, param2);
        mPlaybackThreads.removeItem(output);
    }
    thread->exit();

    if (thread->type() != PlaybackThread::DUPLICATING) {
        mAudioHardware->closeOutputStream(thread->getOutput());
    }
    return NO_ERROR;
}

status_t AudioFlinger::suspendOutput(int output)
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);

    if (thread == NULL) {
        return BAD_VALUE;
    }

    LOGV("suspendOutput() %d", output);
    thread->suspend();

    return NO_ERROR;
}

status_t AudioFlinger::restoreOutput(int output)
{
    Mutex::Autolock _l(mLock);
    PlaybackThread *thread = checkPlaybackThread_l(output);

    if (thread == NULL) {
        return BAD_VALUE;
    }

    LOGV("restoreOutput() %d", output);

    thread->restore();

    return NO_ERROR;
}

int AudioFlinger::openInput(uint32_t *pDevices,
                                uint32_t *pSamplingRate,
                                uint32_t *pFormat,
                                uint32_t *pChannels,
                                uint32_t acoustics)
{
    status_t status;
    RecordThread *thread = NULL;
    uint32_t samplingRate = pSamplingRate ? *pSamplingRate : 0;
    uint32_t format = pFormat ? *pFormat : 0;
    uint32_t channels = pChannels ? *pChannels : 0;
    uint32_t reqSamplingRate = samplingRate;
    uint32_t reqFormat = format;
    uint32_t reqChannels = channels;

    if (pDevices == NULL || *pDevices == 0) {
        return 0;
    }
    Mutex::Autolock _l(mLock);

    AudioStreamIn *input = mAudioHardware->openInputStream(*pDevices,
                                                             (int *)&format,
                                                             &channels,
                                                             &samplingRate,
                                                             &status,
                                                             (AudioSystem::audio_in_acoustics)acoustics);
    LOGV("openInput() openInputStream returned input %p, SamplingRate %d, Format %d, Channels %x, acoustics %x, status %d",
            input,
            samplingRate,
            format,
            channels,
            acoustics,
            status);

    // If the input could not be opened with the requested parameters and we can handle the conversion internally,
    // try to open again with the proposed parameters. The AudioFlinger can resample the input and do mono to stereo
    // or stereo to mono conversions on 16 bit PCM inputs.
    if (input == 0 && status == BAD_VALUE &&
        reqFormat == format && format == AudioSystem::PCM_16_BIT &&
        (samplingRate <= 2 * reqSamplingRate) &&
        (AudioSystem::popCount(channels) < 3) && (AudioSystem::popCount(reqChannels) < 3)) {
        LOGV("openInput() reopening with proposed sampling rate and channels");
        input = mAudioHardware->openInputStream(*pDevices,
                                                 (int *)&format,
                                                 &channels,
                                                 &samplingRate,
                                                 &status,
                                                 (AudioSystem::audio_in_acoustics)acoustics);
    }

    if (input != 0) {
        int id = nextUniqueId();
         // Start record thread
        thread = new RecordThread(this, input, reqSamplingRate, reqChannels, id);
        mRecordThreads.add(id, thread);
        LOGV("openInput() created record thread: ID %d thread %p", id, thread);
        if (pSamplingRate) *pSamplingRate = reqSamplingRate;
        if (pFormat) *pFormat = format;
        if (pChannels) *pChannels = reqChannels;

        input->standby();

        // notify client processes of the new input creation
        thread->audioConfigChanged_l(AudioSystem::INPUT_OPENED);
        return id;
    }

    return 0;
}

status_t AudioFlinger::closeInput(int input)
{
    // keep strong reference on the record thread so that
    // it is not destroyed while exit() is executed
    sp <RecordThread> thread;
    {
        Mutex::Autolock _l(mLock);
        thread = checkRecordThread_l(input);
        if (thread == NULL) {
            return BAD_VALUE;
        }

        LOGV("closeInput() %d", input);
        void *param2 = 0;
        audioConfigChanged_l(AudioSystem::INPUT_CLOSED, input, param2);
        mRecordThreads.removeItem(input);
    }
    thread->exit();

    mAudioHardware->closeInputStream(thread->getInput());

    return NO_ERROR;
}

status_t AudioFlinger::setStreamOutput(uint32_t stream, int output)
{
    Mutex::Autolock _l(mLock);
    MixerThread *dstThread = checkMixerThread_l(output);
    if (dstThread == NULL) {
        LOGW("setStreamOutput() bad output id %d", output);
        return BAD_VALUE;
    }

    LOGV("setStreamOutput() stream %d to output %d", stream, output);
    audioConfigChanged_l(AudioSystem::STREAM_CONFIG_CHANGED, output, &stream);

    for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
        PlaybackThread *thread = mPlaybackThreads.valueAt(i).get();
        if (thread != dstThread &&
            thread->type() != PlaybackThread::DIRECT) {
            MixerThread *srcThread = (MixerThread *)thread;
            srcThread->invalidateTracks(stream);
        }
    }

    return NO_ERROR;
}


int AudioFlinger::newAudioSessionId()
{
    return nextUniqueId();
}

// checkPlaybackThread_l() must be called with AudioFlinger::mLock held
AudioFlinger::PlaybackThread *AudioFlinger::checkPlaybackThread_l(int output) const
{
    PlaybackThread *thread = NULL;
    if (mPlaybackThreads.indexOfKey(output) >= 0) {
        thread = (PlaybackThread *)mPlaybackThreads.valueFor(output).get();
    }
    return thread;
}

// checkMixerThread_l() must be called with AudioFlinger::mLock held
AudioFlinger::MixerThread *AudioFlinger::checkMixerThread_l(int output) const
{
    PlaybackThread *thread = checkPlaybackThread_l(output);
    if (thread != NULL) {
        if (thread->type() == PlaybackThread::DIRECT) {
            thread = NULL;
        }
    }
    return (MixerThread *)thread;
}

// checkRecordThread_l() must be called with AudioFlinger::mLock held
AudioFlinger::RecordThread *AudioFlinger::checkRecordThread_l(int input) const
{
    RecordThread *thread = NULL;
    if (mRecordThreads.indexOfKey(input) >= 0) {
        thread = (RecordThread *)mRecordThreads.valueFor(input).get();
    }
    return thread;
}

int AudioFlinger::nextUniqueId()
{
    return android_atomic_inc(&mNextUniqueId);
}

// ----------------------------------------------------------------------------
//  Effect management
// ----------------------------------------------------------------------------


status_t AudioFlinger::loadEffectLibrary(const char *libPath, int *handle)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }
    // only allow libraries loaded from /system/lib/soundfx for now
    if (strncmp(gEffectLibPath, libPath, strlen(gEffectLibPath)) != 0) {
        return PERMISSION_DENIED;
    }

    Mutex::Autolock _l(mLock);
    return EffectLoadLibrary(libPath, handle);
}

status_t AudioFlinger::unloadEffectLibrary(int handle)
{
    // check calling permissions
    if (!settingsAllowed()) {
        return PERMISSION_DENIED;
    }

    Mutex::Autolock _l(mLock);
    return EffectUnloadLibrary(handle);
}

status_t AudioFlinger::queryNumberEffects(uint32_t *numEffects)
{
    Mutex::Autolock _l(mLock);
    return EffectQueryNumberEffects(numEffects);
}

status_t AudioFlinger::queryEffect(uint32_t index, effect_descriptor_t *descriptor)
{
    Mutex::Autolock _l(mLock);
    return EffectQueryEffect(index, descriptor);
}

status_t AudioFlinger::getEffectDescriptor(effect_uuid_t *pUuid, effect_descriptor_t *descriptor)
{
    Mutex::Autolock _l(mLock);
    return EffectGetDescriptor(pUuid, descriptor);
}


// this UUID must match the one defined in media/libeffects/EffectVisualizer.cpp
static const effect_uuid_t VISUALIZATION_UUID_ =
    {0xd069d9e0, 0x8329, 0x11df, 0x9168, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};

sp<IEffect> AudioFlinger::createEffect(pid_t pid,
        effect_descriptor_t *pDesc,
        const sp<IEffectClient>& effectClient,
        int32_t priority,
        int output,
        int sessionId,
        status_t *status,
        int *id,
        int *enabled)
{
    status_t lStatus = NO_ERROR;
    sp<EffectHandle> handle;
    effect_interface_t itfe;
    effect_descriptor_t desc;
    sp<Client> client;
    wp<Client> wclient;

    LOGV("createEffect pid %d, client %p, priority %d, sessionId %d, output %d",
            pid, effectClient.get(), priority, sessionId, output);

    if (pDesc == NULL) {
        lStatus = BAD_VALUE;
        goto Exit;
    }

    // check audio settings permission for global effects
    if (sessionId == AudioSystem::SESSION_OUTPUT_MIX && !settingsAllowed()) {
        lStatus = PERMISSION_DENIED;
        goto Exit;
    }

    // Session AudioSystem::SESSION_OUTPUT_STAGE is reserved for output stage effects
    // that can only be created by audio policy manager (running in same process)
    if (sessionId == AudioSystem::SESSION_OUTPUT_STAGE && getpid() != pid) {
        lStatus = PERMISSION_DENIED;
        goto Exit;
    }

    // check recording permission for visualizer
    if ((memcmp(&pDesc->type, SL_IID_VISUALIZATION, sizeof(effect_uuid_t)) == 0 ||
         memcmp(&pDesc->uuid, &VISUALIZATION_UUID_, sizeof(effect_uuid_t)) == 0) &&
        !recordingAllowed()) {
        lStatus = PERMISSION_DENIED;
        goto Exit;
    }

    if (output == 0) {
        if (sessionId == AudioSystem::SESSION_OUTPUT_STAGE) {
            // output must be specified by AudioPolicyManager when using session
            // AudioSystem::SESSION_OUTPUT_STAGE
            lStatus = BAD_VALUE;
            goto Exit;
        } else if (sessionId == AudioSystem::SESSION_OUTPUT_MIX) {
            // if the output returned by getOutputForEffect() is removed before we lock the
            // mutex below, the call to checkPlaybackThread_l(output) below will detect it
            // and we will exit safely
            output = AudioSystem::getOutputForEffect(&desc);
        }
    }

    {
        Mutex::Autolock _l(mLock);


        if (!EffectIsNullUuid(&pDesc->uuid)) {
            // if uuid is specified, request effect descriptor
            lStatus = EffectGetDescriptor(&pDesc->uuid, &desc);
            if (lStatus < 0) {
                LOGW("createEffect() error %d from EffectGetDescriptor", lStatus);
                goto Exit;
            }
        } else {
            // if uuid is not specified, look for an available implementation
            // of the required type in effect factory
            if (EffectIsNullUuid(&pDesc->type)) {
                LOGW("createEffect() no effect type");
                lStatus = BAD_VALUE;
                goto Exit;
            }
            uint32_t numEffects = 0;
            effect_descriptor_t d;
            bool found = false;

            lStatus = EffectQueryNumberEffects(&numEffects);
            if (lStatus < 0) {
                LOGW("createEffect() error %d from EffectQueryNumberEffects", lStatus);
                goto Exit;
            }
            for (uint32_t i = 0; i < numEffects; i++) {
                lStatus = EffectQueryEffect(i, &desc);
                if (lStatus < 0) {
                    LOGW("createEffect() error %d from EffectQueryEffect", lStatus);
                    continue;
                }
                if (memcmp(&desc.type, &pDesc->type, sizeof(effect_uuid_t)) == 0) {
                    // If matching type found save effect descriptor. If the session is
                    // 0 and the effect is not auxiliary, continue enumeration in case
                    // an auxiliary version of this effect type is available
                    found = true;
                    memcpy(&d, &desc, sizeof(effect_descriptor_t));
                    if (sessionId != AudioSystem::SESSION_OUTPUT_MIX ||
                            (desc.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
                        break;
                    }
                }
            }
            if (!found) {
                lStatus = BAD_VALUE;
                LOGW("createEffect() effect not found");
                goto Exit;
            }
            // For same effect type, chose auxiliary version over insert version if
            // connect to output mix (Compliance to OpenSL ES)
            if (sessionId == AudioSystem::SESSION_OUTPUT_MIX &&
                    (d.flags & EFFECT_FLAG_TYPE_MASK) != EFFECT_FLAG_TYPE_AUXILIARY) {
                memcpy(&desc, &d, sizeof(effect_descriptor_t));
            }
        }

        // Do not allow auxiliary effects on a session different from 0 (output mix)
        if (sessionId != AudioSystem::SESSION_OUTPUT_MIX &&
             (desc.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
            lStatus = INVALID_OPERATION;
            goto Exit;
        }

        // return effect descriptor
        memcpy(pDesc, &desc, sizeof(effect_descriptor_t));

        // If output is not specified try to find a matching audio session ID in one of the
        // output threads.
        // If output is 0 here, sessionId is neither SESSION_OUTPUT_STAGE nor SESSION_OUTPUT_MIX
        // because of code checking output when entering the function.
        if (output == 0) {
             // look for the thread where the specified audio session is present
            for (size_t i = 0; i < mPlaybackThreads.size(); i++) {
                if (mPlaybackThreads.valueAt(i)->hasAudioSession(sessionId) != 0) {
                    output = mPlaybackThreads.keyAt(i);
                    break;
                }
            }
            // If no output thread contains the requested session ID, default to
            // first output. The effect chain will be moved to the correct output
            // thread when a track with the same session ID is created
            if (output == 0 && mPlaybackThreads.size()) {
                output = mPlaybackThreads.keyAt(0);
            }
        }
        LOGV("createEffect() got output %d for effect %s", output, desc.name);
        PlaybackThread *thread = checkPlaybackThread_l(output);
        if (thread == NULL) {
            LOGE("createEffect() unknown output thread");
            lStatus = BAD_VALUE;
            goto Exit;
        }

        // TODO: allow attachment of effect to inputs

        wclient = mClients.valueFor(pid);

        if (wclient != NULL) {
            client = wclient.promote();
        } else {
            client = new Client(this, pid);
            mClients.add(pid, client);
        }

        // create effect on selected output trhead
        handle = thread->createEffect_l(client, effectClient, priority, sessionId,
                &desc, enabled, &lStatus);
        if (handle != 0 && id != NULL) {
            *id = handle->id();
        }
    }

Exit:
    if(status) {
        *status = lStatus;
    }
    return handle;
}

status_t AudioFlinger::moveEffects(int session, int srcOutput, int dstOutput)
{
    LOGV("moveEffects() session %d, srcOutput %d, dstOutput %d",
            session, srcOutput, dstOutput);
    Mutex::Autolock _l(mLock);
    if (srcOutput == dstOutput) {
        LOGW("moveEffects() same dst and src outputs %d", dstOutput);
        return NO_ERROR;
    }
    PlaybackThread *srcThread = checkPlaybackThread_l(srcOutput);
    if (srcThread == NULL) {
        LOGW("moveEffects() bad srcOutput %d", srcOutput);
        return BAD_VALUE;
    }
    PlaybackThread *dstThread = checkPlaybackThread_l(dstOutput);
    if (dstThread == NULL) {
        LOGW("moveEffects() bad dstOutput %d", dstOutput);
        return BAD_VALUE;
    }

    Mutex::Autolock _dl(dstThread->mLock);
    Mutex::Autolock _sl(srcThread->mLock);
    moveEffectChain_l(session, srcThread, dstThread, false);

    return NO_ERROR;
}

// moveEffectChain_l mustbe called with both srcThread and dstThread mLocks held
status_t AudioFlinger::moveEffectChain_l(int session,
                                   AudioFlinger::PlaybackThread *srcThread,
                                   AudioFlinger::PlaybackThread *dstThread,
                                   bool reRegister)
{
    LOGV("moveEffectChain_l() session %d from thread %p to thread %p",
            session, srcThread, dstThread);

    sp<EffectChain> chain = srcThread->getEffectChain_l(session);
    if (chain == 0) {
        LOGW("moveEffectChain_l() effect chain for session %d not on source thread %p",
                session, srcThread);
        return INVALID_OPERATION;
    }

    // remove chain first. This is useful only if reconfiguring effect chain on same output thread,
    // so that a new chain is created with correct parameters when first effect is added. This is
    // otherwise unecessary as removeEffect_l() will remove the chain when last effect is
    // removed.
    srcThread->removeEffectChain_l(chain);

    // transfer all effects one by one so that new effect chain is created on new thread with
    // correct buffer sizes and audio parameters and effect engines reconfigured accordingly
    int dstOutput = dstThread->id();
    sp<EffectChain> dstChain;
    uint32_t strategy;
    sp<EffectModule> effect = chain->getEffectFromId_l(0);
    while (effect != 0) {
        srcThread->removeEffect_l(effect);
        dstThread->addEffect_l(effect);
        // if the move request is not received from audio policy manager, the effect must be
        // re-registered with the new strategy and output
        if (dstChain == 0) {
            dstChain = effect->chain().promote();
            if (dstChain == 0) {
                LOGW("moveEffectChain_l() cannot get chain from effect %p", effect.get());
                srcThread->addEffect_l(effect);
                return NO_INIT;
            }
            strategy = dstChain->strategy();
        }
        if (reRegister) {
            AudioSystem::unregisterEffect(effect->id());
            AudioSystem::registerEffect(&effect->desc(),
                                        dstOutput,
                                        strategy,
                                        session,
                                        effect->id());
        }
        effect = chain->getEffectFromId_l(0);
    }

    return NO_ERROR;
}

// PlaybackThread::createEffect_l() must be called with AudioFlinger::mLock held
sp<AudioFlinger::EffectHandle> AudioFlinger::PlaybackThread::createEffect_l(
        const sp<AudioFlinger::Client>& client,
        const sp<IEffectClient>& effectClient,
        int32_t priority,
        int sessionId,
        effect_descriptor_t *desc,
        int *enabled,
        status_t *status
        )
{
    sp<EffectModule> effect;
    sp<EffectHandle> handle;
    status_t lStatus;
    sp<Track> track;
    sp<EffectChain> chain;
    bool chainCreated = false;
    bool effectCreated = false;
    bool effectRegistered = false;

    if (mOutput == 0) {
        LOGW("createEffect_l() Audio driver not initialized.");
        lStatus = NO_INIT;
        goto Exit;
    }

    // Do not allow auxiliary effect on session other than 0
    if ((desc->flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY &&
        sessionId != AudioSystem::SESSION_OUTPUT_MIX) {
        LOGW("createEffect_l() Cannot add auxiliary effect %s to session %d",
                desc->name, sessionId);
        lStatus = BAD_VALUE;
        goto Exit;
    }

    // Do not allow effects with session ID 0 on direct output or duplicating threads
    // TODO: add rule for hw accelerated effects on direct outputs with non PCM format
    if (sessionId == AudioSystem::SESSION_OUTPUT_MIX && mType != MIXER) {
        LOGW("createEffect_l() Cannot add auxiliary effect %s to session %d",
                desc->name, sessionId);
        lStatus = BAD_VALUE;
        goto Exit;
    }

    LOGV("createEffect_l() thread %p effect %s on session %d", this, desc->name, sessionId);

    { // scope for mLock
        Mutex::Autolock _l(mLock);

        // check for existing effect chain with the requested audio session
        chain = getEffectChain_l(sessionId);
        if (chain == 0) {
            // create a new chain for this session
            LOGV("createEffect_l() new effect chain for session %d", sessionId);
            chain = new EffectChain(this, sessionId);
            addEffectChain_l(chain);
            chain->setStrategy(getStrategyForSession_l(sessionId));
            chainCreated = true;
        } else {
            effect = chain->getEffectFromDesc_l(desc);
        }

        LOGV("createEffect_l() got effect %p on chain %p", effect == 0 ? 0 : effect.get(), chain.get());

        if (effect == 0) {
            int id = mAudioFlinger->nextUniqueId();
            // Check CPU and memory usage
            lStatus = AudioSystem::registerEffect(desc, mId, chain->strategy(), sessionId, id);
            if (lStatus != NO_ERROR) {
                goto Exit;
            }
            effectRegistered = true;
            // create a new effect module if none present in the chain
            effect = new EffectModule(this, chain, desc, id, sessionId);
            lStatus = effect->status();
            if (lStatus != NO_ERROR) {
                goto Exit;
            }
            lStatus = chain->addEffect_l(effect);
            if (lStatus != NO_ERROR) {
                goto Exit;
            }
            effectCreated = true;

            effect->setDevice(mDevice);
            effect->setMode(mAudioFlinger->getMode());
        }
        // create effect handle and connect it to effect module
        handle = new EffectHandle(effect, client, effectClient, priority);
        lStatus = effect->addHandle(handle);
        if (enabled) {
            *enabled = (int)effect->isEnabled();
        }
    }

Exit:
    if (lStatus != NO_ERROR && lStatus != ALREADY_EXISTS) {
        Mutex::Autolock _l(mLock);
        if (effectCreated) {
            chain->removeEffect_l(effect);
        }
        if (effectRegistered) {
            AudioSystem::unregisterEffect(effect->id());
        }
        if (chainCreated) {
            removeEffectChain_l(chain);
        }
        handle.clear();
    }

    if(status) {
        *status = lStatus;
    }
    return handle;
}

// PlaybackThread::addEffect_l() must be called with AudioFlinger::mLock and
// PlaybackThread::mLock held
status_t AudioFlinger::PlaybackThread::addEffect_l(const sp<EffectModule>& effect)
{
    // check for existing effect chain with the requested audio session
    int sessionId = effect->sessionId();
    sp<EffectChain> chain = getEffectChain_l(sessionId);
    bool chainCreated = false;

    if (chain == 0) {
        // create a new chain for this session
        LOGV("addEffect_l() new effect chain for session %d", sessionId);
        chain = new EffectChain(this, sessionId);
        addEffectChain_l(chain);
        chain->setStrategy(getStrategyForSession_l(sessionId));
        chainCreated = true;
    }
    LOGV("addEffect_l() %p chain %p effect %p", this, chain.get(), effect.get());

    if (chain->getEffectFromId_l(effect->id()) != 0) {
        LOGW("addEffect_l() %p effect %s already present in chain %p",
                this, effect->desc().name, chain.get());
        return BAD_VALUE;
    }

    status_t status = chain->addEffect_l(effect);
    if (status != NO_ERROR) {
        if (chainCreated) {
            removeEffectChain_l(chain);
        }
        return status;
    }

    effect->setDevice(mDevice);
    effect->setMode(mAudioFlinger->getMode());
    return NO_ERROR;
}

void AudioFlinger::PlaybackThread::removeEffect_l(const sp<EffectModule>& effect) {

    LOGV("removeEffect_l() %p effect %p", this, effect.get());
    effect_descriptor_t desc = effect->desc();
    if ((desc.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
        detachAuxEffect_l(effect->id());
    }

    sp<EffectChain> chain = effect->chain().promote();
    if (chain != 0) {
        // remove effect chain if removing last effect
        if (chain->removeEffect_l(effect) == 0) {
            removeEffectChain_l(chain);
        }
    } else {
        LOGW("removeEffect_l() %p cannot promote chain for effect %p", this, effect.get());
    }
}

void AudioFlinger::PlaybackThread::disconnectEffect(const sp<EffectModule>& effect,
                                                    const wp<EffectHandle>& handle) {
    Mutex::Autolock _l(mLock);
    LOGV("disconnectEffect() %p effect %p", this, effect.get());
    // delete the effect module if removing last handle on it
    if (effect->removeHandle(handle) == 0) {
        removeEffect_l(effect);
        AudioSystem::unregisterEffect(effect->id());
    }
}

status_t AudioFlinger::PlaybackThread::addEffectChain_l(const sp<EffectChain>& chain)
{
    int session = chain->sessionId();
    int16_t *buffer = mMixBuffer;
    bool ownsBuffer = false;

    LOGV("addEffectChain_l() %p on thread %p for session %d", chain.get(), this, session);
    if (session > 0) {
        // Only one effect chain can be present in direct output thread and it uses
        // the mix buffer as input
        if (mType != DIRECT) {
            size_t numSamples = mFrameCount * mChannelCount;
            buffer = new int16_t[numSamples];
            memset(buffer, 0, numSamples * sizeof(int16_t));
            LOGV("addEffectChain_l() creating new input buffer %p session %d", buffer, session);
            ownsBuffer = true;
        }

        // Attach all tracks with same session ID to this chain.
        for (size_t i = 0; i < mTracks.size(); ++i) {
            sp<Track> track = mTracks[i];
            if (session == track->sessionId()) {
                LOGV("addEffectChain_l() track->setMainBuffer track %p buffer %p", track.get(), buffer);
                track->setMainBuffer(buffer);
            }
        }

        // indicate all active tracks in the chain
        for (size_t i = 0 ; i < mActiveTracks.size() ; ++i) {
            sp<Track> track = mActiveTracks[i].promote();
            if (track == 0) continue;
            if (session == track->sessionId()) {
                LOGV("addEffectChain_l() activating track %p on session %d", track.get(), session);
                chain->startTrack();
            }
        }
    }

    chain->setInBuffer(buffer, ownsBuffer);
    chain->setOutBuffer(mMixBuffer);
    // Effect chain for session AudioSystem::SESSION_OUTPUT_STAGE is inserted at end of effect
    // chains list in order to be processed last as it contains output stage effects
    // Effect chain for session AudioSystem::SESSION_OUTPUT_MIX is inserted before
    // session AudioSystem::SESSION_OUTPUT_STAGE to be processed
    // after track specific effects and before output stage
    // It is therefore mandatory that AudioSystem::SESSION_OUTPUT_MIX == 0 and
    // that AudioSystem::SESSION_OUTPUT_STAGE < AudioSystem::SESSION_OUTPUT_MIX
    // Effect chain for other sessions are inserted at beginning of effect
    // chains list to be processed before output mix effects. Relative order between other
    // sessions is not important
    size_t size = mEffectChains.size();
    size_t i = 0;
    for (i = 0; i < size; i++) {
        if (mEffectChains[i]->sessionId() < session) break;
    }
    mEffectChains.insertAt(chain, i);

    return NO_ERROR;
}

size_t AudioFlinger::PlaybackThread::removeEffectChain_l(const sp<EffectChain>& chain)
{
    int session = chain->sessionId();

    LOGV("removeEffectChain_l() %p from thread %p for session %d", chain.get(), this, session);

    for (size_t i = 0; i < mEffectChains.size(); i++) {
        if (chain == mEffectChains[i]) {
            mEffectChains.removeAt(i);
            // detach all tracks with same session ID from this chain
            for (size_t i = 0; i < mTracks.size(); ++i) {
                sp<Track> track = mTracks[i];
                if (session == track->sessionId()) {
                    track->setMainBuffer(mMixBuffer);
                }
            }
            break;
        }
    }
    return mEffectChains.size();
}

void AudioFlinger::PlaybackThread::lockEffectChains_l(
        Vector<sp <AudioFlinger::EffectChain> >& effectChains)
{
    effectChains = mEffectChains;
    for (size_t i = 0; i < mEffectChains.size(); i++) {
        mEffectChains[i]->lock();
    }
}

void AudioFlinger::PlaybackThread::unlockEffectChains(
        Vector<sp <AudioFlinger::EffectChain> >& effectChains)
{
    for (size_t i = 0; i < effectChains.size(); i++) {
        effectChains[i]->unlock();
    }
}


sp<AudioFlinger::EffectModule> AudioFlinger::PlaybackThread::getEffect_l(int sessionId, int effectId)
{
    sp<EffectModule> effect;

    sp<EffectChain> chain = getEffectChain_l(sessionId);
    if (chain != 0) {
        effect = chain->getEffectFromId_l(effectId);
    }
    return effect;
}

status_t AudioFlinger::PlaybackThread::attachAuxEffect(
        const sp<AudioFlinger::PlaybackThread::Track> track, int EffectId)
{
    Mutex::Autolock _l(mLock);
    return attachAuxEffect_l(track, EffectId);
}

status_t AudioFlinger::PlaybackThread::attachAuxEffect_l(
        const sp<AudioFlinger::PlaybackThread::Track> track, int EffectId)
{
    status_t status = NO_ERROR;

    if (EffectId == 0) {
        track->setAuxBuffer(0, NULL);
    } else {
        // Auxiliary effects are always in audio session AudioSystem::SESSION_OUTPUT_MIX
        sp<EffectModule> effect = getEffect_l(AudioSystem::SESSION_OUTPUT_MIX, EffectId);
        if (effect != 0) {
            if ((effect->desc().flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
                track->setAuxBuffer(EffectId, (int32_t *)effect->inBuffer());
            } else {
                status = INVALID_OPERATION;
            }
        } else {
            status = BAD_VALUE;
        }
    }
    return status;
}

void AudioFlinger::PlaybackThread::detachAuxEffect_l(int effectId)
{
     for (size_t i = 0; i < mTracks.size(); ++i) {
        sp<Track> track = mTracks[i];
        if (track->auxEffectId() == effectId) {
            attachAuxEffect_l(track, 0);
        }
    }
}

// ----------------------------------------------------------------------------
//  EffectModule implementation
// ----------------------------------------------------------------------------

#undef LOG_TAG
#define LOG_TAG "AudioFlinger::EffectModule"

AudioFlinger::EffectModule::EffectModule(const wp<ThreadBase>& wThread,
                                        const wp<AudioFlinger::EffectChain>& chain,
                                        effect_descriptor_t *desc,
                                        int id,
                                        int sessionId)
    : mThread(wThread), mChain(chain), mId(id), mSessionId(sessionId), mEffectInterface(NULL),
      mStatus(NO_INIT), mState(IDLE)
{
    LOGV("Constructor %p", this);
    int lStatus;
    sp<ThreadBase> thread = mThread.promote();
    if (thread == 0) {
        return;
    }
    PlaybackThread *p = (PlaybackThread *)thread.get();

    memcpy(&mDescriptor, desc, sizeof(effect_descriptor_t));

    // create effect engine from effect factory
    mStatus = EffectCreate(&desc->uuid, sessionId, p->id(), &mEffectInterface);

    if (mStatus != NO_ERROR) {
        return;
    }
    lStatus = init();
    if (lStatus < 0) {
        mStatus = lStatus;
        goto Error;
    }

    LOGV("Constructor success name %s, Interface %p", mDescriptor.name, mEffectInterface);
    return;
Error:
    EffectRelease(mEffectInterface);
    mEffectInterface = NULL;
    LOGV("Constructor Error %d", mStatus);
}

AudioFlinger::EffectModule::~EffectModule()
{
    LOGV("Destructor %p", this);
    if (mEffectInterface != NULL) {
        // release effect engine
        EffectRelease(mEffectInterface);
    }
}

status_t AudioFlinger::EffectModule::addHandle(sp<EffectHandle>& handle)
{
    status_t status;

    Mutex::Autolock _l(mLock);
    // First handle in mHandles has highest priority and controls the effect module
    int priority = handle->priority();
    size_t size = mHandles.size();
    sp<EffectHandle> h;
    size_t i;
    for (i = 0; i < size; i++) {
        h = mHandles[i].promote();
        if (h == 0) continue;
        if (h->priority() <= priority) break;
    }
    // if inserted in first place, move effect control from previous owner to this handle
    if (i == 0) {
        if (h != 0) {
            h->setControl(false, true);
        }
        handle->setControl(true, false);
        status = NO_ERROR;
    } else {
        status = ALREADY_EXISTS;
    }
    mHandles.insertAt(handle, i);
    return status;
}

size_t AudioFlinger::EffectModule::removeHandle(const wp<EffectHandle>& handle)
{
    Mutex::Autolock _l(mLock);
    size_t size = mHandles.size();
    size_t i;
    for (i = 0; i < size; i++) {
        if (mHandles[i] == handle) break;
    }
    if (i == size) {
        return size;
    }
    mHandles.removeAt(i);
    size = mHandles.size();
    // if removed from first place, move effect control from this handle to next in line
    if (i == 0 && size != 0) {
        sp<EffectHandle> h = mHandles[0].promote();
        if (h != 0) {
            h->setControl(true, true);
        }
    }

    // Release effect engine here so that it is done immediately. Otherwise it will be released
    // by the destructor when the last strong reference on the this object is released which can
    // happen after next process is called on this effect.
    if (size == 0 && mEffectInterface != NULL) {
        // release effect engine
        EffectRelease(mEffectInterface);
        mEffectInterface = NULL;
    }

    return size;
}

void AudioFlinger::EffectModule::disconnect(const wp<EffectHandle>& handle)
{
    // keep a strong reference on this EffectModule to avoid calling the
    // destructor before we exit
    sp<EffectModule> keep(this);
    {
        sp<ThreadBase> thread = mThread.promote();
        if (thread != 0) {
            PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
            playbackThread->disconnectEffect(keep, handle);
        }
    }
}

void AudioFlinger::EffectModule::updateState() {
    Mutex::Autolock _l(mLock);

    switch (mState) {
    case RESTART:
        reset_l();
        // FALL THROUGH

    case STARTING:
        // clear auxiliary effect input buffer for next accumulation
        if ((mDescriptor.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
            memset(mConfig.inputCfg.buffer.raw,
                   0,
                   mConfig.inputCfg.buffer.frameCount*sizeof(int32_t));
        }
        start_l();
        mState = ACTIVE;
        break;
    case STOPPING:
        stop_l();
        mDisableWaitCnt = mMaxDisableWaitCnt;
        mState = STOPPED;
        break;
    case STOPPED:
        // mDisableWaitCnt is forced to 1 by process() when the engine indicates the end of the
        // turn off sequence.
        if (--mDisableWaitCnt == 0) {
            reset_l();
            mState = IDLE;
        }
        break;
    default: //IDLE , ACTIVE
        break;
    }
}

void AudioFlinger::EffectModule::process()
{
    Mutex::Autolock _l(mLock);

    if (mEffectInterface == NULL ||
            mConfig.inputCfg.buffer.raw == NULL ||
            mConfig.outputCfg.buffer.raw == NULL) {
        return;
    }

    if (isProcessEnabled()) {
        // do 32 bit to 16 bit conversion for auxiliary effect input buffer
        if ((mDescriptor.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
            AudioMixer::ditherAndClamp(mConfig.inputCfg.buffer.s32,
                                        mConfig.inputCfg.buffer.s32,
                                        mConfig.inputCfg.buffer.frameCount/2);
        }

        // do the actual processing in the effect engine
        int ret = (*mEffectInterface)->process(mEffectInterface,
                                               &mConfig.inputCfg.buffer,
                                               &mConfig.outputCfg.buffer);

        // force transition to IDLE state when engine is ready
        if (mState == STOPPED && ret == -ENODATA) {
            mDisableWaitCnt = 1;
        }

        // clear auxiliary effect input buffer for next accumulation
        if ((mDescriptor.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
            memset(mConfig.inputCfg.buffer.raw, 0, mConfig.inputCfg.buffer.frameCount*sizeof(int32_t));
        }
    } else if ((mDescriptor.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_INSERT &&
                mConfig.inputCfg.buffer.raw != mConfig.outputCfg.buffer.raw){
        // If an insert effect is idle and input buffer is different from output buffer, copy input to
        // output
        sp<EffectChain> chain = mChain.promote();
        if (chain != 0 && chain->activeTracks() != 0) {
            size_t size = mConfig.inputCfg.buffer.frameCount * sizeof(int16_t);
            if (mConfig.inputCfg.channels == CHANNEL_STEREO) {
                size *= 2;
            }
            memcpy(mConfig.outputCfg.buffer.raw, mConfig.inputCfg.buffer.raw, size);
        }
    }
}

void AudioFlinger::EffectModule::reset_l()
{
    if (mEffectInterface == NULL) {
        return;
    }
    (*mEffectInterface)->command(mEffectInterface, EFFECT_CMD_RESET, 0, NULL, 0, NULL);
}

status_t AudioFlinger::EffectModule::configure()
{
    uint32_t channels;
    if (mEffectInterface == NULL) {
        return NO_INIT;
    }

    sp<ThreadBase> thread = mThread.promote();
    if (thread == 0) {
        return DEAD_OBJECT;
    }

    // TODO: handle configuration of effects replacing track process
    if (thread->channelCount() == 1) {
        channels = CHANNEL_MONO;
    } else {
        channels = CHANNEL_STEREO;
    }

    if ((mDescriptor.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
        mConfig.inputCfg.channels = CHANNEL_MONO;
    } else {
        mConfig.inputCfg.channels = channels;
    }
    mConfig.outputCfg.channels = channels;
    mConfig.inputCfg.format = SAMPLE_FORMAT_PCM_S15;
    mConfig.outputCfg.format = SAMPLE_FORMAT_PCM_S15;
    mConfig.inputCfg.samplingRate = thread->sampleRate();
    mConfig.outputCfg.samplingRate = mConfig.inputCfg.samplingRate;
    mConfig.inputCfg.bufferProvider.cookie = NULL;
    mConfig.inputCfg.bufferProvider.getBuffer = NULL;
    mConfig.inputCfg.bufferProvider.releaseBuffer = NULL;
    mConfig.outputCfg.bufferProvider.cookie = NULL;
    mConfig.outputCfg.bufferProvider.getBuffer = NULL;
    mConfig.outputCfg.bufferProvider.releaseBuffer = NULL;
    mConfig.inputCfg.accessMode = EFFECT_BUFFER_ACCESS_READ;
    // Insert effect:
    // - in session AudioSystem::SESSION_OUTPUT_MIX or AudioSystem::SESSION_OUTPUT_STAGE,
    // always overwrites output buffer: input buffer == output buffer
    // - in other sessions:
    //      last effect in the chain accumulates in output buffer: input buffer != output buffer
    //      other effect: overwrites output buffer: input buffer == output buffer
    // Auxiliary effect:
    //      accumulates in output buffer: input buffer != output buffer
    // Therefore: accumulate <=> input buffer != output buffer
    if (mConfig.inputCfg.buffer.raw != mConfig.outputCfg.buffer.raw) {
        mConfig.outputCfg.accessMode = EFFECT_BUFFER_ACCESS_ACCUMULATE;
    } else {
        mConfig.outputCfg.accessMode = EFFECT_BUFFER_ACCESS_WRITE;
    }
    mConfig.inputCfg.mask = EFFECT_CONFIG_ALL;
    mConfig.outputCfg.mask = EFFECT_CONFIG_ALL;
    mConfig.inputCfg.buffer.frameCount = thread->frameCount();
    mConfig.outputCfg.buffer.frameCount = mConfig.inputCfg.buffer.frameCount;

    LOGV("configure() %p thread %p buffer %p framecount %d",
            this, thread.get(), mConfig.inputCfg.buffer.raw, mConfig.inputCfg.buffer.frameCount);

    status_t cmdStatus;
    uint32_t size = sizeof(int);
    status_t status = (*mEffectInterface)->command(mEffectInterface,
                                                   EFFECT_CMD_CONFIGURE,
                                                   sizeof(effect_config_t),
                                                   &mConfig,
                                                   &size,
                                                   &cmdStatus);
    if (status == 0) {
        status = cmdStatus;
    }

    mMaxDisableWaitCnt = (MAX_DISABLE_TIME_MS * mConfig.outputCfg.samplingRate) /
            (1000 * mConfig.outputCfg.buffer.frameCount);

    return status;
}

status_t AudioFlinger::EffectModule::init()
{
    Mutex::Autolock _l(mLock);
    if (mEffectInterface == NULL) {
        return NO_INIT;
    }
    status_t cmdStatus;
    uint32_t size = sizeof(status_t);
    status_t status = (*mEffectInterface)->command(mEffectInterface,
                                                   EFFECT_CMD_INIT,
                                                   0,
                                                   NULL,
                                                   &size,
                                                   &cmdStatus);
    if (status == 0) {
        status = cmdStatus;
    }
    return status;
}

status_t AudioFlinger::EffectModule::start_l()
{
    if (mEffectInterface == NULL) {
        return NO_INIT;
    }
    status_t cmdStatus;
    uint32_t size = sizeof(status_t);
    status_t status = (*mEffectInterface)->command(mEffectInterface,
                                                   EFFECT_CMD_ENABLE,
                                                   0,
                                                   NULL,
                                                   &size,
                                                   &cmdStatus);
    if (status == 0) {
        status = cmdStatus;
    }
    return status;
}

status_t AudioFlinger::EffectModule::stop_l()
{
    if (mEffectInterface == NULL) {
        return NO_INIT;
    }
    status_t cmdStatus;
    uint32_t size = sizeof(status_t);
    status_t status = (*mEffectInterface)->command(mEffectInterface,
                                                   EFFECT_CMD_DISABLE,
                                                   0,
                                                   NULL,
                                                   &size,
                                                   &cmdStatus);
    if (status == 0) {
        status = cmdStatus;
    }
    return status;
}

status_t AudioFlinger::EffectModule::command(uint32_t cmdCode,
                                             uint32_t cmdSize,
                                             void *pCmdData,
                                             uint32_t *replySize,
                                             void *pReplyData)
{
    Mutex::Autolock _l(mLock);
//    LOGV("command(), cmdCode: %d, mEffectInterface: %p", cmdCode, mEffectInterface);

    if (mEffectInterface == NULL) {
        return NO_INIT;
    }
    status_t status = (*mEffectInterface)->command(mEffectInterface,
                                                   cmdCode,
                                                   cmdSize,
                                                   pCmdData,
                                                   replySize,
                                                   pReplyData);
    if (cmdCode != EFFECT_CMD_GET_PARAM && status == NO_ERROR) {
        uint32_t size = (replySize == NULL) ? 0 : *replySize;
        for (size_t i = 1; i < mHandles.size(); i++) {
            sp<EffectHandle> h = mHandles[i].promote();
            if (h != 0) {
                h->commandExecuted(cmdCode, cmdSize, pCmdData, size, pReplyData);
            }
        }
    }
    return status;
}

status_t AudioFlinger::EffectModule::setEnabled(bool enabled)
{
    Mutex::Autolock _l(mLock);
    LOGV("setEnabled %p enabled %d", this, enabled);

    if (enabled != isEnabled()) {
        switch (mState) {
        // going from disabled to enabled
        case IDLE:
            mState = STARTING;
            break;
        case STOPPED:
            mState = RESTART;
            break;
        case STOPPING:
            mState = ACTIVE;
            break;

        // going from enabled to disabled
        case RESTART:
            mState = STOPPED;
            break;
        case STARTING:
            mState = IDLE;
            break;
        case ACTIVE:
            mState = STOPPING;
            break;
        }
        for (size_t i = 1; i < mHandles.size(); i++) {
            sp<EffectHandle> h = mHandles[i].promote();
            if (h != 0) {
                h->setEnabled(enabled);
            }
        }
    }
    return NO_ERROR;
}

bool AudioFlinger::EffectModule::isEnabled()
{
    switch (mState) {
    case RESTART:
    case STARTING:
    case ACTIVE:
        return true;
    case IDLE:
    case STOPPING:
    case STOPPED:
    default:
        return false;
    }
}

bool AudioFlinger::EffectModule::isProcessEnabled()
{
    switch (mState) {
    case RESTART:
    case ACTIVE:
    case STOPPING:
    case STOPPED:
        return true;
    case IDLE:
    case STARTING:
    default:
        return false;
    }
}

status_t AudioFlinger::EffectModule::setVolume(uint32_t *left, uint32_t *right, bool controller)
{
    Mutex::Autolock _l(mLock);
    status_t status = NO_ERROR;

    // Send volume indication if EFFECT_FLAG_VOLUME_IND is set and read back altered volume
    // if controller flag is set (Note that controller == TRUE => EFFECT_FLAG_VOLUME_CTRL set)
    if (isProcessEnabled() &&
            ((mDescriptor.flags & EFFECT_FLAG_VOLUME_MASK) == EFFECT_FLAG_VOLUME_CTRL ||
            (mDescriptor.flags & EFFECT_FLAG_VOLUME_MASK) == EFFECT_FLAG_VOLUME_IND)) {
        status_t cmdStatus;
        uint32_t volume[2];
        uint32_t *pVolume = NULL;
        uint32_t size = sizeof(volume);
        volume[0] = *left;
        volume[1] = *right;
        if (controller) {
            pVolume = volume;
        }
        status = (*mEffectInterface)->command(mEffectInterface,
                                              EFFECT_CMD_SET_VOLUME,
                                              size,
                                              volume,
                                              &size,
                                              pVolume);
        if (controller && status == NO_ERROR && size == sizeof(volume)) {
            *left = volume[0];
            *right = volume[1];
        }
    }
    return status;
}

status_t AudioFlinger::EffectModule::setDevice(uint32_t device)
{
    Mutex::Autolock _l(mLock);
    status_t status = NO_ERROR;
    if ((mDescriptor.flags & EFFECT_FLAG_DEVICE_MASK) == EFFECT_FLAG_DEVICE_IND) {
        // convert device bit field from AudioSystem to EffectApi format.
        device = deviceAudioSystemToEffectApi(device);
        if (device == 0) {
            return BAD_VALUE;
        }
        status_t cmdStatus;
        uint32_t size = sizeof(status_t);
        status = (*mEffectInterface)->command(mEffectInterface,
                                              EFFECT_CMD_SET_DEVICE,
                                              sizeof(uint32_t),
                                              &device,
                                              &size,
                                              &cmdStatus);
        if (status == NO_ERROR) {
            status = cmdStatus;
        }
    }
    return status;
}

status_t AudioFlinger::EffectModule::setMode(uint32_t mode)
{
    Mutex::Autolock _l(mLock);
    status_t status = NO_ERROR;
    if ((mDescriptor.flags & EFFECT_FLAG_AUDIO_MODE_MASK) == EFFECT_FLAG_AUDIO_MODE_IND) {
        // convert audio mode from AudioSystem to EffectApi format.
        int effectMode = modeAudioSystemToEffectApi(mode);
        if (effectMode < 0) {
            return BAD_VALUE;
        }
        status_t cmdStatus;
        uint32_t size = sizeof(status_t);
        status = (*mEffectInterface)->command(mEffectInterface,
                                              EFFECT_CMD_SET_AUDIO_MODE,
                                              sizeof(int),
                                              &effectMode,
                                              &size,
                                              &cmdStatus);
        if (status == NO_ERROR) {
            status = cmdStatus;
        }
    }
    return status;
}

// update this table when AudioSystem::audio_devices or audio_device_e (in EffectApi.h) are modified
const uint32_t AudioFlinger::EffectModule::sDeviceConvTable[] = {
    DEVICE_EARPIECE, // AudioSystem::DEVICE_OUT_EARPIECE
    DEVICE_SPEAKER, // AudioSystem::DEVICE_OUT_SPEAKER
    DEVICE_WIRED_HEADSET, // case AudioSystem::DEVICE_OUT_WIRED_HEADSET
    DEVICE_WIRED_HEADPHONE, // AudioSystem::DEVICE_OUT_WIRED_HEADPHONE
    DEVICE_BLUETOOTH_SCO, // AudioSystem::DEVICE_OUT_BLUETOOTH_SCO
    DEVICE_BLUETOOTH_SCO_HEADSET, // AudioSystem::DEVICE_OUT_BLUETOOTH_SCO_HEADSET
    DEVICE_BLUETOOTH_SCO_CARKIT, //  AudioSystem::DEVICE_OUT_BLUETOOTH_SCO_CARKIT
    DEVICE_BLUETOOTH_A2DP, //  AudioSystem::DEVICE_OUT_BLUETOOTH_A2DP
    DEVICE_BLUETOOTH_A2DP_HEADPHONES, // AudioSystem::DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES
    DEVICE_BLUETOOTH_A2DP_SPEAKER, // AudioSystem::DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER
    DEVICE_AUX_DIGITAL // AudioSystem::DEVICE_OUT_AUX_DIGITAL
};

uint32_t AudioFlinger::EffectModule::deviceAudioSystemToEffectApi(uint32_t device)
{
    uint32_t deviceOut = 0;
    while (device) {
        const uint32_t i = 31 - __builtin_clz(device);
        device &= ~(1 << i);
        if (i >= sizeof(sDeviceConvTable)/sizeof(uint32_t)) {
            LOGE("device convertion error for AudioSystem device 0x%08x", device);
            return 0;
        }
        deviceOut |= (uint32_t)sDeviceConvTable[i];
    }
    return deviceOut;
}

// update this table when AudioSystem::audio_mode or audio_mode_e (in EffectApi.h) are modified
const uint32_t AudioFlinger::EffectModule::sModeConvTable[] = {
    AUDIO_MODE_NORMAL,   // AudioSystem::MODE_NORMAL
    AUDIO_MODE_RINGTONE, // AudioSystem::MODE_RINGTONE
    AUDIO_MODE_IN_CALL   // AudioSystem::MODE_IN_CALL
};

int AudioFlinger::EffectModule::modeAudioSystemToEffectApi(uint32_t mode)
{
    int modeOut = -1;
    if (mode < sizeof(sModeConvTable) / sizeof(uint32_t)) {
        modeOut = (int)sModeConvTable[mode];
    }
    return modeOut;
}

status_t AudioFlinger::EffectModule::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    snprintf(buffer, SIZE, "\tEffect ID %d:\n", mId);
    result.append(buffer);

    bool locked = tryLock(mLock);
    // failed to lock - AudioFlinger is probably deadlocked
    if (!locked) {
        result.append("\t\tCould not lock Fx mutex:\n");
    }

    result.append("\t\tSession Status State Engine:\n");
    snprintf(buffer, SIZE, "\t\t%05d   %03d    %03d   0x%08x\n",
            mSessionId, mStatus, mState, (uint32_t)mEffectInterface);
    result.append(buffer);

    result.append("\t\tDescriptor:\n");
    snprintf(buffer, SIZE, "\t\t- UUID: %08X-%04X-%04X-%04X-%02X%02X%02X%02X%02X%02X\n",
            mDescriptor.uuid.timeLow, mDescriptor.uuid.timeMid, mDescriptor.uuid.timeHiAndVersion,
            mDescriptor.uuid.clockSeq, mDescriptor.uuid.node[0], mDescriptor.uuid.node[1],mDescriptor.uuid.node[2],
            mDescriptor.uuid.node[3],mDescriptor.uuid.node[4],mDescriptor.uuid.node[5]);
    result.append(buffer);
    snprintf(buffer, SIZE, "\t\t- TYPE: %08X-%04X-%04X-%04X-%02X%02X%02X%02X%02X%02X\n",
                mDescriptor.type.timeLow, mDescriptor.type.timeMid, mDescriptor.type.timeHiAndVersion,
                mDescriptor.type.clockSeq, mDescriptor.type.node[0], mDescriptor.type.node[1],mDescriptor.type.node[2],
                mDescriptor.type.node[3],mDescriptor.type.node[4],mDescriptor.type.node[5]);
    result.append(buffer);
    snprintf(buffer, SIZE, "\t\t- apiVersion: %04X\n\t\t- flags: %08X\n",
            mDescriptor.apiVersion,
            mDescriptor.flags);
    result.append(buffer);
    snprintf(buffer, SIZE, "\t\t- name: %s\n",
            mDescriptor.name);
    result.append(buffer);
    snprintf(buffer, SIZE, "\t\t- implementor: %s\n",
            mDescriptor.implementor);
    result.append(buffer);

    result.append("\t\t- Input configuration:\n");
    result.append("\t\t\tBuffer     Frames  Smp rate Channels Format\n");
    snprintf(buffer, SIZE, "\t\t\t0x%08x %05d   %05d    %08x %d\n",
            (uint32_t)mConfig.inputCfg.buffer.raw,
            mConfig.inputCfg.buffer.frameCount,
            mConfig.inputCfg.samplingRate,
            mConfig.inputCfg.channels,
            mConfig.inputCfg.format);
    result.append(buffer);

    result.append("\t\t- Output configuration:\n");
    result.append("\t\t\tBuffer     Frames  Smp rate Channels Format\n");
    snprintf(buffer, SIZE, "\t\t\t0x%08x %05d   %05d    %08x %d\n",
            (uint32_t)mConfig.outputCfg.buffer.raw,
            mConfig.outputCfg.buffer.frameCount,
            mConfig.outputCfg.samplingRate,
            mConfig.outputCfg.channels,
            mConfig.outputCfg.format);
    result.append(buffer);

    snprintf(buffer, SIZE, "\t\t%d Clients:\n", mHandles.size());
    result.append(buffer);
    result.append("\t\t\tPid   Priority Ctrl Locked client server\n");
    for (size_t i = 0; i < mHandles.size(); ++i) {
        sp<EffectHandle> handle = mHandles[i].promote();
        if (handle != 0) {
            handle->dump(buffer, SIZE);
            result.append(buffer);
        }
    }

    result.append("\n");

    write(fd, result.string(), result.length());

    if (locked) {
        mLock.unlock();
    }

    return NO_ERROR;
}

// ----------------------------------------------------------------------------
//  EffectHandle implementation
// ----------------------------------------------------------------------------

#undef LOG_TAG
#define LOG_TAG "AudioFlinger::EffectHandle"

AudioFlinger::EffectHandle::EffectHandle(const sp<EffectModule>& effect,
                                        const sp<AudioFlinger::Client>& client,
                                        const sp<IEffectClient>& effectClient,
                                        int32_t priority)
    : BnEffect(),
    mEffect(effect), mEffectClient(effectClient), mClient(client), mPriority(priority), mHasControl(false)
{
    LOGV("constructor %p", this);

    int bufOffset = ((sizeof(effect_param_cblk_t) - 1) / sizeof(int) + 1) * sizeof(int);
    mCblkMemory = client->heap()->allocate(EFFECT_PARAM_BUFFER_SIZE + bufOffset);
    if (mCblkMemory != 0) {
        mCblk = static_cast<effect_param_cblk_t *>(mCblkMemory->pointer());

        if (mCblk) {
            new(mCblk) effect_param_cblk_t();
            mBuffer = (uint8_t *)mCblk + bufOffset;
         }
    } else {
        LOGE("not enough memory for Effect size=%u", EFFECT_PARAM_BUFFER_SIZE + sizeof(effect_param_cblk_t));
        return;
    }
}

AudioFlinger::EffectHandle::~EffectHandle()
{
    LOGV("Destructor %p", this);
    disconnect();
}

status_t AudioFlinger::EffectHandle::enable()
{
    if (!mHasControl) return INVALID_OPERATION;
    if (mEffect == 0) return DEAD_OBJECT;

    return mEffect->setEnabled(true);
}

status_t AudioFlinger::EffectHandle::disable()
{
    if (!mHasControl) return INVALID_OPERATION;
    if (mEffect == NULL) return DEAD_OBJECT;

    return mEffect->setEnabled(false);
}

void AudioFlinger::EffectHandle::disconnect()
{
    if (mEffect == 0) {
        return;
    }
    mEffect->disconnect(this);
    // release sp on module => module destructor can be called now
    mEffect.clear();
    if (mCblk) {
        mCblk->~effect_param_cblk_t();   // destroy our shared-structure.
    }
    mCblkMemory.clear();            // and free the shared memory
    if (mClient != 0) {
        Mutex::Autolock _l(mClient->audioFlinger()->mLock);
        mClient.clear();
    }
}

status_t AudioFlinger::EffectHandle::command(uint32_t cmdCode,
                                             uint32_t cmdSize,
                                             void *pCmdData,
                                             uint32_t *replySize,
                                             void *pReplyData)
{
//    LOGV("command(), cmdCode: %d, mHasControl: %d, mEffect: %p",
//              cmdCode, mHasControl, (mEffect == 0) ? 0 : mEffect.get());

    // only get parameter command is permitted for applications not controlling the effect
    if (!mHasControl && cmdCode != EFFECT_CMD_GET_PARAM) {
        return INVALID_OPERATION;
    }
    if (mEffect == 0) return DEAD_OBJECT;

    // handle commands that are not forwarded transparently to effect engine
    if (cmdCode == EFFECT_CMD_SET_PARAM_COMMIT) {
        // No need to trylock() here as this function is executed in the binder thread serving a particular client process:
        // no risk to block the whole media server process or mixer threads is we are stuck here
        Mutex::Autolock _l(mCblk->lock);
        if (mCblk->clientIndex > EFFECT_PARAM_BUFFER_SIZE ||
            mCblk->serverIndex > EFFECT_PARAM_BUFFER_SIZE) {
            mCblk->serverIndex = 0;
            mCblk->clientIndex = 0;
            return BAD_VALUE;
        }
        status_t status = NO_ERROR;
        while (mCblk->serverIndex < mCblk->clientIndex) {
            int reply;
            uint32_t rsize = sizeof(int);
            int *p = (int *)(mBuffer + mCblk->serverIndex);
            int size = *p++;
            if (((uint8_t *)p + size) > mBuffer + mCblk->clientIndex) {
                LOGW("command(): invalid parameter block size");
                break;
            }
            effect_param_t *param = (effect_param_t *)p;
            if (param->psize == 0 || param->vsize == 0) {
                LOGW("command(): null parameter or value size");
                mCblk->serverIndex += size;
                continue;
            }
            uint32_t psize = sizeof(effect_param_t) +
                             ((param->psize - 1) / sizeof(int) + 1) * sizeof(int) +
                             param->vsize;
            status_t ret = mEffect->command(EFFECT_CMD_SET_PARAM,
                                            psize,
                                            p,
                                            &rsize,
                                            &reply);
            // stop at first error encountered
            if (ret != NO_ERROR) {
                status = ret;
                *(int *)pReplyData = reply;
                break;
            } else if (reply != NO_ERROR) {
                *(int *)pReplyData = reply;
                break;
            }
            mCblk->serverIndex += size;
        }
        mCblk->serverIndex = 0;
        mCblk->clientIndex = 0;
        return status;
    } else if (cmdCode == EFFECT_CMD_ENABLE) {
        *(int *)pReplyData = NO_ERROR;
        return enable();
    } else if (cmdCode == EFFECT_CMD_DISABLE) {
        *(int *)pReplyData = NO_ERROR;
        return disable();
    }

    return mEffect->command(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
}

sp<IMemory> AudioFlinger::EffectHandle::getCblk() const {
    return mCblkMemory;
}

void AudioFlinger::EffectHandle::setControl(bool hasControl, bool signal)
{
    LOGV("setControl %p control %d", this, hasControl);

    mHasControl = hasControl;
    if (signal && mEffectClient != 0) {
        mEffectClient->controlStatusChanged(hasControl);
    }
}

void AudioFlinger::EffectHandle::commandExecuted(uint32_t cmdCode,
                                                 uint32_t cmdSize,
                                                 void *pCmdData,
                                                 uint32_t replySize,
                                                 void *pReplyData)
{
    if (mEffectClient != 0) {
        mEffectClient->commandExecuted(cmdCode, cmdSize, pCmdData, replySize, pReplyData);
    }
}



void AudioFlinger::EffectHandle::setEnabled(bool enabled)
{
    if (mEffectClient != 0) {
        mEffectClient->enableStatusChanged(enabled);
    }
}

status_t AudioFlinger::EffectHandle::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    return BnEffect::onTransact(code, data, reply, flags);
}


void AudioFlinger::EffectHandle::dump(char* buffer, size_t size)
{
    bool locked = tryLock(mCblk->lock);

    snprintf(buffer, size, "\t\t\t%05d %05d    %01u    %01u      %05u  %05u\n",
            (mClient == NULL) ? getpid() : mClient->pid(),
            mPriority,
            mHasControl,
            !locked,
            mCblk->clientIndex,
            mCblk->serverIndex
            );

    if (locked) {
        mCblk->lock.unlock();
    }
}

#undef LOG_TAG
#define LOG_TAG "AudioFlinger::EffectChain"

AudioFlinger::EffectChain::EffectChain(const wp<ThreadBase>& wThread,
                                        int sessionId)
    : mThread(wThread), mSessionId(sessionId), mActiveTrackCnt(0), mOwnInBuffer(false),
            mVolumeCtrlIdx(-1), mLeftVolume(UINT_MAX), mRightVolume(UINT_MAX),
            mNewLeftVolume(UINT_MAX), mNewRightVolume(UINT_MAX)
{
    mStrategy = AudioSystem::getStrategyForStream(AudioSystem::MUSIC);
}

AudioFlinger::EffectChain::~EffectChain()
{
    if (mOwnInBuffer) {
        delete mInBuffer;
    }

}

// getEffectFromDesc_l() must be called with PlaybackThread::mLock held
sp<AudioFlinger::EffectModule> AudioFlinger::EffectChain::getEffectFromDesc_l(effect_descriptor_t *descriptor)
{
    sp<EffectModule> effect;
    size_t size = mEffects.size();

    for (size_t i = 0; i < size; i++) {
        if (memcmp(&mEffects[i]->desc().uuid, &descriptor->uuid, sizeof(effect_uuid_t)) == 0) {
            effect = mEffects[i];
            break;
        }
    }
    return effect;
}

// getEffectFromId_l() must be called with PlaybackThread::mLock held
sp<AudioFlinger::EffectModule> AudioFlinger::EffectChain::getEffectFromId_l(int id)
{
    sp<EffectModule> effect;
    size_t size = mEffects.size();

    for (size_t i = 0; i < size; i++) {
        // by convention, return first effect if id provided is 0 (0 is never a valid id)
        if (id == 0 || mEffects[i]->id() == id) {
            effect = mEffects[i];
            break;
        }
    }
    return effect;
}

// Must be called with EffectChain::mLock locked
void AudioFlinger::EffectChain::process_l()
{
    sp<ThreadBase> thread = mThread.promote();
    if (thread == 0) {
        LOGW("process_l(): cannot promote mixer thread");
        return;
    }
    PlaybackThread *playbackThread = (PlaybackThread *)thread.get();
    bool isGlobalSession = (mSessionId == AudioSystem::SESSION_OUTPUT_MIX) ||
            (mSessionId == AudioSystem::SESSION_OUTPUT_STAGE);
    bool tracksOnSession = false;
    if (!isGlobalSession) {
        tracksOnSession =
                playbackThread->hasAudioSession(mSessionId) & PlaybackThread::TRACK_SESSION;
    }

    size_t size = mEffects.size();
    // do not process effect if no track is present in same audio session
    if (isGlobalSession || tracksOnSession) {
        for (size_t i = 0; i < size; i++) {
            mEffects[i]->process();
        }
    }
    for (size_t i = 0; i < size; i++) {
        mEffects[i]->updateState();
    }
    // if no track is active, input buffer must be cleared here as the mixer process
    // will not do it
    if (tracksOnSession &&
        activeTracks() == 0) {
        size_t numSamples = playbackThread->frameCount() * playbackThread->channelCount();
        memset(mInBuffer, 0, numSamples * sizeof(int16_t));
    }
}

// addEffect_l() must be called with PlaybackThread::mLock held
status_t AudioFlinger::EffectChain::addEffect_l(const sp<EffectModule>& effect)
{
    effect_descriptor_t desc = effect->desc();
    uint32_t insertPref = desc.flags & EFFECT_FLAG_INSERT_MASK;

    Mutex::Autolock _l(mLock);
    effect->setChain(this);
    sp<ThreadBase> thread = mThread.promote();
    if (thread == 0) {
        return NO_INIT;
    }
    effect->setThread(thread);

    if ((desc.flags & EFFECT_FLAG_TYPE_MASK) == EFFECT_FLAG_TYPE_AUXILIARY) {
        // Auxiliary effects are inserted at the beginning of mEffects vector as
        // they are processed first and accumulated in chain input buffer
        mEffects.insertAt(effect, 0);

        // the input buffer for auxiliary effect contains mono samples in
        // 32 bit format. This is to avoid saturation in AudoMixer
        // accumulation stage. Saturation is done in EffectModule::process() before
        // calling the process in effect engine
        size_t numSamples = thread->frameCount();
        int32_t *buffer = new int32_t[numSamples];
        memset(buffer, 0, numSamples * sizeof(int32_t));
        effect->setInBuffer((int16_t *)buffer);
        // auxiliary effects output samples to chain input buffer for further processing
        // by insert effects
        effect->setOutBuffer(mInBuffer);
    } else {
        // Insert effects are inserted at the end of mEffects vector as they are processed
        //  after track and auxiliary effects.
        // Insert effect order as a function of indicated preference:
        //  if EFFECT_FLAG_INSERT_EXCLUSIVE, insert in first position or reject if
        //  another effect is present
        //  else if EFFECT_FLAG_INSERT_FIRST, insert in first position or after the
        //  last effect claiming first position
        //  else if EFFECT_FLAG_INSERT_LAST, insert in last position or before the
        //  first effect claiming last position
        //  else if EFFECT_FLAG_INSERT_ANY insert after first or before last
        // Reject insertion if an effect with EFFECT_FLAG_INSERT_EXCLUSIVE is
        // already present

        int size = (int)mEffects.size();
        int idx_insert = size;
        int idx_insert_first = -1;
        int idx_insert_last = -1;

        for (int i = 0; i < size; i++) {
            effect_descriptor_t d = mEffects[i]->desc();
            uint32_t iMode = d.flags & EFFECT_FLAG_TYPE_MASK;
            uint32_t iPref = d.flags & EFFECT_FLAG_INSERT_MASK;
            if (iMode == EFFECT_FLAG_TYPE_INSERT) {
                // check invalid effect chaining combinations
                if (insertPref == EFFECT_FLAG_INSERT_EXCLUSIVE ||
                    iPref == EFFECT_FLAG_INSERT_EXCLUSIVE) {
                    LOGW("addEffect_l() could not insert effect %s: exclusive conflict with %s", desc.name, d.name);
                    return INVALID_OPERATION;
                }
                // remember position of first insert effect and by default
                // select this as insert position for new effect
                if (idx_insert == size) {
                    idx_insert = i;
                }
                // remember position of last insert effect claiming
                // first position
                if (iPref == EFFECT_FLAG_INSERT_FIRST) {
                    idx_insert_first = i;
                }
                // remember position of first insert effect claiming
                // last position
                if (iPref == EFFECT_FLAG_INSERT_LAST &&
                    idx_insert_last == -1) {
                    idx_insert_last = i;
                }
            }
        }

        // modify idx_insert from first position if needed
        if (insertPref == EFFECT_FLAG_INSERT_LAST) {
            if (idx_insert_last != -1) {
                idx_insert = idx_insert_last;
            } else {
                idx_insert = size;
            }
        } else {
            if (idx_insert_first != -1) {
                idx_insert = idx_insert_first + 1;
            }
        }

        // always read samples from chain input buffer
        effect->setInBuffer(mInBuffer);

        // if last effect in the chain, output samples to chain
        // output buffer, otherwise to chain input buffer
        if (idx_insert == size) {
            if (idx_insert != 0) {
                mEffects[idx_insert-1]->setOutBuffer(mInBuffer);
                mEffects[idx_insert-1]->configure();
            }
            effect->setOutBuffer(mOutBuffer);
        } else {
            effect->setOutBuffer(mInBuffer);
        }
        mEffects.insertAt(effect, idx_insert);

        LOGV("addEffect_l() effect %p, added in chain %p at rank %d", effect.get(), this, idx_insert);
    }
    effect->configure();
    return NO_ERROR;
}

// removeEffect_l() must be called with PlaybackThread::mLock held
size_t AudioFlinger::EffectChain::removeEffect_l(const sp<EffectModule>& effect)
{
    Mutex::Autolock _l(mLock);
    int size = (int)mEffects.size();
    int i;
    uint32_t type = effect->desc().flags & EFFECT_FLAG_TYPE_MASK;

    for (i = 0; i < size; i++) {
        if (effect == mEffects[i]) {
            if (type == EFFECT_FLAG_TYPE_AUXILIARY) {
                delete[] effect->inBuffer();
            } else {
                if (i == size - 1 && i != 0) {
                    mEffects[i - 1]->setOutBuffer(mOutBuffer);
                    mEffects[i - 1]->configure();
                }
            }
            mEffects.removeAt(i);
            LOGV("removeEffect_l() effect %p, removed from chain %p at rank %d", effect.get(), this, i);
            break;
        }
    }

    return mEffects.size();
}

// setDevice_l() must be called with PlaybackThread::mLock held
void AudioFlinger::EffectChain::setDevice_l(uint32_t device)
{
    size_t size = mEffects.size();
    for (size_t i = 0; i < size; i++) {
        mEffects[i]->setDevice(device);
    }
}

// setMode_l() must be called with PlaybackThread::mLock held
void AudioFlinger::EffectChain::setMode_l(uint32_t mode)
{
    size_t size = mEffects.size();
    for (size_t i = 0; i < size; i++) {
        mEffects[i]->setMode(mode);
    }
}

// setVolume_l() must be called with PlaybackThread::mLock held
bool AudioFlinger::EffectChain::setVolume_l(uint32_t *left, uint32_t *right)
{
    uint32_t newLeft = *left;
    uint32_t newRight = *right;
    bool hasControl = false;
    int ctrlIdx = -1;
    size_t size = mEffects.size();

    // first update volume controller
    for (size_t i = size; i > 0; i--) {
        if (mEffects[i - 1]->isProcessEnabled() &&
            (mEffects[i - 1]->desc().flags & EFFECT_FLAG_VOLUME_MASK) == EFFECT_FLAG_VOLUME_CTRL) {
            ctrlIdx = i - 1;
            hasControl = true;
            break;
        }
    }

    if (ctrlIdx == mVolumeCtrlIdx && *left == mLeftVolume && *right == mRightVolume) {
        if (hasControl) {
            *left = mNewLeftVolume;
            *right = mNewRightVolume;
        }
        return hasControl;
    }

    mVolumeCtrlIdx = ctrlIdx;
    mLeftVolume = newLeft;
    mRightVolume = newRight;

    // second get volume update from volume controller
    if (ctrlIdx >= 0) {
        mEffects[ctrlIdx]->setVolume(&newLeft, &newRight, true);
        mNewLeftVolume = newLeft;
        mNewRightVolume = newRight;
    }
    // then indicate volume to all other effects in chain.
    // Pass altered volume to effects before volume controller
    // and requested volume to effects after controller
    uint32_t lVol = newLeft;
    uint32_t rVol = newRight;

    for (size_t i = 0; i < size; i++) {
        if ((int)i == ctrlIdx) continue;
        // this also works for ctrlIdx == -1 when there is no volume controller
        if ((int)i > ctrlIdx) {
            lVol = *left;
            rVol = *right;
        }
        mEffects[i]->setVolume(&lVol, &rVol, false);
    }
    *left = newLeft;
    *right = newRight;

    return hasControl;
}

status_t AudioFlinger::EffectChain::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    snprintf(buffer, SIZE, "Effects for session %d:\n", mSessionId);
    result.append(buffer);

    bool locked = tryLock(mLock);
    // failed to lock - AudioFlinger is probably deadlocked
    if (!locked) {
        result.append("\tCould not lock mutex:\n");
    }

    result.append("\tNum fx In buffer   Out buffer   Active tracks:\n");
    snprintf(buffer, SIZE, "\t%02d     0x%08x  0x%08x   %d\n",
            mEffects.size(),
            (uint32_t)mInBuffer,
            (uint32_t)mOutBuffer,
            mActiveTrackCnt);
    result.append(buffer);
    write(fd, result.string(), result.size());

    for (size_t i = 0; i < mEffects.size(); ++i) {
        sp<EffectModule> effect = mEffects[i];
        if (effect != 0) {
            effect->dump(fd, args);
        }
    }

    if (locked) {
        mLock.unlock();
    }

    return NO_ERROR;
}

#undef LOG_TAG
#define LOG_TAG "AudioFlinger"

// ----------------------------------------------------------------------------

status_t AudioFlinger::onTransact(
        uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    return BnAudioFlinger::onTransact(code, data, reply, flags);
}

}; // namespace android
