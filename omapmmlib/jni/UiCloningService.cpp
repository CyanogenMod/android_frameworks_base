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

#define LOG_TAG "UiCloningService"
#include "utils/Log.h"

#include "jni.h"
#include <binder/Parcel.h>

#include <fcntl.h>

using namespace android;

/*
 * Notes for Currently supported UI-Cloning-Feature
 *
 * 1. fb0 is on Overlay0 which is on LCD1.
 * 2. fb1 is on Overlay1 which can go to LCD2 or TV.
 * 3. UI-Cloning is supported on LCD2 and TV.
 * 4. Cloning of only fb0 is supported.
 * 5. For cloning of fb0 on Overlay1, fb1 is switched off.
 */

const char fb0Overlays [PATH_MAX] = "/sys/class/graphics/fb0/overlays";
const char fb1Overlays [PATH_MAX] = "/sys/class/graphics/fb1/overlays";
const char fb0FitToScreenOption[PATH_MAX] = "/sys/class/graphics/fb0/fit_to_screen";
const char overlay1ManagerName[PATH_MAX] = "/sys/devices/platform/omapdss/overlay1/manager";
const char overlay1ManagerEnabled[PATH_MAX] = "/sys/devices/platform/omapdss/overlay1/enabled";

enum displayId {
    DISPLAYID_NONE = -1,
    DISPLAYID_LCDSECONDARY = 1,
    DISPLAYID_TVHDMI = 2,
};

/*
 * Helper function to throw an arbitrary exception.
 *
 * Takes the exception class name, a format string
 */
static void jniThrowException(JNIEnv* env, const char* ex, const char* msg = NULL)
{
    if (jclass cls = env->FindClass(ex)) {
        env->ThrowNew(cls, msg);
        /*
         * This is usually not necessary -- local references are released
         * automatically when the native code returns to the VM.  It's
         * required if the code doesn't actually return, e.g. it's sitting
         * in a native event loop.
         */
        env->DeleteLocalRef(cls);
    }
}

int sysfile_write(const char* pathname, const void* buf, size_t size) {
    int fd = open(pathname, O_WRONLY);
    if (fd == -1) {
        LOGE("Can't open [%s]", pathname);
        return -1;
    }
    size_t written_size = write(fd, buf, size);
    if (written_size < size) {
        LOGE("Can't write [%s]", pathname);
        close(fd);
        return -1;
    }
    if (close(fd) == -1) {
        LOGE("cant close [%s]", pathname);
        return -1;
    }
    return 0;
}

/**
* a method to Clone UI to the requested Display Id.
*
* Input: integer between 0 and 3
* 0 - PRIMARY DISPLAY
* 1 - SECONDARY DISPLAY
* 2 - HDTV
* 3 - pico DLP
* these IDs should be in sync with overlay enums
**/
static void UiCloningService_CloneUiToDisplay(JNIEnv* env, jclass clazz, int displayId) {
    LOGD("UiCloningService_CloneUiToDisplay : DisplayId= [%d]", displayId);

    // Clone UI on Other Display
    if(displayId == DISPLAYID_LCDSECONDARY|| displayId == DISPLAYID_TVHDMI) {
        if (sysfile_write(overlay1ManagerEnabled, "0", sizeof("0")) < 0) {
            LOGE("Failed to set overlay1/enabled = 0");
            goto end;
        }

        if (sysfile_write(fb1Overlays, "", sizeof("")) < 0) {
            LOGE("Failed to set fb1/overlays = NULL");
            goto end;
        }
        if(displayId == DISPLAYID_LCDSECONDARY) {
            if (sysfile_write(overlay1ManagerName, "2lcd", sizeof("2lcd")) < 0) {
                LOGE("Failed to set overlay/manager = 2lcd");
                goto end;
            }
        } else if(displayId == DISPLAYID_TVHDMI) {
            if (sysfile_write(overlay1ManagerName, "tv", sizeof("tv")) < 0) {
                LOGE("Failed to set overlay/manager = tv");
                goto end;
            }
        }
        if (sysfile_write(fb0Overlays, "0,1", sizeof("0,1")) < 0) {
            LOGE("Failed to set fb0/overlays = 0,1");
            goto end;
        }
        if (sysfile_write(fb0FitToScreenOption, "1", sizeof("1")) < 0) {
            LOGE("Failed to set fb0/fit_to_screen = 1");
            goto end;
        }
        if (sysfile_write(overlay1ManagerEnabled, "1", sizeof("1")) < 0) {
            LOGE("Failed to set overlay1/enabled = 1");
            goto end;
        }
    }
    // Stop cloning UI on Other Display
    else if(displayId == DISPLAYID_NONE) {
        if (sysfile_write(overlay1ManagerEnabled, "0", sizeof("0")) < 0) {
            LOGE("Failed to set overlay1/enabled = 0");
            goto end;
        }
        if (sysfile_write(fb0Overlays, "0", sizeof("0")) < 0) {
            LOGE("Failed to set fb0/overlays = 0");
            goto end;
        }
        if (sysfile_write(fb0FitToScreenOption, "0", sizeof("0")) < 0) {
            LOGE("Failed to set fb0/fit_to_screen = 0");
            goto end;
        }
        if (sysfile_write(fb1Overlays, "1", sizeof("1")) < 0) {
            LOGE("Failed to set fb0/overlays = 1");
            goto end;
        }
        if (sysfile_write(overlay1ManagerName, "2lcd", sizeof("2lcd")) < 0) {
            LOGE("Failed to restore overlay/manager = 2lcd");
            goto end;
        }
    }

end:
    return;
}

// ----------------------------------------------------------------------------

/*
 * Array of methods.
 *
 * Each entry has three fields: the name of the method, the method
 * signature, and a pointer to the native implementation.
 */
static const JNINativeMethod gMethods[] = {
    { "CloneUiToDisplay","(I)V", (void*)UiCloningService_CloneUiToDisplay },
};

/*
 * Explicitly register all methods for our class.
 *
 * While we're at it, cache some class references and method/field IDs.
 *
 * Returns 0 on success.
 */
int UiCloningService_registerMethods(JNIEnv* env) {
    static const char* const kClassName =
        "com/ti/omap/omap_mm_library/UiCloningService";
    jclass clazz;

    /* look up the class */
    clazz = env->FindClass(kClassName);
    if (clazz == NULL) {
        LOGE("Can't find class %s\n", kClassName);
        return -1;
    }

    /* register all the methods */
    if (env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(gMethods[0])) != JNI_OK) {
        LOGE("Failed registering methods for %s\n", kClassName);
        return -1;
    }

    return 0;
}

