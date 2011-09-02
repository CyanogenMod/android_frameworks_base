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

#include <media/stagefright/MediaDefs.h>

namespace android {

const char *MEDIA_MIMETYPE_IMAGE_JPEG = "image/jpeg";

const char *MEDIA_MIMETYPE_VIDEO_VPX = "video/x-vnd.on2.vp8";
const char *MEDIA_MIMETYPE_VIDEO_AVC = "video/avc";
const char *MEDIA_MIMETYPE_VIDEO_MPEG4 = "video/mp4v-es";
const char *MEDIA_MIMETYPE_VIDEO_H263 = "video/3gpp";
const char *MEDIA_MIMETYPE_VIDEO_RAW = "video/raw";
#if defined(OMAP_ENHANCEMENT)
#if  defined(TARGET_OMAP4)
const char *MEDIA_MIMETYPE_VIDEO_VP6 = "video/x-vp6";
const char *MEDIA_MIMETYPE_VIDEO_VP7 = "video/x-vp7";
#endif
const char *MEDIA_MIMETYPE_CONTAINER_ASF = "video/asf";
const char *MEDIA_MIMETYPE_VIDEO_WMV  = "video/wmv9";
const char *MEDIA_MIMETYPE_AUDIO_WMA = "audio/wma";
const char *MEDIA_MIMETYPE_AUDIO_WMAPRO = "audio/wmapro";
const char *MEDIA_MIMETYPE_AUDIO_WMALSL = "audio/wmalsl";
#endif

const char *MEDIA_MIMETYPE_AUDIO_AMR_NB = "audio/3gpp";
const char *MEDIA_MIMETYPE_AUDIO_AMR_WB = "audio/amr-wb";
const char *MEDIA_MIMETYPE_AUDIO_MPEG = "audio/mpeg";
const char *MEDIA_MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
const char *MEDIA_MIMETYPE_AUDIO_QCELP = "audio/qcelp";
const char *MEDIA_MIMETYPE_AUDIO_VORBIS = "audio/vorbis";
const char *MEDIA_MIMETYPE_AUDIO_G711_ALAW = "audio/g711-alaw";
const char *MEDIA_MIMETYPE_AUDIO_G711_MLAW = "audio/g711-mlaw";
const char *MEDIA_MIMETYPE_AUDIO_RAW = "audio/raw";

const char *MEDIA_MIMETYPE_CONTAINER_MPEG4 = "video/mpeg4";
const char *MEDIA_MIMETYPE_CONTAINER_WAV = "audio/wav";
const char *MEDIA_MIMETYPE_CONTAINER_OGG = "application/ogg";

const char *MEDIA_MIMETYPE_CONTAINER_MATROSKA = "video/x-matroska";
const char *MEDIA_MIMETYPE_CONTAINER_MPEG2TS = "video/mp2ts";

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
const char *MEDIA_MIMETYPE_CONTAINER_AVI = "video/x-msvideo";
#endif
}  // namespace android
