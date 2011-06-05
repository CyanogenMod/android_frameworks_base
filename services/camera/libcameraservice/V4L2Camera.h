/*
 *
 * Copyright (C) 2010 SpectraCore Technologies
 * Author : Venkat Raju
 * Email  : codredruids@spectracoretech.com
 * Initial Code : Based on a code from http://code.google.com/p/android-m912/downloads/detail?name=v4l2_camera_v2.patch
 *
 * Copyright (C) 2009 0xlab.org - http://0xlab.org/
 *
 *      This program is free software; you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation; either version 2 of the License, or
 *      (at your option) any later version.
 *
 */

#ifndef _V4L2CAMERA_H
#define _V4L2CAMERA_H

#define NB_BUFFER 4

#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>
#include <linux/videodev.h>

namespace android {

struct vdIn {
    struct v4l2_capability cap;
    struct v4l2_format format;
    struct v4l2_buffer buf;
    struct v4l2_requestbuffers rb;
    void *mem[NB_BUFFER];
    bool isStreaming;
    int width;
    int height;
    int formatIn;
    int framesizeIn;
};

class V4L2Camera {

public:
    V4L2Camera();
    ~V4L2Camera();

    int Open (const char *device, int width, int height, int pixelformat);
    void Close ();

    int Init ();
    void Uninit ();

    int StartStreaming ();
    int StopStreaming ();

    void GrabPreviewFrame (void *previewBuffer);
    char *GrabRawFrame();
    void ProcessRawFrameDone();
    sp<IMemory> GrabJpegFrame ();
    void convert(unsigned char *buf, unsigned char *rgb, int width, int height);

private:
    struct vdIn *videoIn;
    int fd;

    int nQueued;
    int nDequeued;

    int saveYUYVtoJPEG (unsigned char *inputBuffer, int width, int height, FILE *file, int quality);

    void yuv_to_rgb16(unsigned char y, unsigned char u, unsigned char v, unsigned char *rgb);
};

}; // namespace android

#endif
