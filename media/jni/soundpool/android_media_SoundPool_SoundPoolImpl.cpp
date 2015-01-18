/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include <stdio.h>

//#define LOG_NDEBUG 0
#define LOG_TAG "SoundPool-JNI"

#include <utils/Log.h>
#include <nativehelper/jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>
#include <media/SoundPool.h>

using namespace android;

static struct fields_t {
    jfieldID    mNativeContext;
    jmethodID   mPostEvent;
    jclass      mSoundPoolClass;
} fields;
static inline SoundPool* MusterSoundPool(JNIEnv *env, jobject thiz) {
    return (SoundPool*)env->GetLongField(thiz, fields.mNativeContext);
}
static const char* const kAudioAttributesClassPathName = "android/media/AudioAttributes";
struct audio_attributes_fields_t {
    jfieldID  fieldUsage;        // AudioAttributes.mUsage
    jfieldID  fieldContentType;  // AudioAttributes.mContentType
    jfieldID  fieldFlags;        // AudioAttributes.mFlags
    jfieldID  fieldFormattedTags;// AudioAttributes.mFormattedTags
};
static audio_attributes_fields_t javaAudioAttrFields;

// ----------------------------------------------------------------------------
static jint
android_media_SoundPool_SoundPoolImpl_load_URL(JNIEnv *env, jobject thiz, jstring path, jint priority)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_load_URL");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (path == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return 0;
    }
    const char* s = env->GetStringUTFChars(path, NULL);
    int id = ap->load(s, priority);
    env->ReleaseStringUTFChars(path, s);
    return (jint) id;
}

static jint
android_media_SoundPool_SoundPoolImpl_load_FD(JNIEnv *env, jobject thiz, jobject fileDescriptor,
        jlong offset, jlong length, jint priority)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_load_FD");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return 0;
    return (jint) ap->load(jniGetFDFromFileDescriptor(env, fileDescriptor),
            int64_t(offset), int64_t(length), int(priority));
}

static jboolean
android_media_SoundPool_SoundPoolImpl_unload(JNIEnv *env, jobject thiz, jint sampleID) {
    ALOGV("android_media_SoundPool_SoundPoolImpl_unload\n");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return JNI_FALSE;
    return ap->unload(sampleID) ? JNI_TRUE : JNI_FALSE;
}

static jint
android_media_SoundPool_SoundPoolImpl_play(JNIEnv *env, jobject thiz, jint sampleID,
        jfloat leftVolume, jfloat rightVolume, jint priority, jint loop,
        jfloat rate)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_play\n");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return 0;
    return (jint) ap->play(sampleID, leftVolume, rightVolume, priority, loop, rate);
}

static void
android_media_SoundPool_SoundPoolImpl_pause(JNIEnv *env, jobject thiz, jint channelID)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_pause");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->pause(channelID);
}

static void
android_media_SoundPool_SoundPoolImpl_resume(JNIEnv *env, jobject thiz, jint channelID)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_resume");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->resume(channelID);
}

static void
android_media_SoundPool_SoundPoolImpl_autoPause(JNIEnv *env, jobject thiz)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_autoPause");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->autoPause();
}

static void
android_media_SoundPool_SoundPoolImpl_autoResume(JNIEnv *env, jobject thiz)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_autoResume");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->autoResume();
}

static void
android_media_SoundPool_SoundPoolImpl_stop(JNIEnv *env, jobject thiz, jint channelID)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_stop");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->stop(channelID);
}

static void
android_media_SoundPool_SoundPoolImpl_setVolume(JNIEnv *env, jobject thiz, jint channelID,
        jfloat leftVolume, jfloat rightVolume)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_setVolume");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->setVolume(channelID, (float) leftVolume, (float) rightVolume);
}

static void
android_media_SoundPool_SoundPoolImpl_setPriority(JNIEnv *env, jobject thiz, jint channelID,
        jint priority)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_setPriority");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->setPriority(channelID, (int) priority);
}

static void
android_media_SoundPool_SoundPoolImpl_setLoop(JNIEnv *env, jobject thiz, jint channelID,
        int loop)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_setLoop");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->setLoop(channelID, loop);
}

static void
android_media_SoundPool_SoundPoolImpl_setRate(JNIEnv *env, jobject thiz, jint channelID,
       jfloat rate)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_setRate");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap == NULL) return;
    ap->setRate(channelID, (float) rate);
}

static void android_media_callback(SoundPoolEvent event, SoundPool* soundPool, void* user)
{
    ALOGV("callback: (%d, %d, %d, %p, %p)", event.mMsg, event.mArg1, event.mArg2, soundPool, user);
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    env->CallStaticVoidMethod(fields.mSoundPoolClass, fields.mPostEvent, user, event.mMsg, event.mArg1, event.mArg2, NULL);
}

static jint
android_media_SoundPool_SoundPoolImpl_native_setup(JNIEnv *env, jobject thiz, jobject weakRef,
        jint maxChannels, jobject jaa)
{
    if (jaa == 0) {
        ALOGE("Error creating SoundPool: invalid audio attributes");
        return -1;
    }

    audio_attributes_t *paa = NULL;
    // read the AudioAttributes values
    paa = (audio_attributes_t *) calloc(1, sizeof(audio_attributes_t));
    const jstring jtags =
            (jstring) env->GetObjectField(jaa, javaAudioAttrFields.fieldFormattedTags);
    const char* tags = env->GetStringUTFChars(jtags, NULL);
    // copying array size -1, char array for tags was calloc'd, no need to NULL-terminate it
    strncpy(paa->tags, tags, AUDIO_ATTRIBUTES_TAGS_MAX_SIZE - 1);
    env->ReleaseStringUTFChars(jtags, tags);
    paa->usage = (audio_usage_t) env->GetIntField(jaa, javaAudioAttrFields.fieldUsage);
    paa->content_type =
            (audio_content_type_t) env->GetIntField(jaa, javaAudioAttrFields.fieldContentType);
    paa->flags = env->GetIntField(jaa, javaAudioAttrFields.fieldFlags);

    ALOGV("android_media_SoundPool_SoundPoolImpl_native_setup");
    SoundPool *ap = new SoundPool(maxChannels, paa);
    if (ap == NULL) {
        return -1;
    }

    // save pointer to SoundPool C++ object in opaque field in Java object
    env->SetLongField(thiz, fields.mNativeContext, (jlong) ap);

    // set callback with weak reference
    jobject globalWeakRef = env->NewGlobalRef(weakRef);
    ap->setCallback(android_media_callback, globalWeakRef);

    // audio attributes were copied in SoundPool creation
    free(paa);

    return 0;
}

static void
android_media_SoundPool_SoundPoolImpl_release(JNIEnv *env, jobject thiz)
{
    ALOGV("android_media_SoundPool_SoundPoolImpl_release");
    SoundPool *ap = MusterSoundPool(env, thiz);
    if (ap != NULL) {

        // release weak reference and clear callback
        jobject weakRef = (jobject) ap->getUserData();
        ap->setCallback(NULL, NULL);
        if (weakRef != NULL) {
            env->DeleteGlobalRef(weakRef);
        }

        // clear native context
        env->SetLongField(thiz, fields.mNativeContext, 0);
        delete ap;
    }
}

// ----------------------------------------------------------------------------

// Dalvik VM type signatures
static JNINativeMethod gMethods[] = {
    {   "_load",
        "(Ljava/lang/String;I)I",
        (void *)android_media_SoundPool_SoundPoolImpl_load_URL
    },
    {   "_load",
        "(Ljava/io/FileDescriptor;JJI)I",
        (void *)android_media_SoundPool_SoundPoolImpl_load_FD
    },
    {   "unload",
        "(I)Z",
        (void *)android_media_SoundPool_SoundPoolImpl_unload
    },
    {   "_play",
        "(IFFIIF)I",
        (void *)android_media_SoundPool_SoundPoolImpl_play
    },
    {   "pause",
        "(I)V",
        (void *)android_media_SoundPool_SoundPoolImpl_pause
    },
    {   "resume",
        "(I)V",
        (void *)android_media_SoundPool_SoundPoolImpl_resume
    },
    {   "autoPause",
        "()V",
        (void *)android_media_SoundPool_SoundPoolImpl_autoPause
    },
    {   "autoResume",
        "()V",
        (void *)android_media_SoundPool_SoundPoolImpl_autoResume
    },
    {   "stop",
        "(I)V",
        (void *)android_media_SoundPool_SoundPoolImpl_stop
    },
    {   "_setVolume",
        "(IFF)V",
        (void *)android_media_SoundPool_SoundPoolImpl_setVolume
    },
    {   "setPriority",
        "(II)V",
        (void *)android_media_SoundPool_SoundPoolImpl_setPriority
    },
    {   "setLoop",
        "(II)V",
        (void *)android_media_SoundPool_SoundPoolImpl_setLoop
    },
    {   "setRate",
        "(IF)V",
        (void *)android_media_SoundPool_SoundPoolImpl_setRate
    },
    {   "native_setup",
        "(Ljava/lang/Object;ILjava/lang/Object;)I",
        (void*)android_media_SoundPool_SoundPoolImpl_native_setup
    },
    {   "release",
        "()V",
        (void*)android_media_SoundPool_SoundPoolImpl_release
    }
};

static const char* const kClassPathName = "android/media/SoundPool$SoundPoolImpl";

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;
    jclass clazz;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed\n");
        return result;
    }
    assert(env != NULL);

    clazz = env->FindClass(kClassPathName);
    if (clazz == NULL) {
        ALOGE("Can't find %s", kClassPathName);
        return result;
    }

    fields.mNativeContext = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.mNativeContext == NULL) {
        ALOGE("Can't find SoundPoolImpl.mNativeContext");
        return result;
    }

    fields.mPostEvent = env->GetStaticMethodID(clazz, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");
    if (fields.mPostEvent == NULL) {
        ALOGE("Can't find android/media/SoundPoolImpl.postEventFromNative");
        return result;
    }

    // create a reference to class. Technically, we're leaking this reference
    // since it's a static object.
    fields.mSoundPoolClass = (jclass) env->NewGlobalRef(clazz);

    if (AndroidRuntime::registerNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods)) < 0)
        return result;

    // Get the AudioAttributes class and fields
    jclass audioAttrClass = env->FindClass(kAudioAttributesClassPathName);
    if (audioAttrClass == NULL) {
        ALOGE("Can't find %s", kAudioAttributesClassPathName);
        return result;
    }
    jclass audioAttributesClassRef = (jclass)env->NewGlobalRef(audioAttrClass);
    javaAudioAttrFields.fieldUsage = env->GetFieldID(audioAttributesClassRef, "mUsage", "I");
    javaAudioAttrFields.fieldContentType
                                   = env->GetFieldID(audioAttributesClassRef, "mContentType", "I");
    javaAudioAttrFields.fieldFlags = env->GetFieldID(audioAttributesClassRef, "mFlags", "I");
    javaAudioAttrFields.fieldFormattedTags =
            env->GetFieldID(audioAttributesClassRef, "mFormattedTags", "Ljava/lang/String;");
    env->DeleteGlobalRef(audioAttributesClassRef);
    if (javaAudioAttrFields.fieldUsage == NULL || javaAudioAttrFields.fieldContentType == NULL
            || javaAudioAttrFields.fieldFlags == NULL
            || javaAudioAttrFields.fieldFormattedTags == NULL) {
        ALOGE("Can't initialize AudioAttributes fields");
        return result;
    }

    /* success -- return valid version number */
    return JNI_VERSION_1_4;
}
