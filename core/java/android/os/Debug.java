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

package android.os;

import com.android.internal.util.TypedProperties;

import android.util.Config;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.harmony.dalvik.ddmc.Chunk;
import org.apache.harmony.dalvik.ddmc.ChunkHandler;
import org.apache.harmony.dalvik.ddmc.DdmServer;

import dalvik.bytecode.Opcodes;
import dalvik.system.VMDebug;


/**
 * Provides various debugging functions for Android applications, including
 * tracing and allocation counts.
 * <p><strong>Logging Trace Files</strong></p>
 * <p>Debug can create log files that give details about an application, such as
 * a call stack and start/stop times for any running methods. See <a
href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
 * information about reading trace files. To start logging trace files, call one
 * of the startMethodTracing() methods. To stop tracing, call
 * {@link #stopMethodTracing()}.
 */
public final class Debug
{
    private static final String TAG = "Debug";

    /**
     * Flags for startMethodTracing().  These can be ORed together.
     *
     * TRACE_COUNT_ALLOCS adds the results from startAllocCounting to the
     * trace key file.
     */
    public static final int TRACE_COUNT_ALLOCS  = VMDebug.TRACE_COUNT_ALLOCS;

    /**
     * Flags for printLoadedClasses().  Default behavior is to only show
     * the class name.
     */
    public static final int SHOW_FULL_DETAIL    = 1;
    public static final int SHOW_CLASSLOADER    = (1 << 1);
    public static final int SHOW_INITIALIZED    = (1 << 2);

    // set/cleared by waitForDebugger()
    private static volatile boolean mWaiting = false;

    private Debug() {}

    /*
     * How long to wait for the debugger to finish sending requests.  I've
     * seen this hit 800msec on the device while waiting for a response
     * to travel over USB and get processed, so we take that and add
     * half a second.
     */
    private static final int MIN_DEBUGGER_IDLE = 1300;      // msec

    /* how long to sleep when polling for activity */
    private static final int SPIN_DELAY = 200;              // msec

    /**
     * Default trace file path and file
     */
    private static final String DEFAULT_TRACE_PATH_PREFIX = "/sdcard/";
    private static final String DEFAULT_TRACE_BODY = "dmtrace";
    private static final String DEFAULT_TRACE_EXTENSION = ".trace";
    private static final String DEFAULT_TRACE_FILE_PATH =
        DEFAULT_TRACE_PATH_PREFIX + DEFAULT_TRACE_BODY
        + DEFAULT_TRACE_EXTENSION;


    /**
     * This class is used to retrieved various statistics about the memory mappings for this
     * process. The returns info broken down by dalvik, native, and other. All results are in kB.
     */
    public static class MemoryInfo implements Parcelable {
        /** The proportional set size for dalvik. */
        public int dalvikPss;
        /** The private dirty pages used by dalvik. */
        public int dalvikPrivateDirty;
        /** The shared dirty pages used by dalvik. */
        public int dalvikSharedDirty;

        /** The proportional set size for the native heap. */
        public int nativePss;
        /** The private dirty pages used by the native heap. */
        public int nativePrivateDirty;
        /** The shared dirty pages used by the native heap. */
        public int nativeSharedDirty;

        /** The proportional set size for everything else. */
        public int otherPss;
        /** The private dirty pages used by everything else. */
        public int otherPrivateDirty;
        /** The shared dirty pages used by everything else. */
        public int otherSharedDirty;

        public MemoryInfo() {
        }

        /**
         * Return total PSS memory usage in kB.
         */
        public int getTotalPss() {
            return dalvikPss + nativePss + otherPss;
        }

        /**
         * Return total private dirty memory usage in kB.
         */
        public int getTotalPrivateDirty() {
            return dalvikPrivateDirty + nativePrivateDirty + otherPrivateDirty;
        }

        /**
         * Return total shared dirty memory usage in kB.
         */
        public int getTotalSharedDirty() {
            return dalvikSharedDirty + nativeSharedDirty + otherSharedDirty;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(dalvikPss);
            dest.writeInt(dalvikPrivateDirty);
            dest.writeInt(dalvikSharedDirty);
            dest.writeInt(nativePss);
            dest.writeInt(nativePrivateDirty);
            dest.writeInt(nativeSharedDirty);
            dest.writeInt(otherPss);
            dest.writeInt(otherPrivateDirty);
            dest.writeInt(otherSharedDirty);
        }

        public void readFromParcel(Parcel source) {
            dalvikPss = source.readInt();
            dalvikPrivateDirty = source.readInt();
            dalvikSharedDirty = source.readInt();
            nativePss = source.readInt();
            nativePrivateDirty = source.readInt();
            nativeSharedDirty = source.readInt();
            otherPss = source.readInt();
            otherPrivateDirty = source.readInt();
            otherSharedDirty = source.readInt();
        }

        public static final Creator<MemoryInfo> CREATOR = new Creator<MemoryInfo>() {
            public MemoryInfo createFromParcel(Parcel source) {
                return new MemoryInfo(source);
            }
            public MemoryInfo[] newArray(int size) {
                return new MemoryInfo[size];
            }
        };

        private MemoryInfo(Parcel source) {
            readFromParcel(source);
        }
    }


    /**
     * Wait until a debugger attaches.  As soon as the debugger attaches,
     * this returns, so you will need to place a breakpoint after the
     * waitForDebugger() call if you want to start tracing immediately.
     */
    public static void waitForDebugger() {
        if (!VMDebug.isDebuggingEnabled()) {
            //System.out.println("debugging not enabled, not waiting");
            return;
        }
        if (isDebuggerConnected())
            return;

        // if DDMS is listening, inform them of our plight
        System.out.println("Sending WAIT chunk");
        byte[] data = new byte[] { 0 };     // 0 == "waiting for debugger"
        Chunk waitChunk = new Chunk(ChunkHandler.type("WAIT"), data, 0, 1);
        DdmServer.sendChunk(waitChunk);

        mWaiting = true;
        while (!isDebuggerConnected()) {
            try { Thread.sleep(SPIN_DELAY); }
            catch (InterruptedException ie) {}
        }
        mWaiting = false;

        System.out.println("Debugger has connected");

        /*
         * There is no "ready to go" signal from the debugger, and we're
         * not allowed to suspend ourselves -- the debugger expects us to
         * be running happily, and gets confused if we aren't.  We need to
         * allow the debugger a chance to set breakpoints before we start
         * running again.
         *
         * Sit and spin until the debugger has been idle for a short while.
         */
        while (true) {
            long delta = VMDebug.lastDebuggerActivity();
            if (delta < 0) {
                System.out.println("debugger detached?");
                break;
            }

            if (delta < MIN_DEBUGGER_IDLE) {
                System.out.println("waiting for debugger to settle...");
                try { Thread.sleep(SPIN_DELAY); }
                catch (InterruptedException ie) {}
            } else {
                System.out.println("debugger has settled (" + delta + ")");
                break;
            }
        }
    }

    /**
     * Returns "true" if one or more threads is waiting for a debugger
     * to attach.
     */
    public static boolean waitingForDebugger() {
        return mWaiting;
    }

    /**
     * Determine if a debugger is currently attached.
     */
    public static boolean isDebuggerConnected() {
        return VMDebug.isDebuggerConnected();
    }

    /**
     * Returns an array of strings that identify VM features.  This is
     * used by DDMS to determine what sorts of operations the VM can
     * perform.
     *
     * @hide
     */
    public static String[] getVmFeatureList() {
        return VMDebug.getVmFeatureList();
    }

    /**
     * Change the JDWP port.
     *
     * @deprecated no longer needed or useful
     */
    @Deprecated
    public static void changeDebugPort(int port) {}

    /**
     * This is the pathname to the sysfs file that enables and disables
     * tracing on the qemu emulator.
     */
    private static final String SYSFS_QEMU_TRACE_STATE = "/sys/qemu_trace/state";

    /**
     * Enable qemu tracing. For this to work requires running everything inside
     * the qemu emulator; otherwise, this method will have no effect. The trace
     * file is specified on the command line when the emulator is started. For
     * example, the following command line <br />
     * <code>emulator -trace foo</code><br />
     * will start running the emulator and create a trace file named "foo". This
     * method simply enables writing the trace records to the trace file.
     *
     * <p>
     * The main differences between this and {@link #startMethodTracing()} are
     * that tracing in the qemu emulator traces every cpu instruction of every
     * process, including kernel code, so we have more complete information,
     * including all context switches. We can also get more detailed information
     * such as cache misses. The sequence of calls is determined by
     * post-processing the instruction trace. The qemu tracing is also done
     * without modifying the application or perturbing the timing of calls
     * because no instrumentation is added to the application being traced.
     * </p>
     *
     * <p>
     * One limitation of using this method compared to using
     * {@link #startMethodTracing()} on the real device is that the emulator
     * does not model all of the real hardware effects such as memory and
     * bus contention.  The emulator also has a simple cache model and cannot
     * capture all the complexities of a real cache.
     * </p>
     */
    public static void startNativeTracing() {
        // Open the sysfs file for writing and write "1" to it.
        PrintWriter outStream = null;
        try {
            FileOutputStream fos = new FileOutputStream(SYSFS_QEMU_TRACE_STATE);
            outStream = new PrintWriter(new OutputStreamWriter(fos));
            outStream.println("1");
        } catch (Exception e) {
        } finally {
            if (outStream != null)
                outStream.close();
        }

        VMDebug.startEmulatorTracing();
    }

    /**
     * Stop qemu tracing.  See {@link #startNativeTracing()} to start tracing.
     *
     * <p>Tracing can be started and stopped as many times as desired.  When
     * the qemu emulator itself is stopped then the buffered trace records
     * are flushed and written to the trace file.  In fact, it is not necessary
     * to call this method at all; simply killing qemu is sufficient.  But
     * starting and stopping a trace is useful for examining a specific
     * region of code.</p>
     */
    public static void stopNativeTracing() {
        VMDebug.stopEmulatorTracing();

        // Open the sysfs file for writing and write "0" to it.
        PrintWriter outStream = null;
        try {
            FileOutputStream fos = new FileOutputStream(SYSFS_QEMU_TRACE_STATE);
            outStream = new PrintWriter(new OutputStreamWriter(fos));
            outStream.println("0");
        } catch (Exception e) {
            // We could print an error message here but we probably want
            // to quietly ignore errors if we are not running in the emulator.
        } finally {
            if (outStream != null)
                outStream.close();
        }
    }

    /**
     * Enable "emulator traces", in which information about the current
     * method is made available to the "emulator -trace" feature.  There
     * is no corresponding "disable" call -- this is intended for use by
     * the framework when tracing should be turned on and left that way, so
     * that traces captured with F9/F10 will include the necessary data.
     *
     * This puts the VM into "profile" mode, which has performance
     * consequences.
     *
     * To temporarily enable tracing, use {@link #startNativeTracing()}.
     */
    public static void enableEmulatorTraceOutput() {
        VMDebug.startEmulatorTracing();
    }

    /**
     * Start method tracing with default log name and buffer size. See <a
href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
     * information about reading these files. Call stopMethodTracing() to stop
     * tracing.
     */
    public static void startMethodTracing() {
        VMDebug.startMethodTracing(DEFAULT_TRACE_FILE_PATH, 0, 0);
    }

    /**
     * Start method tracing, specifying the trace log file name.  The trace
     * file will be put under "/sdcard" unless an absolute path is given.
     * See <a
       href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
     * information about reading trace files.
     *
     * @param traceName Name for the trace log file to create.
     * If no name argument is given, this value defaults to "/sdcard/dmtrace.trace".
     * If the files already exist, they will be truncated.
     * If the trace file given does not end in ".trace", it will be appended for you.
     */
    public static void startMethodTracing(String traceName) {
        startMethodTracing(traceName, 0, 0);
    }

    /**
     * Start method tracing, specifying the trace log file name and the
     * buffer size. The trace files will be put under "/sdcard" unless an
     * absolute path is given. See <a
       href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
     * information about reading trace files.
     * @param traceName    Name for the trace log file to create.
     * If no name argument is given, this value defaults to "/sdcard/dmtrace.trace".
     * If the files already exist, they will be truncated.
     * If the trace file given does not end in ".trace", it will be appended for you.
     *
     * @param bufferSize    The maximum amount of trace data we gather. If not given, it defaults to 8MB.
     */
    public static void startMethodTracing(String traceName, int bufferSize) {
        startMethodTracing(traceName, bufferSize, 0);
    }

    /**
     * Start method tracing, specifying the trace log file name and the
     * buffer size. The trace files will be put under "/sdcard" unless an
     * absolute path is given. See <a
       href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
     * information about reading trace files.
     *
     * <p>
     * When method tracing is enabled, the VM will run more slowly than
     * usual, so the timings from the trace files should only be considered
     * in relative terms (e.g. was run #1 faster than run #2).  The times
     * for native methods will not change, so don't try to use this to
     * compare the performance of interpreted and native implementations of the
     * same method.  As an alternative, consider using "native" tracing
     * in the emulator via {@link #startNativeTracing()}.
     * </p>
     *
     * @param traceName    Name for the trace log file to create.
     * If no name argument is given, this value defaults to "/sdcard/dmtrace.trace".
     * If the files already exist, they will be truncated.
     * If the trace file given does not end in ".trace", it will be appended for you.
     * @param bufferSize    The maximum amount of trace data we gather. If not given, it defaults to 8MB.
     */
    public static void startMethodTracing(String traceName, int bufferSize,
        int flags) {

        String pathName = traceName;
        if (pathName.charAt(0) != '/')
            pathName = DEFAULT_TRACE_PATH_PREFIX + pathName;
        if (!pathName.endsWith(DEFAULT_TRACE_EXTENSION))
            pathName = pathName + DEFAULT_TRACE_EXTENSION;

        VMDebug.startMethodTracing(pathName, bufferSize, flags);
    }

    /**
     * Like startMethodTracing(String, int, int), but taking an already-opened
     * FileDescriptor in which the trace is written.  The file name is also
     * supplied simply for logging.  Makes a dup of the file descriptor.
     *
     * Not exposed in the SDK unless we are really comfortable with supporting
     * this and find it would be useful.
     * @hide
     */
    public static void startMethodTracing(String traceName, FileDescriptor fd,
        int bufferSize, int flags) {
        VMDebug.startMethodTracing(traceName, fd, bufferSize, flags);
    }

    /**
     * Starts method tracing without a backing file.  When stopMethodTracing
     * is called, the result is sent directly to DDMS.  (If DDMS is not
     * attached when tracing ends, the profiling data will be discarded.)
     *
     * @hide
     */
    public static void startMethodTracingDdms(int bufferSize, int flags) {
        VMDebug.startMethodTracingDdms(bufferSize, flags);
    }

    /**
     * Determine whether method tracing is currently active.
     * @hide
     */
    public static boolean isMethodTracingActive() {
        return VMDebug.isMethodTracingActive();
    }

    /**
     * Stop method tracing.
     */
    public static void stopMethodTracing() {
        VMDebug.stopMethodTracing();
    }

    /**
     * Get an indication of thread CPU usage.  The value returned
     * indicates the amount of time that the current thread has spent
     * executing code or waiting for certain types of I/O.
     *
     * The time is expressed in nanoseconds, and is only meaningful
     * when compared to the result from an earlier call.  Note that
     * nanosecond resolution does not imply nanosecond accuracy.
     *
     * On system which don't support this operation, the call returns -1.
     */
    public static long threadCpuTimeNanos() {
        return VMDebug.threadCpuTimeNanos();
    }

    /**
     * Count the number and aggregate size of memory allocations between
     * two points.
     *
     * The "start" function resets the counts and enables counting.  The
     * "stop" function disables the counting so that the analysis code
     * doesn't cause additional allocations.  The "get" function returns
     * the specified value.
     *
     * Counts are kept for the system as a whole and for each thread.
     * The per-thread counts for threads other than the current thread
     * are not cleared by the "reset" or "start" calls.
     */
    public static void startAllocCounting() {
        VMDebug.startAllocCounting();
    }
    public static void stopAllocCounting() {
        VMDebug.stopAllocCounting();
    }

    public static int getGlobalAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_OBJECTS);
    }
    public static int getGlobalAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_BYTES);
    }
    public static int getGlobalFreedCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_FREED_OBJECTS);
    }
    public static int getGlobalFreedSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_FREED_BYTES);
    }
    public static int getGlobalClassInitCount() {
        /* number of classes that have been successfully initialized */
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_COUNT);
    }
    public static int getGlobalClassInitTime() {
        /* cumulative elapsed time for class initialization, in usec */
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_TIME);
    }
    public static int getGlobalExternalAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_EXT_ALLOCATED_OBJECTS);
    }
    public static int getGlobalExternalAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_EXT_ALLOCATED_BYTES);
    }
    public static int getGlobalExternalFreedCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_EXT_FREED_OBJECTS);
    }
    public static int getGlobalExternalFreedSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_EXT_FREED_BYTES);
    }
    public static int getGlobalGcInvocationCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_GC_INVOCATIONS);
    }
    public static int getThreadAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_ALLOCATED_OBJECTS);
    }
    public static int getThreadAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_ALLOCATED_BYTES);
    }
    public static int getThreadExternalAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_EXT_ALLOCATED_OBJECTS);
    }
    public static int getThreadExternalAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_EXT_ALLOCATED_BYTES);
    }
    public static int getThreadGcInvocationCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_GC_INVOCATIONS);
    }

    public static void resetGlobalAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_OBJECTS);
    }
    public static void resetGlobalAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_BYTES);
    }
    public static void resetGlobalFreedCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_FREED_OBJECTS);
    }
    public static void resetGlobalFreedSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_FREED_BYTES);
    }
    public static void resetGlobalClassInitCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_COUNT);
    }
    public static void resetGlobalClassInitTime() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_TIME);
    }
    public static void resetGlobalExternalAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_EXT_ALLOCATED_OBJECTS);
    }
    public static void resetGlobalExternalAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_EXT_ALLOCATED_BYTES);
    }
    public static void resetGlobalExternalFreedCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_EXT_FREED_OBJECTS);
    }
    public static void resetGlobalExternalFreedSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_EXT_FREED_BYTES);
    }
    public static void resetGlobalGcInvocationCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_GC_INVOCATIONS);
    }
    public static void resetThreadAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_ALLOCATED_OBJECTS);
    }
    public static void resetThreadAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_ALLOCATED_BYTES);
    }
    public static void resetThreadExternalAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_EXT_ALLOCATED_OBJECTS);
    }
    public static void resetThreadExternalAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_EXT_ALLOCATED_BYTES);
    }
    public static void resetThreadGcInvocationCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_GC_INVOCATIONS);
    }
    public static void resetAllCounts() {
        VMDebug.resetAllocCount(VMDebug.KIND_ALL_COUNTS);
    }

    /**
     * Returns the size of the native heap.
     * @return The size of the native heap in bytes.
     */
    public static native long getNativeHeapSize();

    /**
     * Returns the amount of allocated memory in the native heap.
     * @return The allocated size in bytes.
     */
    public static native long getNativeHeapAllocatedSize();

    /**
     * Returns the amount of free memory in the native heap.
     * @return The freed size in bytes.
     */
    public static native long getNativeHeapFreeSize();

    /**
     * Retrieves information about this processes memory usages. This information is broken down by
     * how much is in use by dalivk, the native heap, and everything else.
     */
    public static native void getMemoryInfo(MemoryInfo memoryInfo);

    /**
     * Note: currently only works when the requested pid has the same UID
     * as the caller.
     * @hide
     */
    public static native void getMemoryInfo(int pid, MemoryInfo memoryInfo);

    /**
     * Establish an object allocation limit in the current thread.  Useful
     * for catching regressions in code that is expected to operate
     * without causing any allocations.
     *
     * Pass in the maximum number of allowed allocations.  Use -1 to disable
     * the limit.  Returns the previous limit.
     *
     * The preferred way to use this is:
     *
     *  int prevLimit = -1;
     *  try {
     *      prevLimit = Debug.setAllocationLimit(0);
     *      ... do stuff that's not expected to allocate memory ...
     *  } finally {
     *      Debug.setAllocationLimit(prevLimit);
     *  }
     *
     * This allows limits to be nested.  The try/finally ensures that the
     * limit is reset if something fails.
     *
     * Exceeding the limit causes a dalvik.system.AllocationLimitError to
     * be thrown from a memory allocation call.  The limit is reset to -1
     * when this happens.
     *
     * The feature may be disabled in the VM configuration.  If so, this
     * call has no effect, and always returns -1.
     */
    public static int setAllocationLimit(int limit) {
        return VMDebug.setAllocationLimit(limit);
    }

    /**
     * Establish a global object allocation limit.  This is similar to
     * {@link #setAllocationLimit(int)} but applies to all threads in
     * the VM.  It will coexist peacefully with per-thread limits.
     *
     * [ The value of "limit" is currently restricted to 0 (no allocations
     *   allowed) or -1 (no global limit).  This may be changed in a future
     *   release. ]
     */
    public static int setGlobalAllocationLimit(int limit) {
        if (limit != 0 && limit != -1)
            throw new IllegalArgumentException("limit must be 0 or -1");
        return VMDebug.setGlobalAllocationLimit(limit);
    }

    /**
     * Dump a list of all currently loaded class to the log file.
     *
     * @param flags See constants above.
     */
    public static void printLoadedClasses(int flags) {
        VMDebug.printLoadedClasses(flags);
    }

    /**
     * Get the number of loaded classes.
     * @return the number of loaded classes.
     */
    public static int getLoadedClassCount() {
        return VMDebug.getLoadedClassCount();
    }

    /**
     * Dump "hprof" data to the specified file.  This will cause a GC.
     *
     * @param fileName Full pathname of output file (e.g. "/sdcard/dump.hprof").
     * @throws UnsupportedOperationException if the VM was built without
     *         HPROF support.
     * @throws IOException if an error occurs while opening or writing files.
     */
    public static void dumpHprofData(String fileName) throws IOException {
        VMDebug.dumpHprofData(fileName);
    }

    /**
     * Collect "hprof" and send it to DDMS.  This will cause a GC.
     *
     * @throws UnsupportedOperationException if the VM was built without
     *         HPROF support.
     *
     * @hide
     */
    public static void dumpHprofDataDdms() {
        VMDebug.dumpHprofDataDdms();
    }

    /**
     * Returns the number of sent transactions from this process.
     * @return The number of sent transactions or -1 if it could not read t.
     */
    public static native int getBinderSentTransactions();

    /**
     * Returns the number of received transactions from the binder driver.
     * @return The number of received transactions or -1 if it could not read the stats.
     */
    public static native int getBinderReceivedTransactions();

    /**
     * Returns the number of active local Binder objects that exist in the
     * current process.
     */
    public static final native int getBinderLocalObjectCount();

    /**
     * Returns the number of references to remote proxy Binder objects that
     * exist in the current process.
     */
    public static final native int getBinderProxyObjectCount();

    /**
     * Returns the number of death notification links to Binder objects that
     * exist in the current process.
     */
    public static final native int getBinderDeathObjectCount();

    /**
     * Primes the register map cache.
     *
     * Only works for classes in the bootstrap class loader.  Does not
     * cause classes to be loaded if they're not already present.
     *
     * The classAndMethodDesc argument is a concatentation of the VM-internal
     * class descriptor, method name, and method descriptor.  Examples:
     *     Landroid/os/Looper;.loop:()V
     *     Landroid/app/ActivityThread;.main:([Ljava/lang/String;)V
     *
     * @param classAndMethodDesc the method to prepare
     *
     * @hide
     */
    public static final boolean cacheRegisterMap(String classAndMethodDesc) {
        return VMDebug.cacheRegisterMap(classAndMethodDesc);
    }

    /**
     * Dumps the contents of VM reference tables (e.g. JNI locals and
     * globals) to the log file.
     *
     * @hide
     */
    public static final void dumpReferenceTables() {
        VMDebug.dumpReferenceTables();
    }

    /**
     * API for gathering and querying instruction counts.
     *
     * Example usage:
     *   Debug.InstructionCount icount = new Debug.InstructionCount();
     *   icount.resetAndStart();
     *    [... do lots of stuff ...]
     *   if (icount.collect()) {
     *       System.out.println("Total instructions executed: "
     *           + icount.globalTotal());
     *       System.out.println("Method invocations: "
     *           + icount.globalMethodInvocations());
     *   }
     */
    public static class InstructionCount {
        private static final int NUM_INSTR = 256;

        private int[] mCounts;

        public InstructionCount() {
            mCounts = new int[NUM_INSTR];
        }

        /**
         * Reset counters and ensure counts are running.  Counts may
         * have already been running.
         *
         * @return true if counting was started
         */
        public boolean resetAndStart() {
            try {
                VMDebug.startInstructionCounting();
                VMDebug.resetInstructionCount();
            } catch (UnsupportedOperationException uoe) {
                return false;
            }
            return true;
        }

        /**
         * Collect instruction counts.  May or may not stop the
         * counting process.
         */
        public boolean collect() {
            try {
                VMDebug.stopInstructionCounting();
                VMDebug.getInstructionCount(mCounts);
            } catch (UnsupportedOperationException uoe) {
                return false;
            }
            return true;
        }

        /**
         * Return the total number of instructions executed globally (i.e. in
         * all threads).
         */
        public int globalTotal() {
            int count = 0;
            for (int i = 0; i < NUM_INSTR; i++)
                count += mCounts[i];
            return count;
        }

        /**
         * Return the total number of method-invocation instructions
         * executed globally.
         */
        public int globalMethodInvocations() {
            int count = 0;

            //count += mCounts[Opcodes.OP_EXECUTE_INLINE];
            count += mCounts[Opcodes.OP_INVOKE_VIRTUAL];
            count += mCounts[Opcodes.OP_INVOKE_SUPER];
            count += mCounts[Opcodes.OP_INVOKE_DIRECT];
            count += mCounts[Opcodes.OP_INVOKE_STATIC];
            count += mCounts[Opcodes.OP_INVOKE_INTERFACE];
            count += mCounts[Opcodes.OP_INVOKE_VIRTUAL_RANGE];
            count += mCounts[Opcodes.OP_INVOKE_SUPER_RANGE];
            count += mCounts[Opcodes.OP_INVOKE_DIRECT_RANGE];
            count += mCounts[Opcodes.OP_INVOKE_STATIC_RANGE];
            count += mCounts[Opcodes.OP_INVOKE_INTERFACE_RANGE];
            //count += mCounts[Opcodes.OP_INVOKE_DIRECT_EMPTY];
            count += mCounts[Opcodes.OP_INVOKE_VIRTUAL_QUICK];
            count += mCounts[Opcodes.OP_INVOKE_VIRTUAL_QUICK_RANGE];
            count += mCounts[Opcodes.OP_INVOKE_SUPER_QUICK];
            count += mCounts[Opcodes.OP_INVOKE_SUPER_QUICK_RANGE];
            return count;
        }
    }


    /**
     * A Map of typed debug properties.
     */
    private static final TypedProperties debugProperties;

    /*
     * Load the debug properties from the standard files into debugProperties.
     */
    static {
        if (Config.DEBUG) {
            final String TAG = "DebugProperties";
            final String[] files = { "/system/debug.prop", "/debug.prop", "/data/debug.prop" };
            final TypedProperties tp = new TypedProperties();

            // Read the properties from each of the files, if present.
            for (String file : files) {
                Reader r;
                try {
                    r = new FileReader(file);
                } catch (FileNotFoundException ex) {
                    // It's ok if a file is missing.
                    continue;
                }

                try {
                    tp.load(r);
                } catch (Exception ex) {
                    throw new RuntimeException("Problem loading " + file, ex);
                } finally {
                    try {
                        r.close();
                    } catch (IOException ex) {
                        // Ignore this error.
                    }
                }
            }

            debugProperties = tp.isEmpty() ? null : tp;
        } else {
            debugProperties = null;
        }
    }


    /**
     * Returns true if the type of the field matches the specified class.
     * Handles the case where the class is, e.g., java.lang.Boolean, but
     * the field is of the primitive "boolean" type.  Also handles all of
     * the java.lang.Number subclasses.
     */
    private static boolean fieldTypeMatches(Field field, Class<?> cl) {
        Class<?> fieldClass = field.getType();
        if (fieldClass == cl) {
            return true;
        }
        Field primitiveTypeField;
        try {
            /* All of the classes we care about (Boolean, Integer, etc.)
             * have a Class field called "TYPE" that points to the corresponding
             * primitive class.
             */
            primitiveTypeField = cl.getField("TYPE");
        } catch (NoSuchFieldException ex) {
            return false;
        }
        try {
            return fieldClass == (Class<?>) primitiveTypeField.get(null);
        } catch (IllegalAccessException ex) {
            return false;
        }
    }


    /**
     * Looks up the property that corresponds to the field, and sets the field's value
     * if the types match.
     */
    private static void modifyFieldIfSet(final Field field, final TypedProperties properties,
                                         final String propertyName) {
        if (field.getType() == java.lang.String.class) {
            int stringInfo = properties.getStringInfo(propertyName);
            switch (stringInfo) {
                case TypedProperties.STRING_SET:
                    // Handle as usual below.
                    break;
                case TypedProperties.STRING_NULL:
                    try {
                        field.set(null, null);  // null object for static fields; null string
                    } catch (IllegalAccessException ex) {
                        throw new IllegalArgumentException(
                            "Cannot set field for " + propertyName, ex);
                    }
                    return;
                case TypedProperties.STRING_NOT_SET:
                    return;
                case TypedProperties.STRING_TYPE_MISMATCH:
                    throw new IllegalArgumentException(
                        "Type of " + propertyName + " " +
                        " does not match field type (" + field.getType() + ")");
                default:
                    throw new IllegalStateException(
                        "Unexpected getStringInfo(" + propertyName + ") return value " +
                        stringInfo);
            }
        }
        Object value = properties.get(propertyName);
        if (value != null) {
            if (!fieldTypeMatches(field, value.getClass())) {
                throw new IllegalArgumentException(
                    "Type of " + propertyName + " (" + value.getClass() + ") " +
                    " does not match field type (" + field.getType() + ")");
            }
            try {
                field.set(null, value);  // null object for static fields
            } catch (IllegalAccessException ex) {
                throw new IllegalArgumentException(
                    "Cannot set field for " + propertyName, ex);
            }
        }
    }


    /**
     * Equivalent to <code>setFieldsOn(cl, false)</code>.
     *
     * @see #setFieldsOn(Class, boolean)
     *
     * @hide
     */
    public static void setFieldsOn(Class<?> cl) {
        setFieldsOn(cl, false);
    }

    /**
     * Reflectively sets static fields of a class based on internal debugging
     * properties.  This method is a no-op if android.util.Config.DEBUG is
     * false.
     * <p>
     * <strong>NOTE TO APPLICATION DEVELOPERS</strong>: Config.DEBUG will
     * always be false in release builds.  This API is typically only useful
     * for platform developers.
     * </p>
     * Class setup: define a class whose only fields are non-final, static
     * primitive types (except for "char") or Strings.  In a static block
     * after the field definitions/initializations, pass the class to
     * this method, Debug.setFieldsOn(). Example:
     * <pre>
     * package com.example;
     *
     * import android.os.Debug;
     *
     * public class MyDebugVars {
     *    public static String s = "a string";
     *    public static String s2 = "second string";
     *    public static String ns = null;
     *    public static boolean b = false;
     *    public static int i = 5;
     *    @Debug.DebugProperty
     *    public static float f = 0.1f;
     *    @@Debug.DebugProperty
     *    public static double d = 0.5d;
     *
     *    // This MUST appear AFTER all fields are defined and initialized!
     *    static {
     *        // Sets all the fields
     *        Debug.setFieldsOn(MyDebugVars.class);
     *
     *        // Sets only the fields annotated with @Debug.DebugProperty
     *        // Debug.setFieldsOn(MyDebugVars.class, true);
     *    }
     * }
     * </pre>
     * setFieldsOn() may override the value of any field in the class based
     * on internal properties that are fixed at boot time.
     * <p>
     * These properties are only set during platform debugging, and are not
     * meant to be used as a general-purpose properties store.
     *
     * {@hide}
     *
     * @param cl The class to (possibly) modify
     * @param partial If false, sets all static fields, otherwise, only set
     *        fields with the {@link android.os.Debug.DebugProperty}
     *        annotation
     * @throws IllegalArgumentException if any fields are final or non-static,
     *         or if the type of the field does not match the type of
     *         the internal debugging property value.
     */
    public static void setFieldsOn(Class<?> cl, boolean partial) {
        if (Config.DEBUG) {
            if (debugProperties != null) {
                /* Only look for fields declared directly by the class,
                 * so we don't mysteriously change static fields in superclasses.
                 */
                for (Field field : cl.getDeclaredFields()) {
                    if (!partial || field.getAnnotation(DebugProperty.class) != null) {
                        final String propertyName = cl.getName() + "." + field.getName();
                        boolean isStatic = Modifier.isStatic(field.getModifiers());
                        boolean isFinal = Modifier.isFinal(field.getModifiers());

                        if (!isStatic || isFinal) {
                            throw new IllegalArgumentException(propertyName +
                                " must be static and non-final");
                        }
                        modifyFieldIfSet(field, debugProperties, propertyName);
                    }
                }
            }
        } else {
            Log.wtf(TAG,
                  "setFieldsOn(" + (cl == null ? "null" : cl.getName()) +
                  ") called in non-DEBUG build");
        }
    }

    /**
     * Annotation to put on fields you want to set with
     * {@link Debug#setFieldsOn(Class, boolean)}.
     *
     * @hide
     */
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface DebugProperty {
    }

    /**
     * Get a debugging dump of a system service by name.
     *
     * <p>Most services require the caller to hold android.permission.DUMP.
     *
     * @param name of the service to dump
     * @param fd to write dump output to (usually an output log file)
     * @param args to pass to the service's dump method, may be null
     * @return true if the service was dumped successfully, false if
     *     the service could not be found or had an error while dumping
     */
    public static boolean dumpService(String name, FileDescriptor fd, String[] args) {
        IBinder service = ServiceManager.getService(name);
        if (service == null) {
            Log.e(TAG, "Can't find service to dump: " + name);
            return false;
        }

        try {
            service.dump(fd, args);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Can't dump service: " + name, e);
            return false;
        }
    }
}
