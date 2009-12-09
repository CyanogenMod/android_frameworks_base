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
#define LOG_TAG "BluetoothFTPService.cpp"

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

static jmethodID method_onCreateSessionComplete;
static jmethodID method_onChangeFolderComplete;
static jmethodID method_onCreateFolderComplete;
static jmethodID method_onListFolderComplete;
static jmethodID method_onGetFileComplete;
static jmethodID method_onPutFileComplete;
static jmethodID method_onDeleteComplete;
static jmethodID method_onObexRequest;
static jmethodID method_onObexProgress;
static jmethodID method_onObexTransferComplete;
static jmethodID method_onObexSessionClosed;


typedef struct {
    DBusConnection *conn;
    jobject me;  // for callbacks to java
    /* our vm and env Version for future env generation */
    JavaVM *vm;
    int envVer;
} native_data_t;

typedef struct {
    char *session;
    char *sourcefile;
    char *targetfile;
    char *folder;
} callback_data_t;

static native_data_t *nat = NULL;  // global native data

extern void dbus_func_args_async_callback(DBusPendingCall *call, void *data);
extern DBusHandlerResult ftpclient_agent(DBusConnection *conn,
                                  DBusMessage *msg,
                                  void *data);

static const DBusObjectPathVTable ftpclient_agent_vtable = {
    NULL, ftpclient_agent, NULL, NULL, NULL, NULL
};


static void onCreateSessionComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  char* c_address = (char *)user;
  DBusError err;
  jboolean is_error = JNI_FALSE;
  char *c_obj_path = NULL;
  jstring obj_path = NULL;

  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... address = %s", c_address);

  jstring address = env->NewStringUTF(c_address);

  dbus_error_init(&err);

  if (dbus_set_error_from_message(&err, msg)  ||
    !dbus_message_get_args(msg, &err,
                           DBUS_TYPE_OBJECT_PATH, &c_obj_path,
                           DBUS_TYPE_INVALID)) {
    LOG_AND_FREE_DBUS_ERROR(&err);

    is_error = JNI_TRUE;

    goto done;
  }

  LOGV(" object path = %s\n", c_obj_path);

  /* Add an object handler for FTP client agent method calls*/
  if (!dbus_connection_register_object_path(nat->conn,
                                            c_obj_path,
                                            &ftpclient_agent_vtable,
                                            NULL)) {
    LOGE("%s: Can't register object path %s for agent!",
         __FUNCTION__, c_obj_path);

    is_error = JNI_TRUE;

    goto done;
  } else {
    LOGV("Registered Object Path %s with dbus", c_obj_path);
    DBusMessage *reply = dbus_func_args_error(env, nat->conn, &err,
                                              OBEXD_DBUS_CLIENT_SVC,
                                              c_obj_path,
                                              OBEXD_DBUS_CLIENT_SESSION_IFC,
                                              OBEXD_DBUS_CLIENT_SESSION_ASSIGN,
                                              DBUS_TYPE_OBJECT_PATH,
                                              &c_obj_path, DBUS_TYPE_INVALID);

    if (reply) {
      LOGV("Added Object Path %s with BM3", c_obj_path);

      is_error = JNI_FALSE;

      dbus_message_unref(reply);
    } else {
      LOG_AND_FREE_DBUS_ERROR(&err);

      is_error = JNI_TRUE;
    }
  }

  done:
  if (is_error == JNI_FALSE) {
    obj_path = env->NewStringUTF(c_obj_path);
  }

  env->CallVoidMethod(nat->me,
                      method_onCreateSessionComplete,
                      obj_path,
                      address,
                      is_error);

  if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
  }

  env->DeleteLocalRef(address);

  if (obj_path != NULL) {
    env->DeleteLocalRef(obj_path);
  }

  free(c_address);

  return;
}

static void onChangeFolderComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  callback_data_t *callback_userdata = (callback_data_t *)user;
  DBusError  err;
  jboolean is_error = JNI_FALSE;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... session = %s ...folder = %s\n", callback_userdata->session, callback_userdata->folder);
  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    is_error = JNI_TRUE;
  }

  jstring session = env->NewStringUTF(callback_userdata->session);
  jstring folder =  env->NewStringUTF(callback_userdata->folder);

  env->CallVoidMethod(nat->me,
                      method_onChangeFolderComplete,
                      session,
                      folder,
                      is_error);

  if (env->ExceptionCheck())
  {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(session);
  env->DeleteLocalRef(folder);

  free(callback_userdata->session);
  free(callback_userdata->folder);
  free(callback_userdata);

  return;
}

static void onCreateFolderComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  callback_data_t *callback_userdata = (callback_data_t *)user;
  DBusError  err;
  jboolean is_error = JNI_FALSE;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... session = %s", callback_userdata->session);
  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    is_error = JNI_TRUE;
  }

  jstring session = env->NewStringUTF(callback_userdata->session);
  jstring folder =  env->NewStringUTF(callback_userdata->folder);

  env->CallVoidMethod(nat->me,
                      method_onCreateFolderComplete,
                      session,
                      folder,
                      is_error);

  if (env->ExceptionCheck())
  {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(session);
  env->DeleteLocalRef(folder);

  free(callback_userdata->session);
  free(callback_userdata->folder);
  free(callback_userdata);

  return;
}


static void onListFolderComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  char* c_session = (char *)user;
  DBusError  err;
  jboolean is_error = JNI_FALSE;
  jobjectArray result = NULL;
  jobject obj;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);
  jclass clazz = NULL;
  jmethodID constructor = NULL;
  DBusMessageIter iter, array;
  const char *c_name = NULL, *c_type = NULL, *c_permission = NULL;
  uint64_t size = 0, modified = 0, accessed = 0, created = 0;
  int array_count = 0;

  dbus_error_init(&err);

  LOGV("onListFolderComplete... session = %s", c_session);

  jstring session = env->NewStringUTF(c_session);

  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    goto fail;

    return;
  }

  if(!dbus_message_iter_init(msg, &iter))
  {
      LOGE("%s: D-Bus msg not valid\n", __FUNCTION__);

      goto fail;

      return;
  }

  dbus_message_iter_recurse(&iter, &array);

  while( dbus_message_iter_get_arg_type(&array) != DBUS_TYPE_INVALID )
  {
      array_count++;
      dbus_message_iter_next(&array);
  }

  LOGV(" No. of array element = %d\n", array_count );

  if(!dbus_message_iter_init(msg, &iter))
  {
      LOGE("%s: D-Bus msg not valid\n", __FUNCTION__);

      goto fail;

      return;
  }

  dbus_message_iter_recurse(&iter, &array);

  clazz = env->FindClass("android/server/BluetoothFtpService$ObjectProperties");

  if(!clazz)
  {
      LOGE("%s:%d Cannot FindClass", __FUNCTION__, __LINE__);

      goto fail;

      return;
  }

  constructor = env->GetMethodID(clazz,"<init>",
                                 "(Landroid/server/BluetoothFtpService;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;III)V");

  if (constructor == NULL)
  {
     LOGE("%s:%d Cannot Get Constructor", __FUNCTION__, __LINE__);

     goto fail;

     return;
  }

  result = env->NewObjectArray(array_count, clazz, NULL);

  if (result == NULL)
  {
      LOGE("%s:%d Cannot Construct an Array", __FUNCTION__, __LINE__);

      goto fail;

      return;
  }

  for( int i = 0; i < array_count; i++)
  {
    DBusMessageIter arr_element;

    dbus_message_iter_recurse(&array, &arr_element);

    while (dbus_message_iter_get_arg_type(&arr_element) == DBUS_TYPE_DICT_ENTRY)
    {
      const char *dict_key;
      DBusMessageIter dict_entry, dict_variant;

      dbus_message_iter_recurse(&arr_element, &dict_entry);

      dbus_message_iter_get_basic(&dict_entry, &dict_key);

      dbus_message_iter_next(&dict_entry);

      dbus_message_iter_recurse(&dict_entry, &dict_variant);

      int32_t itr_type;

      itr_type = dbus_message_iter_get_arg_type(&dict_variant);
      if ((itr_type == DBUS_TYPE_STRING) || (itr_type == DBUS_TYPE_UINT64))
      {

          if (!strncmp(dict_key, "Name", sizeof("Name")))
          {
              dbus_message_iter_get_basic(&dict_variant, &c_name);
              LOGV("...Name...%s", c_name );
          }
          else if (!strncmp(dict_key, "Type", sizeof("Type")))
          {
              dbus_message_iter_get_basic(&dict_variant, &c_type);
              LOGV("...Type...%s", c_type );
          }
          else if (!strncmp(dict_key, "Size", sizeof("Size")))
          {
              dbus_message_iter_get_basic(&dict_variant, &size);
              LOGV("...Size...%d", (int32_t)size );
          }
          else if (!strncmp(dict_key, "Permission", sizeof("Permission")))
          {
              dbus_message_iter_get_basic(&dict_variant, &c_permission);
              LOGV("...Permission... %s", c_permission);
          }
          else if (!strncmp(dict_key, "Modified", sizeof("Modified")))
          {
              dbus_message_iter_get_basic(&dict_variant, &modified);
              LOGV("...Modified...%d", (int32_t)modified);
          }
          else if (!strncmp(dict_key, "Accessed", sizeof("Accessed")))
          {
              dbus_message_iter_get_basic(&dict_variant, &accessed);
              LOGV("...Accessed...%d", (int32_t)accessed );
          }
          else if (!strncmp(dict_key, "Created", sizeof("Created")))
          {
              dbus_message_iter_get_basic(&dict_variant, &created);
              LOGV("...Created...%d", (int32_t)created);
          }
      }

      dbus_message_iter_next(&arr_element);
    }

    LOGV("Folder info: Name = %s   Type = %s Size = %d", c_name , c_type,  (int32_t)size);
    LOGV(" Permission = %s Modified = %d Accessed = %d Created = %d", c_permission,(int32_t) modified, (int32_t)accessed, (int32_t)created);

     jstring name = env->NewStringUTF(c_name);
     jstring type  = env->NewStringUTF(c_type);
     jstring permission  = env->NewStringUTF(c_permission);

    /* Creating new object "ObjectProperties" and initializing  */
    obj = env->NewObject(clazz, constructor, nat->me,
                         name, type, (jint) size, permission,
                         (jint)modified, (jint)accessed, (jint)created);

    env->DeleteLocalRef(name);
    env->DeleteLocalRef(type);
    env->DeleteLocalRef(permission);

    if (env->ExceptionCheck())
    {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }

    /* Populating the array with object  */
    env->SetObjectArrayElement(result, i, obj);

    env->DeleteLocalRef(obj);

    dbus_message_iter_next(&array);
  }// for loop

  env->CallVoidMethod(nat->me,
                      method_onListFolderComplete,
                      session,
                      result,
                      is_error);
  goto done;

fail:

    env->CallVoidMethod(nat->me,
                        method_onListFolderComplete,
                        session,
                        NULL,
                        JNI_TRUE);

done:
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
    }

    if (clazz) {
        env->DeleteLocalRef(clazz);
    }

    if (result) {
        env->DeleteLocalRef(result);
    }

    if (session) {
        env->DeleteLocalRef(session);
    }

    free(c_session);

    return;
}

static void onGetFileComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  callback_data_t *callback_userdata = (callback_data_t *)user;
  DBusError  err;
  jboolean is_error = JNI_FALSE;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... session = %s", callback_userdata->session);
  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    is_error = JNI_TRUE;
  }

  jstring session = env->NewStringUTF(callback_userdata->session);
  jstring targetfile = env->NewStringUTF(callback_userdata->targetfile);
  jstring sourcefile = env->NewStringUTF(callback_userdata->sourcefile);

  env->CallVoidMethod(nat->me,
                      method_onGetFileComplete,
                      session,
                      targetfile,
                      sourcefile,
                      is_error);

  if (env->ExceptionCheck())
  {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(session);
  env->DeleteLocalRef(targetfile);
  env->DeleteLocalRef(sourcefile);

  free(callback_userdata->session);
  free(callback_userdata->targetfile);
  free(callback_userdata->sourcefile);
  free(callback_userdata);

  return;
}

static void onPutFileComplete(DBusMessage *msg, void *user, void* nat_cb)
{
  LOGI(__FUNCTION__);

  callback_data_t *callback_userdata = (callback_data_t *)user;
  DBusError  err;
  jboolean      is_error = JNI_FALSE;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... session = %s", callback_userdata->session);
  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    is_error = JNI_TRUE;
  }

  jstring session = env->NewStringUTF(callback_userdata->session);
  jstring targetfile = env->NewStringUTF(callback_userdata->targetfile);
  jstring sourcefile = env->NewStringUTF(callback_userdata->sourcefile);

  env->CallVoidMethod(nat->me,
                      method_onPutFileComplete,
                      session,
                      sourcefile,
                      targetfile,
                      is_error);

  if (env->ExceptionCheck())
  {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(session);
  env->DeleteLocalRef(targetfile);
  env->DeleteLocalRef(sourcefile);

  free(callback_userdata->session);
  free(callback_userdata->targetfile);
  free(callback_userdata->sourcefile);
  free(callback_userdata);

  return;
}

static void onDeleteComplete(DBusMessage *msg, void *user, void *nat_cb)
{
  LOGI(__FUNCTION__);

  callback_data_t *callback_userdata = (callback_data_t *)user;
  DBusError  err;
  jboolean      is_error = JNI_FALSE;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  dbus_error_init(&err);

  LOGV("... session = %s", callback_userdata->session);
  if (dbus_set_error_from_message(&err, msg))
  {
    LOGE("%s: D-Bus error: %s (%s)\n", __FUNCTION__, err.name, err.message);

    dbus_error_free(&err);

    is_error = JNI_TRUE;
  }

  jstring session = env->NewStringUTF(callback_userdata->session);
  jstring folder = env->NewStringUTF(callback_userdata->folder);

  env->CallVoidMethod(nat->me,
                      method_onDeleteComplete,
                      session,
                      folder,
                      is_error);

  if (env->ExceptionCheck())
  {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(session);
  env->DeleteLocalRef(folder);

  free(callback_userdata->session);
  free(callback_userdata->folder);
  free(callback_userdata);

  return;
}

static DBusHandlerResult ftp_agent_request_handler(DBusConnection *conn,
                                                   DBusMessage *msg)
{
  LOGI(__FUNCTION__);
  const char *c_transfer;
  JNIEnv *env = NULL;
  nat->vm->GetEnv((void**)&env, nat->envVer);

  if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                          DBUS_TYPE_INVALID))
  {
    LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
        OBEXD_DBUS_CLIENT_AGENT_REQUEST);
    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
  }

  LOGV("... transfer = %s\n", c_transfer);

  jstring transfer = env->NewStringUTF(c_transfer);

  jstring filename = (jstring) env->CallObjectMethod(nat->me,
                                                     method_onObexRequest,
                                                     transfer);
  if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
  }

  env->DeleteLocalRef(transfer);

  DBusMessage *reply = NULL;
  const char *c_filename;

  if (filename != NULL)
  {
    reply = dbus_message_new_method_return(msg);
  }
  else
  {
    reply = dbus_message_new_error(msg, OBEXD_DBUS_ERROR_CANCELLED, NULL);

  }

  if( reply != NULL )
  {
      if(filename != NULL)
      {
        c_filename = env->GetStringUTFChars(filename, NULL);
        dbus_message_append_args(reply,
                                 DBUS_TYPE_STRING, &c_filename,
                                 DBUS_TYPE_INVALID);
      }

      dbus_connection_send(nat->conn, reply, NULL);
      dbus_message_unref(reply);

      if (filename != NULL) {
          env->ReleaseStringUTFChars(filename, c_filename);
          env->DeleteLocalRef(filename);
      }

      return DBUS_HANDLER_RESULT_HANDLED;
   } else if (filename != NULL) {
      env->DeleteLocalRef(filename);
   }

   return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult ftp_agent_progress_handler(DBusConnection *conn,
                                                    DBusMessage *msg)
{
    LOGI(__FUNCTION__);
    const char *c_transfer;
    uint64_t transferred;
    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_UINT64, &transferred,
                               DBUS_TYPE_INVALID))
    {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_PROGRESS);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s\n... bytes transferred = %d", c_transfer,
         (int32_t) transferred);

    jstring transfer = env->NewStringUTF(c_transfer);

    env->CallVoidMethod(nat->me, method_onObexProgress,
                        transfer,(jint) transferred);

    if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
    }

    env->DeleteLocalRef(transfer);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply)
    {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult ftp_agent_complete_handler(DBusConnection *conn,
                                                   DBusMessage *msg)
{
    LOGI(__FUNCTION__);
    const char *c_transfer;
    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_INVALID))
    {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_COMPLETE);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s", c_transfer);

    jstring transfer = env->NewStringUTF(c_transfer);

    env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                        transfer, NULL, JNI_TRUE, NULL);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
    }

    env->DeleteLocalRef(transfer);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply)
    {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult ftp_agent_error_handler(DBusConnection *conn,
                                                       DBusMessage *msg)
{
    LOGI(__FUNCTION__);
    const char *c_transfer, *c_message;
    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_OBJECT_PATH, &c_transfer,
                               DBUS_TYPE_STRING, &c_message,
                               DBUS_TYPE_INVALID))
    {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_ERROR);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGV("... transfer = %s\n... message = %s", c_transfer, c_message);

    jstring transfer = env->NewStringUTF(c_transfer);
    jstring message = env->NewStringUTF(c_message);

    env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                        transfer,
                        JNI_FALSE,
                        message);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
    }

    env->DeleteLocalRef(transfer);
    env->DeleteLocalRef(message);

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply)
    {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

static DBusHandlerResult ftp_agent_release_handler(DBusConnection *conn,
                                                   DBusMessage *msg)
{
    LOGI(__FUNCTION__);

    if (!dbus_message_get_args(msg, NULL, DBUS_TYPE_INVALID)) {
        LOGE("%s: Invalid arguments for %s() method", __FUNCTION__,
            OBEXD_DBUS_CLIENT_AGENT_RELEASE);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    JNIEnv *env = NULL;
    nat->vm->GetEnv((void**)&env, nat->envVer);
    const char* c_session = dbus_message_get_path(msg);

    if(c_session)
    {
        LOGV(" %s: Session %s", __FUNCTION__, c_session);

        jstring session = env->NewStringUTF(c_session);

        env->CallVoidMethod(nat->me, method_onObexSessionClosed,
                            session);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }

        env->DeleteLocalRef(session);

        // Unregister object handler for FTP client agent method calls
        if (!dbus_connection_unregister_object_path(nat->conn,
                                                    c_session))
        {
            LOGE("%s: Can't unregister register object path %s for agent!",
                 __FUNCTION__, c_session);
        }

    }

    DBusMessage *reply = dbus_message_new_method_return(msg);

    if (reply) {
        dbus_message_append_args(reply, DBUS_TYPE_INVALID);

        dbus_connection_send(nat->conn, reply, NULL);

        LOGV("ftp client agent released");

        dbus_message_unref(reply);

        return DBUS_HANDLER_RESULT_HANDLED;
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}

/* Called by dbus during WaitForAndDispatchEventNative() */
DBusHandlerResult ftpclient_agent(DBusConnection *conn,
                                  DBusMessage *msg,
                                  void *data)
{
    LOGI(__FUNCTION__);
    if (dbus_message_get_type(msg) != DBUS_MESSAGE_TYPE_METHOD_CALL) {
        LOGE("%s: not interested (not a method call).", __FUNCTION__);
        return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
    }

    LOGE("%s: Received method %s:%s", __FUNCTION__,
        dbus_message_get_interface(msg), dbus_message_get_member(msg));

    if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                    OBEXD_DBUS_CLIENT_AGENT_RELEASE)) {

        return ftp_agent_release_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_REQUEST)) {

        return ftp_agent_request_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_PROGRESS)) {

        return ftp_agent_progress_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_COMPLETE)) {

        return ftp_agent_complete_handler(conn, msg);

    } else if (dbus_message_is_method_call(msg, OBEXD_DBUS_CLIENT_AGENT_IFC,
                                           OBEXD_DBUS_CLIENT_AGENT_ERROR)) {

        return ftp_agent_error_handler(conn, msg);

    } else {
        LOGE("... ignored");
    }

    return DBUS_HANDLER_RESULT_NOT_YET_HANDLED;
}
DBusHandlerResult ftp_event_filter(DBusMessage *msg, JNIEnv *env)
{
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
                               OBEXD_DBUS_SRV_MGR_SGNL_FTP_SESS_CREATED)) {
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
                                       OBEXD_DBUS_SRV_MGR_SGNL_FTP_SESS_REMOVED)) {
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
                                      OBEXD_DBUS_SRV_MGR_SGNL_FTP_TRANS_STARTED)) {

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
                                      OBEXD_DBUS_SRV_MGR_SGNL_FTP_TRANS_COMPLETED)) {
        char *c_transfer;
        char *c_filename;
        dbus_bool_t c_success;

        if (dbus_message_get_args(msg, &err,
                                  DBUS_TYPE_OBJECT_PATH, &c_transfer,
                                  DBUS_TYPE_STRING, &c_filename,
                                  DBUS_TYPE_BOOLEAN, &c_success,
                                  DBUS_TYPE_INVALID)) {

            LOGV("FTP Server Transfer Completed = %s  filename = %s  Success = %d", c_transfer,
                 c_filename, c_success);

            jstring transfer = env->NewStringUTF(c_transfer);
            jstring filename = env->NewStringUTF(c_filename);
            jboolean success = c_success ? JNI_TRUE : JNI_FALSE;

            env->CallVoidMethod(nat->me, method_onObexTransferComplete,
                                NULL, filename, success, NULL);

            env->DeleteLocalRef(transfer);
            env->DeleteLocalRef(filename);

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
#endif
/**
* Connects to system D-Bus and registers with it.
 * @returns true on success (even if adapter is present but disabled).
 * @return false if dbus is down, or another serious error (out of memory)
 */
static bool initNative(JNIEnv* env, jobject object)
{
   LOGI(__FUNCTION__);

#ifdef HAVE_BLUETOOTH
  nat = (native_data_t *)calloc(1, sizeof(native_data_t));
  if (nat == NULL)
  {
      LOGE("%s: out of memory!", __FUNCTION__);
      return JNI_FALSE;
  }
  nat->me = env->NewGlobalRef(object);

  env->GetJavaVM( &(nat->vm) );
  nat->envVer = env->GetVersion();

  DBusError err;
  dbus_error_init(&err);
  dbus_threads_init_default();
  nat->conn = dbus_bus_get(DBUS_BUS_SYSTEM, &err);
  if (dbus_error_is_set(&err))
  {
    LOGE("Could not get onto the system bus %s", err.message);
    dbus_error_free(&err);
    return JNI_FALSE;
  }
  dbus_connection_set_exit_on_disconnect(nat->conn, FALSE);
#endif
  return JNI_TRUE;
}
static void cleanupNative(JNIEnv* env, jobject object)
{
  LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
  if (nat)
  {
    dbus_connection_close(nat->conn);
    env->DeleteGlobalRef(nat->me);
    free(nat);
    nat = NULL;
  }
  LOGI(__FUNCTION__);
#endif
}
/**
* Creates a new OBEX session.
*
* Device is configured as follows
* target as "ftp" and destination as the given address.
*
* This is an asynchronous call as it invokes methods on a remote
* object.
* The object path is obtained in the onCreateSessionComplete method.
* @params destination bt address
* @return FALSE if send fails, TRUE otherwise
*/
static jboolean createSessionNative(JNIEnv* env, jobject object, jstring address  )
{
   LOGI(__FUNCTION__);
   jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
  if( nat && address)
  {
    DBusMessage *msg = dbus_message_new_method_call(OBEXD_DBUS_CLIENT_SVC,
                                                    OBEXD_DBUS_CLIENT_PATH,
                                                    OBEXD_DBUS_CLIENT_IFC,
                                                    OBEXD_DBUS_CLIENT_CREATE);

    if (msg == NULL)
    {
      return JNI_FALSE;
    }

    DBusMessageIter iter, dict;

    dbus_message_iter_init_append(msg, &iter);

    dbus_message_iter_open_container(&iter, DBUS_TYPE_ARRAY,
                                     DBUS_DICT_ENTRY_BEGIN_CHAR_AS_STRING
                                     DBUS_TYPE_STRING_AS_STRING DBUS_TYPE_VARIANT_AS_STRING
                                     DBUS_DICT_ENTRY_END_CHAR_AS_STRING, &dict);

    const char *c_address = env->GetStringUTFChars(address, NULL);
    LOGV(" createSessionNative ... address = %s", c_address);

    dbus_append_ss_dict_entry(&dict, "Target", "ftp");
    dbus_append_ss_dict_entry(&dict, "Destination", c_address);

    dbus_message_iter_close_container(&iter, &dict);

    dbus_async_call_t *pending = NULL;

    pending = (dbus_async_call_t *) malloc(sizeof(dbus_async_call_t));

    if (pending)
    {
      DBusPendingCall *call;

      char *context_address =  (char *) calloc(BTADDR_SIZE,
                                               sizeof(char));

      strlcpy(context_address, c_address, BTADDR_SIZE);  // for callback

      pending->env = env;
      pending->user_cb = onCreateSessionComplete;
      pending->user = context_address;
      pending->nat = nat;

      dbus_bool_t reply = dbus_connection_send_with_reply(nat->conn,
                                                          msg,
                                                          &call,
                                                          10*1000);
      if (reply == TRUE)
      {
        dbus_pending_call_set_notify(call,
                                     dbus_func_args_async_callback,
                                     pending,
                                     NULL);

        result = JNI_TRUE;
      }
      else
      {
        free(pending);
      }
    }
  }
#endif
  return result;
}
/**
* Closes an OBEX session.
* @params obex session.
* @returns TRUE on success, FALSE otherwise
*/
static jboolean closeSessionNative(JNIEnv* env, jobject object, jstring session  )
{
   LOGI(__FUNCTION__);
   jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
  if (nat && session)
  {
    const char *c_session = env->GetStringUTFChars(session, NULL);

    LOGV("Session = %s\n", c_session);


    DBusError err;

    dbus_error_init(&err);

    DBusMessage *reply = dbus_func_args_error(env, nat->conn, &err,
                                              OBEXD_DBUS_CLIENT_SVC,
                                              c_session,
                                              OBEXD_DBUS_CLIENT_SESSION_IFC,
                                              OBEXD_DBUS_CLIENT_SESSION_CLOSE,
                                              DBUS_TYPE_INVALID);

    if (reply)
    {
        dbus_message_unref(reply);
        result = JNI_TRUE;
    }
    else
    {
        LOG_AND_FREE_DBUS_ERROR(&err);
    }

    env->ReleaseStringUTFChars(session, c_session);
  }

  if (env->ExceptionCheck())
  {
    LOGE("VM Exception occurred in native function %s (%s:%d)",
        __FUNCTION__, __FILE__, __LINE__);
  }
#endif
  return result;
}
/**
* Changes the current folder of the remote device to the specified folder.
* @params OBEX session object path.
* @folder name to be changed to.
* @returns FALSE if send fails, TRUE otherwise
*/
static jboolean changeFolderNative(JNIEnv* env, jobject object, jstring session, jstring folder)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    if (nat && session)
    {
      const char *c_session = env->GetStringUTFChars(session, NULL);
      LOGV("Session = %s\n", c_session);

      const char *c_folder = env->GetStringUTFChars(folder, NULL);
      LOGV("Folder = %s\n", c_folder);

      size_t session_sz = env->GetStringUTFLength(session) + 1;
      size_t folder_sz = env->GetStringUTFLength(folder) + 1;

      callback_data_t *callback_userdata = (callback_data_t *)malloc(sizeof(callback_data_t));// callback data

      callback_userdata->session = (char *)malloc(session_sz);  // storing session
      strncpy(callback_userdata->session, c_session, session_sz);

      callback_userdata->folder = (char *)malloc(folder_sz);  // storing folder
      strncpy(callback_userdata->folder, c_folder, folder_sz);

      bool reply = dbus_func_args_async(env, nat->conn, -1,
                         onChangeFolderComplete, (void *)callback_userdata, nat,
                         OBEXD_DBUS_CLIENT_SVC, c_session,
                         OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_CHANGE_FOLDER,
                         DBUS_TYPE_STRING, &c_folder,
                         DBUS_TYPE_INVALID);
      env->ReleaseStringUTFChars(session, c_session);
      env->ReleaseStringUTFChars(session, c_folder);
      if (!reply)
      {
        free( callback_userdata->session );
        free( callback_userdata->folder );
        free( callback_userdata );
        return JNI_FALSE;
      }
      return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

/**
* Creates a new folder in the remote device.
* @params OBEX session object path.
* @folder name to be created.
* @returns FALSE if send fails, TRUE otherwise
*/
static jboolean createFolderNative(JNIEnv* env, jobject object, jstring session, jstring folder)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH
    if (nat && session)
    {
      const char *c_session = env->GetStringUTFChars(session, NULL);
      LOGV("Session = %s\n", c_session);

      const char *c_folder = env->GetStringUTFChars(folder, NULL);
      LOGV("Folder = %s\n", c_folder);

      size_t session_sz = env->GetStringUTFLength(session) + 1;
      size_t folder_sz = env->GetStringUTFLength(folder) + 1;

      callback_data_t *callback_userdata = (callback_data_t *)malloc(sizeof(callback_data_t));// callback data

      callback_userdata->session = (char *)malloc(session_sz);  // storing session
      strncpy(callback_userdata->session, c_session, session_sz);

      callback_userdata->folder = (char *)malloc(folder_sz);  // storing folder
      strncpy(callback_userdata->folder, c_folder, folder_sz);

      bool reply = dbus_func_args_async(env, nat->conn, -1,
                         onCreateFolderComplete, (void *)callback_userdata, nat,
                         OBEXD_DBUS_CLIENT_SVC, c_session,
                         OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_CREATE_FOLDER,
                         DBUS_TYPE_STRING, &c_folder,
                         DBUS_TYPE_INVALID);
      env->ReleaseStringUTFChars(session, c_session);
      env->ReleaseStringUTFChars(session, c_folder);
      if (!reply)
      {
        free( callback_userdata->session );
        free( callback_userdata->folder );
        free( callback_userdata );
        return JNI_FALSE;
      }
      return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

/**
* Returns a dictionary containing information about the current folder content.
  The following keys are defined:
                jstring Name : Object name in UTF-8 format
                jstring Type : Either "folder" or "file"
                ulong Size : Object size or number of items in folder
                jstring Permission : Group, owner and other permission
                ulong Modified : Last change
                ulong Accessed : Last access
                ulong Created : Creation date

  This is an asynchronous call and the results will be obtained in
  onListFolderComplete method.
* @params OBEX session object path.
* @returns FALSE if send fails, TRUE other wise.
*/
static jboolean listFolderNative(JNIEnv* env, jobject object, jstring session)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH

if (nat && session)
    {
        const char *c_session = env->GetStringUTFChars(session, NULL);
        LOGV("listFolderNative .....  Session = %s\n", c_session);

        size_t session_sz = env->GetStringUTFLength(session) + 1;
        char *c_session_copy = (char *)malloc(session_sz);  // callback data
        strncpy(c_session_copy, c_session, session_sz);

        bool reply = dbus_func_args_async(env, nat->conn, 10*1000,
                           onListFolderComplete, (void *)c_session_copy, nat,
                           OBEXD_DBUS_CLIENT_SVC, c_session,
                           OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_LIST_FOLDER,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(session, c_session);
        if (!reply)
        {
            free(c_session_copy);
            return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}

/**
* Copies the source file from the remote device to the target file on
* the local system.
* This will trigger a transfer object to be created and the agent will request
* the file name where to store the file.
* Transfer progress, transfer complete and error if any will be indicated.
* @params OBEX session object path.
* @params sourcefile(remote device) to be copied from
* @params targerfile(local system) to be copied into
* @returns FALSE if send fails, TRUE otherwise.
*/
static jboolean getFileNative(JNIEnv* env, jobject object, jstring session,
                             jstring targetfile, jstring sourcefile)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH

if (nat && session)
    {

       const char *c_session = env->GetStringUTFChars(session, NULL);
       LOGV("Session = %s\n", c_session);

       const char *c_targetfile = env->GetStringUTFChars(targetfile, NULL);
      LOGV("Targetfile = %s\n", c_targetfile);

      const char *c_sourcefile = env->GetStringUTFChars(sourcefile, NULL);
      LOGV("Sourcefile = %s\n", c_sourcefile);

      size_t session_sz = env->GetStringUTFLength(session) + 1;
      size_t targetfile_sz = env->GetStringUTFLength(targetfile) + 1;
      size_t sourcefile_sz = env->GetStringUTFLength(sourcefile) + 1;

      callback_data_t *callback_userdata = (callback_data_t *)malloc(sizeof(callback_data_t));// callback data

      callback_userdata->session = (char *)malloc(session_sz);  // storing session
      strncpy(callback_userdata->session, c_session, session_sz);

      callback_userdata->targetfile= (char *)malloc(targetfile_sz);  // storing targetfile
      strncpy(callback_userdata->targetfile, c_targetfile, targetfile_sz);

      callback_userdata->sourcefile= (char *)malloc(sourcefile_sz);  // storing sourcefile
      strncpy(callback_userdata->sourcefile, c_sourcefile, sourcefile_sz);

      bool reply = dbus_func_args_async(env, nat->conn, 10*1000,
                         onGetFileComplete, (void *)callback_userdata, nat,
                         OBEXD_DBUS_CLIENT_SVC, c_session,
                         OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_GET_FILE,
                         DBUS_TYPE_STRING, &c_targetfile,
                         DBUS_TYPE_STRING, &c_sourcefile,
                         DBUS_TYPE_INVALID);

      env->ReleaseStringUTFChars(session, c_session);
      env->ReleaseStringUTFChars(session, c_targetfile);
      env->ReleaseStringUTFChars(session, c_sourcefile);
      if (!reply)
      {
        free( callback_userdata->session );
        free( callback_userdata->targetfile );
        free( callback_userdata->sourcefile);
        free( callback_userdata);
        return JNI_FALSE;
      }
      return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}
/**
* Copies the source file from the local filesystem to the target file on
* the remote device.
* This will trigger a transfer object to be created and it will request
* the file name to show to the remote device.
* Transfer progress, transfer complete and error if any will be indicated.
* @params OBEX session object path.
* @params sourcefile(local system) to be copied from
* @params targerfile(remote device) to be copied into
* @returns FALSE if send fails, TRUE otherwise.
*/
static jboolean putFileNative(JNIEnv* env, jobject object, jstring session,
                             jstring sourcefile, jstring targetfile)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH

if (nat && session)
    {
      const char *c_session = env->GetStringUTFChars(session, NULL);
      LOGV("Session = %s\n", c_session);

      const char *c_targetfile = env->GetStringUTFChars(targetfile, NULL);
      LOGV("Targetfile = %s\n", c_targetfile);

      const char *c_sourcefile = env->GetStringUTFChars(sourcefile, NULL);
      LOGV("Sourcefile = %s\n", c_sourcefile);

      size_t session_sz = env->GetStringUTFLength(session) + 1;
      size_t targetfile_sz = env->GetStringUTFLength(targetfile) + 1;
      size_t sourcefile_sz = env->GetStringUTFLength(sourcefile) + 1;

      callback_data_t *callback_userdata = (callback_data_t *)malloc(sizeof(callback_data_t));// callback data

      callback_userdata->session = (char *)malloc(session_sz);  // storing session
      strncpy(callback_userdata->session, c_session, session_sz);

      callback_userdata->targetfile= (char *)malloc(targetfile_sz);  // storing targetfile
      strncpy(callback_userdata->targetfile, c_targetfile, targetfile_sz);

      callback_userdata->sourcefile= (char *)malloc(sourcefile_sz);  // storing sourcefile
      strncpy(callback_userdata->sourcefile, c_sourcefile, sourcefile_sz);
      bool reply = dbus_func_args_async(env, nat->conn, -1,
                         onPutFileComplete, (void *)callback_userdata, nat,
                         OBEXD_DBUS_CLIENT_SVC, c_session,
                         OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_PUT_FILE,
                         DBUS_TYPE_STRING, &c_sourcefile,
                         DBUS_TYPE_STRING, &c_targetfile,
                         DBUS_TYPE_INVALID);
      env->ReleaseStringUTFChars(session, c_session);
      env->ReleaseStringUTFChars(session, c_targetfile);
      env->ReleaseStringUTFChars(session, c_sourcefile);
      if (!reply)
      {
        free( callback_userdata->session );
        free( callback_userdata->targetfile );
        free( callback_userdata->sourcefile);
        free( callback_userdata);
        return JNI_FALSE;
      }
      return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}
/**
* Deletes the specified file or folder.
* @params OBEX session object path.
* @params file or folder name to be deleted
* @returns FALSE if delete fails, TRUE otherwise.
*/
static jboolean deleteNative(JNIEnv* env, jobject object, jstring session, jstring name)
{
   LOGI(__FUNCTION__);
#ifdef HAVE_BLUETOOTH

if (nat && session)
    {
      const char *c_session = env->GetStringUTFChars(session, NULL);
      LOGV("Session = %s\n", c_session);

      const char *c_name = env->GetStringUTFChars(name, NULL);
      LOGV("Name = %s\n", c_name);

      size_t session_sz = env->GetStringUTFLength(session) + 1;
      size_t name_sz = env->GetStringUTFLength(name) + 1;

      callback_data_t *callback_userdata = (callback_data_t *)malloc(sizeof(callback_data_t));// callback data

      callback_userdata->session = (char *)malloc(session_sz);  // storing session
      strncpy(callback_userdata->session, c_session, session_sz);

      callback_userdata->folder = (char *)malloc(name_sz);  // storing folder
      strncpy(callback_userdata->folder, c_name, name_sz);

        bool reply = dbus_func_args_async(env, nat->conn, -1,
                           onDeleteComplete, (void *)callback_userdata, nat,
                           OBEXD_DBUS_CLIENT_SVC, c_session,
                           OBEXD_DBUS_CLIENT_FTP_IFC, OBEXD_DBUS_CLIENT_FTP_DELETE,
                           DBUS_TYPE_STRING, &c_name,
                           DBUS_TYPE_INVALID);
        env->ReleaseStringUTFChars(session, c_session);
        env->ReleaseStringUTFChars(session, c_name);
        if (!reply)
        {
          free( callback_userdata->session );
          free( callback_userdata->folder );
          free( callback_userdata );
          return JNI_FALSE;
        }
        return JNI_TRUE;
    }
#endif
    return JNI_FALSE;
}
/**
* Cancels the session's transfer.
* @params transfer object path.
* @returns FALSE if transfer does not belongs to the session, TRUE otherwise.
*/
static jboolean cancelTransferNative(JNIEnv* env, jobject object, jstring transfer)
{
   LOGI(__FUNCTION__);
    jboolean result = JNI_FALSE;
#ifdef HAVE_BLUETOOTH
    if (nat && transfer)
    {
        const char *c_transfer = env->GetStringUTFChars(transfer, NULL);

        LOGV("Transfer = %s\n", c_transfer);

        DBusError err;

        dbus_error_init(&err);

        DBusMessage *reply = dbus_func_args_error(env, nat->conn, &err,
                                                  OBEXD_DBUS_CLIENT_SVC,
                                                  c_transfer,
                                                  OBEXD_DBUS_CLIENT_TRANS_IFC,
                                                  OBEXD_DBUS_CLIENT_TRANS_CANCEL,
                                                  DBUS_TYPE_INVALID);

        if (reply)
        {
            dbus_message_unref(reply);
            result = JNI_TRUE;
        }
        else
        {
            LOG_AND_FREE_DBUS_ERROR(&err);
        }

        env->ReleaseStringUTFChars(transfer, c_transfer);
    }

    if (env->ExceptionCheck())
    {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;

}

/**
* Returns all the properties for the transfer.
    jstring Name of the transferred object.
        jint Size of the transferred object. If the size is unknown,
          then this property will not be present.
        jstring Filename Complete name of the file being received or sent.
* @params transfer object path.
*/
static jobject obexTransferGetPropertiesNative(JNIEnv* env, jobject object, jstring transfer)
{
   LOGI(__FUNCTION__);
   jobject result = NULL;
#ifdef HAVE_BLUETOOTH
    if (nat && transfer )
    {
        const char *c_transfer = env->GetStringUTFChars(transfer, NULL);

        LOGV("%s: Transfer = %s", __FUNCTION__, c_transfer);

        DBusMessage *reply;

        reply = dbus_func_args(env, nat->conn, OBEXD_DBUS_CLIENT_SVC,
                              c_transfer, OBEXD_DBUS_CLIENT_TRANS_IFC,
                              OBEXD_DBUS_CLIENT_TRANS_GETPROPS,
                              DBUS_TYPE_INVALID);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }

        env->ReleaseStringUTFChars(transfer, c_transfer);

        if (reply == NULL)
        {
            LOGE("%s:%d Cannot create message to D-Bus", __FUNCTION__, __LINE__);
            if (env->ExceptionCheck()) {
               env->ExceptionDescribe();

            }
            return result;
        }

        DBusMessageIter iter, array;
        const char *name = NULL;
        const char *filename = NULL;
        uint64_t size = 0;

        dbus_message_iter_init(reply, &iter);
        dbus_message_iter_recurse(&iter, &array);

        while (dbus_message_iter_get_arg_type(&array) == DBUS_TYPE_DICT_ENTRY)
        {
            const char *dict_key;
            DBusMessageIter dict_entry, dict_variant;

            dbus_message_iter_recurse(&array, &dict_entry);

            dbus_message_iter_get_basic(&dict_entry, &dict_key);

            dbus_message_iter_next(&dict_entry);

            dbus_message_iter_recurse(&dict_entry, &dict_variant);

            int32_t type;

            type = dbus_message_iter_get_arg_type(&dict_variant);

            if ((type == DBUS_TYPE_STRING) || (type == DBUS_TYPE_INT64))
            {
                if (!strncmp(dict_key, "Name", sizeof("Name")))
                {
                    dbus_message_iter_get_basic(&dict_variant, &name);
                }
                else if (!strncmp(dict_key, "Size", sizeof("Size")))
                {
                    dbus_message_iter_get_basic(&dict_variant, &size);
                }
                else if (!strncmp(dict_key, "Filename", sizeof("Filename")))
                {
                    dbus_message_iter_get_basic(&dict_variant, & filename);
                }
            }

            dbus_message_iter_next(&array);
        }

        LOGV("Properties: Name = %s   Size = %d   Filename = %s", name ,
        (int32_t) size, filename);

        dbus_message_unref(reply);

        jclass clazz =
        env->FindClass("android/server/BluetoothFtpService$TransferProperties");

        if (clazz == NULL)
        {
            LOGE("%s:%d Cannot FindClass", __FUNCTION__, __LINE__);
            return result;
        }

        jmethodID constructor = env->GetMethodID(clazz,"<init>","(Landroid/server/BluetoothFtpService;Ljava/lang/String;ILjava/lang/String;)V");

        if (constructor == NULL)
        {
           LOGE("%s:%d Cannot Get Constructor", __FUNCTION__, __LINE__);
           return result;
        }

        result = env->NewObject(clazz, constructor, object,
                                env->NewStringUTF(name), (jint) size,
                                env->NewStringUTF(filename));
    }

    if (env->ExceptionCheck())
    {
        LOGE("VM Exception occurred in native function %s (%s:%d)",
            __FUNCTION__, __FILE__, __LINE__);
    }
#endif
    return result;
}


static JNINativeMethod sMethods[] = {

    {"initNative", "()Z", (void *)initNative},
    {"cleanupNative", "()V", (void *)cleanupNative},

    /* Obexd 0.8 API */
    {"createSessionNative", "(Ljava/lang/String;)Z", (void *) createSessionNative},
    {"closeSessionNative", "(Ljava/lang/String;)Z", (void *) closeSessionNative},
    {"changeFolderNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*) changeFolderNative},
    {"createFolderNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*) createFolderNative},
    {"listFolderNative", "(Ljava/lang/String;)Z", (void*) listFolderNative},
    {"getFileNative", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", (void*) getFileNative},
    {"putFileNative", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", (void*) putFileNative},
    {"deleteNative", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*) deleteNative},
    {"cancelTransferNative", "(Ljava/lang/String;)Z", (void*) cancelTransferNative},
    {"obexTransferGetPropertiesNative","(Ljava/lang/String;)Landroid/server/BluetoothFtpService$TransferProperties;", (void *) obexTransferGetPropertiesNative},

};

int register_android_server_BluetoothFtpService(JNIEnv *env)
{
  jclass clazz = env->FindClass("android/server/BluetoothFtpService");
  if (clazz == NULL)
  {
      LOGE("Can't find android/server/BluetoothFtpService");
      return -1;
  }
#ifdef HAVE_BLUETOOTH
  method_onCreateSessionComplete = env->GetMethodID( clazz, "onCreateSessionComplete", "(Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onChangeFolderComplete = env->GetMethodID( clazz, "onChangeFolderComplete", "(Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onCreateFolderComplete = env->GetMethodID( clazz, "onCreateFolderComplete", "(Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onListFolderComplete = env->GetMethodID( clazz, "onListFolderComplete", "(Ljava/lang/String;[Landroid/server/BluetoothFtpService$ObjectProperties;Z)V" );
  method_onGetFileComplete = env->GetMethodID( clazz, "onGetFileComplete", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onPutFileComplete = env->GetMethodID( clazz, "onPutFileComplete", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onDeleteComplete = env->GetMethodID( clazz, "onDeleteComplete", "(Ljava/lang/String;Ljava/lang/String;Z)V" );
  method_onObexRequest = env->GetMethodID( clazz, "onObexRequest", "(Ljava/lang/String;)Ljava/lang/String;" );
  method_onObexProgress = env->GetMethodID( clazz, "onObexProgress", "(Ljava/lang/String;I)V" );
  method_onObexTransferComplete = env->GetMethodID( clazz, "onObexTransferComplete", "(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)V" );
  method_onObexSessionClosed = env->GetMethodID(clazz,"onObexSessionClosed", "(Ljava/lang/String;)V");
#endif

  if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
  }

  return AndroidRuntime::registerNativeMethods(env,
         "android/server/BluetoothFtpService", sMethods, NELEM(sMethods));
}

} /* namespace android */
