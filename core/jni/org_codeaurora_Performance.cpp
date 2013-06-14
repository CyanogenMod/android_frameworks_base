/*
 * Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of The Linux Foundation nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOG_TAG "ANDR-PERF-JNI"

#include "jni.h"
#include "JNIHelp.h"
#include <android_runtime/AndroidRuntime.h>

#include <dlfcn.h>
#include <limits.h>
#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>

#define LIBRARY_PATH_PREFIX	"/system/lib/"

namespace android
{

// ----------------------------------------------------------------------------
static int  (*cpu_setoptions)(int, int)             = NULL;
static int  (*perf_lock_acq)(int, int, int[], int)  = NULL;
static int  (*perf_lock_rel)(int)                   = NULL;
static void *dlhandle                               = NULL;

// ----------------------------------------------------------------------------

static void
org_codeaurora_performance_native_init()
{
    const char *rc;
    void (*init)(void);
    char buf[PROPERTY_VALUE_MAX];
    int len;

    /* Retrieve name of vendor extension library */
    if (property_get("ro.vendor.extension_library", buf, NULL) <= 0) {
        return;
    }

    /* Sanity check - ensure */
    buf[PROPERTY_VALUE_MAX-1] = '\0';
    if ((strncmp(buf, LIBRARY_PATH_PREFIX, sizeof(LIBRARY_PATH_PREFIX) - 1) != 0)
        ||
        (strstr(buf, "..") != NULL)) {
        return;
    }

    dlhandle = dlopen(buf, RTLD_NOW | RTLD_LOCAL);
    if (dlhandle == NULL) {
        return;
    }

    dlerror();

    cpu_setoptions = (int (*) (int, int))dlsym(dlhandle, "perf_cpu_setoptions");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }

    perf_lock_acq = (int (*) (int, int, int[], int))dlsym(dlhandle, "perf_lock_acq");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }

    perf_lock_rel = (int (*) (int))dlsym(dlhandle, "perf_lock_rel");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }

    init = (void (*) ())dlsym(dlhandle, "libqc_opt_init");
    if ((rc = dlerror()) != NULL) {
        goto cleanup;
    }
    (*init)();
    return;

cleanup:
    cpu_setoptions = NULL;
    perf_lock_acq  = NULL;
    perf_lock_rel  = NULL;
    if (dlhandle) {
        dlclose(dlhandle);
        dlhandle = NULL;
    }
}

static void
org_codeaurora_performance_native_deinit(JNIEnv *env, jobject clazz)
{
    void (*deinit)(void);

    if (dlhandle) {
        cpu_setoptions = NULL;
        perf_lock_acq  = NULL;
        perf_lock_rel  = NULL;

        deinit = (void (*) ())dlsym(dlhandle, "libqc_opt_deinit");
        if (deinit) {
            (*deinit)();
        }

        dlclose(dlhandle);
        dlhandle       = NULL;
    }
}

static jint
org_codeaurora_performance_native_cpu_setoptions(JNIEnv *env, jobject clazz,
                                                 jint reqtype, jint reqvalue)
{
    if (cpu_setoptions) {
        return (*cpu_setoptions)(reqtype, reqvalue);
    }
    return 0;
}

static jint
org_codeaurora_performance_native_perf_lock_acq(JNIEnv *env, jobject clazz, jint handle, jint duration, jintArray list)
{
    jint listlen = env->GetArrayLength(list);
    jint buf[listlen];
    int i=0;
    env->GetIntArrayRegion(list, 0, listlen, buf);

    if (dlhandle == NULL) {
        org_codeaurora_performance_native_init();
    }
    if (perf_lock_acq) {
        return (*perf_lock_acq)(handle, duration, buf, listlen);
    }
    return 0;
}

static jint
org_codeaurora_performance_native_perf_lock_rel(JNIEnv *env, jobject clazz, jint handle)
{
    if (dlhandle == NULL) {
        org_codeaurora_performance_native_init();
    }
    if (perf_lock_rel) {
        return (*perf_lock_rel)(handle);
    }
    return 0;
}
// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"native_cpu_setoptions", "(II)I",                 (int *)org_codeaurora_performance_native_cpu_setoptions},
    {"native_perf_lock_acq",  "(II[I)I",               (int *)org_codeaurora_performance_native_perf_lock_acq},
    {"native_perf_lock_rel",  "(I)I",                  (int *)org_codeaurora_performance_native_perf_lock_rel},
    {"native_deinit",         "()V",                   (void *)org_codeaurora_performance_native_deinit},
};


int register_org_codeaurora_Performance(JNIEnv *env)
{
    org_codeaurora_performance_native_init();

    return AndroidRuntime::registerNativeMethods(env,
            "org/codeaurora/Performance", gMethods, NELEM(gMethods));
}

}   // namespace android
