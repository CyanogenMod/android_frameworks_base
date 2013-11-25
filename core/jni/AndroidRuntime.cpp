/*
 * Copyright (C) 2006 The Android Open Source Project
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

#define LOG_TAG "AndroidRuntime"
//#define LOG_NDEBUG 0

#include <android_runtime/AndroidRuntime.h>
#include <binder/IBinder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <binder/Parcel.h>
#include <utils/threads.h>
#include <cutils/properties.h>

#include <SkGraphics.h>
#include <SkImageDecoder.h>
#include <SkImageRef_GlobalPool.h>

#include "jni.h"
#include "JNIHelp.h"
#include "JniInvocation.h"
#include "android_util_Binder.h"

#include <stdio.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <signal.h>
#include <dirent.h>
#include <assert.h>


using namespace android;

extern int register_android_os_Binder(JNIEnv* env);
extern int register_android_os_Process(JNIEnv* env);
extern int register_android_graphics_Bitmap(JNIEnv*);
extern int register_android_graphics_BitmapFactory(JNIEnv*);
extern int register_android_graphics_BitmapRegionDecoder(JNIEnv*);
extern int register_android_graphics_Camera(JNIEnv* env);
extern int register_android_graphics_CreateJavaOutputStreamAdaptor(JNIEnv* env);
extern int register_android_graphics_Graphics(JNIEnv* env);
extern int register_android_graphics_Interpolator(JNIEnv* env);
extern int register_android_graphics_LayerRasterizer(JNIEnv*);
extern int register_android_graphics_MaskFilter(JNIEnv* env);
extern int register_android_graphics_Movie(JNIEnv* env);
extern int register_android_graphics_NinePatch(JNIEnv*);
extern int register_android_graphics_PathEffect(JNIEnv* env);
extern int register_android_graphics_Shader(JNIEnv* env);
extern int register_android_graphics_Typeface(JNIEnv* env);
extern int register_android_graphics_YuvImage(JNIEnv* env);

extern int register_com_google_android_gles_jni_EGLImpl(JNIEnv* env);
extern int register_com_google_android_gles_jni_GLImpl(JNIEnv* env);
extern int register_android_opengl_jni_EGL14(JNIEnv* env);
extern int register_android_opengl_jni_EGLExt(JNIEnv* env);
extern int register_android_opengl_jni_GLES10(JNIEnv* env);
extern int register_android_opengl_jni_GLES10Ext(JNIEnv* env);
extern int register_android_opengl_jni_GLES11(JNIEnv* env);
extern int register_android_opengl_jni_GLES11Ext(JNIEnv* env);
extern int register_android_opengl_jni_GLES20(JNIEnv* env);
extern int register_android_opengl_jni_GLES30(JNIEnv* env);

extern int register_android_hardware_Camera(JNIEnv *env);
extern int register_android_hardware_camera2_CameraMetadata(JNIEnv *env);
extern int register_android_hardware_SensorManager(JNIEnv *env);
extern int register_android_hardware_SerialPort(JNIEnv *env);
extern int register_android_hardware_UsbDevice(JNIEnv *env);
extern int register_android_hardware_UsbDeviceConnection(JNIEnv *env);
extern int register_android_hardware_UsbRequest(JNIEnv *env);

extern int register_android_media_AudioRecord(JNIEnv *env);
extern int register_android_media_AudioSystem(JNIEnv *env);
extern int register_android_media_AudioTrack(JNIEnv *env);
extern int register_android_media_JetPlayer(JNIEnv *env);
extern int register_android_media_ToneGenerator(JNIEnv *env);

extern int register_android_util_FloatMath(JNIEnv* env);

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
extern int register_android_graphics_Region(JNIEnv* env);
extern int register_android_graphics_SurfaceTexture(JNIEnv* env);
extern int register_android_graphics_Xfermode(JNIEnv* env);
extern int register_android_graphics_pdf_PdfDocument(JNIEnv* env);
extern int register_android_view_DisplayEventReceiver(JNIEnv* env);
extern int register_android_view_GraphicBuffer(JNIEnv* env);
extern int register_android_view_GLES20DisplayList(JNIEnv* env);
extern int register_android_view_GLES20Canvas(JNIEnv* env);
extern int register_android_view_HardwareRenderer(JNIEnv* env);
extern int register_android_view_Surface(JNIEnv* env);
extern int register_android_view_SurfaceControl(JNIEnv* env);
extern int register_android_view_SurfaceSession(JNIEnv* env);
extern int register_android_view_TextureView(JNIEnv* env);
extern int register_android_database_CursorWindow(JNIEnv* env);
extern int register_android_database_SQLiteConnection(JNIEnv* env);
extern int register_android_database_SQLiteGlobal(JNIEnv* env);
extern int register_android_database_SQLiteDebug(JNIEnv* env);
extern int register_android_debug_JNITest(JNIEnv* env);
extern int register_android_nio_utils(JNIEnv* env);
extern int register_android_text_format_Time(JNIEnv* env);
extern int register_android_os_Debug(JNIEnv* env);
extern int register_android_os_MessageQueue(JNIEnv* env);
extern int register_android_os_Parcel(JNIEnv* env);
extern int register_android_os_SELinux(JNIEnv* env);
extern int register_android_os_SystemProperties(JNIEnv *env);
extern int register_android_os_SystemClock(JNIEnv* env);
extern int register_android_os_Trace(JNIEnv* env);
extern int register_android_os_FileObserver(JNIEnv *env);
extern int register_android_os_FileUtils(JNIEnv *env);
extern int register_android_os_UEventObserver(JNIEnv* env);
extern int register_android_os_MemoryFile(JNIEnv* env);
extern int register_android_net_LocalSocketImpl(JNIEnv* env);
extern int register_android_net_NetworkUtils(JNIEnv* env);
extern int register_android_net_TrafficStats(JNIEnv* env);
extern int register_android_net_wifi_WifiNative(JNIEnv* env);
extern int register_android_text_AndroidCharacter(JNIEnv *env);
extern int register_android_text_AndroidBidi(JNIEnv *env);
extern int register_android_opengl_classes(JNIEnv *env);
extern int register_android_server_NetworkManagementSocketTagger(JNIEnv* env);
extern int register_android_server_Watchdog(JNIEnv* env);
extern int register_android_ddm_DdmHandleNativeHeap(JNIEnv *env);
extern int register_com_android_internal_os_ZygoteInit(JNIEnv* env);
extern int register_android_backup_BackupDataInput(JNIEnv *env);
extern int register_android_backup_BackupDataOutput(JNIEnv *env);
extern int register_android_backup_FileBackupHelperBase(JNIEnv *env);
extern int register_android_backup_BackupHelperDispatcher(JNIEnv *env);
extern int register_android_app_backup_FullBackup(JNIEnv *env);
extern int register_android_app_ActivityThread(JNIEnv *env);
extern int register_android_app_NativeActivity(JNIEnv *env);
extern int register_android_media_RemoteDisplay(JNIEnv *env);
extern int register_android_view_InputChannel(JNIEnv* env);
extern int register_android_view_InputDevice(JNIEnv* env);
extern int register_android_view_InputEventReceiver(JNIEnv* env);
extern int register_android_view_InputEventSender(JNIEnv* env);
extern int register_android_view_InputQueue(JNIEnv* env);
extern int register_android_view_KeyCharacterMap(JNIEnv *env);
extern int register_android_view_KeyEvent(JNIEnv* env);
extern int register_android_view_MotionEvent(JNIEnv* env);
extern int register_android_view_PointerIcon(JNIEnv* env);
extern int register_android_view_VelocityTracker(JNIEnv* env);
extern int register_android_content_res_ObbScanner(JNIEnv* env);
extern int register_android_content_res_Configuration(JNIEnv* env);
extern int register_android_animation_PropertyValuesHolder(JNIEnv *env);
extern int register_com_android_internal_content_NativeLibraryHelper(JNIEnv *env);
extern int register_com_android_internal_net_NetworkStatsFactory(JNIEnv *env);
extern int register_android_content_res_PackageRedirectionMap(JNIEnv* env);
extern int register_com_android_internal_app_ActivityTrigger(JNIEnv *env);

static AndroidRuntime* gCurRuntime = NULL;

static void doThrow(JNIEnv* env, const char* exc, const char* msg = NULL)
{
    if (jniThrowException(env, exc, msg) != 0)
        assert(false);
}

/*
 * Code written in the Java Programming Language calls here from main().
 */
static void com_android_internal_os_RuntimeInit_nativeFinishInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onStarted();
}

static void com_android_internal_os_RuntimeInit_nativeZygoteInit(JNIEnv* env, jobject clazz)
{
    gCurRuntime->onZygoteInit();
}

static void com_android_internal_os_RuntimeInit_nativeSetExitWithoutCleanup(JNIEnv* env,
        jobject clazz, jboolean exitWithoutCleanup)
{
    gCurRuntime->setExitWithoutCleanup(exitWithoutCleanup);
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    { "nativeFinishInit", "()V",
        (void*) com_android_internal_os_RuntimeInit_nativeFinishInit },
    { "nativeZygoteInit", "()V",
        (void*) com_android_internal_os_RuntimeInit_nativeZygoteInit },
    { "nativeSetExitWithoutCleanup", "(Z)V",
        (void*) com_android_internal_os_RuntimeInit_nativeSetExitWithoutCleanup },
};

int register_com_android_internal_os_RuntimeInit(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/internal/os/RuntimeInit",
        gMethods, NELEM(gMethods));
}

// ----------------------------------------------------------------------

/*static*/ JavaVM* AndroidRuntime::mJavaVM = NULL;


AndroidRuntime::AndroidRuntime() :
        mExitWithoutCleanup(false)
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

status_t AndroidRuntime::callMain(const char* className,
    jclass clazz, int argc, const char* const argv[])
{
    JNIEnv* env;
    jmethodID methodId;

    ALOGD("Calling main entry %s", className);

    env = getJNIEnv();
    if (clazz == NULL || env == NULL) {
        return UNKNOWN_ERROR;
    }

    methodId = env->GetStaticMethodID(clazz, "main", "([Ljava/lang/String;)V");
    if (methodId == NULL) {
        ALOGE("ERROR: could not find method %s.main(String[])\n", className);
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
 * The VM calls this through the "exit" hook.
 */
static void runtime_exit(int code)
{
    gCurRuntime->exit(code);
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
 * The VM calls this when mutex contention debugging is enabled to
 * determine whether or not the blocked thread was a "sensitive thread"
 * for user responsiveness/smoothess.
 *
 * Our policy for this is whether or not we're tracing any StrictMode
 * events on this thread (which we might've inherited via Binder calls
 * into us)
 */
static bool runtime_isSensitiveThread() {
    IPCThreadState* state = IPCThreadState::selfOrNull();
    return state && state->getStrictModePolicy() != 0;
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
    //ALOGD("language=%s region=%s\n", language, region);
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
    char heapstartsizeOptsBuf[sizeof("-Xms")-1 + PROPERTY_VALUE_MAX];
    char heapsizeOptsBuf[sizeof("-Xmx")-1 + PROPERTY_VALUE_MAX];
    char heapgrowthlimitOptsBuf[sizeof("-XX:HeapGrowthLimit=")-1 + PROPERTY_VALUE_MAX];
    char heapminfreeOptsBuf[sizeof("-XX:HeapMinFree=")-1 + PROPERTY_VALUE_MAX];
    char heapmaxfreeOptsBuf[sizeof("-XX:HeapMaxFree=")-1 + PROPERTY_VALUE_MAX];
    char heaptargetutilizationOptsBuf[sizeof("-XX:HeapTargetUtilization=")-1 + PROPERTY_VALUE_MAX];
    char jitcodecachesizeOptsBuf[sizeof("-Xjitcodecachesize:")-1 + PROPERTY_VALUE_MAX];
    char extraOptsBuf[PROPERTY_VALUE_MAX];
    char* stackTraceFile = NULL;
    bool checkJni = false;
    bool checkDexSum = false;
    bool logStdio = false;
    enum {
      kEMDefault,
      kEMIntPortable,
      kEMIntFast,
      kEMJitCompiler,
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

    property_get("dalvik.vm.execution-mode", propBuf, "");
    if (strcmp(propBuf, "int:portable") == 0) {
        executionMode = kEMIntPortable;
    } else if (strcmp(propBuf, "int:fast") == 0) {
        executionMode = kEMIntFast;
    } else if (strcmp(propBuf, "int:jit") == 0) {
        executionMode = kEMJitCompiler;
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

    /* register the framework-specific "is sensitive thread" hook */
    opt.extraInfo = (void*) runtime_isSensitiveThread;
    opt.optionString = "sensitiveThread";
    mOptions.add(opt);

    opt.extraInfo = NULL;

    /* enable verbose; standard options are { jni, gc, class } */
    //options[curOpt++].optionString = "-verbose:jni";
    opt.optionString = "-verbose:gc";
    mOptions.add(opt);
    //options[curOpt++].optionString = "-verbose:class";

    /*
     * The default starting and maximum size of the heap.  Larger
     * values should be specified in a product property override.
     */
    strcpy(heapstartsizeOptsBuf, "-Xms");
    property_get("dalvik.vm.heapstartsize", heapstartsizeOptsBuf+4, "4m");
    opt.optionString = heapstartsizeOptsBuf;
    mOptions.add(opt);
    strcpy(heapsizeOptsBuf, "-Xmx");
    property_get("dalvik.vm.heapsize", heapsizeOptsBuf+4, "16m");
    opt.optionString = heapsizeOptsBuf;
    mOptions.add(opt);

    // Increase the main thread's interpreter stack size for bug 6315322.
    opt.optionString = "-XX:mainThreadStackSize=24K";
    mOptions.add(opt);

    // Set the max jit code cache size.  Note: size of 0 will disable the JIT.
    strcpy(jitcodecachesizeOptsBuf, "-Xjitcodecachesize:");
    property_get("dalvik.vm.jit.codecachesize", jitcodecachesizeOptsBuf+19,  NULL);
    if (jitcodecachesizeOptsBuf[19] != '\0') {
      opt.optionString = jitcodecachesizeOptsBuf;
      mOptions.add(opt);
    }

    strcpy(heapgrowthlimitOptsBuf, "-XX:HeapGrowthLimit=");
    property_get("dalvik.vm.heapgrowthlimit", heapgrowthlimitOptsBuf+20, "");
    if (heapgrowthlimitOptsBuf[20] != '\0') {
        opt.optionString = heapgrowthlimitOptsBuf;
        mOptions.add(opt);
    }

    strcpy(heapminfreeOptsBuf, "-XX:HeapMinFree=");
    property_get("dalvik.vm.heapminfree", heapminfreeOptsBuf+16, "");
    if (heapminfreeOptsBuf[16] != '\0') {
        opt.optionString = heapminfreeOptsBuf;
        mOptions.add(opt);
    }

    strcpy(heapmaxfreeOptsBuf, "-XX:HeapMaxFree=");
    property_get("dalvik.vm.heapmaxfree", heapmaxfreeOptsBuf+16, "");
    if (heapmaxfreeOptsBuf[16] != '\0') {
        opt.optionString = heapmaxfreeOptsBuf;
        mOptions.add(opt);
    }

    strcpy(heaptargetutilizationOptsBuf, "-XX:HeapTargetUtilization=");
    property_get("dalvik.vm.heaptargetutilization", heaptargetutilizationOptsBuf+26, "");
    if (heaptargetutilizationOptsBuf[26] != '\0') {
        opt.optionString = heaptargetutilizationOptsBuf;
        mOptions.add(opt);
    }

    property_get("ro.config.low_ram", propBuf, "");
    if (strcmp(propBuf, "true") == 0) {
      opt.optionString = "-XX:LowMemoryMode";
      mOptions.add(opt);
    }

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
            case 'f':   val = "-Xdexopt:full";      break;
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
    /* use android ADB transport */
    opt.optionString =
        "-agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y";
    mOptions.add(opt);

    ALOGD("CheckJNI is %s\n", checkJni ? "ON" : "OFF");
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

    if (executionMode == kEMIntPortable) {
        opt.optionString = "-Xint:portable";
        mOptions.add(opt);
    } else if (executionMode == kEMIntFast) {
        opt.optionString = "-Xint:fast";
        mOptions.add(opt);
    } else if (executionMode == kEMJitCompiler) {
        opt.optionString = "-Xint:jit";
        mOptions.add(opt);
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
        ALOGI("Assertions enabled: '%s'\n", enableAssertBuf);
        opt.optionString = enableAssertBuf;
        mOptions.add(opt);
    } else {
        ALOGV("Assertions disabled\n");
    }

    if (jniOptsBuf[10] != '\0') {
        ALOGI("JNI options: '%s'\n", jniOptsBuf);
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
        ALOGE("JNI_CreateJavaVM failed\n");
        goto bail;
    }

    result = 0;

bail:
    free(stackTraceFile);
    return result;
}

char* AndroidRuntime::toSlashClassName(const char* className)
{
    char* result = strdup(className);
    for (char* cp = result; *cp != '\0'; cp++) {
        if (*cp == '.') {
            *cp = '/';
        }
    }
    return result;
}

/*
 * Start the Android runtime.  This involves starting the virtual machine
 * and calling the "static void main(String[] args)" method in the class
 * named by "className".
 *
 * Passes the main function two arguments, the class name and the specified
 * options string.
 */
void AndroidRuntime::start(const char* className, const char* options)
{
    ALOGD("\n>>>>>> AndroidRuntime START %s <<<<<<\n",
            className != NULL ? className : "(unknown)");

    /*
     * 'startSystemServer == true' means runtime is obsolete and not run from
     * init.rc anymore, so we print out the boot start event here.
     */
    if (strcmp(options, "start-system-server") == 0) {
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
            return;
        }
        setenv("ANDROID_ROOT", rootDir, 1);
    }

    //const char* kernelHack = getenv("LD_ASSUME_KERNEL");
    //ALOGD("Found LD_ASSUME_KERNEL='%s'\n", kernelHack);

    /* start the virtual machine */
    JniInvocation jni_invocation;
    jni_invocation.Init(NULL);
    JNIEnv* env;
    if (startVm(&mJavaVM, &env) != 0) {
        return;
    }
    onVmCreated(env);

    /*
     * Register android functions.
     */
    if (startReg(env) < 0) {
        ALOGE("Unable to register all android natives\n");
        return;
    }

    /*
     * We want to call main() with a String array with arguments in it.
     * At present we have two arguments, the class name and an option string.
     * Create an array to hold them.
     */
    jclass stringClass;
    jobjectArray strArray;
    jstring classNameStr;
    jstring optionsStr;

    stringClass = env->FindClass("java/lang/String");
    assert(stringClass != NULL);
    strArray = env->NewObjectArray(2, stringClass, NULL);
    assert(strArray != NULL);
    classNameStr = env->NewStringUTF(className);
    assert(classNameStr != NULL);
    env->SetObjectArrayElement(strArray, 0, classNameStr);
    optionsStr = env->NewStringUTF(options);
    env->SetObjectArrayElement(strArray, 1, optionsStr);

    /*
     * Start VM.  This thread becomes the main thread of the VM, and will
     * not return until the VM exits.
     */
    char* slashClassName = toSlashClassName(className);
    jclass startClass = env->FindClass(slashClassName);
    if (startClass == NULL) {
        ALOGE("JavaVM unable to locate class '%s'\n", slashClassName);
        /* keep going */
    } else {
        jmethodID startMeth = env->GetStaticMethodID(startClass, "main",
            "([Ljava/lang/String;)V");
        if (startMeth == NULL) {
            ALOGE("JavaVM unable to find main() in '%s'\n", className);
            /* keep going */
        } else {
            env->CallStaticVoidMethod(startClass, startMeth, strArray);

#if 0
            if (env->ExceptionCheck())
                threadExitUncaughtException(env);
#endif
        }
    }
    free(slashClassName);

    ALOGD("Shutting down VM\n");
    if (mJavaVM->DetachCurrentThread() != JNI_OK)
        ALOGW("Warning: unable to detach main thread\n");
    if (mJavaVM->DestroyJavaVM() != 0)
        ALOGW("Warning: VM did not shut down cleanly\n");
}

void AndroidRuntime::exit(int code)
{
    if (mExitWithoutCleanup) {
        ALOGI("VM exiting with result code %d, cleanup skipped.", code);
        ::_exit(code);
    } else {
        ALOGI("VM exiting with result code %d.", code);
        onExit(code);
        ::exit(code);
    }
}

void AndroidRuntime::onVmCreated(JNIEnv* env)
{
    // If AndroidRuntime had anything to do here, we'd have done it in 'start'.
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
        ALOGI("NOTE: attach of thread '%s' failed\n", threadName);

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
        ALOGE("ERROR: thread detach failed\n");
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
            ALOGD("----------!!! %s failed to load\n", array[i].mName);
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
    REG_JNI(register_android_content_AssetManager),
    REG_JNI(register_android_content_StringBlock),
    REG_JNI(register_android_content_XmlBlock),
    REG_JNI(register_android_emoji_EmojiFactory),
    REG_JNI(register_android_text_AndroidCharacter),
    REG_JNI(register_android_text_AndroidBidi),
    REG_JNI(register_android_view_InputDevice),
    REG_JNI(register_android_view_KeyCharacterMap),
    REG_JNI(register_android_os_Process),
    REG_JNI(register_android_os_SystemProperties),
    REG_JNI(register_android_os_Binder),
    REG_JNI(register_android_os_Parcel),
    REG_JNI(register_android_view_DisplayEventReceiver),
    REG_JNI(register_android_nio_utils),
    REG_JNI(register_android_graphics_Graphics),
    REG_JNI(register_android_view_GraphicBuffer),
    REG_JNI(register_android_view_GLES20DisplayList),
    REG_JNI(register_android_view_GLES20Canvas),
    REG_JNI(register_android_view_HardwareRenderer),
    REG_JNI(register_android_view_Surface),
    REG_JNI(register_android_view_SurfaceControl),
    REG_JNI(register_android_view_SurfaceSession),
    REG_JNI(register_android_view_TextureView),
    REG_JNI(register_com_google_android_gles_jni_EGLImpl),
    REG_JNI(register_com_google_android_gles_jni_GLImpl),
    REG_JNI(register_android_opengl_jni_EGL14),
    REG_JNI(register_android_opengl_jni_EGLExt),
    REG_JNI(register_android_opengl_jni_GLES10),
    REG_JNI(register_android_opengl_jni_GLES10Ext),
    REG_JNI(register_android_opengl_jni_GLES11),
    REG_JNI(register_android_opengl_jni_GLES11Ext),
    REG_JNI(register_android_opengl_jni_GLES20),
    REG_JNI(register_android_opengl_jni_GLES30),

    REG_JNI(register_android_graphics_Bitmap),
    REG_JNI(register_android_graphics_BitmapFactory),
    REG_JNI(register_android_graphics_BitmapRegionDecoder),
    REG_JNI(register_android_graphics_Camera),
    REG_JNI(register_android_graphics_CreateJavaOutputStreamAdaptor),
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
    REG_JNI(register_android_graphics_SurfaceTexture),
    REG_JNI(register_android_graphics_Typeface),
    REG_JNI(register_android_graphics_Xfermode),
    REG_JNI(register_android_graphics_YuvImage),
    REG_JNI(register_android_graphics_pdf_PdfDocument),

    REG_JNI(register_android_database_CursorWindow),
    REG_JNI(register_android_database_SQLiteConnection),
    REG_JNI(register_android_database_SQLiteGlobal),
    REG_JNI(register_android_database_SQLiteDebug),
    REG_JNI(register_android_os_Debug),
    REG_JNI(register_android_os_FileObserver),
    REG_JNI(register_android_os_FileUtils),
    REG_JNI(register_android_os_MessageQueue),
    REG_JNI(register_android_os_SELinux),
    REG_JNI(register_android_os_Trace),
    REG_JNI(register_android_os_UEventObserver),
    REG_JNI(register_android_net_LocalSocketImpl),
    REG_JNI(register_android_net_NetworkUtils),
    REG_JNI(register_android_net_TrafficStats),
    REG_JNI(register_android_net_wifi_WifiNative),
    REG_JNI(register_android_os_MemoryFile),
    REG_JNI(register_com_android_internal_os_ZygoteInit),
    REG_JNI(register_android_hardware_Camera),
    REG_JNI(register_android_hardware_camera2_CameraMetadata),
    REG_JNI(register_android_hardware_SensorManager),
    REG_JNI(register_android_hardware_SerialPort),
    REG_JNI(register_android_hardware_UsbDevice),
    REG_JNI(register_android_hardware_UsbDeviceConnection),
    REG_JNI(register_android_hardware_UsbRequest),
    REG_JNI(register_android_media_AudioRecord),
    REG_JNI(register_android_media_AudioSystem),
    REG_JNI(register_android_media_AudioTrack),
    REG_JNI(register_android_media_JetPlayer),
    REG_JNI(register_android_media_RemoteDisplay),
    REG_JNI(register_android_media_ToneGenerator),

    REG_JNI(register_android_opengl_classes),
    REG_JNI(register_android_server_NetworkManagementSocketTagger),
    REG_JNI(register_android_server_Watchdog),
    REG_JNI(register_android_ddm_DdmHandleNativeHeap),
    REG_JNI(register_android_backup_BackupDataInput),
    REG_JNI(register_android_backup_BackupDataOutput),
    REG_JNI(register_android_backup_FileBackupHelperBase),
    REG_JNI(register_android_backup_BackupHelperDispatcher),
    REG_JNI(register_android_app_backup_FullBackup),
    REG_JNI(register_android_app_ActivityThread),
    REG_JNI(register_android_app_NativeActivity),
    REG_JNI(register_android_view_InputChannel),
    REG_JNI(register_android_view_InputEventReceiver),
    REG_JNI(register_android_view_InputEventSender),
    REG_JNI(register_android_view_InputQueue),
    REG_JNI(register_android_view_KeyEvent),
    REG_JNI(register_android_view_MotionEvent),
    REG_JNI(register_android_view_PointerIcon),
    REG_JNI(register_android_view_VelocityTracker),

    REG_JNI(register_android_content_res_ObbScanner),
    REG_JNI(register_android_content_res_Configuration),

    REG_JNI(register_android_animation_PropertyValuesHolder),
    REG_JNI(register_com_android_internal_content_NativeLibraryHelper),
    REG_JNI(register_com_android_internal_net_NetworkStatsFactory),
    REG_JNI(register_android_content_res_PackageRedirectionMap),
#ifdef QCOM_HARDWARE
    REG_JNI(register_com_android_internal_app_ActivityTrigger),
#endif
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

    ALOGV("--- registering native functions ---\n");

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
