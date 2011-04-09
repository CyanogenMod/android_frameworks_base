/*
 * Copyright (C) 2007 The Android Open Source Project
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
#define LOG_TAG "SoundPool"
#include <utils/Log.h>

//#define USE_SHARED_MEM_BUFFER

// XXX needed for timing latency
#include <utils/Timers.h>

#include <sys/resource.h>
#include <media/AudioTrack.h>
#include <media/mediaplayer.h>

#include "SoundPool.h"
#include "SoundPoolThread.h"

namespace android
{

int kDefaultBufferCount = 4;
uint32_t kMaxSampleRate = 48000;
uint32_t kDefaultSampleRate = 44100;
uint32_t kDefaultFrameCount = 1200;

SoundPool::SoundPool(int maxChannels, int streamType, int srcQuality)
{
    LOGV("SoundPool constructor: maxChannels=%d, streamType=%d, srcQuality=%d",
            maxChannels, streamType, srcQuality);

    // check limits
    mMaxChannels = maxChannels;
    if (mMaxChannels < 1) {
        mMaxChannels = 1;
    }
    else if (mMaxChannels > 32) {
        mMaxChannels = 32;
    }
    LOGW_IF(maxChannels != mMaxChannels, "App requested %d channels", maxChannels);

    mQuit = false;
    mDecodeThread = 0;
    mStreamType = streamType;
    mSrcQuality = srcQuality;
    mAllocated = 0;
    mNextSampleID = 0;
    mNextChannelID = 0;

    mCallback = 0;
    mUserData = 0;

    mChannelPool = new SoundChannel[mMaxChannels];
    for (int i = 0; i < mMaxChannels; ++i) {
        mChannelPool[i].init(this);
        mChannels.push_back(&mChannelPool[i]);
    }

    // start decode thread
    startThreads();
}

SoundPool::~SoundPool()
{
    LOGV("SoundPool destructor");
    mDecodeThread->quit();
    quit();

    Mutex::Autolock lock(&mLock);
    mChannels.clear();
    if (mChannelPool)
        delete [] mChannelPool;

    // clean up samples
    LOGV("clear samples");
    mSamples.clear();

    if (mDecodeThread)
        delete mDecodeThread;
}

void SoundPool::addToRestartList(SoundChannel* channel)
{
    Mutex::Autolock lock(&mRestartLock);
    mRestart.push_back(channel);
    mCondition.signal();
}

int SoundPool::beginThread(void* arg)
{
    SoundPool* p = (SoundPool*)arg;
    return p->run();
}

int SoundPool::run()
{
    mRestartLock.lock();
    while (!mQuit) {
        mCondition.wait(mRestartLock);
        LOGV("awake");
        if (mQuit) break;

        while (!mRestart.empty()) {
            SoundChannel* channel;
            LOGV("Getting channel from list");
            List<SoundChannel*>::iterator iter = mRestart.begin();
            channel = *iter;
            mRestart.erase(iter);
            if (channel) channel->nextEvent();
            if (mQuit) break;
        }
    }

    mRestart.clear();
    mCondition.signal();
    mRestartLock.unlock();
    LOGV("goodbye");
    return 0;
}

void SoundPool::quit()
{
    mRestartLock.lock();
    mQuit = true;
    mCondition.signal();
    mCondition.wait(mRestartLock);
    LOGV("return from quit");
    mRestartLock.unlock();
}

bool SoundPool::startThreads()
{
    createThreadEtc(beginThread, this, "SoundPool");
    if (mDecodeThread == NULL)
        mDecodeThread = new SoundPoolThread(this);
    return mDecodeThread != NULL;
}

SoundChannel* SoundPool::findChannel(int channelID)
{
    for (int i = 0; i < mMaxChannels; ++i) {
        if (mChannelPool[i].channelID() == channelID) {
            return &mChannelPool[i];
        }
    }
    return NULL;
}

SoundChannel* SoundPool::findNextChannel(int channelID)
{
    for (int i = 0; i < mMaxChannels; ++i) {
        if (mChannelPool[i].nextChannelID() == channelID) {
            return &mChannelPool[i];
        }
    }
    return NULL;
}

int SoundPool::load(const char* path, int priority)
{
    LOGV("load: path=%s, priority=%d", path, priority);
    Mutex::Autolock lock(&mLock);
    sp<Sample> sample = new Sample(++mNextSampleID, path);
    mSamples.add(sample->sampleID(), sample);
    doLoad(sample);
    return sample->sampleID();
}

int SoundPool::load(int fd, int64_t offset, int64_t length, int priority)
{
    LOGV("load: fd=%d, offset=%lld, length=%lld, priority=%d",
            fd, offset, length, priority);
    Mutex::Autolock lock(&mLock);
    sp<Sample> sample = new Sample(++mNextSampleID, fd, offset, length);
    mSamples.add(sample->sampleID(), sample);
    doLoad(sample);
    return sample->sampleID();
}

void SoundPool::doLoad(sp<Sample>& sample)
{
    LOGV("doLoad: loading sample sampleID=%d", sample->sampleID());
    sample->startLoad();
    mDecodeThread->loadSample(sample->sampleID());
}

bool SoundPool::unload(int sampleID)
{
    LOGV("unload: sampleID=%d", sampleID);
    Mutex::Autolock lock(&mLock);
    return mSamples.removeItem(sampleID);
}

int SoundPool::play(int sampleID, float leftVolume, float rightVolume,
        int priority, int loop, float rate)
{
    LOGV("sampleID=%d, leftVolume=%f, rightVolume=%f, priority=%d, loop=%d, rate=%f",
            sampleID, leftVolume, rightVolume, priority, loop, rate);
    sp<Sample> sample;
    SoundChannel* channel;
    int channelID;

    // scope for lock
    {
        Mutex::Autolock lock(&mLock);

        // is sample ready?
        sample = findSample(sampleID);
        if ((sample == 0) || (sample->state() != Sample::READY)) {
            LOGW("  sample %d not READY", sampleID);
            return 0;
        }

        dump();

        // allocate a channel
        channel = allocateChannel(priority);

        // no channel allocated - return 0
        if (!channel) {
            LOGV("No channel allocated");
            return 0;
        }

        channelID = ++mNextChannelID;
    }

    LOGV("channel state = %d", channel->state());
    channel->play(sample, channelID, leftVolume, rightVolume, priority, loop, rate);
    return channelID;
}

SoundChannel* SoundPool::allocateChannel(int priority)
{
    List<SoundChannel*>::iterator iter;
    SoundChannel* channel = NULL;

    // allocate a channel
    if (!mChannels.empty()) {
        iter = mChannels.begin();
        if (priority >= (*iter)->priority()) {
            channel = *iter;
            mChannels.erase(iter);
            LOGV("Allocated active channel");
        }
    }

    // update priority and put it back in the list
    if (channel) {
        channel->setPriority(priority);
        for (iter = mChannels.begin(); iter != mChannels.end(); ++iter) {
            if (priority < (*iter)->priority()) {
                break;
            }
        }
        mChannels.insert(iter, channel);
    }
    return channel;
}

// move a channel from its current position to the front of the list
void SoundPool::moveToFront(SoundChannel* channel)
{
    for (List<SoundChannel*>::iterator iter = mChannels.begin(); iter != mChannels.end(); ++iter) {
        if (*iter == channel) {
            mChannels.erase(iter);
            mChannels.push_front(channel);
            break;
        }
    }
}

void SoundPool::pause(int channelID)
{
    LOGV("pause(%d)", channelID);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->pause();
    }
}

void SoundPool::autoPause()
{
    LOGV("autoPause()");
    Mutex::Autolock lock(&mLock);
    for (int i = 0; i < mMaxChannels; ++i) {
        SoundChannel* channel = &mChannelPool[i];
        channel->autoPause();
    }
}

void SoundPool::resume(int channelID)
{
    LOGV("resume(%d)", channelID);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->resume();
    }
}

void SoundPool::autoResume()
{
    LOGV("autoResume()");
    Mutex::Autolock lock(&mLock);
    for (int i = 0; i < mMaxChannels; ++i) {
        SoundChannel* channel = &mChannelPool[i];
        channel->autoResume();
    }
}

void SoundPool::stop(int channelID)
{
    LOGV("stop(%d)", channelID);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->stop();
    } else {
        channel = findNextChannel(channelID);
        if (channel)
            channel->clearNextEvent();
    }
}

void SoundPool::setVolume(int channelID, float leftVolume, float rightVolume)
{
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->setVolume(leftVolume, rightVolume);
    }
}

void SoundPool::setPriority(int channelID, int priority)
{
    LOGV("setPriority(%d, %d)", channelID, priority);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->setPriority(priority);
    }
}

void SoundPool::setLoop(int channelID, int loop)
{
    LOGV("setLoop(%d, %d)", channelID, loop);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->setLoop(loop);
    }
}

void SoundPool::setRate(int channelID, float rate)
{
    LOGV("setRate(%d, %f)", channelID, rate);
    Mutex::Autolock lock(&mLock);
    SoundChannel* channel = findChannel(channelID);
    if (channel) {
        channel->setRate(rate);
    }
}

// call with lock held
void SoundPool::done(SoundChannel* channel)
{
    LOGV("done(%d)", channel->channelID());

    // if "stolen", play next event
    if (channel->nextChannelID() != 0) {
        LOGV("add to restart list");
        addToRestartList(channel);
    }

    // return to idle state
    else {
        LOGV("move to front");
        moveToFront(channel);
    }
}

void SoundPool::setCallback(SoundPoolCallback* callback, void* user)
{
    Mutex::Autolock lock(&mCallbackLock);
    mCallback = callback;
    mUserData = user;
}

void SoundPool::notify(SoundPoolEvent event)
{
    Mutex::Autolock lock(&mCallbackLock);
    if (mCallback != NULL) {
        mCallback(event, this, mUserData);
    }
}

void SoundPool::dump()
{
    for (int i = 0; i < mMaxChannels; ++i) {
        mChannelPool[i].dump();
    }
}


Sample::Sample(int sampleID, const char* url)
{
    init();
    mSampleID = sampleID;
    mUrl = strdup(url);
    LOGV("create sampleID=%d, url=%s", mSampleID, mUrl);
}

Sample::Sample(int sampleID, int fd, int64_t offset, int64_t length)
{
    init();
    mSampleID = sampleID;
    mFd = dup(fd);
    mOffset = offset;
    mLength = length;
    LOGV("create sampleID=%d, fd=%d, offset=%lld, length=%lld", mSampleID, mFd, mLength, mOffset);
}

void Sample::init()
{
    mData = 0;
    mSize = 0;
    mRefCount = 0;
    mSampleID = 0;
    mState = UNLOADED;
    mFd = -1;
    mOffset = 0;
    mLength = 0;
    mUrl = 0;
}

Sample::~Sample()
{
    LOGV("Sample::destructor sampleID=%d, fd=%d", mSampleID, mFd);
    if (mFd > 0) {
        LOGV("close(%d)", mFd);
        ::close(mFd);
    }
    mData.clear();
    delete mUrl;
}

status_t Sample::doLoad()
{
    uint32_t sampleRate;
    int numChannels;
    int format;
    sp<IMemory> p;
    LOGV("Start decode");
    if (mUrl) {
        p = MediaPlayer::decode(mUrl, &sampleRate, &numChannels, &format);
    } else {
        p = MediaPlayer::decode(mFd, mOffset, mLength, &sampleRate, &numChannels, &format);
        LOGV("close(%d)", mFd);
        ::close(mFd);
        mFd = -1;
    }
    if (p == 0) {
        LOGE("Unable to load sample: %s", mUrl);
        return -1;
    }
    LOGV("pointer = %p, size = %u, sampleRate = %u, numChannels = %d",
            p->pointer(), p->size(), sampleRate, numChannels);

    if (sampleRate > kMaxSampleRate) {
       LOGE("Sample rate (%u) out of range", sampleRate);
       return - 1;
    }

    if ((numChannels < 1) || (numChannels > 2)) {
        LOGE("Sample channel count (%d) out of range", numChannels);
        return - 1;
    }

    //_dumpBuffer(p->pointer(), p->size());
    uint8_t* q = static_cast<uint8_t*>(p->pointer()) + p->size() - 10;
    //_dumpBuffer(q, 10, 10, false);

    mData = p;
    mSize = p->size();
    mSampleRate = sampleRate;
    mNumChannels = numChannels;
    mFormat = format;
    mState = READY;
    return 0;
}


void SoundChannel::init(SoundPool* soundPool)
{
    mSoundPool = soundPool;
}

void SoundChannel::play(const sp<Sample>& sample, int nextChannelID, float leftVolume,
        float rightVolume, int priority, int loop, float rate)
{
    AudioTrack* oldTrack;

    LOGV("play %p: sampleID=%d, channelID=%d, leftVolume=%f, rightVolume=%f, priority=%d, loop=%d, rate=%f",
            this, sample->sampleID(), nextChannelID, leftVolume, rightVolume, priority, loop, rate);

    // if not idle, this voice is being stolen
    if (mState != IDLE) {
        LOGV("channel %d stolen - event queued for channel %d", channelID(), nextChannelID);
        mNextEvent.set(sample, nextChannelID, leftVolume, rightVolume, priority, loop, rate);
        stop();
        return;
    }

    // initialize track
    int afFrameCount;
    int afSampleRate;
    int streamType = mSoundPool->streamType();
    if (AudioSystem::getOutputFrameCount(&afFrameCount, streamType) != NO_ERROR) {
        afFrameCount = kDefaultFrameCount;
    }
    if (AudioSystem::getOutputSamplingRate(&afSampleRate, streamType) != NO_ERROR) {
        afSampleRate = kDefaultSampleRate;
    }
    int numChannels = sample->numChannels();
    uint32_t sampleRate = uint32_t(float(sample->sampleRate()) * rate + 0.5);
    uint32_t totalFrames = (kDefaultBufferCount * afFrameCount * sampleRate) / afSampleRate;
    uint32_t bufferFrames = (totalFrames + (kDefaultBufferCount - 1)) / kDefaultBufferCount;
    uint32_t frameCount = 0;

    if (loop) {
        frameCount = sample->size()/numChannels/((sample->format() == AudioSystem::PCM_16_BIT) ? sizeof(int16_t) : sizeof(uint8_t));
    }

#ifndef USE_SHARED_MEM_BUFFER
    // Ensure minimum audio buffer size in case of short looped sample
    if(frameCount < totalFrames) {
        frameCount = totalFrames;
    }
#endif

    AudioTrack* newTrack;

    // mToggle toggles each time a track is started on a given channel.
    // The toggle is concatenated with the SoundChannel address and passed to AudioTrack
    // as callback user data. This enables the detection of callbacks received from the old
    // audio track while the new one is being started and avoids processing them with
    // wrong audio audio buffer size  (mAudioBufferSize)
    unsigned long toggle = mToggle ^ 1;
    void *userData = (void *)((unsigned long)this | toggle);
    uint32_t channels = (numChannels == 2) ? AudioSystem::CHANNEL_OUT_STEREO : AudioSystem::CHANNEL_OUT_MONO;

#ifdef USE_SHARED_MEM_BUFFER
    newTrack = new AudioTrack(streamType, sampleRate, sample->format(),
            channels, sample->getIMemory(), 0, callback, userData);
#else
    newTrack = new AudioTrack(streamType, sampleRate, sample->format(),
            channels, frameCount, 0, callback, userData, bufferFrames);
#endif
    if (newTrack->initCheck() != NO_ERROR) {
        LOGE("Error creating AudioTrack");
        delete newTrack;
        return;
    }
    LOGV("setVolume %p", newTrack);
    newTrack->setVolume(leftVolume, rightVolume);
    newTrack->setLoop(0, frameCount, loop);

    {
        Mutex::Autolock lock(&mLock);
        // From now on, AudioTrack callbacks recevieved with previous toggle value will be ignored.
        mToggle = toggle;
        oldTrack = mAudioTrack;
        mAudioTrack = newTrack;
        mPos = 0;
        mSample = sample;
        mChannelID = nextChannelID;
        mPriority = priority;
        mLoop = loop;
        mLeftVolume = leftVolume;
        mRightVolume = rightVolume;
        mNumChannels = numChannels;
        mRate = rate;
        clearNextEvent();
        mState = PLAYING;
        mAudioTrack->start();
        mAudioBufferSize = newTrack->frameCount()*newTrack->frameSize();
    }

    LOGV("delete oldTrack %p", oldTrack);
    delete oldTrack;
}

void SoundChannel::nextEvent()
{
    sp<Sample> sample;
    int nextChannelID;
    float leftVolume;
    float rightVolume;
    int priority;
    int loop;
    float rate;

    // check for valid event
    {
        Mutex::Autolock lock(&mLock);
        nextChannelID = mNextEvent.channelID();
        if (nextChannelID  == 0) {
            LOGV("stolen channel has no event");
            return;
        }

        sample = mNextEvent.sample();
        leftVolume = mNextEvent.leftVolume();
        rightVolume = mNextEvent.rightVolume();
        priority = mNextEvent.priority();
        loop = mNextEvent.loop();
        rate = mNextEvent.rate();
    }

    LOGV("Starting stolen channel %d -> %d", channelID(), nextChannelID);
    play(sample, nextChannelID, leftVolume, rightVolume, priority, loop, rate);
}

void SoundChannel::callback(int event, void* user, void *info)
{
    unsigned long toggle = (unsigned long)user & 1;
    SoundChannel* channel = static_cast<SoundChannel*>((void *)((unsigned long)user & ~1));

    if (channel->mToggle != toggle) {
        LOGV("callback with wrong toggle");
        return;
    }
    channel->process(event, info);
}

void SoundChannel::process(int event, void *info)
{
    //LOGV("process(%d)", mChannelID);
    sp<Sample> sample = mSample;

//    LOGV("SoundChannel::process event %d", event);

    if (event == AudioTrack::EVENT_MORE_DATA) {
       AudioTrack::Buffer* b = static_cast<AudioTrack::Buffer *>(info);

        // check for stop state
        if (b->size == 0) return;

        if (sample != 0) {
            // fill buffer
            uint8_t* q = (uint8_t*) b->i8;
            size_t count = 0;

            if (mPos < (int)sample->size()) {
                uint8_t* p = sample->data() + mPos;
                count = sample->size() - mPos;
                if (count > b->size) {
                    count = b->size;
                }
                memcpy(q, p, count);
                LOGV("fill: q=%p, p=%p, mPos=%u, b->size=%u, count=%d", q, p, mPos, b->size, count);
            } else if (mPos < mAudioBufferSize) {
                count = mAudioBufferSize - mPos;
                if (count > b->size) {
                    count = b->size;
                }
                memset(q, 0, count);
                LOGV("fill extra: q=%p, mPos=%u, b->size=%u, count=%d", q, mPos, b->size, count);
            }

            mPos += count;
            b->size = count;
            //LOGV("buffer=%p, [0]=%d", b->i16, b->i16[0]);
        }
    } else if (event == AudioTrack::EVENT_UNDERRUN) {
        LOGV("stopping track");
        stop();
    } else if (event == AudioTrack::EVENT_LOOP_END) {
        LOGV("End loop: %d", *(int *)info);
    }
}


// call with lock held
void SoundChannel::stop_l()
{
    if (mState != IDLE) {
        setVolume_l(0, 0);
        LOGV("stop");
        mAudioTrack->stop();
        mSample.clear();
        mState = IDLE;
        mPriority = IDLE_PRIORITY;
    }
}

void SoundChannel::stop()
{
    {
        Mutex::Autolock lock(&mLock);
        stop_l();
    }
    mSoundPool->done(this);
}

//FIXME: Pause is a little broken right now
void SoundChannel::pause()
{
    Mutex::Autolock lock(&mLock);
    if (mState == PLAYING) {
        LOGV("pause track");
        mState = PAUSED;
        mAudioTrack->pause();
    }
}

void SoundChannel::autoPause()
{
    Mutex::Autolock lock(&mLock);
    if (mState == PLAYING) {
        LOGV("pause track");
        mState = PAUSED;
        mAutoPaused = true;
        mAudioTrack->pause();
    }
}

void SoundChannel::resume()
{
    Mutex::Autolock lock(&mLock);
    if (mState == PAUSED) {
        LOGV("resume track");
        mState = PLAYING;
        mAutoPaused = false;
        mAudioTrack->start();
    }
}

void SoundChannel::autoResume()
{
    Mutex::Autolock lock(&mLock);
    if (mAutoPaused && (mState == PAUSED)) {
        LOGV("resume track");
        mState = PLAYING;
        mAutoPaused = false;
        mAudioTrack->start();
    }
}

void SoundChannel::setRate(float rate)
{
    Mutex::Autolock lock(&mLock);
    if (mAudioTrack != 0 && mSample.get() != 0) {
        uint32_t sampleRate = uint32_t(float(mSample->sampleRate()) * rate + 0.5);
        mAudioTrack->setSampleRate(sampleRate);
        mRate = rate;
    }
}

// call with lock held
void SoundChannel::setVolume_l(float leftVolume, float rightVolume)
{
    mLeftVolume = leftVolume;
    mRightVolume = rightVolume;
    if (mAudioTrack != 0) mAudioTrack->setVolume(leftVolume, rightVolume);
}

void SoundChannel::setVolume(float leftVolume, float rightVolume)
{
    Mutex::Autolock lock(&mLock);
    setVolume_l(leftVolume, rightVolume);
}

void SoundChannel::setLoop(int loop)
{
    Mutex::Autolock lock(&mLock);
    if (mAudioTrack != 0 && mSample.get() != 0) {
        mAudioTrack->setLoop(0, mSample->size()/mNumChannels/((mSample->format() == AudioSystem::PCM_16_BIT) ? sizeof(int16_t) : sizeof(uint8_t)), loop);
        mLoop = loop;
    }
}

SoundChannel::~SoundChannel()
{
    LOGV("SoundChannel destructor");
    if (mAudioTrack) {
        LOGV("stop track");
        mAudioTrack->stop();
        delete mAudioTrack;
    }
    clearNextEvent();
    mSample.clear();
}

void SoundChannel::dump()
{
    LOGV("mState = %d mChannelID=%d, mNumChannels=%d, mPos = %d, mPriority=%d, mLoop=%d",
            mState, mChannelID, mNumChannels, mPos, mPriority, mLoop);
}

void SoundEvent::set(const sp<Sample>& sample, int channelID, float leftVolume,
            float rightVolume, int priority, int loop, float rate)
{
    mSample =sample;
    mChannelID = channelID;
    mLeftVolume = leftVolume;
    mRightVolume = rightVolume;
    mPriority = priority;
    mLoop = loop;
    mRate =rate;
}

} // end namespace android
