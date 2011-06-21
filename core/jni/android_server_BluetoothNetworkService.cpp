/*
** Copyright 2008, The Android Open Source Project
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

#define LOG_TAG "BluetoothNetworkService.cpp"

#include "android_bluetooth_common.h"
#include "android_runtime/AndroidRuntime.h"
#include "JNIHelp.h"
#include "jni.h"
#include "utils/Log.h"
#include "utils/misc.h"

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#ifdef HAVE_BLUETOOTH
#include <dbus/dbus.h>
#endif

namespace android {

#ifdef HAVE_BLUETOOTH

typedef struct {
    JavaVM *vm;
    int envVer;
    DBusConnection *conn;
    jobject me;  // for callbacks to java
} native_data_t;

static native_data_t *nat = NULL;  // global native data

static Properties sink_properties[] = {
        {"State", DBUS_TYPE_STRING},
        {"Connected", DBUS_TYPE_BOOLEAN},
      };
#endif

/* Returns true on success (even if adapter is present but disabled).
 * Return false if dbus is down, or another serious error (out of memory)
*/
static bool initNative(JNIEnv* env, jobject object) {
    LOGV(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    nat = (native_data_t *)calloc(1, sizeof(native_data_t));
    if (NULL == nat) {
        LOGE("%s: out of memory!", __FUNCTION__);
        return false;
    }
    env->GetJavaVM( &(nat->vm) );
    nat->envVer = env->GetVersion();
    nat->me = env->NewGlobalRef(object);

    DBusError err;
    dbus_error_init(&err);
    dbus_threads_init_default();
    nat->conn = dbus_bus_get(DBUS_BUS_SYSTEM, &err);
    if (dbus_error_is_set(&err)) {
        LOGE("Could not get onto the system bus: %s", err.message);
        dbus_error_free(&err);
        return false;
    }
    dbus_connection_set_exit_on_disconnect(nat->conn, FALSE);

    const char *c_path = "/org/bluez/hci0";
    bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL,
                                        nat, c_path, "org.bluez.NetworkServer", "Register",
                                        DBUS_TYPE_STRING, "gn");
    //return ret ? JNI_TRUE : JNI_FALSE;

#endif  /*HAVE_BLUETOOTH*/
    return true;
}

static void cleanupNative(JNIEnv* env, jobject object) {
#ifdef HAVE_BLUETOOTH
    LOGV(__FUNCTION__);
    if (nat) {
        dbus_connection_close(nat->conn);
        env->DeleteGlobalRef(nat->me);
        free(nat);
        nat = NULL;
    }
#endif
}

static JNINativeMethod sMethods[] = {
    {"initNative", "()Z", (void *)initNative},
    {"cleanupNative", "()V", (void *)cleanupNative},
};

int register_android_server_BluetoothNetworkService(JNIEnv *env) {
    jclass clazz = env->FindClass("android/server/BluetoothNetworkService");
    if (clazz == NULL) {
        LOGE("Can't find android/server/BluetoothNetworkService");
        return -1;
    }

    return AndroidRuntime::registerNativeMethods(env,
                "android/server/BluetoothNetworkService", sMethods, NELEM(sMethods));
}

} /* namespace android */
