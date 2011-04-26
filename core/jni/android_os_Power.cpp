/* //device/libs/android_runtime/android_os_Power.cpp
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

#include "JNIHelp.h"
#include "jni.h"
#include "android_runtime/AndroidRuntime.h"
#include <utils/misc.h>
#include <hardware_legacy/power.h>
#include <sys/reboot.h>

#if RECOVERY_WRITE_MISC_PART
#include <fcntl.h>
#include <sys/mount.h>
#include <mtd/mtd-user.h>
#define MTD_PROC_FILENAME   "/proc/mtd"
#define BOOT_CMD_SIZE       32
#endif

namespace android
{

static void throw_NullPointerException(JNIEnv *env, const char* msg)
{
    jclass clazz;
    clazz = env->FindClass("java/lang/NullPointerException");
    env->ThrowNew(clazz, msg);
}

static void
acquireWakeLock(JNIEnv *env, jobject clazz, jint lock, jstring idObj)
{
    if (idObj == NULL) {
        throw_NullPointerException(env, "id is null");
        return ;
    }

    const char *id = env->GetStringUTFChars(idObj, NULL);

    acquire_wake_lock(lock, id);

    env->ReleaseStringUTFChars(idObj, id);
}

static void
releaseWakeLock(JNIEnv *env, jobject clazz, jstring idObj)
{
    if (idObj == NULL) {
        throw_NullPointerException(env, "id is null");
        return ;
    }

    const char *id = env->GetStringUTFChars(idObj, NULL);

    release_wake_lock(id);

    env->ReleaseStringUTFChars(idObj, id);

}

static int
setLastUserActivityTimeout(JNIEnv *env, jobject clazz, jlong timeMS)
{
    return set_last_user_activity_timeout(timeMS/1000);
}

static int
setScreenState(JNIEnv *env, jobject clazz, jboolean on)
{
    return set_screen_state(on);
}

static void android_os_Power_shutdown(JNIEnv *env, jobject clazz)
{
    sync();
#ifdef HAVE_ANDROID_OS
    reboot(RB_POWER_OFF);
#endif
}

#if RECOVERY_WRITE_MISC_PART
struct bootloader_message {
    char command[32];
    char status[32];
    char recovery[1024];
    char stub[2048 - 32 - 32 - 1024];
};

static char command[2048]; // block size buffer
static int mtdnum = -1;
static int mtdsize = 0;
static int mtderasesize = 0x20000 * 512;
static char mtdname[64];
static char mtddevname[32];

static int init_mtd_info() {
	if (mtdnum >= 0) {
		return 0;
	}
    int fd = open(MTD_PROC_FILENAME, O_RDONLY);
    if (fd < 0) {
        return (mtdnum = -1);
    }
    int nbytes = read(fd, command, sizeof(command) - 1);
    close(fd);
    if (nbytes < 0) {
        return (mtdnum = -2);
    }
    command[nbytes] = '\0';
	char *cursor = command;
	while (nbytes-- > 0 && *(cursor++) != '\n'); // skip one line
	while (nbytes > 0) {
		int matches = sscanf(cursor, "mtd%d: %x %x \"%63s[^\"]", &mtdnum, &mtdsize, &mtderasesize, mtdname);
		if (matches == ( RECOVERY_WRITE_MISC_PART )) {
			if (strncmp("misc", mtdname, ( RECOVERY_WRITE_MISC_PART )) == 0) {
				sprintf(mtddevname, "/dev/mtd/mtd%d", mtdnum);
				//printf("Partition for parameters: %s\n", mtddevname);
				return 0;
			}
			while (nbytes-- > 0 && *(cursor++) != '\n'); // skip a line
		}
	}
    return (mtdnum = -3);
}

int set_message(char* cmd) {
        int fd;
        int pos = 2048;
        if (init_mtd_info() != 0) {
                return -9;
        }
        fd = open(mtddevname, O_RDWR);
    if (fd < 0) {
        return fd;
    }
    struct erase_info_user erase_info;
    erase_info.start = 0;
    erase_info.length = mtderasesize;
    if (ioctl(fd, MEMERASE, &erase_info) < 0) {
                fprintf(stderr, "mtd: erase failure at 0x%08x (%s)\n", pos, strerror(errno));
    }
        if (lseek(fd, pos, SEEK_SET) != pos) {
                close(fd);
                return pos;
        }
        memset(&command, 0, sizeof(command));
        strncpy(command, cmd, strlen(cmd));
        pos = write(fd, command, sizeof(command));
        //printf("Written %d bytes\n", pos);
        if (pos < 0) {
                close(fd);
        return pos;
    }
        close(fd);
    return 0;
}
#endif  // RECOVERY_WRITE_MISC_PART

static void android_os_Power_reboot(JNIEnv *env, jobject clazz, jstring reason)
{
    sync();
#ifdef HAVE_ANDROID_OS
    if (reason == NULL) {
        reboot(RB_AUTOBOOT);
    } else {
        const char *chars = env->GetStringUTFChars(reason, NULL);
#ifdef RECOVERY_PRE_COMMAND
	if (!strncmp(chars,"recovery",8))
		system( RECOVERY_PRE_COMMAND );
#endif
#if RECOVERY_WRITE_MISC_PART
	set_message((char*)chars);
#endif
        __reboot(LINUX_REBOOT_MAGIC1, LINUX_REBOOT_MAGIC2,
                 LINUX_REBOOT_CMD_RESTART2, (char*) chars);
        env->ReleaseStringUTFChars(reason, chars);  // In case it fails.
    }
    jniThrowIOException(env, errno);
#endif
}

static JNINativeMethod method_table[] = {
    { "acquireWakeLock", "(ILjava/lang/String;)V", (void*)acquireWakeLock },
    { "releaseWakeLock", "(Ljava/lang/String;)V", (void*)releaseWakeLock },
    { "setLastUserActivityTimeout", "(J)I", (void*)setLastUserActivityTimeout },
    { "setScreenState", "(Z)I", (void*)setScreenState },
    { "shutdown", "()V", (void*)android_os_Power_shutdown },
    { "rebootNative", "(Ljava/lang/String;)V", (void*)android_os_Power_reboot },
};

int register_android_os_Power(JNIEnv *env)
{
    return AndroidRuntime::registerNativeMethods(
        env, "android/os/Power",
        method_table, NELEM(method_table));
}

};
