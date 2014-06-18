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

#include <jni.h>
#include <JNIHelp.h>

#include <sensorservice/SensorService.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/misc.h>

namespace android {

void* sensorInit(void *arg)
{
    ALOGI("System server: starting sensor init.\n");
    // Start the sensor service
    SensorService::instantiate();
    ALOGI("System server: sensor init done.\n");
    return NULL;
}

static void android_server_SystemServer_nativeInit(JNIEnv* env, jobject clazz) {
    char propBuf[PROPERTY_VALUE_MAX];
    pthread_t sensor_init_thread;

    property_get("system_init.startsensorservice", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        // We are safe to move this to a new thread because
        // Android frame work has taken care to check whether the
        // service is started or not before using it.
        pthread_create( &sensor_init_thread, NULL, &sensorInit, NULL);
    }
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "nativeInit", "()V", (void*) android_server_SystemServer_nativeInit },
};

int register_android_server_SystemServer(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/SystemServer",
            gMethods, NELEM(gMethods));
}

}; // namespace android
