/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2011-2012 Code Aurora Forum
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
/*--------------------------------------------------------------------------
Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
--------------------------------------------------------------------------*/

//#define LOG_NDEBUG 0
#define LOG_TAG "OMXCodec"
#include <utils/Log.h>

#ifdef QCOM_HARDWARE
#include "include/AACDecoder.h"
#endif
#include "include/AACEncoder.h"
#include "include/AMRNBEncoder.h"
#include "include/AMRWBEncoder.h"
#include "include/AVCEncoder.h"
#include "include/M4vH263Encoder.h"
#ifdef QCOM_HARDWARE
#include "include/MP3Decoder.h"
#endif

#include "include/ESDS.h"

#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <binder/ProcessState.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/IMediaPlayerService.h>
#include <media/stagefright/HardwareAPI.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <media/stagefright/Utils.h>
#include <utils/Vector.h>
#ifdef QCOM_HARDWARE
#include <cutils/properties.h>
#endif

#include <OMX_Audio.h>
#include <OMX_Component.h>

#ifdef QCOM_HARDWARE
#include <OMX_QCOMExtns.h>

#include <gralloc_priv.h>
#include <qcom_ui.h>
#include <QOMX_AudioExtensions.h>
#endif
#include "include/avc_utils.h"

namespace android {

// Treat time out as an error if we have not received any output
// buffers after 3 seconds.
const static int64_t kBufferFilledEventTimeOutNs = 3000000000LL;

// OMX Spec defines less than 50 color formats. If the query for
// color format is executed for more than kMaxColorFormatSupported,
// the query will fail to avoid looping forever.
// 1000 is more than enough for us to tell whether the omx
// component in question is buggy or not.
const static uint32_t kMaxColorFormatSupported = 1000;

#ifdef QCOM_HARDWARE
static const int QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
static const int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
#endif

struct CodecInfo {
    const char *mime;
    const char *codec;
};

#ifdef QCOM_HARDWARE
class ColorFormatInfo {
    private:
        enum {
            LOCAL = 0,
            REMOTE = 1,
            END = 2
        };
        static const int32_t preferredColorFormat[END];
    public:
        static int32_t getPreferredColorFormat(bool isLocal) {
            char colorformat[10]="";
            if(!property_get("sf.debug.colorformat", colorformat, NULL)){
                if(isLocal) {
                    return preferredColorFormat[LOCAL];
                }
                return preferredColorFormat[REMOTE];
            } else {
                if(!strcmp(colorformat, "yamato")) {
                    return QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka;
                }
                return preferredColorFormat[LOCAL];
            }
        }
};

const int32_t ColorFormatInfo::preferredColorFormat[] = {
#ifdef TARGET7x30
    QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka,
    QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka
#endif
#ifdef TARGET8x60
    QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka,
    QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka
#endif
#ifdef TARGET7x27
    OMX_QCOM_COLOR_FormatYVU420SemiPlanar,
    QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka
#endif
#ifdef TARGET7x27A
    OMX_QCOM_COLOR_FormatYVU420SemiPlanar,
    OMX_QCOM_COLOR_FormatYVU420SemiPlanar
#endif
#ifdef TARGET8x50
    OMX_QCOM_COLOR_FormatYVU420SemiPlanar,
    QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka
#endif
};


#define FACTORY_CREATE(name) \
static sp<MediaSource> Make##name(const sp<MediaSource> &source) { \
    return new name(source); \
}
#endif

#define FACTORY_CREATE_ENCODER(name) \
static sp<MediaSource> Make##name(const sp<MediaSource> &source, const sp<MetaData> &meta) { \
    return new name(source, meta); \
}

#define FACTORY_REF(name) { #name, Make##name },

#ifdef QCOM_HARDWARE
FACTORY_CREATE(MP3Decoder)
FACTORY_CREATE(AACDecoder)
#endif
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

#ifdef QCOM_HARDWARE
static sp<MediaSource> InstantiateSoftwareDecoder(
        const char *name, const sp<MediaSource> &source) {
    struct FactoryInfo {
        const char *name;
        sp<MediaSource> (*CreateFunc)(const sp<MediaSource> &);
    };

    static const FactoryInfo kFactoryInfo[] = {
        FACTORY_REF(MP3Decoder)
        FACTORY_REF(AACDecoder)
    };
    for (size_t i = 0;
         i < sizeof(kFactoryInfo) / sizeof(kFactoryInfo[0]); ++i) {
        if (!strcmp(name, kFactoryInfo[i].name)) {
            return (*kFactoryInfo[i].CreateFunc)(source);
        }
    }

    return NULL;
}
#endif

#undef FACTORY_REF
#undef FACTORY_CREATE

static const CodecInfo kDecoderInfo[] = {
    { MEDIA_MIMETYPE_IMAGE_JPEG, "OMX.TI.JPEG.decode" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.qcom.audio.decoder.mp3" },
#endif
//    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.TI.MP3.decode" },
    { MEDIA_MIMETYPE_AUDIO_MPEG, "OMX.google.mp3.decoder" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_AUDIO_MPEG, "MP3Decoder" },
#endif
    { MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II, "OMX.Nvidia.mp2.decoder" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.decode" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.Nvidia.amr.decoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.google.amrnb.decoder" },
//    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.Nvidia.amrwb.decoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.decode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.google.amrwb.decoder" },
#ifdef QCOM_HARDWARE
#ifdef USE_AAC_HW_DEC
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.qcom.audio.decoder.aac" },
#endif
#endif
//    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.Nvidia.aac.decoder" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.decode" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.google.aac.decoder" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACDecoder" },
#endif
    { MEDIA_MIMETYPE_AUDIO_G711_ALAW, "OMX.google.g711.alaw.decoder" },
    { MEDIA_MIMETYPE_AUDIO_G711_MLAW, "OMX.google.g711.mlaw.decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.Nvidia.mp4.decode" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.7x30.video.decoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.video.decoder.mpeg4" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.ittiam.video.decoder.mpeg4" },
#endif
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.SEC.MPEG4.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.google.mpeg4.decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.Nvidia.h263.decode" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.7x30.video.decoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.video.decoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.SEC.H263.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.google.h263.decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.DUCATI1.VIDEO.DECODER" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.Nvidia.h264.decode" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.7x30.video.decoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.video.decoder.avc" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.ittiam.video.decoder.avc" },
#endif
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.SEC.AVC.Decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.google.h264.decoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.google.avc.decoder" },
    { MEDIA_MIMETYPE_AUDIO_VORBIS, "OMX.google.vorbis.decoder" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_VIDEO_VPX, "OMX.qcom.video.decoder.vp8" },
#endif
    { MEDIA_MIMETYPE_VIDEO_VPX, "OMX.google.vpx.decoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG2, "OMX.Nvidia.mpeg2v.decode" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_VIDEO_MPEG2, "OMX.qcom.video.decoder.mpeg2" },
    { MEDIA_MIMETYPE_VIDEO_DIVX, "OMX.qcom.video.decoder.divx"},
    { MEDIA_MIMETYPE_VIDEO_DIVX311, "OMX.qcom.video.decoder.divx311"},
    { MEDIA_MIMETYPE_VIDEO_DIVX4, "OMX.qcom.video.decoder.divx4"},
    { MEDIA_MIMETYPE_AUDIO_AC3, "OMX.qcom.audio.decoder.ac3" },
    { MEDIA_MIMETYPE_AUDIO_QCELP, "OMX.qcom.audio.decoder.Qcelp13Hw"},
    { MEDIA_MIMETYPE_AUDIO_QCELP, "OMX.qcom.audio.decoder.Qcelp13"},
    { MEDIA_MIMETYPE_AUDIO_EVRC, "OMX.qcom.audio.decoder.evrchw" },
    { MEDIA_MIMETYPE_AUDIO_EVRC, "OMX.qcom.audio.decoder.evrc" },
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.qcom.audio.decoder.wma"},
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.qcom.audio.decoder.wmaLossLess"},
    { MEDIA_MIMETYPE_AUDIO_WMA, "OMX.qcom.audio.decoder.wma10Pro"},
    { MEDIA_MIMETYPE_VIDEO_WMV, "OMX.qcom.video.decoder.vc1"},
#endif
};

static const CodecInfo kEncoderInfo[] = {
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "OMX.TI.AMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_NB, "AMRNBEncoder" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "OMX.TI.WBAMR.encode" },
    { MEDIA_MIMETYPE_AUDIO_AMR_WB, "AMRWBEncoder" },
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.TI.AAC.encode" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_AUDIO_AAC, "OMX.qcom.audio.encoder.aac" },
#endif
    { MEDIA_MIMETYPE_AUDIO_AAC, "AACEncoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.DUCATI1.VIDEO.MPEG4E" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.7x30.video.encoder.mpeg4" },
#ifdef QCOM_HARDWARE
    { MEDIA_MIMETYPE_AUDIO_EVRC,   "OMX.qcom.audio.encoder.evrc" },
    { MEDIA_MIMETYPE_AUDIO_QCELP,  "OMX.qcom.audio.encoder.qcelp13" },
#endif
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.qcom.video.encoder.mpeg4" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.Nvidia.mp4.encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "OMX.SEC.MPEG4.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_MPEG4, "M4vH263Encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.DUCATI1.VIDEO.MPEG4E" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.7x30.video.encoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.qcom.video.encoder.h263" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.Nvidia.h263.encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "OMX.SEC.H263.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_H263, "M4vH263Encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.DUCATI1.VIDEO.H264E" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.7x30.video.encoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.qcom.video.encoder.avc" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.TI.Video.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.Nvidia.h264.encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "OMX.SEC.AVC.Encoder" },
    { MEDIA_MIMETYPE_VIDEO_AVC, "AVCEncoder" },
};

#undef OPTIONAL

#define CODEC_LOGI(x, ...) LOGI("[%s] "x, mComponentName, ##__VA_ARGS__)
#define CODEC_LOGV(x, ...) LOGV("[%s] "x, mComponentName, ##__VA_ARGS__)
#define CODEC_LOGE(x, ...) LOGE("[%s] "x, mComponentName, ##__VA_ARGS__)

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

protected:
    virtual ~OMXCodecObserver() {}

private:
    wp<OMXCodec> mTarget;

    OMXCodecObserver(const OMXCodecObserver &);
    OMXCodecObserver &operator=(const OMXCodecObserver &);
};

#ifdef QCOM_HARDWARE
char *HwAacRoles[]={
"OMX.qcom.audio.decoder.multiaac",
"OMX.qcom.audio.decoder.aac",
};
#endif

static const char *GetCodec(const CodecInfo *info, size_t numInfos,
                            const char *mime, int index) {
    CHECK(index >= 0);
#ifdef QCOM_HARDWARE
    char value[PROPERTY_VALUE_MAX];
#endif

    for(size_t i = 0; i < numInfos; ++i) {
        if (!strcasecmp(mime, info[i].mime)) {
            if (index == 0) {

#ifdef QCOM_HARDWARE
                if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC)){
                    if (property_get("media.aaccodectype", value, NULL)){
                        LOGE("using h/w aac decoder");
                        if((atoi(value) >= 0 )&& (atoi(value) < 2) )
                            return HwAacRoles[atoi(value)];
                        else
                            LOGE("media.aaccodectype value not supported");
                    }
                }
#endif

                return info[i].codec;
            }

            --index;
        }
    }

    return NULL;
}

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

static bool IsSoftwareCodec(const char *componentName) {
#ifdef QCOM_HARDWARE
    if (!strncmp("OMX.google.", componentName, 11)
	    || !strncmp("OMX.PV.", componentName, 7)) {
#else
    if (!strncmp("OMX.google.", componentName, 11)) {
#endif
        return true;
    }

    if (!strncmp("OMX.", componentName, 4)) {
        return false;
    }

    return true;
}

// A sort order in which OMX software codecs are first, followed
// by other (non-OMX) software codecs, followed by everything else.
static int CompareSoftwareCodecsFirst(
        const String8 *elem1, const String8 *elem2) {
    bool isOMX1 = !strncmp(elem1->string(), "OMX.", 4);
    bool isOMX2 = !strncmp(elem2->string(), "OMX.", 4);

    bool isSoftwareCodec1 = IsSoftwareCodec(elem1->string());
    bool isSoftwareCodec2 = IsSoftwareCodec(elem2->string());

    if (isSoftwareCodec1) {
        if (!isSoftwareCodec2) { return -1; }

        if (isOMX1) {
            if (isOMX2) { return 0; }

            return -1;
        } else {
            if (isOMX2) { return 0; }

            return 1;
        }

        return -1;
    }

    if (isSoftwareCodec2) {
        return 1;
    }

    return 0;
}

// static
uint32_t OMXCodec::getComponentQuirks(
        const char *componentName, bool isEncoder) {
    uint32_t quirks = 0;

    if (!strcmp(componentName, "OMX.Nvidia.amr.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.amrwb.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.aac.decoder") ||
         !strcmp(componentName, "OMX.Nvidia.mp3.decoder")) {
        quirks |= kDecoderLiesAboutNumberOfChannels;
    }

    if (!strcmp(componentName, "OMX.TI.MP3.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kDecoderLiesAboutNumberOfChannels;
    }
    if (!strcmp(componentName, "OMX.TI.AAC.decode")) {
        quirks |= kNeedsFlushBeforeDisable;
        quirks |= kRequiresFlushCompleteEmulation;
        quirks |= kSupportsMultipleFramesPerInputBuffer;
    }
#ifdef QCOM_HARDWARE
#ifdef USE_AAC_HW_DEC
    if (!strcmp(componentName, "OMX.qcom.audio.decoder.aac")) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        LOGV("setting kRequiresGlobalFlush for AAC");
        quirks |= kRequiresGlobalFlush;
    }
#endif

    if (!strcmp(componentName, "OMX.qcom.audio.encoder.evrc")) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
    }

    if (!strcmp(componentName, "OMX.qcom.audio.encoder.qcelp13")) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.Qcelp13"))  {
       LOGV("setting kRequiresGlobalFlush for QCELP");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.Qcelp13Hw"))  {
       LOGV("setting kRequiresGlobalFlush for QCELPHw");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.evrc"))  {
       LOGV("setting kRequiresGlobalFlush for EVRC");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.evrchw"))  {
       LOGV("setting kRequiresGlobalFlush for EVRCHw");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.mp3"))  {
       LOGV("setting kRequiresGlobalFlush for MP3");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.wma"))  {
       LOGV("setting kRequiresGlobalFlush for WMA");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.wma10Pro"))  {
       LOGV("setting kRequiresGlobalFlush for WMA10Pro");
       quirks |= kRequiresGlobalFlush;
    }

    if(!strcmp(componentName, "OMX.qcom.audio.decoder.wmaLossLess"))  {
       LOGV("setting kRequiresGlobalFlush for WMALossLess");
       quirks |= kRequiresGlobalFlush;
    }
#endif

    if (!strncmp(componentName, "OMX.qcom.video.encoder.", 23)) {
        quirks |= kRequiresLoadedToIdleAfterAllocation;
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        if (!strncmp(componentName, "OMX.qcom.video.encoder.avc", 26)) {

            // The AVC encoder advertises the size of output buffers
            // based on the input video resolution and assumes
            // the worst/least compression ratio is 0.5. It is found that
            // sometimes, the output buffer size is larger than
            // size advertised by the encoder.
#ifdef QCOM_HARDWARE
            //quirks |= kRequiresLargerEncoderOutputBuffer;
#else
            quirks |= kRequiresLargerEncoderOutputBuffer;
#endif
        }
    }
    if (!strncmp(componentName, "OMX.qcom.7x30.video.encoder.", 28)) {
    }
    if (!strncmp(componentName, "OMX.qcom.video.decoder.", 23)) {
#ifdef QCOM_HARDWARE
        quirks |= kRequiresAllocateBufferOnInputPorts;
#endif
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kDefersOutputBufferAllocation;
    }
#ifdef QCOM_HARDWARE
    if (!strncmp(componentName, "OMX.ittiam.video.decoder.", 25)) {
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kDefersOutputBufferAllocation;
    }
#endif
    if (!strncmp(componentName, "OMX.qcom.7x30.video.decoder.", 28)) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        quirks |= kDefersOutputBufferAllocation;
    }

    if (!strcmp(componentName, "OMX.TI.DUCATI1.VIDEO.DECODER")) {
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
    }

    // FIXME:
    // Remove the quirks after the work is done.
    else if (!strcmp(componentName, "OMX.TI.DUCATI1.VIDEO.MPEG4E") ||
             !strcmp(componentName, "OMX.TI.DUCATI1.VIDEO.H264E")) {

        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
    }
    else if (!strncmp(componentName, "OMX.TI.", 7)) {
        // Apparently I must not use OMX_UseBuffer on either input or
        // output ports on any of the TI components or quote:
        // "(I) may have unexpected problem (sic) which can be timing related
        //  and hard to reproduce."

        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
        if (!strncmp(componentName, "OMX.TI.Video.encoder", 20)) {
            quirks |= kAvoidMemcopyInputRecordingFrames;
        }
    }

    if (!strcmp(componentName, "OMX.TI.Video.Decoder")) {
        quirks |= kInputBufferSizesAreBogus;
    }

    if (!strncmp(componentName, "OMX.SEC.", 8) && !isEncoder) {
        // These output buffers contain no video data, just some
        // opaque information that allows the overlay to display their
        // contents.
        quirks |= kOutputBuffersAreUnreadable;
    }

#ifdef QCOM_HARDWARE
    if(!strcmp(componentName,"OMX.qcom.audio.decoder.ac3")) {
        LOGV("AC3 enabling allocate buffer on input and output ports");
        quirks |= kRequiresAllocateBufferOnInputPorts;
        quirks |= kRequiresAllocateBufferOnOutputPorts;
    }

    if (!strcmp(componentName, "OMX.qcom.audio.decoder.wma")) {
        quirks |= kRequiresWMAProComponent;
    }
#endif

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

        // When requesting software-only codecs, only push software codecs
        // When requesting hardware-only codecs, only push hardware codecs
        // When there is request neither for software-only nor for
        // hardware-only codecs, push all codecs
        if (((flags & kSoftwareCodecsOnly) &&   IsSoftwareCodec(componentName)) ||
            ((flags & kHardwareCodecsOnly) &&  !IsSoftwareCodec(componentName)) ||
            (!(flags & (kSoftwareCodecsOnly | kHardwareCodecsOnly)))) {

            matchingCodecs->push(String8(componentName));
        }
    }

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
        uint32_t flags,
        const sp<ANativeWindow> &nativeWindow) {
    int32_t requiresSecureBuffers;
    if (source->getFormat()->findInt32(
                kKeyRequiresSecureBuffers,
                &requiresSecureBuffers)
            && requiresSecureBuffers) {
        flags |= kIgnoreCodecSpecificData;
        flags |= kUseSecureInputBuffers;
    }

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

    for (size_t i = 0; i < matchingCodecs.size(); ++i) {
        const char *componentNameBase = matchingCodecs[i].string();
        const char *componentName = componentNameBase;

        AString tmp;
        if (flags & kUseSecureInputBuffers) {
            tmp = componentNameBase;
            tmp.append(".secure");

            componentName = tmp.c_str();
        }

#ifdef QCOM_HARDWARE
        sp<MediaSource> softwareCodec;
        if (createEncoder) {
            softwareCodec = InstantiateSoftwareEncoder(componentName, source, meta);
        } else {
            softwareCodec = InstantiateSoftwareDecoder(componentName, source);
		}
        if (softwareCodec != NULL) {
            LOGE("Successfully allocated software codec '%s'", componentName);
            return softwareCodec;
        }

        LOGE("Attempting to allocate OMX node '%s'", componentName);
#else
        if (createEncoder) {
            sp<MediaSource> softwareCodec =
                InstantiateSoftwareEncoder(componentName, source, meta);

            if (softwareCodec != NULL) {
                LOGV("Successfully allocated software codec '%s'", componentName);

                return softwareCodec;
            }
        }

        LOGV("Attempting to allocate OMX node '%s'", componentName);
#endif

        uint32_t quirks = getComponentQuirks(componentNameBase, createEncoder);
#ifdef QCOM_HARDWARE
        if(quirks & kRequiresWMAProComponent)
        {
           int32_t version;
           CHECK(meta->findInt32(kKeyWMAVersion, &version));
           if(version==kTypeWMA)
           {
              componentName = "OMX.qcom.audio.decoder.wma";
           }
           else if(version==kTypeWMAPro)
           {
              componentName= "OMX.qcom.audio.decoder.wma10Pro";
           }
           else if(version==kTypeWMALossLess)
           {
              componentName= "OMX.qcom.audio.decoder.wmaLossLess";
           }
        }
#if USE_AAC_HW_DEC
        int aacformattype = 0;
        int aacLTPType = 0;
        sp<MetaData> metadata = source->getFormat();
        metadata->findInt32(kkeyAacFormatAdif, &aacformattype);
        metadata->findInt32(kkeyAacFormatLtp, &aacLTPType);

        if ((aacformattype == true)|| aacLTPType == true)  {
            LOGE("This is ADIF/LTP clip , so using sw decoder ");
            componentName= "OMX.google.aac.decoder";
        }
#endif
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
                    omx, node, quirks, flags,
                    createEncoder, mime, componentName,
                    source, nativeWindow);

            observer->setCodec(codec);

            err = codec->configureCodec(meta);

            if (err == OK) {
                if (!strcmp("OMX.Nvidia.mpeg2v.decode", componentName)) {
                    codec->mFlags |= kOnlySubmitOneInputBufferAtOneTime;
                }

                return codec;
            }

            LOGV("Failed to configure codec '%s'", componentName);
        }
    }

    return NULL;
}

status_t OMXCodec::parseAVCCodecSpecificData(
        const void *data, size_t size,
#ifdef QCOM_HARDWARE
        unsigned *profile, unsigned *level, const sp<MetaData> &meta) {
#else
        unsigned *profile, unsigned *level) {
#endif
    const uint8_t *ptr = (const uint8_t *)data;

    // verify minimum size and configurationVersion == 1.
    if (size < 7 || ptr[0] != 1) {
        return ERROR_MALFORMED;
    }

    *profile = ptr[1];
    *level = ptr[3];

    // There is decodable content out there that fails the following
    // assertion, let's be lenient for now...
    // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

    size_t lengthSize = 1 + (ptr[4] & 3);

    // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
    // violates it...
    // CHECK((ptr[5] >> 5) == 7);  // reserved

    size_t numSeqParameterSets = ptr[5] & 31;
#ifdef QCOM_HARDWARE
    uint16_t spsSize = (((uint16_t)ptr[6]) << 8)
      + (uint16_t)(ptr[7]);
    CODEC_LOGV(" numSeqParameterSets = %d , spsSize = %d",numSeqParameterSets,spsSize);
    SpsInfo info;
    if ( parseSps( spsSize, ptr + 9, &info ) == OK ) {
      mSPSParsed = true;
      CODEC_LOGV("SPS parsed");
      if (info.mInterlaced) {
	mInterlaceFormatDetected = true;
	meta->setInt32(kKeyUseArbitraryMode, 1);
	CODEC_LOGI("Interlace format detected");
      } else {
	CODEC_LOGI("Non-Interlaced format detected");
      }
    }
    else {
      CODEC_LOGI("ParseSPS could not find if content is interlaced");
      mSPSParsed = false;
      mInterlaceFormatDetected = false;
    }
#endif
    ptr += 6;
    size -= 6;

    for (size_t i = 0; i < numSeqParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    if (size < 1) {
        return ERROR_MALFORMED;
    }

    size_t numPictureParameterSets = *ptr;
    ++ptr;
    --size;

    for (size_t i = 0; i < numPictureParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    return OK;
}

status_t OMXCodec::configureCodec(const sp<MetaData> &meta) {
    LOGV("configureCodec protected=%d",
         (mFlags & kEnableGrallocUsageProtected) ? 1 : 0);

    if (!(mFlags & kIgnoreCodecSpecificData)) {
        uint32_t type;
        const void *data;
        size_t size;
#ifdef QCOM_HARDWARE
        const char *mime_type;
#endif
        if (meta->findData(kKeyESDS, &type, &data, &size)) {
            ESDS esds((const char *)data, size);
            CHECK_EQ(esds.InitCheck(), (status_t)OK);

            const void *codec_specific_data;
            size_t codec_specific_data_size;
            esds.getCodecSpecificInfo(
                    &codec_specific_data, &codec_specific_data_size);

#ifdef QCOM_HARDWARE
            meta->findCString(kKeyMIMEType, &mime_type);
            if (strncmp(mime_type, MEDIA_MIMETYPE_AUDIO_MPEG, 10)) {
                addCodecSpecificData(codec_specific_data,
                        codec_specific_data_size);
            }
#else
            addCodecSpecificData(
                    codec_specific_data, codec_specific_data_size);
#endif
        } else if (meta->findData(kKeyAVCC, &type, &data, &size)) {
            // Parse the AVCDecoderConfigurationRecord

            unsigned profile, level;
            status_t err;
            if ((err = parseAVCCodecSpecificData(
#ifdef QCOM_HARDWARE
                            data, size, &profile, &level, meta)) != OK) {
#else
                            data, size, &profile, &level)) != OK) {
#endif
                LOGE("Malformed AVC codec specific data.");
                return err;
            }

            CODEC_LOGI(
                    "AVC profile = %u (%s), level = %u",
                    profile, AVCProfileToString(profile), level);

            if (!strcmp(mComponentName, "OMX.TI.Video.Decoder")
                && (profile != kAVCProfileBaseline || level > 30)) {
                // This stream exceeds the decoder's capabilities. The decoder
                // does not handle this gracefully and would clobber the heap
                // and wreak havoc instead...

                LOGE("Profile and/or level exceed the decoder's capabilities.");
                return ERROR_UNSUPPORTED;
            }
#ifdef QCOM_HARDWARE
            if(!strcmp(mComponentName, "OMX.google.h264.decoder")
                && (profile != kAVCProfileBaseline)) {
                LOGE("%s does not support profiles > kAVCProfileBaseline", mComponentName);
                // The profile is unsupported by the decoder
                return ERROR_UNSUPPORTED;
            }
#endif

        } else if (meta->findData(kKeyVorbisInfo, &type, &data, &size)) {
            addCodecSpecificData(data, size);

            CHECK(meta->findData(kKeyVorbisBooks, &type, &data, &size));
            addCodecSpecificData(data, size);
#ifdef QCOM_HARDWARE
        } else if (meta->findData(kKeyRawCodecSpecificData, &type, &data, &size)) {
            LOGV("OMXCodec::configureCodec found kKeyRawCodecSpecificData of size %d\n", size);
            addCodecSpecificData(data, size);
        }
#else
        }
#endif
    }

#ifdef QCOM_HARDWARE
    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX, mMIME) ||
        !strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX4, mMIME) ||
        !strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX311, mMIME)) {
        LOGV("Setting the QOMX_VIDEO_PARAM_DIVXTYPE params ");
        QOMX_VIDEO_PARAM_DIVXTYPE paramDivX;
        InitOMXParams(&paramDivX);
        paramDivX.nPortIndex = mIsEncoder ? kPortIndexOutput : kPortIndexInput;
        int32_t DivxVersion = 0;
        CHECK(meta->findInt32(kKeyDivXVersion,&DivxVersion));
        CODEC_LOGV("Divx Version Type %d\n",DivxVersion);

        if(DivxVersion == kTypeDivXVer_4) {
            paramDivX.eFormat = QOMX_VIDEO_DIVXFormat4;
        } else if(DivxVersion == kTypeDivXVer_5) {
            paramDivX.eFormat = QOMX_VIDEO_DIVXFormat5;
        } else if(DivxVersion == kTypeDivXVer_6) {
            paramDivX.eFormat = QOMX_VIDEO_DIVXFormat6;
        } else if(DivxVersion == kTypeDivXVer_3_11 ) {
            paramDivX.eFormat = QOMX_VIDEO_DIVXFormat311;
        } else {
            paramDivX.eFormat = QOMX_VIDEO_DIVXFormatUnused;
        }
        paramDivX.eProfile = (QOMX_VIDEO_DIVXPROFILETYPE)0;//Not used for now.

        paramDivX.pDrmHandle = NULL;
        if (meta->findPointer(kKeyDivXDrm, &paramDivX.pDrmHandle) ) {
            if( paramDivX.pDrmHandle != NULL ) {
                LOGV("This DivX Clip is DRM encrypted, set the DRM handle ");
            }
            else {
                LOGV("This DivX Clip is not DRM encrypted ");
            }
        }
        status_t err =  mOMX->setParameter(mNode,
                         (OMX_INDEXTYPE)OMX_QcomIndexParamVideoDivx,
                         &paramDivX, sizeof(paramDivX));
        if (err!=OK) {
            return err;
        }
    }
#endif

    int32_t bitRate = 0;
    if (mIsEncoder) {
        CHECK(meta->findInt32(kKeyBitRate, &bitRate));
    }
    if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_NB, mMIME)) {
        setAMRFormat(false /* isWAMR */, bitRate);
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AMR_WB, mMIME)) {
        setAMRFormat(true /* isWAMR */, bitRate);
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AAC, mMIME)) {
        int32_t numChannels, sampleRate;
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));

#ifdef QCOM_HARDWARE
        meta->findInt32(kKeyBitRate, &bitRate);
        meta->findInt32(kkeyAacFormatAdif, &mIsAacFormatAdif);
#endif
        status_t err = setAACFormat(numChannels, sampleRate, bitRate);

#ifdef QCOM_HARDWARE
        uint32_t type;
        const void *data;
        size_t size;

        if (meta->findData(kKeyAacCodecSpecificData, &type, &data, &size)) {
            LOGV("OMXCodec:: configureCodec found kKeyAacCodecSpecificData of size %d\n", size);
            addCodecSpecificData(data, size);
        }
#endif

        if (err != OK) {
            CODEC_LOGE("setAACFormat() failed (err = %d)", err);
            return err;
        }
#ifdef QCOM_HARDWARE
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_AC3, mMIME)) {
        return BAD_TYPE;
        /*int32_t numChannels, sampleRate;
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
        setAC3Format(numChannels, sampleRate);*/
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_EVRC, mMIME)) {
        int32_t numChannels, sampleRate;
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
        setEVRCFormat(numChannels, sampleRate, bitRate);
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_QCELP, mMIME)) {
        int32_t numChannels, sampleRate;
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
        setQCELPFormat(numChannels, sampleRate, bitRate);
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_WMA, mMIME))  {
        status_t err = setWMAFormat(meta);
        if(err!=OK){
           return err;
        }
#endif
    } else if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_G711_ALAW, mMIME)
            || !strcasecmp(MEDIA_MIMETYPE_AUDIO_G711_MLAW, mMIME)) {
        // These are PCM-like formats with a fixed sample rate but
        // a variable number of channels.

#ifdef QCOM_HARDWARE
        int32_t numChannels, sampleRate ;
#else
        int32_t numChannels;
#endif
        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
#ifdef QCOM_HARDWARE
        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
#endif

#ifdef QCOM_HARDWARE
        setG711Format(numChannels, sampleRate);
#else
        setG711Format(numChannels);
#endif
    }

    if (!strncasecmp(mMIME, "video/", 6)) {
#ifdef QCOM_HARDWARE
        if (mThumbnailMode) {
            LOGV("Enabling thumbnail mode.");
            QOMX_ENABLETYPE enableType;
            OMX_INDEXTYPE indexType;

            status_t err = mOMX->getExtensionIndex(
                mNode, OMX_QCOM_INDEX_PARAM_VIDEO_SYNCFRAMEDECODINGMODE, &indexType);

            CHECK_EQ(err, (status_t)OK);

            enableType.bEnable = OMX_TRUE;

            err = mOMX->setParameter(
                    mNode, indexType, &enableType, sizeof(enableType));
            CHECK_EQ(err, (status_t)OK);

            LOGV("Thumbnail mode enabled.");
        }
#endif

        if (mIsEncoder) {
            setVideoInputFormat(mMIME, meta);
        } else {
            int32_t width, height;
            bool success = meta->findInt32(kKeyWidth, &width);
            success = success && meta->findInt32(kKeyHeight, &height);
            CHECK(success);
            status_t err = setVideoOutputFormat(
                    mMIME, width, height);

            if (err != OK) {
                return err;
            }

#ifdef QCOM_HARDWARE
            int32_t useArbitraryMode = 0;
            success = meta->findInt32(kKeyUseArbitraryMode, &useArbitraryMode);
            if (success && useArbitraryMode == 1) {
                CODEC_LOGI("Decoder should be in arbitrary mode");
                // Is it required to set OMX_QCOM_FramePacking_Arbitrary ??
            }
            else{
                CODEC_LOGI("Enable frame by frame mode");
                OMX_QCOM_PARAM_PORTDEFINITIONTYPE portFmt;
                portFmt.nPortIndex = kPortIndexInput;
                portFmt.nFramePackingFormat = OMX_QCOM_FramePacking_OnlyOneCompleteFrame;
                err = mOMX->setParameter(
                        mNode, (OMX_INDEXTYPE)OMX_QcomIndexPortDefn, (void *)&portFmt, sizeof(portFmt));
                if(err != OK) {
                    LOGW("Failed to set frame packing format on component");
                }
            }
#endif
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
        setMinBufferSize(kPortIndexInput, (OMX_U32)maxInputSize);
    }

    if (!strcmp(mComponentName, "OMX.TI.AMR.encode")
        || !strcmp(mComponentName, "OMX.TI.WBAMR.encode")
        || !strcmp(mComponentName, "OMX.TI.AAC.encode")) {
        setMinBufferSize(kPortIndexOutput, 8192);  // XXX
    }

    initOutputFormat(meta);
#ifdef QCOM_HARDWARE
    if ((!strncasecmp(mMIME, "audio/", 6)) && (!strncmp(mComponentName, "OMX.qcom.", 9))) {
        OMX_PARAM_SUSPENSIONPOLICYTYPE suspensionPolicy;
        // Suspension policy for the OMX component to honor the Power collapse (TCXO shutdown)
        // Whenever there is a power collapse, OMX component releases the hardware
        // resources and hence enabling TCXO shutdown, reducing power consumption.
        // Return value is ignored, since this is not mandated for all the OMX components.
        memset(&suspensionPolicy,0,sizeof(suspensionPolicy));
        suspensionPolicy.ePolicy = OMX_SuspensionEnabled;

        status_t err = mOMX->setParameter(mNode,
            OMX_IndexParamSuspensionPolicy, &suspensionPolicy, sizeof(suspensionPolicy));
        if ( err != OMX_ErrorNone ) {
            CODEC_LOGV("OMXCodec::configureCodec Problem setting suspension"
                "policy parameters in output port ");
        } else {
            CODEC_LOGV("OMXCodec::configureCodec SUCCESS setting suspension "
                "policy parameters in output port ");
        }
    }
#endif
    if ((mFlags & kClientNeedsFramebuffer)
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

    if (mNativeWindow != NULL
        && !mIsEncoder
        && !strncasecmp(mMIME, "video/", 6)
        && !strncmp(mComponentName, "OMX.", 4)) {
        status_t err = initNativeWindow();
        if (err != OK) {
            return err;
        }
    }

    return OK;
}

void OMXCodec::setMinBufferSize(OMX_U32 portIndex, OMX_U32 size) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    if ((portIndex == kPortIndexInput && (mQuirks & kInputBufferSizesAreBogus))
        || (def.nBufferSize < size)) {
        def.nBufferSize = size;
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

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

        if (!strcmp("OMX.TI.Video.encoder", mComponentName)) {
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

        if (format.eCompressionFormat == compressionFormat
                && format.eColorFormat == colorFormat) {
            found = true;
            break;
        }

        ++index;
        if (index >= kMaxColorFormatSupported) {
            CODEC_LOGE("color format %d or compression format %d is not supported",
                colorFormat, compressionFormat);
            return UNKNOWN_ERROR;
        }
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
        case OMX_TI_COLOR_FormatYUV420PackedSemiPlanar:
        /*
        * FIXME: For the Opaque color format, the frame size does not
        * need to be (w*h*3)/2. It just needs to
        * be larger than certain minimum buffer size. However,
        * currently, this opaque foramt has been tested only on
        * YUV420 formats. If that is changed, then we need to revisit
        * this part in the future
        */
        case OMX_COLOR_FormatAndroidOpaque:
            return (width * height * 3) / 2;

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
        if (!strcasecmp("OMX.TI.Video.encoder", mComponentName)) {
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
        if (portFormat.eColorFormat == colorFormat) {
            CODEC_LOGV("Found supported color format: %d", portFormat.eColorFormat);
            return OK;  // colorFormat is supported!
        }
        ++index;
        portFormat.nIndex = index;

        if (index >= kMaxColorFormatSupported) {
            CODEC_LOGE("More than %ld color formats are supported???", index);
            break;
        }
    }

    CODEC_LOGE("color format %d is not supported", colorFormat);
    return UNKNOWN_ERROR;
}

void OMXCodec::setVideoInputFormat(
        const char *mime, const sp<MetaData>& meta) {

    int32_t width, height, frameRate, bitRate, stride, sliceHeight;
#ifdef QCOM_HARDWARE
    int32_t hfr = 0;

    char value[PROPERTY_VALUE_MAX];
    if ( property_get("encoder.video.bitrate", value, 0) > 0 && atoi(value) > 0){
        LOGV("Setting bit rate to %d", atoi(value));
        meta->setInt32(kKeyBitRate, atoi(value));
    }
#endif

    bool success = meta->findInt32(kKeyWidth, &width);
    success = success && meta->findInt32(kKeyHeight, &height);
    success = success && meta->findInt32(kKeyFrameRate, &frameRate);
    success = success && meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeyStride, &stride);
    success = success && meta->findInt32(kKeySliceHeight, &sliceHeight);
#ifdef QCOM_HARDWARE
    meta->findInt32(kKeyHFR, &hfr);
#endif
    CHECK(success);
    CHECK(stride != 0);

#ifdef QCOM_HARDWARE
    frameRate = hfr?hfr:frameRate;
#endif

    OMX_VIDEO_CODINGTYPE compressionFormat = OMX_VIDEO_CodingUnused;
    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_AVC, mime)) {
        compressionFormat = OMX_VIDEO_CodingAVC;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) {
        compressionFormat = OMX_VIDEO_CodingMPEG4;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime)) {
        compressionFormat = OMX_VIDEO_CodingH263;
#ifdef QCOM_HARDWARE
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX, mime)){
        compressionFormat= (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX4, mime)){
        compressionFormat= (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX311, mime)){
        compressionFormat= (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime)){
        compressionFormat = OMX_VIDEO_CodingWMV;
    } else if (!strcasecmp(MEDIA_MIMETYPE_CONTAINER_MPEG2, mime)){
        compressionFormat = OMX_VIDEO_CodingMPEG2;
#endif
    } else {
        LOGE("Not a supported video mime type: %s", mime);
        CHECK(!"Should not be here. Not a supported video mime type.");
    }

    OMX_COLOR_FORMATTYPE colorFormat;
    CHECK_EQ((status_t)OK, findTargetColorFormat(meta, &colorFormat));

    status_t err;
    OMX_PARAM_PORTDEFINITIONTYPE def;
    OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def.format.video;

    //////////////////////// Input port /////////////////////////
    CHECK_EQ(setVideoPortFormatType(
            kPortIndexInput, OMX_VIDEO_CodingUnused,
            colorFormat), (status_t)OK);

    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

#ifdef QCOM_HARDWARE
    if(strncmp(mComponentName, "OMX.qcom", 8) != 0) {
        def.nBufferSize = getFrameSize(colorFormat,
                stride > 0? stride: -stride, sliceHeight);
    }
#else
    def.nBufferSize = getFrameSize(colorFormat,
            stride > 0? stride: -stride, sliceHeight);
#endif

    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;
    video_def->nStride = stride;
    video_def->nSliceHeight = sliceHeight;
    video_def->xFramerate = (frameRate << 16);  // Q16 format
    video_def->eCompressionFormat = OMX_VIDEO_CodingUnused;
    video_def->eColorFormat = colorFormat;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    //////////////////////// Output port /////////////////////////
    CHECK_EQ(setVideoPortFormatType(
            kPortIndexOutput, compressionFormat, OMX_COLOR_FormatUnused),
            (status_t)OK);
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    CHECK_EQ(err, (status_t)OK);
    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainVideo);

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;
#ifdef QCOM_HARDWARE
    video_def->xFramerate = (frameRate << 16);
#else
    video_def->xFramerate = 0;      // No need for output port
#endif
    video_def->nBitrate = bitRate;  // Q16 format
    video_def->eCompressionFormat = compressionFormat;
    video_def->eColorFormat = OMX_COLOR_FormatUnused;
    if (mQuirks & kRequiresLargerEncoderOutputBuffer) {
        // Increases the output buffer size
        def.nBufferSize = ((def.nBufferSize * 3) >> 1);
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    /////////////////// Codec-specific ////////////////////////
    switch (compressionFormat) {
        case OMX_VIDEO_CodingMPEG4:
        {
            CHECK_EQ(setupMPEG4EncoderParameters(meta), (status_t)OK);
            break;
        }

        case OMX_VIDEO_CodingH263:
            CHECK_EQ(setupH263EncoderParameters(meta), (status_t)OK);
            break;

        case OMX_VIDEO_CodingAVC:
        {
            CHECK_EQ(setupAVCEncoderParameters(meta), (status_t)OK);
            break;
        }

        default:
            CHECK(!"Support for this compressionFormat to be implemented.");
            break;
    }

#ifdef QCOM_HARDWARE
    //If compression is AVC and 3D encoding is requested, we'll inform the encoder
    int32_t want3D = 0;
    if (meta->findInt32(kKey3D, &want3D) && want3D) {
        if (compressionFormat == OMX_VIDEO_CodingAVC) {
            OMX_QCOM_FRAME_PACK_ARRANGEMENT fpa;

            fpa.id = 0;
            fpa.cancel_flag = 0;
            fpa.type = 3; //Left-Right arrangement
            fpa.quincunx_sampling_flag = 0;
            fpa.content_interpretation_type = 0;
            fpa.spatial_flipping_flag = 0;
            fpa.frame0_flipped_flag = 0;
            fpa.field_views_flag = 0;
            fpa.current_frame_is_frame0_flag = 0;
            fpa.frame0_self_contained_flag = 0;
            fpa.frame1_self_contained_flag = 0;
            fpa.frame0_grid_position_x = 0;
            fpa.frame0_grid_position_y = 0;
            fpa.frame1_grid_position_x = 0;
            fpa.frame1_grid_position_y = 0;
            fpa.reserved_byte = 0;
            fpa.repetition_period = 0;
            fpa.extension_flag = 0;

            err = mOMX->setConfig(mNode,
                    (OMX_INDEXTYPE)OMX_QcomIndexConfigVideoFramePackingArrangement,
                    &fpa, (size_t)sizeof(fpa));
        } else {
            LOGE("Only H264 supports SEI info, setting 3d failed");
            err = ERROR_UNSUPPORTED;
        }

        if (err != OMX_ErrorNone)
            LOGE("Enabling fpa for 3d failed");
        else
            LOGI("Enabled 3d");

    }
#endif
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
#ifdef QCOM_HARDWARE
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
    CHECK_EQ(err, (status_t)OK);

#ifdef QCOM_HARDWARE
    OMX_VIDEO_CONTROLRATETYPE controlRate = OMX_Video_ControlRateVariable;
    char value[PROPERTY_VALUE_MAX];

    if ( mIsEncoder && property_get("encoder.video.rc", value, NULL ) > 0 ) {
        int rv = atoi( value );
        switch ( rv ) {
        case 0:
            controlRate = OMX_Video_ControlRateDisable;
            break;
        case 1:
            controlRate = OMX_Video_ControlRateVariable;
            break;
        case 2:
            controlRate = OMX_Video_ControlRateConstant;
            break;
        case 3:
            controlRate = OMX_Video_ControlRateVariableSkipFrames;
            break;
        case 4:
            controlRate = OMX_Video_ControlRateConstantSkipFrames;
            break;
        default:
            LOGW("Unknown rate control value, assume default");
            break;
        }
    }

    bitrateType.eControlRate = controlRate;
#else
    bitrateType.eControlRate = OMX_Video_ControlRateVariable;
#endif
    bitrateType.nTargetBitrate = bitRate;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoBitrate,
            &bitrateType, sizeof(bitrateType));
    CHECK_EQ(err, (status_t)OK);
    return OK;
}

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
#ifdef QCOM_HARDWARE
            CODEC_LOGV("profile: %d, level %d is supported",
                       profile, level);
#endif
            return OK;
        }
    }

    CODEC_LOGE("Target profile (%d) and level (%d) is not supported",
            profile, level);
    return BAD_VALUE;
}

status_t OMXCodec::setupH263EncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;
#ifdef QCOM_HARDWARE
    int32_t hfr = 0;
#endif
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeyFrameRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
#ifdef QCOM_HARDWARE
    meta->findInt32(kKeyHFR, &hfr);
#endif
    CHECK(success);
    OMX_VIDEO_PARAM_H263TYPE h263type;
    InitOMXParams(&h263type);
    h263type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
    CHECK_EQ(err, (status_t)OK);

#ifdef QCOM_HARDWARE
    frameRate = hfr ? hfr : frameRate;
#endif
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

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoH263, &h263type, sizeof(h263type));
    CHECK_EQ(err, (status_t)OK);

    CHECK_EQ(setupBitRate(bitRate), (status_t)OK);
    CHECK_EQ(setupErrorCorrectionParameters(), (status_t)OK);

    return OK;
}

#ifdef QCOM_HARDWARE
status_t OMXCodec::setupMPEG2EncoderParameters(const sp<MetaData>& meta) {
    return OK;
}
#endif

status_t OMXCodec::setupMPEG4EncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;
#ifdef QCOM_HARDWARE
    int32_t hfr = 0;
#endif
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeyFrameRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
#ifdef QCOM_HARDWARE
    meta->findInt32(kKeyHFR, &hfr);
#endif
    CHECK(success);
    OMX_VIDEO_PARAM_MPEG4TYPE mpeg4type;
    InitOMXParams(&mpeg4type);
    mpeg4type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));
    CHECK_EQ(err, (status_t)OK);

    mpeg4type.nSliceHeaderSpacing = 0;
    mpeg4type.bSVH = OMX_FALSE;
    mpeg4type.bGov = OMX_FALSE;

    mpeg4type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

#ifdef QCOM_HARDWARE
    frameRate = hfr ? hfr : frameRate;
#endif
    mpeg4type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
    if (mpeg4type.nPFrames == 0) {
        mpeg4type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
    }
    mpeg4type.nBFrames = 0;
    mpeg4type.nIDCVLCThreshold = 0;
    mpeg4type.bACPred = OMX_TRUE;
    mpeg4type.nMaxPacketSize = 256;
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

#ifdef QCOM_HARDWARE
    if (mpeg4type.eProfile > OMX_VIDEO_MPEG4ProfileSimple) {
        mpeg4type.nAllowedPictureTypes |= OMX_VIDEO_PictureTypeB;
        mpeg4type.nBFrames = 1;
    }
#endif

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoMpeg4, &mpeg4type, sizeof(mpeg4type));
    CHECK_EQ(err, (status_t)OK);

    CHECK_EQ(setupBitRate(bitRate), (status_t)OK);
    CHECK_EQ(setupErrorCorrectionParameters(), (status_t)OK);

    return OK;
}

status_t OMXCodec::setupAVCEncoderParameters(const sp<MetaData>& meta) {
    int32_t iFramesInterval, frameRate, bitRate;
#ifdef QCOM_HARDWARE
    int32_t hfr = 0;
#endif
    bool success = meta->findInt32(kKeyBitRate, &bitRate);
    success = success && meta->findInt32(kKeyFrameRate, &frameRate);
    success = success && meta->findInt32(kKeyIFramesInterval, &iFramesInterval);
#ifdef QCOM_HARDWARE
    meta->findInt32(kKeyHFR, &hfr);
#endif
    CHECK(success);

    OMX_VIDEO_PARAM_AVCTYPE h264type;
    InitOMXParams(&h264type);
    h264type.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
    CHECK_EQ(err, (status_t)OK);

    h264type.nAllowedPictureTypes =
        OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP;

    // Check profile and level parameters
    CodecProfileLevel defaultProfileLevel, profileLevel;
    defaultProfileLevel.mProfile = h264type.eProfile;
    defaultProfileLevel.mLevel = h264type.eLevel;
    err = getVideoProfileLevel(meta, defaultProfileLevel, profileLevel);
    if (err != OK) return err;
    h264type.eProfile = static_cast<OMX_VIDEO_AVCPROFILETYPE>(profileLevel.mProfile);
    h264type.eLevel = static_cast<OMX_VIDEO_AVCLEVELTYPE>(profileLevel.mLevel);

#ifdef QCOM_HARDWARE
    frameRate = hfr ? hfr : frameRate;
#endif

    // FIXME:
    // Remove the workaround after the work in done.
    if (!strncmp(mComponentName, "OMX.TI.DUCATI1", 14)) {
        h264type.eProfile = OMX_VIDEO_AVCProfileBaseline;
    }

    if (h264type.eProfile == OMX_VIDEO_AVCProfileBaseline) {
        h264type.nSliceHeaderSpacing = 0;
        h264type.bUseHadamard = OMX_TRUE;
        h264type.nRefFrames = 1;
        h264type.nBFrames = 0;
        h264type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
        if (h264type.nPFrames == 0) {
            h264type.nAllowedPictureTypes = OMX_VIDEO_PictureTypeI;
        }
        h264type.nRefIdx10ActiveMinus1 = 0;
        h264type.nRefIdx11ActiveMinus1 = 0;
        h264type.bEntropyCodingCABAC = OMX_FALSE;
        h264type.bWeightedPPrediction = OMX_FALSE;
        h264type.bconstIpred = OMX_FALSE;
        h264type.bDirect8x8Inference = OMX_FALSE;
        h264type.bDirectSpatialTemporal = OMX_FALSE;
        h264type.nCabacInitIdc = 0;
    }

#ifdef QCOM_HARDWARE
    if (h264type.eProfile > OMX_VIDEO_AVCProfileBaseline) {
        h264type.nPFrames = setPFramesSpacing(iFramesInterval, frameRate);
        h264type.nBFrames = 1;
    }
#endif

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

    if (!strcasecmp("OMX.Nvidia.h264.encoder", mComponentName)) {
        h264type.eLevel = OMX_VIDEO_AVCLevelMax;
    }

    err = mOMX->setParameter(
            mNode, OMX_IndexParamVideoAvc, &h264type, sizeof(h264type));
    CHECK_EQ(err, (status_t)OK);

    CHECK_EQ(setupBitRate(bitRate), (status_t)OK);

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
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_VPX, mime)) {
        compressionFormat = OMX_VIDEO_CodingVPX;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG2, mime)) {
        compressionFormat = OMX_VIDEO_CodingMPEG2;
#ifdef QCOM_HARDWARE
    } else if(!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX, mime)) {
        compressionFormat = (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if(!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX311, mime)) {
        compressionFormat = (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if(!strcasecmp(MEDIA_MIMETYPE_VIDEO_DIVX4, mime)) {
        compressionFormat = (OMX_VIDEO_CODINGTYPE)QOMX_VIDEO_CodingDivx;
    } else if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_WMV, mime)){
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

#ifdef QCOM_HARDWARE
    //Enable decoder to report if there is any SEI data
    //(supported only by H264)
    if (compressionFormat == OMX_VIDEO_CodingAVC)
    {
        QOMX_ENABLETYPE enable_sei_reporting;
        enable_sei_reporting.bEnable = OMX_TRUE;

        err = mOMX->setParameter(
                mNode, (OMX_INDEXTYPE)OMX_QcomIndexParamFrameInfoExtraData,
                &enable_sei_reporting, (size_t)sizeof(enable_sei_reporting));

        if (err != OK)
            LOGV("Not supported parameter OMX_QcomIndexParamFrameInfoExtraData");
    }
#endif

#if 1
    {
        OMX_VIDEO_PARAM_PORTFORMATTYPE format;
        InitOMXParams(&format);
        format.nPortIndex = kPortIndexOutput;
#ifdef QCOM_HARDWARE
        if (!strncmp(mComponentName, "OMX.qcom",8)) {
            int32_t reqdColorFormat = ColorFormatInfo::getPreferredColorFormat(mOMXLivesLocally);
            for(format.nIndex = 0;
                    (OK == mOMX->getParameter(mNode, OMX_IndexParamVideoPortFormat, &format, sizeof(format)));
                    format.nIndex++) {
                if(format.eColorFormat == reqdColorFormat)
                    break;
            }
        } else
#endif
        format.nIndex = 0;

#ifdef QCOM_HARDWARE
        CODEC_LOGV("Video O/P format.nIndex 0x%x",format.nIndex);
        CODEC_LOGE("Video O/P format.eColorFormat 0x%x",format.eColorFormat);
#endif

        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamVideoPortFormat,
                &format, sizeof(format));
        CHECK_EQ(err, (status_t)OK);
        CHECK_EQ((int)format.eCompressionFormat, (int)OMX_VIDEO_CodingUnused);

        CHECK(format.eColorFormat == OMX_COLOR_FormatYUV420Planar
               || format.eColorFormat == OMX_COLOR_FormatYUV420SemiPlanar
               || format.eColorFormat == OMX_COLOR_FormatCbYCrY
               || format.eColorFormat == OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
#ifdef QCOM_HARDWARE
               || format.eColorFormat == OMX_QCOM_COLOR_FormatYVU420SemiPlanar
               || format.eColorFormat == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka);
#else
               || format.eColorFormat == OMX_QCOM_COLOR_FormatYVU420SemiPlanar);
#endif

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

    CHECK_EQ(err, (status_t)OK);

#if 1
    // XXX Need a (much) better heuristic to compute input buffer sizes.
    const size_t X = 64 * 1024;
    if (def.nBufferSize < X) {
        def.nBufferSize = X;
    }
#endif

    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainVideo);

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
    CHECK_EQ(err, (status_t)OK);
    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainVideo);

#if 0
    def.nBufferSize =
        (((width + 15) & -16) * ((height + 15) & -16) * 3) / 2;  // YUV420
#endif

    video_def->nFrameWidth = width;
    video_def->nFrameHeight = height;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    return err;
}

OMXCodec::OMXCodec(
        const sp<IOMX> &omx, IOMX::node_id node,
        uint32_t quirks, uint32_t flags,
        bool isEncoder,
        const char *mime,
        const char *componentName,
        const sp<MediaSource> &source,
        const sp<ANativeWindow> &nativeWindow)
    : mOMX(omx),
      mOMXLivesLocally(omx->livesLocally(getpid())),
      mNode(node),
      mQuirks(quirks),
      mFlags(flags),
      mIsEncoder(isEncoder),
      mMIME(strdup(mime)),
      mComponentName(strdup(componentName)),
      mSource(source),
      mCodecSpecificDataIndex(0),
      mState(LOADED),
      mInitialBufferSubmit(true),
      mSignalledEOS(false),
#ifdef QCOM_HARDWARE
      mFinalStatus(OK),
#endif
      mNoMoreOutputData(false),
      mOutputPortSettingsHaveChanged(false),
      mSeekTimeUs(-1),
      mSeekMode(ReadOptions::SEEK_CLOSEST_SYNC),
      mTargetTimeUs(-1),
      mOutputPortSettingsChangedPending(false),
      mLeftOverBuffer(NULL),
      mPaused(false),
#ifdef QCOM_HARDWARE
      mIsAacFormatAdif(0),
      mInterlaceFormatDetected(false),
      mSPSParsed(false),
      bInvalidState(false),
      latenessUs(0),
      mInterlaceFrame(0),
      LC_level(0),
      mThumbnailMode(false),
      m3DVideoDetected(false),
#endif
      mNativeWindow(
              (!strncmp(componentName, "OMX.google.", 11)
              || !strcmp(componentName, "OMX.Nvidia.mpeg2v.decode"))
                        ? NULL : nativeWindow) {
#ifdef QCOM_HARDWARE
    parseFlags();
#endif
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
        { MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_I,
            "audio_decoder.mp1", "audio_encoder.mp1" },
        { MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II,
            "audio_decoder.mp2", "audio_encoder.mp2" },
        { MEDIA_MIMETYPE_AUDIO_MPEG,
            "audio_decoder.mp3", "audio_encoder.mp3" },
        { MEDIA_MIMETYPE_AUDIO_AMR_NB,
            "audio_decoder.amrnb", "audio_encoder.amrnb" },
        { MEDIA_MIMETYPE_AUDIO_AMR_WB,
            "audio_decoder.amrwb", "audio_encoder.amrwb" },
        { MEDIA_MIMETYPE_AUDIO_AAC,
            "audio_decoder.aac", "audio_encoder.aac" },
        { MEDIA_MIMETYPE_AUDIO_VORBIS,
            "audio_decoder.vorbis", "audio_encoder.vorbis" },
#ifdef QCOM_HARDWARE
        { MEDIA_MIMETYPE_AUDIO_EVRC,
            "audio_decoder.evrchw", "audio_encoder.evrc" },
        { MEDIA_MIMETYPE_AUDIO_QCELP,
            "audio_decoder,qcelp13Hw", "audio_encoder.qcelp13" },
#endif
        { MEDIA_MIMETYPE_VIDEO_AVC,
            "video_decoder.avc", "video_encoder.avc" },
        { MEDIA_MIMETYPE_VIDEO_MPEG4,
            "video_decoder.mpeg4", "video_encoder.mpeg4" },
        { MEDIA_MIMETYPE_VIDEO_H263,
            "video_decoder.h263", "video_encoder.h263" },
#ifdef QCOM_HARDWARE
        { MEDIA_MIMETYPE_VIDEO_DIVX,
            "video_decoder.divx", NULL },
        { MEDIA_MIMETYPE_AUDIO_AC3,
            "audio_decoder.ac3", NULL },
        { MEDIA_MIMETYPE_VIDEO_DIVX311,
            "video_decoder.divx", NULL },
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

    CHECK(mState == LOADED || mState == ERROR || mState == LOADED_TO_IDLE);

    status_t err = mOMX->freeNode(mNode);
    CHECK_EQ(err, (status_t)OK);

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

    CHECK_EQ((int)mState, (int)LOADED);

    status_t err;
    if (!(mQuirks & kRequiresLoadedToIdleAfterAllocation)) {
        err = mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
        CHECK_EQ(err, (status_t)OK);
        setState(LOADED_TO_IDLE);
    }

    err = allocateBuffers();
    if (err != (status_t)OK) {
#ifdef QCOM_HARDWARE
        CODEC_LOGE("Allocate Buffer failed - error = %d", err);
        setState(ERROR);
#endif
        return err;
    }

    if (mQuirks & kRequiresLoadedToIdleAfterAllocation) {
        err = mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
        CHECK_EQ(err, (status_t)OK);

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
#ifdef QCOM_HARDWARE
        || state == PAUSING
        || state == FLUSHING
#endif
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
    if (mNativeWindow != NULL && portIndex == kPortIndexOutput) {
        return allocateOutputBuffersFromNativeWindow();
    }

    if ((mFlags & kEnableGrallocUsageProtected) && portIndex == kPortIndexOutput) {
        LOGE("protected output buffers must be stent to an ANativeWindow");
        return PERMISSION_DENIED;
    }

    status_t err = OK;
    if ((mFlags & kStoreMetaDataInVideoBuffers)
            && portIndex == kPortIndexInput) {
#ifdef QCOM_HARDWARE
        LOGW("Trying to enable metadata mode on encoder");
#endif
        err = mOMX->storeMetaDataInBuffers(mNode, kPortIndexInput, OMX_TRUE);
        if (err != OK) {
            LOGE("Storing meta data in video buffers is not supported");
            return err;
        }
    }

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));

    if (err != OK) {
        return err;
    }

#ifdef QCOM_HARDWARE
    if (mFlags & kUseMinBufferCount) {
        def.nBufferCountActual = def.nBufferCountMin;
        if (!mIsEncoder) {
                if (portIndex == kPortIndexOutput) {
                    def.nBufferCountActual += 2;
                }else {
                    def.nBufferCountActual += 1;
                }
        }
        err = mOMX->setParameter(
                    mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        if (err != OK) {
            CODEC_LOGE("setting nBufferCountActual to %lu failed: %d",
                    def.nBufferCountActual, err);
            return err;
        }
    }
#endif

    CODEC_LOGV("allocating %lu buffers of size %lu on %s port",
            def.nBufferCountActual, def.nBufferSize,
            portIndex == kPortIndexInput ? "input" : "output");

    size_t totalSize = def.nBufferCountActual * def.nBufferSize;
    mDealer[portIndex] = new MemoryDealer(totalSize, "OMXCodec");

    for (OMX_U32 i = 0; i < def.nBufferCountActual; ++i) {
        sp<IMemory> mem = mDealer[portIndex]->allocate(def.nBufferSize);
        CHECK(mem.get() != NULL);

        BufferInfo info;
        info.mData = NULL;
        info.mSize = def.nBufferSize;

        IOMX::buffer_id buffer;
        if (portIndex == kPortIndexInput
                && ((mQuirks & kRequiresAllocateBufferOnInputPorts)
                    || (mFlags & kUseSecureInputBuffers))) {
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
            if (mOMXLivesLocally) {
                mem.clear();

                err = mOMX->allocateBuffer(
                        mNode, portIndex, def.nBufferSize, &buffer,
                        &info.mData);
            } else {
                err = mOMX->allocateBufferWithBackup(
                        mNode, portIndex, mem, &buffer);
            }
        } else {
            err = mOMX->useBuffer(mNode, portIndex, mem, &buffer);
        }

        if (err != OK) {
            LOGE("allocate_buffer_with_backup failed");
            return err;
        }

        if (mem != NULL) {
            info.mData = mem->pointer();
        }

        info.mBuffer = buffer;
        info.mStatus = OWNED_BY_US;
        info.mMem = mem;
        info.mMediaBuffer = NULL;

        if (portIndex == kPortIndexOutput) {
            if (!(mOMXLivesLocally
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

    if (portIndex == kPortIndexInput && (mFlags & kUseSecureInputBuffers)) {
        Vector<MediaBuffer *> buffers;
        for (size_t i = 0; i < def.nBufferCountActual; ++i) {
            const BufferInfo &info = mPortBuffers[kPortIndexInput].itemAt(i);

            MediaBuffer *mbuf = new MediaBuffer(info.mData, info.mSize);
            buffers.push(mbuf);
        }

        status_t err = mSource->setBuffers(buffers);

        if (err != OK) {
            for (size_t i = 0; i < def.nBufferCountActual; ++i) {
                buffers.editItemAt(i)->release();
            }
            buffers.clear();

            CODEC_LOGE(
                    "Codec requested to use secure input buffers but "
                    "upstream source didn't support that.");

            return err;
        }
    }

    return OK;
}

status_t OMXCodec::applyRotation() {
    sp<MetaData> meta = mSource->getFormat();

    int32_t rotationDegrees;
    if (!meta->findInt32(kKeyRotation, &rotationDegrees)) {
        rotationDegrees = 0;
    }

    uint32_t transform;
    switch (rotationDegrees) {
        case 0: transform = 0; break;
        case 90: transform = HAL_TRANSFORM_ROT_90; break;
        case 180: transform = HAL_TRANSFORM_ROT_180; break;
        case 270: transform = HAL_TRANSFORM_ROT_270; break;
        default: transform = 0; break;
    }

    status_t err = OK;

    if (transform) {
        err = native_window_set_buffers_transform(
                mNativeWindow.get(), transform);
    }

    return err;
}

status_t OMXCodec::allocateOutputBuffersFromNativeWindow() {
    // Get the number of buffers needed.
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    if (err != OK) {
        return err;
    }

    err = native_window_set_scaling_mode(mNativeWindow.get(),
            NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);

    if (err != OK) {
        return err;
    }

#ifdef QCOM_HARDWARE
    int format = (def.format.video.eColorFormat ==
                  QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka)?
                 HAL_PIXEL_FORMAT_YCbCr_420_SP_TILED : def.format.video.eColorFormat;
    if(def.format.video.eColorFormat == OMX_QCOM_COLOR_FormatYVU420SemiPlanar)
        format = HAL_PIXEL_FORMAT_YCrCb_420_SP;

    format ^= (mInterlaceFormatDetected ? HAL_PIXEL_FORMAT_INTERLACE : 0);
#endif

#ifdef QCOM_HARDWARE
    err = native_window_set_buffers_geometry(
            mNativeWindow.get(),
            def.format.video.nStride,
            def.format.video.nSliceHeight,
            format);
#else
    err = native_window_set_buffers_geometry(
            mNativeWindow.get(),
            def.format.video.nFrameWidth,
            def.format.video.nFrameHeight,
            def.format.video.eColorFormat);
#endif

    if (err != 0) {
        LOGE("native_window_set_buffers_geometry failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

#ifdef QCOM_HARDWARE
    // Crop the native window to the proper display resolution
    int32_t left, top, right, bottom;
    CHECK(mOutputFormat->findRect(
                kKeyCropRect,
                &left, &top, &right, &bottom));

    android_native_rect_t crop;
    crop.left = left;
    crop.top = top;
    crop.right = right;
    crop.bottom = bottom;

    err = native_window_set_crop(mNativeWindow.get(), &crop);
    if (err != OK) {
        LOGE("native_window_set_crop failed: %s (%d)", strerror(-err), -err);
    }
#endif

    err = applyRotation();
    if (err != OK) {
        return err;
    }

    // Set up the native window.
    OMX_U32 usage = 0;
    err = mOMX->getGraphicBufferUsage(mNode, kPortIndexOutput, &usage);
    if (err != 0) {
        LOGW("querying usage flags from OMX IL component failed: %d", err);
        // XXX: Currently this error is logged, but not fatal.
        usage = 0;
    }
    if (mFlags & kEnableGrallocUsageProtected) {
        usage |= GRALLOC_USAGE_PROTECTED;
    }

    // Make sure to check whether either Stagefright or the video decoder
    // requested protected buffers.
    if (usage & GRALLOC_USAGE_PROTECTED) {
        // Verify that the ANativeWindow sends images directly to
        // SurfaceFlinger.
        int queuesToNativeWindow = 0;
        err = mNativeWindow->query(
                mNativeWindow.get(), NATIVE_WINDOW_QUEUES_TO_WINDOW_COMPOSER,
                &queuesToNativeWindow);
        if (err != 0) {
            LOGE("error authenticating native window: %d", err);
            return err;
        }
        if (queuesToNativeWindow != 1) {
            LOGE("native window could not be authenticated");
            return PERMISSION_DENIED;
        }
    }

    LOGV("native_window_set_usage usage=0x%lx", usage);
    err = native_window_set_usage(
            mNativeWindow.get(), usage | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP);
    if (err != 0) {
        LOGE("native_window_set_usage failed: %s (%d)", strerror(-err), -err);
        return err;
    }

    int minUndequeuedBufs = 0;
    err = mNativeWindow->query(mNativeWindow.get(),
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, &minUndequeuedBufs);
    if (err != 0) {
        LOGE("NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS query failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    // XXX: Is this the right logic to use?  It's not clear to me what the OMX
    // buffer counts refer to - how do they account for the renderer holding on
    // to buffers?
    if (def.nBufferCountActual < def.nBufferCountMin + minUndequeuedBufs) {
        OMX_U32 newBufferCount = def.nBufferCountMin + minUndequeuedBufs;
        def.nBufferCountActual = newBufferCount;
        err = mOMX->setParameter(
                mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
        if (err != OK) {
            CODEC_LOGE("setting nBufferCountActual to %lu failed: %d",
                    newBufferCount, err);
            return err;
        }
    }

    err = native_window_set_buffer_count(
            mNativeWindow.get(), def.nBufferCountActual);
    if (err != 0) {
        LOGE("native_window_set_buffer_count failed: %s (%d)", strerror(-err),
                -err);
        return err;
    }

#ifdef QCOM_HARDWARE
    err = mNativeWindow.get()->perform(mNativeWindow.get(),
                             NATIVE_WINDOW_SET_BUFFERS_SIZE, def.nBufferSize);
    if (err != 0) {
        LOGE("native_window_set_buffers_size failed: %s (%d)", strerror(-err),
                -err);
        return err;
    }
#endif
    CODEC_LOGV("allocating %lu buffers from a native window of size %lu on "
            "output port", def.nBufferCountActual, def.nBufferSize);

    // Dequeue buffers and send them to OMX
    for (OMX_U32 i = 0; i < def.nBufferCountActual; i++) {
        ANativeWindowBuffer* buf;
        err = mNativeWindow->dequeueBuffer(mNativeWindow.get(), &buf);
        if (err != 0) {
            LOGE("dequeueBuffer failed: %s (%d)", strerror(-err), -err);
            break;
        }

#ifdef QCOM_HARDWARE
        private_handle_t *handle = (private_handle_t *)buf->handle;
        if(!handle) {
                LOGE("Native Buffer handle is NULL");
                break;
        }
        CHECK_EQ(def.nBufferSize, handle->size); //otherwise it might cause memory corruption issues. It may fail because of alignment or extradata.
#endif

        sp<GraphicBuffer> graphicBuffer(new GraphicBuffer(buf, false));
        BufferInfo info;
        info.mData = NULL;
        info.mSize = def.nBufferSize;
        info.mStatus = OWNED_BY_US;
        info.mMem = NULL;
        info.mMediaBuffer = new MediaBuffer(graphicBuffer);
        info.mMediaBuffer->setObserver(this);
        mPortBuffers[kPortIndexOutput].push(info);

        IOMX::buffer_id bufferId;
        err = mOMX->useGraphicBuffer(mNode, kPortIndexOutput, graphicBuffer,
                &bufferId);
        if (err != 0) {
            CODEC_LOGE("registering GraphicBuffer with OMX IL component "
                    "failed: %d", err);
            break;
        }

        mPortBuffers[kPortIndexOutput].editItemAt(i).mBuffer = bufferId;

        CODEC_LOGV("registered graphic buffer with ID %p (pointer = %p)",
                bufferId, graphicBuffer.get());
    }

    OMX_U32 cancelStart;
    OMX_U32 cancelEnd;
    if (err != 0) {
        // If an error occurred while dequeuing we need to cancel any buffers
        // that were dequeued.
        cancelStart = 0;
        cancelEnd = mPortBuffers[kPortIndexOutput].size();
    } else {
        // Return the last two buffers to the native window.
        cancelStart = def.nBufferCountActual - minUndequeuedBufs;
        cancelEnd = def.nBufferCountActual;
    }

    for (OMX_U32 i = cancelStart; i < cancelEnd; i++) {
        BufferInfo *info = &mPortBuffers[kPortIndexOutput].editItemAt(i);
        cancelBufferToNativeWindow(info);
    }

    return err;
}

status_t OMXCodec::cancelBufferToNativeWindow(BufferInfo *info) {
    CHECK_EQ((int)info->mStatus, (int)OWNED_BY_US);
    CODEC_LOGV("Calling cancelBuffer on buffer %p", info->mBuffer);
    int err = mNativeWindow->cancelBuffer(
        mNativeWindow.get(), info->mMediaBuffer->graphicBuffer().get());
    if (err != 0) {
      CODEC_LOGE("cancelBuffer failed w/ error 0x%08x", err);

      setState(ERROR);
      return err;
    }
    info->mStatus = OWNED_BY_NATIVE_WINDOW;
    return OK;
}

OMXCodec::BufferInfo* OMXCodec::dequeueBufferFromNativeWindow() {
    // Dequeue the next buffer from the native window.
    ANativeWindowBuffer* buf;
    int err = mNativeWindow->dequeueBuffer(mNativeWindow.get(), &buf);
    if (err != 0) {
      CODEC_LOGE("dequeueBuffer failed w/ error 0x%08x", err);

      setState(ERROR);
      return 0;
    }

    // Determine which buffer we just dequeued.
    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    BufferInfo *bufInfo = 0;
    for (size_t i = 0; i < buffers->size(); i++) {
      sp<GraphicBuffer> graphicBuffer = buffers->itemAt(i).
          mMediaBuffer->graphicBuffer();
      if (graphicBuffer->handle == buf->handle) {
        bufInfo = &buffers->editItemAt(i);
        break;
      }
    }

    if (bufInfo == 0) {
        CODEC_LOGE("dequeued unrecognized buffer: %p", buf);

        setState(ERROR);
        return 0;
    }

    // The native window no longer owns the buffer.
    CHECK_EQ((int)bufInfo->mStatus, (int)OWNED_BY_NATIVE_WINDOW);
    bufInfo->mStatus = OWNED_BY_US;

    return bufInfo;
}

status_t OMXCodec::pushBlankBuffersToNativeWindow() {
    status_t err = NO_ERROR;
    ANativeWindowBuffer* anb = NULL;
    int numBufs = 0;
    int minUndequeuedBufs = 0;

    // We need to reconnect to the ANativeWindow as a CPU client to ensure that
    // no frames get dropped by SurfaceFlinger assuming that these are video
    // frames.
    err = native_window_api_disconnect(mNativeWindow.get(),
            NATIVE_WINDOW_API_MEDIA);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: api_disconnect failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    err = native_window_api_connect(mNativeWindow.get(),
            NATIVE_WINDOW_API_CPU);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: api_connect failed: %s (%d)",
                strerror(-err), -err);
        return err;
    }

    err = native_window_set_scaling_mode(mNativeWindow.get(),
            NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: set_buffers_geometry failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    err = native_window_set_buffers_geometry(mNativeWindow.get(), 1, 1,
            HAL_PIXEL_FORMAT_RGBX_8888);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: set_buffers_geometry failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    err = native_window_set_usage(mNativeWindow.get(),
            GRALLOC_USAGE_SW_WRITE_OFTEN);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: set_usage failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    err = mNativeWindow->query(mNativeWindow.get(),
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, &minUndequeuedBufs);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: MIN_UNDEQUEUED_BUFFERS query "
                "failed: %s (%d)", strerror(-err), -err);
        goto error;
    }

    numBufs = minUndequeuedBufs + 1;
    err = native_window_set_buffer_count(mNativeWindow.get(), numBufs);
    if (err != NO_ERROR) {
        LOGE("error pushing blank frames: set_buffer_count failed: %s (%d)",
                strerror(-err), -err);
        goto error;
    }

    // We  push numBufs + 1 buffers to ensure that we've drawn into the same
    // buffer twice.  This should guarantee that the buffer has been displayed
    // on the screen and then been replaced, so an previous video frames are
    // guaranteed NOT to be currently displayed.
    for (int i = 0; i < numBufs + 1; i++) {
        err = mNativeWindow->dequeueBuffer(mNativeWindow.get(), &anb);
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: dequeueBuffer failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        sp<GraphicBuffer> buf(new GraphicBuffer(anb, false));
        err = mNativeWindow->lockBuffer(mNativeWindow.get(),
                buf->getNativeBuffer());
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: lockBuffer failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        // Fill the buffer with the a 1x1 checkerboard pattern ;)
        uint32_t* img = NULL;
        err = buf->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void**)(&img));
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: lock failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        *img = 0;

        err = buf->unlock();
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: unlock failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        err = mNativeWindow->queueBuffer(mNativeWindow.get(),
                buf->getNativeBuffer());
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: queueBuffer failed: %s (%d)",
                    strerror(-err), -err);
            goto error;
        }

        anb = NULL;
    }

error:

    if (err != NO_ERROR) {
        // Clean up after an error.
        if (anb != NULL) {
            mNativeWindow->cancelBuffer(mNativeWindow.get(), anb);
        }

        native_window_api_disconnect(mNativeWindow.get(),
                NATIVE_WINDOW_API_CPU);
        native_window_api_connect(mNativeWindow.get(),
                NATIVE_WINDOW_API_MEDIA);

        return err;
    } else {
        // Clean up after success.
        err = native_window_api_disconnect(mNativeWindow.get(),
                NATIVE_WINDOW_API_CPU);
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: api_disconnect failed: %s (%d)",
                    strerror(-err), -err);
            return err;
        }

        err = native_window_api_connect(mNativeWindow.get(),
                NATIVE_WINDOW_API_MEDIA);
        if (err != NO_ERROR) {
            LOGE("error pushing blank frames: api_connect failed: %s (%d)",
                    strerror(-err), -err);
            return err;
        }

        return NO_ERROR;
    }
}

int64_t OMXCodec::retrieveDecodingTimeUs(bool isCodecSpecific) {
    CHECK(mIsEncoder);

    if (mDecodingTimeList.empty()) {
#ifndef QCOM_HARDWARE
        CHECK(mSignalledEOS || mNoMoreOutputData);
#endif
        // No corresponding input frame available.
        // This could happen when EOS is reached.
        return 0;
    }

    List<int64_t>::iterator it = mDecodingTimeList.begin();
    int64_t timeUs = *it;

    // If the output buffer is codec specific configuration,
    // do not remove the decoding time from the list.
    if (!isCodecSpecific) {
        mDecodingTimeList.erase(it);
    }
    return timeUs;
}

void OMXCodec::on_message(const omx_message &msg) {
#ifdef QCOM_HARDWARE
    if (mState == ERROR && !strncmp(mComponentName, "OMX.google.", 11)) {
#else
    if (mState == ERROR) {
#endif
        LOGW("Dropping OMX message - we're in ERROR state.");
        return;
    }

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
            if ((*buffers)[i].mStatus != OWNED_BY_COMPONENT) {
                LOGW("We already own input buffer %p, yet received "
                     "an EMPTY_BUFFER_DONE.", buffer);
            }

            BufferInfo* info = &buffers->editItemAt(i);
            info->mStatus = OWNED_BY_US;
#ifdef QCOM_HARDWARE
            if ((mState == ERROR)  && (bInvalidState == true)) {
              CODEC_LOGV("mState ERROR, freeing i/p buffer %p", buffer);
              status_t err = freeBuffer(kPortIndexInput, i);
              CHECK_EQ(err, (status_t)OK);
            }
#endif

            // Buffer could not be released until empty buffer done is called.
            if (info->mMediaBuffer != NULL) {
                if (mIsEncoder &&
                    (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
                    // If zero-copy mode is enabled this will send the
                    // input buffer back to the upstream source.
                    restorePatchedDataPointer(info);
                }

                info->mMediaBuffer->release();
                info->mMediaBuffer = NULL;
            }

            if (mPortStatus[kPortIndexInput] == DISABLING) {
                CODEC_LOGV("Port is disabled, freeing buffer %p", buffer);

                status_t err = freeBuffer(kPortIndexInput, i);
                CHECK_EQ(err, (status_t)OK);
            } else if (mState != ERROR
                    && mPortStatus[kPortIndexInput] != SHUTTING_DOWN) {
                CHECK_EQ((int)mPortStatus[kPortIndexInput], (int)ENABLED);

                if (mFlags & kUseSecureInputBuffers) {
                    drainAnyInputBuffer();
                } else {
                    drainInputBuffer(&buffers->editItemAt(i));
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

#ifdef QCOM_HARDWARE
            if (!strncasecmp(mMIME, "video/", 6) && mOMXLivesLocally
               && msg.u.extended_buffer_data.range_length > 0) {

              CODEC_LOGV("Calling processSEIData");
              if (!mThumbnailMode)
                  processSEIData();
            }
#endif

            Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
            size_t i = 0;
            while (i < buffers->size() && (*buffers)[i].mBuffer != buffer) {
                ++i;
            }

            CHECK(i < buffers->size());
            BufferInfo *info = &buffers->editItemAt(i);

            if (info->mStatus != OWNED_BY_COMPONENT) {
                LOGW("We already own output buffer %p, yet received "
                     "a FILL_BUFFER_DONE.", buffer);
            }

            info->mStatus = OWNED_BY_US;
#ifdef QCOM_HARDWARE
            if ((mState == ERROR) && (bInvalidState == true)) {
              CODEC_LOGV("mState ERROR, freeing o/p buffer %p", buffer);
              status_t err = freeBuffer(kPortIndexOutput, i);
              CHECK_EQ(err, (status_t)OK);
            }
#endif

            if (mPortStatus[kPortIndexOutput] == DISABLING) {
                CODEC_LOGV("Port is disabled, freeing buffer %p", buffer);

                status_t err = freeBuffer(kPortIndexOutput, i);
                CHECK_EQ(err, (status_t)OK);

#if 0
            } else if (mPortStatus[kPortIndexOutput] == ENABLED
                       && (flags & OMX_BUFFERFLAG_EOS)) {
                CODEC_LOGV("No more output data.");
                mNoMoreOutputData = true;
                mBufferFilled.signal();
#endif
            } else if (mPortStatus[kPortIndexOutput] != SHUTTING_DOWN) {
                CHECK_EQ((int)mPortStatus[kPortIndexOutput], (int)ENABLED);

                if (info->mMediaBuffer == NULL) {
                    CHECK(mOMXLivesLocally);
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
                bool isGraphicBuffer = buffer->graphicBuffer() != NULL;

                if (!isGraphicBuffer
                    && msg.u.extended_buffer_data.range_offset
                        + msg.u.extended_buffer_data.range_length
                            > buffer->size()) {
                    CODEC_LOGE(
                            "Codec lied about its buffer size requirements, "
                            "sending a buffer larger than the originally "
                            "advertised size in FILL_BUFFER_DONE!");
                }
                buffer->set_range(
                        msg.u.extended_buffer_data.range_offset,
                        msg.u.extended_buffer_data.range_length);

                buffer->meta_data()->clear();

                buffer->meta_data()->setInt64(
                        kKeyTime, msg.u.extended_buffer_data.timestamp);

                if (msg.u.extended_buffer_data.flags & OMX_BUFFERFLAG_SYNCFRAME) {
                    buffer->meta_data()->setInt32(kKeyIsSyncFrame, true);
                }
                bool isCodecSpecific = false;
                if (msg.u.extended_buffer_data.flags & OMX_BUFFERFLAG_CODECCONFIG) {
                    buffer->meta_data()->setInt32(kKeyIsCodecConfig, true);
                    isCodecSpecific = true;
                }

                if (isGraphicBuffer || mQuirks & kOutputBuffersAreUnreadable) {
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

                if (mIsEncoder) {
                    int64_t decodingTimeUs = retrieveDecodingTimeUs(isCodecSpecific);
#ifdef QCOM_HARDWARE
                    CODEC_LOGV("kkeyTime = %lld, kKeyDecodingTime = %lld, ctts = %lld",
                               msg.u.extended_buffer_data.timestamp, decodingTimeUs,
                               msg.u.extended_buffer_data.timestamp - decodingTimeUs);
#endif
                    buffer->meta_data()->setInt64(kKeyDecodingTime, decodingTimeUs);
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
                if (mIsEncoder) {
                    sched_yield();
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

        int32_t left_from, top_from, right_from, bottom_from;
        CHECK(from->findRect(
                    kKeyCropRect,
                    &left_from, &top_from, &right_from, &bottom_from));

        int32_t left_to, top_to, right_to, bottom_to;
        CHECK(to->findRect(
                    kKeyCropRect,
                    &left_to, &top_to, &right_to, &bottom_to));

        if (left_to != left_from || top_to != top_from
                || right_to != right_from || bottom_to != bottom_from) {
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

void OMXCodec::onEvent(OMX_EVENTTYPE event, OMX_U32 data1, OMX_U32 data2) {
    switch (event) {
        case OMX_EventCmdComplete:
        {
            onCmdComplete((OMX_COMMANDTYPE)data1, data2);
            break;
        }

        case OMX_EventError:
        {
            CODEC_LOGE("ERROR(0x%08lx, %ld)", data1, data2);
#ifdef QCOM_HARDWARE
            if (data1 == OMX_ErrorInvalidState) {
                bInvalidState = true;
                mPortStatus[kPortIndexInput] = SHUTTING_DOWN;
                mPortStatus[kPortIndexOutput] = SHUTTING_DOWN;
            }
#endif

            setState(ERROR);
            break;
        }

        case OMX_EventPortSettingsChanged:
        {
            CODEC_LOGV("OMX_EventPortSettingsChanged(port=%ld, data2=0x%08lx)",
                       data1, data2);

#ifdef QCOM_HARDWARE
            if ((mState != EXECUTING) && (mState != FLUSHING)) {
                CODEC_LOGE("Ignore PortSettingsChanged event mState == %d ", mState);
                break;
            }

            if (mState == FLUSHING) {
                CODEC_LOGE("Received port reconfig while waiting for FBD\n");
                // Seek is completed and waiting for FBD, at this instance
                // Port reconfig is received rather than FBD.
            }
#endif

            if (data2 == 0 || data2 == OMX_IndexParamPortDefinition) {
                // There is no need to check whether mFilledBuffers is empty or not
                // when the OMX_EventPortSettingsChanged is not meant for reallocating
                // the output buffers.
#ifdef QCOM_HARDWARE
                if ((data1 == kPortIndexOutput) && (mFilledBuffers.empty()== false)) {
                    CODEC_LOGE("Port reconfiguration not allowed during middle of playback");
                    setState(ERROR);
                    break;
                }
#else
                if (data1 == kPortIndexOutput) {
                    CHECK(mFilledBuffers.empty());
                }
#endif
                onPortSettingsChanged(data1);
            } else if (data1 == kPortIndexOutput &&
                        (data2 == OMX_IndexConfigCommonOutputCrop ||
                         data2 == OMX_IndexConfigCommonScale)) {

                sp<MetaData> oldOutputFormat = mOutputFormat;
                initOutputFormat(mSource->getFormat());

                if (data2 == OMX_IndexConfigCommonOutputCrop &&
                    formatHasNotablyChanged(oldOutputFormat, mOutputFormat)) {
                    mOutputPortSettingsHaveChanged = true;

                } else if (data2 == OMX_IndexConfigCommonScale) {
                    OMX_CONFIG_SCALEFACTORTYPE scale;
                    InitOMXParams(&scale);
                    scale.nPortIndex = kPortIndexOutput;

                    // Change display dimension only when necessary.
                    if (OK == mOMX->getConfig(
                                        mNode,
                                        OMX_IndexConfigCommonScale,
                                        &scale, sizeof(scale))) {
                        int32_t left, top, right, bottom;
                        CHECK(mOutputFormat->findRect(kKeyCropRect,
                                                      &left, &top,
                                                      &right, &bottom));

                        // The scale is in 16.16 format.
                        // scale 1.0 = 0x010000. When there is no
                        // need to change the display, skip it.
                        LOGV("Get OMX_IndexConfigScale: 0x%lx/0x%lx",
                                scale.xWidth, scale.xHeight);

                        if (scale.xWidth != 0x010000) {
                            mOutputFormat->setInt32(kKeyDisplayWidth,
                                    ((right - left +  1) * scale.xWidth)  >> 16);
                            mOutputPortSettingsHaveChanged = true;
                        }

                        if (scale.xHeight != 0x010000) {
                            mOutputFormat->setInt32(kKeyDisplayHeight,
                                    ((bottom  - top + 1) * scale.xHeight) >> 16);
                            mOutputPortSettingsHaveChanged = true;
                        }
                    }
                }
            }
            break;
        }
#ifdef QCOM_HARDWARE
        case OMX_EventIndexsettingChanged:
        {
            OMX_INTERLACETYPE format = (OMX_INTERLACETYPE)data1;
            if (format == OMX_InterlaceInterleaveFrameTopFieldFirst ||
                format == OMX_InterlaceInterleaveFrameBottomFieldFirst)
            {
                mInterlaceFormatDetected = true;
                LOGW("Interlace detected");
            }
            break;
        }
#endif
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

void OMXCodec::onCmdComplete(OMX_COMMANDTYPE cmd, OMX_U32 data) {
    switch (cmd) {
        case OMX_CommandStateSet:
        {
            onStateChange((OMX_STATETYPE)data);
            break;
        }

        case OMX_CommandPortDisable:
        {
#ifdef QCOM_HARDWARE
            if(mState == ERROR) {
              CODEC_LOGE("Ignoring OMX_CommandPortDisable in ERROR state");
              break;
            }
#endif
            OMX_U32 portIndex = data;
            CODEC_LOGV("PORT_DISABLED(%ld)", portIndex);

            CHECK(mState == EXECUTING || mState == RECONFIGURING);
            CHECK_EQ((int)mPortStatus[portIndex], (int)DISABLING);
            CHECK_EQ(mPortBuffers[portIndex].size(), 0u);

            mPortStatus[portIndex] = DISABLED;

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, (OMX_U32)kPortIndexOutput);

                sp<MetaData> oldOutputFormat = mOutputFormat;
                initOutputFormat(mSource->getFormat());

                // Don't notify clients if the output port settings change
                // wasn't of importance to them, i.e. it may be that just the
                // number of buffers has changed and nothing else.
                bool formatChanged = formatHasNotablyChanged(oldOutputFormat, mOutputFormat);
                if (!mOutputPortSettingsHaveChanged) {
                    mOutputPortSettingsHaveChanged = formatChanged;
                }

                status_t err = enablePortAsync(portIndex);
                if (err != OK) {
                    CODEC_LOGE("enablePortAsync(%ld) failed (err = %d)", portIndex, err);
                    setState(ERROR);
                } else {
                    err = allocateBuffersOnPort(portIndex);
                    if (err != OK) {
                        CODEC_LOGE("allocateBuffersOnPort failed (err = %d)", err);
                        setState(ERROR);
                    }
                }
            }
            break;
        }

        case OMX_CommandPortEnable:
        {
            OMX_U32 portIndex = data;
            CODEC_LOGV("PORT_ENABLED(%ld)", portIndex);

#ifdef QCOM_HARDWARE
            if(ERROR == mState) {
              CODEC_LOGE("Ignoring port Enable since component is in ERROR state");
              break;
            }
#endif

            CHECK(mState == EXECUTING || mState == RECONFIGURING);
            CHECK_EQ((int)mPortStatus[portIndex], (int)ENABLING);

            mPortStatus[portIndex] = ENABLED;

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, (OMX_U32)kPortIndexOutput);

                setState(EXECUTING);

                fillOutputBuffers();
            }
            break;
        }

        case OMX_CommandFlush:
        {
            OMX_U32 portIndex = data;

            CODEC_LOGV("FLUSH_DONE(%ld)", portIndex);

#ifdef QCOM_HARDWARE
            if (portIndex == -1) {
                CHECK_EQ((int)mPortStatus[kPortIndexInput], (int)SHUTTING_DOWN);
                mPortStatus[kPortIndexInput] = ENABLED;

                CHECK_EQ((int)mPortStatus[kPortIndexOutput], (int)SHUTTING_DOWN);
                mPortStatus[kPortIndexOutput] = ENABLED;
            } else {
                CHECK_EQ((int)mPortStatus[portIndex], (int)SHUTTING_DOWN);
                mPortStatus[portIndex] = ENABLED;

                CHECK_EQ(countBuffersWeOwn(mPortBuffers[portIndex]),
                         mPortBuffers[portIndex].size());
            }
#else
            CHECK_EQ((int)mPortStatus[portIndex], (int)SHUTTING_DOWN);
            mPortStatus[portIndex] = ENABLED;

            CHECK_EQ(countBuffersWeOwn(mPortBuffers[portIndex]),
                     mPortBuffers[portIndex].size());
#endif


#ifdef QCOM_HARDWARE
            if(mState == ERROR) {
              CODEC_LOGE("Ignoring OMX_CommandFlush in ERROR state");
              break;
            }
#endif

            if (mState == RECONFIGURING) {
                CHECK_EQ(portIndex, (OMX_U32)kPortIndexOutput);

                disablePortAsync(portIndex);
            } else if (mState == EXECUTING_TO_IDLE) {
                if (mPortStatus[kPortIndexInput] == ENABLED
                    && mPortStatus[kPortIndexOutput] == ENABLED) {
                    CODEC_LOGV("Finished flushing both ports, now completing "
                         "transition from EXECUTING to IDLE.");

                    mPortStatus[kPortIndexInput] = SHUTTING_DOWN;
                    mPortStatus[kPortIndexOutput] = SHUTTING_DOWN;

                    status_t err =
                        mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                    CHECK_EQ(err, (status_t)OK);
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

                if (mOutputPortSettingsChangedPending) {
                    CODEC_LOGV(
                            "Honoring deferred output port settings change.");

                    mOutputPortSettingsChangedPending = false;
                    onPortSettingsChanged(kPortIndexOutput);
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

                CHECK_EQ(err, (status_t)OK);

                setState(IDLE_TO_EXECUTING);
            } else {
#ifdef QCOM_HARDWARE
                if(mState == ERROR) {
                    CODEC_LOGV("mState in ERROR when moving from Executing to Idle\n");
                } else {
                    CHECK_EQ((int)mState, (int)EXECUTING_TO_IDLE);
                }
#else
                CHECK_EQ((int)mState, (int)EXECUTING_TO_IDLE);
#endif

                CHECK_EQ(
                    countBuffersWeOwn(mPortBuffers[kPortIndexInput]),
                    mPortBuffers[kPortIndexInput].size());

                CHECK_EQ(
                    countBuffersWeOwn(mPortBuffers[kPortIndexOutput]),
                    mPortBuffers[kPortIndexOutput].size());

                status_t err = mOMX->sendCommand(
                        mNode, OMX_CommandStateSet, OMX_StateLoaded);

                CHECK_EQ(err, (status_t)OK);

                err = freeBuffersOnPort(kPortIndexInput);
                CHECK_EQ(err, (status_t)OK);

                err = freeBuffersOnPort(kPortIndexOutput);
                CHECK_EQ(err, (status_t)OK);

                mPortStatus[kPortIndexInput] = ENABLED;
                mPortStatus[kPortIndexOutput] = ENABLED;

                if ((mFlags & kEnableGrallocUsageProtected) &&
                        mNativeWindow != NULL) {
                    // We push enough 1x1 blank buffers to ensure that one of
                    // them has made it to the display.  This allows the OMX
                    // component teardown to zero out any protected buffers
                    // without the risk of scanning out one of those buffers.
                    pushBlankBuffersToNativeWindow();
                }

                setState(IDLE_TO_LOADED);
            }
            break;
        }

        case OMX_StateExecuting:
        {
#ifdef QCOM_HARDWARE
            if(mState == ERROR) {
                CODEC_LOGV("mState in ERROR when moving from Idle to Executing\n");
            } else {
                CHECK_EQ((int)mState, (int)IDLE_TO_EXECUTING);

                CODEC_LOGV("Now Executing.");

                mOutputPortSettingsChangedPending = false;

                setState(EXECUTING);
            }
#else
            CHECK_EQ((int)mState, (int)IDLE_TO_EXECUTING);

            CODEC_LOGV("Now Executing.");

            mOutputPortSettingsChangedPending = false;

            setState(EXECUTING);
#endif

            // Buffers will be submitted to the component in the first
            // call to OMXCodec::read as mInitialBufferSubmit is true at
            // this point. This ensures that this on_message call returns,
            // releases the lock and ::init can notice the state change and
            // itself return.
            break;
        }

        case OMX_StateLoaded:
        {
#ifdef QCOM_HARDWARE
            if (mState == ERROR)
                CODEC_LOGE("We are in error state, should have been in idle->loaded");
#endif

            CHECK_EQ((int)mState, (int)IDLE_TO_LOADED);

            CODEC_LOGV("Now Loaded.");

            setState(LOADED);
            break;
        }
#ifdef QCOM_HARDWARE
        case OMX_StatePause:
        {
           CODEC_LOGV("Now paused.");

           CHECK_EQ(mState, PAUSING);

           setState(PAUSED);
           break;
        }
#endif
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
        if (buffers[i].mStatus != OWNED_BY_COMPONENT) {
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

        if (onlyThoseWeOwn && info->mStatus == OWNED_BY_COMPONENT) {
            continue;
        }

        CHECK(info->mStatus == OWNED_BY_US
                || info->mStatus == OWNED_BY_NATIVE_WINDOW);

        CODEC_LOGV("freeing buffer %p on port %ld", info->mBuffer, portIndex);

        status_t err = freeBuffer(portIndex, i);

        if (err != OK) {
            stickyErr = err;
        }

    }

    CHECK(onlyThoseWeOwn || buffers->isEmpty());

    return stickyErr;
}

status_t OMXCodec::freeBuffer(OMX_U32 portIndex, size_t bufIndex) {
    Vector<BufferInfo> *buffers = &mPortBuffers[portIndex];

    BufferInfo *info = &buffers->editItemAt(bufIndex);

    status_t err = mOMX->freeBuffer(mNode, portIndex, info->mBuffer);

    if (err == OK && info->mMediaBuffer != NULL) {
        CHECK_EQ(portIndex, (OMX_U32)kPortIndexOutput);
        info->mMediaBuffer->setObserver(NULL);

        // Make sure nobody but us owns this buffer at this point.
        CHECK_EQ(info->mMediaBuffer->refcount(), 0);

        // Cancel the buffer if it belongs to an ANativeWindow.
        sp<GraphicBuffer> graphicBuffer = info->mMediaBuffer->graphicBuffer();
        if (info->mStatus == OWNED_BY_US && graphicBuffer != 0) {
            err = cancelBufferToNativeWindow(info);
        }

        info->mMediaBuffer->release();
        info->mMediaBuffer = NULL;
    }

    if (err == OK) {
        buffers->removeAt(bufIndex);
    }
#ifdef QCOM_HARDWARE
    else {
        LOGW("Warning, free Buffer failed, to be freed later"
             " returning OK to prevent crash..");
        return OK;
    }
#endif

    return err;
}

void OMXCodec::onPortSettingsChanged(OMX_U32 portIndex) {
    CODEC_LOGV("PORT_SETTINGS_CHANGED(%ld)", portIndex);

    CHECK_EQ((int)mState, (int)EXECUTING);
    CHECK_EQ(portIndex, (OMX_U32)kPortIndexOutput);
    CHECK(!mOutputPortSettingsChangedPending);

    if (mPortStatus[kPortIndexOutput] != ENABLED) {
        CODEC_LOGV("Deferring output port settings change.");
        mOutputPortSettingsChangedPending = true;
        return;
    }

    setState(RECONFIGURING);

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
#ifdef QCOM_HARDWARE
            || mState == EXECUTING_TO_IDLE || mState == FLUSHING);
#else
            || mState == EXECUTING_TO_IDLE);
#endif

#ifdef QCOM_HARDWARE
    if ( portIndex == -1 ) {
        mPortStatus[kPortIndexInput] = SHUTTING_DOWN;
        mPortStatus[kPortIndexOutput] = SHUTTING_DOWN;
    } else {
#endif
    CODEC_LOGV("flushPortAsync(%ld): we own %d out of %d buffers already.",
         portIndex, countBuffersWeOwn(mPortBuffers[portIndex]),
         mPortBuffers[portIndex].size());

    CHECK_EQ((int)mPortStatus[portIndex], (int)ENABLED);
    mPortStatus[portIndex] = SHUTTING_DOWN;

    if ((mQuirks & kRequiresFlushCompleteEmulation)
        && countBuffersWeOwn(mPortBuffers[portIndex])
                == mPortBuffers[portIndex].size()) {
        // No flush is necessary and this component fails to send a
        // flush-complete event in this case.

        return false;
    }
#ifdef QCOM_HARDWARE
    }
#endif

    status_t err =
        mOMX->sendCommand(mNode, OMX_CommandFlush, portIndex);
    CHECK_EQ(err, (status_t)OK);

    return true;
}

void OMXCodec::disablePortAsync(OMX_U32 portIndex) {
    CHECK(mState == EXECUTING || mState == RECONFIGURING);

    CHECK_EQ((int)mPortStatus[portIndex], (int)ENABLED);
    mPortStatus[portIndex] = DISABLING;

    CODEC_LOGV("sending OMX_CommandPortDisable(%ld)", portIndex);
    status_t err =
        mOMX->sendCommand(mNode, OMX_CommandPortDisable, portIndex);
    CHECK_EQ(err, (status_t)OK);

    freeBuffersOnPort(portIndex, true);
}

status_t OMXCodec::enablePortAsync(OMX_U32 portIndex) {
    CHECK(mState == EXECUTING || mState == RECONFIGURING);

    CHECK_EQ((int)mPortStatus[portIndex], (int)DISABLED);
    mPortStatus[portIndex] = ENABLING;

    CODEC_LOGV("sending OMX_CommandPortEnable(%ld)", portIndex);
    return mOMX->sendCommand(mNode, OMX_CommandPortEnable, portIndex);
}

void OMXCodec::fillOutputBuffers() {
#ifdef QCOM_HARDWARE
    CHECK(mState == EXECUTING || mState == FLUSHING);
#else
    CHECK_EQ((int)mState, (int)EXECUTING);
#endif

    // This is a workaround for some decoders not properly reporting
    // end-of-output-stream. If we own all input buffers and also own
    // all output buffers and we already signalled end-of-input-stream,
    // the end-of-output-stream is implied.
#ifdef QCOM_HARDWARE
    // NOTE: Thumbnail mode needs a call to fillOutputBuffer in order
    // to get the decoded frame from the component. Currently,
    // thumbnail mode calls emptyBuffer with an EOS flag on its first
    // frame and sets mSignalledEOS to true, so without the check for
    // !mThumbnailMode, fillOutputBuffer will never be called.
    if (!mThumbnailMode) {
        if (mSignalledEOS
            && countBuffersWeOwn(mPortBuffers[kPortIndexInput])
                == mPortBuffers[kPortIndexInput].size()
            && countBuffersWeOwn(mPortBuffers[kPortIndexOutput])
                == mPortBuffers[kPortIndexOutput].size()) {
            mNoMoreOutputData = true;
            mBufferFilled.signal();
            return;
        }
    }
#else
    if (mSignalledEOS
            && countBuffersWeOwn(mPortBuffers[kPortIndexInput])
                == mPortBuffers[kPortIndexInput].size()
            && countBuffersWeOwn(mPortBuffers[kPortIndexOutput])
                == mPortBuffers[kPortIndexOutput].size()) {
        mNoMoreOutputData = true;
        mBufferFilled.signal();

        return;
    }
#endif

    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        BufferInfo *info = &buffers->editItemAt(i);
        if (info->mStatus == OWNED_BY_US) {
            fillOutputBuffer(&buffers->editItemAt(i));
        }
    }
}

void OMXCodec::drainInputBuffers() {
#ifdef QCOM_HARDWARE
    CHECK(mState == EXECUTING || mState == RECONFIGURING || mState == FLUSHING);
#else
    CHECK(mState == EXECUTING || mState == RECONFIGURING);
#endif

    if (mFlags & kUseSecureInputBuffers) {
        Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
        for (size_t i = 0; i < buffers->size(); ++i) {
            if (!drainAnyInputBuffer()
                    || (mFlags & kOnlySubmitOneInputBufferAtOneTime)) {
                break;
            }
        }
    } else {
#ifdef QCOM_HARDWARE
        size_t CAMERA_BUFFERS = 4;
#endif
        Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
        for (size_t i = 0; i < buffers->size(); ++i) {
            BufferInfo *info = &buffers->editItemAt(i);

            if (info->mStatus != OWNED_BY_US) {
                continue;
            }

#ifdef QCOM_HARDWARE
            if(mIsEncoder && (i == CAMERA_BUFFERS))
                break;
#endif

            if (!drainInputBuffer(info)) {
                break;
            }

            if (mFlags & kOnlySubmitOneInputBufferAtOneTime) {
                break;
            }
        }
    }
}

bool OMXCodec::drainAnyInputBuffer() {
    return drainInputBuffer((BufferInfo *)NULL);
}

OMXCodec::BufferInfo *OMXCodec::findInputBufferByDataPointer(void *ptr) {
    Vector<BufferInfo> *infos = &mPortBuffers[kPortIndexInput];
    for (size_t i = 0; i < infos->size(); ++i) {
        BufferInfo *info = &infos->editItemAt(i);

        if (info->mData == ptr) {
            CODEC_LOGV(
                    "input buffer data ptr = %p, buffer_id = %p",
                    ptr,
                    info->mBuffer);

            return info;
        }
    }

    TRESPASS();
}

OMXCodec::BufferInfo *OMXCodec::findEmptyInputBuffer() {
    Vector<BufferInfo> *infos = &mPortBuffers[kPortIndexInput];
    for (size_t i = 0; i < infos->size(); ++i) {
        BufferInfo *info = &infos->editItemAt(i);

        if (info->mStatus == OWNED_BY_US) {
            return info;
        }
    }

    TRESPASS();
}

bool OMXCodec::drainInputBuffer(BufferInfo *info) {
    if (info != NULL) {
        CHECK_EQ((int)info->mStatus, (int)OWNED_BY_US);
    }

    if (mSignalledEOS) {
        return false;
    }

    if (mCodecSpecificDataIndex < mCodecSpecificData.size()) {
        CHECK(!(mFlags & kUseSecureInputBuffers));

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

        status_t err = mOMX->emptyBuffer(
                mNode, info->mBuffer, 0, size,
                OMX_BUFFERFLAG_ENDOFFRAME | OMX_BUFFERFLAG_CODECCONFIG,
                0);
        CHECK_EQ(err, (status_t)OK);

        info->mStatus = OWNED_BY_COMPONENT;

        ++mCodecSpecificDataIndex;
        return true;
    }

#ifdef QCOM_HARDWARE
    if ((!strncmp(mComponentName, "OMX.qcom.", 9)) && mPaused) {
        CODEC_LOGE("Returning as in Pause State and H/W decoder");
        return false;
    }
#else
    if (mPaused) {
        return false;
    }
#endif

    status_t err;

    bool signalEOS = false;
    int64_t timestampUs = 0;

    size_t offset = 0;
    int32_t n = 0;


    for (;;) {
        MediaBuffer *srcBuffer;
        if (mSeekTimeUs >= 0) {
            if (mLeftOverBuffer) {
                mLeftOverBuffer->release();
                mLeftOverBuffer = NULL;
            }

            MediaSource::ReadOptions options;
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
                    CODEC_LOGV("targetTimeUs = %lld us", targetTimeUs);
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
            err = mSource->read(&srcBuffer);
        }

#ifdef QCOM_HARDWARE
        if (err == ERROR_CORRUPT_NAL) {
            LOGW("Ignore Corrupt NAL");
            continue;
        }
        else if (err != OK) {
            signalEOS = true;
            mFinalStatus = err;
            mSignalledEOS = true;
            mBufferFilled.signal();
            break;
        }
#else
        if (err != OK) {
            signalEOS = true;
            mFinalStatus = err;
            mSignalledEOS = true;
            mBufferFilled.signal();
            break;
        }
#endif

        if (mFlags & kUseSecureInputBuffers) {
            info = findInputBufferByDataPointer(srcBuffer->data());
            CHECK(info != NULL);
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
                return false;
            }

            mLeftOverBuffer = srcBuffer;
            break;
        }

        bool releaseBuffer = true;
        if (mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames)) {
            CHECK(mOMXLivesLocally && offset == 0);

            OMX_BUFFERHEADERTYPE *header =
                (OMX_BUFFERHEADERTYPE *)info->mBuffer;

#ifdef QCOM_HARDWARE
            //not commenting this one for now. XXX - remove when memcopy can be avoided
            //for encoder
#endif
            CHECK(header->pBuffer == info->mData);

            header->pBuffer =
                (OMX_U8 *)srcBuffer->data() + srcBuffer->range_offset();

            releaseBuffer = false;
            info->mMediaBuffer = srcBuffer;
        } else {
            if (mFlags & kStoreMetaDataInVideoBuffers) {
                releaseBuffer = false;
                info->mMediaBuffer = srcBuffer;
            }

            if (mFlags & kUseSecureInputBuffers) {
                // Data in "info" is already provided at this time.

                releaseBuffer = false;

                CHECK(info->mMediaBuffer == NULL);
                info->mMediaBuffer = srcBuffer;
            } else {
                CHECK(srcBuffer->data() != NULL) ;
                memcpy((uint8_t *)info->mData + offset,
                        (const uint8_t *)srcBuffer->data()
                            + srcBuffer->range_offset(),
                        srcBuffer->range_length());
            }
        }

        int64_t lastBufferTimeUs;
        CHECK(srcBuffer->meta_data()->findInt64(kKeyTime, &lastBufferTimeUs));
        CHECK(lastBufferTimeUs >= 0);
        if (mIsEncoder) {
#ifdef QCOM_HARDWARE
            CODEC_LOGV("pushing %lld to mDecodingTimeList", lastBufferTimeUs);
#endif
            mDecodingTimeList.push_back(lastBufferTimeUs);
        }

        if (offset == 0) {
            timestampUs = lastBufferTimeUs;
        }

        offset += srcBuffer->range_length();

        if (!strcasecmp(MEDIA_MIMETYPE_AUDIO_VORBIS, mMIME)) {
            CHECK(!(mQuirks & kSupportsMultipleFramesPerInputBuffer));
            CHECK_GE(info->mSize, offset + sizeof(int32_t));

            int32_t numPageSamples;
            if (!srcBuffer->meta_data()->findInt32(
                        kKeyValidSamples, &numPageSamples)) {
                numPageSamples = -1;
            }

            memcpy((uint8_t *)info->mData + offset,
                   &numPageSamples,
                   sizeof(numPageSamples));

            offset += sizeof(numPageSamples);
        }

        if (releaseBuffer) {
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

#ifdef QCOM_HARDWARE
    if(mInterlaceFormatDetected) {
      mInterlaceFrame++;
    }
#endif

    if (signalEOS) {
        flags |= OMX_BUFFERFLAG_EOS;
#ifdef QCOM_HARDWARE
    } else if ((mThumbnailMode && !mInterlaceFormatDetected)
               || (mThumbnailMode && (mInterlaceFrame >= 2))) {
        // Because we don't get an EOS after getting the first frame, we
        // need to notify the component with OMX_BUFFERFLAG_EOS, set
        // mNoMoreOutputData to false so fillOutputBuffer gets called on
        // the first output buffer (see comment in fillOutputBuffer), and
        // mSignalledEOS must be true so drainInputBuffer is not executed
        // on extra frames. Setting mFinalStatus to ERROR_END_OF_STREAM as
        // we dont want to return OK and NULL buffer in read.
        flags |= OMX_BUFFERFLAG_EOS;
        mNoMoreOutputData = false;
        mSignalledEOS = true;
        mFinalStatus = ERROR_END_OF_STREAM;
#endif
    } else {
        mNoMoreOutputData = false;
    }

    CODEC_LOGV("Calling emptyBuffer on buffer %p (length %d), "
               "timestamp %lld us (%.2f secs)",
               info->mBuffer, offset,
               timestampUs, timestampUs / 1E6);

    if (info == NULL) {
        CHECK(mFlags & kUseSecureInputBuffers);
        CHECK(signalEOS);

        // This is fishy, there's still a MediaBuffer corresponding to this
        // info available to the source at this point even though we're going
        // to use it to signal EOS to the codec.
        info = findEmptyInputBuffer();
    }

    err = mOMX->emptyBuffer(
            mNode, info->mBuffer, 0, offset,
            flags, timestampUs);

    if (err != OK) {
        setState(ERROR);
        return false;
    }

    info->mStatus = OWNED_BY_COMPONENT;

    // This component does not ever signal the EOS flag on output buffers,
    // Thanks for nothing.
    if (mSignalledEOS && !strcmp(mComponentName, "OMX.TI.Video.encoder")) {
        mNoMoreOutputData = true;
        mBufferFilled.signal();
    }

    return true;
}

void OMXCodec::fillOutputBuffer(BufferInfo *info) {
    CHECK_EQ((int)info->mStatus, (int)OWNED_BY_US);

    if (mNoMoreOutputData) {
        CODEC_LOGV("There is no more output data available, not "
             "calling fillOutputBuffer");
        return;
    }

    if (info->mMediaBuffer != NULL) {
        sp<GraphicBuffer> graphicBuffer = info->mMediaBuffer->graphicBuffer();
        if (graphicBuffer != 0) {
            // When using a native buffer we need to lock the buffer before
            // giving it to OMX.
            CODEC_LOGV("Calling lockBuffer on %p", info->mBuffer);
            int err = mNativeWindow->lockBuffer(mNativeWindow.get(),
                    graphicBuffer.get());
            if (err != 0) {
                CODEC_LOGE("lockBuffer failed w/ error 0x%08x", err);

                setState(ERROR);
                return;
            }
        }
    }

    CODEC_LOGV("Calling fillBuffer on buffer %p", info->mBuffer);
    status_t err = mOMX->fillBuffer(mNode, info->mBuffer);

    if (err != OK) {
        CODEC_LOGE("fillBuffer failed w/ error 0x%08x", err);

        setState(ERROR);
        return;
    }

    info->mStatus = OWNED_BY_COMPONENT;
}

bool OMXCodec::drainInputBuffer(IOMX::buffer_id buffer) {
    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexInput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        if ((*buffers)[i].mBuffer == buffer) {
            return drainInputBuffer(&buffers->editItemAt(i));
        }
    }

    CHECK(!"should not be here.");

    return false;
}

void OMXCodec::fillOutputBuffer(IOMX::buffer_id buffer) {
    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        if ((*buffers)[i].mBuffer == buffer) {
            fillOutputBuffer(&buffers->editItemAt(i));
            return;
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

status_t OMXCodec::waitForBufferFilled_l() {

    if (mIsEncoder) {
        // For timelapse video recording, the timelapse video recording may
        // not send an input frame for a _long_ time. Do not use timeout
        // for video encoding.
        return mBufferFilled.wait(mLock);
    }
    status_t err = mBufferFilled.waitRelative(mLock, kBufferFilledEventTimeOutNs);
#ifdef QCOM_HARDWARE
    if ((err == -ETIMEDOUT) && (mPaused == true)){
        // When the audio playback is paused, the fill buffer maybe timed out
        // if input data is not available to decode. Hence, considering the
        // timed out as a valid case.
        err = OK;
    }
#endif
    if (err != OK) {
        CODEC_LOGE("Timed out waiting for output buffers: %d/%d",
            countBuffersWeOwn(mPortBuffers[kPortIndexInput]),
            countBuffersWeOwn(mPortBuffers[kPortIndexOutput]));
    }
    return err;
}

void OMXCodec::setRawAudioFormat(
        OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels) {

    // port definition
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = portIndex;
#ifdef QCOM_HARDWARE
    def.format.audio.cMIMEType = NULL;
#endif
    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);
    def.format.audio.eEncoding = OMX_AUDIO_CodingPCM;
    CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
            &def, sizeof(def)), (status_t)OK);

    // pcm param
    OMX_AUDIO_PARAM_PCMMODETYPE pcmParams;
    InitOMXParams(&pcmParams);
    pcmParams.nPortIndex = portIndex;

    err = mOMX->getParameter(
            mNode, OMX_IndexParamAudioPcm, &pcmParams, sizeof(pcmParams));

    CHECK_EQ(err, (status_t)OK);

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

    CHECK_EQ(err, (status_t)OK);
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

    CHECK_EQ(err, (status_t)OK);

    def.eAMRFrameFormat = OMX_AUDIO_AMRFrameFormatFSF;

    def.eAMRBandMode = pickModeFromBitRate(isWAMR, bitRate);
    err = mOMX->setParameter(mNode, OMX_IndexParamAudioAmr, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

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

status_t OMXCodec::setAACFormat(int32_t numChannels, int32_t sampleRate, int32_t bitRate) {
    if (numChannels > 2)
        LOGW("Number of channels: (%d) \n", numChannels);

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
                    &format, sizeof(format)), (status_t)OK);
            if (format.eEncoding == OMX_AUDIO_CodingAAC) {
                break;
            }
            format.nIndex++;
        }
        CHECK_EQ((status_t)OK, err);
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioPortFormat,
                &format, sizeof(format)), (status_t)OK);

        // port definition
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);
        def.format.audio.bFlagErrorConcealment = OMX_TRUE;
        def.format.audio.eEncoding = OMX_AUDIO_CodingAAC;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);

        // profile
        OMX_AUDIO_PARAM_AACPROFILETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamAudioAac,
                &profile, sizeof(profile)), (status_t)OK);
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
        err = mOMX->setParameter(mNode, OMX_IndexParamAudioAac,
                &profile, sizeof(profile));

        if (err != OK) {
            CODEC_LOGE("setParameter('OMX_IndexParamAudioAac') failed (err = %d)", err);
            return err;
        }
    } else {
        OMX_AUDIO_PARAM_AACPROFILETYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexInput;

        status_t err = mOMX->getParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));
        CHECK_EQ(err, (status_t)OK);

        profile.nChannels = numChannels;
        profile.nSampleRate = sampleRate;
#ifdef QCOM_HARDWARE
        if(mIsAacFormatAdif){
            profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatADIF;
        }else{
            profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatMP4ADTS;
        }
#else
        profile.eAACStreamFormat = OMX_AUDIO_AACStreamFormatMP4ADTS;
#endif

        err = mOMX->setParameter(
                mNode, OMX_IndexParamAudioAac, &profile, sizeof(profile));

        if (err != OK) {
            CODEC_LOGE("setParameter('OMX_IndexParamAudioAac') failed (err = %d)", err);
            return err;
        }
    }

    return OK;
}

#ifdef QCOM_HARDWARE
void OMXCodec::setAC3Format(int32_t /*numChannels*/, int32_t /*sampleRate*/) {
/*
    QOMX_AUDIO_PARAM_AC3TYPE profileAC3;
    QOMX_AUDIO_PARAM_AC3PP profileAC3PP;
    OMX_INDEXTYPE indexTypeAC3;
    OMX_INDEXTYPE indexTypeAC3PP;
    OMX_PARAM_PORTDEFINITIONTYPE portParam;

    //configure input port
    InitOMXParams(&portParam);
    portParam.nPortIndex = 0;
    status_t err = mOMX->getParameter(
       mNode, OMX_IndexParamPortDefinition, &portParam, sizeof(portParam));
    CHECK_EQ(err, OK);

    portParam.nBufferSize = 2*4096;
    err = mOMX->setParameter(
       mNode, OMX_IndexParamPortDefinition, &portParam, sizeof(portParam));
    CHECK_EQ(err, OK);

    //configure output port
    portParam.nPortIndex = 1;
    err = mOMX->getParameter(
       mNode, OMX_IndexParamPortDefinition, &portParam, sizeof(portParam));
    CHECK_EQ(err, OK);
    portParam.nBufferSize = 20*4096;
    err = mOMX->setParameter(
       mNode, OMX_IndexParamPortDefinition, &portParam, sizeof(portParam));
    CHECK_EQ(err, OK);

    err = mOMX->getExtensionIndex(mNode, OMX_QCOM_INDEX_PARAM_AC3TYPE, &indexTypeAC3);

    InitOMXParams(&profileAC3);
    profileAC3.nPortIndex = kPortIndexInput;
    err = mOMX->getParameter(mNode, indexTypeAC3, &profileAC3, sizeof(profileAC3));
    CHECK_EQ(err, OK);

    profileAC3.nSamplingRate  =  sampleRate;
    profileAC3.nChannels      =  2;
    profileAC3.eChannelConfig =  OMX_AUDIO_AC3_CHANNEL_CONFIG_2_0;

    LOGE("numChannels = %d, profileAC3.nChannels = %d", numChannels, profileAC3.nChannels);

    err = mOMX->setParameter(mNode, indexTypeAC3, &profileAC3, sizeof(profileAC3));
    CHECK_EQ(err, OK);

    //for output port
    OMX_AUDIO_PARAM_PCMMODETYPE profilePcm;
    InitOMXParams(&profilePcm);
    profilePcm.nPortIndex = kPortIndexOutput;
    err = mOMX->getParameter(mNode, OMX_IndexParamAudioPcm, &profilePcm, sizeof(profilePcm));
    profilePcm.nSamplingRate  =  sampleRate;
    err = mOMX->setParameter(mNode, OMX_IndexParamAudioPcm, &profilePcm, sizeof(profilePcm));
    LOGE("for output port profileAC3.nSamplingRate = %d", profileAC3.nSamplingRate);

    mOMX->getExtensionIndex(mNode, OMX_QCOM_INDEX_PARAM_AC3PP, &indexTypeAC3PP);

    InitOMXParams(&profileAC3PP);
    profileAC3PP.nPortIndex = kPortIndexInput;
    err = mOMX->getParameter(mNode, indexTypeAC3PP, &profileAC3PP, sizeof(profileAC3PP));
    CHECK_EQ(err, OK);

    int i;
    int channel_routing[6];

    for (i=0; i<6; i++) {
        channel_routing[i] = -1;
    }
    for (i=0; i<6; i++) {
        profileAC3PP.eChannelRouting[i] =  (OMX_AUDIO_AC3_CHANNEL_ROUTING)channel_routing[i];
    }
    profileAC3PP.eChannelRouting[0] =  OMX_AUDIO_AC3_CHANNEL_LEFT;
    profileAC3PP.eChannelRouting[1] =  OMX_AUDIO_AC3_CHANNEL_RIGHT;
    err = mOMX->setParameter(mNode, indexTypeAC3PP, &profileAC3PP, sizeof(profileAC3PP));
    CHECK_EQ(err, OK);
*/
}


status_t OMXCodec::setWMAFormat(const sp<MetaData> &meta)
	{
	    if (mIsEncoder) {
	        CODEC_LOGE("WMA encoding not supported");
	        return OK;
	    } else {
	        int32_t version;
	        OMX_AUDIO_PARAM_WMATYPE paramWMA;
	        QOMX_AUDIO_PARAM_WMA10PROTYPE paramWMA10;
	        CHECK(meta->findInt32(kKeyWMAVersion, &version));
	        int32_t numChannels;
	        int32_t bitRate;
	        int32_t sampleRate;
	        int32_t encodeOptions;
	        int32_t blockAlign;
	        int32_t bitspersample;
	        int32_t formattag;
	        int32_t advencopt1;
	        int32_t advencopt2;
	        int32_t VirtualPktSize;
        if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           CHECK(meta->findInt32(kKeyWMABitspersample, &bitspersample));
	           CHECK(meta->findInt32(kKeyWMAFormatTag, &formattag));
	           CHECK(meta->findInt32(kKeyWMAAdvEncOpt1,&advencopt1));
	           CHECK(meta->findInt32(kKeyWMAAdvEncOpt2,&advencopt2));
	           CHECK(meta->findInt32(kKeyWMAVirPktSize,&VirtualPktSize));
	        }
	        if(version==kTypeWMA) {
	           InitOMXParams(&paramWMA);
	           paramWMA.nPortIndex = kPortIndexInput;
        } else if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           InitOMXParams(&paramWMA10);
	           paramWMA10.nPortIndex = kPortIndexInput;
	        }
	        CHECK(meta->findInt32(kKeyChannelCount, &numChannels));
	        CHECK(meta->findInt32(kKeySampleRate, &sampleRate));
	        CHECK(meta->findInt32(kKeyBitRate, &bitRate));
	        CHECK(meta->findInt32(kKeyWMAEncodeOpt, &encodeOptions));
	        CHECK(meta->findInt32(kKeyWMABlockAlign, &blockAlign));
	        CODEC_LOGV("Channels: %d, SampleRate: %d, BitRate; %d"
	                   "EncodeOptions: %d, blockAlign: %d", numChannels,
	                   sampleRate, bitRate, encodeOptions, blockAlign);
	        if(sampleRate>48000 || numChannels>2)
	        {
	           LOGE("Unsupported samplerate/channels");
	           return ERROR_UNSUPPORTED;
	        }
        if(version==kTypeWMAPro || version==kTypeWMALossLess)
	        {
	           CODEC_LOGV("Bitspersample: %d, wmaformattag: %d,"
	                      "advencopt1: %d, advencopt2: %d VirtualPktSize %d", bitspersample,
	                      formattag, advencopt1, advencopt2, VirtualPktSize);
	        }
	        status_t err = OK;
	        OMX_INDEXTYPE index;
	        if(version==kTypeWMA) {
	           err = mOMX->getParameter(
	                   mNode, OMX_IndexParamAudioWma, &paramWMA, sizeof(paramWMA));
        } else if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           mOMX->getExtensionIndex(mNode,"OMX.Qualcomm.index.audio.wma10Pro",&index);
	           err = mOMX->getParameter(
	                   mNode, index, &paramWMA10, sizeof(paramWMA10));
	        }
	        CHECK_EQ(err, (status_t)OK);
	        if(version==kTypeWMA) {
	           paramWMA.nChannels = numChannels;
	           paramWMA.nSamplingRate = sampleRate;
	           paramWMA.nEncodeOptions = encodeOptions;
	           paramWMA.nBitRate = bitRate;
	           paramWMA.nBlockAlign = blockAlign;
        } else if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           paramWMA10.nChannels = numChannels;
	           paramWMA10.nSamplingRate = sampleRate;
	           paramWMA10.nEncodeOptions = encodeOptions;
	           paramWMA10.nBitRate = bitRate;
	           paramWMA10.nBlockAlign = blockAlign;
	        }
        if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           paramWMA10.advancedEncodeOpt = advencopt1;
	           paramWMA10.advancedEncodeOpt2 = advencopt2;
	           paramWMA10.formatTag = formattag;
	           paramWMA10.validBitsPerSample = bitspersample;
	           paramWMA10.nVirtualPktSize = VirtualPktSize;
	        }
	        if(version==kTypeWMA) {
	           err = mOMX->setParameter(
	                   mNode, OMX_IndexParamAudioWma, &paramWMA, sizeof(paramWMA));
        } else if(version==kTypeWMAPro || version==kTypeWMALossLess) {
	           err = mOMX->setParameter(
	                   mNode, index, &paramWMA10, sizeof(paramWMA10));
	        }
	        return err;
	    }
	}
#endif

#ifdef QCOM_HARDWARE
void OMXCodec::setG711Format(int32_t numChannels, int32_t sampleRate) {
    CHECK(!mIsEncoder);
    setRawAudioFormat(kPortIndexInput, sampleRate, numChannels);
}
#else
void OMXCodec::setG711Format(int32_t numChannels) {
    CHECK(!mIsEncoder);
    setRawAudioFormat(kPortIndexInput, 8000, numChannels);
}
#endif

void OMXCodec::setImageOutputFormat(
        OMX_COLOR_FORMATTYPE format, OMX_U32 width, OMX_U32 height) {
    CODEC_LOGV("setImageOutputFormat(%ld, %ld)", width, height);

#if 0
    OMX_INDEXTYPE index;
    status_t err = mOMX->get_extension_index(
            mNode, "OMX.TI.JPEG.decode.Config.OutputColorFormat", &index);
    CHECK_EQ(err, (status_t)OK);

    err = mOMX->set_config(mNode, index, &format, sizeof(format));
    CHECK_EQ(err, (status_t)OK);
#endif

    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexOutput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainImage);

    OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;

    CHECK_EQ((int)imageDef->eCompressionFormat, (int)OMX_IMAGE_CodingUnused);
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
    CHECK_EQ(err, (status_t)OK);
}

void OMXCodec::setJPEGInputFormat(
        OMX_U32 width, OMX_U32 height, OMX_U32 compressedSize) {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);
    def.nPortIndex = kPortIndexInput;

    status_t err = mOMX->getParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);

    CHECK_EQ((int)def.eDomain, (int)OMX_PortDomainImage);
    OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;

    CHECK_EQ((int)imageDef->eCompressionFormat, (int)OMX_IMAGE_CodingJPEG);
    imageDef->nFrameWidth = width;
    imageDef->nFrameHeight = height;

    def.nBufferSize = compressedSize;
    def.nBufferCountActual = def.nBufferCountMin;

    err = mOMX->setParameter(
            mNode, OMX_IndexParamPortDefinition, &def, sizeof(def));
    CHECK_EQ(err, (status_t)OK);
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
#ifdef QCOM_HARDWARE
    CODEC_LOGV("OMXCodec::start ");
#endif
    Mutex::Autolock autoLock(mLock);

#ifdef QCOM_HARDWARE
    if(mPaused) {
        if (!strncmp(mComponentName, "OMX.qcom.", 9)) {
            while (isIntermediateState(mState)) {
                mAsyncCompletion.wait(mLock);
            }
            CHECK_EQ(mState, (status_t)PAUSED);
            status_t err = mOMX->sendCommand(mNode,
            OMX_CommandStateSet, OMX_StateExecuting);
            CHECK_EQ(err, (status_t)OK);
            setState(IDLE_TO_EXECUTING);
            mPaused = false;
            while (mState != EXECUTING && mState != ERROR) {
                mAsyncCompletion.wait(mLock);
            }
            return mState == ERROR ? UNKNOWN_ERROR : OK;
        } else {   // SW Codec
            mPaused = false;
            return OK;
        }
    }
#endif

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

    bool isError = false;
    switch (mState) {
        case LOADED:
            break;

        case ERROR:
        {
#ifdef QCOM_HARDWARE
            CODEC_LOGE("in error state, check omx il state and decide whether to free or skip");
#endif

            OMX_STATETYPE state = OMX_StateInvalid;
            status_t err = mOMX->getState(mNode, &state);
            CHECK_EQ(err, (status_t)OK);

#ifdef QCOM_HARDWARE
            CODEC_LOGE("OMX IL is in state %d", state);

            /* OMX IL spec page 98
               The call should be performed under the following conditions:
               . While the component is in the OMX_StateIdle state and the IL client has
                 already sent a request for the state transition to OMX_StateLoaded
                 (e.g., during the stopping of the component)
               . On a disabled port when the component is in the OMX_StateExecuting,
                 the OMX_StatePause, or the OMX_StateIdle state.
            */

            bool canFree = true;
            if (!strncmp(mComponentName, "OMX.qcom.video.decoder.", 23)) {
                if (state == OMX_StateInvalid) {
                    canFree = true;
                }
                else if ((state == OMX_StateIdle) && mState == IDLE_TO_LOADED) {
                    canFree = true;
                }
                else if ((state == OMX_StateExecuting || state == OMX_StatePause ||
                          state == OMX_StateIdle) &&
                         (mPortStatus[kPortIndexOutput] == DISABLED ||
                          mPortStatus[kPortIndexOutput] == DISABLING)) {
                    canFree = true;
                }
                else
                    canFree = false;
            }

            if (canFree) {
                err = freeBuffersOnPort(kPortIndexOutput, true);
                CHECK_EQ(err, (status_t)OK);
            }
            else {
                LOGW("%s IL component does not match conditions for free, skip freeing for later",
                     mComponentName);
            }
#endif

            if (state != OMX_StateExecuting) {
                break;
            }
#ifdef QCOM_HARDWARE
            else {
                CODEC_LOGV("Component is still in executing state, fall through and move component"
                           " to idle");
            }
#endif
            // else fall through to the idling code
            isError = true;
        }

#ifdef QCOM_HARDWARE
        case PAUSED:
#endif
        case EXECUTING:
        {
            setState(EXECUTING_TO_IDLE);

            if (mQuirks & kRequiresFlushBeforeShutdown) {
                CODEC_LOGV("This component requires a flush before transitioning "
                     "from EXECUTING to IDLE...");
#ifdef QCOM_HARDWARE
            //DSP supports flushing of ports simultaneously. Flushing individual port is not supported.

                if(mQuirks & kRequiresGlobalFlush) {
                  bool emulateFlushCompletion = !flushPortAsync(kPortIndexBoth);

                  if (emulateFlushCompletion) {
                    onCmdComplete(OMX_CommandFlush, kPortIndexBoth);
                  }
                }
                else {
#endif
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
#ifdef QCOM_HARDWARE
               }
#endif
            } else {
                mPortStatus[kPortIndexInput] = SHUTTING_DOWN;
                mPortStatus[kPortIndexOutput] = SHUTTING_DOWN;

                status_t err =
                    mOMX->sendCommand(mNode, OMX_CommandStateSet, OMX_StateIdle);
                CHECK_EQ(err, (status_t)OK);
            }

            while (mState != LOADED && mState != ERROR) {
                mAsyncCompletion.wait(mLock);
            }

            if (isError) {
                // We were in the ERROR state coming in, so restore that now
                // that we've idled the OMX component.
                setState(ERROR);
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

    CODEC_LOGV("stopped in state %d", mState);

    return OK;
}

sp<MetaData> OMXCodec::getFormat() {
    Mutex::Autolock autoLock(mLock);

    return mOutputFormat;
}

status_t OMXCodec::read(
        MediaBuffer **buffer, const ReadOptions *options) {
    status_t err = OK;
    *buffer = NULL;

    Mutex::Autolock autoLock(mLock);

    if (mState != EXECUTING && mState != RECONFIGURING) {
        return UNKNOWN_ERROR;
    }

    bool seeking = false;
    int64_t seekTimeUs;
    ReadOptions::SeekMode seekMode;
    if (options && options->getSeekTo(&seekTimeUs, &seekMode)) {
        seeking = true;
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
        while (mState == RECONFIGURING) {
            if ((err = waitForBufferFilled_l()) != OK) {
                return err;
            }
        }

        if (mState != EXECUTING) {
            return UNKNOWN_ERROR;
        }

        CODEC_LOGV("seeking to %lld us (%.2f secs)", seekTimeUs, seekTimeUs / 1E6);

        mSignalledEOS = false;

        CHECK(seekTimeUs >= 0);
        mSeekTimeUs = seekTimeUs;
        mSeekMode = seekMode;

        mFilledBuffers.clear();

        CHECK_EQ((int)mState, (int)EXECUTING);
#ifdef QCOM_HARDWARE
        setState(FLUSHING);
        //DSP supports flushing of ports simultaneously. Flushing individual port is not supported.

        if(mQuirks & kRequiresGlobalFlush) {
          bool emulateFlushCompletion = !flushPortAsync(kPortIndexBoth);
          if (emulateFlushCompletion) {
              onCmdComplete(OMX_CommandFlush, kPortIndexBoth);
          }
        } else {
#endif

        bool emulateInputFlushCompletion = !flushPortAsync(kPortIndexInput);
        bool emulateOutputFlushCompletion = !flushPortAsync(kPortIndexOutput);

        if (emulateInputFlushCompletion) {
            onCmdComplete(OMX_CommandFlush, kPortIndexInput);
        }

        if (emulateOutputFlushCompletion) {
            onCmdComplete(OMX_CommandFlush, kPortIndexOutput);
#ifdef QCOM_HARDWARE
        }
#endif
        }

        while (mSeekTimeUs >= 0) {
            if ((err = waitForBufferFilled_l()) != OK) {
                return err;
            }
        }
    }

#ifdef QCOM_HARDWARE
    if (!strncmp("OMX.ittiam.video.decoder", mComponentName, 24)) {
        OMX_INDEXTYPE index;
        OMX_U32 set_level = 0;
        #define LC_LEVEL2_HIGH_CUTOFF 125000
        #define LC_LEVEL2_LOW_CUTOFF 100000
        #define LC_LEVEL1_HIGH_CUTOFF 75000
        #define LC_LEVEL1_LOW_CUTOFF 50000
        CODEC_LOGV("OMXCodec latenessUs = %lld", latenessUs);
        if((LC_level == 0) && (latenessUs > LC_LEVEL1_HIGH_CUTOFF))
        {
          LC_level = 1;
          set_level = 1;
        }
        else if((LC_level == 1) && (latenessUs > LC_LEVEL2_HIGH_CUTOFF))
        {
          LC_level = 2;
          set_level = 1;
        }
        else if((LC_level == 1) && (latenessUs < LC_LEVEL1_LOW_CUTOFF))
        {
          LC_level = 0;
          set_level = 1;
        }
        else if((LC_level == 2) && (latenessUs < LC_LEVEL2_LOW_CUTOFF))
        {
          LC_level = 1;
          set_level = 1;
        }
        if(set_level == 1)
        {
          CODEC_LOGV("set_level = 1");
          status_t err = mOMX->getExtensionIndex(
            mNode,
            "OMX.ITTIAM.index.LClevel",
            &index);
          if (err != OK) {
              return err;
          }
          mOMX->setConfig(mNode, index, &LC_level, sizeof(LC_level));
        }
    }
#endif
    while (mState != ERROR && !mNoMoreOutputData && mFilledBuffers.empty()) {
        if ((err = waitForBufferFilled_l()) != OK) {
            return err;
        }
    }

    if (mState == ERROR) {
        return UNKNOWN_ERROR;
    }
#ifdef QCOM_HARDWARE
    if(seeking) {
        CHECK_EQ((int)mState, (int)FLUSHING);
        setState(EXECUTING);
    }
#endif

    if (mFilledBuffers.empty()) {
        return mSignalledEOS ? mFinalStatus : ERROR_END_OF_STREAM;
    }

    if (mOutputPortSettingsHaveChanged) {
        mOutputPortSettingsHaveChanged = false;

        return INFO_FORMAT_CHANGED;
    }

    size_t index = *mFilledBuffers.begin();
    mFilledBuffers.erase(mFilledBuffers.begin());

    BufferInfo *info = &mPortBuffers[kPortIndexOutput].editItemAt(index);
    CHECK_EQ((int)info->mStatus, (int)OWNED_BY_US);
    info->mStatus = OWNED_BY_CLIENT;

    info->mMediaBuffer->add_ref();
    *buffer = info->mMediaBuffer;

    return OK;
}

void OMXCodec::signalBufferReturned(MediaBuffer *buffer) {
    Mutex::Autolock autoLock(mLock);

    Vector<BufferInfo> *buffers = &mPortBuffers[kPortIndexOutput];
    for (size_t i = 0; i < buffers->size(); ++i) {
        BufferInfo *info = &buffers->editItemAt(i);

        if (info->mMediaBuffer == buffer) {
            CHECK_EQ((int)mPortStatus[kPortIndexOutput], (int)ENABLED);
            CHECK_EQ((int)info->mStatus, (int)OWNED_BY_CLIENT);

            info->mStatus = OWNED_BY_US;

            if (buffer->graphicBuffer() == 0) {
                fillOutputBuffer(info);
            } else {
                sp<MetaData> metaData = info->mMediaBuffer->meta_data();
                int32_t rendered = 0;
                if (!metaData->findInt32(kKeyRendered, &rendered)) {
                    rendered = 0;
                }
#ifdef QCOM_HARDWARE
                // Set latenessUs here from kKeyLateness
                metaData->findInt64(kKeyLateness, &latenessUs);
#endif
                if (!rendered) {
                    status_t err = cancelBufferToNativeWindow(info);
                    if (err < 0) {
                        return;
                    }
                }

                info->mStatus = OWNED_BY_NATIVE_WINDOW;

                // Dequeue the next buffer from the native window.
                BufferInfo *nextBufInfo = dequeueBufferFromNativeWindow();
                if (nextBufInfo == 0) {
                    return;
                }

                // Give the buffer to the OMX node to fill.
                fillOutputBuffer(nextBufInfo);
            }
            return;
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

    if (type == OMX_TI_COLOR_FormatYUV420PackedSemiPlanar) {
        return "OMX_TI_COLOR_FormatYUV420PackedSemiPlanar";
    } else if (type == OMX_QCOM_COLOR_FormatYVU420SemiPlanar) {
        return "OMX_QCOM_COLOR_FormatYVU420SemiPlanar";
#ifdef QCOM_HARDWARE
    } else if (type == QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka) {
        return "QOMX_COLOR_FormatYVU420PackedSemiPlanar32m4ka";
    } else if (type == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka) {
        return "QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka";
#endif
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
    };

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
    CHECK_EQ(err, (status_t)OK);

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
                CHECK_EQ(err, (status_t)OK);

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
                CHECK_EQ(err, (status_t)OK);

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

status_t OMXCodec::initNativeWindow() {
    // Enable use of a GraphicBuffer as the output for this node.  This must
    // happen before getting the IndexParamPortDefinition parameter because it
    // will affect the pixel format that the node reports.
    status_t err = mOMX->enableGraphicBuffers(mNode, kPortIndexOutput, OMX_TRUE);
    if (err != 0) {
        return err;
    }

    return OK;
}

void OMXCodec::initNativeWindowCrop() {
    int32_t left, top, right, bottom;

    CHECK(mOutputFormat->findRect(
                        kKeyCropRect,
                        &left, &top, &right, &bottom));

    android_native_rect_t crop;
    crop.left = left;
    crop.top = top;
    crop.right = right + 1;
    crop.bottom = bottom + 1;

    // We'll ignore any errors here, if the surface is
    // already invalid, we'll know soon enough.
    native_window_set_crop(mNativeWindow.get(), &crop);
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
    CHECK_EQ(err, (status_t)OK);

    switch (def.eDomain) {
        case OMX_PortDomainImage:
        {
            OMX_IMAGE_PORTDEFINITIONTYPE *imageDef = &def.format.image;
            CHECK_EQ((int)imageDef->eCompressionFormat,
                     (int)OMX_IMAGE_CodingUnused);

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
                CHECK_EQ(err, (status_t)OK);

                CHECK_EQ((int)params.eNumData, (int)OMX_NumericalDataSigned);
                CHECK_EQ(params.nBitPerSample, 16u);
                CHECK_EQ((int)params.ePCMMode, (int)OMX_AUDIO_PCMModeLinear);

                int32_t numChannels, sampleRate;
                inputFormat->findInt32(kKeyChannelCount, &numChannels);
                inputFormat->findInt32(kKeySampleRate, &sampleRate);

                if ((OMX_U32)numChannels != params.nChannels) {
                    LOGV("Codec outputs a different number of channels than "
                         "the input stream contains (contains %d channels, "
                         "codec outputs %ld channels).",
                         numChannels, params.nChannels);
                }

                if (sampleRate != (int32_t)params.nSamplingRate) {
                    LOGV("Codec outputs at different sampling rate than "
                         "what the input stream contains (contains data at "
                         "%d Hz, codec outputs %lu Hz)",
                         sampleRate, params.nSamplingRate);
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

                mOutputFormat->setInt32(kKeySampleRate, params.nSamplingRate);
            } else if (audio_def->eEncoding == OMX_AUDIO_CodingAMR) {
                OMX_AUDIO_PARAM_AMRTYPE amr;
                InitOMXParams(&amr);
                amr.nPortIndex = kPortIndexOutput;

                err = mOMX->getParameter(
                        mNode, OMX_IndexParamAudioAmr, &amr, sizeof(amr));
                CHECK_EQ(err, (status_t)OK);

                CHECK_EQ(amr.nChannels, 1u);
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
#ifdef QCOM_HARDWARE
            } else if (audio_def->eEncoding == OMX_AUDIO_CodingQCELP13 ) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_QCELP);
                int32_t numChannels, sampleRate, bitRate;
                inputFormat->findInt32(kKeyChannelCount, &numChannels);
                inputFormat->findInt32(kKeySampleRate, &sampleRate);
                inputFormat->findInt32(kKeyBitRate, &bitRate);
                mOutputFormat->setInt32(kKeyChannelCount, numChannels);
                mOutputFormat->setInt32(kKeySampleRate, sampleRate);
                mOutputFormat->setInt32(kKeyBitRate, bitRate);
            } else if (audio_def->eEncoding == OMX_AUDIO_CodingEVRC ) {
                mOutputFormat->setCString(
                        kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_EVRC);
                int32_t numChannels, sampleRate, bitRate;
                inputFormat->findInt32(kKeyChannelCount, &numChannels);
                inputFormat->findInt32(kKeySampleRate, &sampleRate);
                inputFormat->findInt32(kKeyBitRate, &bitRate);
                mOutputFormat->setInt32(kKeyChannelCount, numChannels);
                mOutputFormat->setInt32(kKeySampleRate, sampleRate);
                mOutputFormat->setInt32(kKeyBitRate, bitRate);
#endif
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
            } else {
                CHECK(!"Unknown compression format.");
            }

            mOutputFormat->setInt32(kKeyWidth, video_def->nFrameWidth);
            mOutputFormat->setInt32(kKeyHeight, video_def->nFrameHeight);
            mOutputFormat->setInt32(kKeyColorFormat, video_def->eColorFormat);

            if (!mIsEncoder) {
                OMX_CONFIG_RECTTYPE rect;
                InitOMXParams(&rect);
                rect.nPortIndex = kPortIndexOutput;
                status_t err =
                        mOMX->getConfig(
                            mNode, OMX_IndexConfigCommonOutputCrop,
                            &rect, sizeof(rect));

                CODEC_LOGI(
                        "video dimensions are %ld x %ld",
                        video_def->nFrameWidth, video_def->nFrameHeight);

                if (err == OK) {
                    CHECK_GE(rect.nLeft, 0);
                    CHECK_GE(rect.nTop, 0);
                    CHECK_GE(rect.nWidth, 0u);
                    CHECK_GE(rect.nHeight, 0u);
#ifdef QCOM_HARDWARE
                    /* Due to padding requirements of Ittiam SW decoder, nStride,
                     * nSliceHeight contains actual buffer width where as crop will
                     * give actual frame resolution.
                     * TBD: implement padding without changing crop values in next
                     * release and remove the below check */
                    if (!strncmp("OMX.ittiam.video.decoder", mComponentName, 24)) {
                        CHECK_LE(rect.nLeft + rect.nWidth - 1, (video_def->nStride));
                        CHECK_LE(rect.nTop + rect.nHeight - 1, (video_def->nSliceHeight));
                    }
                    else {
                        CHECK_LE(rect.nLeft + rect.nWidth - 1, video_def->nFrameWidth);
                        CHECK_LE(rect.nTop + rect.nHeight - 1, video_def->nFrameHeight);
                    }
#else
                    CHECK_LE(rect.nLeft + rect.nWidth - 1, video_def->nFrameWidth);
                    CHECK_LE(rect.nTop + rect.nHeight - 1, video_def->nFrameHeight);
#endif
                    mOutputFormat->setRect(
                            kKeyCropRect,
                            rect.nLeft,
                            rect.nTop,
                            rect.nLeft + rect.nWidth - 1,
                            rect.nTop + rect.nHeight - 1);

                    CODEC_LOGI(
                            "Crop rect is %ld x %ld @ (%ld, %ld)",
                            rect.nWidth, rect.nHeight, rect.nLeft, rect.nTop);
                } else {
                    mOutputFormat->setRect(
                            kKeyCropRect,
                            0, 0,
                            video_def->nFrameWidth - 1,
                            video_def->nFrameHeight - 1);
                }

                if (mNativeWindow != NULL) {
                     initNativeWindowCrop();
                }
#ifdef QCOM_HARDWARE
            } else {
                int32_t width, height;
                bool success = inputFormat->findInt32(kKeyWidth, &width) &&
                               inputFormat->findInt32(kKeyHeight, &height);
                CHECK(success);
                int32_t frameRate = 0, hfr = 0;

                success = inputFormat->findInt32(kKeyHFR, &hfr);
                success = inputFormat->findInt32(kKeyFrameRate, &frameRate);

                mOutputFormat->setInt32(kKeyWidth, width);
                mOutputFormat->setInt32(kKeyHeight, height);
                mOutputFormat->setInt32(kKeyHFR, hfr);
                mOutputFormat->setInt32(kKeyFrameRate, frameRate);
#endif
            }
            break;
        }

        default:
        {
            CHECK(!"should not be here, neither audio nor video.");
            break;
        }
    }

    // If the input format contains rotation information, flag the output
    // format accordingly.

    int32_t rotationDegrees;
    if (mSource->getFormat()->findInt32(kKeyRotation, &rotationDegrees)) {
        mOutputFormat->setInt32(kKeyRotation, rotationDegrees);
    }
}

#ifdef QCOM_HARDWARE
status_t OMXCodec::pause() {
   CODEC_LOGV("pause mState=%d", mState);

   Mutex::Autolock autoLock(mLock);

   if (mState != EXECUTING) {
       return UNKNOWN_ERROR;
   }

   while (isIntermediateState(mState)) {
       mAsyncCompletion.wait(mLock);
   }
   if (!strncmp(mComponentName, "OMX.qcom.", 9)) {
       status_t err = mOMX->sendCommand(mNode,
           OMX_CommandStateSet, OMX_StatePause);
       CHECK_EQ(err, (status_t)OK);
       setState(PAUSING);

       mPaused = true;
       while (mState != PAUSED && mState != ERROR) {
           mAsyncCompletion.wait(mLock);
       }
       return mState == ERROR ? UNKNOWN_ERROR : OK;
   } else {
       mPaused = true;
       return OK;
   }

}
#else
status_t OMXCodec::pause() {
    Mutex::Autolock autoLock(mLock);

    mPaused = true;

    return OK;
}
#endif

#ifdef QCOM_HARDWARE
void OMXCodec::parseFlags() {
    //TODO - uncomment if needed
    //    mGPUComposition = ((flags & kEnableGPUComposition) ? true : false);
    mThumbnailMode = ((mFlags & kEnableThumbnailMode) ? true : false);
}
#endif

////////////////////////////////////////////////////////////////////////////////

status_t QueryCodecs(
        const sp<IOMX> &omx,
        const char *mime, bool queryDecoders, bool hwCodecOnly,
        Vector<CodecCapabilities> *results) {
    Vector<String8> matchingCodecs;
    results->clear();

    OMXCodec::findMatchingCodecs(mime,
            !queryDecoders /*createEncoder*/,
            NULL /*matchComponentName*/,
            hwCodecOnly ? OMXCodec::kHardwareCodecsOnly : 0 /*flags*/,
            &matchingCodecs);

    for (size_t c = 0; c < matchingCodecs.size(); c++) {
        const char *componentName = matchingCodecs.itemAt(c).string();

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

        // Color format query
        OMX_VIDEO_PARAM_PORTFORMATTYPE portFormat;
        InitOMXParams(&portFormat);
        portFormat.nPortIndex = queryDecoders ? 1 : 0;
        for (portFormat.nIndex = 0;; ++portFormat.nIndex)  {
            err = omx->getParameter(
                    node, OMX_IndexParamVideoPortFormat,
                    &portFormat, sizeof(portFormat));
            if (err != OK) {
                break;
            }
            caps->mColorFormats.push(portFormat.eColorFormat);
        }

        CHECK_EQ(omx->freeNode(node), (status_t)OK);
    }

    return OK;
}

status_t QueryCodecs(
        const sp<IOMX> &omx,
        const char *mimeType, bool queryDecoders,
        Vector<CodecCapabilities> *results) {
    return QueryCodecs(omx, mimeType, queryDecoders, false /*hwCodecOnly*/, results);
}

void OMXCodec::restorePatchedDataPointer(BufferInfo *info) {
    CHECK(mIsEncoder && (mQuirks & kAvoidMemcopyInputRecordingFrames));
    CHECK(mOMXLivesLocally);

    OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *)info->mBuffer;
    header->pBuffer = (OMX_U8 *)info->mData;
}

#ifdef QCOM_HARDWARE
void OMXCodec::setEVRCFormat(int32_t numChannels, int32_t sampleRate, int32_t bitRate) {
    if (mIsEncoder) {
      CHECK(numChannels == 1);
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
                    &format, sizeof(format)), (status_t)OK);
            if (format.eEncoding == OMX_AUDIO_CodingEVRC) {
                break;
            }
            format.nIndex++;
        }
        CHECK_EQ((status_t)OK, err);
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioPortFormat,
                &format, sizeof(format)), (status_t)OK);

        // port definition
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        def.format.audio.cMIMEType = NULL;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);
        def.format.audio.bFlagErrorConcealment = OMX_TRUE;
        def.format.audio.eEncoding = OMX_AUDIO_CodingEVRC;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);

        // profile
        OMX_AUDIO_PARAM_EVRCTYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamAudioEvrc,
                &profile, sizeof(profile)), (status_t)OK);
        profile.nChannels = numChannels;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioEvrc,
                &profile, sizeof(profile)), (status_t)OK);
    }
    else{
      LOGI("EVRC decoder \n");
    }
}

void OMXCodec::setQCELPFormat(int32_t numChannels, int32_t sampleRate, int32_t bitRate) {
    if (mIsEncoder) {
        CHECK(numChannels == 1);
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
                    &format, sizeof(format)), (status_t)OK);
            if (format.eEncoding == OMX_AUDIO_CodingQCELP13) {
                break;
            }
            format.nIndex++;
        }
        CHECK_EQ((status_t)OK, err);
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioPortFormat,
                &format, sizeof(format)), (status_t)OK);

        // port definition
        OMX_PARAM_PORTDEFINITIONTYPE def;
        InitOMXParams(&def);
        def.nPortIndex = kPortIndexOutput;
        def.format.audio.cMIMEType = NULL;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);
        def.format.audio.bFlagErrorConcealment = OMX_TRUE;
        def.format.audio.eEncoding = OMX_AUDIO_CodingQCELP13;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamPortDefinition,
                &def, sizeof(def)), (status_t)OK);

        // profile
        OMX_AUDIO_PARAM_QCELP13TYPE profile;
        InitOMXParams(&profile);
        profile.nPortIndex = kPortIndexOutput;
        CHECK_EQ(mOMX->getParameter(mNode, OMX_IndexParamAudioQcelp13,
                &profile, sizeof(profile)), (status_t)OK);
        profile.nChannels = numChannels;
        CHECK_EQ(mOMX->setParameter(mNode, OMX_IndexParamAudioQcelp13,
                &profile, sizeof(profile)), (status_t)OK);
    }
    else{
      LOGI("QCELP decoder \n");
    }
}

status_t OMXCodec::processSEIData() {
    if (!m3DVideoDetected)
    {
        CODEC_LOGI("In processSEIData");
        //We don't want to continue checking every buffer, so we mark as 3D detected
        //regardless. We only take action by xoring the 3d flag when cancel_flag is set
        m3DVideoDetected = true;


        OMX_QCOM_FRAME_PACK_ARRANGEMENT arrangementInfo;
        arrangementInfo.cancel_flag = 1; //Need to initialize to 1, because the core doesn't touch this
                                         //struct if video is not H264
        status_t err = mOMX->getConfig(mNode, (OMX_INDEXTYPE)OMX_QcomIndexConfigVideoFramePackingArrangement,
                                       &arrangementInfo, (size_t)sizeof(arrangementInfo));
        if (err != OK)
        {
            LOGV("Not supported config OMX_QcomIndexConfigVideoFramePackingArrangement");
            return OK;
        }

        if (arrangementInfo.cancel_flag != 1)
        {
            int width, height, colorFormat;
            CHECK(mOutputFormat->findInt32(kKeyWidth, &width));
            CHECK(mOutputFormat->findInt32(kKeyHeight, &height));
            CHECK(mOutputFormat->findInt32(kKeyColorFormat, &colorFormat));

            colorFormat = (colorFormat == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka) ?
                HAL_PIXEL_FORMAT_YCbCr_420_SP_TILED : colorFormat;

            bool flip = (arrangementInfo.content_interpretation_type == 2); //LR should be treated as RL

            if (arrangementInfo.type == 3) //side-by-side
            {
                if (flip)
                    colorFormat |= HAL_3D_OUT_SIDE_BY_SIDE | HAL_3D_IN_SIDE_BY_SIDE_R_L;
                else
                    colorFormat |= HAL_3D_OUT_SIDE_BY_SIDE | HAL_3D_IN_SIDE_BY_SIDE_L_R;
            }
            else if (arrangementInfo.type == 4) //top-bottom
            {
                if (flip)
                    LOGE("flipping top-bottom 3d video not supported, continuing to display as top bottom");
                colorFormat |= HAL_3D_OUT_TOP_BOTTOM | HAL_3D_IN_TOP_BOTTOM;
            }
            else
                LOGW("This is supposedly a 3d video but the frame arragement [%d] is not supported", (int)arrangementInfo.type);
            err = mNativeWindow.get()->perform(mNativeWindow.get(), NATIVE_WINDOW_UPDATE_BUFFERS_GEOMETRY, width, height, colorFormat);
            if (err != 0) {
                LOGE("native_window_update_buffers_geometry failed: %s (%d)",
                        strerror(-err), -err);
                return err;
            }

            LOGW("Is a 3D video");
        }
    }
    return OK;
}
#endif

}  // namespace android
