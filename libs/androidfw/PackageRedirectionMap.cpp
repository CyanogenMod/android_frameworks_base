/*
 * Copyright (C) 2006 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

//
// Provide access to read-only assets.
//

#define LOG_TAG "packageresmap"

#include <androidfw/PackageRedirectionMap.h>
#include <androidfw/ResourceTypes.h>
#include <utils/misc.h>

using namespace android;

PackageRedirectionMap::PackageRedirectionMap()
    : mPackage(-1), mEntriesByType(NULL)
{
}

static void clearEntriesByType(uint32_t** entriesByType)
{
    SharedBuffer* buf = SharedBuffer::bufferFromData(entriesByType);
    const size_t N = buf->size() / sizeof(entriesByType[0]);
    for (size_t i = 0; i < N; i++) {
        uint32_t* entries = entriesByType[i];
        if (entries != NULL) {
            SharedBuffer::bufferFromData(entries)->release();
        }
    }
    buf->release();
}

PackageRedirectionMap::~PackageRedirectionMap()
{
    if (mEntriesByType != NULL) {
        clearEntriesByType(mEntriesByType);
    }
}

static void* ensureCapacity(void* data, size_t nmemb, size_t size)
{
    SharedBuffer* buf;
    size_t currentSize;

    if (data != NULL) {
        buf = SharedBuffer::bufferFromData(data);
        currentSize = buf->size();
    } else {
        buf = NULL;
        currentSize = 0;
    }

    size_t minSize = nmemb * size;
    if (minSize > currentSize) {
        unsigned int requestSize = roundUpPower2(minSize);
        if (buf == NULL) {
            buf = SharedBuffer::alloc(requestSize);
        } else {
            buf = buf->editResize(requestSize);
        }
        memset((unsigned char*)buf->data()+currentSize, 0, requestSize - currentSize);
    }

    return buf->data();
}

bool PackageRedirectionMap::addRedirection(uint32_t fromIdent, uint32_t toIdent)
{
    const int package = Res_GETPACKAGE(fromIdent);
    const int type = Res_GETTYPE(fromIdent);
    const int entry = Res_GETENTRY(fromIdent);

    // The first time we add a redirection we can infer the package for all
    // future redirections.
    if (mPackage == -1) {
        mPackage = package+1;
    } else if (mPackage != (package+1)) {
        ALOGW("cannot add redirection for conflicting package 0x%02x (expecting package 0x%02x)\n", package+1, mPackage);
        return false;
    }

    mEntriesByType = (uint32_t**)ensureCapacity(mEntriesByType, type + 1, sizeof(uint32_t*));
    uint32_t* entries = mEntriesByType[type];
    entries = (uint32_t*)ensureCapacity(entries, entry + 1, sizeof(uint32_t));
    entries[entry] = toIdent;
    mEntriesByType[type] = entries;

    return true;
}

uint32_t PackageRedirectionMap::lookupRedirection(uint32_t fromIdent)
{
    if (mPackage == -1 || mEntriesByType == NULL || fromIdent == 0) {
        return 0;
    }

    const int package = Res_GETPACKAGE(fromIdent);
    const int type = Res_GETTYPE(fromIdent);
    const int entry = Res_GETENTRY(fromIdent);

    if (package+1 != mPackage) {
        return 0;
    }

    size_t nTypes = getNumberOfTypes();
    if (type < 0 || type >= nTypes) {
        return 0;
    }
    uint32_t* entries = mEntriesByType[type];
    if (entries == NULL) {
        return 0;
    }
    size_t nEntries = getNumberOfEntries(type);
    if (entry < 0 || entry >= nEntries) {
        return 0;
    }
    return entries[entry];
}

int PackageRedirectionMap::getPackage()
{
    return mPackage;
}

size_t PackageRedirectionMap::getNumberOfTypes()
{
    if (mEntriesByType == NULL) {
        return 0;
    } else {
        return SharedBuffer::bufferFromData(mEntriesByType)->size() /
                sizeof(mEntriesByType[0]);
    }
}

size_t PackageRedirectionMap::getNumberOfUsedTypes()
{
    uint32_t** entriesByType = mEntriesByType;
    size_t N = getNumberOfTypes();
    size_t count = 0;
    for (size_t i=0; i<N; i++) {
        if (entriesByType[i] != NULL) {
            count++;
        }
    }
    return count;
}

size_t PackageRedirectionMap::getNumberOfEntries(int type)
{
    uint32_t* entries = mEntriesByType[type];
    if (entries == NULL) {
        return 0;
    } else {
        return SharedBuffer::bufferFromData(entries)->size() /
                sizeof(entries[0]);
    }
}

size_t PackageRedirectionMap::getNumberOfUsedEntries(int type)
{
    size_t N = getNumberOfEntries(type);
    uint32_t* entries = mEntriesByType[type];
    size_t count = 0;
    for (size_t i=0; i<N; i++) {
        if (entries[i] != 0) {
            count++;
        }
    }
    return count;
}

uint32_t PackageRedirectionMap::getEntry(int type, int entry)
{
    uint32_t* entries = mEntriesByType[type];
    return entries[entry];
}
