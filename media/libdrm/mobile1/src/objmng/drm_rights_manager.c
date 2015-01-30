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

#include <drm_rights_manager.h>
#include <drm_inner.h>
#include <drm_file.h>
#include <drm_i18n.h>
#include "log.h"

/*add this for fl feature, adjust the locations later*/
/*begin*/
#include <stdlib.h>
#include <parser_dcf.h>

#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>
#include <stdio.h>
#include <errno.h>

#include <openssl/hmac.h>
#include <openssl/evp.h>

#define LOG_TAG "libdrm:drm_rights_manager"
#define LOG_NDEBUG 0

#define FIXED_FL_RO_CID "cid:.flockro@localhost"
#define FIXED_FL_RO_CID_JAVA "cid:.flockro@localhost.java"
#define MAX_HEADER_LEN 2048
#define CONTENT_TYPE "application/vnd.oma.drm.content"

/*used for FL case to generate the dcf header*/
#define HEADER_ENCRYPTION_METHOD_VALUE "AES128CBC"
#define HEADER_CONTENT_NAME_VALUE "The salt wond routine"
#define HEADER_CONTENT_DESCRIPTION_VALUE  "piano"
#define HEADER_CONTENT_VENDOR_VALUE "Thirteen senses"
#define HEADER_ICON_URI_VALUE  "band"
#define HEADER_CONTENT_TYPE_JAVA_APK "audio/mpeg"

/*used for encrypt content key*/
#define AES_KEK "encdrmcontentkey"
/*used for calculate hmac for rights*/
#define HMAC_KEY "drmrightshmackey"
#define HMAC_KEY_LEN 16
#define HMAC_SHA1_LEN 20

/*
#define MAX_ENCRYPT_BLOCKS 64
#define MAX_ENCRYPT_SIZE MAX_ENCRYPT_BLOCKS * DRM_ONE_AES_BLOCK_LEN

#define DRM_NO_CONSTRAINT 0x80
*/

#define FILL_HEADER_FIELDS(p, field, len, value) \
    do { \
        strncpy((char*)p, (char*)field, len); \
        p += len; \
        strncpy((char*)p, (char*)value, strlen(value)); \
        p += strlen(value); \
        *p = '\r'; \
        p++; \
    } while(0)
/*end*/

typedef struct _T_DRM_Rights_Hmac {
    T_DRM_Rights ro;
    unsigned char hmac[HMAC_SHA1_LEN];
} T_DRM_Rights_Hmac;

unsigned char* AES_enc_cek(const unsigned char* cek, unsigned char *enc_cek) {
    ALOGD("entering AES_enc_cek\n");
    AES_KEY key;
    AES_set_encrypt_key((unsigned char*)AES_KEK, DRM_KEY_LEN * 8, &key);
    AES_encrypt(cek, enc_cek, &key);
    return enc_cek;
}

unsigned char* AES_dec_cek(const unsigned char* enc_cek,unsigned char* cek) {
    ALOGD("entering AES_dec_cek\n");
    AES_KEY key;
    AES_set_decrypt_key((unsigned char*)AES_KEK, DRM_KEY_LEN * 8, &key);
    AES_decrypt(enc_cek, cek, &key);
    return cek;
}

unsigned char * HMAC_calculate(const unsigned char *ro, int32_t roLen, unsigned char* md, int32_t *md_len) {
    ALOGD("entering HMAC_calculate.............\n");
    unsigned char * p = HMAC(EVP_sha1(), HMAC_KEY, HMAC_KEY_LEN, ro, roLen, md, (unsigned int*)md_len);
    ALOGD("leaving HMAC_calculate with %s, len is %d\n", p, *md_len);
    return p;
}

static int32_t drm_getString(uint8_t* string, int32_t len, int64_t handle)
{
    int32_t i;

    for (i = 0; i < len; i++) {
        if (DRM_FILE_FAILURE == DRM_file_read(handle, &string[i], 1))
            return FALSE;
        if (string[i] == '\n') {
            string[i + 1] = '\0';
            break;
        }
    }
    return TRUE;
}

static int32_t drm_putString(uint8_t* string, int64_t handle)
{
    int32_t i = 0;

    for (i = 0;; i++) {
        if (string[i] == '\0')
            break;
        if (DRM_FILE_FAILURE == DRM_file_write(handle, &string[i], 1))
            return FALSE;
    }
    return TRUE;
}

static int32_t drm_writeToUidTxt(uint8_t* Uid, int32_t* id)
{
    int32_t length;
    int32_t i;
    uint8_t idStr[8];
    int32_t idMax;
    uint8_t(*uidStr)[256];
    uint16_t nameUcs2[MAX_FILENAME_LEN];
    int32_t nameLen;
    int32_t bytesConsumed;
    int64_t handle;
    int32_t fileRes;

    if (*id < 1)
        return FALSE;

    /* convert in ucs2 */
    nameLen = strlen(DRM_UID_FILE_PATH);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        (uint8_t *)DRM_UID_FILE_PATH,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    fileRes = DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_READ,
                        &handle);
    if (DRM_FILE_SUCCESS != fileRes) {
        DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_WRITE,
                        &handle);
        DRM_file_write(handle, (uint8_t *)"0\n", 2);
        DRM_file_close(handle);
        DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_READ,
                        &handle);
    }

    if (!drm_getString(idStr, 8, handle)) {
        DRM_file_close(handle);
        return FALSE;
    }
    idMax = atoi((char *)idStr);

    if (idMax < *id)
        uidStr = malloc((idMax + 1) * 256);
    else
        uidStr = malloc(idMax * 256);

    for (i = 0; i < idMax; i++) {
        if (!drm_getString(uidStr[i], 256, handle)) {
            DRM_file_close(handle);
            free(uidStr);
            return FALSE;
        }
    }
    length = strlen((char *)Uid);
    strcpy((char *)uidStr[*id - 1], (char *)Uid);
    uidStr[*id - 1][length] = '\n';
    uidStr[*id - 1][length + 1] = '\0';
    if (idMax < (*id))
        idMax++;
    DRM_file_close(handle);
    DRM_file_open(DRM_UID_FILE_PATH,
                    nameLen,
                    DRM_FILE_MODE_WRITE,
                    &handle);
    sprintf((char *)idStr, "%d", idMax);

    if (!drm_putString(idStr, handle)) {
        DRM_file_close(handle);
        free(uidStr);
        return FALSE;
    }
    if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t *)"\n", 1)) {
        DRM_file_close(handle);
        free(uidStr);
        return FALSE;
    }
    for (i = 0; i < idMax; i++) {
        if (!drm_putString(uidStr[i], handle)) {
            DRM_file_close(handle);
            free(uidStr);
            return FALSE;
        }
    }
    if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t *)"\n", 1)) {
        DRM_file_close(handle);
        free(uidStr);
        return FALSE;
    }
    DRM_file_close(handle);
    free(uidStr);
    return TRUE;
}

/* See objmng_files.h */
int32_t drm_readFromUidTxt(uint8_t* Uid, int32_t* id, int32_t option)
{
    int32_t i;
    uint8_t p[256] = { 0 };
    uint8_t idStr[8];
    int32_t idMax = 0;
    uint16_t nameUcs2[MAX_FILENAME_LEN];
    int32_t nameLen = 0;
    int32_t bytesConsumed;
    int64_t handle;
    int32_t fileRes;

    if (NULL == id || NULL == Uid)
        return FALSE;

    DRM_file_startup();

    /* convert in ucs2 */
    nameLen = strlen(DRM_UID_FILE_PATH);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        (uint8_t *)DRM_UID_FILE_PATH,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    fileRes = DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_READ,
                        &handle);
    if (DRM_FILE_SUCCESS != fileRes) {
        DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_WRITE,
                        &handle);
        DRM_file_write(handle, (uint8_t *)"0\n", 2);
        DRM_file_close(handle);
        DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_READ,
                        &handle);
    }

    if (!drm_getString(idStr, 8, handle)) {
        DRM_file_close(handle);
        return FALSE;
    }
    idMax = atoi((char *)idStr);

    if (option == GET_UID) {
        if (*id < 1 || *id > idMax) {
            DRM_file_close(handle);
            return FALSE;
        }
        for (i = 1; i <= *id; i++) {
            if (!drm_getString(Uid, 256, handle)) {
                DRM_file_close(handle);
                return FALSE;
            }
        }
        DRM_file_close(handle);
        return TRUE;
    }
    if (option == GET_ID) {
        *id = -1;
        for (i = 1; i <= idMax; i++) {
            if (!drm_getString(p, 256, handle)) {
                DRM_file_close(handle);
                return FALSE;
            }
            if (strstr((char *)p, (char *)Uid) != NULL
                && strlen((char *)p) == strlen((char *)Uid) + 1) {
                *id = i;
                DRM_file_close(handle);
                return TRUE;
            }
            if ((*id == -1) && (strlen((char *)p) < 3))
                *id = i;
        }
        if (*id != -1) {
            DRM_file_close(handle);
            return FALSE;
        }
        *id = idMax + 1;
        DRM_file_close(handle);
        return FALSE;
    }
    DRM_file_close(handle);
    return FALSE;
}

static int32_t drm_acquireId(uint8_t* uid, int32_t* id)
{
    if (TRUE == drm_readFromUidTxt(uid, id, GET_ID))
        return TRUE;

    drm_writeToUidTxt(uid, id);

    return FALSE; /* The Uid is not exit, then return FALSE indicate it */
}

int32_t drm_writeOrReadInfo(int32_t id, T_DRM_Rights* Ro, int32_t* RoAmount, int32_t option)
{
    uint8_t fullname[MAX_FILENAME_LEN] = {0};
    int32_t tmpRoAmount;
    uint16_t nameUcs2[MAX_FILENAME_LEN];
    int32_t nameLen = 0;
    int32_t bytesConsumed;
    int64_t handle = -1;
    int32_t fileRes;
    unsigned char cek[16] = {0};
    unsigned char hmac[HMAC_SHA1_LEN] = {0};
    int hmac_len = -1;
    T_DRM_Rights_Hmac *ro_hmac = NULL;

    sprintf((char *)fullname, ANDROID_DRM_CORE_PATH"%d"EXTENSION_NAME_INFO, id);

    /* convert in ucs2 */
    nameLen = strlen((char *)fullname);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        fullname,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    fileRes = DRM_file_open(fullname,
                            nameLen,
                            DRM_FILE_MODE_READ,
                            &handle);
    if (DRM_FILE_SUCCESS != fileRes) {
        if (GET_ALL_RO == option || GET_A_RO == option)
            return FALSE;

        if (GET_ROAMOUNT == option) {
            *RoAmount = -1;
            return TRUE;
        }
    }
    if (handle >= 0)
        DRM_file_close(handle);

    DRM_file_open(fullname,
                nameLen,
                DRM_FILE_MODE_READ | DRM_FILE_MODE_WRITE,
                &handle);

    ALOGI("DRM_file_open sucessful, handle = %d", handle);

    switch(option) {
    case GET_ROAMOUNT:
        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)RoAmount, sizeof(int32_t))) {
            DRM_file_close(handle);
            return FALSE;
        }
        break;
    case GET_ALL_RO:
        DRM_file_setPosition(handle, sizeof(int32_t));
#if 0
        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)Ro, (*RoAmount) * sizeof(T_DRM_Rights))) {
#else
        ro_hmac = (T_DRM_Rights_Hmac*)malloc((*RoAmount) * sizeof(T_DRM_Rights_Hmac));
        if (NULL == ro_hmac)
            return FALSE;
        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)ro_hmac, (*RoAmount) * sizeof(T_DRM_Rights_Hmac))) {
            free(ro_hmac);
#endif
            DRM_file_close(handle);
            return FALSE;
        }
        int i = 0;
        for (i = 0; i < *RoAmount; i++) {
            memcpy(&(Ro[i]), &(ro_hmac[i].ro), sizeof(T_DRM_Rights));
            if (NULL == HMAC_calculate((unsigned char*)&(Ro[i]), sizeof(T_DRM_Rights), hmac, &hmac_len)) {
                free(ro_hmac);
                return FALSE;
            }
            if (0 != memcmp(hmac, ro_hmac[i].hmac, HMAC_SHA1_LEN)) {
                free(ro_hmac);
                return FALSE;
            }
            strncpy(Ro[i].KeyValue, AES_dec_cek(Ro[i].KeyValue, cek), DRM_KEY_LEN);
        }
        free(ro_hmac);
        break;
    case SAVE_ALL_RO:
        if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*)RoAmount, sizeof(int32_t))) {
            DRM_file_close(handle);
            return FALSE;
        }
#if 0
        if (NULL != Ro && *RoAmount >= 1) {
            if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*) Ro, (*RoAmount) * sizeof(T_DRM_Rights))) {
#else
        ro_hmac = (T_DRM_Rights_Hmac*)malloc((*RoAmount) * sizeof(T_DRM_Rights_Hmac));
        if (NULL == ro_hmac)
            return FALSE;

        int j = 0;
        for (j = 0; j < *RoAmount; j++) {
            strncpy(Ro[j].KeyValue, AES_enc_cek(Ro[j].KeyValue, cek), DRM_KEY_LEN);
            memcpy(&(ro_hmac[j].ro), &(Ro[j]), sizeof(T_DRM_Rights));
            if (NULL == HMAC_calculate((unsigned char*)&(Ro[j]), sizeof(T_DRM_Rights), hmac, &hmac_len)) {
                free(ro_hmac);
                return FALSE;
            }
            memcpy(ro_hmac[j].hmac, hmac, HMAC_SHA1_LEN);
        }

        if (NULL != ro_hmac && *RoAmount >= 1) {
            if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*) ro_hmac, (*RoAmount) * sizeof(T_DRM_Rights_Hmac))) {
                free(ro_hmac);
#endif
                DRM_file_close(handle);
                return FALSE;
            }
        }
        free(ro_hmac);
        break;
    case GET_A_RO:
#if 0
        DRM_file_setPosition(handle, sizeof(int32_t) + (*RoAmount - 1) * sizeof(T_DRM_Rights));

        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)Ro, sizeof(T_DRM_Rights))) {
            DRM_file_close(handle);
#else
        ALOGD("GET_A_RO\n");
        DRM_file_setPosition(handle, sizeof(int32_t) + (*RoAmount - 1) * sizeof(T_DRM_Rights_Hmac));

        ro_hmac = (T_DRM_Rights_Hmac*)malloc(sizeof(T_DRM_Rights_Hmac));
        if (NULL == ro_hmac)
            return FALSE;

        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)ro_hmac, sizeof(T_DRM_Rights_Hmac))) {
            DRM_file_close(handle);
            free(ro_hmac);
            ALOGD("read ro error\n");
#endif
            return FALSE;
        }
        memcpy(&(Ro[0]), &(ro_hmac[0].ro), sizeof(T_DRM_Rights));
        if (NULL == HMAC_calculate((unsigned char*)&(Ro[0]), sizeof(T_DRM_Rights), hmac, &hmac_len)) {
            free(ro_hmac);
            return FALSE;
        }
        if (0 != memcmp(hmac, ro_hmac[0].hmac, HMAC_SHA1_LEN)) {
            free(ro_hmac);
            ALOGD("compare mac error\n");
            return FALSE;
        }

        strncpy(Ro[0].KeyValue, AES_dec_cek(Ro[0].KeyValue, cek), DRM_KEY_LEN);
        free(ro_hmac);
        break;
    case SAVE_A_RO:
#if 0
        DRM_file_setPosition(handle, sizeof(int32_t) + (*RoAmount - 1) * sizeof(T_DRM_Rights));

        if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*)Ro, sizeof(T_DRM_Rights))) {
            DRM_file_close(handle);
#else
        ALOGD("SAVE_A_RO\n");
        ALOGI("Before DRM_file_setPosition handle = %d ", handle);
        DRM_file_setPosition(handle, sizeof(int32_t) + (*RoAmount - 1) * sizeof(T_DRM_Rights_Hmac));

        strncpy(Ro[0].KeyValue, AES_enc_cek(Ro[0].KeyValue, cek), DRM_KEY_LEN);

        ro_hmac = (T_DRM_Rights_Hmac*)malloc(sizeof(T_DRM_Rights_Hmac));
        if (NULL == ro_hmac)
            return FALSE;

        memcpy(&(ro_hmac[0].ro), &(Ro[0]), sizeof(T_DRM_Rights));
        if (NULL == HMAC_calculate((unsigned char*)&(Ro[0]), sizeof(T_DRM_Rights), hmac, &hmac_len)) {
            ALOGE("HMAC_calculate failed");
            free(ro_hmac);
            return FALSE;
        }
        memcpy(ro_hmac[0].hmac, hmac, HMAC_SHA1_LEN);
        ALOGI("Before DRM_file_write handle = %d ", handle);
        if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*) ro_hmac, sizeof(T_DRM_Rights_Hmac))) {
            DRM_file_close(handle);
            free(ro_hmac);
            ALOGE("DRM_file_write failed");
#endif
            return FALSE;
        }

        DRM_file_setPosition(handle, 0);
        if (DRM_FILE_FAILURE == DRM_file_read(handle, (uint8_t*)&tmpRoAmount, sizeof(int32_t))) {
            DRM_file_close(handle);
            free(ro_hmac);
            ALOGE("DRM_file_read failed");
            return FALSE;
        }
        if (tmpRoAmount < *RoAmount) {
            DRM_file_setPosition(handle, 0);
            DRM_file_write(handle, (uint8_t*)RoAmount, sizeof(int32_t));
        }
        free(ro_hmac);
        break;
    case DELETE_ALL_RO:
        //truncate 0
        tmpRoAmount = 0;
        if (DRM_FILE_FAILURE == DRM_file_write(handle, (uint8_t*)&tmpRoAmount, sizeof(int32_t))) {
            DRM_file_close(handle);
            return FALSE;
        }
        break;
    default:
        DRM_file_close(handle);
        return FALSE;
    }
    if (handle >= 0)
        DRM_file_close(handle);
    return TRUE;
}

int32_t drm_appendRightsInfo(T_DRM_Rights* rights)
{
    int32_t id;
    int32_t roAmount;
    ALOGI("Enter appendRightsInfo");

    if (NULL == rights)
        return FALSE;

    drm_acquireId(rights->uid, &id);

    if (FALSE == drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT))
        return FALSE;

    if (-1 == roAmount)
        roAmount = 0;

    /* The RO amount increase */
    roAmount++;

    /* Save the rights information */
    if (FALSE == drm_writeOrReadInfo(id, rights, &roAmount, SAVE_A_RO))
        return FALSE;
    ALOGI("Exit appendRightsInfo! success");
    return TRUE;
}

int32_t drm_getMaxIdFromUidTxt()
{
    uint8_t idStr[8];
    int32_t idMax = 0;
    uint16_t nameUcs2[MAX_FILENAME_LEN] = {0};
    int32_t nameLen = 0;
    int32_t bytesConsumed;
    int32_t handle;
    int32_t fileRes;

    /* convert in ucs2 */
    nameLen = strlen(DRM_UID_FILE_PATH);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        (uint8_t *)DRM_UID_FILE_PATH,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    fileRes = DRM_file_open(DRM_UID_FILE_PATH,
                        nameLen,
                        DRM_FILE_MODE_READ,
                        &handle);

    /* this means the uid.txt file is not exist, so there is not any DRM object */
    if (DRM_FILE_SUCCESS != fileRes)
        return 0;

    if (!drm_getString(idStr, 8, handle)) {
        DRM_file_close(handle);
        return -1;
    }
    DRM_file_close(handle);

    idMax = atoi((char *)idStr);
    return idMax;
}

int32_t drm_removeIdInfoFile(int32_t id)
{
    uint8_t filename[MAX_FILENAME_LEN] = {0};
    uint16_t nameUcs2[MAX_FILENAME_LEN];
    int32_t nameLen = 0;
    int32_t bytesConsumed;

    if (id <= 0)
        return FALSE;

    sprintf((char *)filename, ANDROID_DRM_CORE_PATH"%d"EXTENSION_NAME_INFO, id);

    /* convert in ucs2 */
    nameLen = strlen((char *)filename);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        filename,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    if (DRM_FILE_SUCCESS != DRM_file_delete(filename, nameLen))
        return FALSE;

    return TRUE;
}

int32_t drm_updateUidTxtWhenDelete(int32_t id)
{
    uint16_t nameUcs2[MAX_FILENAME_LEN];
    int32_t nameLen = 0;
    int32_t bytesConsumed;
    int64_t handle;
    int32_t fileRes;
    int32_t bufferLen;
    uint8_t *buffer;
    uint8_t idStr[8];
    int32_t idMax;

    if (id <= 0)
        return FALSE;

    nameLen = strlen(DRM_UID_FILE_PATH);
#if 0
    nameLen = DRM_i18n_mbsToWcs(DRM_CHARSET_UTF8,
                        (uint8_t *)DRM_UID_FILE_PATH,
                        nameLen,
                        nameUcs2,
                        MAX_FILENAME_LEN,
                        &bytesConsumed);
#endif
    bufferLen = DRM_file_getFileLength(DRM_UID_FILE_PATH, nameLen);
    if (bufferLen <= 0)
        return FALSE;

    buffer = (uint8_t *)malloc(bufferLen);
    if (NULL == buffer)
        return FALSE;
    fileRes = DRM_file_open(DRM_UID_FILE_PATH,
                            nameLen,
                            DRM_FILE_MODE_READ,
                            &handle);
    if (DRM_FILE_SUCCESS != fileRes) {
        free(buffer);
        return FALSE;
    }

    drm_getString(idStr, 8, handle);
    idMax = atoi((char *)idStr);

    bufferLen -= strlen((char *)idStr);
    fileRes = DRM_file_read(handle, buffer, bufferLen);
    buffer[bufferLen] = '\0';
    DRM_file_close(handle);

    /* handle this buffer */
    {
        uint8_t *pStart, *pEnd;
        int32_t i, movLen;

        pStart = buffer;
        pEnd = pStart;
        for (i = 0; i < id; i++) {
            if (pEnd != pStart)
                pStart = ++pEnd;
            while ('\n' != *pEnd)
                pEnd++;
            if (pStart == pEnd)
                pStart--;
        }
        movLen = bufferLen - (pEnd - buffer);
        memmove(pStart, pEnd, movLen);
        bufferLen -= (pEnd - pStart);
    }

    if (DRM_FILE_SUCCESS != DRM_file_delete(DRM_UID_FILE_PATH , nameLen)) {
        free(buffer);
        return FALSE;
    }
    fileRes = DRM_file_open(DRM_UID_FILE_PATH,
        nameLen,
        DRM_FILE_MODE_WRITE,
        &handle);
    if (DRM_FILE_SUCCESS != fileRes) {
        free(buffer);
        return FALSE;
    }
    sprintf((char *)idStr, "%d", idMax);
    drm_putString(idStr, handle);
    DRM_file_write(handle, (uint8_t*)"\n", 1);
    DRM_file_write(handle, buffer, bufferLen);
    free(buffer);
    DRM_file_close(handle);
    return TRUE;
}

int32_t drm_getKey(uint8_t* uid, uint8_t* KeyValue)
{
    ALOGI("Enter drm_getKey");
    T_DRM_Rights ro;
    int32_t id, roAmount;

    if (NULL == uid || NULL == KeyValue){
        ALOGE("drm_getKey failed! invalide params");
        return FALSE;
    }

    if (FALSE == drm_readFromUidTxt(uid, &id, GET_ID)) {
        ALOGE("drm_readFromUidTxt: failed");
        return FALSE;
    }

    if (FALSE == drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT)) {
        ALOGE("drm_writeOrReadInfo: failed");
        return FALSE;
    }

    if (roAmount <= 0) {
        ALOGE("drm_getKey: roAmount <= 0");
        return FALSE;
    }

    memset(&ro, 0, sizeof(T_DRM_Rights));
    roAmount = 1;
    if (FALSE == drm_writeOrReadInfo(id, &ro, &roAmount, GET_A_RO)){
        ALOGE("drm_writeOrReadInfo: failed");
        return FALSE;
    }

    memcpy(KeyValue, ro.KeyValue, DRM_KEY_LEN);
    ALOGI("Exit drm_getKey");
    return TRUE;
}

void drm_discardPaddingByte(uint8_t *decryptedBuf, int32_t *decryptedBufLen)
{
    int32_t tmpLen = *decryptedBufLen;
    int32_t i;

    if (NULL == decryptedBuf || *decryptedBufLen < 0)
        return;

    /* Check whether the last several bytes are padding or not */
    for (i = 1; i < decryptedBuf[tmpLen - 1]; i++) {
        if (decryptedBuf[tmpLen - 1 - i] != decryptedBuf[tmpLen - 1])
            break; /* Not the padding bytes */
    }
    if (i == decryptedBuf[tmpLen - 1]) /* They are padding bytes */
        *decryptedBufLen = tmpLen - i;
    return;
}

int32_t drm_aesDecBuffer(uint8_t * Buffer, int32_t * BufferLen, AES_KEY *key)
{
    uint8_t dbuf[3 * DRM_ONE_AES_BLOCK_LEN], buf[DRM_ONE_AES_BLOCK_LEN];
    uint64_t i, len, wlen = DRM_ONE_AES_BLOCK_LEN, curLen, restLen;
    uint8_t *pTarget, *pTargetHead;

    pTargetHead = Buffer;
    pTarget = Buffer;
    curLen = 0;
    restLen = *BufferLen;

    if (restLen > 2 * DRM_ONE_AES_BLOCK_LEN) {
        len = 2 * DRM_ONE_AES_BLOCK_LEN;
    } else {
        len = restLen;
    }
    memcpy(dbuf, Buffer, (size_t)len);
    restLen -= len;
    Buffer += len;

    if (len < 2 * DRM_ONE_AES_BLOCK_LEN) { /* The original file is less than one block in length */
        len -= DRM_ONE_AES_BLOCK_LEN;
        /* Decrypt from position len to position len + DRM_ONE_AES_BLOCK_LEN */
        AES_decrypt((dbuf + len), (dbuf + len), key);

        /* Undo the CBC chaining */
        for (i = 0; i < len; ++i)
            dbuf[i] ^= dbuf[i + DRM_ONE_AES_BLOCK_LEN];

        /* Output the decrypted bytes */
        memcpy(pTarget, dbuf, (size_t)len);
        pTarget += len;
    } else {
        uint8_t *b1 = dbuf, *b2 = b1 + DRM_ONE_AES_BLOCK_LEN, *b3 = b2 + DRM_ONE_AES_BLOCK_LEN, *bt;

        for (;;) { /* While some ciphertext remains, prepare to decrypt block b2 */
            /* Read in the next block to see if ciphertext stealing is needed */
            b3 = Buffer;
            if (restLen > DRM_ONE_AES_BLOCK_LEN) {
                len = DRM_ONE_AES_BLOCK_LEN;
            } else {
                len = restLen;
            }
            restLen -= len;
            Buffer += len;

            /* Decrypt the b2 block */
            AES_decrypt((uint8_t *)b2, buf, key);

            if (len == 0 || len == DRM_ONE_AES_BLOCK_LEN) { /* No ciphertext stealing */
                /* Unchain CBC using the previous ciphertext block in b1 */
                for (i = 0; i < DRM_ONE_AES_BLOCK_LEN; ++i)
                    buf[i] ^= b1[i];
            } else { /* Partial last block - use ciphertext stealing */
                wlen = len;
                /* Produce last 'len' bytes of plaintext by xoring with */
                /* The lowest 'len' bytes of next block b3 - C[N-1] */
                for (i = 0; i < len; ++i)
                    buf[i] ^= b3[i];

                /* Reconstruct the C[N-1] block in b3 by adding in the */
                /* Last (DRM_ONE_AES_BLOCK_LEN - len) bytes of C[N-2] in b2 */
                for (i = len; i < DRM_ONE_AES_BLOCK_LEN; ++i)
                    b3[i] = buf[i];

                /* Decrypt the C[N-1] block in b3 */
                AES_decrypt((uint8_t *)b3, (uint8_t *)b3, key);

                /* Produce the last but one plaintext block by xoring with */
                /* The last but two ciphertext block */
                for (i = 0; i < DRM_ONE_AES_BLOCK_LEN; ++i)
                    b3[i] ^= b1[i];

                /* Write decrypted plaintext blocks */
                memcpy(pTarget, b3, DRM_ONE_AES_BLOCK_LEN);
                pTarget += DRM_ONE_AES_BLOCK_LEN;
            }

            /* Write the decrypted plaintext block */
            memcpy(pTarget, buf, (size_t)wlen);
            pTarget += wlen;

            if (len != DRM_ONE_AES_BLOCK_LEN) {
                *BufferLen = pTarget - pTargetHead;
                return 0;
            }

            /* Advance the buffer pointers */
            bt = b1, b1 = b2, b2 = b3, b3 = bt;
        }
    }
    return 0;
}

int32_t drm_updateDcfDataLen(uint8_t* pDcfLastData, uint8_t* keyValue, int32_t* moreBytes)
{
    AES_KEY key;
    int32_t len = DRM_TWO_AES_BLOCK_LEN;

    if (NULL == pDcfLastData || NULL == keyValue)
        return FALSE;

    AES_set_decrypt_key(keyValue, DRM_KEY_LEN * 8, &key);

    if (drm_aesDecBuffer(pDcfLastData, &len, &key) < 0)
        return FALSE;

    drm_discardPaddingByte(pDcfLastData, &len);

    *moreBytes = DRM_TWO_AES_BLOCK_LEN - len;

    return TRUE;
}
static int32_t drm_UintvarData(uint8_t* result, uint32_t data, uint32_t size)
{
    int32_t highBitCount;
    int32_t flag = 0 , i = 0, j = 0;
    int32_t tmpData, cur;
    uint8_t wrk;
    int32_t lowSevenBitCount;

    highBitCount = (size * 8) % 7;
    lowSevenBitCount = (8 * size) / 7;
    tmpData = (uint32_t)data;

    if (highBitCount) {
        wrk = ((uint8_t)(tmpData  >> (32 - highBitCount)));/*highest bit set to 1*/
        if (wrk) {
            flag = 1;
            wrk |= 0x80;
            result[j] = wrk;
            j++;
        }
        tmpData <<= highBitCount;
    }

    for (i = 0; i < lowSevenBitCount - 1; i++) {
        cur = ((tmpData << (i * 7)) & 0xfe000000);
        if (flag || cur) {
            flag = 1;
            wrk = ((uint8_t)(cur >> 25)) | (0x80);/*highest bit set to 1*/
            result[j] = wrk;
            j++;
        }
    }

    wrk = ((uint8_t)data) & 0x7f;
    result[j] = wrk;
    j++;

    return j;
}

static void drm_generate128RandValue(uint8_t* key)
{
    int32_t i = 0;

    for (i = 0; i < DRM_KEY_LEN; i++) {
        key[i] = 1 + (int)(256.0 * rand() / (RAND_MAX + 1.0));
    }

    return;
}

/*there is just one right in db for all forward lock encrypted files, of course, just one key */
static void drm_generateFLRights(T_DRM_Rights* pRights, uint8_t* key, uint8_t* cid)
{
    strncpy((char*)pRights->Version, (char*)"1.0", strlen("1.0"));
    strncpy((char*)pRights->uid, (char*)cid, strlen((char*)cid));
    memcpy ((char*)pRights->KeyValue, key, DRM_KEY_LEN);
    pRights->bIsPlayable = TRUE;
    pRights->bIsDisplayable = TRUE;
    pRights->bIsExecuteable = TRUE;
    pRights->bIsPrintable = TRUE;
    pRights->bIsUnlimited = TRUE;

    pRights->PlayConstraint.Indicator = DRM_NO_CONSTRAINT;
    pRights->DisplayConstraint.Indicator = DRM_NO_CONSTRAINT;
    pRights->ExecuteConstraint.Indicator = DRM_NO_CONSTRAINT;
    pRights->PrintConstraint.Indicator = DRM_NO_CONSTRAINT;

    return;
}

static int32_t drm_saveFLRights(uint8_t* key, uint8_t* cid)
{
    int32_t id;
    T_DRM_Rights rights;

    memset(&rights, 0, sizeof(rights));

    ALOGD("drm_saveFLRights------ID===%d", cid);
    if (drm_readFromUidTxt(cid, &id, GET_ID) == FALSE) {
        drm_generateFLRights(&rights, key, cid);
        if (FALSE == drm_appendRightsInfo(&rights)) {
            ALOGD("-------------------------------FAILED TO APPENDRIGHTS INFO---------\n");
            return -1;
        }
    }

    return 0;
}

static int32_t drm_formatDCFHeader(uint8_t* pHeader, T_DRM_DM_Info* dmInfo)
{
    uint8_t* pCursor = NULL;
    uint8_t* pHeaderLenPos = NULL;
    uint8_t headerBuf[MAX_HEADER_LEN];
    uint8_t* pTmp = headerBuf;
    uint32_t headerLen = 0;
    int32_t varLen = 0;
    uint32_t encryptedDataLen = 0;

    ALOGD("enter drm_generateFLDCFHeader with dmInfo %p.\n", dmInfo);
    memset(headerBuf, 0, MAX_HEADER_LEN);

    pCursor = pHeader;

    /*version should be 1*/
    *pCursor = 1;
    pCursor++;

    /*ContentTypeLen*/
    *pCursor = strlen((char*)dmInfo->contentType);
    pCursor++;

    /*ContentURILen*/
    *pCursor = strlen((char*)dmInfo->contentID);
    pCursor++;

    /*ContentType*/
    strncpy((char*)pCursor, (char*)dmInfo->contentType, strlen((char*)dmInfo->contentType));
    pCursor += strlen((char*)dmInfo->contentType);

    /*ContentURI*/
    strncpy((char*)pCursor, (char*)dmInfo->contentID, strlen((char*)dmInfo->contentID));
    pCursor += strlen((char*)dmInfo->contentID);

    /*figure out the length of the header*/
    headerLen = pTmp - headerBuf;

    varLen = drm_UintvarData(pCursor, headerLen, sizeof(headerLen));
    pCursor += varLen;

    /*EncryptedDataLen*/
    if (dmInfo->contentLen % DRM_ONE_AES_BLOCK_LEN == 0)
        encryptedDataLen = dmInfo->contentLen + DRM_ONE_AES_BLOCK_LEN;
    else
        encryptedDataLen = dmInfo->contentLen + (DRM_ONE_AES_BLOCK_LEN - dmInfo->contentLen % DRM_ONE_AES_BLOCK_LEN) + DRM_ONE_AES_BLOCK_LEN;

    varLen = drm_UintvarData(pCursor, encryptedDataLen, sizeof(encryptedDataLen));
    pCursor += varLen;

    /*copy the header to pCursor*/
    memcpy(pCursor, headerBuf, headerLen);
    pCursor += headerLen;

    return pCursor - pHeader;
}

static void drm_addPaddingBytes(uint8_t* pRawBuf, int32_t remainBytes)
{
    uint8_t rfc2630Pad = DRM_ONE_AES_BLOCK_LEN - remainBytes;
    ALOGD("remainBytes is %d\n", remainBytes);

    ALOGD("rfc2630Pad is %d\n", rfc2630Pad);

    memset(pRawBuf + remainBytes, rfc2630Pad, DRM_ONE_AES_BLOCK_LEN - remainBytes);

    return;
}

/*when this function returns, the aes data will be filled into rawBuf*/
static void drm_encBuffer(T_DRM_Enc_Context* ctx, uint8_t* pRawBuf, int32_t encBufLen)
{
    uint8_t rawBlock[DRM_ONE_AES_BLOCK_LEN];
    uint8_t aesBlock[DRM_ONE_AES_BLOCK_LEN];
    uint8_t* pCursorBlockAhead = ctx->aesBlockAhead; /*pointing to the previous encrypted block*/
    int32_t remainBytes = 0;
    int32_t i = 0;
    int32_t j = 0;

    ALOGD("encBufLen is %d\n", encBufLen);

    for (i = 0; i < encBufLen / DRM_ONE_AES_BLOCK_LEN; i++) {
        memcpy(rawBlock, pRawBuf + i * DRM_ONE_AES_BLOCK_LEN, DRM_ONE_AES_BLOCK_LEN);

        for (j = 0; j < DRM_ONE_AES_BLOCK_LEN; j++) {
            rawBlock[j] ^= *(pCursorBlockAhead + j);
        }
        AES_encrypt(rawBlock, aesBlock, &ctx->enKey);

        memcpy(pRawBuf + i * DRM_ONE_AES_BLOCK_LEN, aesBlock, DRM_ONE_AES_BLOCK_LEN);

        pCursorBlockAhead = pRawBuf + i * DRM_ONE_AES_BLOCK_LEN;
    }

    memcpy(ctx->aesBlockAhead, pCursorBlockAhead, DRM_ONE_AES_BLOCK_LEN);

    ALOGD("out drm_encBuffer.\n");

    return;

}

static int32_t drm_handleLastSeveralBytes(T_DRM_Enc_Context* ctx, uint8_t* pRawBuf, int32_t bufLen)
{
    /*need to wait the remain bytes to format a buffer with 16 bytes*/
    /*smaller than one block, just pass*/

    /*
     * A sluggy bug
     */
    //ctx->rawBackupDataLen += bufLen;

    /*back up the raw data*/
    memcpy(ctx->rawBackupData + ctx->rawBackupDataLen, pRawBuf, bufLen);

    ctx->rawBackupDataLen += bufLen;

    return 0;
}

static int32_t drm_write2File(T_DRM_Enc_Context* ctx, uint8_t* pRawBuf, int32_t bufLen)
{
    int32_t remainBytes = 0;
    int32_t bytes = 0;
    int32_t writeBytes = 0;

    remainBytes = bufLen % DRM_ONE_AES_BLOCK_LEN;
    writeBytes = bufLen - remainBytes;
    drm_encBuffer(ctx, pRawBuf, writeBytes);
    ctx->curEncDataLen += writeBytes;

    if (write(ctx->dcfHandle, pRawBuf, writeBytes) != writeBytes) {
        ALOGE("write error.\n");
        return -1;
    }

    if (remainBytes > 0) {
        ALOGE("remainBytes is %d\n", remainBytes);
        memcpy(ctx->rawBackupData, pRawBuf + writeBytes, remainBytes);
        ctx->rawBackupDataLen = remainBytes;
    }

    return writeBytes;
}

int32_t drm_initEncSession(T_DRM_Enc_Context** ctx, T_DRM_DM_Info* dmInfo, T_DRM_Rights* rights, int64_t dcfHandle)
{
    (*ctx) = (T_DRM_Enc_Context*)malloc(sizeof(T_DRM_Enc_Context));
    if (*ctx == NULL) {
        ALOGE("malloc fail.\n");
        return -1;
    }

    memset((*ctx), 0, sizeof(T_DRM_Enc_Context));

    (*ctx)->dcfHandle = dcfHandle;
    (*ctx)->dmInfo = dmInfo;

    memset((*ctx)->rawBackupData, 0, DRM_ONE_AES_BLOCK_LEN);
    (*ctx)->rawBackupDataLen = 0;
    (*ctx)->curEncDataLen = 0;

    if (dmInfo->deliveryType == FORWARD_LOCK) {
        /*first time generate fl key, and there is only one key for all fl files*/
        strncpy((char*)dmInfo->contentID, (char*)FIXED_FL_RO_CID, strlen(FIXED_FL_RO_CID));
        if (drm_getKey(dmInfo->contentID, (*ctx)->encKey) == FALSE) {
            drm_generate128RandValue((*ctx)->encKey);
        }
        AES_set_encrypt_key((*ctx)->encKey, DRM_KEY_LEN * 8, &(*ctx)->enKey);
    } else if (dmInfo->deliveryType == COMBINED_DELIVERY) {
        ALOGD("dmInfo->contentID: %s\n", dmInfo->contentID);
        /*check if there has been a key corresponding to this contentID, if has, use the existed key*/
        if (drm_getKey(dmInfo->contentID, (*ctx)->encKey) == FALSE) {
            /*generate a random key for this content*/
            drm_generate128RandValue((*ctx)->encKey);
        }
        AES_set_encrypt_key((*ctx)->encKey, DRM_KEY_LEN * 8, &(*ctx)->enKey);
        (*ctx)->rights = rights;
    } else if (dmInfo->deliveryType == JAVA_APK) {
        strncpy((char*)dmInfo->contentID, (char*)FIXED_FL_RO_CID_JAVA, strlen(FIXED_FL_RO_CID_JAVA));
        strncpy((char*)dmInfo->contentType, (char*)HEADER_CONTENT_TYPE_JAVA_APK, strlen(HEADER_CONTENT_TYPE_JAVA_APK));
        if (drm_getKey(dmInfo->contentID, (*ctx)->encKey) == FALSE) {
            drm_generate128RandValue((*ctx)->encKey);
        }
        AES_set_encrypt_key((*ctx)->encKey, DRM_KEY_LEN * 8, &(*ctx)->enKey);
    }
    return 0;
}

int32_t drm_generateDCFHeader(T_DRM_Enc_Context* ctx)
{
    uint8_t header[MAX_HEADER_LEN];
    int32_t totalSize = 0;

    totalSize = drm_formatDCFHeader(header, ctx->dmInfo);

    if (write(ctx->dcfHandle, header, totalSize) != totalSize) {
        ALOGE("write DCF header error.\n");
        return -1;
    }
    return 0;
}

int32_t drm_encContent(T_DRM_Enc_Context* ctx, int64_t handle)
{
    int32_t bytes = 0;
    int32_t toReadBytes = 0;
    int32_t cipherLen = 0;
    uint8_t cipherBlock [DRM_ONE_AES_BLOCK_LEN];
    uint8_t encryptedBlock [DRM_ONE_AES_BLOCK_LEN];
    uint8_t *iv = NULL;
    int32_t n;
    int32_t padded = 0;
    int32_t remainCipherLen = 0;

    ALOGD ("In drm_encContent");

    drm_generate128RandValue(ctx->aesBlockAhead);

    if (write(ctx->dcfHandle, ctx->aesBlockAhead, DRM_ONE_AES_BLOCK_LEN) != DRM_ONE_AES_BLOCK_LEN) {
        ALOGE("write error.\n");
        return -1;
    }

    iv = ctx->aesBlockAhead;
    remainCipherLen = ctx->dmInfo->contentLen;
    ALOGD ("remain cipher length [%d]", remainCipherLen);

    //reposition to content start
    int res = lseek (handle, ctx->dmInfo->contentOffset, SEEK_SET);
    ALOGV("Seek result = %d", res);
    if(res == -1) {
        ALOGE("Problem on seek file , handle %d, error = %s", handle, strerror(errno));
    }

    do {
        //DRMV1_ASSERT (remainCipherLen < 0);
        toReadBytes = (remainCipherLen > DRM_ONE_AES_BLOCK_LEN) ? DRM_ONE_AES_BLOCK_LEN : remainCipherLen;

        if (toReadBytes > 0) {
            cipherLen = read (handle, cipherBlock, toReadBytes);
            // ALOGD("enLoop : cipherLen %d", cipherLen);
            if (cipherLen != toReadBytes) {
                ALOGE("read cipher data error from file, length [%d], errinfo [%s]", cipherLen, strerror(errno));
                return -1 ;
            }
            remainCipherLen -= cipherLen;
        }

        if (remainCipherLen == 0) {
            ALOGD ("Last block length [%d]", cipherLen);
            memset (cipherBlock + cipherLen, DRM_ONE_AES_BLOCK_LEN - cipherLen, DRM_ONE_AES_BLOCK_LEN - cipherLen);
            padded = 1;
        }

        for (n=0; n < AES_BLOCK_SIZE; ++n)
            cipherBlock[n] ^= iv[n];
        AES_encrypt(cipherBlock, encryptedBlock, &ctx->enKey);
        iv = encryptedBlock;

        if (write(ctx->dcfHandle, encryptedBlock, DRM_ONE_AES_BLOCK_LEN) != DRM_ONE_AES_BLOCK_LEN) {
            ALOGE("write encrypted buf to file error.\n");
            return -1;
        }
    } while (!padded);

    ALOGD ("out of drm_encContent");

    return 0;
}

void drm_abortEncSession(T_DRM_Enc_Context* ctx)
{
    if (ctx) {
        free(ctx);
    }
}

int32_t drm_releaseEncSession(T_DRM_Enc_Context* ctx)
{
    int32_t ret = 0;

    ALOGI("Enter releaseEncSession");

    if (ctx->dmInfo->deliveryType == FORWARD_LOCK) {
        ret = drm_saveFLRights(ctx->encKey, (uint8_t*)FIXED_FL_RO_CID);
    } else if (ctx->dmInfo->deliveryType == COMBINED_DELIVERY) {
        memcpy(ctx->rights->KeyValue, ctx->encKey, DRM_KEY_LEN);
        ret = drm_appendRightsInfo(ctx->rights);
    } else if (ctx->dmInfo->deliveryType == JAVA_APK) {
        ret = drm_saveFLRights(ctx->encKey, (uint8_t*)FIXED_FL_RO_CID_JAVA);
    }

    if (ctx) {
        free(ctx);
    }
    ALOGI("Exit releaseEncSession ret = %d ", ret);
    return ret;
}
