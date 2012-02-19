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


//#define LOG_NDEBUG 0
#define LOG_TAG "FMA2DPWriter"
#include <utils/Log.h>


#include <media/stagefright/FMA2DPWriter.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/mediarecorder.h>
#include <sys/prctl.h>
#include <sys/resource.h>

#include <media/AudioRecord.h>
#include <media/AudioTrack.h>
namespace android {

#define BUFFER_POOL_SIZE 5
static int kMaxBufferSize = 2048;

FMA2DPWriter::FMA2DPWriter()
    :mStarted(false),
    mAudioChannels(0),
    mSampleRate(0),
    mAudioFormat(AUDIO_FORMAT_PCM_16_BIT),
    mAudioSource(AUDIO_SOURCE_FM_RX_A2DP),
    mBufferSize(0){
    sem_init(&mReaderThreadWakeupsem,0,0);
    sem_init(&mWriterThreadWakeupsem,0,0);
}



FMA2DPWriter::~FMA2DPWriter() {
    if (mStarted) {
        stop();
    }
    sem_destroy(&mReaderThreadWakeupsem);
    sem_destroy(&mWriterThreadWakeupsem);
}

status_t FMA2DPWriter::initCheck() const {
// API not need for FMA2DPWriter
    return OK;
}


status_t FMA2DPWriter::addSource(const sp<MediaSource> &source) {
// API not need for FMA2DPWriter
    return OK;
}

status_t FMA2DPWriter::allocateBufferPool()
{
    Mutex::Autolock lock(mFreeQLock);

    for (int i = 0; i < BUFFER_POOL_SIZE; ++i) {
        int *buffer = (int*)malloc(mBufferSize);
        if(buffer){
            audioBufferstruct audioBuffer(buffer,mBufferSize);
            mFreeQ.push_back(audioBuffer);
        }
        else{
            LOGE("fatal:failed to alloate buffer pool");
            return  NO_INIT;
        }
    }
    return OK;
}

status_t FMA2DPWriter::start(MetaData *params) {

    if (mStarted) {
        // Already started, does nothing
        return OK;
    }

    if(!mStarted){
        if(!params){
            LOGE("fatal:params cannot be null");
            return NO_INIT;
        }
        CHECK( params->findInt32( kKeyChannelCount, &mAudioChannels ) );
        CHECK(mAudioChannels  == 1 || mAudioChannels  == 2);
        CHECK( params->findInt32( kKeySampleRate, &mSampleRate ) );

        if ( NO_ERROR != AudioSystem::getInputBufferSize(
                    mSampleRate, mAudioFormat, mAudioChannels, &mBufferSize) ){
            mBufferSize = kMaxBufferSize ;
        }
        LOGV("mBufferSize = %d", mBufferSize);
    }

    status_t err = allocateBufferPool();

    if(err != OK)
        return err;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    mDone = false;

    pthread_create(&mReaderThread, &attr, ReaderThreadWrapper, this);
    pthread_create(&mWriterThread, &attr, WriterThreadWrapper, this);

    pthread_attr_destroy(&attr);


    mStarted = true;

    return OK;
}

status_t FMA2DPWriter::pause() {
// API not need for FMA2DPWriter
    return OK;
}

status_t FMA2DPWriter::stop() {
    if (!mStarted) {
        return OK;
    }

    mDone = true;

    void *dummy;
    pthread_join(mReaderThread, &dummy);
    pthread_join(mWriterThread, &dummy);

    for ( List<audioBufferstruct>::iterator it = mDataQ.begin();
         it != mDataQ.end(); ++it){
            free(it->audioBuffer);
    }
    for ( List<audioBufferstruct>::iterator it = mFreeQ.begin();
         it != mFreeQ.end(); ++it){
            free(it->audioBuffer);
    }
    mStarted = false;

    return OK;
}

void *FMA2DPWriter::ReaderThreadWrapper(void *me) {
    return (void *) static_cast<FMA2DPWriter *>(me)->readerthread();
}

void *FMA2DPWriter::WriterThreadWrapper(void *me) {
    return (void *) static_cast<FMA2DPWriter *>(me)->writerthread();
}

status_t FMA2DPWriter::readerthread() {
    status_t err = OK;
    int framecount =((4*mBufferSize)/mAudioChannels)/sizeof(int16_t);
    //sizeof(int16_t) is frame size for PCM stream
    int inChannel =
        (mAudioChannels == 2) ? AUDIO_CHANNEL_IN_STEREO :
        AUDIO_CHANNEL_IN_MONO;

    prctl(PR_SET_NAME, (unsigned long)"FMA2DPReaderThread", 0, 0, 0);

    AudioRecord* record = new AudioRecord(
                     mAudioSource,
                     mSampleRate,
                     mAudioFormat,
                     inChannel,
                     framecount,
                     0);
    if(!record){
        LOGE("fatal:Not able to open audiorecord");
        return UNKNOWN_ERROR;
    }

    status_t res = record->initCheck();
    if (res == NO_ERROR)
        res = record->start();
    else{
        LOGE("fatal:record init check failure");
        return UNKNOWN_ERROR;
    }


    while (!mDone) {

        mFreeQLock.lock();
        if(mFreeQ.empty()){
            mFreeQLock.unlock();
            LOGV("FreeQ empty");
            sem_wait(&mReaderThreadWakeupsem);
            LOGV("FreeQ filled up");
            continue;
        }
        List<audioBufferstruct>::iterator it = mFreeQ.begin();
        audioBufferstruct buff ( it->audioBuffer,it->bufferlen);
        mFreeQ.erase(it);
        mFreeQLock.unlock();

        buff.bufferlen = record->read(buff.audioBuffer, mBufferSize);
        LOGV("read %d bytes", buff.bufferlen);
        if (buff.bufferlen <= 0){
            LOGE("error in reading from audiorecord..bailing out.");
            this ->notify(MEDIA_RECORDER_EVENT_ERROR, MEDIA_RECORDER_ERROR_UNKNOWN,
                           ERROR_MALFORMED);
            err = INVALID_OPERATION ;
            break;
        }

        mDataQLock.lock();
        if(mDataQ.empty()){
            LOGV("waking up reader");
            sem_post(&mWriterThreadWakeupsem);
        }
        mDataQ.push_back(buff);
        mDataQLock.unlock();
    }
    record->stop();
    delete record;

    return err;
}


status_t FMA2DPWriter::writerthread(){
    status_t err = OK;
    int framecount =(16*mBufferSize)/sizeof(int16_t);
    //sizeof(int16_t) is frame size for PCM stream
    int outChannel = (mAudioChannels== 2) ? AUDIO_CHANNEL_OUT_STEREO :
        AUDIO_CHANNEL_OUT_MONO;

    prctl(PR_SET_NAME, (unsigned long)"FMA2DPWriterThread", 0, 0, 0);

    AudioTrack *audioTrack= new AudioTrack(
                AUDIO_STREAM_FM,
                mSampleRate,
                mAudioFormat,
                outChannel,
                framecount);

    if(!audioTrack){
        LOGE("fatal:Not able to open audiotrack");
        return UNKNOWN_ERROR;
    }
    status_t res = audioTrack->initCheck();
    if (res == NO_ERROR) {
        audioTrack->setVolume(1, 1);
        audioTrack->start();
    }
    else{
        LOGE("fatal:audiotrack init check failure");
        return UNKNOWN_ERROR;
    }


    while (!mDone) {

        mDataQLock.lock();
        if(mDataQ.empty()){
            mDataQLock.unlock();
            LOGV("dataQ empty");
            sem_wait(&mWriterThreadWakeupsem);
            LOGV("dataQ filled up");
            continue;
        }
        List<audioBufferstruct>::iterator it = mDataQ.begin();
        audioBufferstruct buff ( it->audioBuffer,it->bufferlen);
        mDataQ.erase(it);
        mDataQLock.unlock();

       size_t retval = audioTrack->write(buff.audioBuffer, buff.bufferlen);
       if(!retval){
            LOGE("audio track write failure..bailing out");
            this ->notify(MEDIA_RECORDER_EVENT_ERROR, MEDIA_RECORDER_ERROR_UNKNOWN,
                           ERROR_MALFORMED);
            err = INVALID_OPERATION ;
            break;
        }
        LOGV("wrote %d bytes", buff.bufferlen);

        mFreeQLock.lock();
        if(mFreeQ.empty()){
            LOGV("WAKING UP READER");
            sem_post(&mReaderThreadWakeupsem);
        }
        mFreeQ.push_back(buff);
        mFreeQLock.unlock();
    }
    audioTrack->stop();
    delete audioTrack;

    return err;
}

bool FMA2DPWriter::reachedEOS() {
// API not need for FMA2DPWriter
    return OK;
}


}  // namespace android
