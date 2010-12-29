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
#define LOG_TAG "OMXCodec"
#include <utils/Log.h>

#include "include/AACDecoder.h"
#include "include/AACEncoder.h"
#include "include/AMRNBDecoder.h"
#include "include/AMRNBEncoder.h"
#include "include/AMRWBDecoder.h"
#include "include/AMRWBEncoder.h"
#include "include/AVCDecoder.h"
#include "include/AVCEncoder.h"
#include "include/G711Decoder.h"
#include "include/M4vH263Decoder.h"
#include "include/M4vH263Encoder.h"
#include "include/MP3Decoder.h"
#include "include/VorbisDecoder.h"
#include "include/VPXDecoder.h"

#include "include/ESDS.h"

#if defined(OMAP_ENHANCEMENT) && (TARGET_OMAP4)
#define NPA_BUFFERS
#endif

#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <binder/ProcessState.h>
#ifdef USE_GETBUFFERINFO
#include <binder/MemoryBase.h>
#endif
#include <media/IMediaPlayerService.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <media/stagefright/Utils.h>
#include <utils/Vector.h>

#include <OMX_Audio.h>
#include <OMX_Component.h>

#ifdef USE_GETBUFFERINFO
#include <OMX_QCOMExtns.h>
#endif

#include "include/ThreadedSource.h"
#ifdef OMAP_ENHANCEMENT
#include <overlay_common.h>
#include <cutils/properties.h>
#ifdef TARGET_OMAP4
#include <TICameraParameters.h>

#include "OMX_TI_Index.h"
#include "OMX_TI_Common.h"
#include "OMX_TI_Video.h"
#include "OMX_TI_IVCommon.h"
#endif

/**
* Important Note#
* Method to calculate the reference frames required for decoder on output port
* This is as per standard, hence no changes are expected in this function logic in the future
* But for any changes done inside Codec, it is required to update here as well as in
* TIHardwareRenderer.cpp (platform/hardware/ti/omap3/libstagefrighthw)
* And this will be removed once flash backward compatibity issue is resolved.
*/
static int Calculate_TotalRefFrames(int nWidth, int nHeight) {
    LOGD("Calculate_TotalRefFrames");
    uint32_t ref_frames = 0;
    uint32_t MaxDpbMbs;
    uint32_t PicWidthInMbs;
    uint32_t FrameHeightInMbs;

    MaxDpbMbs = 32768; //Maximum value for upto level 4.1

    PicWidthInMbs = nWidth / 16;

    FrameHeightInMbs = nHeight / 16;

    ref_frames =  (uint32_t)(MaxDpbMbs / (PicWidthInMbs * FrameHeightInMbs));

    LOGD("nWidth [%d] PicWidthInMbs [%d] nHeight [%d] FrameHeightInMbs [%d] ref_frames [%d]",
          nWidth, PicWidthInMbs, nHeight, FrameHeightInMbs, ref_frames);

    ref_frames = (ref_frames > 16) ? 16 : ref_frames;

    LOGD("Final ref_frames [%d]",  ref_frames);

    return (ref_frames + 3 + 2*NUM_BUFFERS_TO_BE_QUEUED_FOR_OPTIMAL_PERFORMANCE);
}

#if defined NPA_BUFFERS
#define OMX_BUFFERHEADERFLAG_MODIFIED 0x00000100
#define THUMBNAIL_BUFFERS_NPA_MODE 2
#define NPA_BUFFER_SIZE 4
#endif

#ifdef TARGET_OMAP4
#define SUPPORT_B_FRAMES
#ifdef SUPPORT_B_FRAMES
#define OMX_NUM_B_FRAMES 2
#else
#define OMX_NUM_B_FRAMES 0
#endif
#define OUTPUT_BUFFER_COUNT 2
#define INPUT_BUFFER_COUNT 2
#elif defined(TARGET_OMAP3)
#define OUTPUT_BUFFER_COUNT 4
#endif

#endif

#ifdef OMAP_ENHANCEMENT
#include <overlay_common.h>
#endif

namespace android {

static const int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;

struct CodecInfo {
    const char *mime;
    const char *codec;
};

#define FACTORY_CREATE(name) \
static sp<MediaSource> Make##name(const sp<MediaSource> &source) { \
    return new name(source); \
}

#define FACTORY_CREATE_ENCODER(name) \
static sp<MediaSource> Make##name(const sp<MediaSource> &source, const sp<MetaData> &meta) { \
    return new name(source, meta); \
}

#define FACTORY_REF(name) { #name, Make##name },

FACTORY_CREATE(MP3Decoder)
FACTORY_CREATE(AMRNBDecoder)
FACTORY_CREATE(AMRWBDecoder)
FACTORY_CREATE(AACDecoder)
FACTORY_CREATE(AVCDecoder)
FACTORY_CREATE(G711Decoder)
FACTORY_CREATE(M4vH263Decoder)
FACTORY_CREATE(VorbisDecoder)
FACTORY_CREATE(VPXDecoder)
FACTORY_CREATE_ENCODER(AMRNBEncoder)
FACTORY_CREATE_ENCODER(AMRWBEncoder)
FACTORY_CREATE_ENCODER(AACEncoder)
FACTORY_CREATE_ENCODER(AVCEncoder)
FACTORY_CREATE_ENCODER(M4vH263Encoder)

static sp<MediaSource> InstantiateSoftwareEncoder(
        const char *name, const sp<MediaSource> &source,
        const sp<MetaData> &meta) {
    struct FactoryInfo {
        const char *name;
        sp<MediaSource> (*CreateFunc)(const sp<MediaSource> &, const sp<MetaData> &);
    };

    static const FactoryInfo kFactoryInfo[] = {
        FACTORY_REF(AMRNBEncoder)
        FACTORY_REF(AMRWBEncoder)
        FACTORY_REF(AACEncoder)
        FACTORY_REF(AVCEncoder)
        FACTORY_REF(M4vH263Encoder)
    };
    for (size_t i = 0;
         i < sizeof(kFactoryInfo) / sizeof(kFactoryInfo[0]); ++i) {
        if (!strcmp(name, kFactoryInfo[i].name)) {
            return (*kFactoryInfo[i].CreateFunc)(source, meta);
        }
    }

    return NULL;
}

static sp<MediaSource> InstantiateSoftwareCodec(
        const char *name, const sp<MediaSource> &source) {
    struct FactoryInfo {
        const char *name;
        sp<MediaSource> (*CreateFunc)(const sp<MediaSource> &);
    };

    static const FactoryInfo kFactoryInfo[] = {
        FACTORY_REF(MP3Decoder)
        FACTORY_REF(AMRNBDecoder)
        FACTORY_REF(AMRWBDecoder)
        FACTORY_REF(AACDecoder)
        FACTORY_REF(AVCDecoder)
        FACTORY_REF(G711Decoder)
        FACTORY_REF(M4vH263Decoder)
        FACTORY_REF(VorbisDecoder)
        FACTORY_REF(VPXDecoder)
    };
    for (size_t i = 0;
         i < sizeof(kFactoryInfo) / sizeof(kFactoryInfo[0]); ++i) {
        if (!strcmp(name, kFactoryInfo[i].name)) {
            if (!strcmp(name, "VPXDecoder")) {
                return new ThreadedSource(
                        (*kFactoryInfo[i].CreateFunc)(source));
            }
            return (*kFactoryInfo[i].CreateFunc)(source);
        }
    }

    return NULL;
}

#undef FACTORY_REF
#undef FACTORY_CREATE

#ifdef OMAP_ENHANCEMENT
#ifdef TARGET_OMAP4
//Enable Ducati Codecs for Video, PV SW codecs for Audio
static const CodecInfo kDecoderInfo[] = {
    { MEDIA_MIMETYPE_AUDIO_MPEG, "MP3Decoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "AMRNBDecoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "AMRWBDecoder" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACDecoder" },
    { MEDIA_MIMETYPE_VIDEO_WMV, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.ITTIAM.WMA.decode" },
    { MEDIA_MIMETYPE_AUDIO_WMALSL, "OMX.ITTIAM.WMALSL.decode" },
    { MEDIA_MIMETYPE_AUDIO_WMAPRO, "OMX.ITTIAM.WMAPRO.decode" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "M4vH263Decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_H263, "M4vH263Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "AVCDecoder" },
    { MEDIA_MIMETYPE_AUDIO_VORBIS, "VorbisDecoder" },
    { MEDIA_MIMETYPE_VIDEO_VP6, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_VP7, "OMX.TI.DUCATI1.VIDEO.DECODER" }
};

//Maintain only s/w encoders till ducati encoders are integrated to SF
static const CodecInfo kEncoderInfo[] = {
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "AMRNBEncoder" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACEncoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.DUCATI1.VIDEO.MPEG4E" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.DUCATI1.VIDEO.MPEG4E" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.DUCATI1.VIDEO.H264E" },
};
#elif defined(TARGET_OMAP3)
static const CodecInfo kDecoderInfo[] = {
    { MEDIA_MIMETYPE_IMAGE_JPEG, "OMX.TI.JPEG.decode" },
    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.TI.MP3.decode" },
    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.PV.mp3dec" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.decode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.PV.amrdec" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.decode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.PV.amrdec" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.decode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.ITTIAM.AAC.decode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.PV.aacdec" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACDecoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.720P.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.Video.Decoder" },
    /* 720p Video Decoder must be placed before the TI Video Decoder.
       DO NOT CHANGE THIS SEQUENCE. IT WILL BREAK FLASH. */
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.720P.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_AUDIO_VORBIS, "VorbisDecoder" },
    { MEDIA_MIMETYPE_VIDEO_WMV, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_WMV, "OMX.TI.720P.Decoder" },
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.TI.WMA.decode"},
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.ITTIAM.WMA.decode"},
};

static const CodecInfo kEncoderInfo[] = {
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.encode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.ITTIAM.AAC.encode" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.720P.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.720P.Encoder" },
};
#endif
#else
static const CodecInfo kDecoderInfo[] = {
    { MEDIA_MIMETYPE_IMAGE_JPEG, "OMX.TI.JPEG.decode" },
    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.Nvidia.mp3.decoder" },
//    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.TI.MP3.decode" },
    { MEDIA_MIMETYPE_AUDIO_MPEG, "MP3Decoder" },
//    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.PV.mp3dec" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.decode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "AMRNBDecoder" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.PV.amrdec" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.decode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "AMRWBDecoder" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.PV.amrdec" },
#ifndef USE_SOFTWARE_AUDIO_AAC
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.Nvidia.aac.decoder" },
#endif
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.decode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACDecoder" },
//    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.PV.aacdec" },
    //{ MEDIA_MIMETYPE_AUDIO_WMA, "OMX.Nvidia.wma.decoder" },
    { MEDIA_MIMETYPE_AUDIO_G711_ALAW, "G711Decoder" },
    { MEDIA_MIMETYPE_AUDIO_G711_MLAW, "G711Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.Nvidia.mp4.decode" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.7x30.video.decoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.video.decoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.SEC.MPEG4.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "M4vH263Decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.Nvidia.h263.decode" },
//    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.PV.mpeg4dec" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.7x30.video.decoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.video.decoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.SEC.H263.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "M4vH263Decoder" },
//    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.PV.h263dec" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.Nvidia.h264.decode" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.7x30.video.decoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.video.decoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.SEC.AVC.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "AVCDecoder" },
//    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.PV.avcdec" },
    //{MEDIA_MIMETYPE_VIDEO_WMV, "OMX.Nvidia.vc1.decode" },
    { MEDIA_MIMETYPE_AUDIO_VORBIS, "VorbisDecoder" },
    { MEDIA_MIMETYPE_VIDEO_VPX, "VPXDecoder" },
};

static const CodecInfo kEncoderInfo[] = {
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "AMRNBEncoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "AMRWBEncoder" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.encode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACEncoder" },
//    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.PV.aacenc" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.Nvidia.mp4.encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.7x30.video.encoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.video.encoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.SEC.MPEG4.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "M4vH263Encoder" },
//    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.PV.mpeg4enc" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.Nvidia.h263.encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.7x30.video.encoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.video.encoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.SEC.H263.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "M4vH263Encoder" },
//    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.PV.h263enc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.Nvidia.h264.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.7x30.video.encoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.video.encoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.SEC.AVC.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "AVCEncoder" },
//    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.PV.avcenc" },
};
#endif

#undef OPTIONAL

#define CODEC_LOGI(x, ...) LOGI("[%s] "x, mComponentName, ##__VA_ARGS__)
#define CODEC_LOGV(x, ...) LOGV("[%s] "x, mComponentName, ##__VA_ARGS__)
#define CODEC_LOGE(x, ...) LOGE("[%s] "x, mComponentName, ##__VA_ARGS__)

#ifdef OMAP_ENHANCEMENT
/* This flag has to be updated according to the Memory dealer cache line
 * alignment value "SimpleBestFitAllocator::kMemoryAlign"
 * It is required to allocate correct amount of memory considering the
 * alignment, Otherwise alloc fails when input buffer sizes are not aligned*/
#ifdef TARGET_OMAP4
#define CACHELINE_BOUNDARY_MEMALIGNMENT 32
#else
#define CACHELINE_BOUNDARY_MEMALIGNMENT 128
#endif
#endif

#ifndef OMAP_ENHANCEMENT
struct OMXCodecObserver : public BnOMXObserver {
    OMXCodecObserver() {
    }

    void setCodec(const sp<OMXCodec> &target) {
        mTarget = target;
    }

    // from IOMXObserver
    virtual void onMessage(const omx_message &msg) {
        sp<OMXCodec> codec = mTarget.promote();

        if (codec.get() != NULL) {
            Mutex::Autolock autoLock(codec->mLock);
            codec->on_message(msg);
            codec.clear();
        }
    }

    virtual void registerBuffers(const sp<IMemoryHeap> &mem) {
        sp<OMXCodec> codec = mTarget.promote();
        if (codec.get() != NULL) {
            codec->registerBuffers(mem);
        }
    }

protected:
    virtual ~OMXCodecObserver() {}

private:
    wp<OMXCodec> mTarget;

    OMXCodecObserver(const OMXCodecObserver &);
    OMXCodecObserver &operator=(const OMXCodecObserver &);
};
#endif

static const char *GetCodec(const CodecInfo *info, size_t numInfos,
                            const char *mime, int index) {
    CHECK(index >= 0);
    for(size_t i = 0; i < numInfos; ++i) {
        if (!strcasecmp(mime, info[i].mime)) {
            if (index == 0) {
                return info[i].codec;
            }

            --index;
        }
    }

    return NULL;
}

enum {
    kAVCProfileBaseline      = 0x42,
    kAVCProfileMain          = 0x4d,
    kAVCProfileExtended      = 0x58,
    kAVCProfileHigh          = 0x64,
    kAVCProfileHigh10        = 0x6e,
    kAVCProfileHigh422       = 0x7a,
    kAVCProfileHigh444       = 0xf4,
    kAVCProfileCAVLC444Intra = 0x2c
};

static const char *AVCProfileToString(uint8_t profile) {
    switch (profile) {
        case kAVCProfileBaseline:
            return "Baseline";
        case kAVCProfileMain:
            return "Main";
        case kAVCProfileExtended:
            return "Extended";
        case kAVCProfileHigh:
            return "High";
        case kAVCProfileHigh10:
            return "High 10";
        case kAVCProfileHigh422:
            return "High 422";
        case kAVCProfileHigh444:
            return "High 444";
        case kAVCProfileCAVLC444Intra:
            return "CAVLC 444 Intra";
        default:   return "Unknown";
    }
}

#ifndef OMAP_ENHANCEMENT
template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}
#endif

static bool IsSoftwareCodec(const char *componentName) {
    if (!strncmp("OMX.PV.", componentName, 7)) {
        return true;
    }

    return false;
}

// A sort order in which non-OMX components are first,
// followed by software codecs, i.e. OMX.PV.*, followed
// by all the others.
static int CompareSoftwareCodecsFirst(
        const String8 *elem1, const String8 *elem2) {
    bool isNotOMX1 = strncmp(elem1->string(), "OMX.", 4);
    bool isNotOMX2 = strncmp(elem2->string(), "OMX.", 4);

    if (isNotOMX1) {
        if (isNotOMX2) { return 0; }
        return -1;
    }
    if (isNotOMX2) {
        return 1;
    }

    bool isSoftwareCodec1 = IsSoftwareCodec(elem1->string());
    bool isSoftwareCodec2 = IsSoftwareCodec(elem2->string());

    if (isSoftwareCodec1) {
        if (isSoftwareCodec2) { return 0; }
        return -1;
    }

    if (isSoftwareCodec2) {
        return 1;
    }

    return 0;
}

// static
#ifdef OMAP_ENHANCEMENT
uint32_t OMXCodec::getComponentQuirks(const char *componentName,bool isEncoder, uint32_t flags) {
#else
uint32_t OMXCodec::getComponentQuirks(
        const char *componentName, bool isEncoder) {
#endif
    uint32_t quirks = 0;

    if (!strcmp(componentName, "OMX.PV.avcdec")) {
        quirks |= kWantsNALFragments;
    }

    if (!strcmp(componentName, "OMX.Nvidia.amr.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.amrwb.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.aac.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.mp3.decoder")) {
        quirks |= kDecoderLiesAboutNumberOfChannels;
    }

    if (!strcmp(componentName, "OMX.TI.MP3.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kDecoderLiesAboutNumberOfChannels;
#ifdef OMAP_ENHANCEMENT
        quirks |= kSupportsMultipleFramesPerInputBuffer;
        quirks |= kDecoderCantRenderSmallClips;
#endif
    }
    if (!strcmp(componentName, "OMX.TI.AAC.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kRequiresFlushCompleteEmulation;
        quirks |= kSupportsMultipleFramesPerInputBuffer;
    }
#ifdef OMAP_ENHANCEMENT
    if (!strcmp(componentName, "OMX.TI.WMA.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kRequiresFlushCompleteEmulation;
    }
    if (!strcmp(componentName, "OMX.ITTIAM.WMA.decode")) {
       quirks |= kNeedsFlushBeforeDisable;
       quirks |= kRequiresFlushCompleteEmulation;
    }
    if (!strcmp(componentName, "OMX.ITTIAM.WMALSL.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kRequiresFlushCompleteEmulation;
    }
    if (!strcmp(componentName, "OMX.ITTIAM.WMAPRO.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kRequiresFlushCompleteEmulation;
    }
    if (!strcmp(componentName, "OMX.ITTIAM.AAC.decode")) {

        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kDecoderNeedsPortReconfiguration;
    }
    if (!strcmp(componentName, "OMX.PV.aacdec")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kDecoderNeedsPortReconfiguration;
    }
#endif
    if (!strncmp(componentName, "OMX.qcom.video.encoder.", 23)) {
        quirks |= kRequiresLoadedToIdleAfterAllocation;
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kCanNotSetVideoParameters;
        if (!strncmp(componentName, "OMX.qcom.video.encoder.avc", 26)) {

            // The AVC encoder advertises the size of output buffers
            // based on the input video resolution and assumes
            // the worst/least compression ratio is 0.5. It is found that
            // sometimes, the output buffer size is larger than
            // size advertised by the encoder.
            quirks |= kRequiresLargerEncoderOutputBuffer;
        }
    }
    if (!strncmp(componentName, "OMX.qcom.7x30.video.encoder.", 28)) {
        quirks |= kAvoidMemcopyInputRecordingFrames;
        quirks |= kCanNotSetVideoParameters;
    }
    if (!strncmp(componentName, "OMX.qcom.video.decoder.", 23)) {
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kDefersOutputBufferAllocation;
    }
    if (!strncmp(componentName, "OMX.qcom.7x30.video.decoder.", 28)) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kDefersOutputBufferAllocation;
        quirks |= kDoesNotRequireMemcpyOnOutputPort;
    }
#ifdef OMAP_ENHANCEMENT
    if (!strcmp(componentName, "OMX.TI.Video.Decoder") ||
            !strcmp(componentName, "OMX.TI.720P.Decoder")) {
        // TI Video Decoder and TI 720p Decoder must use buffers allocated
        // by Overlay for output port. So, I cannot call OMX_AllocateBuffer
        // on output port. I must use OMX_UseBuffer on input port to ensure
        // 128 byte alignment.
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kInputBufferSizesAreBogus;

        if( flags & kPreferThumbnailMode) {
                quirks |= OMXCodec::kRequiresAllocateBufferOnOutputPorts;
        }
    }
#ifdef TARGET_OMAP4
    else if(!strcmp(componentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {
        //quirks |= kRequiresAllocateBufferOnInputPorts;

        quirks |= kNeedsFlushBeforeDisable;
        if(flags & kPreferThumbnailMode)
        {
            quirks |= OMXCodec::kThumbnailMode;
        }

        if(flags & kPreferInterlacedOutputContent) {
                quirks |= OMXCodec::kInterlacedOutputContent;
        }

    }
#endif
    else if (!strncmp(componentName, "OMX.TI.", 7) || !strncmp("OMX.ITTIAM.", componentName, 11)) {
#else
    if (!strncmp(componentName, "OMX.TI.", 7)) {
#endif
        // Apparently I must not use OMX_UseBuffer on either input or
        // output ports on any of the TI components or quote:
        // "(I) may have unexpected problem (sic) which can be timing related
        //  and hard to reproduce."

#if defined(OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
        if (!strncmp(componentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E", strlen("OMX.TI.DUCATI1.VIDEO.MPEG4E")) ||
           !strncmp(componentName, "OMX.TI.DUCATI1.VIDEO.H264E", strlen("OMX.TI.DUCATI1.VIDEO.H264E"))) {
            LOGV("USING DUCATI ENCODER. isEncoder : %d",isEncoder);
            quirks |= kRequiresAllocateBufferOnOutputPorts;
            quirks |= kAvoidMemcopyInputRecordingFrames;
        }
#else
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        if (!strncmp(componentName, "OMX.TI.Video.encoder", 20) ||
            !strncmp(componentName, "OMX.TI.720P.Encoder", 19)) {
            quirks |= kAvoidMemcopyInputRecordingFrames;
        }
#endif
    }
#ifndef OMAP_ENHANCEMENT
    if (!strcmp(componentName, "OMX.TI.Video.Decoder")) {
        quirks |= kInputBufferSizesAreBogus;
    }
#endif
    if (!strncmp(componentName, "OMX.SEC.", 8) && !isEncoder) {
        // These output buffers contain no video data, just some
        // opaque information that allows the overlay to display their
        // contents.
        quirks |= kOutputBuffersAreUnreadable;
    }

    if (!strncmp(componentName, "OMX.SEC.", 8) && isEncoder) {
        // These input buffers contain meta data (for instance,
        // information helps locate the actual YUV data, or
        // the physical address of the YUV data).
        quirks |= kStoreMetaDataInInputVideoBuffers;
    }

    return quirks;
}

// static
void OMXCodec::findMatchingCodecs(
        const char *mime,
        bool createEncoder, const char *matchComponentName,
        uint32_t flags,
        Vector<String8> *matchingCodecs) {
    matchingCodecs->clear();

    for (int index = 0;; ++index) {
        const char *componentName;

        if (createEncoder) {
            componentName = GetCodec(
                    kEncoderInfo,
                    sizeof(kEncoderInfo) / sizeof(kEncoderInfo[0]),
                    mime, index);
        } else {
            componentName = GetCodec(
                    kDecoderInfo,
                    sizeof(kDecoderInfo) / sizeof(kDecoderInfo[0]),
                    mime, index);
        }

        if (!componentName) {
            break;
        }

        // If a specific codec is requested, skip the non-matching ones.
        if (matchComponentName && strcmp(componentName, matchComponentName)) {
            continue;
        }

        matchingCodecs->push(String8(componentName));
    }

#ifdef OMAP_ENHANCEMENT
    char value[PROPERTY_VALUE_MAX];
    property_get("debug.video.preferswcodec", value, "0");
    if (atoi(value))
    {
        flags |= kPreferSoftwareCodecs;
    }
#endif

    if (flags & kPreferSoftwareCodecs) {
        matchingCodecs->sort(CompareSoftwareCodecsFirst);
    }
}

// static
sp<MediaSource> OMXCodec::Create(
        const sp<IOMX> &omx,
        const sp<MetaData> &meta, bool createEncoder,
        const sp<MediaSource> &source,
        const char *matchComponentName,
        uint32_t flags) {
    const char *mime;
    bool success = meta->findCString(kKeyMIMEType, &mime);
    CHECK(success);

    Vector<String8> matchingCodecs;
    findMatchingCodecs(
            mime, createEncoder, matchComponentName, flags, &matchingCodecs);

    if (matchingCodecs.isEmpty()) {
        return NULL;
    }

    sp<OMXCodecObserver> observer = new OMXCodecObserver;
    IOMX::node_id node = 0;

    const char *componentName;
    for (size_t i = 0; i < matchingCodecs.size(); ++i) {
        componentName = matchingCodecs[i].string();

        sp<MediaSource> softwareCodec = createEncoder?
            InstantiateSoftwareEncoder(componentName, source, meta):
            InstantiateSoftwareCodec(componentName, source);

        if (softwareCodec != NULL) {
            LOGV("Successfully allocated software codec '%s'", componentName);

            return softwareCodec;
        }

        LOGV("Attempting to allocate OMX node '%s'", componentName);
#ifdef OMAP_ENHANCEMENT
uint32_t quirks = getComponentQuirks(componentName, createEncoder, flags);
#else
        uint32_t quirks = getComponentQuirks(componentName, createEncoder);
#endif
        if (!createEncoder
                && (quirks & kOutputBuffersAreUnreadable)
                && (flags & kClientNeedsFramebuffer)) {
            if (strncmp(componentName, "OMX.SEC.", 8)) {
                // For OMX.SEC.* decoders we can enable a special mode that
                // gives the client access to the framebuffer contents.

                LOGW("Component '%s' does not give the client access to "
                     "the framebuffer contents. Skipping.",
                     componentName);

                continue;
            }
        }

        status_t err = omx->allocateNode(componentName, observer, &node);
        if (err == OK) {
            LOGV("Successfully allocated OMX node '%s'", componentName);

            sp<OMXCodec> codec = new OMXCodec(
                    omx, node, quirks,
                    createEncoder, mime, componentName,
                    source);

            observer->setCodec(codec);

            err = codec->configureCodec(meta, flags);

            if (err == OK) {
                return codec;
            }

            LOGV("Failed to configure codec '%s'", componentName);
        }
    }

    return NULL;
}

#ifdef OMAP_ENHANCEMENT
// static
sp<MediaSource> OMXCodec::Create(
        const sp<IOMX> &omx,
        const sp<MetaData> &meta, bool createEncoder,
        const sp<MediaSource> &source,
        IOMX::node_id &nodeId,
        const char *matchComponentName,
        uint32_t flags) {
    const char *mime;
    bool success = meta->findCString(kKeyMIMEType, &mime);
    CHECK(success);

    Vector<String8> matchingCodecs;
    findMatchingCodecs(
            mime, createEncoder, matchComponentName, flags, &matchingCodecs);

    if (matchingCodecs.isEmpty()) {
        return NULL;
    }

    sp<OMXCodecObserver> observer = new OMXCodecObserver;
    IOMX::node_id node = 0;

    const char *componentName;
    for (size_t i = 0; i < matchingCodecs.size(); ++i) {
        componentName = matchingCodecs[i].string();

#if BUILD_WITH_FULL_STAGEFRIGHT
        sp<MediaSource> softwareCodec =
            InstantiateSoftwareCodec(componentName, source);

        if (softwareCodec != NULL) {
            LOGV("Successfully allocated software codec '%s'", componentName);

            return softwareCodec;
        }
#endif

        LOGV("Attempting to allocate OMX node '%s'", componentName);

        status_t err = omx->allocateNode(componentName, observer, &node);
        if (err == OK) {
            LOGV("Successfully allocated OMX node '%s'", componentName);

#ifdef TARGET_OMAP4
            sp<OMXCodec> codec = new OMXCodec(
                    omx, node, getComponentQuirks(componentName,createEncoder,flags),
                    createEncoder, mime, componentName,
                    source);
#else
            sp<OMXCodec> codec = new OMXCodec(
                    omx, node, getComponentQuirks(componentName,createEncoder),
                    createEncoder, mime, componentName,
                    source);
#endif

            observer->setCodec(codec);

            nodeId = node;

            err = codec->configureCodec(meta,flags);

            if (err == OK) {
                return codec;
            }

            LOGV("Failed to configure codec '%s'", componentName);
        }
    }

    return NULL;
}
#endif

status_t OMXCodec::configureCodec(const sp<MetaData> &meta, uint32_t flags) {
#if !(defined(OMAP_ENHANCEMENT) && defined (TARGET_OMAP4))
    if (!(flags & kIgnoreCodecSpecificData)) {
#endif
        uint32_t type;
        const void *data;
        size_t size;

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    status_t err;

    if (meta->findData(kKeyStreamSpecificData, &type, &data, &size))
    {
        if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {

            if ((!strcasecmp(MEDIA_MIMETYPE_VIDEO_VP6, mMIME)) ||
                (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VP7, mMIME))) {

                LOGD("Setting VP6/7 Params");
                OMX_TI_PARAM_IVFFLAG tVP6Param;
                InitOMXParams(&tVP6Param);
                err = mOMX->getParameter(
                        mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoIvfMode, &tVP6Param, sizeof(tVP6Param));

                CHECK_EQ(err, OK);
                tVP6Param.bIvfFlag = OMX_FALSE;

                char *t = ((char *)(data));
                if ((t[0] == 'D') && (t[1] == 'K') && (t[2] == 'I') && (t[3] == 'F')){
                    LOGD("IVF Header Present. Size = %d. Limiting it to 32 as per spec.", size);
                    size = 32;
                    addCodecSpecificData(data, size);
                    tVP6Param.bIvfFlag = OMX_TRUE;
                }
                err = mOMX->setParameter(
                        mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoIvfMode, &tVP6Param, sizeof(tVP6Param));

                OMX_TI_PARAM_PAYLOADHEADERFLAG tVP6Payloadheader;
                InitOMXParams(&tVP6Payloadheader);
                err = mOMX->getParameter(
                        mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoPayloadHeaderFlag, &tVP6Payloadheader, sizeof(tVP6Payloadheader));
                CHECK_EQ(err, OK);
                tVP6Payloadheader.bPayloadHeaderFlag = OMX_FALSE;
                err = mOMX->setParameter(
                        mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoPayloadHeaderFlag, &tVP6Payloadheader, sizeof(tVP6Payloadheader));

            }
        }
        else addCodecSpecificData(data, size);
    }
    else if (meta->findData(kKeyESDS, &type, &data, &size)) {
#else
    if (meta->findData(kKeyESDS, &type, &data, &size)) {
#endif
            ESDS esds((const char *)data, size);
            CHECK_EQ(esds.InitCheck(), OK);

            const void *codec_specific_data;
            size_t codec_specific_data_size;
            esds.getCodecSpecificInfo(
                    &codec_specific_data, &codec_specific_data_size);

            addCodecSpecificData(
                    codec_specific_data, codec_specific_data_size);
        } else if (meta->findData(kKeyAVCC, &type, &data, &size)) {
            // Parse the AVCDecoderConfigurationRecord

            const uint8_t *ptr = (const uint8_t *)data;

            CHECK(size >= 7);
            CHECK_EQ(ptr[0], 1);  // configurationVersion == 1
            uint8_t profile = ptr[1];
            uint8_t level = ptr[3];

            // There is decodable content out there that fails the following
            // assertion, let's be lenient for now...
            // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

            size_t lengthSize = 1 + (ptr[4] & 3);

            // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
            // violates it...
            // CHECK((ptr[5] >> 5) == 7);  // reserved

            size_t numSeqParameterSets = ptr[5] & 31;

            ptr += 6;
            size -= 6;

            for (size_t i = 0; i < numSeqParameterSets; ++i) {
                CHECK(size >= 2);
                size_t length = U16_AT(ptr);

                ptr += 2;
                size -= 2;

                CHECK(size >= length);

                addCodecSpecificData(ptr, length);

                ptr += length;
                size -= length;
            }

            CHECK(size >= 1);
            size_t numPictureParameterSets = *ptr;
            ++ptr;
            --size;

            for (size_t i = 0; i < numPictureParameterSets; ++i) {
                CHECK(size >= 2);
                size_t length = U16_AT(ptr);

                ptr += 2;
                size -= 2;

                CHECK(size >= length);

                addCodecSpecificData(ptr, length);

                ptr += length;
                size -= length;
            }

            CODEC_LOGV(
                    "AVC profile = %d (%s), level = %d",
                    (int)profile, AVCProfileToString(profile), level);

            if (!strcmp(mComponentName, "OMX.TI.Video.Decoder")
                && (profile != kAVCProfileBaseline || level > 31)) {
                // This stream exceeds the decoder's capabilities. The decoder
                // does not handle this gracefully and would clobber the heap
                // and wreak havoc instead...

                LOGE("Profile and/or level exceed the decoder's capabilities.");
                return ERROR_UNSUPPORTED;
            }
#ifdef OMAP_ENHANCEMENT
            int32_t width, height;
            bool success = meta->findInt32(kKeyWidth, &width);
            success = success && meta->findInt32(kKeyHeight, &height);
            CHECK(success);
            if (!strcmp(mComponentName, "OMX.TI.720P.Decoder")
                && (profile == kAVCProfileBaseline && level <= 39)
                && (width*height <= MAX_RESOLUTION)
                && (width <= MAX_RESOLUTION_WIDTH && height <= MAX_RESOLUTION_HEIGHT ))
            {
                // Though this decoder can handle this profile/level,
                // we prefer to use "OMX.TI.Video.Decoder" for
                // Baseline Profile with level <=39 and sub 720p
                return ERROR_UNSUPPORTED;
            }
#endif
        }
#ifdef OMAP_ENHANCEMENT
    else if (meta->findData(kKeyHdr, &type, &data, &size)) {
        CODEC_LOGV("Codec specific information of size %d", size);
        addCodecSpecificData(data, size);
    }

    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mMIME)) {
        //Set the profile (RCV or VC1)
        meta->findData(kKeyHdr, &type, &data, &size);
        const uint8_t *ptr = (const uint8_t *)data;

        OMX_U32 width = (((OMX_U32)ptr[18] << 24) | ((OMX_U32)ptr[17] << 16) | ((OMX_U32)ptr[16] << 8) | (OMX_U32)ptr[15]);
        OMX_U32 height  = (((OMX_U32)ptr[22] << 24) | ((OMX_U32)ptr[21] << 16) | ((OMX_U32)ptr[20] << 8) | (OMX_U32)ptr[19]);

        CODEC_LOGV("Height and width = %u %u\n", height, width);

        //This logic is used by omap3 codec, is use less in omap4 at this time, but we may want to use it after, when the
        // OpenMAX IL gets the update.
        //MAX_RESOLUTION is use to take the desition between TI and Ittiam WMV codec
        if((!strcmp(mComponentName, "OMX.TI.720P.Decoder")) &&
            (!strcmp(mComponentName, "OMX.TI.Video.Decoder")) &&
            (width*height > MAX_RESOLUTION)) {
            OMX_U32 NewCompression = MAKEFOURCC_WMC((OMX_U8)ptr[27], (OMX_U8)ptr[28], (OMX_U8)ptr[29], (OMX_U8)ptr[30]);
            OMX_U32 StreamType;
            OMX_PARAM_WMVFILETYPE WMVFileType;
            InitOMXParams(&WMVFileType);

            if (NewCompression == FOURCC_WMV3) {
                CODEC_LOGV("VIDDEC_WMV_RCVSTREAM\n");
                StreamType = VIDDEC_WMV_RCVSTREAM;
            }
            else if(NewCompression == FOURCC_WVC1) {
                CODEC_LOGV("VIDDEC_WMV_ELEMSTREAM\n");
                StreamType = VIDDEC_WMV_ELEMSTREAM;
            }
            else {
                CODEC_LOGV("ERROR...  PROFILE NOT KNOWN ASSUMED TO BE VIDDEC_WMV_RCVSTREAM\n");
                StreamType = VIDDEC_WMV_RCVSTREAM;
            }
            WMVFileType.nWmvFileType = StreamType;

            status_t err = mOMX->setParameter(mNode, (OMX_INDEXTYPE)VideoDecodeCustomParamWMVFileType, &WMVFileType, sizeof(WMVFileType));
            if (err != OMX_ErrorNone) {
                return err;
            }
        }
    }
#endif
#if !(defined(OMAP_ENHANCEMENT) && defined (TARGET_OMAP4))
    }
#endif

    int32_t bitRate = 0;
    if (mIsEncoder) {
        CHECK(meta->findInt32(kKeyBitRate, &bitRate));

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP3)
        if (!strcmp(mComponentName, "OMX.TI.Video.encoder")) {
            int32_t width, height;
            bool success = meta->findInt32(kKeyWidth, &width);
            success = success && meta->findInt32(kKeyHeight, &height);
            CHECK(success);
            if (width*height > MAX_RESOLUTION) {
                // need OMX.TI.720P.Encoder
                return ERROR_UNSUPPORTED;
            }
        }
#endif
    }
    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_NB, mMIME)) {
        setAMRFormat(false /* isWAMR */, bitRate);
    }
    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_WB, mMIME)) {
        setAMRFormat(true /* isWAMR */, bitRate);
    }
    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AAC, mMIME)) {
        int32_t numChannels, sampleRate;
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));

#ifndef DONT_SET_AUDIO_AAC_FORMAT
        setAACFormat(numChannels, sampleRate, bitRate);
#endif
#ifdef OMAP_ENHANCEMENT
        // Configure TI OMX component to FrameMode for AAC-ADTS
        if (!strcmp(mComponentName, "OMX.TI.AAC.decode")) {
            OMX_INDEXTYPE index;

            CODEC_LOGV("OMXCodec::configureCodec() TI AAC - Configure Component to FrameMode");

            // Get Extension Index from Component
            status_t err = mOMX->getExtensionIndex(mNode, "OMX.TI.index.config.AacDecFrameModeInfo", &index);
            if (err != OK) {
                CODEC_LOGV("OMXCodec::configureCodec() TI AAC - Problem getting ExtensionIndex - Use SteamMode");
            }
            else {
                OMX_U16 framemode = 1;
                // Set FrameMode for ADTS streams
                err = mOMX->setConfig(mNode, index, &framemode, sizeof(framemode));
                if (err != OK) {
                    CODEC_LOGV("OMXCodec::configureCodec() TI AAC - Problem configuring FrameMode - Use SteamMode");
                }
            }
        }
#endif
    }

    if (!strncasecmp(mMIME, "video/", 6)) {

        if (mIsEncoder) {
            setVideoInputFormat(mMIME, meta);
        } else {
            int32_t width, height;
            bool success = meta->findInt32(kKeyWidth, &width);
            success = success && meta->findInt32(kKeyHeight, &height);
            CHECK(success);

#ifdef OMAP_ENHANCEMENT

            /* Update proper Profile, Level, No. of Reference frames.
               This will aid in less (tiler) memory utilization by ducati side */
            if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {
                /* save video FPS */
                if (!(meta->findInt32(kKeyVideoFPS, &mVideoFPS))) {
                    mVideoFPS = 30; //default value in case of FPS data not found
                }

                int32_t vprofile,vlevel,vinterlaced,vnumrefframes;

                if ((!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mMIME)) &&
                        meta->findInt32(kKeyVideoProfile, &vprofile) &&
                        meta->findInt32(kKeyVideoLevel, &vlevel) &&
                        meta->findInt32(kKeyVideoInterlaced, &vinterlaced) &&
                        meta->findInt32(kKeyNumRefFrames, &vnumrefframes))
                {

                    OMX_VIDEO_PARAM_AVCTYPE h264type;
                    InitOMXParams(&h264type);
                    h264type.nPortIndex = kPortIndexInput;

                    status_t err = mOMX->getParameter(
                            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
                    CHECK_EQ(err, OK);

                    //h264type.nBFrames = 0; //check if requred


                    /* Update profile from uint32_t type */
                    switch(vprofile)
                    {
                        case kAVCProfileBaseline :
                                    h264type.eProfile = OMX_VIDEO_AVCProfileBaseline;
                                    break;
                        case kAVCProfileMain :
                                    h264type.eProfile = OMX_VIDEO_AVCProfileMain;
                                    break;
                        case kAVCProfileExtended :
                                    h264type.eProfile = OMX_VIDEO_AVCProfileExtended;
                                    break;
                        case kAVCProfileHigh :
                                    h264type.eProfile = OMX_VIDEO_AVCProfileHigh;
                                    break;

                        case kAVCProfileHigh10 :
                        case kAVCProfileHigh422 :
                        case kAVCProfileHigh444 :
                        default:
                                    //Unsupported profiles by OMX.TI.DUCATI1.VIDEO.DECODER
                                    LOGE("profile code 0x%x %d not supported", vprofile,vprofile);
                                    CHECK_EQ(0,1);
                                    return UNKNOWN_ERROR;
                    }


                    switch(vlevel)
                    {

                        case 9 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel1b;
                                    break;
                        case 10 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel1;
                                    break;
                        case 11 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel11;
                                    break;
                        case 12 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel12;
                                    break;
                        case 13 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel13;
                                    break;
                        case 20 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel2;
                                    break;
                        case 21 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel21;
                                    break;
                        case 22 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel22;
                                    break;
                        case 30 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel3;
                                    break;
                        case 31 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel31;
                                    break;
                        case 32 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel32;
                                    break;

                        case 40 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel4;
                                    break;

                        case 41 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel41;
                                    break;

                        case 42 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel42;
                                    break;

                        case 50 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel5;
                                    break;

                        case 51 :
                                    h264type.eLevel = OMX_VIDEO_AVCLevel51;
                                    break;

                        default:
                                    //Unsupported level value
                                    LOGE("profile code 0x%x %d not supported", vlevel,vlevel);
                                    CHECK_EQ(0,1);
                                    return UNKNOWN_ERROR;
                    }

                    h264type.nRefFrames = vnumrefframes;

                    err = mOMX->setParameter(
                            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
                    CHECK_EQ(err, OK);

                    /* Cross check from component */
                    err = mOMX->getParameter(
                            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));

                    LOGD("Updated. H264 Component profile %d level %d NRefFrames %d", h264type.eProfile,h264type.eLevel, (int)h264type.nRefFrames);
                }
            }
#endif
            status_t err = setVideoOutputFormat(
                    mMIME, width, height);

            if (err != OK) {
                return err;
            }
        }
    }

    if (!strcasecmp(mMIME, MEDIA_MIMETYPE_IMAGE_JPEG)
        && !strcmp(mComponentName, "OMX.TI.JPEG.decode")) {
        OMX_COLOR_FORMATTYPE format =
            OMX_COLOR_Format32bitARGB8888;
            // OMX_COLOR_FormatYUV420PackedPlanar;
            // OMX_COLOR_FormatCbYCrY;
            // OMX_COLOR_FormatYUV411Planar;

        int32_t width, height;
        bool success = meta->findInt32(kKeyWidth, &width);
        success = success && meta->findInt32(kKeyHeight, &height);

        int32_t compressedSize;
        success = success && meta->findInt32(
                kKeyMaxInputSize, &compressedSize);

        CHECK(success);
        CHECK(compressedSize > 0);

        setImageOutputFormat(format, width, height);
        setJPEGInputFormat(width, height, (OMX_U32)compressedSize);
    }

    int32_t maxInputSize;
    if (meta->findInt32(kKeyMaxInputSize, &maxInputSize)) {
#ifdef OMAP_ENHANCEMENT
        if (!strcmp(mComponentName, "OMX.TI.Video.Decoder") || !strcmp(mComponentName, "OMX.TI.720P.Decoder")) {
            // We need to allocate at least twice the "maxInputSize"
            // to get enough room for internal OMX buffer handling.
            maxInputSize += maxInputSize;
            CODEC_LOGV("Resize maxInputSize*2, maxInputSize=%d", maxInputSize);
        }
#endif
        setMinBufferSize(kPortIndexInput, (OMX_U32)maxInputSize);
    }

    if (!strcmp(mComponentName, "OMX.TI.AMR.encode")
        || !strcmp(mComponentName, "OMX.TI.WBAMR.encode")
        || !strcmp(mComponentName, "OMX.TI.AAC.encode")) {
        setMinBufferSize(kPortIndexOutput, 8192);  // XXX
    }

    initOutputFormat(meta);

    if ((flags & kClientNeedsFramebuffer)
            && !strncmp(mComponentName, "OMX.SEC.", 8)) {
        OMX_INDEXTYPE index;

        status_t err =
            mOMX->getExtensionIndex(
                    mNode,
                    "OMX.SEC.index.ThumbnailMode",
                    &index);

        if (err != OK) {
            return err;
        }

        OMX_BOOL enable = OMX_TRUE;
        err = mOMX->setConfig(mNode, index, &enable, sizeof(enable));

        if (err != OK) {
            CODEC_LOGE("setConfig('OMX.SEC.index.ThumbnailMode') "
                       "returned error 0x%08x", err);

            return err;
        }

        mQuirks &= ~kOutputBuffersAreUnreadable;
    }

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    mNSecsToWait = 5000000000; // 5 seconds
    const char *flash_fp_usecase;
    const char *flash_em_usecase;
    if ((meta->findCString('fpfl', &flash_fp_usecase)) ||
        (meta->findCString('fpem', &flash_em_usecase))) {
        LOGD("\n\n\n Executing a flash usecase \n\n\n");
        mNSecsToWait = 0;
    }
#endif

    return OK;
}

void OMXCodec::setMinBufferSize(OMX_U32 portIndex, OMX_U32 size) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    if ((portIndex == kPortIndexInput && (mQuirks & kInputBufferSizesAreBogus))
        || (def.nBufferSize < size)) {
        def.nBufferSize = size;
    }

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    /* Aligning the buffer size as observed memory failures in allocateBuffersOnPort
     * for input port incase buffer sizes are not Cache line boundary aligned.
     * Actually the Memory Dealer allocates a large chunk for the Total buffer size.
     * While requesting individual buffers, it provides chunks (offset) from the large
     * memory. During this, it provides address based on Cache line boundary. Hence
     * there would not be sufficient memory for the last chunk and it fails */
    def.nBufferSize = ((def.nBufferSize + CACHELINE_BOUNDARY_MEMALIGNMENT - 1) &
                        ~(CACHELINE_BOUNDARY_MEMALIGNMENT - 1));
#endif

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    // Make sure the setting actually stuck.
    if (portIndex == kPortIndexInput
            && (mQuirks & kInputBufferSizesAreBogus)) {
        CHECK_EQ(def.nBufferSize, size);
    } else {
        CHECK(def.nBufferSize >= size);
    }
}

status_t OMXCodec::setVideoPortFormatType(
        OMX_U32 portIndex,
        OMX_VIDEO_CODINGTYPE compressionFormat,
        OMX_COLOR_FORMATTYPE colorFormat) {
    OMX_VIDEO_PARAM_PORTFORMATTYPE format;
    InitOMXParams(&format);
    format.nPortIndex = portIndex;
    format.nIndex = 0;
    bool found = false;

    OMX_U32 index = 0;
    for (;;) {
        format.nIndex = index;
        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &format, sizeof(format));

        if (err != OK) {
            return err;
        }

        // The following assertion is violated by TI's video decoder.
        // CHECK_EQ(format.nIndex, index);

#if 1
        CODEC_LOGV("portIndex: %ld, index: %ld, eCompressionFormat=%d eColorFormat=%d",
             portIndex,
             index, format.eCompressionFormat, format.eColorFormat);
#endif

        if (!strcmp("OMX.TI.Video.encoder", mComponentName) ||
            !strcmp("OMX.TI.720P.Encoder", mComponentName)) {
            if (portIndex == kPortIndexInput
                    && colorFormat == format.eColorFormat) {
                // eCompressionFormat does not seem right.
                found = true;
                break;
            }
            if (portIndex == kPortIndexOutput
                    && compressionFormat == format.eCompressionFormat) {
                // eColorFormat does not seem right.
                found = true;
                break;
            }
        }

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        if (!strcmp("OMX.TI.DUCATI1.VIDEO.DECODER", mComponentName)) {
            if ( (compressionFormat == OMX_VIDEO_CodingH263) &&
                 (format.eCompressionFormat != compressionFormat) ) {
                // Ducati Decoder returns MPEG4 as default compression type.
                // Update H263 compression type
                format.eCompressionFormat=compressionFormat;
                found = true;
                break;
            }
        format.eColorFormat = colorFormat; //HACK. Should be removed in 1.20 ducati release
        }
#endif

        if (format.eCompressionFormat == compressionFormat
            && format.eColorFormat == colorFormat) {
            found = true;
            break;
        }

        ++index;
    }

    if (!found) {
        return UNKNOWN_ERROR;
    }

    CODEC_LOGV("found a match.");
    status_t err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoPortFormat,
            &format, sizeof(format));

    return err;
}

static size_t getFrameSize(
        OMX_COLOR_FORMATTYPE colorFormat, int32_t width, int32_t height) {
    switch (colorFormat) {
        case OMX_COLOR_FormatYCbYCr:
        case OMX_COLOR_FormatCbYCrY:
            return width * height * 2;

        case OMX_COLOR_FormatYUV420Planar:
        case OMX_COLOR_FormatYUV420SemiPlanar:
            return (width * height * 3) / 2;

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
        case OMX_COLOR_FormatYUV420PackedSemiPlanar:
            return (4096 * height *3)/2;
#endif

        default:
            CHECK(!"Should not be here. Unsupported color format.");
            break;
    }
}

status_t OMXCodec::findTargetColorFormat(
        const sp<MetaData>& meta, OMX_COLOR_FORMATTYPE *colorFormat) {
    LOGV("findTargetColorFormat");
    CHECK(mIsEncoder);

    *colorFormat = OMX_COLOR_FormatYUV420SemiPlanar;
    int32_t targetColorFormat;
    if (meta->findInt32(kKeyColorFormat, &targetColorFormat)) {
        *colorFormat = (OMX_COLOR_FORMATTYPE) targetColorFormat;
    } else {
        if (!strcasecmp("OMX.TI.Video.encoder", mComponentName) ||
            !strcasecmp("OMX.TI.720P.Encoder", mComponentName)) {
            *colorFormat = OMX_COLOR_FormatYCbYCr;
        }
    }

    // Check whether the target color format is supported.
    return isColorFormatSupported(*colorFormat, kPortIndexInput);
}

status_t OMXCodec::isColorFormatSupported(
        OMX_COLOR_FORMATTYPE colorFormat, int portIndex) {
    LOGV("isColorFormatSupported: %d", static_cast<int>(colorFormat));

    // Enumerate all the color formats supported by
    // the omx component to see whether the given
    // color format is supported.
    OMX_VIDEO_PARAM_PORTFORMATTYPE portFormat;
    InitOMXParams(&portFormat);
    portFormat.nPortIndex = portIndex;
    OMX_U32 index = 0;
    portFormat.nIndex = index;
    while (true) {
        if (OMX_ErrorNone != mOMX->getParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &portFormat, sizeof(portFormat))) {
            break;
        }
        // Make sure that omx component does not overwrite
        // the incremented index (bug 2897413).
        CHECK_EQ(index, portFormat.nIndex);
        if ((portFormat.eColorFormat == colorFormat)) {
            LOGV("Found supported color format: %d", portFormat.eColorFormat);
            return OK;  // colorFormat is supported!
        }
        ++index;
        portFormat.nIndex = index;

        // OMX Spec defines less than 50 color formats
        // 1000 is more than enough for us to tell whether the omx
        // component in question is buggy or not.
        if (index >= 1000) {
            LOGE("More than %ld color formats are supported???", index);
            break;
        }
    }

    LOGE("color format %d is not supported", colorFormat);
    return UNKNOWN_ERROR;
}

void OMXCodec::setVideoInputFormat(
        const char *mime, const sp<MetaData>& meta) {

    int32_t width, height, frameRate, bitRate, stride, sliceHeight;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    int32_t paddedWidth, paddedHeight;
#endif
    bool success = meta->findInt32(kKeyWidth, &width);
    success = success && meta->findInt32(kKeyHeight, &height);
    success = success && meta->findInt32(kKeySampleRate, &frameRate);
    success = success && meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeyStride, &stride);
    success = success && meta->findInt32(kKeySliceHeight, &sliceHeight);
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    success = success && meta->findInt32(kKeyPaddedWidth, &paddedWidth);
    success = success && meta->findInt32(kKeyPaddedHeight, &paddedHeight);
#endif

    CHECK(success);
    CHECK(stride != 0);

    OMX_VIDEO_CODINGTYPE compressionFormat = OMX_VIDEO_CodingUnused;
    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime)) {
        compressionFormat = OMX_VIDEO_CodingAVC;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) {
        compressionFormat = OMX_VIDEO_CodingMPEG4;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime)) {
        compressionFormat = OMX_VIDEO_CodingH263;
#ifdef OMAP_ENHANCEMENT
    }
    else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime))
    {
        compressionFormat = OMX_VIDEO_CodingWMV;
#endif
    } else {
        LOGE("Not a supported video mime type: %s", mime);
        CHECK(!"Should not be here. Not a supported video mime type.");
    }

    OMX_COLOR_FORMATTYPE colorFormat;
    CHECK_EQ(OK, findTargetColorFormat(meta, &colorFormat));

    status_t err;
    OMX_PARAM_PORTDEFINITIONTYPE def;
    OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

    //////////////////////// Input port /////////////////////////
    CHECK_EQ(setVideoPortFormatType(
            kPortIndexInput, OMX_VIDEO_CodingUnused,
            colorFormat), OK);

    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    def.nBufferSize = getFrameSize(colorFormat,
            stride > 0? stride: -stride, paddedHeight);
#else
    def.nBufferSize = getFrameSize(colorFormat,
            stride > 0? stride: -stride, sliceHeight);
#endif

    CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;
    video_def->nStride = stride;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    if( !strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
        || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E")){
        video_def->nStride = 4096; // TI 2D-Buffer specific Hardcoding
    }

    def.nBufferCountActual = INPUT_BUFFER_COUNT;
    if(!strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E"))
    {
#ifdef SUPPORT_B_FRAMES
        def.nBufferCountActual = OMX_NUM_B_FRAMES + 1;;
#else
        def.nBufferCountActual = 2;
#endif
    }
#else
    video_def->nSliceHeight = sliceHeight;
#endif
    video_def->xFramerate = (frameRate << 16);  // Q16 format
    video_def->eCompressionFormat = OMX_VIDEO_CodingUnused;
    video_def->eColorFormat = colorFormat;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    //////////////////////// Output port /////////////////////////
    CHECK_EQ(setVideoPortFormatType(
            kPortIndexOutput, compressionFormat, OMX_COLOR_FormatUnused),
            OK);
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    CHECK_EQ(err, OK);
    CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;
    video_def->xFramerate = (frameRate << 16);      // Value is used on output port for rate control
    video_def->nBitrate = bitRate;  // Q16 format
    video_def->eCompressionFormat = compressionFormat;
    video_def->eColorFormat = OMX_COLOR_FormatUnused;
    if (mQuirks & kRequiresLargerEncoderOutputBuffer) {
        // Increases the output buffer size
        def.nBufferSize = ((def.nBufferSize * 3) >> 1);
    }

#ifdef TARGET_OMAP4
    def.nBufferCountActual = OUTPUT_BUFFER_COUNT;
#endif
    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    if( !strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
        || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E")){

        //OMX_TI_IndexParam2DBufferAllocDimension
        OMX_CONFIG_RECTTYPE tFrameDim;
        tFrameDim.nPortIndex = kPortIndexInput;
        tFrameDim.nWidth = paddedWidth;
        tFrameDim.nHeight = paddedHeight;
        InitOMXParams(&tFrameDim);

        err = mOMX->setParameter( mNode, (OMX_INDEXTYPE)OMX_TI_IndexParam2DBufferAllocDimension, &tFrameDim, sizeof(tFrameDim));
            CHECK_EQ(err, OK);

        //OMX_TI_IndexParamBufferPreAnnouncement
        OMX_TI_PARAM_BUFFERPREANNOUNCE PreAnnouncement;
        InitOMXParams(&PreAnnouncement);
        PreAnnouncement.nPortIndex = kPortIndexInput;
        err = mOMX->getParameter( mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamBufferPreAnnouncement, &PreAnnouncement, sizeof(PreAnnouncement));
        CHECK_EQ(err, OK);

        PreAnnouncement.bEnabled = OMX_FALSE;
        err = mOMX->setParameter( mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamBufferPreAnnouncement, &PreAnnouncement,sizeof(PreAnnouncement));
        CHECK_EQ(err, OK);
    }
#endif

    /////////////////// Codec-specific ////////////////////////
    switch (compressionFormat) {
        case OMX_VIDEO_CodingMPEG4:
        {
            CHECK_EQ(setupMPEG4EncoderParameters(meta), OK);
            break;
        }

        case OMX_VIDEO_CodingH263:
            CHECK_EQ(setupH263EncoderParameters(meta), OK);
            break;

        case OMX_VIDEO_CodingAVC:
        {
            CHECK_EQ(setupAVCEncoderParameters(meta), OK);
            break;
        }

        default:
            CHECK(!"Support for this compressionFormat to be implemented.");
            break;
    }
}

static OMX_U32 setPFramesSpacing(int32_t iFramesInterval, int32_t frameRate) {
    if (iFramesInterval < 0) {
        return 0xFFFFFFFF;
    } else if (iFramesInterval == 0) {
        return 0;
    }
    OMX_U32 ret = frameRate * iFramesInterval;
    CHECK(ret > 1);
    return ret;
}

status_t OMXCodec::setupErrorCorrectionParameters() {
    OMX_VIDEO_PARAM_ERRORCORRECTIONTYPE errorCorrectionType;
    InitOMXParams(&errorCorrectionType);
    errorCorrectionType.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoErrorCorrection,
            &errorCorrectionType, sizeof(errorCorrectionType));
    if (err != OK) {
        LOGW("Error correction param query is not supported");
        return OK;  // Optional feature. Ignore this failure
    }

    errorCorrectionType.bEnableHEC = OMX_FALSE;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    errorCorrectionType.bEnableResync = OMX_FALSE;
    errorCorrectionType.nResynchMarkerSpacing = 0;
#else
    errorCorrectionType.bEnableResync = OMX_TRUE;
    errorCorrectionType.nResynchMarkerSpacing = 256;
#endif
    errorCorrectionType.bEnableDataPartitioning = OMX_FALSE;
    errorCorrectionType.bEnableRVLC = OMX_FALSE;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoErrorCorrection,
            &errorCorrectionType, sizeof(errorCorrectionType));
    if (err != OK) {
        LOGW("Error correction param configuration is not supported");
    }

    // Optional feature. Ignore the failure.
    return OK;
}

status_t OMXCodec::setupBitRate(int32_t bitRate) {
    OMX_VIDEO_PARAM_BITRATETYPE bitrateType;
    InitOMXParams(&bitrateType);
    bitrateType.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoBitrate,
            &bitrateType, sizeof(bitrateType));
    CHECK_EQ(err, OK);

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.H264E"))
    {
        bitrateType.eControlRate = OMX_Video_ControlRateVariable;
    }
    else if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E"))
    {
        bitrateType.eControlRate = OMX_Video_ControlRateVariable;
    }
#else
    bitrateType.eControlRate = OMX_Video_ControlRateVariable;
#endif

    bitrateType.nTargetBitrate = bitRate;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoBitrate,
            &bitrateType, sizeof(bitrateType));
    CHECK_EQ(err, OK);
    return OK;
}

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
status_t OMXCodec::setupEncoderPresetParams(int32_t isS3DEnabled) {
    OMX_VIDEO_PARAM_ENCODER_PRESETTYPE EncoderPreset;
    status_t Err = OK;

    /*Encoder Preset settings*/
    InitOMXParams(&EncoderPreset);
    EncoderPreset.nPortIndex = kPortIndexOutput;
    Err = mOMX->getParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoEncoderPreset, &EncoderPreset,sizeof(EncoderPreset));
    CHECK_EQ(Err, OK);

    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.H264E"))
    {
//#ifdef FOURMV_ENABLED
        if(isS3DEnabled)
            EncoderPreset.eEncodingModePreset = OMX_Video_Enc_User_Defined;
        else
            EncoderPreset.eEncodingModePreset = OMX_Video_Enc_Med_Speed_High_Quality;

        EncoderPreset.eRateControlPreset= OMX_Video_RC_Storage ; //mpeg4VencClient->encoderPreset.eRateControlPreset;  // 4
//#endif
    }
    else if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E"))
    {
        EncoderPreset.eEncodingModePreset = OMX_Video_Enc_User_Defined; // mpeg4VencClient->encoderPreset.eEncodingModePreset; // 2
        EncoderPreset.eRateControlPreset= OMX_Video_RC_Storage ; //mpeg4VencClient->encoderPreset.eRateControlPreset;  // 4
    }

    Err = mOMX->setParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoEncoderPreset, &EncoderPreset,sizeof(EncoderPreset));
    CHECK_EQ(Err, OK);

    return Err;
}
#endif

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)

static int32_t getFrameLayout(const char* frameLayout)
{
    if (!strcmp(frameLayout, TICameraParameters::S3D_TB_FULL)) {
       return OMX_TI_Video_FRAMEPACK_TOP_BOTTOM;
    }

    if (!strcmp(frameLayout, TICameraParameters::S3D_SS_FULL)) {
        return OMX_TI_Video_FRAMEPACK_SIDE_BY_SIDE;
    }
    if (!strcmp(frameLayout, TICameraParameters::S3D_TB_SUBSAMPLED)) {
        return OMX_TI_Video_FRAMEPACK_TOP_BOTTOM;
    }

    if (!strcmp(frameLayout,TICameraParameters::S3D_SS_SUBSAMPLED)) {
       return OMX_TI_Video_FRAMEPACK_SIDE_BY_SIDE;
    }

    LOGE("Unsupported frame layout (%s)", frameLayout);

    CHECK_EQ(0, "Unsupported frame layout");
    return OMX_TI_Video_FRAMEPACK_TOP_BOTTOM;
}

status_t OMXCodec::setupEncoderFrameDataContentParams(const sp<MetaData>& meta) {
    OMX_TI_VIDEO_PARAM_FRAMEDATACONTENTTYPE FrameDataType;
    status_t Err = OK;
    int32_t seiEncodingType;
    const char *frameLayout;

    bool success = meta->findInt32(kKeySEIEncodingType, &seiEncodingType);
    CHECK(success);

    InitOMXParams(&FrameDataType);
    FrameDataType.nPortIndex = kPortIndexInput;
    Err = mOMX->getParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoFrameDataContentSettings, &FrameDataType,sizeof(FrameDataType));
    CHECK_EQ(Err, OK);

    FrameDataType.eContentType = (OMX_TI_VIDEO_FRAMECONTENTTYPE) seiEncodingType;
    Err = mOMX->setParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamVideoFrameDataContentSettings, &FrameDataType,sizeof(FrameDataType));
    CHECK_EQ(Err, OK);

    if(OMX_TI_Video_AVC_2010_StereoFramePackingType == seiEncodingType) {
        success = meta->findCString(kKeyFrameLayout, &frameLayout);
        CHECK(success);

        OMX_TI_VIDEO_PARAM_AVCENC_FRAMEPACKINGINFO2010 SettingsSEI2010;
        InitOMXParams(&SettingsSEI2010);
        SettingsSEI2010.nPortIndex = kPortIndexInput;
        Err = mOMX->getParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamStereoFramePacking2010Settings, &SettingsSEI2010,sizeof(SettingsSEI2010));
        CHECK_EQ(Err, OK);

        SettingsSEI2010.eFramePackingType = (OMX_TI_VIDEO_AVCSTEREO_FRAMEPACKTYPE) getFrameLayout(frameLayout);
        SettingsSEI2010.nFrame0PositionX = 0;
        SettingsSEI2010.nFrame0PositionY = 0;
        SettingsSEI2010.nFrame1PositionX = 0;
        SettingsSEI2010.nFrame1PositionY = 0;
        Err = mOMX->setParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamStereoFramePacking2010Settings, &SettingsSEI2010,sizeof(SettingsSEI2010));
        CHECK_EQ(Err, OK);
    }
    else if(OMX_TI_Video_AVC_2004_StereoInfoType == seiEncodingType)
    {
        OMX_TI_VIDEO_PARAM_AVCENC_STEREOINFO2004 SettingsSEI2004;
        InitOMXParams(&SettingsSEI2004);
        SettingsSEI2004.nPortIndex = kPortIndexInput;
        Err = mOMX->getParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamStereoInfo2004Settings, &SettingsSEI2004,sizeof(SettingsSEI2004));
        CHECK_EQ(Err, OK);

        SettingsSEI2004.btopFieldIsLeftViewFlag = OMX_TRUE;
        SettingsSEI2004.bViewSelfContainedFlag = OMX_FALSE;
        Err = mOMX->setParameter(mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamStereoInfo2004Settings, &SettingsSEI2004,sizeof(SettingsSEI2004));
        CHECK_EQ(Err, OK);
    }

    return Err;
}
#endif

status_t OMXCodec::getVideoProfileLevel(
        const sp<MetaData>& meta,
        const CodecProfileLevel& defaultProfileLevel,
        CodecProfileLevel &profileLevel) {
    CODEC_LOGV("Default profile: %ld, level %ld",
            defaultProfileLevel.mProfile, defaultProfileLevel.mLevel);

    // Are the default profile and level overwriten?
    int32_t profile, level;
    if (!meta->findInt32(kKeyVideoProfile, &profile)) {
        profile = defaultProfileLevel.mProfile;
    }
    if (!meta->findInt32(kKeyVideoLevel, &level)) {
        level = defaultProfileLevel.mLevel;
    }
    CODEC_LOGV("Target profile: %d, level: %d", profile, level);

    // Are the target profile and level supported by the encoder?
    OMX_VIDEO_PARAM_PROFILELEVELTYPE param;
    InitOMXParams(&param);
    param.nPortIndex = kPortIndexOutput;
    for (param.nProfileIndex = 0;; ++param.nProfileIndex) {
        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamVideoProfileLevelQuerySupported,
                &param, sizeof(param));

        if (err != OK) break;

        int32_t supportedProfile = static_cast<int32_t>(param.eProfile);
        int32_t supportedLevel = static_cast<int32_t>(param.eLevel);
        CODEC_LOGV("Supported profile: %d, level %d",
            supportedProfile, supportedLevel);

        if (profile == supportedProfile &&
            level <= supportedLevel) {
            // We can further check whether the level is a valid
            // value; but we will leave that to the omx encoder component
            // via OMX_SetParameter call.
            profileLevel.mProfile = profile;
            profileLevel.mLevel = level;
            return OK;
        }
    }

    CODEC_LOGE("Target profile (%d) and level (%d) is not supported",
            profile, level);
    return BAD_VALUE;
}

status_t OMXCodec::setupH263EncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeySampleRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
    CHECK(success);
    OMX_VIDEO_PARAM_H263TYPE h263type;
    InitOMXParams(&h263type);
    h263type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
    if (!(mQuirks & kCanNotSetVideoParameters)) {
        CHECK_EQ(err, OK);
    }

    h263type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    h263type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
    if (h263type.nPFrames == 0) {
        h263type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }
    h263type.nBFrames = 0;

    // Check profile and level parameters
    CodecProfileLevel defaultProfileLevel, profileLevel;
    defaultProfileLevel.mProfile = h263type.eProfile;
    defaultProfileLevel.mLevel = h263type.eLevel;
    err = getVideoProfileLevel(meta, defaultProfileLevel, profileLevel);
    if (err != OK) return err;
    h263type.eProfile = static_cast<OMX_VIDEO_H263PROFILETYPE>(profileLevel.mProfile);
    h263type.eLevel = static_cast<OMX_VIDEO_H263LEVELTYPE>(profileLevel.mLevel);

    h263type.bPLUSPTYPEAllowed = OMX_FALSE;
    h263type.bForceRoundingTypeToZero = OMX_FALSE;
    h263type.nPictureHeaderRepetition = 0;
    h263type.nGOBHeaderInterval = 0;

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    h263type.eProfile = OMX_VIDEO_H263ProfileBaseline;
    h263type.eLevel =  OMX_VIDEO_H263Level70;
#endif

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
    if (!(mQuirks & kCanNotSetVideoParameters)) {
        CHECK_EQ(err, OK);
    }

    CHECK_EQ(setupBitRate(bitRate), OK);
    CHECK_EQ(setupErrorCorrectionParameters(), OK);
#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    CHECK_EQ(setupEncoderPresetParams(false), OK);

     //OMX_VIDEO_PARAM_MOTIONVECTORTYPE Settings
    OMX_VIDEO_PARAM_MOTIONVECTORTYPE MotionVector;
    OMX_VIDEO_PARAM_INTRAREFRESHTYPE RefreshParam;
    InitOMXParams(&MotionVector);
    MotionVector.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoMotionVector, &MotionVector,sizeof(MotionVector));

    MotionVector.nPortIndex = kPortIndexOutput;

    // extra parameters - hardcoded
    MotionVector.sXSearchRange = 16;
    MotionVector.sYSearchRange = 16;
    MotionVector.bFourMV =  OMX_FALSE;
    MotionVector.eAccuracy =  OMX_Video_MotionVectorHalfPel ;
    MotionVector.bUnrestrictedMVs = OMX_TRUE;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoMotionVector, &MotionVector,sizeof(MotionVector));

    //OMX_VIDEO_PARAM_INTRAREFRESHTYPE Settings
     InitOMXParams(&RefreshParam);
    RefreshParam.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam,sizeof(RefreshParam));

    // extra parameters - hardcoded based on PV defaults
    RefreshParam.nPortIndex = kPortIndexOutput;
    RefreshParam.eRefreshMode = OMX_VIDEO_IntraRefreshBoth;
    //RefreshParam.nCirMBs = 0;

    err = mOMX->setParameter(mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam,sizeof(RefreshParam));
#endif


    return OK;
}

status_t OMXCodec::setupMPEG4EncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeySampleRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
    CHECK(success);
    OMX_VIDEO_PARAM_MPEG4TYPE mpeg4type;
    InitOMXParams(&mpeg4type);
    mpeg4type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));
    CHECK_EQ(err, OK);

    mpeg4type.nSliceHeaderSpacing = 0;
    mpeg4type.bSVH = OMX_FALSE;
    mpeg4type.bGov = OMX_FALSE;

    mpeg4type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    mpeg4type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate) -1;
#else
    mpeg4type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
#endif

    if (mpeg4type.nPFrames == 0) {
        mpeg4type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }
    mpeg4type.nBFrames = 0;
    mpeg4type.nIDCVLCThreshold = 0;
    mpeg4type.bACPred = OMX_TRUE;
    mpeg4type.nMaxPacketSize = 256;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    int32_t width, height;
    success = success && meta->findInt32(kKeyWidth, &width);
    success = success && meta->findInt32(kKeyHeight, &height);
    // In order to have normal amount of resync markers(and packets) in the encoded stream,
    // we need set this value to 4096, this will reduce the number of bits used for marking
    // seperate packets in the stream and allow us to use these bits for other purposes.
    if((width >= 640) || (height >= 480)) {
    // To improve performance
        if( (frameRate > 15) && (bitRate > 2000000)) {
            mpeg4type.bACPred = OMX_FALSE;
        }
    }
#endif
    mpeg4type.nTimeIncRes = 1000;
    mpeg4type.nHeaderExtension = 0;
    mpeg4type.bReversibleVLC = OMX_FALSE;

    // Check profile and level parameters
    CodecProfileLevel defaultProfileLevel, profileLevel;
    defaultProfileLevel.mProfile = mpeg4type.eProfile;
    defaultProfileLevel.mLevel = mpeg4type.eLevel;
    err = getVideoProfileLevel(meta, defaultProfileLevel, profileLevel);
    if (err != OK) return err;
    mpeg4type.eProfile = static_cast<OMX_VIDEO_MPEG4PROFILETYPE>(profileLevel.mProfile);
    mpeg4type.eLevel = static_cast<OMX_VIDEO_MPEG4LEVELTYPE>(profileLevel.mLevel);
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    mpeg4type.eLevel = OMX_VIDEO_MPEG4Level5;
#endif

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));
    if (!(mQuirks & kCanNotSetVideoParameters)) {
        CHECK_EQ(err, OK);
    }

    CHECK_EQ(setupBitRate(bitRate), OK);
    CHECK_EQ(setupErrorCorrectionParameters(), OK);

#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
     //OMX_VIDEO_PARAM_MOTIONVECTORTYPE Settings
    OMX_VIDEO_PARAM_MOTIONVECTORTYPE MotionVector;
    OMX_VIDEO_PARAM_INTRAREFRESHTYPE RefreshParam;
    InitOMXParams(&MotionVector);
    MotionVector.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoMotionVector, &MotionVector,sizeof(MotionVector));

    MotionVector.nPortIndex = kPortIndexOutput;

    // extra parameters - hardcoded
    MotionVector.sXSearchRange = 16;
    MotionVector.sYSearchRange = 16;
    MotionVector.bFourMV =  OMX_FALSE;
    MotionVector.eAccuracy =  OMX_Video_MotionVectorHalfPel ;
    MotionVector.bUnrestrictedMVs = OMX_TRUE;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoMotionVector, &MotionVector,sizeof(MotionVector));

    //OMX_VIDEO_PARAM_INTRAREFRESHTYPE Settings
     InitOMXParams(&RefreshParam);
    RefreshParam.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam,sizeof(RefreshParam));

    // extra parameters - hardcoded based on PV defaults
    RefreshParam.nPortIndex = kPortIndexOutput;
    RefreshParam.eRefreshMode = OMX_VIDEO_IntraRefreshBoth;

    if(((width >= 640) || (height >= 480)) && (frameRate > 15) && (bitRate > 2000000))
    {
        RefreshParam.nAirRef = 0;
    }

    err = mOMX->setParameter(mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam,sizeof(RefreshParam));
#endif

    return OK;
}

status_t OMXCodec::setupAVCEncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    OMX_VIDEO_PARAM_VBSMCTYPE VbsmcType;
#endif
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeySampleRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
    CHECK(success);

    OMX_VIDEO_PARAM_AVCTYPE h264type;
    InitOMXParams(&h264type);
    h264type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
    CHECK_EQ(err, OK);

    h264type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    h264type.nSliceHeaderSpacing = 0;

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    h264type.nBFrames = OMX_NUM_B_FRAMES;
    h264type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);

#ifdef SUPPORT_B_FRAMES
    int32_t remainder = h264type.nPFrames % (OMX_NUM_B_FRAMES + 1);
    if(remainder)
    {
        LOGD("h264type.nPFrames=%d", (int) h264type.nPFrames);
        h264type.nPFrames = h264type.nPFrames - remainder;
        LOGD("adjusted to h264type.nPFrames=%d", (int) h264type.nPFrames);
    }
#endif

#else
    h264type.nBFrames = 0;   // No B frames support yet
    h264type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
#endif

    if (h264type.nPFrames == 0) {
        h264type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }

    // Check profile and level parameters
    CodecProfileLevel defaultProfileLevel, profileLevel;
    defaultProfileLevel.mProfile = h264type.eProfile;
    defaultProfileLevel.mLevel = h264type.eLevel;
    err = getVideoProfileLevel(meta, defaultProfileLevel, profileLevel);
    if (!(mQuirks & kCanNotSetVideoParameters) && err != OK) return err;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    h264type.eProfile = OMX_VIDEO_AVCProfileHigh;
    h264type.eLevel = OMX_VIDEO_AVCLevel4;
    LOGV("h264type.eProfile=%d, h264type.eLevel=%d", h264type.eProfile, h264type.eLevel);
#else
    h264type.eProfile = static_cast<OMX_VIDEO_AVCPROFILETYPE>(profileLevel.mProfile);
    h264type.eLevel = static_cast<OMX_VIDEO_AVCLEVELTYPE>(profileLevel.mLevel);
#endif

    if (h264type.eProfile == OMX_VIDEO_AVCProfileBaseline
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
       || h264type.eProfile == OMX_VIDEO_AVCProfileMain
       || h264type.eProfile == OMX_VIDEO_AVCProfileHigh
#endif
       ) {
        h264type.bUseHadamard = OMX_TRUE;
        h264type.nRefFrames = 1;
        h264type.nRefIdx10ActiveMinus1 = 0;
        h264type.nRefIdx11ActiveMinus1 = 0;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        h264type.bEntropyCodingCABAC = OMX_TRUE;
#else
        h264type.bEntropyCodingCABAC = OMX_FALSE;
#endif
        h264type.bWeightedPPrediction = OMX_FALSE;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        h264type.nWeightedBipredicitonMode = OMX_FALSE;
#endif
        h264type.bconstIpred = OMX_FALSE;
        h264type.bDirect8x8Inference = OMX_FALSE;
        h264type.bDirectSpatialTemporal = OMX_FALSE;
        h264type.nCabacInitIdc = 0;
    }

    if (h264type.nBFrames != 0) {
        h264type.nAllowedPictureTypes |= OMX_VIDEO_PictureTypeB;
    }

    h264type.bEnableUEP = OMX_FALSE;
    h264type.bEnableFMO = OMX_FALSE;
    h264type.bEnableASO = OMX_FALSE;
    h264type.bEnableRS = OMX_FALSE;
    h264type.bFrameMBsOnly = OMX_TRUE;
    h264type.bMBAFF = OMX_FALSE;
    h264type.eLoopFilterMode = OMX_VIDEO_AVCLoopFilterEnable;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
    if (!(mQuirks & kCanNotSetVideoParameters)) {
        CHECK_EQ(err, OK);
    }

    CHECK_EQ(setupBitRate(bitRate), OK);

#if defined (TARGET_OMAP4) && defined (OMAP_ENHANCEMENT)
    //OMX_IndexParamVideoMotionVector+
    OMX_VIDEO_PARAM_MOTIONVECTORTYPE MotionVector;
    InitOMXParams(&MotionVector);
    MotionVector.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(mNode, OMX_IndexParamVideoMotionVector, &MotionVector, sizeof(MotionVector));
    CHECK_EQ(err, OK);

    // extra parameters - hardcoded
    MotionVector.sXSearchRange = 16;
    MotionVector.sYSearchRange = 16;
    MotionVector.sXSearchRange = 144; // Set Horizontal search range to 144
    MotionVector.sYSearchRange = 32; // Set Vertical search range to 32
    MotionVector.bFourMV =  OMX_FALSE;
    MotionVector.eAccuracy = OMX_Video_MotionVectorQuarterPel; // hardcoded
    MotionVector.bUnrestrictedMVs = OMX_TRUE;

    err = mOMX->setParameter(mNode, OMX_IndexParamVideoMotionVector, &MotionVector, sizeof(MotionVector));
    CHECK_EQ(err, OK);

    //OMX_IndexParamVideoIntraRefresh+
    OMX_VIDEO_PARAM_INTRAREFRESHTYPE RefreshParam;
    InitOMXParams(&RefreshParam);
    RefreshParam.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam, sizeof(RefreshParam));
    CHECK_EQ(err, OK);

    // extra parameters - hardcoded
    RefreshParam.nPortIndex = kPortIndexOutput;
    RefreshParam.eRefreshMode = OMX_VIDEO_IntraRefreshMax;//OMX_VIDEO_IntraRefreshBoth;
    RefreshParam.nCirMBs = 0; //TO DO: need to confirm again.

    err = mOMX->setParameter(mNode, OMX_IndexParamVideoIntraRefresh, &RefreshParam, sizeof(RefreshParam));
    CHECK_EQ(err, OK);

    int32_t isS3DEnabled = false;
    success = success && meta->findInt32(kKeyS3dSupported, &isS3DEnabled);
    CHECK(success);

    //OMX_TI_IndexParamVideoEncoderPreset+
    CHECK_EQ(setupEncoderPresetParams(isS3DEnabled), OK);

    //OMX_TI_IndexParamVideoFrameDataContentSettings+
    if(isS3DEnabled)
        CHECK_EQ(setupEncoderFrameDataContentParams(meta), OK);

    //OMX_IndexParamVideoVBSMC+
    InitOMXParams(&VbsmcType);
    VbsmcType.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(mNode, OMX_IndexParamVideoVBSMC, &VbsmcType,sizeof(VbsmcType));
    CHECK_EQ(err, OK);

    VbsmcType.b16x16 = OMX_TRUE;
    VbsmcType.b16x8 = VbsmcType.b8x16 = VbsmcType.b8x8 = VbsmcType.b8x4 = VbsmcType.b4x8 = VbsmcType.b4x4 = OMX_FALSE;

    err =mOMX->setParameter(mNode, OMX_IndexParamVideoVBSMC, &VbsmcType,sizeof(VbsmcType));
    CHECK_EQ(err, OK);
#endif

    return OK;
}

status_t OMXCodec::setVideoOutputFormat(
        const char *mime, OMX_U32 width, OMX_U32 height) {
    CODEC_LOGV("setVideoOutputFormat width=%ld, height=%ld", width, height);

    OMX_VIDEO_CODINGTYPE compressionFormat = OMX_VIDEO_CodingUnused;
    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime)) {
        compressionFormat = OMX_VIDEO_CodingAVC;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) {
        compressionFormat = OMX_VIDEO_CodingMPEG4;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime)) {
        compressionFormat = OMX_VIDEO_CodingH263;
#if defined(OMAP_ENHANCEMENT)
#if defined(TARGET_OMAP4)
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VP6, mime)) {
        compressionFormat = (OMX_VIDEO_CODINGTYPE)OMX_VIDEO_CodingVP6;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VP7, mime)) {
        compressionFormat = (OMX_VIDEO_CODINGTYPE)OMX_VIDEO_CodingVP7;
#endif
    }
    else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV , mime))
    {
        compressionFormat = OMX_VIDEO_CodingWMV;
#endif
    } else {
        LOGE("Not a supported video mime type: %s", mime);
        CHECK(!"Should not be here. Not a supported video mime type.");
    }

    status_t err = setVideoPortFormatType(
            kPortIndexInput, compressionFormat, OMX_COLOR_FormatUnused);

    if (err != OK) {
        return err;
    }

#if 1
    {
        OMX_VIDEO_PARAM_PORTFORMATTYPE format;
        InitOMXParams(&format);
        format.nPortIndex = kPortIndexOutput;

        // For 3rd party applications we want to iterate through all the
        // supported color formats by the OMX component. If OMX codec is
        // being run in a sepparate process, then pick the second iterated
        // color format.
#if 1
        if (!strncmp(mComponentName, "OMX.qcom.7x30",13)) {
            OMX_U32 index;
	    
            for(index = 0 ;; index++){
              format.nIndex = index;
	      if(mOMX->getParameter(
			    mNode, OMX_IndexParamVideoPortFormat,
			    &format, sizeof(format)) != OK) {
		if(format.nIndex) format.nIndex--;
		break;
	      }
            }
            if(mOMXLivesLocally)
              format.nIndex = 0;
        } else
          format.nIndex = 0;
#else
        format.nIndex = 0;
#endif

        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &format, sizeof(format));
        CHECK_EQ(err, OK);
        CHECK_EQ(format.eCompressionFormat, OMX_VIDEO_CodingUnused);

        static const int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
        static const int QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka = 0x7FA30C01;
        static const int QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;

        CHECK(format.eColorFormat == OMX_COLOR_FormatYUV420Planar
               || format.eColorFormat == OMX_COLOR_FormatYUV420SemiPlanar
               || format.eColorFormat == OMX_COLOR_FormatCbYCrY
               || format.eColorFormat == OMX_QCOM_COLOR_FormatYVU420SemiPlanar
               || format.eColorFormat == QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
               || format.eColorFormat == OMX_COLOR_FormatYUV420PackedSemiPlanar
#endif
               || format.eColorFormat == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka);

        err = mOMX->setParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &format, sizeof(format));

        if (err != OK) {
            return err;
        }
    }
#endif

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    CHECK_EQ(err, OK);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {
                //update input buffer size as per resolution
                def.nBufferSize = width * height;

         if(mQuirks & OMXCodec::kThumbnailMode){
                def.nBufferCountActual = 1;
         }else{
                def.nBufferCountActual = 4;
    }
    }
#else
#if 1
    // XXX Need a (much) better heuristic to compute input buffer sizes.
    const size_t X = 64 * 1024;
    if (def.nBufferSize < X) {
        def.nBufferSize = X;
    }
#endif
#endif

    CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;

    video_def->eCompressionFormat = compressionFormat;
    video_def->eColorFormat = OMX_COLOR_FormatUnused;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    ////////////////////////////////////////////////////////////////////////////

    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);
    CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

#if 0
    def.nBufferSize =
        (((width + 15) & -16) * ((height + 15) & -16) * 3) / 2;  // YUV420
#endif

#if defined(OMAP_ENHANCEMENT)
#if defined(TARGET_OMAP4)
    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {

        OMX_CONFIG_RECTTYPE tParamStruct;
        InitOMXParams(&tParamStruct);
        tParamStruct.nPortIndex = kPortIndexOutput;

        err = mOMX->getParameter(
                mNode, (OMX_INDEXTYPE)OMX_TI_IndexParam2DBufferAllocDimension, &tParamStruct, sizeof(tParamStruct));

        CHECK_EQ(err, OK);

        video_def->nStride = tParamStruct.nWidth;
        mStride = video_def->nStride ;

        if(mQuirks & kInterlacedOutputContent){
            video_def->eColorFormat = (OMX_COLOR_FORMATTYPE) OMX_TI_COLOR_FormatYUV420PackedSemiPlanar_Sequential_TopBottom;
        }

        video_def->xFramerate = mVideoFPS << 16;
    }

    if(!(mQuirks & OMXCodec::kThumbnailMode)){
            //playback usecase. Calculate the buffer count to take display optimal buffer count into account
            //Note: this is applicable for both ducati and h/w codecs
            //def.nBufferCountMin    => minimum no of buffers required on a port (communicated by codec)
            //def.nBufferCountActual => actual no. of buffers allocated on a port (communicated to codec)
            def.nBufferCountActual = def.nBufferCountMin + (2 * NUM_BUFFERS_TO_BE_QUEUED_FOR_OPTIMAL_PERFORMANCE);
    }
#elif defined(TARGET_OMAP3)
    // Suppress output buffer count for OMAP3
    // The number of VRFB buffer, which is used for rotation, is 4.
    // Output buffer count can not exceed VRFB buffer count.
    if (def.nBufferCountActual > OUTPUT_BUFFER_COUNT)
        def.nBufferCountActual = OUTPUT_BUFFER_COUNT;
#endif
#endif
    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {

        if(mQuirks & OMXCodec::kThumbnailMode){

            LOGD("Thumbnail Mode");
            //Get the calculated min buffer count
            err = mOMX->getParameter(
                    mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
            CHECK_EQ(err, OK);
            CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

            //Thumbnail usecase. Set buffer count to optimal
            def.nBufferCountActual = def.nBufferCountMin;

            //Set the proper parameters again, as it is marshalled in Get_Parameter()
            video_def->nFrameWidth = width;
            video_def->nFrameHeight = height;

            err = mOMX->setParameter(
                    mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
            CHECK_EQ(err, OK);

#if defined (NPA_BUFFERS)
            CODEC_LOGV("Setting up the non-pre-annoucement mode");
            OMX_TI_PARAM_BUFFERPREANNOUNCE PreAnnouncement;
            InitOMXParams(&PreAnnouncement);
            PreAnnouncement.nPortIndex = def.nPortIndex;
            err = mOMX->getParameter(
                    mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamBufferPreAnnouncement, &PreAnnouncement, sizeof(PreAnnouncement));
               if (err != OMX_ErrorNone)
            {
                CODEC_LOGE("get OMX_TI_IndexParamBufferPreAnnouncement err : %x",err);
            }

            //Set the pre-annoucement to be false. i.e we will provide the buffer when we get it from camera.
            PreAnnouncement.bEnabled = OMX_FALSE;
            err = mOMX->setParameter(
                    mNode, (OMX_INDEXTYPE)OMX_TI_IndexParamBufferPreAnnouncement, &PreAnnouncement, sizeof(PreAnnouncement));
            if (err != OMX_ErrorNone)
            {
                CODEC_LOGE("set OMX_TI_IndexParamBufferPreAnnouncement err : %x",err);
            }

            mNumberOfNPABuffersSent = 0;
            mThumbnailEOSSent = 0;
#endif

        }
   }
#endif

    return err;
}

OMXCodec::OMXCodec(
        const sp<IOMX> &omx, IOMX::node_id node, uint32_t quirks,
        bool isEncoder,
        const char *mime,
        const char *componentName,
        const sp<MediaSource> &source)
    : mOMX(omx),
      mOMXLivesLocally(omx->livesLocally(getpid())),
      mNode(node),
      mQuirks(quirks),
      mIsEncoder(isEncoder),
      mMIME(strdup(mime)),
      mComponentName(strdup(componentName)),
      mSource(source),
      mCodecSpecificDataIndex(0),
      mPmemInfo(NULL),
      mState(LOADED),
      mInitialBufferSubmit(true),
      mSignalledEOS(false),
      mNoMoreOutputData(false),
      mOutputPortSettingsHaveChanged(false),
      mSeekTimeUs(-1),
      mSeekMode(ReadOptions::SEEK_CLOSEST_SYNC),
      mTargetTimeUs(-1),
      mSkipTimeUs(-1),
      mLeftOverBuffer(NULL),
      mPaused(false){
    mPortStatus[kPortIndexInput] = ENABLED;
    mPortStatus[kPortIndexOutput] = ENABLED;

    setComponentRole();
}

// static
void OMXCodec::setComponentRole(
        const sp<IOMX> &omx, IOMX::node_id node, bool isEncoder,
        const char *mime) {
    struct MimeToRole {
        const char *mime;
        const char *decoderRole;
        const char *encoderRole;
    };

    static const MimeToRole kMimeToRole[] = {
        { MEDIA_MIMETYPE_AUDIO_MPEG,
            "audio_decoder.mp3", "audio_encoder.mp3" },
        { MEDIA_MIMETYPE_AUDIO_AMR_NB,
            "audio_decoder.amrnb", "audio_encoder.amrnb" },
        { MEDIA_MIMETYPE_AUDIO_AMR_WB,
            "audio_decoder.amrwb", "audio_encoder.amrwb" },
        { MEDIA_MIMETYPE_AUDIO_AAC,
            "audio_decoder.aac", "audio_encoder.aac" },
        { MEDIA_MIMETYPE_VIDEO_AVC,
            "video_decoder.avc", "video_encoder.avc" },
        { MEDIA_MIMETYPE_VIDEO_MPEG4,
            "video_decoder.mpeg4", "video_encoder.mpeg4" },
        { MEDIA_MIMETYPE_VIDEO_H263,
            "video_decoder.h263", "video_encoder.h263" },
#if defined(OMAP_ENHANCEMENT)
#if defined(TARGET_OMAP4)
        { MEDIA_MIMETYPE_VIDEO_VP6,
            "video_decoder.vp6", NULL },
        { MEDIA_MIMETYPE_VIDEO_VP7,
            "video_decoder.vp7", NULL },
#endif
        { MEDIA_MIMETYPE_VIDEO_WMV,
            "video_decoder.wmv", "video_encoder.wmv" },
        { MEDIA_MIMETYPE_AUDIO_WMA,
            "audio_decoder.wma", "audio_encoder.wma" },
        { MEDIA_MIMETYPE_AUDIO_WMAPRO,
            "audio_decoder.wmapro", "audio_encoder.wmapro" },
        { MEDIA_MIMETYPE_AUDIO_WMALSL,
            "audio_decoder.wmalsl", "audio_encoder.wmalsl" },
#endif
    };

    static const size_t kNumMimeToRole =
        sizeof(kMimeToRole) / sizeof(kMimeToRole[0]);

    size_t i;
    for (i = 0; i < kNumMimeToRole; ++i) {
        if (!strcasecmp(mime, kMimeToRole[i].mime)) {
            break;
        }
    }

    if (i == kNumMimeToRole) {
        return;
    }

    const char *role =
        isEncoder ? kMimeToRole[i].encoderRole
                  : kMimeToRole[i].decoderRole;

    if (role != NULL) {
        OMX_PARAM_COMPONENTROLETYPE roleParams;
        InitOMXParams(&roleParams);

        strncpy((char *)roleParams.cRole,
                role, OMX_MAX_STRINGNAME_SIZE - 1);

        roleParams.cRole[OMX_MAX_STRINGNAME_SIZE - 1] = '\0';

        status_t err = omx->setParameter(
                node, OMX_IndexParamStandardComponentRole,
                &roleParams, sizeof(roleParams));

        if (err != OK) {
            LOGW("Failed to set standard component role '%s'.", role);
        }
    }
}

void OMXCodec::setComponentRole() {
    setComponentRole(mOMX, mNode, mIsEncoder, mMIME);
}

OMXCodec::~OMXCodec() {
    mSource.clear();

    CHECK(mState == LOADED || mState == ERROR);

    status_t err = mOMX->freeNode(mNode);
    CHECK_EQ(err, OK);

    mNode = NULL;
    setState(DEAD);

    clearCodecSpecificData();

    free(mComponentName);
    mComponentName = NULL;

    free(mMIME);
    mMIME = NULL;
}

status_t OMXCodec::init() {
    // mLock is held.

    CHECK_EQ(mState, LOADED);

    status_t err;
    if (!(mQuirks & kRequiresLoadedToIdleAfterAllocation)) {
        err = mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
        CHECK_EQ(err, OK);
        setState(LOADED_TO_IDLE);
    }

    err = allocateBuffers();
    CHECK_EQ(err, OK);

    if (mQuirks & kRequiresLoadedToIdleAfterAllocation) {
        err = mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
        CHECK_EQ(err, OK);

        setState(LOADED_TO_IDLE);
    }

    while (mState != EXECUTING && mState != ERROR) {
        mAsyncCompletion.wait(mLock);
    }

    return mState == ERROR ? UNKNOWN_ERROR : OK;
}

// static
bool OMXCodec::isIntermediateState(State state) {
    return state == LOADED_TO_IDLE
        || state == IDLE_TO_EXECUTING
        || state == EXECUTING_TO_IDLE
        || state == IDLE_TO_LOADED
        || state == RECONFIGURING;
}

status_t OMXCodec::allocateBuffers() {
    status_t err = allocateBuffersOnPort(kPortIndexInput);

    if (err != OK) {
        return err;
    }

    return allocateBuffersOnPort(kPortIndexOutput);
}

status_t OMXCodec::allocateBuffersOnPort(OMX_U32 portIndex) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

    CODEC_LOGI("allocating %lu buffers of size %lu on %s port",
            def.nBufferCountActual, def.nBufferSize,
            portIndex == kPortIndexInput ? "input" : "output");

    size_t totalSize = def.nBufferCountActual * ((def.nBufferSize + 31) & (~31));
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4) && defined (NPA_BUFFERS)
            if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER") &&
                (mQuirks & OMXCodec::kThumbnailMode) &&
                (portIndex == kPortIndexOutput)){
            totalSize = (THUMBNAIL_BUFFERS_NPA_MODE * def.nBufferSize)
                        + ((def.nBufferCountActual - THUMBNAIL_BUFFERS_NPA_MODE) * NPA_BUFFER_SIZE);
            }
#endif

#ifdef OMAP_ENHANCEMENT
    bool useExternallyAllocatedBuffers = false;

    if ((!strcmp(mComponentName, "OMX.TI.Video.Decoder") ||
         !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER") ||
         !strcmp(mComponentName, "OMX.TI.720P.Decoder")) &&
        (portIndex == kPortIndexOutput) &&
        (mExtBufferAddresses.size() == def.nBufferCountActual)
#if defined(TARGET_OMAP4)
        && !(mQuirks & OMXCodec::kThumbnailMode)
#endif
        ){

        // One must use overlay buffers for TI video decoder output port.
        // So, do not allocate memory here.
        useExternallyAllocatedBuffers = true;
    }
    else{
        mDealer[portIndex] = new MemoryDealer(totalSize, "OMXCodec");
    }

    sp<IMemory> mem;
#else
    mDealer[portIndex] = new MemoryDealer(totalSize, "OMXCodec");
#endif

#ifdef USE_GETBUFFERINFO
    sp<IMemoryHeap> pFrameHeap = NULL;
    size_t alignedSize = 0;
    size_t size = 0;
    if (mIsEncoder && (portIndex == kPortIndexInput) &&
            (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
        sp<IMemory> pFrame = NULL;
        ssize_t offset = 0;
        size_t nBuffers = 0;

        mSource->getBufferInfo(pFrame, &alignedSize);
        if (pFrame == NULL)
            LOGE("pFrame==NULL");

        pFrameHeap = pFrame->getMemory(&offset, &size);
        nBuffers = pFrameHeap->getSize( )/alignedSize;

        //update OMX with new buffer count.
        if( nBuffers < def.nBufferCountMin || size < def.nBufferSize ) {
            LOGE("Buffer count/size less than minimum required");
            return UNKNOWN_ERROR;
        }

        if (nBuffers < def.nBufferCountActual) {
            status_t err = mOMX->setParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

            if (err != OK) {
                LOGE("Updating buffer count failed");
                return err;
            }
        }
    }
#endif

    for (OMX_U32 i = 0; i < def.nBufferCountActual; ++i) {
#ifdef OMAP_ENHANCEMENT
        if (useExternallyAllocatedBuffers){
            mem = mExtBufferAddresses[i];
        }
        else{

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4) && defined (NPA_BUFFERS)
            if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER") &&
                (mQuirks & OMXCodec::kThumbnailMode) &&
                (portIndex == kPortIndexOutput) &&
                (i>=THUMBNAIL_BUFFERS_NPA_MODE))
                mem = mDealer[portIndex]->allocate(NPA_BUFFER_SIZE);
            else
#endif
            {
                if(portIndex == kPortIndexOutput)
                {
                    def.nBufferSize = ((def.nBufferSize + CACHELINE_BOUNDARY_MEMALIGNMENT - 1) &
                      ~(CACHELINE_BOUNDARY_MEMALIGNMENT - 1));
                }
                mem = mDealer[portIndex]->allocate(def.nBufferSize);
            }
        }
#else
        sp<IMemory> mem = mDealer[portIndex]->allocate(def.nBufferSize);
#endif
        CHECK(mem.get() != NULL);

        BufferInfo info;
        info.mData = NULL;
        info.mSize = def.nBufferSize;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        //Update Output buffers range as per 2D buffer requirement.
        //Doubling size to contain (nFilledLen + nOffset) check. Extra range is harmless here.
        if (!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")){
            if(useExternallyAllocatedBuffers)
            {
                OMX_VIDEO_PORTDEFINITIONTYPE *videoDef = &def.format.video;
                int32_t padded_height;

                if (!(mOutputFormat->findInt32(kKeyPaddedHeight, &padded_height))) {
                    padded_height =  videoDef->nFrameHeight;
                }

                info.mSize = ARM_4K_PAGE_SIZE * padded_height * 2;
            }
        }
#endif

        IOMX::buffer_id buffer;
        if (portIndex == kPortIndexInput
                && (mQuirks & kRequiresAllocateBufferOnInputPorts)) {
            if (mOMXLivesLocally) {
                mem.clear();

                err = mOMX->allocateBuffer(
                        mNode, portIndex, def.nBufferSize, &buffer,
                        &info.mData);
            } else {
                err = mOMX->allocateBufferWithBackup(
                        mNode, portIndex, mem, &buffer);
            }
        } else if (portIndex == kPortIndexOutput
                && (mQuirks & kRequiresAllocateBufferOnOutputPorts)) {
            if (mOMXLivesLocally || (mQuirks & kDoesNotRequireMemcpyOnOutputPort)) {
                mem.clear();

                err = mOMX->allocateBuffer(
                        mNode, portIndex, def.nBufferSize, &buffer,
                        &info.mData);
            } else {
                err = mOMX->allocateBufferWithBackup(
                        mNode, portIndex, mem, &buffer);
            }
        } else {
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
                if((!strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
                                        || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E"))
                                && (portIndex == kPortIndexInput)
                                && !(mQuirks & kRequiresAllocateBufferOnInputPorts) ){
                        sp<IMemory> tempmem = mem;
                        tempmem.clear();
                        err = mOMX->useBuffer(mNode, portIndex, tempmem, &buffer, mem->size());
                } else {
                        err = mOMX->useBuffer(mNode, portIndex, mem, &buffer, mem->size());
                }
#elif defined(USE_GETBUFFERINFO)
            if(pFrameHeap != NULL && mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
                ssize_t temp_offset = i * alignedSize;
                size_t temp_size = size;
                sp<IMemory> pFrame = NULL;
                sp<MemoryBase> pFrameBase = new MemoryBase(pFrameHeap, temp_offset, temp_size);
                pFrame = pFrameBase;
                LOGI("getParametersSync: pmem_fd = %d, base = %p, temp_offset %d,"
                    " temp_size %d, alignedSize = %d, pointer %p, Total Size = %d", pFrameHeap->getHeapID(), pFrameHeap->base(), temp_offset,
                    temp_size, alignedSize, pFrame->pointer(), pFrameHeap->getSize());
                err = mOMX->useBuffer(mNode, portIndex, pFrame, &buffer);
            } else
#else
            {
                err = mOMX->useBuffer(mNode, portIndex, mem, &buffer);
            }
#endif
        }

        if (err != OK) {
            LOGE("allocate_buffer_with_backup failed");
            return err;
        }

        if (mem != NULL) {
            info.mData = mem->pointer();
        }

        info.mBuffer = buffer;
        info.mOwnedByComponent = false;
#ifdef OMAP_ENHANCEMENT
        info.mOwnedByPlayer = false;
#endif
        info.mMem = mem;
        info.mMediaBuffer = NULL;

        if (portIndex == kPortIndexOutput) {
            if (!((mOMXLivesLocally || (mQuirks & kDoesNotRequireMemcpyOnOutputPort))
                        && (mQuirks & kRequiresAllocateBufferOnOutputPorts)
                        && (mQuirks & kDefersOutputBufferAllocation))) {
                // If the node does not fill in the buffer ptr at this time,
                // we will defer creating the MediaBuffer until receiving
                // the first FILL_BUFFER_DONE notification instead.
                info.mMediaBuffer = new MediaBuffer(info.mData, info.mSize);
                info.mMediaBuffer->setObserver(this);
            }
        }

        mPortBuffers[portIndex].push(info);

        CODEC_LOGV("allocated buffer %p on %s port", buffer,
             portIndex == kPortIndexInput ? "input" : "output");
    }

    // dumpPortStatus(portIndex);

    return OK;
}

void OMXCodec::on_message(const omx_message &msg) {
    switch (msg.type) {
        case omx_message::EVENT:
        {
            onEvent(
                 msg.u.event_data.event, msg.u.event_data.data1,
                 msg.u.event_data.data2);

            break;
        }

        case omx_message::EMPTY_BUFFER_DONE:
        {
            IOMX::buffer_id buffer = msg.u.extended_buffer_data.buffer;

            CODEC_LOGV("EMPTY_BUFFER_DONE(buffer: %p)", buffer);

            Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
            size_t i = 0;
            while (i < buffers->size() && (*buffers)[i].mBuffer != buffer) {
                ++i;
            }

            CHECK(i < buffers->size());
            if (!(*buffers)[i].mOwnedByComponent) {
                LOGW("We already own input buffer %p, yet received "
                     "an EMPTY_BUFFER_DONE.", buffer);
            }

            {
                BufferInfo *info = &buffers->editItemAt(i);
                info->mOwnedByComponent = false;
                if (info->mMediaBuffer != NULL) {
                    // It is time to release the media buffers storing meta data
                    info->mMediaBuffer->release();
                    info->mMediaBuffer = NULL;
                }
            }

            if (mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames) && (NULL != (*buffers)[i].mMediaBuffer)) {
                CODEC_LOGV("EBD: %x %d", (*buffers)[i].mMediaBuffer, (*buffers)[i].mMediaBuffer->refcount() );
                (*buffers)[i].mMediaBuffer->release();
                buffers->editItemAt(i).mMediaBuffer = NULL;
            }

            if (mPortStatus[kPortIndexInput] == DISABLING) {
                CODEC_LOGV("Port is disabled, freeing buffer %p", buffer);

                status_t err =
                    mOMX->freeBuffer(mNode, kPortIndexInput, buffer);
                CHECK_EQ(err, OK);

                buffers->removeAt(i);
            } else if (mState != ERROR
                    && mPortStatus[kPortIndexInput] != SHUTTING_DOWN) {
                CHECK_EQ(mPortStatus[kPortIndexInput], ENABLED);
                drainInputBuffer(&buffers->editItemAt(i));
            } else if ((mQuirks & kRequiresFlushBeforeShutdown) && mState == EXECUTING_TO_IDLE && mPortStatus[kPortIndexInput] == SHUTTING_DOWN) {
                if (countBuffersWeOwn(mPortBuffers[kPortIndexInput]) == mPortBuffers[kPortIndexInput].size()
                    && countBuffersWeOwn(mPortBuffers[kPortIndexOutput]) == mPortBuffers[kPortIndexOutput].size()) {
                    CODEC_LOGV("Finished emptying both ports, now completing "
                         "transition from EXECUTING to IDLE.");

                    status_t err =
                        mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                    CHECK_EQ(err, OK);
                } else {
                    LOGV("own %d/%d input and %d/%d output", countBuffersWeOwn(mPortBuffers[kPortIndexInput]), mPortBuffers[kPortIndexInput].size(),
                        countBuffersWeOwn(mPortBuffers[kPortIndexOutput]), mPortBuffers[kPortIndexOutput].size());
                }
            }
            break;
        }

        case omx_message::FILL_BUFFER_DONE:
        {
            IOMX::buffer_id buffer = msg.u.extended_buffer_data.buffer;
            OMX_U32 flags = msg.u.extended_buffer_data.flags;

            CODEC_LOGV("FILL_BUFFER_DONE(buffer: %p, size: %ld, flags: 0x%08lx, timestamp: %lld us (%.2f secs))",
                 buffer,
                 msg.u.extended_buffer_data.range_length,
                 flags,
                 msg.u.extended_buffer_data.timestamp,
                 msg.u.extended_buffer_data.timestamp / 1E6);

            Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
            size_t i = 0;
            while (i < buffers->size() && (*buffers)[i].mBuffer != buffer) {
                ++i;
            }

            CHECK(i < buffers->size());
            BufferInfo *info = &buffers->editItemAt(i);

            if (!info->mOwnedByComponent) {
                LOGW("We already own output buffer %p, yet received "
                     "a FILL_BUFFER_DONE.", buffer);
            }

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4) && defined (NPA_BUFFERS)
        if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER") &&
            (mQuirks & OMXCodec::kThumbnailMode) ){
            if (mNumberOfNPABuffersSent > 0)
                mNumberOfNPABuffersSent--;
        }
#endif
            info->mOwnedByComponent = false;

            if (mPortStatus[kPortIndexOutput] == DISABLING) {
                CODEC_LOGV("Port is disabled, freeing buffer %p", buffer);

                status_t err =
                    mOMX->freeBuffer(mNode, kPortIndexOutput, buffer);
                CHECK_EQ(err, OK);

                buffers->removeAt(i);
#if 0
            } else if (mPortStatus[kPortIndexOutput] == ENABLED
                       && (flags & OMX_BUFFERFLAG_EOS)) {
                CODEC_LOGV("No more output data.");
                mNoMoreOutputData = true;
                mBufferFilled.signal();
#endif
            } else if (mPortStatus[kPortIndexOutput] != SHUTTING_DOWN) {
                CHECK_EQ(mPortStatus[kPortIndexOutput], ENABLED);

                if (info->mMediaBuffer == NULL) {
                    CHECK(mOMXLivesLocally || (mQuirks & kDoesNotRequireMemcpyOnOutputPort));
                    CHECK(mQuirks & kRequiresAllocateBufferOnOutputPorts);
                    CHECK(mQuirks & kDefersOutputBufferAllocation);

                    // The qcom video decoders on Nexus don't actually allocate
                    // output buffer memory on a call to OMX_AllocateBuffer
                    // the "pBuffer" member of the OMX_BUFFERHEADERTYPE
                    // structure is only filled in later.

                    info->mMediaBuffer = new MediaBuffer(
                            msg.u.extended_buffer_data.data_ptr,
                            info->mSize);
                    info->mMediaBuffer->setObserver(this);
                }

                MediaBuffer *buffer = info->mMediaBuffer;

                if (msg.u.extended_buffer_data.range_offset
                        + msg.u.extended_buffer_data.range_length
                            > buffer->size()) {
                    CODEC_LOGE(
                            "Codec lied about its buffer size requirements, "
                            "sending a buffer larger than the originally "
                            "advertised size in FILL_BUFFER_DONE!");
		}
                
                if(!mOMXLivesLocally && mPmemInfo != NULL && buffer != NULL) {
                    OMX_U8* base = (OMX_U8*)mPmemInfo->getBase();
                    OMX_U8* data = base + msg.u.extended_buffer_data.pmem_offset;
                    buffer->setData(data);
                }

                buffer->set_range(
                        msg.u.extended_buffer_data.range_offset,
                        msg.u.extended_buffer_data.range_length);

                buffer->meta_data()->clear();

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
                buffer->meta_data()->setInt32(kKeyStride, mStride);
#endif
                buffer->meta_data()->setInt64(
                        kKeyTime, msg.u.extended_buffer_data.timestamp);

                if (msg.u.extended_buffer_data.flags & OMX_BUFFERFLAG_SYNCFRAME) {
                    buffer->meta_data()->setInt32(kKeyIsSyncFrame, true);
                }
                if (msg.u.extended_buffer_data.flags & OMX_BUFFERFLAG_CODECCONFIG) {
                    buffer->meta_data()->setInt32(kKeyIsCodecConfig, true);
                }

#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
                if (msg.u.extended_buffer_data.flags & OMX_TI_BUFFERFLAG_DETACHEDEXTRADATA)
                    buffer->meta_data()->setInt32(kKeyIsExtraData, true);
#endif

                if (mQuirks & kOutputBuffersAreUnreadable) {
                    buffer->meta_data()->setInt32(kKeyIsUnreadable, true);
                }

                buffer->meta_data()->setPointer(
                        kKeyPlatformPrivate,
                        msg.u.extended_buffer_data.platform_private);

                buffer->meta_data()->setPointer(
                        kKeyBufferID,
                        msg.u.extended_buffer_data.buffer);

                if (msg.u.extended_buffer_data.flags & OMX_BUFFERFLAG_EOS) {
                    CODEC_LOGV("No more output data.");
                    mNoMoreOutputData = true;
                }
                if (mTargetTimeUs >= 0) {
                    CHECK(msg.u.extended_buffer_data.timestamp <= mTargetTimeUs);

                    if (msg.u.extended_buffer_data.timestamp < mTargetTimeUs) {
                        CODEC_LOGV(
                                "skipping output buffer at timestamp %lld us",
                                msg.u.extended_buffer_data.timestamp);

                        fillOutputBuffer(info);
                        break;
                    }

                    CODEC_LOGV(
                            "returning output buffer at target timestamp "
                            "%lld us",
                            msg.u.extended_buffer_data.timestamp);

                    mTargetTimeUs = -1;
                }

                mFilledBuffers.push_back(i);
                mBufferFilled.signal();
            } else if ((mQuirks & kRequiresFlushBeforeShutdown) && mState == EXECUTING_TO_IDLE) {
                if (countBuffersWeOwn(mPortBuffers[kPortIndexInput]) == mPortBuffers[kPortIndexInput].size()
                    && countBuffersWeOwn(mPortBuffers[kPortIndexOutput]) == mPortBuffers[kPortIndexOutput].size()) {
                    CODEC_LOGV("Finished flushing both ports, now completing "
                         "transition from EXECUTING to IDLE.");

                    status_t err =
                        mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                    CHECK_EQ(err, OK);
                } else {
                    LOGV("own %d/%d input and %d/%d output", countBuffersWeOwn(mPortBuffers[kPortIndexInput]), mPortBuffers[kPortIndexInput].size(),
                        countBuffersWeOwn(mPortBuffers[kPortIndexOutput]), mPortBuffers[kPortIndexOutput].size());
                }
            }

            break;
        }

        default:
        {
            CHECK(!"should not be here.");
            break;
        }
    }
}
void OMXCodec::registerBuffers(const sp<IMemoryHeap> &mem) {
    mPmemInfo = mem;
}
void OMXCodec::onEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            onCmdComplete((OMX_COMMANDTYPE)data1, data2);
            break;
        }

        case OMX_EventError:
        {
            if (data1 && (OMX_S32)data1 == OMX_ErrorSameState) {
                /* Don't raise fatal errors for samestate changes */
                break;
            }
            CODEC_LOGE("ERROR(0x%08lx, %ld)", data1, data2);

            setState(ERROR);
            break;
        }

        case OMX_EventPortSettingsChanged:
        {
#ifndef OMAP_ENHANCEMENT
            if(mState == EXECUTING)
                CODEC_LOGV("OMX_EventPortSettingsChanged(port=%ld, data2=0x%08lx)",
                       data1, data2);

                if (data2 == 0 || data2 == OMX_IndexParamPortDefinition) {
                    onPortSettingsChanged(data1);
                } else if (data1 == kPortIndexOutput
                        && data2 == OMX_IndexConfigCommonOutputCrop) {
                    // todo: handle crop rect
                }
            else
              LOGE("Ignore PortSettingsChanged event \n");
#else
            onPortSettingsChanged(data1);
#endif
            break;
        }

#if 0
        case OMX_EventBufferFlag:
        {
            CODEC_LOGV("EVENT_BUFFER_FLAG(%ld)", data1);

            if (data1 == kPortIndexOutput) {
                mNoMoreOutputData = true;
            }
            break;
        }
#endif

        default:
        {
            CODEC_LOGV("EVENT(%d, %ld, %ld)", event, data1, data2);
            break;
        }
    }
}

// Has the format changed in any way that the client would have to be aware of?
static bool formatHasNotablyChanged(
        const sp<MetaData> &from, const sp<MetaData> &to) {
    if (from.get() == NULL && to.get() == NULL) {
        return false;
    }

    if ((from.get() == NULL && to.get() != NULL)
        || (from.get() != NULL && to.get() == NULL)) {
        return true;
    }

    const char *mime_from, *mime_to;
    CHECK(from->findCString(kKeyMIMEType, &mime_from));
    CHECK(to->findCString(kKeyMIMEType, &mime_to));

    if (strcasecmp(mime_from, mime_to)) {
        return true;
    }

    if (!strcasecmp(mime_from, MEDIA_MIMETYPE_VIDEO_RAW)) {
        int32_t colorFormat_from, colorFormat_to;
        CHECK(from->findInt32(kKeyColorFormat, &colorFormat_from));
        CHECK(to->findInt32(kKeyColorFormat, &colorFormat_to));

        if (colorFormat_from != colorFormat_to) {
            return true;
        }

        int32_t width_from, width_to;
        CHECK(from->findInt32(kKeyWidth, &width_from));
        CHECK(to->findInt32(kKeyWidth, &width_to));

        if (width_from != width_to) {
            return true;
        }

        int32_t height_from, height_to;
        CHECK(from->findInt32(kKeyHeight, &height_from));
        CHECK(to->findInt32(kKeyHeight, &height_to));

        if (height_from != height_to) {
            return true;
        }
    } else if (!strcasecmp(mime_from, MEDIA_MIMETYPE_AUDIO_RAW)) {
        int32_t numChannels_from, numChannels_to;
        CHECK(from->findInt32(kKeyChannelCount, &numChannels_from));
        CHECK(to->findInt32(kKeyChannelCount, &numChannels_to));

        if (numChannels_from != numChannels_to) {
            return true;
        }

        int32_t sampleRate_from, sampleRate_to;
        CHECK(from->findInt32(kKeySampleRate, &sampleRate_from));
        CHECK(to->findInt32(kKeySampleRate, &sampleRate_to));

        if (sampleRate_from != sampleRate_to) {
            return true;
        }
    }

    return false;
}

void OMXCodec::onCmdComplete(OMX_COMMANDTYPE cmd, OMX_U32 data) {
    switch (cmd) {
        case OMX_CommandStateSet:
        {
            onStateChange((OMX_STATETYPE)data);
            break;
        }

        case OMX_CommandPortDisable:
        {
            OMX_U32 portIndex = data;
            CODEC_LOGV("PORT_DISABLED(%ld)", portIndex);

            CHECK(mState == EXECUTING || mState == RECONFIGURING);
            CHECK_EQ(mPortStatus[portIndex], DISABLING);
            CHECK_EQ(mPortBuffers[portIndex].size(), 0);

            mPortStatus[portIndex] = DISABLED;

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, kPortIndexOutput);

                sp<MetaData> oldOutputFormat = mOutputFormat;
                if (strncmp(mComponentName, "OMX.Nvidia.h26",14)) {
                    initOutputFormat(mSource->getFormat());
                }

                // Don't notify clients if the output port settings change
                // wasn't of importance to them, i.e. it may be that just the
                // number of buffers has changed and nothing else.
                mOutputPortSettingsHaveChanged =
                    formatHasNotablyChanged(oldOutputFormat, mOutputFormat);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
                if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")){
                    if (mExtBufferAddresses.size() == 0) /* Any Ducati codec non-overlay usecase */
                    {
                        LOGD("OMX_CommandPortDisable Done. Reenabling port for non-overlay playback usecase");

                        /*Since we are mostly dealing 1D buffers here,
                          nStride has to be updated as nFrameWidth will change on portreconfig
                          */
                        OMX_PARAM_PORTDEFINITIONTYPE def;
                        InitOMXParams(&def);
                        def.nPortIndex = kPortIndexOutput;

                        status_t err = mOMX->getParameter(
                              mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
                        CHECK_EQ(err, OK);

                        OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

                        int32_t padded_width;
                        CHECK(mOutputFormat->findInt32(kKeyPaddedWidth, &padded_width));
                        video_def->nStride = padded_width;
                        mStride = video_def->nStride;

                        LOGE("Updating video_def->nStride with new width %d", (int) video_def->nStride);

                        err = mOMX->setParameter(
                                     mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));


                        enablePortAsync(portIndex);

                        err = allocateBuffersOnPort(portIndex);
                        CHECK_EQ(err, OK);
                   }else
                   {
                      LOGD("OMX_CommandPortDisable Done. Not enabling port till overlay buffers are available");
                   }
                } else
                {
                enablePortAsync(portIndex);

                status_t err = allocateBuffersOnPort(portIndex);
                CHECK_EQ(err, OK);
                }
#else
                enablePortAsync(portIndex);

                status_t err = allocateBuffersOnPort(portIndex);
                CHECK_EQ(err, OK);
#endif
            }
            break;
        }

        case OMX_CommandPortEnable:
        {
            OMX_U32 portIndex = data;
            CODEC_LOGV("PORT_ENABLED(%ld)", portIndex);

            CHECK(mState == EXECUTING || mState == RECONFIGURING);
            CHECK_EQ(mPortStatus[portIndex], ENABLING);

            mPortStatus[portIndex] = ENABLED;

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, kPortIndexOutput);

                setState(EXECUTING);

                fillOutputBuffers();
            }
            break;
        }

        case OMX_CommandFlush:
        {
            OMX_U32 portIndex = data;

            CODEC_LOGV("FLUSH_DONE(%ld)", portIndex);

            CHECK_EQ(mPortStatus[portIndex], SHUTTING_DOWN);

            mPortStatus[portIndex] = ENABLED;

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, kPortIndexOutput);

                disablePortAsync(portIndex);
            } else if (mState == EXECUTING_TO_IDLE) {
                mPortStatus[portIndex] = SHUTTING_DOWN;

                CHECK_EQ(mPortStatus[kPortIndexInput], SHUTTING_DOWN);
                CHECK_EQ(mPortStatus[kPortIndexOutput], SHUTTING_DOWN);

                if (countBuffersWeOwn(mPortBuffers[kPortIndexInput]) == mPortBuffers[kPortIndexInput].size()
                    && countBuffersWeOwn(mPortBuffers[kPortIndexOutput]) == mPortBuffers[kPortIndexOutput].size()) {
                    CODEC_LOGV("Finished flushing both ports, now completing "
                         "transition from EXECUTING to IDLE.");

                    status_t err =
                        mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                    CHECK_EQ(err, OK);
                }
            } else {
                // We're flushing both ports in preparation for seeking.

                if (mPortStatus[kPortIndexInput] == ENABLED
                    && mPortStatus[kPortIndexOutput] == ENABLED) {
                    CODEC_LOGV("Finished flushing both ports, now continuing from"
                         " seek-time.");

                    // We implicitly resume pulling on our upstream source.
                    mPaused = false;

                    drainInputBuffers();
                    fillOutputBuffers();
                }
            }

            break;
        }

        default:
        {
            CODEC_LOGV("CMD_COMPLETE(%d, %ld)", cmd, data);
            break;
        }
    }
}

void OMXCodec::onStateChange(OMX_STATETYPE newState) {
    CODEC_LOGV("onStateChange %d", newState);

    switch (newState) {
        case OMX_StateIdle:
        {
            CODEC_LOGV("Now Idle.");
            if (mState == LOADED_TO_IDLE) {
                status_t err = mOMX->sendCommand(
                        mNode, OMX_CommandStateSet, OMX_StateExecuting);

                CHECK_EQ(err, OK);

                setState(IDLE_TO_EXECUTING);
            } else {
                CHECK_EQ(mState, EXECUTING_TO_IDLE);

                CHECK_EQ(
                    countBuffersWeOwn(mPortBuffers[kPortIndexInput]),
                    mPortBuffers[kPortIndexInput].size());

                CHECK_EQ(
                    countBuffersWeOwn(mPortBuffers[kPortIndexOutput]),
                    mPortBuffers[kPortIndexOutput].size());

                status_t err = mOMX->sendCommand(
                        mNode, OMX_CommandStateSet, OMX_StateLoaded);

                CHECK_EQ(err, OK);

                err = freeBuffersOnPort(kPortIndexInput);
                CHECK_EQ(err, OK);

                err = freeBuffersOnPort(kPortIndexOutput);
                CHECK_EQ(err, OK);

                mPortStatus[kPortIndexInput] = ENABLED;
                mPortStatus[kPortIndexOutput] = ENABLED;

                setState(IDLE_TO_LOADED);
            }
            break;
        }

        case OMX_StateExecuting:
        {
            CHECK_EQ(mState, IDLE_TO_EXECUTING);

            CODEC_LOGV("Now Executing.");

            setState(EXECUTING);

            // Buffers will be submitted to the component in the first
            // call to OMXCodec::read as mInitialBufferSubmit is true at
            // this point. This ensures that this on_message call returns,
            // releases the lock and ::init can notice the state change and
            // itself return.
            break;
        }

        case OMX_StateLoaded:
        {
#ifdef OMAP_ENHANCEMENT
            if(LOADED == mState)
            {
                break;
            }
#endif
            CHECK_EQ(mState, IDLE_TO_LOADED);

            CODEC_LOGV("Now Loaded.");

            setState(LOADED);
            break;
        }

        case OMX_StateInvalid:
        {
            setState(ERROR);
            break;
        }

        default:
        {
            CHECK(!"should not be here.");
            break;
        }
    }
}

// static
size_t OMXCodec::countBuffersWeOwn(const Vector<BufferInfo> &buffers) {
    size_t n = 0;
    for (size_t i = 0; i < buffers.size(); ++i) {
        if (!buffers[i].mOwnedByComponent) {
            ++n;
        }
    }

    return n;
}

status_t OMXCodec::freeBuffersOnPort(
        OMX_U32 portIndex, bool onlyThoseWeOwn) {
    Vector<BufferInfo> *buffers = &mPortBuffers[portIndex];

    status_t stickyErr = OK;

    for (size_t i = buffers->size(); i-- > 0;) {
        BufferInfo *info = &buffers->editItemAt(i);

#ifdef OMAP_ENHANCEMENT
        if (onlyThoseWeOwn && (info->mOwnedByComponent || info->mOwnedByPlayer) ) {
#else
        if (onlyThoseWeOwn && info->mOwnedByComponent ) {
#endif
            continue;
        }

        CHECK_EQ(info->mOwnedByComponent, false);

        CODEC_LOGV("freeing buffer %p on port %ld", info->mBuffer, portIndex);

        status_t err =
            mOMX->freeBuffer(mNode, portIndex, info->mBuffer);

        if (err != OK) {
            stickyErr = err;
        }

        if (info->mMediaBuffer != NULL) {
            info->mMediaBuffer->setObserver(NULL);

            // Make sure nobody but us owns this buffer at this point.
            CHECK_EQ(info->mMediaBuffer->refcount(), 0);

            info->mMediaBuffer->release();
            info->mMediaBuffer = NULL;
        }

        buffers->removeAt(i);
    }

    CHECK(onlyThoseWeOwn || buffers->isEmpty());

    return stickyErr;
}

void OMXCodec::onPortSettingsChanged(OMX_U32 portIndex) {
    CODEC_LOGV("PORT_SETTINGS_CHANGED(%ld)", portIndex);

    CHECK_EQ(mState, EXECUTING);
    CHECK_EQ(portIndex, kPortIndexOutput);
    setState(RECONFIGURING);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    LOGD("[%s] PORT_SETTINGS_CHANGED(%d)",mComponentName, (int)portIndex);

#if defined (NPA_BUFFERS)
    //reset NPA buffer counter on port reconfig.
    mNumberOfNPABuffersSent = 0;
	mThumbnailEOSSent = 0;
#endif

    if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER"))
    {
        /* update new port settings, since renderer needs new WxH for new buffers */
        initOutputFormat(mSource->getFormat());

        /* For thumbnail color conversion routine will require unpadded display WxH
           update it in video-source meta data here, to be used later during color conversion */
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ(err, OK);
        OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;
        mSource->getFormat()->setInt32(kKeyWidth, video_def->nFrameWidth);
        mSource->getFormat()->setInt32(kKeyHeight, video_def->nFrameHeight);
        LOGI("Updated source WxH %dx%d", (int) video_def->nFrameWidth, (int) video_def->nFrameHeight);
    }
#endif
    if (mQuirks & kNeedsFlushBeforeDisable) {
        if (!flushPortAsync(portIndex)) {
            onCmdComplete(OMX_CommandFlush, portIndex);
        }
    } else {
        disablePortAsync(portIndex);
    }
}

bool OMXCodec::flushPortAsync(OMX_U32 portIndex) {
    CHECK(mState == EXECUTING || mState == RECONFIGURING
            || mState == EXECUTING_TO_IDLE);

    CODEC_LOGV("flushPortAsync(%ld): we own %d out of %d buffers already.",
         portIndex, countBuffersWeOwn(mPortBuffers[portIndex]),
         mPortBuffers[portIndex].size());

    CHECK_EQ(mPortStatus[portIndex], ENABLED);
    mPortStatus[portIndex] = SHUTTING_DOWN;

    if ((mQuirks & kRequiresFlushCompleteEmulation)
        && countBuffersWeOwn(mPortBuffers[portIndex])
                == mPortBuffers[portIndex].size()) {
        // No flush is necessary and this component fails to send a
        // flush-complete event in this case.

        return false;
    }

    status_t err =
        mOMX->sendCommand(mNode, OMX_CommandFlush, portIndex);
    CHECK_EQ(err, OK);

    return true;
}

void OMXCodec::disablePortAsync(OMX_U32 portIndex) {
    CHECK(mState == EXECUTING || mState == RECONFIGURING);
    CHECK_EQ(mPortStatus[portIndex], ENABLED);
    mPortStatus[portIndex] = DISABLING;

    status_t err =
        mOMX->sendCommand(mNode, OMX_CommandPortDisable, portIndex);
    CHECK_EQ(err, OK);

    freeBuffersOnPort(portIndex, true);
}

void OMXCodec::enablePortAsync(OMX_U32 portIndex) {
    CHECK(mState == EXECUTING || mState == RECONFIGURING);

    CHECK_EQ(mPortStatus[portIndex], DISABLED);
    mPortStatus[portIndex] = ENABLING;

    status_t err =
        mOMX->sendCommand(mNode, OMX_CommandPortEnable, portIndex);
    CHECK_EQ(err, OK);
}

void OMXCodec::fillOutputBuffers() {
    CHECK_EQ(mState, EXECUTING);

    // This is a workaround for some decoders not properly reporting
    // end-of-output-stream. If we own all input buffers and also own
    // all output buffers and we already signalled end-of-input-stream,
    // the end-of-output-stream is implied.
    if (mSignalledEOS
            && countBuffersWeOwn(mPortBuffers[kPortIndexInput])
                == mPortBuffers[kPortIndexInput].size()
            && countBuffersWeOwn(mPortBuffers[kPortIndexOutput])
                == mPortBuffers[kPortIndexOutput].size()) {
        mNoMoreOutputData = true;
        mBufferFilled.signal();

        return;
    }

    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
#ifdef OMAP_ENHANCEMENT
        if ((*buffers)[i].mOwnedByPlayer) {
            continue;
        }
#endif
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
        if (!(*buffers)[i].mOwnedByComponent) {

#if defined (NPA_BUFFERS)
        if( !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER") &&
            (mQuirks & OMXCodec::kThumbnailMode) ){
            if(mNumberOfNPABuffersSent++ >= THUMBNAIL_BUFFERS_NPA_MODE)
                return;
        }
#endif
            fillOutputBuffer(&buffers->editItemAt(i));
        }
#else
        fillOutputBuffer(&buffers->editItemAt(i));
#endif
    }
}

void OMXCodec::drainInputBuffers() {
    CHECK(mState == EXECUTING || mState == RECONFIGURING);

    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
    for (size_t i = 0; i < buffers->size(); ++i) {
#ifdef USE_GETBUFFERINFO
        //we need do this since camera holds 3 buffer + 1 is for index starts from 0
        //if we don't do this it will be a deadlock since we will be waiting for camera to be done
        //and camera will not give any more unless we give release
        if (mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames) && (i == 4))
            break;
#elif defined(OMAP_ENHANCEMENT)
        if (!(*buffers)[i].mOwnedByComponent) {
            drainInputBuffer(&buffers->editItemAt(i));
        }
#else
        drainInputBuffer(&buffers->editItemAt(i));
#endif
    }
}

void OMXCodec::drainInputBuffer(BufferInfo *info) {
    CHECK_EQ(info->mOwnedByComponent, false);

    if (mSignalledEOS) {
        return;
    }

    if (mCodecSpecificDataIndex < mCodecSpecificData.size()) {
        const CodecSpecificData *specific =
            mCodecSpecificData[mCodecSpecificDataIndex];

        size_t size = specific->mSize;

        if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mMIME)
                && !(mQuirks & kWantsNALFragments)) {
            static const uint8_t kNALStartCode[4] =
                    { 0x00, 0x00, 0x00, 0x01 };

            CHECK(info->mSize >= specific->mSize + 4);

            size += 4;

            memcpy(info->mData, kNALStartCode, 4);
            memcpy((uint8_t *)info->mData + 4,
                   specific->mData, specific->mSize);
        } else {
            CHECK(info->mSize >= specific->mSize);
            memcpy(info->mData, specific->mData, specific->mSize);
        }

        mNoMoreOutputData = false;

        CODEC_LOGV("calling emptyBuffer with codec specific data");

        info->mOwnedByComponent = true;
        status_t err = mOMX->emptyBuffer(
                mNode, info->mBuffer, 0, size,
                OMX_BUFFERFLAG_ENDOFFRAME | OMX_BUFFERFLAG_CODECCONFIG,
                0);
        CHECK_EQ(err, OK);

        ++mCodecSpecificDataIndex;
        return;
    }

    if (mPaused) {
        return;
    }

    status_t err;

    bool signalEOS = false;
    int64_t timestampUs = 0;

    size_t offset = 0;
    int32_t n = 0;
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    uint32_t omx_offset = 0;
#endif

    for (;;) {
        MediaBuffer *srcBuffer;
        MediaSource::ReadOptions options;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        MediaBuffer *tmpBuffer = NULL;
        if (!mIsEncoder) {
            srcBuffer = new MediaBuffer(info->mData, info->mSize);
            tmpBuffer = srcBuffer;
        }
#endif
        if (mSkipTimeUs >= 0) {
            options.setSkipFrame(mSkipTimeUs);
        }
        if (mSeekTimeUs >= 0) {
            if (mLeftOverBuffer) {
                mLeftOverBuffer->release();
                mLeftOverBuffer = NULL;
            }
            options.setSeekTo(mSeekTimeUs, mSeekMode);

            mSeekTimeUs = -1;
            mSeekMode = ReadOptions::SEEK_CLOSEST_SYNC;
            mBufferFilled.signal();

            err = mSource->read(&srcBuffer, &options);

            if (err == OK) {
                int64_t targetTimeUs;
                if (srcBuffer->meta_data()->findInt64(
                            kKeyTargetTime, &targetTimeUs)
                        && targetTimeUs >= 0) {
                    mTargetTimeUs = targetTimeUs;
                } else {
                    mTargetTimeUs = -1;
                }
            }
        } else if (mLeftOverBuffer) {
            srcBuffer = mLeftOverBuffer;
            mLeftOverBuffer = NULL;

            err = OK;
        } else {
            err = mSource->read(&srcBuffer, &options);
        }

        if (err != OK) {
#ifdef OMAP_ENHANCEMENT
            if(mQuirks & kDecoderCantRenderSmallClips &&
               err == ERROR_END_OF_STREAM){
                static char mBufferAfterEos = 0;
                if(!mBufferAfterEos){
                    mBufferAfterEos = 1;
                    break;
                }
                mBufferAfterEos = 0;
            }
#endif
            signalEOS = true;
            mFinalStatus = err;
            mSignalledEOS = true;
            break;
        }

        size_t remainingBytes = info->mSize - offset;

        if (srcBuffer->range_length() > remainingBytes) {
            if (offset == 0) {
                CODEC_LOGE(
                     "Codec's input buffers are too small to accomodate "
                     "buffer read from source (info->mSize = %d, srcLength = %d)",
                     info->mSize, srcBuffer->range_length());

                srcBuffer->release();
                srcBuffer = NULL;

                setState(ERROR);
                return;
            }

            mLeftOverBuffer = srcBuffer;
            break;
        }

        // Do not release the media buffer if it stores meta data
        // instead of YUV data. The release is delayed until
        // EMPTY_BUFFER_DONE callback is received.
        bool releaseBuffer = true;
        if (mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
            CHECK(mOMXLivesLocally && offset == 0);
            OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *) info->mBuffer;
            header->pBuffer = (OMX_U8 *) srcBuffer->data() + srcBuffer->range_offset();
#if defined (OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
            //closed loop.
            if(!strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
               || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E")) {
                srcBuffer->meta_data()->findInt32(kKeyOffset,(int32_t *) &omx_offset);
            }

            releaseBuffer = false;
            info->mMediaBuffer = srcBuffer;
#endif
        } else {
            if (mQuirks & kStoreMetaDataInInputVideoBuffers) {
                releaseBuffer = false;
                info->mMediaBuffer = srcBuffer;
            }
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        if (tmpBuffer != srcBuffer) {
            memcpy((uint8_t *)info->mData + offset,
                    (const uint8_t *)srcBuffer->data() + srcBuffer->range_offset(),
                    srcBuffer->range_length());
            if (tmpBuffer)
                tmpBuffer->release();
        }
#else
            memcpy((uint8_t *)info->mData + offset,
                    (const uint8_t *)srcBuffer->data() + srcBuffer->range_offset(),
                    srcBuffer->range_length());
#endif
        }

        int64_t lastBufferTimeUs;
        CHECK(srcBuffer->meta_data()->findInt64(kKeyTime, &lastBufferTimeUs));
        CHECK(lastBufferTimeUs >= 0);

        if (offset == 0) {
            timestampUs = lastBufferTimeUs;
        }

        offset += srcBuffer->range_length();

        if (mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
            info->mMediaBuffer = srcBuffer;
        } else if (releaseBuffer) {
            srcBuffer->release();
            srcBuffer = NULL;
        }

        ++n;

        if (!(mQuirks & kSupportsMultipleFramesPerInputBuffer)) {
            break;
        }

        int64_t coalescedDurationUs = lastBufferTimeUs - timestampUs;

        if (coalescedDurationUs > 250000ll) {
            // Don't coalesce more than 250ms worth of encoded data at once.
            break;
        }
    }

    if (n > 1) {
        LOGV("coalesced %d frames into one input buffer", n);
    }

    OMX_U32 flags = OMX_BUFFERFLAG_ENDOFFRAME;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4) && defined (NPA_BUFFERS)
    if(mQuirks & OMXCodec::kThumbnailMode) {
        CODEC_LOGV("Sending eos flag %d",mThumbnailEOSSent);
        if(mThumbnailEOSSent == 1) {
            CODEC_LOGV("Previously EOS was sent out for thumbnail mode");
            // Returning for this point since we dont want to send
            // any more input buffers to the codec as it is expected to
            // flush out the frame from codec since we have send EOS
            // flag in input previous buffer.
            return;
        }
        mThumbnailEOSSent = 1;
        flags |= OMX_BUFFERFLAG_EOS;
    }
#endif
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

    if(!strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
       || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E"))
    {
        flags |= OMX_BUFFERFLAG_SYNCFRAME;
        flags |= OMX_BUFFERHEADERFLAG_MODIFIED;
    }
#endif

    if (signalEOS) {
        flags |= OMX_BUFFERFLAG_EOS;
    } else {
        mNoMoreOutputData = false;
    }

    CODEC_LOGV("Calling emptyBuffer on buffer %p (length %d), "
               "timestamp %lld us (%.2f secs)",
               info->mBuffer, offset,
               timestampUs, timestampUs / 1E6);

    info->mOwnedByComponent = true;
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
    if(!strcmp(mComponentName,"OMX.TI.DUCATI1.VIDEO.H264E")
       || !strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E")) {
        CODEC_LOGV("Calling emptybuffer with offset value =%ld ",omx_offset);

        err = mOMX->emptyBuffer(
            mNode, info->mBuffer, omx_offset, offset,
            flags, timestampUs);
    } else {
        err = mOMX->emptyBuffer(
            mNode, info->mBuffer, 0, offset,
            flags, timestampUs);
    }
#else
    err = mOMX->emptyBuffer(
            mNode, info->mBuffer, 0, offset,
            flags, timestampUs);
#endif

    if (err != OK) {
        setState(ERROR);
        return;
    }

    // This component does not ever signal the EOS flag on output buffers,
    // Thanks for nothing.
    if (mSignalledEOS &&
            (!strcmp(mComponentName, "OMX.TI.Video.encoder") ||
             !strcmp(mComponentName, "OMX.TI.720P.Encoder"))) {
        mNoMoreOutputData = true;
        mBufferFilled.signal();
    }
}

void OMXCodec::fillOutputBuffer(BufferInfo *info) {
    CHECK_EQ(info->mOwnedByComponent, false);

    if (mNoMoreOutputData) {
        CODEC_LOGV("There is no more output data available, not "
             "calling fillOutputBuffer");
        return;
    }

    CODEC_LOGV("Calling fill_buffer on buffer %p", info->mBuffer);
    info->mOwnedByComponent = true;
    status_t err = mOMX->fillBuffer(mNode, info->mBuffer);

    if (err != OK) {
        CODEC_LOGE("fillBuffer failed w/ error 0x%08x", err);

        setState(ERROR);
        return;
    }
}

void OMXCodec::drainInputBuffer(IOMX::buffer_id buffer) {
    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        if ((*buffers)[i].mBuffer == buffer) {
            drainInputBuffer(&buffers->editItemAt(i));
            return;
        }
    }

    CHECK(!"should not be here.");
}

void OMXCodec::fillOutputBuffer(IOMX::buffer_id buffer) {
    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        if ((*buffers)[i].mBuffer == buffer) {
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
            if (!(*buffers)[i].mOwnedByComponent) {
                fillOutputBuffer(&buffers->editItemAt(i));
                return;
            }
            else {
                CODEC_LOGV("ALREADY OWNING THE BUFFER. RETURNING");
                return;
            }
#else
        fillOutputBuffer(&buffers->editItemAt(i));
        return;
#endif
        }
    }

    CHECK(!"should not be here.");
}

void OMXCodec::setState(State newState) {
    mState = newState;
    mAsyncCompletion.signal();

    // This may cause some spurious wakeups but is necessary to
    // unblock the reader if we enter ERROR state.
    mBufferFilled.signal();
}

void OMXCodec::setRawAudioFormat(
        OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels) {

    // port definition
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;
    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);
    def.format.audio.eEncoding = OMX_AUDIO_CodingPCM;
    CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
            &def, sizeof(def)), OK);

    // pcm param
    OMX_AUDIO_PARAM_PCMMODETYPE pcmParams;
    InitOMXParams(&pcmParams);
    pcmParams.nPortIndex = portIndex;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamAudioPcm, &pcmParams, sizeof(pcmParams));

    CHECK_EQ(err, OK);

    pcmParams.nChannels = numChannels;
    pcmParams.eNumData = OMX_NumericalDataSigned;
    pcmParams.bInterleaved = OMX_TRUE;
    pcmParams.nBitPerSample = 16;
    pcmParams.nSamplingRate = sampleRate;
    pcmParams.ePCMMode = OMX_AUDIO_PCMModeLinear;

    if (numChannels == 1) {
        pcmParams.eChannelMapping[0] = OMX_AUDIO_ChannelCF;
    } else {
        CHECK_EQ(numChannels, 2);

        pcmParams.eChannelMapping[0] = OMX_AUDIO_ChannelLF;
        pcmParams.eChannelMapping[1] = OMX_AUDIO_ChannelRF;
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamAudioPcm, &pcmParams, sizeof(pcmParams));

    CHECK_EQ(err, OK);
}

static OMX_AUDIO_AMRBANDMODETYPE pickModeFromBitRate(bool isAMRWB, int32_t bps) {
    if (isAMRWB) {
        if (bps <= 6600) {
            return OMX_AUDIO_AMRBandModeWB0;
        } else if (bps <= 8850) {
            return OMX_AUDIO_AMRBandModeWB1;
        } else if (bps <= 12650) {
            return OMX_AUDIO_AMRBandModeWB2;
        } else if (bps <= 14250) {
            return OMX_AUDIO_AMRBandModeWB3;
        } else if (bps <= 15850) {
            return OMX_AUDIO_AMRBandModeWB4;
        } else if (bps <= 18250) {
            return OMX_AUDIO_AMRBandModeWB5;
        } else if (bps <= 19850) {
            return OMX_AUDIO_AMRBandModeWB6;
        } else if (bps <= 23050) {
            return OMX_AUDIO_AMRBandModeWB7;
        }

        // 23850 bps
        return OMX_AUDIO_AMRBandModeWB8;
    } else {  // AMRNB
        if (bps <= 4750) {
            return OMX_AUDIO_AMRBandModeNB0;
        } else if (bps <= 5150) {
            return OMX_AUDIO_AMRBandModeNB1;
        } else if (bps <= 5900) {
            return OMX_AUDIO_AMRBandModeNB2;
        } else if (bps <= 6700) {
            return OMX_AUDIO_AMRBandModeNB3;
        } else if (bps <= 7400) {
            return OMX_AUDIO_AMRBandModeNB4;
        } else if (bps <= 7950) {
            return OMX_AUDIO_AMRBandModeNB5;
        } else if (bps <= 10200) {
            return OMX_AUDIO_AMRBandModeNB6;
        }

        // 12200 bps
        return OMX_AUDIO_AMRBandModeNB7;
    }
}

void OMXCodec::setAMRFormat(bool isWAMR, int32_t bitRate) {
    OMX_U32 portIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;

    OMX_AUDIO_PARAM_AMRTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err =
        mOMX->getParameter(mNode, OMX_IndexParamAudioAmr, &def, sizeof(def));

    CHECK_EQ(err, OK);

    def.eAMRFrameFormat = OMX_AUDIO_AMRFrameFormatFSF;

    def.eAMRBandMode = pickModeFromBitRate(isWAMR, bitRate);
    err = mOMX->setParameter(mNode, OMX_IndexParamAudioAmr, &def, sizeof(def));
    CHECK_EQ(err, OK);

    ////////////////////////

    if (mIsEncoder) {
        sp<MetaData> format = mSource->getFormat();
        int32_t sampleRate;
        int32_t numChannels;
        CHECK(format->findInt32(kKeySampleRate, &sampleRate));
        CHECK(format->findInt32(kKeyChannelCount, &numChannels));

        setRawAudioFormat(kPortIndexInput, sampleRate, numChannels);
    }
}

void OMXCodec::setAACFormat(int32_t numChannels, int32_t sampleRate, int32_t bitRate) {
    CHECK(numChannels == 1 || numChannels == 2);
    if (mIsEncoder) {
        //////////////// input port ////////////////////
        setRawAudioFormat(kPortIndexInput, sampleRate, numChannels);

        //////////////// output port ////////////////////
        // format
        OMX_AUDIO_PARAM_PORTFORMATTYPE format;
        format.nPortIndex = kPortIndexOutput;
        format.nIndex = 0;
        status_t err = OMX_ErrorNone;
        while (OMX_ErrorNone == err) {
            CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamAudioPortFormat,
                    &format, sizeof(format)), OK);
            if (format.eEncoding == OMX_AUDIO_CodingAAC) {
                break;
            }
            format.nIndex++;
        }
        CHECK_EQ(OK, err);
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioPortFormat,
                &format, sizeof(format)), OK);

        // port definition
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), OK);
        def.format.audio.bFlagErrorConcealment = OMX_TRUE;
        def.format.audio.eEncoding = OMX_AUDIO_CodingAAC;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), OK);

        // profile
        OMX_AUDIO_PARAM_AACPROFILETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamAudioAac,
                &profile, sizeof(profile)), OK);
        profile.nChannels = numChannels;
        profile.eChannelMode = (numChannels == 1?
                OMX_AUDIO_ChannelModeMono: OMX_AUDIO_ChannelModeStereo);
        profile.nSampleRate = sampleRate;
        profile.nBitRate = bitRate;
        profile.nAudioBandWidth = 0;
        profile.nFrameLength = 0;
        profile.nAACtools = OMX_AUDIO_AACToolAll;
        profile.nAACERtools = OMX_AUDIO_AACERNone;
        profile.eAACProfile = OMX_AUDIO_AACObjectLC;
        profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatMP4FF;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioAac,
                &profile, sizeof(profile)), OK);

    } else {
        OMX_AUDIO_PARAM_AACPROFILETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexInput;

        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));
        CHECK_EQ(err, OK);

        profile.nChannels = numChannels;
        profile.nSampleRate = sampleRate;
        profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatMP4ADTS;

        err = mOMX->setParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));
        CHECK_EQ(err, OK);
    }
}

void OMXCodec::setImageOutputFormat(
        OMX_COLOR_FORMATTYPE format, OMX_U32 width, OMX_U32 height) {
    CODEC_LOGV("setImageOutputFormat(%ld, %ld)", width, height);

#if 0
    OMX_INDEXTYPE index;
    status_t err = mOMX->get_extension_index(
            mNode, "OMX.TI.JPEG.decode.Config.OutputColorFormat", &index);
    CHECK_EQ(err, OK);

    err = mOMX->set_config(mNode, index, &format, sizeof(format));
    CHECK_EQ(err, OK);
#endif

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    CHECK_EQ(def.eDomain, OMX_PortDomainImage);

    OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;

    CHECK_EQ(imageDef->eCompressionFormat, OMX_IMAGE_CodingUnused);
    imageDef->eColorFormat = format;
    imageDef->nFrameWidth = width;
    imageDef->nFrameHeight = height;

    switch (format) {
        case OMX_COLOR_FormatYUV420PackedPlanar:
        case OMX_COLOR_FormatYUV411Planar:
        {
            def.nBufferSize = (width * height * 3) / 2;
            break;
        }

        case OMX_COLOR_FormatCbYCrY:
        {
            def.nBufferSize = width * height * 2;
            break;
        }

        case OMX_COLOR_Format32bitARGB8888:
        {
            def.nBufferSize = width * height * 4;
            break;
        }

        case OMX_COLOR_Format16bitARGB4444:
        case OMX_COLOR_Format16bitARGB1555:
        case OMX_COLOR_Format16bitRGB565:
        case OMX_COLOR_Format16bitBGR565:
        {
            def.nBufferSize = width * height * 2;
            break;
        }

        default:
            CHECK(!"Should not be here. Unknown color format.");
            break;
    }

    def.nBufferCountActual = def.nBufferCountMin;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);
}

void OMXCodec::setJPEGInputFormat(
        OMX_U32 width, OMX_U32 height, OMX_U32 compressedSize) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    CHECK_EQ(def.eDomain, OMX_PortDomainImage);
    OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;

    CHECK_EQ(imageDef->eCompressionFormat, OMX_IMAGE_CodingJPEG);
    imageDef->nFrameWidth = width;
    imageDef->nFrameHeight = height;

    def.nBufferSize = compressedSize;
    def.nBufferCountActual = def.nBufferCountMin;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);
}

void OMXCodec::addCodecSpecificData(const void *data, size_t size) {
    CodecSpecificData *specific =
        (CodecSpecificData *)malloc(sizeof(CodecSpecificData) + size - 1);

    specific->mSize = size;
    memcpy(specific->mData, data, size);

    mCodecSpecificData.push(specific);
}

void OMXCodec::clearCodecSpecificData() {
    for (size_t i = 0; i < mCodecSpecificData.size(); ++i) {
        free(mCodecSpecificData.editItemAt(i));
    }
    mCodecSpecificData.clear();
    mCodecSpecificDataIndex = 0;
}

status_t OMXCodec::start(MetaData *meta) {
    Mutex::Autolock autoLock(mLock);

    if (mState != LOADED) {
        return UNKNOWN_ERROR;
    }

    sp<MetaData> params = new MetaData;
    if (mQuirks & kWantsNALFragments) {
        params->setInt32(kKeyWantsNALFragments, true);
    }
    if (meta) {
        int64_t startTimeUs = 0;
        int64_t timeUs;
        if (meta->findInt64(kKeyTime, &timeUs)) {
            startTimeUs = timeUs;
        }
        params->setInt64(kKeyTime, startTimeUs);
    }
    status_t err = mSource->start(params.get());

    if (err != OK) {
        return err;
    }

    mCodecSpecificDataIndex = 0;
    mInitialBufferSubmit = true;
    mSignalledEOS = false;
    mNoMoreOutputData = false;
    mOutputPortSettingsHaveChanged = false;
    mSeekTimeUs = -1;
    mSeekMode = ReadOptions::SEEK_CLOSEST_SYNC;
    mTargetTimeUs = -1;
    mFilledBuffers.clear();
    mPaused = false;

    return init();
}

status_t OMXCodec::stop() {
    CODEC_LOGV("stop mState=%d", mState);

    Mutex::Autolock autoLock(mLock);

    while (isIntermediateState(mState)) {
        mAsyncCompletion.wait(mLock);
    }

    switch (mState) {
        case LOADED:
        case ERROR:
            break;

        case EXECUTING:
        {
            setState(EXECUTING_TO_IDLE);

            if (mQuirks & kRequiresFlushBeforeShutdown) {
                CODEC_LOGV("This component requires a flush before transitioning "
                     "from EXECUTING to IDLE...");

                bool emulateInputFlushCompletion =
                    !flushPortAsync(kPortIndexInput);

                bool emulateOutputFlushCompletion =
                    !flushPortAsync(kPortIndexOutput);

                if (emulateInputFlushCompletion) {
                    onCmdComplete(OMX_CommandFlush, kPortIndexInput);
                }

                if (emulateOutputFlushCompletion) {
                    onCmdComplete(OMX_CommandFlush, kPortIndexOutput);
                }
            } else {
                mPortStatus[kPortIndexInput] = SHUTTING_DOWN;
                mPortStatus[kPortIndexOutput] = SHUTTING_DOWN;

                status_t err =
                    mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                CHECK_EQ(err, OK);
            }

            while (mState != LOADED && mState != ERROR) {
                mAsyncCompletion.wait(mLock);
            }

            break;
        }

        default:
        {
            CHECK(!"should not be here.");
            break;
        }
    }

    if (mLeftOverBuffer) {
        mLeftOverBuffer->release();
        mLeftOverBuffer = NULL;
    }

    mSource->stop();

    int i = 0;
    while(getStrongCount() != 1) {
        usleep(100);
        i++;
        if( i > 5) {
            LOGE("Someone else, besides client, is holding the refernce. We might have trouble.");
            break;
        }
    }

    CODEC_LOGV("stopped");

    return OK;
}

sp<MetaData> OMXCodec::getFormat() {
    Mutex::Autolock autoLock(mLock);

    return mOutputFormat;
}

#ifdef OMAP_ENHANCEMENT
void OMXCodec::setBuffers(Vector< sp<IMemory> > mBufferAddresses, bool portReconfig){
    mExtBufferAddresses = mBufferAddresses;

#ifdef TARGET_OMAP4
    if(!portReconfig){

        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;

        status_t err = mOMX->getParameter(
                        mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ(err, OK);
        //reconfigure codec with the number of buffers actually got created
        //by the Hardware Renderer
        def.nBufferCountActual = mExtBufferAddresses.size();

        //Update stride for 2D buffers
        OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;
        video_def->nStride = ARM_4K_PAGE_SIZE;
        mStride = video_def->nStride;

        err = mOMX->setParameter(
                     mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        //CHECK_EQ(err, OK);
    }else{

        if(portReconfig && mState!= RECONFIGURING)
        {
            LOGE("Extra setbuffer(portreconfig=true) call when port reconfigure is done. REPORT THIS");
            return;
        }

        // Wait till all fillbuffer done events are handled cleanly
        usleep(10000); 

        // Flush all the buffers
        freeBuffersOnPort(kPortIndexOutput);

        //disable the port if it enabled
        if (mPortStatus[kPortIndexOutput] == ENABLED) {
            disablePortAsync(kPortIndexOutput);
        }

        int32_t retrycount = 0;
        while(mPortStatus[kPortIndexOutput] == DISABLING){
            usleep(2000); // 2 mS
            LOGD("(%d) Output port is DISABLING.. Waiting for port to be disabled.. %d",retrycount,mPortStatus[kPortIndexOutput] );
            retrycount++;
            CHECK(retrycount < 100);
        }

        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;

        status_t err = mOMX->getParameter(
        mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        CHECK_EQ(err, OK);

        OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;
        video_def->nStride = 4096;
        LOGE("Updating video_def->nStride with new width %d", (int) video_def->nStride);
        err = mOMX->setParameter(mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

        /*output port is not enabled for ducati codecs, delayed till we get buffers here */
        enablePortAsync(kPortIndexOutput);
        allocateBuffersOnPort(kPortIndexOutput);

        //Make sure output port is reached to ENABLED state and thus mstate to EXECUTING
        retrycount = 0;
        while(mPortStatus[kPortIndexOutput] == ENABLING){
            usleep(2000); // 2 mS
            LOGD("(%d) Output port is ENABLING.. Waiting for port to be enabled.. %d",retrycount,mPortStatus[kPortIndexOutput] );
            retrycount++;
            CHECK(retrycount < 100);
        }
    }
#endif
}
#endif

status_t OMXCodec::read(
        MediaBuffer **buffer, const ReadOptions *options) {

#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
    status_t wait_status = 0;
#endif

    *buffer = NULL;

    Mutex::Autolock autoLock(mLock);

    if (mState != EXECUTING && mState != RECONFIGURING) {
        return UNKNOWN_ERROR;
    }

#if defined(OMAP_ENHANCEMENT) && defined (TARGET_OMAP4)
    /*Stagefright port-reconfiguration logic is based on mOutputPortSettingsHaveChanged, which will be updated very late when port is reenabled */
    /*Detect port-config event quickly so overlay buffers will be available upfront*/
    if(!strcmp(mComponentName, "OMX.TI.DUCATI1.VIDEO.DECODER")){
        if (mState == RECONFIGURING) {
            mOutputPortSettingsHaveChanged = false;
            mFilledBuffers.clear();
            return INFO_FORMAT_CHANGED;
        }
    }
#endif

    bool seeking = false;
    int64_t seekTimeUs;
    ReadOptions::SeekMode seekMode;
    if (options && options->getSeekTo(&seekTimeUs, &seekMode)) {
        seeking = true;
    }
    int64_t skipTimeUs;
    if (options && options->getSkipFrame(&skipTimeUs)) {
        mSkipTimeUs = skipTimeUs;
    } else {
        mSkipTimeUs = -1;
    }

    if (mInitialBufferSubmit) {
        mInitialBufferSubmit = false;

        if (seeking) {
            CHECK(seekTimeUs >= 0);
            mSeekTimeUs = seekTimeUs;
            mSeekMode = seekMode;

            // There's no reason to trigger the code below, there's
            // nothing to flush yet.
            seeking = false;
            mPaused = false;
        }

        drainInputBuffers();

        if (mState == EXECUTING) {
            // Otherwise mState == RECONFIGURING and this code will trigger
            // after the output port is reenabled.
            fillOutputBuffers();
        }
    }

    if (seeking) {
        CODEC_LOGV("seeking to %lld us (%.2f secs)", seekTimeUs, seekTimeUs / 1E6);

        mSignalledEOS = false;

        CHECK(seekTimeUs >= 0);
        mSeekTimeUs = seekTimeUs;
        mSeekMode = seekMode;

        mFilledBuffers.clear();

        CHECK_EQ(mState, EXECUTING);

        bool emulateInputFlushCompletion = !flushPortAsync(kPortIndexInput);
        bool emulateOutputFlushCompletion = !flushPortAsync(kPortIndexOutput);

        if (emulateInputFlushCompletion) {
            onCmdComplete(OMX_CommandFlush, kPortIndexInput);
        }

        if (emulateOutputFlushCompletion) {
            onCmdComplete(OMX_CommandFlush, kPortIndexOutput);
        }

        while (mSeekTimeUs >= 0) {
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
            wait_status = (mNSecsToWait == 0) ? mBufferFilled.wait(mLock) : mBufferFilled.waitRelative(mLock, mNSecsToWait);
            if (wait_status) {
                LOGE("Timed out waiting for the buffer! Line %d", __LINE__);
                return UNKNOWN_ERROR;
            }
#else
            mBufferFilled.wait(mLock);
#endif
        }
    }

#ifdef OMAP_ENHANCEMENT
    bool wasPaused = false;
    if (mPaused) {
        wasPaused = mPaused;
        // We are resuming from the pause state. resetting the pause flag
        mPaused = false;
            /*Initiate the buffer flow on input port once again
             * This is required to avoid starvation on I/P port if the
             * previous empty_buffer_done calls are returned without
             * initiating empty_buffer due to mpause was asserted.
             * This needs to be done before waiting for the output buffer fill
             * to avoid the deadlock
             */
            if (mState == EXECUTING) {
                drainInputBuffers();
            }
    }
#endif

#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
    //only for OMAP4 Video decoder we shall check the buffers which are not with component
    if (!strcmp("OMX.TI.DUCATI1.VIDEO.DECODER", mComponentName)) {
        while (mState != RECONFIGURING && mState != ERROR && !mNoMoreOutputData && mFilledBuffers.empty()) {
            CODEC_LOGV("READ LOCKED BUFFER QUEUE EMPTY FLAG : %d",mFilledBuffers.empty());
            if (mState == EXECUTING && wasPaused) {
                fillOutputBuffers();
            }

#if defined (NPA_BUFFERS)
            if (mQuirks & OMXCodec::kThumbnailMode) {
                if(mNumberOfNPABuffersSent){
                    /*wait if atleast one filledbuffer is sent in NPA mode*/
                    wait_status = (mNSecsToWait == 0) ? mBufferFilled.wait(mLock) : mBufferFilled.waitRelative(mLock, mNSecsToWait);
                    if (wait_status) {
                        LOGE("Timed out waiting for the buffer! Line %d", __LINE__);
                        return UNKNOWN_ERROR;
                    }
                }
            }else{
                //Dont wait forever.
                wait_status = (mNSecsToWait == 0) ? mBufferFilled.wait(mLock) : mBufferFilled.waitRelative(mLock, mNSecsToWait);
                if (wait_status) {
                    LOGE("Timed out waiting for the buffer! Line %d", __LINE__);
                    return UNKNOWN_ERROR;
                }
            }
#else
            wait_status = (mNSecsToWait == 0) ? mBufferFilled.wait(mLock) : mBufferFilled.waitRelative(mLock, mNSecsToWait);
            if (wait_status) {
                LOGE("Timed out waiting for the buffer! Line %d", __LINE__);
                return UNKNOWN_ERROR;
            }
#endif
        }
        /*see if we received a port reconfiguration */
        if (mState == RECONFIGURING) {
            mOutputPortSettingsHaveChanged = false;
            mFilledBuffers.clear();
            return INFO_FORMAT_CHANGED;
        }
    }
    else {
        while (mState != ERROR && !mNoMoreOutputData && mFilledBuffers.empty()) {
            wait_status = (mNSecsToWait == 0) ? mBufferFilled.wait(mLock) : mBufferFilled.waitRelative(mLock, mNSecsToWait);
        if (wait_status) {
            LOGE("Timed out waiting for the buffer! Line %d", __LINE__);
            return UNKNOWN_ERROR;
        }
    }
    }

#else
    while (mState != ERROR && !mNoMoreOutputData && mFilledBuffers.empty()) {
        mBufferFilled.wait(mLock);
    }
#endif

    if (mState == ERROR) {
        return UNKNOWN_ERROR;
    }

    if (mFilledBuffers.empty()) {
        return mSignalledEOS ? mFinalStatus : ERROR_END_OF_STREAM;
    }

    if (mOutputPortSettingsHaveChanged) {
        mOutputPortSettingsHaveChanged = false;
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
        //for ducati codecs, to assert port reconfiguration immediately (so as to allocate overlay buffers upfront), we use mState as above.
        //so, mOutputPortSettingsHaveChanged conditon check will result in redundant INFO_FORMAT_CHANGED event. Just proceed now.
        //also, mOutputPortSettingsHaveChanged will be updated very late on port reenable, by the time overlay buffers are available and port enabled => just go ahead.
        if (strcmp("OMX.TI.DUCATI1.VIDEO.DECODER", mComponentName)){
            mFilledBuffers.clear();
            return INFO_FORMAT_CHANGED;
        }
#else
        return INFO_FORMAT_CHANGED;
#endif
    }

    size_t index = *mFilledBuffers.begin();
    mFilledBuffers.erase(mFilledBuffers.begin());

    BufferInfo *info = &mPortBuffers[kPortIndexOutput].editItemAt(index);
    info->mMediaBuffer->add_ref();
#ifdef OMAP_ENHANCEMENT
    info->mOwnedByPlayer = true;
#endif
    *buffer = info->mMediaBuffer;

    return OK;
}

void OMXCodec::signalBufferReturned(MediaBuffer *buffer) {
    Mutex::Autolock autoLock(mLock);

    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        BufferInfo *info = &buffers->editItemAt(i);

        if (info->mMediaBuffer == buffer) {
#ifdef OMAP_ENHANCEMENT
            info->mOwnedByPlayer = false;
            if(mState == RECONFIGURING) {
                return;
            }
#endif
            CHECK_EQ(mPortStatus[kPortIndexOutput], ENABLED);
#if defined(TARGET_OMAP4) && defined(OMAP_ENHANCEMENT)
            if (!(*buffers)[i].mOwnedByComponent) {
                fillOutputBuffer(info);
                return;
            }
            else {
                CODEC_LOGV("OP BUFFER ALREADY OWNED BY COMPONENT. RETURNING");
                return;
            }
#else
        fillOutputBuffer(info);
        return;
#endif
        }
    }

    CHECK(!"should not be here.");
}

static const char *imageCompressionFormatString(OMX_IMAGE_CODINGTYPE type) {
    static const char *kNames[] = {
        "OMX_IMAGE_CodingUnused",
        "OMX_IMAGE_CodingAutoDetect",
        "OMX_IMAGE_CodingJPEG",
        "OMX_IMAGE_CodingJPEG2K",
        "OMX_IMAGE_CodingEXIF",
        "OMX_IMAGE_CodingTIFF",
        "OMX_IMAGE_CodingGIF",
        "OMX_IMAGE_CodingPNG",
        "OMX_IMAGE_CodingLZW",
        "OMX_IMAGE_CodingBMP",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *colorFormatString(OMX_COLOR_FORMATTYPE type) {
    static const char *kNames[] = {
        "OMX_COLOR_FormatUnused",
        "OMX_COLOR_FormatMonochrome",
        "OMX_COLOR_Format8bitRGB332",
        "OMX_COLOR_Format12bitRGB444",
        "OMX_COLOR_Format16bitARGB4444",
        "OMX_COLOR_Format16bitARGB1555",
        "OMX_COLOR_Format16bitRGB565",
        "OMX_COLOR_Format16bitBGR565",
        "OMX_COLOR_Format18bitRGB666",
        "OMX_COLOR_Format18bitARGB1665",
        "OMX_COLOR_Format19bitARGB1666",
        "OMX_COLOR_Format24bitRGB888",
        "OMX_COLOR_Format24bitBGR888",
        "OMX_COLOR_Format24bitARGB1887",
        "OMX_COLOR_Format25bitARGB1888",
        "OMX_COLOR_Format32bitBGRA8888",
        "OMX_COLOR_Format32bitARGB8888",
        "OMX_COLOR_FormatYUV411Planar",
        "OMX_COLOR_FormatYUV411PackedPlanar",
        "OMX_COLOR_FormatYUV420Planar",
        "OMX_COLOR_FormatYUV420PackedPlanar",
        "OMX_COLOR_FormatYUV420SemiPlanar",
        "OMX_COLOR_FormatYUV422Planar",
        "OMX_COLOR_FormatYUV422PackedPlanar",
        "OMX_COLOR_FormatYUV422SemiPlanar",
        "OMX_COLOR_FormatYCbYCr",
        "OMX_COLOR_FormatYCrYCb",
        "OMX_COLOR_FormatCbYCrY",
        "OMX_COLOR_FormatCrYCbY",
        "OMX_COLOR_FormatYUV444Interleaved",
        "OMX_COLOR_FormatRawBayer8bit",
        "OMX_COLOR_FormatRawBayer10bit",
        "OMX_COLOR_FormatRawBayer8bitcompressed",
        "OMX_COLOR_FormatL2",
        "OMX_COLOR_FormatL4",
        "OMX_COLOR_FormatL8",
        "OMX_COLOR_FormatL16",
        "OMX_COLOR_FormatL24",
        "OMX_COLOR_FormatL32",
        "OMX_COLOR_FormatYUV420PackedSemiPlanar",
        "OMX_COLOR_FormatYUV422PackedSemiPlanar",
        "OMX_COLOR_Format18BitBGR666",
        "OMX_COLOR_Format24BitARGB6666",
        "OMX_COLOR_Format24BitABGR6666",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type == OMX_QCOM_COLOR_FormatYVU420SemiPlanar) {
        return "OMX_QCOM_COLOR_FormatYVU420SemiPlanar";
    } else if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *videoCompressionFormatString(OMX_VIDEO_CODINGTYPE type) {
    static const char *kNames[] = {
        "OMX_VIDEO_CodingUnused",
        "OMX_VIDEO_CodingAutoDetect",
        "OMX_VIDEO_CodingMPEG2",
        "OMX_VIDEO_CodingH263",
        "OMX_VIDEO_CodingMPEG4",
        "OMX_VIDEO_CodingWMV",
        "OMX_VIDEO_CodingRV",
        "OMX_VIDEO_CodingAVC",
        "OMX_VIDEO_CodingMJPEG",
#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)
        "OMX_VIDEO_CodingVP6",
        "OMX_VIDEO_CodingVP7"
#endif
    };

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

    if (type == (OMX_VIDEO_CODINGTYPE) OMX_VIDEO_CodingVP6) {
        return kNames[9];
    }

    if (type == (OMX_VIDEO_CODINGTYPE) OMX_VIDEO_CodingVP7) {
        return kNames[10];
    }
#endif

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *audioCodingTypeString(OMX_AUDIO_CODINGTYPE type) {
    static const char *kNames[] = {
        "OMX_AUDIO_CodingUnused",
        "OMX_AUDIO_CodingAutoDetect",
        "OMX_AUDIO_CodingPCM",
        "OMX_AUDIO_CodingADPCM",
        "OMX_AUDIO_CodingAMR",
        "OMX_AUDIO_CodingGSMFR",
        "OMX_AUDIO_CodingGSMEFR",
        "OMX_AUDIO_CodingGSMHR",
        "OMX_AUDIO_CodingPDCFR",
        "OMX_AUDIO_CodingPDCEFR",
        "OMX_AUDIO_CodingPDCHR",
        "OMX_AUDIO_CodingTDMAFR",
        "OMX_AUDIO_CodingTDMAEFR",
        "OMX_AUDIO_CodingQCELP8",
        "OMX_AUDIO_CodingQCELP13",
        "OMX_AUDIO_CodingEVRC",
        "OMX_AUDIO_CodingSMV",
        "OMX_AUDIO_CodingG711",
        "OMX_AUDIO_CodingG723",
        "OMX_AUDIO_CodingG726",
        "OMX_AUDIO_CodingG729",
        "OMX_AUDIO_CodingAAC",
        "OMX_AUDIO_CodingMP3",
        "OMX_AUDIO_CodingSBC",
        "OMX_AUDIO_CodingVORBIS",
        "OMX_AUDIO_CodingWMA",
        "OMX_AUDIO_CodingRA",
        "OMX_AUDIO_CodingMIDI",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *audioPCMModeString(OMX_AUDIO_PCMMODETYPE type) {
    static const char *kNames[] = {
        "OMX_AUDIO_PCMModeLinear",
        "OMX_AUDIO_PCMModeALaw",
        "OMX_AUDIO_PCMModeMULaw",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *amrBandModeString(OMX_AUDIO_AMRBANDMODETYPE type) {
    static const char *kNames[] = {
        "OMX_AUDIO_AMRBandModeUnused",
        "OMX_AUDIO_AMRBandModeNB0",
        "OMX_AUDIO_AMRBandModeNB1",
        "OMX_AUDIO_AMRBandModeNB2",
        "OMX_AUDIO_AMRBandModeNB3",
        "OMX_AUDIO_AMRBandModeNB4",
        "OMX_AUDIO_AMRBandModeNB5",
        "OMX_AUDIO_AMRBandModeNB6",
        "OMX_AUDIO_AMRBandModeNB7",
        "OMX_AUDIO_AMRBandModeWB0",
        "OMX_AUDIO_AMRBandModeWB1",
        "OMX_AUDIO_AMRBandModeWB2",
        "OMX_AUDIO_AMRBandModeWB3",
        "OMX_AUDIO_AMRBandModeWB4",
        "OMX_AUDIO_AMRBandModeWB5",
        "OMX_AUDIO_AMRBandModeWB6",
        "OMX_AUDIO_AMRBandModeWB7",
        "OMX_AUDIO_AMRBandModeWB8",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

static const char *amrFrameFormatString(OMX_AUDIO_AMRFRAMEFORMATTYPE type) {
    static const char *kNames[] = {
        "OMX_AUDIO_AMRFrameFormatConformance",
        "OMX_AUDIO_AMRFrameFormatIF1",
        "OMX_AUDIO_AMRFrameFormatIF2",
        "OMX_AUDIO_AMRFrameFormatFSF",
        "OMX_AUDIO_AMRFrameFormatRTPPayload",
        "OMX_AUDIO_AMRFrameFormatITU",
    };

    size_t numNames = sizeof(kNames) / sizeof(kNames[0]);

    if (type < 0 || (size_t)type >= numNames) {
        return "UNKNOWN";
    } else {
        return kNames[type];
    }
}

void OMXCodec::dumpPortStatus(OMX_U32 portIndex) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    printf("%s Port = {\n", portIndex == kPortIndexInput ? "Input" : "Output");

    CHECK((portIndex == kPortIndexInput && def.eDir == OMX_DirInput)
          || (portIndex == kPortIndexOutput && def.eDir == OMX_DirOutput));

    printf("  nBufferCountActual = %ld\n", def.nBufferCountActual);
    printf("  nBufferCountMin = %ld\n", def.nBufferCountMin);
    printf("  nBufferSize = %ld\n", def.nBufferSize);

    switch (def.eDomain) {
        case OMX_PortDomainImage:
        {
            const OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;

            printf("\n");
            printf("  // Image\n");
            printf("  nFrameWidth = %ld\n", imageDef->nFrameWidth);
            printf("  nFrameHeight = %ld\n", imageDef->nFrameHeight);
            printf("  nStride = %ld\n", imageDef->nStride);

            printf("  eCompressionFormat = %s\n",
                   imageCompressionFormatString(imageDef->eCompressionFormat));

            printf("  eColorFormat = %s\n",
                   colorFormatString(imageDef->eColorFormat));

            break;
        }

        case OMX_PortDomainVideo:
        {
            OMX_VIDEO_PORTDEFINITIONTYPE *videoDef = &def.format.video;

            printf("\n");
            printf("  // Video\n");
            printf("  nFrameWidth = %ld\n", videoDef->nFrameWidth);
            printf("  nFrameHeight = %ld\n", videoDef->nFrameHeight);
            printf("  nStride = %ld\n", videoDef->nStride);

            printf("  eCompressionFormat = %s\n",
                   videoCompressionFormatString(videoDef->eCompressionFormat));

            printf("  eColorFormat = %s\n",
                   colorFormatString(videoDef->eColorFormat));

            break;
        }

        case OMX_PortDomainAudio:
        {
            OMX_AUDIO_PORTDEFINITIONTYPE *audioDef = &def.format.audio;

            printf("\n");
            printf("  // Audio\n");
            printf("  eEncoding = %s\n",
                   audioCodingTypeString(audioDef->eEncoding));

            if (audioDef->eEncoding == OMX_AUDIO_CodingPCM) {
                OMX_AUDIO_PARAM_PCMMODETYPE params;
                InitOMXParams(&params);
                params.nPortIndex = portIndex;

                err = mOMX->getParameter(
                        mNode, OMX_IndexParamAudioPcm, &params, sizeof(params));
                CHECK_EQ(err, OK);

                printf("  nSamplingRate = %ld\n", params.nSamplingRate);
                printf("  nChannels = %ld\n", params.nChannels);
                printf("  bInterleaved = %d\n", params.bInterleaved);
                printf("  nBitPerSample = %ld\n", params.nBitPerSample);

                printf("  eNumData = %s\n",
                       params.eNumData == OMX_NumericalDataSigned
                        ? "signed" : "unsigned");

                printf("  ePCMMode = %s\n", audioPCMModeString(params.ePCMMode));
            } else if (audioDef->eEncoding == OMX_AUDIO_CodingAMR) {
                OMX_AUDIO_PARAM_AMRTYPE amr;
                InitOMXParams(&amr);
                amr.nPortIndex = portIndex;

                err = mOMX->getParameter(
                        mNode, OMX_IndexParamAudioAmr, &amr, sizeof(amr));
                CHECK_EQ(err, OK);

                printf("  nChannels = %ld\n", amr.nChannels);
                printf("  eAMRBandMode = %s\n",
                        amrBandModeString(amr.eAMRBandMode));
                printf("  eAMRFrameFormat = %s\n",
                        amrFrameFormatString(amr.eAMRFrameFormat));
            }

            break;
        }

        default:
        {
            printf("  // Unknown\n");
            break;
        }
    }

    printf("}\n");
}

void OMXCodec::initOutputFormat(const sp<MetaData> &inputFormat) {
    mOutputFormat = new MetaData;
    mOutputFormat->setCString(kKeyDecoderComponent, mComponentName);
    if (mIsEncoder) {
        int32_t timeScale;
        if (inputFormat->findInt32(kKeyTimeScale, &timeScale)) {
            mOutputFormat->setInt32(kKeyTimeScale, timeScale);
        }
    }

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);

    switch (def.eDomain) {
        case OMX_PortDomainImage:
        {
            OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;
            CHECK_EQ(imageDef->eCompressionFormat, OMX_IMAGE_CodingUnused);

            mOutputFormat->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
            mOutputFormat->setInt32(kKeyColorFormat, imageDef->eColorFormat);
            mOutputFormat->setInt32(kKeyWidth, imageDef->nFrameWidth);
            mOutputFormat->setInt32(kKeyHeight, imageDef->nFrameHeight);
            break;
        }

        case OMX_PortDomainAudio:
        {
            OMX_AUDIO_PORTDEFINITIONTYPE *audio_def = &def.format.audio;

            if (audio_def->eEncoding == OMX_AUDIO_CodingPCM) {
                OMX_AUDIO_PARAM_PCMMODETYPE params;
                InitOMXParams(&params);
                params.nPortIndex = kPortIndexOutput;

                err = mOMX->getParameter(
                        mNode, OMX_IndexParamAudioPcm, &params, sizeof(params));
                CHECK_EQ(err, OK);

                CHECK_EQ(params.eNumData, OMX_NumericalDataSigned);
                CHECK_EQ(params.nBitPerSample, 16);
                CHECK_EQ(params.ePCMMode, OMX_AUDIO_PCMModeLinear);

                int32_t numChannels, sampleRate;
                inputFormat->findInt32(kKeyChannelCount, &numChannels);
                inputFormat->findInt32(kKeySampleRate, &sampleRate);

                if ((OMX_U32)numChannels != params.nChannels) {
                    LOGW("Codec outputs a different number of channels than "
                         "the input stream contains (contains %d channels, "
                         "codec outputs %ld channels).",
                         numChannels, params.nChannels);
                }

                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);

                // Use the codec-advertised number of channels, as some
                // codecs appear to output stereo even if the input data is
                // mono. If we know the codec lies about this information,
                // use the actual number of channels instead.
                mOutputFormat->setInt32(
                        kKeyChannelCount,
                        (mQuirks & kDecoderLiesAboutNumberOfChannels)
                            ? numChannels : params.nChannels);

#ifndef OMAP_ENHANCEMENT
                // The codec-reported sampleRate is not reliable...
                mOutputFormat->setInt32(kKeySampleRate, sampleRate);
#else
                if ((OMX_U32)sampleRate != params.nSamplingRate) {
                    LOGW("Codec outputs a different number of samplerate than "
                            "the input stream contains (contains %d samplerate, "
                            "codec outputs %ld samplerate).",
                            sampleRate, params.nSamplingRate);
                    mOutputFormat->setInt32(
                            kKeySampleRate,
                            (mQuirks & kDecoderNeedsPortReconfiguration)
                            ? params.nSamplingRate : sampleRate);
                }
                else
                {
                  // The codec-reported sampleRate is not reliable...
                    mOutputFormat->setInt32(kKeySampleRate, sampleRate);
                }
#endif
            } else if (audio_def->eEncoding == OMX_AUDIO_CodingAMR) {
                OMX_AUDIO_PARAM_AMRTYPE amr;
                InitOMXParams(&amr);
                amr.nPortIndex = kPortIndexOutput;

                err = mOMX->getParameter(
                        mNode, OMX_IndexParamAudioAmr, &amr, sizeof(amr));
                CHECK_EQ(err, OK);

                CHECK_EQ(amr.nChannels, 1);
                mOutputFormat->setInt32(kKeyChannelCount, 1);

                if (amr.eAMRBandMode >= OMX_AUDIO_AMRBandModeNB0
                    && amr.eAMRBandMode <= OMX_AUDIO_AMRBandModeNB7) {
                    mOutputFormat->setCString(
                            kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AMR_NB);
                    mOutputFormat->setInt32(kKeySampleRate, 8000);
                } else if (amr.eAMRBandMode >= OMX_AUDIO_AMRBandModeWB0
                            && amr.eAMRBandMode <= OMX_AUDIO_AMRBandModeWB8) {
                    mOutputFormat->setCString(
                            kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AMR_WB);
                    mOutputFormat->setInt32(kKeySampleRate, 16000);
                } else {
                    CHECK(!"Unknown AMR band mode.");
                }
            } else if (audio_def->eEncoding == OMX_AUDIO_CodingAAC) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                int32_t numChannels, sampleRate, bitRate;
                inputFormat->findInt32(kKeyChannelCount, &numChannels);
                inputFormat->findInt32(kKeySampleRate, &sampleRate);
                inputFormat->findInt32(kKeyBitRate, &bitRate);
                mOutputFormat->setInt32(kKeyChannelCount, numChannels);
                mOutputFormat->setInt32(kKeySampleRate, sampleRate);
                mOutputFormat->setInt32(kKeyBitRate, bitRate);
            } else {
                CHECK(!"Should not be here. Unknown audio encoding.");
            }
            break;
        }

        case OMX_PortDomainVideo:
        {
            OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

            if (video_def->eCompressionFormat == OMX_VIDEO_CodingUnused) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
            } else if (video_def->eCompressionFormat == OMX_VIDEO_CodingMPEG4) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_MPEG4);
            } else if (video_def->eCompressionFormat == OMX_VIDEO_CodingH263) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_H263);
            } else if (video_def->eCompressionFormat == OMX_VIDEO_CodingAVC) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
#if defined(OMAP_ENHANCEMENT)
#if defined(TARGET_OMAP4)
            } else if (video_def->eCompressionFormat == (OMX_VIDEO_CODINGTYPE)OMX_VIDEO_CodingVP6) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP6);
            } else if (video_def->eCompressionFormat == (OMX_VIDEO_CODINGTYPE)OMX_VIDEO_CodingVP7) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_VP7);
#endif
            }
            else if (video_def->eCompressionFormat == OMX_VIDEO_CodingWMV)
            {
                mOutputFormat->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_WMV);
#endif
            } else {
                CHECK(!"Unknown compression format.");
            }

            if (!strcmp(mComponentName, "OMX.PV.avcdec")) {
                // This component appears to be lying to me.
                mOutputFormat->setInt32(
                        kKeyWidth, (video_def->nFrameWidth + 15) & -16);
                mOutputFormat->setInt32(
                        kKeyHeight, (video_def->nFrameHeight + 15) & -16);
            } else {
#ifdef USE_QCOM_OMX_FIX
                //Update the Stride and Slice Height
                //Allows creation of Renderer with correct height and width
                if( mIsEncoder ){
                    int32_t width, height;
                    bool success = inputFormat->findInt32( kKeyWidth, &width ) &&
                        inputFormat->findInt32( kKeyHeight, &height);
                    CHECK( success );
                    mOutputFormat->setInt32(kKeyWidth, width );
                    mOutputFormat->setInt32(kKeyHeight, height );

#ifdef USE_GETBUFFERINFO
                    /* Tell the encoder to use our supplied pmem on
                       the input buffer, via vendor-specific useBuffer */
                    OMX_QCOM_PARAM_PORTDEFINITIONTYPE portDefn;
                    InitOMXParams(&portDefn);
                    portDefn.nPortIndex = kPortIndexInput;
                    portDefn.nMemRegion = OMX_QCOM_MemRegionEBI1;

                    err = mOMX->setParameter( mNode,
                        (OMX_INDEXTYPE) OMX_QcomIndexPortDefn,
                        &portDefn, sizeof(portDefn) );
                    CHECK_EQ(err, OK);
#endif
                }
                else {
                    LOGV("video_def->nStride = %d, video_def->nSliceHeight = %d", video_def->nStride,
                            video_def->nSliceHeight );
                    if (video_def->nStride && video_def->nSliceHeight) {
                        /* Make sure we actually got the values from the decoder */
                        mOutputFormat->setInt32(kKeyWidth, video_def->nStride);
                        mOutputFormat->setInt32(kKeyHeight, video_def->nSliceHeight);
                    } else {
                        /* We didn't. Use the old behavior */
                        mOutputFormat->setInt32(kKeyWidth, video_def->nFrameWidth);
                        mOutputFormat->setInt32(kKeyHeight, video_def->nFrameHeight);
                    }
                }
#else
                //Some hardware expects the old behavior
                mOutputFormat->setInt32(kKeyWidth, video_def->nFrameWidth);
                mOutputFormat->setInt32(kKeyHeight, video_def->nFrameHeight);
#endif
            }

            mOutputFormat->setInt32(kKeyColorFormat, video_def->eColorFormat);

#if defined(OMAP_ENHANCEMENT) && defined(TARGET_OMAP4)

        /* for Non-Ducati HW codecs, padded with and height are updated with the
        * video frame width and Height
        */
        mOutputFormat->setInt32(kKeyPaddedWidth, video_def->nFrameWidth);
        mOutputFormat->setInt32(kKeyPaddedHeight, video_def->nFrameHeight);

        /* Ducati codecs require padded output buffers.
           Query proper size and update meta-data accordingly
           which will be used later in renderer.
           Note: 2D WxH in other OMX Get/Set parameter calls are seamless */
        if (!strcmp("OMX.TI.DUCATI1.VIDEO.DECODER", mComponentName)) {

            OMX_CONFIG_RECTTYPE tParamStruct;

            InitOMXParams(&tParamStruct);
            tParamStruct.nPortIndex = kPortIndexOutput;

            err = mOMX->getParameter(
                    mNode, (OMX_INDEXTYPE)OMX_TI_IndexParam2DBufferAllocDimension, &tParamStruct, sizeof(tParamStruct));

            CHECK_EQ(err, OK);
            mOutputFormat->setInt32(kKeyPaddedWidth, tParamStruct.nWidth);
            mOutputFormat->setInt32(kKeyPaddedHeight, tParamStruct.nHeight);
            LOGD("initOutputFormat WxH %dx%d Padded %dx%d ",
                        (int) video_def->nFrameWidth, (int) video_def->nFrameHeight,
                        (int) tParamStruct.nWidth, (int) tParamStruct.nHeight);
        }
#endif
            break;
        }

        default:
        {
            CHECK(!"should not be here, neither audio nor video.");
            break;
        }
    }
}

status_t OMXCodec::pause() {
    Mutex::Autolock autoLock(mLock);

    mPaused = true;

    return OK;
}

#ifdef OMAP_ENHANCEMENT
int OMXCodec::getNumofOutputBuffers() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, OK);
    CHECK_EQ(def.eDomain, OMX_PortDomainVideo);

    LOGD("CodecRecommended O/P BufferCnt[%ld]", def.nBufferCountActual);

    return (def.nBufferCountActual);
}
#endif

////////////////////////////////////////////////////////////////////////////////

status_t QueryCodecs(
        const sp<IOMX> &omx,
        const char *mime, bool queryDecoders,
        Vector<CodecCapabilities> *results) {
    results->clear();

    for (int index = 0;; ++index) {
        const char *componentName;

        if (!queryDecoders) {
            componentName = GetCodec(
                    kEncoderInfo, sizeof(kEncoderInfo) / sizeof(kEncoderInfo[0]),
                    mime, index);
        } else {
            componentName = GetCodec(
                    kDecoderInfo, sizeof(kDecoderInfo) / sizeof(kDecoderInfo[0]),
                    mime, index);
        }

        if (!componentName) {
            return OK;
        }

        if (strncmp(componentName, "OMX.", 4)) {
            // Not an OpenMax component but a software codec.

            results->push();
            CodecCapabilities *caps = &results->editItemAt(results->size() - 1);
            caps->mComponentName = componentName;

            continue;
        }

        sp<OMXCodecObserver> observer = new OMXCodecObserver;
        IOMX::node_id node;
        status_t err = omx->allocateNode(componentName, observer, &node);

        if (err != OK) {
            continue;
        }

        OMXCodec::setComponentRole(omx, node, !queryDecoders, mime);

        results->push();
        CodecCapabilities *caps = &results->editItemAt(results->size() - 1);
        caps->mComponentName = componentName;

        OMX_VIDEO_PARAM_PROFILELEVELTYPE param;
        InitOMXParams(&param);

        param.nPortIndex = queryDecoders ? 0 : 1;

        for (param.nProfileIndex = 0;; ++param.nProfileIndex) {
            err = omx->getParameter(
                    node, OMX_IndexParamVideoProfileLevelQuerySupported,
                    &param, sizeof(param));

            if (err != OK) {
                break;
            }

            CodecProfileLevel profileLevel;
            profileLevel.mProfile = param.eProfile;
            profileLevel.mLevel = param.eLevel;

            caps->mProfileLevels.push(profileLevel);
        }

        CHECK_EQ(omx->freeNode(node), OK);
    }
}

}  // namespace android
