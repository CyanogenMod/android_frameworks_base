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

#include <svc_drm.h>
#include <drm_inner.h>
#include <parser_dm.h>
#include <parser_dcf.h>
#include <parser_rel.h>
#include <drm_rights_manager.h>
#include <drm_time.h>
#include <drm_decoder.h>
#include <drm_file.h>
#include <drm_i18n.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <stdio.h>
#include "log.h"

#define LOG_TAG "libdrm:drm_api"
#define LOG_NDEBUG 0

/**
 * Current id.
 */
static int32_t curID = 0;

/**
 * The header pointer for the session list.
 */
static T_DRM_Session_Node* sessionTable = NULL;

#define TEMP_PATH "/data/local/.Drm/.drmtmp"

#define USING_MIX_SOLUTION

/*
 * license StartTime is current server time, device time maybe delay with server time.
 * for customer experience, have a grace time period.
 */
#define    DRM_GRACE_TIME_PERIOD        5*60

static void dumpRights(T_DRM_Rights rights);

#ifdef DRM_ROLLBACKCLOCK
static int32_t drm_checkRollBackClock (T_DRM_Rights_Constraint *XXConstraint)
{
    T_DB_TIME_SysTime curDateTime;
    T_DRM_DATETIME CurrentTime;

    ALOGD ("Enter drm_checkRollBackClock");

    if (0 == (XXConstraint->Indicator & (DRM_START_TIME_CONSTRAINT | DRM_END_TIME_CONSTRAINT))) {
        ALOGD ("Leave drm_checkRollBackClock, Not a date-time right");
        return DRM_SUCCESS;
    }

    DRM_time_getSysTime(&curDateTime);

    if (-1 == drm_checkDate(curDateTime.year, curDateTime.month, curDateTime.day,
                            curDateTime.hour, curDateTime.min, curDateTime.sec))
        return DRM_FAILURE;

    YMD_HMS_2_INT(curDateTime.year, curDateTime.month, curDateTime.day,
                  CurrentTime.date, curDateTime.hour, curDateTime.min,
                  curDateTime.sec, CurrentTime.time);

    if (XXConstraint->SavedDatetime.date > CurrentTime.date
            || (XXConstraint->SavedDatetime.date == CurrentTime.date
                    && XXConstraint->SavedDatetime.time > CurrentTime.time)) {
        ALOGE ("clock rollback, current date [%d] time [%d], saved date [%d] time [%d]",
                  CurrentTime.date, CurrentTime.time, XXConstraint->SavedDatetime.date,
                  XXConstraint->SavedDatetime.time);
        return DRM_ROLLBACK_CLOCK;
    }

    XXConstraint->SavedDatetime.date = CurrentTime.date;
    XXConstraint->SavedDatetime.time = CurrentTime.time;

    ALOGD ("Leave drm_checkRollBackClock, No rollback clock");
    return DRM_SUCCESS;
}
#endif //DRM_ROLLBACKCLOCK

/**
 * New a session.
 */
static T_DRM_Session_Node* newSession(T_DRM_Input_Data data)
{
    T_DRM_Session_Node* s = (T_DRM_Session_Node *)malloc(sizeof(T_DRM_Session_Node));

    if (NULL != s) {
        memset(s, 0, sizeof(T_DRM_Session_Node));

        s->sessionId = curID++;
        s->inputHandle = data.inputHandle;
        s->mimeType = data.mimeType;
        s->getInputDataLengthFunc = data.getInputDataLength;
        s->readInputDataFunc = data.readInputData;
        s->seekInputDataFunc = data.seekInputData;
    }

    return s;
}

/**
 * Free a session.
 */
static void freeSession(T_DRM_Session_Node* s)
{
    if (NULL == s)
        return;

    if (NULL != s->rawContent)
        free(s->rawContent);

    if (NULL != s->readBuf)
        free(s->readBuf);

    if (NULL != s->infoStruct)
        free(s->infoStruct);

    free(s);
}

/**
 * Add a session to list.
 */
static int32_t addSession(T_DRM_Session_Node* s)
{
    if (NULL == s)
        return -1;

    s->next = sessionTable;
    sessionTable = s;

    return s->sessionId;
}

/**
 * Get a session from the list.
 */
static T_DRM_Session_Node* getSession(int32_t sessionId)
{
    T_DRM_Session_Node* s;

    if (sessionId < 0 || NULL == sessionTable)
        return NULL;

    for (s = sessionTable; s != NULL; s = s->next) {
        if (sessionId == s->sessionId)
            return s;
    }

    return NULL;
}

/**
 * Remove a session from the list.
 */
static void removeSession(int32_t sessionId)
{
    T_DRM_Session_Node *curS, *preS;

    if (sessionId < 0 || NULL == sessionTable)
        return;

    if (sessionId == sessionTable->sessionId) {
        curS = sessionTable;
        sessionTable = curS->next;
        freeSession(curS);
        return;
    }

    for (preS = sessionTable; preS->next != NULL; preS = preS->next) {
        if (preS->next->sessionId == sessionId)
            curS = preS->next;
    }

    if (NULL == preS->next)
        return;

    preS->next = curS->next;
    freeSession(curS);
}

/**
 * Try to identify the mimetype according the input DRM data.
 */
static int32_t getMimeType(const uint8_t *buf, int32_t bufLen)
{
    const uint8_t *p;

    if (NULL == buf || bufLen <= 0)
        return TYPE_DRM_UNKNOWN;

    p = buf;

    /* check if it is DRM Content Format, only check the first field of Version, it must be "0x01" */
    if (0x01 == *p)
        return TYPE_DRM_CONTENT;

    /* check if it is DRM Forward Lock encrypted content */
    if (0xfe == *p)
        return TYPE_DRM_FL_CONTENT;

    /* check if it is DRM Combined Delivery encrypted content */
    if (0xff == *p)
        return TYPE_DRM_CD_CONTENT;

    /* check if it is DRM Message, only check the first two bytes, it must be the start flag of boundary: "--" */
    if (bufLen >= 2 && '-' == *p && '-' == *(p + 1))
        return TYPE_DRM_MESSAGE;

    /* check if it is DRM Rights XML format, only check the first several bytes, it must be: "<o-ex:rights" */
    if (bufLen >= 12 && 0 == strncmp("<o-ex:rights", (char *)p, 12))
        return TYPE_DRM_RIGHTS_XML;

    /* check if it is DRM Rights WBXML format, only check the first two bytes, it must be: 0x03, 0x0e */
    if (bufLen >= 2 && 0x03 == *p && 0x0e == *(p + 1))
        return TYPE_DRM_RIGHTS_WBXML;

    return TYPE_DRM_UNKNOWN;
}

static int32_t drm_skipCRLFinB64(const uint8_t* b64Data, int32_t len)
{
    const uint8_t* p;
    int32_t skipLen = 0;

    if (NULL == b64Data || len <= 0)
        return -1;

    p = b64Data;
    while (p - b64Data < len) {
        if ('\r' == *p || '\n'== *p)
            skipLen++;
        p++;
    }

    return skipLen;
}

static int32_t drm_scanEndBoundary(const uint8_t* pBuf, int32_t len, uint8_t* const boundary)
{
    const uint8_t* p;
    int32_t leftLen;
    int32_t boundaryLen;

    if (NULL == pBuf || len <=0 || NULL == boundary)
        return -1;

    p = pBuf;
    boundaryLen = strlen((char *)boundary) + 2; /* 2 means: '\r' and '\n' */
    leftLen = len - (p - pBuf);
    while (leftLen > 0) {
        if (NULL == (p = memchr(p, '\r', leftLen)))
            break;

        leftLen = len - (p - pBuf);
        if (leftLen < boundaryLen)
            return -2; /* here means may be the boundary has been split */

        if (('\n' == *(p + 1)) && (0 == memcmp(p + 2, boundary, strlen((char *)boundary))))
            return p - pBuf; /* find the boundary here */

        p++;
        leftLen--;
    }

    return len; /* no boundary found */
}

static int32_t drm_getLicenseInfo(T_DRM_Rights* pRights, T_DRM_Rights_Info* licenseInfo)
{
    if (NULL != licenseInfo && NULL != pRights) {
        strcpy((char *)licenseInfo->roId, (char *)pRights->uid);
        if (TRUE == pRights->bIsUnlimited) {
            licenseInfo->bIsUnlimited = TRUE;
            licenseInfo->displayRights.indicator = pRights->DisplayConstraint.Indicator;
            licenseInfo->playRights.indicator = pRights->PlayConstraint.Indicator;
            licenseInfo->executeRights.indicator = pRights->ExecuteConstraint.Indicator;
            licenseInfo->printRights.indicator = pRights->PrintConstraint.Indicator;

            return TRUE;
        }

        if (1 == pRights->bIsDisplayable) {
            licenseInfo->displayRights.indicator = pRights->DisplayConstraint.Indicator;
            #ifdef DRM_ROLLBACKCLOCK
            if (drm_checkRollBackClock (&pRights->DisplayConstraint) != DRM_SUCCESS)
                licenseInfo->displayRights.valid = FALSE;
            else
                licenseInfo->displayRights.valid = TRUE;
            #else
                licenseInfo->displayRights.valid = TRUE;
            #endif //DRM_ROLLBACKCLOCK

            licenseInfo->displayRights.count =
                pRights->DisplayConstraint.Count;
            licenseInfo->displayRights.startDate =
                pRights->DisplayConstraint.StartTime.date;
            licenseInfo->displayRights.startTime =
                pRights->DisplayConstraint.StartTime.time;
            licenseInfo->displayRights.endDate =
                pRights->DisplayConstraint.EndTime.date;
            licenseInfo->displayRights.endTime =
                pRights->DisplayConstraint.EndTime.time;
            licenseInfo->displayRights.intervalDate =
                pRights->DisplayConstraint.Interval.date;
            licenseInfo->displayRights.intervalTime =
                pRights->DisplayConstraint.Interval.time;
        }
        if (1 == pRights->bIsPlayable) {
            licenseInfo->playRights.indicator = pRights->PlayConstraint.Indicator;
            #ifdef DRM_ROLLBACKCLOCK
            if (drm_checkRollBackClock (&(pRights->PlayConstraint)) != DRM_SUCCESS)
                licenseInfo->playRights.valid = FALSE;
            else
                licenseInfo->playRights.valid = TRUE;
            #else
                licenseInfo->playRights.valid = TRUE;
            #endif //DRM_ROLLBACKCLOCK
            licenseInfo->playRights.count = pRights->PlayConstraint.Count;
            licenseInfo->playRights.startDate =
                pRights->PlayConstraint.StartTime.date;
            licenseInfo->playRights.startTime =
                pRights->PlayConstraint.StartTime.time;
            licenseInfo->playRights.endDate =
                pRights->PlayConstraint.EndTime.date;
            licenseInfo->playRights.endTime =
                pRights->PlayConstraint.EndTime.time;
            licenseInfo->playRights.intervalDate =
                pRights->PlayConstraint.Interval.date;
            licenseInfo->playRights.intervalTime =
                pRights->PlayConstraint.Interval.time;
        }
        if (1 == pRights->bIsExecuteable) {
            licenseInfo->executeRights.indicator = pRights->ExecuteConstraint.Indicator;
            #ifdef DRM_ROLLBACKCLOCK
            if (drm_checkRollBackClock (&pRights->ExecuteConstraint) != DRM_SUCCESS)
                licenseInfo->executeRights.valid = FALSE;
            else
                licenseInfo->executeRights.valid = TRUE;
            #else
                licenseInfo->executeRights.valid = TRUE;
            #endif //DRM_ROLLBACKCLOCK
            licenseInfo->executeRights.count =
                pRights->ExecuteConstraint.Count;
            licenseInfo->executeRights.startDate =
                pRights->ExecuteConstraint.StartTime.date;
            licenseInfo->executeRights.startTime =
                pRights->ExecuteConstraint.StartTime.time;
            licenseInfo->executeRights.endDate =
                pRights->ExecuteConstraint.EndTime.date;
            licenseInfo->executeRights.endTime =
                pRights->ExecuteConstraint.EndTime.time;
            licenseInfo->executeRights.intervalDate =
                pRights->ExecuteConstraint.Interval.date;
            licenseInfo->executeRights.intervalTime =
                pRights->ExecuteConstraint.Interval.time;
        }
        if (1 == pRights->bIsPrintable) {
            licenseInfo->printRights.indicator = pRights->PrintConstraint.Indicator;
            #ifdef DRM_ROLLBACKCLOCK
            if (drm_checkRollBackClock (&pRights->PrintConstraint) != DRM_SUCCESS)
                licenseInfo->printRights.valid = FALSE;
            else
                licenseInfo->printRights.valid = TRUE;
            #else
                licenseInfo->printRights.valid = TRUE;
            #endif //DRM_ROLLBACKCLOCK
            licenseInfo->printRights.count =
                pRights->PrintConstraint.Count;
            licenseInfo->printRights.startDate =
                pRights->PrintConstraint.StartTime.date;
            licenseInfo->printRights.startTime =
                pRights->PrintConstraint.StartTime.time;
            licenseInfo->printRights.endDate =
                pRights->PrintConstraint.EndTime.date;
            licenseInfo->printRights.endTime =
                pRights->PrintConstraint.EndTime.time;
            licenseInfo->printRights.intervalDate =
                pRights->PrintConstraint.Interval.date;
            licenseInfo->printRights.intervalTime =
                pRights->PrintConstraint.Interval.time;
        }
        return TRUE;
    }
    return FALSE;
}

static int32_t drm_addRightsNodeToList(T_DRM_Rights_Info_Node **ppRightsHeader,
                                       T_DRM_Rights_Info_Node *pInputRightsNode)
{
    T_DRM_Rights_Info_Node *pRightsNode;

    if (NULL == ppRightsHeader || NULL == pInputRightsNode)
        return FALSE;

    pRightsNode = (T_DRM_Rights_Info_Node *)malloc(sizeof(T_DRM_Rights_Info_Node));
    if (NULL == pRightsNode)
        return FALSE;

    memcpy(pRightsNode, pInputRightsNode, sizeof(T_DRM_Rights_Info_Node));
    pRightsNode->next = NULL;

    /* this means it is the first node */
    if (NULL == *ppRightsHeader)
        *ppRightsHeader = pRightsNode;
    else {
        T_DRM_Rights_Info_Node *pTmp;

        pTmp = *ppRightsHeader;
        while (NULL != pTmp->next)
            pTmp = pTmp->next;

        pTmp->next = pRightsNode;
    }
    return TRUE;
}

static int32_t drm_startConsumeRights(int32_t * bIsXXable,
                                      T_DRM_Rights_Constraint * XXConstraint,
                                      int32_t * writeFlag)
{
    T_DB_TIME_SysTime curDateTime;
    T_DRM_DATETIME CurrentTime;
    uint8_t countFlag = 0;

    memset(&CurrentTime, 0, sizeof(T_DRM_DATETIME));

    if (NULL == bIsXXable || 0 == *bIsXXable || NULL == XXConstraint || NULL == writeFlag)
        return DRM_FAILURE;

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_NO_CONSTRAINT)) /* Have utter right? */
        return DRM_SUCCESS;

    *bIsXXable = 0; /* Assume have invalid rights at first */
    *writeFlag = 0;

    if (0 != (XXConstraint->Indicator & (DRM_START_TIME_CONSTRAINT | DRM_END_TIME_CONSTRAINT | DRM_INTERVAL_CONSTRAINT))) {
        DRM_time_getSysTime(&curDateTime);

        if (-1 == drm_checkDate(curDateTime.year, curDateTime.month, curDateTime.day,
                                curDateTime.hour, curDateTime.min, curDateTime.sec))
            return DRM_FAILURE;

        YMD_HMS_2_INT(curDateTime.year, curDateTime.month, curDateTime.day,
                      CurrentTime.date, curDateTime.hour, curDateTime.min,
                      curDateTime.sec, CurrentTime.time);
    }

/* Implemented RollBack Clock.*/
    #ifdef DRM_ROLLBACKCLOCK
    if (drm_checkRollBackClock (XXConstraint) != DRM_SUCCESS) {
        *writeFlag = 1;
        *bIsXXable = 1; /* Assume have valid rights, but can't consume it as rollback clock */
        return DRM_ROLLBACK_CLOCK;
    }
    *writeFlag = 1;
    #endif //DRM_ROLLBACKCLOCK

#if 0
    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_COUNT_CONSTRAINT)) { /* Have count restrict? */
        *writeFlag = 1;
        /* If it has only one time for use, after use this function, we will delete this rights */
        if (XXConstraint->Count <= 0) {
            XXConstraint->Indicator &= ~DRM_COUNT_CONSTRAINT;
            return DRM_RIGHTS_EXPIRED;
        }

        if (XXConstraint->Count-- <= 1) {
            XXConstraint->Indicator &= ~DRM_COUNT_CONSTRAINT;
            countFlag = 1;
        }
    }
#endif

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_START_TIME_CONSTRAINT)) {
        if (XXConstraint->StartTime.date > CurrentTime.date ||
            (XXConstraint->StartTime.date == CurrentTime.date &&
             XXConstraint->StartTime.time >= CurrentTime.time + DRM_GRACE_TIME_PERIOD)) {
            *bIsXXable = 1;
            return DRM_RIGHTS_PENDING;
        }
    }

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_END_TIME_CONSTRAINT)) { /* Have end time restrict? */
        if (XXConstraint->EndTime.date < CurrentTime.date ||
            (XXConstraint->EndTime.date == CurrentTime.date &&
             XXConstraint->EndTime.time <= CurrentTime.time)) {
            *writeFlag = 1;
            XXConstraint->Indicator &= ~DRM_END_TIME_CONSTRAINT;
            return DRM_RIGHTS_EXPIRED;
        }
    }

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_INTERVAL_CONSTRAINT)) { /* Have interval time restrict? */
        int32_t year, mon, day, hour, min, sec, date, time;
        int32_t ret;

        XXConstraint->Indicator |= (DRM_START_TIME_CONSTRAINT | DRM_END_TIME_CONSTRAINT);
        XXConstraint->Indicator &= ~DRM_INTERVAL_CONSTRAINT; /* Write off interval right */
        *writeFlag = 1;

        if (XXConstraint->Interval.date == 0
            && XXConstraint->Interval.time == 0) {
            return DRM_RIGHTS_EXPIRED;
        }
        date = CurrentTime.date + XXConstraint->Interval.date;
        time = CurrentTime.time + XXConstraint->Interval.time;

        if ((XXConstraint->EndTime.date != 0)
                && (XXConstraint->EndTime.date < date
                        || (XXConstraint->EndTime.date == date
                                && XXConstraint->EndTime.time <= time))) {
             date = XXConstraint->EndTime.date;
             time = XXConstraint->EndTime.time;
        }

        INT_2_YMD_HMS(year, mon, day, date, hour, min, sec, time);

        if (sec > 59) {
            min += sec / 60;
            sec %= 60;
        }
        if (min > 59) {
            hour += min / 60;
            min %= 60;
        }
        if (hour > 23) {
            day += hour / 24;
            hour %= 24;
        }
        if (day > 31) {
            mon += day / 31;
            day %= 31;
        }
        if (mon > 12) {
            year += mon / 12;
            mon %= 12;
        }
        if (day > (ret = drm_monthDays(year, mon))) {
            day -= ret;
            mon++;
            if (mon > 12) {
                mon -= 12;
                year++;
            }
        }
        YMD_HMS_2_INT(year, mon, day, XXConstraint->EndTime.date, hour,
                      min, sec, XXConstraint->EndTime.time);
        XXConstraint->StartTime = CurrentTime;
    }

    if (0 != (uint8_t) (XXConstraint->Indicator & DRM_COUNT_CONSTRAINT)) { /* Have count restrict? */
        *writeFlag = 1;
        ALOGD("Before count %d\n", XXConstraint->Count);

        if (XXConstraint->Count-- <= 1) {
            XXConstraint->Indicator &= ~DRM_COUNT_CONSTRAINT;
            countFlag = 1;
            *bIsXXable = 0;
            ALOGD("remain the last time, *bIsXXable  [%d]\n", *bIsXXable );
            return DRM_SUCCESS;
        }

        ALOGD("After count %d\n", XXConstraint->Count);
    }
    //if (1 != countFlag)
    *bIsXXable = 1; /* Can go here ,so  right must be valid */

    return DRM_SUCCESS;
}

static int32_t drm_startCheckRights(int32_t * bIsXXable,
                                    T_DRM_Rights_Constraint * XXConstraint)
{
    T_DB_TIME_SysTime curDateTime;
    T_DRM_DATETIME CurrentTime;

    memset(&CurrentTime, 0, sizeof(T_DRM_DATETIME));

    if (NULL == bIsXXable || 0 == *bIsXXable || NULL == XXConstraint)
        return DRM_FAILURE;

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_NO_CONSTRAINT)) { /* Have utter right? */
        *bIsXXable = 1;
        ALOGD ("Leave %s, rights is valid", __FUNCTION__);
        return DRM_SUCCESS;
    }

    *bIsXXable = 0; /* Assume have invalid rights at first */
    #ifdef DRM_ROLLBACKCLOCK
    if (drm_checkRollBackClock (XXConstraint) != DRM_SUCCESS)
        return DRM_ROLLBACK_CLOCK;
    #endif //DRM_ROLLBACKCLOCK
    if (0 != (XXConstraint->Indicator & (DRM_START_TIME_CONSTRAINT | DRM_END_TIME_CONSTRAINT))) {
        DRM_time_getSysTime(&curDateTime);

        if (-1 == drm_checkDate(curDateTime.year, curDateTime.month, curDateTime.day,
                                curDateTime.hour, curDateTime.min, curDateTime.sec))
            return DRM_FAILURE;

        YMD_HMS_2_INT(curDateTime.year, curDateTime.month, curDateTime.day,
                      CurrentTime.date, curDateTime.hour, curDateTime.min,
                      curDateTime.sec, CurrentTime.time);
    }
#if 0
    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_COUNT_CONSTRAINT)) { /* Have count restrict? */
        if (XXConstraint->Count <= 0) {
            XXConstraint->Indicator &= ~DRM_COUNT_CONSTRAINT;
            return DRM_RIGHTS_EXPIRED;
        }
    }
#endif
    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_START_TIME_CONSTRAINT)) {
        if (XXConstraint->StartTime.date > CurrentTime.date ||
            (XXConstraint->StartTime.date == CurrentTime.date &&
             XXConstraint->StartTime.time >= CurrentTime.time + DRM_GRACE_TIME_PERIOD)) {
            *bIsXXable = 1;
            return DRM_RIGHTS_PENDING;
        }
    }

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_END_TIME_CONSTRAINT)) { /* Have end time restrict? */
        if (XXConstraint->EndTime.date < CurrentTime.date ||
            (XXConstraint->EndTime.date == CurrentTime.date &&
             XXConstraint->EndTime.time <= CurrentTime.time)) {
            XXConstraint->Indicator = DRM_NO_PERMISSION;
            return DRM_RIGHTS_EXPIRED;
        }
    }

    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_INTERVAL_CONSTRAINT)) { /* Have interval time restrict? */
        if (XXConstraint->Interval.date == 0 && XXConstraint->Interval.time == 0) {
            XXConstraint->Indicator = DRM_NO_PERMISSION;
            return DRM_RIGHTS_EXPIRED;
        }
    }
    if (0 != (uint8_t)(XXConstraint->Indicator & DRM_COUNT_CONSTRAINT)) { /* Have count restrict? */
        if (XXConstraint->Count <= 0) {
            ALOGD("Count [%d], invalid right", XXConstraint->Count);
            *bIsXXable = 1;
            return DRM_SUCCESS;
        }
    }
    *bIsXXable = 1;
    return DRM_SUCCESS;
}

int32_t drm_checkRoAndUpdate(int32_t id, int32_t permission)
{
    int32_t writeFlag = 0;
    int32_t roAmount;
    int32_t validRoAmount = 0;
    int32_t flag = DRM_FAILURE;
    int32_t i, j;
    T_DRM_Rights *pRo;
    T_DRM_Rights *pCurRo;
    int32_t * pNumOfPriority;
    int32_t iNum;
    T_DRM_Rights_Constraint * pCurConstraint;
    T_DRM_Rights_Constraint * pCompareConstraint;
    int priority[8] = {1, 2, 4, 3, 8, 6, 7, 5};

    if (FALSE == drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT))
        return DRM_FAILURE;

    validRoAmount = roAmount;
    if (roAmount < 1)
        return DRM_NO_RIGHTS;

    pRo = malloc(roAmount * sizeof(T_DRM_Rights));
    pCurRo = pRo;
    if (NULL == pRo)
        return DRM_FAILURE;

    if (FALSE == drm_writeOrReadInfo(id, pRo, &roAmount, GET_ALL_RO)) {
        free(pRo);
        return DRM_FAILURE;
    }

    /** check the right priority */
    pNumOfPriority = malloc(sizeof(int32_t) * roAmount);
    memset(pNumOfPriority, 0, sizeof(int32_t) * roAmount);
    for(i = 0; i < roAmount; i++) {
        iNum = roAmount - 1;
        for(j = 0; j < roAmount; j++) {
            if(i == j)
                continue;
            switch(permission) {
            case DRM_PERMISSION_PLAY:
                pCurConstraint = &pRo[i].PlayConstraint;
                pCompareConstraint = &pRo[j].PlayConstraint;
                break;
            case DRM_PERMISSION_DISPLAY:
                pCurConstraint = &pRo[i].DisplayConstraint;
                pCompareConstraint = &pRo[j].DisplayConstraint;
                break;
            case DRM_PERMISSION_EXECUTE:
                pCurConstraint = &pRo[i].ExecuteConstraint;
                pCompareConstraint = &pRo[j].ExecuteConstraint;
                break;
            case DRM_PERMISSION_PRINT:
                pCurConstraint = &pRo[i].PrintConstraint;
                pCompareConstraint = &pRo[j].PrintConstraint;
                break;
            default:
                free(pRo);
                free(pNumOfPriority);
                return DRM_FAILURE;
            }

            /**get priority by Indicator*/
            if(0 == (pCurConstraint->Indicator & DRM_NO_CONSTRAINT) &&
                0 == (pCompareConstraint->Indicator & DRM_NO_CONSTRAINT)) {
                    int num1, num2;
                    num1 = (pCurConstraint->Indicator & 0x0e) >> 1;
                    num2 = (pCompareConstraint->Indicator & 0x0e) >> 1;
                    if(priority[num1] > priority[num2]) {
                        iNum--;
                        continue;
                    } else if(priority[pCurConstraint->Indicator] < priority[pCompareConstraint->Indicator])
                        continue;
            } else if(pCurConstraint->Indicator > pCompareConstraint->Indicator) {
                iNum--;
                continue;
            } else if(pCurConstraint->Indicator < pCompareConstraint->Indicator)
                continue;

            if(0 != (pCurConstraint->Indicator & DRM_END_TIME_CONSTRAINT)) {
                if(pCurConstraint->EndTime.date < pCompareConstraint->EndTime.date) {
                    iNum--;
                    continue;
                } else if(pCurConstraint->EndTime.date > pCompareConstraint->EndTime.date)
                    continue;

                if(pCurConstraint->EndTime.time < pCompareConstraint->EndTime.time) {
                    iNum--;
                    continue;
                } else if(pCurConstraint->EndTime.date > pCompareConstraint->EndTime.date)
                    continue;
            }

            if(0 != (pCurConstraint->Indicator & DRM_INTERVAL_CONSTRAINT)) {
                if(pCurConstraint->Interval.date < pCompareConstraint->Interval.date) {
                    iNum--;
                    continue;
                } else if(pCurConstraint->Interval.date > pCompareConstraint->Interval.date)
                    continue;

                if(pCurConstraint->Interval.time < pCompareConstraint->Interval.time) {
                    iNum--;
                    continue;
                } else if(pCurConstraint->Interval.time > pCompareConstraint->Interval.time)
                    continue;
            }

            if(0 != (pCurConstraint->Indicator & DRM_COUNT_CONSTRAINT)) {
                if(pCurConstraint->Count < pCompareConstraint->Count) {
                    iNum--;
                    continue;
                } else if(pCurConstraint->Count > pCompareConstraint->Count)
                    continue;
            }

            if(i < j)
                iNum--;
        }
        pNumOfPriority[iNum] = i;
    }

    for (i = 0; i < validRoAmount; i++) {
        /** check the right priority */
        if (pNumOfPriority[i] >= validRoAmount)
            break;

        pCurRo = pRo + pNumOfPriority[i];

        switch (permission) {
        case DRM_PERMISSION_PLAY:
            flag =
                drm_startConsumeRights(&pCurRo->bIsPlayable,
                                       &pCurRo->PlayConstraint, &writeFlag);
            ALOGD ("after drm_startConsumeRights, pCurRo->bIsPlayable [%d], writeFlag [%d], count [%d], indicator [%x]",
                      pCurRo->bIsPlayable, writeFlag, pCurRo->PlayConstraint.Count, pCurRo->PlayConstraint.Indicator);
            break;
        case DRM_PERMISSION_DISPLAY:
            flag =
                drm_startConsumeRights(&pCurRo->bIsDisplayable,
                                       &pCurRo->DisplayConstraint,
                                       &writeFlag);
            break;
        case DRM_PERMISSION_EXECUTE:
            flag =
                drm_startConsumeRights(&pCurRo->bIsExecuteable,
                                       &pCurRo->ExecuteConstraint,
                                       &writeFlag);
            break;
        case DRM_PERMISSION_PRINT:
            flag =
                drm_startConsumeRights(&pCurRo->bIsPrintable,
                                       &pCurRo->PrintConstraint, &writeFlag);
            break;
        default:
            free(pNumOfPriority);
            free(pRo);
            return DRM_FAILURE;
        }

        /* Here confirm the valid RO amount and set the writeFlag */
        if (0 == pCurRo->bIsPlayable && 0 == pCurRo->bIsDisplayable &&
            0 == pCurRo->bIsExecuteable && 0 == pCurRo->bIsPrintable) {
            int32_t iCurPri;

            /** refresh the right priority */
            iCurPri = pNumOfPriority[i];
            for(j = i; j < validRoAmount - 1; j++)
                pNumOfPriority[j] = pNumOfPriority[j + 1];

            if(iCurPri != validRoAmount - 1) {
                memcpy(pCurRo, pRo + validRoAmount - 1,
                    sizeof(T_DRM_Rights));
                for(j = 0; j < validRoAmount -1; j++) {
                    if(validRoAmount - 1 == pNumOfPriority[j])
                        pNumOfPriority[j] = iCurPri;
                }
            }

            /* Here means it is not the last one RO, so the invalid RO should be deleted */
            writeFlag = 1;
            validRoAmount--; /* If current right is invalid */
            i--;
        }

        /* If the flag is TRUE, this means: we have found a valid RO, so break, no need to check other RO */
        if (DRM_SUCCESS == flag)
            break;
    }

    if (1 == writeFlag) {
        /* Delete the *.info first */
        //drm_removeIdInfoFile(id);

        if (FALSE == drm_writeOrReadInfo(id, pRo, &validRoAmount, SAVE_ALL_RO))
            flag = DRM_FAILURE;
    }

    free(pNumOfPriority);
    free(pRo);
    return flag;
}

static int32_t convertMimetype(const uint8_t *mimetype)
{
    if (mimetype == NULL)
        return TYPE_DRM_UNKNOWN;

    if (strncmp((const char*)mimetype, MIMETYPE_DRM_MESSAGE, sizeof(MIMETYPE_DRM_MESSAGE)) == 0)
        return TYPE_DRM_MESSAGE;
    else if (strncmp((const char*)mimetype, MIMETYPE_DRM_CONTENT, sizeof(MIMETYPE_DRM_CONTENT)) == 0)
        return TYPE_DRM_CONTENT;
    else if (strncmp((const char*)mimetype, MIMETYPE_DRM_RIGHTS_XML, sizeof(MIMETYPE_DRM_RIGHTS_XML)) == 0)
        return TYPE_DRM_RIGHTS_XML;
    else if (strncmp((const char*)mimetype, MIMETYPE_DRM_RIGHTS_WBXML, sizeof(MIMETYPE_DRM_RIGHTS_WBXML)) == 0)
        return TYPE_DRM_RIGHTS_WBXML;
    else
        return TYPE_DRM_UNKNOWN;
}

static int32_t getDRMObjectDataLength(int64_t handle)
{
    struct stat sbuf;

    if (fstat((int)handle, &sbuf) != 0) {
        ALOGE("Failed to stat %d file information\n", handle);
        return 0;
    }

    if (sbuf.st_size >= INT32_MAX) {
        ALOGE("DRM_file_getFileLength: file too big");
        return 0;
    }

    return (int32_t)sbuf.st_size;
}

static int32_t readDRMObjectData(int64_t handle,uint8_t *buf, int32_t bufLen)
{
    int32_t result;

    result = read(handle, buf, bufLen);

    if (result > 0 )
        return result;

    return (result == 0) ? -1 : 0;
}

static int32_t seekDRMObjectData(int64_t handle, int32_t offset)
{
    off_t result;

    result = lseek( (int)handle, (off_t)offset, SEEK_SET);
    return ( result == (off_t)-1 ) ? -1 : 0;
}

static void dumpDM(const T_DRM_DM_Info *dmInfo)
{
    ALOGD("DRM Message struct info:%p\n", dmInfo);
    ALOGD("\t content type:%s\n", dmInfo->contentType);
    ALOGD("\t content id:%s\n", dmInfo->contentID);
    ALOGD("\t boundary:%s\n", dmInfo->boundary);
    ALOGD("\t delivery type %d\n", dmInfo->deliveryType);
    ALOGD("\t transferEncoding:%d\n", dmInfo->transferEncoding);
    ALOGD("\t content offset:0x%0x\n", dmInfo->contentOffset);
    ALOGD("\t content length:%d\n", dmInfo->contentLen);
    ALOGD("\t dcf offset:0x%0x\n", dmInfo->dcfOffset);
    ALOGD("\t dcf length:%d\n", dmInfo->dcfLen);
    ALOGD("\t right offset:0x%0x\n", dmInfo->rightsOffset);
    ALOGD("\t rightsLen:%d\n", dmInfo->rightsLen);
    ALOGD("\t rightsURL:%s\n", dmInfo->rightsIssuer);
}

static void dumpDCF(T_DRM_DCF_Info info)
{
    ALOGD("info.Version %d\n", info.Version);
    ALOGD("info.ContentTypeLen %d\n", info.ContentTypeLen);
    ALOGD("info.ContentURILen %d\n", info.ContentURILen);
    ALOGD("info.ContentType %s\n", info.ContentType);
    ALOGD("info.ContentURI %s\n", info.ContentURI);
    ALOGD("info.HeadersLen %d\n", info.HeadersLen);
    ALOGD("info.EncryptedDataLen %d\n", info.EncryptedDataLen);
    ALOGD("info.Encryption_Method %s\n", info.Encryption_Method);
    ALOGD("info.Rights_Issuer %s\n", info.Rights_Issuer);
    ALOGD("info.Content_Name %s\n", info.Content_Name);
    ALOGD("info.ContentDescription %s\n", info.ContentDescription);
    ALOGD("info.ContentVendor %s\n", info.ContentVendor);
    ALOGD("info.Icon_URI %s\n", info.Icon_URI);
}

static void dumpRights(T_DRM_Rights rights)
{
    ALOGD("rights.Version: %s\n", rights.Version);
    ALOGD("rights.uid: %s\n", rights.uid);
//  ALOGD("rights.KeyValue: %s\n", rights.KeyValue);
    ALOGD("rights.bIsPlayable: %d\n", rights.bIsPlayable);
    ALOGD("rights.bIsDisplayable: %d\n", rights.bIsDisplayable);
    ALOGD("rights.bIsExcuteable: %d\n", rights.bIsExecuteable);
    ALOGD("rights.bIsPrintable: %d\n", rights.bIsPrintable);

    if (rights.bIsPlayable) {
        ALOGD("rights.PlayConstraint.Indicator: 0x%x\n", rights.PlayConstraint.Indicator);
#ifdef DRM_ROLLBACKCLOCK
        ALOGD("rights.PlayConstraint.SavedDatetime.date: %d\n", rights.PlayConstraint.SavedDatetime.date);
        ALOGD("rights.PlayConstraint.SavedDatetime.time: %d\n", rights.PlayConstraint.SavedDatetime.time);
#endif //DRM_ROLLBACKCLOCK
        ALOGD("rights.PlayConstraint.Count: %d\n", rights.PlayConstraint.Count);
        ALOGD("rights.PlayConstraint.StartTime.date: %d\n", rights.PlayConstraint.StartTime.date);
        ALOGD("rights.PlayConstraint.StartTime.time: %d\n", rights.PlayConstraint.StartTime.time);
        ALOGD("rights.PlayConstraint.EndTime.date: %d\n", rights.PlayConstraint.EndTime.date);
        ALOGD("rights.PlayConstraint.EndTime.time: %d\n", rights.PlayConstraint.EndTime.time);
        ALOGD("rights.PlayConstraint.Interval.date: %d\n", rights.PlayConstraint.Interval.date);
        ALOGD("rights.PlayConstraint.Interval.time: %d\n", rights.PlayConstraint.Interval.time);
    }

    if (rights.bIsDisplayable) {
        ALOGD("rights.DisplayConstraint.Indicator: 0x%x\n", rights.DisplayConstraint.Indicator);
#ifdef DRM_ROLLBACKCLOCK
        ALOGD("rights.PlayConstraint.SavedDatetime.date: %d\n", rights.PlayConstraint.SavedDatetime.date);
        ALOGD("rights.PlayConstraint.SavedDatetime.time: %d\n", rights.PlayConstraint.SavedDatetime.time);
#endif //DRM_ROLLBACKCLOCK
        ALOGD("rights.DisplayConstraint.Count: %d\n", rights.DisplayConstraint.Count);
        ALOGD("rights.DisplayConstraint.StartTime.date: %d\n", rights.DisplayConstraint.StartTime.date);
        ALOGD("rights.DisplayConstraint.StartTime.time: %d\n", rights.DisplayConstraint.StartTime.time);
        ALOGD("rights.DisplayConstraint.EndTime.date: %d\n", rights.DisplayConstraint.EndTime.date);
        ALOGD("rights.DisplayConstraint.EndTime.time: %d\n", rights.DisplayConstraint.EndTime.time);
        ALOGD("rights.DisplayConstraint.Interval.date: %d\n", rights.DisplayConstraint.Interval.date);
        ALOGD("rights.DisplayConstraint.Interval.time: %d\n", rights.DisplayConstraint.Interval.time);
    }

    if (rights.bIsExecuteable) {
        ALOGD("rights.ExecuteConstraint.Indicator: 0x%x\n", rights.ExecuteConstraint.Indicator);
#ifdef DRM_ROLLBACKCLOCK
        ALOGD("rights.PlayConstraint.SavedDatetime.date: %d\n", rights.PlayConstraint.SavedDatetime.date);
        ALOGD("rights.PlayConstraint.SavedDatetime.time: %d\n", rights.PlayConstraint.SavedDatetime.time);
#endif //DRM_ROLLBACKCLOCK
        ALOGD("rights.ExecuteConstraint.Count: %d\n", rights.ExecuteConstraint.Count);
        ALOGD("rights.ExecuteConstraint.StartTime.date: %d\n", rights.ExecuteConstraint.StartTime.date);
        ALOGD("rights.ExecuteConstraint.StartTime.time: %d\n", rights.ExecuteConstraint.StartTime.time);
        ALOGD("rights.ExecuteConstraint.EndTime.date: %d\n", rights.ExecuteConstraint.EndTime.date);
        ALOGD("rights.ExecuteConstraint.EndTime.time: %d\n", rights.ExecuteConstraint.EndTime.time);
        ALOGD("rights.ExecuteConstraint.Interval.date: %d\n", rights.ExecuteConstraint.Interval.date);
        ALOGD("rights.ExecuteConstraint.Interval.time: %d\n", rights.ExecuteConstraint.Interval.time);
    }

    if (rights.bIsPrintable) {
        ALOGD("rights.PrintConstraint.Indicator: 0x%x\n", rights.PrintConstraint.Indicator);
#ifdef DRM_ROLLBACKCLOCK
        ALOGD("rights.PlayConstraint.SavedDatetime.date: %d\n", rights.PlayConstraint.SavedDatetime.date);
        ALOGD("rights.PlayConstraint.SavedDatetime.time: %d\n", rights.PlayConstraint.SavedDatetime.time);
#endif //DRM_ROLLBACKCLOCK
        ALOGD("rights.PrintConstraint.Count: %d\n", rights.PrintConstraint.Count);
        ALOGD("rights.PrintConstraint.StartTime.date: %d\n", rights.PrintConstraint.StartTime.date);
        ALOGD("rights.PrintConstraint.StartTime.time: %d\n", rights.PrintConstraint.StartTime.time);
        ALOGD("rights.PrintConstraint.EndTime.date: %d\n", rights.PrintConstraint.EndTime.date);
        ALOGD("rights.PrintConstraint.EndTime.time: %d\n", rights.PrintConstraint.EndTime.time);
        ALOGD("rights.PrintConstraint.Interval.date: %d\n", rights.PrintConstraint.Interval.date);
        ALOGD("rights.PrintConstraint.Interval.time: %d\n", rights.PrintConstraint.Interval.time);
    }
}

static int32_t drm_installDRMMessage(const uint8_t *filepath)
{
    int32_t ret = DRM_FAILURE;
    uint8_t *rawContent = NULL;
    uint8_t *tmpBuf = NULL;
//  uint8_t dcfFileName[MAX_FILENAME_LEN + 4] = {'\0'}; //+".dcf"
    int64_t handle = -1;
    int32_t bytesConsumed;
    int32_t fileRes;
    int32_t fileLength;
    int32_t rawContentLen = 0;
    int64_t tmpHandle = -1;
    int32_t tmpFileLen = 0;
    int32_t result;
    int32_t len = 0;
    mode_t mode = S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH;

    T_DRM_DM_Info dmInfo;
    T_DRM_Enc_Context *ctx = NULL;
    T_DRM_Rights* pRights = NULL;
    uint32_t  endBoundaryOffset = 0;

    ALOGD("drm_installDRMMessage : Going to install DRM Message, filepath = %s", filepath);

    handle = open((char*) filepath, O_RDWR);
    ALOGI("handle value = %d", handle);
    if (handle < 0) {
        ALOGE("Failed to open file %s", filepath);
        return DRM_FAILURE;
    }

    fileLength = getDRMObjectDataLength(handle);
    if (fileLength <= 0){
        ALOGE("Invalid file length");
        ret = DRM_FAILURE;
        goto exit;
    }

    ALOGD("Got the file length %d", fileLength);

    /*remove the tmp file first to make sure there is no files*/
    unlink(TEMP_PATH);
    tmpHandle = open(TEMP_PATH, O_RDWR | O_CREAT, mode);
    if (tmpHandle < 0) {
        ALOGE("Failed to open tmp file ");
        ret = DRM_FAILURE;
        goto exit;
    }

    //assumed that 50k is enough for mime head or CD right
    if (fileLength > DRM_MAX_MALLOC_LEN)
        rawContentLen = DRM_MAX_MALLOC_LEN;
    else
        rawContentLen = fileLength;

    rawContent = (uint8_t*) malloc(rawContentLen);
    if (rawContent == NULL) {
        ALOGE("No enough memory for the file content");
        ret = DRM_FAILURE;
        goto exit;
    }

    fileRes = read(handle, rawContent, rawContentLen);
    ALOGD("Got %d from DRM_file_read", fileRes);
    if (fileRes != rawContentLen) {
        ALOGE("readed buf length [%d] < to read buf length [%d]", fileRes, rawContentLen);
        ret = DRM_FAILURE;
        goto exit;

    }

    ALOGI("Going to install DRM Message");

    memset(&dmInfo, 0, sizeof(T_DRM_DM_Info));

    if (FALSE == drm_parseDM(rawContent, fileRes, &dmInfo)) {
        ALOGE("Oooh, parse DRM Message failed");

        ret = DRM_FAILURE;
        goto exit;
    }

    ALOGI("ParseDm success");

    //For file size is more than 50k, find out the real content length
    if (dmInfo.contentLen == DRM_UNKNOWN_DATA_LEN) {
        if (drm_findEndBoundary (handle, dmInfo.boundary, dmInfo.contentOffset, &endBoundaryOffset)) {
            ret = DRM_FAILURE;
            goto exit;
        }

        dmInfo.contentLen = endBoundaryOffset - dmInfo.contentOffset;
    }

    dumpDM(&dmInfo);

    if (COMBINED_DELIVERY == dmInfo.deliveryType) {
        ALOGE("It is combined delivery, parse the right first.\n");
        pRights = (T_DRM_Rights*)malloc(sizeof(T_DRM_Rights));
        memset(pRights, 0, sizeof(T_DRM_Rights));
        if (drm_relParser(rawContent + dmInfo.rightsOffset, dmInfo.rightsLen, TYPE_DRM_RIGHTS_XML, pRights) == FALSE) {
            ret = DRM_FAILURE;
            goto exit;
        }
        memset(dmInfo.contentID, 0, MAX_CONTENT_ID);
        strcpy((char *)dmInfo.contentID , (char *)pRights->uid);
        dumpRights(*pRights);
    }

    if (SEPARATE_DELIVERY_FL == dmInfo.deliveryType) {
        //For file size is more than 50k, find out the real content length
        if (dmInfo.dcfLen == DRM_UNKNOWN_DATA_LEN) {
            if (drm_findEndBoundary(handle, dmInfo.boundary, dmInfo.dcfOffset, &endBoundaryOffset)) {
                ret = DRM_FAILURE;
                goto exit;
            }

            dmInfo.dcfLen = endBoundaryOffset - dmInfo.dcfOffset;
        }

        ALOGD ("FLSD dcfOffset [%d], dcfLen [%d]", dmInfo.dcfOffset, dmInfo.dcfLen);

        if (DRM_file_truncate(handle, dmInfo.dcfOffset, dmInfo.dcfOffset + dmInfo.dcfLen) != 0)
            ret = DRM_FAILURE;
        else
            ret = DRM_SUCCESS;

        goto exit;
    }

    
    result = drm_initEncSession(&ctx, &dmInfo, pRights, tmpHandle);
    ALOGI("initEncSession, result = %d", result);
    if (result < 0) {
        ALOGE("Failed to initEncSession with %d\n", result);
        ret = DRM_FAILURE;
        goto exit;
    }

    result = drm_generateDCFHeader(ctx);
    ALOGI("generateDCFHeader, retult = %d", result);
    if (result < 0) {
        ALOGE("Failed to generateDcfHeader with %d\n", result);
        ret = DRM_FAILURE;
        drm_abortEncSession(ctx);
        goto exit;
    }

    ALOGD("(fileRes:dmInfo.contentOffset:dmInfo.contentLen)(%d:%d:%d)\n",
            fileRes, dmInfo.contentOffset, dmInfo.contentLen);

    result = drm_encContent(ctx, handle);
    ALOGI("encContent , result = %d", result);
    if (result < 0) {
        ALOGE("Failed to encrypt content with %d\n", result);
        ret = DRM_FAILURE;
        drm_abortEncSession(ctx);
        goto exit;
    }

    drm_releaseEncSession(ctx);

    //Copy the encrypted content from temp file to original file
    DRM_file_copy (tmpHandle, handle);

    ret = DRM_SUCCESS;

exit:
    if (handle >= 0) {
        close(handle);
    }

    if (rawContent != NULL) {
        free(rawContent);
    }

    if (tmpHandle >= 0) {
        close(tmpHandle);
    }

    if (tmpBuf != NULL)
        free(tmpBuf);

    if (pRights) {
        free(pRights);
    }

    unlink(TEMP_PATH);

    if (ret == DRM_SUCCESS) {
        //returning drm type in case of success
        ALOGE("returing dmInfo.deliveryType = %d\n", dmInfo.deliveryType);
        ret = dmInfo.deliveryType;
    }
    return ret;
}

static int32_t drm_installDRMRights(const uint8_t *filepath, int32_t mimetype)
{
    int32_t ret = DRM_FAILURE;
    int64_t handle = -1;
    T_DRM_Input_Data data;
    T_DRM_Rights_Info rightsInfo;

    ALOGD("Going to install DRM rights\n");

    handle = open((char*)filepath, O_RDONLY);
    if (handle < 0) {
        ALOGE("Failed to open file %s\n", filepath);
        return DRM_FAILURE;
    }
    ALOGD("Going to install Rights object\n");

    memset(&data, 0, sizeof(data));
    memset(&rightsInfo, 0, sizeof(rightsInfo));

    data.inputHandle = (int64_t)handle;
    data.mimeType = mimetype;
    data.getInputDataLength = getDRMObjectDataLength;
    data.readInputData = readDRMObjectData;
    data.seekInputData = seekDRMObjectData;

    ALOGD("Going to SVC_drm_installRights\n");

    ret = SVC_drm_installRights(data, &rightsInfo);

    ALOGD("SVC_drm_installRights with result %d\n",ret);

exit:
    if (handle >= 0)
        close(handle);
    unlink((char*)filepath);

    return ret;
}

/*
 * Public Interface to install DRM Object
 */
int32_t SVC_drm_installDRMObject(const uint8_t *filepath, const uint8_t *mime)
{
    ALOGI("SVC_drm_installDRMObject");
    int32_t mimetype;

    if (filepath == NULL || mime == NULL) {
        ALOGE("Invalid argument for filepath %p, mimetype %p\n", filepath, mime);
        return DRM_MIMETYPE_INVALID;
    }

    ALOGD("Going to install DRM object with filepath %s, mimetype %s\n", filepath, mime);

    mimetype = convertMimetype(mime);
    if (mimetype == TYPE_DRM_UNKNOWN) {
        ALOGE("Failed to parse the mimetype with result %d\n", mimetype);
        return DRM_MIMETYPE_INVALID;
    }

    ALOGD("New interface to install DRM Object\n");

    switch (mimetype) {
        case TYPE_DRM_MESSAGE:
            return drm_installDRMMessage(filepath);

        case TYPE_DRM_CONTENT:
            ALOGD("Going to install DRM content, nothing special\n");
            /*
             * Need not anything special
             */
            //return DRM_SUCCESS;
            return SEPARATE_DELIVERY;
        case TYPE_DRM_RIGHTS_XML:
        case TYPE_DRM_RIGHTS_WBXML:
            return drm_installDRMRights(filepath, mimetype);

        default:
            ALOGD("Ahhh, not support mimetype %d\n", mimetype);

            return DRM_FAILURE;
    }
}

/* see svc_drm.h */
int32_t SVC_drm_installRights(T_DRM_Input_Data data, T_DRM_Rights_Info* pRightsInfo)
{
    uint8_t *buf;
    int32_t dataLen, bufLen;
    T_DRM_Rights rights;
    ALOGD("Into SVC_drm_installRights\n");
    if (0 == data.inputHandle)
        return DRM_RIGHTS_DATA_INVALID;

    /* Get input rights data length */
    dataLen = data.getInputDataLength(data.inputHandle);
    if (dataLen <= 0)
        return DRM_RIGHTS_DATA_INVALID;

    /* Check if the length is larger than DRM max malloc length */
    if (dataLen > DRM_MAX_MALLOC_LEN)
        bufLen = DRM_MAX_MALLOC_LEN;
    else
        bufLen = dataLen;

    buf = (uint8_t *)malloc(bufLen);
    if (NULL == buf)
        return DRM_FAILURE;

    /* Read input data to buffer */
    if (0 >= data.readInputData(data.inputHandle, buf, bufLen)) {
        free(buf);
        return DRM_RIGHTS_DATA_INVALID;
    }

    /* if the input mime type is unknown, DRM engine will try to recognize it. */
    if (TYPE_DRM_UNKNOWN == data.mimeType)
        data.mimeType = getMimeType(buf, bufLen);

    switch(data.mimeType) {
    case TYPE_DRM_MESSAGE: /* in case of Combined Delivery, extract the rights part to install */
        {
            T_DRM_DM_Info dmInfo;

            memset(&dmInfo, 0, sizeof(T_DRM_DM_Info));
            if (FALSE == drm_parseDM(buf, bufLen, &dmInfo)) {
                free(buf);
                return DRM_RIGHTS_DATA_INVALID;
            }

            /* if it is not Combined Delivery, it can not use to "SVC_drm_installRights" */
            if (COMBINED_DELIVERY != dmInfo.deliveryType || dmInfo.rightsOffset <= 0 || dmInfo.rightsLen <= 0) {
                free(buf);
                return DRM_RIGHTS_DATA_INVALID;
            }

            memset(&rights, 0, sizeof(T_DRM_Rights));
            if (FALSE == drm_relParser(buf + dmInfo.rightsOffset, dmInfo.rightsLen, TYPE_DRM_RIGHTS_XML, &rights)) {
                free(buf);
                return DRM_RIGHTS_DATA_INVALID;
            }
        }
        break;
    case TYPE_DRM_RIGHTS_XML:
    case TYPE_DRM_RIGHTS_WBXML:
        memset(&rights, 0, sizeof(T_DRM_Rights));
        if (FALSE == drm_relParser(buf, bufLen, data.mimeType, &rights)) {
            free(buf);
            return DRM_RIGHTS_DATA_INVALID;
        }
        dumpRights(rights);
        break;
    case TYPE_DRM_CONTENT: /* DCF should not using "SVC_drm_installRights", it should be used to open a session. */
    case TYPE_DRM_UNKNOWN:
    default:
        free(buf);
        return DRM_MEDIA_DATA_INVALID;
    }

    free(buf);

    /* append the rights information to DRM engine storage */
    if (FALSE == drm_appendRightsInfo(&rights))
        return DRM_FAILURE;

    memset(pRightsInfo, 0, sizeof(T_DRM_Rights_Info));
    drm_getLicenseInfo(&rights, pRightsInfo);
    ALOGD("SVC_drm_installRights Success\n");
    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_openSession(T_DRM_Input_Data data)
{
    int32_t session;
    int32_t dataLen;
    T_DRM_Session_Node* s;

    if (FALSE == data.inputHandle) {
        ALOGE("Fail! data.inputHandle = 0");
        return DRM_MEDIA_DATA_INVALID;
    }

    ALOGI("Get input data length");
    /* Get input data length */
    dataLen = data.getInputDataLength(data.inputHandle);
    if (dataLen <= 0){
        ALOGE("Fail! Invalide data. dataLen <= 0 ");
        return DRM_MEDIA_DATA_INVALID;
    }

    s = newSession(data);
    if (NULL == s)
        return DRM_FAILURE;

    ALOGI("Check if the length is larger than DRM max malloc length ");
    /* Check if the length is larger than DRM max malloc length */
    if (dataLen > DRM_MAX_MALLOC_LEN)
        s->rawContentLen = DRM_MAX_MALLOC_LEN;
    else
        s->rawContentLen = dataLen;

    s->rawContent = (uint8_t *)malloc(s->rawContentLen);
    if (NULL == s->rawContent)
        return DRM_FAILURE;

    ALOGI("Read input data to buffer");
    /* Read input data to buffer */
    if (0 >= data.readInputData(data.inputHandle, s->rawContent, s->rawContentLen)) {
        freeSession(s);
        return DRM_MEDIA_DATA_INVALID;
    }

    ALOGI("if the input mime type is unknown, DRM engine will try to recognize it.");
    /* if the input mime type is unknown, DRM engine will try to recognize it. */
    if (TYPE_DRM_UNKNOWN == data.mimeType)
        data.mimeType = getMimeType(s->rawContent, s->rawContentLen);
    ALOGD("Got the mime type is %d\n", data.mimeType);
    switch(data.mimeType) {
    case TYPE_DRM_MESSAGE:
        {
            T_DRM_DM_Info dmInfo;

            memset(&dmInfo, 0, sizeof(T_DRM_DM_Info));
            if (FALSE == drm_parseDM(s->rawContent, s->rawContentLen, &dmInfo)) {
                freeSession(s);
                return DRM_MEDIA_DATA_INVALID;
            }

            s->deliveryMethod = dmInfo.deliveryType;
            if (COMBINED_DELIVERY == dmInfo.deliveryType) {
                T_DRM_Rights* pRights = NULL;
                ALOGE("It is combined delivery, parse the right first.\n");
                pRights = (T_DRM_Rights*)malloc(sizeof(T_DRM_Rights));
                memset(pRights, 0, sizeof(T_DRM_Rights));
                if (drm_relParser(s->rawContent + dmInfo.rightsOffset, dmInfo.rightsLen, TYPE_DRM_RIGHTS_XML, pRights) == FALSE) {
                    return DRM_FAILURE;
                }
                memset(dmInfo.contentID, 0, MAX_CONTENT_ID);
                strcpy((char *)dmInfo.contentID , (char *)pRights->uid);
                strcpy((char *)s->contentID , (char *)pRights->uid);
                dumpRights(*pRights);
                if (pRights) free(pRights);
                pRights = NULL;
            }

            if (SEPARATE_DELIVERY_FL == s->deliveryMethod)
                s->contentLength = DRM_UNKNOWN_DATA_LEN;
            else
                s->contentLength = dmInfo.contentLen;

            s->transferEncoding = dmInfo.transferEncoding;
            s->contentOffset = dmInfo.contentOffset;
            s->bEndData = FALSE;
            strcpy((char *)s->contentType, (char *)dmInfo.contentType);
            strcpy((char *)s->contentID, (char *)dmInfo.contentID);

            if (SEPARATE_DELIVERY_FL == s->deliveryMethod) {
                s->infoStruct = (T_DRM_Dcf_Node *)malloc(sizeof(T_DRM_Dcf_Node));
                if (NULL == s->infoStruct)
                    return DRM_FAILURE;
                memset(s->infoStruct, 0, sizeof(T_DRM_Dcf_Node));

                ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength = dmInfo.contentLen;
                strcpy((char *)((T_DRM_Dcf_Node *)(s->infoStruct))->rightsIssuer, (char *)dmInfo.rightsIssuer);
                break;
            }

            if (DRM_MESSAGE_CODING_BASE64 == s->transferEncoding) {
                s->infoStruct = (T_DRM_DM_Base64_Node *)malloc(sizeof(T_DRM_DM_Base64_Node));
                if (NULL == s->infoStruct)
                    return DRM_FAILURE;
                memset(s->infoStruct, 0, sizeof(T_DRM_DM_Base64_Node));

                strcpy((char *)((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary, (char *)dmInfo.boundary);
            } else {
                s->infoStruct = (T_DRM_DM_Binary_Node *)malloc(sizeof(T_DRM_DM_Binary_Node));
                if (NULL == s->infoStruct)
                    return DRM_FAILURE;
                memset(s->infoStruct, 0, sizeof(T_DRM_DM_Binary_Node));

                strcpy((char *)((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary, (char *)dmInfo.boundary);
            }


            if (DRM_MESSAGE_CODING_BASE64 == s->transferEncoding) {
                if (s->contentLength > 0) {
                    int32_t encLen, decLen;

                    encLen = s->contentLength;
                    decLen = encLen / DRM_B64_ENC_BLOCK * DRM_B64_DEC_BLOCK;

                    decLen = drm_decodeBase64(s->rawContent, decLen, s->rawContent + s->contentOffset, &encLen);
                    s->contentLength = decLen;
                } else {
                    int32_t encLen = DRM_MAX_MALLOC_LEN - s->contentOffset, decLen;
                    int32_t skipLen, needBytes, i;
                    uint8_t *pStart;
                    int32_t res, bFoundBoundary = FALSE;

                    pStart = s->rawContent + s->contentOffset;
                    if (-1 == (skipLen = drm_skipCRLFinB64(pStart, encLen))) {
                        freeSession(s);
                        return DRM_FAILURE;
                    }

                    needBytes = DRM_B64_ENC_BLOCK - ((encLen - skipLen) % DRM_B64_ENC_BLOCK);
                    if (needBytes < DRM_B64_ENC_BLOCK) {
                        s->rawContent = (uint8_t *)realloc(s->rawContent, DRM_MAX_MALLOC_LEN + needBytes);
                        if (NULL == s->rawContent) {
                            freeSession(s);
                            return DRM_FAILURE;
                        }

                        i = 0;
                        while (i < needBytes) {
                            if (-1 != data.readInputData(data.inputHandle, s->rawContent + DRM_MAX_MALLOC_LEN + i, 1)) {
                                if ('\r' == *(s->rawContent + DRM_MAX_MALLOC_LEN + i) || '\n' == *(s->rawContent + DRM_MAX_MALLOC_LEN + i))
                                    continue;
                                i++;
                            } else
                                break;
                        }
                        encLen += i;
                    }

                    res = drm_scanEndBoundary(pStart, encLen, ((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary);
                    if (-1 == res) {
                        freeSession(s);
                        return DRM_FAILURE;
                    }
                    if (-2 == res) { /* may be there is a boundary */
                        int32_t boundaryLen, leftLen, readBytes;
                        char* pTmp = memrchr(pStart, '\r', encLen);

                        if (NULL == pTmp) {
                            freeSession(s);
                            return DRM_FAILURE; /* conflict */
                        }
                        boundaryLen = strlen((char *)((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary) + 2; /* 2 means: '\r''\n' */
                        s->readBuf = (uint8_t *)malloc(boundaryLen);
                        if (NULL == s->readBuf) {
                            freeSession(s);
                            return DRM_FAILURE;
                        }
                        s->readBufOff = encLen - ((uint8_t *)pTmp - pStart);
                        s->readBufLen = boundaryLen - s->readBufOff;
                        memcpy(s->readBuf, pTmp, s->readBufOff);
                        readBytes = data.readInputData(data.inputHandle, s->readBuf + s->readBufOff, s->readBufLen);
                        if (-1 == readBytes || readBytes < s->readBufLen) {
                            freeSession(s);
                            return DRM_MEDIA_DATA_INVALID;
                        }

                        if (0 == drm_scanEndBoundary(s->readBuf, boundaryLen, ((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary)) {
                            encLen = (uint8_t *)pTmp - pStart; /* yes, it is the end boundary */
                            bFoundBoundary = TRUE;
                        }
                    } else {
                        if (res >= 0 && res < encLen) {
                            encLen = res;
                            bFoundBoundary = TRUE;
                        }
                    }

                    decLen = encLen / DRM_B64_ENC_BLOCK * DRM_B64_DEC_BLOCK;
                    decLen = drm_decodeBase64(s->rawContent, decLen, s->rawContent + s->contentOffset, &encLen);
                    ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeDataLen = decLen;
                    if (bFoundBoundary)
                        s->contentLength = decLen;
                }
            } else {
                /* binary data */
                if (DRM_UNKNOWN_DATA_LEN == s->contentLength) {
                    /* try to check whether there is boundary may be split */
                    int32_t res, binContentLen;
                    uint8_t* pStart;
                    int32_t bFoundBoundary = FALSE;

                    pStart = s->rawContent + s->contentOffset;
                    binContentLen = s->rawContentLen - s->contentOffset;
                    res = drm_scanEndBoundary(pStart, binContentLen, ((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary);

                    if (-1 == res) {
                        freeSession(s);
                        return DRM_FAILURE;
                    }

                    if (-2 == res) { /* may be the boundary is split */
                        int32_t boundaryLen, leftLen, readBytes;
                        char* pTmp = memrchr(pStart, '\r', binContentLen);

                        if (NULL == pTmp) {
                            freeSession(s);
                            return DRM_FAILURE; /* conflict */
                        }

                        boundaryLen = strlen((char *)((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary) + 2; /* 2 means: '\r''\n' */
                        s->readBuf = (uint8_t *)malloc(boundaryLen);
                        if (NULL == s->readBuf) {
                            freeSession(s);
                            return DRM_FAILURE;
                        }
                        s->readBufOff = binContentLen - ((uint8_t *)pTmp - pStart);
                        s->readBufLen = boundaryLen - s->readBufOff;
                        memcpy(s->readBuf, pTmp, s->readBufOff);
                        readBytes = data.readInputData(data.inputHandle, s->readBuf + s->readBufOff, s->readBufLen);
                        if (-1 == readBytes || readBytes < s->readBufLen) {
                            freeSession(s);
                            return DRM_MEDIA_DATA_INVALID;
                        }

                        if (0 == drm_scanEndBoundary(s->readBuf, boundaryLen, ((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary)) {
                            binContentLen = (uint8_t *)pTmp - pStart; /* yes, it is the end boundary */
                            bFoundBoundary = TRUE;
                        }
                    } else {
                        if (res >= 0 && res < binContentLen) {
                            binContentLen = res;
                            bFoundBoundary = TRUE;
                        }
                    }

                    if (bFoundBoundary)
                        s->contentLength = binContentLen;
                }
            }
        }
        break;
    case TYPE_DRM_FL_CONTENT:
    case TYPE_DRM_CD_CONTENT:
            s->isEncrypted = 1;
        /** Fall through */
    case TYPE_DRM_CONTENT:
        {
            T_DRM_DCF_Info dcfInfo;
            uint8_t* pEncData = NULL;

            memset(&dcfInfo, 0, sizeof(T_DRM_DCF_Info));
            ALOGI("start dcf parser");
            if (FALSE == drm_dcfParser(s->rawContent, s->rawContentLen, &dcfInfo, &pEncData)) {
                freeSession(s);
                ALOGD("DRM_MEDIA_DATA_INVALID");
                return DRM_MEDIA_DATA_INVALID;
            }
            dumpDCF(dcfInfo);

            s->infoStruct = (T_DRM_Dcf_Node *)malloc(sizeof(T_DRM_Dcf_Node));
            if (NULL == s->infoStruct)
                return DRM_FAILURE;
            memset(s->infoStruct, 0, sizeof(T_DRM_Dcf_Node));
            ALOGD("data.mimeType is %d\n", data.mimeType);
            //s->deliveryMethod = SEPARATE_DELIVERY;
            if (data.mimeType == TYPE_DRM_FL_CONTENT)
                s->deliveryMethod = FORWARD_LOCK;
            else if (data.mimeType == TYPE_DRM_CD_CONTENT)
                s->deliveryMethod = COMBINED_DELIVERY;
            else
                s->deliveryMethod = SEPARATE_DELIVERY;

            s->deliveryMethod = SEPARATE_DELIVERY;
            s->contentLength = dcfInfo.DecryptedDataLen;
            ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength = dcfInfo.EncryptedDataLen;
            s->contentOffset = pEncData - s->rawContent;
            strcpy((char *)s->contentType, (char *)dcfInfo.ContentType);
            strcpy((char *)s->contentID, (char *)dcfInfo.ContentURI);
            strcpy((char *)((T_DRM_Dcf_Node *)(s->infoStruct))->rightsIssuer, (char *)dcfInfo.Rights_Issuer);
            ALOGD ("try to cache content key");
            drm_getKey(s->contentID, s->key);
        }
        break;
    case TYPE_DRM_RIGHTS_XML:   /* rights object should using "SVC_drm_installRights", it can not open a session */
    case TYPE_DRM_RIGHTS_WBXML: /* rights object should using "SVC_drm_installRights", it can not open a session */
    case TYPE_DRM_UNKNOWN:
    default:
        freeSession(s);
        return DRM_MEDIA_DATA_INVALID;
    }

    if ((SEPARATE_DELIVERY_FL == s->deliveryMethod || SEPARATE_DELIVERY == s->deliveryMethod) &&
        s->contentOffset + ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength <= DRM_MAX_MALLOC_LEN) {
        uint8_t keyValue[DRM_KEY_LEN];
        uint8_t lastDcfBuf[DRM_TWO_AES_BLOCK_LEN];
        int32_t seekPos, moreBytes;

        if (TRUE == drm_getKey(s->contentID, keyValue)) {
            seekPos = s->contentOffset + ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength - DRM_TWO_AES_BLOCK_LEN;
            //when the file size is bigger than 50K, we will read the two last bolcks
            //to calculate file size.
            //when google give another solution that can resolve 50K issues, then please
            //remove this codes.   b073/Wei Yongqiang
#ifdef USING_GOOGLE_SOLUTION
            ALOGD("Restore to google solution with delivery method %d\n", s->deliveryMethod);
            memcpy(lastDcfBuf, s->rawContent + seekPos, DRM_TWO_AES_BLOCK_LEN);
#elif defined( USING_MIX_SOLUTION)
            //ALOGD("Using mixed solution with deliverMethod %d\n", s->deliveryMethod); //Incorrect log
            if (s->seekInputDataFunc != NULL) {
                s->seekInputDataFunc(s->inputHandle, seekPos);
                if (-1 == s->readInputDataFunc(s->inputHandle, lastDcfBuf, DRM_TWO_AES_BLOCK_LEN)) {
                    ALOGE("SVC_drm_getContentLength read fail");
                    return DRM_MEDIA_DATA_INVALID;
                }
            } else{
                memcpy(lastDcfBuf, s->rawContent + seekPos, DRM_TWO_AES_BLOCK_LEN);
            }
#else
            ALOGD("Using the seek solution instead of google with delivery method %d\n", s->deliveryMethod);
            s->seekInputDataFunc(s->inputHandle, seekPos);
            if (-1 == s->readInputDataFunc(s->inputHandle, lastDcfBuf, DRM_TWO_AES_BLOCK_LEN)) {
                LOGE("SVC_drm_getContentLength read fail");
                return DRM_MEDIA_DATA_INVALID;
            }
#endif
            if (TRUE == drm_updateDcfDataLen(lastDcfBuf, keyValue, &moreBytes)) {
                s->contentLength = ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength;
                s->contentLength -= moreBytes;
            }
        }
    }

    session = addSession(s);
    if (-1 == session)
        return DRM_FAILURE;

    return session;
}

/* see svc_drm.h */
int32_t SVC_drm_getDeliveryMethod(int32_t session)
{
    T_DRM_Session_Node* s;

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    return s->deliveryMethod;
}

/* see svc_drm.h */
int32_t SVC_drm_getContentType(int32_t session, uint8_t* mediaType)
{
    T_DRM_Session_Node* s;
    uint8_t *pSemicolon = NULL;

    ALOGD("Enter in SVC_drm_getContentType.\n");

    if (session < 0 || NULL == mediaType)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    strcpy((char *)mediaType, (char *)s->contentType);

    /*
     * According RFC2045 chapter 5.1, Syntax of Content-Type Header Field
     *      content := "Content-Type" ":" type "/" subtype
     *                 *(";" parameter)
     *                 ; Matching of media type and subtype
     *                 ; is ALWAYS case-insensitive.
     *
     * Only get the string "type/subtype", omit the remaining string
     */

    pSemicolon = strstr(mediaType, DRM_SEMICOLON);
    if (pSemicolon)
        *pSemicolon = '\0';

    ALOGD("Leave SVC_drm_getContentType, contentType = [%s]", mediaType);

    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_getContentID(int32_t session, uint8_t* contentID)
{
    T_DRM_Session_Node* s;
    ALOGD("Enter in SVC_drm_getContentID.\n");

    if (session < 0 || NULL == contentID)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    strcpy((char *)contentID, (char *)s->contentID);

    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_checkRights(int32_t session, int32_t permission)
{
    T_DRM_Session_Node* s;
    int32_t id;
    T_DRM_Rights *pRo, *pCurRo;
    int32_t roAmount, position;
    int32_t i;
    int32_t res = DRM_FAILURE;

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    /* if it is Forward-Lock cases, check it and return directly */
    if (FORWARD_LOCK == s->deliveryMethod) {
        if (DRM_PERMISSION_PLAY == permission ||
            DRM_PERMISSION_DISPLAY == permission ||
            DRM_PERMISSION_EXECUTE == permission ||
            DRM_PERMISSION_PRINT == permission)
            return DRM_SUCCESS;

        return DRM_FAILURE;
    }

    /* if try to forward, only DCF can be forwarded */
    if (DRM_PERMISSION_FORWARD == permission) {
        if (SEPARATE_DELIVERY == s->deliveryMethod)
            return DRM_SUCCESS;

        return DRM_FAILURE;
    }

    /* The following will check CD or SD other permissions */
    if (FALSE == drm_readFromUidTxt(s->contentID, &id, GET_ID))
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT);
    if (roAmount <= 0)
        return DRM_FAILURE;

    pRo = malloc(roAmount * sizeof(T_DRM_Rights));
    if (NULL == pRo)
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, pRo, &roAmount, GET_ALL_RO);

    pCurRo = pRo;
    for (i = 0; i < roAmount; i++) {
        switch (permission) {
        case DRM_PERMISSION_PLAY:
            res = drm_startCheckRights(&(pCurRo->bIsPlayable), &(pCurRo->PlayConstraint));
            break;
        case DRM_PERMISSION_DISPLAY:
            res = drm_startCheckRights(&(pCurRo->bIsDisplayable), &(pCurRo->DisplayConstraint));
            break;
        case DRM_PERMISSION_EXECUTE:
            res = drm_startCheckRights(&(pCurRo->bIsExecuteable), &(pCurRo->ExecuteConstraint));
            break;
        case DRM_PERMISSION_PRINT:
            res = drm_startCheckRights(&(pCurRo->bIsPrintable), &(pCurRo->PrintConstraint));
            break;
        default:
            free(pRo);
            return DRM_FAILURE;
        }

        if (DRM_SUCCESS == res) {
            free(pRo);
            return DRM_SUCCESS;
        } else {
            position = i + 1;
            if (FALSE == drm_writeOrReadInfo(id, pCurRo, &position, SAVE_A_RO)) {
                return DRM_FAILURE;
            }
            ALOGD("=========position is %d\n", position);
        }
        pCurRo++;
    }

    free(pRo);
    return res;
}

/* see svc_drm.h */
int32_t SVC_drm_consumeRights(int32_t session, int32_t permission)
{
    T_DRM_Session_Node* s;
    int32_t id;
    int32_t re;

    ALOGD("Enter in SVC_drm_consumeRights.\n");

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    if (DRM_PERMISSION_FORWARD == permission) {
        if (SEPARATE_DELIVERY == s->deliveryMethod)
            return DRM_SUCCESS;

        return DRM_FAILURE;
    }

    if (FORWARD_LOCK == s->deliveryMethod) /* Forwardlock type have utter rights */
        return DRM_SUCCESS;

    if (FALSE == drm_readFromUidTxt(s->contentID, &id, GET_ID))
        return DRM_FAILURE;

    //return drm_checkRoAndUpdate(id, permission);
    if ((re = drm_checkRoAndUpdate(id, permission)) != DRM_SUCCESS) {
        ALOGE ("update RO failed");
        memset (s->key, 0x0, DRM_KEY_LEN);
    }

    ALOGD("Leave SVC_drm_consumeRights, re [%d]\n", re);

    return re;
}

/* see svc_drm.h */
int32_t SVC_drm_getContentLength(int32_t session)
{
    T_DRM_Session_Node* s;

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;
    //when the file size is bigger than 50K, we will read the two last bolcks
    //to calculate file size.
    //when google give another solution that can resolve 50K issues, then please
    //remove this codes.   b073/Wei Yongqiang
#ifdef USING_GOOGLE_SOLUTION
    ALOGD("Restore to google solution with deliver method %d\n", s->deliveryMethod);
    ALOGD("DRM_UNKNOWN_DATA_LEN 0x%x, content length: 0x%x, offset 0x%x, encContentLength 0x%x, Max Length 0x%x\n",
            DRM_UNKNOWN_DATA_LEN,
            s->contentLength,
            s->contentOffset,
            ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength,
            DRM_MAX_MALLOC_LEN);
    if (DRM_UNKNOWN_DATA_LEN == s->contentLength && s->contentOffset + ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength <= DRM_MAX_MALLOC_LEN &&
        (SEPARATE_DELIVERY == s->deliveryMethod || SEPARATE_DELIVERY_FL == s->deliveryMethod)) {
#else //MIX solution and Seek solution
    ALOGD("Using Seek solution instead of google solution with deliver method %d\n", s->deliveryMethod);

    if (DRM_UNKNOWN_DATA_LEN == s->contentLength && (SEPARATE_DELIVERY == s->deliveryMethod || SEPARATE_DELIVERY_FL == s->deliveryMethod)) {
#endif
        uint8_t keyValue[DRM_KEY_LEN];
        uint8_t lastDcfBuf[DRM_TWO_AES_BLOCK_LEN];
        int32_t seekPos, moreBytes;

        ALOGI("Got valide data! contentID=%d, deliveryMethod = %d , contentLength=%d", s->contentID, s->deliveryMethod, s->contentLength );
        if (TRUE == drm_getKey(s->contentID, keyValue)) {
            seekPos = s->contentOffset + ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength - DRM_TWO_AES_BLOCK_LEN;
#ifdef USING_GOOGLE_SOLUTION
            ALOGD("Restore to google solution with delive method %d\n", s->deliveryMethod);
            memcpy(lastDcfBuf, s->rawContent + seekPos, DRM_TWO_AES_BLOCK_LEN);
#elif defined(USING_MIX_SOLUTION)
            ALOGD("Using Mix solution with deliveryMethod %d\n", s->deliveryMethod);
            if (s->seekInputDataFunc != NULL) {
                s->seekInputDataFunc(s->inputHandle, seekPos);
                if (-1 == s->readInputDataFunc(s->inputHandle, lastDcfBuf, DRM_TWO_AES_BLOCK_LEN)) {
                    ALOGE("SVC_drm_getContentLength read fail");
                    return DRM_MEDIA_DATA_INVALID;
                }
            } else {
                if (s->contentOffset + ((T_DRM_Dcf_Node*)(s->infoStruct))->encContentLength <= DRM_MAX_MALLOC_LEN) {
                    memcpy(lastDcfBuf, s->rawContent + seekPos, DRM_TWO_AES_BLOCK_LEN);
                } else {
                    ALOGE("Overflow with buffer, exit\n");
                    goto out;
                }
            }
#else
            ALOGD("Using Seek solution instead of google solution with delivery method %d\n", s->deliveryMethod);

            s->seekInputDataFunc(s->inputHandle, seekPos);
            if (-1 == s->readInputDataFunc(s->inputHandle, lastDcfBuf, DRM_TWO_AES_BLOCK_LEN)) {
                ALOGE("SVC_drm_getContentLength read fail");
                return DRM_MEDIA_DATA_INVALID;
            }
#endif
            ALOGD("Going to updateDCFDataLen\n");
            if (TRUE == drm_updateDcfDataLen(lastDcfBuf, keyValue, &moreBytes)) {
                s->contentLength = ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength;
                s->contentLength -= moreBytes;
            }
        }
    }
out:
    ALOGD("Going out with content length %d of the session %p\n", s->contentLength, s);
    return s->contentLength;
}

static int32_t drm_readAesData(uint8_t* buf, T_DRM_Session_Node* s, int32_t aesStart, int32_t bufLen)
{
    if (NULL == buf || NULL == s || aesStart < 0 || bufLen < 0)
        return -1;

    if (aesStart - s->contentOffset + bufLen > ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength)
        return -2;

    if (aesStart < DRM_MAX_MALLOC_LEN) {
        if (aesStart + bufLen <= DRM_MAX_MALLOC_LEN) { /* read from buffer */
            memcpy(buf, s->rawContent + aesStart, bufLen);
            return bufLen;
        } else { /* first read from buffer and then from InputStream */
            int32_t point = DRM_MAX_MALLOC_LEN - aesStart;
            int32_t res;

            if (((T_DRM_Dcf_Node *)(s->infoStruct))->bAesBackupBuf) {
                memcpy(buf, ((T_DRM_Dcf_Node *)(s->infoStruct))->aesBackupBuf, DRM_ONE_AES_BLOCK_LEN);
                res = s->readInputDataFunc(s->inputHandle, buf + DRM_ONE_AES_BLOCK_LEN, DRM_ONE_AES_BLOCK_LEN);
                if (0 == res || -1 == res)
                    return -1;

                res += DRM_ONE_AES_BLOCK_LEN;
            } else {
                memcpy(buf, s->rawContent + aesStart, point);
                res = s->readInputDataFunc(s->inputHandle, buf + point, bufLen - point);
                if (0 == res || -1 == res)
                    return -1;

                res += point;
            }

            memcpy(((T_DRM_Dcf_Node *)(s->infoStruct))->aesBackupBuf, buf + DRM_ONE_AES_BLOCK_LEN, DRM_ONE_AES_BLOCK_LEN);
            ((T_DRM_Dcf_Node *)(s->infoStruct))->bAesBackupBuf = TRUE;

            return res;
        }
    } else { /* read from InputStream */
        int32_t res;

        memcpy(buf, ((T_DRM_Dcf_Node *)(s->infoStruct))->aesBackupBuf, DRM_ONE_AES_BLOCK_LEN);
        res = s->readInputDataFunc(s->inputHandle, buf + DRM_ONE_AES_BLOCK_LEN, DRM_ONE_AES_BLOCK_LEN);

        if (0 == res || -1 == res)
            return -1;

        memcpy(((T_DRM_Dcf_Node *)(s->infoStruct))->aesBackupBuf, buf + DRM_ONE_AES_BLOCK_LEN, DRM_ONE_AES_BLOCK_LEN);

        return DRM_ONE_AES_BLOCK_LEN + res;
    }
}

static int32_t drm_readContentFromBuf(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    int32_t readBytes;

    if (offset > s->contentLength)
        return DRM_FAILURE;

    if (offset == s->contentLength)
        return DRM_MEDIA_EOF;

    if (offset + mediaBufLen > s->contentLength)
        readBytes = s->contentLength - offset;
    else
        readBytes = mediaBufLen;

    if (DRM_MESSAGE_CODING_BASE64 == s->transferEncoding)
        memcpy(mediaBuf, s->rawContent + offset, readBytes);
    else
        memcpy(mediaBuf, s->rawContent + s->contentOffset + offset, readBytes);

    return readBytes;
}

static int32_t drm_readB64ContentFromInputStream(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    uint8_t encBuf[DRM_B64_ENC_BLOCK], decBuf[DRM_B64_DEC_BLOCK];
    int32_t encLen, decLen;
    int32_t i, j, piece, leftLen, firstBytes;
    int32_t readBytes = 0;

    if (offset < ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeDataLen) {
        readBytes = ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeDataLen - offset;
        memcpy(mediaBuf, s->rawContent + offset, readBytes);
    } else {
        if (s->bEndData)
            return DRM_MEDIA_EOF;

        firstBytes = offset % DRM_B64_DEC_BLOCK;
        if (firstBytes > 0) {
            if (DRM_B64_DEC_BLOCK - firstBytes >= mediaBufLen) {
                readBytes = mediaBufLen;
                memcpy(mediaBuf, ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeData + firstBytes, readBytes);
                return readBytes;
            }

            readBytes = DRM_B64_DEC_BLOCK - firstBytes;
            memcpy(mediaBuf, ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeData + firstBytes, readBytes);
        }
    }

    leftLen = mediaBufLen - readBytes;
    encLen = (leftLen - 1) / DRM_B64_DEC_BLOCK * DRM_B64_ENC_BLOCK + DRM_B64_ENC_BLOCK;
    piece = encLen / DRM_B64_ENC_BLOCK;

    for (i = 0; i < piece; i++) {
        j = 0;
        while (j < DRM_B64_ENC_BLOCK) {
            if (NULL != s->readBuf && s->readBufLen > 0) { /* read from backup buffer */
                *(encBuf + j) = s->readBuf[s->readBufOff];
                s->readBufOff++;
                s->readBufLen--;
            } else { /* read from InputStream */
                if (0 == s->readInputDataFunc(s->inputHandle, encBuf + j, 1))
                    return DRM_MEDIA_DATA_INVALID;
            }

            if ('\r' == *(encBuf + j) || '\n' == *(encBuf + j))
                continue; /* skip CRLF */

            if ('-' == *(encBuf + j)) {
                int32_t k, len;

                /* invalid base64 data, it comes to end boundary */
                if (0 != j)
                    return DRM_MEDIA_DATA_INVALID;

                /* check whether it is really the boundary */
                len = strlen((char *)((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary);
                if (NULL == s->readBuf) {
                    s->readBuf = (uint8_t *)malloc(len);
                    if (NULL == s->readBuf)
                        return DRM_FAILURE;
                }

                s->readBuf[0] = '-';
                for (k = 0; k < len - 1; k++) {
                    if (NULL != s->readBuf && s->readBufLen > 0) { /* read from backup buffer */
                        *(s->readBuf + k + 1) = s->readBuf[s->readBufOff];
                        s->readBufOff++;
                        s->readBufLen--;
                    } else { /* read from InputStream */
                        if (-1 == s->readInputDataFunc(s->inputHandle, s->readBuf + k + 1, 1))
                            return DRM_MEDIA_DATA_INVALID;
                    }
                }
                if (0 == memcmp(s->readBuf, ((T_DRM_DM_Base64_Node *)(s->infoStruct))->boundary, len))
                    s->bEndData = TRUE;
                else
                    return DRM_MEDIA_DATA_INVALID;

                break;
            }
            j++;
        }

        if (TRUE == s->bEndData) { /* it means come to the end of base64 data */
            if (0 == readBytes)
                return DRM_MEDIA_EOF;

            break;
        }

        encLen = DRM_B64_ENC_BLOCK;
        decLen = DRM_B64_DEC_BLOCK;
        if (-1 == (decLen = drm_decodeBase64(decBuf, decLen, encBuf, &encLen)))
            return DRM_MEDIA_DATA_INVALID;

        if (leftLen >= decLen) {
            memcpy(mediaBuf + readBytes, decBuf, decLen);
            readBytes += decLen;
            leftLen -= decLen;
        } else {
            if (leftLen > 0) {
                memcpy(mediaBuf + readBytes, decBuf, leftLen);
                readBytes += leftLen;
            }
            break;
        }
    }
    memcpy(((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeData, decBuf, DRM_B64_DEC_BLOCK);

    return readBytes;
}

static int32_t drm_readBase64Content(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    int32_t readBytes;

    /* when the content length has been well-known */
    if (s->contentLength >= 0)
        readBytes = drm_readContentFromBuf(s, offset, mediaBuf, mediaBufLen);
    else /* else when the content length has not been well-known yet */
        if (offset < ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeDataLen)
            if (offset + mediaBufLen <= ((T_DRM_DM_Base64_Node *)(s->infoStruct))->b64DecodeDataLen) {
                readBytes = mediaBufLen;
                memcpy(mediaBuf, s->rawContent + offset, readBytes);
            } else
                readBytes = drm_readB64ContentFromInputStream(s, offset, mediaBuf, mediaBufLen);
        else
            readBytes = drm_readB64ContentFromInputStream(s, offset, mediaBuf, mediaBufLen);

    return readBytes;
}

static int32_t drm_readBinaryContentFromInputStream(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    int32_t res = 0, readBytes = 0;
    int32_t leftLen;

    if (s->contentOffset + offset < DRM_MAX_MALLOC_LEN) {
        readBytes = DRM_MAX_MALLOC_LEN - s->contentOffset - offset;
        memcpy(mediaBuf, s->rawContent + s->contentOffset + offset, readBytes);
    } else
        if (s->bEndData)
            return DRM_MEDIA_EOF;

    leftLen = mediaBufLen - readBytes;

    if (NULL != s->readBuf && s->readBufLen > 0) { /* read from backup buffer */
        if (leftLen <= s->readBufLen) {
            memcpy(mediaBuf, s->readBuf + s->readBufOff, leftLen);
            s->readBufOff += leftLen;
            s->readBufLen -= leftLen;
            readBytes += leftLen;
            leftLen = 0;
        } else {
            memcpy(mediaBuf, s->readBuf + s->readBufOff, s->readBufLen);
            s->readBufOff += s->readBufLen;
            leftLen -= s->readBufLen;
            readBytes += s->readBufLen;
            s->readBufLen = 0;
        }
    }

    if (leftLen > 0) {
        res = s->readInputDataFunc(s->inputHandle, mediaBuf + readBytes, mediaBufLen - readBytes);
        if (-1 == res)
            return DRM_MEDIA_DATA_INVALID;
    }

    readBytes += res;
    res = drm_scanEndBoundary(mediaBuf, readBytes, ((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary);
    if (-1 == res)
        return DRM_MEDIA_DATA_INVALID;
    if (-2 == res) { /* may be the boundary is split */
        int32_t boundaryLen, len, off, k;
        char* pTmp = memrchr(mediaBuf, '\r', readBytes);

        if (NULL == pTmp)
            return DRM_FAILURE; /* conflict */

        boundaryLen = strlen((char *)((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary) + 2; /* 2 means: '\r''\n' */
        if (NULL == s->readBuf) {
            s->readBuf = (uint8_t *)malloc(boundaryLen);
            if (NULL == s->readBuf)
                return DRM_FAILURE;
        }

        off = readBytes - ((uint8_t *)pTmp - mediaBuf);
        len = boundaryLen - off;
        memcpy(s->readBuf, pTmp, off);
        for (k = 0; k < boundaryLen - off; k++) {
            if (NULL != s->readBuf && s->readBufLen > 0) { /* read from backup buffer */
                *(s->readBuf + k + off) = s->readBuf[s->readBufOff];
                s->readBufOff++;
                s->readBufLen--;
            } else { /* read from InputStream */
                if (-1 == s->readInputDataFunc(s->inputHandle, s->readBuf + k + off, 1))
                    return DRM_MEDIA_DATA_INVALID;
            }
        }
        s->readBufOff = off;
        s->readBufLen = len;

        if (0 == drm_scanEndBoundary(s->readBuf, boundaryLen, ((T_DRM_DM_Binary_Node *)(s->infoStruct))->boundary)) {
            readBytes = (uint8_t *)pTmp - mediaBuf; /* yes, it is the end boundary */
            s->bEndData = TRUE;
        }
    } else {
        if (res >= 0 && res < readBytes) {
            readBytes = res;
            s->bEndData = TRUE;
        }
    }

    if (s->bEndData) {
        if (0 == readBytes)
            return DRM_MEDIA_EOF;
    }

    return readBytes;
}

static int32_t drm_readBinaryContent(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    int32_t readBytes;

    if (s->contentLength >= 0)
        readBytes = drm_readContentFromBuf(s, offset, mediaBuf, mediaBufLen);
    else /* else when the content length has not been well-known yet */
        if (s->contentOffset + offset < DRM_MAX_MALLOC_LEN)
            if (s->contentOffset + offset + mediaBufLen <= DRM_MAX_MALLOC_LEN) {
                readBytes = mediaBufLen;
                memcpy(mediaBuf, s->rawContent + s->contentOffset + offset, readBytes);
            } else
                readBytes = drm_readBinaryContentFromInputStream(s, offset, mediaBuf, mediaBufLen);
        else
            readBytes = drm_readBinaryContentFromInputStream(s, offset, mediaBuf, mediaBufLen);

    return readBytes;
}

static int32_t drm_readAesContent(T_DRM_Session_Node* s, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    uint8_t keyValue[DRM_KEY_LEN];
    uint8_t buf[DRM_TWO_AES_BLOCK_LEN];
    int32_t readBytes = 0;
    int32_t bufLen, piece, i, copyBytes = 0, leftBytes;
    int32_t aesStart, mediaStart, mediaBufOff;
    AES_KEY key;
#if 0
    if (FALSE == drm_getKey(s->contentID, keyValue))
        return DRM_NO_RIGHTS;
#endif
    memset (keyValue, 0x0, DRM_KEY_LEN);
    if (!memcmp (s->key, keyValue, DRM_KEY_LEN)) {
        ALOGE ("cann't get content key");
        return DRM_NO_RIGHTS;
    }
    memcpy (keyValue, s->key, DRM_KEY_LEN);

    /* when the content length has been well-known */
    if (s->contentLength > 0) {
        if (offset > s->contentLength)
            return DRM_FAILURE;

        if (offset == s->contentLength)
            return DRM_MEDIA_EOF;

        if (offset + mediaBufLen > s->contentLength)
            readBytes = s->contentLength - offset;
        else
            readBytes = mediaBufLen;

        aesStart = s->contentOffset + (offset / DRM_ONE_AES_BLOCK_LEN * DRM_ONE_AES_BLOCK_LEN);
        piece = (offset + readBytes - 1) / DRM_ONE_AES_BLOCK_LEN - offset / DRM_ONE_AES_BLOCK_LEN + 2;
        mediaStart = offset % DRM_ONE_AES_BLOCK_LEN;

        AES_set_decrypt_key(keyValue, DRM_KEY_LEN * 8, &key);
        mediaBufOff = 0;
        leftBytes = readBytes;

        //Here might be updated to support following case:
        //when the file size is bigger than 50K, we will read the two last bolcks
        //to calculate file size.
        //when google give another solution that can resolve 50K issues, then please
        //remove this codes.   b073

        for (i = 0; i < piece - 1; i++) {
#ifdef USING_GOOGLE_SOLUTION
            memcpy(buf, s->rawContent + aesStart + i * DRM_ONE_AES_BLOCK_LEN, DRM_TWO_AES_BLOCK_LEN);
#elif defined (USING_MIX_SOLUTION)
            if (s->seekInputDataFunc != NULL) {
                s->seekInputDataFunc(s->inputHandle, (aesStart + i * DRM_ONE_AES_BLOCK_LEN));
                s->readInputDataFunc(s->inputHandle, buf, DRM_TWO_AES_BLOCK_LEN);
            } else {
                memcpy(buf, s->rawContent + aesStart + i * DRM_ONE_AES_BLOCK_LEN, DRM_TWO_AES_BLOCK_LEN);
            }
#else
            s->seekInputDataFunc(s->inputHandle, (aesStart + i * DRM_ONE_AES_BLOCK_LEN));
            s->readInputDataFunc(s->inputHandle, buf, DRM_TWO_AES_BLOCK_LEN);
#endif

            bufLen = DRM_TWO_AES_BLOCK_LEN;

            if (drm_aesDecBuffer(buf, &bufLen, &key) < 0)
                return DRM_MEDIA_DATA_INVALID;

            if (0 != i)
                mediaStart = 0;

            if (bufLen - mediaStart <= leftBytes)
                copyBytes = bufLen - mediaStart;
            else
                copyBytes = leftBytes;

            memcpy(mediaBuf + mediaBufOff, buf + mediaStart, copyBytes);
            leftBytes -= copyBytes;
            mediaBufOff += copyBytes;
        }
    } else {
        int32_t res;

        if (s->bEndData)
            return DRM_MEDIA_EOF;

        if (((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataLen > ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff) {
            if (mediaBufLen < ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataLen - ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff)
                copyBytes = mediaBufLen;
            else
                copyBytes = ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataLen - ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff;

            memcpy(mediaBuf, ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecData + ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff, copyBytes);
            ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff += copyBytes;
            readBytes += copyBytes;
        }

        leftBytes = mediaBufLen - readBytes;
        if (0 == leftBytes)
            return readBytes;
        if (leftBytes < 0)
            return DRM_FAILURE;

        offset += readBytes;
        aesStart = s->contentOffset + (offset / DRM_ONE_AES_BLOCK_LEN * DRM_ONE_AES_BLOCK_LEN);
        piece = (offset + leftBytes - 1) / DRM_ONE_AES_BLOCK_LEN - offset / DRM_ONE_AES_BLOCK_LEN + 2;
        mediaBufOff = readBytes;

        AES_set_decrypt_key(keyValue, DRM_KEY_LEN * 8, &key);

        for (i = 0; i < piece - 1; i++) {
            if (-1 == (res = drm_readAesData(buf, s, aesStart, DRM_TWO_AES_BLOCK_LEN)))
                return DRM_MEDIA_DATA_INVALID;

            if (-2 == res)
                break;

            bufLen = DRM_TWO_AES_BLOCK_LEN;
            aesStart += DRM_ONE_AES_BLOCK_LEN;

            if (drm_aesDecBuffer(buf, &bufLen, &key) < 0)
                return DRM_MEDIA_DATA_INVALID;
            if (bufLen <= leftBytes)
                copyBytes = bufLen;
            else
                copyBytes = leftBytes;

            memcpy(mediaBuf + mediaBufOff, buf, copyBytes);
            leftBytes -= copyBytes;
            mediaBufOff += copyBytes;
            readBytes += copyBytes;
        }

        memcpy(((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecData, buf, DRM_ONE_AES_BLOCK_LEN);
        ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataLen = bufLen;
        ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff = copyBytes;

        if (aesStart - s->contentOffset > ((T_DRM_Dcf_Node *)(s->infoStruct))->encContentLength - DRM_TWO_AES_BLOCK_LEN && ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataOff == ((T_DRM_Dcf_Node *)(s->infoStruct))->aesDecDataLen) {
            ALOGD("end of data in drm_readAesContent.\n");
            drm_discardPaddingByte(buf, &bufLen);
            readBytes -= (DRM_ONE_AES_BLOCK_LEN - bufLen);
            ALOGD("readBytes is %d\n", readBytes);
            s->bEndData = TRUE;
            if (0 == readBytes)
                return DRM_MEDIA_EOF;
        }
    }

    return readBytes;
}

static int32_t drm_getContentRightsList(uint8_t* roId, int32_t* pRoAmount, T_DRM_Rights** ppRightsList)
{
    int32_t id = 0;

    if (roId == NULL)
        return DRM_FAILURE;

    if (FALSE == drm_readFromUidTxt(roId, &id, GET_ID))
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, NULL, pRoAmount, GET_ROAMOUNT);

    if (*pRoAmount <= 0)
        return DRM_FAILURE;

    *ppRightsList = malloc((*pRoAmount) * sizeof(T_DRM_Rights));
    if (NULL == ppRightsList)
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, *ppRightsList, pRoAmount, GET_ALL_RO);

    return DRM_SUCCESS;
}

static int32_t drm_canBeAutoUsed(int32_t bIsXXable, T_DRM_Rights_Constraint XXConstraint)
{
    if (bIsXXable == FALSE)
        return DRM_FAILURE;

    if (XXConstraint.Indicator & DRM_NO_CONSTRAINT)
        return DRM_SUCCESS;

    if (XXConstraint.Indicator & DRM_START_TIME_CONSTRAINT
            || XXConstraint.Indicator & DRM_END_TIME_CONSTRAINT
            || XXConstraint.Indicator & DRM_INTERVAL_CONSTRAINT) {
        if (drm_startCheckRights(&bIsXXable, &XXConstraint) == DRM_SUCCESS)
            return DRM_SUCCESS;
        else
            return DRM_FAILURE;
    }

    return DRM_FAILURE;
}

/* see svc_drm.h */
int32_t SVC_drm_getContent(int32_t session, int32_t offset, uint8_t* mediaBuf, int32_t mediaBufLen)
{
    T_DRM_Session_Node* s;
    int32_t readBytes = 0;

    if (session < 0 || offset < 0 || NULL == mediaBuf || mediaBufLen <= 0){
        ALOGE("SVC_drm_getContent: invalide params value");
        return DRM_FAILURE;
    }

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    if (0 >= s->getInputDataLengthFunc(s->inputHandle))
        return DRM_MEDIA_DATA_INVALID;

    switch(s->deliveryMethod) {
    case FORWARD_LOCK:
    case COMBINED_DELIVERY:
        if (1 == s->isEncrypted) {
            readBytes = drm_readAesContent(s, offset, mediaBuf, mediaBufLen);
            break;
        }
        if (DRM_MESSAGE_CODING_BASE64 == s->transferEncoding)
            readBytes = drm_readBase64Content(s, offset, mediaBuf, mediaBufLen);
        else /* binary */
            readBytes = drm_readBinaryContent(s, offset, mediaBuf, mediaBufLen);
        break;
    case SEPARATE_DELIVERY:
    case SEPARATE_DELIVERY_FL:
        readBytes = drm_readAesContent(s, offset, mediaBuf, mediaBufLen);
        break;
    default: {
            ALOGE("SVC_drm_getContent: Invalde delivery methord type");
            return DRM_FAILURE;
        }
    }

    ALOGV("SVC_drm_getContent: return readBytes =%d", readBytes);

    return readBytes;
}

/* see svc_drm.h */
int32_t SVC_drm_getRightsIssuer(int32_t session, uint8_t* rightsIssuer)
{
    T_DRM_Session_Node* s;

    if (session < 0 || NULL == rightsIssuer)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    if (SEPARATE_DELIVERY == s->deliveryMethod || SEPARATE_DELIVERY_FL == s->deliveryMethod) {
        strcpy((char *)rightsIssuer, (char *)((T_DRM_Dcf_Node *)(s->infoStruct))->rightsIssuer);
        return DRM_SUCCESS;
    }

    return DRM_NOT_SD_METHOD;
}

/* see svc_drm.h */
int32_t SVC_drm_getRightsInfo(int32_t session, T_DRM_Rights_Info* rights)
{
    T_DRM_Session_Node* s;
    T_DRM_Rights rightsInfo;
    int32_t roAmount, id, i, pos;

    if (session < 0 || NULL == rights)
        return DRM_FAILURE;

    s = getSession(session);
    if (NULL == s)
        return DRM_SESSION_NOT_OPENED;

    if (FORWARD_LOCK == s->deliveryMethod) {
        strcpy((char *)rights->roId, "ForwardLock");
        rights->displayRights.indicator = DRM_NO_CONSTRAINT;
        rights->playRights.indicator = DRM_NO_CONSTRAINT;
        rights->executeRights.indicator = DRM_NO_CONSTRAINT;
        rights->printRights.indicator = DRM_NO_CONSTRAINT;
        rights->displayRights.valid = TRUE;
        rights->playRights.valid = TRUE;
        rights->executeRights.valid = TRUE;
        rights->printRights.valid = TRUE;
        return DRM_SUCCESS;
    }

    if (TRUE == s->deliveryMethod) {
        strcpy((char *)rights->roId, "ForwardLock");
        rights->displayRights.indicator = DRM_NO_CONSTRAINT;
        rights->playRights.indicator = DRM_NO_CONSTRAINT;
        rights->executeRights.indicator = DRM_NO_CONSTRAINT;
        rights->printRights.indicator = DRM_NO_CONSTRAINT;
        return DRM_SUCCESS;
    }

    if (FALSE == drm_readFromUidTxt(s->contentID, &id, GET_ID))
        return DRM_NO_RIGHTS;

    if (FALSE == drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT))
        return DRM_FAILURE;

    if (roAmount < 0)
        return DRM_NO_RIGHTS;

    /* some rights has been installed, but now there is no valid rights */
    if (0 == roAmount) {
        strcpy((char *)rights->roId, (char*)s->contentID);
        rights->displayRights.indicator = DRM_NO_PERMISSION;
        rights->playRights.indicator = DRM_NO_PERMISSION;
        rights->executeRights.indicator = DRM_NO_PERMISSION;
        rights->printRights.indicator = DRM_NO_PERMISSION;
        rights->displayRights.valid = FALSE;
        rights->playRights.valid = FALSE;
        rights->executeRights.valid = FALSE;
        rights->printRights.valid = FALSE;
        return DRM_SUCCESS;
    }

    for(i = 0; i < roAmount; i++) {
        ALOGD("getRightsInfo, i is %d\n", i);
        memset(&rightsInfo, 0, sizeof(T_DRM_Rights));
        pos = i + 1;
        if (FALSE == drm_writeOrReadInfo(id, &rightsInfo, &pos, GET_A_RO))
            return DRM_FAILURE;

        if (rightsInfo.PlayConstraint.Indicator != DRM_NO_PERMISSION
                || rightsInfo.DisplayConstraint.Indicator != DRM_NO_PERMISSION
                || rightsInfo.ExecuteConstraint.Indicator != DRM_NO_PERMISSION
                || rightsInfo.PrintConstraint.Indicator != DRM_NO_PERMISSION) {
            memset(rights, 0, sizeof(T_DRM_Rights_Info));
            drm_getLicenseInfo(&rightsInfo, rights);

            return DRM_SUCCESS;
        }
    }

    return DRM_FAILURE;
}

/* see svc_drm.h */
int32_t SVC_drm_closeSession(int32_t session)
{
    if (session < 0)
        return DRM_FAILURE;

    if (NULL == getSession(session))
        return DRM_SESSION_NOT_OPENED;

    removeSession(session);

    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_updateRights(uint8_t* contentID, int32_t permission)
{
    int32_t id;

    if (NULL == contentID)
        return DRM_FAILURE;

    if (FALSE == drm_readFromUidTxt(contentID, &id, GET_ID))
        return DRM_FAILURE;

    return drm_checkRoAndUpdate(id, permission);
}

/* see svc_drm.h */
int32_t SVC_drm_viewAllRights(T_DRM_Rights_Info_Node **ppRightsInfo)
{
    T_DRM_Rights_Info_Node rightsNode;
    int32_t maxId, id, roAmount, j;
    T_DRM_Rights rights;

    memset(&rights, 0, sizeof(T_DRM_Rights));

    if (NULL == ppRightsInfo)
        return DRM_FAILURE;

    *ppRightsInfo = NULL;

    maxId = drm_getMaxIdFromUidTxt();
    if (-1 == maxId)
        return DRM_FAILURE;

    for (id = 1; id <= maxId; id++) {
        drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT);
        if (roAmount <= 0) /* this means there is not any rights */
            continue;

        for (j = 1; j <= roAmount; j++) {
            if (FALSE == drm_writeOrReadInfo(id, &rights, &j, GET_A_RO))
                continue;

            memset(&rightsNode, 0, sizeof(T_DRM_Rights_Info_Node));

            drm_getLicenseInfo(&rights, &(rightsNode.roInfo));

            if (FALSE == drm_addRightsNodeToList(ppRightsInfo, &rightsNode))
                continue;
        }
    }
    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_freeRightsInfoList(T_DRM_Rights_Info_Node *pRightsHeader)
{
    T_DRM_Rights_Info_Node *pNode, *pTmp;

    if (NULL == pRightsHeader)
        return DRM_FAILURE;

    pNode = pRightsHeader;

    while (NULL != pNode) {
        pTmp = pNode;
        pNode = pNode->next;
        free(pTmp);
    }
    return DRM_SUCCESS;
}

int32_t SVC_drm_deleteUnusedRights (uint8_t* roId)
{
    int32_t roAmount, id;
    T_DRM_Rights *pAllRights = NULL, *pCurRight = NULL;
    int32_t delFlag;
    int32_t usedRoAmount;
    int32_t i;

    ALOGD("Enter in %s, roId = 0x%x\n", __FUNCTION__, roId);

    if (NULL == roId)
        return DRM_FAILURE;

    if (FALSE == drm_readFromUidTxt(roId, &id, GET_ID)) {
        return DRM_NO_RIGHTS;
    }

    ALOGD ("roId = %s, id = %d", roId, id);

    if (FALSE == drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT)) {
        return DRM_FAILURE;
    }

    ALOGD ("roAmount = %d", roAmount);

    if (roAmount == 0)
        return DRM_SUCCESS;

    pAllRights = (T_DRM_Rights *)malloc(roAmount * sizeof(T_DRM_Rights));
    if (NULL == pAllRights)
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, pAllRights, &roAmount, GET_ALL_RO);

    drm_writeOrReadInfo(id, NULL, NULL, DELETE_ALL_RO);
    ALOGD ("delete all ROs from file after to read all ROs into memory");

    usedRoAmount = 0;
    for (i = 0; i < roAmount; i++) {
        pCurRight = pAllRights + i;
//      dumpRights (*pCurRight);

        delFlag = 1;
        if (pCurRight->bIsPlayable) {
            if (pCurRight->PlayConstraint.Indicator == DRM_COUNT_CONSTRAINT
                    && pCurRight->PlayConstraint.Count == 0) {
                delFlag = 1;
                pCurRight->bIsPlayable = 0;
                pCurRight->PlayConstraint.Indicator = ~DRM_COUNT_CONSTRAINT;
            } else {
                delFlag = 0;
            }
        }

        if (pCurRight->bIsDisplayable) {
            if (pCurRight->DisplayConstraint.Indicator == DRM_COUNT_CONSTRAINT
                    && pCurRight->DisplayConstraint.Count == 0) {
                delFlag = 1;
                pCurRight->bIsDisplayable = 0;
                pCurRight->DisplayConstraint.Indicator = ~DRM_COUNT_CONSTRAINT;
            } else {
                delFlag = 0;
            }
        }

        if (pCurRight->bIsExecuteable) {
            if (pCurRight->ExecuteConstraint.Indicator == DRM_COUNT_CONSTRAINT
                    && pCurRight->ExecuteConstraint.Count == 0) {
                delFlag = 1;
                pCurRight->bIsExecuteable = 0;
                pCurRight->ExecuteConstraint.Indicator = ~DRM_COUNT_CONSTRAINT;
            } else {
                delFlag = 0;
            }
        }

        if (pCurRight->bIsPrintable) {
            if (pCurRight->PrintConstraint.Indicator == DRM_COUNT_CONSTRAINT
                    && pCurRight->PrintConstraint.Count == 0) {
                delFlag = 1;
                pCurRight->bIsPrintable = 0;
                pCurRight->PrintConstraint.Indicator = ~DRM_COUNT_CONSTRAINT;
            } else {
                delFlag = 0;
            }
        }

        //This right is valid, save into right file
        if (!delFlag) {
            usedRoAmount++;
            drm_writeOrReadInfo(id, pCurRight, &usedRoAmount, SAVE_A_RO);
        }
        ALOGD ("right delFlag [%d], usedRoAmount [%d]", delFlag, usedRoAmount);
    }

    free (pAllRights);

    ALOGD ("Leave %s, usedRoAmount [%d]", __FUNCTION__, usedRoAmount);
    return DRM_SUCCESS;
}

/* see svc_drm.h */
int32_t SVC_drm_deleteRights(uint8_t* roId)
{
    int32_t maxId, id, roAmount, j;
    T_DRM_Rights rights;

    memset(&rights, 0, sizeof(T_DRM_Rights));

    if (NULL == roId)
        return DRM_FAILURE;

    maxId = drm_getMaxIdFromUidTxt();
    if (-1 == maxId)
        return DRM_NO_RIGHTS;

    for (id = 1; id <= maxId; id++) {
        drm_writeOrReadInfo(id, NULL, &roAmount, GET_ROAMOUNT);
        if (roAmount <= 0) /* this means there is not any rights */
            continue;

        for (j = 1; j <= roAmount; j++) {
            if (FALSE == drm_writeOrReadInfo(id, &rights, &j, GET_A_RO))
                continue;

            /* here find the RO which will be deleted */
            if (0 == strcmp((char *)rights.uid, (char *)roId)) {
                T_DRM_Rights *pAllRights;

                pAllRights = (T_DRM_Rights *)malloc(roAmount * sizeof(T_DRM_Rights));
                if (NULL == pAllRights)
                    return DRM_FAILURE;

                drm_writeOrReadInfo(id, pAllRights, &roAmount, GET_ALL_RO);
                roAmount--;
                if (0 == roAmount) { /* this means it is the last one rights */
                    drm_removeIdInfoFile(id); /* delete the id.info file first */
                    drm_updateUidTxtWhenDelete(id); /* update uid.txt file */
                    free(pAllRights);
                    return DRM_SUCCESS;
                } else /* using the last one rights instead of the deleted one */
                    memcpy(pAllRights + (j - 1), pAllRights + roAmount, sizeof(T_DRM_Rights));

                /* delete the id.info file first */
//                drm_removeIdInfoFile(id);

                if (FALSE == drm_writeOrReadInfo(id, pAllRights, &roAmount, SAVE_ALL_RO)) {
                    free(pAllRights);
                    return DRM_FAILURE;
                }

                free(pAllRights);
                return DRM_SUCCESS;
            }
        }
    }

    return DRM_FAILURE;
}

int32_t SVC_drm_canBeAutoUsed(int32_t session, int32_t permission)
{
    T_DRM_Session_Node* s = NULL;
    T_DRM_Rights* pRightsList = NULL;
    int32_t roAmount = 0;
    int32_t ret = DRM_FAILURE;
    int32_t i = 0;

    ALOGD("session: %d, permission: %d\n", session, permission);

    if ((permission != DRM_PERMISSION_PLAY) && (permission != DRM_PERMISSION_DISPLAY))
        return DRM_FAILURE;

    if (session < 0 )
        return DRM_FAILURE;

    s = getSession(session);

    if (s == NULL)
        return DRM_SESSION_NOT_OPENED;

    if (drm_getContentRightsList(s->contentID, &roAmount, &pRightsList) != DRM_SUCCESS)
        return DRM_FAILURE;

    if (permission == DRM_PERMISSION_PLAY) {
        for (i = 0; i < roAmount; i++) {
            if ((ret = drm_canBeAutoUsed(pRightsList[i].bIsPlayable, pRightsList[i].PlayConstraint)) == DRM_SUCCESS)
                break;
        }
    } else if (permission == DRM_PERMISSION_DISPLAY) {
        for(i = 0; i < roAmount; i++) {
            if ((ret = drm_canBeAutoUsed(pRightsList[i].bIsDisplayable, pRightsList[i].DisplayConstraint)) == DRM_SUCCESS)
                break;
        }
    } else {
        ALOGD("There are only play and display til now.\n");
        return DRM_FAILURE;
    }

    ALOGD("SVC_drm_canBeAutoUse return with ret %d\n", ret);

    return ret;
}

int32_t SVC_drm_getContentRightsNum(int32_t session, int32_t* roAmount)
{
    T_DRM_Session_Node* s = NULL;
    int32_t id = 0;

    ALOGD("enter in SVC_drm_getContentRightsNum.\n");

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);

    if (s == NULL)
        return DRM_SESSION_NOT_OPENED;

    if (FALSE == drm_readFromUidTxt(s->contentID, &id, GET_ID))
        return DRM_FAILURE;

    drm_writeOrReadInfo(id, NULL, roAmount, GET_ROAMOUNT);
    ALOGD("roAmount: %d\n", *roAmount);

    return DRM_SUCCESS;
}

int32_t SVC_drm_getContentRightsList(int32_t session, T_DRM_Rights_Info_Node** ppRightsInfo)
{
    T_DRM_Session_Node* s = NULL;
    T_DRM_Rights* pRightsList = NULL;
    T_DRM_Rights_Info_Node rightsNode;
    int32_t roAmount = 0;
    int32_t i;

    if (session < 0)
        return DRM_FAILURE;

    s = getSession(session);

    if (s == NULL)
        return DRM_SESSION_NOT_OPENED;

    if (drm_getContentRightsList(s->contentID, &roAmount, &pRightsList) != DRM_SUCCESS)
        return DRM_FAILURE;

    *ppRightsInfo = NULL;

    for (i = 0; i < roAmount; i++) {
        memset(&rightsNode, 0, sizeof(T_DRM_Rights_Info_Node));
        dumpRights(pRightsList[i]);

        if (drm_getLicenseInfo(&(pRightsList[i]), &(rightsNode.roInfo)) == FALSE)
            return DRM_FAILURE;

        if (drm_addRightsNodeToList(ppRightsInfo, &rightsNode) == FALSE)
            return DRM_FAILURE;
    }

    free(pRightsList);
    return DRM_SUCCESS;
}
