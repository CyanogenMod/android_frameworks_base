/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

#ifndef ANDROID_MEMORY_HEAP_ION_H
#define ANDROID_MEMORY_HEAP_ION_H

#include <stdlib.h>
#include <stdint.h>

#include <binder/MemoryHeapBase.h>
#include <binder/IMemory.h>
#include <utils/SortedVector.h>
#include <utils/threads.h>
#include <linux/ion.h>

namespace android {

class MemoryHeapBase;

// ---------------------------------------------------------------------------

class MemoryHeapIon : public MemoryHeapBase
{
public:
    MemoryHeapIon(const char*, size_t, uint32_t, long unsigned int);
    MemoryHeapIon();
    ~MemoryHeapIon();

    status_t mapIonFd(int fd, size_t size, unsigned long memory_type, int flags);

    status_t ionInit(int ionFd, void *base, int size, int flags,
                                const char* device, struct ion_handle *handle,
                                int ionMapFd);

private:
	int mIonDeviceFd;  /*fd we get from open("/dev/ion")*/
	struct ion_handle *mIonHandle;  /*handle we get from ION_IOC_ALLOC*/ };

// ---------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_MEMORY_HEAP_ION_H
