/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
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

#define LOG_NDEBUG 0
#define LOG_TAG "LPAPlayerION"
#include <utils/Log.h>

#include <media/stagefright/LPAPlayer.h>

#define MEM_BUFFER_SIZE 524288
#define MEM_BUFFER_COUNT 4

namespace android {
void LPAPlayer::audio_register_memory() {
    void *ion_buf; int32_t ion_fd;
    struct msm_audio_ion_info ion_info;
    //1. Open the ion_audio
    ionfd = open("/dev/ion", O_RDONLY | O_SYNC);
    if (ionfd < 0) {
        LOGE("/dev/ion open failed \n");
        return;
    }
    for (int i = 0; i < MEM_BUFFER_COUNT; i++) {
        ion_buf = memBufferAlloc(MEM_BUFFER_SIZE, &ion_fd);
        memset(&ion_info, 0, sizeof(msm_audio_ion_info));
        LOGV("Registering ION with fd %d and address as %x", ion_fd, ion_buf);
        ion_info.fd = ion_fd;
        ion_info.vaddr = ion_buf;
        if ( ioctl(afd, AUDIO_REGISTER_ION, &ion_info) < 0 ) {
            LOGE("Registration of ION with the Driver failed with fd %d and memory %x",
                 ion_info.fd, (unsigned int)ion_info.vaddr);
        }
    }
}

void *LPAPlayer::memBufferAlloc(int32_t nSize, int32_t *ion_fd){
    void  *ion_buf = NULL;
    void  *local_buf = NULL;
    struct ion_fd_data fd_data;
    struct ion_allocation_data alloc_data;

    alloc_data.len =   nSize;
    alloc_data.align = 0x1000;
    alloc_data.flags = ION_HEAP(ION_AUDIO_HEAP_ID);
    int rc = ioctl(ionfd, ION_IOC_ALLOC, &alloc_data);
    if (rc) {
        LOGE("ION_IOC_ALLOC ioctl failed\n");
        return ion_buf;
    }
    fd_data.handle = alloc_data.handle;

    rc = ioctl(ionfd, ION_IOC_SHARE, &fd_data);
    if (rc) {
        LOGE("ION_IOC_SHARE ioctl failed\n");
        rc = ioctl(ionfd, ION_IOC_FREE, &(alloc_data.handle));
        if (rc) {
            LOGE("ION_IOC_FREE ioctl failed\n");
        }
        return ion_buf;
    }

    // 2. MMAP to get the virtual address
    ion_buf = mmap(NULL, nSize, PROT_READ | PROT_WRITE, MAP_SHARED, fd_data.fd, 0);
    if(MAP_FAILED == ion_buf) {
        LOGE("mmap() failed \n");
        close(fd_data.fd);
        rc = ioctl(ionfd, ION_IOC_FREE, &(alloc_data.handle));
        if (rc) {
            LOGE("ION_IOC_FREE ioctl failed\n");
        }
        return ion_buf;
    }

    local_buf = malloc(nSize);
    if (NULL == local_buf) {
        // unmap the corresponding ION buffer and close the fd
        munmap(ion_buf, MEM_BUFFER_SIZE);
        close(fd_data.fd);
        rc = ioctl(ionfd, ION_IOC_FREE, &(alloc_data.handle));
        if (rc) {
            LOGE("ION_IOC_FREE ioctl failed\n");
        }
        return NULL;
    }

    // 3. Store this information for internal mapping / maintanence
    BuffersAllocated buf(local_buf, ion_buf, nSize, fd_data.fd, alloc_data.handle);
    memBuffersRequestQueue.push_back(buf);

    // 4. Send the mem fd information
    *ion_fd = fd_data.fd;
    LOGV("IONBufferAlloc calling with required size %d", nSize);
    LOGV("ION allocated is %d, fd_data.fd %d and buffer is %x", *ion_fd, fd_data.fd, (unsigned int)ion_buf);

    // 5. Return the virtual address
    return ion_buf;
}

void LPAPlayer::memBufferDeAlloc()
{
    int rc = 0;
    //Remove all the buffers from request queue
    while (!memBuffersRequestQueue.empty())  {
        List<BuffersAllocated>::iterator it = memBuffersRequestQueue.begin();
        BuffersAllocated &ionBuffer = *it;
        struct msm_audio_ion_info ion_info;
        ion_info.vaddr = (*it).memBuf;
        ion_info.fd = (*it).memFd;
        if (ioctl(afd, AUDIO_DEREGISTER_ION, &ion_info) < 0) {
            LOGE("ION deregister failed");
        }
        LOGV("Ion Unmapping the address %u, size %d, fd %d from Request",ionBuffer.memBuf,ionBuffer.bytesToWrite,ionBuffer.memFd);
        munmap(ionBuffer.memBuf,MEM_BUFFER_SIZE);
        LOGV("closing the ion shared fd");
        close(ionBuffer.memFd);
        rc = ioctl(ionfd, ION_IOC_FREE, &ionBuffer.ion_handle);
        if (rc) {
            LOGE("ION_IOC_FREE ioctl failed\n");
        }
        // free the local buffer corresponding to ion buffer
        free(ionBuffer.localBuf);
        LOGE("Removing from request Q");
        memBuffersRequestQueue.erase(it);
    }

    //Remove all the buffers from response queue
    while(!memBuffersResponseQueue.empty()){
        List<BuffersAllocated>::iterator it = memBuffersResponseQueue.begin();
        BuffersAllocated &ionBuffer = *it;
        struct msm_audio_ion_info ion_info;
        ion_info.vaddr = (*it).memBuf;
        ion_info.fd = (*it).memFd;
        if (ioctl(afd, AUDIO_DEREGISTER_ION, &ion_info) < 0) {
            LOGE("ION deregister failed");
        }
        LOGV("Ion Unmapping the address %u, size %d, fd %d from Request",ionBuffer.memBuf,ionBuffer.bytesToWrite,ionBuffer.memFd);
        munmap(ionBuffer.memBuf, MEM_BUFFER_SIZE);
        LOGV("closing the ion shared fd");
        close(ionBuffer.memFd);
        rc = ioctl(ionfd, ION_IOC_FREE, &ionBuffer.ion_handle);
        if (rc) {
            LOGE("ION_IOC_FREE ioctl failed\n");
        }
        // free the local buffer corresponding to ion buffer
        free(ionBuffer.localBuf);
        LOGV("Removing from response Q");
        memBuffersResponseQueue.erase(it);
    }
    if (ionfd >= 0) {
        close(ionfd);
        ionfd = -1;
    }
}
}// namespace android
