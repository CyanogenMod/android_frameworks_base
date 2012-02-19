/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
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

#ifndef FM_A2DP_WRITER_H_

#define FM_A2DP_WRITER_H_

#include <stdio.h>

#include <media/stagefright/MediaWriter.h>
#include <utils/threads.h>
#include <media/AudioRecord.h>
#include <utils/List.h>
#include <semaphore.h>
#include <media/mediarecorder.h>

namespace android {

struct MediaSource;
struct MetaData;

struct audioBufferstruct {
   public:
   audioBufferstruct (void *buff, size_t bufflen)
      :audioBuffer(buff), bufferlen(bufflen){}

   void  *audioBuffer;
   size_t bufferlen;
 };

struct FMA2DPWriter : public MediaWriter {
    FMA2DPWriter();

    status_t initCheck() const;
    virtual status_t addSource(const sp<MediaSource> &source);
    virtual bool reachedEOS();
    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t pause();
    virtual status_t allocateBufferPool();

protected:
    virtual ~FMA2DPWriter();

private:
    List<audioBufferstruct > mFreeQ,mDataQ;
    Mutex mFreeQLock,mDataQLock;
    sem_t mReaderThreadWakeupsem,mWriterThreadWakeupsem;
    pthread_t mReaderThread,mWriterThread;
    bool mStarted;
    volatile bool mDone;
    int32_t mAudioChannels;
    int32_t mSampleRate;
    int32_t mAudioFormat;
    audio_source_t mAudioSource;
    size_t mBufferSize;
    static void *ReaderThreadWrapper(void *);
    static void *WriterThreadWrapper(void *);
    status_t readerthread();
    status_t writerthread();
    FMA2DPWriter(const FMA2DPWriter &);
    FMA2DPWriter &operator=(const FMA2DPWriter &);
};

}  // namespace android

#endif  // FM_A2DP_WRITER_H_
