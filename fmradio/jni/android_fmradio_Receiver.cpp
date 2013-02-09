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
 * Native part of the generic RX FmRadio inteface
 */

#define ALOG_TAG "FmReceiverServiceNative"

// #define LOG_NDEBUG 1

#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <termios.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <stdarg.h>
#include <signal.h>
#include <pthread.h>
#include <math.h>


#include "jni.h"
#include "JNIHelp.h"
#include "android_fmradio.h"
#include <utils/Log.h>


/* *INDENT-OFF* */
namespace android {


// state machine

static const ValidEventsForStates_t IsValidRxEventForState = {
  /* this table defines valid transitions. (turn off indent, we want this easy readable) */
             /* FMRADIO_STATE_ IDLE,STARTING,STARTED,PAUSED,SCANNING,EXTRA_COMMAND */

   /* FMRADIO_EVENT_START */         {true ,false,false,false,false,false},
   /* FMRADIO_EVENT_START_ASYNC */   {true ,false,false,false,false,false},
   /* FMRADIO_EVENT_PAUSE */         {false,false,true, true, false,false},
   /* FMRADIO_EVENT_RESUME */        {false,false,true, true, false,false},
   /* FMRADIO_EVENT_RESET */         {true, true, true, true, true, true },
   /* FMRADIO_EVENT_GET_FREQUENCY */ {false,false,true, true, false,false},
   /* FMRADIO_EVENT_SET_FREQUENCY */ {false,false,true, true, false,false},
   /* FMRADIO_EVENT_SET_PARAMETER */ {false,false,true, true, true, true },
   /* FMRADIO_EVENT_STOP_SCAN */     {true, true, true, true, true, true },
   /* FMRADIO_EVENT_EXTRA_COMMAND */ {true, true, true, true, true, true },
   /* Rx Only */
   /* FMRADIO_EVENT_GET_PARAMETER */ {false,false,true, true, true, true },
   /* FMRADIO_EVENT_GET_SIGNAL_STRENGTH */{false,false,true,true,false,false},
   /* FMRADIO_EVENT_SCAN */          {false,false,true, true, false,false},
   /* FMRADIO_EVENT_FULL_SCAN */     {false,false,true, true, false,false},
   // Tx Only - never allowed
   /* FMRADIO_EVENT_BLOCK_SCAN */    {false,false,false,false,false,false},
};
/*  *INDENT-ON*  */

/* Callbacks to java layer */

static void androidFmRadioRxCallbackOnStateChanged(int oldState,
                                                   int newState);
static void androidFmRadioRxCallbackOnError(void);

static void androidFmRadioRxCallbackOnStarted(void);

static void androidFmRadioRxCallbackOnScan(int foundFreq,
                                           int signalStrength,
                                           int scanDirection,
                                           bool aborted);
static void androidFmRadioRxCallbackOnFullScan(int noItems,
                                               int *frequencies,
                                               int *sigStrengths,
                                               bool aborted);
static void androidFmRadioRxCallbackOnForcedReset(enum fmradio_reset_reason_t reason);

static void androidFmRadioRxCallbackOnVendorForcedReset(enum fmradio_reset_reason_t reason);

static void androidFmRadioRxCallbackOnSignalStrengthChanged(int newLevel);

static void androidFmRadioRxCallbackOnRDSDataFound(struct
                                                   fmradio_rds_bundle_t
                                                   *t, int frequency);

static void androidFmRadioRxCallbackOnPlayingInStereo(int
                                                      isPlayingInStereo);

static void androidFmRadioRxCallbackOnExtraCommand(char* command,
                                                   struct
                                                   fmradio_extra_command_ret_item_t
                                                   *retItem);

static void androidFmRadioRxCallbackOnAutomaticSwitch(int newFrequency, enum fmradio_switch_reason_t reason);

static const FmRadioCallbacks_t FmRadioRxCallbacks = {
    androidFmRadioRxCallbackOnStateChanged,
    androidFmRadioRxCallbackOnError,
    androidFmRadioRxCallbackOnStarted,
    androidFmRadioRxCallbackOnScan,
    androidFmRadioRxCallbackOnFullScan,
    NULL,
    androidFmRadioRxCallbackOnForcedReset,
    androidFmRadioRxCallbackOnExtraCommand,
};

/* callbacks from vendor layer */

static const fmradio_vendor_callbacks_t FmRadioRxVendorCallbacks = {
    androidFmRadioRxCallbackOnPlayingInStereo,
    androidFmRadioRxCallbackOnRDSDataFound,
    androidFmRadioRxCallbackOnSignalStrengthChanged,
    androidFmRadioRxCallbackOnAutomaticSwitch,
    androidFmRadioRxCallbackOnVendorForcedReset
};

extern struct FmSession_t fmTransmitterSession;

struct FmSession_t fmReceiverSession = {
    NULL,
    NULL,
    false,
    FMRADIO_STATE_IDLE,
    NULL,
    &IsValidRxEventForState,
    &FmRadioRxCallbacks,
    NULL,
    NULL,
    &fmTransmitterSession,
    NULL,
    FMRADIO_STATE_IDLE,
    false,
    false,
    false,
    &rx_tx_common_mutex,
    PTHREAD_COND_INITIALIZER,
    NULL,
};

// make sure we don't refer the TransmitterSession anymore from here
#define fmTransmitterSession ERRORDONOTUSERECEIVERSESSIONINTRANSMITTER

/*
* Implementation of callbacks from within service layer. For these the
*  mutex lock is always held on entry and need to be released before doing
*  calls to java layer (env->Call*Method)  becasue these might trigger new
*  calls from java and a deadlock would occure if lock was still held.
*/

static void androidFmRadioRxCallbackOnStateChanged(int oldState,
                                                   int newState)
{
    jmethodID notifyOnStateChangedMethod;
    JNIEnv *env;
    jclass clazz;
    bool reAttached = false;

    ALOGI("androidFmRadioRxCallbackOnStateChanged: Old state %d, new state %d", oldState, newState);

    /* since we might be both in main thread and subthread both test getenv
     * and attach */
    if (fmReceiverSession.jvm_p->GetEnv((void **) &env, JNI_VERSION_1_4) !=
        JNI_OK) {
        reAttached = true;
        if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
            ALOGE("Error, can't attach current thread");
            return;
        }
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);

    notifyOnStateChangedMethod =
        env->GetMethodID(clazz, "notifyOnStateChanged", "(II)V");
    if (notifyOnStateChangedMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj,
                            notifyOnStateChangedMethod, oldState,
                            newState);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnStateChanged method");
    }

    if (reAttached) {
        fmReceiverSession.jvm_p->DetachCurrentThread();
    }
}

static void androidFmRadioRxCallbackOnError(void)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioRxCallbackOnError");

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    notifyMethod = env->GetMethodID(clazz, "notifyOnError", "()V");

    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnError method");
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioRxCallbackOnStarted(void)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioRxCallbackOnStarted");

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    notifyMethod = env->GetMethodID(clazz, "notifyOnStarted", "()V");

    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnStarted method");
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
}


static void androidFmRadioRxCallbackOnScan(int foundFreq,
                                           int signalStrength,
                                           int scanDirection,
                                           bool aborted)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioRxCallbackOnScan: Callback foundFreq %d, signalStrength %d,"
         " scanDirection %d, aborted %u", foundFreq, signalStrength, scanDirection,
         aborted);

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);

    notifyMethod = env->GetMethodID(clazz, "notifyOnScan", "(IIIZ)V");

    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod, foundFreq, signalStrength,
                            scanDirection, aborted);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnScan method");
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioRxCallbackOnFullScan(int noItems,
                                               int *frequencies,
                                               int *sigStrengths,
                                               bool aborted)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    jintArray jFreqs;
    jintArray jSigStrengths;

    int d;

    ALOGI("androidFmRadioRxCallbackOnFullScan: No items %d, aborted %d",
         noItems, aborted);

    for (d = 0; d < noItems; d++) {
        ALOGI("%d -> %d", frequencies[d], sigStrengths[d]);
    }

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    jFreqs = env->NewIntArray(noItems);
    jSigStrengths = env->NewIntArray(noItems);
    clazz = env->GetObjectClass(fmReceiverSession.jobj);

    env->SetIntArrayRegion(jFreqs, 0, noItems, frequencies);
    env->SetIntArrayRegion(jSigStrengths, 0, noItems, sigStrengths);


    notifyMethod = env->GetMethodID(clazz, "notifyOnFullScan", "([I[IZ)V");

    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

        env->CallVoidMethod(jobj, notifyMethod,
                            jFreqs, jSigStrengths, aborted);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    } else {
        ALOGE("ERROR - JNI can't find java notifyOnFullScan method");
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
}

static void androidFmRadioRxCallbackOnForcedReset(enum fmradio_reset_reason_t reason)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    bool reAttached = false;

    ALOGI("androidFmRadioRxCallbackOnForcedReset");

    if (fmReceiverSession.jvm_p->GetEnv((void **) &env, JNI_VERSION_1_4) !=
        JNI_OK) {
        reAttached = true;
        if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
            ALOGE("Error, can't attch current thread");
            return;
        }
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);

    notifyMethod = env->GetMethodID(clazz, "notifyOnForcedReset", "(I)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            reason);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }

    if (reAttached) {
        fmReceiverSession.jvm_p->DetachCurrentThread();
    }
}

static void androidFmRadioRxCallbackOnVendorForcedReset(enum fmradio_reset_reason_t reason)
{

    ALOGI("androidFmRadioRxCallbackOnVendorForcedReset");
    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    if (fmReceiverSession.state != FMRADIO_STATE_IDLE) {
        FMRADIO_SET_STATE(&fmReceiverSession, FMRADIO_STATE_IDLE);
        androidFmRadioUnLoadFmLibrary(&fmReceiverSession);
        fmReceiverSession.isRegistered = false;
    }
    fmReceiverSession.callbacks_p->onForcedReset(reason);
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}


static void androidFmRadioRxCallbackOnExtraCommand(char* command,
                                                   struct
                                                   fmradio_extra_command_ret_item_t
                                                   *retList)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    struct bundle_descriptor_offsets_t *bundle_p =
        fmReceiverSession.bundleOffsets_p;

    ALOGI("androidFmRadioRxCallbackOnSendExtraCommand");

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        return;
    }

    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    jobject retBundle = extraCommandRetList2Bundle(env, bundle_p, retList);
    jstring jcommand = env->NewStringUTF(command);

    notifyMethod =
        env->GetMethodID(clazz, "notifyOnExtraCommand",
                         "(Ljava/lang/String;Landroid/os/Bundle;)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

        env->CallVoidMethod(jobj, notifyMethod, jcommand, retBundle);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
}

/*
* Implementation of callbacks from vendor layer. For these the  mutex lock
* is NOT held on entry and need to be taken and released before doing
*  calls to java layer (env->Call*Method)  becasue these might trigger new
*  calls from java and a deadlock would occure
*/

static void
androidFmRadioRxCallbackOnRDSDataFound(struct fmradio_rds_bundle_t *t,
                                       int frequency)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;
    jobject bundle;
    jshortArray jsArr;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    struct bundle_descriptor_offsets_t *bundle_p =
        fmReceiverSession.bundleOffsets_p;

    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        goto drop_lock;
    }
    bundle = env->NewObject(bundle_p->mClass,
                                    bundle_p->mConstructor);
    /* note, these calls are to predefined methods, no need to release lock */
    env->CallVoidMethod(bundle, bundle_p->mPutShort,
                        env->NewStringUTF("PI"), t->pi);
    env->CallVoidMethod(bundle, bundle_p->mPutShort,
                        env->NewStringUTF("TP"), t->tp);
    env->CallVoidMethod(bundle, bundle_p->mPutShort,
                        env->NewStringUTF("PTY"), t->pty);
    env->CallVoidMethod(bundle, bundle_p->mPutShort,
                        env->NewStringUTF("TA"), t->ta);
    env->CallVoidMethod(bundle, bundle_p->mPutShort,
                        env->NewStringUTF("M/S"), t->ms);

    if (t->num_afs > 0 && t->num_afs < RDS_MAX_AFS) {
        jintArray jArr = env->NewIntArray(t->num_afs);
        env->SetIntArrayRegion(jArr, 0, t->num_afs, t->af);
        env->CallVoidMethod(bundle, bundle_p->mPutIntArray,
                            env->NewStringUTF("AF"), jArr);
    }
    env->CallVoidMethod(bundle, bundle_p->mPutString,
                        env->NewStringUTF("PSN"),
                        env->NewStringUTF(t->psn));
    env->CallVoidMethod(bundle, bundle_p->mPutString,
                        env->NewStringUTF("RT"),
                        env->NewStringUTF(t->rt));
    env->CallVoidMethod(bundle, bundle_p->mPutString,
                        env->NewStringUTF("CT"),
                        env->NewStringUTF(t->ct));
    env->CallVoidMethod(bundle, bundle_p->mPutString,
                        env->NewStringUTF("PTYN"),
                        env->NewStringUTF(t->ptyn));

    jsArr = env->NewShortArray(3);

    env->SetShortArrayRegion(jsArr, 0, 3, t->tmc);
    env->CallVoidMethod(bundle, bundle_p->mPutShortArray,
                        env->NewStringUTF("TMC"), jsArr);

    env->CallVoidMethod(bundle, bundle_p->mPutInt,
                        env->NewStringUTF("TAF"), t->taf);

    clazz = env->GetObjectClass(fmReceiverSession.jobj);

    notifyMethod =
        env->GetMethodID(clazz, "notifyOnRDSDataFound",
                         "(Landroid/os/Bundle;I)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            bundle, frequency);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }
    fmReceiverSession.jvm_p->DetachCurrentThread();

 drop_lock:
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void androidFmRadioRxCallbackOnSignalStrengthChanged(int newLevel)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        goto drop_lock;
    }
    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    notifyMethod =
        env->GetMethodID(clazz, "notifyOnSignalStrengthChanged", "(I)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            newLevel);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
 drop_lock:
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void androidFmRadioRxCallbackOnPlayingInStereo(int
                                                      isPlayingInStereo)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioRxCallbackOnPlayingInStereo (%d)",
         isPlayingInStereo);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        goto drop_lock;
    }
    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    notifyMethod =
        env->GetMethodID(clazz, "notifyOnPlayingInStereo", "(Z)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod,
                            (bool) isPlayingInStereo);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }

    fmReceiverSession.jvm_p->DetachCurrentThread();
 drop_lock:
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

/*
 * currently frequency changed event is not supported by interface, to be
 * implemented quite soon...
 */

static void androidFmRadioRxCallbackOnAutomaticSwitch(int newFrequency, enum fmradio_switch_reason_t reason)
{
    jmethodID notifyMethod;
    JNIEnv *env;
    jclass clazz;

    ALOGI("androidFmRadioRxCallbackOnAutomaticSwitch: new frequency %d, reason %d",
         newFrequency, (int) reason);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    if (fmReceiverSession.jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
        ALOGE("Error, can't attch current thread");
        goto drop_lock;
    }
    clazz = env->GetObjectClass(fmReceiverSession.jobj);
    notifyMethod =
        env->GetMethodID(clazz, "notifyOnAutomaticSwitching", "(II)V");
    if (notifyMethod != NULL) {
        jobject jobj = fmReceiverSession.jobj;
        pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
        env->CallVoidMethod(jobj, notifyMethod, (jint)newFrequency,
                            (jint)reason);
        pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    }


    fmReceiverSession.jvm_p->DetachCurrentThread();
 drop_lock:
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

/*
 *  function calls from java layer.
 */

static jint androidFmRadioRxGetState(JNIEnv * env, jobject obj)
{
    FmRadioState_t state;

    ALOGI("androidFmRadioRxGetState, state\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    state = fmReceiverSession.state;
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
    return state;
}

/* common ones with tx, just forward to the generic androidFmRadioxxxxx version */

static void
androidFmRadioRxStart(JNIEnv * env, jobject obj, int lowFreq,
                      int highFreq, int defaultFreq, int grid)
{
    ALOGI("androidFmRadioRxStart. LowFreq %d, HighFreq %d, DefaultFreq %d, grid %d.", lowFreq, highFreq, defaultFreq, grid);

    if (fmReceiverSession.jobj == NULL)
        fmReceiverSession.jobj = env->NewGlobalRef(obj);
    (void) androidFmRadioStart(&fmReceiverSession, FMRADIO_RX,
                               &FmRadioRxVendorCallbacks, false, lowFreq,
                               highFreq, defaultFreq, grid);
}


static void
androidFmRadioRxStartAsync(JNIEnv * env, jobject obj, int lowFreq,
                           int highFreq, int defaultFreq, int grid)
{
    ALOGI("androidFmRadioRxStartAsync...");

    if (fmReceiverSession.jobj == NULL)
        fmReceiverSession.jobj = env->NewGlobalRef(obj);
    (void) androidFmRadioStart(&fmReceiverSession, FMRADIO_RX,
                               &FmRadioRxVendorCallbacks, true, lowFreq,
                               highFreq, defaultFreq, grid);
}

static void androidFmRadioRxPause(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioRxPause\n");

    (void)androidFmRadioPause(&fmReceiverSession);
}

static void androidFmRadioRxResume(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioRxResume\n");
    (void)androidFmRadioResume(&fmReceiverSession);
}

static jint androidFmRadioRxReset(JNIEnv * env, jobject obj)
{
    int retval = 0;

    ALOGI("androidFmRadioRxReset");
    retval = androidFmRadioReset(&fmReceiverSession);

    if (retval >= 0 && fmReceiverSession.state == FMRADIO_STATE_IDLE &&
        fmReceiverSession.jobj != NULL) {
        env->DeleteGlobalRef(fmReceiverSession.jobj);
        fmReceiverSession.jobj = NULL;
    }

    return retval;
}

static void
androidFmRadioRxSetFrequency(JNIEnv * env, jobject obj, jint frequency)
{
    ALOGI("androidFmRadioRxSetFrequency tuneTo:%d\n", (int) frequency);
    return androidFmRadioSetFrequency(&fmReceiverSession, (int) frequency);
}

static jint androidFmRadioRxGetFrequency(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioRxGetFrequency:\n");
    return androidFmRadioGetFrequency(&fmReceiverSession);
}

static void androidFmRadioRxStopScan(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioRxStopScan\n");
    androidFmRadioStopScan(&fmReceiverSession);
}

/* the rest of the calls are specific for RX */

static jint androidFmRadioRxGetSignalStrength(JNIEnv * env, jobject obj)
{
    int retval = SIGNAL_STRENGTH_UNKNOWN;

    ALOGI("androidFmRadioRxGetSignalStrength\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_GET_SIGNAL_STRENGTH)) {
        goto drop_lock;
    }

    if (fmReceiverSession.vendorMethods_p->get_signal_strength) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(&fmReceiverSession);

        retval =
            fmReceiverSession.vendorMethods_p->
            get_signal_strength(&fmReceiverSession.vendorData_p);

        if (retval < 0) {
            retval = SIGNAL_STRENGTH_UNKNOWN;
        } else if (retval > SIGNAL_STRENGTH_MAX) {
            retval = SIGNAL_STRENGTH_MAX;
        }
        androidFmRadioPauseIfTempResumed(&fmReceiverSession);
    }

  drop_lock:

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    return retval;
}

static jboolean
androidFmRadioRxIsPlayingInStereo(JNIEnv * env, jobject obj)
{
    bool retval;

    ALOGI("androidFmRadioRxIsPlayingInStereo:\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    /* if we haven't register we don't know yet */
    if (!fmReceiverSession.isRegistered) {
        retval = false;
        goto drop_lock;
    }
    // valid in all states
    if (fmReceiverSession.vendorMethods_p->is_playing_in_stereo != NULL) {
        retval =
            fmReceiverSession.vendorMethods_p->
            is_playing_in_stereo(&fmReceiverSession.vendorData_p);
    } else {
        retval = false;
    }

  drop_lock:

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    return retval;
}

static jboolean
androidFmRadioRxIsRDSDataSupported(JNIEnv * env, jobject obj)
{
    bool retval;

    ALOGI("androidFmRadioRxIsRDSDataSupported:\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    /* if we haven't register we don't know yet */
    if (!fmReceiverSession.isRegistered) {
        retval = false;
        goto drop_lock;
    }
    // valid in all states
    if (fmReceiverSession.vendorMethods_p->is_rds_data_supported != NULL) {
        retval =
            fmReceiverSession.vendorMethods_p->
            is_rds_data_supported(&fmReceiverSession.vendorData_p);
    } else {
        retval = false;
    }

  drop_lock:

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
    return retval;
}

static jboolean
androidFmRadioRxIsTunedToValidChannel(JNIEnv * env, jobject obj)
{
    bool retval;

    ALOGI("androidFmRadioRxIsTunedToValidChannel:\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    /* if we haven't register we don't know yet */
    if (!fmReceiverSession.isRegistered) {
        retval = false;
        goto drop_lock;
    }
    // valid in all states
    if (fmReceiverSession.vendorMethods_p->is_tuned_to_valid_channel != NULL) {
        retval =
            fmReceiverSession.vendorMethods_p->
            is_tuned_to_valid_channel(&fmReceiverSession.vendorData_p);
    } else {
        retval = false;
    }

  drop_lock:

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
    return retval;
}

static void *execute_androidFmRadioRxScan(void *args)
{
    enum fmradio_seek_direction_t scanDirection =
        *(enum fmradio_seek_direction_t *) args;
    int signalStrength = -1;
    int retval;
    enum FmRadioState_t oldState;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    free(args);
    // we should still be in SCANNING mode, but we can't be 100.00 % sure since main thread released lock
    // before we could run

    if (fmReceiverSession.state != FMRADIO_STATE_SCANNING) {
        ALOGE("execute_androidFmRadioRxScan - warning, state not scanning");
    }

    /*
     * if mode has been changed to IDLE in the mean time by main thread,
     * exit the worker thread gracefully
     */
    if (fmReceiverSession.state == FMRADIO_STATE_IDLE) {
        goto drop_lock;
    }

    oldState = fmReceiverSession.oldState;

    // temporary resume chip if sleeping
    if (oldState == FMRADIO_STATE_PAUSED) {
        (void) fmReceiverSession.
            vendorMethods_p->resume(&fmReceiverSession.vendorData_p);
    }

    if (pthread_cond_signal(&fmReceiverSession.sync_cond) != 0) {
        ALOGE("execute_androidFmRadioRxScan - warning, signal failed");
    }
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    retval =
        fmReceiverSession.vendorMethods_p->scan(&fmReceiverSession.
                                                vendorData_p,
                                                scanDirection);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (retval >= 0) {
        // also get signal strength (if supported)
        if (fmReceiverSession.vendorMethods_p->get_signal_strength)
            signalStrength =
                fmReceiverSession.vendorMethods_p->
                get_signal_strength(&fmReceiverSession.vendorData_p);
    }
    /*
     * if state has changed we should keep it, probably a forced reset
     */
    if (fmReceiverSession.state != FMRADIO_STATE_SCANNING) {
        ALOGI("State changed while scanning (state now %d), keeping",
             fmReceiverSession.state);
        retval = -1;
    } else {
        // put back to sleep if we did a temporary wake-up
        if ((oldState == FMRADIO_STATE_PAUSED
             || fmReceiverSession.pendingPause))
            (void) fmReceiverSession.
                vendorMethods_p->pause(&fmReceiverSession.vendorData_p);
        if (fmReceiverSession.pendingPause) {
            FMRADIO_SET_STATE(&fmReceiverSession, FMRADIO_STATE_PAUSED);
        } else {
            FMRADIO_SET_STATE(&fmReceiverSession, oldState);
        }

        // if we failed but we have a pending abort just read the current frequency to give a proper
        // onScan return

        if (retval < 0 && fmReceiverSession.lastScanAborted &&
            fmReceiverSession.vendorMethods_p->get_frequency) {
            retval = fmReceiverSession.vendorMethods_p->get_frequency(&fmReceiverSession.vendorData_p);
        }
    }

    fmReceiverSession.pendingPause = false;

    if (retval >= 0) {
        fmReceiverSession.callbacks_p->onScan(retval,
                                              signalStrength,
                                              scanDirection,
                                              fmReceiverSession.
                                              lastScanAborted);
    } else {
        fmReceiverSession.callbacks_p->onError();
    }
    drop_lock:
    /* Wake up the main thread if it is currently waiting on the condition variable */
    if (pthread_cond_signal(&fmReceiverSession.sync_cond) != 0) {
        ALOGE("execute_androidFmRadioRxScan - signal failed\n");
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    pthread_exit(NULL);
    return NULL;
}


static void androidFmRadioRxScan(enum fmradio_seek_direction_t scanDirection)
{
    int retval = 0;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SCAN)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (fmReceiverSession.vendorMethods_p->scan) {
        enum fmradio_seek_direction_t *scanDirectionParam_p =
            (enum fmradio_seek_direction_t *)
            malloc(sizeof(*scanDirectionParam_p));

        pthread_t execute_thread;

        // we need to create a new thread actually executing the command

        fmReceiverSession.oldState = fmReceiverSession.state;
        FMRADIO_SET_STATE(&fmReceiverSession, FMRADIO_STATE_SCANNING);
        *scanDirectionParam_p = scanDirection;

        fmReceiverSession.lastScanAborted = false;

        if (pthread_create
            (&execute_thread, NULL, execute_androidFmRadioRxScan,
             (void *) scanDirectionParam_p) != 0) {

            ALOGE("pthread_create failure...\n");
            free(scanDirectionParam_p);
            FMRADIO_SET_STATE(&fmReceiverSession, fmReceiverSession.oldState);
            retval = FMRADIO_IO_ERROR;
        } else {
            /* await thread startup, THREAD_WAIT_TIMEOUT_S sec timeout */
            struct timespec ts;
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += THREAD_WAIT_TIMEOUT_S;
            if (pthread_cond_timedwait(&fmReceiverSession.sync_cond,
                                       fmReceiverSession.dataMutex_p,
                                       &ts) != 0) {
                ALOGE("androidFmRadioRxScan: warning, wait failure\n");
            }
            pthread_detach(execute_thread);

        }
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    if (retval < 0) {
        ALOGE("androidFmRadioRxScan failed\n");
    }
}

static void
androidFmRadioRxScanUp(JNIEnv * env, jobject obj, jlong * frequency)
{
    ALOGI("androidFmRadioRxScanUp\n");

    androidFmRadioRxScan(FMRADIO_SEEK_UP);
}

static void
androidFmRadioRxScanDown(JNIEnv * env, jobject obj, jlong * frequency)
{
    ALOGI("androidFmRadioRxScanDown\n");

    androidFmRadioRxScan(FMRADIO_SEEK_DOWN);
}

static void *execute_androidFmRadioRxFullScan(void *args)
{
    int retval;
    enum FmRadioState_t oldState = fmReceiverSession.oldState;
    int *frequencies_p = NULL;
    int *rssi_p = NULL;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    // we should still be in SCANNING mode, but we can't be 100.00 % sure since main thread released lock
    // before we could run

    if (fmReceiverSession.state != FMRADIO_STATE_SCANNING) {
        ALOGE("execute_androidFmRadioRxFullScan - warning, state not scanning\n");
    }

    /*
     * if mode has been changed to IDLE in the mean time by main thread,
     * exit the worker thread gracefully
     */
    if (fmReceiverSession.state == FMRADIO_STATE_IDLE) {
        goto drop_lock;
    }
    // temporary resume chip if sleeping
    if (oldState == FMRADIO_STATE_PAUSED) {
        (void) fmReceiverSession.
            vendorMethods_p->resume(&fmReceiverSession.vendorData_p);
    }

    if (pthread_cond_signal(&fmReceiverSession.sync_cond) != 0) {
        ALOGE("execute_androidFmRadioRxFullScan - warning, signal failed\n");
    }
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    retval =
        fmReceiverSession.vendorMethods_p->full_scan(&fmReceiverSession.
                                                    vendorData_p,
                                                    &frequencies_p,
                                                    &rssi_p);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    /*
     * if state has changed we should keep it, probably a forced pause or
     * forced reset
     */
    if (fmReceiverSession.state != FMRADIO_STATE_SCANNING) {
        ALOGI("State changed while scanning (state now %d), keeping\n",
             fmReceiverSession.state);
        retval = -1;
    } else {
        if (fmReceiverSession.pendingPause) {
            FMRADIO_SET_STATE(&fmReceiverSession, FMRADIO_STATE_PAUSED);
        } else {
            FMRADIO_SET_STATE(&fmReceiverSession, oldState);
        }

        fmReceiverSession.pendingPause = false;
    }

    if (retval >= 0) {
        fmReceiverSession.callbacks_p->onFullScan(retval,
                                                  frequencies_p,
                                                  rssi_p,
                                                  fmReceiverSession.
                                                  lastScanAborted);
    } else {
        fmReceiverSession.callbacks_p->onError();
    }

    if (frequencies_p != NULL) {
        free(frequencies_p);
    }

    if (rssi_p != NULL) {
        free(rssi_p);
    }

    drop_lock:
    /* Wake up the main thread if it is currently waiting on the condition variable */
    if (pthread_cond_signal(&fmReceiverSession.sync_cond) != 0) {
        ALOGE("execute_androidFmRadioRxFullScan - signal failed\n");
    }
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    pthread_exit(NULL);
    return NULL;
}

static void androidFmRadioRxStartFullScan(JNIEnv * env, jobject obj)
{
    ALOGI("androidFmRadioRxStartFullScan\n");
    int retval = 0;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_FULL_SCAN)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }


    if (fmReceiverSession.vendorMethods_p->full_scan) {
        pthread_t execute_thread;

        fmReceiverSession.oldState = fmReceiverSession.state;
        FMRADIO_SET_STATE(&fmReceiverSession, FMRADIO_STATE_SCANNING);
        fmReceiverSession.lastScanAborted = false;

        if (pthread_create
            (&execute_thread, NULL, execute_androidFmRadioRxFullScan,
             NULL) != 0) {

            ALOGE("pthread_create failure...\n");
            FMRADIO_SET_STATE(&fmReceiverSession, fmReceiverSession.oldState);
            retval = FMRADIO_IO_ERROR;
        } else {
            /* await thread startup, THREAD_WAIT_TIMEOUT_S sec timeout */
            struct timespec ts;
            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += THREAD_WAIT_TIMEOUT_S;
            if (pthread_cond_timedwait(&fmReceiverSession.sync_cond,
                                       fmReceiverSession.dataMutex_p,
                                       &ts) != 0) {
                ALOGE("androidFmRadioRxStartFullScan: warning, wait failure\n");
            }
            pthread_detach(execute_thread);
        }
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void androidFmRadioRxSetAutomaticAFSwitching(JNIEnv * env,
                                                  jobject obj,
                                                  jboolean automatic)
{
    int retval = -1;

    ALOGI("androidFmRadioRxSetAutomaticAFSwitching\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }


    if (fmReceiverSession.vendorMethods_p->set_automatic_af_switching) {
        retval =
            fmReceiverSession.vendorMethods_p->
            set_automatic_af_switching(&fmReceiverSession.vendorData_p, automatic);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void androidFmRadioRxSetAutomaticTASwitching(JNIEnv * env, jobject obj,
                                                    jboolean automatic)
{
    int retval = -1;

    ALOGI("androidFmRadioRxSetAutomaticTASwitching\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }


    if (fmReceiverSession.vendorMethods_p->set_automatic_ta_switching) {
        retval =
            fmReceiverSession.vendorMethods_p->
            set_automatic_ta_switching(&fmReceiverSession.vendorData_p, automatic);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void androidFmRadioRxSetForceMono(JNIEnv * env, jobject obj,
                                         jboolean forceMono)
{
    int retval = -1;

    ALOGI("androidFmRadioRxSetForceMono\n");

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }


    if (fmReceiverSession.vendorMethods_p->set_force_mono) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(&fmReceiverSession);

        retval =
            fmReceiverSession.vendorMethods_p->
            set_force_mono(&fmReceiverSession.vendorData_p, forceMono);

        androidFmRadioPauseIfTempResumed(&fmReceiverSession);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static void
androidFmRadioRxSetThreshold(JNIEnv * env, jobject obj, jint threshold)
{
    int retval;

    ALOGI("androidFmRadioRxSetThreshold threshold:%d\n", (int) threshold);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }


    if (fmReceiverSession.vendorMethods_p->set_threshold) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(&fmReceiverSession);

        retval =
            fmReceiverSession.
            vendorMethods_p->set_threshold(&fmReceiverSession.vendorData_p,
                                          threshold);
        /* if in pause state temporary resume */
        androidFmRadioPauseIfTempResumed(&fmReceiverSession);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }

  drop_lock:
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static jint androidFmRadioRxGetThreshold(JNIEnv * env, jobject obj)
{
    int retval;

    ALOGI("androidFmRadioRxGetThreshold\n");
    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_GET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (fmReceiverSession.vendorMethods_p->get_threshold) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(&fmReceiverSession);
        retval =
            fmReceiverSession.
            vendorMethods_p->get_threshold(&fmReceiverSession.vendorData_p);
        androidFmRadioPauseIfTempResumed(&fmReceiverSession);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }
  drop_lock:

    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(&fmReceiverSession);
    } else if (retval < 0) {
        THROW_IO_ERROR(&fmReceiverSession);
    }
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);

    return retval;
}

static void androidFmRadioRxSetRDS(JNIEnv * env, jobject obj,
                                   jboolean receiveRDS)
{
    int retval = -1;

    ALOGI("androidFmRadioRxSetRDS(%d)", (int)receiveRDS);

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (&fmReceiverSession, FMRADIO_EVENT_SET_PARAMETER)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (fmReceiverSession.vendorMethods_p->set_rds_reception) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(&fmReceiverSession);

        retval = fmReceiverSession.vendorMethods_p->
            set_rds_reception(&fmReceiverSession.vendorData_p, receiveRDS);

        androidFmRadioPauseIfTempResumed(&fmReceiverSession);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    /*
     * Set rds is not executed by explicit command but rather triggered
     * on startup and on adding and removal of listeners. Because of this
     * it should not trigger exceptions, just ALOG any failure.
     */

    if (retval != FMRADIO_OK) {
        ALOGE("androidFmRadioRxSetRDS failed, retval = %d.", retval);
    }

    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
}

static jboolean androidFmRadioRxSendExtraCommand(JNIEnv * env, jobject obj,
                                                 jstring command,
                                                 jobjectArray parameters)
{
    ALOGI("androidFmRadioRxSendExtraCommand");

/* we need to set jobj since this might be called before start */

    if (fmReceiverSession.jobj == NULL)
        fmReceiverSession.jobj = env->NewGlobalRef(obj);

    androidFmRadioSendExtraCommand(&fmReceiverSession, env, command,
                                   parameters);

    return true;
}


static JNINativeMethod gMethods[] = {
    {(char *)"_fm_receiver_getState", (char *)"()I",
     (void *) androidFmRadioRxGetState},
    {(char *)"_fm_receiver_start", (char *)"(IIII)V",
     (void *) androidFmRadioRxStart},
    {(char *)"_fm_receiver_startAsync", (char *)"(IIII)V",
     (void *) androidFmRadioRxStartAsync},
    {(char *)"_fm_receiver_pause", (char *)"()V",
     (void *) androidFmRadioRxPause},
    {(char *)"_fm_receiver_resume", (char *)"()V",
     (void *) androidFmRadioRxResume},
    {(char *)"_fm_receiver_reset", (char *)"()I",
     (void *) androidFmRadioRxReset},
    {(char *)"_fm_receiver_setFrequency", (char *)"(I)V",
     (void *) androidFmRadioRxSetFrequency},
    {(char *)"_fm_receiver_getFrequency", (char *)"()I",
     (void *) androidFmRadioRxGetFrequency},
    {(char *)"_fm_receiver_getSignalStrength", (char *)"()I",
     (void *) androidFmRadioRxGetSignalStrength},
    {(char *)"_fm_receiver_scanUp", (char *)"()V",
     (void *) androidFmRadioRxScanUp},
    {(char *)"_fm_receiver_scanDown", (char *)"()V",
     (void *) androidFmRadioRxScanDown},
    {(char *)"_fm_receiver_startFullScan", (char *)"()V",
     (void *) androidFmRadioRxStartFullScan},
    {(char *)"_fm_receiver_isPlayingInStereo", (char *)"()Z",
     (void *) androidFmRadioRxIsPlayingInStereo},
    {(char *)"_fm_receiver_isRDSDataSupported", (char *)"()Z",
     (void *) androidFmRadioRxIsRDSDataSupported},
    {(char *)"_fm_receiver_isTunedToValidChannel", (char *)"()Z",
     (void *) androidFmRadioRxIsTunedToValidChannel},
    {(char *)"_fm_receiver_stopScan", (char *)"()V",
     (void *) androidFmRadioRxStopScan},
    {(char *)"_fm_receiver_setAutomaticAFSwitching", (char *)"(Z)V",
     (void *) androidFmRadioRxSetAutomaticAFSwitching},
    {(char *)"_fm_receiver_setAutomaticTASwitching", (char *)"(Z)V",
     (void *) androidFmRadioRxSetAutomaticTASwitching},
    {(char *)"_fm_receiver_setForceMono", (char *)"(Z)V",
     (void *) androidFmRadioRxSetForceMono},
    {(char *)"_fm_receiver_sendExtraCommand",
     (char *)"(Ljava/lang/String;[Ljava/lang/String;)Z",
     (void *) androidFmRadioRxSendExtraCommand},
    {(char *)"_fm_receiver_getThreshold", (char *)"()I",
     (void *) androidFmRadioRxGetThreshold},
    {(char *)"_fm_receiver_setThreshold", (char *)"(I)V",
     (void *) androidFmRadioRxSetThreshold},
    {(char *)"_fm_receiver_setRDS", (char *)"(Z)V",
     (void *) androidFmRadioRxSetRDS},
};




int registerAndroidFmRadioReceiver(JavaVM * vm, JNIEnv * env)
{
    ALOGI("registerAndroidFmRadioReceiver\n");
    jclass clazz;

    pthread_mutex_lock(fmReceiverSession.dataMutex_p);
    fmReceiverSession.jvm_p = vm;

    struct bundle_descriptor_offsets_t *bundle_p =
        (struct bundle_descriptor_offsets_t *)
        malloc(sizeof(struct bundle_descriptor_offsets_t));

    clazz = env->FindClass("android/os/Bundle");
    bundle_p->mClass = (jclass) env->NewGlobalRef(clazz);
    bundle_p->mConstructor = env->GetMethodID(clazz, "<init>", "()V");
    bundle_p->mPutInt =
        env->GetMethodID(clazz, "putInt", "(Ljava/lang/String;I)V");
    bundle_p->mPutShort =
        env->GetMethodID(clazz, "putShort", "(Ljava/lang/String;S)V");
    bundle_p->mPutIntArray =
        env->GetMethodID(clazz, "putIntArray", "(Ljava/lang/String;[I)V");
    bundle_p->mPutShortArray =
        env->GetMethodID(clazz, "putShortArray",
                         "(Ljava/lang/String;[S)V");
    bundle_p->mPutString =
        env->GetMethodID(clazz, "putString",
                         "(Ljava/lang/String;Ljava/lang/String;)V");

    fmReceiverSession.bundleOffsets_p = bundle_p;
    pthread_mutex_unlock(fmReceiverSession.dataMutex_p);
    return jniRegisterNativeMethods(env,
                                    "com/stericsson/hardware/fm/FmReceiverService",
                                    gMethods, NELEM(gMethods));
}

/* *INDENT-OFF* */
};                              // namespace android
