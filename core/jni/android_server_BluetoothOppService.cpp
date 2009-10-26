/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
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

#define LOG_TAG "BluetoothOppService.cpp"

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
static jmethodID method_onObexAuthorize;
static jmethodID method_onObexAuthorizeCancel;
static jmethodID method_onObexRequest;
static jmethodID method_onObexProgress;
static jmethodID method_onObexTransferComplete;
static jmethodID method_onSendFilesComplete;
static jmethodID method_onPullBusinessCardComplete;

typedef struct {
    DBusConnection *conn;
    jobject me;  // for callbacks to java
    /* our vm and env Version for future env generation */
    JavaVM *vm;
    int envVer;
} native_data_t;

static native_data_t *nat = NULL;  // global native data

extern void dbus_func_args_async_callback(DBusPendingCall *call, void *data);
static void onSendFilesComplete(DBusMessage *msg, void *user, void *nat_cb);
static void onPullBusinessCardComplete(DBusMessage *msg, void *user, void *nat_cb);
static DBusHandlerResult oppclient_agent_release_handler(DBusConnection *conn,
                                                         DBusMessage *msg);
static DBusHandlerResult oppclient_agent_request_handler(DBusConnection *conn,
                                                         DBusMessage *msg);
static DBusHandlerResult oppclient_agent_progress_handler(DBusConnection *conn,
                                                          DBusMessage *msg);
static DBusHandlerResult oppclient_agent_complete_handler(DBusConnection *conn,
                                                          DBusMessage *msg);
static DBusHandlerResult oppclient_agent_error_handler(DBusConnection *conn,
                                                       DBusMessage *msg);
static DBusHandlerResult oppserver_agent_authorize_handler(DBusConnection *conn,
                                                           DBusMessage *msg);
static DBusHandlerResult oppserver_agent_cancel_handler(DBusConnection *conn,
                                                        DBusMessage *msg);

#endif

/* Returns true on success (even if adapter is present but disabled).
 * Return false if dbus is down, or another serious error (out of memory)
 */
static bool initNative(JNIEnv* env, jobject object) {
    LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    nat = (native_data_t *)calloc(1, sizeof(native_data_t));
    if (NULL == nat) {
        LOGE("%s: out of memory!", __FUNCTION__);
        return false;
    }
    nat->me = env->NewGlobalRef(object);

    env->GetJavaVM( &(nat->vm) );
    nat->envVer = env->GetVersion();

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
#endif  /*HAVE_BLUETOOTH*/
    return true;
}

static void cleanupNative(JNIEnv* env, jobject object) {
#ifdef HAVE_BLUETOOTH
    LOGI(__FUNCTION__);
    if (nat) {
         dbus_connection_close(nat->conn);
         env->DeleteGlobalRef(nat->me);
         free(nat);
         nat = NULL;
    }
#endif
}

static jboolean sendFilesNative(JNIEnv *env, jobject object,
                                jstring address,
                                jobjectArray txFilenames) {
    LOGI(__FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    if (nat) {
        DBusMessage *msg = dbus_message_new_method_call(OBEXD_DBUS_CLIENT_SVC,
                                                        OBEXD_DBUS_CLIENT_PATH,
                                                        OBEXD_DBUS_CLIENT_IFC,
                                                        OBEXD_DBUS_CLIENT_SENDFILES);

        if (msg == NULL) {
            return JNI_FALSE;
        }

        DBusMessageIter iter, array, dict_entry;

        dbus_message_iter_init_append(msg, &iter);

        dbus_message_iter_open_container(&iter, DBUS_TYPE_ARRAY,
                                         DBUS_DICT_ENTRY_BEGIN_CHAR_AS_STRING
                                         DBUS_TYPE_STRING_AS_STRING
                                         DBUS_TYPE_VARIANT_AS_STRING
                                         DBUS_DICT_ENTRY_END_CHAR_AS_STRING,
                                         &array);

        const char *c_address = env->GetStringUTFChars(address, NULL);
        LOGV("%s Destination Address = %s\n", __FUNCTION__, c_address);

        dbus_append_ss_dict_entry(&array, "Destination", c_address);
        dbus_message_iter_close_container(&iter, &array);

        jsize i = 0;
        jsize length = 0;

        length = env->GetArrayLength((jarray) txFilenames);

        LOGV("Number of files = %d", length);

        jstring java_string[length];
        const char **filename = (const char **) calloc((size_t) length,
                                                       sizeof(filename));

        if (filename != NULL) {
            for (i = 0; i < length; i++) {
                java_string[i] = (jstring) env->GetObjectArrayElement(txFilenames, i);

                filename[i] = env->GetStringUTFChars(java_string[i], NULL);

                LOGV("Filename[%d] = %s", i, filename[i]);
            }

            const char *path = ANDROID_OPPCLIENT_AGENT_PATH;

            dbus_message_append_args(msg, DBUS_TYPE_ARRAY, DBUS_TYPE_STRING,
                                     &filename, length, DBUS_TYPE_OBJECT_PATH,
                                     &path, DBUS_TYPE_INVALID);

            dbus_async_call_t *pending = NULL;

            pending = (dbus_async_call_t *) malloc(sizeof(dbus_async_call_t));

            if (pending) {
                DBusPendingCall *call;

                char *context_address =  (char *) calloc(BTADDR_SIZE,
                                                         sizeof(char));

                strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback

                pending->env = env;
                pending->user_cb = onSendFilesComplete;
                pending->user = context_address;
                pending->nat = nat;

                dbus_bool_t reply = dbus_connection_send_with_reply(nat->conn,
                                                                    msg,
                                                                    &call,
                                                                    10*1000);
                if (reply == TRUE) {
                    dbus_pending_call_set_notify(call,
                                                 dbus_func_args_async_callback,
                                                 pending,
                                                 NULL);

                    result = JNI_TRUE;
                } else {
                    LOGE("Failed to Send D-BUS message");
                    free(pending);
                }
            }

            for (i = 0; i < length; i++) {
                env->ReleaseStringUTFChars(java_string[i], filename[i]);
            }

            free(filename);
        }

        env->ReleaseStringUTFChars(address, c_address);
        dbus_message_unref(msg);
    }

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;
}

static jboolean pullBusinessCardNative(JNIEnv *env, jobject object,
                                       jstring address, jstring rxFilename) {
    LOGI(__FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    if (nat) {
        DBusMessage *msg = dbus_message_new_method_call(OBEXD_DBUS_CLIENT_SVC,
                                                        OBEXD_DBUS_CLIENT_PATH,
                                                        OBEXD_DBUS_CLIENT_IFC,
                                                        OBEXD_DBUS_CLIENT_PULLCARD);

        if (msg == NULL) {
            return JNI_FALSE;
        }

        DBusMessageIter iter, array, dict_entry;

        dbus_message_iter_init_append(msg, &iter);

        dbus_message_iter_open_container(&iter, DBUS_TYPE_ARRAY,
                                         DBUS_DICT_ENTRY_BEGIN_CHAR_AS_STRING
                                         DBUS_TYPE_STRING_AS_STRING
                                         DBUS_TYPE_VARIANT_AS_STRING
                                         DBUS_DICT_ENTRY_END_CHAR_AS_STRING,
                                         &array);

        const char *c_address = env->GetStringUTFChars(address, NULL);
        LOGV("%s Destination Address = %s\n", __FUNCTION__, c_address);

        dbus_append_ss_dict_entry(&array, "Destination", c_address);
        dbus_message_iter_close_container(&iter, &array);

        const char *filename = env->GetStringUTFChars(rxFilename, NULL);
        LOGV("%s Filename = %s\n", __FUNCTION__, filename);

        dbus_message_append_args(msg, DBUS_TYPE_STRING, &filename,
                                 DBUS_TYPE_INVALID);

        dbus_async_call_t *pending = NULL;

        pending = (dbus_async_call_t *) malloc(sizeof(dbus_async_call_t));

        if (pending) {
            DBusPendingCall *call;

            char *context_address = (char *) calloc(BTADDR_SIZE, sizeof(char));

            strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback

            pending->env = env;
            pending->user_cb = onPullBusinessCardComplete;
            pending->user = context_address;
            pending->nat = nat;

            dbus_bool_t reply = dbus_connection_send_with_reply(nat->conn,
                                                                msg,
                                                                &call,
                                                                10*1000);
            if (reply == TRUE) {
                dbus_pending_call_set_notify(call,
                                             dbus_func_args_async_callback,
                                             pending,
                                             NULL);

                result = JNI_TRUE;
            } else {
                free(pending);
            }
        }

        env->ReleaseStringUTFChars(rxFilename, filename);
        env->ReleaseStringUTFChars(address, c_address);
        dbus_message_unref(msg);
    }

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;
}

static jboolean cancelTransferNative(JNIEnv *env, jobject object,
                                     jstring transfer, jboolean isServer) {
    LOGI(__FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    if (nat && transfer) {
        const char *c_transfer = env->GetStringUTFChars(transfer, NULL);
        DBusMessage *reply = NULL;
        DBusError err;

        LOGV("Transfer = %s\n", c_transfer);

        dbus_error_init(&err);

        if (isServer == JNI_TRUE) {
            reply = dbus_func_args_error(env, nat->conn, &err,
                                         OBEXD_DBUS_SRV_SVC,
                                         c_transfer,
                                         OBEXD_DBUS_SRV_TRANS_IFC,
                                         OBEXD_DBUS_SRV_TRANS_CANCEL,
                                         DBUS_TYPE_INVALID);
        } else {
            reply = dbus_func_args_error(env, nat->conn, &err,
                                         OBEXD_DBUS_CLIENT_SVC,
                                         c_transfer,
                                         OBEXD_DBUS_CLIENT_TRANS_IFC,
                                         OBEXD_DBUS_CLIENT_TRANS_CANCEL,
                                         DBUS_TYPE_INVALID);
        }

        if (reply) {
            dbus_message_unref(reply);
            result = JNI_TRUE;
        } else {
            LOG_AND_FREE_DBUS_ERROR(&err);
        }

        env->ReleaseStringUTFChars(transfer, c_transfer);
    }

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;
}

static jboolean obexAuthorizeCompleteNative(JNIEnv *env, jobject object,
                                            jboolean accept, jstring filename,
                                            jint message) {
    LOGI(__FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    if (nat) {
        DBusMessage *reply;
        DBusMessage *msg = (DBusMessage *) message;
        bool error;
        const char *c_filename;

        LOGV("Authorization: accept = %d", accept);

        if (accept == JNI_TRUE) {
            reply = dbus_message_new_method_return(msg);

            error = FALSE;
        } else {
            reply = dbus_message_new_error(msg,
                                           OBEXD_DBUS_ERROR_CANCELLED,
                                           NULL);

            error = TRUE;
        }

        if (reply != NULL) {
            if (error == FALSE) {
                c_filename = env->GetStringUTFChars(filename, NULL);

                dbus_message_append_args(reply,
                                         DBUS_TYPE_STRING, &c_filename,
                                         DBUS_TYPE_INVALID);

                LOGV("Authorization: filename = %s", c_filename);
            }

            dbus_connection_send(nat->conn, reply, NULL);

            if (error == FALSE) {
                env->ReleaseStringUTFChars(filename, c_filename);
            }

            dbus_message_unref(reply);
            dbus_message_unref(msg);

            result = JNI_TRUE;
        } else {
            LOGE("%s: Cannot create message reply to return filename to "
                 "D-Bus\n", __FUNCTION__);

            dbus_message_unref(msg);

            result = JNI_FALSE;
        }
    }

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;
}

static jobject obexTransferGetPropertiesNative(JNIEnv *env, jobject object,
                                               jstring transfer) {
    LOGI(__FUNCTION__);
    jobject result = NULL;
#ifdef HAVE_BLUETOOTH
    if (nat) {
        const char *c_transfer = env->GetStringUTFChars(transfer, NULL);

        LOGV("%s: Transfer = %s", __FUNCTION__, c_transfer);

        DBusMessage *reply = NULL;

        reply = dbus_func_args(env, nat->conn, OBEXD_DBUS_CLIENT_SVC,
                              c_transfer, OBEXD_DBUS_CLIENT_TRANS_IFC,
                              OBEXD_DBUS_CLIENT_TRANS_GETPROPS,
                              DBUS_TYPE_INVALID);

        env->ReleaseStringUTFChars(transfer, c_transfer);

        if (reply == NULL) {
            LOGE("%s:%d Cannot create message to D-Bus", __FUNCTION__, __LINE__);
            result = NULL;

            goto Done;
        }

        DBusMessageIter iter, array;
        const char *name = NULL;
        const char *filename = NULL;
        uint64_t size = 0;
        bool pass;

        pass = dbus_message_iter_init(reply, &iter);

        if (!pass)
        {
            LOGE("%s: Iter init fails", __FUNCTION__);
            return NULL;
        }

        dbus_message_iter_recurse(&iter, &array);

        while (dbus_message_iter_get_arg_type(&array) == DBUS_TYPE_DICT_ENTRY) {
            const char *dict_key;
            DBusMessageIter dict_entry, dict_variant;

            dbus_message_iter_recurse(&array, &dict_entry);

            dbus_message_iter_get_basic(&dict_entry, &dict_key);

            dbus_message_iter_next(&dict_entry);

            dbus_message_iter_recurse(&dict_entry, &dict_variant);

            int32_t type;

            type = dbus_message_iter_get_arg_type(&dict_variant);

            if ((type == DBUS_TYPE_STRING) || (DBUS_TYPE_INT64)) {
                if (!strncmp(dict_key, "Name", sizeof("Name"))) {
                    dbus_message_iter_get_basic(&dict_variant, &name);
                    LOGV("%s: Name = %s", __FUNCTION__, name);
                } else if (!strncmp(dict_key, "Size", sizeof("Size"))) {
                    dbus_message_iter_get_basic(&dict_variant, &size);
                    LOGV("%s: Size = %d", __FUNCTION__, size);
                } else if (!strncmp(dict_key, "Filename", sizeof("Filename"))) {
                    dbus_message_iter_get_basic(&dict_variant, &filename);
                    LOGV("%s: Filename = %s", __FUNCTION__, filename);
                }
            }

            dbus_message_iter_next(&array);
        }

        dbus_message_unref(reply);

        jclass clazz =
        env->FindClass("android/server/BluetoothOppService$TransferProperties");

        if (clazz == NULL) {
            LOGE("%s:%d Cannot FindClass", __FUNCTION__, __LINE__);
            result = NULL;

            goto Done;
        }

        jmethodID constructor =
        env->GetMethodID(clazz,"<init>", "(Landroid/server/BluetoothOppService;"
                         "Ljava/lang/String;ILjava/lang/String;)V");

        if (constructor == NULL) {
            LOGE("%s:%d Cannot Get Constructor", __FUNCTION__, __LINE__);
            result = NULL;

            goto Done;
        }

        result = env->NewObject(clazz, constructor, object,
                                env->NewStringUTF(name), (jint) size,
                                env->NewStringUTF(filename));
    }
#endif
Done:
    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }

    return result;
}

#ifdef HAVE_BLUETOOTH
static void onSendFilesComplete(DBusMessage *msg, void *user, void *nat_cb) {
    LOGI(__FUNCTION__);

    char *c_address = (char *)user;
    DBusError err;
    jboolean is_error = JNI_FALSE;
    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    dbus_error_init(&err);

    LOGV("... address = %s", c_address);
    if (dbus_set_error_from_message(&err, msg)) {
        LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

        dbus_error_free(&err);

        is_error = JNI_TRUE;
    }

    jstring address = env->NewStringUTF(c_address);

    env->CallVoidMethod(nat->me,
                        method_onSendFilesComplete,
                        address, is_error);

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }

    env->DeleteLocalRef(address);

    free(c_address);
}

static void onPullBusinessCardComplete(DBusMessage *msg, void *user, void *nat_cb) {
    LOGI(__FUNCTION__);

    char *c_address = (char *)user;
    DBusError err;
    jboolean is_error = JNI_FALSE;
    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    dbus_error_init(&err);

    LOGV("... address = %s", c_address);
    if (dbus_set_error_from_message(&err, msg)) {
        LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

        dbus_error_free(&err);

        is_error = JNI_TRUE;
    }

    jstring address = env->NewStringUTF(c_address);

    env->CallVoidMethod(nat->me,
                        method_onPullBusinessCardComplete,
                        address, is_error);

    if (env->ExceptionCheck()) {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }

    env->DeleteLocalRef(address);

    free(c_address);
}

DBusHandlerResult opp_event_filter(DBusMessage *msg, JNIEnv *env) {
    LOGI(__FUNCTION__);

    DBusError err;

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

    if (dbus_message_is_signal(msg, OBEXD_DBUS_SRV_MGR_IFC,
                               OBEXD_DBUS_SRV_MGR_SGNL_OPP_SESS_CREATED)) {
        char *c_session;
        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_OBJECT_PATH, &c_session,
                                  DBUS_TYPE_INVALID)) {
            LOGV("... Session Created = %s", c_session);
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }

        result = DBUS_HANDLER_RESULT_HANDLED;
     } else if (dbus_message_is_signal(msg, OBEXD_DBUS_SRV_MGR_IFC,
                                       OBEXD_DBUS_SRV_MGR_SGNL_OPP_SESS_REMOVED)) {
        char *c_session;
        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_OBJECT_PATH, &c_session,
                                  DBUS_TYPE_INVALID)) {
            LOGV("... Session Removed = %s", c_session);
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }

        result = DBUS_HANDLER_RESULT_HANDLED;
    } else if (dbus_message_is_signal(msg, OBEXD_DBUS_SRV_MGR_IFC,
                                      OBEXD_DBUS_SRV_MGR_SGNL_OPP_TRANS_STARTED)) {

        char *c_transfer;

        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_OBJECT_PATH, &c_transfer,
                                  DBUS_TYPE_INVALID)) {
            LOGV("... Transfer Started = %s", c_transfer);
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }

        result = DBUS_HANDLER_RESULT_HANDLED;
    } else if (dbus_message_is_signal(msg, OBEXD_DBUS_SRV_MGR_IFC,
                                      OBEXD_DBUS_SRV_MGR_SGNL_OPP_TRANS_COMPLETED)) {
        const char *c_transfer;
        dbus_bool_t c_success;

        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_OBJECT_PATH, &c_transfer,
                                  DBUS_TYPE_BOOLEAN, &c_success,
                                  DBUS_TYPE_INVALID)) {
            LOGV("... Transfer Completed = %s  Success = %d", c_transfer,
                c_success);

            jstring transfer = env->NewStringUTF(c_transfer);
            jboolean success = c_success ? JNI_TRUE : JNI_FALSE;

            env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                                transfer, success, NULL);

            env->DeleteLocalRef(transfer);
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }

        result = DBUS_HANDLER_RESULT_HANDLED;
    } else if (dbus_message_is_signal(msg, OBEXD_DBUS_SRV_TRANS_IFC,
                                      OBEXD_DBUS_SRV_TRANS_SGNL_PROGRESS)) {
        int32_t total;
        int32_t transferred;

        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_INT32, &total,
                                  DBUS_TYPE_INT32, &transferred,
                                  DBUS_TYPE_INVALID)) {
            const char *c_path = NULL;

            c_path = dbus_message_get_path(msg);

            if (c_path == NULL){
                LOGE("Function %s:%d Unable to get path",
                         __FUNCTION__, __LINE__);
            } else {
                LOGV("... Transfer: %s Total = %d Transferred = %d", c_path,
                     total, transferred);

                jstring path = env->NewStringUTF(c_path);

                env->CallVoidMethod(nat->me, method_onObexProgress,
                                    path, (jint) transferred);

                env->DeleteLocalRef(path);
            }
        } else {
            LOG_AND_FREE_DBUS_ERROR_WITH_MSG(&err, msg);
        }

        result = DBUS_HANDLER_RESULT_HANDLED;
    }

    if (result == DBUS_HANDLER_RESULT_NOT_YET_HANDLED) {
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

// Called by dbus during WaitForAndDispatchEventNative()
DBusHandlerResult oppclient_agent(DBusConnection *conn,
                                  DBusMessage *msg,
                                  void *data) {
    LOGI(__FUNCTION__);

    if (dbus_message_get_type(msg) != DBUS_MESSAGE_TYPE_METHOD_CALL) {
        LOGE("%s: not interested (not a method call).", __FUNCTION__);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("%s: Received method %s:%s", __FUNCTION__,
        dbus_message_get_interface(msg), dbus_message_get_member(msg));

    if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                    OBEXD_DBUS_CLIENT_AGENT_RELEASE)) {

        return oppclient_agent_release_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_REQUEST)) {

        return oppclient_agent_request_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_PROGRESS)) {

        return oppclient_agent_progress_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_COMPLETE)) {

        return oppclient_agent_complete_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_ERROR)) {

        return oppclient_agent_error_handler(conn, msg);

    } else {
        LOGV("... ignored");
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

// Called by dbus during WaitForAndDispatchEventNative()
DBusHandlerResult oppserver_agent(DBusConnection *conn,
                                  DBusMessage *msg,
                                  void *data) {
    LOGI(__FUNCTION__);

    if (dbus_message_get_type(msg) != DBUS_MESSAGE_TYPE_METHOD_CALL) {
        LOGE("%s: not interested (not a method call).", __FUNCTION__);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("%s: Received method %s:%s", __FUNCTION__,
        dbus_message_get_interface(msg), dbus_message_get_member(msg));

    if (dbus_message_is_method_call(msg, OBEXD_DBUS_SRV_AGENT_IFC,
                                    OBEXD_DBUS_SRV_AGENT_AUTHORIZE)) {

        return oppserver_agent_authorize_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_SRV_AGENT_IFC,
                                           OBEXD_DBUS_SRV_AGENT_CANCEL)) {

        return oppserver_agent_cancel_handler(conn, msg);

    } else {
        LOGV("... ignored");
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}


static DBusHandlerResult oppclient_agent_release_handler(DBusConnection *conn,
                                                         DBusMessage *msg)
{
    LOGI(__FUNCTION__);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_RELEASE);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply) {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        LOGV("No longer the obexd Client agent!");

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult oppclient_agent_request_handler(DBusConnection *conn,
                                                         DBusMessage *msg) {
    LOGI(__FUNCTION__);

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    const char *c_transfer;

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                            DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_REQUEST);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s", c_transfer);

    jstring transfer = env->NewStringUTF(c_transfer);

    jstring filename = (jstring) env->CallObjectMethod(nat->me,
                                                       method_onObexRequest,
                                                       transfer);

    env->DeleteLocalRef(transfer);

    DBusMessage *reply;
    const char *c_filename;

    if (filename != NULL) {
        reply = dbus_message_new_method_return(msg);
    } else {
        reply = dbus_message_new_error(msg,
                                       OBEXD_DBUS_ERROR_CANCELLED,
                                       NULL);
    }

    if (reply != NULL) {
        if (filename != NULL) {
            c_filename = env->GetStringUTFChars(filename, NULL);

            dbus_message_append_args(reply,
                                     DBUS_TYPE_STRING, &c_filename,
                                     DBUS_TYPE_INVALID);
        }

        dbus_connection_send(nat->conn, reply, NULL);

        if (filename != NULL) {
            env->ReleaseStringUTFChars(filename, c_filename);
            env->DeleteLocalRef(filename);
        }

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    } else if (filename != NULL) {
        env->DeleteLocalRef(filename);
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult oppclient_agent_progress_handler(DBusConnection *conn,
                                                   DBusMessage *msg) {
    LOGI(__FUNCTION__);

    const char *c_transfer;
    uint64_t transferred;

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_UINT64, &transferred,
                               DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_PROGRESS);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s\n... bytes transferred = %d", c_transfer,
         (int32_t) transferred);

    jstring transfer = env->NewStringUTF(c_transfer);

    env->CallVoidMethod(nat->me, method_onObexProgress,
                        transfer, (jint) transferred);

    env->DeleteLocalRef(transfer);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply) {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult oppclient_agent_complete_handler(DBusConnection *conn,
                                                   DBusMessage *msg) {
    LOGI(__FUNCTION__);

    const char *c_transfer;

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_COMPLETE);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s", c_transfer);

    jstring transfer = env->NewStringUTF(c_transfer);

    env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                        transfer, JNI_TRUE, NULL);

    env->DeleteLocalRef(transfer);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply) {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult oppclient_agent_error_handler(DBusConnection *conn,
                                                       DBusMessage *msg) {
    LOGI(__FUNCTION__);

    const char *c_transfer, *c_message;

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_STRING, &c_message,
                               DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_ERROR);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s\n... message = %s", c_transfer, c_message);

    jstring transfer = env->NewStringUTF(c_transfer);
    jstring message = env->NewStringUTF(c_message);

    env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                        transfer, JNI_FALSE, message);

    env->DeleteLocalRef(transfer);
    env->DeleteLocalRef(message);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply) {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult oppserver_agent_authorize_handler(DBusConnection *conn,
                                                           DBusMessage *msg) {
    LOGI(__FUNCTION__);

    const char *c_transfer, *c_address, *c_filename, *c_type;
    int32_t length, time;

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_STRING, &c_address,
                               DBUS_TYPE_STRING, &c_filename,
                               DBUS_TYPE_STRING, &c_type,
                               DBUS_TYPE_INT32, &length,
                               DBUS_TYPE_INT32, &time,
                               DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
             OBEXD_DBUS_SRV_AGENT_AUTHORIZE);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s\n... address = %s\n... filename = %s\n... type ="
         "%s\n... length = %d\n... time = %d\n", c_transfer, c_address, c_filename,
         c_type, length, time);

    dbus_message_ref(msg);

    jstring transfer = env->NewStringUTF(c_transfer);
    jstring address = env->NewStringUTF(c_address);
    jstring filename = env->NewStringUTF(c_filename);
    jstring type = env->NewStringUTF(c_type);

    env->CallBooleanMethod(nat->me, method_onObexAuthorize,
                           transfer, address, filename, type,
                           (jint) length,
                           (jint) msg);

    env->DeleteLocalRef(transfer);
    env->DeleteLocalRef(address);
    env->DeleteLocalRef(filename);
    env->DeleteLocalRef(type);

    return DBUS_HANDLER_RESULT_HANDLED;
}

static DBusHandlerResult oppserver_agent_cancel_handler(DBusConnection *conn,
                                                        DBusMessage *msg) {
    LOGI(__FUNCTION__);

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
             OBEXD_DBUS_SRV_AGENT_CANCEL);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    const char *c_path;

    c_path = dbus_message_get_path(msg);

    if (!c_path){
        LOGE("Function %s:%d Unable to get path",
                 __FUNCTION__, __LINE__);
    } else {
        LOGV("... Authorize for transfer %s cancelled by OBEX", c_path);

        jstring path = env->NewStringUTF(c_path);

        env->CallBooleanMethod(nat->me, method_onObexAuthorizeCancel,
                               path);

        env->DeleteLocalRef(path);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}


#endif

static JNINativeMethod sMethods[] = {
    {"initNative", "()Z", (void *)initNative},
    {"cleanupNative", "()V", (void *)cleanupNative},

    /* Obexd 0.8 API */
    {
        "sendFilesNative",
        "(Ljava/lang/String;[Ljava/lang/String;)Z",
        (void *) sendFilesNative
    },
    {
        "pullBusinessCardNative",
        "(Ljava/lang/String;Ljava/lang/String;)Z",
        (void *) pullBusinessCardNative
    },
    {
        "cancelTransferNative",
        "(Ljava/lang/String;Z)Z",
        (void*) cancelTransferNative
    },
    {
        "obexAuthorizeCompleteNative",
        "(ZLjava/lang/String;I)Z",
        (void *) obexAuthorizeCompleteNative
    },
    {
        "obexTransferGetPropertiesNative",
        "(Ljava/lang/String;)Landroid/server/BluetoothOppService$TransferProperties;",
        (void *) obexTransferGetPropertiesNative
    },
};

int register_android_server_BluetoothOppService(JNIEnv *env) {
    LOGI(__FUNCTION__);

    jclass clazz = env->FindClass("android/server/BluetoothOppService");
    if (clazz == NULL) {
        LOGE("Can't find android/server/BluetoothOppService");
        return -1;
    }

#ifdef HAVE_BLUETOOTH
    method_onSendFilesComplete = env->GetMethodID(clazz, "onSendFilesComplete",
            "(Ljava/lang/String;Z)V");

    method_onPullBusinessCardComplete = env->GetMethodID(clazz, "onPullBusinessCardComplete",
            "(Ljava/lang/String;Z)V");

    method_onObexRequest = env->GetMethodID(clazz, "onObexRequest",
            "(Ljava/lang/String;)Ljava/lang/String;");

    method_onObexProgress = env->GetMethodID(clazz, "onObexProgress",
            "(Ljava/lang/String;I)V");

    method_onObexTransferComplete = env->GetMethodID(clazz, "onObexTransferComplete",
            "(Ljava/lang/String;ZLjava/lang/String;)V");

    method_onObexAuthorize = env->GetMethodID(clazz, "onObexAuthorize",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)Z");

    method_onObexAuthorizeCancel = env->GetMethodID(clazz, "onObexAuthorizeCancel",
            "(Ljava/lang/String;)Z");
#endif

    return AndroidRuntime::registerNativeMethods(env,
            "android/server/BluetoothOppService", sMethods, NELEM(sMethods));
}

} /* namespace android */
