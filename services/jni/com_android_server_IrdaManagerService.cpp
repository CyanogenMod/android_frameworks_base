/*
 * Copyright (C) 2013 CyanogenMod Project
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

#define LOG_TAG "IrdaManagerService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/hardware.h>
#include <hardware/irda.h>

#include <stdio.h>

namespace android
{

irda_device_t* irda_dev;

static jint init_native(JNIEnv *env, jobject clazz)
{
    int err;
    hw_module_t* module;
    irda_device_t* dev = NULL;

    err = hw_get_module(IRDA_HARDWARE_MODULE_ID, (hw_module_t const**)&module);
    if (err == 0) {
        ALOGI("Got IRDA module.");
        if (module->methods->open(module, "", ((hw_device_t**) &dev)) != 0) {
            ALOGE("Unable to open IRDA device.");
            return 0;
        }
    }
    else {
        ALOGE("Could not get IRDA HAL module.");
    }
    ALOGI("Opened IRDA module.");
    return (jint)dev;
}

static void finalize_native(JNIEnv *env, jobject clazz, int ptr)
{
    irda_device_t* dev = (irda_device_t*)ptr;

    if (dev == NULL) {
        return;
    }

    free(dev);
}

static void send_ircode_native(JNIEnv *env, jobject clazz, int ptr, jbyteArray buffer)
{
    ALOGI("Sending IR Code.");
    irda_device_t* dev = (irda_device_t*)ptr;
    jbyte* transmitBuffer;

    transmitBuffer = env->GetByteArrayElements(buffer, NULL);

    if (dev == NULL) {
        ALOGE("dev was null in IRDA jni");
        return;
    }

    dev->send_ircode((char*) transmitBuffer, env->GetArrayLength(buffer));

    env->ReleaseByteArrayElements(buffer, transmitBuffer, 0);

}

static JNINativeMethod method_table[] = {
    { "init_native", "()I", (void*)init_native },
    { "finalize_native", "(I)V", (void*)finalize_native },
    { "send_ircode_native", "(I[B)V", (void*)send_ircode_native },
};

int register_android_server_IrdaManagerService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/IrdaManagerService",
            method_table, NELEM(method_table));

};

};
