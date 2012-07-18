/*
 * Copyright (C) ST-Ericsson SA 2010
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Author: johan.xj.palmaeus@stericsson.com for ST-Ericsson
 */

/*
 * Internal stuff for android_fmradio(_Receiver/_Transmitter).cpp
 */

#ifndef ANDROID_FMRADIO_H
#define ANDROID_FMRADIO_H

#include "jni.h"
#include "fmradio.h"

enum FmRadioState_t {
    FMRADIO_STATE_IDLE,
    FMRADIO_STATE_STARTING,
    FMRADIO_STATE_STARTED,
    FMRADIO_STATE_PAUSED,
    FMRADIO_STATE_SCANNING,
    FMRADIO_STATE_EXTRA_COMMAND,
    /* sum up */
    FMRADIO_NUMBER_OF_STATES
};

enum FmRadioCommand_t {
    FMRADIO_EVENT_START,
    FMRADIO_EVENT_START_ASYNC,
    FMRADIO_EVENT_PAUSE,
    FMRADIO_EVENT_RESUME,
    FMRADIO_EVENT_RESET,
    FMRADIO_EVENT_GET_FREQUENCY,
    FMRADIO_EVENT_SET_FREQUENCY,
    FMRADIO_EVENT_SET_PARAMETER,
    FMRADIO_EVENT_STOP_SCAN,
    FMRADIO_EVENT_EXTRA_COMMAND,
    /* RX Only */
    FMRADIO_EVENT_GET_PARAMETER,
    FMRADIO_EVENT_GET_SIGNAL_STRENGTH,
    FMRADIO_EVENT_SCAN,
    FMRADIO_EVENT_FULL_SCAN,
    /* TX Only */
    FMRADIO_EVENT_BLOCK_SCAN,
    /* sum up */
    FMRADIO_NUMBER_OF_EVENTS
};

enum RadioMode_t {
    FMRADIO_RX,
    FMRADIO_TX
};

typedef bool ValidEventsForStates_t[FMRADIO_NUMBER_OF_EVENTS]
    [FMRADIO_NUMBER_OF_STATES];

struct FmRadioCallbacks_t {
    void (*onStateChanged) (int, int);
    void (*onError) (void);
    void (*onStarted) (void);
    void (*onScan) (int, int, int, bool);    /* RX only */
    void (*onFullScan) (int, int *, int *, bool);       /* RX only */
    void (*onBlockScan) (int, int *, int *, bool);      /* TX only */
    void (*onForcedReset) (enum fmradio_reset_reason_t reason);
    void (*onSendExtraCommand) (char*, struct fmradio_extra_command_ret_item_t *);
};

struct bundle_descriptor_offsets_t {
    jclass mClass;
    jmethodID mConstructor;
    jmethodID mGetInt;
    jmethodID mGetIntArray;
    jmethodID mGetShort;
    jmethodID mGetShortArray;
    jmethodID mGetString;
    jmethodID mContainsKey;
    jmethodID mSize;
    jmethodID mKeySet;
    jmethodID mPutInt;
    jmethodID mPutShort;
    jmethodID mPutIntArray;
    jmethodID mPutShortArray;
    jmethodID mPutString;
};

struct FmSession_t {
    // vendor specific data, we do not know about this type
    void *vendorData_p;
    void *fmLibrary_p;
    bool isRegistered;
    enum FmRadioState_t state;
    struct fmradio_vendor_methods_t *vendorMethods_p;
    const ValidEventsForStates_t *validEventsForStates_p;
    const struct FmRadioCallbacks_t *callbacks_p;
    JavaVM *jvm_p;
    jobject jobj;
    struct FmSession_t *partnerSession_p;
    struct bundle_descriptor_offsets_t *bundleOffsets_p;
    enum FmRadioState_t oldState;    /* used when scanning */
    bool lastScanAborted;            /* used when scanning */
    bool pendingPause;               /* used when scanning & asyncStarting */
    bool ongoingReset;               /* used during reset while waiting */
    pthread_mutex_t *dataMutex_p;    /* data access to this struct */
    pthread_cond_t  sync_cond;
    struct ThreadCtrl_t *signalStrengthThreadCtrl_p;    /* RX Only */
};

#define FMRADIO_SET_STATE(_session_p,_newState) {int _oldState = (_session_p)->state; (_session_p)->state = _newState;(_session_p)->callbacks_p->onStateChanged(_oldState, _newState);}

/* exceptions */

#define THROW_ILLEGAL_ARGUMENT(_session_p) \
      androidFmRadioThrowException(_session_p,\
           "java/lang/IllegalArgumentException",\
           "Illegal argument", __FILE__, __LINE__,\
           __FUNCTION__)
#define THROW_UNSUPPORTED_OPERATION(_session_p) \
      androidFmRadioThrowException(_session_p,\
           "java/lang/UnsupportedOperationException",\
           "Unsupported operation", __FILE__, __LINE__,\
            __FUNCTION__)
#define THROW_INVALID_STATE(_session_p)	\
      androidFmRadioThrowException(_session_p,\
           "java/lang/IllegalStateException",\
           "State is invalid", __FILE__, __LINE__,\
           __FUNCTION__)
#define THROW_IO_ERROR(_session_p) \
      androidFmRadioThrowException(_session_p,\
           "java/io/IOException",\
           "IO Exception", __FILE__, __LINE__,\
           __FUNCTION__)


#define FM_LIBRARY_NAME_MAX_LENGTH 128

#define THREAD_WAIT_TIMEOUT_S 2

#define SIGNAL_STRENGTH_MAX 1000
#define SIGNAL_STRENGTH_UNKNOWN -1

extern pthread_mutex_t rx_tx_common_mutex;

jobject extraCommandRetList2Bundle(JNIEnv * env_p, struct bundle_descriptor_offsets_t
                                   *bundleOffsets_p,
                                   struct fmradio_extra_command_ret_item_t *itemList);

void freeExtraCommandRetList(struct extra_command_ret_item_t *itemList);

void androidFmRadioTempResumeIfPaused(struct FmSession_t *session_p);

void androidFmRadioPauseIfTempResumed(struct FmSession_t *session_p);

bool androidFmRadioIsValidEventForState(struct FmSession_t *session_p,
                                        enum FmRadioCommand_t event);

void androidFmRadioThrowException(struct FmSession_t *session_p,
                                  const char *exception,
                                  const char *message, const char *file,
                                  int line, const char *function);

bool androidFmRadioLoadFmLibrary(struct FmSession_t *session_p,
                                 enum RadioMode_t mode);

void androidFmRadioUnLoadFmLibrary(struct FmSession_t *session_p);

int
androidFmRadioStart(struct FmSession_t *session_p, enum RadioMode_t mode,
                    const struct fmradio_vendor_callbacks_t *callbacks,
                    bool async, int lowFreq, int highFreq, int defaultFreq,
                    int grid);

int androidFmRadioPause(struct FmSession_t *session_p);

int androidFmRadioResume(struct FmSession_t *session_p);

int androidFmRadioReset(struct FmSession_t *session_p);

void androidFmRadioSetFrequency(struct FmSession_t *session_p,
                                int frequency);

int androidFmRadioGetFrequency(struct FmSession_t *session_p);

void androidFmRadioStopScan(struct FmSession_t *session_p);

void
androidFmRadioSendExtraCommand(struct FmSession_t *session_p, JNIEnv * env,
                               jstring command, jobjectArray parameter);

#endif
