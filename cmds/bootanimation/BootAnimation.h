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

#ifndef ANDROID_BOOTANIMATION_H
#define ANDROID_BOOTANIMATION_H

#include <stdint.h>
#include <sys/types.h>

#include <androidfw/AssetManager.h>
#include <utils/Thread.h>

#include <EGL/egl.h>
#include <GLES/gl.h>

#include <utils/Thread.h>

class SkBitmap;

namespace android {

class AudioPlayer;
class Surface;
class SurfaceComposerClient;
class SurfaceControl;
#ifdef MULTITHREAD_DECODE
class FrameManager;
#endif

// ---------------------------------------------------------------------------

class BootAnimation : public Thread, public IBinder::DeathRecipient
{
#ifdef MULTITHREAD_DECODE
    friend class FrameManager;
#endif
public:
                BootAnimation();
    virtual     ~BootAnimation();

    sp<SurfaceComposerClient> session() const;

private:
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    struct Texture {
        GLint   w;
        GLint   h;
        GLuint  name;
    };

    struct Animation {
        struct Frame {
            String8 name;
            FileMap* map;
            mutable GLuint tid;
            bool operator < (const Frame& rhs) const {
                return name < rhs.name;
            }
        };
        struct Part {
            int count;  // The number of times this part should repeat, 0 for infinite
            int pause;  // The number of frames to pause for at the end of this part
            int clockPosY;  // The y position of the clock, in pixels, from the bottom of the
                            // display (the clock is centred horizontally). -1 to disable the clock
            String8 path;
            SortedVector<Frame> frames;
            bool playUntilComplete;
            float backgroundColor[3];
            FileMap* audioFile;
            Animation* animation;
        };
        int fps;
        int width;
        int height;
        Vector<Part> parts;
        String8 audioConf;
        String8 fileName;
        ZipFileRO* zip;
    };

    /**
     *IMG_OEM: bootanimation file from oem/media
     *IMG_SYS: bootanimation file from system/media
     *IMG_ENC: encrypted bootanimation file from system/media
     */
    enum ImageID { IMG_OEM = 0, IMG_SYS = 1, IMG_ENC = 2 };
    const char *getAnimationFileName(ImageID image);
    const char *getBootRingtoneFileName(ImageID image);
    void playBackgroundMusic();
    bool checkBootState();
    status_t initTexture(Texture* texture, AssetManager& asset, const char* name);
    status_t initTexture(const Animation::Frame& frame);
    status_t initTexture(SkBitmap *bitmap);
    bool android();
    bool movie();
    void drawTime(const Texture& clockTex, const int yPos);
    Animation* loadAnimation(const String8&);
    bool playAnimation(const Animation&);
    void releaseAnimation(Animation*) const;
    bool parseAnimationDesc(Animation&);
    bool preloadZip(Animation &animation);

    void checkExit();

    static SkBitmap *decode(const Animation::Frame& frame);

    sp<SurfaceComposerClient>       mSession;
    sp<AudioPlayer>                 mAudioPlayer;
    AssetManager mAssets;
    Texture     mAndroid[3];
    Texture     mClock;
    int         mWidth;
    int         mHeight;
    EGLDisplay  mDisplay;
    EGLDisplay  mContext;
    EGLDisplay  mSurface;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;
    bool        mClockEnabled;
    String8     mZipFileName;
    SortedVector<String8> mLoadedFiles;
};

#ifdef MULTITHREAD_DECODE
class FrameManager {
public:
    struct DecodeWork {
        const BootAnimation::Animation::Frame *frame;
        SkBitmap *bitmap;
        size_t idx;
    };

    FrameManager(int numThreads, size_t maxSize,
            const SortedVector<BootAnimation::Animation::Frame>& frames);
    virtual ~FrameManager();

    SkBitmap* next();

protected:
    DecodeWork getWork();
    void completeWork(DecodeWork work);

private:

    class DecodeThread : public Thread {
    public:
        DecodeThread(FrameManager* manager);
        virtual ~DecodeThread() {}
    private:
        virtual bool threadLoop();
        FrameManager *mManager;
    };

    size_t mMaxSize;
    size_t mFrameCounter;
    size_t mNextIdx;
    const SortedVector<BootAnimation::Animation::Frame>& mFrames;
    Vector<DecodeWork> mDecodedFrames;
    pthread_mutex_t mBitmapsMutex;
    pthread_cond_t mSpaceAvailableCondition;
    pthread_cond_t mBitmapReadyCondition;
    bool mExit;
    Vector<sp<DecodeThread> > mThreads;
};
#endif

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_BOOTANIMATION_H
