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

#ifndef HARDWARE_API_H_

#define HARDWARE_API_H_

#include <media/stagefright/OMXPluginBase.h>
#include <media/stagefright/VideoRenderer.h>
#include <surfaceflinger/ISurface.h>
#include <utils/RefBase.h>

#include <OMX_Component.h>

extern android::VideoRenderer *createRenderer(
        const android::sp<android::ISurface> &surface,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight);

extern android::VideoRenderer *createRendererWithRotation(
        const android::sp<android::ISurface> &surface,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight,
        int32_t rotationDegrees);

#ifdef OMAP_ENHANCEMENT
extern android::VideoRenderer *createRenderer(
        const android::sp<android::ISurface> &surface,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight,
        int isS3D, int numOfOpBuffers);

extern android::VideoRenderer *createRendererWithRotation(
        const android::sp<android::ISurface> &surface,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight,
        int32_t rotationDegrees,
        int isS3D, int numOfOpBuffers);
#endif
extern android::OMXPluginBase *createOMXPlugin();

#endif  // HARDWARE_API_H_

