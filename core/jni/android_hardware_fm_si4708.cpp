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
 * 
 * This file is only for the ZTE devices (Blade, V9, Racer, etc)
 * will only work with the ZTE FM radio driver
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "fmradio_si4708"

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

#define FM_RDS_STD_NONE    2

#define FM_DE_EMP75 0
#define FM_DE_EMP50 1

#define V4L2_CID_PRIVATE_BASE               0x8000000
#define V4L2_CID_PRIVATE_TAVARUA_REGION     V4L2_CID_PRIVATE_BASE + 7
#define V4L2_CID_PRIVATE_TAVARUA_EMPHASIS   V4L2_CID_PRIVATE_BASE + 12
#define V4L2_CID_PRIVATE_TAVARUA_RDS_STD    V4L2_CID_PRIVATE_BASE + 13
#define V4L2_CID_PRIVATE_TAVARUA_SPACING    V4L2_CID_PRIVATE_BASE + 14

struct rssi_snr_t
{
    uint8_t curr_rssi;
    uint8_t curr_rssi_th;
    uint8_t curr_snr;
};

/*dev settings*/
/*band*/
#define BAND_87500_108000_kHz   0
#define BAND_76000_108000_kHz   2
#define BAND_76000_90000_kHz    1

/*channel spacing*/
#define CHAN_SPACING_200_kHz   0        /*US*/
#define CHAN_SPACING_100_kHz   1        /*Europe,Japan*/
#define CHAN_SPACING_50_kHz    2

/*DE-emphasis Time Constant*/
#define DE_TIME_CONSTANT_50   1          /*Europe,Japan,Australia*/
#define DE_TIME_CONSTANT_75   0          /*US*/

#define      SEEKUP             1               /* seek up*/
#define      SEEKDOWN           0               /* seek down*/

/*****************IOCTLS******************/
/*magic no*/
#define Si4708_IOC_MAGIC  'k'

/*commands*/
#define Si4708_IOC_INIT2NORMAL                      _IO(Si4708_IOC_MAGIC, 1)
#define Si4708_IOC_NORMAL2STANDBY                   _IO(Si4708_IOC_MAGIC, 2)
#define Si4708_IOC_STANDBY2NORMAL                   _IO(Si4708_IOC_MAGIC, 3)
#define Si4708_IOC_BAND_SET                         _IOW(Si4708_IOC_MAGIC, 12, int)
#define Si4708_IOC_CHAN_SPACING_SET                 _IOW(Si4708_IOC_MAGIC, 14, int)
#define Si4708_IOC_CHAN_SELECT                      _IOW(Si4708_IOC_MAGIC, 4, int)
#define Si4708_IOC_CHAN_SEEK                        _IOW(Si4708_IOC_MAGIC, 5, int[2])

#define Si4708_IOC_CHAN_GET                         _IOR(Si4708_IOC_MAGIC, 17, int)

#define Si4708_IOC_CUR_RSSI_GET                     _IOR(Si4708_IOC_MAGIC, 12, rssi_snr_t)

#define Si4708_IOC_VOLUME_GET                       _IOR(Si4708_IOC_MAGIC, 7, int)
#define Si4708_IOC_VOLUME_SET                       _IOW(Si4708_IOC_MAGIC, 8, int)

#define Si4708_IOC_RDS_ENABLE                       _IO(Si4708_IOC_MAGIC, 23)
#define Si4708_IOC_RDS_DISABLE                      _IO(Si4708_IOC_MAGIC, 24)
#define Si4708_IOC_DE_SET                           _IOW(Si4708_IOC_MAGIC,32,uint8_t)  /*Setting DE-emphasis Time Constant. For DE=0,TC=50us(Europe,Japan,Australia) and DE=1,TC=75us(USA)*/

//Extra
#define Si4708_IOC_SET_AUDIOTRACK _IOW(Si4708_IOC_MAGIC, 16, int)


/*****************************************/

int setFreq(int freq, int fd);
int radioOn(int fd);
int radioOff(int fd);

bool radioInitialised = false;
bool radioEnabled = false;
static int lastFreq = 0;
static int lastVolume = 0;

int radioOn(int fd)
{
    LOGV("%s: enabling radio", __func__);
    if (radioEnabled) {
        return FM_JNI_SUCCESS;
    }

    int ret;
    if(!radioInitialised){
      ret = ioctl(fd, Si4708_IOC_INIT2NORMAL);
      radioInitialised = true;
    }
    else
      ret = ioctl(fd, Si4708_IOC_STANDBY2NORMAL);

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_INIT2NORMAL failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    radioEnabled = true;

    LOGD("FMRadio on");
    return FM_JNI_SUCCESS;
}

int radioOff(int fd)
{
  LOGD("%s: disabling radio radioEnabled=%i", __func__, radioEnabled);
  //    if (!radioEnabled) {
  //        return FM_JNI_SUCCESS;
  //    }

    int ret;

    ret = ioctl(fd, Si4708_IOC_NORMAL2STANDBY);

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_POWERDOWN failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }
    radioEnabled = false;
    LOGD("FMRadio off");

    return FM_JNI_SUCCESS;
}

int setFreq(int freq, int fd)
{
    int ret;
    freq = freq / 10;
    LOGV("%s: setting freq: %d", __func__, freq);

    ret = ioctl(fd, Si4708_IOC_CHAN_SELECT, &freq);

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_CHAN_SELECT failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    lastFreq = freq*10;
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
    
    ret = ioctl(fd, Si4708_IOC_CHAN_SPACING_SET, &nativeSpacing);

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_CHAN_SPACING_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

int setMute(int mute, int fd)
{
    LOGV("%s: setting mute %d", __func__, mute);

    int ret;
    int zero = 0;

    if(mute){
        ret = ioctl(fd, Si4708_IOC_VOLUME_GET, &lastVolume);
        ret = ioctl(fd, Si4708_IOC_VOLUME_SET, &zero);
    }
    else
        ret = ioctl(fd, Si4708_IOC_VOLUME_SET, &lastVolume);

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_MUTE failed failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }
    return FM_JNI_SUCCESS;

}

int setRDSMode(int rdsMode, int fd)
{
    return FM_JNI_SUCCESS;
    /*
    int nativeMode;
    int ret;

    if (rdsMode == FM_RDS_STD_NONE)
        ret = ioctl(fd, Si4708_IOC_RDS_DISABLE);
    else
        ret = ioctl(fd, Si4708_IOC_RDS_ENABLE);

    LOGV("%s: rdsMode is %d", __func__, rdsMode);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_RDS failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
    */
}

int setEmphais(int emphais, int fd)
{
    LOGV("%s", __func__);
    return FM_JNI_SUCCESS;
    /*
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

    ret = ioctl(fd, Si4708_IOC_DE_SET, &nativeEmphais);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_DE_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
*/
}

using namespace android;

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setControlNative
(JNIEnv * env, jobject thiz, jint fd, jint id, jint value)
{
    LOGV("%s : fd = %d id = %d value = %d", __func__, fd, id, value);

    switch(id) {
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
            switch (value) {
                case 1:
                    return radioOn(fd);
                    break;

                case 2:
                    return radioOff(fd);
                    break;

                case 3:
                    return setMute(1, fd);
                    break;

                case 4:
                    return setMute(0, fd);
                    break;

            }
            return FM_JNI_SUCCESS;
    }
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getFreqNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGV("%s: getting channel", __func__);
    int ret;
    uint32_t freq;

    int freq2;
    ret = ioctl(fd, Si4708_IOC_CHAN_GET, &freq2);
    LOGV("%s: IOCTL Si4708_IOC_CHAN_GET: %d", __func__, freq2);
    freq = freq2;

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_CHAN_GET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return freq*10;
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

    LOGV("%s : opening %s", __func__, nativeString);

    return open("/dev/si4708", O_RDWR);
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
  int val[2];
  int retval;
  if (dir == 0){
    val[0]=SEEKDOWN;
    retval = ioctl(fd, Si4708_IOC_CHAN_SEEK, val);
  } else {
    val[0]=SEEKUP;
    retval = ioctl(fd, Si4708_IOC_CHAN_SEEK, val);
  }
  if (retval!=0)
    LOGE("Search failed");

  LOGV("startSearchNative() %d freq=%d", retval,val[1]);
  return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_cancelSearchNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGV("cancelSearchNative()");
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRSSINative
(JNIEnv * env, jobject thiz, jint fd)
{
    return FM_JNI_FAILURE;
    /*
//FIXME
    struct rssi_snr_t rssi;
    int ret;
    LOGV("%s", __func__);

    ret = ioctl(fd, Si4708_IOC_CUR_RSSI_GET, &rssi);

    if (ret < 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_CUR_RSSI_GET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    LOGD("getRSSINative(), %d", rssi.curr_rssi);
    return rssi.curr_rssi;
    */
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

    LOGV("%s: Setting band %d", __func__, band);
    
    ret = ioctl(fd, Si4708_IOC_BAND_SET, &band);
    

    if (ret != 0)
    {
        LOGE("%s: IOCTL Si4708_IOC_BAND_SET failed %d", __func__, ret);
        return FM_JNI_FAILURE;
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getLowerBandNative
(JNIEnv * env, jobject thiz, jint fd)
{
    LOGV("getLowerBandNative()");
    return FM_JNI_SUCCESS;
}

static jint android_hardware_fmradio_FmReceiverJNI_setMonoStereoNative
(JNIEnv * env, jobject thiz, jint fd, jint val)
{
    LOGV("%s: setting audio track %d", __func__, val);
    int ret;
    if (val == 1) {
        int stereo = 0;
        ret = ioctl(fd, Si4708_IOC_SET_AUDIOTRACK, &stereo);

        if (ret != 0)
        {
            LOGE("%s: IOCTL Si4708_IOC_STEREO_SET failed %d", __func__, ret);
            return FM_JNI_FAILURE;
        }
    } else {
        int mono = 1;
        ret = ioctl(fd, Si4708_IOC_SET_AUDIOTRACK, &mono);

        if (ret != 0)
        {
            LOGE("%s: IOCTL Si4708_IOC_MONO_SET failed %d", __func__, ret);
            return FM_JNI_FAILURE;
        }
    }

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getBufferNative
(JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint index)
{
    LOGV("getBufferNative() %d", index);
    return index;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRawRdsNative
(JNIEnv * env, jobject thiz, jint fd, jbooleanArray buff, jint count)
{
    LOGV("getRawRdsNative() %d", count);
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
