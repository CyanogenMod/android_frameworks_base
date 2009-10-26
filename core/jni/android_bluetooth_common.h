/*
** Copyright 2006, The Android Open Source Project
** Copyright (c) 2009, Code Aurora Forum, Inc. All rights reserved.
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

#ifndef ANDROID_BLUETOOTH_COMMON_H
#define ANDROID_BLUETOOTH_COMMON_H

// Set to 0 to enable verbose, debug, and/or info bluetooth logging
#define LOG_NDEBUG 1
#define LOG_NDDEBUG 1
#define LOG_NIDEBUG 1

#include "jni.h"
#include "utils/Log.h"

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <sys/poll.h>

#ifdef HAVE_BLUETOOTH
#include <dbus/dbus.h>
#include <bluetooth/bluetooth.h>
#endif

namespace android {

#ifdef HAVE_BLUETOOTH
#define BLUEZ_DBUS_BASE_SVC       "org.bluez"
#define BLUEZ_DBUS_BASE_PATH      "/org/bluez"
#define BLUEZ_DBUS_BASE_IFC       "org.bluez"

/*
 * OBEXD DBUS API
 */
#define OBEXD_DBUS_BASE_SVC                 "org.openobex"
#define OBEXD_DBUS_BASE_IFC                 "org.openobex"
#define OBEXD_DBUS_BASE_ERROR               "org.openobex.Error"
#define OBEXD_DBUS_SGNL_RULE(ifc, path)     "type='signal',interface='"(ifc)"'" \
                                            ",path='"(path)"'"

/* OBEXD Client DBUS API */
#define OBEXD_DBUS_CLIENT_SVC               "org.openobex.client"

#define OBEXD_DBUS_CLIENT_IFC               OBEXD_DBUS_BASE_IFC".Client"
#define OBEXD_DBUS_CLIENT_PATH              "/"
#define OBEXD_DBUS_CLIENT_SENDFILES         "SendFiles"
#define OBEXD_DBUS_CLIENT_PULLCARD          "PullBusinessCard"
#define OBEXD_DBUS_CLIENT_EXCHANGE          "ExchangeBusinessCards"
#define OBEXD_DBUS_CLIENT_CREATE            "CreateSession"

#define OBEXD_DBUS_CLIENT_SESSION_IFC       OBEXD_DBUS_BASE_IFC".Session"
#define OBEXD_DBUS_CLIENT_SESSION_GETPROPS  "GetProperties"
#define OBEXD_DBUS_CLIENT_SESSION_ASSIGN    "AssignAgent"
#define OBEXD_DBUS_CLIENT_SESSION_RELEASE   "ReleaseAgent"
#define OBEXD_DBUS_CLIENT_SESSION_CLOSE     "Close"

#define OBEXD_DBUS_CLIENT_FTP_IFC           OBEXD_DBUS_BASE_IFC".FileTransfer"
#define OBEXD_DBUS_CLIENT_FTP_CHANGE_FOLDER "ChangeFolder"
#define OBEXD_DBUS_CLIENT_FTP_CREATE_FOLDER "CreateFolder"
#define OBEXD_DBUS_CLIENT_FTP_LIST_FOLDER   "ListFolder"
#define OBEXD_DBUS_CLIENT_FTP_GET_FILE      "GetFile"
#define OBEXD_DBUS_CLIENT_FTP_PUT_FILE      "PutFile"
#define OBEXD_DBUS_CLIENT_FTP_COPY_FILE     "CopyFile"
#define OBEXD_DBUS_CLIENT_FTP_MOVE_FILE     "MoveFile"
#define OBEXD_DBUS_CLIENT_FTP_DELETE        "Delete"

#define OBEXD_DBUS_CLIENT_TRANS_IFC         OBEXD_DBUS_BASE_IFC".Transfer"
#define OBEXD_DBUS_CLIENT_TRANS_GETPROPS    "GetProperties"
#define OBEXD_DBUS_CLIENT_TRANS_CANCEL      "Cancel"

#define OBEXD_DBUS_CLIENT_AGENT_IFC         OBEXD_DBUS_BASE_IFC".Agent"
#define OBEXD_DBUS_CLIENT_AGENT_RELEASE     "Release"
#define OBEXD_DBUS_CLIENT_AGENT_REQUEST     "Request"
#define OBEXD_DBUS_CLIENT_AGENT_PROGRESS    "Progress"
#define OBEXD_DBUS_CLIENT_AGENT_COMPLETE    "Complete"
#define OBEXD_DBUS_CLIENT_AGENT_ERROR       "Error"

/* OBEXD Server DBUS API */
#define OBEXD_DBUS_SRV_SVC                          "org.openobex"

#define OBEXD_DBUS_SRV_MGR_IFC                      OBEXD_DBUS_BASE_IFC".Manager"
#define OBEXD_DBUS_SRV_MGR_PATH                     "/"
#define OBEXD_DBUS_SRV_MGR_REG_AGENT                "RegisterAgent"
#define OBEXD_DBUS_SRV_MGR_UNREG_AGENT              "UnregisterAgent"

#define OBEXD_DBUS_SRV_MGR_SGNL_RULE \
        OBEXD_DBUS_SGNL_RULE(OBEXD_DBUS_SRV_MGR_IFC, OBEXD_DBUS_SRV_MGR_PATH)
#define OBEXD_DBUS_SRV_MGR_SGNL_FTP_SESS_CREATED    "SessionCreated"
#define OBEXD_DBUS_SRV_MGR_SGNL_FTP_SESS_REMOVED    "SessionRemoved"
#define OBEXD_DBUS_SRV_MGR_SGNL_FTP_TRANS_STARTED   "FTPTransferStarted"
#define OBEXD_DBUS_SRV_MGR_SGNL_FTP_TRANS_COMPLETED "FTPTransferCompleted"
#define OBEXD_DBUS_SRV_MGR_SGNL_OPP_SESS_CREATED    "OPPSessionCreated"
#define OBEXD_DBUS_SRV_MGR_SGNL_OPP_SESS_REMOVED    "OPPSessionRemoved"
#define OBEXD_DBUS_SRV_MGR_SGNL_OPP_TRANS_STARTED   "TransferStarted"
#define OBEXD_DBUS_SRV_MGR_SGNL_OPP_TRANS_COMPLETED "TransferCompleted"

#define OBEXD_DBUS_SRV_TRANS_IFC                    OBEXD_DBUS_BASE_IFC".Transfer"
#define OBEXD_DBUS_SRV_TRANS_CANCEL                 "Cancel"

#define OBEXD_DBUS_SRV_TRANS_SGNL_PROGRESS          "Progress"

#define OBEXD_DBUS_SRV_SESS_IFC                     OBEXD_DBUS_BASE_IFC".Session"
#define OBEXD_DBUS_SRV_SESS_GETPROPS                "GetProperties"

#define OBEXD_DBUS_SRV_AGENT_IFC                    OBEXD_DBUS_BASE_IFC".Agent"
#define OBEXD_DBUS_SRV_AGENT_AUTHORIZE              "Authorize"
#define OBEXD_DBUS_SRV_AGENT_CANCEL                 "Cancel"

/* OBEXD Errors*/
#define OBEXD_DBUS_ERROR_CANCELLED     OBEXD_DBUS_BASE_ERROR".Cancelled"
#define OBEXD_DBUS_ERROR_AGENT_EXIST   OBEXD_DBUS_BASE_ERROR".AlreadyExists"
#define OBEXD_DBUS_ERROR_REJECTED      OBEXD_DBUS_BASE_ERROR".Rejected"

#define ANDROID_DBUS_AGENT_BASE_PATH "/android/bluetooth"
#define ANDROID_PASSKEY_AGENT_PATH   ANDROID_DBUS_AGENT_BASE_PATH"/Agent"
#define ANDROID_OPPCLIENT_AGENT_PATH ANDROID_DBUS_AGENT_BASE_PATH"/OPPClient"
#define ANDROID_OPPSRV_AGENT_PATH    ANDROID_DBUS_AGENT_BASE_PATH"/OPPServer"
#define ANDROID_FTPCLIENT_AGENT_PATH ANDROID_DBUS_AGENT_BASE_PATH"/FTPClient"

// It would be nicer to retrieve this from bluez using GetDefaultAdapter,
// but this is only possible when the adapter is up (and hcid is running).
// It is much easier just to hardcode bluetooth adapter to hci0
#define BLUETOOTH_ADAPTER_HCI_NUM 0
#define BLUEZ_ADAPTER_OBJECT_NAME BLUEZ_DBUS_BASE_PATH "/hci0"

#define BTADDR_SIZE 18   // size of BT address character array (including null)

// size of the dbus event loops pollfd structure, hopefully never to be grown
#define DEFAULT_INITIAL_POLLFD_COUNT 8

typedef struct {
    void (*user_cb)(DBusMessage *, void *, void *);
    void *user;
    void *nat;
    JNIEnv *env;
} dbus_async_call_t;

jfieldID get_field(JNIEnv *env,
                   jclass clazz,
                   const char *member,
                   const char *mtype);

// LOGE and free a D-Bus error
// Using #define so that __FUNCTION__ resolves usefully
#define LOG_AND_FREE_DBUS_ERROR_WITH_MSG(err, msg) \
    {   LOGE("%s: D-Bus error in %s: %s (%s)", __FUNCTION__, \
        dbus_message_get_member((msg)), (err)->name, (err)->message); \
         dbus_error_free((err)); }
#define LOG_AND_FREE_DBUS_ERROR(err) \
    {   LOGE("%s: D-Bus error: %s (%s)", __FUNCTION__, \
        (err)->name, (err)->message); \
        dbus_error_free((err)); }

struct event_loop_native_data_t {
    DBusConnection *conn;

    /* protects the thread */
    pthread_mutex_t thread_mutex;
    pthread_t thread;
    /* our comms socket */
    /* mem for the list of sockets to listen to */
    struct pollfd *pollData;
    int pollMemberCount;
    int pollDataSize;
    /* mem for matching set of dbus watch ptrs */
    DBusWatch **watchData;
    /* pair of sockets for event loop control, Reader and Writer */
    int controlFdR;
    int controlFdW;
    /* our vm and env Version for future env generation */
    JavaVM *vm;
    int envVer;
    /* reference to our java self */
    jobject me;
};

dbus_bool_t dbus_func_args_async_valist(JNIEnv *env,
                                        DBusConnection *conn,
                                        int timeout_ms,
                                        void (*reply)(DBusMessage *, void *),
                                        void *user,
                                        const char *dest,
                                        const char *path,
                                        const char *ifc,
                                        const char *func,
                                        int first_arg_type,
                                        va_list args);

dbus_bool_t dbus_func_args_async(JNIEnv *env,
                                 DBusConnection *conn,
                                 int timeout_ms,
                                 void (*reply)(DBusMessage *, void *, void *),
                                 void *user,
                                 void *nat,
                                 const char *dest,
                                 const char *path,
                                 const char *ifc,
                                 const char *func,
                                 int first_arg_type,
                                 ...);

DBusMessage * dbus_func_args(JNIEnv *env,
                             DBusConnection *conn,
                             const char *dest,
                             const char *path,
                             const char *ifc,
                             const char *func,
                             int first_arg_type,
                             ...);

DBusMessage * dbus_func_args_error(JNIEnv *env,
                                   DBusConnection *conn,
                                   DBusError *err,
                                   const char *dest,
                                   const char *path,
                                   const char *ifc,
                                   const char *func,
                                   int first_arg_type,
                                   ...);

DBusMessage * dbus_func_args_timeout(JNIEnv *env,
                                     DBusConnection *conn,
                                     int timeout_ms,
                                     const char *dest,
                                     const char *path,
                                     const char *ifc,
                                     const char *func,
                                     int first_arg_type,
                                     ...);

DBusMessage * dbus_func_args_timeout_valist(JNIEnv *env,
                                            DBusConnection *conn,
                                            int timeout_ms,
                                            DBusError *err,
                                            const char *dest,
                                            const char *path,
                                            const char *ifc,
                                            const char *func,
                                            int first_arg_type,
                                            va_list args);

jint dbus_returns_int32(JNIEnv *env, DBusMessage *reply);
jint dbus_returns_uint32(JNIEnv *env, DBusMessage *reply);
jstring dbus_returns_string(JNIEnv *env, DBusMessage *reply);
jboolean dbus_returns_boolean(JNIEnv *env, DBusMessage *reply);
jobjectArray dbus_returns_array_of_strings(JNIEnv *env, DBusMessage *reply);
jbyteArray dbus_returns_array_of_bytes(JNIEnv *env, DBusMessage *reply);

bool dbus_append_ss_dict_entry(DBusMessageIter *array_iter, const char *key,
                               const char *value);

void get_bdaddr(const char *str, bdaddr_t *ba);
void get_bdaddr_as_string(const bdaddr_t *ba, char *str);

bool debug_no_encrypt();

#endif
} /* namespace android */

#endif/*ANDROID_BLUETOOTH_COMMON_H*/
