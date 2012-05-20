/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2009-2012, Code Aurora Forum. All rights reserved.
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

#ifndef LPA_PLAYER_H_

#define LPA_PLAYER_H_

#include "AudioPlayer.h"
#include <media/IAudioFlinger.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <utils/Vector.h>
#include <fcntl.h>
#include <pthread.h>
#include <binder/IServiceManager.h>
#include <linux/unistd.h>
#include <linux/msm_audio.h>
#include <linux/ion.h>
#include <include/TimedEventQueue.h>
#include <binder/BinderService.h>
#include <binder/MemoryDealer.h>
#include <powermanager/IPowerManager.h>

// Pause timeout = 3sec
#define LPA_PAUSE_TIMEOUT_USEC 3000000

namespace android {

class LPAPlayer : public AudioPlayer  {
public:
    enum {
        REACHED_EOS,
        SEEK_COMPLETE
    };

    LPAPlayer(const sp<MediaPlayerBase::AudioSink> &audioSink, bool &initCheck,
                AwesomePlayer *audioObserver = NULL);

    virtual ~LPAPlayer();

    // Caller retains ownership of "source".
    virtual void setSource(const sp<MediaSource> &source);

    // Return time in us.
    virtual int64_t getRealTimeUs();

    virtual status_t start(bool sourceAlreadyStarted = false);

    virtual void pause(bool playPendingSamples = false);
    virtual void resume();

    // Returns the timestamp of the last buffer played (in us).
    virtual int64_t getMediaTimeUs();

    // Returns true iff a mapping is established, i.e. the LPAPlayer
    // has played at least one frame of audio.
    virtual bool getMediaTimeMapping(int64_t *realtime_us, int64_t *mediatime_us);

    virtual status_t seekTo(int64_t time_us);

    virtual bool isSeeking();
    virtual bool reachedEOS(status_t *finalStatus);


    void* handle;
    static int objectsAlive;
private:

    int afd;
    int efd;
    int ionfd;
    int sessionId;
    uint32_t bytesToWrite;
    bool isPaused;
    bool mSeeked;
    bool a2dpDisconnectPause;
    bool a2dpThreadStarted;
    volatile bool asyncReset;
    bool eventThreadCreated;
    int mBuffSize;
    int mBuffNumber;

    void clearPowerManager();

    class PMDeathRecipient : public IBinder::DeathRecipient {
        public:
                        PMDeathRecipient(void *obj){parentClass = (LPAPlayer *)obj;}
            virtual     ~PMDeathRecipient() {}

            // IBinder::DeathRecipient
            virtual     void        binderDied(const wp<IBinder>& who);

        private:
                        LPAPlayer *parentClass;
                        PMDeathRecipient(const PMDeathRecipient&);
                        PMDeathRecipient& operator = (const PMDeathRecipient&);

        friend class LPAPlayer;
    };

    friend class PMDeathRecipient;

    void        acquireWakeLock();
    void        releaseWakeLock();

    sp<IPowerManager>       mPowerManager;
    sp<IBinder>             mWakeLockToken;
    sp<PMDeathRecipient>    mDeathRecipient;

    //Structure to hold ion buffer information
    class BuffersAllocated {
    /* overload BuffersAllocated constructor to support both ion and pmem memory allocation */
    public:
        BuffersAllocated(void *buf1, void *buf2, int32_t nSize, int32_t fd) :
        localBuf(buf1), memBuf(buf2), memBufsize(nSize), memFd(fd)
        {}
        BuffersAllocated(void *buf1, void *buf2, int32_t nSize, int32_t share_fd, struct ion_handle *handle) :
        ion_handle(handle), localBuf(buf1), memBuf(buf2), memBufsize(nSize), memFd(share_fd)
        {}
        struct ion_handle *ion_handle;
        void* localBuf;
        void* memBuf;
        int32_t memBufsize;
        int32_t memFd;
        uint32_t bytesToWrite;
    };
    void audio_register_memory();
    void memBufferDeAlloc();
    void *memBufferAlloc(int32_t nSize, int32_t *mem_fd);

    List<BuffersAllocated> memBuffersRequestQueue;
    List<BuffersAllocated> memBuffersResponseQueue;
    List<BuffersAllocated> bufPool;
    List<BuffersAllocated> effectsQueue;


    //Declare all the threads
    pthread_t eventThread;
    pthread_t decoderThread;
    pthread_t A2DPThread;
    pthread_t EffectsThread;
    pthread_t A2DPNotificationThread;

    //Kill Thread boolean
    bool killDecoderThread;
    bool killEventThread;
    bool killA2DPThread;
    bool killEffectsThread;
    bool killA2DPNotificationThread;

    //Thread alive boolean
    bool decoderThreadAlive;
    bool eventThreadAlive;
    bool a2dpThreadAlive;
    bool effectsThreadAlive;
    bool a2dpNotificationThreadAlive;

    //Declare the condition Variables and Mutex
    pthread_mutex_t mem_request_mutex;
    pthread_mutex_t mem_response_mutex;
    pthread_mutex_t decoder_mutex;
    pthread_mutex_t event_mutex;
    pthread_mutex_t a2dp_mutex;
    pthread_mutex_t effect_mutex;
    pthread_mutex_t apply_effect_mutex;
    pthread_mutex_t a2dp_notification_mutex;
    pthread_mutex_t pause_mutex;

    pthread_cond_t event_cv;
    pthread_cond_t decoder_cv;
    pthread_cond_t a2dp_cv;
    pthread_cond_t effect_cv;
    pthread_cond_t event_thread_cv;
    pthread_cond_t a2dp_notification_cv;
    pthread_cond_t pause_cv;

    // make sure Decoder thread has exited
    void requestAndWaitForDecoderThreadExit();

    // make sure the event thread also exited
    void requestAndWaitForEventThreadExit();

    // make sure the A2dp thread also exited
    void requestAndWaitForA2DPThreadExit();

    // make sure the Effects thread also exited
    void requestAndWaitForEffectsThreadExit();

    // make sure the Effects thread also exited
    void requestAndWaitForA2DPNotificationThreadExit();

    static void *eventThreadWrapper(void *me);
    void eventThreadEntry();
    static void *decoderThreadWrapper(void *me);
    void decoderThreadEntry();
    static void *A2DPThreadWrapper(void *me);
    void A2DPThreadEntry();
    static void *EffectsThreadWrapper(void *me);
    void EffectsThreadEntry();
    static void *A2DPNotificationThreadWrapper(void *me);
    void A2DPNotificationThreadEntry();

    void createThreads();

    volatile bool bIsA2DPEnabled, bIsAudioRouted, bEffectConfigChanged;

    //Structure to recieve the BT notification from the flinger.
    class AudioFlingerLPAdecodeClient: public IBinder::DeathRecipient, public BnAudioFlingerClient {
    public:
        AudioFlingerLPAdecodeClient(void *obj);

        LPAPlayer *pBaseClass;
        // DeathRecipient
        virtual void binderDied(const wp<IBinder>& who);

        // IAudioFlingerClient

        // indicate a change in the configuration of an output or input: keeps the cached
        // values for output/input parameters upto date in client process
        virtual void ioConfigChanged(int event, int ioHandle, void *param2);

        friend class LPAPlayer;
    };

    sp<IAudioFlinger> mAudioFlinger;

    // helper function to obtain AudioFlinger service handle
    void getAudioFlinger();

    void handleA2DPSwitch();

    sp<AudioFlingerLPAdecodeClient> AudioFlingerClient;
    friend class AudioFlingerLPAdecodeClient;
    Mutex AudioFlingerLock;
    bool mSourceEmpty;
    bool mAudioSinkOpen;

    sp<MediaSource> mSource;

    MediaBuffer *mInputBuffer;
    int32_t numChannels;
    int mSampleRate;
    int64_t mLatencyUs;
    size_t mFrameSize;

    Mutex pmLock;
    Mutex mLock;
    Mutex mSeekLock;
    Mutex a2dpSwitchLock;
    Mutex resumeLock;
    int64_t mNumFramesPlayed;

    int64_t mPositionTimeMediaUs;
    int64_t mPositionTimeRealUs;

    bool mSeeking;
    bool mInternalSeeking;
    bool mReachedEOS;
    status_t mFinalStatus;
    int64_t mSeekTimeUs;
    int64_t mPauseTime;
    int64_t mNumA2DPBytesPlayed;
    int64_t timePlayed;
    int64_t timeStarted;

    bool mStarted;

    bool mIsFirstBuffer;
    status_t mFirstBufferResult;
    MediaBuffer *mFirstBuffer;
    TimedEventQueue mQueue;
    bool            mQueueStarted;
    sp<TimedEventQueue::Event>  mPauseEvent;
    bool                        mPauseEventPending;
    bool                        mPlaybackSuspended;
    bool                        mIsDriverStarted;
    bool                        mIsAudioRouted;

    sp<MediaPlayerBase::AudioSink> mAudioSink;
    AwesomePlayer *mObserver;

    enum A2DPState {
        A2DP_ENABLED,
        A2DP_DISABLED,
        A2DP_CONNECT,
        A2DP_DISCONNECT
    };

    size_t fillBuffer(void *data, size_t size);

    int64_t getRealTimeUsLocked();
    int64_t getTimeStamp(A2DPState state);

    void reset();

    void onPauseTimeOut();


    LPAPlayer(const LPAPlayer &);
    LPAPlayer &operator=(const LPAPlayer &);
};

struct TimedEvent : public TimedEventQueue::Event {
    TimedEvent(LPAPlayer *player,
               void (LPAPlayer::*method)())
        : mPlayer(player),
          mMethod(method) {
    }

protected:
    virtual ~TimedEvent() {}

    virtual void fire(TimedEventQueue *queue, int64_t /* now_us */) {
        (mPlayer->*mMethod)();
    }

private:
    LPAPlayer *mPlayer;
    void (LPAPlayer::*mMethod)();

    TimedEvent(const TimedEvent &);
    TimedEvent &operator=(const TimedEvent &);
};

}  // namespace android

#endif  // LPA_PLAYER_H_

