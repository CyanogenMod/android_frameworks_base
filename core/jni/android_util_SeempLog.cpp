/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2007-2014 The Android Open Source Project
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

#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <assert.h>
#include <cutils/properties.h>
#include <utils/String8.h>
#include <android_runtime/Log.h>
#include <utils/Log.h>
#ifdef __BIONIC__
#include <android/set_abort_message.h>
#endif
#include <utils/Log.h>

#include "jni.h"
#include "JNIHelp.h"
#include "utils/misc.h"
#include "android_runtime/AndroidRuntime.h"

#define LOG_BUF_SIZE 1024
#define SEEMP_SOCK_NAME "/dev/socket/seempdw"
#ifndef __unused
#define __unused  __attribute__((__unused__))
#endif

static int __write_to_log_init(struct iovec *vec, size_t nr);
static int (*write_to_log)(struct iovec *vec, size_t nr) = __write_to_log_init;
static int logd_fd = -1;

/* give up, resources too limited */
static int __write_to_log_null(struct iovec *vec __unused,
                               size_t nr __unused)
{
    return -1;
}

/* log_init_lock assumed */
static int __write_to_log_initialize()
{
    int i, ret = 0;
    if (logd_fd >= 0) {
        i = logd_fd;
        logd_fd = -1;
        close(i);
    }

    i = socket(PF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (i < 0) {
        ret = -errno;
        write_to_log = __write_to_log_null;
    } else if (fcntl(i, F_SETFL, O_NONBLOCK) < 0) {
        ret = -errno;
        close(i);
        i = -1;
        write_to_log = __write_to_log_null;
    } else {
        struct sockaddr_un un;
        memset(&un, 0, sizeof(struct sockaddr_un));
        un.sun_family = AF_UNIX;
        strlcpy(un.sun_path, SEEMP_SOCK_NAME, sizeof(un.sun_path));
        if (connect(i, (struct sockaddr *)&un, sizeof(struct sockaddr_un)) < 0) {
            ret = -errno;
            close(i);
            i = -1;
        }
    }
    logd_fd = i;
    return ret;
}

static int __write_to_log_socket(struct iovec *vec, size_t nr)
{
    ssize_t ret;
    if (logd_fd < 0) {
        return -EBADF;
    }

    /*
     * The write below could be lost, but will never block.
     *
     * ENOTCONN occurs if logd dies.
     * EAGAIN occurs if logd is overloaded.
     */
    ret = writev(logd_fd, vec, nr);
    if (ret < 0) {
        ret = -errno;
        if (ret == -ENOTCONN) {
            ret = __write_to_log_initialize();
            if (ret < 0) {
                return ret;
            }

            ret = writev(logd_fd, vec, nr);
            if (ret < 0) {
                ret = -errno;
            }
        }
    }

    return ret;
}

static int __write_to_log_init(struct iovec *vec, size_t nr)
{
    if (write_to_log == __write_to_log_init) {
        int ret;

        ret = __write_to_log_initialize();
        if (ret < 0) {
            return ret;
        }

        write_to_log = __write_to_log_socket;
    }
    return write_to_log(vec, nr);
}

int __android_seemp_socket_write(int len, const char *msg)
{
     struct iovec vec;
     vec.iov_base   = (void *) msg;
     vec.iov_len  = len;

     return write_to_log(&vec, 1);
}

namespace android {

/*
 * In class android.util.Log:
 *  public static native int println_native(int buffer, int priority, String tag, String msg)
 */
static jint android_util_SeempLog_println_native(JNIEnv* env, jobject clazz,
                                jint api, jstring msgObj)
{
    if (msgObj == NULL) {
        jniThrowNullPointerException(env, "seemp_println needs a message");
        return -1;
    }

    int  apiId    = (int)api;
    int  apiIdLen = sizeof(apiId);
    int  msgLen   = env->GetStringUTFLength(msgObj);
    int  len      = apiIdLen + 1 + msgLen + 1;
    char *msg     = (char*)malloc(len);
    if ( NULL == msg )
    {
        return -1;
    }
    char *params  = msg + apiIdLen + 1; // api_id + encoding byte + params

    *((int*)msg)  = apiId;                              // copy api id
    //                                                  // skip encoding byte
    env->GetStringUTFRegion(msgObj, 0, msgLen, params); // copy message
    msg[len - 1]  = 0;                                  // copy terminating zero

    int  res      = __android_seemp_socket_write(len, msg); // send message

    free(msg);

    return res;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "seemp_println_native",  "(ILjava/lang/String;)I",
            (void*) android_util_SeempLog_println_native },
};

int register_android_util_SeempLog(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/util/SeempLog");
    if (clazz == NULL) {
        return -1;
    }

    return AndroidRuntime::registerNativeMethods(env, "android/util/SeempLog", gMethods,
            NELEM(gMethods));
}

}; // namespace android
