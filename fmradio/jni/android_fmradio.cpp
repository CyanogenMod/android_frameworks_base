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
 * Author: johan.xj.palmaeus@stericsson.com for ST-Ericsson
 */

/*
 * Native part of the generic TX-RX common FmRadio inteface
 */


#define ALOG_TAG "FmServiceNative"

// #define ALOG_NDEBUG 1

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <dirent.h>
#include <errno.h>

#include <utils/Log.h>
#include "jni.h"
#include "JNIHelp.h"

#include "android_fmradio.h"

#ifndef LIBRARY_PATH
#define LIBRARY_PATH "/system/lib/"
#endif

#ifndef LIBRARY_PREFIX
#define LIBRARY_PREFIX "libfmradio."
#endif

#ifndef LIBRARY_SUFFIX
#define LIBRARY_SUFFIX ".so"
#endif

#ifndef LIBRARY_SUFFIX_RX
#define LIBRARY_SUFFIX_RX "_rx.so"
#endif

#ifndef LIBRARY_SUFFIX_TX
#define LIBRARY_SUFFIX_TX "_tx.so"
#endif

/* structs for passing startup arguments to threads */

struct FmRadioStartAsyncParameters {
    int (*startFunc) (void **, const struct fmradio_vendor_callbacks_t*, int, int, int,
                      int);
    struct FmSession_t *session_p;
    const struct fmradio_vendor_callbacks_t *callbacks_p;
    int lowFreq;
    int highFreq;
    int defaultFreq;
    int grid;
};

struct FmRadioSendExtraCommandParameters {
    struct FmSession_t *session_p;
    char* c_command;
    char ** cparams;
};


pthread_mutex_t rx_tx_common_mutex = PTHREAD_MUTEX_INITIALIZER;

jobject extraCommandRetList2Bundle(JNIEnv * env_p, struct bundle_descriptor_offsets_t
                                   *bundleOffsets_p,
                                   struct fmradio_extra_command_ret_item_t *itemList)
{
    struct fmradio_extra_command_ret_item_t *listIterator = itemList;

    jobject bundle = env_p->NewObject(bundleOffsets_p->mClass,
                                      bundleOffsets_p->mConstructor);

    while (listIterator && listIterator->key != NULL) {
        switch (listIterator->type) {
        case FMRADIO_TYPE_INT:
            env_p->CallVoidMethod(bundle, bundleOffsets_p->mPutInt,
                                  env_p->NewStringUTF(listIterator->key),
                                  listIterator->data.int_value);
            break;
        case FMRADIO_TYPE_STRING:
            env_p->CallVoidMethod(bundle, bundleOffsets_p->mPutString,
                                  env_p->NewStringUTF(listIterator->key),
                                  env_p->NewStringUTF(listIterator->data.string_value));
            break;
        default:
            ALOGE("warning. Unknown command ret item type %d\n",
                 listIterator->type);
            break;
        }

        listIterator++;
    }
    return bundle;
}

void freeExtraCommandRetList(struct fmradio_extra_command_ret_item_t *itemList)
{
    struct fmradio_extra_command_ret_item_t *listIterator = itemList;

    while (listIterator && listIterator->key != NULL) {
        free(listIterator->key);
        if (listIterator->type == FMRADIO_TYPE_STRING) {
            free(listIterator->data.string_value);
        }
        listIterator++;
    }
    free(itemList);

}

static bool jstringArray2cstringArray(JNIEnv * env_p, jobjectArray jarray,
                                      char ***carray_p)
{
    int arrayLength;

    if (jarray != NULL &&
        (arrayLength = env_p->GetArrayLength(jarray)) > 0) {
        int d;

        char **carray = (char **) calloc(arrayLength + 1, sizeof(*carray));

        if (carray == NULL) {
            ALOGE("malloc failed\n");
            return false;
        }

        for (d = 0; d < arrayLength; d++) {
            const char *itemstr = env_p->GetStringUTFChars((jstring)
                                                           env_p->GetObjectArrayElement
                                                           (jarray, d),
                                                           NULL);

            carray[d] = strdup(itemstr);
            env_p->ReleaseStringUTFChars((jstring)
                                         env_p->GetObjectArrayElement
                                         (jarray, d), itemstr);
            if (carray[d] == NULL) {
                ALOGE("strdup failed\n");
                /* free any arrays already allocated, free the array pointer
                 * and exit */
                while (--d >= 0) {
                    free(carray[d]);
                }
                free(carray);
                return false;
            }
        }
        carray[arrayLength] = NULL;     /* to be able to detect end of array */
        *carray_p = carray;
    } else {
        *carray_p = NULL;
    }

    return true;
}

static void freeCstringArray(char **cstringarray)
{
    if (cstringarray != NULL && *cstringarray != NULL) {
        char **iterator = cstringarray;

        while (*iterator != NULL) {
            free(*iterator);
            iterator++;
        }
        free(cstringarray);

    }
}

/*
 * Function to temporary resume a paused device for executing command
 */
void androidFmRadioTempResumeIfPaused(struct FmSession_t *session_p)
{
    if (session_p->state == FMRADIO_STATE_PAUSED) {
        /* no need to handle error here, the main command will fail anyway */
        (void)session_p->vendorMethods_p->resume(&session_p->vendorData_p);
    }
}

/*
 * Function to pause device after a temporary resume
 */
void androidFmRadioPauseIfTempResumed(struct FmSession_t *session_p)
{
    if (session_p->state == FMRADIO_STATE_PAUSED) {
        if (session_p->vendorMethods_p->pause(&session_p->vendorData_p)
            != FMRADIO_OK)  {
            ALOGE("androidFmRadioPauseIfTempResumed: pause failed, force "
                 "resetting");
            /*
             * Failed to set pause again. Since the device is supposed to be
             * paused we can't leave it this way, our best choice is to issue
             * a force reset to put the device in a known idle state. Temporary
             * drop locks since we might trigger callbacks.
             */
            FMRADIO_SET_STATE(session_p, FMRADIO_STATE_IDLE);
            /* temporary drop lock to allow reset triggered callbacks */
            pthread_mutex_unlock(session_p->dataMutex_p);
            int retval = session_p->vendorMethods_p->reset(&session_p->vendorData_p);
            pthread_mutex_lock(session_p->dataMutex_p);

            if (retval != FMRADIO_OK) {
                ALOGE("androidFmRadioPauseIfTempResumed: CRITICAL ERROR: "
                     "can't reset device");
                /* if we can't even reset we have a critical problem */
                session_p->callbacks_p->onForcedReset(FMRADIO_RESET_CRITICAL);
            } else {
                /* now we are in known state */
                session_p->callbacks_p->onForcedReset(FMRADIO_RESET_NON_CRITICAL);
            }

            /* unload vendor driver */
            androidFmRadioUnLoadFmLibrary(session_p);
            session_p->isRegistered = false;
        }
    }
}

bool
androidFmRadioIsValidEventForState(struct FmSession_t *session_p,
                                   enum FmRadioCommand_t event)
{
    bool retval;

    if (!session_p->isRegistered && (event != FMRADIO_EVENT_RESET) &&
        (event != FMRADIO_EVENT_START) && (event != FMRADIO_EVENT_START_ASYNC)) {
        ALOGE("ERROR - library not loaded, only reset, start and start async are valid events");
        retval = false;
    } else if (session_p->ongoingReset && (event != FMRADIO_EVENT_RESET)) {
        ALOGE("ERROR - ongoing reset invalidates state changes");
        retval = false;
    } else if (!(*session_p->validEventsForStates_p)[event][session_p->state]) {
        ALOGE("ERROR - Invalid event %u for state %u", event,
             session_p->state);
        retval = false;
    } else {
        retval = true;
    }

    return retval;
}

void
androidFmRadioThrowException(struct FmSession_t *session_p,
                             const char *exception,
                             const char *message, const char *file,
                             int line, const char *function)
{
    bool reAttached = false;

    JNIEnv *env;

    ALOGI("androidFmRadioThrowException, %s ('%s') @ %s %d (%s)\n",
         exception, message, file, line, function);


    if (session_p->jvm_p->GetEnv((void **) &env, JNI_VERSION_1_4) !=
        JNI_OK) {
        /* we are probably a subthread. Attach instead */
        if (session_p->jvm_p->AttachCurrentThread(&env, NULL) != JNI_OK) {
            ALOGE("Error, can't attch current thread\n");
            return;
        }
        reAttached = true;
    }

    jniThrowException(env, exception, message);

    if (reAttached) {
        session_p->jvm_p->DetachCurrentThread();
    }

}


/*
 *  Functions for loading and unloading the vendor dynamic library
 *   (libfmradio.xxxxxx.so)
 */

static bool
androidFmRadioGetLibraryName(enum RadioMode_t mode,
                             char *libraryName)
{
    DIR *dirp;
    struct dirent *dp;
    char foundName[sizeof(dp->d_name)] = "";

    if ((dirp = opendir(LIBRARY_PATH)) == NULL) {
        ALOGE("couldn't open path '%s'", LIBRARY_PATH);
        return false;
    }

    errno = 0;

    while ((dp = readdir(dirp)) != NULL) {
        char *name = dp->d_name;
        size_t nameLength = strlen(name);

        /*
         * since prefix end with . and suffix start with . we need to make sure
         * we don't get a match on something that is shorter than the two
         * strings concatinated
         */
        if (nameLength < sizeof(LIBRARY_PREFIX) + sizeof(LIBRARY_SUFFIX) - 2) {
            continue;
        }

        if ((nameLength > sizeof(LIBRARY_SUFFIX) -1) &&
            (strcmp(name + nameLength - sizeof(LIBRARY_SUFFIX) + 1,
                    LIBRARY_SUFFIX) == 0)) {
        } else {
            continue;
        }

        if (strncmp(name, LIBRARY_PREFIX, sizeof(LIBRARY_PREFIX) - 1) != 0) {
            /* prefix doesn't match, this is not our file */
            continue;
        }


        /* check if it is RX/TX specific or general */

        if ((nameLength > sizeof(LIBRARY_SUFFIX_RX) -1) &&
            (strcmp(name + nameLength - sizeof(LIBRARY_SUFFIX_RX) + 1,
                    LIBRARY_SUFFIX_RX) == 0)) {
            if (mode == FMRADIO_RX) {
                strcpy(foundName, name);
                break;
            }
        } else if ((nameLength > sizeof(LIBRARY_SUFFIX_TX) -1) &&
                   (strcmp(name + nameLength - sizeof(LIBRARY_SUFFIX_TX) + 1,
                           LIBRARY_SUFFIX_TX) == 0)) {
            if (mode == FMRADIO_TX) {
                strcpy(foundName, name);
                break;
            }
        } else {
            strcpy(foundName, name);
            /* do not break, if there is a rx/tx specific name we prefer it */
        }
    }

    (void)closedir(dirp);

    if (dp == NULL && errno != 0) {
        ALOGE("error reading directory, errno = %d\n", errno);
        return false;
    }

    if (foundName[0] == '\0') {
        ALOGE("No matching library found in path %s\n", LIBRARY_PATH);
        return false;
    }

    // copy name with file path
    strcpy(libraryName, LIBRARY_PATH);
    // add slash if not last of path
    if (libraryName[strlen(libraryName) -1] != '/') {
        strcat(libraryName, "/");
    }
    strcat(libraryName, foundName);
    return true;
}

bool
androidFmRadioLoadFmLibrary(struct FmSession_t * session_p,
                            enum RadioMode_t mode)
{
    char fmLibName[FM_LIBRARY_NAME_MAX_LENGTH + 1] = "";
    fmradio_reg_func_t fmRegFunc = NULL;
    unsigned int magicVal = 0;
    bool retval = false;
    bool missingMandatoryFunctions = false;

    // read library directory and find matching library

    if (!androidFmRadioGetLibraryName(mode, fmLibName)) {
        goto funcret;
    }

    // load the library
    session_p->fmLibrary_p = dlopen(fmLibName, RTLD_LAZY);
    if (session_p->fmLibrary_p == NULL) {
        ALOGI("Could not load library '%s'\n", fmLibName);
        goto funcret;
    } else {
        ALOGI("Loaded library %s\n", fmLibName);
    }

    // now we have loaded the library, check for function
    fmRegFunc = (fmradio_reg_func_t) dlsym(session_p->fmLibrary_p,
                                           FMRADIO_REGISTER_FUNC);

    if (fmRegFunc == NULL) {
        ALOGE("Could not find symbol '%s' in loaded library '%s'\n",
             FMRADIO_REGISTER_FUNC, fmLibName);
        goto closelib;
    }

    // call function
    if (fmRegFunc(&magicVal, session_p->vendorMethods_p) != 0) {
        ALOGE("Loaded function '%s' returned unsuccessful\n",
             FMRADIO_REGISTER_FUNC);
        goto closelib;
    }

    // just to make sure correct function was called
    if (magicVal != FMRADIO_SIGNATURE) {
        ALOGE("Loaded function '%s' returned successful but failed setting magic value\n", FMRADIO_REGISTER_FUNC);
        goto closelib;
    }

    // some methods are considered mandatory to implement, check them

    if(mode == FMRADIO_RX && session_p->vendorMethods_p->rx_start == NULL) {
        missingMandatoryFunctions = true;
        ALOGE("Mandatory method rx_start is not implemented\n");
    }
    if(mode == FMRADIO_TX && session_p->vendorMethods_p->tx_start == NULL) {
        missingMandatoryFunctions = true;
        ALOGE("Mandatory method tx_start is not implemented\n");
    }
    if(session_p->vendorMethods_p->pause == NULL) {
        missingMandatoryFunctions = true;
        ALOGE("Mandatory method pause is not implemented\n");
    }
    if(session_p->vendorMethods_p->resume == NULL) {
        missingMandatoryFunctions = true;
        ALOGE("Mandatory method resume is not implemented\n");
    }
    if(session_p->vendorMethods_p->reset == NULL) {
        missingMandatoryFunctions = true;
        ALOGE("Mandatory method reset is not implemented\n");
    }
    if(missingMandatoryFunctions) {
        goto closelib;
    }

    retval = true;
    goto funcret;
  closelib:
    dlclose(session_p->fmLibrary_p);
  funcret:
    return retval;
}

void
androidFmRadioUnLoadFmLibrary(struct FmSession_t * session_p)
{

    if (session_p->fmLibrary_p != NULL) {
        dlclose(session_p->fmLibrary_p);
        free(session_p->vendorMethods_p);
        session_p->vendorMethods_p = NULL;
    }
}

/* methods common for both RX and TX */

static bool androidFmRadioStartSyncPartner(struct FmSession_t *session_p)
{
    /* lock is held when entering this mehod */
    int maxTime = 5000;    /* in ms */

    /* if partner has ongoing reset await it finishing */
    while (session_p->partnerSession_p->ongoingReset && maxTime > 0) {
        pthread_mutex_unlock(session_p->dataMutex_p);
        usleep(250000);
        pthread_mutex_lock(session_p->dataMutex_p);
        maxTime -= 250;
    }

    if (session_p->partnerSession_p->ongoingReset) {
        ALOGE("partner session stale reset");
        return false;
    }

    /* if partner has any other state than IDLE it should be stoped */
    if (session_p->partnerSession_p->state != FMRADIO_STATE_IDLE) {
        /* if partner is starting it must be allowed to finish */

        session_p->partnerSession_p->ongoingReset = true; /* to make sure it doesn't do anything else */

        while (!session_p->ongoingReset &&
               (session_p->partnerSession_p->state == FMRADIO_STATE_STARTING && maxTime > 0)){
            pthread_mutex_unlock(session_p->dataMutex_p);
            usleep(250000);
            pthread_mutex_lock(session_p->dataMutex_p);
            maxTime -= 250;
        }

        session_p->partnerSession_p->ongoingReset = false;

        /* if we now have a ongoing reset on our own session just exit */

        if (session_p->ongoingReset) {
            return false;
        }

        /* if partner is still starting we should exist */
        if (session_p->partnerSession_p->state == FMRADIO_STATE_STARTING) {
            ALOGE("time-out waiting for partner startup");
            return false;
        }

        /* unless partner already is IDLE, reset and send forced reset */
        if (session_p->partnerSession_p->state != FMRADIO_STATE_IDLE) {
            /* temporary drop lock to allow reset triggered callbacks */
            pthread_mutex_unlock(session_p->dataMutex_p);
            session_p->partnerSession_p->vendorMethods_p->reset(&session_p->partnerSession_p->vendorData_p);
            pthread_mutex_lock(session_p->dataMutex_p);
            FMRADIO_SET_STATE(session_p->partnerSession_p, FMRADIO_STATE_IDLE);
            session_p->partnerSession_p->callbacks_p->onForcedReset(FMRADIO_RESET_OTHER_IN_USE);
        }
        /* unload partner vendor driver */
        if (session_p->partnerSession_p->isRegistered) {
            androidFmRadioUnLoadFmLibrary(session_p->partnerSession_p);
            session_p->partnerSession_p->isRegistered = false;
        }
    }

    return true;
}

static void *execute_androidFmRadioStartAsync(void *args)
{
    struct FmRadioStartAsyncParameters *inArgs_p = (struct FmRadioStartAsyncParameters *)args;

    int (*startFunc) (void **, const struct fmradio_vendor_callbacks_t *, int, int, int,
                      int) = inArgs_p->startFunc;
    struct FmSession_t *session_p = inArgs_p->session_p;

    const struct fmradio_vendor_callbacks_t *callbacks_p = inArgs_p->callbacks_p;

    int lowFreq = inArgs_p->lowFreq;
    int highFreq = inArgs_p->highFreq;
    int defaultFreq = inArgs_p->defaultFreq;
    int grid = inArgs_p->grid;
    int retval = 0;

    free(inArgs_p);

    pthread_mutex_lock(session_p->dataMutex_p);

    /*
     * if other session is starting should wait for them to finish unless we
     * get a ongoing reset
     */

    if (!androidFmRadioStartSyncPartner(session_p)) {
        retval = -1;
        goto fix_state_and_send_retval;
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    retval =
        startFunc(&session_p->vendorData_p, callbacks_p, lowFreq, highFreq,
                  defaultFreq, grid);

    pthread_mutex_lock(session_p->dataMutex_p);
    /* sanity check, not even reset should alter the state when starting */
    if (session_p->state != FMRADIO_STATE_STARTING) {
        ALOGE("state not starting when going to started...");
        retval = -1;
    }

  fix_state_and_send_retval:
    if (retval >= 0) {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_STARTED);
    } else {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_IDLE);
        /* unload vendor driver */
        androidFmRadioUnLoadFmLibrary(session_p);
        session_p->isRegistered = false;
    }

    /*
     * these need to be called after state is updated and after the lock
     * has been dropped
     */
    if (retval >= 0) {
        session_p->callbacks_p->onStarted();
    } else {
        session_p->callbacks_p->onError();
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    pthread_exit(NULL);
    return NULL;
}

int
androidFmRadioStart(struct FmSession_t *session_p, enum RadioMode_t mode,
                    const struct fmradio_vendor_callbacks_t *callbacks_p,
                    bool async, int lowFreq, int highFreq, int defaultFreq,
                    int grid)
{
    int retval = 0;

    int (*startFunc) (void **, const struct fmradio_vendor_callbacks_t *,
                      int, int, int, int) = NULL;

    pthread_mutex_lock(session_p->dataMutex_p);
    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_START)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }
    // set our state to STARTING here to make sure the partner session
    // can't start again after it is finished
    FMRADIO_SET_STATE(session_p, FMRADIO_STATE_STARTING);

    // if we haven't registred the library yet do it

    if (!session_p->isRegistered) {
        session_p->vendorMethods_p = (fmradio_vendor_methods_t *)
            malloc(sizeof(*session_p->vendorMethods_p));
        if (session_p->vendorMethods_p == NULL) {
            ALOGE("malloc failed");
            retval = FMRADIO_IO_ERROR;
            goto early_exit;
        } else if (androidFmRadioLoadFmLibrary(session_p, mode)) {
            session_p->isRegistered = true;
        } else {
            ALOGE("vendor registration failed");
            free(session_p->vendorMethods_p);
            retval = FMRADIO_IO_ERROR;
            goto early_exit;
        }
    }

    if (mode == FMRADIO_RX) {
        startFunc = session_p->vendorMethods_p->rx_start;
    } else if (mode == FMRADIO_TX) {
        startFunc = session_p->vendorMethods_p->tx_start;
    }

    if (!startFunc) {
        ALOGE("androidFmRadioStart - ERROR - No valid start function found.");
        retval = FMRADIO_UNSUPPORTED_OPERATION;
        goto early_exit;
    }

    if (async) {
        pthread_t execute_thread;

        struct FmRadioStartAsyncParameters *args_p = (struct FmRadioStartAsyncParameters *)
                malloc(sizeof(struct FmRadioStartAsyncParameters));    /* freed in created thread */

        if (args_p == NULL) {
            ALOGE("malloc failed");
            retval = FMRADIO_IO_ERROR;
            goto early_exit;
        }

        args_p->startFunc = startFunc;
        args_p->session_p = session_p;
        args_p->callbacks_p = callbacks_p;
        args_p->lowFreq = lowFreq;
        args_p->highFreq = highFreq;
        args_p->defaultFreq = defaultFreq;
        args_p->grid = grid;

        // we need to create a new thread actually executing the command
        if (pthread_create
            (&execute_thread, NULL, execute_androidFmRadioStartAsync,
             (void *) args_p) != 0) {
            ALOGE("pthread_create failure...");
            free(args_p);
            retval = FMRADIO_IO_ERROR;
        } else {
            pthread_detach(execute_thread);
        }
    } else {
        if (!androidFmRadioStartSyncPartner(session_p)) {
            retval = -1;
            goto early_exit;
        }

        /*
         * drop lock during long time call but set state to make sure no other
         * process tries to start while we are starting. Do not use
         * FMRADIO_SET_STATE macro since it will trigger a onStateChanged
         * callback
         */
        pthread_mutex_unlock(session_p->dataMutex_p);
        retval =
            startFunc(&session_p->vendorData_p, callbacks_p, lowFreq,
                      highFreq, defaultFreq, grid);
        /* regain lock */
        pthread_mutex_lock(session_p->dataMutex_p);
        /* check that nothing has happened before we regained the lock */
        if (session_p->state != FMRADIO_STATE_STARTING) {
            ALOGE("Error, radio not in IDLE when about to set started mode");
        }
    }

    // if successful syncronous start update state
    if (retval == 0 && !async) {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_STARTED);
    }

  early_exit:

    if (retval < 0) {
        if (session_p->state != FMRADIO_STATE_IDLE) {
            FMRADIO_SET_STATE(session_p, FMRADIO_STATE_IDLE);
        }

        if (retval != FMRADIO_INVALID_STATE && session_p->isRegistered) {
            androidFmRadioUnLoadFmLibrary(session_p);
            session_p->isRegistered = false;
        }
    }

  drop_lock:

    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if ((retval == FMRADIO_INVALID_PARAMETER) ||
               (retval == FMRADIO_UNSUPPORTED_OPERATION)) {
        THROW_UNSUPPORTED_OPERATION(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    return retval;
}

int androidFmRadioPause(struct FmSession_t *session_p)
{
    int retval;

    pthread_mutex_lock(session_p->dataMutex_p);
    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_PAUSE)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (session_p->state == FMRADIO_STATE_PAUSED) {
        // already paused, just return
        retval = FMRADIO_OK;
        goto drop_lock;
    }


    retval =  session_p->vendorMethods_p->pause(&session_p->vendorData_p);

    if (retval == 0) {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_PAUSED);
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    return retval;
}

int androidFmRadioResume(struct FmSession_t *session_p)
{
    int retval = 0;

    pthread_mutex_lock(session_p->dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_RESUME)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (session_p->state == FMRADIO_STATE_STARTED) {
        //already started, just return
        retval = FMRADIO_OK;
        goto drop_lock;
    }

    retval = session_p->vendorMethods_p->resume(&session_p->vendorData_p);

    // if successful update state
    if (retval == 0) {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_STARTED);
    }
    // nothing on failure

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);
    return retval;
}

int androidFmRadioReset(struct FmSession_t *session_p)
{
    int retval = FMRADIO_OK;
    int oldState = session_p->state;

    pthread_mutex_lock(session_p->dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_RESET)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    /* Worker threads must be cleaned up before sending reset */
    if(session_p->state == FMRADIO_STATE_SCANNING){
        pthread_mutex_unlock(session_p->dataMutex_p);
        androidFmRadioStopScan(session_p);
        pthread_mutex_lock(session_p->dataMutex_p);
        /* Waiting for worker thread to exit gracefully */
        pthread_cond_wait(&session_p->sync_cond,
                session_p->dataMutex_p);
    }
    /* idle or about to be reset, just return state */
    if (session_p->ongoingReset || oldState == FMRADIO_STATE_IDLE) {
        retval = oldState;
        goto drop_lock;
    }
    session_p->ongoingReset = true;

    /* if we are in starting state we must await the start finishing */
    if (oldState == FMRADIO_STATE_STARTING) {
        /* we need to await end of start before starting */
        int maxTime = 5000;    /* in ms */
        do {
            pthread_mutex_unlock(session_p->dataMutex_p);
            usleep(250000);
            pthread_mutex_lock(session_p->dataMutex_p);
            maxTime -= 250;
        } while (maxTime > 0 && (session_p->state == FMRADIO_STATE_STARTING));
    }

    /* if we still are in STARTING state we must fail now */
    if (session_p->state == FMRADIO_STATE_STARTING) {
        retval = FMRADIO_IO_ERROR;
        goto drop_ongoing_reset;
    }

    /*
     * we need to temporary release lock since reset might trigger
     * callbacks. Set flag to not allow any state changing command
     */
    FMRADIO_SET_STATE(session_p, FMRADIO_STATE_IDLE);

    pthread_mutex_unlock(session_p->dataMutex_p);
    retval = session_p->vendorMethods_p->
                        reset(&session_p->vendorData_p);
    pthread_mutex_lock(session_p->dataMutex_p);

    // if successful unload vendor driver
    if (retval >= 0) {
        retval = oldState;
        if (session_p->isRegistered) {
            androidFmRadioUnLoadFmLibrary(session_p);
            session_p->isRegistered = false;
        }
    } else {
        ALOGE("androidFmRadioReset failed");
    }
    // nothing on failure
  drop_ongoing_reset:
    session_p->ongoingReset = false;
  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    return retval;
}

void
androidFmRadioSetFrequency(struct FmSession_t *session_p, int frequency)
{
    int retval = 0;

    pthread_mutex_lock(session_p->dataMutex_p);
    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_SET_FREQUENCY)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (session_p->vendorMethods_p->set_frequency) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(session_p);

        retval =
            session_p->vendorMethods_p->set_frequency(&session_p->
                                                     vendorData_p,
                                                     frequency);

        androidFmRadioPauseIfTempResumed(session_p);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

    // no state is ever updated
    if (retval < 0) {
        ALOGE("androidFmRadioSetFrequency failed\n");
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval == FMRADIO_INVALID_PARAMETER) {
        THROW_ILLEGAL_ARGUMENT(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);
}

int androidFmRadioGetFrequency(struct FmSession_t *session_p)
{
    int retval = 0;

    pthread_mutex_lock(session_p->dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_GET_FREQUENCY)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (session_p->vendorMethods_p->get_frequency) {
        /* if in pause state temporary resume */
        androidFmRadioTempResumeIfPaused(session_p);

        retval =
            session_p->vendorMethods_p->get_frequency(&session_p->
                                                     vendorData_p);

        androidFmRadioPauseIfTempResumed(session_p);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

  drop_lock:
    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);

    return retval;
}

void androidFmRadioStopScan(struct FmSession_t *session_p)
{
    int retval = 0;

    pthread_mutex_lock(session_p->dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_STOP_SCAN)) {
        goto drop_lock;
    }

    if (session_p->state != FMRADIO_STATE_SCANNING) {
        /* if we're not in scanning, don't attempt anything just return */
        goto drop_lock;
    }

    if (session_p->vendorMethods_p->stop_scan) {
        retval =
            session_p->vendorMethods_p->stop_scan(&session_p->vendorData_p);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

    if (retval == 0) {
        session_p->lastScanAborted = true;
    }

    retval = FMRADIO_OK;
  drop_lock:
    /* note - no exceptions. Just return */

    if (retval != FMRADIO_OK) {
        ALOGE("androidFmRadioStopScan failed (%d), ignored.\n", retval);
    }
    pthread_mutex_unlock(session_p->dataMutex_p);
}

static void *execute_androidFmRadioSendExtraCommand(void *args_p)
{
    struct FmRadioSendExtraCommandParameters* inArgs_p = (struct FmRadioSendExtraCommandParameters*)args_p;
    struct FmSession_t *session_p = inArgs_p->session_p;
    char *c_command = inArgs_p->c_command;
    char **parameter = inArgs_p->cparams;
    struct fmradio_extra_command_ret_item_t *returnParam = NULL;
    int retval;

    free(inArgs_p);

    pthread_mutex_lock(session_p->dataMutex_p);

    // we should be in state EXTRA_COMMAND
    if (session_p->state != FMRADIO_STATE_EXTRA_COMMAND) {
        ALOGE("execute_androidFmRadioSendExtraCommand - warning, state not extra commands\n");
    }

    if (session_p->vendorMethods_p->send_extra_command != NULL) {
        pthread_mutex_unlock(session_p->dataMutex_p);
        retval =
            session_p->vendorMethods_p->send_extra_command(&session_p->
                                                         vendorData_p,
                                                         c_command,
                                                         parameter,
                                                         &returnParam);
        pthread_mutex_lock(session_p->dataMutex_p);
        freeCstringArray(parameter);
    } else {
        retval = FMRADIO_UNSUPPORTED_OPERATION;
    }

    if (session_p->state != FMRADIO_STATE_EXTRA_COMMAND) {
        ALOGE("State changed while executing extra commands (state now %d), keeping\n", session_p->state);
    } else {
        if (session_p->pendingPause) {
            session_p->vendorMethods_p->pause(&session_p->
                                                  vendorData_p);
            FMRADIO_SET_STATE(session_p, FMRADIO_STATE_PAUSED);
        } else {
            FMRADIO_SET_STATE(session_p, session_p->oldState);
        }
    }

    session_p->pendingPause = false;

    if (retval >= 0) {
        session_p->callbacks_p->onSendExtraCommand(c_command, returnParam);
    } else {
        session_p->callbacks_p->onError();
    }

    if (returnParam != NULL) {
        freeExtraCommandRetList(returnParam);
    }

    if (c_command != NULL) {
        free(c_command);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);
    pthread_exit(NULL);
    return NULL;
}

void
androidFmRadioSendExtraCommand(struct FmSession_t *session_p,
                               JNIEnv * env_p, jstring jcommand,
                               jobjectArray parameter)
{
    int retval = FMRADIO_OK;
    char **cparams;
    struct FmRadioSendExtraCommandParameters *args_p = NULL;
    pthread_t execute_thread;

    pthread_mutex_lock(session_p->dataMutex_p);

    if (!androidFmRadioIsValidEventForState
        (session_p, FMRADIO_EVENT_EXTRA_COMMAND)) {
        retval = FMRADIO_INVALID_STATE;
        goto drop_lock;
    }

    if (!jstringArray2cstringArray(env_p, parameter, &cparams)) {
        retval = FMRADIO_IO_ERROR;
        goto drop_lock;
    }

    session_p->oldState = session_p->state;

    args_p = (struct FmRadioSendExtraCommandParameters *) malloc(sizeof(struct FmRadioSendExtraCommandParameters));     /* freed in created thread */
    if (args_p == NULL) {
        ALOGE("malloc failed\n");
        retval = FMRADIO_IO_ERROR;
    }

    if (retval == FMRADIO_OK) {
        const char* c_command = env_p->GetStringUTFChars(jcommand, 0);

        args_p->session_p = session_p;
        args_p->c_command = strdup(c_command);
        args_p->cparams = cparams;

        env_p->ReleaseStringUTFChars(jcommand, c_command);

        // we need to create a new thread actually executing the command
        if (pthread_create
            (&execute_thread, NULL, execute_androidFmRadioSendExtraCommand,
             args_p) != 0) {
            ALOGE("pthread_create failed\n");
            free(args_p->c_command);
            free(args_p);
            retval = FMRADIO_IO_ERROR;
        } else {
            pthread_detach(execute_thread);
        }

    }

    if (retval == FMRADIO_OK) {
        FMRADIO_SET_STATE(session_p, FMRADIO_STATE_EXTRA_COMMAND);
    }

  drop_lock:

    if (retval == FMRADIO_INVALID_STATE) {
        THROW_INVALID_STATE(session_p);
    } else if (retval < 0) {
        THROW_IO_ERROR(session_p);
    }

    pthread_mutex_unlock(session_p->dataMutex_p);
}


namespace android {
int registerAndroidFmRadioReceiver(JavaVM* vm, JNIEnv *env);
int registerAndroidFmRadioTransmitter(JavaVM* vm, JNIEnv *env);
};

using namespace android;

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("GetEnv failed!");
        return result;
    }
    ALOG_ASSERT(env, "Could not retrieve the env!");

    registerAndroidFmRadioReceiver(vm, env);
    registerAndroidFmRadioTransmitter(vm, env);

    return JNI_VERSION_1_4;
}
