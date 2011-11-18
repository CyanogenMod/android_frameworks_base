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
#define LOG_TAG "LPAPlayerPMEM"
#include <utils/Log.h>

#include <media/stagefright/LPAPlayer.h>

#define MEM_BUFFER_SIZE 524288
#define MEM_BUFFER_COUNT 4

namespace android {
void LPAPlayer::audio_register_memory() {
    void *pmem_buf; int32_t pmem_fd;
    struct msm_audio_pmem_info  pmem_info;
    for (int i = 0; i < MEM_BUFFER_COUNT; i++) {
        pmem_buf = memBufferAlloc(MEM_BUFFER_SIZE, &pmem_fd);
        memset(&pmem_info, 0, sizeof(msm_audio_pmem_info));
        LOGV("Registering PMEM with fd %d and address as %x", pmem_fd, pmem_buf);
        pmem_info.fd = pmem_fd;
        pmem_info.vaddr = pmem_buf;
        if ( ioctl(afd, AUDIO_REGISTER_PMEM, &pmem_info) < 0 ) {
            LOGE("Registration of PMEM with the Driver failed with fd %d and memory %x",
                 pmem_info.fd, (unsigned int)pmem_info.vaddr);
        }
    }

}

void *LPAPlayer::memBufferAlloc(int32_t nSize, int32_t *pmem_fd){
    int32_t pmemfd = -1;
    void  *pmem_buf = NULL;
    void  *local_buf = NULL;

    // 1. Open the pmem_audio
    pmemfd = open("/dev/pmem_audio", O_RDWR);
    if (pmemfd < 0) {
        LOGE("memBufferAlloc failed to open pmem_audio");
        *pmem_fd = -1;
        return pmem_buf;
    }

    // 2. MMAP to get the virtual address
    pmem_buf = mmap(0, nSize, PROT_READ | PROT_WRITE, MAP_SHARED, pmemfd, 0);
    if ( NULL == pmem_buf) {
        LOGE("memBufferAlloc failed to mmap");
        *pmem_fd = -1;
        return NULL;
    }

    local_buf = malloc(nSize);
    if (NULL == local_buf) {
        // unmap the corresponding PMEM buffer and close the fd
        munmap(pmem_buf, MEM_BUFFER_SIZE);
        close(pmemfd);
        return NULL;
    }

    // 3. Store this information for internal mapping / maintanence
    BuffersAllocated buf(local_buf, pmem_buf, nSize, pmemfd);
    memBuffersRequestQueue.push_back(buf);

    // 4. Send the pmem fd information
    *pmem_fd = pmemfd;
    LOGV("memBufferAlloc calling with required size %d", nSize);
    LOGV("The PMEM that is allocated is %d and buffer is %x", pmemfd, (unsigned int)pmem_buf);

    // 5. Return the virtual address
    return pmem_buf;
}

void LPAPlayer::memBufferDeAlloc()
{
    //Remove all the buffers from request queue
    while (!memBuffersRequestQueue.empty())  {
        List<BuffersAllocated>::iterator it = memBuffersRequestQueue.begin();
        BuffersAllocated &pmemBuffer = *it;
        struct msm_audio_pmem_info pmem_info;
        pmem_info.vaddr = (*it).memBuf;
        pmem_info.fd = (*it).memFd;
        if (ioctl(afd, AUDIO_DEREGISTER_PMEM, &pmem_info) < 0) {
            LOGE("PMEM deregister failed");
        }
        LOGV("Unmapping the address %u, size %d, fd %d from Request",pmemBuffer.memBuf,pmemBuffer.bytesToWrite,pmemBuffer.memFd);
        munmap(pmemBuffer.memBuf, MEM_BUFFER_SIZE);
        LOGV("closing the pmem fd");
        close(pmemBuffer.memFd);
        // free the local buffer corresponding to pmem buffer
        free(pmemBuffer.localBuf);
        LOGV("Removing from request Q");
        memBuffersRequestQueue.erase(it);
    }

    //Remove all the buffers from response queue
    while(!memBuffersResponseQueue.empty()){
        List<BuffersAllocated>::iterator it = memBuffersResponseQueue.begin();
        BuffersAllocated &pmemBuffer = *it;
        struct msm_audio_pmem_info pmem_info;
        pmem_info.vaddr = (*it).memBuf;
        pmem_info.fd = (*it).memFd;
        if (ioctl(afd, AUDIO_DEREGISTER_PMEM, &pmem_info) < 0) {
            LOGE("PMEM deregister failed");
        }
        LOGV("Unmapping the address %u, size %d, fd %d from Response",pmemBuffer.memBuf,MEM_BUFFER_SIZE,pmemBuffer.memFd);
        munmap(pmemBuffer.memBuf, MEM_BUFFER_SIZE);
        LOGV("closing the pmem fd");
        close(pmemBuffer.memFd);
        // free the local buffer corresponding to pmem buffer
        free(pmemBuffer.localBuf);
        LOGV("Removing from response Q");
        memBuffersResponseQueue.erase(it);
    }
}
} //namespace android
