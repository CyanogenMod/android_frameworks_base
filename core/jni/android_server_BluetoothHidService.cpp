/*
** Copyright 2009 ISB Corporation
** Copyright (C) 2010 0xlab
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

#define LOG_TAG "BluetoothHidService.cpp"

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
static jmethodID method_onPropertyChanged;
static jmethodID method_onHidDeviceConnected;
static jmethodID method_onHidDeviceDisconnected;

typedef struct {
    JavaVM *vm;
    int envVer;
    DBusConnection *conn;
    jobject me;  // for callbacks to java
} native_data_t;

static native_data_t *nat = NULL;  // global native data
static Properties hid_properties[] = {
        {"Connected", DBUS_TYPE_BOOLEAN},
      };
static void onConnectHidDeviceResult(DBusMessage *msg, void *user, void *nat);
static void onDisconnectHidDeviceResult(DBusMessage *msg, void *user, void *nat);
#endif


/* Returns true on success (even if adapter is present but disabled).
 * Return false if dbus is down, or another serious error (out of memory)
*/
static bool initNative(JNIEnv* env, jobject object) {
    LOGD(__FUNCTION__);
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
#endif  /*HAVE_BLUETOOTH*/
    return true;
}

static void cleanupNative(JNIEnv* env, jobject object) {
#ifdef HAVE_BLUETOOTH
    LOGD(__FUNCTION__);
    if (nat) {
        dbus_connection_close(nat->conn);
        env->DeleteGlobalRef(nat->me);
        free(nat);
        nat = NULL;
    }
#endif
}

static jobjectArray getHidPropertiesNative(JNIEnv *env, jobject object,
                                            jstring path) {
#ifdef HAVE_BLUETOOTH
    LOGD(__FUNCTION__);
    if (nat) {
        DBusMessage *msg, *reply;
        DBusError err;
        dbus_error_init(&err);

        const char *c_path = env->GetStringUTFChars(path, NULL);
        reply = dbus_func_args_timeout(env,
                                   nat->conn, -1, c_path,
                                   "org.bluez.Input", "GetProperties",
                                   DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(path, c_path);
        if (!reply && dbus_error_is_set(&err)) {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, reply);
            return NULL;
        } else if (!reply) {
            LOGE("DBus reply is NULL in function %s", __FUNCTION__);
            return NULL;
        }
        DBusMessageIter iter;
        if (dbus_message_iter_init(reply, &iter))
            return parse_properties(env, &iter, (Properties *)&hid_properties,
                                 sizeof(hid_properties) / sizeof(Properties));
    }
#endif
    return NULL;
}

static jboolean connectHidDeviceNative(JNIEnv *env, jobject object, jstring path) {
#ifdef HAVE_BLUETOOTH
    LOGD(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);
        size_t path_sz = env->GetStringUTFLength(path) + 1;
        char *c_path_copy = (char *)malloc(path_sz);  // callback data
        strncpy(c_path_copy, c_path, path_sz);

        bool ret =
            dbus_func_args_async(env, nat->conn, -1, onConnectHidDeviceResult, 
				(void *)c_path_copy, nat,
				c_path, "org.bluez.Input",
				"Connect", DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(path, c_path);
        if (!ret) {
            free(c_path_copy);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

static jboolean disconnectHidDeviceNative(JNIEnv *env, jobject object,
                                     jstring path) {
#ifdef HAVE_BLUETOOTH
    LOGD(__FUNCTION__);
    if (nat) {
        const char *c_path = env->GetStringUTFChars(path, NULL);

        bool ret = dbus_func_args_async(env, nat->conn, -1, NULL, NULL, nat,
                                    c_path, "org.bluez.Input", "Disconnect",
                                    DBUS_TYPE_INVALID);
        LOGD("... path = %s", c_path);
        env->ReleaseStringUTFChars(path, c_path);
        return ret ? JNI_TRUE : JNI_FALSE;
    }
#endif
    return JNI_FALSE;
}


#ifdef HAVE_BLUETOOTH

static void onConnectHidDeviceResult(DBusMessage *msg, void *user, void *natData) {
    LOGD(__FUNCTION__);

    char *c_path = (char *)user;
    DBusError err;
    JNIEnv *env;

    if (nat->vm->GetEnv((void**)&env, nat->envVer) < 0) {
        LOGE("%s: error finding Env for our VM\n", __FUNCTION__);
        return;
    }

    dbus_error_init(&err);

    LOGD("... path = %s", c_path);
    if (dbus_set_error_from_message(&err, msg)) {
        LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);
        dbus_error_free(&err);
        env->CallVoidMethod(nat->me,
                            method_onHidDeviceDisconnected,
                            env->NewStringUTF(c_path));
        if (env->ExceptionCheck()) {
            LOGE("VM Exception occurred in native function %s (%s:%d)",
                 __FUNCTION__, __FILE__, __LINE__);
        }
    } else { // else Java callback is triggered by signal in hid_event_filter?
        env->CallVoidMethod(nat->me,
                            method_onHidDeviceConnected,
                            env->NewStringUTF(c_path));
    }

    free(c_path);
}

static void onDisconnectHidDeviceResult(DBusMessage *msg, void *user, void *natData) {
    LOGD(__FUNCTION__);

    char *c_path = (char *)user;
    DBusError err;
    JNIEnv *env;

    if (nat->vm->GetEnv((void**)&env, nat->envVer) < 0) {
        LOGE("%s: error finding Env for our VM\n", __FUNCTION__);
        return;
    }

    dbus_error_init(&err);

    LOGD("... path = %s", c_path);
    if (dbus_set_error_from_message(&err, msg)) {
        LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);
        if (strcmp(err.name, "org.bluez.Error.NotConnected") == 0) {
            // we were already disconnected, so report disconnect
            env->CallVoidMethod(nat->me,
                                method_onHidDeviceDisconnected,
                                env->NewStringUTF(c_path));
        } else {
            // Assume it is still connected
            env->CallVoidMethod(nat->me,
                                method_onHidDeviceConnected,
                                env->NewStringUTF(c_path));
        }
        dbus_error_free(&err);
        if (env->ExceptionCheck()) {
            LOGE("VM Exception occurred in native function %s (%s:%d)",
                 __FUNCTION__, __FILE__, __LINE__);
        }
    } else { // else Java callback is triggered by signal in hid_event_filter?
        env->CallVoidMethod(nat->me,
                            method_onHidDeviceDisconnected,
                            env->NewStringUTF(c_path));
    }
    free(c_path);
}

DBusHandlerResult hid_event_filter(DBusMessage *msg, JNIEnv *env) {
    DBusError err;
    LOGD("hid_event_filter...");
    if (!nat) {
        LOGE("... skipping %s\n", __FUNCTION__);
        LOGE("... ignored\n");
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    dbus_error_init(&err);

    if (dbus_message_get_type(msg) != DBUS_MESSAGE_TYPE_SIGNAL) {
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    DBusHandlerResult result = DBUS_HANDLER_RESULT_NOT_YET_HANDLED;

    if (dbus_message_is_signal(msg, "org.bluez.Input",
                                      "PropertyChanged")) {
        jobjectArray str_array =
                    parse_property_change(env, msg, (Properties *)&hid_properties,
                                sizeof(hid_properties) / sizeof(Properties));
        const char *c_path = dbus_message_get_path(msg);
        LOGD("received org.bluez.Input PropertyChanged...");
        jstring path = env->NewStringUTF(c_path);

        env->CallVoidMethod(nat->me,
                            method_onPropertyChanged,
                            path,
                            str_array);

        env->DeleteLocalRef(path);

        result = DBUS_HANDLER_RESULT_HANDLED;
        return result;
    } else {
        LOGV("... ignored");
    }
    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred while handling %s.%s (%s) in %s,"
             " leaving for VM",
             dbus_message_get_interface(msg), dbus_message_get_member(msg),
             dbus_message_get_path(msg), __FUNCTION__);
    }

    return result;
}
#endif


static JNINativeMethod sMethods[] = {
    {"initNative", "()Z", (void *)initNative},
    {"cleanupNative", "()V", (void *)cleanupNative},


    {"connectHidDeviceNative", "(Ljava/lang/String;)Z", (void*)connectHidDeviceNative},
    {"disconnectHidDeviceNative", "(Ljava/lang/String;)Z", (void*)disconnectHidDeviceNative},
    {"getHidPropertiesNative", "(Ljava/lang/String;)[Ljava/lang/Object;",
                                    (void *)getHidPropertiesNative},
};


int register_android_server_BluetoothHidService(JNIEnv *env) {
    jclass clazz = env->FindClass("android/server/BluetoothHidService");
    if (clazz == NULL) {
        LOGE("Can't find android/server/BluetoothHidService");
        return -1;
    }

#ifdef HAVE_BLUETOOTH
    method_onHidDeviceConnected = env->GetMethodID(clazz, "onHidDeviceConnected", "(Ljava/lang/String;)V");
    method_onHidDeviceDisconnected = env->GetMethodID(clazz, "onHidDeviceDisconnected", "(Ljava/lang/String;)V");
    method_onPropertyChanged = env->GetMethodID(clazz, "onPropertyChanged",
                                          "(Ljava/lang/String;[Ljava/lang/String;)V");
#endif

    return AndroidRuntime::registerNativeMethods(env,
                "android/server/BluetoothHidService", sMethods, NELEM(sMethods));
}


} /* namespace android */
