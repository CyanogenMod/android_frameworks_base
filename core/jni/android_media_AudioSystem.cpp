/* //device/libs/android_runtime/android_media_AudioSystem.cpp
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

#define LOG_TAG "AudioSystem"
#include "utils/Log.h"

#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <math.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <media/AudioSystem.h>
#include <media/AudioTrack.h>

// ----------------------------------------------------------------------------

using namespace android;

enum AudioError {
    kAudioStatusOk = 0,
    kAudioStatusError = 1,
    kAudioStatusMediaServerDied = 100
};

static int check_AudioSystem_Command(status_t status)
{
    if (status == NO_ERROR) {
        return kAudioStatusOk;
    } else {
        return kAudioStatusError;
    }
}

static int
android_media_AudioSystem_muteMicrophone(JNIEnv *env, jobject thiz, jboolean on)
{
    return check_AudioSystem_Command(AudioSystem::muteMicrophone(on));
}

static jboolean
android_media_AudioSystem_isMicrophoneMuted(JNIEnv *env, jobject thiz)
{
    bool state = false;
    AudioSystem::isMicrophoneMuted(&state);
    return state;
}

static jboolean
android_media_AudioSystem_isStreamActive(JNIEnv *env, jobject thiz, jint stream)
{
    bool state = false;
    AudioSystem::isStreamActive(stream, &state);
    return state;
}

static int
android_media_AudioSystem_hasFmPropMode(JNIEnv *env, jobject thiz)
{
  return AudioSystem::hasFmPropMode();
}

static int
android_media_AudioSystem_setParameters(JNIEnv *env, jobject thiz, jstring keyValuePairs)
{
    const jchar* c_keyValuePairs = env->GetStringCritical(keyValuePairs, 0);
    String8 c_keyValuePairs8;
    if (keyValuePairs) {
        c_keyValuePairs8 = String8(c_keyValuePairs, env->GetStringLength(keyValuePairs));
        env->ReleaseStringCritical(keyValuePairs, c_keyValuePairs);
    }
    int status = check_AudioSystem_Command(AudioSystem::setParameters(0, c_keyValuePairs8));
    return status;
}

static jstring
android_media_AudioSystem_getParameters(JNIEnv *env, jobject thiz, jstring keys)
{
    const jchar* c_keys = env->GetStringCritical(keys, 0);
    String8 c_keys8;
    if (keys) {
        c_keys8 = String8(c_keys, env->GetStringLength(keys));
        env->ReleaseStringCritical(keys, c_keys);
    }
    return env->NewStringUTF(AudioSystem::getParameters(0, c_keys8).string());
}

void android_media_AudioSystem_error_callback(status_t err)
{
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    if (env == NULL) {
        return;
    }

    jclass clazz = env->FindClass("android/media/AudioSystem");

    int error;

    switch (err) {
    case DEAD_OBJECT:
        error = kAudioStatusMediaServerDied;
        break;
    case NO_ERROR:
        error = kAudioStatusOk;
        break;
    default:
        error = kAudioStatusError;
        break;
    }

    env->CallStaticVoidMethod(clazz, env->GetStaticMethodID(clazz, "errorCallbackFromNative","(I)V"), error);
}

static int
android_media_AudioSystem_setDeviceConnectionState(JNIEnv *env, jobject thiz, jint device, jint state, jstring device_address)
{
    const char *c_address = env->GetStringUTFChars(device_address, NULL);
    int status = check_AudioSystem_Command(AudioSystem::setDeviceConnectionState(static_cast <AudioSystem::audio_devices>(device),
                                          static_cast <AudioSystem::device_connection_state>(state),
                                          c_address));
    env->ReleaseStringUTFChars(device_address, c_address);
    return status;
}

static int
android_media_AudioSystem_getDeviceConnectionState(JNIEnv *env, jobject thiz, jint device, jstring device_address)
{
    const char *c_address = env->GetStringUTFChars(device_address, NULL);
    int state = static_cast <int>(AudioSystem::getDeviceConnectionState(static_cast <AudioSystem::audio_devices>(device),
                                          c_address));
    env->ReleaseStringUTFChars(device_address, c_address);
    return state;
}

static int
android_media_AudioSystem_setPhoneState(JNIEnv *env, jobject thiz, jint state)
{
    return check_AudioSystem_Command(AudioSystem::setPhoneState(state));
}

static int
android_media_AudioSystem_setRingerMode(JNIEnv *env, jobject thiz, jint mode, jint mask)
{
    return check_AudioSystem_Command(AudioSystem::setRingerMode(mode, mask));
}

static int
android_media_AudioSystem_setForceUse(JNIEnv *env, jobject thiz, jint usage, jint config)
{
    return check_AudioSystem_Command(AudioSystem::setForceUse(static_cast <AudioSystem::force_use>(usage),
                                                           static_cast <AudioSystem::forced_config>(config)));
}

static int
android_media_AudioSystem_getForceUse(JNIEnv *env, jobject thiz, jint usage)
{
    return static_cast <int>(AudioSystem::getForceUse(static_cast <AudioSystem::force_use>(usage)));
}

static int
android_media_AudioSystem_initStreamVolume(JNIEnv *env, jobject thiz, jint stream, jint indexMin, jint indexMax)
{
    return check_AudioSystem_Command(AudioSystem::initStreamVolume(static_cast <AudioSystem::stream_type>(stream),
                                                                   indexMin,
                                                                   indexMax));
}

static int
android_media_AudioSystem_setStreamVolumeIndex(JNIEnv *env, jobject thiz, jint stream, jint index)
{
    return check_AudioSystem_Command(AudioSystem::setStreamVolumeIndex(static_cast <AudioSystem::stream_type>(stream), index));
}

static int
android_media_AudioSystem_getStreamVolumeIndex(JNIEnv *env, jobject thiz, jint stream)
{
    int index;
    if (AudioSystem::getStreamVolumeIndex(static_cast <AudioSystem::stream_type>(stream), &index) != NO_ERROR) {
        index = -1;
    }
    return index;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"setParameters",        "(Ljava/lang/String;)I", (void *)android_media_AudioSystem_setParameters},
    {"getParameters",        "(Ljava/lang/String;)Ljava/lang/String;", (void *)android_media_AudioSystem_getParameters},
    {"muteMicrophone",      "(Z)I",     (void *)android_media_AudioSystem_muteMicrophone},
    {"isMicrophoneMuted",   "()Z",      (void *)android_media_AudioSystem_isMicrophoneMuted},
    {"isStreamActive",      "(I)Z",     (void *)android_media_AudioSystem_isStreamActive},
    {"hasFmPropMode",       "()I",      (void *)android_media_AudioSystem_hasFmPropMode},
    {"setDeviceConnectionState", "(IILjava/lang/String;)I", (void *)android_media_AudioSystem_setDeviceConnectionState},
    {"getDeviceConnectionState", "(ILjava/lang/String;)I",  (void *)android_media_AudioSystem_getDeviceConnectionState},
    {"setPhoneState",       "(I)I",     (void *)android_media_AudioSystem_setPhoneState},
    {"setRingerMode",       "(II)I",    (void *)android_media_AudioSystem_setRingerMode},
    {"setForceUse",         "(II)I",    (void *)android_media_AudioSystem_setForceUse},
    {"getForceUse",         "(I)I",     (void *)android_media_AudioSystem_getForceUse},
    {"initStreamVolume",    "(III)I",   (void *)android_media_AudioSystem_initStreamVolume},
    {"setStreamVolumeIndex","(II)I",    (void *)android_media_AudioSystem_setStreamVolumeIndex},
    {"getStreamVolumeIndex","(I)I",     (void *)android_media_AudioSystem_getStreamVolumeIndex}
};

const char* const kClassPathName = "android/media/AudioSystem";

int register_android_media_AudioSystem(JNIEnv *env)
{
    AudioSystem::setErrorCallback(android_media_AudioSystem_error_callback);
    
    return AndroidRuntime::registerNativeMethods(env,
                "android/media/AudioSystem", gMethods, NELEM(gMethods));
}
