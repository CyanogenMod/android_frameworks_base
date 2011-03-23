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
#define LOG_TAG "AMRExtractor"
#include <utils/Log.h>

#include "include/AMRExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>

namespace android {

class AMRSource : public MediaSource {
public:
    AMRSource(const sp<DataSource> &source,
              const sp<MetaData> &meta,
              size_t frameSize,
              bool isWide,
              List<AMRFrameTableEntry> mAMRFrameTableEntries,
              uint64_t mTotalFrames);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~AMRSource();

private:
    sp<DataSource> mDataSource;
    sp<MetaData> mMeta;
    uint64_t mTotalFrames;
    List<AMRFrameTableEntry> mAMRFrameTableEntries;

    size_t mFrameSize;
    bool mIsWide;

    off_t mOffset;
    int64_t mCurrentTimeUs;
    bool mStarted;
    MediaBufferGroup *mGroup;

    AMRSource(const AMRSource &);
    AMRSource &operator=(const AMRSource &);
};

////////////////////////////////////////////////////////////////////////////////
#define MAX_AMRMODE 16
static size_t getFrameSize(bool isWide, unsigned FT) {
//RFC 4867
    static const size_t kFrameSizeNB[MAX_AMRMODE] = { 13      // AMR 4.75 Kbps
          , 14        // AMR 5.15 Kbps
          , 16        // AMR 5.90 Kbps
          , 18        // AMR 6.70 Kbps
          , 20        // AMR 7.40 Kbps
          , 21        // AMR 7.95 Kbps
          , 27        // AMR 10.2 Kbps
          , 32        // AMR 12.2 Kbps
          , 6     // GsmAmr comfort noise
          , 7     // Gsm-Efr comfort noise
          , 6     // IS-641 comfort noise
          , 6     // Pdc-Efr comfort noise
          , 1     // future use; 0 length but set to 1 to skip the frame type byte
          , 1     // future use; 0 length but set to 1 to skip the frame type byte
          , 1     // future use; 0 length but set to 1 to skip the frame type byte
          , 1     // AMR Frame No Data
    };
    static const size_t kFrameSizeWB[MAX_AMRMODE] = { 18      // AMR-WB 6.60 Kbps
          , 24        // AMR-WB 8.85 Kbps
          , 33        // AMR-WB 12.65 Kbps
          , 37        // AMR-WB 14.25 Kbps
          , 41        // AMR-WB 15.85 Kbps
          , 47        // AMR-WB 18.25 Kbps
          , 51        // AMR-WB 19.85 Kbps
          , 59        // AMR-WB 23.05 Kbps
          , 61        // AMR-WB 23.85 Kbps
          , 6     // AMR-WB SID
          , 1
          , 1
          , 1
          , 1
          , 1     // WBAMR Frame No Data
          , 1     // WBAMR Frame No Data
    };

    size_t frameSize = isWide ? kFrameSizeWB[FT] : kFrameSizeNB[FT];

    return frameSize;
}

AMRExtractor::AMRExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mInitCheck(NO_INIT) {
    String8 mimeType;
    float confidence;
    if (!SniffAMR(mDataSource, &mimeType, &confidence, NULL)) {
        return;
    }

    mIsWide = (mimeType == MEDIA_MIMETYPE_AUDIO_AMR_WB);

    mMeta = new MetaData;
    mMeta->setCString(
            kKeyMIMEType, mIsWide ? MEDIA_MIMETYPE_AUDIO_AMR_WB
                                  : MEDIA_MIMETYPE_AUDIO_AMR_NB);

    mMeta->setInt32(kKeyChannelCount, 1);
    mMeta->setInt32(kKeySampleRate, mIsWide ? 16000 : 8000);

    off_t offset = mIsWide ? 9 : 6;

    uint32_t mFrmNumber;

    //add first sample entry mIsWide ? 9 : 6;
    {
       AMRFrameTableEntry amrframetableentry(1, offset, 0);
       mAMRFrameTableEntries.push_back(amrframetableentry);
    }

    ssize_t n = 0;
    mFrmNumber = 0;
    mTotalFrames = 0;
    uint8_t header;

    n = mDataSource->readAt(offset, &header, 1);

    if (header & 0x83) {
          LOGE("padding bits must be 0, header is 0x%02x", header);
          return;
    }

    if(n<1){
        LOGE("AMRxtractor: header incorrect");
        return;
    }

    unsigned FT = (header >> 3) & 0x0f;

    if (FT > MAX_AMRMODE || (!mIsWide && FT > MAX_AMRMODE)) {
        LOGE("UnSupported AMR FrameType(%d) ERROR",FT);
        return;
    }

    mFrameSize = getFrameSize(mIsWide, FT);

    size_t framesize = mFrameSize;
    size_t numframes = 1;
    size_t framerate = FT;

    mFrmNumber++;
    offset = offset+mFrameSize;

    while(n)
    {
        n = mDataSource->readAt(offset, &header, 1);

        if(n<1){
           LOGI("EOF reached");
           break;
        }

        if (header & 0x83) {
        LOGE("padding bits must be 0, header is 0x%02x", header);
        return; //ERROR_MALFORMED;
        }

        unsigned FT = (header >> 3) & 0x0f;
        if (FT > MAX_AMRMODE) {
            LOGE("UnSupported AMR FrameType(%d) ERROR",FT);
            return;
        }

        mFrameSize = getFrameSize(mIsWide, FT);
        size_t framesize1 = mFrameSize;
        size_t framerate1 = FT;

        mFrmNumber++;
        offset = offset+mFrameSize;

        if(framerate1 != framerate)
        {
           /* When framerate is different then we store the chunk back on the
            * list
            */
           AMRFrameTableEntry amrframetableentry(numframes, framesize, framerate);
           mAMRFrameTableEntries.push_back(amrframetableentry);

           framesize = framesize1;
           framerate = framerate1;
           numframes = 1;
        }
        else
           numframes++;
    }

    {
       AMRFrameTableEntry amrframetableentry(numframes, framesize, framerate);
       mAMRFrameTableEntries.push_back(amrframetableentry);
       mTotalFrames = mFrmNumber;
    }

    uint32_t numFrames = mFrmNumber;
    int64_t duration = 20000ll * ((int64_t)numFrames+1);
    mMeta->setInt64(kKeyDuration, duration);
    mInitCheck = OK;
}

AMRExtractor::~AMRExtractor() {
}

sp<MetaData> AMRExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, mIsWide ? "audio/amr-wb" : "audio/amr");

    return meta;
}

size_t AMRExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<MediaSource> AMRExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

    return new AMRSource(mDataSource, mMeta, mFrameSize, mIsWide, mAMRFrameTableEntries, mTotalFrames);
}

sp<MetaData> AMRExtractor::getTrackMetaData(size_t index, uint32_t flags) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

    return mMeta;
}

////////////////////////////////////////////////////////////////////////////////

AMRSource::AMRSource(
        const sp<DataSource> &source, const sp<MetaData> &meta,
        size_t frameSize, bool isWide, List<AMRFrameTableEntry> AMRFrameTableEntries,
        uint64_t TotalFrames)
    : mDataSource(source),
      mMeta(meta),
      mTotalFrames(TotalFrames),
      mAMRFrameTableEntries(AMRFrameTableEntries),
      mFrameSize(frameSize),
      mIsWide(isWide),
      mOffset(mIsWide ? 9 : 6),
      mCurrentTimeUs(0),
      mStarted(false),
      mGroup(NULL) {
}

AMRSource::~AMRSource() {
    if (mStarted) {
        stop();
    }
}

status_t AMRSource::start(MetaData *params) {
    CHECK(!mStarted);

    mOffset = mIsWide ? 9 : 6;
    mCurrentTimeUs = 0;
    mGroup = new MediaBufferGroup;
    //if crash observed during playback then the size needs to be increased
    mGroup->add_buffer(new MediaBuffer(128));
    mStarted = true;

    return OK;
}

status_t AMRSource::stop() {
    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;

    mStarted = false;
    return OK;
}

sp<MetaData> AMRSource::getFormat() {
    return mMeta;
}

status_t AMRSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        uint64_t seekFrame = seekTimeUs / 20000ll;  // 20ms per frame.
        mCurrentTimeUs = seekFrame * 20000ll;
        uint32_t framesize=0;
        uint64_t offset = 0, numframes = 0;
        seekFrame = seekFrame + 1; //why seekframe+1, since the array starts from zero
        LOGI("seekframe %lld", seekFrame);
        for (List<AMRFrameTableEntry>::iterator it = mAMRFrameTableEntries.begin();
               it != mAMRFrameTableEntries.end(); ++it) {

             numframes = it->mNumFrames;
             framesize = it->mFrameSize;
             if(seekFrame >= mTotalFrames)
             {
               LOGE("seek beyond EOF");
               return ERROR_OUT_OF_RANGE;
             }

             if(seekFrame > numframes)
             {
               offset = offset + (numframes * framesize);
               seekFrame = seekFrame - numframes;
               LOGV("> offset %lld seekFrame %lld numframes %lld framesize %d", offset, seekFrame, numframes, framesize);
             }
             else
             {
               offset = offset + (seekFrame * framesize);
               LOGV("!> offset %lld numframes %lld framesize %d", offset, numframes, framesize);
               break;
             }
        }
        mOffset = offset;
    }

    uint8_t header;
    ssize_t n = mDataSource->readAt(mOffset, &header, 1);

    if (n < 1) {
        return ERROR_END_OF_STREAM;
    }

    if (header & 0x83) {
        // Padding bits must be 0.

        LOGE("padding bits must be 0, header is 0x%02x", header);

        return ERROR_MALFORMED;
    }

    unsigned FT = (header >> 3) & 0x0f;

    if (FT > MAX_AMRMODE) {

        LOGE("illegal AMR frame type %d", FT);

        return ERROR_MALFORMED;
    }

    size_t frameSize = getFrameSize(mIsWide, FT);

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }

    n = mDataSource->readAt(mOffset, buffer->data(), frameSize);

    if (n != (ssize_t)frameSize) {
        buffer->release();
        buffer = NULL;

        return ERROR_IO;
    }

    buffer->set_range(0, frameSize);
    buffer->meta_data()->setInt64(kKeyTime, mCurrentTimeUs);
    buffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);

    mOffset += frameSize;
    mCurrentTimeUs += 20000;  // Each frame is 20ms

    *out = buffer;

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

bool SniffAMR(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    char header[9];

    if (source->readAt(0, header, sizeof(header)) != sizeof(header)) {
        return false;
    }

    if (!memcmp(header, "#!AMR\n", 6)) {
        *mimeType = MEDIA_MIMETYPE_AUDIO_AMR_NB;
        *confidence = 0.5;

        return true;
    } else if (!memcmp(header, "#!AMR-WB\n", 9)) {
        *mimeType = MEDIA_MIMETYPE_AUDIO_AMR_WB;
        *confidence = 0.5;

        return true;
    }

    return false;
}

}  // namespace android
