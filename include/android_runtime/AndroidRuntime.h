/*
 * Copyright (C) 2005 The Android Open Source Project
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

//

#ifndef _RUNTIME_ANDROID_RUNTIME_H
#define _RUNTIME_ANDROID_RUNTIME_H

#include <utils/Errors.h>
#include <binder/IBinder.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <utils/Vector.h>
#include <utils/threads.h>
#include <pthread.h>
#include <nativehelper/jni.h>


namespace android {

class AndroidRuntime
{
public:
    AndroidRuntime();
    virtual ~AndroidRuntime();

    enum StartMode {
        Zygote,
        SystemServer,
        Application,
        Tool,
    };

    /**
     * Register a set of methods in the specified class.
     */
    static int registerNativeMethods(JNIEnv* env,
        const char* className, const JNINativeMethod* gMethods, int numMethods);

    /**
     * Call a class's static main method with the given arguments,
     */
    status_t callMain(const char* className, jclass clazz, int argc,
        const char* const argv[]);

    /**
     * Find a class, with the input either of the form
     * "package/class" or "package.class".
     */
    static jclass findClass(JNIEnv* env, const char* className);

    int addVmArguments(int argc, const char* const argv[]);

    void start(const char *classname, const char* options, bool zygote);

    void exit(int code);

    void setExitWithoutCleanup(bool exitWithoutCleanup) {
        mExitWithoutCleanup = exitWithoutCleanup;
    }

    static AndroidRuntime* getRuntime();

    /**
     * This gets called after the VM has been created, but before we
     * run any code. Override it to make any FindClass calls that need
     * to use CLASSPATH.
     */
    virtual void onVmCreated(JNIEnv* env);

    /**
     * This gets called after the JavaVM has initialized.  Override it
     * with the system's native entry point.
     */
    virtual void onStarted() = 0;

    /**
     * This gets called after the JavaVM has initialized after a Zygote
     * fork. Override it to initialize threads, etc. Upon return, the
     * correct static main will be invoked.
     */
    virtual void onZygoteInit() { }

    /**
     * Called when the Java application exits to perform additional cleanup actions
     * before the process is terminated.
     */
    virtual void onExit(int code) { }

    /** create a new thread that is visible from Java */
    static android_thread_id_t createJavaThread(const char* name, void (*start)(void *),
        void* arg);

    /** return a pointer to the VM running in this process */
    static JavaVM* getJavaVM() { return mJavaVM; }

    /** return a pointer to the JNIEnv pointer for this thread */
    static JNIEnv* getJNIEnv();

    /** return a new string corresponding to 'className' with all '.'s replaced by '/'s. */
    static char* toSlashClassName(const char* className);

private:
    static int startReg(JNIEnv* env);
    void parseExtraOpts(char* extraOptsBuf);
    int startVm(JavaVM** pJavaVM, JNIEnv** pEnv, bool zygote);

    Vector<JavaVMOption> mOptions;
    bool mExitWithoutCleanup;

    /* JNI JavaVM pointer */
    static JavaVM* mJavaVM;

    /*
     * Thread creation helpers.
     */
    static int javaCreateThreadEtc(
                                android_thread_func_t entryFunction,
                                void* userData,
                                const char* threadName,
                                int32_t threadPriority,
                                size_t threadStackSize,
                                android_thread_id_t* threadId);
    static int javaThreadShell(void* args);
};

}

#endif
