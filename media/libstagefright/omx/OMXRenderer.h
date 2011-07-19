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

#ifndef OMX_RENDERER_H_

#define OMX_RENDERER_H_

#include <media/IOMX.h>

namespace android {

class VideoRenderer;

class OMXRenderer : public BnOMXRenderer {
public:
    // Assumes ownership of "impl".
    OMXRenderer(VideoRenderer *impl);
    virtual ~OMXRenderer();

    virtual void render(IOMX::buffer_id buffer);
#ifdef OMAP_ENHANCEMENT
    virtual Vector< sp<IMemory> > getBuffers();
    virtual bool setCallback(release_rendered_buffer_callback cb, void *cookie);

    virtual void set_s3d_frame_layout(uint32_t s3d_mode, uint32_t s3d_fmt, uint32_t s3d_order, uint32_t s3d_subsampling);
    virtual void resizeRenderer(void* resize_params);
    virtual void requestRendererClone(bool enable);
#endif

private:
    VideoRenderer *mImpl;

    OMXRenderer(const OMXRenderer &);
    OMXRenderer &operator=(const OMXRenderer &);
};

}  // namespace android

#endif  // OMX_RENDERER_H_
