/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *            notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *            notice, this list of conditions and the following disclaimer in the
 *            documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *            the names of its contributors may be used to endorse or promote
 *            products derived from this software without specific prior written
 *            permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOGI printf
#define LOG_TAG "fmradio"

#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "utils/Log.h"
#include "utils/misc.h"
#include "android_runtime/AndroidRuntime.h"
#include <cutils/properties.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <math.h>

#define FM_JNI_SUCCESS 0L
#define FM_JNI_FAILURE -1L
#define SEARCH_DOWN 0
#define SEARCH_UP 1
#define TUNE_MULT 16000
enum search_dir_t {
    SEEK_UP,
    SEEK_DN,
    SCAN_UP,
    SCAN_DN
};
enum BCM_FM_CMD
{
    BCM4325_I2C_FM_RDS_SYSTEM = 0x00,                /*0x00  FM enable, RDS enable*/
    BCM4325_I2C_FM_CTRL,                             /*0x01  Band select, mono/stereo blend, mono/stearo select*/
    BCM4325_I2C_RDS_CTRL0,                           /*0x02  RDS/RDBS, flush FIFO*/
    BCM4325_I2C_RDS_CTRL1,                           /*0x03  Not used*/
    BCM4325_I2C_FM_AUDIO_PAUSE,                      /*0x04  Pause level and time constant*/
    BCM4325_I2C_FM_AUDIO_CTRL0,                      /*0x05  Mute, volume, de-emphasis, route parameters, BW select*/
    BCM4325_I2C_FM_AUDIO_CTRL1,                      /*0x06  Mute, volume, de-emphasis, route parameters, BW select*/
    BCM4325_I2C_FM_SEARCH_CTRL0,                     /*0x07  Search parameters such as stop level, up/down*/
    BCM4325_I2C_FM_SEARCH_CTRL1,                     /*0x08  Not used*/
    BCM4325_I2C_FM_SEARCH_TUNE_MODE,                 /*0x09  Search/tune mode and stop*/
    BCM4325_I2C_FM_FREQ0,                            /*0x0a  Set and get frequency*/
    BCM4325_I2C_FM_FREQ1,                            /*0x0b  Set and get frequency*/
    BCM4325_I2C_FM_AF_FREQ0,                         /*0x0c  Set alternate jump frequency*/
    BCM4325_I2C_FM_AF_FREQ1,                         /*0x0d  Set alternate jump frequency*/
    BCM4325_I2C_FM_CARRIER,                          /*0x0e  IF frequency error*/
    BCM4325_I2C_FM_RSSI,                             /*0x0f  Recived signal strength*/
    BCM4325_I2C_FM_RDS_MASK0,                        /*0x10  FM and RDS IRQ mask register*/
    BCM4325_I2C_FM_RDS_MASK1,                        /*0x11  FM and RDS IRQ mask register*/
    BCM4325_I2C_FM_RDS_FLAG0,                        /*0x12  FM and RDS flag register*/
    BCM4325_I2C_FM_RDS_FLAG1,                        /*0x13  FM and RDS flag register*/
    BCM4325_I2C_RDS_WLINE,                           /*0x14  FIFO water line set level*/
    BCM4325_I2C_RDS_BLKB_MATCH0,                     /*0x16  Block B match pattern*/
    BCM4325_I2C_RDS_BLKB_MATCH1,                     /*0x17  Block B match pattern*/
    BCM4325_I2C_RDS_BLKB_MASK0,                      /*0x18  Block B mask pattern*/
    BCM4325_I2C_RDS_BLKB_MASK1,                      /*0x19  Block B mask pattern*/
    BCM4325_I2C_RDS_PI_MATCH0,                       /*0x1a  PI match pattern*/
    BCM4325_I2C_RDS_PI_MATCH1,                       /*0x1b  PI match pattern*/
    BCM4325_I2C_RDS_PI_MASK0,                        /*0x1c  PI mask pattern*/
    BCM4325_I2C_RDS_PI_MASK1,                        /*0x1d  PI mask pattern*/
    BCM4325_I2C_FM_RDS_BOOT,                         /*0x1e  FM_RDS_BOOT register*/
    BCM4325_I2C_FM_RDS_TEST,                         /*0x1f  FM_RDS_TEST register*/
    BCM4325_I2C_SPARE0,                              /*0x20  Spare register #0*/
    BCM4325_I2C_SPARE1,                              /*0x21  Spare register #1*/
    /*0x21-0x26 Reserved*/
    BCM4325_I2C_FM_RDS_REV_ID = 0x28,                /*0x28  Revision ID of the FM demodulation core*/
    BCM4325_I2C_SLAVE_CONFIGURATION,                 /*0x29  Enable/disable I2C slave. Configure I2C slave address*/
    /*0x2a-0x7f Reserved*/
    BCM4325_I2C_FM_PCM_ROUTE = 0x4d,                 /*0x4d  Controls routing of FM audio output to either PM or Bluetooth SCO*/

    BCM4325_I2C_RDS_DATA = 0x80,                     /*0x80  Read RDS tuples(3 bytes each)*/

    BCM4325_I2C_FM_BEST_TUNE = 0x90,                 /*0x90  Best tune mode enable/disable for AF jump*/
    /*0x91-0xfb Reserved*/
    BCM4325_I2C_FM_SEARCH_METHOD = 0xfc,             /*0xfc  Select search methods: normal, preset, RSSI monitoring*/
    BCM4325_I2C_FM_SEARCH_STEPS,                     /*0xfd  Adjust search steps in units of 1kHz to 100kHz*/
    BCM4325_I2C_FM_MAX_PRESET,                       /*0xfe  Sets the maximum number of preset channels found for FM scan command*/
    BCM4325_I2C_FM_PRESET_STATION,                   /*0xff  Read the number of preset stations returned after a FM scan command*/
};


/*! @brief Size of writes to BCM4325: 1 byte for opcode/register and 1 bytes for data */
#define BCM4325_WRITE_CMD_SIZE                   2

/* Defines for read/write sizes to the BCM4325 */
#define BCM4325_REG_SIZE                         1
#define BCM4325_RDS_SIZE                         3
#define BCM4325_MAX_READ_SIZE                    3

/* Defines for the FM_RDS_SYSTEM register */
#define BCM4325_FM_RDS_SYSTEM_OFF                0x00
#define BCM4325_FM_RDS_SYSTEM_FM                 0x01
#define BCM4325_FM_RDS_SYSTEM_RDS                0x02

#define BCM4325_FM_CTRL_BAND_EUROPE_US           0x00
#define BCM4325_FM_CTRL_BAND_JAPAN               0x01
#define BCM4325_FM_CTRL_AUTO                     0x02
#define BCM4325_FM_CTRL_MANUAL                   0x00
#define BCM4325_FM_CTRL_STEREO                   0x04
#define BCM4325_FM_CTRL_MONO                     0x00
#define BCM4325_FM_CTRL_SWITCH                   0x08
#define BCM4325_FM_CTRL_BLEND                    0x00

#define BCM4325_FM_AUDIO_CTRL0_RF_MUTE_ENABLE    0x01
#define BCM4325_FM_AUDIO_CTRL0_RF_MUTE_DISABLE   0x00
#define BCM4325_FM_AUDIO_CTRL0_MANUAL_MUTE_ON    0x02
#define BCM4325_FM_AUDIO_CTRL0_MANUAL_MUTE_OFF   0x00
#define BCM4325_FM_AUDIO_CTRL0_DAC_OUT_LEFT_ON   0x04
#define BCM4325_FM_AUDIO_CTRL0_DAC_OUT_LEFT_OFF  0x00
#define BCM4325_FM_AUDIO_CTRL0_DAC_OUT_RIGHT_ON  0x08
#define BCM4325_FM_AUDIO_CTRL0_DAC_OUT_RIGHT_OFF 0x00
#define BCM4325_FM_AUDIO_CTRL0_ROUTE_DAC_ENABLE  0x10
#define BCM4325_FM_AUDIO_CTRL0_ROUTE_DAC_DISABLE 0x00
#define BCM4325_FM_AUDIO_CTRL0_ROUTE_I2S_ENABLE  0x20
#define BCM4325_FM_AUDIO_CTRL0_ROUTE_I2S_DISABLE 0x00
#define BCM4325_FM_AUDIO_CTRL0_DEMPH_75US        0x40
#define BCM4325_FM_AUDIO_CTRL0_DEMPH_50US        0x00
/* Defines for the SEARCH_CTRL0 register */
/*Bit 7: Search up/down*/
#define BCM4325_FM_SEARCH_CTRL0_UP               0x80
#define BCM4325_FM_SEARCH_CTRL0_DOWN             0x00

/* Defines for FM_SEARCH_TUNE_MODE register */
#define BCM4325_FM_TERMINATE_SEARCH_TUNE_MODE    0x00
#define BCM4325_FM_PRE_SET_MODE                  0x01
#define BCM4325_FM_AUTO_SEARCH_MODE              0x02
#define BCM4325_FM_AF_JUMP_MODE                  0x03

#define BCM4325_FM_FLAG_SEARCH_TUNE_FINISHED     0x01
#define BCM4325_FM_FLAG_SEARCH_TUNE_FAIL         0x02
#define BCM4325_FM_FLAG_RSSI_LOW                 0x04
#define BCM4325_FM_FLAG_CARRIER_ERROR_HIGH       0x08
#define BCM4325_FM_FLAG_AUDIO_PAUSE_INDICATION   0x10
#define BCM4325_FLAG_STEREO_DETECTION            0x20
#define BCM4325_FLAG_STEREO_ACTIVE               0x40

#define BCM4325_RDS_FLAG_FIFO_WLINE              0x02
#define BCM4325_RDS_FLAG_B_BLOCK_MATCH           0x08
#define BCM4325_RDS_FLAG_SYNC_LOST               0x10
#define BCM4325_RDS_FLAG_PI_MATCH                0x20

#define BCM4325_SEARCH_NORMAL                    0x00
#define BCM4325_SEARCH_PRESET                    0x01
#define BCM4325_SEARCH_RSSI                      0x02

#define BCM4325_FREQ_64MHZ                       64000

#define BCM4325_SEARCH_RSSI_60DB                 94

int hci_w(int reg, int val)
{
    int returnval = 0;

    char s1[100] = "hcitool cmd 0x3f 0x15 ";
    char stemp[10] = "";
    char starget[100] = "";
    char *pstarget = starget;

    sprintf(stemp, "0x%x ", reg);
    pstarget = strcat(s1, stemp);

    sprintf(stemp, "0x%x ", 0);
    pstarget = strcat(pstarget, stemp);

    sprintf(stemp, "0x%x ", val);
    pstarget = strcat(pstarget, stemp);
    returnval = system(pstarget);
    return returnval;
}

int hci_r(int reg)
{
    int returnval = 0;

    char s1[100] = "hcitool cmd 0x3f 0x15 ";
    char stemp[10] = "";
    char starget[100] = "";
    char *pstarget = starget;

    sprintf(stemp, "0x%x ", reg);
    pstarget=strcat(s1, stemp);

    sprintf(stemp, "0x%x ", 1);
    pstarget=strcat(pstarget, stemp);

    sprintf(stemp, "0x%x ", 1);
    pstarget = strcat(pstarget, stemp);
    returnval = system(pstarget);
    returnval /= 0x100;
    LOGD("hci_r 0x%x \n", returnval);

    return returnval;
}

using namespace android;

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_acquireFdNative
(JNIEnv* env, jobject thiz, jstring path)
{
    int fd;
    int i;
    char value = 0;
    int init_success = 0;
    LOGD("Radio starting\n");

    hci_w(BCM4325_I2C_FM_RDS_SYSTEM, BCM4325_FM_RDS_SYSTEM_FM);

    /* Write the POWER register again.  If this fails, then we're screwed. */
    if (hci_w(BCM4325_I2C_FM_RDS_SYSTEM,BCM4325_FM_RDS_SYSTEM_FM) < 0){
        return FM_JNI_FAILURE;
    }

    /* Write the band setting, mno/stereo blend setting */
    if(hci_w(BCM4325_I2C_FM_CTRL, BCM4325_FM_CTRL_MANUAL |BCM4325_FM_CTRL_STEREO) < 0){
        return FM_JNI_FAILURE;
    }

    if (hci_w(BCM4325_I2C_FM_AUDIO_CTRL0,
        BCM4325_FM_AUDIO_CTRL0_DAC_OUT_LEFT_ON | BCM4325_FM_AUDIO_CTRL0_DAC_OUT_RIGHT_ON |
        BCM4325_FM_AUDIO_CTRL0_ROUTE_DAC_ENABLE | BCM4325_FM_AUDIO_CTRL0_DEMPH_75US) < 0) {
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_closeFdNative
    (JNIEnv * env, jobject thiz, jint fd)
{
    LOGD("Radio close\n");
    
    if (hci_w(BCM4325_I2C_FM_RDS_SYSTEM,BCM4325_FM_RDS_SYSTEM_OFF) < 0){
        return FM_JNI_FAILURE;
    }
    return FM_JNI_SUCCESS;
}

/********************************************************************
 * Current JNI
 *******************************************************************/

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getFreqNative
    (JNIEnv * env, jobject thiz, jint fd)
{
    int retval;
    int freq = 103900;

    retval = hci_r(BCM4325_I2C_FM_FREQ1);
    freq = retval << 8;
    retval = hci_r(BCM4325_I2C_FM_FREQ0);
    freq += (retval + BCM4325_FREQ_64MHZ);

    return freq;
}

/*native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setFreqNative
    (JNIEnv * env, jobject thiz, jint fd, jint freq)
{
    /* Adjust frequency to be an offset from 64MHz */
    freq -= BCM4325_FREQ_64MHZ;

    /* Write the FREQ0 register */
    hci_w(BCM4325_I2C_FM_FREQ0, freq & 0xFF);

    /* Write the FREQ1 register */
    hci_w(BCM4325_I2C_FM_FREQ1, freq >> 8);

    /* Write the TUNER_MODE register to PRESET to actually start tuning */
    if ( hci_w(BCM4325_I2C_FM_SEARCH_TUNE_MODE, BCM4325_FM_PRE_SET_MODE) < 0){
        LOGE("fail \n");
    }

    return 0;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setControlNative
    (JNIEnv * env, jobject thiz, jint fd, jint id, jint value)
{
    if (value == 1) {
        LOGD("Radio ON \n");
    }
    if (value == 2) {
        LOGD("Radio OFF\n");
        if (hci_w(BCM4325_I2C_FM_RDS_SYSTEM,BCM4325_FM_RDS_SYSTEM_OFF) < 0){
            return FM_JNI_FAILURE;
        }
    }
    if (value == 3) {
        LOGD("MUTE off \n");
    }
    if (value == 4) {
        LOGD("MUTE on \n");
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getControlNative
    (JNIEnv * env, jobject thiz, jint fd, jint id)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_startSearchNative
    (JNIEnv * env, jobject thiz, jint fd, jint dir)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_cancelSearchNative
    (JNIEnv * env, jobject thiz, jint fd)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRSSINative
    (JNIEnv * env, jobject thiz, jint fd)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setBandNative
    (JNIEnv * env, jobject thiz, jint fd, jint low, jint high)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getLowerBandNative
    (JNIEnv * env, jobject thiz, jint fd)
{
    return FM_JNI_SUCCESS;
}

static jint android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative
    (JNIEnv * env, jobject thiz, jint fd, jint val)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getBufferNative
 (JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint index)
{
    return index;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRawRdsNative
 (JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint count)
{
    return FM_JNI_SUCCESS;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        { "acquireFdNative", "(Ljava/lang/String;)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_acquireFdNative},
        { "closeFdNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_closeFdNative},
        { "getFreqNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getFreqNative},
        { "setFreqNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setFreqNative},
        { "getControlNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getControlNative},
        { "setControlNative", "(III)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setControlNative},
        { "startSearchNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_startSearchNative},
        { "cancelSearchNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_cancelSearchNative},
        { "getRSSINative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getRSSINative},
        { "setBandNative", "(III)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setBandNative},
        { "getLowerBandNative", "(I)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getLowerBandNative},
        { "getBufferNative", "(I[BI)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getBufferNative},
        { "setMonoStereoNative", "(II)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative},
        { "getRawRdsNative", "(I[BI)I",
            (void*)android_hardware_fmradio_FmReceiverJNI_getRawRdsNative},
};

int register_android_hardware_fm_fmradio(JNIEnv* env)
{
        return jniRegisterNativeMethods(env, "android/hardware/fmradio/FmReceiverJNI", gMethods, NELEM(gMethods));
}
