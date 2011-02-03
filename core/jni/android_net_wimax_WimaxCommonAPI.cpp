/*
 * Copyright 2011, The CyanogenMod Project
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

#define LOG_TAG "wimax"

#include "jni.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>
#include <arpa/inet.h>

//#include <netutils/ifc.h>
#include "cutils/properties.h"

#include <stdlib.h>
#include <string.h>

#include "wimax.h"

#ifdef HAVE_LIBC_SYSTEM_PROPERTIES
#define _REALLY_INCLUDE_SYS__SYSTEM_PROPERTIES_H_
#include <sys/_system_properties.h>
#endif

#define WIMAX_PKG_NAME "com/htc/net/wimax/WimaxNative"
typedef unsigned char byte;

namespace android {

static struct fieldIds {
    jclass dhcpInfoClass;
    jmethodID constructorId;
    jfieldID ipaddress;
    jfieldID gateway;
    jfieldID netmask;
    jfieldID dns1;
    jfieldID dns2;
    jfieldID serverAddress;
    jfieldID leaseDuration;
} dhcpInfoFieldIds;

static jboolean com_htc_net_wimax_loadWimaxDriver(JNIEnv* env, jobject clazz)
{
    int rval = ::loadWimaxDriver();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_loadWimaxDriver() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_unloadWimaxDriver(JNIEnv* env, jobject clazz)
{
    int rval = ::unloadWimaxDriver();
    //int rval = 0;
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_unloadWimaxDriver() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_startWimaxDaemon(JNIEnv* env, jobject clazz)
{
    int rval = ::startWimaxDaemon();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_startWimaxDaemon() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_stopWimaxDaemon(JNIEnv* env, jobject clazz)
{
    int rval = ::stopWimaxDaemon();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_stopWimaxDaemon() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_getWimaxProp(JNIEnv* env, jobject clazz, jstring param1)
{
    return (jboolean)(::getWimaxProp() == 0);
}

static jboolean com_htc_net_wimax_setWimaxProp(JNIEnv* env, jobject clazz, jstring param1, jstring param2)
{
    jboolean blnIsCopy;
    const char* strCIn = (env)->GetStringUTFChars(param1 , &blnIsCopy);
    const char* strCIn2 = (env)->GetStringUTFChars(param2 , &blnIsCopy);

    LOGD("com_htc_net_wimax_setWimaxProp() - param1 = ");
    LOGD("com_htc_net_wimax_setWimaxProp() - param2 = ");

    return (jboolean)(::setWimaxProp() == 0);
}

static jboolean com_htc_net_wimax_stopDhcpWimax(JNIEnv* env, jobject clazz)
{
    int rval = ::stopDhcpWimax();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_stopDhcpWimax() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_startDhcpWimaxDaemon(JNIEnv* env, jobject clazz)
{
    int rval = ::startDhcpWimaxDaemon();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_startDhcpWimaxDaemon() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_terminateProcess(JNIEnv* env, jobject clazz, jstring param1)
{
   jboolean blnIsCopy;
   const char *strCIn = (env)->GetStringUTFChars(param1 , &blnIsCopy);
   char pid[10];
   strcpy (pid, strCIn);
   int rval = ::terminateProcess(pid);
   /* char buffer[100];
   sprintf (buffer, "com_htc_net_wimax_terminateProcess() - rval = %d\n", rval);
   LOGI(buffer); */
   
   return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_addRouteToGateway(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::addRouteToGateway() == 0);
}

static jboolean com_htc_net_wimax_dhcpRelease(JNIEnv* env, jobject clazz)
{
    int rval = ::dhcpRelease();
    /* char buffer[100];
    sprintf (buffer, "com_htc_net_wimax_dhcpRelease() - rval = %d\n", rval);
    LOGI(buffer); */

    return (jboolean)(rval == 0);
}

static jboolean com_htc_net_wimax_doWimaxDhcpRequest(JNIEnv* env, jobject clazz, jobject info)
{
    int result = 0;
    //in_addr_t ipaddr, gateway, mask, dns1, dns2, server;
    //uint32_t lease;

    //const char *nameStr = env->GetStringUTFChars(ifname, NULL);
    //result = ::doWimaxDhcpRequest(nameStr, &ipaddr, &gateway, &mask,
    //                                    &dns1, &dns2, &server, &lease);
    //env->ReleaseStringUTFChars(ifname, nameStr);
    //if (result == 0 && dhcpInfoFieldIds.dhcpInfoClass != NULL) {
    //    env->SetIntField(info, dhcpInfoFieldIds.ipaddress, ipaddr);
    //    env->SetIntField(info, dhcpInfoFieldIds.gateway, gateway);
    //    env->SetIntField(info, dhcpInfoFieldIds.netmask, mask);
    //    env->SetIntField(info, dhcpInfoFieldIds.dns1, dns1);
    //    env->SetIntField(info, dhcpInfoFieldIds.dns2, dns2);
    //    env->SetIntField(info, dhcpInfoFieldIds.serverAddress, server);
    //    env->SetIntField(info, dhcpInfoFieldIds.leaseDuration, lease);
    //}
    return (jboolean)(result == 0);
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gWimaxMethods[] = {
    /* name, signature, funcPtr */

    { "LoadDriver", "()Z",  (void *)com_htc_net_wimax_loadWimaxDriver },
    { "UnloadDriver", "()Z",  (void *)com_htc_net_wimax_unloadWimaxDriver },
    { "StartDaemon", "()Z",  (void *)com_htc_net_wimax_startWimaxDaemon },
    { "StopDaemon", "()Z",  (void *)com_htc_net_wimax_stopWimaxDaemon },
    { "getWimaxProp", "(Ljava/lang/String;)Ljava/lang/String;",  (void *)com_htc_net_wimax_getWimaxProp },
    { "setWimaxProp", "(Ljava/lang/String;Ljava/lang/String;)Z",  (void *)com_htc_net_wimax_setWimaxProp },
    { "StopDhcpWimax", "()Z",  (void *)com_htc_net_wimax_stopDhcpWimax },
    { "StartDhcpWimax", "()Z",  (void *)com_htc_net_wimax_startDhcpWimaxDaemon },
    { "TerminateProcess", "(Ljava/lang/String;)Z",  (void *)com_htc_net_wimax_terminateProcess },
    { "AddRouteToGateway", "()Z",  (void *)com_htc_net_wimax_addRouteToGateway },
    { "DoWimaxDhcpRequest", "(Landroid/net/DhcpInfo;)Z",  (void *)com_htc_net_wimax_doWimaxDhcpRequest },
    { "DoWimaxDhcpRelease", "()Z",  (void *)com_htc_net_wimax_dhcpRelease },
};

int register_com_htc_net_wimax_WimaxController(JNIEnv* env)
{
    jclass commonAPI = env->FindClass(WIMAX_PKG_NAME);
    LOG_FATAL_IF(commonAPI == NULL, "Unable to find class " WIMAX_PKG_NAME);

    return AndroidRuntime::registerNativeMethods(env,
            WIMAX_PKG_NAME, gWimaxMethods, NELEM(gWimaxMethods));
}

}; // namespace android
