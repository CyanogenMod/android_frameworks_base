/*
 * Copyright (C) 2008 The Android Open Source Project
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

#define LOG_TAG "GpsLocationProvider"

#define LOG_NDEBUG 0

#include "JNIHelp.h"
#include "jni.h"
#include "hardware/hardware.h"
#include "hardware/gps.h"
#include "hardware_legacy/power.h"
#include "utils/Log.h"
#include "utils/misc.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>
#include <pthread.h>

static jobject mCallbacksObj = NULL;

static jmethodID method_reportLocation;
static jmethodID method_reportStatus;
static jmethodID method_reportSvStatus;
static jmethodID method_reportAGpsStatus;
static jmethodID method_reportNmea;
static jmethodID method_setEngineCapabilities;
static jmethodID method_xtraDownloadRequest;
static jmethodID method_reportNiNotification;
static jmethodID method_requestRefLocation;
static jmethodID method_requestSetID;

static const GpsInterface* sGpsInterface = NULL;
static const GpsXtraInterface* sGpsXtraInterface = NULL;
static const AGpsInterface* sAGpsInterface = NULL;
static const GpsNiInterface* sGpsNiInterface = NULL;
static const GpsDebugInterface* sGpsDebugInterface = NULL;
static const AGpsRilInterface* sAGpsRilInterface = NULL;

// temporary storage for GPS callbacks
static GpsSvStatus  sGpsSvStatus;
static const char* sNmeaString;
static int sNmeaStringLength;

#define WAKE_LOCK_NAME  "GPS"

namespace android {

static void checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
    if (env->ExceptionCheck()) {
        LOGE("An exception was thrown by callback '%s'.", methodName);
        LOGE_EX(env);
        env->ExceptionClear();
    }
}

static void location_callback(GpsLocation* location)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_reportLocation, location->flags,
            (jdouble)location->latitude, (jdouble)location->longitude,
            (jdouble)location->altitude,
            (jfloat)location->speed, (jfloat)location->bearing,
            (jfloat)location->accuracy, (jlong)location->timestamp);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void status_callback(GpsStatus* status)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_reportStatus, status->status);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void sv_status_callback(GpsSvStatus* sv_status)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    memcpy(&sGpsSvStatus, sv_status, sizeof(sGpsSvStatus));
    env->CallVoidMethod(mCallbacksObj, method_reportSvStatus);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void nmea_callback(GpsUtcTime timestamp, const char* nmea, int length)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    // The Java code will call back to read these values
    // We do this to avoid creating unnecessary String objects
    sNmeaString = nmea;
    sNmeaStringLength = length;
    env->CallVoidMethod(mCallbacksObj, method_reportNmea, timestamp);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void set_capabilities_callback(uint32_t capabilities)
{
    LOGD("set_capabilities_callback: %ld\n", capabilities);
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_setEngineCapabilities, capabilities);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void acquire_wakelock_callback()
{
    acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
}

static void release_wakelock_callback()
{
    release_wake_lock(WAKE_LOCK_NAME);
}

static pthread_t create_thread_callback(const char* name, void (*start)(void *), void* arg)
{
    return (pthread_t)AndroidRuntime::createJavaThread(name, start, arg);
}

GpsCallbacks sGpsCallbacks = {
    sizeof(GpsCallbacks),
    location_callback,
    status_callback,
    sv_status_callback,
    nmea_callback,
    set_capabilities_callback,
    acquire_wakelock_callback,
    release_wakelock_callback,
    create_thread_callback,
};

static void xtra_download_request_callback()
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_xtraDownloadRequest);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

GpsXtraCallbacks sGpsXtraCallbacks = {
    xtra_download_request_callback,
    create_thread_callback,
};

static void agps_status_callback(AGpsStatus* agps_status)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_reportAGpsStatus,
                        agps_status->type, agps_status->status);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

AGpsCallbacks sAGpsCallbacks = {
    agps_status_callback,
    create_thread_callback,
};

static void gps_ni_notify_callback(GpsNiNotification *notification)
{
    LOGD("gps_ni_notify_callback\n");
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    jstring requestor_id = env->NewStringUTF(notification->requestor_id);
    jstring text = env->NewStringUTF(notification->text);
    jstring extras = env->NewStringUTF(notification->extras);

    if (requestor_id && text && extras) {
        env->CallVoidMethod(mCallbacksObj, method_reportNiNotification,
            notification->notification_id, notification->ni_type,
            notification->notify_flags, notification->timeout,
            notification->default_response, requestor_id, text,
            notification->requestor_id_encoding,
            notification->text_encoding, extras);
    } else {
        LOGE("out of memory in gps_ni_notify_callback\n");
    }

    if (requestor_id)
        env->DeleteLocalRef(requestor_id);
    if (text)
        env->DeleteLocalRef(text);
    if (extras)
        env->DeleteLocalRef(extras);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

GpsNiCallbacks sGpsNiCallbacks = {
    gps_ni_notify_callback,
    create_thread_callback,
};

static void agps_request_set_id(uint32_t flags)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestSetID, flags);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

static void agps_request_ref_location(uint32_t flags)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->CallVoidMethod(mCallbacksObj, method_requestRefLocation, flags);
    checkAndClearExceptionFromCallback(env, __FUNCTION__);
}

AGpsRilCallbacks sAGpsRilCallbacks = {
    agps_request_set_id,
    agps_request_ref_location,
    create_thread_callback,
};

static void android_location_GpsLocationProvider_class_init_native(JNIEnv* env, jclass clazz) {
    int err;
    hw_module_t* module;

    method_reportLocation = env->GetMethodID(clazz, "reportLocation", "(IDDDFFFJ)V");
    method_reportStatus = env->GetMethodID(clazz, "reportStatus", "(I)V");
    method_reportSvStatus = env->GetMethodID(clazz, "reportSvStatus", "()V");
    method_reportAGpsStatus = env->GetMethodID(clazz, "reportAGpsStatus", "(II)V");
    method_reportNmea = env->GetMethodID(clazz, "reportNmea", "(J)V");
    method_setEngineCapabilities = env->GetMethodID(clazz, "setEngineCapabilities", "(I)V");
    method_xtraDownloadRequest = env->GetMethodID(clazz, "xtraDownloadRequest", "()V");
    method_reportNiNotification = env->GetMethodID(clazz, "reportNiNotification",
            "(IIIIILjava/lang/String;Ljava/lang/String;IILjava/lang/String;)V");
    method_requestRefLocation = env->GetMethodID(clazz,"requestRefLocation","(I)V");
    method_requestSetID = env->GetMethodID(clazz,"requestSetID","(I)V");

    err = hw_get_module(GPS_HARDWARE_MODULE_ID, (hw_module_t const**)&module);
    if (err == 0) {
        hw_device_t* device;
        err = module->methods->open(module, GPS_HARDWARE_MODULE_ID, &device);
        if (err == 0) {
            gps_device_t* gps_device = (gps_device_t *)device;
            sGpsInterface = gps_device->get_gps_interface(gps_device);
        }
    }
    if (sGpsInterface) {
        sGpsXtraInterface =
            (const GpsXtraInterface*)sGpsInterface->get_extension(GPS_XTRA_INTERFACE);
        sAGpsInterface =
            (const AGpsInterface*)sGpsInterface->get_extension(AGPS_INTERFACE);
        sGpsNiInterface =
            (const GpsNiInterface*)sGpsInterface->get_extension(GPS_NI_INTERFACE);
        sGpsDebugInterface =
            (const GpsDebugInterface*)sGpsInterface->get_extension(GPS_DEBUG_INTERFACE);
        sAGpsRilInterface =
            (const AGpsRilInterface*)sGpsInterface->get_extension(AGPS_RIL_INTERFACE);
    }
}

static jboolean android_location_GpsLocationProvider_is_supported(JNIEnv* env, jclass clazz) {
    return (sGpsInterface != NULL);
}

static jboolean android_location_GpsLocationProvider_init(JNIEnv* env, jobject obj)
{
    // this must be set before calling into the HAL library
    if (!mCallbacksObj)
        mCallbacksObj = env->NewGlobalRef(obj);

    // fail if the main interface fails to initialize
    if (!sGpsInterface || sGpsInterface->init(&sGpsCallbacks) != 0)
        return false;

    // if XTRA initialization fails we will disable it by sGpsXtraInterface to null,
    // but continue to allow the rest of the GPS interface to work.
    if (sGpsXtraInterface && sGpsXtraInterface->init(&sGpsXtraCallbacks) != 0)
        sGpsXtraInterface = NULL;
    if (sAGpsInterface)
        sAGpsInterface->init(&sAGpsCallbacks);
    if (sGpsNiInterface)
        sGpsNiInterface->init(&sGpsNiCallbacks);
    if (sAGpsRilInterface)
        sAGpsRilInterface->init(&sAGpsRilCallbacks);

    return true;
}

static void android_location_GpsLocationProvider_cleanup(JNIEnv* env, jobject obj)
{
    if (sGpsInterface) {
        sGpsInterface->cleanup();
    }
}

static jboolean android_location_GpsLocationProvider_set_position_mode(JNIEnv* env, jobject obj,
        jint mode, jint recurrence, jint min_interval, jint preferred_accuracy, jint preferred_time)
{
    if (sGpsInterface)
        return (sGpsInterface->set_position_mode(mode, recurrence, min_interval, preferred_accuracy,
                preferred_time) == 0);
    else
        return false;
}

static jboolean android_location_GpsLocationProvider_start(JNIEnv* env, jobject obj)
{
    if (sGpsInterface)
        return (sGpsInterface->start() == 0);
    else
        return false;
}

static jboolean android_location_GpsLocationProvider_stop(JNIEnv* env, jobject obj)
{
    if (sGpsInterface)
        return (sGpsInterface->stop() == 0);
    else
        return false;
}

static void android_location_GpsLocationProvider_delete_aiding_data(JNIEnv* env, jobject obj, jint flags)
{
    if (sGpsInterface)
        sGpsInterface->delete_aiding_data(flags);
}

static jint android_location_GpsLocationProvider_read_sv_status(JNIEnv* env, jobject obj,
        jintArray prnArray, jfloatArray snrArray, jfloatArray elevArray, jfloatArray azumArray,
        jintArray maskArray)
{
    // this should only be called from within a call to reportSvStatus

    jint* prns = env->GetIntArrayElements(prnArray, 0);
    jfloat* snrs = env->GetFloatArrayElements(snrArray, 0);
    jfloat* elev = env->GetFloatArrayElements(elevArray, 0);
    jfloat* azim = env->GetFloatArrayElements(azumArray, 0);
    jint* mask = env->GetIntArrayElements(maskArray, 0);

    int num_svs = sGpsSvStatus.num_svs;
    for (int i = 0; i < num_svs; i++) {
        prns[i] = sGpsSvStatus.sv_list[i].prn;
        snrs[i] = sGpsSvStatus.sv_list[i].snr;
        elev[i] = sGpsSvStatus.sv_list[i].elevation;
        azim[i] = sGpsSvStatus.sv_list[i].azimuth;
    }
    mask[0] = sGpsSvStatus.ephemeris_mask;
    mask[1] = sGpsSvStatus.almanac_mask;
    mask[2] = sGpsSvStatus.used_in_fix_mask;

    env->ReleaseIntArrayElements(prnArray, prns, 0);
    env->ReleaseFloatArrayElements(snrArray, snrs, 0);
    env->ReleaseFloatArrayElements(elevArray, elev, 0);
    env->ReleaseFloatArrayElements(azumArray, azim, 0);
    env->ReleaseIntArrayElements(maskArray, mask, 0);
    return num_svs;
}

static void android_location_GpsLocationProvider_agps_set_reference_location_cellid(JNIEnv* env,
        jobject obj, jint type, jint mcc, jint mnc, jint lac, jint cid)
{
    AGpsRefLocation location;

    if (!sAGpsRilInterface) {
        LOGE("no AGPS RIL interface in agps_set_reference_location_cellid");
        return;
    }

    switch(type) {
        case AGPS_REF_LOCATION_TYPE_GSM_CELLID:
        case AGPS_REF_LOCATION_TYPE_UMTS_CELLID:
            location.type = type;
            location.u.cellID.mcc = mcc;
            location.u.cellID.mnc = mnc;
            location.u.cellID.lac = lac;
            location.u.cellID.cid = cid;
            break;
        default:
            LOGE("Neither a GSM nor a UMTS cellid (%s:%d).",__FUNCTION__,__LINE__);
            return;
            break;
    }
    sAGpsRilInterface->set_ref_location(&location, sizeof(location));
}

static void android_location_GpsLocationProvider_agps_send_ni_message(JNIEnv* env,
        jobject obj, jbyteArray ni_msg, jint size)
{
    size_t sz;

    if (!sAGpsRilInterface) {
        LOGE("no AGPS RIL interface in send_ni_message");
        return;
    }
    if (size < 0)
        return;
    sz = (size_t)size;
    jbyte* b = env->GetByteArrayElements(ni_msg, 0);
    sAGpsRilInterface->ni_message((uint8_t *)b,sz);
    env->ReleaseByteArrayElements(ni_msg,b,0);
}

static void android_location_GpsLocationProvider_agps_set_id(JNIEnv *env,
        jobject obj, jint type, jstring  setid_string)
{
    if (!sAGpsRilInterface) {
        LOGE("no AGPS RIL interface in agps_set_id");
        return;
    }

    const char *setid = env->GetStringUTFChars(setid_string, NULL);
    sAGpsRilInterface->set_set_id(type, setid);
    env->ReleaseStringUTFChars(setid_string, setid);
}

static jint android_location_GpsLocationProvider_read_nmea(JNIEnv* env, jobject obj,
                                            jbyteArray nmeaArray, jint buffer_size)
{
    // this should only be called from within a call to reportNmea
    jbyte* nmea = (jbyte *)env->GetPrimitiveArrayCritical(nmeaArray, 0);
    int length = sNmeaStringLength;
    if (length > buffer_size)
        length = buffer_size;
    memcpy(nmea, sNmeaString, length);
    env->ReleasePrimitiveArrayCritical(nmeaArray, nmea, JNI_ABORT);
    return length;
}

static void android_location_GpsLocationProvider_inject_time(JNIEnv* env, jobject obj,
        jlong time, jlong timeReference, jint uncertainty)
{
    if (sGpsInterface)
        sGpsInterface->inject_time(time, timeReference, uncertainty);
}

static void android_location_GpsLocationProvider_inject_location(JNIEnv* env, jobject obj,
        jdouble latitude, jdouble longitude, jfloat accuracy)
{
    if (sGpsInterface)
        sGpsInterface->inject_location(latitude, longitude, accuracy);
}

static jboolean android_location_GpsLocationProvider_supports_xtra(JNIEnv* env, jobject obj)
{
    return (sGpsXtraInterface != NULL);
}

static void android_location_GpsLocationProvider_inject_xtra_data(JNIEnv* env, jobject obj,
        jbyteArray data, jint length)
{
    if (!sGpsXtraInterface) {
        LOGE("no XTRA interface in inject_xtra_data");
        return;
    }

    jbyte* bytes = (jbyte *)env->GetPrimitiveArrayCritical(data, 0);
    sGpsXtraInterface->inject_xtra_data((char *)bytes, length);
    env->ReleasePrimitiveArrayCritical(data, bytes, JNI_ABORT);
}

static void android_location_GpsLocationProvider_agps_data_conn_open(JNIEnv* env, jobject obj, jstring apn)
{
    if (!sAGpsInterface) {
        LOGE("no AGPS interface in agps_data_conn_open");
        return;
    }
    if (apn == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return;
    }
    const char *apnStr = env->GetStringUTFChars(apn, NULL);
    sAGpsInterface->data_conn_open(apnStr);
    env->ReleaseStringUTFChars(apn, apnStr);
}

static void android_location_GpsLocationProvider_agps_data_conn_closed(JNIEnv* env, jobject obj)
{
    if (!sAGpsInterface) {
        LOGE("no AGPS interface in agps_data_conn_open");
        return;
    }
    sAGpsInterface->data_conn_closed();
}

static void android_location_GpsLocationProvider_agps_data_conn_failed(JNIEnv* env, jobject obj)
{
    if (!sAGpsInterface) {
        LOGE("no AGPS interface in agps_data_conn_open");
        return;
    }
    sAGpsInterface->data_conn_failed();
}

static void android_location_GpsLocationProvider_set_agps_server(JNIEnv* env, jobject obj,
        jint type, jstring hostname, jint port)
{
    if (!sAGpsInterface) {
        LOGE("no AGPS interface in agps_data_conn_open");
        return;
    }
    const char *c_hostname = env->GetStringUTFChars(hostname, NULL);
    sAGpsInterface->set_server(type, c_hostname, port);
    env->ReleaseStringUTFChars(hostname, c_hostname);
}

static void android_location_GpsLocationProvider_send_ni_response(JNIEnv* env, jobject obj,
      jint notifId, jint response)
{
    if (!sGpsNiInterface) {
        LOGE("no NI interface in send_ni_response");
        return;
    }

    sGpsNiInterface->respond(notifId, response);
}

static jstring android_location_GpsLocationProvider_get_internal_state(JNIEnv* env, jobject obj)
{
    jstring result = NULL;
    if (sGpsDebugInterface) {
        const size_t maxLength = 2047;
        char buffer[maxLength+1];
        size_t length = sGpsDebugInterface->get_internal_state(buffer, maxLength);
        if (length > maxLength) length = maxLength;
        buffer[length] = 0;
        result = env->NewStringUTF(buffer);
    }
    return result;
}

static void android_location_GpsLocationProvider_update_network_state(JNIEnv* env, jobject obj,
        jboolean connected, int type, jboolean roaming, jstring extraInfo)
{

    if (sAGpsRilInterface && sAGpsRilInterface->update_network_state) {
        if (extraInfo) {
            const char *extraInfoStr = env->GetStringUTFChars(extraInfo, NULL);
            sAGpsRilInterface->update_network_state(connected, type, roaming, extraInfoStr);
            env->ReleaseStringUTFChars(extraInfo, extraInfoStr);
        } else {
            sAGpsRilInterface->update_network_state(connected, type, roaming, NULL);
        }
    }
}

static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
    {"class_init_native", "()V", (void *)android_location_GpsLocationProvider_class_init_native},
    {"native_is_supported", "()Z", (void*)android_location_GpsLocationProvider_is_supported},
    {"native_init", "()Z", (void*)android_location_GpsLocationProvider_init},
    {"native_cleanup", "()V", (void*)android_location_GpsLocationProvider_cleanup},
    {"native_set_position_mode", "(IIIII)Z", (void*)android_location_GpsLocationProvider_set_position_mode},
    {"native_start", "()Z", (void*)android_location_GpsLocationProvider_start},
    {"native_stop", "()Z", (void*)android_location_GpsLocationProvider_stop},
    {"native_delete_aiding_data", "(I)V", (void*)android_location_GpsLocationProvider_delete_aiding_data},
    {"native_read_sv_status", "([I[F[F[F[I)I", (void*)android_location_GpsLocationProvider_read_sv_status},
    {"native_read_nmea", "([BI)I", (void*)android_location_GpsLocationProvider_read_nmea},
    {"native_inject_time", "(JJI)V", (void*)android_location_GpsLocationProvider_inject_time},
    {"native_inject_location", "(DDF)V", (void*)android_location_GpsLocationProvider_inject_location},
    {"native_supports_xtra", "()Z", (void*)android_location_GpsLocationProvider_supports_xtra},
    {"native_inject_xtra_data", "([BI)V", (void*)android_location_GpsLocationProvider_inject_xtra_data},
    {"native_agps_data_conn_open", "(Ljava/lang/String;)V", (void*)android_location_GpsLocationProvider_agps_data_conn_open},
    {"native_agps_data_conn_closed", "()V", (void*)android_location_GpsLocationProvider_agps_data_conn_closed},
    {"native_agps_data_conn_failed", "()V", (void*)android_location_GpsLocationProvider_agps_data_conn_failed},
    {"native_agps_set_id","(ILjava/lang/String;)V",(void*)android_location_GpsLocationProvider_agps_set_id},
    {"native_agps_set_ref_location_cellid","(IIIII)V",(void*)android_location_GpsLocationProvider_agps_set_reference_location_cellid},
    {"native_set_agps_server", "(ILjava/lang/String;I)V", (void*)android_location_GpsLocationProvider_set_agps_server},
    {"native_send_ni_response", "(II)V", (void*)android_location_GpsLocationProvider_send_ni_response},
    {"native_agps_ni_message", "([BI)V", (void *)android_location_GpsLocationProvider_agps_send_ni_message},
    {"native_get_internal_state", "()Ljava/lang/String;", (void*)android_location_GpsLocationProvider_get_internal_state},
    {"native_update_network_state", "(ZIZLjava/lang/String;)V", (void*)android_location_GpsLocationProvider_update_network_state },
};

int register_android_server_location_GpsLocationProvider(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/location/GpsLocationProvider", sMethods, NELEM(sMethods));
}

} /* namespace android */
