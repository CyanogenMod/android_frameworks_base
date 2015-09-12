/* //device/libs/android_runtime/android_util_Process.cpp
**
** Copyright 2006, The Android Open Source Project
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

#define LOG_TAG "Process"

#include <utils/Log.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <cutils/process_name.h>
#include <cutils/sched_policy.h>
#include <utils/String8.h>
#include <utils/Vector.h>
#include <processgroup/processgroup.h>

#include <android_runtime/AndroidRuntime.h>

#include "android_util_Binder.h"
#include "JNIHelp.h"

#include <dirent.h>
#include <fcntl.h>
#include <grp.h>
#include <inttypes.h>
#include <pwd.h>
#include <signal.h>
#include <sys/errno.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define POLICY_DEBUG 0
#define GUARD_THREAD_PRIORITY 0

#define DEBUG_PROC(x) //x

using namespace android;

#if GUARD_THREAD_PRIORITY
Mutex gKeyCreateMutex;
static pthread_key_t gBgKey = -1;
#endif

// For both of these, err should be in the errno range (positive), not a status_t (negative)

static void signalExceptionForPriorityError(JNIEnv* env, int err)
{
    switch (err) {
        case EINVAL:
            jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
            break;
        case ESRCH:
            jniThrowException(env, "java/lang/IllegalArgumentException", "Given thread does not exist");
            break;
        case EPERM:
            jniThrowException(env, "java/lang/SecurityException", "No permission to modify given thread");
            break;
        case EACCES:
            jniThrowException(env, "java/lang/SecurityException", "No permission to set to given priority");
            break;
        default:
            jniThrowException(env, "java/lang/RuntimeException", "Unknown error");
            break;
    }
}

static void signalExceptionForGroupError(JNIEnv* env, int err)
{
    switch (err) {
        case EINVAL:
            jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
            break;
        case ESRCH:
            jniThrowException(env, "java/lang/IllegalArgumentException", "Given thread does not exist");
            break;
        case EPERM:
            jniThrowException(env, "java/lang/SecurityException", "No permission to modify given thread");
            break;
        case EACCES:
            jniThrowException(env, "java/lang/SecurityException", "No permission to set to given group");
            break;
        default:
            jniThrowException(env, "java/lang/RuntimeException", "Unknown error");
            break;
    }
}

jint android_os_Process_getUidForName(JNIEnv* env, jobject clazz, jstring name)
{
    if (name == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    const jchar* str16 = env->GetStringCritical(name, 0);
    String8 name8;
    if (str16) {
        name8 = String8(str16, env->GetStringLength(name));
        env->ReleaseStringCritical(name, str16);
    }

    const size_t N = name8.size();
    if (N > 0) {
        const char* str = name8.string();
        for (size_t i=0; i<N; i++) {
            if (str[i] < '0' || str[i] > '9') {
                struct passwd* pwd = getpwnam(str);
                if (pwd == NULL) {
                    return -1;
                }
                return pwd->pw_uid;
            }
        }
        return atoi(str);
    }
    return -1;
}

jint android_os_Process_getGidForName(JNIEnv* env, jobject clazz, jstring name)
{
    if (name == NULL) {
        jniThrowNullPointerException(env, NULL);
        return -1;
    }

    const jchar* str16 = env->GetStringCritical(name, 0);
    String8 name8;
    if (str16) {
        name8 = String8(str16, env->GetStringLength(name));
        env->ReleaseStringCritical(name, str16);
    }

    const size_t N = name8.size();
    if (N > 0) {
        const char* str = name8.string();
        for (size_t i=0; i<N; i++) {
            if (str[i] < '0' || str[i] > '9') {
                struct group* grp = getgrnam(str);
                if (grp == NULL) {
                    return -1;
                }
                return grp->gr_gid;
            }
        }
        return atoi(str);
    }
    return -1;
}

void android_os_Process_setThreadGroup(JNIEnv* env, jobject clazz, int tid, jint grp)
{
    ALOGV("%s tid=%d grp=%" PRId32, __func__, tid, grp);
    SchedPolicy sp = (SchedPolicy) grp;
    int res = set_sched_policy(tid, sp);
    if (res != NO_ERROR) {
        signalExceptionForGroupError(env, -res);
    }
}

void android_os_Process_setProcessGroup(JNIEnv* env, jobject clazz, int pid, jint grp)
{
    ALOGV("%s pid=%d grp=%" PRId32, __func__, pid, grp);
    DIR *d;
    FILE *fp;
    char proc_path[255];
    struct dirent *de;

    if ((grp == SP_FOREGROUND) || (grp > SP_MAX)) {
        signalExceptionForGroupError(env, EINVAL);
        return;
    }

    bool isDefault = false;
    if (grp < 0) {
        grp = SP_FOREGROUND;
        isDefault = true;
    }
    SchedPolicy sp = (SchedPolicy) grp;

#if POLICY_DEBUG
    char cmdline[32];
    int fd;

    strcpy(cmdline, "unknown");

    sprintf(proc_path, "/proc/%d/cmdline", pid);
    fd = open(proc_path, O_RDONLY);
    if (fd >= 0) {
        int rc = read(fd, cmdline, sizeof(cmdline)-1);
        cmdline[rc] = 0;
        close(fd);
    }

    if (sp == SP_BACKGROUND) {
        ALOGD("setProcessGroup: vvv pid %d (%s)", pid, cmdline);
    } else {
        ALOGD("setProcessGroup: ^^^ pid %d (%s)", pid, cmdline);
    }
#endif
    sprintf(proc_path, "/proc/%d/task", pid);
    if (!(d = opendir(proc_path))) {
        // If the process exited on us, don't generate an exception
        if (errno != ENOENT)
            signalExceptionForGroupError(env, errno);
        return;
    }

    while ((de = readdir(d))) {
        int t_pid;
        int t_pri;

        if (de->d_name[0] == '.')
            continue;
        t_pid = atoi(de->d_name);

        if (!t_pid) {
            ALOGE("Error getting pid for '%s'\n", de->d_name);
            continue;
        }

        t_pri = getpriority(PRIO_PROCESS, t_pid);

        if (t_pri <= ANDROID_PRIORITY_AUDIO) {
            int scheduler = sched_getscheduler(t_pid);
            if ((scheduler == SCHED_FIFO) || (scheduler == SCHED_RR)) {
                // This task wants to stay in it's current audio group so it can keep it's budget
                continue;
            }
        }

        if (isDefault) {
            if (t_pri >= ANDROID_PRIORITY_BACKGROUND) {
                // This task wants to stay at background
                continue;
            }
        }

        int err = set_sched_policy(t_pid, sp);
        if (err != NO_ERROR) {
            signalExceptionForGroupError(env, -err);
            break;
        }
    }
    closedir(d);
}

jint android_os_Process_getProcessGroup(JNIEnv* env, jobject clazz, jint pid)
{
    SchedPolicy sp;
    if (get_sched_policy(pid, &sp) != 0) {
        signalExceptionForGroupError(env, errno);
    }
    return (int) sp;
}

static void android_os_Process_setCanSelfBackground(JNIEnv* env, jobject clazz, jboolean bgOk) {
    // Establishes the calling thread as illegal to put into the background.
    // Typically used only for the system process's main looper.
#if GUARD_THREAD_PRIORITY
    ALOGV("Process.setCanSelfBackground(%d) : tid=%d", bgOk, androidGetTid());
    {
        Mutex::Autolock _l(gKeyCreateMutex);
        if (gBgKey == -1) {
            pthread_key_create(&gBgKey, NULL);
        }
    }

    // inverted:  not-okay, we set a sentinel value
    pthread_setspecific(gBgKey, (void*)(bgOk ? 0 : 0xbaad));
#endif
}

void android_os_Process_setThreadScheduler(JNIEnv* env, jclass clazz,
                                              jint tid, jint policy, jint pri)
{
#ifdef HAVE_SCHED_SETSCHEDULER
    struct sched_param param;
    param.sched_priority = pri;
    int rc = sched_setscheduler(tid, policy, &param);
    if (rc) {
        signalExceptionForPriorityError(env, errno);
    }
#else
    signalExceptionForPriorityError(env, ENOSYS);
#endif
}

void android_os_Process_setThreadPriority(JNIEnv* env, jobject clazz,
                                              jint pid, jint pri)
{
#if GUARD_THREAD_PRIORITY
    // if we're putting the current thread into the background, check the TLS
    // to make sure this thread isn't guarded.  If it is, raise an exception.
    if (pri >= ANDROID_PRIORITY_BACKGROUND) {
        if (pid == androidGetTid()) {
            void* bgOk = pthread_getspecific(gBgKey);
            if (bgOk == ((void*)0xbaad)) {
                ALOGE("Thread marked fg-only put self in background!");
                jniThrowException(env, "java/lang/SecurityException", "May not put this thread into background");
                return;
            }
        }
    }
#endif

    int rc = androidSetThreadPriority(pid, pri);
    if (rc != 0) {
        if (rc == INVALID_OPERATION) {
            signalExceptionForPriorityError(env, errno);
        } else {
            signalExceptionForGroupError(env, errno);
        }
    }

    //ALOGI("Setting priority of %" PRId32 ": %" PRId32 ", getpriority returns %d\n",
    //     pid, pri, getpriority(PRIO_PROCESS, pid));
}

void android_os_Process_setCallingThreadPriority(JNIEnv* env, jobject clazz,
                                                        jint pri)
{
    android_os_Process_setThreadPriority(env, clazz, androidGetTid(), pri);
}

jint android_os_Process_getThreadPriority(JNIEnv* env, jobject clazz,
                                              jint pid)
{
    errno = 0;
    jint pri = getpriority(PRIO_PROCESS, pid);
    if (errno != 0) {
        signalExceptionForPriorityError(env, errno);
    }
    //ALOGI("Returning priority of %" PRId32 ": %" PRId32 "\n", pid, pri);
    return pri;
}

static bool memcgSupported = false;
static int memcgTasksFd = -1;
static int memcgSwapTasksFd = -1;
static pthread_once_t memcgInitOnce = PTHREAD_ONCE_INIT;

static void memcgInit(void) {
    if (!access("/sys/fs/cgroup/memory/tasks", F_OK)) {
        memcgTasksFd = open("/sys/fs/cgroup/memory/tasks", O_WRONLY);
        if (memcgTasksFd < 0) {
            return;
        }
        memcgSwapTasksFd = open("/sys/fs/cgroup/memory/sw/tasks", O_WRONLY);
        if (memcgSwapTasksFd < 0) {
            close(memcgTasksFd);
            return;
        }
        memcgSupported = true;
    }
}

jboolean android_os_Process_setSwappiness(JNIEnv *env, jobject clazz,
                                          jint pid, jboolean is_increased)
{
    pthread_once(&memcgInitOnce, memcgInit);
    if (!memcgSupported) {
        return false;
    }

    int fd = is_increased ? memcgSwapTasksFd : memcgTasksFd;
    char text[64];

    if (fd >= 0) {
        sprintf(text, "%" PRId32, pid);
        write(fd, text, strlen(text));
    }

    return true;
}

void android_os_Process_setArgV0(JNIEnv* env, jobject clazz, jstring name)
{
    if (name == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    const jchar* str = env->GetStringCritical(name, 0);
    String8 name8;
    if (str) {
        name8 = String8(str, env->GetStringLength(name));
        env->ReleaseStringCritical(name, str);
    }

    if (name8.size() > 0) {
        const char* procName = name8.string();
        set_process_name(procName);
        AndroidRuntime::getRuntime()->setArgv0(procName);
    }
}

jint android_os_Process_setUid(JNIEnv* env, jobject clazz, jint uid)
{
    return setuid(uid) == 0 ? 0 : errno;
}

jint android_os_Process_setGid(JNIEnv* env, jobject clazz, jint uid)
{
    return setgid(uid) == 0 ? 0 : errno;
}

static int pid_compare(const void* v1, const void* v2)
{
    //ALOGI("Compare %" PRId32 " vs %" PRId32 "\n", *((const jint*)v1), *((const jint*)v2));
    return *((const jint*)v1) - *((const jint*)v2);
}

static jlong getFreeMemoryImpl(const char* const sums[], const size_t sumsLen[], size_t num)
{
    int fd = open("/proc/meminfo", O_RDONLY);

    if (fd < 0) {
        ALOGW("Unable to open /proc/meminfo");
        return -1;
    }

    char buffer[256];
    const int len = read(fd, buffer, sizeof(buffer)-1);
    close(fd);

    if (len < 0) {
        ALOGW("Unable to read /proc/meminfo");
        return -1;
    }
    buffer[len] = 0;

    size_t numFound = 0;
    jlong mem = 0;

    char* p = buffer;
    while (*p && numFound < num) {
        int i = 0;
        while (sums[i]) {
            if (strncmp(p, sums[i], sumsLen[i]) == 0) {
                p += sumsLen[i];
                while (*p == ' ') p++;
                char* num = p;
                while (*p >= '0' && *p <= '9') p++;
                if (*p != 0) {
                    *p = 0;
                    p++;
                    if (*p == 0) p--;
                }
                mem += atoll(num) * 1024;
                numFound++;
                break;
            }
            i++;
        }
        p++;
    }

    return numFound > 0 ? mem : -1;
}

static jlong android_os_Process_getFreeMemory(JNIEnv* env, jobject clazz)
{
    static const char* const sums[] = { "MemFree:", "Cached:", NULL };
    static const size_t sumsLen[] = { strlen("MemFree:"), strlen("Cached:"), 0 };
    return getFreeMemoryImpl(sums, sumsLen, 2);
}

static jlong android_os_Process_getTotalMemory(JNIEnv* env, jobject clazz)
{
    static const char* const sums[] = { "MemTotal:", NULL };
    static const size_t sumsLen[] = { strlen("MemTotal:"), 0 };
    return getFreeMemoryImpl(sums, sumsLen, 1);
}

void android_os_Process_readProcLines(JNIEnv* env, jobject clazz, jstring fileStr,
                                      jobjectArray reqFields, jlongArray outFields)
{
    //ALOGI("getMemInfo: %p %p", reqFields, outFields);

    if (fileStr == NULL || reqFields == NULL || outFields == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    const char* file8 = env->GetStringUTFChars(fileStr, NULL);
    if (file8 == NULL) {
        return;
    }
    String8 file(file8);
    env->ReleaseStringUTFChars(fileStr, file8);

    jsize count = env->GetArrayLength(reqFields);
    if (count > env->GetArrayLength(outFields)) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Array lengths differ");
        return;
    }

    Vector<String8> fields;
    int i;

    for (i=0; i<count; i++) {
        jobject obj = env->GetObjectArrayElement(reqFields, i);
        if (obj != NULL) {
            const char* str8 = env->GetStringUTFChars((jstring)obj, NULL);
            //ALOGI("String at %d: %p = %s", i, obj, str8);
            if (str8 == NULL) {
                jniThrowNullPointerException(env, "Element in reqFields");
                return;
            }
            fields.add(String8(str8));
            env->ReleaseStringUTFChars((jstring)obj, str8);
        } else {
            jniThrowNullPointerException(env, "Element in reqFields");
            return;
        }
    }

    jlong* sizesArray = env->GetLongArrayElements(outFields, 0);
    if (sizesArray == NULL) {
        return;
    }

    //ALOGI("Clearing %" PRId32 " sizes", count);
    for (i=0; i<count; i++) {
        sizesArray[i] = 0;
    }

    int fd = open(file.string(), O_RDONLY);

    if (fd >= 0) {
        const size_t BUFFER_SIZE = 2048;
        char* buffer = (char*)malloc(BUFFER_SIZE);
        int len = read(fd, buffer, BUFFER_SIZE-1);
        close(fd);

        if (len < 0) {
            ALOGW("Unable to read %s", file.string());
            len = 0;
        }
        buffer[len] = 0;

        int foundCount = 0;

        char* p = buffer;
        while (*p && foundCount < count) {
            bool skipToEol = true;
            //ALOGI("Parsing at: %s", p);
            for (i=0; i<count; i++) {
                const String8& field = fields[i];
                if (strncmp(p, field.string(), field.length()) == 0) {
                    p += field.length();
                    while (*p == ' ' || *p == '\t') p++;
                    char* num = p;
                    while (*p >= '0' && *p <= '9') p++;
                    skipToEol = *p != '\n';
                    if (*p != 0) {
                        *p = 0;
                        p++;
                    }
                    char* end;
                    sizesArray[i] = strtoll(num, &end, 10);
                    //ALOGI("Field %s = %" PRId64, field.string(), sizesArray[i]);
                    foundCount++;
                    break;
                }
            }
            if (skipToEol) {
                while (*p && *p != '\n') {
                    p++;
                }
                if (*p == '\n') {
                    p++;
                }
            }
        }

        free(buffer);
    } else {
        ALOGW("Unable to open %s", file.string());
    }

    //ALOGI("Done!");
    env->ReleaseLongArrayElements(outFields, sizesArray, 0);
}

jintArray android_os_Process_getPids(JNIEnv* env, jobject clazz,
                                     jstring file, jintArray lastArray)
{
    if (file == NULL) {
        jniThrowNullPointerException(env, NULL);
        return NULL;
    }

    const char* file8 = env->GetStringUTFChars(file, NULL);
    if (file8 == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return NULL;
    }

    DIR* dirp = opendir(file8);

    env->ReleaseStringUTFChars(file, file8);

    if(dirp == NULL) {
        return NULL;
    }

    jsize curCount = 0;
    jint* curData = NULL;
    if (lastArray != NULL) {
        curCount = env->GetArrayLength(lastArray);
        curData = env->GetIntArrayElements(lastArray, 0);
    }

    jint curPos = 0;

    struct dirent* entry;
    while ((entry=readdir(dirp)) != NULL) {
        const char* p = entry->d_name;
        while (*p) {
            if (*p < '0' || *p > '9') break;
            p++;
        }
        if (*p != 0) continue;

        char* end;
        int pid = strtol(entry->d_name, &end, 10);
        //ALOGI("File %s pid=%d\n", entry->d_name, pid);
        if (curPos >= curCount) {
            jsize newCount = (curCount == 0) ? 10 : (curCount*2);
            jintArray newArray = env->NewIntArray(newCount);
            if (newArray == NULL) {
                closedir(dirp);
                jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
                return NULL;
            }
            jint* newData = env->GetIntArrayElements(newArray, 0);
            if (curData != NULL) {
                memcpy(newData, curData, sizeof(jint)*curCount);
                env->ReleaseIntArrayElements(lastArray, curData, 0);
            }
            lastArray = newArray;
            curCount = newCount;
            curData = newData;
        }

        curData[curPos] = pid;
        curPos++;
    }

    closedir(dirp);

    if (curData != NULL && curPos > 0) {
        qsort(curData, curPos, sizeof(jint), pid_compare);
    }

    while (curPos < curCount) {
        curData[curPos] = -1;
        curPos++;
    }

    if (curData != NULL) {
        env->ReleaseIntArrayElements(lastArray, curData, 0);
    }

    return lastArray;
}

enum {
    PROC_TERM_MASK = 0xff,
    PROC_ZERO_TERM = 0,
    PROC_SPACE_TERM = ' ',
    PROC_COMBINE = 0x100,
    PROC_PARENS = 0x200,
    PROC_QUOTES = 0x400,
    PROC_OUT_STRING = 0x1000,
    PROC_OUT_LONG = 0x2000,
    PROC_OUT_FLOAT = 0x4000,
};

jboolean android_os_Process_parseProcLineArray(JNIEnv* env, jobject clazz,
        char* buffer, jint startIndex, jint endIndex, jintArray format,
        jobjectArray outStrings, jlongArray outLongs, jfloatArray outFloats)
{

    const jsize NF = env->GetArrayLength(format);
    const jsize NS = outStrings ? env->GetArrayLength(outStrings) : 0;
    const jsize NL = outLongs ? env->GetArrayLength(outLongs) : 0;
    const jsize NR = outFloats ? env->GetArrayLength(outFloats) : 0;

    jint* formatData = env->GetIntArrayElements(format, 0);
    jlong* longsData = outLongs ?
        env->GetLongArrayElements(outLongs, 0) : NULL;
    jfloat* floatsData = outFloats ?
        env->GetFloatArrayElements(outFloats, 0) : NULL;
    if (formatData == NULL || (NL > 0 && longsData == NULL)
            || (NR > 0 && floatsData == NULL)) {
        if (formatData != NULL) {
            env->ReleaseIntArrayElements(format, formatData, 0);
        }
        if (longsData != NULL) {
            env->ReleaseLongArrayElements(outLongs, longsData, 0);
        }
        if (floatsData != NULL) {
            env->ReleaseFloatArrayElements(outFloats, floatsData, 0);
        }
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return JNI_FALSE;
    }

    jsize i = startIndex;
    jsize di = 0;

    jboolean res = JNI_TRUE;

    for (jsize fi=0; fi<NF; fi++) {
        jint mode = formatData[fi];
        if ((mode&PROC_PARENS) != 0) {
            i++;
        } else if ((mode&PROC_QUOTES != 0)) {
            if (buffer[i] == '"') {
                i++;
            } else {
                mode &= ~PROC_QUOTES;
            }
        }
        const char term = (char)(mode&PROC_TERM_MASK);
        const jsize start = i;
        if (i >= endIndex) {
            DEBUG_PROC(ALOGW("Ran off end of data @%d", i));
            res = JNI_FALSE;
            break;
        }

        jsize end = -1;
        if ((mode&PROC_PARENS) != 0) {
            while (i < endIndex && buffer[i] != ')') {
                i++;
            }
            end = i;
            i++;
        } else if ((mode&PROC_QUOTES) != 0) {
            while (buffer[i] != '"' && i < endIndex) {
                i++;
            }
            end = i;
            i++;
        }
        while (i < endIndex && buffer[i] != term) {
            i++;
        }
        if (end < 0) {
            end = i;
        }

        if (i < endIndex) {
            i++;
            if ((mode&PROC_COMBINE) != 0) {
                while (i < endIndex && buffer[i] == term) {
                    i++;
                }
            }
        }

        //ALOGI("Field %" PRId32 ": %" PRId32 "-%" PRId32 " dest=%" PRId32 " mode=0x%" PRIx32 "\n", i, start, end, di, mode);

        if ((mode&(PROC_OUT_FLOAT|PROC_OUT_LONG|PROC_OUT_STRING)) != 0) {
            char c = buffer[end];
            buffer[end] = 0;
            if ((mode&PROC_OUT_FLOAT) != 0 && di < NR) {
                char* end;
                floatsData[di] = strtof(buffer+start, &end);
            }
            if ((mode&PROC_OUT_LONG) != 0 && di < NL) {
                char* end;
                longsData[di] = strtoll(buffer+start, &end, 10);
            }
            if ((mode&PROC_OUT_STRING) != 0 && di < NS) {
                jstring str = env->NewStringUTF(buffer+start);
                env->SetObjectArrayElement(outStrings, di, str);
            }
            buffer[end] = c;
            di++;
        }
    }

    env->ReleaseIntArrayElements(format, formatData, 0);
    if (longsData != NULL) {
        env->ReleaseLongArrayElements(outLongs, longsData, 0);
    }
    if (floatsData != NULL) {
        env->ReleaseFloatArrayElements(outFloats, floatsData, 0);
    }

    return res;
}

jboolean android_os_Process_parseProcLine(JNIEnv* env, jobject clazz,
        jbyteArray buffer, jint startIndex, jint endIndex, jintArray format,
        jobjectArray outStrings, jlongArray outLongs, jfloatArray outFloats)
{
        jbyte* bufferArray = env->GetByteArrayElements(buffer, NULL);

        jboolean result = android_os_Process_parseProcLineArray(env, clazz,
                (char*) bufferArray, startIndex, endIndex, format, outStrings,
                outLongs, outFloats);

        env->ReleaseByteArrayElements(buffer, bufferArray, 0);

        return result;
}

jboolean android_os_Process_readProcFile(JNIEnv* env, jobject clazz,
        jstring file, jintArray format, jobjectArray outStrings,
        jlongArray outLongs, jfloatArray outFloats)
{
    if (file == NULL || format == NULL) {
        jniThrowNullPointerException(env, NULL);
        return JNI_FALSE;
    }

    const char* file8 = env->GetStringUTFChars(file, NULL);
    if (file8 == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return JNI_FALSE;
    }
    int fd = open(file8, O_RDONLY);

    if (fd < 0) {
        DEBUG_PROC(ALOGW("Unable to open process file: %s\n", file8));
        env->ReleaseStringUTFChars(file, file8);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(file, file8);

    char buffer[256];
    const int len = read(fd, buffer, sizeof(buffer)-1);
    close(fd);

    if (len < 0) {
        DEBUG_PROC(ALOGW("Unable to open process file: %s fd=%d\n", file8, fd));
        return JNI_FALSE;
    }
    buffer[len] = 0;

    return android_os_Process_parseProcLineArray(env, clazz, buffer, 0, len,
            format, outStrings, outLongs, outFloats);

}

void android_os_Process_setApplicationObject(JNIEnv* env, jobject clazz,
                                             jobject binderObject)
{
    if (binderObject == NULL) {
        jniThrowNullPointerException(env, NULL);
        return;
    }

    sp<IBinder> binder = ibinderForJavaObject(env, binderObject);
}

void android_os_Process_sendSignal(JNIEnv* env, jobject clazz, jint pid, jint sig)
{
    if (pid > 0) {
        ALOGI("Sending signal. PID: %" PRId32 " SIG: %" PRId32, pid, sig);
        kill(pid, sig);
    }
}

void android_os_Process_sendSignalQuiet(JNIEnv* env, jobject clazz, jint pid, jint sig)
{
    if (pid > 0) {
        kill(pid, sig);
    }
}

static jlong android_os_Process_getElapsedCpuTime(JNIEnv* env, jobject clazz)
{
    struct timespec ts;

    int res = clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &ts);

    if (res != 0) {
        return (jlong) 0;
    }

    nsecs_t when = seconds_to_nanoseconds(ts.tv_sec) + ts.tv_nsec;
    return (jlong) nanoseconds_to_milliseconds(when);
}

static jlong android_os_Process_getPss(JNIEnv* env, jobject clazz, jint pid)
{
    char filename[64];

    snprintf(filename, sizeof(filename), "/proc/%" PRId32 "/smaps", pid);

    FILE * file = fopen(filename, "r");
    if (!file) {
        return (jlong) -1;
    }

    // Tally up all of the Pss from the various maps
    char line[256];
    jlong pss = 0;
    while (fgets(line, sizeof(line), file)) {
        jlong v;
        if (sscanf(line, "Pss: %" SCNd64 " kB", &v) == 1) {
            pss += v;
        }
    }

    fclose(file);

    // Return the Pss value in bytes, not kilobytes
    return pss * 1024;
}

jintArray android_os_Process_getPidsForCommands(JNIEnv* env, jobject clazz,
        jobjectArray commandNames)
{
    if (commandNames == NULL) {
        jniThrowNullPointerException(env, NULL);
        return NULL;
    }

    Vector<String8> commands;

    jsize count = env->GetArrayLength(commandNames);

    for (int i=0; i<count; i++) {
        jobject obj = env->GetObjectArrayElement(commandNames, i);
        if (obj != NULL) {
            const char* str8 = env->GetStringUTFChars((jstring)obj, NULL);
            if (str8 == NULL) {
                jniThrowNullPointerException(env, "Element in commandNames");
                return NULL;
            }
            commands.add(String8(str8));
            env->ReleaseStringUTFChars((jstring)obj, str8);
        } else {
            jniThrowNullPointerException(env, "Element in commandNames");
            return NULL;
        }
    }

    Vector<jint> pids;

    DIR *proc = opendir("/proc");
    if (proc == NULL) {
        fprintf(stderr, "/proc: %s\n", strerror(errno));
        return NULL;
    }

    struct dirent *d;
    while ((d = readdir(proc))) {
        int pid = atoi(d->d_name);
        if (pid <= 0) continue;

        char path[PATH_MAX];
        char data[PATH_MAX];
        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);

        int fd = open(path, O_RDONLY);
        if (fd < 0) {
            continue;
        }
        const int len = read(fd, data, sizeof(data)-1);
        close(fd);

        if (len < 0) {
            continue;
        }
        data[len] = 0;

        for (int i=0; i<len; i++) {
            if (data[i] == ' ') {
                data[i] = 0;
                break;
            }
        }

        for (size_t i=0; i<commands.size(); i++) {
            if (commands[i] == data) {
                pids.add(pid);
                break;
            }
        }
    }

    closedir(proc);

    jintArray pidArray = env->NewIntArray(pids.size());
    if (pidArray == NULL) {
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return NULL;
    }

    if (pids.size() > 0) {
        env->SetIntArrayRegion(pidArray, 0, pids.size(), pids.array());
    }

    return pidArray;
}

jint android_os_Process_killProcessGroup(JNIEnv* env, jobject clazz, jint uid, jint pid)
{
    return killProcessGroup(uid, pid, SIGKILL);
}

void android_os_Process_removeAllProcessGroups(JNIEnv* env, jobject clazz)
{
    return removeAllProcessGroups();
}

static const JNINativeMethod methods[] = {
    {"getUidForName",       "(Ljava/lang/String;)I", (void*)android_os_Process_getUidForName},
    {"getGidForName",       "(Ljava/lang/String;)I", (void*)android_os_Process_getGidForName},
    {"setThreadPriority",   "(II)V", (void*)android_os_Process_setThreadPriority},
    {"setThreadScheduler",  "(III)V", (void*)android_os_Process_setThreadScheduler},
    {"setCanSelfBackground", "(Z)V", (void*)android_os_Process_setCanSelfBackground},
    {"setThreadPriority",   "(I)V", (void*)android_os_Process_setCallingThreadPriority},
    {"getThreadPriority",   "(I)I", (void*)android_os_Process_getThreadPriority},
    {"setThreadGroup",      "(II)V", (void*)android_os_Process_setThreadGroup},
    {"setProcessGroup",     "(II)V", (void*)android_os_Process_setProcessGroup},
    {"getProcessGroup",     "(I)I", (void*)android_os_Process_getProcessGroup},
    {"setSwappiness",   "(IZ)Z", (void*)android_os_Process_setSwappiness},
    {"setArgV0",    "(Ljava/lang/String;)V", (void*)android_os_Process_setArgV0},
    {"setUid", "(I)I", (void*)android_os_Process_setUid},
    {"setGid", "(I)I", (void*)android_os_Process_setGid},
    {"sendSignal", "(II)V", (void*)android_os_Process_sendSignal},
    {"sendSignalQuiet", "(II)V", (void*)android_os_Process_sendSignalQuiet},
    {"getFreeMemory", "()J", (void*)android_os_Process_getFreeMemory},
    {"getTotalMemory", "()J", (void*)android_os_Process_getTotalMemory},
    {"readProcLines", "(Ljava/lang/String;[Ljava/lang/String;[J)V", (void*)android_os_Process_readProcLines},
    {"getPids", "(Ljava/lang/String;[I)[I", (void*)android_os_Process_getPids},
    {"readProcFile", "(Ljava/lang/String;[I[Ljava/lang/String;[J[F)Z", (void*)android_os_Process_readProcFile},
    {"parseProcLine", "([BII[I[Ljava/lang/String;[J[F)Z", (void*)android_os_Process_parseProcLine},
    {"getElapsedCpuTime", "()J", (void*)android_os_Process_getElapsedCpuTime},
    {"getPss", "(I)J", (void*)android_os_Process_getPss},
    {"getPidsForCommands", "([Ljava/lang/String;)[I", (void*)android_os_Process_getPidsForCommands},
    //{"setApplicationObject", "(Landroid/os/IBinder;)V", (void*)android_os_Process_setApplicationObject},
    {"killProcessGroup", "(II)I", (void*)android_os_Process_killProcessGroup},
    {"removeAllProcessGroups", "()V", (void*)android_os_Process_removeAllProcessGroups},
};

const char* const kProcessPathName = "android/os/Process";

int register_android_os_Process(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(
        env, kProcessPathName,
        methods, NELEM(methods));
}
