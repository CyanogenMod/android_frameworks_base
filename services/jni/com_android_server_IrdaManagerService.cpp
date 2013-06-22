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
    ALOGE("init_native called in IRDA jni");
    int err;
    hw_module_t* module;
    irda_device_t* dev = NULL;
    
    err = hw_get_module(IRDA_HARDWARE_MODULE_ID, (hw_module_t const**)&module);
    if (err == 0) {
        if (module->methods->open(module, "", ((hw_device_t**) &dev)) != 0)
	   return 0;
    }

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

static void write_native(JNIEnv *env, jobject clazz, int ptr, jbyteArray buffer)
{
    irda_device_t* dev = (irda_device_t*)ptr;
    jbyte* real_byte_array;
    int length;

    real_byte_array = env->GetByteArrayElements(buffer, NULL);

    ALOGE("Write_native called in IRDA jni");
    if (dev == NULL) {
        ALOGE("dev was null in IRA jni");
        return;
    }

    dev->write((char*) real_byte_array, env->GetArrayLength(buffer));

    env->ReleaseByteArrayElements(buffer, real_byte_array, 0);

}

static JNINativeMethod method_table[] = {
    { "init_native", "()I", (void*)init_native },
    { "finalize_native", "(I)V", (void*)finalize_native },
    { "write_native", "(I[B)V", (void*)write_native },
};

int register_android_server_IrdaManagerService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/IrdaManagerService",
            method_table, NELEM(method_table));

};

};
