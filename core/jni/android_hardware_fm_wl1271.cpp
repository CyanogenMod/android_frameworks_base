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

#define LOGI printf
#define LOG_TAG "fmradio_wl1271"

#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#define FM_JNI_SUCCESS 0L
#define FM_JNI_FAILURE -1L


int setFreq(int freq);
int radioOn();
int radioOff();
int setBand(int iLow, int iHigh);
int HexStr2Int(const char* szHexStr);
int getFreq();

bool radioEnabled = false;
static int lastFreq = 0;
static int giLow = 87500;
static int giStep = 100;

int radioOn()
{
    if (radioEnabled) {
        return FM_JNI_SUCCESS;
    }

    // power up FM radio
    if (system("hcitool cmd 0x3f 0x137 0x01 0x01") < 0) {
        return FM_JNI_FAILURE;
    }

    // now discard the following readings
    system("hcitool cmd 0x3f 0x133 0x20 0x02 0x00");
    system("hcitool cmd 0x3f 0x133 0x20 0x02 0x00");

    // set FM + RDS
    system("hcitool cmd 0x3f 0x135 0x20 0x02 0x00 0x00 0x03");
    // AUDIO_ENABLE_ANALOG | AUDIO_ENABLE_I2S
    system("hcitool cmd 0x3f 0x135 0x1d 0x02 0x00 0x00 0x03");

    // FM_RX_EMPHASIS_FILTER_50_USEC
    system("hcitool cmd 0x3f 0x135 0x0e 0x02 0x00 0x00 0x00");
    // FM_RX_IFFREQ_HILO_AUTOMATIC
    system("hcitool cmd 0x3f 0x135 0x23 0x02 0x00 0x00 0x02");

    radioEnabled = true;

    if (lastFreq != 0) {
        setFreq(lastFreq);
    }

    return FM_JNI_SUCCESS;
}

int radioOff()
{
    if (!radioEnabled) {
        return FM_JNI_SUCCESS;
    }

    // disable FM + RDS
    system("hcitool cmd 0x3f 0x135 0x20 0x02 0x00 0x00 0x00");

    // power down
    if (system("hcitool cmd 0x3f 0x137 0x01 0x00") < 0) {
        return FM_JNI_FAILURE;
    }

    radioEnabled = false;
    return FM_JNI_SUCCESS;
}

int setFreq(int freq)
{
    int val = freq - 87500;
    val /= 50; // round to 50KHZ

    char s[100] = "hcitool cmd 0x3f 0x135 0x0a 0x02 0x00 ";
    char stemp[10] = "";

    sprintf(stemp, "0x%2.2x ", val >> 8);
    strcat(s, stemp);
    sprintf(stemp, "0x%2.2x", val & 0xFF);
    strcat(s, stemp);

    system(s);

    // TUNER_MODE_PRESET needed after setting frequency
    if (system("hcitool cmd 0x3f 0x135 0x2d 0x02 0x00 0x00 0x01") < 0) {
        return FM_JNI_FAILURE;
    }

    lastFreq = freq;
    return FM_JNI_SUCCESS;
}

int setBand(int iLow, int iHigh)
{
    if (iLow == 76000) {
        // Japan
        system("hcitool cmd 0x3f 0x135 0x10 0x02 0x00 0x00 0x01");
        giLow = iLow;
    } else {
        system("hcitool cmd 0x3f 0x135 0x10 0x02 0x00 0x00 0x00");
    }

    if (iLow == 87500 && iHigh == 107900) {
        // spacing 200kHz for north america
        system("hcitool cmd 0x3f 0x135 0x38 0x02 0x00 0x00 0x04");
        giStep = 200;
    } else {
        // spacing 100kHz
        system("hcitool cmd 0x3f 0x135 0x38 0x02 0x00 0x00 0x02");
    }

    return FM_JNI_SUCCESS;
}

int HexStr2Int(const char* szHexStr)
{
     unsigned long Result;
     sscanf(szHexStr, "%lx", &Result);
     return Result;
}

int getFreq(){
     FILE *pRunPipe=NULL;
     char sAux[200];
     char sLow[3];
     char sHigh[3];
     char sNoValid[3];
     char sResult[5];

     // Create a pipe and wait for tool answere
     pRunPipe = popen("hcitool cmd 0x3F 0x133 0x0A 0x02 0x00", "r");
     if (pRunPipe)
     {
         // Wait until pipe finish
         while (!feof(pRunPipe))
             fgets(sAux, 200, pRunPipe);
         pclose(pRunPipe);

         // Check if we get a valid answere
         if ( strstr(sAux, "  01 33 FD 00 ") )
         {
             // Convert result data into HEX to get the freq.
             sscanf(sAux,"  01 33 FD 00 %s %s%s",sLow,sHigh,sNoValid);
             sprintf(sResult,"%s%s",sLow,sHigh);

             // Return FM actual freq.
             return (HexStr2Int( sResult ) * 50 + 87500);
         }
         else
              return FM_JNI_FAILURE;
     }
     else
         return FM_JNI_FAILURE;
}

using namespace android;

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setControlNative
    (JNIEnv * env, jobject thiz, jint fd, jint id, jint value)
{
    switch (value) {
    case 1:
        return radioOn();
        break;

    case 2:
        return radioOff();
        break;

    case 3:
        // mute not used
        break;

    case 4:
        // unmute
        if (system("hcitool cmd 0x3f 0x135 0x11 0x02 0x00 0x00 0x00") < 0) {
            return FM_JNI_FAILURE;
        }
        break;
    }

    return FM_JNI_SUCCESS;
}


/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getFreqNative
    (JNIEnv * env, jobject thiz, jint fd)
{
        return getFreq();
}

/*native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setFreqNative
    (JNIEnv * env, jobject thiz, jint fd, jint freq)
{
    return setFreq(freq);
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_acquireFdNative
(JNIEnv* env, jobject thiz, jstring path)
{
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_closeFdNative
    (JNIEnv * env, jobject thiz, jint fd)
{
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
int iCurrentFreq;

    // Added in order to move before seek and control all the FM Limits depending on the country
    iCurrentFreq = getFreq();
    if(dir == 0)
    {
        if(iCurrentFreq == giLow)
            iCurrentFreq = 18000 + giStep;
        setFreq(iCurrentFreq-giStep);
    }
    else
    {
        if(iCurrentFreq == 17900)
            iCurrentFreq = giLow - giStep;
        setFreq(iCurrentFreq+giStep);
    }

    int retval = system("hcitool cmd 0x3f 0x135 0x0f 0x02 0x00 0x00 0x10");
    if (dir == 0)
        retval = system("hcitool cmd 0x3f 0x135 0x1b 0x02 0x00 0x00 0x00");
    else
        retval = system("hcitool cmd 0x3f 0x135 0x1b 0x02 0x00 0x00 0x01");
    retval = system("hcitool cmd 0x3f 0x135 0x2d 0x02 0x00 0x00 0x02");
    LOGD("startSearchNative() %d", retval);

    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_cancelSearchNative
    (JNIEnv * env, jobject thiz, jint fd)
{
    int retval = system("hcitool cmd 0x3f 0x135 0x2d 0x02 0x00 0x00 0x00");

    LOGD("cancelSearchNative() %d", retval);
    return FM_JNI_SUCCESS;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_getRSSINative
    (JNIEnv * env, jobject thiz, jint fd)
{
    int rssi = system("hcitool cmd 0x3f 0x133 0x01 0x02 0x00");
    rssi /= 0x100; //???
    LOGD("getRSSINative(), %d", rssi);
    return rssi;
}

/* native interface */
static jint android_hardware_fmradio_FmReceiverJNI_setBandNative
    (JNIEnv * env, jobject thiz, jint fd, jint low, jint high)
{
    return setBand(low,high);
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
    if (val == 1) {
        // FM_STEREO_MODE
        system("hcitool cmd 0x3f 0x135 0x0c 0x02 0x00 0x00 0x00");
        // FM_STEREO_SOFT_BLEND
        system("hcitool cmd 0x3f 0x135 0x0d 0x02 0x00 0x00 0x01");
    } else {
        // forced FM_MONO_MODE ??
        system("hcitool cmd 0x3f 0x135 0x0c 0x02 0x00 0x00 0x01");
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
