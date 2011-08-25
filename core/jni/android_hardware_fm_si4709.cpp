/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 * Copyright (c) 2011, The CyanogenMod Project
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

#define LOG_NDEBUG 0
#define LOG_TAG "fmradio_si4709"

#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "utils/Log.h"
#include <sys/ioctl.h>
#include <fcntl.h>
#include "android_runtime/AndroidRuntime.h"

#define FM_JNI_SUCCESS 0L
#define FM_JNI_FAILURE -1L

/* Magic values from the framework
 * they have to match the values in FMTransiver.java
 * */

#define JAVA_FM_CHSPACE_200_KHZ 0
#define JAVA_FM_CHSPACE_100_KHZ 1
#define JAVA_FM_CHSPACE_50_KHZ  2

#define JAVA_FM_RX_SEARCHDIR_DOWN 0
#define JAVA_FM_RX_SEARCHDIR_UP   1

#define FM_RDS_STD_RBDS    0
#define FM_RDS_STD_RDS     1
#define FM_RDS_STD_NONE    2

#define FM_DE_EMP75 0
#define FM_DE_EMP50 1

#define V4L2_CID_PRIVATE_BASE                   0x8000000
#define V4L2_CID_PRIVATE_TAVARUA_SRCHMODE       V4L2_CID_PRIVATE_BASE + 1
#define V4L2_CID_PRIVATE_TAVARUA_SCANDWELL      V4L2_CID_PRIVATE_BASE + 2
#define V4L2_CID_PRIVATE_TAVARUA_SRCHON         V4L2_CID_PRIVATE_BASE + 3
#define V4L2_CID_PRIVATE_TAVARUA_STATE          V4L2_CID_PRIVATE_BASE + 4
#define V4L2_CID_PRIVATE_TAVARUA_TRANSMIT_MODE  V4L2_CID_PRIVATE_BASE + 5
#define V4L2_CID_PRIVATE_TAVARUA_RDSGROUP_MASK  V4L2_CID_PRIVATE_BASE + 6
#define V4L2_CID_PRIVATE_TAVARUA_REGION         V4L2_CID_PRIVATE_BASE + 7
#define V4L2_CID_PRIVATE_TAVARUA_SIGNAL_TH      V4L2_CID_PRIVATE_BASE + 8
#define V4L2_CID_PRIVATE_TAVARUA_SRCH_PTY       V4L2_CID_PRIVATE_BASE + 9
#define V4L2_CID_PRIVATE_TAVARUA_SRCH_PI        V4L2_CID_PRIVATE_BASE + 10
#define V4L2_CID_PRIVATE_TAVARUA_SRCH_CNT       V4L2_CID_PRIVATE_BASE + 11
#define V4L2_CID_PRIVATE_TAVARUA_EMPHASIS       V4L2_CID_PRIVATE_BASE + 12
#define V4L2_CID_PRIVATE_TAVARUA_RDS_STD        V4L2_CID_PRIVATE_BASE + 13
#define V4L2_CID_PRIVATE_TAVARUA_SPACING        V4L2_CID_PRIVATE_BASE + 14
#define V4L2_CID_PRIVATE_TAVARUA_RDSON          V4L2_CID_PRIVATE_BASE + 15
#define V4L2_CID_PRIVATE_TAVARUA_RDSGROUP_PROC  V4L2_CID_PRIVATE_BASE + 16
#define V4L2_CID_PRIVATE_TAVARUA_LP_MODE        V4L2_CID_PRIVATE_BASE + 17

#define V4L2_CTRL_CLASS_USER                    0x980000
#define V4L2_CID_BASE                           (V4L2_CTRL_CLASS_USER | 0x900)
#define V4L2_CID_AUDIO_MUTE                     V4L2_CID_BASE + 9

struct dev_state_t
{
    int power_state;
    int seek_state;
};

struct rssi_snr_t
{
    uint8_t curr_rssi;
    uint8_t curr_rssi_th;
    uint8_t curr_snr;
};

struct device_id
{
    uint8_t part_number;
    uint16_t manufact_number;
};

struct chip_id
{
    uint8_t  chip_version;
    uint8_t     device;
    uint8_t  firmware_version;
};

struct sys_config2
{
    uint16_t rssi_th;
    uint8_t fm_band;
    uint8_t fm_chan_spac;
    uint8_t fm_vol;
};

struct sys_config3
{
    uint8_t smmute;
    uint8_t smutea;
    uint8_t volext;
    uint8_t sksnr;
    uint8_t skcnt;
};

struct status_rssi
{
    uint8_t rdsr;
    uint8_t stc;
    uint8_t sfbl;
    uint8_t afcrl;
    uint8_t rdss;
    uint8_t blera;
    uint8_t st;
    uint16_t rssi;
};

struct power_config
{
    uint16_t dsmute :1;
    uint16_t dmute:1;
    uint16_t mono:1;
    uint16_t rds_mode:1;
    uint16_t sk_mode:1;
    uint16_t seek_up:1;
    uint16_t seek:1;
    uint16_t power_disable:1;
    uint16_t power_enable:1;
};

struct radio_data_t
{
    uint16_t rdsa;
    uint16_t rdsb;
    uint16_t rdsc;
    uint16_t rdsd;
    uint8_t  curr_rssi;
    uint32_t curr_channel;
    uint8_t blera;
    uint8_t blerb;
    uint8_t blerc;
    uint8_t blerd;
};

#define NUM_SEEK_PRESETS    20

#define WAIT_OVER           0
#define SEEK_WAITING        1
#define NO_WAIT             2
#define TUNE_WAITING        4
#define RDS_WAITING         5
#define SEEK_CANCEL         6

/*dev settings*/
/*band*/
#define BAND_87500_108000_kHz   1
#define BAND_76000_108000_kHz   2
#define BAND_76000_90000_kHz    3

/*channel spacing*/
#define CHAN_SPACING_200_kHz   20        /*US*/
#define CHAN_SPACING_100_kHz   10        /*Europe,Japan*/
#define CHAN_SPACING_50_kHz    5

/*DE-emphasis Time Constant*/
#define DE_TIME_CONSTANT_50   1          /*Europe,Japan,Australia*/
#define DE_TIME_CONSTANT_75   0          /*US*/


/*****************IOCTLS******************/
/*magic no*/
#define Si4709_IOC_MAGIC  0xFA
/*max seq no*/
#define Si4709_IOC_NR_MAX 40

/*commands*/
#define Si4709_IOC_POWERUP                          _IO(Si4709_IOC_MAGIC, 0)
#define Si4709_IOC_POWERDOWN                        _IO(Si4709_IOC_MAGIC, 1)
#define Si4709_IOC_BAND_SET                         _IOW(Si4709_IOC_MAGIC, 2, int)
#define Si4709_IOC_CHAN_SPACING_SET                 _IOW(Si4709_IOC_MAGIC, 3, int)
#define Si4709_IOC_CHAN_SELECT                      _IOW(Si4709_IOC_MAGIC, 4, uint32_t)
#define Si4709_IOC_CHAN_GET                         _IOR(Si4709_IOC_MAGIC, 5, uint32_t)
#define Si4709_IOC_SEEK_UP                          _IOR(Si4709_IOC_MAGIC, 6, uint32_t)
#define Si4709_IOC_SEEK_DOWN                        _IOR(Si4709_IOC_MAGIC, 7, uint32_t)
/*VNVS:28OCT'09---- Si4709_IOC_SEEK_AUTO is disabled as of now*/
//#define Si4709_IOC_SEEK_AUTO                       _IOR(Si4709_IOC_MAGIC, 8, u32)
#define Si4709_IOC_RSSI_SEEK_TH_SET                 _IOW(Si4709_IOC_MAGIC, 9, u8)
#define Si4709_IOC_SEEK_SNR_SET                     _IOW(Si4709_IOC_MAGIC, 10, u8)
#define Si4709_IOC_SEEK_CNT_SET                     _IOW(Si4709_IOC_MAGIC, 11, u8)
#define Si4709_IOC_CUR_RSSI_GET                     _IOR(Si4709_IOC_MAGIC, 12, rssi_snr_t)
#define Si4709_IOC_VOLEXT_ENB                       _IO(Si4709_IOC_MAGIC, 13)
#define Si4709_IOC_VOLEXT_DISB                      _IO(Si4709_IOC_MAGIC, 14)
#define Si4709_IOC_VOLUME_SET                       _IOW(Si4709_IOC_MAGIC, 15, u8)
#define Si4709_IOC_VOLUME_GET                       _IOR(Si4709_IOC_MAGIC, 16, u8)
#define Si4709_IOC_MUTE_ON                          _IO(Si4709_IOC_MAGIC, 17)
#define Si4709_IOC_MUTE_OFF                         _IO(Si4709_IOC_MAGIC, 18)
#define Si4709_IOC_MONO_SET                         _IO(Si4709_IOC_MAGIC, 19)
#define Si4709_IOC_STEREO_SET                       _IO(Si4709_IOC_MAGIC, 20)
#define Si4709_IOC_RSTATE_GET                       _IOR(Si4709_IOC_MAGIC, 21, dev_state_t)
#define Si4709_IOC_RDS_DATA_GET                     _IOR(Si4709_IOC_MAGIC, 22, radio_data_t)
#define Si4709_IOC_RDS_ENABLE                       _IO(Si4709_IOC_MAGIC, 23)
#define Si4709_IOC_RDS_DISABLE                      _IO(Si4709_IOC_MAGIC, 24)
#define Si4709_IOC_RDS_TIMEOUT_SET                  _IOW(Si4709_IOC_MAGIC, 25, u32)
#define Si4709_IOC_SEEK_CANCEL                      _IO(Si4709_IOC_MAGIC, 26)/*VNVS:START 13-OCT'09---- Added IOCTLs for reading the device-id,chip-id,power configuration, system configuration2 registers*/
#define Si4709_IOC_DEVICE_ID_GET                    _IOR(Si4709_IOC_MAGIC, 27,device_id)
#define Si4709_IOC_CHIP_ID_GET                      _IOR(Si4709_IOC_MAGIC, 28,chip_id)
#define Si4709_IOC_SYS_CONFIG2_GET                  _IOR(Si4709_IOC_MAGIC,29,sys_config2)
#define Si4709_IOC_POWER_CONFIG_GET                 _IOR(Si4709_IOC_MAGIC,30,power_config)
#define Si4709_IOC_AFCRL_GET                        _IOR(Si4709_IOC_MAGIC,31,u8)  /*For reading AFCRL bit, to check for a valid channel*/
#define Si4709_IOC_DE_SET                           _IOW(Si4709_IOC_MAGIC,32,uint8_t)  /*Setting DE-emphasis Time Constant. For DE=0,TC=50us(Europe,Japan,Australia) and DE=1,TC=75us(USA)*/
#define Si4709_IOC_SYS_CONFIG3_GET                  _IOR(Si4709_IOC_MAGIC, 33, sys_config3)
#define Si4709_IOC_STATUS_RSSI_GET                  _IOR(Si4709_IOC_MAGIC, 34, status_rssi)
#define Si4709_IOC_SYS_CONFIG2_SET                  _IOW(Si4709_IOC_MAGIC, 35, sys_config2)
#define Si4709_IOC_SYS_CONFIG3_SET                  _IOW(Si4709_IOC_MAGIC, 36, sys_config3)
#define Si4709_IOC_DSMUTE_ON                        _IO(Si4709_IOC_MAGIC, 37)
#define Si4709_IOC_DSMUTE_OFF                       _IO(Si4709_IOC_MAGIC, 38)
#define Si4709_IOC_RESET_RDS_DATA                   _IO(Si4709_IOC_MAGIC, 39)


/*****************************************/


int setFreq(int freq, int fd);
int radioOn(int fd);
int radioOff(int fd);


bool radioEnabled = false;
static int lastFreq = 0;

int radioOn(int fd)
{
    LOGV("%s", __func__);
    if (radioEnabled) {
        return FM_JNI_SUCCESS;
    }

    int ret;

    ret = ioctl(fd, Si4709_IOC_POWERUP);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_POWERUP failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    radioEnabled = true;

    if (lastFreq != 0) {
        setFreq(lastFreq, fd);
    }

    return FM_JNI_SUCCESS;
}

int radioOff(int fd)
{
    LOGV("%s", __func__);
    if (!radioEnabled) {
        return FM_JNI_SUCCESS;
    }

    int ret;

    ret = ioctl(fd, Si4709_IOC_POWERDOWN);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_POWERDOWN failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    radioEnabled = false;
    return FM_JNI_SUCCESS;
}

int setFreq(int freq, int fd)
{
    LOGV("%s %d", __func__, freq);

    //The driver expects the frequency to be in a different unit
    freq = freq / 10;

    int ret;

    ret = ioctl(fd, Si4709_IOC_CHAN_SELECT, &freq);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_CHAN_SELECT failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    lastFreq = freq;
    return FM_JNI_SUCCESS;
}

int setFreqSpacing(int spacing, int fd)
{
    int nativeSpacing, ret;

    switch(spacing) {
        case JAVA_FM_CHSPACE_200_KHZ:
            nativeSpacing = CHAN_SPACING_200_kHz;
            break;

        case JAVA_FM_CHSPACE_100_KHZ:
            nativeSpacing = CHAN_SPACING_100_kHz;
            break;

        case JAVA_FM_CHSPACE_50_KHZ:
            nativeSpacing = CHAN_SPACING_50_kHz;
            break;

        default:
            LOGE("%s : ERROR invalid Freqency spacing %d", __func__, spacing);
            return FM_JNI_FAILURE;
    }

    LOGV("%s: spacing is %d", __func__, nativeSpacing);

    ret = ioctl(fd, Si4709_IOC_CHAN_SPACING_SET, &nativeSpacing);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_CHAN_SPACING_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

int setMute(int mute, int fd)
{
    LOGV("%s %d", __func__, mute);

    int ret;

    if(mute)
        ret = ioctl(fd, Si4709_IOC_MUTE_ON);
    else
        ret = ioctl(fd, Si4709_IOC_MUTE_OFF);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_MUTE failed failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }
    return FM_JNI_SUCCESS;

}

int setRDSMode(int rdsMode, int fd)
{
    int nativeMode;
    int ret;

    if (rdsMode == FM_RDS_STD_NONE)
        ret = ioctl(fd, Si4709_IOC_RDS_DISABLE);
    else
        ret = ioctl(fd, Si4709_IOC_RDS_ENABLE);

    LOGV("%s: rdsMode is %d", __func__, rdsMode);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_RDS failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

int setEmphais(int emphais, int fd)
{
    int nativeEmphais, ret;

    switch(emphais) {
        case FM_DE_EMP75:
            nativeEmphais = DE_TIME_CONSTANT_75;
            break;

        case FM_DE_EMP50:
            nativeEmphais = DE_TIME_CONSTANT_50;
            break;

        default:
            LOGE("%s : ERROR invalid Freqency spacing %d", __func__, emphais);
            return FM_JNI_FAILURE;
    }

    LOGV("%s: spacing is %d", __func__, nativeEmphais);

    ret = ioctl(fd, Si4709_IOC_DE_SET, &nativeEmphais);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_DE_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;

}

using namespace android;

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setControlNative
(JNIEnv * env, jobject thiz, jint fd, jint id, jint value)
{
    LOGV("%s : fd = %d id = %d value = %d", __func__, fd, id, value);

    switch(id) {
        case V4L2_CID_AUDIO_MUTE:
            if (value == 3) {
                return setMute(1, fd);
            }
            else if (value == 4) {
                return setMute(0, fd);
            }
            else {
                return FM_JNI_FAILURE;
            }
            break;

        case V4L2_CID_PRIVATE_TAVARUA_STATE:
            if (value == 1) {
                return radioOn(fd);
            }
            else if (value == 2) {
                return radioOff(fd);
            }
            else {
                return FM_JNI_FAILURE;
            }
            break;

        case V4L2_CID_PRIVATE_TAVARUA_SPACING:
            return setFreqSpacing(value, fd);
            break;

        case V4L2_CID_PRIVATE_TAVARUA_RDS_STD:
            return setRDSMode(value, fd);
            break;

        case V4L2_CID_PRIVATE_TAVARUA_EMPHASIS:
            return setEmphais(value, fd);
            break;

        default:
            return FM_JNI_SUCCESS;
    }
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getFreqNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGV("%s", __func__);

    int ret;
    uint32_t freq;

    ret = ioctl(fd, Si4709_IOC_CHAN_GET, &freq);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_CHAN_GET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    //convert the frquency to khz units for the application
    freq = freq * 10;

    return freq;
}

/*native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setFreqNative
(JNIEnv * env, jobject thiz, jint fd, jint freq)
{
    return setFreq(freq, fd);
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_acquireFdNative
(JNIEnv *env, jobject thiz, jstring path)
{
    jboolean iscopy;
    const char *nativeString = env->GetStringUTFChars(path, &iscopy);

    LOGD("%s : opening %s", __func__, nativeString);

    return open(nativeString, O_RDWR);
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_closeFdNative
(JNIEnv * env, jobject thiz, jint fd)
{
    return close(fd);
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
    int freq;
    int retval;
    if (dir == JAVA_FM_RX_SEARCHDIR_DOWN) {
        retval = ioctl(fd, Si4709_IOC_SEEK_DOWN, &freq);
    }
    else {
        retval = ioctl(fd, Si4709_IOC_SEEK_UP, &freq);
    }

    if (retval != 0) {
        LOGE("Search failed");
    }

    LOGD("startSearchNative() %d freq=%d", retval, freq);
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_cancelSearchNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGD("cancelSearchNative()");
    ioctl(fd, Si4709_IOC_SEEK_CANCEL);
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRSSINative
(JNIEnv * env, jobject thiz, jint fd)
{
    struct rssi_snr_t rssi;
    int ret;
    LOGV("%s", __func__);

    ret = ioctl(fd, Si4709_IOC_CUR_RSSI_GET, &rssi);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_CUR_RSSI_GET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    LOGD("getRSSINative(), %d", rssi.curr_rssi);
    return rssi.curr_rssi;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setBandNative
(JNIEnv * env, jobject thiz, jint fd, jint low, jint high)
{
    LOGV("%s", __func__);

    int ret;
    int spacing, de, band;

    if (low == 76000 && high == 90000)
        band = BAND_76000_90000_kHz;
    else if (low == 87500 && high == 107900)
        band = BAND_87500_108000_kHz;
    else
        band = BAND_76000_108000_kHz;

    LOGV("%s: band is %d", __func__, band);

    ret = ioctl(fd, Si4709_IOC_BAND_SET, &band);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4709_IOC_BAND_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getLowerBandNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGD("getLowerBandNative()");
    return FM_JNI_SUCCESS;
}

static jint android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative
(JNIEnv * env, jobject thiz, jint fd, jint val)
{
    LOGV("%s", __func__);
    int ret;
    if (val == 1) {
        ret = ioctl(fd, Si4709_IOC_STEREO_SET);

        if (ret < 0)
        {
            LOGE("%s: IOCTL Si4709_IOC_STEREO_SET failed %d", __func__, ret);
            return FM_JNI_FAILURE;
        }
    } else {
        ret = ioctl(fd, Si4709_IOC_MONO_SET);

        if (ret < 0)
        {
            LOGE("%s: IOCTL Si4709_IOC_MONO_SET failed %d", __func__, ret);
            return FM_JNI_FAILURE;
        }
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getBufferNative
(JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint index)
{
    LOGD("getBufferNative() %d", index);
    return index;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRawRdsNative
(JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint count)
{
    LOGD("getRawRdsNative() %d", count);
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
