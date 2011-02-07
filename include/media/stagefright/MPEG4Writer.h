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

#ifndef MPEG4_WRITER_H_

#define MPEG4_WRITER_H_

#include <stdio.h>

#include <media/stagefright/MediaWriter.h>
#include <utils/List.h>
#include <utils/threads.h>

namespace android {

class MediaBuffer;
class MediaSource;
class MetaData;

class MPEG4Writer : public MediaWriter {
public:
    MPEG4Writer(const char *filename);
    MPEG4Writer(int fd);

    virtual status_t addSource(const sp<MediaSource> &source);
    virtual status_t start(MetaData *param = NULL);
    virtual status_t stop();
    virtual status_t pause();
    virtual bool reachedEOS();
    virtual status_t dump(int fd, const Vector<String16>& args);

    void beginBox(const char *fourcc);
    void writeInt8(int8_t x);
    void writeInt16(int16_t x);
    void writeInt32(int32_t x);
    void writeInt64(int64_t x);
    void writeCString(const char *s);
    void writeFourcc(const char *fourcc);
    void write(const void *data, size_t size);
    void endBox();
    uint32_t interleaveDuration() const { return mInterleaveDurationUs; }
    status_t setInterleaveDuration(uint32_t duration);
    int32_t getTimeScale() const { return mTimeScale; }

protected:
    virtual ~MPEG4Writer();

private:
    class Track;

    FILE *mFile;
    bool mUse4ByteNalLength;
    bool mUse32BitOffset;
    bool mIsFileSizeLimitExplicitlyRequested;
    bool mPaused;
    bool mStarted;
    off64_t mOffset;
    off64_t mMdatOffset;
    uint8_t *mMoovBoxBuffer;
    off64_t mMoovBoxBufferOffset;
    bool  mWriteMoovBoxToMemory;
    off64_t mFreeBoxOffset;
    bool mStreamableFile;
    off64_t mEstimatedMoovBoxSize;
    uint32_t mInterleaveDurationUs;
    int32_t mTimeScale;
    int64_t mStartTimestampUs;

    Mutex mLock;

    List<Track *> mTracks;

    List<off64_t> mBoxes;

    void setStartTimestampUs(int64_t timeUs);
    int64_t getStartTimestampUs();  // Not const
    status_t startTracks(MetaData *params);
    size_t numTracks();
    int64_t estimateMoovBoxSize(int32_t bitRate);

    struct Chunk {
        Track               *mTrack;        // Owner
        int64_t             mTimeStampUs;   // Timestamp of the 1st sample
        List<MediaBuffer *> mSamples;       // Sample data

        // Convenient constructor
        Chunk(Track *track, int64_t timeUs, List<MediaBuffer *> samples)
            : mTrack(track), mTimeStampUs(timeUs), mSamples(samples) {
        }

    };
    struct ChunkInfo {
        Track               *mTrack;        // Owner
        List<Chunk>         mChunks;        // Remaining chunks to be written
    };

    bool            mIsFirstChunk;
    volatile bool   mDone;                  // Writer thread is done?
    pthread_t       mThread;                // Thread id for the writer
    List<ChunkInfo> mChunkInfos;            // Chunk infos
    Condition       mChunkReadyCondition;   // Signal that chunks are available

    // Writer thread handling
    status_t startWriterThread();
    void stopWriterThread();
    static void *ThreadWrapper(void *me);
    void threadFunc();

    // Buffer a single chunk to be written out later.
    void bufferChunk(const Chunk& chunk);

    // Write all buffered chunks from all tracks
    void writeChunks();

    // Write a chunk if there is one
    status_t writeOneChunk();

    // Write the first chunk from the given ChunkInfo.
    void writeFirstChunk(ChunkInfo* info);

    // Adjust other track media clock (presumably wall clock)
    // based on audio track media clock with the drift time.
    int64_t mDriftTimeUs;
    void setDriftTimeUs(int64_t driftTimeUs);
    int64_t getDriftTimeUs();

    // Return whether the nal length is 4 bytes or 2 bytes
    // Only makes sense for H.264/AVC
    bool useNalLengthFour();

    void lock();
    void unlock();

    // Acquire lock before calling these methods
    off64_t addSample_l(MediaBuffer *buffer);
    off64_t addLengthPrefixedSample_l(MediaBuffer *buffer);

    inline size_t write(const void *ptr, size_t size, size_t nmemb, FILE* stream);
    bool exceedsFileSizeLimit();
    bool use32BitFileOffset() const;
    bool exceedsFileDurationLimit();
    bool isFileStreamable() const;
    void trackProgressStatus(const Track* track, int64_t timeUs, status_t err = OK);
    void writeCompositionMatrix(int32_t degrees);

    MPEG4Writer(const MPEG4Writer &);
    MPEG4Writer &operator=(const MPEG4Writer &);
};

}  // namespace android

#endif  // MPEG4_WRITER_H_
