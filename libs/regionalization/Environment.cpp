/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <string.h>
#include <stdlib.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <private/regionalization/Environment.h>

using namespace android;

static const char* ENVIRONMENT_PROP = "ro.regionalization.support";
static const char* SPEC_FILE = "/persist/speccfg/spec";
static const char* BOOT_SHUTDOWN_FILE[2][2] = {
        {"/system/media/bootanimation.zip", "/system/media/shutdownanimation.zip"},
        {"/system/media/boot.wav", "/system/media/shutdown.wav"} };
static const char* OVERLAY_DIR = "/system/vendor/overlay";

static const bool kIsDebug = true;

Environment::Environment(void)
    : mStoragePos(NULL), mPackagesCount(0),
      mPackages(NULL), mMediaFile(NULL),
      mOverlayDir(NULL)
{
    mStoragePos = new char[PATH_MAX];
    mMediaFile  = new char[PATH_MAX];
    mOverlayDir = new char[PATH_MAX];
    if (mStoragePos == NULL || mMediaFile == NULL || mOverlayDir == NULL) {
        if (kIsDebug) {
            ALOGD("Regionalization Environment new memory error!");
        }
        return;
    }

    bool success = loadPackagesFromSpecFile();
    if (!success) {
        if (kIsDebug) {
            ALOGD("Regionalization Environment load packages for Carrier error!");
        }
    }
}

Environment::~Environment(void)
{
    if (mStoragePos != NULL) {
        delete[] mStoragePos;
    }

    if (mPackages != NULL) {
        for (int i=0; i < mPackagesCount; i++) {
            if (mPackages[i] != NULL) {
                delete[] mPackages[i];
            }
        }
        delete[] mPackages;
    }

    mPackagesCount = 0;

    if (mMediaFile != NULL) {
        delete[] mMediaFile;
    }

    if (mOverlayDir != NULL) {
        delete[] mOverlayDir;
    }
}

bool Environment::isSupported(void)
{
    char value[PROPERTY_VALUE_MAX];
    memset(value, 0, PROPERTY_VALUE_MAX * sizeof(char));
    property_get(ENVIRONMENT_PROP, value, "false");
    if (!strcmp(value, "true")) {
        return true;
    }

    return false;
}

bool Environment::loadPackagesFromSpecFile(void) {
    FILE* fSpec = NULL;
    if ((fSpec = fopen(SPEC_FILE, "r")) == NULL) {
        return false;
    }

    // Read first line to get storage position of packages
    int res = fscanf(fSpec, "%*[^=]=%s", mStoragePos);
    if (res < 1 || strcmp(mStoragePos, "") == 0) {
        fclose(fSpec);
        return false;
    }

    // Read second line to get count of packages.
    res = fscanf(fSpec, "%*[^=]=%d", &mPackagesCount);
    if (res < 1 || mPackagesCount <= 0) {
        fclose(fSpec);
        return false;
    }

    mPackages = new char*[mPackagesCount];
    if (mPackages == NULL) {
        fclose(fSpec);
        return false;
    }
    for (int i = 0; i < mPackagesCount; i++) {
        mPackages[i] = new char[PATH_MAX];
    }

    for (int i = 0; i < mPackagesCount; i++) {
        res = fscanf(fSpec, "%*[^=]=%s", mPackages[i]);
        if (res < 1) {
            fclose(fSpec);
            return false;
        }
    }

    fclose(fSpec);
    return true;
}

// type: {0:Animation; 1:Audio}
// state: {0:boot; 1:shutdown}
const char* Environment::getMediaFile(int type, int state)
{
    if (mPackagesCount != 0 && mStoragePos != NULL && mPackages != NULL) {
        for(int i = mPackagesCount-1; i >= 0; i--) {
            memset(mMediaFile, 0, PATH_MAX);
            strlcpy(mMediaFile, mStoragePos, PATH_MAX);
            strlcat(mMediaFile, "/", PATH_MAX);
            strlcat(mMediaFile, mPackages[i], PATH_MAX);
            strlcat(mMediaFile, BOOT_SHUTDOWN_FILE[type][state], PATH_MAX);
            if(access(mMediaFile, R_OK) == 0) {
                if (kIsDebug) {
                    ALOGD("Environment::getMediaFile() = %s\n", mMediaFile);
                }
                return mMediaFile;
            }
        }
    }

    return NULL;
}

const char* Environment::getOverlayDir(void)
{
    if (mPackagesCount != 0 && mStoragePos != NULL && mPackages != NULL) {
        for (int i = mPackagesCount-1; i >= 0; i--) {
            memset(mMediaFile, 0, PATH_MAX);
            strlcpy(mOverlayDir, mStoragePos, PATH_MAX);
            strlcat(mOverlayDir, "/", PATH_MAX);
            strlcat(mOverlayDir, mPackages[i], PATH_MAX);
            strlcat(mOverlayDir, OVERLAY_DIR, PATH_MAX);
            if (kIsDebug) {
                ALOGD("Environment::getOverlayDir() = %s\n", mOverlayDir);
            }
            // Check if PackageFrameworksRes dir exists.
            char overlayFile[PATH_MAX];
            memset(overlayFile, 0, PATH_MAX);
            strlcpy(overlayFile, mOverlayDir, PATH_MAX);
            strlcat(overlayFile, "/", PATH_MAX);
            strlcat(overlayFile, mPackages[i], PATH_MAX);
            strlcat(overlayFile, "FrameworksRes", PATH_MAX);
            if (access(overlayFile, R_OK) == 0) {
                if (kIsDebug) {
                    ALOGD("Environment::getOverlayDir() - overlayFile exists!\n");
                }
                return mOverlayDir;
            }
        }
    }

    return NULL;
}
