/*
**
** Copyright (C) 2008, The Android Open Source Project
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

#define LOG_TAG "CameraService"

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <media/AudioSystem.h>
#include <media/mediaplayer.h>
#include <surfaceflinger/ISurface.h>
#include <ui/Overlay.h>
#include <utils/Errors.h>
#include <utils/Log.h>
#include <utils/String16.h>

#include "CameraService.h"

#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
#include "gralloc_priv.h"
#endif

#include <cutils/properties.h>

namespace android {

// ----------------------------------------------------------------------------
// Logging support -- this is for debugging only
// Use "adb shell dumpsys media.camera -v 1" to change it.
static volatile int32_t gLogLevel = 0;

#define LOG1(...) LOGD_IF(gLogLevel >= 1, __VA_ARGS__);
#define LOG2(...) LOGD_IF(gLogLevel >= 2, __VA_ARGS__);

static void setLogLevel(int level) {
    android_atomic_write(level, &gLogLevel);
}

// ----------------------------------------------------------------------------

#ifdef BOARD_USE_FROYO_LIBCAMERA
struct camera_size_type {
    int width;
    int height;
};

static const camera_size_type preview_sizes[] = {
    { 1280, 720 }, // 720P
    { 768, 432 },
};
#endif

static int getCallingPid() {
    return IPCThreadState::self()->getCallingPid();
}

static int getCallingUid() {
    return IPCThreadState::self()->getCallingUid();
}

#if defined(BOARD_USE_FROYO_LIBCAMERA) || defined(BOARD_HAVE_HTC_FFC)
#define HTC_SWITCH_CAMERA_FILE_PATH "/sys/android_camera2/htcwc"
static void htcCameraSwitch(int cameraId)
{
    char buffer[16];
    int fd;

    if (access(HTC_SWITCH_CAMERA_FILE_PATH, W_OK) == 0) {
        snprintf(buffer, sizeof(buffer), "%d", cameraId);

        fd = open(HTC_SWITCH_CAMERA_FILE_PATH, O_WRONLY);
        write(fd, buffer, strlen(buffer));
        close(fd);
    }
}
#endif

#ifdef OMAP3_FW3A_LIBCAMERA
static void setOmapISPReserve(int state)
{
    char buffer[16];
    int fd;

    if (access("/sys/devices/platform/omap3isp/isp_reserve", W_OK) == 0) {
        snprintf(buffer, sizeof(buffer), "%d", state);

        fd = open("/sys/devices/platform/omap3isp/isp_reserve", O_WRONLY);
        write(fd, buffer, strlen(buffer));
        close(fd);
    }
}
#endif

// ----------------------------------------------------------------------------

// This is ugly and only safe if we never re-create the CameraService, but
// should be ok for now.
static CameraService *gCameraService;

CameraService::CameraService()
:mSoundRef(0)
{
    LOGI("CameraService started (pid=%d)", getpid());

    mNumberOfCameras = HAL_getNumberOfCameras();
    if (mNumberOfCameras > MAX_CAMERAS) {
        LOGE("Number of cameras(%d) > MAX_CAMERAS(%d).",
             mNumberOfCameras, MAX_CAMERAS);
        mNumberOfCameras = MAX_CAMERAS;
    }

    for (int i = 0; i < mNumberOfCameras; i++) {
        setCameraFree(i);
    }

    gCameraService = this;
}

CameraService::~CameraService() {
    for (int i = 0; i < mNumberOfCameras; i++) {
        if (mBusy[i]) {
            LOGE("camera %d is still in use in destructor!", i);
        }
    }

    gCameraService = NULL;
}

int32_t CameraService::getNumberOfCameras() {
    return mNumberOfCameras;
}

#if defined(BOARD_USE_FROYO_LIBCAMERA) || defined(BOARD_HAVE_HTC_FFC)
#ifndef FIRST_CAMERA_FACING
#define FIRST_CAMERA_FACING CAMERA_FACING_BACK
#endif
#ifndef FIRST_CAMERA_ORIENTATION
#define FIRST_CAMERA_ORIENTATION 90
#endif
static const CameraInfo sCameraInfo[] = {
    {
        FIRST_CAMERA_FACING,
        FIRST_CAMERA_ORIENTATION,  /* orientation */
    },
    {
        CAMERA_FACING_FRONT,
        270, /* orientation */
    }
};
#endif

status_t CameraService::getCameraInfo(int cameraId,
                                      struct CameraInfo* cameraInfo) {
    if (cameraId < 0 || cameraId >= mNumberOfCameras) {
        return BAD_VALUE;
    }
#if defined(BOARD_USE_FROYO_LIBCAMERA) || defined(BOARD_HAVE_HTC_FFC)
    memcpy(cameraInfo, &sCameraInfo[cameraId], sizeof(CameraInfo));
#else
    HAL_getCameraInfo(cameraId, cameraInfo);
#endif
    return OK;
}

sp<ICamera> CameraService::connect(
        const sp<ICameraClient>& cameraClient, int cameraId) {
    int callingPid = getCallingPid();
    LOG1("CameraService::connect E (pid %d, id %d)", callingPid, cameraId);

    sp<Client> client;
    if (cameraId < 0 || cameraId >= mNumberOfCameras) {
        LOGE("CameraService::connect X (pid %d) rejected (invalid cameraId %d).",
            callingPid, cameraId);
        return NULL;
    }

    Mutex::Autolock lock(mServiceLock);
    if (mClient[cameraId] != 0) {
        client = mClient[cameraId].promote();
        if (client != 0) {
            if (cameraClient->asBinder() == client->getCameraClient()->asBinder()) {
                LOG1("CameraService::connect X (pid %d) (the same client)",
                    callingPid);
                return client;
            } else {
                LOGW("CameraService::connect X (pid %d) rejected (existing client).",
                    callingPid);
                return NULL;
            }
        }
        mClient[cameraId].clear();
    }

    if (mBusy[cameraId]) {
        LOGW("CameraService::connect X (pid %d) rejected"
             " (camera %d is still busy).", callingPid, cameraId);
        return NULL;
    }

#if defined(BOARD_USE_FROYO_LIBCAMERA) || defined(BOARD_HAVE_HTC_FFC)
    htcCameraSwitch(cameraId);
#endif
    sp<CameraHardwareInterface> hardware = HAL_openCameraHardware(cameraId);
    if (hardware == NULL) {
        LOGE("Fail to open camera hardware (id=%d)", cameraId);
        return NULL;
    }

#if defined(OMAP3_FW3A_LIBCAMERA) && defined(OMAP3_SECONDARY_CAMERA)
    {
        CameraParameters params(hardware->getParameters());
        params.set("video-input", cameraId);
        /* FFC doesn't export its own parameter list... :( */
        if (cameraId) {
            params.set("picture-size-values", "1600x1200,1280x960,1280x720,640x480,512x384,320x240"); 
            params.set("focus-mode-values", "fixed");
        }
        hardware->setParameters(params);
    }
#endif


#if defined(BOARD_USE_REVERSE_FFC)
    if (cameraId == 1) {
        /* Change default parameters for the front camera */
        CameraParameters params(hardware->getParameters());
        params.set("front-camera-mode", "reverse"); // default is "mirror"
        hardware->setParameters(params);
    }
#endif
#ifdef BOARD_HAS_LGE_FFC
    CameraParameters params(hardware->getParameters());
    if (cameraId == 1) {
        params.set("nv-flip-mode","vertical");
    } else {
        params.set("nv-flip-mode","off");
    }
    hardware->setParameters(params);
#endif


    CameraInfo info;
    HAL_getCameraInfo(cameraId, &info);

    client = new Client(this, cameraClient, hardware, cameraId, info.facing,
                        callingPid);
    mClient[cameraId] = client;
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
    if (client->mHardware == NULL) {
        client = NULL;
        mClient[cameraId] = NULL;
        return client;
    }
#endif
    LOG1("CameraService::connect X");
    return client;
}

void CameraService::removeClient(const sp<ICameraClient>& cameraClient) {
    int callingPid = getCallingPid();
    LOG1("CameraService::removeClient E (pid %d)", callingPid);

    for (int i = 0; i < mNumberOfCameras; i++) {
        // Declare this before the lock to make absolutely sure the
        // destructor won't be called with the lock held.
        sp<Client> client;

        Mutex::Autolock lock(mServiceLock);

        // This happens when we have already disconnected (or this is
        // just another unused camera).
        if (mClient[i] == 0) continue;

        // Promote mClient. It can fail if we are called from this path:
        // Client::~Client() -> disconnect() -> removeClient().
        client = mClient[i].promote();

        if (client == 0) {
            mClient[i].clear();
            continue;
        }

        if (cameraClient->asBinder() == client->getCameraClient()->asBinder()) {
            // Found our camera, clear and leave.
            LOG1("removeClient: clear camera %d", i);
            mClient[i].clear();
            break;
        }
    }

    LOG1("CameraService::removeClient X (pid %d)", callingPid);
}

sp<CameraService::Client> CameraService::getClientById(int cameraId) {
    if (cameraId < 0 || cameraId >= mNumberOfCameras) return NULL;
    return mClient[cameraId].promote();
}

status_t CameraService::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
    // Permission checks
    switch (code) {
        case BnCameraService::CONNECT:
            const int pid = getCallingPid();
            const int self_pid = getpid();
            if (pid != self_pid) {
                // we're called from a different process, do the real check
                if (!checkCallingPermission(
                        String16("android.permission.CAMERA"))) {
                    const int uid = getCallingUid();
                    LOGE("Permission Denial: "
                         "can't use the camera pid=%d, uid=%d", pid, uid);
                    return PERMISSION_DENIED;
                }
            }
            break;
    }

    return BnCameraService::onTransact(code, data, reply, flags);
}

// The reason we need this busy bit is a new CameraService::connect() request
// may come in while the previous Client's destructor has not been run or is
// still running. If the last strong reference of the previous Client is gone
// but the destructor has not been finished, we should not allow the new Client
// to be created because we need to wait for the previous Client to tear down
// the hardware first.
void CameraService::setCameraBusy(int cameraId) {
    android_atomic_write(1, &mBusy[cameraId]);
}

void CameraService::setCameraFree(int cameraId) {
    android_atomic_write(0, &mBusy[cameraId]);
}

// We share the media players for shutter and recording sound for all clients.
// A reference count is kept to determine when we will actually release the
// media players.

static MediaPlayer* newMediaPlayer(const char *file) {
    MediaPlayer* mp = new MediaPlayer();
    if (mp->setDataSource(file, NULL) == NO_ERROR) {
        mp->setAudioStreamType(AudioSystem::ENFORCED_AUDIBLE);
        mp->prepare();
    } else {
        LOGE("Failed to load CameraService sounds: %s", file);
        return NULL;
    }
    return mp;
}

void CameraService::loadSound() {
    Mutex::Autolock lock(mSoundLock);
    LOG1("CameraService::loadSound ref=%d", mSoundRef);
    if (mSoundRef++) return;

    char value[PROPERTY_VALUE_MAX];
    property_get("ro.camera.sound.disabled", value, "0");
    int systemMute = atoi(value);
    property_get("persist.sys.camera-mute", value, "0");
    int userMute = atoi(value);

    if(!systemMute && !userMute) {
        mSoundPlayer[SOUND_SHUTTER] = newMediaPlayer("/system/media/audio/ui/camera_click.ogg");
        mSoundPlayer[SOUND_RECORDING] = newMediaPlayer("/system/media/audio/ui/VideoRecord.ogg");
    }
    else {
        mSoundPlayer[SOUND_SHUTTER] = NULL;
        mSoundPlayer[SOUND_RECORDING] = NULL;
    }
}

void CameraService::releaseSound() {
    Mutex::Autolock lock(mSoundLock);
    LOG1("CameraService::releaseSound ref=%d", mSoundRef);
    if (--mSoundRef) return;

    for (int i = 0; i < NUM_SOUNDS; i++) {
        if (mSoundPlayer[i] != 0) {
            mSoundPlayer[i]->disconnect();
            mSoundPlayer[i].clear();
        }
    }
}

void CameraService::playSound(sound_kind kind) {
    LOG1("playSound(%d)", kind);
    Mutex::Autolock lock(mSoundLock);
    sp<MediaPlayer> player = mSoundPlayer[kind];
    if (player != 0) {
        // do not play the sound if stream volume is 0
        // (typically because ringer mode is silent).
        int index;
        AudioSystem::getStreamVolumeIndex(AudioSystem::ENFORCED_AUDIBLE, &index);
        if (index != 0) {
#ifndef OMAP_ENHANCEMENT
            player->seekTo(0);
#endif
            player->start();
        }
    }
}

// ----------------------------------------------------------------------------

CameraService::Client::Client(const sp<CameraService>& cameraService,
        const sp<ICameraClient>& cameraClient,
        const sp<CameraHardwareInterface>& hardware,
        int cameraId, int cameraFacing, int clientPid) {
    int callingPid = getCallingPid();
    LOG1("Client::Client E (pid %d)", callingPid);

    mCameraService = cameraService;
    mCameraClient = cameraClient;
    mHardware = hardware;
    mCameraId = cameraId;
    mCameraFacing = cameraFacing;
    mClientPid = clientPid;
    mMsgEnabled = 0;
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
    if (mHardware != NULL) {
#endif
        mUseOverlay = mHardware->useOverlay();

        mHardware->setCallbacks(notifyCallback,
                                dataCallback,
                                dataCallbackTimestamp,
                                (void *)cameraId);

        // Enable zoom, error, and focus messages by default
        enableMsgType(CAMERA_MSG_ERROR |
                      CAMERA_MSG_ZOOM |
                      CAMERA_MSG_FOCUS);
        mOverlayW = 0;
        mOverlayH = 0;

#ifdef OMAP_ENHANCEMENT
	mS3DOverlay = false;
#endif
#ifdef OMAP3_FW3A_LIBCAMERA
        setOmapISPReserve(1);
#endif

        // Callback is disabled by default
        mPreviewCallbackFlag = FRAME_CALLBACK_FLAG_NOOP;
        mOrientation = getOrientation(0, mCameraFacing == CAMERA_FACING_FRONT);
        mOrientationChanged = false;
        cameraService->setCameraBusy(cameraId);
        cameraService->loadSound();
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
    }
#endif
    LOG1("Client::Client X (pid %d)", callingPid);
}

static void *unregister_surface(void *arg) {
    ISurface *surface = (ISurface *)arg;
    surface->unregisterBuffers();
    IPCThreadState::self()->flushCommands();
    return NULL;
}

// tear down the client
CameraService::Client::~Client() {
    int callingPid = getCallingPid();
    LOG1("Client::~Client E (pid %d, this %p)", callingPid, this);

    if (mSurface != 0 && !mUseOverlay) {
        pthread_t thr;
        // We unregister the buffers in a different thread because binder does
        // not let us make sychronous transactions in a binder destructor (that
        // is, upon our reaching a refcount of zero.)
        pthread_create(&thr,
                       NULL,  // attr
                       unregister_surface,
                       mSurface.get());
        pthread_join(thr, NULL);
    }

    // set mClientPid to let disconnet() tear down the hardware
    mClientPid = callingPid;
    disconnect();
    mCameraService->releaseSound();
    LOG1("Client::~Client X (pid %d, this %p)", callingPid, this);
}

// ----------------------------------------------------------------------------

status_t CameraService::Client::checkPid() const {
    int callingPid = getCallingPid();
    if (callingPid == mClientPid) return NO_ERROR;

    LOGW("attempt to use a locked camera from a different process"
         " (old pid %d, new pid %d)", mClientPid, callingPid);
    return EBUSY;
}

status_t CameraService::Client::checkPidAndHardware() const {
    status_t result = checkPid();
    if (result != NO_ERROR) return result;
    if (mHardware == 0) {
        LOGE("attempt to use a camera after disconnect() (pid %d)", getCallingPid());
        return INVALID_OPERATION;
    }
    return NO_ERROR;
}

status_t CameraService::Client::lock() {
    int callingPid = getCallingPid();
    LOG1("lock (pid %d)", callingPid);
    Mutex::Autolock lock(mLock);

    // lock camera to this client if the the camera is unlocked
    if (mClientPid == 0) {
        mClientPid = callingPid;
        return NO_ERROR;
    }

    // returns NO_ERROR if the client already owns the camera, EBUSY otherwise
    return checkPid();
}

status_t CameraService::Client::unlock() {
    int callingPid = getCallingPid();
    LOG1("unlock (pid %d)", callingPid);
    Mutex::Autolock lock(mLock);

    // allow anyone to use camera (after they lock the camera)
    status_t result = checkPid();
    if (result == NO_ERROR) {
        mClientPid = 0;
        LOG1("clear mCameraClient (pid %d)", callingPid);
        // we need to remove the reference to ICameraClient so that when the app
        // goes away, the reference count goes to 0.
        mCameraClient.clear();
    }
    return result;
}

// connect a new client to the camera
status_t CameraService::Client::connect(const sp<ICameraClient>& client) {
    int callingPid = getCallingPid();
    LOG1("connect E (pid %d)", callingPid);
    Mutex::Autolock lock(mLock);

    if (mClientPid != 0 && checkPid() != NO_ERROR) {
        LOGW("Tried to connect to a locked camera (old pid %d, new pid %d)",
                mClientPid, callingPid);
        return EBUSY;
    }

    if (mCameraClient != 0 && (client->asBinder() == mCameraClient->asBinder())) {
        LOG1("Connect to the same client");
        return NO_ERROR;
    }

    mPreviewCallbackFlag = FRAME_CALLBACK_FLAG_NOOP;
    mClientPid = callingPid;
    mCameraClient = client;

    LOG1("connect X (pid %d)", callingPid);
    return NO_ERROR;
}

void CameraService::Client::disconnect() {
    int callingPid = getCallingPid();
    LOG1("disconnect E (pid %d)", callingPid);
    Mutex::Autolock lock(mLock);

    if (checkPid() != NO_ERROR) {
        LOGW("different client - don't disconnect");
        return;
    }

    if (mClientPid <= 0) {
        LOG1("camera is unlocked (mClientPid = %d), don't tear down hardware", mClientPid);
        return;
    }

    // Make sure disconnect() is done once and once only, whether it is called
    // from the user directly, or called by the destructor.
    if (mHardware == 0) return;

    LOG1("hardware teardown");
    // Before destroying mHardware, we must make sure it's in the
    // idle state.
    // Turn off all messages.
    disableMsgType(CAMERA_MSG_ALL_MSGS);
    mHardware->stopPreview();
    mHardware->cancelPicture();
    // Release the hardware resources.
    mHardware->release();
#ifdef OMAP3_FW3A_LIBCAMERA
    setOmapISPReserve(0);
#endif
    // Release the held overlay resources.
    if (mUseOverlay) {
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
        /* Release previous overlay handle */
        if (mOverlay != NULL) {
            mOverlay->destroy();
        }
#endif
        mOverlayRef = 0;
    }
    mHardware.clear();

    mCameraService->removeClient(mCameraClient);
    mCameraService->setCameraFree(mCameraId);

    LOG1("disconnect X (pid %d)", callingPid);
}

// ----------------------------------------------------------------------------

// set the ISurface that the preview will use
status_t CameraService::Client::setPreviewDisplay(const sp<ISurface>& surface) {
    LOG1("setPreviewDisplay(%p) (pid %d)", surface.get(), getCallingPid());
    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    result = NO_ERROR;

    // return if no change in surface.
    // asBinder() is safe on NULL (returns NULL)
    if (surface->asBinder() == mSurface->asBinder()) {
        return result;
    }

    if (mSurface != 0) {
        LOG1("clearing old preview surface %p", mSurface.get());
        if (mUseOverlay) {
            // Force the destruction of any previous overlay
            sp<Overlay> dummy;
            mHardware->setOverlay(dummy);
            mOverlayRef = 0;
        } else {
            mSurface->unregisterBuffers();
        }
    }
    mSurface = surface;
    mOverlayRef = 0;
    // If preview has been already started, set overlay or register preview
    // buffers now.
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
    if (mHardware->previewEnabled() || mUseOverlay) {
#else
    if (mHardware->previewEnabled()) {
#endif
        if (mUseOverlay) {
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
            if (mSurface != NULL) {
#endif
                result = setOverlay();
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
            }
#endif
        } else if (mSurface != 0) {
            result = registerPreviewBuffers();
        }
    }

    return result;
}

status_t CameraService::Client::registerPreviewBuffers() {
    int w, h;
    CameraParameters params(mHardware->getParameters());
    params.getPreviewSize(&w, &h);

#ifdef BOARD_USE_FROYO_LIBCAMERA
    //for 720p recording , preview can be 800X448
    if(w ==  preview_sizes[0].width && h== preview_sizes[0].height){
        LOGD("registerpreviewbufs :changing dimensions to 768X432 for 720p recording.");
        w = preview_sizes[1].width;
        h = preview_sizes[1].height;
    }
#endif

    // FIXME: don't use a hardcoded format here.
    ISurface::BufferHeap buffers(w, h, w, h,
                                 HAL_PIXEL_FORMAT_YCrCb_420_SP,
                                 mOrientation,
                                 0,
                                 mHardware->getPreviewHeap());

    status_t result = mSurface->registerBuffers(buffers);
    if (result != NO_ERROR) {
        LOGE("registerBuffers failed with status %d", result);
    }
    return result;
}

status_t CameraService::Client::setOverlay() {
    int w, h;
#ifdef OMAP_ENHANCEMENT
    uint32_t overlayFormat;
    bool isS3d = false;

#endif

    CameraParameters params(mHardware->getParameters());
    params.getPreviewSize(&w, &h);

#ifdef BOARD_USE_FROYO_LIBCAMERA
    //for 720p recording , preview can be 800X448
    if(w == preview_sizes[0].width && h==preview_sizes[0].height){
        LOGD("Changing overlay dimensions to 768X432 for 720p recording.");
        w = preview_sizes[1].width;
        h = preview_sizes[1].height;
    }
#endif

#ifdef OMAP_ENHANCEMENT

    ///Query the current preview pixel format from Camera HAL to create the overlay
    ///in that particular format
    const char *prevFormat = params.getPreviewFormat();
#if defined(TARGET_OMAP3)
    LOGD("Camera service Selected OVERLAY_FORMAT_CbYCrY_422_I");
    overlayFormat = OVERLAY_FORMAT_CbYCrY_422_I;
#else
    if(strcmp(prevFormat, CameraParameters::PIXEL_FORMAT_YUV422I)==0)
    {
        LOGD("Camera service Selected OVERLAY_FORMAT_CbYCrY_422_I");
        overlayFormat = OVERLAY_FORMAT_CbYCrY_422_I;
    }
    else if(strcmp(prevFormat, CameraParameters::PIXEL_FORMAT_YUV420SP)==0)
    {
        LOGD("Camera service Selected OVERLAY_FORMAT_YCbCr_420_SP");
        overlayFormat = OVERLAY_FORMAT_YCbCr_420_SP;
    }
    else if(strcmp(prevFormat, CameraParameters::PIXEL_FORMAT_RGB565)==0)
    {
        LOGD("Camera service Selected OVERLAY_FORMAT_RGB_565");
        overlayFormat = OVERLAY_FORMAT_RGB_565;
    }
    else
    {
        overlayFormat = OVERLAY_FORMAT_DEFAULT;
    }
#endif

    if(params.get("s3d-supported")!= NULL && CameraParameters::TRUE != NULL)
        isS3d = strcmp(params.get("s3d-supported"), CameraParameters::TRUE) == 0;
#endif

    if (w != mOverlayW || h != mOverlayH || mOrientationChanged
#ifdef OMAP_ENHANCEMENT
        || ((mOverlayFormat!=NULL) && (strcmp(prevFormat, mOverlayFormat)!=0))
        || (mS3DOverlay != isS3d)
#endif
    ) {
        // Force the destruction of any previous overlay
        sp<Overlay> dummy;
        mHardware->setOverlay(dummy);
        mOverlayRef = 0;
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
        if (mOverlay != NULL) {
            mOverlay->destroy();
        }
#endif
        mOrientationChanged = false;
#ifdef OMAP_ENHANCEMENT
        mS3DOverlay = isS3d;
#endif
    }

    status_t result = NO_ERROR;
    if (mSurface == 0) {
        result = mHardware->setOverlay(NULL);
    } else {
        if (mOverlayRef == 0) {
            // FIXME:
            // Surfaceflinger may hold onto the previous overlay reference for some
            // time after we try to destroy it. retry a few times. In the future, we
            // should make the destroy call block, or possibly specify that we can
            // wait in the createOverlay call if the previous overlay is in the
            // process of being destroyed.
            for (int retry = 0; retry < 50; ++retry) {
#ifdef OMAP_ENHANCEMENT
                mOverlayRef = mSurface->createOverlay(w, h, overlayFormat,
                                      mOrientation, isS3d);
#else
                mOverlayRef = mSurface->createOverlay(w, h,
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP)
                                                      HAL_PIXEL_FORMAT_YCbCr_420_SP,
#elif defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
                                                      HAL_PIXEL_FORMAT_YCrCb_420_SP,
#else
                                                      OVERLAY_FORMAT_DEFAULT,
#endif
                                                      mOrientation);
#endif
                if (mOverlayRef != 0) break;
                LOGW("Overlay create failed - retrying");
                usleep(20000);
            }
            if (mOverlayRef == 0) {
                LOGE("Overlay Creation Failed!");
                return -EINVAL;
            }
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
            mOverlay = new Overlay(mOverlayRef);
            result = mHardware->setOverlay(mOverlay);
#else
            result = mHardware->setOverlay(new Overlay(mOverlayRef));
#endif
        }
    }
    if (result != NO_ERROR) {
        LOGE("mHardware->setOverlay() failed with status %d\n", result);
        return result;
    }

    mOverlayW = w;
    mOverlayH = h;

#ifdef OMAP_ENHANCEMENT
    strncpy(mOverlayFormat, prevFormat, OVERLAY_FORMAT_BUFFER_SIZE);
#endif

    return result;
}

// set the preview callback flag to affect how the received frames from
// preview are handled.
void CameraService::Client::setPreviewCallbackFlag(int callback_flag) {
    LOG1("setPreviewCallbackFlag(%d) (pid %d)", callback_flag, getCallingPid());
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return;

    mPreviewCallbackFlag = callback_flag;

    // If we don't use overlay, we always need the preview frame for display.
    // If we do use overlay, we only need the preview frame if the user
    // wants the data.
    if (mUseOverlay) {
        if(mPreviewCallbackFlag & FRAME_CALLBACK_FLAG_ENABLE_MASK) {
            enableMsgType(CAMERA_MSG_PREVIEW_FRAME);
        } else {
            disableMsgType(CAMERA_MSG_PREVIEW_FRAME);
        }
    }
}

// start preview mode
status_t CameraService::Client::startPreview() {
    LOG1("startPreview (pid %d)", getCallingPid());
    return startCameraMode(CAMERA_PREVIEW_MODE);
}

// start recording mode
status_t CameraService::Client::startRecording() {
    LOG1("startRecording (pid %d)", getCallingPid());
    return startCameraMode(CAMERA_RECORDING_MODE);
}

// start preview or recording
status_t CameraService::Client::startCameraMode(camera_mode mode) {
    LOG1("startCameraMode(%d)", mode);
    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    switch(mode) {
        case CAMERA_PREVIEW_MODE:
            if (mSurface == 0) {
                LOG1("mSurface is not set yet.");
                // still able to start preview in this case.
            }
            return startPreviewMode();
        case CAMERA_RECORDING_MODE:
            if (mSurface == 0) {
                LOGE("mSurface must be set before startRecordingMode.");
                return INVALID_OPERATION;
            }
            return startRecordingMode();
        default:
            return UNKNOWN_ERROR;
    }
}

status_t CameraService::Client::startPreviewMode() {
    LOG1("startPreviewMode");
    status_t result = NO_ERROR;

#ifdef OMAP_ENHANCEMENT

    //According to framework documentation, preview should be
    //restarted after each capture. This will make sure
    //that image capture related messages get disabled if
    //not done already in their respective handlers.
    disableMsgType(CAMERA_MSG_SHUTTER |
                  CAMERA_MSG_POSTVIEW_FRAME |
                  CAMERA_MSG_RAW_IMAGE |
                  CAMERA_MSG_COMPRESSED_IMAGE |
                  CAMERA_MSG_BURST_IMAGE);

#endif

    // if preview has been enabled, nothing needs to be done
    if (mHardware->previewEnabled()) {
        return NO_ERROR;
    }

    if (mUseOverlay) {
        // If preview display has been set, set overlay now.
        if (mSurface != 0) {
            result = setOverlay();
        }
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
        result = mHardware->startPreview();
#endif
        if (result != NO_ERROR) return result;
#if !defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) && !defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
        result = mHardware->startPreview();
#endif
    } else {
        enableMsgType(CAMERA_MSG_PREVIEW_FRAME);
        result = mHardware->startPreview();
        if (result != NO_ERROR) return result;
        // If preview display has been set, register preview buffers now.
        if (mSurface != 0) {
           // Unregister here because the surface may be previously registered
           // with the raw (snapshot) heap.
           mSurface->unregisterBuffers();
           result = registerPreviewBuffers();
        }
    }
    return result;
}

#ifdef USE_GETBUFFERINFO
status_t CameraService::Client::getBufferInfo(sp<IMemory>& Frame, size_t *alignedSize)
{
    LOGD(" getBufferInfo : E");
    if (mHardware == NULL) {
        LOGE("mHardware is NULL, returning.");
        Frame = NULL;
        return INVALID_OPERATION;
    }
    return mHardware->getBufferInfo(Frame, alignedSize);
}
#endif

status_t CameraService::Client::startRecordingMode() {
    LOG1("startRecordingMode");
    status_t result = NO_ERROR;

    // if recording has been enabled, nothing needs to be done
    if (mHardware->recordingEnabled()) {
        return NO_ERROR;
    }

    // if preview has not been started, start preview first
    if (!mHardware->previewEnabled()) {
        result = startPreviewMode();
        if (result != NO_ERROR) {
            return result;
        }
    }

    // start recording mode
    enableMsgType(CAMERA_MSG_VIDEO_FRAME);
    mCameraService->playSound(SOUND_RECORDING);
    result = mHardware->startRecording();
    if (result != NO_ERROR) {
        LOGE("mHardware->startRecording() failed with status %d", result);
    }
    return result;
}

// stop preview mode
void CameraService::Client::stopPreview() {
    LOG1("stopPreview (pid %d)", getCallingPid());
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return;
#ifdef OMAP_ENHANCEMENT

    //According to framework documentation, preview needs
    //to be started for image capture. This will make sure
    //that image capture related messages get disabled if
    //not done already in their respective handlers.
    //If these messages come when in the midddle of
    //stopping preview. We will deadlock the system in
    //lockIfMessageWanted()

    disableMsgType(CAMERA_MSG_SHUTTER |
                  CAMERA_MSG_POSTVIEW_FRAME |
                  CAMERA_MSG_RAW_IMAGE |
                  CAMERA_MSG_COMPRESSED_IMAGE |
                  CAMERA_MSG_BURST_IMAGE);

#endif
    disableMsgType(CAMERA_MSG_PREVIEW_FRAME);
    mHardware->stopPreview();

    if (mSurface != 0 && !mUseOverlay) {
        mSurface->unregisterBuffers();
#if defined(USE_OVERLAY_FORMAT_YCbCr_420_SP) || defined(USE_OVERLAY_FORMAT_YCrCb_420_SP)
    } else {
        mOverlayW = 0;
        mOverlayH = 0;
#endif
    }

    mPreviewBuffer.clear();
}

// stop recording mode
void CameraService::Client::stopRecording() {
    LOG1("stopRecording (pid %d)", getCallingPid());
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return;

    mCameraService->playSound(SOUND_RECORDING);
    disableMsgType(CAMERA_MSG_VIDEO_FRAME);
    mHardware->stopRecording();

    mPreviewBuffer.clear();
}

// release a recording frame
void CameraService::Client::releaseRecordingFrame(const sp<IMemory>& mem) {
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return;
    mHardware->releaseRecordingFrame(mem);
}

bool CameraService::Client::previewEnabled() {
    LOG1("previewEnabled (pid %d)", getCallingPid());

    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return false;
    return mHardware->previewEnabled();
}

bool CameraService::Client::recordingEnabled() {
    LOG1("recordingEnabled (pid %d)", getCallingPid());

    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return false;
    return mHardware->recordingEnabled();
}

status_t CameraService::Client::autoFocus() {
    LOG1("autoFocus (pid %d)", getCallingPid());

    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    return mHardware->autoFocus();
}

status_t CameraService::Client::cancelAutoFocus() {
    LOG1("cancelAutoFocus (pid %d)", getCallingPid());

    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    return mHardware->cancelAutoFocus();
}

// take a picture - image is returned in callback
status_t CameraService::Client::takePicture() {
    LOG1("takePicture (pid %d)", getCallingPid());

    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    enableMsgType(CAMERA_MSG_SHUTTER |
                  CAMERA_MSG_POSTVIEW_FRAME |
                  CAMERA_MSG_RAW_IMAGE |
#ifdef OMAP_ENHANCEMENT
                  CAMERA_MSG_BURST_IMAGE |
#endif
                  CAMERA_MSG_COMPRESSED_IMAGE);

    return mHardware->takePicture();
}

// set preview/capture parameters - key/value pairs
status_t CameraService::Client::setParameters(const String8& params) {
    LOG1("setParameters (pid %d) (%s)", getCallingPid(), params.string());

    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;


    CameraParameters p(params);

#ifdef BOARD_HAS_LGE_FFC
    /* Do not set nvidia focus area to 0 */
    if(p.get("nv-areas-to-focus")!= NULL &&
       !strncmp(p.get("nv-areas-to-focus"),"0",1)) {
        p.remove("nv-areas-to-focus");
    }
#endif

    return mHardware->setParameters(p);
}

// get preview/capture parameters - key/value pairs
String8 CameraService::Client::getParameters() const {
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return String8();

    String8 params(mHardware->getParameters().flatten());
    LOG1("getParameters (pid %d) (%s)", getCallingPid(), params.string());
    return params;
}

#ifdef MOTO_CUSTOM_PARAMETERS
// set preview/capture custom parameters - key/value pairs
status_t CameraService::Client::setCustomParameters(const String8& params) {
    LOG1("setCustomParameters (pid %d) (%s)", getCallingPid(), params.string());

    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;


    CameraParameters p(params);

    return mHardware->setCustomParameters(p);
}

// get preview/capture custom parameters - key/value pairs
String8 CameraService::Client::getCustomParameters() const {
    Mutex::Autolock lock(mLock);
    if (checkPidAndHardware() != NO_ERROR) return String8();

    String8 params(mHardware->getCustomParameters().flatten());
    LOG1("getCustomParameters (pid %d) (%s)", getCallingPid(), params.string());
    return params;
}
#endif

status_t CameraService::Client::sendCommand(int32_t cmd, int32_t arg1, int32_t arg2) {
    LOG1("sendCommand (pid %d)", getCallingPid());
    int orientation;
    Mutex::Autolock lock(mLock);
    status_t result = checkPidAndHardware();
    if (result != NO_ERROR) return result;

    if (cmd == CAMERA_CMD_SET_DISPLAY_ORIENTATION) {
        // The orientation cannot be set during preview.
        if (mHardware->previewEnabled()) {
            return INVALID_OPERATION;
        }
        // Mirror the preview if the camera is front-facing.
        orientation = getOrientation(arg1, mCameraFacing == CAMERA_FACING_FRONT);
        if (orientation == -1) return BAD_VALUE;

        if (mOrientation != orientation) {
            mOrientation = orientation;
            if (mOverlayRef != 0) mOrientationChanged = true;
        }
        return OK;
    }

    return mHardware->sendCommand(cmd, arg1, arg2);
}

// ----------------------------------------------------------------------------

void CameraService::Client::enableMsgType(int32_t msgType) {
    android_atomic_or(msgType, &mMsgEnabled);
    mHardware->enableMsgType(msgType);
}

void CameraService::Client::disableMsgType(int32_t msgType) {
    android_atomic_and(~msgType, &mMsgEnabled);
    mHardware->disableMsgType(msgType);
}

#define CHECK_MESSAGE_INTERVAL 10 // 10ms
bool CameraService::Client::lockIfMessageWanted(int32_t msgType) {
    int sleepCount = 0;
    while (mMsgEnabled & msgType) {
        if (mLock.tryLock() == NO_ERROR) {
            if (sleepCount > 0) {
                LOG1("lockIfMessageWanted(%d): waited for %d ms",
                    msgType, sleepCount * CHECK_MESSAGE_INTERVAL);
            }
            return true;
        }
        if (sleepCount++ == 0) {
            LOG1("lockIfMessageWanted(%d): enter sleep", msgType);
        }
        usleep(CHECK_MESSAGE_INTERVAL * 1000);
#if (defined(TARGET_OMAP3) && defined(OMAP_ENHANCEMENT))
        // Return true after 100ms. We don't want to enter in an infinite loop.
        if (sleepCount == 10) {
            LOGE("lockIfMessageWanted(%d): timed out in %d ms",
                msgType, sleepCount * CHECK_MESSAGE_INTERVAL);
            return true;
        }
#endif
    }
    LOGW("lockIfMessageWanted(%d): dropped unwanted message", msgType);
    return false;
}

// ----------------------------------------------------------------------------

// Converts from a raw pointer to the client to a strong pointer during a
// hardware callback. This requires the callbacks only happen when the client
// is still alive.
sp<CameraService::Client> CameraService::Client::getClientFromCookie(void* user) {
    sp<Client> client = gCameraService->getClientById((int) user);

    // This could happen if the Client is in the process of shutting down (the
    // last strong reference is gone, but the destructor hasn't finished
    // stopping the hardware).
    if (client == 0) return NULL;

    // The checks below are not necessary and are for debugging only.
    if (client->mCameraService.get() != gCameraService) {
        LOGE("mismatch service!");
        return NULL;
    }

    if (client->mHardware == 0) {
        LOGE("mHardware == 0: callback after disconnect()?");
        return NULL;
    }

    return client;
}

// Callback messages can be dispatched to internal handlers or pass to our
// client's callback functions, depending on the message type.
//
// notifyCallback:
//      CAMERA_MSG_SHUTTER              handleShutter
//      (others)                        c->notifyCallback
// dataCallback:
//      CAMERA_MSG_PREVIEW_FRAME        handlePreviewData
//      CAMERA_MSG_POSTVIEW_FRAME       handlePostview
//      CAMERA_MSG_RAW_IMAGE            handleRawPicture
//      CAMERA_MSG_COMPRESSED_IMAGE     handleCompressedPicture
//      (others)                        c->dataCallback
// dataCallbackTimestamp
//      (others)                        c->dataCallbackTimestamp
//
// NOTE: the *Callback functions grab mLock of the client before passing
// control to handle* functions. So the handle* functions must release the
// lock before calling the ICameraClient's callbacks, so those callbacks can
// invoke methods in the Client class again (For example, the preview frame
// callback may want to releaseRecordingFrame). The handle* functions must
// release the lock after all accesses to member variables, so it must be
// handled very carefully.

void CameraService::Client::notifyCallback(int32_t msgType, int32_t ext1,
        int32_t ext2, void* user) {
    LOG2("notifyCallback(%d)", msgType);

    sp<Client> client = getClientFromCookie(user);
    if (client == 0) return;
    if (!client->lockIfMessageWanted(msgType)) return;

    switch (msgType) {
        case CAMERA_MSG_SHUTTER:
            // ext1 is the dimension of the yuv picture.
#ifdef BOARD_USE_CAF_LIBCAMERA
            client->handleShutter((image_rect_type *)ext1, (bool)ext2);
#else
            client->handleShutter((image_rect_type *)ext1);
#endif
            break;
        default:
            client->handleGenericNotify(msgType, ext1, ext2);
            break;
    }
}

void CameraService::Client::dataCallback(int32_t msgType,
        const sp<IMemory>& dataPtr, void* user) {
    LOG2("dataCallback(%d)", msgType);

    sp<Client> client = getClientFromCookie(user);
    if (client == 0) return;
    if (!client->lockIfMessageWanted(msgType)) return;

    if (dataPtr == 0) {
        LOGE("Null data returned in data callback");
        client->handleGenericNotify(CAMERA_MSG_ERROR, UNKNOWN_ERROR, 0);
#if (defined(TARGET_OMAP3) && defined(OMAP_ENHANCEMENT))
        //Handle the NULL data returned in RawCallback from lower layers in OMAP3
        client->handleGenericData(msgType, NULL);
#endif
        return;
    }

    switch (msgType) {
        case CAMERA_MSG_PREVIEW_FRAME:
            client->handlePreviewData(dataPtr);
            break;
        case CAMERA_MSG_POSTVIEW_FRAME:
            client->handlePostview(dataPtr);
            break;
        case CAMERA_MSG_RAW_IMAGE:
            client->handleRawPicture(dataPtr);
            break;
        case CAMERA_MSG_COMPRESSED_IMAGE:
            client->handleCompressedPicture(dataPtr);
            break;

#ifdef OMAP_ENHANCEMENT

        case CAMERA_MSG_BURST_IMAGE:
            client->handleBurstPicture(dataPtr);
            break;

#endif

        default:
            client->handleGenericData(msgType, dataPtr);
            break;
    }
}
#ifdef OMAP_ENHANCEMENT
void CameraService::Client::dataCallbackTimestamp(nsecs_t timestamp, int32_t msgType,
        const sp<IMemory>& dataPtr, void* user, uint32_t offset, uint32_t stride)
#else
void CameraService::Client::dataCallbackTimestamp(nsecs_t timestamp,
        int32_t msgType, const sp<IMemory>& dataPtr, void* user)
#endif
{
    LOG2("dataCallbackTimestamp(%d)", msgType);

    sp<Client> client = getClientFromCookie(user);
    if (client == 0) return;
    if (!client->lockIfMessageWanted(msgType)) return;

    if (dataPtr == 0) {
        LOGE("Null data returned in data with timestamp callback");
        client->handleGenericNotify(CAMERA_MSG_ERROR, UNKNOWN_ERROR, 0);
        return;
    }
#ifdef OMAP_ENHANCEMENT
    client->handleGenericDataTimestamp(timestamp, msgType, dataPtr, offset, stride);
#else
    client->handleGenericDataTimestamp(timestamp, msgType, dataPtr);
#endif
}

// snapshot taken callback
// "size" is the width and height of yuv picture for registerBuffer.
// If it is NULL, use the picture size from parameters.
void CameraService::Client::handleShutter(image_rect_type *size
#ifdef BOARD_USE_CAF_LIBCAMERA
    , bool playShutterSoundOnly
#endif
) {

#ifdef BOARD_USE_CAF_LIBCAMERA
    if(playShutterSoundOnly) {
#endif
    mCameraService->playSound(SOUND_SHUTTER);
#ifdef BOARD_USE_CAF_LIBCAMERA
    sp<ICameraClient> c = mCameraClient;
    if (c != 0) {
        mLock.unlock();
        c->notifyCallback(CAMERA_MSG_SHUTTER, 0, 0);
    }
    return;
    }
#endif

    // Screen goes black after the buffer is unregistered.
    if (mSurface != 0 && !mUseOverlay) {
        mSurface->unregisterBuffers();
    }

    sp<ICameraClient> c = mCameraClient;
    if (c != 0) {
        mLock.unlock();
        c->notifyCallback(CAMERA_MSG_SHUTTER, 0, 0);
        if (!lockIfMessageWanted(CAMERA_MSG_SHUTTER)) return;
    }
    disableMsgType(CAMERA_MSG_SHUTTER);

    // It takes some time before yuvPicture callback to be called.
    // Register the buffer for raw image here to reduce latency.
    if (mSurface != 0 && !mUseOverlay) {
        int w, h;
        CameraParameters params(mHardware->getParameters());
        if (size == NULL) {
            params.getPictureSize(&w, &h);
        } else {
            w = size->width;
            h = size->height;
            w &= ~1;
            h &= ~1;
            LOG1("Snapshot image width=%d, height=%d", w, h);
        }
        // FIXME: don't use hardcoded format constants here
        ISurface::BufferHeap buffers(w, h, w, h,
            HAL_PIXEL_FORMAT_YCrCb_420_SP, mOrientation, 0,
            mHardware->getRawHeap());

        mSurface->registerBuffers(buffers);
        IPCThreadState::self()->flushCommands();
    }

    mLock.unlock();
}

// preview callback - frame buffer update
void CameraService::Client::handlePreviewData(const sp<IMemory>& mem) {
    ssize_t offset;
    size_t size;
    sp<IMemoryHeap> heap = mem->getMemory(&offset, &size);

    if (!mUseOverlay) {
        if (mSurface != 0) {
            mSurface->postBuffer(offset);
        }
    }

    // local copy of the callback flags
    int flags = mPreviewCallbackFlag;

    // is callback enabled?
    if (!(flags & FRAME_CALLBACK_FLAG_ENABLE_MASK)) {
        // If the enable bit is off, the copy-out and one-shot bits are ignored
        LOG2("frame callback is disabled");
        mLock.unlock();
        return;
    }

    // hold a strong pointer to the client
    sp<ICameraClient> c = mCameraClient;

    // clear callback flags if no client or one-shot mode
    if (c == 0 || (mPreviewCallbackFlag & FRAME_CALLBACK_FLAG_ONE_SHOT_MASK)) {
        LOG2("Disable preview callback");
        mPreviewCallbackFlag &= ~(FRAME_CALLBACK_FLAG_ONE_SHOT_MASK |
                                  FRAME_CALLBACK_FLAG_COPY_OUT_MASK |
                                  FRAME_CALLBACK_FLAG_ENABLE_MASK);
        if (mUseOverlay) {
            disableMsgType(CAMERA_MSG_PREVIEW_FRAME);
        }
    }

    if (c != 0) {
        // Is the received frame copied out or not?
        if (flags & FRAME_CALLBACK_FLAG_COPY_OUT_MASK) {
            LOG2("frame is copied");
            copyFrameAndPostCopiedFrame(c, heap, offset, size);
        } else {
            LOG2("frame is forwarded");
            mLock.unlock();
            c->dataCallback(CAMERA_MSG_PREVIEW_FRAME, mem);
        }
    } else {
        mLock.unlock();
    }
}

// picture callback - postview image ready
void CameraService::Client::handlePostview(const sp<IMemory>& mem) {
    disableMsgType(CAMERA_MSG_POSTVIEW_FRAME);

    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->dataCallback(CAMERA_MSG_POSTVIEW_FRAME, mem);
    }
}

// picture callback - raw image ready
void CameraService::Client::handleRawPicture(const sp<IMemory>& mem) {
    disableMsgType(CAMERA_MSG_RAW_IMAGE);

    ssize_t offset;
    size_t size;
    sp<IMemoryHeap> heap = mem->getMemory(&offset, &size);

    // Put the YUV version of the snapshot in the preview display.
    if (mSurface != 0 && !mUseOverlay) {
        mSurface->postBuffer(offset);
    }

    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->dataCallback(CAMERA_MSG_RAW_IMAGE, mem);
    }
}

// picture callback - compressed picture ready
void CameraService::Client::handleCompressedPicture(const sp<IMemory>& mem) {
    disableMsgType(CAMERA_MSG_COMPRESSED_IMAGE);

    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->dataCallback(CAMERA_MSG_COMPRESSED_IMAGE, mem);
    }
}

#ifdef OMAP_ENHANCEMENT

// burst callback
void CameraService::Client::handleBurstPicture(const sp<IMemory>& mem) {
    //Don't disable this message type yet. In this mode takePicture() will
    //get called only once. When burst finishes this message will get automatically
    //disabled in the respective call for restarting the preview.

    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->dataCallback(CAMERA_MSG_COMPRESSED_IMAGE, mem);
    }
}

#endif

void CameraService::Client::handleGenericNotify(int32_t msgType,
    int32_t ext1, int32_t ext2) {
    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->notifyCallback(msgType, ext1, ext2);
    }
}

void CameraService::Client::handleGenericData(int32_t msgType,
    const sp<IMemory>& dataPtr) {
    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
        c->dataCallback(msgType, dataPtr);
    }
}
#ifdef OMAP_ENHANCEMENT
void CameraService::Client::handleGenericDataTimestamp(nsecs_t timestamp,
    int32_t msgType, const sp<IMemory>& dataPtr, uint32_t offset, uint32_t stride)
#else
void CameraService::Client::handleGenericDataTimestamp(nsecs_t timestamp,
    int32_t msgType, const sp<IMemory>& dataPtr)
#endif
{
    sp<ICameraClient> c = mCameraClient;
    mLock.unlock();
    if (c != 0) {
#ifdef OMAP_ENHANCEMENT
        c->dataCallbackTimestamp(timestamp, msgType, dataPtr, offset, stride);
#else
        c->dataCallbackTimestamp(timestamp, msgType, dataPtr);
#endif
    }
}

void CameraService::Client::copyFrameAndPostCopiedFrame(
        const sp<ICameraClient>& client, const sp<IMemoryHeap>& heap,
        size_t offset, size_t size) {
    LOG2("copyFrameAndPostCopiedFrame");
    // It is necessary to copy out of pmem before sending this to
    // the callback. For efficiency, reuse the same MemoryHeapBase
    // provided it's big enough. Don't allocate the memory or
    // perform the copy if there's no callback.
    // hold the preview lock while we grab a reference to the preview buffer
    sp<MemoryHeapBase> previewBuffer;

    if (mPreviewBuffer == 0) {
        mPreviewBuffer = new MemoryHeapBase(size, 0, NULL);
    } else if (size > mPreviewBuffer->virtualSize()) {
        mPreviewBuffer.clear();
        mPreviewBuffer = new MemoryHeapBase(size, 0, NULL);
    }
    if (mPreviewBuffer == 0) {
        LOGE("failed to allocate space for preview buffer");
        mLock.unlock();
        return;
    }
    previewBuffer = mPreviewBuffer;

    memcpy(previewBuffer->base(), (uint8_t *)heap->base() + offset, size);

    sp<MemoryBase> frame = new MemoryBase(previewBuffer, 0, size);
    if (frame == 0) {
        LOGE("failed to allocate space for frame callback");
        mLock.unlock();
        return;
    }

    mLock.unlock();
    client->dataCallback(CAMERA_MSG_PREVIEW_FRAME, frame);
}

int CameraService::Client::getOrientation(int degrees, bool mirror) {
#ifdef BOARD_HAS_LGE_FFC
    /* FLIP_* generate weird behaviors that don't include flipping */
    LOGV("Asking orientation %d with %d",degrees,mirror);
    if (mirror && 
          degrees == 270 || degrees == 90)  // ROTATE_90 just for these orientations
            return HAL_TRANSFORM_ROT_90;
    mirror = 0;
#endif
    if (!mirror) {
        if (degrees == 0) return 0;
        else if (degrees == 90) return HAL_TRANSFORM_ROT_90;
        else if (degrees == 180) return HAL_TRANSFORM_ROT_180;
        else if (degrees == 270) return HAL_TRANSFORM_ROT_270;
    } else {  // Do mirror (horizontal flip)
        if (degrees == 0) {           // FLIP_H and ROT_0
            return HAL_TRANSFORM_FLIP_H;
        } else if (degrees == 90) {   // FLIP_H and ROT_90
            return HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_ROT_90;
        } else if (degrees == 180) {  // FLIP_H and ROT_180
            return HAL_TRANSFORM_FLIP_V;
        } else if (degrees == 270) {  // FLIP_H and ROT_270
            return HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_ROT_90;
        }
    }
    LOGE("Invalid setDisplayOrientation degrees=%d", degrees);
    return -1;
}


// ----------------------------------------------------------------------------

static const int kDumpLockRetries = 50;
static const int kDumpLockSleep = 60000;

static bool tryLock(Mutex& mutex)
{
    bool locked = false;
    for (int i = 0; i < kDumpLockRetries; ++i) {
        if (mutex.tryLock() == NO_ERROR) {
            locked = true;
            break;
        }
        usleep(kDumpLockSleep);
    }
    return locked;
}

status_t CameraService::dump(int fd, const Vector<String16>& args) {
    static const char* kDeadlockedString = "CameraService may be deadlocked\n";

    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    if (checkCallingPermission(String16("android.permission.DUMP")) == false) {
        snprintf(buffer, SIZE, "Permission Denial: "
                "can't dump CameraService from pid=%d, uid=%d\n",
                getCallingPid(),
                getCallingUid());
        result.append(buffer);
        write(fd, result.string(), result.size());
    } else {
        bool locked = tryLock(mServiceLock);
        // failed to lock - CameraService is probably deadlocked
        if (!locked) {
            String8 result(kDeadlockedString);
            write(fd, result.string(), result.size());
        }

        bool hasClient = false;
        for (int i = 0; i < mNumberOfCameras; i++) {
            sp<Client> client = mClient[i].promote();
            if (client == 0) continue;
            hasClient = true;
            sprintf(buffer, "Client[%d] (%p) PID: %d\n",
                    i,
                    client->getCameraClient()->asBinder().get(),
                    client->mClientPid);
            result.append(buffer);
            write(fd, result.string(), result.size());
            client->mHardware->dump(fd, args);
        }
        if (!hasClient) {
            result.append("No camera client yet.\n");
            write(fd, result.string(), result.size());
        }

        if (locked) mServiceLock.unlock();

        // change logging level
        int n = args.size();
        for (int i = 0; i + 1 < n; i++) {
            if (args[i] == String16("-v")) {
                String8 levelStr(args[i+1]);
                int level = atoi(levelStr.string());
                sprintf(buffer, "Set Log Level to %d", level);
                result.append(buffer);
                setLogLevel(level);
            }
        }
    }
    return NO_ERROR;
}

#ifdef BOARD_USE_FROYO_LIBCAMERA
static int getNumberOfCameras() {
    if (access(HTC_SWITCH_CAMERA_FILE_PATH, W_OK) == 0) {
        return 2;
    }
    /* FIXME: Support non-HTC front camera */
    return 1;
}

extern "C" int HAL_getNumberOfCameras()
{
    return getNumberOfCameras();
}

extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo* cameraInfo)
{
    memcpy(cameraInfo, &sCameraInfo[cameraId], sizeof(CameraInfo));
}

extern "C" sp<CameraHardwareInterface> openCameraHardware(int cameraId);

extern "C" sp<CameraHardwareInterface> HAL_openCameraHardware(int cameraId)
{
    LOGV("openCameraHardware: call createInstance");
    return openCameraHardware(cameraId);
}
#endif
#ifdef OMAP3_FW3A_LIBCAMERA
static const CameraInfo sCameraInfo[] = {
    {
        CAMERA_FACING_BACK,
        90,  /* orientation */
    },
#ifdef OMAP3_SECONDARY_CAMERA
    {
        CAMERA_FACING_FRONT,
        270, /* orientation */
    }
#endif
};
static int getNumberOfCameras() {
    return sizeof(sCameraInfo) / sizeof(sCameraInfo[0]);
}

extern "C" int HAL_getNumberOfCameras()
{
    return getNumberOfCameras();
}

extern "C" void HAL_getCameraInfo(int cameraId, struct CameraInfo* cameraInfo)
{
    memcpy(cameraInfo, &sCameraInfo[cameraId], sizeof(CameraInfo));
}
#endif
}; // namespace android
