/*
**
** Copyright (C) 2010 SpectraCore Technologies
** Author : Venkat Raju
** Email  : codredruids@spectracoretech.com
** Initial Code : Based on a code from http://code.google.com/p/android-m912/downloads/detail?name=v4l2_camera_v2.patch
**
** Copyright (C) 2009 0xlab.org - http://0xlab.org/ 
**
** Copyright 2008, The Android Open Source Project
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

#define LOG_TAG "V4L2Camera"
#include <utils/Log.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>

#include <linux/videodev.h>

extern "C" {
#include "jpeglib.h"
}

#include "V4L2Camera.h"

namespace android {

V4L2Camera::V4L2Camera ()
    : nQueued(0), nDequeued(0)
{
    videoIn = (struct vdIn *) calloc (1, sizeof (struct vdIn));
}

V4L2Camera::~V4L2Camera()
{
    free(videoIn);
}

int V4L2Camera::Open (const char *device, int width, int height, int pixelformat)
{
    int ret;

    if ((fd = open(device, O_RDWR)) == -1) {
        LOGE("ERROR opening V4L interface: %s", strerror(errno));
    return -1;
    }

    ret = ioctl (fd, VIDIOC_QUERYCAP, &videoIn->cap);
    if (ret < 0) {
        LOGE("Error opening device: unable to query device.");
        return -1;
    }

    if ((videoIn->cap.capabilities & V4L2_CAP_VIDEO_CAPTURE) == 0) {
        LOGE("Error opening device: video capture not supported.");
        return -1;
    }

    if (!(videoIn->cap.capabilities & V4L2_CAP_STREAMING)) {
        LOGE("Capture device does not support streaming i/o");
        return -1;
    }

    videoIn->width = width;
    videoIn->height = height;
    videoIn->framesizeIn = (width * height << 1);
    videoIn->formatIn = pixelformat;

    videoIn->format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->format.fmt.pix.width = width;
    videoIn->format.fmt.pix.height = height;
    videoIn->format.fmt.pix.pixelformat = pixelformat;

    ret = ioctl(fd, VIDIOC_S_FMT, &videoIn->format);
    if (ret < 0) {
        LOGE("Open: VIDIOC_S_FMT Failed: %s", strerror(errno));
        return ret;
    }

    return 0;
}

void V4L2Camera::Close ()
{
    close(fd);
}

int V4L2Camera::Init()
{
    int ret;

    /* Check if camera can handle NB_BUFFER buffers */
    videoIn->rb.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->rb.memory = V4L2_MEMORY_MMAP;
    videoIn->rb.count = NB_BUFFER;

    ret = ioctl(fd, VIDIOC_REQBUFS, &videoIn->rb);
    if (ret < 0) {
        LOGE("Init: VIDIOC_REQBUFS failed: %s", strerror(errno));
        return ret;
    }

    for (int i = 0; i < NB_BUFFER; i++) {

        memset (&videoIn->buf, 0, sizeof (struct v4l2_buffer));

        videoIn->buf.index = i;
        videoIn->buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        videoIn->buf.memory = V4L2_MEMORY_MMAP;

        ret = ioctl (fd, VIDIOC_QUERYBUF, &videoIn->buf);
        if (ret < 0) {
            LOGE("Init: Unable to query buffer (%s)", strerror(errno));
            return ret;
        }

        videoIn->mem[i] = mmap (0,
               videoIn->buf.length,
               PROT_READ | PROT_WRITE,
               MAP_SHARED,
               fd,
               videoIn->buf.m.offset);

        if (videoIn->mem[i] == MAP_FAILED) {
            LOGE("Init: Unable to map buffer (%s)", strerror(errno));
            return -1;
        }

        ret = ioctl(fd, VIDIOC_QBUF, &videoIn->buf);
        if (ret < 0) {
            LOGE("Init: VIDIOC_QBUF Failed");
            return -1;
        }

        nQueued++;
    }

    return 0;
}

void V4L2Camera::Uninit ()
{
    int ret;

    videoIn->buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->buf.memory = V4L2_MEMORY_MMAP;

    /* Dequeue everything */
    int DQcount = nQueued - nDequeued;

    for (int i = 0; i < DQcount-1; i++) {
        ret = ioctl(fd, VIDIOC_DQBUF, &videoIn->buf);
        if (ret < 0)
            LOGE("Uninit: VIDIOC_DQBUF Failed");
    }
    nQueued = 0;
    nDequeued = 0;

    /* Unmap buffers */
    for (int i = 0; i < NB_BUFFER; i++)
        if (munmap(videoIn->mem[i], videoIn->buf.length) < 0)
            LOGE("Uninit: Unmap failed");
}

int V4L2Camera::StartStreaming ()
{
    enum v4l2_buf_type type;
    int ret;

    if (!videoIn->isStreaming) {
        type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

        ret = ioctl (fd, VIDIOC_STREAMON, &type);
        if (ret < 0) {
            LOGE("StartStreaming: Unable to start capture: %s", strerror(errno));
            return ret;
        }

        videoIn->isStreaming = true;
    }

    return 0;
}

int V4L2Camera::StopStreaming ()
{
    enum v4l2_buf_type type;
    int ret;

    if (videoIn->isStreaming) {
        type = V4L2_BUF_TYPE_VIDEO_CAPTURE;

        ret = ioctl (fd, VIDIOC_STREAMOFF, &type);
        if (ret < 0) {
            LOGE("StopStreaming: Unable to stop capture: %s", strerror(errno));
            return ret;
        }

        videoIn->isStreaming = false;
    }

    return 0;
}

void V4L2Camera::GrabPreviewFrame (void *previewBuffer)
{
    unsigned char *tmpBuffer;
    int ret;

    tmpBuffer = (unsigned char *) calloc (1, videoIn->width * videoIn->height * 2);

    videoIn->buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->buf.memory = V4L2_MEMORY_MMAP;

    /* DQ */
    ret = ioctl(fd, VIDIOC_DQBUF, &videoIn->buf);
    if (ret < 0) {
        //LOGE("GrabPreviewFrame: VIDIOC_DQBUF Failed");

        return;
    }
    nDequeued++;

    memcpy (tmpBuffer, videoIn->mem[videoIn->buf.index], (size_t) videoIn->buf.bytesused);

    //convertYUYVtoYUV422SP((uint8_t *)tmpBuffer, (uint8_t *)previewBuffer, videoIn->width, videoIn->height);
    convert((unsigned char *)tmpBuffer, (unsigned char *)previewBuffer, videoIn->width, videoIn->height);

    ret = ioctl(fd, VIDIOC_QBUF, &videoIn->buf);
    if (ret < 0) {
        LOGE("GrabPreviewFrame: VIDIOC_QBUF Failed");
        return;
    }

    nQueued++;

    free(tmpBuffer);
}

char * V4L2Camera::GrabRawFrame()
{
    int ret;

    videoIn->buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->buf.memory = V4L2_MEMORY_MMAP;

    /* DQ */
    ret = ioctl(fd, VIDIOC_DQBUF, &videoIn->buf);
    if (ret < 0) {
        LOGE("GrabRawFrame: VIDIOC_DQBUF Failed");
        return NULL;
    }
    nDequeued++;

    return (char *)videoIn->mem[videoIn->buf.index];
}

void V4L2Camera::ProcessRawFrameDone()
{
    int ret;
    ret = ioctl(fd, VIDIOC_QBUF, &videoIn->buf);
    if (ret < 0) {
        LOGE("postGrabRawFrame: VIDIOC_QBUF Failed");
        return;
    }

    nQueued++;
}

sp<IMemory> V4L2Camera::GrabJpegFrame ()
{
    FILE *output;
    FILE *input;
    int fileSize;
    int ret;

    videoIn->buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    videoIn->buf.memory = V4L2_MEMORY_MMAP;

    /* Dequeue buffer */
    ret = ioctl(fd, VIDIOC_DQBUF, &videoIn->buf);
    if (ret < 0) {
        LOGE("GrabJpegFrame: VIDIOC_DQBUF Failed");
        return NULL;
    }
    nDequeued++;

    LOGI("GrabJpegFrame: Generated a frame from capture device");

    /* Enqueue buffer */
    ret = ioctl(fd, VIDIOC_QBUF, &videoIn->buf);
    if (ret < 0) {
        LOGE("GrabJpegFrame: VIDIOC_QBUF Failed");
        return NULL;
    }
    nQueued++;

    output = fopen("/sdcard/tmp.jpg", "wb");

    if (output == NULL) {
        LOGE("GrabJpegFrame: Ouput file == NULL");
        return NULL;
    }

    fileSize = saveYUYVtoJPEG((unsigned char *)videoIn->mem[videoIn->buf.index], videoIn->width, videoIn->height, output, 85);

    fclose(output);

    input = fopen("/sdcard/tmp.jpg", "rb");

    if (input == NULL)
        LOGE("GrabJpegFrame: Input file == NULL");
    else {
        sp<MemoryHeapBase> mjpegPictureHeap = new MemoryHeapBase(fileSize);
        sp<MemoryBase> jpegmemBase = new MemoryBase(mjpegPictureHeap, 0, fileSize);

        fread((uint8_t *)mjpegPictureHeap->base(), 1, fileSize, input);
        fclose(input);

        return jpegmemBase;
    }

    return NULL;
}

int V4L2Camera::saveYUYVtoJPEG (unsigned char *inputBuffer, int width, int height, FILE *file, int quality)
{
    struct jpeg_compress_struct cinfo;
    struct jpeg_error_mgr jerr;
    JSAMPROW row_pointer[1];
    unsigned char *line_buffer, *yuyv;
    int z;
    int fileSize;

    line_buffer = (unsigned char *) calloc (width * 3, 1);
    yuyv = inputBuffer;

    cinfo.err = jpeg_std_error (&jerr);
    jpeg_create_compress (&cinfo);
    jpeg_stdio_dest (&cinfo, file);

    LOGI("JPEG PICTURE WIDTH AND HEIGHT: %dx%d", width, height);

    cinfo.image_width = width;
    cinfo.image_height = height;
    cinfo.input_components = 3;
    cinfo.in_color_space = JCS_RGB;

    jpeg_set_defaults (&cinfo);
    jpeg_set_quality (&cinfo, quality, TRUE);

    jpeg_start_compress (&cinfo, TRUE);

    z = 0;
    while (cinfo.next_scanline < cinfo.image_height) {
        int x;
        unsigned char *ptr = line_buffer;

        for (x = 0; x < width; x++) {
            int r, g, b;
            int y, u, v;

            if (!z)
                y = yuyv[0] << 8;
            else
                y = yuyv[2] << 8;

            u = yuyv[1] - 128;
            v = yuyv[3] - 128;

            r = (y + (359 * v)) >> 8;
            g = (y - (88 * u) - (183 * v)) >> 8;
            b = (y + (454 * u)) >> 8;

            *(ptr++) = (r > 255) ? 255 : ((r < 0) ? 0 : r);
            *(ptr++) = (g > 255) ? 255 : ((g < 0) ? 0 : g);
            *(ptr++) = (b > 255) ? 255 : ((b < 0) ? 0 : b);

            if (z++) {
                z = 0;
                yuyv += 4;
            }
        }

        row_pointer[0] = line_buffer;
        jpeg_write_scanlines (&cinfo, row_pointer, 1);
    }

    jpeg_finish_compress (&cinfo);
    fileSize = ftell(file);
    jpeg_destroy_compress (&cinfo);

    free (line_buffer);

    return fileSize;
}

void V4L2Camera::convert(unsigned char *buf, unsigned char *rgb, int width, int height)
{
    int x,y,z=0;
    int blocks;

    blocks = (width * height) * 2;

    for (y = 0; y < blocks; y+=4) {
        unsigned char Y1, Y2, U, V;

        Y1 = buf[y + 0];
        U = buf[y + 1];
        Y2 = buf[y + 2];
        V = buf[y + 3];

        yuv_to_rgb16(Y1, U, V, &rgb[y]);
        yuv_to_rgb16(Y2, U, V, &rgb[y + 2]);
    }

}

void V4L2Camera::yuv_to_rgb16(unsigned char y, unsigned char u, unsigned char v, unsigned char *rgb)
{
    int r,g,b;
    int z;
    int rgb16;

    z = 0;

    r = 1.164 * (y - 16) + 1.596 * (v - 128);
    g = 1.164 * (y - 16) - 0.813 * (v - 128) - 0.391 * (u -128);
    b = 1.164 * (y - 16) + 2.018 * (u - 128);

    if (r > 255) r = 255;
    if (g > 255) g = 255;
    if (b > 255) b = 255;

    if (r < 0) r = 0;
    if (g < 0) g = 0;
    if (b < 0) b = 0;

    rgb16 = (int)(((r >> 3)<<11) | ((g >> 2) << 5)| ((b >> 3) << 0));

    *rgb = (unsigned char)(rgb16 & 0xFF);
    rgb++;
    *rgb = (unsigned char)((rgb16 & 0xFF00) >> 8);

}


}; // namespace android
