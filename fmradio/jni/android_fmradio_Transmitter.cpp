/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright 2010, The Android Open Source Project
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
 *
 * Authors: johan.xj.palmaeus@stericsson.com
 *          stuart.macdonald@stericsson.com
 *          for ST-Ericsson
 */

/*
 * Native part of the generic TX FmRadio inteface
 */

#define ALOG_TAG "FmTransmitterServiceNative"

// #define LOG_NDEBUG 1

#include <stdio.h>
#include <unistd.h>
#include <termios.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <stdarg.h>
#include <pthread.h>
#include <media/AudioSystem.h>
#include <system/audio.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_fmradio.h"
#include <utils/Log.h>


/* *INDENT-OFF* */
namespace android {

// RDS Fields

static const char* rds_field_names[] = {
    "PI",
    "TP",
    "PTY",
    "TA",
    "M/S",
    "AF",
    "numAFs",
    "PSN",
    "RT",
    "CT",
    "PTYN",
    "TMC",
    "TAF",
    NULL
};


// state machine

static const ValidEventsForStates_t IsValidTxEventForState = {
/* this table defines valid transitions. (turn off indent, we want this easy readable) */
         /* FMRADIO_STATE_  IDLE,STARTING,STARTED,PAUSED,SCANNING,EXTRA_COMMAND */

   /* FMRADIO_EVENT_START */         {true, false,false,false,false,false},
   /* FMRADIO_EVENT_START_ASYNC */   {true, false,false,false,false,false},
   /* FMRADIO_EVENT_PAUSE */         {false,false,true, true, false,false},
   /* FMRADIO_EVENT_RESUME */        {false,false,true, true, false,false},
   /* FMRADIO_EVENT_RESET */         {true, true, true, true, true, true },
   /* FMRADIO_EVENT_GET_FREQUENCY */ {false,false,true, true, false,false},
   /* FMRADIO_EVENT_SET_FREQUENCY */ {false,false,true, true, false,false},
   /* FMRADIO_EVENT_SET_PARAMETER */ {false,false,true, true, false,false},
   /* FMRADIO_EVENT_STOP_SCAN */     {true, true, true, true, true, true },
   /* FMRADIO_EVENT_EXTRA_COMMAND */ {true, true, true, true, true, true },
   /* Rx Only - never allowed */
   /* FMRADIO_EVENT_GET_PARAMETER */ {false,false,false,false,false,false},
   /* FMRADIO_EVENT_GET_SIGNAL_STRENGTH */{false,false,false,false,false,false},
   /* FMRADIO_EVENT_SCAN */          {false,false,false,false,false,false},
   /* FMRADIO_EVENT_FULL_SCAN */     {false,false,false,false,false,false},
   /* Tx Only */
   /* FMRADIO_EVENT_BLOCK_SCAN */    {false,false,true, true, false,false},
};
/* *INDENT-ON* */

static void androidFmRadioTxCallbackOnStateChanged(int oldState,
                                                   int newState);

static void androidFmRadioTxCallbackOnError(void);

static void androidFmRadioTxCallbackOnStarted(void);

static void androidFmRadioTxCallbackOnBlockScan(int noValues,
                                                int *freqs,
                                                int *sigStrengths,
                                                bool aborted);
static void androidFmRadioTxCallbackOnForcedReset(enum fmradio_reset_reason_t reason);

static void androidFmRadioTxCallbackOnVendorForcedReset(enum fmradio_reset_reason_t reason);

static void androidFmRadioTxCallbackOnExtraCommand(char* command,
                                                   struct
                                                   fmradio_extra_command_ret_item_t
                                                   *retList);

static const FmRadioCallbacks_t FmRadioTxCallbacks = {
    androidFmRadioTxCallbackOnStateChanged,
    androidFmRadioTxCallbackOnError,
    androidFmRadioTxCallbackOnStarted,
    NULL,
    NULL,
    androidFmRadioTxCallbackOnBlockScan,
    androidFmRadioTxCallbackOnForcedReset,
    androidFmRadioTxCallbackOnExtraCommand,
};


/* callbacks from vendor layer */

static const fmradio_vendor_callbacks_t FmRadioTxVendorCallbacks = {
    NULL,
    NULL,
    NULL,
    NULL,
    androidFmRadioTxCallbackOnVendorForcedReset
};

extern struct FmSession_t fmReceiverSession;

struct FmSession_t fmTransmitterSession = {
    NULL,
    NULL,
    false,
    FMRADIO_STATE_IDLE,
    NULL,
    &IsValidTxEventForState,
    &FmRadioTxCallbacks,
    NULL,
    NULL,
    &fmReceiverSession,
    NULL,
    FMRADIO_STATE_IDLE,
    false,
    false,
    false,
    &rx_tx_common_mutex,
    PTHREAD_COND_INITIALIZER,
    NULL,
};

struct FmRadioBlockScanParameters {
    int startFreq;
    int endFreq;
};

// make sure we don't refere the ReceiverSession anymore from here
#define fmReceiverSession ERRORDONOTUSERECEIVERSESSIONINTRANSMITTER

/*
* Implementation of callbacks from within service layer. For these the
*  mutex lock is always held on entry and need to be released before doing
*  calls to java layer (env->Call*Method)  becasue these calls might trigger
*  new calls from java and a deadlock would occure if lock was still held.
*/


static void androidFmRadioTxCallbackOnStateChanged(int oldState,
                                                   int newState)
{
    jmethodID notifyOnStateChangedMethod;
    JNIEnv *env;
    jclass clazz;
    bool reAttached = false;

    ALOGI("androidFmRadioTxCallbackOnStateChanged: Old state %d, new state %d", oldState, newState);

    /* since we might be both in main thread and subthread both test getenv
     * and attach */
    if (fmTransmitterSession.jvm_p->
        GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        reAttached = true;
        if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
            ALOGE("Error, can't attach current thread");
            return;
        }
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);

    notifyOnStateChangedMethod =
        env->GetMethodID(clazz, "notifyOnStateChanged", "(II)V");
    if (notifyOnStateChangedMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj,
                            notifyOnStateChangedMethod, oldState,
                            newState);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    }
    if (reAttached) {
        fmTransmitterSession.jvm_p->DetachCurrentThread();
    }
}

static void androidFmRadioTxCallbackOnError(void)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioTxCallbackOnError");


    if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);
    notifyMethod = env->GetMethodID(clazz, "notifyOnError", "()V");

    if (notifyMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnError method");
    }

    fmTransmitterSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioTxCallbackOnStarted(void)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    status_t err;

    ALOGI("androidFmRadioTxCallbackOnStarted: Callback");

    if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);
    notifyMethod = env->GetMethodID(clazz, "notifyOnStarted", "()V");

    if (notifyMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnStarted method");
    }

   // err =
        //AudioSystem::
      //  setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
       //                          AUDIO_POLICY_DEVICE_STATE_AVAILABLE, "");

    if (err != OK) {
        ALOGE("ERROR - Unable to set audio output device to FM Radio TX");
       // AudioSystem::
        //    setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
           //                          AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
           //                          "");
    }

    fmTransmitterSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioTxCallbackOnBlockScan(int noItems,
                                                int *freqs,
                                                int *sigStrengths,
                                                bool aborted)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    jintArray jFreqs;
    jintArray jSigStrengths;
    int d;

    ALOGI("androidFmRadioTxCallbackOnBlockScan: No items %d, aborted %d",
         noItems, aborted);

    for (d = 0; d < noItems; d++) {
        ALOGI("%d->%d", freqs[d], sigStrengths[d]);
    }

    if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);

    jFreqs = env->NewIntArray(noItems);
    jSigStrengths = env->NewIntArray(noItems);

    env->SetIntArrayRegion(jFreqs, 0, noItems, freqs);
    env->SetIntArrayRegion(jSigStrengths, 0, noItems, sigStrengths);

    notifyMethod =
        env->GetMethodID(clazz, "notifyOnBlockScan", "([I[IZ)V");


    if (notifyMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            jFreqs, jSigStrengths, aborted);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnBlockScan method");
    }

    fmTransmitterSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioTxCallbackOnForcedReset(enum fmradio_reset_reason_t reason)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    bool reAttached = false;

    ALOGI("androidFmRadioTxCallbackOnForcedReset");

    if (fmTransmitterSession.jvm_p->
        GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        reAttached = true;
        if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
            ALOGE("Error, can't attch current thread");
            return;
        }
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);

    notifyMethod = env->GetMethodID(clazz, "notifyOnForcedReset", "(I)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod, reason);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    }

    //AudioSystem::
      //  setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
         //                        AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
          //                       "");

    if (reAttached) {
        fmTransmitterSession.jvm_p->DetachCurrentThread();
    }
}

static void androidFmRadioTxCallbackOnVendorForcedReset(enum fmradio_reset_reason_t reason)
{
    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    if (fmTransmitterSession.state != FMRADIO_STATE_IDLE) {
        FMRADIO_SET_STATE(&fmTransmitterSession, FMRADIO_STATE_IDLE);
        androidFmRadioUnLoadFmLibrary(&fmTransmitterSession);
        fmTransmitterSession.isRegistered = false;
    }
    fmTransmitterSession.callbacks_p->onForcedReset(reason);
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
}

static void androidFmRadioTxCallbackOnExtraCommand(char* command,
                                                   struct
                                                   fmradio_extra_command_ret_item_t
                                                   *retList)
{
    jmethodID notifyMethod;

    JNIEnv *env;

    jclass clazz;

    struct bundle_descriptor_offsets_t *bundle_p =
        fmTransmitterSession.bundleOffsets_p;
    ALOGI("androidFmRadioTxCallbackOnExtraCommand");

    if (fmTransmitterSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmTransmitterSession.jobj);

    jobject retBundle = extraCommandRetList2Bundle(env, bundle_p, retList);
    jstring jcommand = env->NewStringUTF(command);

    notifyMethod =
        env->GetMethodID(clazz, "notifyOnExtraCommand",
                         "(Ljava/lang/String;Landroid/os/Bundle;)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmTransmitterSession.jobj;
        pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            jcommand, retBundle);
        pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    }

    fmTransmitterSession.jvm_p->DetachCurrentThread();
}

/*
 *  function calls from java layer
 */

static jint androidFmRadioTxGetState(JNIEnv * env, jobject obj)
{
    FmRadioState_t state;

    ALOGI("androidFmRadioTxGetState\n");

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    state = fmTransmitterSession.state;
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);

    return state;
}

/* common ones with rx, just forward to the generic androidFmRadioxxxxx version */

static void
androidFmRadioTxStart(JNIEnv * env, jobject obj, int lowFreq,
                      int highFreq, int defaultFreq, int grid)
{
    int retval;

    status_t err;

    ALOGI("androidFmRadioTxStart...");

    if (fmTransmitterSession.jobj == NULL)
        fmTransmitterSession.jobj = env->NewGlobalRef(obj);

    retval =
        androidFmRadioStart(&fmTransmitterSession, FMRADIO_TX,
                            &FmRadioTxVendorCallbacks, false, lowFreq,
                            highFreq, defaultFreq, grid);
    if (retval >= 0) {
        //err =
        //    AudioSystem::
           // setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
            //                         AUDIO_POLICY_DEVICE_STATE_AVAILABLE,
             //                        "");

        if (err != OK) {
            ALOGE("ERROR - Unable to set audio output device to FM Radio TX");
           // (void) AudioSystem::setDeviceConnectionState
            //    (AUDIO_DEVICE_OUT_FM_TX,
             //    AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE, "");
        }
    }

}

static void
androidFmRadioTxStartAsync(JNIEnv * env, jobject obj, int lowFreq,
                           int highFreq, int defaultFreq, int grid)
{
    ALOGI("androidFmRadioTxStartAsync...");


    if (fmTransmitterSession.jobj == NULL)
        fmTransmitterSession.jobj = env->NewGlobalRef(obj);

    androidFmRadioStart(&fmTransmitterSession, FMRADIO_TX,
                        &FmRadioTxVendorCallbacks, true, lowFreq, highFreq,
                        defaultFreq, grid);
}

static void androidFmRadioTxPause(JNIEnv * env, jobject obj)
{
    int retval;

    ALOGI("androidFmRadioTxPause\n");

    retval = androidFmRadioPause(&fmTransmitterSession);

    if (retval >= 0) {
        //AudioSystem::
           // setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
              //                       AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
               //                      "");
    }
}

static void androidFmRadioTxResume(JNIEnv * env, jobject obj)
{
    int retval;

    ALOGI("androidFmResumeTxResume\n");
    retval = androidFmRadioResume(&fmTransmitterSession);

    if (retval >= 0) {
       // status_t err =
           // AudioSystem::
            //setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
               //                      AUDIO_POLICY_DEVICE_STATE_AVAILABLE, "");

        //if (err != OK) {
            ALOGE("ERROR - Unable to set audio output device to FM Radio TX\n");
            //AudioSystem::
               // setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
                    //                     AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
                      //                   "");
       // }
    }
}

static jint androidFmRadioTxReset(JNIEnv * env, jobject obj)
{
    int retval;

    ALOGI("androidFmRadioTxReset");

    retval = androidFmRadioReset(&fmTransmitterSession);

    if (retval >= 0) {
        if (retval != FMRADIO_STATE_IDLE) {
               // (void) AudioSystem::setDeviceConnectionState(AUDIO_DEVICE_OUT_FM_TX,
                //                                     AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
                   //                                  "");
        }


        if (fmTransmitterSession.state == FMRADIO_STATE_IDLE &&
            fmTransmitterSession.jobj != NULL) {
            env->DeleteGlobalRef(fmTransmitterSession.jobj);
            fmTransmitterSession.jobj = NULL;
        }
    }

    return retval;
}

static void
androidFmRadioTxSetFrequency(JNIEnv * env, jobject obj, jlong frequency)
{
    ALOGI("androidFmRadioTxSetFrequency tuneTo:%d\n", (int) frequency);
    androidFmRadioSetFrequency(&fmTransmitterSession, (int) frequency);
}

static void
androidFmRadioTxSetRDSData(JNIEnv * env, jobject obj, jobject bundle)
{
    ALOGI("androidFmRadioTxSetRDSData start");

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmTransmitterSession, FMRADIO_EVENT_SET_PARAMETER)) {
        THROW_INVALID_STATE(&fmTransmitterSession);
        goto drop_lock;
    }

    /* if in pause state temporary resume */
    androidFmRadioTempResumeIfPaused(&fmTransmitterSession);

    if (bundle == NULL) {
        /* just shut down RDS transmission and leave */
        fmTransmitterSession.vendorMethods_p->
            set_rds_data(&fmTransmitterSession.vendorData_p, NULL, NULL);
        goto resume_and_drop_lock;
    };

    /* new block to control variable life time */
    {
        struct bundle_descriptor_offsets_t *bundle_p =
            fmTransmitterSession.bundleOffsets_p;
        jobject keys_set = env->CallObjectMethod(bundle, bundle_p->mKeySet);
        jclass setClass = env->FindClass("java/util/Set");
        jclass entryClass = env->FindClass("java/lang/String");
        jmethodID iterator =
            env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject iter = env->CallObjectMethod(keys_set, iterator);
        jobject iter2 = env->CallObjectMethod(keys_set, iterator);
        jclass iteratorClass = env->FindClass("java/util/Iterator");
        jmethodID hasNext = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID next =
            env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");
        jmethodID getString =
            env->GetMethodID(entryClass, "toString", "()Ljava/lang/String;");

        while (env->CallBooleanMethod(iter, hasNext)) {
            int i;
            jobject entry = env->CallObjectMethod(iter, next);
            jstring string = (jstring) env->CallObjectMethod(entry, getString);
            const char *str = env->GetStringUTFChars(string, NULL);
            int found = 0;

            if (!str) {             // Out of memory
                THROW_IO_ERROR(&fmTransmitterSession); /* excecution will continue to cleanup */
                env->DeleteLocalRef(entry);
                env->DeleteLocalRef(string);
                goto free_up_and_leave;
            }

            for (i = 0; rds_field_names[i] != NULL; i++) {
                if (!strcmp(rds_field_names[i], str)) {
                    found = 1;
                    break;
                }
            }

            env->DeleteLocalRef(entry);
            env->ReleaseStringUTFChars(string, str);
            env->DeleteLocalRef(string);

            if (!found) {
                ALOGE("androidFmRadioTxSetRDSData: Error, invalid key");
                THROW_ILLEGAL_ARGUMENT(&fmTransmitterSession); /* excecution will continue to cleanup */
                goto free_up_and_leave;
            }
        }

        while (env->CallBooleanMethod(iter2, hasNext)) {
            jobject entry = env->CallObjectMethod(iter2, next);
            jstring string = (jstring) env->CallObjectMethod(entry, getString);
            char *str = (char *) env->GetStringUTFChars(string, NULL);
            int rv = -1;

            if (!str) {             // Out of memory
                ALOGE("androidFmRadioTxSetRDSData: out of memory");
                THROW_IO_ERROR(&fmTransmitterSession); /* excecution will continue to cleanup */
                rv = -2; /* not -1 since we already thrown exception */
            } else if ((strcmp(str, "PI") == 0) ||
                       (strcmp(str, "TP") == 0) ||
                       (strcmp(str, "PTY") == 0) ||
                       (strcmp(str, "TA") == 0) ||
                       (strcmp(str, "M/S") == 0)) {
                /* types setting numeric (short) value */
                int passedval = 0;
                short value = env->CallShortMethod(bundle, bundle_p->mGetShort,
                                                   env->NewStringUTF(str));

                passedval = (int) value;
                rv = fmTransmitterSession.
                    vendorMethods_p->set_rds_data(&fmTransmitterSession.
                                                  vendorData_p, str,
                                                  &passedval);
            } else if (!strcmp(str, "TAF")) {
                /* type setting numeric (int) value */
                int value = env->CallIntMethod(bundle, bundle_p->mGetInt,
                                               env->NewStringUTF(str));

                rv = fmTransmitterSession.
                    vendorMethods_p->set_rds_data(&fmTransmitterSession.
                                                  vendorData_p, str,
                                                  &value);
            } else if (strcmp(str, "AF") == 0) {
                /* type setting array of ints */
                jintArray value = (jintArray) env->CallObjectMethod(bundle,
                                                                    bundle_p->mGetIntArray,
                                                                    env->NewStringUTF
                                                                    (str));

                int numInts = (value ? env->GetArrayLength(value) : 0);

                if (numInts == 0) {
                    env->DeleteLocalRef(entry);
                    env->ReleaseStringUTFChars(string, str);
                    env->DeleteLocalRef(string);
                    goto free_up_and_leave;
                }

                int *temparray = env->GetIntArrayElements(value, NULL);
                int *array = (int *) malloc((numInts + 1) * sizeof(*temparray));


                if (array != NULL) {
                    // Place a 0 after the final entry
                    memcpy(array, temparray, numInts * sizeof(*temparray));
                    array[numInts] = 0;
                    rv = fmTransmitterSession.vendorMethods_p->
                        set_rds_data(&fmTransmitterSession.vendorData_p, str, array);
                    free(array);
                } else {
                    ALOGE("android_setRdsData:malloc failed");
                    rv = -1;
                }
                env->ReleaseIntArrayElements(value, temparray, 0);
            } else if (strcmp(str, "TMC") == 0) {
                /* type setting array of shorts */
                jshortArray value = (jshortArray) env->CallObjectMethod(bundle,
                                                                        bundle_p->mGetShortArray,
                                                                        env->NewStringUTF
                                                                        ("TMC"));
                int numShorts = (value ? env->GetArrayLength(value) : 0);
                short *temparray = env->GetShortArrayElements(value, NULL);
                short *array  = (short *) malloc((numShorts + 1) * sizeof(*temparray));

                if (array != NULL) {
                    // Place a 0 after the final entry
                    memcpy(array, temparray, numShorts * sizeof(*temparray));
                    array[numShorts] = 0;
                    rv = fmTransmitterSession.vendorMethods_p->
                        set_rds_data(&fmTransmitterSession.vendorData_p, str, array);
                    free(array);
                } else {
                    ALOGE("android_setRdsData:malloc failed");
                    rv = -1;
                }
                env->ReleaseShortArrayElements(value, temparray, 0);
                /* types setting string */
            } else if ((strcmp(str, "PSN") == 0) ||
                       (strcmp(str, "RT") == 0) ||
                       (strcmp(str, "CT") == 0) ||
                       (strcmp(str, "PTYN") == 0)) {
                unsigned int maxLength = 0;

                if (strcmp(str, "PSN") == 0) {
                    maxLength = RDS_PSN_MAX_LENGTH;
                } else if (strcmp(str, "RT") == 0) {
                    maxLength = RDS_RT_MAX_LENGTH;
                } else if (strcmp(str, "CT") == 0) {
                    maxLength = RDS_CT_MAX_LENGTH;
                } else if (strcmp(str, "PTYN") == 0) {
                    maxLength = RDS_PTYN_MAX_LENGTH;
                }

                jstring value = (jstring) env->CallObjectMethod(bundle,
                                                                bundle_p->
                                                                mGetString,
                                                                env->
                                                                NewStringUTF
                                                                (str));

                if (value == NULL) {
                    ALOGI("android_setRdsData:No key found for %s", str);
                    rv = -1;
                } else {
                    const char *cvalue = env->GetStringUTFChars(value, NULL);

                    // May need to add termination char
                    if (strlen(cvalue) > maxLength) {
                        ALOGE("android_setRdsData:%s - Too long value.", str);
                        rv = -1;
                    } else {
                        rv = fmTransmitterSession.vendorMethods_p->
                            set_rds_data(&fmTransmitterSession.vendorData_p, str, (char *) cvalue);
                    }
                    env->ReleaseStringUTFChars(value, cvalue);
                }
            }
            if (rv == FMRADIO_UNSUPPORTED_OPERATION) {
                ALOGE("android_setRdsData: key '%s' unsupported by vendor.", str);
            } else if (rv < 0){
                ALOGE("Error processing key '%s'", str);
                THROW_ILLEGAL_ARGUMENT(&fmTransmitterSession); /* execution will continue to cleanup */
            }
            env->DeleteLocalRef(entry);
            if (str != NULL) {
                env->ReleaseStringUTFChars(string, str);
            }
            env->DeleteLocalRef(string);
            if (rv < 0 && rv != FMRADIO_UNSUPPORTED_OPERATION) {
                break;
            }
        }
    free_up_and_leave:
        env->DeleteLocalRef(entryClass);
        env->DeleteLocalRef(iteratorClass);
        env->DeleteLocalRef(iter);
        env->DeleteLocalRef(iter2);
        env->DeleteLocalRef(setClass);
        env->DeleteLocalRef(keys_set);
    }
 resume_and_drop_lock:
    androidFmRadioPauseIfTempResumed(&fmTransmitterSession);
 drop_lock:
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
}

static jint androidFmRadioTxGetFrequency(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioTxGetFrequency \n");
    return androidFmRadioGetFrequency(&fmTransmitterSession);
}

static void androidFmRadioTxStopScan(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioTxStopScan\n");

    androidFmRadioStopScan(&fmTransmitterSession);
}

static jboolean
androidFmRadioTxIsBlockScanSupported(JNIEnv * env, jobject obj)
{
    bool retval;

    ALOGI("androidFmRadioTxIsBlockScanSupported:\n");

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);

    /* if we haven't register we don't know yet */
    if (!fmTransmitterSession.isRegistered) {
        retval = false;
        goto drop_lock;
    }
    // valid in all states
    if (fmTransmitterSession.vendorMethods_p->block_scan != NULL) {
        retval = true;
    } else {
        retval = false;
    }

  drop_lock:
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
    return retval;
}

static void *execute_androidFmRadioTxBlockScan(void *args_p)
{
    struct FmRadioBlockScanParameters *inArgs_p = (struct FmRadioBlockScanParameters *) args_p;
    int startFreq = inArgs_p->startFreq;
    int endFreq = inArgs_p->endFreq;
    int retval;
    enum FmRadioState_t oldState = fmTransmitterSession.oldState;
    int *rssi_p = NULL;
    int *freqs_p = NULL;

    free(inArgs_p);

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);

    /*
     * we should still be in SCANNING mode, but we can't be 100.00 % sure since
     * main thread released lock before we could run
     *
     */

    if (fmTransmitterSession.state != FMRADIO_STATE_SCANNING) {
        ALOGE("execute_androidFmRadioTxBlockScan - warning, state not scanning\n");
    }

    /*
     * if mode has been changed to IDLE in the mean time by main thread,
     * exit the worker thread gracefully
     */
    if (fmTransmitterSession.state == FMRADIO_STATE_IDLE) {
        goto drop_lock;
    }
    // temporary resume chip if sleeping
    if (oldState == FMRADIO_STATE_PAUSED) {
        (void) fmTransmitterSession.vendorMethods_p->
            resume(&fmTransmitterSession.vendorData_p);
    }

    if (pthread_cond_signal(&fmTransmitterSession.sync_cond) != 0) {
        ALOGE("execute_androidFmRadioTxBlockScan - warning, signal failed\n");
    }
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);

    retval =
        fmTransmitterSession.
        vendorMethods_p->block_scan(&fmTransmitterSession.vendorData_p,
                                   startFreq, endFreq, &freqs_p, &rssi_p);

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);

    /*
     * if state has changed we should keep it, probably a forced pause or
     * forced reset
     */
    if (fmTransmitterSession.state != FMRADIO_STATE_SCANNING) {
        ALOGI("State changed while scanning (state now %d), keeping\n",
             fmTransmitterSession.state);
        retval = -1;
    } else {
        if (fmTransmitterSession.pendingPause) {
            FMRADIO_SET_STATE(&fmTransmitterSession, FMRADIO_STATE_PAUSED);
        } else {
            FMRADIO_SET_STATE(&fmTransmitterSession, oldState);
        }

        fmTransmitterSession.pendingPause = false;
    }

    if (retval >= 0) {
        fmTransmitterSession.callbacks_p->onBlockScan(retval, freqs_p,
                                                      rssi_p,
                                                      fmTransmitterSession.
                                                      lastScanAborted);
    } else {
        fmTransmitterSession.callbacks_p->onError();
    }

    drop_lock:

    if (rssi_p != NULL) {
        free(rssi_p);
    }

    if (freqs_p != NULL) {
        free(freqs_p);
    }

    /* Wake up the main thread if it is currently waiting on the condition variable */
    if (pthread_cond_signal(&fmTransmitterSession.sync_cond) != 0) {
            ALOGE("execute_androidFmRadioTxBlockScan - signal failed\n");
    }
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);

    pthread_exit(NULL);

    return NULL;
}

static void
androidFmRadioTxStartBlockScan(JNIEnv * env, jobject obj,
                               int startFreq, int endFreq)
{
    int retval = 0;

    ALOGI("androidFmRadioTxStartBlockScan, From = %d, To = %d\n",
         startFreq, endFreq);

    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmTransmitterSession, FMRADIO_EVENT_BLOCK_SCAN)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (fmTransmitterSession.vendorMethods_p->block_scan) {
        struct FmRadioBlockScanParameters* args_p = (struct FmRadioBlockScanParameters*) malloc(sizeof(struct FmRadioBlockScanParameters));

        pthread_t execute_thread;

        args_p->startFreq = startFreq;
        args_p->endFreq = endFreq;

        fmTransmitterSession.oldState = fmTransmitterSession.state;

        FMRADIO_SET_STATE(&fmTransmitterSession, FMRADIO_STATE_SCANNING);

        fmTransmitterSession.lastScanAborted = false;

        if (pthread_create
            (&execute_thread, NULL, execute_androidFmRadioTxBlockScan,
             args_p) != 0) {

            ALOGE("pthread_create failure...\n");
            free(args_p);

            FMRADIO_SET_STATE(&fmTransmitterSession, fmTransmitterSession.oldState);
            retval = FMRADIO_IO_ERROR;
        } else {
            /* await thread startup, THREAD_WAIT_TIMEOUT_S sec timeout */
            struct timespec ts;
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += THREAD_WAIT_TIMEOUT_S;
            if (pthread_cond_timedwait(&fmTransmitterSession.sync_cond,
                                       fmTransmitterSession.dataMutex_p,
                                       &ts) != 0) {
                ALOGE("androidFmRadioTxStartBlockScan: warning, wait failure\n");
            }
            pthread_detach(execute_thread);
        }
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmTransmitterSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmTransmitterSession);
    }

    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);
}

static jboolean androidFmRadioTxSendExtraCommand(JNIEnv * env, jobject obj,
                                             jstring command,
                                             jobjectArray parameters)
{
    ALOGI("androidFmRadioTxSendExtraCommand");

    /* we need to set jobj since this might be called before start */


    if (fmTransmitterSession.jobj == NULL)
        fmTransmitterSession.jobj = env->NewGlobalRef(obj);

    androidFmRadioSendExtraCommand(&fmTransmitterSession, env, command,
                                   parameters);

    return true;
}

static JNINativeMethod gMethods[] = {
    {(char*)"_fm_transmitter_getState", (char*)"()I",
     (void *) androidFmRadioTxGetState},
    {(char*)"_fm_transmitter_start", (char*)"(IIII)V",
     (void *) androidFmRadioTxStart},
    {(char*)"_fm_transmitter_startAsync", (char*)"(IIII)V",
     (void *) androidFmRadioTxStartAsync},
    {(char*)"_fm_transmitter_pause", (char*)"()V",
     (void *) androidFmRadioTxPause},
    {(char*)"_fm_transmitter_resume", (char*)"()V",
     (void *) androidFmRadioTxResume},
    {(char*)"_fm_transmitter_reset", (char*)"()I",
     (void *) androidFmRadioTxReset},
    {(char*)"_fm_transmitter_setFrequency", (char*)"(I)V",
     (void *) androidFmRadioTxSetFrequency},
    {(char*)"_fm_transmitter_getFrequency", (char*)"()I",
     (void *) androidFmRadioTxGetFrequency},
    {(char*)"_fm_transmitter_isBlockScanSupported", (char*)"()Z",
     (void *) androidFmRadioTxIsBlockScanSupported},
    {(char*)"_fm_transmitter_startBlockScan", (char*)"(II)V",
     (void *) androidFmRadioTxStartBlockScan},
    {(char*)"_fm_transmitter_stopScan", (char*)"()V",
     (void *) androidFmRadioTxStopScan},
    {(char*)"_fm_transmitter_setRdsData", (char*)"(Landroid/os/Bundle;)V",
     (void *) androidFmRadioTxSetRDSData},
    {(char*)"_fm_transmitter_sendExtraCommand",
     (char*)"(Ljava/lang/String;[Ljava/lang/String;)Z",
     (void *) androidFmRadioTxSendExtraCommand},

};

int registerAndroidFmRadioTransmitter(JavaVM * vm, JNIEnv * env)
{

    ALOGI("registerAndroidFmRadioTransmitter\n");
    pthread_mutex_lock(fmTransmitterSession.dataMutex_p);
    fmTransmitterSession.jvm_p = vm;
    // setRDS bundle handling
    jclass clazz = env->FindClass("android/os/Bundle");


    struct bundle_descriptor_offsets_t *bundle_p =
        (struct bundle_descriptor_offsets_t *)
        malloc(sizeof(struct bundle_descriptor_offsets_t));

    bundle_p->mSize = env->GetMethodID(clazz, "size", "()I");
    bundle_p->mGetInt =
        env->GetMethodID(clazz, "getInt", "(Ljava/lang/String;)I");
    bundle_p->mGetIntArray =
        env->GetMethodID(clazz, "getIntArray", "(Ljava/lang/String;)[I");
    bundle_p->mGetShort =
        env->GetMethodID(clazz, "getShort", "(Ljava/lang/String;)S");
    bundle_p->mGetShortArray =
        env->GetMethodID(clazz, "getShortArray", "(Ljava/lang/String;)[S");
    bundle_p->mGetString =
        env->GetMethodID(clazz, "getString",
                         "(Ljava/lang/String;)Ljava/lang/String;");
    bundle_p->mContainsKey =
        env->GetMethodID(clazz, "containsKey", "(Ljava/lang/String;)Z");
    bundle_p->mKeySet =
        env->GetMethodID(clazz, "keySet", "()Ljava/util/Set;");
    bundle_p->mClass = (jclass) env->NewGlobalRef(clazz);
    bundle_p->mConstructor = env->GetMethodID(clazz, "<init>", "()V");
    bundle_p->mPutInt =
        env->GetMethodID(clazz, "putInt", "(Ljava/lang/String;I)V");
    bundle_p->mPutIntArray =
        env->GetMethodID(clazz, "putIntArray", "(Ljava/lang/String;[I)V");
    bundle_p->mPutShortArray =
        env->GetMethodID(clazz, "putShortArray",
                         "(Ljava/lang/String;[S)V");
    bundle_p->mPutString =
        env->GetMethodID(clazz, "putString",
                         "(Ljava/lang/String;Ljava/lang/String;)V");

    fmTransmitterSession.bundleOffsets_p = bundle_p;
    pthread_mutex_unlock(fmTransmitterSession.dataMutex_p);

    return jniRegisterNativeMethods(env,
                                    "com/stericsson/hardware/fm/FmTransmitterService",
                                    gMethods, NELEM(gMethods));
}

/* *INDENT-OFF* */
};                              // namespace android
