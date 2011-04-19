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

#ifndef ANDROID_IOMX_H_

#define ANDROID_IOMX_H_

#include <binder/IInterface.h>
#include <binder/MemoryHeapBase.h>
#include <utils/List.h>
#include <utils/String8.h>

#include <OMX_Core.h>
#include <OMX_Video.h>

#include "jni.h"

namespace android {

class IMemory;
class IOMXObserver;
class IOMXRenderer;
class ISurface;
class Surface;

class IOMX : public IInterface {
public:
    DECLARE_META_INTERFACE(OMX);

    typedef void *buffer_id;
    typedef void *node_id;

    // Given the calling process' pid, returns true iff
    // the implementation of the OMX interface lives in the same
    // process.
    virtual bool livesLocally(pid_t pid) = 0;

    struct ComponentInfo {
        String8 mName;
        List<String8> mRoles;
    };
    virtual status_t listNodes(List<ComponentInfo> *list) = 0;

    virtual status_t allocateNode(
            const char *name, const sp<IOMXObserver> &observer,
            node_id *node) = 0;

    virtual status_t freeNode(node_id node) = 0;

    virtual status_t sendCommand(
            node_id node, OMX_COMMANDTYPE cmd, OMX_S32 param) = 0;

    virtual status_t getParameter(
            node_id node, OMX_INDEXTYPE index,
            void *params, size_t size) = 0;

    virtual status_t setParameter(
            node_id node, OMX_INDEXTYPE index,
            const void *params, size_t size) = 0;

    virtual status_t getConfig(
            node_id node, OMX_INDEXTYPE index,
            void *params, size_t size) = 0;

    virtual status_t setConfig(
            node_id node, OMX_INDEXTYPE index,
            const void *params, size_t size) = 0;

    virtual status_t useBuffer(
            node_id node, OMX_U32 port_index, const sp<IMemory> &params,
            buffer_id *buffer) = 0;

    // This API clearly only makes sense if the caller lives in the
    // same process as the callee, i.e. is the media_server, as the
    // returned "buffer_data" pointer is just that, a pointer into local
    // address space.
    virtual status_t allocateBuffer(
            node_id node, OMX_U32 port_index, size_t size,
            buffer_id *buffer, void **buffer_data) = 0;

    virtual status_t allocateBufferWithBackup(
            node_id node, OMX_U32 port_index, const sp<IMemory> &params,
            buffer_id *buffer) = 0;

    virtual status_t freeBuffer(
            node_id node, OMX_U32 port_index, buffer_id buffer) = 0;

    virtual status_t fillBuffer(node_id node, buffer_id buffer) = 0;

    virtual status_t emptyBuffer(
            node_id node,
            buffer_id buffer,
            OMX_U32 range_offset, OMX_U32 range_length,
            OMX_U32 flags, OMX_TICKS timestamp) = 0;

    virtual status_t getExtensionIndex(
            node_id node,
            const char *parameter_name,
            OMX_INDEXTYPE *index) = 0;

    virtual sp<IOMXRenderer> createRenderer(
            const sp<ISurface> &surface,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            size_t encodedWidth, size_t encodedHeight,
            size_t displayWidth, size_t displayHeight,
            int32_t rotationDegrees) = 0;

    // Note: These methods are _not_ virtual, it exists as a wrapper around
    // the virtual "createRenderer" method above facilitating extraction
    // of the ISurface from a regular Surface or a java Surface object.
    sp<IOMXRenderer> createRenderer(
            const sp<Surface> &surface,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            size_t encodedWidth, size_t encodedHeight,
            size_t displayWidth, size_t displayHeight,
            int32_t rotationDegrees);

    sp<IOMXRenderer> createRendererFromJavaSurface(
            JNIEnv *env, jobject javaSurface,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            size_t encodedWidth, size_t encodedHeight,
            size_t displayWidth, size_t displayHeight,
            int32_t rotationDegrees);
};

struct omx_message {
    enum {
        EVENT,
        EMPTY_BUFFER_DONE,
        FILL_BUFFER_DONE,
        REGISTER_BUFFERS

    } type;

    IOMX::node_id node;

    union {
        // if type == EVENT
        struct {
            OMX_EVENTTYPE event;
            OMX_U32 data1;
            OMX_U32 data2;
        } event_data;

        // if type == EMPTY_BUFFER_DONE
        struct {
            IOMX::buffer_id buffer;
        } buffer_data;

        // if type == FILL_BUFFER_DONE
        struct {
            IOMX::buffer_id buffer;
            OMX_U32 range_offset;
            OMX_U32 range_length;
            OMX_U32 flags;
            OMX_TICKS timestamp;
            OMX_PTR platform_private;
            OMX_PTR data_ptr;
            OMX_U32 pmem_offset;
        } extended_buffer_data;

    } u;
};

class IOMXObserver : public IInterface {
public:
    DECLARE_META_INTERFACE(OMXObserver);

    virtual void onMessage(const omx_message &msg) = 0;
    virtual void registerBuffers(const sp<IMemoryHeap> &mem) = 0;
};

class IOMXRenderer : public IInterface {
public:
    DECLARE_META_INTERFACE(OMXRenderer);

    virtual void render(IOMX::buffer_id buffer) = 0;
};

////////////////////////////////////////////////////////////////////////////////

class BnOMX : public BnInterface<IOMX> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
};

class BnOMXObserver : public BnInterface<IOMXObserver> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
};

class BnOMXRenderer : public BnInterface<IOMXRenderer> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
};

}  // namespace android

#endif  // ANDROID_IOMX_H_
