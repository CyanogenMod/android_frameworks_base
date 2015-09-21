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

#include <parser_dm.h>
#include <parser_dcf.h>
#include <svc_drm.h>
#include "log.h"

#define LOG_TAG "libdrm:parse_dm"
#define LOG_NDEBUG 0

#define DRM_SKIP_SPACE_TAB(p) while( (*(p) == ' ') || (*(p) == '\t') ) \
                                  p++
#define DRM_ERASE_TAIL_SPACE_TAB(p) while( ((p)[strlen((char *)p) - 1] == ' ') || ((p)[strlen((char *)p) - 1] == '\t') ) \
                                        (p)[strlen((char *)p) - 1] = 0x0

typedef enum _DM_PARSE_STATUS {
    DM_PARSE_START,
    DM_PARSING_RIGHTS,
    DM_PARSING_CONTENT,
    DM_PARSE_END
} DM_PARSE_STATUS;

static int drm_strnicmp(const uint8_t* s1, const uint8_t* s2, int32_t n)
{
    if (n < 0 || NULL == s1 || NULL == s2)
        return -1;

    if (n == 0)
        return 0;

    while (n-- != 0 && tolower(*s1) == tolower(*s2))
    {
        if (n == 0 || *s1 == '\0' || *s2 == '\0')
            break;
        s1++;
        s2++;
    }

    return tolower(*s1) - tolower(*s2);
}

const uint8_t * drm_strnstr(const uint8_t * str, const uint8_t * strSearch, int32_t len)
{
    int32_t i, stringLen;

    if (NULL == str || NULL == strSearch || len <= 0)
        return NULL;

    stringLen = strlen((char *)strSearch);
    for (i = 0; i < len - stringLen + 1; i++) {
        if (str[i] == *strSearch && 0 == memcmp(str + i, strSearch, stringLen))
            return str + i;
    }
    return NULL;
}

/* See parser_dm.h */
int32_t drm_parseDM(const uint8_t *buffer, int32_t bufferLen, T_DRM_DM_Info *pDmInfo)
{
    const uint8_t *pStart = NULL, *pEnd = NULL;
    const uint8_t *pBufferEnd;
    int32_t contentLen, leftLen;
    DM_PARSE_STATUS status = DM_PARSE_START;
    int32_t boundaryLen;

    if (NULL == buffer || bufferLen <= 0 || NULL == pDmInfo)
        return FALSE;

    /* Find the end of the input buffer */
    pBufferEnd = buffer + bufferLen;
    leftLen = bufferLen;

    /* Find out the boundary */
    pStart = drm_strnstr(buffer, (uint8_t *)"--", bufferLen);
    if (NULL == pStart)
        return FALSE; /* No boundary error */
    pEnd = pStart;

    /* Record the boundary */
    pEnd = drm_strnstr(pStart, (uint8_t *)DRM_NEW_LINE_CRLF, leftLen);
    /* if can not find the CRLF, return FALSE */
    if (NULL == pEnd)
        return FALSE;
    if ((pEnd - pStart) >= MAX_CONTENT_BOUNDARY_LEN)
        return FALSE;
    strncpy((char *)pDmInfo->boundary, (char *)pStart, pEnd - pStart);
    pDmInfo->boundary[MAX_CONTENT_BOUNDARY_LEN - 1] = 0;
    boundaryLen = strlen((char *)pDmInfo->boundary) + 2; /* 2 means: '\r' and '\n' */

    pEnd += 2; /* skip the '\r' and '\n' */
    pStart = pEnd;
    leftLen = pBufferEnd - pStart;
    int32_t count = 0;
    do {
        pDmInfo->transferEncoding = DRM_MESSAGE_CODING_7BIT; /* According RFC2045 chapter 6.1, the default value should be 7bit.*/
        strcpy((char *)pDmInfo->contentType, "text/plain");  /* According RFC2045 chapter 5.2, the default value should be "text/plain". */

        /* Deal the header information */
        while ((('\r' != *pStart) || ('\n' != *(pStart + 1))) && pStart < pBufferEnd) {
            pEnd = drm_strnstr(pStart, (uint8_t *)DRM_NEW_LINE_CRLF, leftLen);
            if (NULL == pEnd)
                return FALSE;

            if (0 != pDmInfo->deliveryType) { /* This means the delivery type has been confirmed */
                if (0 == strncmp((char *)pStart, HEADERS_TRANSFER_CODING, HEADERS_TRANSFER_CODING_LEN)) {
                    if (count == 1 ) {
                        status = DM_PARSING_CONTENT;
                    }
                    count++;
                    pStart += HEADERS_TRANSFER_CODING_LEN;
                    DRM_SKIP_SPACE_TAB(pStart);

                    if (0 == strncmp((char *)pStart, TRANSFER_CODING_TYPE_7BIT, pEnd - pStart))
                        pDmInfo->transferEncoding = DRM_MESSAGE_CODING_7BIT;
                    else if (0 == strncmp((char *)pStart, TRANSFER_CODING_TYPE_8BIT, pEnd - pStart))
                        pDmInfo->transferEncoding = DRM_MESSAGE_CODING_8BIT;
                    else if (0 == strncmp((char *)pStart, TRANSFER_CODING_TYPE_BINARY, pEnd - pStart))
                        pDmInfo->transferEncoding = DRM_MESSAGE_CODING_BINARY;
                    else if (0 == strncmp((char *)pStart, TRANSFER_CODING_TYPE_BASE64, pEnd - pStart))
                        pDmInfo->transferEncoding = DRM_MESSAGE_CODING_BASE64;
                    else
                        return FALSE; /* Unknown transferCoding error */
                } else if (0 == drm_strnicmp(pStart, (uint8_t *)HEADERS_CONTENT_TYPE, HEADERS_CONTENT_TYPE_LEN)) {
                    pStart += HEADERS_CONTENT_TYPE_LEN;
                    DRM_SKIP_SPACE_TAB(pStart);

                    if (pEnd - pStart > 0) {
                        if ((pEnd - pStart) >= MAX_CONTENT_TYPE_LEN)
                            return FALSE;
                        strncpy((char *)pDmInfo->contentType, (char *)pStart, pEnd - pStart);
                        pDmInfo->contentType[pEnd - pStart] = '\0';
                    }
                } else if (0 == drm_strnicmp(pStart, (uint8_t *)HEADERS_CONTENT_ID, HEADERS_CONTENT_ID_LEN)) {
                    uint8_t tmpBuf[MAX_CONTENT_ID] = {0};
                    uint8_t *pTmp;

                    pStart += HEADERS_CONTENT_ID_LEN;
                    DRM_SKIP_SPACE_TAB(pStart);

                    /* error: more than one content id */
                    if(drm_strnstr(pStart, (uint8_t*)HEADERS_CONTENT_ID, pBufferEnd - pStart)){
                        ALOGD("drm_dmParser: error: more than one content id\r\n");
                        return FALSE;
                    }

                    status = DM_PARSING_CONTENT; /* can go here means that the rights object has been parsed. */

                    /* Change the format from <...> to cid:... */
                    if (NULL != (pTmp = (uint8_t *)memchr((char *)pStart, '<', pEnd - pStart))) {
                        if ((pEnd - pTmp - 1) >= (int) sizeof(tmpBuf))
                            return FALSE;
                        strncpy((char *)tmpBuf, (char *)(pTmp + 1), pEnd - pTmp - 1);
                        tmpBuf[MAX_CONTENT_ID - 1] = 0;

                        if (NULL != (pTmp = (uint8_t *)memchr((char *)tmpBuf, '>', pEnd - pTmp - 1))) {
                            *pTmp = '\0';

                            memset(pDmInfo->contentID, 0, MAX_CONTENT_ID);
                            snprintf((char *)pDmInfo->contentID, MAX_CONTENT_ID, "%s%s", "cid:", (int8_t *)tmpBuf);
                        }
                    }
                }
            } else { /* First confirm delivery type, Forward_Lock, Combined Delivery or Separate Delivery */
                if (0 == drm_strnicmp(pStart, (uint8_t *)HEADERS_CONTENT_TYPE, HEADERS_CONTENT_TYPE_LEN)) {
                    pStart += HEADERS_CONTENT_TYPE_LEN;
                    DRM_SKIP_SPACE_TAB(pStart);

                    if (pEnd - pStart > 0) {
                        strncpy((char *)pDmInfo->contentType, (char *)pStart, pEnd - pStart);
                        pDmInfo->contentType[pEnd - pStart] = '\0';
                    }
                    DRM_ERASE_TAIL_SPACE_TAB(pDmInfo->contentType);
                    ALOGD ("pDmInfo->contentType [%s], len [%d]", pDmInfo->contentType, strlen ((char *)pDmInfo->contentType));
                    ALOGD ("---------pDmInfo->contentID[%s]----",pDmInfo->contentID);

                    if (0 == strcmp((char *)pDmInfo->contentType, DRM_MIME_TYPE_RIGHTS_XML)) {
                        pDmInfo->deliveryType = COMBINED_DELIVERY;
                        status = DM_PARSING_RIGHTS;
                    }
                    else if (0 == strcmp((char *)pDmInfo->contentType, DRM_MIME_TYPE_CONTENT)) {
                        pDmInfo->deliveryType = SEPARATE_DELIVERY_FL;
                        status = DM_PARSING_CONTENT;
                    }
                    else if (0 == pDmInfo->deliveryType) {
                        pDmInfo->deliveryType = FORWARD_LOCK;
                        status = DM_PARSING_CONTENT;
                    }
                }
            }
            pEnd += 2; /* skip the '\r' and '\n' */
            pStart = pEnd;
            leftLen = pBufferEnd - pStart;
        }
        pStart += 2; /* skip the second CRLF: "\r\n" */
        pEnd = pStart;

        /* Deal the content part, including rel or real content */
        while (leftLen > 0) {
            if (NULL == (pEnd = memchr(pEnd, '\r', leftLen))) {
                pEnd = pBufferEnd;
                break; /* no boundary found */
            }

            leftLen = pBufferEnd - pEnd;
            if (leftLen < boundaryLen) {
                pEnd = pBufferEnd;
                break; /* here means may be the boundary has been split */
            }

            if (('\n' == *(pEnd + 1)) && (0 == memcmp(pEnd + 2, pDmInfo->boundary, strlen((char *)pDmInfo->boundary))))
                break; /* find the boundary here */

            pEnd++;
            leftLen--;
        }

        if (pEnd >= pBufferEnd)
            contentLen = DRM_UNKNOWN_DATA_LEN;
        else
            contentLen = pEnd - pStart;

        switch(pDmInfo->deliveryType) {
        case FORWARD_LOCK:
            pDmInfo->contentLen = contentLen;
            pDmInfo->contentOffset = pStart - buffer;
            status = DM_PARSE_END;
            break;
        case COMBINED_DELIVERY:
            if (DM_PARSING_RIGHTS == status) {
                pDmInfo->rightsLen = contentLen;
                pDmInfo->rightsOffset = pStart - buffer;
            } else {
                pDmInfo->contentLen = contentLen;
                pDmInfo->contentOffset = pStart - buffer;
                status = DM_PARSE_END;
            }
            break;
        case SEPARATE_DELIVERY_FL:
            {
                T_DRM_DCF_Info dcfInfo;
                uint8_t* pEncData = NULL;
                /*these 2 only for sd-fl case*/
                pDmInfo->dcfLen = contentLen;
                pDmInfo->dcfOffset = pStart - buffer;
                memset(&dcfInfo, 0, sizeof(T_DRM_DCF_Info));
                if (DRM_UNKNOWN_DATA_LEN == contentLen)
                    contentLen = pEnd - pStart;
                if (FALSE == drm_dcfParser(pStart, contentLen, &dcfInfo, &pEncData))
                    return FALSE;

                pDmInfo->contentLen = dcfInfo.EncryptedDataLen;
                pDmInfo->contentOffset = pEncData - buffer;
                strcpy((char *)pDmInfo->contentType, (char *)dcfInfo.ContentType);
                strcpy((char *)pDmInfo->contentID, (char *)dcfInfo.ContentURI);
                strcpy((char *)pDmInfo->rightsIssuer, (char *)dcfInfo.Rights_Issuer);
                status = DM_PARSE_END;
            }
            break;
        default:
            return FALSE;
        }

        if (DM_PARSING_RIGHTS == status) {
            /* Here means the rights object data has been completed, boundary must exist */
            leftLen = pBufferEnd - pEnd;
            pStart = drm_strnstr(pEnd, pDmInfo->boundary, leftLen);
            if (NULL == pStart)
                return FALSE;
            leftLen = pBufferEnd - pStart;
            pEnd = drm_strnstr(pStart, (uint8_t *)DRM_NEW_LINE_CRLF, leftLen);
            if (NULL == pEnd)
                return FALSE; /* only rights object, no media object, error */

            pEnd += 2; /* skip the "\r\n" */
            pStart = pEnd;
        }
    } while (DM_PARSE_END != status);

    return TRUE;
}

int32_t
drm_findEndBoundary (uint64_t handle, uint8_t *boundary, uint32_t startOffset, uint32_t *endOffset)
{
    int32_t  pos;
    //According to RFC2046, boundary delimiter must be no longer than 70 bytes.
    uint8_t   endBoundary[MAX_CONTENT_BOUNDARY_LEN + 1] = {0x0};
    uint32_t  boundaryLen = 0;
    uint32_t  readBytes = 0;
    uint8_t   crlf[2] = {0x0};
    uint32_t  curPos;
    ALOGD ("In drm_findEndBoundary, boundary [%s], startOffset [%d] \n", boundary, startOffset);
    boundaryLen = strlen ((char *)boundary);
    ALOGD ("boundary [%s], len [%d]", boundary, boundaryLen);

    /*    From RFC2046
     *
     *    The boundary delimiter line following the last body part is a
     *    distinguished delimiter that indicates that no further body parts
     *    will follow.  Such a delimiter line is identical to the previous
     *    delimiter lines, with the addition of two more hyphens after the
     *    boundary parameter value.
     *
     *    --gc0pJq0M:08jU534c0p--
     */
    pos = -(boundaryLen + 2);

    do {
        ALOGD ("seek pos [%d] against the end\n", pos);
        curPos = lseek (handle, pos, SEEK_END);
        if (curPos <= startOffset) {
            ALOGE ("Didn't found out the end boundary delimiter, curPos [%d], startOffset [%d]\n",
                    curPos, startOffset);
            return -1;
        }

        readBytes = read (handle, endBoundary, boundaryLen + 2);
        if (readBytes < (boundaryLen + 2)) {
            ALOGE ("read the end boundary delimiter, read buf len [%d]", readBytes);
            continue;
        }

        ALOGD ("endBoundary [%s]", endBoundary);
        if ((strncmp ((char *)endBoundary, (char *)boundary, boundaryLen) == 0)
                && (endBoundary[boundaryLen] == '-')
                && (endBoundary[boundaryLen + 1] == '-')) {
            ALOGD ("Found out the end boundary delimiter !!!");
            break;
        }
        pos--;
    } while(1);

    //CRLF should be parsed
    curPos -= 2;
    curPos = lseek (handle, curPos, SEEK_SET);
    readBytes = read (handle, crlf, 2);
    if (readBytes < 2) {
        ALOGE ("read the end boundary delimiter error, read buf len [%d]", readBytes);
        return -1;
    }
    if ((crlf[0] != 13) || (crlf[1] != 10)) {
        ALOGE ("CRLF didn't found");
        return -1;
    }
    ALOGD ("start positin [%d], end position [%d] of real content\n", startOffset, curPos);
    if (curPos < startOffset) {
        ALOGE ("curPos [%d] < startOffset [%d]", curPos, startOffset);
        return -1;
    }

    *endOffset = curPos;

    ALOGD ("end boundary offset [%d]", *endOffset);
    return 0;
}
