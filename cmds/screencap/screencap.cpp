/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>

#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/mman.h>

#include <binder/ProcessState.h>

#include <gui/SurfaceComposerClient.h>
#include <gui/ISurfaceComposer.h>

#include <ui/DisplayInfo.h>
#include <ui/PixelFormat.h>

// TODO: Fix Skia.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#include <SkImageEncoder.h>
#include <SkData.h>
#pragma GCC diagnostic pop

using namespace android;

static uint32_t DEFAULT_DISPLAY_ID = ISurfaceComposer::eDisplayIdMain;

static void usage(const char* pname)
{
    fprintf(stderr,
            "usage: %s [-hp] [-d display-id] [FILENAME]\n"
            "   -h: this message\n"
            "   -p: save the file as a png.\n"
            "   -j: save the file as a jpeg.\n"
            "   -d: specify the display id to capture, default %d.\n"
            "If FILENAME ends with .png it will be saved as a png.\n"
            "If FILENAME is not given, the results will be printed to stdout.\n",
            pname, DEFAULT_DISPLAY_ID
    );
}

static SkColorType flinger2skia(PixelFormat f)
{
    switch (f) {
        case PIXEL_FORMAT_RGB_565:
            return kRGB_565_SkColorType;
        default:
            return kN32_SkColorType;
    }
}

static status_t vinfoToPixelFormat(const fb_var_screeninfo& vinfo,
        uint32_t* bytespp, uint32_t* f)
{

    switch (vinfo.bits_per_pixel) {
        case 16:
            *f = PIXEL_FORMAT_RGB_565;
            *bytespp = 2;
            break;
        case 24:
            *f = PIXEL_FORMAT_RGB_888;
            *bytespp = 3;
            break;
        case 32:
            // TODO: do better decoding of vinfo here
            *f = PIXEL_FORMAT_RGBX_8888;
            *bytespp = 4;
            break;
        default:
            return BAD_VALUE;
    }
    return NO_ERROR;
}

static status_t notifyMediaScanner(const char* fileName) {
    String8 cmd("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://");
    String8 fileUrl("\"");
    fileUrl.append(fileName);
    fileUrl.append("\"");
    cmd.append(fileName);
    cmd.append(" > /dev/null");
    int result = system(cmd.string());
    if (result < 0) {
        fprintf(stderr, "Unable to broadcast intent for media scanner.\n");
        return UNKNOWN_ERROR;
    }
    return NO_ERROR;
}

int main(int argc, char** argv)
{
    ProcessState::self()->startThreadPool();

    const char* pname = argv[0];
    bool png = false;
    bool jpeg = false;
    int32_t displayId = DEFAULT_DISPLAY_ID;
    int c;
    while ((c = getopt(argc, argv, "pjhd:")) != -1) {
        switch (c) {
            case 'p':
                png = true;
                break;
            case 'j':
                jpeg = true;
                break;
            case 'd':
                displayId = atoi(optarg);
                break;
            case '?':
            case 'h':
                usage(pname);
                return 1;
        }
    }
    argc -= optind;
    argv += optind;

    int fd = -1;
    const char* fn = NULL;
    if (argc == 0) {
        fd = dup(STDOUT_FILENO);
    } else if (argc == 1) {
        fn = argv[0];
        fd = open(fn, O_WRONLY | O_CREAT | O_TRUNC, 0664);
        if (fd == -1) {
            fprintf(stderr, "Error opening file: %s (%s)\n", fn, strerror(errno));
            return 1;
        }
        const int len = strlen(fn);
        if (len >= 4) {
            if (0 == strcmp(fn+len-4, ".png")) {
                png = true;
            } else if (0 == strcmp(fn+len-4, ".jpg")) {
                jpeg = true;
            } else if (len > 4 && 0 == strcmp(fn+len-5, ".jpeg")) {
                jpeg = true;
            }
        }
    }
    
    if (fd == -1) {
        usage(pname);
        return 1;
    }

    void const* mapbase = MAP_FAILED;
    ssize_t mapsize = -1;

    void const* base = NULL;
    uint32_t w, s, h, f;
    size_t size = 0;

    // Maps orientations from DisplayInfo to ISurfaceComposer
    static const uint32_t ORIENTATION_MAP[] = {
        ISurfaceComposer::eRotateNone, // 0 == DISPLAY_ORIENTATION_0
        ISurfaceComposer::eRotate270, // 1 == DISPLAY_ORIENTATION_90
        ISurfaceComposer::eRotate180, // 2 == DISPLAY_ORIENTATION_180
        ISurfaceComposer::eRotate90, // 3 == DISPLAY_ORIENTATION_270
    };

    ScreenshotClient screenshot;
    sp<IBinder> display = SurfaceComposerClient::getBuiltInDisplay(displayId);
    if (display == NULL) {
        fprintf(stderr, "Unable to get handle for display %d\n", displayId);
        return 1;
    }

    Vector<DisplayInfo> configs;
    SurfaceComposerClient::getDisplayConfigs(display, &configs);
    int activeConfig = SurfaceComposerClient::getActiveConfig(display);
    if (static_cast<size_t>(activeConfig) >= configs.size()) {
        fprintf(stderr, "Active config %d not inside configs (size %zu)\n",
                activeConfig, configs.size());
        return 1;
    }
    uint8_t displayOrientation = configs[activeConfig].orientation;
    uint32_t captureOrientation = ORIENTATION_MAP[displayOrientation];

    status_t result = screenshot.update(display, Rect(), 0, 0, 0, -1U,
            false, captureOrientation);
    if (result == NO_ERROR) {
        base = screenshot.getPixels();
        w = screenshot.getWidth();
        h = screenshot.getHeight();
        s = screenshot.getStride();
        f = screenshot.getFormat();
        size = screenshot.getSize();
    } else {
        const char* fbpath = "/dev/graphics/fb0";
        int fb = open(fbpath, O_RDONLY);
        if (fb >= 0) {
            struct fb_var_screeninfo vinfo;
            if (ioctl(fb, FBIOGET_VSCREENINFO, &vinfo) == 0) {
                uint32_t bytespp;
                if (vinfoToPixelFormat(vinfo, &bytespp, &f) == NO_ERROR) {
                    size_t offset = (vinfo.xoffset + vinfo.yoffset*vinfo.xres) * bytespp;
                    w = vinfo.xres;
                    h = vinfo.yres;
                    s = vinfo.xres;
                    size = w*h*bytespp;
                    mapsize = offset + size;
                    mapbase = mmap(0, mapsize, PROT_READ, MAP_PRIVATE, fb, 0);
                    if (mapbase != MAP_FAILED) {
                        base = (void const *)((char const *)mapbase + offset);
                    }
                }
            }
            close(fb);
        }
    }

    if (base != NULL) {
        if (png || jpeg) {
            const SkImageInfo info = SkImageInfo::Make(w, h, flinger2skia(f),
                                                       kPremul_SkAlphaType);
            SkAutoTUnref<SkData> data(SkImageEncoder::EncodeData(info, base, s*bytesPerPixel(f),
                    (png ? SkImageEncoder::kPNG_Type : SkImageEncoder::kJPEG_Type),
                    SkImageEncoder::kDefaultQuality));
            if (data.get()) {
                write(fd, data->data(), data->size());
            }
            if (fn != NULL) {
                notifyMediaScanner(fn);
            }
        } else {
            write(fd, &w, 4);
            write(fd, &h, 4);
            write(fd, &f, 4);
            size_t Bpp = bytesPerPixel(f);
            for (size_t y=0 ; y<h ; y++) {
                write(fd, base, w*Bpp);
                base = (void *)((char *)base + s*Bpp);
            }
        }
    }
    close(fd);
    if (mapbase != MAP_FAILED) {
        munmap((void *)mapbase, mapsize);
    }
    return 0;
}
