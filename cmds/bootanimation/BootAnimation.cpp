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

#define LOG_NDEBUG 0
#define LOG_TAG "BootAnimation"

#include <stdint.h>
#include <sys/inotify.h>
#include <sys/poll.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <math.h>
#include <fcntl.h>
#include <utils/misc.h>
#include <signal.h>
#include <time.h>
#include <sys/syscall.h>

#include <cutils/properties.h>

#include <androidfw/AssetManager.h>
#include <binder/IPCThreadState.h>
#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/Log.h>

#include <ui/PixelFormat.h>
#include <ui/Rect.h>
#include <ui/Region.h>
#include <ui/DisplayInfo.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

// TODO: Fix Skia.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#include <SkBitmap.h>
#include <SkStream.h>
#include <SkImageDecoder.h>
#pragma GCC diagnostic pop

#include <GLES/gl.h>
#include <GLES/glext.h>
#include <EGL/eglext.h>

#include "BootAnimation.h"
#include "audioplay.h"

#include <private/regionalization/Environment.h>

namespace android {

static const char OEM_BOOTANIMATION_FILE[] = "/oem/media/bootanimation.zip";
static const char SYSTEM_BOOTANIMATION_FILE[] = "/system/media/bootanimation.zip";
static const char SYSTEM_ENCRYPTED_BOOTANIMATION_FILE[] = "/system/media/bootanimation-encrypted.zip";
static const char THEME_BOOTANIMATION_FILE[] = "/data/system/theme/bootanimation.zip";
static const char SYSTEM_DATA_DIR_PATH[] = "/data/system";
static const char SYSTEM_TIME_DIR_NAME[] = "time";
static const char SYSTEM_TIME_DIR_PATH[] = "/data/system/time";
static const char LAST_TIME_CHANGED_FILE_NAME[] = "last_time_change";
static const char LAST_TIME_CHANGED_FILE_PATH[] = "/data/system/time/last_time_change";
static const char ACCURATE_TIME_FLAG_FILE_NAME[] = "time_is_accurate";
static const char ACCURATE_TIME_FLAG_FILE_PATH[] = "/data/system/time/time_is_accurate";
// Java timestamp format. Don't show the clock if the date is before 2000-01-01 00:00:00.
static const long long ACCURATE_TIME_EPOCH = 946684800000;
static const char EXIT_PROP_NAME[] = "service.bootanim.exit";
static const char PLAY_SOUND_PROP_NAME[] = "persist.sys.bootanim.play_sound";
static const int ANIM_ENTRY_NAME_MAX = 256;
static const char BOOT_COMPLETED_PROP_NAME[] = "sys.boot_completed";
static const char BOOTREASON_PROP_NAME[] = "ro.boot.bootreason";
// bootreasons list in "system/core/bootstat/bootstat.cpp".
static const std::vector<std::string> PLAY_SOUND_BOOTREASON_BLACKLIST {
  "kernel_panic",
  "Panic",
  "Watchdog",
};

// ---------------------------------------------------------------------------

#ifdef MULTITHREAD_DECODE
static const int MAX_DECODE_THREADS = 2;
static const int MAX_DECODE_CACHE = 3;
#endif

static unsigned long getFreeMemory(void)
{
    int fd = open("/proc/meminfo", O_RDONLY);
    const char* const sums[] = { "MemFree:", "Cached:", NULL };
    const size_t sumsLen[] = { strlen("MemFree:"), strlen("Cached:"), 0 };
    unsigned int num = 2;

    if (fd < 0) {
        ALOGW("Unable to open /proc/meminfo");
        return -1;
    }

    char buffer[256];
    const int len = read(fd, buffer, sizeof(buffer)-1);
    close(fd);

    if (len < 0) {
        ALOGW("Unable to read /proc/meminfo");
        return -1;
    }
    buffer[len] = 0;

    size_t numFound = 0;
    unsigned long mem = 0;

    char* p = buffer;
    while (*p && numFound < num) {
        int i = 0;
        while (sums[i]) {
            if (strncmp(p, sums[i], sumsLen[i]) == 0) {
                p += sumsLen[i];
                while (*p == ' ') p++;
                char* num = p;
                while (*p >= '0' && *p <= '9') p++;
                if (*p != 0) {
                    *p = 0;
                    p++;
                    if (*p == 0) p--;
                }
                mem += atoll(num);
                numFound++;
                break;
            }
            i++;
        }
        p++;
    }

    return numFound > 0 ? mem : -1;
}

BootAnimation::BootAnimation() : Thread(false), mClockEnabled(true), mTimeIsAccurate(false),
        mTimeCheckThread(NULL) {
    mSession = new SurfaceComposerClient();

    // If the system has already booted, the animation is not being used for a boot.
    mSystemBoot = !property_get_bool(BOOT_COMPLETED_PROP_NAME, 0);
}

BootAnimation::~BootAnimation() {}

void BootAnimation::onFirstRef() {
    status_t err = mSession->linkToComposerDeath(this);
    ALOGE_IF(err, "linkToComposerDeath failed (%s) ", strerror(-err));
    if (err == NO_ERROR) {
        run("BootAnimation", PRIORITY_DISPLAY);
    }
}

sp<SurfaceComposerClient> BootAnimation::session() const {
    return mSession;
}


void BootAnimation::binderDied(const wp<IBinder>&)
{
    // woah, surfaceflinger died!
    ALOGD("SurfaceFlinger died, exiting...");

    // calling requestExit() is not enough here because the Surface code
    // might be blocked on a condition variable that will never be updated.
    kill( getpid(), SIGKILL );
    requestExit();
    audioplay::destroy();
}

status_t BootAnimation::initTexture(Texture* texture, AssetManager& assets,
        const char* name) {
    Asset* asset = assets.open(name, Asset::ACCESS_BUFFER);
    if (asset == NULL)
        return NO_INIT;
    SkBitmap bitmap;
    SkImageDecoder::DecodeMemory(asset->getBuffer(false), asset->getLength(),
            &bitmap, kUnknown_SkColorType, SkImageDecoder::kDecodePixels_Mode);
    asset->close();
    delete asset;

    // ensure we can call getPixels(). No need to call unlock, since the
    // bitmap will go out of scope when we return from this method.
    bitmap.lockPixels();

    const int w = bitmap.width();
    const int h = bitmap.height();
    const void* p = bitmap.getPixels();

    GLint crop[4] = { 0, h, w, -h };
    texture->w = w;
    texture->h = h;

    glGenTextures(1, &texture->name);
    glBindTexture(GL_TEXTURE_2D, texture->name);

    switch (bitmap.colorType()) {
        case kAlpha_8_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, w, h, 0, GL_ALPHA,
                    GL_UNSIGNED_BYTE, p);
            break;
        case kARGB_4444_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_SHORT_4_4_4_4, p);
            break;
        case kN32_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, p);
            break;
        case kRGB_565_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                    GL_UNSIGNED_SHORT_5_6_5, p);
            break;
        default:
            break;
    }

    glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    return NO_ERROR;
}

SkBitmap* BootAnimation::decode(const Animation::Frame& frame)
{

    SkBitmap *bitmap = NULL;
    SkMemoryStream  stream(frame.map->getDataPtr(), frame.map->getDataLength());
    SkImageDecoder* codec = SkImageDecoder::Factory(&stream);
    if (codec != NULL) {
        bitmap = new SkBitmap();
        codec->setDitherImage(false);
        codec->decode(&stream, bitmap,
                #ifdef USE_565
                kRGB_565_SkColorType,
                #else
                kN32_SkColorType,
                #endif
                SkImageDecoder::kDecodePixels_Mode);
        delete codec;
    }

    return bitmap;
}

status_t BootAnimation::initTexture(const Animation::Frame& frame)
{
    //StopWatch watch("blah");
    return initTexture(decode(frame));
}

status_t BootAnimation::initTexture(SkBitmap *bitmap)
{
    // ensure we can call getPixels().
    bitmap->lockPixels();

    const int w = bitmap->width();
    const int h = bitmap->height();
    const void* p = bitmap->getPixels();

    GLint crop[4] = { 0, h, w, -h };
    int tw = 1 << (31 - __builtin_clz(w));
    int th = 1 << (31 - __builtin_clz(h));
    if (tw < w) tw <<= 1;
    if (th < h) th <<= 1;

    switch (bitmap->colorType()) {
        case kN32_SkColorType:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, 0);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, p);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, p);
            }
            break;

        case kRGB_565_SkColorType:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, tw, th, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, 0);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, p);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, p);
            }
            break;
        default:
            break;
    }

    glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);

    bitmap->unlockPixels();
    delete bitmap;
    return NO_ERROR;
}


// Get bootup Animation File
// Parameter: ImageID: IMG_OEM IMG_SYS IMG_ENC IMG_THM
// Return Value : File path
const char *BootAnimation::getAnimationFileName(ImageID image)
{
    const char *fileName[4] = { OEM_BOOTANIMATION_FILE,
            SYSTEM_BOOTANIMATION_FILE,
            SYSTEM_ENCRYPTED_BOOTANIMATION_FILE,
            THEME_BOOTANIMATION_FILE};

    // Load animations of Carrier through regionalization environment
    if (Environment::isSupported()) {
        Environment* environment = new Environment();
        const char* animFile = environment->getMediaFile(
                Environment::ANIMATION_TYPE, Environment::BOOT_STATUS);
        ALOGE("Get Carrier Animation type: %d,status:%d", Environment::ANIMATION_TYPE,Environment::BOOT_STATUS);
        if (animFile != NULL && strcmp(animFile, "") != 0) {
           return animFile;
        }else{
           ALOGD("Get Carrier Animation file: %s failed", animFile);
        }
        delete environment;
    }else{
           ALOGE("Get Carrier Animation file,since it's not support carrier");
    }

    return fileName[image];
}

status_t BootAnimation::readyToRun() {
    mAssets.addDefaultAssets();

    sp<IBinder> dtoken(SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain));
    DisplayInfo dinfo;
    status_t status = SurfaceComposerClient::getDisplayInfo(dtoken, &dinfo);
    if (status)
        return -1;

    // create the native surface
    sp<SurfaceControl> control = session()->createSurface(String8("BootAnimation"),
            dinfo.w, dinfo.h, PIXEL_FORMAT_RGB_565);

    SurfaceComposerClient::openGlobalTransaction();
    control->setLayer(0x40000000);
    SurfaceComposerClient::closeGlobalTransaction();

    sp<Surface> s = control->getSurface();

    // initialize opengl and egl
    const EGLint attribs[] = {
            EGL_RED_SIZE,   8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE,  8,
            EGL_DEPTH_SIZE, 0,
            EGL_NONE
    };
    EGLint w, h;
    EGLint numConfigs;
    EGLConfig config;
    EGLSurface surface;
    EGLContext context;

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);

    eglInitialize(display, 0, 0);
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);
    surface = eglCreateWindowSurface(display, config, s.get(), NULL);
    context = eglCreateContext(display, config, NULL, NULL);
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);

    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE)
        return NO_INIT;

    mDisplay = display;
    mContext = context;
    mSurface = surface;
    mWidth = w;
    mHeight = h;
    mFlingerSurfaceControl = control;
    mFlingerSurface = s;

    // If the device has encryption turned on or is in process
    // of being encrypted we show the encrypted boot animation.
    char decrypt[PROPERTY_VALUE_MAX];
    property_get("vold.decrypt", decrypt, "");

    bool encryptedAnimation = atoi(decrypt) != 0 || !strcmp("trigger_restart_min_framework", decrypt);

    if (encryptedAnimation && (access(getAnimationFileName(IMG_ENC), R_OK) == 0)) {
        mZipFileName = getAnimationFileName(IMG_ENC);
    }
    else if (access(getAnimationFileName(IMG_THM), R_OK) == 0) {
        mZipFileName = getAnimationFileName(IMG_THM);
    }
    else if (access(getAnimationFileName(IMG_OEM), R_OK) == 0) {
        mZipFileName = getAnimationFileName(IMG_OEM);
    }
    else if (access(getAnimationFileName(IMG_SYS), R_OK) == 0) {
        mZipFileName = getAnimationFileName(IMG_SYS);
    }

#ifdef PRELOAD_BOOTANIMATION
    // Preload the bootanimation zip on memory, so we don't stutter
    // when showing the animation
    FILE* fd;
    if (encryptedAnimation && access(getAnimationFileName(IMG_ENC), R_OK) == 0)
        fd = fopen(getAnimationFileName(IMG_ENC), "r");
    else if (access(getAnimationFileName(IMG_OEM), R_OK) == 0)
        fd = fopen(getAnimationFileName(IMG_OEM), "r");
    else if (access(getAnimationFileName(IMG_THM), R_OK) == 0)
        fd = fopen(getAnimationFileName(IMG_THM), "r");
    else if (access(getAnimationFileName(IMG_SYS), R_OK) == 0)
        fd = fopen(getAnimationFileName(IMG_SYS), "r");
    else
        return NO_ERROR;

    if (fd != NULL) {
        // Since including fcntl.h doesn't give us the wrapper, use the syscall.
        // 32 bits takes LO/HI offset (we don't care about endianness of 0).
#if defined(__aarch64__) || defined(__x86_64__)
        if (syscall(__NR_readahead, fd, 0, INT_MAX))
#else
        if (syscall(__NR_readahead, fd, 0, 0, INT_MAX))
#endif
            ALOGW("Unable to cache the animation");
        fclose(fd);
    }
#endif

    return NO_ERROR;
}

bool BootAnimation::threadLoop()
{
    bool r;
    // We have no bootanimation file, so we use the stock android logo
    // animation.
    if (mZipFileName.isEmpty()) {
        r = android();
    } else {
        r = movie();
    }

    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mDisplay, mContext);
    eglDestroySurface(mDisplay, mSurface);
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    eglTerminate(mDisplay);
    IPCThreadState::self()->stopProcess();
    return r;
}

bool BootAnimation::android()
{
    initTexture(&mAndroid[0], mAssets, "images/android-logo-mask.png");
    initTexture(&mAndroid[1], mAssets, "images/android-logo-shine.png");

    // clear screen
    glShadeModel(GL_FLAT);
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(0,0,0,1);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(mDisplay, mSurface);

    glEnable(GL_TEXTURE_2D);
    glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);

    const GLint xc = (mWidth  - mAndroid[0].w) / 2;
    const GLint yc = (mHeight - mAndroid[0].h) / 2;
    const Rect updateRect(xc, yc, xc + mAndroid[0].w, yc + mAndroid[0].h);

    glScissor(updateRect.left, mHeight - updateRect.bottom, updateRect.width(),
            updateRect.height());

    // Blend state
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);

    const nsecs_t startTime = systemTime();
    do {
        nsecs_t now = systemTime();
        double time = now - startTime;
        float t = 4.0f * float(time / us2ns(16667)) / mAndroid[1].w;
        GLint offset = (1 - (t - floorf(t))) * mAndroid[1].w;
        GLint x = xc - offset;

        glDisable(GL_SCISSOR_TEST);
        glClear(GL_COLOR_BUFFER_BIT);

        glEnable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glBindTexture(GL_TEXTURE_2D, mAndroid[1].name);
        glDrawTexiOES(x,                 yc, 0, mAndroid[1].w, mAndroid[1].h);
        glDrawTexiOES(x + mAndroid[1].w, yc, 0, mAndroid[1].w, mAndroid[1].h);

        glEnable(GL_BLEND);
        glBindTexture(GL_TEXTURE_2D, mAndroid[0].name);
        glDrawTexiOES(xc, yc, 0, mAndroid[0].w, mAndroid[0].h);

        EGLBoolean res = eglSwapBuffers(mDisplay, mSurface);
        if (res == EGL_FALSE)
            break;

        // 12fps: don't animate too fast to preserve CPU
        const nsecs_t sleepTime = 83333 - ns2us(systemTime() - now);
        if (sleepTime > 0)
            usleep(sleepTime);

        checkExit();
    } while (!exitPending());

    glDeleteTextures(1, &mAndroid[0].name);
    glDeleteTextures(1, &mAndroid[1].name);
    return false;
}


void BootAnimation::checkExit() {
    // Allow surface flinger to gracefully request shutdown
    char value[PROPERTY_VALUE_MAX];
    property_get(EXIT_PROP_NAME, value, "0");
    int exitnow = atoi(value);
    if (exitnow) {
        requestExit();
    }
}

// Parse a color represented as an HTML-style 'RRGGBB' string: each pair of
// characters in str is a hex number in [0, 255], which are converted to
// floating point values in the range [0.0, 1.0] and placed in the
// corresponding elements of color.
//
// If the input string isn't valid, parseColor returns false and color is
// left unchanged.
static bool parseColor(const char str[7], float color[3]) {
    float tmpColor[3];
    for (int i = 0; i < 3; i++) {
        int val = 0;
        for (int j = 0; j < 2; j++) {
            val *= 16;
            char c = str[2*i + j];
            if      (c >= '0' && c <= '9') val += c - '0';
            else if (c >= 'A' && c <= 'F') val += (c - 'A') + 10;
            else if (c >= 'a' && c <= 'f') val += (c - 'a') + 10;
            else                           return false;
        }
        tmpColor[i] = static_cast<float>(val) / 255.0f;
    }
    memcpy(color, tmpColor, sizeof(tmpColor));
    return true;
}


static bool readFile(ZipFileRO* zip, const char* name, String8& outString)
{
    ZipEntryRO entry = zip->findEntryByName(name);
    ALOGE_IF(!entry, "couldn't find %s", name);
    if (!entry) {
        return false;
    }

    FileMap* entryMap = zip->createEntryFileMap(entry);
    zip->releaseEntry(entry);
    ALOGE_IF(!entryMap, "entryMap is null");
    if (!entryMap) {
        return false;
    }

    outString.setTo((char const*)entryMap->getDataPtr(), entryMap->getDataLength());
    delete entryMap;
    return true;
}

// The time glyphs are stored in a single image of height 64 pixels. Each digit is 40 pixels wide,
// and the colon character is half that at 20 pixels. The glyph order is '0123456789:'.
// We render 24 hour time.
void BootAnimation::drawTime(const Texture& clockTex, const int yPos) {
    static constexpr char TIME_FORMAT[] = "%H:%M";
    static constexpr int TIME_LENGTH = sizeof(TIME_FORMAT);

    static constexpr int DIGIT_HEIGHT = 64;
    static constexpr int DIGIT_WIDTH = 40;
    static constexpr int COLON_WIDTH = DIGIT_WIDTH / 2;
    static constexpr int TIME_WIDTH = (DIGIT_WIDTH * 4) + COLON_WIDTH;

    if (clockTex.h < DIGIT_HEIGHT || clockTex.w < (10 * DIGIT_WIDTH + COLON_WIDTH)) {
        ALOGE("Clock texture is too small; abandoning boot animation clock");
        mClockEnabled = false;
        return;
    }

    time_t rawtime;
    time(&rawtime);
    struct tm* timeInfo = localtime(&rawtime);

    char timeBuff[TIME_LENGTH];
    size_t length = strftime(timeBuff, TIME_LENGTH, TIME_FORMAT, timeInfo);

    if (length != TIME_LENGTH - 1) {
        ALOGE("Couldn't format time; abandoning boot animation clock");
        mClockEnabled = false;
        return;
    }

    glEnable(GL_BLEND);  // Allow us to draw on top of the animation
    glBindTexture(GL_TEXTURE_2D, clockTex.name);

    int xPos = (mWidth - TIME_WIDTH) / 2;
    int cropRect[4] = { 0, DIGIT_HEIGHT, DIGIT_WIDTH, -DIGIT_HEIGHT };

    for (int i = 0; i < TIME_LENGTH - 1; i++) {
        char c = timeBuff[i];
        int width = DIGIT_WIDTH;
        int pos = c - '0';  // Position in the character list
        if (pos < 0 || pos > 10) {
            continue;
        }
        if (c == ':') {
            width = COLON_WIDTH;
        }

        // Crop the texture to only the pixels in the current glyph
        int left = pos * DIGIT_WIDTH;
        cropRect[0] = left;
        cropRect[2] = width;
        glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, cropRect);

        glDrawTexiOES(xPos, yPos, 0, width, DIGIT_HEIGHT);

        xPos += width;
    }

    glDisable(GL_BLEND);  // Return to the animation's default behaviour
    glBindTexture(GL_TEXTURE_2D, 0);
}

bool BootAnimation::parseAnimationDesc(Animation& animation)
{
    String8 desString;

    if (!readFile(animation.zip, "desc.txt", desString)) {
        return false;
    }
    char const* s = desString.string();

    // Parse the description file
    for (;;) {
        const char* endl = strstr(s, "\n");
        if (endl == NULL) break;
        String8 line(s, endl - s);
        const char* l = line.string();
        int fps = 0;
        int width = 0;
        int height = 0;
        int count = 0;
        int pause = 0;
        int clockPosY = -1;
        char path[ANIM_ENTRY_NAME_MAX];
        char color[7] = "000000"; // default to black if unspecified

        char pathType;
        if (sscanf(l, "%d %d %d", &width, &height, &fps) == 3) {
            // ALOGD("> w=%d, h=%d, fps=%d", width, height, fps);
            animation.width = width;
            animation.height = height;
            animation.fps = fps;
        } else if (sscanf(l, " %c %d %d %s #%6s %d",
                          &pathType, &count, &pause, path, color, &clockPosY) >= 4) {
            // ALOGD("> type=%c, count=%d, pause=%d, path=%s, color=%s, clockPosY=%d", pathType, count, pause, path, color, clockPosY);
            Animation::Part part;
            part.playUntilComplete = pathType == 'c';
            part.count = count;
            part.pause = pause;
            part.path = path;
            part.clockPosY = clockPosY;
            part.audioData = NULL;
            part.animation = NULL;
            if (!parseColor(color, part.backgroundColor)) {
                ALOGE("> invalid color '#%s'", color);
                part.backgroundColor[0] = 0.0f;
                part.backgroundColor[1] = 0.0f;
                part.backgroundColor[2] = 0.0f;
            }
            animation.parts.add(part);
        }
        else if (strcmp(l, "$SYSTEM") == 0) {
            // ALOGD("> SYSTEM");
            Animation::Part part;
            part.playUntilComplete = false;
            part.count = 1;
            part.pause = 0;
            part.audioData = NULL;
            part.animation = loadAnimation(String8(SYSTEM_BOOTANIMATION_FILE));
            if (part.animation != NULL)
                animation.parts.add(part);
        }
        s = ++endl;
    }

    return true;
}

bool BootAnimation::preloadZip(Animation& animation)
{
    // read all the data structures
    const size_t pcount = animation.parts.size();
    void *cookie = NULL;
    ZipFileRO* zip = animation.zip;
    if (!zip->startIteration(&cookie)) {
        return false;
    }

    Animation::Part* partWithAudio = NULL;
    ZipEntryRO entry;
    char name[ANIM_ENTRY_NAME_MAX];
    while ((entry = zip->nextEntry(cookie)) != NULL) {
        const int foundEntryName = zip->getEntryFileName(entry, name, ANIM_ENTRY_NAME_MAX);
        if (foundEntryName > ANIM_ENTRY_NAME_MAX || foundEntryName == -1) {
            ALOGE("Error fetching entry file name");
            continue;
        }

        const String8 entryName(name);
        const String8 path(entryName.getPathDir());
        const String8 leaf(entryName.getPathLeaf());
        if (leaf.size() > 0) {
            for (size_t j = 0; j < pcount; j++) {
                if (path == animation.parts[j].path) {
                    uint16_t method;
                    // supports only stored png files
                    if (zip->getEntryInfo(entry, &method, NULL, NULL, NULL, NULL, NULL)) {
                        if (method == ZipFileRO::kCompressStored) {
                            FileMap* map = zip->createEntryFileMap(entry);
                            if (map) {
                                Animation::Part& part(animation.parts.editItemAt(j));
                                if (leaf == "audio.wav") {
                                    // a part may have at most one audio file
                                    part.audioData = (uint8_t *)map->getDataPtr();
                                    part.audioLength = map->getDataLength();
                                    partWithAudio = &part;
                                } else if (leaf == "trim.txt") {
                                    part.trimData.setTo((char const*)map->getDataPtr(),
                                                        map->getDataLength());
                                } else {
                                    Animation::Frame frame;
                                    frame.name = leaf;
                                    frame.map = map;
                                    frame.trimWidth = animation.width;
                                    frame.trimHeight = animation.height;
                                    frame.trimX = 0;
                                    frame.trimY = 0;
                                    part.frames.add(frame);
                                }
                            }
                        } else {
                            ALOGE("bootanimation.zip is compressed; must be only stored");
                        }
                    }
                }
            }
        }
    }

    // If there is trimData present, override the positioning defaults.
    for (Animation::Part& part : animation.parts) {
        const char* trimDataStr = part.trimData.string();
        for (size_t frameIdx = 0; frameIdx < part.frames.size(); frameIdx++) {
            const char* endl = strstr(trimDataStr, "\n");
            // No more trimData for this part.
            if (endl == NULL) {
                break;
            }
            String8 line(trimDataStr, endl - trimDataStr);
            const char* lineStr = line.string();
            trimDataStr = ++endl;
            int width = 0, height = 0, x = 0, y = 0;
            if (sscanf(lineStr, "%dx%d+%d+%d", &width, &height, &x, &y) == 4) {
                Animation::Frame& frame(part.frames.editItemAt(frameIdx));
                frame.trimWidth = width;
                frame.trimHeight = height;
                frame.trimX = x;
                frame.trimY = y;
            } else {
                ALOGE("Error parsing trim.txt, line: %s", lineStr);
                break;
            }
        }
    }

    // Create and initialize audioplay if there is a wav file in any of the animations.
    if (partWithAudio != NULL) {
        ALOGD("found audio.wav, creating playback engine");
        if (!audioplay::create(partWithAudio->audioData, partWithAudio->audioLength)) {
            return false;
        }
    }

    zip->endIteration(cookie);

    return true;
}

bool BootAnimation::movie()
{
    Animation* animation = loadAnimation(mZipFileName);
    if (animation == NULL)
        return false;

    bool anyPartHasClock = false;
    for (size_t i=0; i < animation->parts.size(); i++) {
        if(animation->parts[i].clockPosY >= 0) {
            anyPartHasClock = true;
            break;
        }
    }
    if (!anyPartHasClock) {
        mClockEnabled = false;
    }

    // Check if npot textures are supported
    mUseNpotTextures = false;
    String8 gl_extensions;
    const char* exts = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    if (!exts) {
        glGetError();
    } else {
        gl_extensions.setTo(exts);
        if ((gl_extensions.find("GL_ARB_texture_non_power_of_two") != -1) ||
            (gl_extensions.find("GL_OES_texture_npot") != -1)) {
            mUseNpotTextures = true;
        }
    }

    // Blend required to draw time on top of animation frames.
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glShadeModel(GL_FLAT);
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_BLEND);

    glBindTexture(GL_TEXTURE_2D, 0);
    glEnable(GL_TEXTURE_2D);
    glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    bool clockTextureInitialized = false;
    if (mClockEnabled) {
        clockTextureInitialized = (initTexture(&mClock, mAssets, "images/clock64.png") == NO_ERROR);
        mClockEnabled = clockTextureInitialized;
    }

    if (mClockEnabled && !updateIsTimeAccurate()) {
        mTimeCheckThread = new TimeCheckThread(this);
        mTimeCheckThread->run("BootAnimation::TimeCheckThread", PRIORITY_NORMAL);
    }

    playAnimation(*animation);

    if (mTimeCheckThread != NULL) {
        mTimeCheckThread->requestExit();
        mTimeCheckThread = NULL;
    }

    releaseAnimation(animation);

    if (clockTextureInitialized) {
        glDeleteTextures(1, &mClock.name);
    }

    return false;
}

bool BootAnimation::playAnimation(const Animation& animation)
{
    const size_t pcount = animation.parts.size();
    nsecs_t frameDuration = s2ns(1) / animation.fps;
    const int animationX = (mWidth - animation.width) / 2;
    const int animationY = (mHeight - animation.height) / 2;

    for (size_t i=0 ; i<pcount ; i++) {
        const Animation::Part& part(animation.parts[i]);
        const size_t fcount = part.frames.size();

        // can be 1, 0, or not set
        #ifdef NO_TEXTURE_CACHE
        const int noTextureCache = NO_TEXTURE_CACHE;
        #else
        const int noTextureCache =
                ((animation.width * animation.height * fcount) > 48 * 1024 * 1024) ? 1 : 0;
        #endif

        glBindTexture(GL_TEXTURE_2D, 0);

        // Calculate if we need to save memory by disabling texture cache
        // If free memory is less than the max texture size, cache will be disabled
        GLint mMaxTextureSize;
        bool needSaveMem = false;
        GLuint mTextureid;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &mMaxTextureSize);
        ALOGD("Free memory: %ld, max texture size: %d", getFreeMemory(), mMaxTextureSize);
        if (getFreeMemory() < mMaxTextureSize * mMaxTextureSize * fcount / 1024 ||
                noTextureCache) {
            ALOGD("Disabled bootanimation texture cache, FPS drops might occur.");
            needSaveMem = true;
            glGenTextures(1, &mTextureid);
            glBindTexture(GL_TEXTURE_2D, mTextureid);
            glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }

        // Handle animation package
        if (part.animation != NULL) {
            playAnimation(*part.animation);
            if (exitPending())
                break;
            continue; //to next part
        }

        for (int r=0 ; !part.count || r<part.count ; r++) {
            // Exit any non playuntil complete parts immediately
            if(exitPending() && !part.playUntilComplete)
                break;

            // only play audio file the first time we animate the part
            if (r == 0 && part.audioData && playSoundsAllowed()) {
                ALOGD("playing clip for part%d, size=%d", (int) i, part.audioLength);
                audioplay::playClip(part.audioData, part.audioLength);
            }

            glClearColor(
                    part.backgroundColor[0],
                    part.backgroundColor[1],
                    part.backgroundColor[2],
                    1.0f);

#ifdef MULTITHREAD_DECODE
            FrameManager *frameManager = NULL;
            if (r == 0 || needSaveMem) {
                frameManager = new FrameManager(MAX_DECODE_THREADS,
                    MAX_DECODE_CACHE, part.frames);
            }
#endif

            for (size_t j=0 ; j<fcount && (!exitPending() || part.playUntilComplete) ; j++) {
                const Animation::Frame& frame(part.frames[j]);
                nsecs_t lastFrame = systemTime();

                if (r > 0 && !needSaveMem) {
                    glBindTexture(GL_TEXTURE_2D, frame.tid);
                } else {
                    if (!needSaveMem && part.count != 1) {
                        glGenTextures(1, &frame.tid);
                        glBindTexture(GL_TEXTURE_2D, frame.tid);
                        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    }
#ifdef MULTITHREAD_DECODE
                    initTexture(frameManager->next());
#else
                    initTexture(frame);
#endif
                }

                const int xc = animationX + frame.trimX;
                const int yc = animationY + frame.trimY;
                Region clearReg(Rect(mWidth, mHeight));
                clearReg.subtractSelf(Rect(xc, yc, xc+frame.trimWidth, yc+frame.trimHeight));
                if (!clearReg.isEmpty()) {
                    Region::const_iterator head(clearReg.begin());
                    Region::const_iterator tail(clearReg.end());
                    glEnable(GL_SCISSOR_TEST);
                    while (head != tail) {
                        const Rect& r2(*head++);
                        glScissor(r2.left, mHeight - r2.bottom, r2.width(), r2.height());
                        glClear(GL_COLOR_BUFFER_BIT);
                    }
                    glDisable(GL_SCISSOR_TEST);
                }
                // specify the y center as ceiling((mHeight - frame.trimHeight) / 2)
                // which is equivalent to mHeight - (yc + frame.trimHeight)
                glDrawTexiOES(xc, mHeight - (yc + frame.trimHeight),
                              0, frame.trimWidth, frame.trimHeight);
                if (mClockEnabled && mTimeIsAccurate && part.clockPosY >= 0) {
                    drawTime(mClock, part.clockPosY);
                }

                eglSwapBuffers(mDisplay, mSurface);

                nsecs_t now = systemTime();
                nsecs_t delay = frameDuration - (now - lastFrame);
                //ALOGD("%lld, %lld", ns2ms(now - lastFrame), ns2ms(delay));
                lastFrame = now;

                if (delay > 0) {
                    struct timespec spec;
                    spec.tv_sec  = (now + delay) / 1000000000;
                    spec.tv_nsec = (now + delay) % 1000000000;
                    int err;
                    do {
                        err = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &spec, NULL);
                    } while (err<0 && errno == EINTR);
                }

                checkExit();
            }

            usleep(part.pause * ns2us(frameDuration));

#ifdef MULTITHREAD_DECODE
            if (frameManager) {
                delete frameManager;
            }
#endif

            // For infinite parts, we've now played them at least once, so perhaps exit
            if(exitPending() && !part.count)
                break;
        }

        if (needSaveMem) {
            glDeleteTextures(1, &mTextureid);
        }
    }

    // Free textures created for looping parts now that the animation is done.
    for (const Animation::Part& part : animation.parts) {
        if (part.count != 1) {
            const size_t fcount = part.frames.size();
            for (size_t j = 0; j < fcount; j++) {
                const Animation::Frame& frame(part.frames[j]);
                glDeleteTextures(1, &frame.tid);
            }
        }
    }

    // we've finally played everything we're going to play
    audioplay::setPlaying(false);
    audioplay::destroy();

    return true;
}

void BootAnimation::releaseAnimation(Animation* animation) const
{
    for (Vector<Animation::Part>::iterator it = animation->parts.begin(),
         e = animation->parts.end(); it != e; ++it) {
        if (it->animation)
            releaseAnimation(it->animation);
    }
    if (animation->zip)
        delete animation->zip;
    delete animation;
}

BootAnimation::Animation* BootAnimation::loadAnimation(const String8& fn)
{
    if (mLoadedFiles.indexOf(fn) >= 0) {
        ALOGE("File \"%s\" is already loaded. Cyclic ref is not allowed",
            fn.string());
        return NULL;
    }
    ZipFileRO *zip = ZipFileRO::open(fn);
    if (zip == NULL) {
        ALOGE("Failed to open animation zip \"%s\": %s",
            fn.string(), strerror(errno));
        return NULL;
    }

    Animation *animation =  new Animation;
    animation->fileName = fn;
    animation->zip = zip;
    mLoadedFiles.add(animation->fileName);

    parseAnimationDesc(*animation);
    if (!preloadZip(*animation)) {
        return NULL;
    }


    mLoadedFiles.remove(fn);
    return animation;
}

#ifdef MULTITHREAD_DECODE
FrameManager::FrameManager(int numThreads, size_t maxSize, const SortedVector<BootAnimation::Animation::Frame>& frames) :
    mMaxSize(maxSize),
    mFrameCounter(0),
    mNextIdx(0),
    mFrames(frames),
    mExit(false)
{
    pthread_mutex_init(&mBitmapsMutex, NULL);
    pthread_cond_init(&mSpaceAvailableCondition, NULL);
    pthread_cond_init(&mBitmapReadyCondition, NULL);
    for (int i = 0; i < numThreads; i++) {
        DecodeThread *thread = new DecodeThread(this);
        thread->run("bootanimation", PRIORITY_URGENT_DISPLAY);
        mThreads.add(thread);
    }
}

FrameManager::~FrameManager()
{
    mExit = true;
    pthread_cond_broadcast(&mSpaceAvailableCondition);
    pthread_cond_broadcast(&mBitmapReadyCondition);
    for (size_t i = 0; i < mThreads.size(); i++) {
        mThreads.itemAt(i)->requestExitAndWait();
    }

    // Any bitmap left in the queue won't get cleaned up by
    // the consumer.  Clean up now.
    for (size_t i = 0; i < mDecodedFrames.size(); i++) {
        delete mDecodedFrames[i].bitmap;
    }
}

SkBitmap* FrameManager::next()
{
    pthread_mutex_lock(&mBitmapsMutex);

    while (mDecodedFrames.size() == 0 ||
            mDecodedFrames.itemAt(0).idx != mNextIdx) {
        pthread_cond_wait(&mBitmapReadyCondition, &mBitmapsMutex);
    }
    DecodeWork work = mDecodedFrames.itemAt(0);
    mDecodedFrames.removeAt(0);
    mNextIdx++;
    pthread_cond_signal(&mSpaceAvailableCondition);
    pthread_mutex_unlock(&mBitmapsMutex);
    // The caller now owns the bitmap
    return work.bitmap;
}

FrameManager::DecodeWork FrameManager::getWork()
{
    DecodeWork work = {
        .frame = NULL,
        .bitmap = NULL,
        .idx = 0
    };

    pthread_mutex_lock(&mBitmapsMutex);

    while (mDecodedFrames.size() >= mMaxSize && !mExit) {
        pthread_cond_wait(&mSpaceAvailableCondition, &mBitmapsMutex);
    }

    if (!mExit) {
        work.frame = &mFrames.itemAt(mFrameCounter % mFrames.size());
        work.idx = mFrameCounter;
        mFrameCounter++;
    }

    pthread_mutex_unlock(&mBitmapsMutex);
    return work;
}

void FrameManager::completeWork(DecodeWork work) {
    size_t insertIdx;
    pthread_mutex_lock(&mBitmapsMutex);

    for (insertIdx = 0; insertIdx < mDecodedFrames.size(); insertIdx++) {
        if (work.idx < mDecodedFrames.itemAt(insertIdx).idx) {
            break;
        }
    }

    mDecodedFrames.insertAt(work, insertIdx);
    pthread_cond_signal(&mBitmapReadyCondition);

    pthread_mutex_unlock(&mBitmapsMutex);
}

FrameManager::DecodeThread::DecodeThread(FrameManager* manager) :
    Thread(false),
    mManager(manager)
{

}

bool FrameManager::DecodeThread::threadLoop()
{
    DecodeWork work = mManager->getWork();
    if (work.frame != NULL) {
        work.bitmap = BootAnimation::decode(*work.frame);
        mManager->completeWork(work);
        return true;
    }

    return false;
}
#endif

bool BootAnimation::playSoundsAllowed() const {
    // Only play sounds for system boots, not runtime restarts.
    if (!mSystemBoot) {
        return false;
    }

    // Read the system property to see if we should play the sound.
    // If it's not present, default to allowed.
    if (!property_get_bool(PLAY_SOUND_PROP_NAME, 1)) {
        return false;
    }

    // Don't play sounds if this is a reboot due to an error.
    char bootreason[PROPERTY_VALUE_MAX];
    if (property_get(BOOTREASON_PROP_NAME, bootreason, nullptr) > 0) {
        for (const auto& str : PLAY_SOUND_BOOTREASON_BLACKLIST) {
            if (strcasecmp(str.c_str(), bootreason) == 0) {
                return false;
            }
        }
    }
    return true;
}

bool BootAnimation::updateIsTimeAccurate() {
    static constexpr long long MAX_TIME_IN_PAST =   60000LL * 60LL * 24LL * 30LL;  // 30 days
    static constexpr long long MAX_TIME_IN_FUTURE = 60000LL * 90LL;  // 90 minutes

    if (mTimeIsAccurate) {
        return true;
    }

    struct stat statResult;
    if(stat(ACCURATE_TIME_FLAG_FILE_PATH, &statResult) == 0) {
        mTimeIsAccurate = true;
        return true;
    }

    FILE* file = fopen(LAST_TIME_CHANGED_FILE_PATH, "r");
    if (file != NULL) {
      long long lastChangedTime = 0;
      fscanf(file, "%lld", &lastChangedTime);
      fclose(file);
      if (lastChangedTime > 0) {
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        // Match the Java timestamp format
        long long rtcNow = (now.tv_sec * 1000LL) + (now.tv_nsec / 1000000LL);
        if (ACCURATE_TIME_EPOCH < rtcNow
            && lastChangedTime > (rtcNow - MAX_TIME_IN_PAST)
            && lastChangedTime < (rtcNow + MAX_TIME_IN_FUTURE)) {
            mTimeIsAccurate = true;
        }
      }
    }

    return mTimeIsAccurate;
}

BootAnimation::TimeCheckThread::TimeCheckThread(BootAnimation* bootAnimation) : Thread(false),
    mInotifyFd(-1), mSystemWd(-1), mTimeWd(-1), mBootAnimation(bootAnimation) {}

BootAnimation::TimeCheckThread::~TimeCheckThread() {
    // mInotifyFd may be -1 but that's ok since we're not at risk of attempting to close a valid FD.
    close(mInotifyFd);
}

bool BootAnimation::TimeCheckThread::threadLoop() {
    bool shouldLoop = doThreadLoop() && !mBootAnimation->mTimeIsAccurate
        && mBootAnimation->mClockEnabled;
    if (!shouldLoop) {
        close(mInotifyFd);
        mInotifyFd = -1;
    }
    return shouldLoop;
}

bool BootAnimation::TimeCheckThread::doThreadLoop() {
    static constexpr int BUFF_LEN (10 * (sizeof(struct inotify_event) + NAME_MAX + 1));

    // Poll instead of doing a blocking read so the Thread can exit if requested.
    struct pollfd pfd = { mInotifyFd, POLLIN, 0 };
    ssize_t pollResult = poll(&pfd, 1, 1000);

    if (pollResult == 0) {
        return true;
    } else if (pollResult < 0) {
        ALOGE("Could not poll inotify events");
        return false;
    }

    char buff[BUFF_LEN] __attribute__ ((aligned(__alignof__(struct inotify_event))));;
    ssize_t length = read(mInotifyFd, buff, BUFF_LEN);
    if (length == 0) {
        return true;
    } else if (length < 0) {
        ALOGE("Could not read inotify events");
        return false;
    }

    const struct inotify_event *event;
    for (char* ptr = buff; ptr < buff + length; ptr += sizeof(struct inotify_event) + event->len) {
        event = (const struct inotify_event *) ptr;
        if (event->wd == mSystemWd && strcmp(SYSTEM_TIME_DIR_NAME, event->name) == 0) {
            addTimeDirWatch();
        } else if (event->wd == mTimeWd && (strcmp(LAST_TIME_CHANGED_FILE_NAME, event->name) == 0
                || strcmp(ACCURATE_TIME_FLAG_FILE_NAME, event->name) == 0)) {
            return !mBootAnimation->updateIsTimeAccurate();
        }
    }

    return true;
}

void BootAnimation::TimeCheckThread::addTimeDirWatch() {
        mTimeWd = inotify_add_watch(mInotifyFd, SYSTEM_TIME_DIR_PATH,
                IN_CLOSE_WRITE | IN_MOVED_TO | IN_ATTRIB);
        if (mTimeWd > 0) {
            // No need to watch for the time directory to be created if it already exists
            inotify_rm_watch(mInotifyFd, mSystemWd);
            mSystemWd = -1;
        }
}

status_t BootAnimation::TimeCheckThread::readyToRun() {
    mInotifyFd = inotify_init();
    if (mInotifyFd < 0) {
        ALOGE("Could not initialize inotify fd");
        return NO_INIT;
    }

    mSystemWd = inotify_add_watch(mInotifyFd, SYSTEM_DATA_DIR_PATH, IN_CREATE | IN_ATTRIB);
    if (mSystemWd < 0) {
        close(mInotifyFd);
        mInotifyFd = -1;
        ALOGE("Could not add watch for %s", SYSTEM_DATA_DIR_PATH);
        return NO_INIT;
    }

    addTimeDirWatch();

    if (mBootAnimation->updateIsTimeAccurate()) {
        close(mInotifyFd);
        mInotifyFd = -1;
        return ALREADY_EXISTS;
    }

    return NO_ERROR;
}

// ---------------------------------------------------------------------------

}
; // namespace android
