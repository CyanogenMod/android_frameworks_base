/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

#define LOG_TAG "ActTriggerJNI"

#include "jni.h"
#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>

#include <dlfcn.h>
#include <limits.h>
#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>

#define LIBRARY_PATH_PREFIX "/vendor/lib/"

namespace android
{

// ----------------------------------------------------------------------------
/*
 * Stuct containing handle to dynamically loaded lib as well as function
 * pointers to key interfaces.
 */
typedef struct dlLibHandler {
    void *dlhandle;
    void (*startActivity)(const char *, int *);
    void (*resumeActivity)(const char *);
    void (*pauseActivity)(const char *);
    void (*stopActivity)(const char *);
    void (*animationScalesCheck)(const char *, int, float *);
    void (*networkOptsCheck)(int, int, const char *);
    void (*init)(void);
    void (*deinit)(void);
    void (*startProcessActivity)(const char *, int);
    const char *dlname;
}dlLibHandler;

/*
 * Array of dlhandlers
 * library -both handlers for Start and Resume events.
 */
static dlLibHandler mDlLibHandlers[] = {
    {NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     "ro.vendor.at_library"},
    {NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
     "ro.vendor.gt_library"},
};

static size_t gTotalNumLibs = 0;
// ----------------------------------------------------------------------------

static void
com_android_internal_app_ActivityTrigger_native_at_init()
{
    const char *rc;
    char buf[PROPERTY_VALUE_MAX];
    bool errored = false;
    size_t numlibs = 0;

    gTotalNumLibs = numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);

    for(size_t i = 0; i < numlibs; i++) {
        errored = false;

        /* Retrieve name of vendor library */
        if (property_get(mDlLibHandlers[i].dlname, buf, NULL) <= 0) {
            continue;
        }

        /* Sanity check - ensure */
        buf[PROPERTY_VALUE_MAX-1] = '\0';
        if (strstr(buf, "/") != NULL) {
            continue;
        }

        mDlLibHandlers[i].dlhandle = dlopen(buf, RTLD_NOW | RTLD_LOCAL);
        if (mDlLibHandlers[i].dlhandle == NULL) {
            continue;
        }

        dlerror();

        *(void **) (&mDlLibHandlers[i].startActivity) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_start");
        if ((rc = dlerror()) != NULL) {
            errored = true;
        }

        if (!errored) {
            *(void **) (&mDlLibHandlers[i].resumeActivity) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_resume");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].pauseActivity) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_pause");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].stopActivity) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_stop");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].init) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_init");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].startProcessActivity) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_process_start");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].animationScalesCheck) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_animationScalesCheck");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (!errored) {
            *(void **) (&mDlLibHandlers[i].networkOptsCheck) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_networkOptsCheck");
            if ((rc = dlerror()) != NULL) {
                errored = true;
            }
        }
        if (errored) {
            mDlLibHandlers[i].startActivity  = NULL;
            mDlLibHandlers[i].resumeActivity = NULL;
            mDlLibHandlers[i].pauseActivity  = NULL;
            mDlLibHandlers[i].stopActivity = NULL;
            mDlLibHandlers[i].startProcessActivity = NULL;
            mDlLibHandlers[i].animationScalesCheck = NULL;
            mDlLibHandlers[i].networkOptsCheck = NULL;
            if (mDlLibHandlers[i].dlhandle) {
                dlclose(mDlLibHandlers[i].dlhandle);
                mDlLibHandlers[i].dlhandle = NULL;
            }
            gTotalNumLibs = 0;
        } else {
            (*mDlLibHandlers[i].init)();
        }
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_deinit(JNIEnv *env, jobject clazz)
{
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);

    for(size_t i = 0; i < numlibs; i++) {
        if (mDlLibHandlers[i].dlhandle) {
            mDlLibHandlers[i].startActivity  = NULL;
            mDlLibHandlers[i].resumeActivity = NULL;
            mDlLibHandlers[i].pauseActivity  = NULL;
            mDlLibHandlers[i].stopActivity = NULL;
            mDlLibHandlers[i].startProcessActivity = NULL;
            mDlLibHandlers[i].animationScalesCheck = NULL;
            mDlLibHandlers[i].networkOptsCheck = NULL;

            *(void **) (&mDlLibHandlers[i].deinit) = dlsym(mDlLibHandlers[i].dlhandle, "activity_trigger_deinit");
            if (mDlLibHandlers[i].deinit) {
                (*mDlLibHandlers[i].deinit)();
            }

            dlclose(mDlLibHandlers[i].dlhandle);
            mDlLibHandlers[i].dlhandle = NULL;
        }
    }
    gTotalNumLibs = 0;
}

static void
com_android_internal_app_ActivityTrigger_native_at_startProcessActivity(JNIEnv *env, jobject clazz, jstring process, jint pid)
{
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);
    const char *actStr = env->GetStringUTFChars(process, NULL);
    for(size_t i = 0; i < numlibs; i++){
        if(mDlLibHandlers[i].startProcessActivity && process && actStr) {
            (*mDlLibHandlers[i].startProcessActivity)(actStr, pid);
        }
    }
    env->ReleaseStringUTFChars(process, actStr);
}

static jint
com_android_internal_app_ActivityTrigger_native_at_startActivity(JNIEnv *env, jobject clazz, jstring activity, jint flags)
{
    int activiyFlags = flags;
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);
    for(size_t i = 0; i < numlibs; i++){
        if(mDlLibHandlers[i].startActivity && activity) {
            const char *actStr = env->GetStringUTFChars(activity, NULL);
            if (actStr) {
                (*mDlLibHandlers[i].startActivity)(actStr, &activiyFlags);
                env->ReleaseStringUTFChars(activity, actStr);
            }
        }
    }
    return activiyFlags;
}

static void
com_android_internal_app_ActivityTrigger_native_at_resumeActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);

    for(size_t i = 0; i < numlibs; i++){
        if(mDlLibHandlers[i].resumeActivity && activity) {
            const char *actStr = env->GetStringUTFChars(activity, NULL);
            if (actStr) {
                (*mDlLibHandlers[i].resumeActivity)(actStr);
                env->ReleaseStringUTFChars(activity, actStr);
            }
        }
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_pauseActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    for(size_t i = 0; i < gTotalNumLibs; i++){
        if(mDlLibHandlers[i].pauseActivity && activity) {
            const char *actStr = env->GetStringUTFChars(activity, NULL);
            if ( NULL != actStr) {
                (*mDlLibHandlers[i].pauseActivity)(actStr);
                env->ReleaseStringUTFChars(activity, actStr);
            }
        }
    }
}

static void
com_android_internal_app_ActivityTrigger_native_at_stopActivity(JNIEnv *env, jobject clazz, jstring activity)
{
    for(size_t i = 0; i < gTotalNumLibs; i++){
        if(mDlLibHandlers[i].stopActivity && activity) {
            const char *actStr = env->GetStringUTFChars(activity, NULL);
            if (NULL != actStr) {
                (*mDlLibHandlers[i].stopActivity)(actStr);
                env->ReleaseStringUTFChars(activity, actStr);
            }
        }
    }
}

static jfloat
com_android_internal_app_ActivityTrigger_native_at_animationScalesCheck(JNIEnv *env, jobject clazz, jstring activity, jint scaleType)
{
    int type = scaleType;
    float scaleValue = -1.0f;
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);
    for (size_t i = 0; i < numlibs; i++) {
        if (mDlLibHandlers[i].animationScalesCheck && activity) {
            const char *actStr = env->GetStringUTFChars(activity, NULL);
            if (actStr) {
                (*mDlLibHandlers[i].animationScalesCheck)(actStr, type, &scaleValue);
                env->ReleaseStringUTFChars(activity, actStr);
            }
        }
    }
    return scaleValue;
}

static void
com_android_internal_app_ActivityTrigger_native_at_networkOptsCheck(JNIEnv *env, jobject clazz, jint flag, jint netType, jstring packageName)
{
    size_t numlibs = sizeof (mDlLibHandlers) / sizeof (*mDlLibHandlers);

    for (size_t i = 0; i < numlibs; i++) {
        if (mDlLibHandlers[i].networkOptsCheck && packageName) {
            const char *actStr = env->GetStringUTFChars(packageName, NULL);
            if (actStr) {
                (*mDlLibHandlers[i].networkOptsCheck)(flag, netType, actStr);
                env->ReleaseStringUTFChars(packageName, actStr);
            }
        }
    }
}
// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"native_at_startActivity",  "(Ljava/lang/String;I)I", (void *)com_android_internal_app_ActivityTrigger_native_at_startActivity},
    {"native_at_resumeActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_resumeActivity},
    {"native_at_pauseActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_pauseActivity},
    {"native_at_stopActivity", "(Ljava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_stopActivity},
    {"native_at_deinit",         "()V",                   (void *)com_android_internal_app_ActivityTrigger_native_at_deinit},
    {"native_at_startProcessActivity", "(Ljava/lang/String;I)V", (void *)com_android_internal_app_ActivityTrigger_native_at_startProcessActivity},
    {"native_at_animationScalesCheck", "(Ljava/lang/String;I)F", (void *)com_android_internal_app_ActivityTrigger_native_at_animationScalesCheck},
    {"native_at_networkOptsCheck", "(IILjava/lang/String;)V", (void *)com_android_internal_app_ActivityTrigger_native_at_networkOptsCheck},
};


int register_com_android_internal_app_ActivityTrigger(JNIEnv *env)
{
    com_android_internal_app_ActivityTrigger_native_at_init();

    return AndroidRuntime::registerNativeMethods(env,
            "com/android/internal/app/ActivityTrigger", gMethods, NELEM(gMethods));
}

}   // namespace android