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

#define LOG_TAG "MemoryHeapIon"

#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>

#include <cutils/log.h>

#include <binder/MemoryHeapIon.h>
#include <binder/MemoryHeapBase.h>

#include <linux/ion.h>

namespace android {

MemoryHeapIon::MemoryHeapIon() : mIonDeviceFd(-1), mIonHandle(NULL)
{
}

MemoryHeapIon::MemoryHeapIon(const char* device, size_t size,
    uint32_t flags, unsigned long memory_types)
    : MemoryHeapBase()
{
    int open_flags = O_RDWR;
    if (flags & NO_CACHING)
         open_flags |= O_SYNC;

    int fd = open(device, open_flags);
    if (fd >= 0) {
            const size_t pagesize = getpagesize();
            size = ((size + pagesize-1) & ~(pagesize-1));
            if (mapIonFd(fd, size, memory_types, flags) == NO_ERROR) {
                MemoryHeapBase::setDevice(device);
            }
    }
}

status_t MemoryHeapIon::ionInit(int ionFd, void *base, int size, int flags,
				const char* device, struct ion_handle *handle,
				int ionMapFd) {
    mIonDeviceFd = ionFd;
    mIonHandle = handle;
    MemoryHeapBase::init(ionMapFd, base, size, flags, device);
    return NO_ERROR;
}


status_t MemoryHeapIon::mapIonFd(int fd, size_t size, unsigned long memory_type, int uflags)
{
    /* If size is 0, just fail the mmap. There is no way to get the size
     * with ion
     */
    int map_fd;

    struct ion_allocation_data data;
    struct ion_fd_data fd_data;
    struct ion_handle_data handle_data;
    void *base = NULL;

    data.len = size;
    data.align = getpagesize();
    data.flags = memory_type;

    if (ioctl(fd, ION_IOC_ALLOC, &data) < 0) {
        close(fd);
        return -errno;
    }

    if ((uflags & DONT_MAP_LOCALLY) == 0) {
        int flags = (uflags & MAP_LOCKED_MAP_POPULATE) ?
                    MAP_POPULATE|MAP_LOCKED : 0;

        fd_data.handle = data.handle;

        if (ioctl(fd, ION_IOC_MAP, &fd_data) < 0) {
            handle_data.handle = data.handle;
            ioctl(fd, ION_IOC_FREE, &handle_data);
            close(fd);
            return -errno;
        }

        base = (uint8_t*)mmap(0, size,
                PROT_READ|PROT_WRITE, MAP_SHARED|flags, fd_data.fd, 0);
        if (base == MAP_FAILED) {
            LOGE("mmap(fd=%d, size=%u) failed (%s)",
                    fd, uint32_t(size), strerror(errno));
            handle_data.handle = data.handle;
            ioctl(fd, ION_IOC_FREE, &handle_data);
            close(fd);
            return -errno;
        }
    }
    mIonHandle = data.handle;
    mIonDeviceFd = fd;

    /*
     * Call this with NULL now and set device with set_device
     * above for consistency sake with how MemoryHeapPmem works.
     */
    MemoryHeapBase::init(fd_data.fd, base, size, uflags, NULL);

    return NO_ERROR;
}

MemoryHeapIon::~MemoryHeapIon()
{
    struct ion_handle_data data;

    data.handle = mIonHandle;

    /*
     * Due to the way MemoryHeapBase is set up, munmap will never
     * be called so we need to call it ourselves here.
     */
    munmap(MemoryHeapBase::getBase(), MemoryHeapBase::getSize());
    if (mIonDeviceFd > 0) {
        ioctl(mIonDeviceFd, ION_IOC_FREE, &data);
        close(mIonDeviceFd);
    }
}

// ---------------------------------------------------------------------------
}; // namespace android
