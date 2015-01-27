/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "wifi"

#include "jni.h"
#include <ScopedUtfChars.h>
#include <android_runtime/AndroidRuntime.h>

#include "wifi.h"

#define REPLY_BUF_SIZE 4096 // wpa_supplicant's maximum size.
#define EVENT_BUF_SIZE 2048

#define CONVERT_LINE_LEN 2048
#define CHARSET_CN ("gbk")

#include "android_net_wifi_Gbk2Utf.h"

namespace android {

static jint DBG = false;

extern struct accessPointObjectItem *g_pItemList;
extern struct accessPointObjectItem *g_pLastNode;
extern pthread_mutex_t *g_pItemListMutex;
extern String8 *g_pCurrentSSID;
static bool doCommand(JNIEnv* env, jstring javaCommand,
                      char* reply, size_t reply_len) {
    ScopedUtfChars command(env, javaCommand);
    if (command.c_str() == NULL) {
        return false; // ScopedUtfChars already threw on error.
    }
    if(strstr(command.c_str(), "SET_NETWORK")) {
        if(!setNetworkVariable((char *)command.c_str())) {
            return false;
        }
    }
    if (DBG) {
        ALOGD("doCommand: %s", command.c_str());
    }

    --reply_len; // Ensure we have room to add NUL termination.
    if (::wifi_command(command.c_str(), reply, &reply_len) != 0) {
        return false;
    }

    // Strip off trailing newline.
    if (reply_len > 0 && reply[reply_len-1] == '\n') {
        reply[reply_len-1] = '\0';
    } else {
        reply[reply_len] = '\0';
    }
    return true;
}

static jint doIntCommand(JNIEnv* env, jstring javaCommand) {
    char reply[REPLY_BUF_SIZE];
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return -1;
    }
    return static_cast<jint>(atoi(reply));
}

static jboolean doBooleanCommand(JNIEnv* env, jstring javaCommand) {
    char reply[REPLY_BUF_SIZE];
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return JNI_FALSE;
    }
    return (strcmp(reply, "OK") == 0);
}

// Send a command to the supplicant, and return the reply as a String.
static jstring doStringCommand(JNIEnv* env, jstring javaCommand) {
    char reply[REPLY_BUF_SIZE];
    ScopedUtfChars command(env, javaCommand);
    if (!doCommand(env, javaCommand, reply, sizeof(reply))) {
        return NULL;
    }
    if (DBG) ALOGD("cmd = %s, reply: %s", command.c_str(), reply);
    String16 str;
    if (strstr(command.c_str(),"BSS RANGE=")) {
        parseScanResults(str,reply);
    } else if (strstr(command.c_str(),"GET_NETWORK") &&
              strstr(command.c_str(),"ssid") && !strstr(command.c_str(),"bssid")
              && !strstr(command.c_str(),"scan_ssid")){
        constructSsid(str, reply);
    } else {
        str += String16((char *)reply);
    }
    return env->NewString((const jchar *)str.string(), str.size());
}

static jboolean android_net_wifi_setMode(JNIEnv* env, jobject, jint type)
{
    return (jboolean)(::wifi_set_mode(type) == 0);
}

static jboolean android_net_wifi_isDriverLoaded(JNIEnv* env, jobject)
{
    return (::is_wifi_driver_loaded() == 1);
}

static jboolean android_net_wifi_loadDriver(JNIEnv* env, jobject)
{
    g_pItemListMutex = new pthread_mutex_t;
    if (NULL == g_pItemListMutex) {
        ALOGE("Failed to allocate memory for g_pItemListMutex!");
        return JNI_FALSE;
    }
    pthread_mutex_init(g_pItemListMutex, NULL);
    return (::wifi_load_driver() == 0);
}

static jboolean android_net_wifi_unloadDriver(JNIEnv* env, jobject)
{
    if (g_pItemListMutex != NULL) {
        pthread_mutex_lock(g_pItemListMutex);
        struct accessPointObjectItem *pCurrentNode = g_pItemList;
        struct accessPointObjectItem *pNextNode = NULL;
        while (pCurrentNode) {
            pNextNode = pCurrentNode->pNext;
            if (NULL != pCurrentNode->ssid) {
                delete pCurrentNode->ssid;
                pCurrentNode->ssid = NULL;
            }
            if (NULL != pCurrentNode->ssid_utf8) {
                delete pCurrentNode->ssid_utf8;
                pCurrentNode->ssid_utf8 = NULL;
            }
            delete pCurrentNode;
            pCurrentNode = pNextNode;
        }
        g_pItemList = NULL;
        g_pLastNode = NULL;
        pthread_mutex_unlock(g_pItemListMutex);
        pthread_mutex_destroy(g_pItemListMutex);
        delete g_pItemListMutex;
        g_pItemListMutex = NULL;
    }
    return (::wifi_unload_driver() == 0);
}

static jboolean android_net_wifi_startSupplicant(JNIEnv* env, jobject, jboolean p2pSupported)
{
    return (::wifi_start_supplicant(p2pSupported) == 0);
}

static jboolean android_net_wifi_killSupplicant(JNIEnv* env, jobject, jboolean p2pSupported)
{
    return (::wifi_stop_supplicant(p2pSupported) == 0);
}

static jboolean android_net_wifi_connectToSupplicant(JNIEnv* env, jobject)
{
    return (::wifi_connect_to_supplicant() == 0);
}

static void android_net_wifi_closeSupplicantConnection(JNIEnv* env, jobject)
{
    ::wifi_close_supplicant_connection();
}

static jstring android_net_wifi_waitForEvent(JNIEnv* env, jobject)
{
    char buf[EVENT_BUF_SIZE];
    int nread = ::wifi_wait_for_event(buf, sizeof buf);
    if (nread > 0) {
        if (strstr(buf, " SSID=") || strstr(buf, " SSID ")){
            constructEventSsid(buf);
        }
        return env->NewStringUTF(buf);
    } else {
        return NULL;
    }
}

static jboolean android_net_wifi_doBooleanCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doBooleanCommand(env, javaCommand);
}

static jint android_net_wifi_doIntCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doIntCommand(env, javaCommand);
}

static jstring android_net_wifi_doStringCommand(JNIEnv* env, jobject, jstring javaCommand) {
    return doStringCommand(env,javaCommand);
}



// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gWifiMethods[] = {
    /* name, signature, funcPtr */

    { "loadDriver", "()Z",  (void *)android_net_wifi_loadDriver },
    { "isDriverLoaded", "()Z",  (void *)android_net_wifi_isDriverLoaded },
    { "unloadDriver", "()Z",  (void *)android_net_wifi_unloadDriver },
    { "startSupplicant", "(Z)Z",  (void *)android_net_wifi_startSupplicant },
    { "killSupplicant", "(Z)Z",  (void *)android_net_wifi_killSupplicant },
    { "connectToSupplicantNative", "()Z", (void *)android_net_wifi_connectToSupplicant },
    { "closeSupplicantConnectionNative", "()V",
            (void *)android_net_wifi_closeSupplicantConnection },
    { "waitForEventNative", "()Ljava/lang/String;", (void*)android_net_wifi_waitForEvent },
    { "doBooleanCommandNative", "(Ljava/lang/String;)Z", (void*)android_net_wifi_doBooleanCommand },
    { "doIntCommandNative", "(Ljava/lang/String;)I", (void*)android_net_wifi_doIntCommand },
    { "doStringCommandNative", "(Ljava/lang/String;)Ljava/lang/String;",
            (void*) android_net_wifi_doStringCommand },
    { "setMode", "(I)Z", (void*) android_net_wifi_setMode},
};

int register_android_net_wifi_WifiNative(JNIEnv* env) {
    return AndroidRuntime::registerNativeMethods(env,
            "android/net/wifi/WifiNative", gWifiMethods, NELEM(gWifiMethods));
}

}; // namespace android
