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

#define LOG_TAG "OmapMMLibrary"
#include "utils/Log.h"

#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <assert.h>
#include <stdio.h>

#include "jni.h"
#include <surfaceflinger/Surface.h>
#include <surfaceflinger/ISurface.h>
#include <binder/Parcel.h>
#include <utils/RefBase.h>
#include <media/mediaplayer.h>

using namespace android;

// ----------------------------------------------------------------------------
namespace android {
//friend class for Surface, so that it can extract ISurface
class OmapMMLibrary {
    public:
        void getISurface(const sp<Surface>& s) {
            mSurface =  s->getISurface();
            return;
        }
    sp<ISurface> mSurface;
    };
}

static OmapMMLibrary omapmmlib;
/*
 * Field/method IDs and class object references.
 *
 * You should not need to store the JNIEnv pointer in here.  It is
 * thread-specific and will be passed back in on every call.
 */
struct fields_t{
    jclass      omapMMLibraryClass;
    jfieldID    jniInt;
    jfieldID    mysurface;
    /* actually in android.view.Surface XXX */
    jfieldID    mysurface_native;
    jfieldID    mediaplayer;
    jfieldID    mediaplayer_native;
};

static fields_t fields;

// ----------------------------------------------------------------------------

/*
 * Helper function to throw an arbitrary exception.
 *
 * Takes the exception class name, a format string, and one optional integer
 * argument (useful for including an error code, perhaps from errno).
 */
static void jniThrowException(JNIEnv* env, const char* ex, const char* fmt,
    int data) {
        if (jclass cls = env->FindClass(ex)) {
            if (fmt != NULL) {
                char msg[1000];
                snprintf(msg, sizeof(msg), fmt, data);
                env->ThrowNew(cls, msg);
            } else {
                env->ThrowNew(cls, NULL);
            }
        /*
         * This is usually not necessary -- local references are released
         * automatically when the native code returns to the VM.  It's
         * required if the code doesn't actually return, e.g. it's sitting
         * in a native event loop.
         */
        env->DeleteLocalRef(cls);
    }
}
/**
  Initialization steps:
  1. init fields
**/
static void OmapMMLibrary_native_init(JNIEnv* env, jclass clazz1) {
    static const char* const kClassName =
        "com/ti/omap/omap_mm_library/OmapMMLibrary";
    jclass clazz;

    /* look up the class */
    clazz = env->FindClass(kClassName);
    if (clazz == NULL) {
        LOGE("Can't find class %s\n", kClassName);
        return;
    }

       fields.mysurface = env->GetFieldID(clazz, "mSurface", "Landroid/view/Surface;");
    if (fields.mysurface == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MMLibrary.mSurface", 0);
        return;
    }

    jclass mysurface = env->FindClass("android/view/Surface");
    if (mysurface == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/view/Surface", 0);
        return;
    }

    fields.mysurface_native = env->GetFieldID(mysurface, "mNativeSurface", "I");
    if (fields.mysurface_native == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find Surface.mSurface", 0);
        return;
    }

    fields.mediaplayer = env->GetFieldID(clazz, "mMediaPlayer", "Landroid/media/MediaPlayer;");
    if (fields.mediaplayer == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/media/MediaPlayer", 0);
        return;
    }

    jclass mp = env->FindClass("android/media/MediaPlayer");
    if (mp == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/media/MediaPlayer", 0);
        return;
    }

    fields.mediaplayer_native = env->GetFieldID(mp, "mNativeContext", "I");
    if (fields.mediaplayer_native == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MediaPlayer.mNativeContext", 0);
        return;
    }


    return;
}

/**
  Deinitialization steps:
  1. do nothing for now
**/
static void OmapMMLibrary_deinit(JNIEnv* env, jclass clazz) {
    return;
}


/**
* a method to set the requested Display Id.
*
* Input: integer between 0 and 3
* 0 - PRIMARY DISPLAY
* 1 - SECONDARY DISPLAY
* 2 - HDTV
* 3 - pico DLP
* these enums should be in sync with overlay enums
**/
static void OmapMMLibrary_setDisplayId(JNIEnv* env, jclass clazz, int displayId) {
    LOGD("displayId[%d]", displayId);
    jobject myplayer = env->GetObjectField(clazz, fields.mediaplayer);
    if (myplayer != NULL) { //media player would be null for switch usecases
        MediaPlayer* const p = (MediaPlayer*)env->GetIntField(myplayer, fields.mediaplayer_native);
        if (p) {
            if (displayId != 0) {
                p->requestVideoCloneMode(1);
            }
            else {
                p->requestVideoCloneMode(0);
            }
        }
    }
    if (omapmmlib.mSurface != NULL) {
        omapmmlib.mSurface->setDisplayId(displayId);
    }
    return;
}

/**
* a method to set the ISurface for requested Surface.
* Surface was already set in JAVA class method.
*
**/
static void OmapMMLibrary_setVideoISurface(JNIEnv* env, jclass clazz) {
    jobject mysurface = env->GetObjectField(clazz, fields.mysurface);
    if (mysurface != NULL) {
        sp<Surface> native_surface = (Surface*)env->GetIntField(mysurface, fields.mysurface_native);
        //LOGD("surface=%p (id=%d)",native_surface.get(), native_surface->ID());
        if (native_surface.get() != NULL) {
            omapmmlib.getISurface(native_surface);
            //msurface is ready now
        }
    }
    return;
}

// ----------------------------------------------------------------------------

/*
 * Array of methods.
 *
 * Each entry has three fields: the name of the method, the method
 * signature, and a pointer to the native implementation.
 */
static const JNINativeMethod gMethods[] = {
    { "native_init","()V", (void*)OmapMMLibrary_native_init },
    { "deinit","()V", (void*)OmapMMLibrary_deinit },
    { "setVideoISurface","()V", (void*)OmapMMLibrary_setVideoISurface },
    { "setDisplayId","(I)V", (void*)OmapMMLibrary_setDisplayId },
};

/*
 * Do some (slow-ish) lookups now and save the results.
 *
 * Returns 0 on success.
 */
static int cacheIds(JNIEnv* env, jclass clazz) {
    /*
     * Save the class in case we want to use it later.  Because this is a
     * reference to the Class object, we need to convert it to a JNI global
     * reference.
     */
    fields.omapMMLibraryClass = (jclass) env->NewGlobalRef(clazz);
    if (clazz == NULL) {
        LOGE("Can't create new global ref\n");
        return -1;
    }

    return 0;
}

/*
 * Explicitly register all methods for our class.
 *
 * While we're at it, cache some class references and method/field IDs.
 *
 * Returns 0 on success.
 */
static int registerMethods(JNIEnv* env) {
    static const char* const kClassName =
        "com/ti/omap/omap_mm_library/OmapMMLibrary";
    jclass clazz;

    /* look up the class */
    clazz = env->FindClass(kClassName);
    if (clazz == NULL) {
        LOGE("Can't find class %s\n", kClassName);
        return -1;
    }

    /* register all the methods */
    if (env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(gMethods[0])) != JNI_OK)
    {
        LOGE("Failed registering methods for %s\n", kClassName);
        return -1;
    }

    /* fill out the rest of the ID cache */
    return cacheIds(env, clazz);
}

extern int UiCloningService_registerMethods(JNIEnv* env);

// ----------------------------------------------------------------------------

/*
 * This is called by the VM when the shared library is first loaded.
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);

    if (registerMethods(env) != 0) {
        LOGE("ERROR: PlatformLibrary native registration failed\n");
        goto bail;
    }

    if (UiCloningService_registerMethods(env) != 0) {
        LOGE("ERROR: PlatformLibrary native registration for UiCloningService failed\n");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}




