/*
 * Copyright (C) 2009 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "OMXNodeInstance"
#include <utils/Log.h>

#include "../include/OMXNodeInstance.h"
#include "OMXMaster.h"

#include <OMX_Component.h>
#ifdef USE_GETBUFFERINFO
#include <OMX_QCOMExtns.h>
#endif

#include <binder/IMemory.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryHeapPmem.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaErrors.h>
namespace android {

struct BufferMeta {
    BufferMeta(const sp<IMemory> &mem, bool is_backup = false)
        : mMem(mem),
          mIsBackup(is_backup) {
    }

    BufferMeta(size_t size)
        : mSize(size),
          mIsBackup(false) {
    }

    void CopyFromOMX(const OMX_BUFFERHEADERTYPE *header) {
        if (!mIsBackup) {
            return;
        }

        memcpy((OMX_U8 *)mMem->pointer() + header->nOffset,
               header->pBuffer + header->nOffset,
               header->nFilledLen);
    }

    void CopyToOMX(const OMX_BUFFERHEADERTYPE *header) {
        if (!mIsBackup) {
            return;
        }

        memcpy(header->pBuffer + header->nOffset,
               (const OMX_U8 *)mMem->pointer() + header->nOffset,
               header->nFilledLen);
    }

private:
    sp<IMemory> mMem;
    size_t mSize;
    bool mIsBackup;
    char *mBase;

    BufferMeta(const BufferMeta &);
    BufferMeta &operator=(const BufferMeta &);
};

// static
OMX_CALLBACKTYPE OMXNodeInstance::kCallbacks = {
    &OnEvent, &OnEmptyBufferDone, &OnFillBufferDone
};

OMXNodeInstance::OMXNodeInstance(
        OMX *owner, const sp<IOMXObserver> &observer)
    : mOwner(owner),
      mNodeID(NULL),
      mHandle(NULL),
      mObserver(observer),
      mDying(false),
      pmem_registered_with_client(false){
}

OMXNodeInstance::~OMXNodeInstance() {
    CHECK_EQ(mHandle, NULL);
}

void OMXNodeInstance::setHandle(OMX::node_id node_id, OMX_HANDLETYPE handle) {
    CHECK_EQ(mHandle, NULL);
    mNodeID = node_id;
    mHandle = handle;
}

OMX *OMXNodeInstance::owner() {
    return mOwner;
}

sp<IOMXObserver> OMXNodeInstance::observer() {
    return mObserver;
}

OMX::node_id OMXNodeInstance::nodeID() {
    return mNodeID;
}

static status_t StatusFromOMXError(OMX_ERRORTYPE err) {
    switch (err) {
        case OMX_ErrorNone:
            return OK;
        case OMX_ErrorUnsupportedSetting:
            return ERROR_UNSUPPORTED;
        default:
            return UNKNOWN_ERROR;
    }
}

status_t OMXNodeInstance::freeNode(OMXMaster *master) {
    // Transition the node from its current state all the way down
    // to "Loaded".
    // This ensures that all active buffers are properly freed even
    // for components that don't do this themselves on a call to
    // "FreeHandle".

    // The code below may trigger some more events to be dispatched
    // by the OMX component - we want to ignore them as our client
    // does not expect them.
    mDying = true;

    OMX_STATETYPE state;
    CHECK_EQ(OMX_GetState(mHandle, &state), OMX_ErrorNone);
    switch (state) {
        case OMX_StateExecuting:
        {
            LOGV("forcing Executing->Idle");
            sendCommand(OMX_CommandStateSet, OMX_StateIdle);
            OMX_ERRORTYPE err;
            while ((err = OMX_GetState(mHandle, &state)) == OMX_ErrorNone
                   && state != OMX_StateIdle
                   && state != OMX_StateInvalid) {
                usleep(100000);
            }
            CHECK_EQ(err, OMX_ErrorNone);

            if (state == OMX_StateInvalid) {
                break;
            }

            // fall through
        }

        case OMX_StateIdle:
        {
            LOGV("forcing Idle->Loaded");
            sendCommand(OMX_CommandStateSet, OMX_StateLoaded);

            freeActiveBuffers();

            OMX_ERRORTYPE err;
            while ((err = OMX_GetState(mHandle, &state)) == OMX_ErrorNone
                   && state != OMX_StateLoaded
                   && state != OMX_StateInvalid) {
                LOGV("waiting for Loaded state...");
                usleep(100000);
            }
            CHECK_EQ(err, OMX_ErrorNone);

            // fall through
        }

        case OMX_StateLoaded:
        case OMX_StateInvalid:
            break;

        default:
            CHECK(!"should not be here, unknown state.");
            break;
    }

#ifdef TARGET_7X30
    if(true == pmem_registered_with_client) {
      mObserver->registerBuffers(NULL);
      pmem_registered_with_client = false;
    }
#endif

    OMX_ERRORTYPE err = master->destroyComponentInstance(
            static_cast<OMX_COMPONENTTYPE *>(mHandle));

    mHandle = NULL;

    if (err != OMX_ErrorNone) {
        LOGE("FreeHandle FAILED with error 0x%08x.", err);
    }

    mOwner->invalidateNodeID(mNodeID);
    mNodeID = NULL;

    LOGV("OMXNodeInstance going away.");
    delete this;

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::sendCommand(
        OMX_COMMANDTYPE cmd, OMX_S32 param) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_SendCommand(mHandle, cmd, param, NULL);
    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::getParameter(
        OMX_INDEXTYPE index, void *params, size_t size) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_GetParameter(mHandle, index, params);
    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::setParameter(
        OMX_INDEXTYPE index, const void *params, size_t size) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_SetParameter(
            mHandle, index, const_cast<void *>(params));

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::getConfig(
        OMX_INDEXTYPE index, void *params, size_t size) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_GetConfig(mHandle, index, params);
    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::setConfig(
        OMX_INDEXTYPE index, const void *params, size_t size) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_SetConfig(
            mHandle, index, const_cast<void *>(params));

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::useBuffer(
        OMX_U32 portIndex, const sp<IMemory> &params,
        OMX::buffer_id *buffer) {
    Mutex::Autolock autoLock(mLock);

    BufferMeta *buffer_meta = new BufferMeta(params);

    OMX_BUFFERHEADERTYPE *header;

#ifdef USE_GETBUFFERINFO
    OMX_QCOM_PLATFORM_PRIVATE_PMEM_INFO pmem_info;
    ssize_t offset;
    size_t size;
    sp<IMemoryHeap> heap = params->getMemory(&offset, &size);
    pmem_info.pmem_fd = heap->getHeapID();
    pmem_info.offset = offset;

    OMX_ERRORTYPE err = OMX_UseBuffer(
            mHandle, &header, portIndex, &pmem_info,
            params->size(), static_cast<OMX_U8 *>(params->pointer()));
#else
    OMX_ERRORTYPE err = OMX_UseBuffer(
            mHandle, &header, portIndex, buffer_meta,
            params->size(), static_cast<OMX_U8 *>(params->pointer()));
#endif

    if (err != OMX_ErrorNone) {
        LOGE("OMX_UseBuffer failed with error %d (0x%08x)", err, err);

        delete buffer_meta;
        buffer_meta = NULL;

        *buffer = 0;

        return UNKNOWN_ERROR;
    }

#ifdef USE_GETBUFFERINFO
    header->pAppPrivate = buffer_meta;
#endif

    CHECK_EQ(header->pAppPrivate, buffer_meta);

    *buffer = header;

    addActiveBuffer(portIndex, *buffer);

    return OK;
}

status_t OMXNodeInstance::allocateBuffer(
        OMX_U32 portIndex, size_t size, OMX::buffer_id *buffer,
        void **buffer_data) {
    Mutex::Autolock autoLock(mLock);

    BufferMeta *buffer_meta = new BufferMeta(size);

    OMX_BUFFERHEADERTYPE *header;

    OMX_ERRORTYPE err = OMX_AllocateBuffer(
            mHandle, &header, portIndex, buffer_meta, size);

    if (err != OMX_ErrorNone) {
        LOGE("OMX_AllocateBuffer failed with error %d (0x%08x)", err, err);

        delete buffer_meta;
        buffer_meta = NULL;

        *buffer = 0;

        return UNKNOWN_ERROR;
    }

    CHECK_EQ(header->pAppPrivate, buffer_meta);

    *buffer = header;
    *buffer_data = header->pBuffer;

    addActiveBuffer(portIndex, *buffer);

    return OK;
}

status_t OMXNodeInstance::allocateBufferWithBackup(
        OMX_U32 portIndex, const sp<IMemory> &params,
        OMX::buffer_id *buffer) {
    Mutex::Autolock autoLock(mLock);

    BufferMeta *buffer_meta = new BufferMeta(params, true);

    OMX_BUFFERHEADERTYPE *header;

    OMX_ERRORTYPE err = OMX_AllocateBuffer(
            mHandle, &header, portIndex, buffer_meta, params->size());

    if (err != OMX_ErrorNone) {
        LOGE("OMX_AllocateBuffer failed with error %d (0x%08x)", err, err);

        delete buffer_meta;
        buffer_meta = NULL;

        *buffer = 0;

        return UNKNOWN_ERROR;
    }

    CHECK_EQ(header->pAppPrivate, buffer_meta);

    *buffer = header;

    addActiveBuffer(portIndex, *buffer);

    return OK;
}

status_t OMXNodeInstance::freeBuffer(
        OMX_U32 portIndex, OMX::buffer_id buffer) {
    Mutex::Autolock autoLock(mLock);

    removeActiveBuffer(portIndex, buffer);

    OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *)buffer;
    BufferMeta *buffer_meta = static_cast<BufferMeta *>(header->pAppPrivate);

    OMX_ERRORTYPE err = OMX_FreeBuffer(mHandle, portIndex, header);

    delete buffer_meta;
    buffer_meta = NULL;

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::fillBuffer(OMX::buffer_id buffer) {
    Mutex::Autolock autoLock(mLock);

    OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *)buffer;
    header->nFilledLen = 0;
    header->nOffset = 0;
    header->nFlags = 0;

    OMX_ERRORTYPE err = OMX_FillThisBuffer(mHandle, header);

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::emptyBuffer(
        OMX::buffer_id buffer,
        OMX_U32 rangeOffset, OMX_U32 rangeLength,
        OMX_U32 flags, OMX_TICKS timestamp) {
    Mutex::Autolock autoLock(mLock);

    OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *)buffer;
    header->nFilledLen = rangeLength;
    header->nOffset = rangeOffset;
    header->nFlags = flags;
    header->nTimeStamp = timestamp;

    BufferMeta *buffer_meta =
        static_cast<BufferMeta *>(header->pAppPrivate);
    buffer_meta->CopyToOMX(header);

    OMX_ERRORTYPE err = OMX_EmptyThisBuffer(mHandle, header);

    return StatusFromOMXError(err);
}

status_t OMXNodeInstance::getExtensionIndex(
        const char *parameterName, OMX_INDEXTYPE *index) {
    Mutex::Autolock autoLock(mLock);

    OMX_ERRORTYPE err = OMX_GetExtensionIndex(
            mHandle, const_cast<char *>(parameterName), index);

    return StatusFromOMXError(err);
}

void OMXNodeInstance::onMessage(const omx_message &msg) {
    if (msg.type == omx_message::FILL_BUFFER_DONE) {
        OMX_BUFFERHEADERTYPE *buffer =
            static_cast<OMX_BUFFERHEADERTYPE *>(
                    msg.u.extended_buffer_data.buffer);

        BufferMeta *buffer_meta =
            static_cast<BufferMeta *>(buffer->pAppPrivate);

#ifdef TARGET_7X30
        PLATFORM_PRIVATE_LIST *pPlatfromList = (PLATFORM_PRIVATE_LIST *)buffer->pPlatformPrivate;
        PLATFORM_PRIVATE_ENTRY *pPlatformEntry;
        PLATFORM_PRIVATE_PMEM_INFO *pPMEMInfo;
        if(pPlatfromList && (false == pmem_registered_with_client)) {
        sp<IMemoryHeap> mMem;
        sp<MemoryHeapBase> master = NULL;
            for(size_t i=0; i<pPlatfromList->nEntries; i++) {
               if(pPlatfromList->entryList->type == PLATFORM_PRIVATE_PMEM)
               {
                  pPlatformEntry = (PLATFORM_PRIVATE_ENTRY *)pPlatfromList->entryList;
                  pPMEMInfo = (PLATFORM_PRIVATE_PMEM_INFO *)pPlatformEntry->entry;
                  if(pPMEMInfo) {
                     master = (MemoryHeapBase *)pPMEMInfo->pmem_fd;
                  }
                  break;
               }
            }
            if(master != NULL) {
                master->setDevice("/dev/pmem_adsp");
                uint32_t heap_flags = master->getFlags() & MemoryHeapBase::NO_CACHING;
                sp<MemoryHeapPmem> heap = new MemoryHeapPmem(master, heap_flags);
                heap->slap();
                mMem = interface_cast<IMemoryHeap>(heap);
                mBase = (OMX_U8 *)mMem->getBase();
                mObserver->registerBuffers(mMem);
                pmem_registered_with_client = true;
            }
        }
#endif
        buffer_meta->CopyFromOMX(buffer);
    }

    mObserver->onMessage(msg);
}

void OMXNodeInstance::onObserverDied(OMXMaster *master) {
    LOGE("!!! Observer died. Quickly, do something, ... anything...");

    // Try to force shutdown of the node and hope for the best.
    freeNode(master);
}

void OMXNodeInstance::onGetHandleFailed() {
    delete this;
}

// static
OMX_ERRORTYPE OMXNodeInstance::OnEvent(
        OMX_IN OMX_HANDLETYPE hComponent,
        OMX_IN OMX_PTR pAppData,
        OMX_IN OMX_EVENTTYPE eEvent,
        OMX_IN OMX_U32 nData1,
        OMX_IN OMX_U32 nData2,
        OMX_IN OMX_PTR pEventData) {
    OMXNodeInstance *instance = static_cast<OMXNodeInstance *>(pAppData);
    if (instance->mDying) {
        return OMX_ErrorNone;
    }
#ifdef TARGET_7X30
    if(eEvent == OMX_EventCmdComplete && nData1 == OMX_CommandPortDisable && nData2 == 1) {
      /*This is needed to clear the reference on pmem fd.
       * If this is skipped then we will see pmem leak.
       */
      if(true == instance->pmem_registered_with_client) {
        instance->mObserver->registerBuffers(NULL);
        instance->pmem_registered_with_client = false;
      }
    }
#endif
    return instance->owner()->OnEvent(
            instance->nodeID(), eEvent, nData1, nData2, pEventData);
}

// static
OMX_ERRORTYPE OMXNodeInstance::OnEmptyBufferDone(
        OMX_IN OMX_HANDLETYPE hComponent,
        OMX_IN OMX_PTR pAppData,
        OMX_IN OMX_BUFFERHEADERTYPE* pBuffer) {
    OMXNodeInstance *instance = static_cast<OMXNodeInstance *>(pAppData);
    if (instance->mDying) {
        return OMX_ErrorNone;
    }
    return instance->owner()->OnEmptyBufferDone(instance->nodeID(), pBuffer);
}

// static
OMX_ERRORTYPE OMXNodeInstance::OnFillBufferDone(
        OMX_IN OMX_HANDLETYPE hComponent,
        OMX_IN OMX_PTR pAppData,
        OMX_IN OMX_BUFFERHEADERTYPE* pBuffer) {
    OMXNodeInstance *instance = static_cast<OMXNodeInstance *>(pAppData);
    if (instance->mDying) {
        return OMX_ErrorNone;
    }
    return instance->owner()->OnFillBufferDone(instance->nodeID(), pBuffer);
}

void OMXNodeInstance::addActiveBuffer(OMX_U32 portIndex, OMX::buffer_id id) {
    ActiveBuffer active;
    active.mPortIndex = portIndex;
    active.mID = id;
    mActiveBuffers.push(active);
}

void OMXNodeInstance::removeActiveBuffer(
        OMX_U32 portIndex, OMX::buffer_id id) {
    bool found = false;
    for (size_t i = 0; i < mActiveBuffers.size(); ++i) {
        if (mActiveBuffers[i].mPortIndex == portIndex
            && mActiveBuffers[i].mID == id) {
            found = true;
            mActiveBuffers.removeItemsAt(i);
            break;
        }
    }

    if (!found) {
        LOGW("Attempt to remove an active buffer we know nothing about...");
    }
}

void OMXNodeInstance::freeActiveBuffers() {
    // Make sure to count down here, as freeBuffer will in turn remove
    // the active buffer from the vector...
    for (size_t i = mActiveBuffers.size(); i--;) {
        freeBuffer(mActiveBuffers[i].mPortIndex, mActiveBuffers[i].mID);
    }
}

}  // namespace android

