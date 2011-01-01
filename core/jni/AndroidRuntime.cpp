/* //device/libs/android_runtime/AndroidRuntime.cpp
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

#define LOG_TAG "AndroidRuntime"
//#define LOG_NDEBUG 0

#include <android_runtime/AndroidRuntime.h>
#include <binder/IBinder.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <binder/Parcel.h>
#include <utils/StringArray.h>
#include <utils/threads.h>
#include <cutils/properties.h>

#include <SkGraphics.h>
#include <SkImageDecoder.h>
#include <SkImageRef_GlobalPool.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_util_Binder.h"

#include <stdio.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <signal.h>
#include <dirent.h>
#include <assert.h>


using namespace android;

extern void register_BindTest();

extern int register_android_os_Binder(JNIEnv* env);
extern int register_android_os_Process(JNIEnv* env);
extern int register_android_graphics_Bitmap(JNIEnv*);
extern int register_android_graphics_BitmapFactory(JNIEnv*);
extern int register_android_graphics_Camera(JNIEnv* env);
extern int register_android_graphics_Graphics(JNIEnv* env);
extern int register_android_graphics_Interpolator(JNIEnv* env);
extern int register_android_graphics_LayerRasterizer(JNIEnv*);
extern int register_android_graphics_MaskFilter(JNIEnv* env);
extern int register_android_graphics_Movie(JNIEnv* env);
extern int register_android_graphics_NinePatch(JNIEnv*);
extern int register_android_graphics_PathEffect(JNIEnv* env);
extern int register_android_graphics_Region(JNIEnv* env);
extern int register_android_graphics_Shader(JNIEnv* env);
extern int register_android_graphics_Typeface(JNIEnv* env);
extern int register_android_graphics_YuvImage(JNIEnv* env);

extern int register_com_google_android_gles_jni_EGLImpl(JNIEnv* env);
extern int register_com_google_android_gles_jni_GLImpl(JNIEnv* env);
extern int register_android_opengl_jni_GLES10(JNIEnv* env);
extern int register_android_opengl_jni_GLES10Ext(JNIEnv* env);
extern int register_android_opengl_jni_GLES11(JNIEnv* env);
extern int register_android_opengl_jni_GLES11Ext(JNIEnv* env);
extern int register_android_opengl_jni_GLES20(JNIEnv* env);

extern int register_android_hardware_Camera(JNIEnv *env);

extern int register_android_hardware_SensorManager(JNIEnv *env);

extern int register_android_media_AudioRecord(JNIEnv *env);
extern int register_android_media_AudioSystem(JNIEnv *env);
extern int register_android_media_AudioTrack(JNIEnv *env);
extern int register_android_media_JetPlayer(JNIEnv *env);
extern int register_android_media_ToneGenerator(JNIEnv *env);

extern int register_android_message_digest_sha1(JNIEnv *env);

extern int register_android_util_FloatMath(JNIEnv* env);

#ifdef HAVE_FM_RADIO
extern int register_android_hardware_fm_fmradio(JNIEnv* env);
#endif

namespace android {

/*
 * JNI-based registration functions.  Note these are properly contained in
 * namespace android.
 */
extern int register_android_content_AssetManager(JNIEnv* env);
extern int register_android_util_EventLog(JNIEnv* env);
extern int register_android_util_Log(JNIEnv* env);
extern int register_android_content_StringBlock(JNIEnv* env);
extern int register_android_content_XmlBlock(JNIEnv* env);
extern int register_android_emoji_EmojiFactory(JNIEnv* env);
extern int register_android_graphics_Canvas(JNIEnv* env);
extern int register_android_graphics_ColorFilter(JNIEnv* env);
extern int register_android_graphics_DrawFilter(JNIEnv* env);
extern int register_android_graphics_Matrix(JNIEnv* env);
extern int register_android_graphics_Paint(JNIEnv* env);
extern int register_android_graphics_Path(JNIEnv* env);
extern int register_android_graphics_PathMeasure(JNIEnv* env);
extern int register_android_graphics_Picture(JNIEnv*);
extern int register_android_graphics_PorterDuff(JNIEnv* env);
extern int register_android_graphics_Rasterizer(JNIEnv* env);
extern int register_android_graphics_Xfermode(JNIEnv* env);
extern int register_android_graphics_PixelFormat(JNIEnv* env);
extern int register_com_android_internal_graphics_NativeUtils(JNIEnv *env);
extern int register_android_view_Display(JNIEnv* env);
extern int register_android_view_Surface(JNIEnv* env);
extern int register_android_view_ViewRoot(JNIEnv* env);
extern int register_android_database_CursorWindow(JNIEnv* env);
extern int register_android_database_SQLiteCompiledSql(JNIEnv* env);
extern int register_android_database_SQLiteDatabase(JNIEnv* env);
extern int register_android_database_SQLiteDebug(JNIEnv* env);
extern int register_android_database_SQLiteProgram(JNIEnv* env);
extern int register_android_database_SQLiteQuery(JNIEnv* env);
extern int register_android_database_SQLiteStatement(JNIEnv* env);
extern int register_android_debug_JNITest(JNIEnv* env);
extern int register_android_nio_utils(JNIEnv* env);
extern int register_android_nfc_NdefMessage(JNIEnv *env);
extern int register_android_nfc_NdefRecord(JNIEnv *env);
extern int register_android_pim_EventRecurrence(JNIEnv* env);
extern int register_android_text_format_Time(JNIEnv* env);
extern int register_android_os_Debug(JNIEnv* env);
extern int register_android_os_MessageQueue(JNIEnv* env);
extern int register_android_os_ParcelFileDescriptor(JNIEnv *env);
extern int register_android_os_Power(JNIEnv *env);
extern int register_android_os_StatFs(JNIEnv *env);
extern int register_android_os_SystemProperties(JNIEnv *env);
extern int register_android_os_SystemClock(JNIEnv* env);
extern int register_android_os_FileObserver(JNIEnv *env);
extern int register_android_os_FileUtils(JNIEnv *env);
extern int register_android_os_UEventObserver(JNIEnv* env);
extern int register_android_os_MemoryFile(JNIEnv* env);
extern int register_android_net_LocalSocketImpl(JNIEnv* env);
extern int register_android_net_NetworkUtils(JNIEnv* env);
extern int register_android_net_TrafficStats(JNIEnv* env);
extern int register_android_net_wifi_WifiManager(JNIEnv* env);
extern int register_android_security_Md5MessageDigest(JNIEnv *env);
extern int register_android_text_AndroidCharacter(JNIEnv *env);
extern int register_android_text_AndroidBidi(JNIEnv *env);
extern int register_android_text_KeyCharacterMap(JNIEnv *env);
extern int register_android_opengl_classes(JNIEnv *env);
extern int register_android_bluetooth_HeadsetBase(JNIEnv* env);
extern int register_android_bluetooth_BluetoothAudioGateway(JNIEnv* env);
extern int register_android_bluetooth_BluetoothSocket(JNIEnv *env);
extern int register_android_bluetooth_ScoSocket(JNIEnv *env);
extern int register_android_server_BluetoothService(JNIEnv* env);
extern int register_android_server_BluetoothEventLoop(JNIEnv *env);
extern int register_android_server_BluetoothA2dpService(JNIEnv* env);
extern int register_android_server_Watchdog(JNIEnv* env);
extern int register_android_ddm_DdmHandleNativeHeap(JNIEnv *env);
extern int register_com_android_internal_os_ZygoteInit(JNIEnv* env);
extern int register_android_backup_BackupDataInput(JNIEnv *env);
extern int register_android_backup_BackupDataOutput(JNIEnv *env);
extern int register_android_backup_FileBackupHelperBase(JNIEnv *env);
extern int register_android_backup_BackupHelperDispatcher(JNIEnv *env);
extern int register_android_app_NativeActivity(JNIEnv *env);
extern int register_android_view_InputChannel(JNIEnv* env);
extern int register_android_view_InputQueue(JNIEnv* env);
extern int register_android_view_KeyEvent(JNIEnv* env);
extern int register_android_view_MotionEvent(JNIEnv* env);
extern int register_android_content_res_ObbScanner(JNIEnv* env);
extern int register_android_content_res_Configuration(JNIEnv* env);

static AndroidRuntime* gCurRuntime = NULL;

static void doThrow(JNIEnv* env, const char* exc, const char* msg = NULL)
{
    if (jniThrowException(env, exc, msg) != 0)
        assert(false);
}

/*
 * Code written in the Java Programming Language calls here from main().
 */
static void com_android_internal_os_RuntimeInit_finishInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onStarted();
}

static void com_android_internal_os_RuntimeInit_zygoteInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onZygoteInit();
}

static jint com_android_internal_os_RuntimeInit_isComputerOn(JNIEnv* env, jobject clazz)
{
    return 1;
}

static void com_android_internal_os_RuntimeInit_turnComputerOn(JNIEnv* env, jobject clazz)
{
}

static jint com_android_internal_os_RuntimeInit_getQwertyKeyboard(JNIEnv* env, jobject clazz)
{
    char* value = getenv("qwerty");
    if (value != NULL && strcmp(value, "true") == 0) {
        return 1;
    }
    
    return 0;
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    { "finishInit", "()V",
        (void*) com_android_internal_os_RuntimeInit_finishInit },
    { "zygoteInitNative", "()V",
        (void*) com_android_internal_os_RuntimeInit_zygoteInit },
    { "isComputerOn", "()I",
        (void*) com_android_internal_os_RuntimeInit_isComputerOn },
    { "turnComputerOn", "()V",
        (void*) com_android_internal_os_RuntimeInit_turnComputerOn },    
    { "getQwertyKeyboard", "()I",
        (void*) com_android_internal_os_RuntimeInit_getQwertyKeyboard },
};

int register_com_android_internal_os_RuntimeInit(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/internal/os/RuntimeInit",
        gMethods, NELEM(gMethods));
}

// ----------------------------------------------------------------------

/*static*/ JavaVM* AndroidRuntime::mJavaVM = NULL;


AndroidRuntime::AndroidRuntime()
{
    SkGraphics::Init();
    // this sets our preference for 16bit images during decode
    // in case the src is opaque and 24bit
    SkImageDecoder::SetDeviceConfig(SkBitmap::kRGB_565_Config);
    // This cache is shared between browser native images, and java "purgeable"
    // bitmaps. This globalpool is for images that do not either use the java
    // heap, or are not backed by ashmem. See BitmapFactory.cpp for the key
    // java call site.
    SkImageRef_GlobalPool::SetRAMBudget(512 * 1024);
    // There is also a global font cache, but its budget is specified in code
    // see SkFontHost_android.cpp

    // Pre-allocate enough space to hold a fair number of options.
    mOptions.setCapacity(20);

    assert(gCurRuntime == NULL);        // one per process
    gCurRuntime = this;
}

AndroidRuntime::~AndroidRuntime()
{
    SkGraphics::Term();
}

/*
 * Register native methods using JNI.
 */
/*static*/ int AndroidRuntime::registerNativeMethods(JNIEnv* env,
    const char* className, const JNINativeMethod* gMethods, int numMethods)
{
    return jniRegisterNativeMethods(env, className, gMethods, numMethods);
}

/*
 * Call a static Java Programming Language function that takes no arguments and returns void.
 */
status_t AndroidRuntime::callStatic(const char* className, const char* methodName)
{
    JNIEnv* env;
    jclass clazz;
    jmethodID methodId;

    env = getJNIEnv();
    if (env == NULL)
        return UNKNOWN_ERROR;

    clazz = findClass(env, className);
    if (clazz == NULL) {
        LOGE("ERROR: could not find class '%s'\n", className);
        return UNKNOWN_ERROR;
    }
    methodId = env->GetStaticMethodID(clazz, methodName, "()V");
    if (methodId == NULL) {
        LOGE("ERROR: could not find method %s.%s\n", className, methodName);
        return UNKNOWN_ERROR;
    }

    env->CallStaticVoidMethod(clazz, methodId);

    return NO_ERROR;
}

status_t AndroidRuntime::callMain(
    const char* className, int argc, const char* const argv[])
{
    JNIEnv* env;
    jclass clazz;
    jmethodID methodId;

    LOGD("Calling main entry %s", className);

    env = getJNIEnv();
    if (env == NULL)
        return UNKNOWN_ERROR;

    clazz = findClass(env, className);
    if (clazz == NULL) {
        LOGE("ERROR: could not find class '%s'\n", className);
        return UNKNOWN_ERROR;
    }

    methodId = env->GetStaticMethodID(clazz, "main", "([Ljava/lang/String;)V");
    if (methodId == NULL) {
        LOGE("ERROR: could not find method %s.main(String[])\n", className);
        return UNKNOWN_ERROR;
    }

    /*
     * We want to call main() with a String array with our arguments in it.
     * Create an array and populate it.
     */
    jclass stringClass;
    jobjectArray strArray;

    stringClass = env->FindClass("java/lang/String");
    strArray = env->NewObjectArray(argc, stringClass, NULL);

    for (int i = 0; i < argc; i++) {
        jstring argStr = env->NewStringUTF(argv[i]);
        env->SetObjectArrayElement(strArray, i, argStr);
    }

    env->CallStaticVoidMethod(clazz, methodId, strArray);
    return NO_ERROR;
}

/*
 * Find the named class.
 */
jclass AndroidRuntime::findClass(JNIEnv* env, const char* className)
{
    char* convName = NULL;

    if (env->ExceptionCheck()) {
        LOGE("ERROR: exception pending on entry to findClass()\n");
        return NULL;
    }

    /*
     * JNI FindClass uses class names with slashes, but ClassLoader.loadClass
     * uses the dotted "binary name" format.  We don't need to convert the
     * name with the new approach.
     */
#if 0
    /* (convName only created if necessary -- use className) */
    for (char* cp = const_cast<char*>(className); *cp != '\0'; cp++) {
        if (*cp == '.') {
            if (convName == NULL) {
                convName = strdup(className);
                cp = convName + (cp-className);
                className = convName;
            }
            *cp = '/';
        }
    }
#endif

    /*
     * This is a little awkward because the JNI FindClass call uses the
     * class loader associated with the native method we're executing in.
     * Because this native method is part of a "boot" class, JNI doesn't
     * look for the class in CLASSPATH, which unfortunately is a likely
     * location for it.  (Had we issued the FindClass call before calling
     * into the VM -- at which point there isn't a native method frame on
     * the stack -- the VM would have checked CLASSPATH.  We have to do
     * this because we call into Java Programming Language code and
     * bounce back out.)
     *
     * JNI lacks a "find class in a specific class loader" operation, so we
     * have to do things the hard way.
     */
    jclass cls = NULL;
    //cls = env->FindClass(className);

    jclass javaLangClassLoader;
    jmethodID getSystemClassLoader, loadClass;
    jobject systemClassLoader;
    jstring strClassName;

    /* find the "system" class loader; none of this is expected to fail */
    javaLangClassLoader = env->FindClass("java/lang/ClassLoader");
    assert(javaLangClassLoader != NULL);
    getSystemClassLoader = env->GetStaticMethodID(javaLangClassLoader,
        "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    loadClass = env->GetMethodID(javaLangClassLoader,
        "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    assert(getSystemClassLoader != NULL && loadClass != NULL);
    systemClassLoader = env->CallStaticObjectMethod(javaLangClassLoader,
        getSystemClassLoader);
    assert(systemClassLoader != NULL);

    /* create an object for the class name string; alloc could fail */
    strClassName = env->NewStringUTF(className);
    if (env->ExceptionCheck()) {
        LOGE("ERROR: unable to convert '%s' to string\n", className);
        goto bail;
    }
    LOGV("system class loader is %p, loading %p (%s)\n",
        systemClassLoader, strClassName, className);

    /* try to find the named class */
    cls = (jclass) env->CallObjectMethod(systemClassLoader, loadClass,
                        strClassName);
    if (env->ExceptionCheck()) {
        LOGE("ERROR: unable to load class '%s' from %p\n",
            className, systemClassLoader);
        cls = NULL;
        goto bail;
    }

bail:
    free(convName);
    return cls;
}

/*
 * The VM calls this through the "exit" hook.
 */
static void runtime_exit(int code)
{
    gCurRuntime->onExit(code);
    exit(code);
}

/*
 * The VM calls this through the "vfprintf" hook.
 *
 * We ignore "fp" and just write the results to the log file.
 */
static void runtime_vfprintf(FILE* fp, const char* format, va_list ap)
{
    LOG_PRI_VA(ANDROID_LOG_INFO, "vm-printf", format, ap);
}


/**
 * Add VM arguments to the to-be-executed VM
 * Stops at first non '-' argument (also stops at an argument of '--')
 * Returns the number of args consumed
 */
int AndroidRuntime::addVmArguments(int argc, const char* const argv[])
{
    int i;
    
    for (i = 0; i<argc; i++) {
        if (argv[i][0] != '-') {
            return i;
        }
        if (argv[i][1] == '-' && argv[i][2] == 0) {
            return i+1;
        }

        JavaVMOption opt;
        memset(&opt, 0, sizeof(opt));
        opt.optionString = (char*)argv[i];
        mOptions.add(opt);
    }
    return i;
}

static int hasDir(const char* dir)
{
    struct stat s;
    int res = stat(dir, &s);
    if (res == 0) {
        return S_ISDIR(s.st_mode);
    }
    return 0;
}

/*
 * We just want failed write() calls to just return with an error.
 */
static void blockSigpipe()
{
    sigset_t mask;

    sigemptyset(&mask);
    sigaddset(&mask, SIGPIPE);
    if (sigprocmask(SIG_BLOCK, &mask, NULL) != 0)
        LOGW("WARNING: SIGPIPE not blocked\n");
}

/*
 * Read the persistent locale.
 */
static void readLocale(char* language, char* region)
{
    char propLang[PROPERTY_VALUE_MAX], propRegn[PROPERTY_VALUE_MAX];

    property_get("persist.sys.language", propLang, "");
    property_get("persist.sys.country", propRegn, "");
    if (*propLang == 0 && *propRegn == 0) {
        /* Set to ro properties, default is en_US */
        property_get("ro.product.locale.language", propLang, "en");
        property_get("ro.product.locale.region", propRegn, "US");
    }
    strncat(language, propLang, 2);
    strncat(region, propRegn, 2);
    //LOGD("language=%s region=%s\n", language, region);
}

/*
 * Parse a property containing space-separated options that should be
 * passed directly to the VM, e.g. "-Xmx32m -verbose:gc -Xregenmap".
 *
 * This will cut up "extraOptsBuf" as we chop it into individual options.
 *
 * Adds the strings, if any, to mOptions.
 */
void AndroidRuntime::parseExtraOpts(char* extraOptsBuf)
{
    JavaVMOption opt;
    char* start;
    char* end;

    memset(&opt, 0, sizeof(opt));
    start = extraOptsBuf;
    while (*start != '\0') {
        while (*start == ' ')                   /* skip leading whitespace */
            start++;
        if (*start == '\0')                     /* was trailing ws, bail */
            break;

        end = start+1;
        while (*end != ' ' && *end != '\0')     /* find end of token */
            end++;
        if (*end == ' ')
            *end++ = '\0';          /* mark end, advance to indicate more */

        opt.optionString = start;
        mOptions.add(opt);
        start = end;
    }
}

/*
 * Start the Dalvik Virtual Machine.
 *
 * Various arguments, most determined by system properties, are passed in.
 * The "mOptions" vector is updated.
 *
 * Returns 0 on success.
 */
int AndroidRuntime::startVm(JavaVM** pJavaVM, JNIEnv** pEnv)
{
    int result = -1;
    JavaVMInitArgs initArgs;
    JavaVMOption opt;
    char propBuf[PROPERTY_VALUE_MAX];
    char stackTraceFileBuf[PROPERTY_VALUE_MAX];
    char dexoptFlagsBuf[PROPERTY_VALUE_MAX];
    char enableAssertBuf[sizeof("-ea:")-1 + PROPERTY_VALUE_MAX];
    char jniOptsBuf[sizeof("-Xjniopts:")-1 + PROPERTY_VALUE_MAX];
    char heapsizeOptsBuf[sizeof("-Xmx")-1 + PROPERTY_VALUE_MAX];
    char extraOptsBuf[PROPERTY_VALUE_MAX];
    char* stackTraceFile = NULL;
    bool checkJni = false;
    bool checkDexSum = false;
    bool logStdio = false;
    enum {
      kEMDefault,
      kEMIntPortable,
      kEMIntFast,
#if defined(WITH_JIT)
      kEMJitCompiler,
#endif
    } executionMode = kEMDefault;


    property_get("dalvik.vm.checkjni", propBuf, "");
    if (strcmp(propBuf, "true") == 0) {
        checkJni = true;
    } else if (strcmp(propBuf, "false") != 0) {
        /* property is neither true nor false; fall back on kernel parameter */
        property_get("ro.kernel.android.checkjni", propBuf, "");
        if (propBuf[0] == '1') {
            checkJni = true;
        }
    }

    property_get("persist.sys.jit-mode", propBuf, "");
    if (strcmp(propBuf, "") == 0) {
        property_get("dalvik.vm.execution-mode", propBuf, "");
    }
    if (strcmp(propBuf, "int:portable") == 0) {
        executionMode = kEMIntPortable;
    } else if (strcmp(propBuf, "int:fast") == 0) {
        executionMode = kEMIntFast;
#if defined(WITH_JIT)
    } else if (strcmp(propBuf, "int:jit") == 0) {
        executionMode = kEMJitCompiler;
#endif
    }

    property_get("dalvik.vm.stack-trace-file", stackTraceFileBuf, "");

    property_get("dalvik.vm.check-dex-sum", propBuf, "");
    if (strcmp(propBuf, "true") == 0) {
        checkDexSum = true;
    }

    property_get("log.redirect-stdio", propBuf, "");
    if (strcmp(propBuf, "true") == 0) {
        logStdio = true;
    }

    strcpy(enableAssertBuf, "-ea:");
    property_get("dalvik.vm.enableassertions", enableAssertBuf+4, "");

    strcpy(jniOptsBuf, "-Xjniopts:");
    property_get("dalvik.vm.jniopts", jniOptsBuf+10, "");

    /* route exit() to our handler */
    opt.extraInfo = (void*) runtime_exit;
    opt.optionString = "exit";
    mOptions.add(opt);

    /* route fprintf() to our handler */
    opt.extraInfo = (void*) runtime_vfprintf;
    opt.optionString = "vfprintf";
    mOptions.add(opt);

    opt.extraInfo = NULL;

    /* enable verbose; standard options are { jni, gc, class } */
    //options[curOpt++].optionString = "-verbose:jni";
    opt.optionString = "-verbose:gc";
    mOptions.add(opt);
    //options[curOpt++].optionString = "-verbose:class";

    strcpy(heapsizeOptsBuf, "-Xmx");
    property_get("persist.sys.vm.heapsize", propBuf, "");
    if (strcmp(propBuf, "") == 0) {
        property_get("dalvik.vm.heapsize", propBuf, "16m");
    }
    strcpy(heapsizeOptsBuf+4, propBuf);
    LOGI("Heap size: %s", heapsizeOptsBuf);
    opt.optionString = heapsizeOptsBuf;
    mOptions.add(opt);

    /*
     * Enable or disable dexopt features, such as bytecode verification and
     * calculation of register maps for precise GC.
     */
    property_get("dalvik.vm.dexopt-flags", dexoptFlagsBuf, "");
    if (dexoptFlagsBuf[0] != '\0') {
        const char* opc;
        const char* val;

        opc = strstr(dexoptFlagsBuf, "v=");     /* verification */
        if (opc != NULL) {
            switch (*(opc+2)) {
            case 'n':   val = "-Xverify:none";      break;
            case 'r':   val = "-Xverify:remote";    break;
            case 'a':   val = "-Xverify:all";       break;
            default:    val = NULL;                 break;
            }

            if (val != NULL) {
                opt.optionString = val;
                mOptions.add(opt);
            }
        }

        opc = strstr(dexoptFlagsBuf, "o=");     /* optimization */
        if (opc != NULL) {
            switch (*(opc+2)) {
            case 'n':   val = "-Xdexopt:none";      break;
            case 'v':   val = "-Xdexopt:verified";  break;
            case 'a':   val = "-Xdexopt:all";       break;
            default:    val = NULL;                 break;
            }

            if (val != NULL) {
                opt.optionString = val;
                mOptions.add(opt);
            }
        }

        opc = strstr(dexoptFlagsBuf, "m=y");    /* register map */
        if (opc != NULL) {
            opt.optionString = "-Xgenregmap";
            mOptions.add(opt);

            /* turn on precise GC while we're at it */
            opt.optionString = "-Xgc:precise";
            mOptions.add(opt);
        }
    }

    /* enable debugging; set suspend=y to pause during VM init */
#ifdef HAVE_ANDROID_OS
    /* use android ADB transport */
    opt.optionString =
        "-agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y";
#else
    /* use TCP socket; address=0 means start at port 8000 and probe up */
    LOGI("Using TCP socket for JDWP\n");
    opt.optionString =
        "-agentlib:jdwp=transport=dt_socket,suspend=n,server=y,address=0";
#endif
    mOptions.add(opt);

    char enableDPBuf[sizeof("-Xdeadlockpredict:") + PROPERTY_VALUE_MAX];
    property_get("dalvik.vm.deadlock-predict", propBuf, "");
    if (strlen(propBuf) > 0) {
        strcpy(enableDPBuf, "-Xdeadlockpredict:");
        strcat(enableDPBuf, propBuf);
        opt.optionString = enableDPBuf;
        mOptions.add(opt);
    }

    LOGD("CheckJNI is %s\n", checkJni ? "ON" : "OFF");
    if (checkJni) {
        /* extended JNI checking */
        opt.optionString = "-Xcheck:jni";
        mOptions.add(opt);

        /* set a cap on JNI global references */
        opt.optionString = "-Xjnigreflimit:2000";
        mOptions.add(opt);

        /* with -Xcheck:jni, this provides a JNI function call trace */
        //opt.optionString = "-verbose:jni";
        //mOptions.add(opt);
    }

    char lockProfThresholdBuf[sizeof("-Xlockprofthreshold:") + sizeof(propBuf)];
    property_get("dalvik.vm.lockprof.threshold", propBuf, "");
    if (strlen(propBuf) > 0) {
      strcpy(lockProfThresholdBuf, "-Xlockprofthreshold:");
      strcat(lockProfThresholdBuf, propBuf);
      opt.optionString = lockProfThresholdBuf;
      mOptions.add(opt);
    }

#if defined(WITH_JIT)
    /* Force interpreter-only mode for selected opcodes. Eg "1-0a,3c,f1-ff" */
    char jitOpBuf[sizeof("-Xjitop:") + PROPERTY_VALUE_MAX];
    property_get("dalvik.vm.jit.op", propBuf, "");
    if (strlen(propBuf) > 0) {
        strcpy(jitOpBuf, "-Xjitop:");
        strcat(jitOpBuf, propBuf);
        opt.optionString = jitOpBuf;
        mOptions.add(opt);
    }

    /* Force interpreter-only mode for selected methods */
    char jitMethodBuf[sizeof("-Xjitmethod:") + PROPERTY_VALUE_MAX];
    property_get("dalvik.vm.jit.method", propBuf, "");
    if (strlen(propBuf) > 0) {
        strcpy(jitMethodBuf, "-Xjitmethod:");
        strcat(jitMethodBuf, propBuf);
        opt.optionString = jitMethodBuf;
        mOptions.add(opt);
    }
#endif

    if (executionMode == kEMIntPortable) {
        opt.optionString = "-Xint:portable";
        mOptions.add(opt);
    } else if (executionMode == kEMIntFast) {
        opt.optionString = "-Xint:fast";
        mOptions.add(opt);
#if defined(WITH_JIT)
    } else if (executionMode == kEMJitCompiler) {
        opt.optionString = "-Xint:jit";
        mOptions.add(opt);
#endif
    }

    if (checkDexSum) {
        /* perform additional DEX checksum tests */
        opt.optionString = "-Xcheckdexsum";
        mOptions.add(opt);
    }

    if (logStdio) {
        /* convert stdout/stderr to log messages */
        opt.optionString = "-Xlog-stdio";
        mOptions.add(opt);
    }

    if (enableAssertBuf[4] != '\0') {
        /* accept "all" to mean "all classes and packages" */
        if (strcmp(enableAssertBuf+4, "all") == 0)
            enableAssertBuf[3] = '\0';
        LOGI("Assertions enabled: '%s'\n", enableAssertBuf);
        opt.optionString = enableAssertBuf;
        mOptions.add(opt);
    } else {
        LOGV("Assertions disabled\n");
    }

    if (jniOptsBuf[10] != '\0') {
        LOGI("JNI options: '%s'\n", jniOptsBuf);
        opt.optionString = jniOptsBuf;
        mOptions.add(opt);
    }

    if (stackTraceFileBuf[0] != '\0') {
        static const char* stfOptName = "-Xstacktracefile:";

        stackTraceFile = (char*) malloc(strlen(stfOptName) +
            strlen(stackTraceFileBuf) +1);
        strcpy(stackTraceFile, stfOptName);
        strcat(stackTraceFile, stackTraceFileBuf);
        opt.optionString = stackTraceFile;
        mOptions.add(opt);
    }

    /* extra options; parse this late so it overrides others */
    property_get("dalvik.vm.extra-opts", extraOptsBuf, "");
    parseExtraOpts(extraOptsBuf);

    /* Set the properties for locale */
    {
        char langOption[sizeof("-Duser.language=") + 3];
        char regionOption[sizeof("-Duser.region=") + 3];
        strcpy(langOption, "-Duser.language=");
        strcpy(regionOption, "-Duser.region=");
        readLocale(langOption, regionOption);
        opt.extraInfo = NULL;
        opt.optionString = langOption;
        mOptions.add(opt);
        opt.optionString = regionOption;
        mOptions.add(opt);
    }

    /*
     * We don't have /tmp on the device, but we often have an SD card.  Apps
     * shouldn't use this, but some test suites might want to exercise it.
     */
    opt.optionString = "-Djava.io.tmpdir=/sdcard";
    mOptions.add(opt);

    initArgs.version = JNI_VERSION_1_4;
    initArgs.options = mOptions.editArray();
    initArgs.nOptions = mOptions.size();
    initArgs.ignoreUnrecognized = JNI_FALSE;

    /*
     * Initialize the VM.
     *
     * The JavaVM* is essentially per-process, and the JNIEnv* is per-thread.
     * If this call succeeds, the VM is ready, and we can start issuing
     * JNI calls.
     */
    if (JNI_CreateJavaVM(pJavaVM, pEnv, &initArgs) < 0) {
        LOGE("JNI_CreateJavaVM failed\n");
        goto bail;
    }

    result = 0;

bail:
    free(stackTraceFile);
    return result;
}

/*
 * Start the Android runtime.  This involves starting the virtual machine
 * and calling the "static void main(String[] args)" method in the class
 * named by "className".
 */
void AndroidRuntime::start(const char* className, const bool startSystemServer)
{
    LOGD("\n>>>>>> AndroidRuntime START %s <<<<<<\n",
            className != NULL ? className : "(unknown)");

    char* slashClassName = NULL;
    char* cp;
    JNIEnv* env;

    blockSigpipe();

    /* 
     * 'startSystemServer == true' means runtime is obslete and not run from 
     * init.rc anymore, so we print out the boot start event here.
     */
    if (startSystemServer) {
        /* track our progress through the boot sequence */
        const int LOG_BOOT_PROGRESS_START = 3000;
        LOG_EVENT_LONG(LOG_BOOT_PROGRESS_START, 
                       ns2ms(systemTime(SYSTEM_TIME_MONOTONIC)));
    }

    const char* rootDir = getenv("ANDROID_ROOT");
    if (rootDir == NULL) {
        rootDir = "/system";
        if (!hasDir("/system")) {
            LOG_FATAL("No root directory specified, and /android does not exist.");
            goto bail;
        }
        setenv("ANDROID_ROOT", rootDir, 1);
    }

    //const char* kernelHack = getenv("LD_ASSUME_KERNEL");
    //LOGD("Found LD_ASSUME_KERNEL='%s'\n", kernelHack);

    /* start the virtual machine */
    if (startVm(&mJavaVM, &env) != 0)
        goto bail;

    /*
     * Register android functions.
     */
    if (startReg(env) < 0) {
        LOGE("Unable to register all android natives\n");
        goto bail;
    }

    /*
     * We want to call main() with a String array with arguments in it.
     * At present we only have one argument, the class name.  Create an
     * array to hold it.
     */
    jclass stringClass;
    jobjectArray strArray;
    jstring classNameStr;
    jstring startSystemServerStr;

    stringClass = env->FindClass("java/lang/String");
    assert(stringClass != NULL);
    strArray = env->NewObjectArray(2, stringClass, NULL);
    assert(strArray != NULL);
    classNameStr = env->NewStringUTF(className);
    assert(classNameStr != NULL);
    env->SetObjectArrayElement(strArray, 0, classNameStr);
    startSystemServerStr = env->NewStringUTF(startSystemServer ? 
                                                 "true" : "false");
    env->SetObjectArrayElement(strArray, 1, startSystemServerStr);

    /*
     * Start VM.  This thread becomes the main thread of the VM, and will
     * not return until the VM exits.
     */
    jclass startClass;
    jmethodID startMeth;

    slashClassName = strdup(className);
    for (cp = slashClassName; *cp != '\0'; cp++)
        if (*cp == '.')
            *cp = '/';

    startClass = env->FindClass(slashClassName);
    if (startClass == NULL) {
        LOGE("JavaVM unable to locate class '%s'\n", slashClassName);
        /* keep going */
    } else {
        startMeth = env->GetStaticMethodID(startClass, "main",
            "([Ljava/lang/String;)V");
        if (startMeth == NULL) {
            LOGE("JavaVM unable to find main() in '%s'\n", className);
            /* keep going */
        } else {
            env->CallStaticVoidMethod(startClass, startMeth, strArray);

#if 0
            if (env->ExceptionCheck())
                threadExitUncaughtException(env);
#endif
        }
    }

    LOGD("Shutting down VM\n");
    if (mJavaVM->DetachCurrentThread() != JNI_OK)
        LOGW("Warning: unable to detach main thread\n");
    if (mJavaVM->DestroyJavaVM() != 0)
        LOGW("Warning: VM did not shut down cleanly\n");

bail:
    free(slashClassName);
}

void AndroidRuntime::start()
{
    start("com.android.internal.os.RuntimeInit",
        false /* Don't start the system server */);
}

void AndroidRuntime::onExit(int code)
{
    LOGV("AndroidRuntime onExit calling exit(%d)", code);
    exit(code);
}

/*
 * Get the JNIEnv pointer for this thread.
 *
 * Returns NULL if the slot wasn't allocated or populated.
 */
/*static*/ JNIEnv* AndroidRuntime::getJNIEnv()
{
    JNIEnv* env;
    JavaVM* vm = AndroidRuntime::getJavaVM();
    assert(vm != NULL);

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK)
        return NULL;
    return env;
}

/*
 * Makes the current thread visible to the VM.
 *
 * The JNIEnv pointer returned is only valid for the current thread, and
 * thus must be tucked into thread-local storage.
 */
static int javaAttachThread(const char* threadName, JNIEnv** pEnv)
{
    JavaVMAttachArgs args;
    JavaVM* vm;
    jint result;

    vm = AndroidRuntime::getJavaVM();
    assert(vm != NULL);

    args.version = JNI_VERSION_1_4;
    args.name = (char*) threadName;
    args.group = NULL;

    result = vm->AttachCurrentThread(pEnv, (void*) &args);
    if (result != JNI_OK)
        LOGI("NOTE: attach of thread '%s' failed\n", threadName);

    return result;
}

/*
 * Detach the current thread from the set visible to the VM.
 */
static int javaDetachThread(void)
{
    JavaVM* vm;
    jint result;

    vm = AndroidRuntime::getJavaVM();
    assert(vm != NULL);

    result = vm->DetachCurrentThread();
    if (result != JNI_OK)
        LOGE("ERROR: thread detach failed\n");
    return result;
}

/*
 * When starting a native thread that will be visible from the VM, we
 * bounce through this to get the right attach/detach action.
 * Note that this function calls free(args)
 */
/*static*/ int AndroidRuntime::javaThreadShell(void* args) {
    void* start = ((void**)args)[0];
    void* userData = ((void **)args)[1];
    char* name = (char*) ((void **)args)[2];        // we own this storage
    free(args);
    JNIEnv* env;
    int result;

    /* hook us into the VM */
    if (javaAttachThread(name, &env) != JNI_OK)
        return -1;

    /* start the thread running */
    result = (*(android_thread_func_t)start)(userData);

    /* unhook us */
    javaDetachThread();
    free(name);

    return result;
}

/*
 * This is invoked from androidCreateThreadEtc() via the callback
 * set with androidSetCreateThreadFunc().
 *
 * We need to create the new thread in such a way that it gets hooked
 * into the VM before it really starts executing.
 */
/*static*/ int AndroidRuntime::javaCreateThreadEtc(
                                android_thread_func_t entryFunction, 
                                void* userData,
                                const char* threadName,
                                int32_t threadPriority,
                                size_t threadStackSize,
                                android_thread_id_t* threadId)
{
    void** args = (void**) malloc(3 * sizeof(void*));   // javaThreadShell must free
    int result;

    assert(threadName != NULL);

    args[0] = (void*) entryFunction;
    args[1] = userData;
    args[2] = (void*) strdup(threadName);   // javaThreadShell must free

    result = androidCreateRawThreadEtc(AndroidRuntime::javaThreadShell, args,
        threadName, threadPriority, threadStackSize, threadId);
    return result;
}

/*
 * Create a thread that is visible from the VM.
 *
 * This is called from elsewhere in the library.
 */
/*static*/ android_thread_id_t AndroidRuntime::createJavaThread(const char* name,
    void (*start)(void *), void* arg)
{
    android_thread_id_t threadId = 0;
    javaCreateThreadEtc((android_thread_func_t) start, arg, name,
        ANDROID_PRIORITY_DEFAULT, 0, &threadId);
    return threadId;
}

#if 0
static void quickTest(void* arg)
{
    const char* str = (const char*) arg;

    printf("In quickTest: %s\n", str);
}
#endif

#ifdef NDEBUG
    #define REG_JNI(name)      { name }
    struct RegJNIRec {
        int (*mProc)(JNIEnv*);
    };
#else
    #define REG_JNI(name)      { name, #name }
    struct RegJNIRec {
        int (*mProc)(JNIEnv*);
        const char* mName;
    };
#endif

typedef void (*RegJAMProc)();

static int register_jni_procs(const RegJNIRec array[], size_t count, JNIEnv* env)
{
    for (size_t i = 0; i < count; i++) {
        if (array[i].mProc(env) < 0) {
#ifndef NDEBUG
            LOGD("----------!!! %s failed to load\n", array[i].mName);
#endif
            return -1;
        }
    }
    return 0;
}

static void register_jam_procs(const RegJAMProc array[], size_t count)
{
    for (size_t i = 0; i < count; i++) {
        array[i]();
    }
}

static const RegJNIRec gRegJNI[] = {
    REG_JNI(register_android_debug_JNITest),
    REG_JNI(register_com_android_internal_os_RuntimeInit),
    REG_JNI(register_android_os_SystemClock),
    REG_JNI(register_android_util_EventLog),
    REG_JNI(register_android_util_Log),
    REG_JNI(register_android_util_FloatMath),
    REG_JNI(register_android_text_format_Time),
    REG_JNI(register_android_pim_EventRecurrence),
    REG_JNI(register_android_content_AssetManager),
    REG_JNI(register_android_content_StringBlock),
    REG_JNI(register_android_content_XmlBlock),
    REG_JNI(register_android_emoji_EmojiFactory),
    REG_JNI(register_android_security_Md5MessageDigest),
    REG_JNI(register_android_text_AndroidCharacter),
    REG_JNI(register_android_text_AndroidBidi),
    REG_JNI(register_android_text_KeyCharacterMap),
    REG_JNI(register_android_os_Process),
    REG_JNI(register_android_os_Binder),
    REG_JNI(register_android_view_Display),
    REG_JNI(register_android_nio_utils),
    REG_JNI(register_android_graphics_PixelFormat),
    REG_JNI(register_android_graphics_Graphics),
    REG_JNI(register_android_view_Surface),
    REG_JNI(register_android_view_ViewRoot),
    REG_JNI(register_com_google_android_gles_jni_EGLImpl),
    REG_JNI(register_com_google_android_gles_jni_GLImpl),
    REG_JNI(register_android_opengl_jni_GLES10),
    REG_JNI(register_android_opengl_jni_GLES10Ext),
    REG_JNI(register_android_opengl_jni_GLES11),
    REG_JNI(register_android_opengl_jni_GLES11Ext),
    REG_JNI(register_android_opengl_jni_GLES20),

    REG_JNI(register_android_graphics_Bitmap),
    REG_JNI(register_android_graphics_BitmapFactory),
    REG_JNI(register_android_graphics_Camera),
    REG_JNI(register_android_graphics_Canvas),
    REG_JNI(register_android_graphics_ColorFilter),
    REG_JNI(register_android_graphics_DrawFilter),
    REG_JNI(register_android_graphics_Interpolator),
    REG_JNI(register_android_graphics_LayerRasterizer),
    REG_JNI(register_android_graphics_MaskFilter),
    REG_JNI(register_android_graphics_Matrix),
    REG_JNI(register_android_graphics_Movie),
    REG_JNI(register_android_graphics_NinePatch),
    REG_JNI(register_android_graphics_Paint),
    REG_JNI(register_android_graphics_Path),
    REG_JNI(register_android_graphics_PathMeasure),
    REG_JNI(register_android_graphics_PathEffect),
    REG_JNI(register_android_graphics_Picture),
    REG_JNI(register_android_graphics_PorterDuff),
    REG_JNI(register_android_graphics_Rasterizer),
    REG_JNI(register_android_graphics_Region),
    REG_JNI(register_android_graphics_Shader),
    REG_JNI(register_android_graphics_Typeface),
    REG_JNI(register_android_graphics_Xfermode),
    REG_JNI(register_android_graphics_YuvImage),
    REG_JNI(register_com_android_internal_graphics_NativeUtils),

    REG_JNI(register_android_database_CursorWindow),
    REG_JNI(register_android_database_SQLiteCompiledSql),
    REG_JNI(register_android_database_SQLiteDatabase),
    REG_JNI(register_android_database_SQLiteDebug),
    REG_JNI(register_android_database_SQLiteProgram),
    REG_JNI(register_android_database_SQLiteQuery),
    REG_JNI(register_android_database_SQLiteStatement),
    REG_JNI(register_android_os_Debug),
    REG_JNI(register_android_os_FileObserver),
    REG_JNI(register_android_os_FileUtils),
    REG_JNI(register_android_os_MessageQueue),
    REG_JNI(register_android_os_ParcelFileDescriptor),
    REG_JNI(register_android_os_Power),
    REG_JNI(register_android_os_StatFs),
    REG_JNI(register_android_os_SystemProperties),
    REG_JNI(register_android_os_UEventObserver),
    REG_JNI(register_android_net_LocalSocketImpl),
    REG_JNI(register_android_net_NetworkUtils),
    REG_JNI(register_android_net_TrafficStats),
    REG_JNI(register_android_net_wifi_WifiManager),
    REG_JNI(register_android_nfc_NdefMessage),
    REG_JNI(register_android_nfc_NdefRecord),
    REG_JNI(register_android_os_MemoryFile),
    REG_JNI(register_com_android_internal_os_ZygoteInit),
    REG_JNI(register_android_hardware_Camera),
#ifdef HAVE_FM_RADIO
    REG_JNI(register_android_hardware_fm_fmradio),
#endif
    REG_JNI(register_android_hardware_SensorManager),
    REG_JNI(register_android_media_AudioRecord),
    REG_JNI(register_android_media_AudioSystem),
    REG_JNI(register_android_media_AudioTrack),
    REG_JNI(register_android_media_JetPlayer),
    REG_JNI(register_android_media_ToneGenerator),

    REG_JNI(register_android_opengl_classes),
    REG_JNI(register_android_bluetooth_HeadsetBase),
    REG_JNI(register_android_bluetooth_BluetoothAudioGateway),
    REG_JNI(register_android_bluetooth_BluetoothSocket),
    REG_JNI(register_android_bluetooth_ScoSocket),
    REG_JNI(register_android_server_BluetoothService),
    REG_JNI(register_android_server_BluetoothEventLoop),
    REG_JNI(register_android_server_BluetoothA2dpService),
    REG_JNI(register_android_server_Watchdog),
    REG_JNI(register_android_message_digest_sha1),
    REG_JNI(register_android_ddm_DdmHandleNativeHeap),
    REG_JNI(register_android_backup_BackupDataInput),
    REG_JNI(register_android_backup_BackupDataOutput),
    REG_JNI(register_android_backup_FileBackupHelperBase),
    REG_JNI(register_android_backup_BackupHelperDispatcher),
    
    REG_JNI(register_android_app_NativeActivity),
    REG_JNI(register_android_view_InputChannel),
    REG_JNI(register_android_view_InputQueue),
    REG_JNI(register_android_view_KeyEvent),
    REG_JNI(register_android_view_MotionEvent),

    REG_JNI(register_android_content_res_ObbScanner),
    REG_JNI(register_android_content_res_Configuration),
};

/*
 * Register android native functions with the VM.
 */
/*static*/ int AndroidRuntime::startReg(JNIEnv* env)
{
    /*
     * This hook causes all future threads created in this process to be
     * attached to the JavaVM.  (This needs to go away in favor of JNI
     * Attach calls.)
     */
    androidSetCreateThreadFunc((android_create_thread_fn) javaCreateThreadEtc);

    LOGV("--- registering native functions ---\n");

    /*
     * Every "register" function calls one or more things that return
     * a local reference (e.g. FindClass).  Because we haven't really
     * started the VM yet, they're all getting stored in the base frame
     * and never released.  Use Push/Pop to manage the storage.
     */
    env->PushLocalFrame(200);

    if (register_jni_procs(gRegJNI, NELEM(gRegJNI), env) < 0) {
        env->PopLocalFrame(NULL);
        return -1;
    }
    env->PopLocalFrame(NULL);

    //createJavaThread("fubar", quickTest, (void*) "hello");

    return 0;
}

AndroidRuntime* AndroidRuntime::getRuntime()
{
    return gCurRuntime;
}

/**
 * Used by WithFramework to register native functions.
 */
extern "C"
jint Java_com_android_internal_util_WithFramework_registerNatives(
        JNIEnv* env, jclass clazz) {
    return register_jni_procs(gRegJNI, NELEM(gRegJNI), env);
}

/**
 * Used by LoadClass to register native functions.
 */
extern "C"
jint Java_LoadClass_registerNatives(JNIEnv* env, jclass clazz) {
    return register_jni_procs(gRegJNI, NELEM(gRegJNI), env);
}

}   // namespace android
