/*
**
** Copyright 2006, The Android Open Source Project
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

//#define LOG_NDEBUG 0

#define LOG_TAG "AudioSystem-JNI"
#include <utils/Log.h>

#include <jni.h>
#include <JNIHelp.h>
#include "core_jni_helpers.h"

#include <media/AudioSystem.h>
#include <media/AudioPolicy.h>

#include <system/audio.h>
#include <system/audio_policy.h>
#include "android_media_AudioFormat.h"
#include "android_media_AudioErrors.h"

// ----------------------------------------------------------------------------

using namespace android;

static const char* const kClassPathName = "android/media/AudioSystem";

static jclass gArrayListClass;
static struct {
    jmethodID    add;
    jmethodID    toArray;
} gArrayListMethods;

static jclass gAudioHandleClass;
static jmethodID gAudioHandleCstor;
static struct {
    jfieldID    mId;
} gAudioHandleFields;

static jclass gAudioPortClass;
static jmethodID gAudioPortCstor;
static struct {
    jfieldID    mHandle;
    jfieldID    mRole;
    jfieldID    mGains;
    jfieldID    mActiveConfig;
    // other fields unused by JNI
} gAudioPortFields;

static jclass gAudioPortConfigClass;
static jmethodID gAudioPortConfigCstor;
static struct {
    jfieldID    mPort;
    jfieldID    mSamplingRate;
    jfieldID    mChannelMask;
    jfieldID    mFormat;
    jfieldID    mGain;
    jfieldID    mConfigMask;
} gAudioPortConfigFields;

static jclass gAudioDevicePortClass;
static jmethodID gAudioDevicePortCstor;

static jclass gAudioDevicePortConfigClass;
static jmethodID gAudioDevicePortConfigCstor;

static jclass gAudioMixPortClass;
static jmethodID gAudioMixPortCstor;

static jclass gAudioMixPortConfigClass;
static jmethodID gAudioMixPortConfigCstor;

static jclass gAudioGainClass;
static jmethodID gAudioGainCstor;

static jclass gAudioGainConfigClass;
static jmethodID gAudioGainConfigCstor;
static struct {
    jfieldID mIndex;
    jfieldID mMode;
    jfieldID mChannelMask;
    jfieldID mValues;
    jfieldID mRampDurationMs;
    // other fields unused by JNI
} gAudioGainConfigFields;

static jclass gAudioPatchClass;
static jmethodID gAudioPatchCstor;
static struct {
    jfieldID    mHandle;
    // other fields unused by JNI
} gAudioPatchFields;

static jclass gAudioMixClass;
static struct {
    jfieldID    mRule;
    jfieldID    mFormat;
    jfieldID    mRouteFlags;
    jfieldID    mRegistrationId;
    jfieldID    mMixType;
    jfieldID    mCallbackFlags;
} gAudioMixFields;

static jclass gAudioFormatClass;
static struct {
    jfieldID    mEncoding;
    jfieldID    mSampleRate;
    jfieldID    mChannelMask;
    // other fields unused by JNI
} gAudioFormatFields;

static jclass gAudioMixingRuleClass;
static struct {
    jfieldID    mCriteria;
    // other fields unused by JNI
} gAudioMixingRuleFields;

static jclass gAttributeMatchCriterionClass;
static struct {
    jfieldID    mAttr;
    jfieldID    mRule;
} gAttributeMatchCriterionFields;

static jclass gAudioAttributesClass;
static struct {
    jfieldID    mUsage;
    jfieldID    mSource;
} gAudioAttributesFields;


static const char* const kEventHandlerClassPathName =
        "android/media/AudioPortEventHandler";
static struct {
    jfieldID    mJniCallback;
} gEventHandlerFields;
static struct {
    jmethodID    postEventFromNative;
} gAudioPortEventHandlerMethods;

static struct {
    jmethodID postDynPolicyEventFromNative;
} gDynPolicyEventHandlerMethods;

static struct {
    jmethodID postEffectSessionEventFromNative;
} gEffectSessionEventHandlerMethods;


static Mutex gLock;

enum AudioError {
    kAudioStatusOk = 0,
    kAudioStatusError = 1,
    kAudioStatusMediaServerDied = 100
};

enum  {
    AUDIOPORT_EVENT_PORT_LIST_UPDATED = 1,
    AUDIOPORT_EVENT_PATCH_LIST_UPDATED = 2,
    AUDIOPORT_EVENT_SERVICE_DIED = 3,
};

#define MAX_PORT_GENERATION_SYNC_ATTEMPTS 5

// ----------------------------------------------------------------------------
// ref-counted object for audio port callbacks
class JNIAudioPortCallback: public AudioSystem::AudioPortCallback
{
public:
    JNIAudioPortCallback(JNIEnv* env, jobject thiz, jobject weak_thiz);
    ~JNIAudioPortCallback();

    virtual void onAudioPortListUpdate();
    virtual void onAudioPatchListUpdate();
    virtual void onServiceDied();

private:
    void sendEvent(int event);

    jclass      mClass;     // Reference to AudioPortEventHandler class
    jobject     mObject;    // Weak ref to AudioPortEventHandler Java object to call on
};

JNIAudioPortCallback::JNIAudioPortCallback(JNIEnv* env, jobject thiz, jobject weak_thiz)
{

    // Hold onto the AudioPortEventHandler class for use in calling the static method
    // that posts events to the application thread.
    jclass clazz = env->GetObjectClass(thiz);
    if (clazz == NULL) {
        ALOGE("Can't find class %s", kEventHandlerClassPathName);
        return;
    }
    mClass = (jclass)env->NewGlobalRef(clazz);

    // We use a weak reference so the AudioPortEventHandler object can be garbage collected.
    // The reference is only used as a proxy for callbacks.
    mObject  = env->NewGlobalRef(weak_thiz);
}

JNIAudioPortCallback::~JNIAudioPortCallback()
{
    // remove global references
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }
    env->DeleteGlobalRef(mObject);
    env->DeleteGlobalRef(mClass);
}

void JNIAudioPortCallback::sendEvent(int event)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }
    env->CallStaticVoidMethod(mClass, gAudioPortEventHandlerMethods.postEventFromNative, mObject,
                              event, 0, 0, NULL);
    if (env->ExceptionCheck()) {
        ALOGW("An exception occurred while notifying an event.");
        env->ExceptionClear();
    }
}

void JNIAudioPortCallback::onAudioPortListUpdate()
{
    sendEvent(AUDIOPORT_EVENT_PORT_LIST_UPDATED);
}

void JNIAudioPortCallback::onAudioPatchListUpdate()
{
    sendEvent(AUDIOPORT_EVENT_PATCH_LIST_UPDATED);
}

void JNIAudioPortCallback::onServiceDied()
{
    sendEvent(AUDIOPORT_EVENT_SERVICE_DIED);
}

static sp<JNIAudioPortCallback> setJniCallback(JNIEnv* env,
                                       jobject thiz,
                                       const sp<JNIAudioPortCallback>& callback)
{
    Mutex::Autolock l(gLock);
    sp<JNIAudioPortCallback> old =
            (JNIAudioPortCallback*)env->GetLongField(thiz, gEventHandlerFields.mJniCallback);
    if (callback.get()) {
        callback->incStrong((void*)setJniCallback);
    }
    if (old != 0) {
        old->decStrong((void*)setJniCallback);
    }
    env->SetLongField(thiz, gEventHandlerFields.mJniCallback, (jlong)callback.get());
    return old;
}

static int check_AudioSystem_Command(status_t status)
{
    switch (status) {
    case DEAD_OBJECT:
        return kAudioStatusMediaServerDied;
    case NO_ERROR:
        return kAudioStatusOk;
    default:
        break;
    }
    return kAudioStatusError;
}

static jint
android_media_AudioSystem_muteMicrophone(JNIEnv *env, jobject thiz, jboolean on)
{
    return (jint) check_AudioSystem_Command(AudioSystem::muteMicrophone(on));
}

static jboolean
android_media_AudioSystem_isMicrophoneMuted(JNIEnv *env, jobject thiz)
{
    bool state = false;
    AudioSystem::isMicrophoneMuted(&state);
    return state;
}

static jboolean
android_media_AudioSystem_isStreamActive(JNIEnv *env, jobject thiz, jint stream, jint inPastMs)
{
    bool state = false;
    AudioSystem::isStreamActive((audio_stream_type_t) stream, &state, inPastMs);
    return state;
}

static jboolean
android_media_AudioSystem_isStreamActiveRemotely(JNIEnv *env, jobject thiz, jint stream,
        jint inPastMs)
{
    bool state = false;
    AudioSystem::isStreamActiveRemotely((audio_stream_type_t) stream, &state, inPastMs);
    return state;
}

static jboolean
android_media_AudioSystem_isSourceActive(JNIEnv *env, jobject thiz, jint source)
{
    bool state = false;
    AudioSystem::isSourceActive((audio_source_t) source, &state);
    return state;
}

static jint
android_media_AudioSystem_newAudioSessionId(JNIEnv *env, jobject thiz)
{
    return AudioSystem::newAudioUniqueId();
}

static jint
android_media_AudioSystem_setParameters(JNIEnv *env, jobject thiz, jstring keyValuePairs)
{
    const jchar* c_keyValuePairs = env->GetStringCritical(keyValuePairs, 0);
    String8 c_keyValuePairs8;
    if (keyValuePairs) {
        c_keyValuePairs8 = String8(
            reinterpret_cast<const char16_t*>(c_keyValuePairs),
            env->GetStringLength(keyValuePairs));
        env->ReleaseStringCritical(keyValuePairs, c_keyValuePairs);
    }
    int status = check_AudioSystem_Command(AudioSystem::setParameters(c_keyValuePairs8));
    return (jint) status;
}

static jstring
android_media_AudioSystem_getParameters(JNIEnv *env, jobject thiz, jstring keys)
{
    const jchar* c_keys = env->GetStringCritical(keys, 0);
    String8 c_keys8;
    if (keys) {
        c_keys8 = String8(reinterpret_cast<const char16_t*>(c_keys),
                          env->GetStringLength(keys));
        env->ReleaseStringCritical(keys, c_keys);
    }
    return env->NewStringUTF(AudioSystem::getParameters(c_keys8).string());
}

static void
android_media_AudioSystem_error_callback(status_t err)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }

    jclass clazz = env->FindClass(kClassPathName);

    env->CallStaticVoidMethod(clazz, env->GetStaticMethodID(clazz,
                              "errorCallbackFromNative","(I)V"),
                              check_AudioSystem_Command(err));

    env->DeleteLocalRef(clazz);
}

static void
android_media_AudioSystem_dyn_policy_callback(int event, String8 regId, int val)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }

    jclass clazz = env->FindClass(kClassPathName);
    const char* zechars = regId.string();
    jstring zestring = env->NewStringUTF(zechars);

    env->CallStaticVoidMethod(clazz, gDynPolicyEventHandlerMethods.postDynPolicyEventFromNative,
            event, zestring, val);

    env->ReleaseStringUTFChars(zestring, zechars);
    env->DeleteLocalRef(clazz);

}

static void
android_media_AudioSystem_effect_session_callback(int event, audio_stream_type_t stream,
        audio_unique_id_t sessionId, bool added)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }

    jclass clazz = env->FindClass(kClassPathName);

    env->CallStaticVoidMethod(clazz, gEffectSessionEventHandlerMethods.postEffectSessionEventFromNative,
            event, stream, sessionId, added);

    env->DeleteLocalRef(clazz);

}

static jint
android_media_AudioSystem_setDeviceConnectionState(JNIEnv *env, jobject thiz, jint device, jint state, jstring device_address, jstring device_name)
{
    const char *c_address = env->GetStringUTFChars(device_address, NULL);
    const char *c_name = env->GetStringUTFChars(device_name, NULL);
    int status = check_AudioSystem_Command(AudioSystem::setDeviceConnectionState(static_cast <audio_devices_t>(device),
                                          static_cast <audio_policy_dev_state_t>(state),
                                          c_address, c_name));
    env->ReleaseStringUTFChars(device_address, c_address);
    env->ReleaseStringUTFChars(device_name, c_name);
    return (jint) status;
}

static jint
android_media_AudioSystem_getDeviceConnectionState(JNIEnv *env, jobject thiz, jint device, jstring device_address)
{
    const char *c_address = env->GetStringUTFChars(device_address, NULL);
    int state = static_cast <int>(AudioSystem::getDeviceConnectionState(static_cast <audio_devices_t>(device),
                                          c_address));
    env->ReleaseStringUTFChars(device_address, c_address);
    return (jint) state;
}

static jint
android_media_AudioSystem_setPhoneState(JNIEnv *env, jobject thiz, jint state)
{
    return (jint) check_AudioSystem_Command(AudioSystem::setPhoneState((audio_mode_t) state));
}

static jint
android_media_AudioSystem_setForceUse(JNIEnv *env, jobject thiz, jint usage, jint config)
{
    return (jint) check_AudioSystem_Command(AudioSystem::setForceUse(static_cast <audio_policy_force_use_t>(usage),
                                                           static_cast <audio_policy_forced_cfg_t>(config)));
}

static jint
android_media_AudioSystem_getForceUse(JNIEnv *env, jobject thiz, jint usage)
{
    return static_cast <jint>(AudioSystem::getForceUse(static_cast <audio_policy_force_use_t>(usage)));
}

static jint
android_media_AudioSystem_initStreamVolume(JNIEnv *env, jobject thiz, jint stream, jint indexMin, jint indexMax)
{
    return (jint) check_AudioSystem_Command(AudioSystem::initStreamVolume(static_cast <audio_stream_type_t>(stream),
                                                                   indexMin,
                                                                   indexMax));
}

static jint
android_media_AudioSystem_setStreamVolumeIndex(JNIEnv *env,
                                               jobject thiz,
                                               jint stream,
                                               jint index,
                                               jint device)
{
    return (jint) check_AudioSystem_Command(
            AudioSystem::setStreamVolumeIndex(static_cast <audio_stream_type_t>(stream),
                                              index,
                                              (audio_devices_t)device));
}

static jint
android_media_AudioSystem_getStreamVolumeIndex(JNIEnv *env,
                                               jobject thiz,
                                               jint stream,
                                               jint device)
{
    int index;
    if (AudioSystem::getStreamVolumeIndex(static_cast <audio_stream_type_t>(stream),
                                          &index,
                                          (audio_devices_t)device)
            != NO_ERROR) {
        index = -1;
    }
    return (jint) index;
}

static jint
android_media_AudioSystem_setMasterVolume(JNIEnv *env, jobject thiz, jfloat value)
{
    return (jint) check_AudioSystem_Command(AudioSystem::setMasterVolume(value));
}

static jfloat
android_media_AudioSystem_getMasterVolume(JNIEnv *env, jobject thiz)
{
    float value;
    if (AudioSystem::getMasterVolume(&value) != NO_ERROR) {
        value = -1.0;
    }
    return value;
}

static jint
android_media_AudioSystem_setMasterMute(JNIEnv *env, jobject thiz, jboolean mute)
{
    return (jint) check_AudioSystem_Command(AudioSystem::setMasterMute(mute));
}

static jboolean
android_media_AudioSystem_getMasterMute(JNIEnv *env, jobject thiz)
{
    bool mute;
    if (AudioSystem::getMasterMute(&mute) != NO_ERROR) {
        mute = false;
    }
    return mute;
}

static jint
android_media_AudioSystem_getDevicesForStream(JNIEnv *env, jobject thiz, jint stream)
{
    return (jint) AudioSystem::getDevicesForStream(static_cast <audio_stream_type_t>(stream));
}

static jint
android_media_AudioSystem_getPrimaryOutputSamplingRate(JNIEnv *env, jobject clazz)
{
    return (jint) AudioSystem::getPrimaryOutputSamplingRate();
}

static jint
android_media_AudioSystem_getPrimaryOutputFrameCount(JNIEnv *env, jobject clazz)
{
    return (jint) AudioSystem::getPrimaryOutputFrameCount();
}

static jint
android_media_AudioSystem_getOutputLatency(JNIEnv *env, jobject clazz, jint stream)
{
    uint32_t afLatency;
    if (AudioSystem::getOutputLatency(&afLatency, static_cast <audio_stream_type_t>(stream))
            != NO_ERROR) {
        afLatency = -1;
    }
    return (jint) afLatency;
}

static jint
android_media_AudioSystem_setLowRamDevice(JNIEnv *env, jobject clazz, jboolean isLowRamDevice)
{
    return (jint) AudioSystem::setLowRamDevice((bool) isLowRamDevice);
}

static jint
android_media_AudioSystem_checkAudioFlinger(JNIEnv *env, jobject clazz)
{
    return (jint) check_AudioSystem_Command(AudioSystem::checkAudioFlinger());
}


static bool useInChannelMask(audio_port_type_t type, audio_port_role_t role)
{
    return ((type == AUDIO_PORT_TYPE_DEVICE) && (role == AUDIO_PORT_ROLE_SOURCE)) ||
                ((type == AUDIO_PORT_TYPE_MIX) && (role == AUDIO_PORT_ROLE_SINK));
}

static void convertAudioGainConfigToNative(JNIEnv *env,
                                               struct audio_gain_config *nAudioGainConfig,
                                               const jobject jAudioGainConfig,
                                               bool useInMask)
{
    nAudioGainConfig->index = env->GetIntField(jAudioGainConfig, gAudioGainConfigFields.mIndex);
    nAudioGainConfig->mode = env->GetIntField(jAudioGainConfig, gAudioGainConfigFields.mMode);
    ALOGV("convertAudioGainConfigToNative got gain index %d", nAudioGainConfig->index);
    jint jMask = env->GetIntField(jAudioGainConfig, gAudioGainConfigFields.mChannelMask);
    audio_channel_mask_t nMask;
    if (useInMask) {
        nMask = inChannelMaskToNative(jMask);
        ALOGV("convertAudioGainConfigToNative IN mask java %x native %x", jMask, nMask);
    } else {
        nMask = outChannelMaskToNative(jMask);
        ALOGV("convertAudioGainConfigToNative OUT mask java %x native %x", jMask, nMask);
    }
    nAudioGainConfig->channel_mask = nMask;
    nAudioGainConfig->ramp_duration_ms = env->GetIntField(jAudioGainConfig,
                                                       gAudioGainConfigFields.mRampDurationMs);
    jintArray jValues = (jintArray)env->GetObjectField(jAudioGainConfig,
                                                       gAudioGainConfigFields.mValues);
    int *nValues = env->GetIntArrayElements(jValues, NULL);
    size_t size = env->GetArrayLength(jValues);
    memcpy(nAudioGainConfig->values, nValues, size * sizeof(int));
    env->DeleteLocalRef(jValues);
}


static jint convertAudioPortConfigToNative(JNIEnv *env,
                                               struct audio_port_config *nAudioPortConfig,
                                               const jobject jAudioPortConfig,
                                               bool useConfigMask)
{
    jobject jAudioPort = env->GetObjectField(jAudioPortConfig, gAudioPortConfigFields.mPort);
    jobject jHandle = env->GetObjectField(jAudioPort, gAudioPortFields.mHandle);
    nAudioPortConfig->id = env->GetIntField(jHandle, gAudioHandleFields.mId);
    nAudioPortConfig->role = (audio_port_role_t)env->GetIntField(jAudioPort,
                                                                 gAudioPortFields.mRole);
    if (env->IsInstanceOf(jAudioPort, gAudioDevicePortClass)) {
        nAudioPortConfig->type = AUDIO_PORT_TYPE_DEVICE;
    } else if (env->IsInstanceOf(jAudioPort, gAudioMixPortClass)) {
        nAudioPortConfig->type = AUDIO_PORT_TYPE_MIX;
    } else {
        env->DeleteLocalRef(jAudioPort);
        env->DeleteLocalRef(jHandle);
        return (jint)AUDIO_JAVA_ERROR;
    }
    ALOGV("convertAudioPortConfigToNative handle %d role %d type %d",
          nAudioPortConfig->id, nAudioPortConfig->role, nAudioPortConfig->type);

    unsigned int configMask = 0;

    nAudioPortConfig->sample_rate = env->GetIntField(jAudioPortConfig,
                                                     gAudioPortConfigFields.mSamplingRate);
    if (nAudioPortConfig->sample_rate != 0) {
        configMask |= AUDIO_PORT_CONFIG_SAMPLE_RATE;
    }

    bool useInMask = useInChannelMask(nAudioPortConfig->type, nAudioPortConfig->role);
    audio_channel_mask_t nMask;
    jint jMask = env->GetIntField(jAudioPortConfig,
                                   gAudioPortConfigFields.mChannelMask);
    if (useInMask) {
        nMask = inChannelMaskToNative(jMask);
        ALOGV("convertAudioPortConfigToNative IN mask java %x native %x", jMask, nMask);
    } else {
        nMask = outChannelMaskToNative(jMask);
        ALOGV("convertAudioPortConfigToNative OUT mask java %x native %x", jMask, nMask);
    }
    nAudioPortConfig->channel_mask = nMask;
    if (nAudioPortConfig->channel_mask != AUDIO_CHANNEL_NONE) {
        configMask |= AUDIO_PORT_CONFIG_CHANNEL_MASK;
    }

    jint jFormat = env->GetIntField(jAudioPortConfig, gAudioPortConfigFields.mFormat);
    audio_format_t nFormat = audioFormatToNative(jFormat);
    ALOGV("convertAudioPortConfigToNative format %d native %d", jFormat, nFormat);
    nAudioPortConfig->format = nFormat;
    if (nAudioPortConfig->format != AUDIO_FORMAT_DEFAULT &&
            nAudioPortConfig->format != AUDIO_FORMAT_INVALID) {
        configMask |= AUDIO_PORT_CONFIG_FORMAT;
    }

    jobject jGain = env->GetObjectField(jAudioPortConfig, gAudioPortConfigFields.mGain);
    if (jGain != NULL) {
        convertAudioGainConfigToNative(env, &nAudioPortConfig->gain, jGain, useInMask);
        env->DeleteLocalRef(jGain);
        configMask |= AUDIO_PORT_CONFIG_GAIN;
    } else {
        ALOGV("convertAudioPortConfigToNative no gain");
        nAudioPortConfig->gain.index = -1;
    }
    if (useConfigMask) {
        nAudioPortConfig->config_mask = env->GetIntField(jAudioPortConfig,
                                                         gAudioPortConfigFields.mConfigMask);
    } else {
        nAudioPortConfig->config_mask = configMask;
    }
    env->DeleteLocalRef(jAudioPort);
    env->DeleteLocalRef(jHandle);
    return (jint)AUDIO_JAVA_SUCCESS;
}

static jint convertAudioPortConfigFromNative(JNIEnv *env,
                                                 jobject jAudioPort,
                                                 jobject *jAudioPortConfig,
                                                 const struct audio_port_config *nAudioPortConfig)
{
    jint jStatus = AUDIO_JAVA_SUCCESS;
    jobject jAudioGainConfig = NULL;
    jobject jAudioGain = NULL;
    jintArray jGainValues;
    bool audioportCreated = false;

    ALOGV("convertAudioPortConfigFromNative jAudioPort %p", jAudioPort);

    if (jAudioPort == NULL) {
        jobject jHandle = env->NewObject(gAudioHandleClass, gAudioHandleCstor,
                                                 nAudioPortConfig->id);

        ALOGV("convertAudioPortConfigFromNative handle %d is a %s", nAudioPortConfig->id,
              nAudioPortConfig->type == AUDIO_PORT_TYPE_DEVICE ? "device" : "mix");

        if (jHandle == NULL) {
            return (jint)AUDIO_JAVA_ERROR;
        }
        // create dummy port and port config objects with just the correct handle
        // and configuration data. The actual AudioPortConfig objects will be
        // constructed by java code with correct class type (device, mix etc...)
        // and reference to AudioPort instance in this client
        jAudioPort = env->NewObject(gAudioPortClass, gAudioPortCstor,
                                           jHandle, // handle
                                           0,       // role
                                           NULL,    // name
                                           NULL,    // samplingRates
                                           NULL,    // channelMasks
                                           NULL,    // channelIndexMasks
                                           NULL,    // formats
                                           NULL);   // gains
        env->DeleteLocalRef(jHandle);
        if (jAudioPort == NULL) {
            return (jint)AUDIO_JAVA_ERROR;
        }
        ALOGV("convertAudioPortConfigFromNative jAudioPort created for handle %d",
              nAudioPortConfig->id);

        audioportCreated = true;
    }

    bool useInMask = useInChannelMask(nAudioPortConfig->type, nAudioPortConfig->role);

    audio_channel_mask_t nMask;
    jint jMask;

    int gainIndex = nAudioPortConfig->gain.index;
    if (gainIndex >= 0) {
        ALOGV("convertAudioPortConfigFromNative gain found with index %d mode %x",
              gainIndex, nAudioPortConfig->gain.mode);
        if (audioportCreated) {
            ALOGV("convertAudioPortConfigFromNative creating gain");
            jAudioGain = env->NewObject(gAudioGainClass, gAudioGainCstor,
                                               gainIndex,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0);
            if (jAudioGain == NULL) {
                ALOGV("convertAudioPortConfigFromNative creating gain FAILED");
                jStatus = (jint)AUDIO_JAVA_ERROR;
                goto exit;
            }
        } else {
            ALOGV("convertAudioPortConfigFromNative reading gain from port");
            jobjectArray jGains = (jobjectArray)env->GetObjectField(jAudioPort,
                                                                      gAudioPortFields.mGains);
            if (jGains == NULL) {
                ALOGV("convertAudioPortConfigFromNative could not get gains from port");
                jStatus = (jint)AUDIO_JAVA_ERROR;
                goto exit;
            }
            jAudioGain = env->GetObjectArrayElement(jGains, gainIndex);
            env->DeleteLocalRef(jGains);
            if (jAudioGain == NULL) {
                ALOGV("convertAudioPortConfigFromNative could not get gain at index %d", gainIndex);
                jStatus = (jint)AUDIO_JAVA_ERROR;
                goto exit;
            }
        }
        int numValues;
        if (useInMask) {
            numValues = audio_channel_count_from_in_mask(nAudioPortConfig->gain.channel_mask);
        } else {
            numValues = audio_channel_count_from_out_mask(nAudioPortConfig->gain.channel_mask);
        }
        jGainValues = env->NewIntArray(numValues);
        if (jGainValues == NULL) {
            ALOGV("convertAudioPortConfigFromNative could not create gain values %d", numValues);
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
        env->SetIntArrayRegion(jGainValues, 0, numValues,
                               nAudioPortConfig->gain.values);

        nMask = nAudioPortConfig->gain.channel_mask;
        if (useInMask) {
            jMask = inChannelMaskFromNative(nMask);
            ALOGV("convertAudioPortConfigFromNative IN mask java %x native %x", jMask, nMask);
        } else {
            jMask = outChannelMaskFromNative(nMask);
            ALOGV("convertAudioPortConfigFromNative OUT mask java %x native %x", jMask, nMask);
        }

        jAudioGainConfig = env->NewObject(gAudioGainConfigClass,
                                        gAudioGainConfigCstor,
                                        gainIndex,
                                        jAudioGain,
                                        nAudioPortConfig->gain.mode,
                                        jMask,
                                        jGainValues,
                                        nAudioPortConfig->gain.ramp_duration_ms);
        env->DeleteLocalRef(jGainValues);
        if (jAudioGainConfig == NULL) {
            ALOGV("convertAudioPortConfigFromNative could not create gain config");
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
    }
    jclass clazz;
    jmethodID methodID;
    if (audioportCreated) {
        clazz = gAudioPortConfigClass;
        methodID = gAudioPortConfigCstor;
        ALOGV("convertAudioPortConfigFromNative building a generic port config");
    } else {
        if (env->IsInstanceOf(jAudioPort, gAudioDevicePortClass)) {
            clazz = gAudioDevicePortConfigClass;
            methodID = gAudioDevicePortConfigCstor;
            ALOGV("convertAudioPortConfigFromNative building a device config");
        } else if (env->IsInstanceOf(jAudioPort, gAudioMixPortClass)) {
            clazz = gAudioMixPortConfigClass;
            methodID = gAudioMixPortConfigCstor;
            ALOGV("convertAudioPortConfigFromNative building a mix config");
        } else {
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
    }
    nMask = nAudioPortConfig->channel_mask;
    if (useInMask) {
        jMask = inChannelMaskFromNative(nMask);
        ALOGV("convertAudioPortConfigFromNative IN mask java %x native %x", jMask, nMask);
    } else {
        jMask = outChannelMaskFromNative(nMask);
        ALOGV("convertAudioPortConfigFromNative OUT mask java %x native %x", jMask, nMask);
    }

    *jAudioPortConfig = env->NewObject(clazz, methodID,
                                       jAudioPort,
                                       nAudioPortConfig->sample_rate,
                                       jMask,
                                       audioFormatFromNative(nAudioPortConfig->format),
                                       jAudioGainConfig);
    if (*jAudioPortConfig == NULL) {
        ALOGV("convertAudioPortConfigFromNative could not create new port config");
        jStatus = (jint)AUDIO_JAVA_ERROR;
    } else {
        ALOGV("convertAudioPortConfigFromNative OK");
    }

exit:
    if (audioportCreated) {
        env->DeleteLocalRef(jAudioPort);
        if (jAudioGain != NULL) {
            env->DeleteLocalRef(jAudioGain);
        }
    }
    if (jAudioGainConfig != NULL) {
        env->DeleteLocalRef(jAudioGainConfig);
    }
    return jStatus;
}

static bool hasFormat(int* formats, size_t size, int format) {
    for (size_t index = 0; index < size; index++) {
        if (formats[index] == format) {
            return true; // found
        }
    }
    return false; // not found
}

static jint convertAudioPortFromNative(JNIEnv *env,
                                           jobject *jAudioPort, const struct audio_port *nAudioPort)
{
    jint jStatus = (jint)AUDIO_JAVA_SUCCESS;
    jintArray jSamplingRates = NULL;
    jintArray jChannelMasks = NULL;
    jintArray jChannelIndexMasks = NULL;
    int* cFormats = NULL;
    jintArray jFormats = NULL;
    jobjectArray jGains = NULL;
    jobject jHandle = NULL;
    jstring jDeviceName = NULL;
    bool useInMask;
    size_t numPositionMasks = 0;
    size_t numIndexMasks = 0;
    size_t numUniqueFormats = 0;

    ALOGV("convertAudioPortFromNative id %d role %d type %d name %s",
        nAudioPort->id, nAudioPort->role, nAudioPort->type, nAudioPort->name);

    jSamplingRates = env->NewIntArray(nAudioPort->num_sample_rates);
    if (jSamplingRates == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    if (nAudioPort->num_sample_rates) {
        env->SetIntArrayRegion(jSamplingRates, 0, nAudioPort->num_sample_rates,
                               (jint *)nAudioPort->sample_rates);
    }

    // count up how many masks are positional and indexed
    for(size_t index = 0; index < nAudioPort->num_channel_masks; index++) {
        const audio_channel_mask_t mask = nAudioPort->channel_masks[index];
        if (audio_channel_mask_get_representation(mask) == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            numIndexMasks++;
        } else {
            numPositionMasks++;
        }
    }

    jChannelMasks = env->NewIntArray(numPositionMasks);
    if (jChannelMasks == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    jChannelIndexMasks = env->NewIntArray(numIndexMasks);
    if (jChannelIndexMasks == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    useInMask = useInChannelMask(nAudioPort->type, nAudioPort->role);

    // put the masks in the output arrays
    for (size_t maskIndex = 0, posMaskIndex = 0, indexedMaskIndex = 0;
         maskIndex < nAudioPort->num_channel_masks; maskIndex++) {
        const audio_channel_mask_t mask = nAudioPort->channel_masks[maskIndex];
        if (audio_channel_mask_get_representation(mask) == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
            jint jMask = audio_channel_mask_get_bits(mask);
            env->SetIntArrayRegion(jChannelIndexMasks, indexedMaskIndex++, 1, &jMask);
        } else {
            jint jMask = useInMask ? inChannelMaskFromNative(mask)
                                   : outChannelMaskFromNative(mask);
            env->SetIntArrayRegion(jChannelMasks, posMaskIndex++, 1, &jMask);
        }
    }

    // formats
    if (nAudioPort->num_formats != 0) {
        cFormats = new int[nAudioPort->num_formats];
        for (size_t index = 0; index < nAudioPort->num_formats; index++) {
            int format = audioFormatFromNative(nAudioPort->formats[index]);
            if (!hasFormat(cFormats, numUniqueFormats, format)) {
                cFormats[numUniqueFormats++] = format;
            }
        }
    }
    jFormats = env->NewIntArray(numUniqueFormats);
    if (jFormats == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    if (numUniqueFormats != 0) {
        env->SetIntArrayRegion(jFormats, 0, numUniqueFormats, cFormats);
    }

    // gains
    jGains = env->NewObjectArray(nAudioPort->num_gains,
                                          gAudioGainClass, NULL);
    if (jGains == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }

    for (size_t j = 0; j < nAudioPort->num_gains; j++) {
        audio_channel_mask_t nMask = nAudioPort->gains[j].channel_mask;
        jint jMask;
        if (useInMask) {
            jMask = inChannelMaskFromNative(nMask);
            ALOGV("convertAudioPortConfigFromNative IN mask java %x native %x", jMask, nMask);
        } else {
            jMask = outChannelMaskFromNative(nMask);
            ALOGV("convertAudioPortConfigFromNative OUT mask java %x native %x", jMask, nMask);
        }

        jobject jGain = env->NewObject(gAudioGainClass, gAudioGainCstor,
                                                 j,
                                                 nAudioPort->gains[j].mode,
                                                 jMask,
                                                 nAudioPort->gains[j].min_value,
                                                 nAudioPort->gains[j].max_value,
                                                 nAudioPort->gains[j].default_value,
                                                 nAudioPort->gains[j].step_value,
                                                 nAudioPort->gains[j].min_ramp_ms,
                                                 nAudioPort->gains[j].max_ramp_ms);
        if (jGain == NULL) {
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
        env->SetObjectArrayElement(jGains, j, jGain);
        env->DeleteLocalRef(jGain);
    }

    jHandle = env->NewObject(gAudioHandleClass, gAudioHandleCstor,
                                             nAudioPort->id);
    if (jHandle == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }

    jDeviceName = env->NewStringUTF(nAudioPort->name);

    if (nAudioPort->type == AUDIO_PORT_TYPE_DEVICE) {
        ALOGV("convertAudioPortFromNative is a device %08x", nAudioPort->ext.device.type);
        jstring jAddress = env->NewStringUTF(nAudioPort->ext.device.address);
        *jAudioPort = env->NewObject(gAudioDevicePortClass, gAudioDevicePortCstor,
                                     jHandle, jDeviceName,
                                     jSamplingRates, jChannelMasks, jChannelIndexMasks,
                                     jFormats, jGains,
                                     nAudioPort->ext.device.type, jAddress);
        env->DeleteLocalRef(jAddress);
    } else if (nAudioPort->type == AUDIO_PORT_TYPE_MIX) {
        ALOGV("convertAudioPortFromNative is a mix");
        *jAudioPort = env->NewObject(gAudioMixPortClass, gAudioMixPortCstor,
                                     jHandle, nAudioPort->ext.mix.handle,
                                     nAudioPort->role, jDeviceName,
                                     jSamplingRates, jChannelMasks, jChannelIndexMasks,
                                     jFormats, jGains);
    } else {
        ALOGE("convertAudioPortFromNative unknown nAudioPort type %d", nAudioPort->type);
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    if (*jAudioPort == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }

    jobject jAudioPortConfig;
    jStatus = convertAudioPortConfigFromNative(env,
                                                       *jAudioPort,
                                                       &jAudioPortConfig,
                                                       &nAudioPort->active_config);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        return jStatus;
    }

    env->SetObjectField(*jAudioPort, gAudioPortFields.mActiveConfig, jAudioPortConfig);

exit:
    if (jDeviceName != NULL) {
        env->DeleteLocalRef(jDeviceName);
    }
    if (jSamplingRates != NULL) {
        env->DeleteLocalRef(jSamplingRates);
    }
    if (jChannelMasks != NULL) {
        env->DeleteLocalRef(jChannelMasks);
    }
    if (jChannelIndexMasks != NULL) {
        env->DeleteLocalRef(jChannelIndexMasks);
    }
    if (cFormats != NULL) {
        delete[] cFormats;
    }
    if (jFormats != NULL) {
        env->DeleteLocalRef(jFormats);
    }
    if (jGains != NULL) {
        env->DeleteLocalRef(jGains);
    }
    if (jHandle != NULL) {
        env->DeleteLocalRef(jHandle);
    }

    return jStatus;
}


static jint
android_media_AudioSystem_listAudioPorts(JNIEnv *env, jobject clazz,
                                         jobject jPorts, jintArray jGeneration)
{
    ALOGV("listAudioPorts");

    if (jPorts == NULL) {
        ALOGE("listAudioPorts NULL AudioPort ArrayList");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    if (!env->IsInstanceOf(jPorts, gArrayListClass)) {
        ALOGE("listAudioPorts not an arraylist");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    if (jGeneration == NULL || env->GetArrayLength(jGeneration) != 1) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    status_t status;
    unsigned int generation1;
    unsigned int generation;
    unsigned int numPorts;
    jint *nGeneration;
    struct audio_port *nPorts = NULL;
    int attempts = MAX_PORT_GENERATION_SYNC_ATTEMPTS;

    // get the port count and all the ports until they both return the same generation
    do {
        if (attempts-- < 0) {
            status = TIMED_OUT;
            break;
        }

        numPorts = 0;
        status = AudioSystem::listAudioPorts(AUDIO_PORT_ROLE_NONE,
                                             AUDIO_PORT_TYPE_NONE,
                                                      &numPorts,
                                                      NULL,
                                                      &generation1);
        if (status != NO_ERROR || numPorts == 0) {
            ALOGE_IF(status != NO_ERROR, "AudioSystem::listAudioPorts error %d", status);
            break;
        }
        nPorts = (struct audio_port *)realloc(nPorts, numPorts * sizeof(struct audio_port));

        status = AudioSystem::listAudioPorts(AUDIO_PORT_ROLE_NONE,
                                             AUDIO_PORT_TYPE_NONE,
                                                      &numPorts,
                                                      nPorts,
                                                      &generation);
        ALOGV("listAudioPorts AudioSystem::listAudioPorts numPorts %d generation %d generation1 %d",
              numPorts, generation, generation1);
    } while (generation1 != generation && status == NO_ERROR);

    jint jStatus = nativeToJavaStatus(status);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        goto exit;
    }

    nGeneration = env->GetIntArrayElements(jGeneration, NULL);
    if (nGeneration == NULL) {
        jStatus = (jint)AUDIO_JAVA_ERROR;
        goto exit;
    }
    nGeneration[0] = generation1;
    env->ReleaseIntArrayElements(jGeneration, nGeneration, 0);

    for (size_t i = 0; i < numPorts; i++) {
        jobject jAudioPort;
        jStatus = convertAudioPortFromNative(env, &jAudioPort, &nPorts[i]);
        if (jStatus != AUDIO_JAVA_SUCCESS) {
            goto exit;
        }
        env->CallBooleanMethod(jPorts, gArrayListMethods.add, jAudioPort);
    }

exit:
    free(nPorts);
    return jStatus;
}

static int
android_media_AudioSystem_createAudioPatch(JNIEnv *env, jobject clazz,
                                 jobjectArray jPatches, jobjectArray jSources, jobjectArray jSinks)
{
    status_t status;
    jint jStatus;

    ALOGV("createAudioPatch");
    if (jPatches == NULL || jSources == NULL || jSinks == NULL) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    if (env->GetArrayLength(jPatches) != 1) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    jint numSources = env->GetArrayLength(jSources);
    if (numSources == 0 || numSources > AUDIO_PATCH_PORTS_MAX) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    jint numSinks = env->GetArrayLength(jSinks);
    if (numSinks == 0 || numSinks > AUDIO_PATCH_PORTS_MAX) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    audio_patch_handle_t handle = (audio_patch_handle_t)0;
    jobject jPatch = env->GetObjectArrayElement(jPatches, 0);
    jobject jPatchHandle = NULL;
    if (jPatch != NULL) {
        if (!env->IsInstanceOf(jPatch, gAudioPatchClass)) {
            return (jint)AUDIO_JAVA_BAD_VALUE;
        }
        jPatchHandle = env->GetObjectField(jPatch, gAudioPatchFields.mHandle);
        handle = (audio_patch_handle_t)env->GetIntField(jPatchHandle, gAudioHandleFields.mId);
    }

    struct audio_patch nPatch;

    nPatch.id = handle;
    nPatch.num_sources = 0;
    nPatch.num_sinks = 0;
    jobject jSource = NULL;
    jobject jSink = NULL;

    for (jint i = 0; i < numSources; i++) {
        jSource = env->GetObjectArrayElement(jSources, i);
        if (!env->IsInstanceOf(jSource, gAudioPortConfigClass)) {
            jStatus = (jint)AUDIO_JAVA_BAD_VALUE;
            goto exit;
        }
        jStatus = convertAudioPortConfigToNative(env, &nPatch.sources[i], jSource, false);
        env->DeleteLocalRef(jSource);
        jSource = NULL;
        if (jStatus != AUDIO_JAVA_SUCCESS) {
            goto exit;
        }
        nPatch.num_sources++;
    }

    for (jint i = 0; i < numSinks; i++) {
        jSink = env->GetObjectArrayElement(jSinks, i);
        if (!env->IsInstanceOf(jSink, gAudioPortConfigClass)) {
            jStatus = (jint)AUDIO_JAVA_BAD_VALUE;
            goto exit;
        }
        jStatus = convertAudioPortConfigToNative(env, &nPatch.sinks[i], jSink, false);
        env->DeleteLocalRef(jSink);
        jSink = NULL;
        if (jStatus != AUDIO_JAVA_SUCCESS) {
            goto exit;
        }
        nPatch.num_sinks++;
    }

    ALOGV("AudioSystem::createAudioPatch");
    status = AudioSystem::createAudioPatch(&nPatch, &handle);
    ALOGV("AudioSystem::createAudioPatch() returned %d hande %d", status, handle);

    jStatus = nativeToJavaStatus(status);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        goto exit;
    }

    if (jPatchHandle == NULL) {
        jPatchHandle = env->NewObject(gAudioHandleClass, gAudioHandleCstor,
                                           handle);
        if (jPatchHandle == NULL) {
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
        jPatch = env->NewObject(gAudioPatchClass, gAudioPatchCstor, jPatchHandle, jSources, jSinks);
        if (jPatch == NULL) {
            jStatus = (jint)AUDIO_JAVA_ERROR;
            goto exit;
        }
        env->SetObjectArrayElement(jPatches, 0, jPatch);
    } else {
        env->SetIntField(jPatchHandle, gAudioHandleFields.mId, handle);
    }

exit:
    if (jPatchHandle != NULL) {
        env->DeleteLocalRef(jPatchHandle);
    }
    if (jPatch != NULL) {
        env->DeleteLocalRef(jPatch);
    }
    if (jSource != NULL) {
        env->DeleteLocalRef(jSource);
    }
    if (jSink != NULL) {
        env->DeleteLocalRef(jSink);
    }
    return jStatus;
}

static jint
android_media_AudioSystem_releaseAudioPatch(JNIEnv *env, jobject clazz,
                                               jobject jPatch)
{
    ALOGV("releaseAudioPatch");
    if (jPatch == NULL) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    audio_patch_handle_t handle = (audio_patch_handle_t)0;
    jobject jPatchHandle = NULL;
    if (!env->IsInstanceOf(jPatch, gAudioPatchClass)) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    jPatchHandle = env->GetObjectField(jPatch, gAudioPatchFields.mHandle);
    handle = (audio_patch_handle_t)env->GetIntField(jPatchHandle, gAudioHandleFields.mId);
    env->DeleteLocalRef(jPatchHandle);

    ALOGV("AudioSystem::releaseAudioPatch");
    status_t status = AudioSystem::releaseAudioPatch(handle);
    ALOGV("AudioSystem::releaseAudioPatch() returned %d", status);
    jint jStatus = nativeToJavaStatus(status);
    return jStatus;
}

static jint
android_media_AudioSystem_listAudioPatches(JNIEnv *env, jobject clazz,
                                           jobject jPatches, jintArray jGeneration)
{
    ALOGV("listAudioPatches");
    if (jPatches == NULL) {
        ALOGE("listAudioPatches NULL AudioPatch ArrayList");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    if (!env->IsInstanceOf(jPatches, gArrayListClass)) {
        ALOGE("listAudioPatches not an arraylist");
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    if (jGeneration == NULL || env->GetArrayLength(jGeneration) != 1) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }

    status_t status;
    unsigned int generation1;
    unsigned int generation;
    unsigned int numPatches;
    jint *nGeneration;
    struct audio_patch *nPatches = NULL;
    jobjectArray jSources = NULL;
    jobject jSource = NULL;
    jobjectArray jSinks = NULL;
    jobject jSink = NULL;
    jobject jPatch = NULL;
    int attempts = MAX_PORT_GENERATION_SYNC_ATTEMPTS;

    // get the patch count and all the patches until they both return the same generation
    do {
        if (attempts-- < 0) {
            status = TIMED_OUT;
            break;
        }

        numPatches = 0;
        status = AudioSystem::listAudioPatches(&numPatches,
                                               NULL,
                                               &generation1);
        if (status != NO_ERROR || numPatches == 0) {
            ALOGE_IF(status != NO_ERROR, "listAudioPatches AudioSystem::listAudioPatches error %d",
                                      status);
            break;
        }
        nPatches = (struct audio_patch *)realloc(nPatches, numPatches * sizeof(struct audio_patch));

        status = AudioSystem::listAudioPatches(&numPatches,
                                               nPatches,
                                               &generation);
        ALOGV("listAudioPatches AudioSystem::listAudioPatches numPatches %d generation %d generation1 %d",
              numPatches, generation, generation1);

    } while (generation1 != generation && status == NO_ERROR);

    jint jStatus = nativeToJavaStatus(status);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        goto exit;
    }

    nGeneration = env->GetIntArrayElements(jGeneration, NULL);
    if (nGeneration == NULL) {
        jStatus = AUDIO_JAVA_ERROR;
        goto exit;
    }
    nGeneration[0] = generation1;
    env->ReleaseIntArrayElements(jGeneration, nGeneration, 0);

    for (size_t i = 0; i < numPatches; i++) {
        jobject patchHandle = env->NewObject(gAudioHandleClass, gAudioHandleCstor,
                                                 nPatches[i].id);
        if (patchHandle == NULL) {
            jStatus = AUDIO_JAVA_ERROR;
            goto exit;
        }
        ALOGV("listAudioPatches patch %zu num_sources %d num_sinks %d",
              i, nPatches[i].num_sources, nPatches[i].num_sinks);

        env->SetIntField(patchHandle, gAudioHandleFields.mId, nPatches[i].id);

        // load sources
        jSources = env->NewObjectArray(nPatches[i].num_sources,
                                       gAudioPortConfigClass, NULL);
        if (jSources == NULL) {
            jStatus = AUDIO_JAVA_ERROR;
            goto exit;
        }

        for (size_t j = 0; j < nPatches[i].num_sources; j++) {
            jStatus = convertAudioPortConfigFromNative(env,
                                                      NULL,
                                                      &jSource,
                                                      &nPatches[i].sources[j]);
            if (jStatus != AUDIO_JAVA_SUCCESS) {
                goto exit;
            }
            env->SetObjectArrayElement(jSources, j, jSource);
            env->DeleteLocalRef(jSource);
            jSource = NULL;
            ALOGV("listAudioPatches patch %zu source %zu is a %s handle %d",
                  i, j,
                  nPatches[i].sources[j].type == AUDIO_PORT_TYPE_DEVICE ? "device" : "mix",
                  nPatches[i].sources[j].id);
        }
        // load sinks
        jSinks = env->NewObjectArray(nPatches[i].num_sinks,
                                     gAudioPortConfigClass, NULL);
        if (jSinks == NULL) {
            jStatus = AUDIO_JAVA_ERROR;
            goto exit;
        }

        for (size_t j = 0; j < nPatches[i].num_sinks; j++) {
            jStatus = convertAudioPortConfigFromNative(env,
                                                      NULL,
                                                      &jSink,
                                                      &nPatches[i].sinks[j]);

            if (jStatus != AUDIO_JAVA_SUCCESS) {
                goto exit;
            }
            env->SetObjectArrayElement(jSinks, j, jSink);
            env->DeleteLocalRef(jSink);
            jSink = NULL;
            ALOGV("listAudioPatches patch %zu sink %zu is a %s handle %d",
                  i, j,
                  nPatches[i].sinks[j].type == AUDIO_PORT_TYPE_DEVICE ? "device" : "mix",
                  nPatches[i].sinks[j].id);
        }

        jPatch = env->NewObject(gAudioPatchClass, gAudioPatchCstor,
                                       patchHandle, jSources, jSinks);
        env->DeleteLocalRef(jSources);
        jSources = NULL;
        env->DeleteLocalRef(jSinks);
        jSinks = NULL;
        if (jPatch == NULL) {
            jStatus = AUDIO_JAVA_ERROR;
            goto exit;
        }
        env->CallBooleanMethod(jPatches, gArrayListMethods.add, jPatch);
        env->DeleteLocalRef(jPatch);
        jPatch = NULL;
    }

exit:
    if (jSources != NULL) {
        env->DeleteLocalRef(jSources);
    }
    if (jSource != NULL) {
        env->DeleteLocalRef(jSource);
    }
    if (jSinks != NULL) {
        env->DeleteLocalRef(jSinks);
    }
    if (jSink != NULL) {
        env->DeleteLocalRef(jSink);
    }
    if (jPatch != NULL) {
        env->DeleteLocalRef(jPatch);
    }
    free(nPatches);
    return jStatus;
}

static jint
android_media_AudioSystem_setAudioPortConfig(JNIEnv *env, jobject clazz,
                                 jobject jAudioPortConfig)
{
    ALOGV("setAudioPortConfig");
    if (jAudioPortConfig == NULL) {
        return AUDIO_JAVA_BAD_VALUE;
    }
    if (!env->IsInstanceOf(jAudioPortConfig, gAudioPortConfigClass)) {
        return AUDIO_JAVA_BAD_VALUE;
    }
    struct audio_port_config nAudioPortConfig;
    jint jStatus = convertAudioPortConfigToNative(env, &nAudioPortConfig, jAudioPortConfig, true);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        return jStatus;
    }
    status_t status = AudioSystem::setAudioPortConfig(&nAudioPortConfig);
    ALOGV("AudioSystem::setAudioPortConfig() returned %d", status);
    jStatus = nativeToJavaStatus(status);
    return jStatus;
}

static void
android_media_AudioSystem_eventHandlerSetup(JNIEnv *env, jobject thiz, jobject weak_this)
{
    ALOGV("eventHandlerSetup");

    sp<JNIAudioPortCallback> callback = new JNIAudioPortCallback(env, thiz, weak_this);

    if (AudioSystem::addAudioPortCallback(callback) == NO_ERROR) {
        setJniCallback(env, thiz, callback);
    }
}

static void
android_media_AudioSystem_eventHandlerFinalize(JNIEnv *env, jobject thiz)
{
    ALOGV("eventHandlerFinalize");

    sp<JNIAudioPortCallback> callback = setJniCallback(env, thiz, 0);

    if (callback != 0) {
        AudioSystem::removeAudioPortCallback(callback);
    }
}

static jint
android_media_AudioSystem_getAudioHwSyncForSession(JNIEnv *env, jobject thiz, jint sessionId)
{
    return (jint)AudioSystem::getAudioHwSyncForSession((audio_session_t)sessionId);
}

static void
android_media_AudioSystem_registerDynPolicyCallback(JNIEnv *env, jobject thiz)
{
    AudioSystem::setDynPolicyCallback(android_media_AudioSystem_dyn_policy_callback);
}

static void
android_media_AudioSystem_registerEffectSessionCallback(JNIEnv *env, jobject thiz)
{
    AudioSystem::setEffectSessionCallback(android_media_AudioSystem_effect_session_callback);
}


static jint convertAudioMixToNative(JNIEnv *env,
                                    AudioMix *nAudioMix,
                                    const jobject jAudioMix)
{
    nAudioMix->mMixType = env->GetIntField(jAudioMix, gAudioMixFields.mMixType);
    nAudioMix->mRouteFlags = env->GetIntField(jAudioMix, gAudioMixFields.mRouteFlags);

    jstring jRegistrationId = (jstring)env->GetObjectField(jAudioMix,
                                                           gAudioMixFields.mRegistrationId);
    const char *nRegistrationId = env->GetStringUTFChars(jRegistrationId, NULL);
    nAudioMix->mRegistrationId = String8(nRegistrationId);
    env->ReleaseStringUTFChars(jRegistrationId, nRegistrationId);
    env->DeleteLocalRef(jRegistrationId);

    nAudioMix->mCbFlags = env->GetIntField(jAudioMix, gAudioMixFields.mCallbackFlags);

    jobject jFormat = env->GetObjectField(jAudioMix, gAudioMixFields.mFormat);
    nAudioMix->mFormat.sample_rate = env->GetIntField(jFormat,
                                                     gAudioFormatFields.mSampleRate);
    nAudioMix->mFormat.channel_mask = outChannelMaskToNative(env->GetIntField(jFormat,
                                                     gAudioFormatFields.mChannelMask));
    nAudioMix->mFormat.format = audioFormatToNative(env->GetIntField(jFormat,
                                                     gAudioFormatFields.mEncoding));
    env->DeleteLocalRef(jFormat);

    jobject jRule = env->GetObjectField(jAudioMix, gAudioMixFields.mRule);
    jobject jRuleCriteria = env->GetObjectField(jRule, gAudioMixingRuleFields.mCriteria);
    env->DeleteLocalRef(jRule);
    jobjectArray jCriteria = (jobjectArray)env->CallObjectMethod(jRuleCriteria,
                                                                 gArrayListMethods.toArray);
    env->DeleteLocalRef(jRuleCriteria);

    jint numCriteria = env->GetArrayLength(jCriteria);
    if (numCriteria > MAX_CRITERIA_PER_MIX) {
        numCriteria = MAX_CRITERIA_PER_MIX;
    }

    for (jint i = 0; i < numCriteria; i++) {
        AttributeMatchCriterion nCriterion;

        jobject jCriterion = env->GetObjectArrayElement(jCriteria, i);

        nCriterion.mRule = env->GetIntField(jCriterion, gAttributeMatchCriterionFields.mRule);

        jobject jAttributes = env->GetObjectField(jCriterion, gAttributeMatchCriterionFields.mAttr);
        if (nCriterion.mRule == RULE_MATCH_ATTRIBUTE_USAGE ||
                nCriterion.mRule == RULE_EXCLUDE_ATTRIBUTE_USAGE) {
            nCriterion.mAttr.mUsage = (audio_usage_t)env->GetIntField(jAttributes,
                                                       gAudioAttributesFields.mUsage);
        } else {
            nCriterion.mAttr.mSource = (audio_source_t)env->GetIntField(jAttributes,
                                                        gAudioAttributesFields.mSource);
        }
        env->DeleteLocalRef(jAttributes);

        nAudioMix->mCriteria.add(nCriterion);
        env->DeleteLocalRef(jCriterion);
    }

    env->DeleteLocalRef(jCriteria);

    return (jint)AUDIO_JAVA_SUCCESS;
}

static jint
android_media_AudioSystem_registerPolicyMixes(JNIEnv *env, jobject clazz,
                                              jobject jMixesList, jboolean registration)
{
    ALOGV("registerPolicyMixes");

    if (jMixesList == NULL) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    if (!env->IsInstanceOf(jMixesList, gArrayListClass)) {
        return (jint)AUDIO_JAVA_BAD_VALUE;
    }
    jobjectArray jMixes = (jobjectArray)env->CallObjectMethod(jMixesList,
                                                              gArrayListMethods.toArray);
    jint numMixes = env->GetArrayLength(jMixes);
    if (numMixes > MAX_MIXES_PER_POLICY) {
        numMixes = MAX_MIXES_PER_POLICY;
    }

    status_t status;
    jint jStatus;
    jobject jAudioMix = NULL;
    Vector <AudioMix> mixes;
    for (jint i = 0; i < numMixes; i++) {
        jAudioMix = env->GetObjectArrayElement(jMixes, i);
        if (!env->IsInstanceOf(jAudioMix, gAudioMixClass)) {
            jStatus = (jint)AUDIO_JAVA_BAD_VALUE;
            goto exit;
        }
        AudioMix mix;
        jStatus = convertAudioMixToNative(env, &mix, jAudioMix);
        env->DeleteLocalRef(jAudioMix);
        jAudioMix = NULL;
        if (jStatus != AUDIO_JAVA_SUCCESS) {
            goto exit;
        }
        mixes.add(mix);
    }

    ALOGV("AudioSystem::registerPolicyMixes numMixes %d registration %d", numMixes, registration);
    status = AudioSystem::registerPolicyMixes(mixes, registration);
    ALOGV("AudioSystem::registerPolicyMixes() returned %d", status);

    jStatus = nativeToJavaStatus(status);
    if (jStatus != AUDIO_JAVA_SUCCESS) {
        goto exit;
    }

exit:
    if (jAudioMix != NULL) {
        env->DeleteLocalRef(jAudioMix);
    }
    return jStatus;
}

static jint
android_media_AudioSystem_systemReady(JNIEnv *env, jobject thiz)
{
    return nativeToJavaStatus(AudioSystem::systemReady());
}


// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"setParameters",        "(Ljava/lang/String;)I", (void *)android_media_AudioSystem_setParameters},
    {"getParameters",        "(Ljava/lang/String;)Ljava/lang/String;", (void *)android_media_AudioSystem_getParameters},
    {"muteMicrophone",      "(Z)I",     (void *)android_media_AudioSystem_muteMicrophone},
    {"isMicrophoneMuted",   "()Z",      (void *)android_media_AudioSystem_isMicrophoneMuted},
    {"isStreamActive",      "(II)Z",    (void *)android_media_AudioSystem_isStreamActive},
    {"isStreamActiveRemotely","(II)Z",  (void *)android_media_AudioSystem_isStreamActiveRemotely},
    {"isSourceActive",      "(I)Z",     (void *)android_media_AudioSystem_isSourceActive},
    {"newAudioSessionId",   "()I",      (void *)android_media_AudioSystem_newAudioSessionId},
    {"setDeviceConnectionState", "(IILjava/lang/String;Ljava/lang/String;)I", (void *)android_media_AudioSystem_setDeviceConnectionState},
    {"getDeviceConnectionState", "(ILjava/lang/String;)I",  (void *)android_media_AudioSystem_getDeviceConnectionState},
    {"setPhoneState",       "(I)I",     (void *)android_media_AudioSystem_setPhoneState},
    {"setForceUse",         "(II)I",    (void *)android_media_AudioSystem_setForceUse},
    {"getForceUse",         "(I)I",     (void *)android_media_AudioSystem_getForceUse},
    {"initStreamVolume",    "(III)I",   (void *)android_media_AudioSystem_initStreamVolume},
    {"setStreamVolumeIndex","(III)I",   (void *)android_media_AudioSystem_setStreamVolumeIndex},
    {"getStreamVolumeIndex","(II)I",    (void *)android_media_AudioSystem_getStreamVolumeIndex},
    {"setMasterVolume",     "(F)I",     (void *)android_media_AudioSystem_setMasterVolume},
    {"getMasterVolume",     "()F",      (void *)android_media_AudioSystem_getMasterVolume},
    {"setMasterMute",       "(Z)I",     (void *)android_media_AudioSystem_setMasterMute},
    {"getMasterMute",       "()Z",      (void *)android_media_AudioSystem_getMasterMute},
    {"getDevicesForStream", "(I)I",     (void *)android_media_AudioSystem_getDevicesForStream},
    {"getPrimaryOutputSamplingRate", "()I", (void *)android_media_AudioSystem_getPrimaryOutputSamplingRate},
    {"getPrimaryOutputFrameCount",   "()I", (void *)android_media_AudioSystem_getPrimaryOutputFrameCount},
    {"getOutputLatency",    "(I)I",     (void *)android_media_AudioSystem_getOutputLatency},
    {"setLowRamDevice",     "(Z)I",     (void *)android_media_AudioSystem_setLowRamDevice},
    {"checkAudioFlinger",    "()I",     (void *)android_media_AudioSystem_checkAudioFlinger},
    {"listAudioPorts",      "(Ljava/util/ArrayList;[I)I",
                                                (void *)android_media_AudioSystem_listAudioPorts},
    {"createAudioPatch",    "([Landroid/media/AudioPatch;[Landroid/media/AudioPortConfig;[Landroid/media/AudioPortConfig;)I",
                                            (void *)android_media_AudioSystem_createAudioPatch},
    {"releaseAudioPatch",   "(Landroid/media/AudioPatch;)I",
                                            (void *)android_media_AudioSystem_releaseAudioPatch},
    {"listAudioPatches",    "(Ljava/util/ArrayList;[I)I",
                                                (void *)android_media_AudioSystem_listAudioPatches},
    {"setAudioPortConfig",   "(Landroid/media/AudioPortConfig;)I",
                                            (void *)android_media_AudioSystem_setAudioPortConfig},
    {"getAudioHwSyncForSession", "(I)I",
                                    (void *)android_media_AudioSystem_getAudioHwSyncForSession},
    {"registerPolicyMixes",    "(Ljava/util/ArrayList;Z)I",
                                            (void *)android_media_AudioSystem_registerPolicyMixes},
    {"native_register_dynamic_policy_callback", "()V",
                                    (void *)android_media_AudioSystem_registerDynPolicyCallback},
    {"native_register_effect_session_callback", "()V",
                                    (void *)android_media_AudioSystem_registerEffectSessionCallback},
    {"systemReady", "()I", (void *)android_media_AudioSystem_systemReady},
};


static JNINativeMethod gEventHandlerMethods[] = {
    {"native_setup",
        "(Ljava/lang/Object;)V",
        (void *)android_media_AudioSystem_eventHandlerSetup},
    {"native_finalize",
        "()V",
        (void *)android_media_AudioSystem_eventHandlerFinalize},
};

int register_android_media_AudioSystem(JNIEnv *env)
{
    jclass arrayListClass = FindClassOrDie(env, "java/util/ArrayList");
    gArrayListClass = MakeGlobalRefOrDie(env, arrayListClass);
    gArrayListMethods.add = GetMethodIDOrDie(env, arrayListClass, "add", "(Ljava/lang/Object;)Z");
    gArrayListMethods.toArray = GetMethodIDOrDie(env, arrayListClass, "toArray", "()[Ljava/lang/Object;");

    jclass audioHandleClass = FindClassOrDie(env, "android/media/AudioHandle");
    gAudioHandleClass = MakeGlobalRefOrDie(env, audioHandleClass);
    gAudioHandleCstor = GetMethodIDOrDie(env, audioHandleClass, "<init>", "(I)V");
    gAudioHandleFields.mId = GetFieldIDOrDie(env, audioHandleClass, "mId", "I");

    jclass audioPortClass = FindClassOrDie(env, "android/media/AudioPort");
    gAudioPortClass = MakeGlobalRefOrDie(env, audioPortClass);
    gAudioPortCstor = GetMethodIDOrDie(env, audioPortClass, "<init>",
            "(Landroid/media/AudioHandle;ILjava/lang/String;[I[I[I[I[Landroid/media/AudioGain;)V");
    gAudioPortFields.mHandle = GetFieldIDOrDie(env, audioPortClass, "mHandle",
                                               "Landroid/media/AudioHandle;");
    gAudioPortFields.mRole = GetFieldIDOrDie(env, audioPortClass, "mRole", "I");
    gAudioPortFields.mGains = GetFieldIDOrDie(env, audioPortClass, "mGains",
                                              "[Landroid/media/AudioGain;");
    gAudioPortFields.mActiveConfig = GetFieldIDOrDie(env, audioPortClass, "mActiveConfig",
                                                     "Landroid/media/AudioPortConfig;");

    jclass audioPortConfigClass = FindClassOrDie(env, "android/media/AudioPortConfig");
    gAudioPortConfigClass = MakeGlobalRefOrDie(env, audioPortConfigClass);
    gAudioPortConfigCstor = GetMethodIDOrDie(env, audioPortConfigClass, "<init>",
            "(Landroid/media/AudioPort;IIILandroid/media/AudioGainConfig;)V");
    gAudioPortConfigFields.mPort = GetFieldIDOrDie(env, audioPortConfigClass, "mPort",
                                                   "Landroid/media/AudioPort;");
    gAudioPortConfigFields.mSamplingRate = GetFieldIDOrDie(env, audioPortConfigClass,
                                                           "mSamplingRate", "I");
    gAudioPortConfigFields.mChannelMask = GetFieldIDOrDie(env, audioPortConfigClass,
                                                          "mChannelMask", "I");
    gAudioPortConfigFields.mFormat = GetFieldIDOrDie(env, audioPortConfigClass, "mFormat", "I");
    gAudioPortConfigFields.mGain = GetFieldIDOrDie(env, audioPortConfigClass, "mGain",
                                                   "Landroid/media/AudioGainConfig;");
    gAudioPortConfigFields.mConfigMask = GetFieldIDOrDie(env, audioPortConfigClass, "mConfigMask",
                                                         "I");

    jclass audioDevicePortConfigClass = FindClassOrDie(env, "android/media/AudioDevicePortConfig");
    gAudioDevicePortConfigClass = MakeGlobalRefOrDie(env, audioDevicePortConfigClass);
    gAudioDevicePortConfigCstor = GetMethodIDOrDie(env, audioDevicePortConfigClass, "<init>",
            "(Landroid/media/AudioDevicePort;IIILandroid/media/AudioGainConfig;)V");

    jclass audioMixPortConfigClass = FindClassOrDie(env, "android/media/AudioMixPortConfig");
    gAudioMixPortConfigClass = MakeGlobalRefOrDie(env, audioMixPortConfigClass);
    gAudioMixPortConfigCstor = GetMethodIDOrDie(env, audioMixPortConfigClass, "<init>",
            "(Landroid/media/AudioMixPort;IIILandroid/media/AudioGainConfig;)V");

    jclass audioDevicePortClass = FindClassOrDie(env, "android/media/AudioDevicePort");
    gAudioDevicePortClass = MakeGlobalRefOrDie(env, audioDevicePortClass);
    gAudioDevicePortCstor = GetMethodIDOrDie(env, audioDevicePortClass, "<init>",
            "(Landroid/media/AudioHandle;Ljava/lang/String;[I[I[I[I[Landroid/media/AudioGain;ILjava/lang/String;)V");

    jclass audioMixPortClass = FindClassOrDie(env, "android/media/AudioMixPort");
    gAudioMixPortClass = MakeGlobalRefOrDie(env, audioMixPortClass);
    gAudioMixPortCstor = GetMethodIDOrDie(env, audioMixPortClass, "<init>",
            "(Landroid/media/AudioHandle;IILjava/lang/String;[I[I[I[I[Landroid/media/AudioGain;)V");

    jclass audioGainClass = FindClassOrDie(env, "android/media/AudioGain");
    gAudioGainClass = MakeGlobalRefOrDie(env, audioGainClass);
    gAudioGainCstor = GetMethodIDOrDie(env, audioGainClass, "<init>", "(IIIIIIIII)V");

    jclass audioGainConfigClass = FindClassOrDie(env, "android/media/AudioGainConfig");
    gAudioGainConfigClass = MakeGlobalRefOrDie(env, audioGainConfigClass);
    gAudioGainConfigCstor = GetMethodIDOrDie(env, audioGainConfigClass, "<init>",
                                             "(ILandroid/media/AudioGain;II[II)V");
    gAudioGainConfigFields.mIndex = GetFieldIDOrDie(env, gAudioGainConfigClass, "mIndex", "I");
    gAudioGainConfigFields.mMode = GetFieldIDOrDie(env, audioGainConfigClass, "mMode", "I");
    gAudioGainConfigFields.mChannelMask = GetFieldIDOrDie(env, audioGainConfigClass, "mChannelMask",
                                                          "I");
    gAudioGainConfigFields.mValues = GetFieldIDOrDie(env, audioGainConfigClass, "mValues", "[I");
    gAudioGainConfigFields.mRampDurationMs = GetFieldIDOrDie(env, audioGainConfigClass,
                                                             "mRampDurationMs", "I");

    jclass audioPatchClass = FindClassOrDie(env, "android/media/AudioPatch");
    gAudioPatchClass = MakeGlobalRefOrDie(env, audioPatchClass);
    gAudioPatchCstor = GetMethodIDOrDie(env, audioPatchClass, "<init>",
"(Landroid/media/AudioHandle;[Landroid/media/AudioPortConfig;[Landroid/media/AudioPortConfig;)V");
    gAudioPatchFields.mHandle = GetFieldIDOrDie(env, audioPatchClass, "mHandle",
                                                "Landroid/media/AudioHandle;");

    jclass eventHandlerClass = FindClassOrDie(env, kEventHandlerClassPathName);
    gAudioPortEventHandlerMethods.postEventFromNative = GetStaticMethodIDOrDie(
                                                    env, eventHandlerClass, "postEventFromNative",
                                                    "(Ljava/lang/Object;IIILjava/lang/Object;)V");
    gEventHandlerFields.mJniCallback = GetFieldIDOrDie(env,
                                                    eventHandlerClass, "mJniCallback", "J");

    gDynPolicyEventHandlerMethods.postDynPolicyEventFromNative =
            GetStaticMethodIDOrDie(env, env->FindClass(kClassPathName),
                    "dynamicPolicyCallbackFromNative", "(ILjava/lang/String;I)V");

    gEffectSessionEventHandlerMethods.postEffectSessionEventFromNative =
            GetStaticMethodIDOrDie(env, env->FindClass(kClassPathName),
                    "effectSessionCallbackFromNative", "(IIIZ)V");

    jclass audioMixClass = FindClassOrDie(env, "android/media/audiopolicy/AudioMix");
    gAudioMixClass = MakeGlobalRefOrDie(env, audioMixClass);
    gAudioMixFields.mRule = GetFieldIDOrDie(env, audioMixClass, "mRule",
                                                "Landroid/media/audiopolicy/AudioMixingRule;");
    gAudioMixFields.mFormat = GetFieldIDOrDie(env, audioMixClass, "mFormat",
                                                "Landroid/media/AudioFormat;");
    gAudioMixFields.mRouteFlags = GetFieldIDOrDie(env, audioMixClass, "mRouteFlags", "I");
    gAudioMixFields.mRegistrationId = GetFieldIDOrDie(env, audioMixClass, "mRegistrationId",
                                                      "Ljava/lang/String;");
    gAudioMixFields.mMixType = GetFieldIDOrDie(env, audioMixClass, "mMixType", "I");
    gAudioMixFields.mCallbackFlags = GetFieldIDOrDie(env, audioMixClass, "mCallbackFlags", "I");

    jclass audioFormatClass = FindClassOrDie(env, "android/media/AudioFormat");
    gAudioFormatClass = MakeGlobalRefOrDie(env, audioFormatClass);
    gAudioFormatFields.mEncoding = GetFieldIDOrDie(env, audioFormatClass, "mEncoding", "I");
    gAudioFormatFields.mSampleRate = GetFieldIDOrDie(env, audioFormatClass, "mSampleRate", "I");
    gAudioFormatFields.mChannelMask = GetFieldIDOrDie(env, audioFormatClass, "mChannelMask", "I");

    jclass audioMixingRuleClass = FindClassOrDie(env, "android/media/audiopolicy/AudioMixingRule");
    gAudioMixingRuleClass = MakeGlobalRefOrDie(env, audioMixingRuleClass);
    gAudioMixingRuleFields.mCriteria = GetFieldIDOrDie(env, audioMixingRuleClass, "mCriteria",
                                                       "Ljava/util/ArrayList;");

    jclass attributeMatchCriterionClass =
                FindClassOrDie(env, "android/media/audiopolicy/AudioMixingRule$AttributeMatchCriterion");
    gAttributeMatchCriterionClass = MakeGlobalRefOrDie(env, attributeMatchCriterionClass);
    gAttributeMatchCriterionFields.mAttr = GetFieldIDOrDie(env, attributeMatchCriterionClass, "mAttr",
                                                       "Landroid/media/AudioAttributes;");
    gAttributeMatchCriterionFields.mRule = GetFieldIDOrDie(env, attributeMatchCriterionClass, "mRule",
                                                       "I");

    jclass audioAttributesClass = FindClassOrDie(env, "android/media/AudioAttributes");
    gAudioAttributesClass = MakeGlobalRefOrDie(env, audioAttributesClass);
    gAudioAttributesFields.mUsage = GetFieldIDOrDie(env, audioAttributesClass, "mUsage", "I");
    gAudioAttributesFields.mSource = GetFieldIDOrDie(env, audioAttributesClass, "mSource", "I");

    AudioSystem::setErrorCallback(android_media_AudioSystem_error_callback);

    RegisterMethodsOrDie(env, kClassPathName, gMethods, NELEM(gMethods));
    return RegisterMethodsOrDie(env, kEventHandlerClassPathName, gEventHandlerMethods,
                                NELEM(gEventHandlerMethods));
}
