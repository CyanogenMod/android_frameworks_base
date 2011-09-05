/* //device/include/server/AudioFlinger/AudioFlinger.h
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef ANDROID_AUDIO_FLINGER_H
#define ANDROID_AUDIO_FLINGER_H

#include <stdint.h>
#include <sys/types.h>
#include <limits.h>

#include <media/IAudioFlinger.h>
#include <media/IAudioFlingerClient.h>
#include <media/IAudioTrack.h>
#include <media/IAudioRecord.h>
#include <media/AudioTrack.h>

#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/threads.h>
#include <utils/SortedVector.h>
#include <utils/Vector.h>

#include <binder/BinderService.h>
#include <binder/MemoryDealer.h>

#include <hardware_legacy/AudioHardwareInterface.h>

#include "AudioBufferProvider.h"

namespace android {

class audio_track_cblk_t;
class effect_param_cblk_t;
class AudioMixer;
class AudioBuffer;
class AudioResampler;


// ----------------------------------------------------------------------------

#define LIKELY( exp )       (__builtin_expect( (exp) != 0, true  ))
#define UNLIKELY( exp )     (__builtin_expect( (exp) != 0, false ))


// ----------------------------------------------------------------------------

static const nsecs_t kStandbyTimeInNsecs = seconds(3);

class AudioFlinger :
    public BinderService<AudioFlinger>,
    public BnAudioFlinger
{
    friend class BinderService<AudioFlinger>;
public:
    static char const* getServiceName() { return "media.audio_flinger"; }

    virtual     status_t    dump(int fd, const Vector<String16>& args);

    // IAudioFlinger interface
    virtual sp<IAudioTrack> createTrack(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int frameCount,
                                uint32_t flags,
                                const sp<IMemory>& sharedBuffer,
                                int output,
                                int *sessionId,
                                status_t *status);

    virtual     uint32_t    sampleRate(int output) const;
    virtual     int         channelCount(int output) const;
    virtual     int         format(int output) const;
    virtual     size_t      frameCount(int output) const;
    virtual     uint32_t    latency(int output) const;

    virtual     status_t    setMasterVolume(float value);
    virtual     status_t    setMasterMute(bool muted);

    virtual     float       masterVolume() const;
    virtual     bool        masterMute() const;

    virtual     status_t    setStreamVolume(int stream, float value, int output);
    virtual     status_t    setStreamMute(int stream, bool muted);

    virtual     float       streamVolume(int stream, int output) const;
    virtual     bool        streamMute(int stream) const;

    virtual     status_t    setMode(int mode);

    virtual     status_t    setMicMute(bool state);
    virtual     bool        getMicMute() const;

    virtual     bool        isStreamActive(int stream) const;
#ifdef OMAP_ENHANCEMENT
   virtual     status_t	setFMRxActive(bool state);
#endif
    virtual     status_t    setParameters(int ioHandle, const String8& keyValuePairs);
    virtual     String8     getParameters(int ioHandle, const String8& keys);

    virtual     void        registerClient(const sp<IAudioFlingerClient>& client);

    virtual     size_t      getInputBufferSize(uint32_t sampleRate, int format, int channelCount);
    virtual     unsigned int  getInputFramesLost(int ioHandle);

    virtual int openOutput(uint32_t *pDevices,
                                    uint32_t *pSamplingRate,
                                    uint32_t *pFormat,
                                    uint32_t *pChannels,
                                    uint32_t *pLatencyMs,
                                    uint32_t flags);

    virtual int openDuplicateOutput(int output1, int output2);

    virtual status_t closeOutput(int output);

    virtual status_t suspendOutput(int output);

    virtual status_t restoreOutput(int output);

    virtual int openInput(uint32_t *pDevices,
                            uint32_t *pSamplingRate,
                            uint32_t *pFormat,
                            uint32_t *pChannels,
                            uint32_t acoustics);

    virtual status_t closeInput(int input);

    virtual status_t setStreamOutput(uint32_t stream, int output);

    virtual status_t setVoiceVolume(float volume);

    virtual status_t getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames, int output);

    virtual int newAudioSessionId();

    virtual status_t loadEffectLibrary(const char *libPath, int *handle);

    virtual status_t unloadEffectLibrary(int handle);

    virtual status_t queryNumberEffects(uint32_t *numEffects);

    virtual status_t queryEffect(uint32_t index, effect_descriptor_t *descriptor);

    virtual status_t getEffectDescriptor(effect_uuid_t *pUuid, effect_descriptor_t *descriptor);

    virtual sp<IEffect> createEffect(pid_t pid,
                        effect_descriptor_t *pDesc,
                        const sp<IEffectClient>& effectClient,
                        int32_t priority,
                        int output,
                        int sessionId,
                        status_t *status,
                        int *id,
                        int *enabled);

    virtual status_t moveEffects(int session, int srcOutput, int dstOutput);

#ifdef HAVE_FM_RADIO
    virtual status_t setFmVolume(float volume);
#endif

    enum hardware_call_state {
        AUDIO_HW_IDLE = 0,
        AUDIO_HW_INIT,
        AUDIO_HW_OUTPUT_OPEN,
        AUDIO_HW_OUTPUT_CLOSE,
        AUDIO_HW_INPUT_OPEN,
        AUDIO_HW_INPUT_CLOSE,
        AUDIO_HW_STANDBY,
        AUDIO_HW_SET_MASTER_VOLUME,
        AUDIO_HW_GET_ROUTING,
        AUDIO_HW_SET_ROUTING,
        AUDIO_HW_GET_MODE,
        AUDIO_HW_SET_MODE,
        AUDIO_HW_GET_MIC_MUTE,
        AUDIO_HW_SET_MIC_MUTE,
        AUDIO_SET_VOICE_VOLUME,
        AUDIO_SET_PARAMETER,
#ifdef HAVE_FM_RADIO
        AUDIO_SET_FM_VOLUME
#endif
    };

    // record interface
    virtual sp<IAudioRecord> openRecord(
                                pid_t pid,
                                int input,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int frameCount,
                                uint32_t flags,
                                int *sessionId,
                                status_t *status);

    virtual     status_t    onTransact(
                                uint32_t code,
                                const Parcel& data,
                                Parcel* reply,
                                uint32_t flags);

                uint32_t    getMode() { return mMode; }

private:
                            AudioFlinger();
    virtual                 ~AudioFlinger();


    // Internal dump utilites.
    status_t dumpPermissionDenial(int fd, const Vector<String16>& args);
    status_t dumpClients(int fd, const Vector<String16>& args);
    status_t dumpInternals(int fd, const Vector<String16>& args);

    // --- Client ---
    class Client : public RefBase {
    public:
                            Client(const sp<AudioFlinger>& audioFlinger, pid_t pid);
        virtual             ~Client();
        const sp<MemoryDealer>&     heap() const;
        pid_t               pid() const { return mPid; }
        sp<AudioFlinger>    audioFlinger() { return mAudioFlinger; }

    private:
                            Client(const Client&);
                            Client& operator = (const Client&);
        sp<AudioFlinger>    mAudioFlinger;
        sp<MemoryDealer>    mMemoryDealer;
        pid_t               mPid;
    };

    // --- Notification Client ---
    class NotificationClient : public IBinder::DeathRecipient {
    public:
                            NotificationClient(const sp<AudioFlinger>& audioFlinger,
                                                const sp<IAudioFlingerClient>& client,
                                                pid_t pid);
        virtual             ~NotificationClient();

                sp<IAudioFlingerClient>    client() { return mClient; }

                // IBinder::DeathRecipient
                virtual     void        binderDied(const wp<IBinder>& who);

    private:
                            NotificationClient(const NotificationClient&);
                            NotificationClient& operator = (const NotificationClient&);

        sp<AudioFlinger>        mAudioFlinger;
        pid_t                   mPid;
        sp<IAudioFlingerClient> mClient;
    };

    class TrackHandle;
    class RecordHandle;
    class RecordThread;
    class PlaybackThread;
    class MixerThread;
    class DirectOutputThread;
    class DuplicatingThread;
    class Track;
    class RecordTrack;
    class EffectModule;
    class EffectHandle;
    class EffectChain;

    class ThreadBase : public Thread {
    public:
        ThreadBase (const sp<AudioFlinger>& audioFlinger, int id);
        virtual             ~ThreadBase();

        status_t dumpBase(int fd, const Vector<String16>& args);

        // base for record and playback
        class TrackBase : public AudioBufferProvider, public RefBase {

        public:
            enum track_state {
                IDLE,
                TERMINATED,
                STOPPED,
                RESUMING,
                ACTIVE,
                PAUSING,
                PAUSED
            };

            enum track_flags {
                STEPSERVER_FAILED = 0x01, //  StepServer could not acquire cblk->lock mutex
                SYSTEM_FLAGS_MASK = 0x0000ffffUL,
                // The upper 16 bits are used for track-specific flags.
            };

                                TrackBase(const wp<ThreadBase>& thread,
                                        const sp<Client>& client,
                                        uint32_t sampleRate,
                                        int format,
                                        int channelCount,
                                        int frameCount,
                                        uint32_t flags,
                                        const sp<IMemory>& sharedBuffer,
                                        int sessionId);
                                ~TrackBase();

            virtual status_t    start() = 0;
            virtual void        stop() = 0;
                    sp<IMemory> getCblk() const;
                    audio_track_cblk_t* cblk() const { return mCblk; }
                    int         sessionId() { return mSessionId; }

        protected:
            friend class ThreadBase;
            friend class RecordHandle;
            friend class PlaybackThread;
            friend class RecordThread;
            friend class MixerThread;
            friend class DirectOutputThread;

                                TrackBase(const TrackBase&);
                                TrackBase& operator = (const TrackBase&);

            virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer) = 0;
            virtual void releaseBuffer(AudioBufferProvider::Buffer* buffer);

            int format() const {
                return mFormat;
            }

            int channelCount() const ;

            int sampleRate() const;

            void* getBuffer(uint32_t offset, uint32_t frames) const;

            bool isStopped() const {
                return mState == STOPPED;
            }

            bool isTerminated() const {
                return mState == TERMINATED;
            }

            bool step();
            void reset();

            wp<ThreadBase>      mThread;
            sp<Client>          mClient;
            sp<IMemory>         mCblkMemory;
            audio_track_cblk_t* mCblk;
            void*               mBuffer;
            void*               mBufferEnd;
            uint32_t            mFrameCount;
            // we don't really need a lock for these
            int                 mState;
            int                 mClientTid;
            uint8_t             mFormat;
            uint32_t            mFlags;
            int                 mSessionId;
        };

        class ConfigEvent {
        public:
            ConfigEvent() : mEvent(0), mParam(0) {}

            int mEvent;
            int mParam;
        };

                    uint32_t    sampleRate() const;
                    int         channelCount() const;
                    int         format() const;
                    size_t      frameCount() const;
                    void        wakeUp()    { mWaitWorkCV.broadcast(); }
                    void        exit();
        virtual     bool        checkForNewParameters_l() = 0;
        virtual     status_t    setParameters(const String8& keyValuePairs);
        virtual     String8     getParameters(const String8& keys) = 0;
        virtual     void        audioConfigChanged_l(int event, int param = 0) = 0;
                    void        sendConfigEvent(int event, int param = 0);
                    void        sendConfigEvent_l(int event, int param = 0);
                    void        processConfigEvents();
                    int         id() const { return mId;}
                    bool        standby() { return mStandby; }

        mutable     Mutex                   mLock;

    protected:

        friend class Track;
        friend class TrackBase;
        friend class PlaybackThread;
        friend class MixerThread;
        friend class DirectOutputThread;
        friend class DuplicatingThread;
        friend class RecordThread;
        friend class RecordTrack;

                    Condition               mWaitWorkCV;
                    sp<AudioFlinger>        mAudioFlinger;
                    uint32_t                mSampleRate;
                    size_t                  mFrameCount;
                    uint32_t                mChannels;
                    uint16_t                mChannelCount;
                    uint16_t                mFrameSize;
                    int                     mFormat;
                    Condition               mParamCond;
                    Vector<String8>         mNewParameters;
                    status_t                mParamStatus;
                    Vector<ConfigEvent *>   mConfigEvents;
                    bool                    mStandby;
                    int                     mId;
                    bool                    mExiting;
    };

    // --- PlaybackThread ---
    class PlaybackThread : public ThreadBase {
    public:

        enum type {
            MIXER,
            DIRECT,
            DUPLICATING
        };

        enum mixer_state {
            MIXER_IDLE,
            MIXER_TRACKS_ENABLED,
            MIXER_TRACKS_READY
        };

        // playback track
        class Track : public TrackBase {
        public:
                                Track(  const wp<ThreadBase>& thread,
                                        const sp<Client>& client,
                                        int streamType,
                                        uint32_t sampleRate,
                                        int format,
                                        int channelCount,
                                        int frameCount,
                                        const sp<IMemory>& sharedBuffer,
                                        int sessionId);
                                ~Track();

                    void        dump(char* buffer, size_t size);
            virtual status_t    start();
            virtual void        stop();
                    void        pause();

                    void        flush();
                    void        destroy();
                    void        mute(bool);
                    void        setVolume(float left, float right);
                    int name() const {
                        return mName;
                    }

                    int type() const {
                        return mStreamType;
                    }
                    status_t    attachAuxEffect(int EffectId);
                    void        setAuxBuffer(int EffectId, int32_t *buffer);
                    int32_t     *auxBuffer() { return mAuxBuffer; }
                    void        setMainBuffer(int16_t *buffer) { mMainBuffer = buffer; }
                    int16_t     *mainBuffer() { return mMainBuffer; }
                    int         auxEffectId() { return mAuxEffectId; }


        protected:
            friend class ThreadBase;
            friend class AudioFlinger;
            friend class TrackHandle;
            friend class PlaybackThread;
            friend class MixerThread;
            friend class DirectOutputThread;

                                Track(const Track&);
                                Track& operator = (const Track&);

            virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer);
            bool isMuted() { return mMute; }
            bool isPausing() const {
                return mState == PAUSING;
            }
            bool isPaused() const {
                return mState == PAUSED;
            }
            bool isReady() const;
            void setPaused() { mState = PAUSED; }
            void reset();

            bool isOutputTrack() const {
                return (mStreamType == AudioSystem::NUM_STREAM_TYPES);
            }

            // we don't really need a lock for these
            float               mVolume[2];
            volatile bool       mMute;
            // FILLED state is used for suppressing volume ramp at begin of playing
            enum {FS_FILLING, FS_FILLED, FS_ACTIVE};
            mutable uint8_t     mFillingUpStatus;
            int8_t              mRetryCount;
            sp<IMemory>         mSharedBuffer;
            bool                mResetDone;
            int                 mStreamType;
            int                 mName;
            int16_t             *mMainBuffer;
            int32_t             *mAuxBuffer;
            int                 mAuxEffectId;
            bool                mHasVolumeController;
        };  // end of Track


        // playback track
        class OutputTrack : public Track {
        public:

            class Buffer: public AudioBufferProvider::Buffer {
            public:
                int16_t *mBuffer;
            };

                                OutputTrack(  const wp<ThreadBase>& thread,
                                        DuplicatingThread *sourceThread,
                                        uint32_t sampleRate,
                                        int format,
                                        int channelCount,
                                        int frameCount);
                                ~OutputTrack();

            virtual status_t    start();
            virtual void        stop();
                    bool        write(int16_t* data, uint32_t frames);
                    bool        bufferQueueEmpty() { return (mBufferQueue.size() == 0) ? true : false; }
                    bool        isActive() { return mActive; }
            wp<ThreadBase>&     thread()  { return mThread; }

        private:

            status_t            obtainBuffer(AudioBufferProvider::Buffer* buffer, uint32_t waitTimeMs);
            void                clearBufferQueue();

            // Maximum number of pending buffers allocated by OutputTrack::write()
            static const uint8_t kMaxOverFlowBuffers = 10;

            Vector < Buffer* >          mBufferQueue;
            AudioBufferProvider::Buffer mOutBuffer;
            bool                        mActive;
            DuplicatingThread*          mSourceThread;
        };  // end of OutputTrack

        PlaybackThread (const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output, int id, uint32_t device);
        virtual             ~PlaybackThread();

        virtual     status_t    dump(int fd, const Vector<String16>& args);

        // Thread virtuals
        virtual     status_t    readyToRun();
        virtual     void        onFirstRef();

        virtual     uint32_t    latency() const;

        virtual     status_t    setMasterVolume(float value);
        virtual     status_t    setMasterMute(bool muted);

        virtual     float       masterVolume() const;
        virtual     bool        masterMute() const;

        virtual     status_t    setStreamVolume(int stream, float value);
        virtual     status_t    setStreamMute(int stream, bool muted);

        virtual     float       streamVolume(int stream) const;
        virtual     bool        streamMute(int stream) const;

                    bool        isStreamActive(int stream) const;
#ifdef OMAP_ENHANCEMENT
        virtual     status_t	setFMRxActive(bool state);
#endif

                    sp<Track>   createTrack_l(
                                    const sp<AudioFlinger::Client>& client,
                                    int streamType,
                                    uint32_t sampleRate,
                                    int format,
                                    int channelCount,
                                    int frameCount,
                                    const sp<IMemory>& sharedBuffer,
                                    int sessionId,
                                    status_t *status);

                    AudioStreamOut* getOutput() { return mOutput; }

        virtual     int         type() const { return mType; }
                    void        suspend() { mSuspended++; }
                    void        restore() { if (mSuspended) mSuspended--; }
                    bool        isSuspended() { return (mSuspended != 0); }
        virtual     String8     getParameters(const String8& keys);
        virtual     void        audioConfigChanged_l(int event, int param = 0);
        virtual     status_t    getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames);
                    int16_t     *mixBuffer() { return mMixBuffer; };

                    sp<EffectHandle> createEffect_l(
                                        const sp<AudioFlinger::Client>& client,
                                        const sp<IEffectClient>& effectClient,
                                        int32_t priority,
                                        int sessionId,
                                        effect_descriptor_t *desc,
                                        int *enabled,
                                        status_t *status);
                    void disconnectEffect(const sp< EffectModule>& effect,
                                          const wp<EffectHandle>& handle);

                    // return values for hasAudioSession (bit field)
                    enum effect_state {
                        EFFECT_SESSION = 0x1,   // the audio session corresponds to at least one
                                                // effect
                        TRACK_SESSION = 0x2     // the audio session corresponds to at least one
                                                // track
                    };

                    uint32_t hasAudioSession(int sessionId);
                    sp<EffectChain> getEffectChain(int sessionId);
                    sp<EffectChain> getEffectChain_l(int sessionId);
                    status_t addEffectChain_l(const sp<EffectChain>& chain);
                    size_t removeEffectChain_l(const sp<EffectChain>& chain);
                    void lockEffectChains_l(Vector<sp <EffectChain> >& effectChains);
                    void unlockEffectChains(Vector<sp <EffectChain> >& effectChains);

                    sp<AudioFlinger::EffectModule> getEffect_l(int sessionId, int effectId);
                    void detachAuxEffect_l(int effectId);
                    status_t attachAuxEffect(const sp<AudioFlinger::PlaybackThread::Track> track,
                            int EffectId);
                    status_t attachAuxEffect_l(const sp<AudioFlinger::PlaybackThread::Track> track,
                            int EffectId);
                    void setMode(uint32_t mode);

                    status_t addEffect_l(const sp< EffectModule>& effect);
                    void removeEffect_l(const sp< EffectModule>& effect);

                    uint32_t getStrategyForSession_l(int sessionId);

        struct  stream_type_t {
            stream_type_t()
                :   volume(1.0f),
                    mute(false)
            {
            }
            float       volume;
            bool        mute;
        };

    protected:
        int                             mType;
        int16_t*                        mMixBuffer;
        int                             mSuspended;
        int                             mBytesWritten;
        bool                            mMasterMute;
#ifdef OMAP_ENHANCEMENT
        bool                            mFmInplay;
#endif
        SortedVector< wp<Track> >       mActiveTracks;

        virtual int             getTrackName_l() = 0;
        virtual void            deleteTrackName_l(int name) = 0;
        virtual uint32_t        activeSleepTimeUs() = 0;
        virtual uint32_t        idleSleepTimeUs() = 0;
        virtual uint32_t        suspendSleepTimeUs() = 0;

    private:

        friend class AudioFlinger;
        friend class OutputTrack;
        friend class Track;
        friend class TrackBase;
        friend class MixerThread;
        friend class DirectOutputThread;
        friend class DuplicatingThread;

        PlaybackThread(const Client&);
        PlaybackThread& operator = (const PlaybackThread&);

        status_t    addTrack_l(const sp<Track>& track);
        void        destroyTrack_l(const sp<Track>& track);

        void        readOutputParameters();

        uint32_t    device() { return mDevice; }

        virtual status_t    dumpInternals(int fd, const Vector<String16>& args);
        status_t    dumpTracks(int fd, const Vector<String16>& args);
        status_t    dumpEffectChains(int fd, const Vector<String16>& args);

        SortedVector< sp<Track> >       mTracks;
        // mStreamTypes[] uses 1 additionnal stream type internally for the OutputTrack used by DuplicatingThread
        stream_type_t                   mStreamTypes[AudioSystem::NUM_STREAM_TYPES + 1];
        AudioStreamOut*                 mOutput;
        float                           mMasterVolume;
        nsecs_t                         mLastWriteTime;
        int                             mNumWrites;
        int                             mNumDelayedWrites;
        bool                            mInWrite;
        Vector< sp<EffectChain> >       mEffectChains;
        uint32_t                        mDevice;
    };

    class MixerThread : public PlaybackThread {
    public:
        MixerThread (const sp<AudioFlinger>& audioFlinger,
                     AudioStreamOut* output,
                     int id,
                     uint32_t device);
        virtual             ~MixerThread();

        // Thread virtuals
        virtual     bool        threadLoop();

                    void        invalidateTracks(int streamType);
        virtual     bool        checkForNewParameters_l();
        virtual     status_t    dumpInternals(int fd, const Vector<String16>& args);

    protected:
                    uint32_t    prepareTracks_l(const SortedVector< wp<Track> >& activeTracks,
                                                Vector< sp<Track> > *tracksToRemove);
        virtual     int         getTrackName_l();
        virtual     void        deleteTrackName_l(int name);
        virtual     uint32_t    activeSleepTimeUs();
        virtual     uint32_t    idleSleepTimeUs();
        virtual     uint32_t    suspendSleepTimeUs();

        AudioMixer*                     mAudioMixer;
    };

    class DirectOutputThread : public PlaybackThread {
    public:

        DirectOutputThread (const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output, int id, uint32_t device);
        ~DirectOutputThread();

        // Thread virtuals
        virtual     bool        threadLoop();

        virtual     bool        checkForNewParameters_l();

    protected:
        virtual     int         getTrackName_l();
        virtual     void        deleteTrackName_l(int name);
        virtual     uint32_t    activeSleepTimeUs();
        virtual     uint32_t    idleSleepTimeUs();
        virtual     uint32_t    suspendSleepTimeUs();

    private:
        void applyVolume(uint16_t leftVol, uint16_t rightVol, bool ramp);

        float mLeftVolFloat;
        float mRightVolFloat;
        uint16_t mLeftVolShort;
        uint16_t mRightVolShort;
    };

    class DuplicatingThread : public MixerThread {
    public:
        DuplicatingThread (const sp<AudioFlinger>& audioFlinger, MixerThread* mainThread, int id);
        ~DuplicatingThread();

        // Thread virtuals
        virtual     bool        threadLoop();
                    void        addOutputTrack(MixerThread* thread);
                    void        removeOutputTrack(MixerThread* thread);
                    uint32_t    waitTimeMs() { return mWaitTimeMs; }
    protected:
        virtual     uint32_t    activeSleepTimeUs();

    private:
                    bool        outputsReady(SortedVector< sp<OutputTrack> > &outputTracks);
                    void        updateWaitTime();

        SortedVector < sp<OutputTrack> >  mOutputTracks;
                    uint32_t    mWaitTimeMs;
    };

              PlaybackThread *checkPlaybackThread_l(int output) const;
              MixerThread *checkMixerThread_l(int output) const;
              RecordThread *checkRecordThread_l(int input) const;
              float streamVolumeInternal(int stream) const { return mStreamTypes[stream].volume; }
              void audioConfigChanged_l(int event, int ioHandle, void *param2);

              int  nextUniqueId();
              status_t moveEffectChain_l(int session,
                                     AudioFlinger::PlaybackThread *srcThread,
                                     AudioFlinger::PlaybackThread *dstThread,
                                     bool reRegister);

    friend class AudioBuffer;

    class TrackHandle : public android::BnAudioTrack {
    public:
                            TrackHandle(const sp<PlaybackThread::Track>& track);
        virtual             ~TrackHandle();
        virtual status_t    start();
        virtual void        stop();
        virtual void        flush();
        virtual void        mute(bool);
        virtual void        pause();
        virtual void        setVolume(float left, float right);
        virtual sp<IMemory> getCblk() const;
        virtual status_t    attachAuxEffect(int effectId);
        virtual status_t onTransact(
            uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
    private:
        sp<PlaybackThread::Track> mTrack;
    };

    friend class Client;
    friend class PlaybackThread::Track;


                void        removeClient_l(pid_t pid);
                void        removeNotificationClient(pid_t pid);


    // record thread
    class RecordThread : public ThreadBase, public AudioBufferProvider
    {
    public:

        // record track
        class RecordTrack : public TrackBase {
        public:
                                RecordTrack(const wp<ThreadBase>& thread,
                                        const sp<Client>& client,
                                        uint32_t sampleRate,
                                        int format,
                                        int channelCount,
                                        int frameCount,
                                        uint32_t flags,
                                        int sessionId);
                                ~RecordTrack();

            virtual status_t    start();
            virtual void        stop();

                    bool        overflow() { bool tmp = mOverflow; mOverflow = false; return tmp; }
                    bool        setOverflow() { bool tmp = mOverflow; mOverflow = true; return tmp; }

                    void        dump(char* buffer, size_t size);
        private:
            friend class AudioFlinger;
            friend class RecordThread;

                                RecordTrack(const RecordTrack&);
                                RecordTrack& operator = (const RecordTrack&);

            virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer);

            bool                mOverflow;
        };


                RecordThread(const sp<AudioFlinger>& audioFlinger,
                        AudioStreamIn *input,
                        uint32_t sampleRate,
                        uint32_t channels,
                        int id);
                ~RecordThread();

        virtual bool        threadLoop();
        virtual status_t    readyToRun() { return NO_ERROR; }
        virtual void        onFirstRef();

                status_t    start(RecordTrack* recordTrack);
                void        stop(RecordTrack* recordTrack);
                status_t    dump(int fd, const Vector<String16>& args);
                AudioStreamIn* getInput() { return mInput; }

        virtual status_t    getNextBuffer(AudioBufferProvider::Buffer* buffer);
        virtual void        releaseBuffer(AudioBufferProvider::Buffer* buffer);
        virtual bool        checkForNewParameters_l();
        virtual String8     getParameters(const String8& keys);
        virtual void        audioConfigChanged_l(int event, int param = 0);
                void        readInputParameters();
        virtual unsigned int  getInputFramesLost();

    private:
                RecordThread();
                AudioStreamIn                       *mInput;
                sp<RecordTrack>                     mActiveTrack;
                Condition                           mStartStopCond;
                AudioResampler                      *mResampler;
                int32_t                             *mRsmpOutBuffer;
                int16_t                             *mRsmpInBuffer;
                size_t                              mRsmpInIndex;
                size_t                              mInputBytes;
                int                                 mReqChannelCount;
                uint32_t                            mReqSampleRate;
                ssize_t                             mBytesRead;
    };

    class RecordHandle : public android::BnAudioRecord {
    public:
        RecordHandle(const sp<RecordThread::RecordTrack>& recordTrack);
        virtual             ~RecordHandle();
        virtual status_t    start();
        virtual void        stop();
        virtual sp<IMemory> getCblk() const;
        virtual status_t onTransact(
            uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
    private:
        sp<RecordThread::RecordTrack> mRecordTrack;
    };

    //--- Audio Effect Management

    // EffectModule and EffectChain classes both have their own mutex to protect
    // state changes or resource modifications. Always respect the following order
    // if multiple mutexes must be acquired to avoid cross deadlock:
    // AudioFlinger -> ThreadBase -> EffectChain -> EffectModule

    // The EffectModule class is a wrapper object controlling the effect engine implementation
    // in the effect library. It prevents concurrent calls to process() and command() functions
    // from different client threads. It keeps a list of EffectHandle objects corresponding
    // to all client applications using this effect and notifies applications of effect state,
    // control or parameter changes. It manages the activation state machine to send appropriate
    // reset, enable, disable commands to effect engine and provide volume
    // ramping when effects are activated/deactivated.
    // When controlling an auxiliary effect, the EffectModule also provides an input buffer used by
    // the attached track(s) to accumulate their auxiliary channel.
    class EffectModule: public RefBase {
    public:
        EffectModule(const wp<ThreadBase>& wThread,
                        const wp<AudioFlinger::EffectChain>& chain,
                        effect_descriptor_t *desc,
                        int id,
                        int sessionId);
        ~EffectModule();

        enum effect_state {
            IDLE,
            RESTART,
            STARTING,
            ACTIVE,
            STOPPING,
            STOPPED
        };

        int         id() { return mId; }
        void process();
        void updateState();
        status_t command(uint32_t cmdCode,
                         uint32_t cmdSize,
                         void *pCmdData,
                         uint32_t *replySize,
                         void *pReplyData);

        void reset_l();
        status_t configure();
        status_t init();
        uint32_t state() {
            return mState;
        }
        uint32_t status() {
            return mStatus;
        }
        int sessionId() {
            return mSessionId;
        }
        status_t    setEnabled(bool enabled);
        bool isEnabled();
        bool isProcessEnabled();

        void        setInBuffer(int16_t *buffer) { mConfig.inputCfg.buffer.s16 = buffer; }
        int16_t     *inBuffer() { return mConfig.inputCfg.buffer.s16; }
        void        setOutBuffer(int16_t *buffer) { mConfig.outputCfg.buffer.s16 = buffer; }
        int16_t     *outBuffer() { return mConfig.outputCfg.buffer.s16; }
        void        setChain(const wp<EffectChain>& chain) { mChain = chain; }
        void        setThread(const wp<ThreadBase>& thread) { mThread = thread; }

        status_t addHandle(sp<EffectHandle>& handle);
        void disconnect(const wp<EffectHandle>& handle);
        size_t removeHandle (const wp<EffectHandle>& handle);

        effect_descriptor_t& desc() { return mDescriptor; }
        wp<EffectChain>&     chain() { return mChain; }

        status_t         setDevice(uint32_t device);
        status_t         setVolume(uint32_t *left, uint32_t *right, bool controller);
        status_t         setMode(uint32_t mode);

        status_t         dump(int fd, const Vector<String16>& args);

    protected:

        // Maximum time allocated to effect engines to complete the turn off sequence
        static const uint32_t MAX_DISABLE_TIME_MS = 10000;

        EffectModule(const EffectModule&);
        EffectModule& operator = (const EffectModule&);

        status_t start_l();
        status_t stop_l();

        // update this table when AudioSystem::audio_devices or audio_device_e (in EffectApi.h) are modified
        static const uint32_t sDeviceConvTable[];
        static uint32_t deviceAudioSystemToEffectApi(uint32_t device);

        // update this table when AudioSystem::audio_mode or audio_mode_e (in EffectApi.h) are modified
        static const uint32_t sModeConvTable[];
        static int modeAudioSystemToEffectApi(uint32_t mode);

        Mutex               mLock;      // mutex for process, commands and handles list protection
        wp<ThreadBase>      mThread;    // parent thread
        wp<EffectChain>     mChain;     // parent effect chain
        int                 mId;        // this instance unique ID
        int                 mSessionId; // audio session ID
        effect_descriptor_t mDescriptor;// effect descriptor received from effect engine
        effect_config_t     mConfig;    // input and output audio configuration
        effect_interface_t  mEffectInterface; // Effect module C API
        status_t mStatus;               // initialization status
        uint32_t mState;                // current activation state (effect_state)
        Vector< wp<EffectHandle> > mHandles;    // list of client handles
        uint32_t mMaxDisableWaitCnt;    // maximum grace period before forcing an effect off after
                                        // sending disable command.
        uint32_t mDisableWaitCnt;       // current process() calls count during disable period.
    };

    // The EffectHandle class implements the IEffect interface. It provides resources
    // to receive parameter updates, keeps track of effect control
    // ownership and state and has a pointer to the EffectModule object it is controlling.
    // There is one EffectHandle object for each application controlling (or using)
    // an effect module.
    // The EffectHandle is obtained by calling AudioFlinger::createEffect().
    class EffectHandle: public android::BnEffect {
    public:

        EffectHandle(const sp<EffectModule>& effect,
                const sp<AudioFlinger::Client>& client,
                const sp<IEffectClient>& effectClient,
                int32_t priority);
        virtual ~EffectHandle();

        // IEffect
        virtual status_t enable();
        virtual status_t disable();
        virtual status_t command(uint32_t cmdCode,
                                 uint32_t cmdSize,
                                 void *pCmdData,
                                 uint32_t *replySize,
                                 void *pReplyData);
        virtual void disconnect();
        virtual sp<IMemory> getCblk() const;
        virtual status_t onTransact(uint32_t code, const Parcel& data,
                Parcel* reply, uint32_t flags);


        // Give or take control of effect module
        void setControl(bool hasControl, bool signal);
        void commandExecuted(uint32_t cmdCode,
                             uint32_t cmdSize,
                             void *pCmdData,
                             uint32_t replySize,
                             void *pReplyData);
        void setEnabled(bool enabled);

        // Getters
        int id() { return mEffect->id(); }
        int priority() { return mPriority; }
        bool hasControl() { return mHasControl; }
        sp<EffectModule> effect() { return mEffect; }

        void dump(char* buffer, size_t size);

    protected:

        EffectHandle(const EffectHandle&);
        EffectHandle& operator =(const EffectHandle&);

        sp<EffectModule> mEffect;           // pointer to controlled EffectModule
        sp<IEffectClient> mEffectClient;    // callback interface for client notifications
        sp<Client>          mClient;        // client for shared memory allocation
        sp<IMemory>         mCblkMemory;    // shared memory for control block
        effect_param_cblk_t* mCblk;         // control block for deferred parameter setting via shared memory
        uint8_t*            mBuffer;        // pointer to parameter area in shared memory
        int mPriority;                      // client application priority to control the effect
        bool mHasControl;                   // true if this handle is controlling the effect
    };

    // the EffectChain class represents a group of effects associated to one audio session.
    // There can be any number of EffectChain objects per output mixer thread (PlaybackThread).
    // The EffecChain with session ID 0 contains global effects applied to the output mix.
    // Effects in this chain can be insert or auxiliary. Effects in other chains (attached to tracks)
    // are insert only. The EffectChain maintains an ordered list of effect module, the order corresponding
    // in the effect process order. When attached to a track (session ID != 0), it also provide it's own
    // input buffer used by the track as accumulation buffer.
    class EffectChain: public RefBase {
    public:
        EffectChain(const wp<ThreadBase>& wThread, int sessionId);
        ~EffectChain();

        void process_l();

        void lock() {
            mLock.lock();
        }
        void unlock() {
            mLock.unlock();
        }

        status_t addEffect_l(const sp<EffectModule>& handle);
        size_t removeEffect_l(const sp<EffectModule>& handle);

        int sessionId() {
            return mSessionId;
        }

        sp<EffectModule> getEffectFromDesc_l(effect_descriptor_t *descriptor);
        sp<EffectModule> getEffectFromId_l(int id);
        bool setVolume_l(uint32_t *left, uint32_t *right);
        void setDevice_l(uint32_t device);
        void setMode_l(uint32_t mode);

        void setInBuffer(int16_t *buffer, bool ownsBuffer = false) {
            mInBuffer = buffer;
            mOwnInBuffer = ownsBuffer;
        }
        int16_t *inBuffer() {
            return mInBuffer;
        }
        void setOutBuffer(int16_t *buffer) {
            mOutBuffer = buffer;
        }
        int16_t *outBuffer() {
            return mOutBuffer;
        }

        void startTrack() {mActiveTrackCnt++;}
        void stopTrack() {mActiveTrackCnt--;}
        int activeTracks() { return mActiveTrackCnt;}

        uint32_t strategy() { return mStrategy; }
        void setStrategy(uint32_t strategy)
                 { mStrategy = strategy; }

        status_t dump(int fd, const Vector<String16>& args);

    protected:

        EffectChain(const EffectChain&);
        EffectChain& operator =(const EffectChain&);

        wp<ThreadBase> mThread;     // parent mixer thread
        Mutex mLock;                // mutex protecting effect list
        Vector<sp<EffectModule> > mEffects; // list of effect modules
        int mSessionId;             // audio session ID
        int16_t *mInBuffer;         // chain input buffer
        int16_t *mOutBuffer;        // chain output buffer
        int mActiveTrackCnt;        // number of active tracks connected
        bool mOwnInBuffer;          // true if the chain owns its input buffer
        int mVolumeCtrlIdx;         // index of insert effect having control over volume
        uint32_t mLeftVolume;       // previous volume on left channel
        uint32_t mRightVolume;      // previous volume on right channel
        uint32_t mNewLeftVolume;       // new volume on left channel
        uint32_t mNewRightVolume;      // new volume on right channel
        uint32_t mStrategy; // strategy for this effect chain
    };

    friend class RecordThread;
    friend class PlaybackThread;


    mutable     Mutex                               mLock;

                DefaultKeyedVector< pid_t, wp<Client> >     mClients;

                mutable     Mutex                   mHardwareLock;
                AudioHardwareInterface*             mAudioHardware;
#ifdef OMAP_ENHANCEMENT
                bool                                mFmEnabled;
#endif
    mutable     int                                 mHardwareStatus;


                DefaultKeyedVector< int, sp<PlaybackThread> >  mPlaybackThreads;
                PlaybackThread::stream_type_t       mStreamTypes[AudioSystem::NUM_STREAM_TYPES];
                float                               mMasterVolume;
                bool                                mMasterMute;

                DefaultKeyedVector< int, sp<RecordThread> >    mRecordThreads;

                DefaultKeyedVector< pid_t, sp<NotificationClient> >    mNotificationClients;
                volatile int32_t                    mNextUniqueId;
#ifdef LVMX
                int mLifeVibesClientPid;
#endif
                uint32_t mMode;

#ifdef HAVE_FM_RADIO
                bool                                mFmOn;
#endif
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_AUDIO_FLINGER_H
