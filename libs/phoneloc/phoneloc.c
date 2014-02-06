/* Copyright (C) 2013 The MoKee Open Source Project
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

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include <assert.h>
#include <android/log.h>

//#define DEBUG

//#ifdef DEBUG
#define TAG "phonelocjni"
//#endif

#define MAX_PHONE_LEN 20
#define MAX_PHONE_CN_LEN 40

typedef struct known_phone_info{
    char known_phone[MAX_PHONE_LEN];
    char known_phone_cn[MAX_PHONE_CN_LEN];
} known_phone_info_t;

static known_phone_info_t g_known_phone[] = {
    {"13800138000","001,中国移动客服"},
    {"10086000","001,中国移动短信客服"},
    {"1008611","001,中国移动客服"},
    {"1061","001,信息服务台"},
    {"1062","001,信息服务台"},
    {"1063","001,信息服务台"},
    {"1064","001,信息服务台"},
    {"1065","001,信息服务台"},
    {"1066","001,信息服务台"},
    {"1067","001,信息服务台"},
    {"1068","001,信息服务台"},
    {"1069","001,信息服务台"},
};

static const int KNOWN_PREFIX_LEN = 12;
static const char LOC_FILE[] = "/system/etc/phoneloc.dat";
static const char* KNOWN_PREFIX[] = {"0086", "106", "12520", "17951", "17909", "12593", "17950", "17910", "17911", 
    "193", "17900", "17901"};
static int exists = 0;

int file_exists(const char * filename) {
    if (exists != 0) return exists > 0 ? 0 : -1;
    FILE * file;
    if (file = fopen(filename, "r")) {
        fclose(file);
        exists = 1;
        return 0;
    }
    exists = -1;
    return exists;
}

int isInterPhone(char * phone, int len) {
    if (strncmp(phone, "00", 2) == 0) {
        return 0;
    }
    return -1;
}

void formatPhone(char* phone, int len, char* nphone) {//得到电话号码的标准格式，去掉开头的＋86等
    if (phone == NULL || nphone == NULL) {
        return;
    }
    // shouldn't length over 40!
    if (len > 40) len = 40;
    strncpy(nphone, phone, len);
    char* pch = strchr(nphone, '-');
    while (pch != NULL) {
        int pos = pch - nphone;
        memmove(nphone + pos, nphone + pos + 1, len - pos);
        pch = strchr(nphone, '-');
    }

    if (nphone[0] == '+') {
        if (strncmp(nphone, "+00", 3) != 0) {
            memmove(nphone + 2, nphone + 1, len);
            memmove(nphone, "00", 2);
			if(len>=6)
			{
			if(nphone[4]=='0'&&nphone[5]!='0')//输入错误区号，如+860535,多输入一个0
			{
				memmove(nphone+5, "0", 1);
				memmove(nphone +5, nphone + 4, len);
				memmove(nphone+4, "0", 1);//86353,86换成了

			}
			else if(nphone[4] != '0' && nphone[4] != '1' && nphone[5] != '0')//应该把手机号除了
			{
				memmove(nphone +5, nphone + 4, len);
				memmove(nphone+4, "0", 1);//86353,86换成了
			}
			else if(nphone[4]=='1'&&nphone[5]=='0'&&nphone[6]!='0')//特指北京,三排除10086之类
			{
				memmove(nphone +5, nphone + 4, len);
				memmove(nphone+4, "0", 1);//86353,86换成了
			}
			}
        } else {
            memmove(nphone, nphone + 1, len);
        }
    }
    if(nphone[0]!='0'&&nphone[0]!='1'&& nphone[0]!='9')//国内的固定电话,9是银行等的开头把这些也除去
	        memmove(nphone+1, "0", 1);//把第二位也置为0，这样在数据库就找不到
    if(nphone[1]=='0'&&nphone[0]=='1'&&nphone[2]!='0')//北京做特殊处理
	        memmove(nphone+1, "0", 1);//把第二位也置为0，这样在数据库就找不到
    strncpy(phone, nphone, len);
    strncpy(phone, nphone, len);
    int i;
    for (i = 0; i < KNOWN_PREFIX_LEN; i++) {
        int l = strlen(KNOWN_PREFIX[i]);
        if (strncmp(nphone, KNOWN_PREFIX[i], l) == 0) {
            memmove(nphone, nphone+l, len);
            break;
        }
    }
    if (pch=strchr(nphone, '#')) {
        pch[0] = 0x00;
    }
    if (pch=strchr(nphone, '*')) {
        pch[0] = 0x00;
    }
#ifdef DEBUG
     __android_log_print(ANDROID_LOG_DEBUG, TAG, "after format: %s", nphone);
#endif
} 
JNIEXPORT jstring JNICALL
getPhoneLocationJni( JNIEnv* env, jclass thiz, jstring phone ) {
    char* phone2;
    jboolean is_copy;
    phone2 = (*env)->GetStringUTFChars (env, phone, &is_copy);
#ifdef DEBUG
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "called [%s]", phone2);
#endif
    if (phone2 == NULL) return NULL;
    int len = strlen(phone2);
    if (len < 3) return NULL;

    char nphone[48];
    memset(nphone, 0x00, sizeof(nphone));
    formatPhone(phone2, len, nphone);
    len = strlen(nphone);
    if (len < 3) return NULL;

#ifdef DEBUG
     __android_log_print(ANDROID_LOG_DEBUG, TAG, "parse: %s %d", phone2, len);
#endif
    if (strncmp(phone2, "12520", 5) == 0 && len < 11) {  // test whether start with 12520 and other is not a mobile no.
        return (*env)->NewStringUTF(env, "001,移动飞信用户");
    }
    {  // parse the known phones
        int i;
        int count = sizeof(g_known_phone) / sizeof(known_phone_info_t);
        for (i = 0; i < count; i++) {
            int l = strlen(g_known_phone[i].known_phone);
            if (strncmp(phone2, g_known_phone[i].known_phone, l) == 0) {
                return (*env)->NewStringUTF(env, g_known_phone[i].known_phone_cn);
            }
        }
    }
    char location[48];
    char locationCode[48];
    memset(locationCode,0x00,48);
    memset(location,0x00,48);

    if (isInterPhone(nphone, len) >= 0) {
#ifdef DEBUG
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "inter phone[%s]", nphone);
#endif
        int pos = len > 6 ? 6 : len;
        char m[8];
        memset(m, 0x00, 8);
        int i;
        for (i = 0; i < 7-pos; i++) {
            m[i] = '9';
        }
        strncpy(m+7-pos, nphone, pos);
        for (; pos >= 3; pos--) {
            int num = atol(&m[0]);
            if (getLocationInfoEx(num, location, locationCode) >= 0) {
                return (*env)->NewStringUTF(env, locationCode);
            }
            memmove(m + 1, m, 6);
            m[7] = 0x00;
        }
        return NULL;
    }
    if (nphone[0] == '0') {
        if (nphone[1] == '1' || nphone[1] == '2') {
            nphone[3] = 0x00;
        } else if (len >= 4) {
            nphone[4] = 0x00;
        } else {
            return NULL;
        }
    } else {
        if (len >= 7) {
            nphone[7] = 0x00;
        }
    }
#ifdef DEBUG
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "find %s", nphone);
#endif
    int num = atol(nphone);
    if (getLocationInfoEx(num, location, locationCode) >= 0) {
        return (*env)->NewStringUTF(env, locationCode);
    }
#ifdef DEBUG
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "return emptystr");
#endif
    return NULL;
}

int getLocationInfoEx(int num, char * location, char * locationCode) {
    if (file_exists(LOC_FILE) < 0) {
#ifdef DEBUG
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "data file not exist!");
#endif
        return -1;
    }

    getLocationInfo(LOC_FILE, num, location, locationCode);
#ifdef DEBUG
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "return is %d, %s, %d, %s", strlen(location), location, strlen(locationCode), locationCode);
#endif
    if (location[0] == ' ' && location[1] == 0x00) return -1;
    strcat(locationCode, ",");
    strcat(locationCode, location);
#ifdef DEBUG
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "[%d] == %s", num, locationCode);
#endif
    return 0;
}
