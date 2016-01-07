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

import com.android.internal.util.FastPrintWriter;
import com.android.internal.util.TypedProperties;

import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

import org.apache.harmony.dalvik.ddmc.Chunk;
import org.apache.harmony.dalvik.ddmc.ChunkHandler;
import org.apache.harmony.dalvik.ddmc.DdmServer;

import dalvik.bytecode.OpcodeInfo;
import dalvik.system.VMDebug;


/**
 * Provides various debugging methods for Android applications, including
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
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
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
    private static final String DEFAULT_TRACE_PATH_PREFIX =
        Environment.getLegacyExternalStorageDirectory().getPath() + "/";
    private static final String DEFAULT_TRACE_BODY = "dmtrace";
    private static final String DEFAULT_TRACE_EXTENSION = ".trace";
    private static final String DEFAULT_TRACE_FILE_PATH =
        DEFAULT_TRACE_PATH_PREFIX + DEFAULT_TRACE_BODY
        + DEFAULT_TRACE_EXTENSION;


    /**
     * This class is used to retrieved various statistics about the memory mappings for this
     * process. The returned info is broken down by dalvik, native, and other. All results are in kB.
     */
    public static class MemoryInfo implements Parcelable {
        /** The proportional set size for dalvik heap.  (Doesn't include other Dalvik overhead.) */
        public int dalvikPss;
        /** The proportional set size that is swappable for dalvik heap. */
        /** @hide We may want to expose this, eventually. */
        public int dalvikSwappablePss;
        /** The private dirty pages used by dalvik heap. */
        public int dalvikPrivateDirty;
        /** The shared dirty pages used by dalvik heap. */
        public int dalvikSharedDirty;
        /** The private clean pages used by dalvik heap. */
        /** @hide We may want to expose this, eventually. */
        public int dalvikPrivateClean;
        /** The shared clean pages used by dalvik heap. */
        /** @hide We may want to expose this, eventually. */
        public int dalvikSharedClean;
        /** The dirty dalvik pages that have been swapped out. */
        /** @hide We may want to expose this, eventually. */
        public int dalvikSwappedOut;

        /** The proportional set size for the native heap. */
        public int nativePss;
        /** The proportional set size that is swappable for the native heap. */
        /** @hide We may want to expose this, eventually. */
        public int nativeSwappablePss;
        /** The private dirty pages used by the native heap. */
        public int nativePrivateDirty;
        /** The shared dirty pages used by the native heap. */
        public int nativeSharedDirty;
        /** The private clean pages used by the native heap. */
        /** @hide We may want to expose this, eventually. */
        public int nativePrivateClean;
        /** The shared clean pages used by the native heap. */
        /** @hide We may want to expose this, eventually. */
        public int nativeSharedClean;
        /** The dirty native pages that have been swapped out. */
        /** @hide We may want to expose this, eventually. */
        public int nativeSwappedOut;

        /** The proportional set size for everything else. */
        public int otherPss;
        /** The proportional set size that is swappable for everything else. */
        /** @hide We may want to expose this, eventually. */
        public int otherSwappablePss;
        /** The private dirty pages used by everything else. */
        public int otherPrivateDirty;
        /** The shared dirty pages used by everything else. */
        public int otherSharedDirty;
        /** The private clean pages used by everything else. */
        /** @hide We may want to expose this, eventually. */
        public int otherPrivateClean;
        /** The shared clean pages used by everything else. */
        /** @hide We may want to expose this, eventually. */
        public int otherSharedClean;
        /** The dirty pages used by anyting else that have been swapped out. */
        /** @hide We may want to expose this, eventually. */
        public int otherSwappedOut;

        /** @hide */
        public static final int HEAP_UNKNOWN = 0;
        /** @hide */
        public static final int HEAP_DALVIK = 1;
        /** @hide */
        public static final int HEAP_NATIVE = 2;

        /** @hide */
        public static final int OTHER_DALVIK_OTHER = 0;
        /** @hide */
        public static final int OTHER_STACK = 1;
        /** @hide */
        public static final int OTHER_CURSOR = 2;
        /** @hide */
        public static final int OTHER_ASHMEM = 3;
        /** @hide */
        public static final int OTHER_GL_DEV = 4;
        /** @hide */
        public static final int OTHER_UNKNOWN_DEV = 5;
        /** @hide */
        public static final int OTHER_SO = 6;
        /** @hide */
        public static final int OTHER_JAR = 7;
        /** @hide */
        public static final int OTHER_APK = 8;
        /** @hide */
        public static final int OTHER_TTF = 9;
        /** @hide */
        public static final int OTHER_DEX = 10;
        /** @hide */
        public static final int OTHER_OAT = 11;
        /** @hide */
        public static final int OTHER_ART = 12;
        /** @hide */
        public static final int OTHER_UNKNOWN_MAP = 13;
        /** @hide */
        public static final int OTHER_GRAPHICS = 14;
        /** @hide */
        public static final int OTHER_GL = 15;
        /** @hide */
        public static final int OTHER_OTHER_MEMTRACK = 16;

        /** @hide */
        public static final int OTHER_DALVIK_NORMAL = 17;
        /** @hide */
        public static final int OTHER_DALVIK_LARGE = 18;
        /** @hide */
        public static final int OTHER_DALVIK_LINEARALLOC = 19;
        /** @hide */
        public static final int OTHER_DALVIK_ACCOUNTING = 20;
        /** @hide */
        public static final int OTHER_DALVIK_CODE_CACHE = 21;
        /** @hide */
        public static final int OTHER_DALVIK_ZYGOTE = 22;
        /** @hide */
        public static final int OTHER_DALVIK_NON_MOVING = 23;
        /** @hide */
        public static final int OTHER_DALVIK_INDIRECT_REFERENCE_TABLE = 24;

        /** @hide */
        public static final int NUM_OTHER_STATS = 17;

        /** @hide */
        public static final int NUM_DVK_STATS = 8;

        /** @hide */
        public static final int NUM_CATEGORIES = 7;

        /** @hide */
        public static final int offsetPss = 0;
        /** @hide */
        public static final int offsetSwappablePss = 1;
        /** @hide */
        public static final int offsetPrivateDirty = 2;
        /** @hide */
        public static final int offsetSharedDirty = 3;
        /** @hide */
        public static final int offsetPrivateClean = 4;
        /** @hide */
        public static final int offsetSharedClean = 5;
        /** @hide */
        public static final int offsetSwappedOut = 6;

        private int[] otherStats = new int[(NUM_OTHER_STATS+NUM_DVK_STATS)*NUM_CATEGORIES];

        public MemoryInfo() {
        }

        /**
         * Return total PSS memory usage in kB.
         */
        public int getTotalPss() {
            return dalvikPss + nativePss + otherPss;
        }

        /**
         * @hide Return total PSS memory usage in kB.
         */
        public int getTotalUss() {
            return dalvikPrivateClean + dalvikPrivateDirty
                    + nativePrivateClean + nativePrivateDirty
                    + otherPrivateClean + otherPrivateDirty;
        }

        /**
         * Return total PSS memory usage in kB.
         */
        public int getTotalSwappablePss() {
            return dalvikSwappablePss + nativeSwappablePss + otherSwappablePss;
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

        /**
         * Return total shared clean memory usage in kB.
         */
        public int getTotalPrivateClean() {
            return dalvikPrivateClean + nativePrivateClean + otherPrivateClean;
        }

        /**
         * Return total shared clean memory usage in kB.
         */
        public int getTotalSharedClean() {
            return dalvikSharedClean + nativeSharedClean + otherSharedClean;
        }

        /**
         * Return total swapped out memory in kB.
         * @hide
         */
        public int getTotalSwappedOut() {
            return dalvikSwappedOut + nativeSwappedOut + otherSwappedOut;
        }

        /** @hide */
        public int getOtherPss(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetPss];
        }


        /** @hide */
        public int getOtherSwappablePss(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetSwappablePss];
        }


        /** @hide */
        public int getOtherPrivateDirty(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetPrivateDirty];
        }

        /** @hide */
        public int getOtherSharedDirty(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetSharedDirty];
        }

        /** @hide */
        public int getOtherPrivateClean(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetPrivateClean];
        }

        /** @hide */
        public int getOtherPrivate(int which) {
          return getOtherPrivateClean(which) + getOtherPrivateDirty(which);
        }

        /** @hide */
        public int getOtherSharedClean(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetSharedClean];
        }

        /** @hide */
        public int getOtherSwappedOut(int which) {
            return otherStats[which*NUM_CATEGORIES + offsetSwappedOut];
        }

        /** @hide */
        public static String getOtherLabel(int which) {
            switch (which) {
                case OTHER_DALVIK_OTHER: return "Dalvik Other";
                case OTHER_STACK: return "Stack";
                case OTHER_CURSOR: return "Cursor";
                case OTHER_ASHMEM: return "Ashmem";
                case OTHER_GL_DEV: return "Gfx dev";
                case OTHER_UNKNOWN_DEV: return "Other dev";
                case OTHER_SO: return ".so mmap";
                case OTHER_JAR: return ".jar mmap";
                case OTHER_APK: return ".apk mmap";
                case OTHER_TTF: return ".ttf mmap";
                case OTHER_DEX: return ".dex mmap";
                case OTHER_OAT: return ".oat mmap";
                case OTHER_ART: return ".art mmap";
                case OTHER_UNKNOWN_MAP: return "Other mmap";
                case OTHER_GRAPHICS: return "EGL mtrack";
                case OTHER_GL: return "GL mtrack";
                case OTHER_OTHER_MEMTRACK: return "Other mtrack";
                case OTHER_DALVIK_NORMAL: return ".Heap";
                case OTHER_DALVIK_LARGE: return ".LOS";
                case OTHER_DALVIK_LINEARALLOC: return ".LinearAlloc";
                case OTHER_DALVIK_ACCOUNTING: return ".GC";
                case OTHER_DALVIK_CODE_CACHE: return ".JITCache";
                case OTHER_DALVIK_ZYGOTE: return ".Zygote";
                case OTHER_DALVIK_NON_MOVING: return ".NonMoving";
                case OTHER_DALVIK_INDIRECT_REFERENCE_TABLE: return ".IndirectRef";
                default: return "????";
            }
        }

      /**
       * Returns the value of a particular memory statistic or {@code null} if no
       * such memory statistic exists.
       *
       * <p>The following table lists the memory statistics that are supported.
       * Note that memory statistics may be added or removed in a future API level.</p>
       *
       * <table>
       *     <thead>
       *         <tr>
       *             <th>Memory statistic name</th>
       *             <th>Meaning</th>
       *             <th>Example</th>
       *             <th>Supported (API Levels)</th>
       *         </tr>
       *     </thead>
       *     <tbody>
       *         <tr>
       *             <td>summary.java-heap</td>
       *             <td>The private Java Heap usage in kB. This corresponds to the Java Heap field
       *                 in the App Summary section output by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.native-heap</td>
       *             <td>The private Native Heap usage in kB. This corresponds to the Native Heap
       *                 field in the App Summary section output by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.code</td>
       *             <td>The memory usage for static code and resources in kB. This corresponds to
       *                 the Code field in the App Summary section output by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.stack</td>
       *             <td>The stack usage in kB. This corresponds to the Stack field in the
       *                 App Summary section output by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.graphics</td>
       *             <td>The graphics usage in kB. This corresponds to the Graphics field in the
       *                 App Summary section output by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.private-other</td>
       *             <td>Other private memory usage in kB. This corresponds to the Private Other
       *                 field output in the App Summary section by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.system</td>
       *             <td>Shared and system memory usage in kB. This corresponds to the System
       *                 field output in the App Summary section by dumpsys meminfo.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.total-pss</td>
       *             <td>Total PPS memory usage in kB.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *         <tr>
       *             <td>summary.total-swap</td>
       *             <td>Total swap usage in kB.</td>
       *             <td>{@code 1442}</td>
       *             <td>23</td>
       *         </tr>
       *     </tbody>
       * </table>
       */
       public String getMemoryStat(String statName) {
            switch(statName) {
                case "summary.java-heap":
                    return Integer.toString(getSummaryJavaHeap());
                case "summary.native-heap":
                    return Integer.toString(getSummaryNativeHeap());
                case "summary.code":
                    return Integer.toString(getSummaryCode());
                case "summary.stack":
                    return Integer.toString(getSummaryStack());
                case "summary.graphics":
                    return Integer.toString(getSummaryGraphics());
                case "summary.private-other":
                    return Integer.toString(getSummaryPrivateOther());
                case "summary.system":
                    return Integer.toString(getSummarySystem());
                case "summary.total-pss":
                    return Integer.toString(getSummaryTotalPss());
                case "summary.total-swap":
                    return Integer.toString(getSummaryTotalSwap());
                default:
                    return null;
            }
        }

        /**
         * Returns a map of the names/values of the memory statistics
         * that {@link #getMemoryStat(String)} supports.
         *
         * @return a map of the names/values of the supported memory statistics.
         */
        public Map<String, String> getMemoryStats() {
            Map<String, String> stats = new HashMap<String, String>();
            stats.put("summary.java-heap", Integer.toString(getSummaryJavaHeap()));
            stats.put("summary.native-heap", Integer.toString(getSummaryNativeHeap()));
            stats.put("summary.code", Integer.toString(getSummaryCode()));
            stats.put("summary.stack", Integer.toString(getSummaryStack()));
            stats.put("summary.graphics", Integer.toString(getSummaryGraphics()));
            stats.put("summary.private-other", Integer.toString(getSummaryPrivateOther()));
            stats.put("summary.system", Integer.toString(getSummarySystem()));
            stats.put("summary.total-pss", Integer.toString(getSummaryTotalPss()));
            stats.put("summary.total-swap", Integer.toString(getSummaryTotalSwap()));
            return stats;
        }

        /**
         * Pss of Java Heap bytes in KB due to the application.
         * Notes:
         *  * OTHER_ART is the boot image. Anything private here is blamed on
         *    the application, not the system.
         *  * dalvikPrivateDirty includes private zygote, which means the
         *    application dirtied something allocated by the zygote. We blame
         *    the application for that memory, not the system.
         *  * Does not include OTHER_DALVIK_OTHER, which is considered VM
         *    Overhead and lumped into Private Other.
         *  * We don't include dalvikPrivateClean, because there should be no
         *    such thing as private clean for the Java Heap.
         * @hide
         */
        public int getSummaryJavaHeap() {
            return dalvikPrivateDirty + getOtherPrivate(OTHER_ART);
        }

        /**
         * Pss of Native Heap bytes in KB due to the application.
         * Notes:
         *  * Includes private dirty malloc space.
         *  * We don't include nativePrivateClean, because there should be no
         *    such thing as private clean for the Native Heap.
         * @hide
         */
        public int getSummaryNativeHeap() {
            return nativePrivateDirty;
        }

        /**
         * Pss of code and other static resource bytes in KB due to
         * the application.
         * @hide
         */
        public int getSummaryCode() {
            return getOtherPrivate(OTHER_SO)
              + getOtherPrivate(OTHER_JAR)
              + getOtherPrivate(OTHER_APK)
              + getOtherPrivate(OTHER_TTF)
              + getOtherPrivate(OTHER_DEX)
              + getOtherPrivate(OTHER_OAT);
        }

        /**
         * Pss in KB of the stack due to the application.
         * Notes:
         *  * Includes private dirty stack, which includes both Java and Native
         *    stack.
         *  * Does not include private clean stack, because there should be no
         *    such thing as private clean for the stack.
         * @hide
         */
        public int getSummaryStack() {
            return getOtherPrivateDirty(OTHER_STACK);
        }

        /**
         * Pss in KB of graphics due to the application.
         * Notes:
         *  * Includes private Gfx, EGL, and GL.
         *  * Warning: These numbers can be misreported by the graphics drivers.
         *  * We don't include shared graphics. It may make sense to, because
         *    shared graphics are likely buffers due to the application
         *    anyway, but it's simpler to implement to just group all shared
         *    memory into the System category.
         * @hide
         */
        public int getSummaryGraphics() {
            return getOtherPrivate(OTHER_GL_DEV)
              + getOtherPrivate(OTHER_GRAPHICS)
              + getOtherPrivate(OTHER_GL);
        }

        /**
         * Pss in KB due to the application that haven't otherwise been
         * accounted for.
         * @hide
         */
        public int getSummaryPrivateOther() {
            return getTotalPrivateClean()
              + getTotalPrivateDirty()
              - getSummaryJavaHeap()
              - getSummaryNativeHeap()
              - getSummaryCode()
              - getSummaryStack()
              - getSummaryGraphics();
        }

        /**
         * Pss in KB due to the system.
         * Notes:
         *  * Includes all shared memory.
         * @hide
         */
        public int getSummarySystem() {
            return getTotalPss()
              - getTotalPrivateClean()
              - getTotalPrivateDirty();
        }

        /**
         * Total Pss in KB.
         * @hide
         */
        public int getSummaryTotalPss() {
            return getTotalPss();
        }

        /**
         * Total Swap in KB.
         * Notes:
         *  * Some of this memory belongs in other categories, but we don't
         *    know if the Swap memory is shared or private, so we don't know
         *    what to blame on the application and what on the system.
         *    For now, just lump all the Swap in one place.
         * @hide
         */
        public int getSummaryTotalSwap() {
            return getTotalSwappedOut();
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(dalvikPss);
            dest.writeInt(dalvikSwappablePss);
            dest.writeInt(dalvikPrivateDirty);
            dest.writeInt(dalvikSharedDirty);
            dest.writeInt(dalvikPrivateClean);
            dest.writeInt(dalvikSharedClean);
            dest.writeInt(dalvikSwappedOut);
            dest.writeInt(nativePss);
            dest.writeInt(nativeSwappablePss);
            dest.writeInt(nativePrivateDirty);
            dest.writeInt(nativeSharedDirty);
            dest.writeInt(nativePrivateClean);
            dest.writeInt(nativeSharedClean);
            dest.writeInt(nativeSwappedOut);
            dest.writeInt(otherPss);
            dest.writeInt(otherSwappablePss);
            dest.writeInt(otherPrivateDirty);
            dest.writeInt(otherSharedDirty);
            dest.writeInt(otherPrivateClean);
            dest.writeInt(otherSharedClean);
            dest.writeInt(otherSwappedOut);
            dest.writeIntArray(otherStats);
        }

        public void readFromParcel(Parcel source) {
            dalvikPss = source.readInt();
            dalvikSwappablePss = source.readInt();
            dalvikPrivateDirty = source.readInt();
            dalvikSharedDirty = source.readInt();
            dalvikPrivateClean = source.readInt();
            dalvikSharedClean = source.readInt();
            dalvikSwappedOut = source.readInt();
            nativePss = source.readInt();
            nativeSwappablePss = source.readInt();
            nativePrivateDirty = source.readInt();
            nativeSharedDirty = source.readInt();
            nativePrivateClean = source.readInt();
            nativeSharedClean = source.readInt();
            nativeSwappedOut = source.readInt();
            otherPss = source.readInt();
            otherSwappablePss = source.readInt();
            otherPrivateDirty = source.readInt();
            otherSharedDirty = source.readInt();
            otherPrivateClean = source.readInt();
            otherSharedClean = source.readInt();
            otherSwappedOut = source.readInt();
            otherStats = source.createIntArray();
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
            outStream = new FastPrintWriter(fos);
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
            outStream = new FastPrintWriter(fos);
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
        VMDebug.startMethodTracing(DEFAULT_TRACE_FILE_PATH, 0, 0, false, 0);
    }

    /**
     * Start method tracing, specifying the trace log file name.  The trace
     * file will be put under "/sdcard" unless an absolute path is given.
     * See <a
       href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a> for
     * information about reading trace files.
     *
     * @param traceName Name for the trace log file to create.
     * If {@code traceName} is null, this value defaults to "/sdcard/dmtrace.trace".
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
     * If {@code traceName} is null, this value defaults to "/sdcard/dmtrace.trace".
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
     * same method.  As an alternative, consider using sampling-based method
     * tracing via {@link #startMethodTracingSampling(String, int, int)} or
     * "native" tracing in the emulator via {@link #startNativeTracing()}.
     * </p>
     *
     * @param traceName    Name for the trace log file to create.
     * If {@code traceName} is null, this value defaults to "/sdcard/dmtrace.trace".
     * If the files already exist, they will be truncated.
     * If the trace file given does not end in ".trace", it will be appended for you.
     * @param bufferSize    The maximum amount of trace data we gather. If not given, it defaults to 8MB.
     * @param flags    Flags to control method tracing. The only one that is currently defined is {@link #TRACE_COUNT_ALLOCS}.
     */
    public static void startMethodTracing(String traceName, int bufferSize,
        int flags) {
        VMDebug.startMethodTracing(fixTraceName(traceName), bufferSize, flags, false, 0);
    }

    /**
     * Start sampling-based method tracing, specifying the trace log file name,
     * the buffer size, and the sampling interval. The trace files will be put
     * under "/sdcard" unless an absolute path is given. See <a
       href="{@docRoot}guide/developing/tools/traceview.html">Traceview: A Graphical Log Viewer</a>
     * for information about reading trace files.
     *
     * @param traceName    Name for the trace log file to create.
     * If {@code traceName} is null, this value defaults to "/sdcard/dmtrace.trace".
     * If the files already exist, they will be truncated.
     * If the trace file given does not end in ".trace", it will be appended for you.
     * @param bufferSize    The maximum amount of trace data we gather. If not given, it defaults to 8MB.
     * @param intervalUs    The amount of time between each sample in microseconds.
     */
    public static void startMethodTracingSampling(String traceName,
        int bufferSize, int intervalUs) {
        VMDebug.startMethodTracing(fixTraceName(traceName), bufferSize, 0, true, intervalUs);
    }

    /**
     * Formats name of trace log file for method tracing.
     */
    private static String fixTraceName(String traceName) {
        if (traceName == null)
            traceName = DEFAULT_TRACE_FILE_PATH;
        if (traceName.charAt(0) != '/')
            traceName = DEFAULT_TRACE_PATH_PREFIX + traceName;
        if (!traceName.endsWith(DEFAULT_TRACE_EXTENSION))
            traceName = traceName + DEFAULT_TRACE_EXTENSION;

        return traceName;
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
        VMDebug.startMethodTracing(traceName, fd, bufferSize, flags, false, 0);
    }

    /**
     * Starts method tracing without a backing file.  When stopMethodTracing
     * is called, the result is sent directly to DDMS.  (If DDMS is not
     * attached when tracing ends, the profiling data will be discarded.)
     *
     * @hide
     */
    public static void startMethodTracingDdms(int bufferSize, int flags,
        boolean samplingEnabled, int intervalUs) {
        VMDebug.startMethodTracingDdms(bufferSize, flags, samplingEnabled, intervalUs);
    }

    /**
     * Determine whether method tracing is currently active and what type is
     * active.
     *
     * @hide
     */
    public static int getMethodTracingMode() {
        return VMDebug.getMethodTracingMode();
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
     * Start counting the number and aggregate size of memory allocations.
     *
     * <p>The {@link #startAllocCounting() start} method resets the counts and enables counting.
     * The {@link #stopAllocCounting() stop} method disables the counting so that the analysis
     * code doesn't cause additional allocations.  The various <code>get</code> methods return
     * the specified value. And the various <code>reset</code> methods reset the specified
     * count.</p>
     *
     * <p>Counts are kept for the system as a whole (global) and for each thread.
     * The per-thread counts for threads other than the current thread
     * are not cleared by the "reset" or "start" calls.</p>
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void startAllocCounting() {
        VMDebug.startAllocCounting();
    }

    /**
     * Stop counting the number and aggregate size of memory allocations.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void stopAllocCounting() {
        VMDebug.stopAllocCounting();
    }

    /**
     * Returns the global count of objects allocated by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_OBJECTS);
    }

    /**
     * Clears the global count of objects allocated.
     * @see #getGlobalAllocCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_OBJECTS);
    }

    /**
     * Returns the global size, in bytes, of objects allocated by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_BYTES);
    }

    /**
     * Clears the global size of objects allocated.
     * @see #getGlobalAllocSize()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_ALLOCATED_BYTES);
    }

    /**
     * Returns the global count of objects freed by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalFreedCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_FREED_OBJECTS);
    }

    /**
     * Clears the global count of objects freed.
     * @see #getGlobalFreedCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalFreedCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_FREED_OBJECTS);
    }

    /**
     * Returns the global size, in bytes, of objects freed by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalFreedSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_FREED_BYTES);
    }

    /**
     * Clears the global size of objects freed.
     * @see #getGlobalFreedSize()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalFreedSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_FREED_BYTES);
    }

    /**
     * Returns the number of non-concurrent GC invocations between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalGcInvocationCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_GC_INVOCATIONS);
    }

    /**
     * Clears the count of non-concurrent GC invocations.
     * @see #getGlobalGcInvocationCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalGcInvocationCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_GC_INVOCATIONS);
    }

    /**
     * Returns the number of classes successfully initialized (ie those that executed without
     * throwing an exception) between a {@link #startAllocCounting() start} and
     * {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalClassInitCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_COUNT);
    }

    /**
     * Clears the count of classes initialized.
     * @see #getGlobalClassInitCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalClassInitCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_COUNT);
    }

    /**
     * Returns the time spent successfully initializing classes between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getGlobalClassInitTime() {
        /* cumulative elapsed time for class initialization, in usec */
        return VMDebug.getAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_TIME);
    }

    /**
     * Clears the count of time spent initializing classes.
     * @see #getGlobalClassInitTime()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetGlobalClassInitTime() {
        VMDebug.resetAllocCount(VMDebug.KIND_GLOBAL_CLASS_INIT_TIME);
    }

    /**
     * This method exists for compatibility and always returns 0.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getGlobalExternalAllocCount() {
        return 0;
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetGlobalExternalAllocSize() {}

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetGlobalExternalAllocCount() {}

    /**
     * This method exists for compatibility and always returns 0.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getGlobalExternalAllocSize() {
        return 0;
    }

    /**
     * This method exists for compatibility and always returns 0.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getGlobalExternalFreedCount() {
        return 0;
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetGlobalExternalFreedCount() {}

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getGlobalExternalFreedSize() {
        return 0;
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetGlobalExternalFreedSize() {}

    /**
     * Returns the thread-local count of objects allocated by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getThreadAllocCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_ALLOCATED_OBJECTS);
    }

    /**
     * Clears the thread-local count of objects allocated.
     * @see #getThreadAllocCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetThreadAllocCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_ALLOCATED_OBJECTS);
    }

    /**
     * Returns the thread-local size of objects allocated by the runtime between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     * @return The allocated size in bytes.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getThreadAllocSize() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_ALLOCATED_BYTES);
    }

    /**
     * Clears the thread-local count of objects allocated.
     * @see #getThreadAllocSize()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetThreadAllocSize() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_ALLOCATED_BYTES);
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getThreadExternalAllocCount() {
        return 0;
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetThreadExternalAllocCount() {}

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int getThreadExternalAllocSize() {
        return 0;
    }

    /**
     * This method exists for compatibility and has no effect.
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static void resetThreadExternalAllocSize() {}

    /**
     * Returns the number of thread-local non-concurrent GC invocations between a
     * {@link #startAllocCounting() start} and {@link #stopAllocCounting() stop}.
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static int getThreadGcInvocationCount() {
        return VMDebug.getAllocCount(VMDebug.KIND_THREAD_GC_INVOCATIONS);
    }

    /**
     * Clears the thread-local count of non-concurrent GC invocations.
     * @see #getThreadGcInvocationCount()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetThreadGcInvocationCount() {
        VMDebug.resetAllocCount(VMDebug.KIND_THREAD_GC_INVOCATIONS);
    }

    /**
     * Clears all the global and thread-local memory allocation counters.
     * @see #startAllocCounting()
     *
     * @deprecated Accurate counting is a burden on the runtime and may be removed.
     */
    @Deprecated
    public static void resetAllCounts() {
        VMDebug.resetAllocCount(VMDebug.KIND_ALL_COUNTS);
    }

    /**
     * Returns the value of a particular runtime statistic or {@code null} if no
     * such runtime statistic exists.
     *
     * <p>The following table lists the runtime statistics that the runtime supports.
     * Note runtime statistics may be added or removed in a future API level.</p>
     *
     * <table>
     *     <thead>
     *         <tr>
     *             <th>Runtime statistic name</th>
     *             <th>Meaning</th>
     *             <th>Example</th>
     *             <th>Supported (API Levels)</th>
     *         </tr>
     *     </thead>
     *     <tbody>
     *         <tr>
     *             <td>art.gc.gc-count</td>
     *             <td>The number of garbage collection runs.</td>
     *             <td>{@code 164}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.gc-time</td>
     *             <td>The total duration of garbage collection runs in ms.</td>
     *             <td>{@code 62364}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.bytes-allocated</td>
     *             <td>The total number of bytes that the application allocated.</td>
     *             <td>{@code 1463948408}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.bytes-freed</td>
     *             <td>The total number of bytes that garbage collection reclaimed.</td>
     *             <td>{@code 1313493084}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.blocking-gc-count</td>
     *             <td>The number of blocking garbage collection runs.</td>
     *             <td>{@code 2}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.blocking-gc-time</td>
     *             <td>The total duration of blocking garbage collection runs in ms.</td>
     *             <td>{@code 804}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.gc-count-rate-histogram</td>
     *             <td>Every 10 seconds, the gc-count-rate is computed as the number of garbage
     *                 collection runs that have occurred over the last 10
     *                 seconds. art.gc.gc-count-rate-histogram is a histogram of the gc-count-rate
     *                 samples taken since the process began. The histogram can be used to identify
     *                 instances of high rates of garbage collection runs. For example, a histogram
     *                 of "0:34503,1:45350,2:11281,3:8088,4:43,5:8" shows that most of the time
     *                 there are between 0 and 2 garbage collection runs every 10 seconds, but there
     *                 were 8 distinct 10-second intervals in which 5 garbage collection runs
     *                 occurred.</td>
     *             <td>{@code 0:34503,1:45350,2:11281,3:8088,4:43,5:8}</td>
     *             <td>23</td>
     *         </tr>
     *         <tr>
     *             <td>art.gc.blocking-gc-count-rate-histogram</td>
     *             <td>Every 10 seconds, the blocking-gc-count-rate is computed as the number of
     *                 blocking garbage collection runs that have occurred over the last 10
     *                 seconds. art.gc.blocking-gc-count-rate-histogram is a histogram of the
     *                 blocking-gc-count-rate samples taken since the process began. The histogram
     *                 can be used to identify instances of high rates of blocking garbage
     *                 collection runs. For example, a histogram of "0:99269,1:1,2:1" shows that
     *                 most of the time there are zero blocking garbage collection runs every 10
     *                 seconds, but there was one 10-second interval in which one blocking garbage
     *                 collection run occurred, and there was one interval in which two blocking
     *                 garbage collection runs occurred.</td>
     *             <td>{@code 0:99269,1:1,2:1}</td>
     *             <td>23</td>
     *         </tr>
     *     </tbody>
     * </table>
     *
     * @param statName
     *            the name of the runtime statistic to look up.
     * @return the value of the specified runtime statistic or {@code null} if the
     *         runtime statistic doesn't exist.
     */
    public static String getRuntimeStat(String statName) {
        return VMDebug.getRuntimeStat(statName);
    }

    /**
     * Returns a map of the names/values of the runtime statistics
     * that {@link #getRuntimeStat(String)} supports.
     *
     * @return a map of the names/values of the supported runtime statistics.
     */
    public static Map<String, String> getRuntimeStats() {
        return VMDebug.getRuntimeStats();
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
     * how much is in use by dalvik, the native heap, and everything else.
     *
     * <p><b>Note:</b> this method directly retrieves memory information for the give process
     * from low-level data available to it.  It may not be able to retrieve information about
     * some protected allocations, such as graphics.  If you want to be sure you can see
     * all information about allocations by the process, use instead
     * {@link android.app.ActivityManager#getProcessMemoryInfo(int[])}.</p>
     */
    public static native void getMemoryInfo(MemoryInfo memoryInfo);

    /**
     * Note: currently only works when the requested pid has the same UID
     * as the caller.
     * @hide
     */
    public static native void getMemoryInfo(int pid, MemoryInfo memoryInfo);

    /**
     * Retrieves the PSS memory used by the process as given by the
     * smaps.
     */
    public static native long getPss();

    /**
     * Retrieves the PSS memory used by the process as given by the
     * smaps.  Optionally supply a long array of 1 entry to also
     * receive the uss of the process, and another array to also
     * retrieve the separate memtrack size.  @hide
     */
    public static native long getPss(int pid, long[] outUss, long[] outMemtrack);

    /** @hide */
    public static final int MEMINFO_TOTAL = 0;
    /** @hide */
    public static final int MEMINFO_FREE = 1;
    /** @hide */
    public static final int MEMINFO_BUFFERS = 2;
    /** @hide */
    public static final int MEMINFO_CACHED = 3;
    /** @hide */
    public static final int MEMINFO_SHMEM = 4;
    /** @hide */
    public static final int MEMINFO_SLAB = 5;
    /** @hide */
    public static final int MEMINFO_SWAP_TOTAL = 6;
    /** @hide */
    public static final int MEMINFO_SWAP_FREE = 7;
    /** @hide */
    public static final int MEMINFO_ZRAM_TOTAL = 8;
    /** @hide */
    public static final int MEMINFO_MAPPED = 9;
    /** @hide */
    public static final int MEMINFO_VM_ALLOC_USED = 10;
    /** @hide */
    public static final int MEMINFO_PAGE_TABLES = 11;
    /** @hide */
    public static final int MEMINFO_KERNEL_STACK = 12;
    /** @hide */
    public static final int MEMINFO_COUNT = 13;

    /**
     * Retrieves /proc/meminfo.  outSizes is filled with fields
     * as defined by MEMINFO_* offsets.
     * @hide
     */
    public static native void getMemInfo(long[] outSizes);

    /**
     * Establish an object allocation limit in the current thread.
     * This feature was never enabled in release builds.  The
     * allocation limits feature was removed in Honeycomb.  This
     * method exists for compatibility and always returns -1 and has
     * no effect.
     *
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int setAllocationLimit(int limit) {
        return -1;
    }

    /**
     * Establish a global object allocation limit.  This feature was
     * never enabled in release builds.  The allocation limits feature
     * was removed in Honeycomb.  This method exists for compatibility
     * and always returns -1 and has no effect.
     *
     * @deprecated This method is now obsolete.
     */
    @Deprecated
    public static int setGlobalAllocationLimit(int limit) {
        return -1;
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
     * Dump "hprof" data to the specified file.  This may cause a GC.
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
     * Like dumpHprofData(String), but takes an already-opened
     * FileDescriptor to which the trace is written.  The file name is also
     * supplied simply for logging.  Makes a dup of the file descriptor.
     *
     * Primarily for use by the "am" shell command.
     *
     * @hide
     */
    public static void dumpHprofData(String fileName, FileDescriptor fd)
            throws IOException {
        VMDebug.dumpHprofData(fileName, fd);
    }

    /**
     * Collect "hprof" and send it to DDMS.  This may cause a GC.
     *
     * @throws UnsupportedOperationException if the VM was built without
     *         HPROF support.
     * @hide
     */
    public static void dumpHprofDataDdms() {
        VMDebug.dumpHprofDataDdms();
    }

    /**
     * Writes native heap data to the specified file descriptor.
     *
     * @hide
     */
    public static native void dumpNativeHeap(FileDescriptor fd);

    /**
      * Returns a count of the extant instances of a class.
     *
     * @hide
     */
    public static long countInstancesOfClass(Class cls) {
        return VMDebug.countInstancesOfClass(cls, true);
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
     * <pre>
     *   Debug.InstructionCount icount = new Debug.InstructionCount();
     *   icount.resetAndStart();
     *    [... do lots of stuff ...]
     *   if (icount.collect()) {
     *       System.out.println("Total instructions executed: "
     *           + icount.globalTotal());
     *       System.out.println("Method invocations: "
     *           + icount.globalMethodInvocations());
     *   }
     * </pre>
     *
     * @deprecated Instruction counting is no longer supported.
     */
    @Deprecated
    public static class InstructionCount {
        private static final int NUM_INSTR =
            OpcodeInfo.MAXIMUM_PACKED_VALUE + 1;

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

            for (int i = 0; i < NUM_INSTR; i++) {
                count += mCounts[i];
            }

            return count;
        }

        /**
         * Return the total number of method-invocation instructions
         * executed globally.
         */
        public int globalMethodInvocations() {
            int count = 0;

            for (int i = 0; i < NUM_INSTR; i++) {
                if (OpcodeInfo.isInvoke(i)) {
                    count += mCounts[i];
                }
            }

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
        if (false) {
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
     * properties.  This method is a no-op if false is
     * false.
     * <p>
     * <strong>NOTE TO APPLICATION DEVELOPERS</strong>: false will
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
        if (false) {
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

    /**
     * Have the stack traces of the given native process dumped to the
     * specified file.  Will be appended to the file.
     * @hide
     */
    public static native void dumpNativeBacktraceToFile(int pid, String file);

    /**
     * Return a String describing the calling method and location at a particular stack depth.
     * @param callStack the Thread stack
     * @param depth the depth of stack to return information for.
     * @return the String describing the caller at that depth.
     */
    private static String getCaller(StackTraceElement callStack[], int depth) {
        // callStack[4] is the caller of the method that called getCallers()
        if (4 + depth >= callStack.length) {
            return "<bottom of call stack>";
        }
        StackTraceElement caller = callStack[4 + depth];
        return caller.getClassName() + "." + caller.getMethodName() + ":" + caller.getLineNumber();
    }

    /**
     * Return a string consisting of methods and locations at multiple call stack levels.
     * @param depth the number of levels to return, starting with the immediate caller.
     * @return a string describing the call stack.
     * {@hide}
     */
    public static String getCallers(final int depth) {
        final StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < depth; i++) {
            sb.append(getCaller(callStack, i)).append(" ");
        }
        return sb.toString();
    }

    /**
     * Return a string consisting of methods and locations at multiple call stack levels.
     * @param depth the number of levels to return, starting with the immediate caller.
     * @return a string describing the call stack.
     * {@hide}
     */
    public static String getCallers(final int start, int depth) {
        final StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        StringBuffer sb = new StringBuffer();
        depth += start;
        for (int i = start; i < depth; i++) {
            sb.append(getCaller(callStack, i)).append(" ");
        }
        return sb.toString();
    }

    /**
     * Like {@link #getCallers(int)}, but each location is append to the string
     * as a new line with <var>linePrefix</var> in front of it.
     * @param depth the number of levels to return, starting with the immediate caller.
     * @param linePrefix prefix to put in front of each location.
     * @return a string describing the call stack.
     * {@hide}
     */
    public static String getCallers(final int depth, String linePrefix) {
        final StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < depth; i++) {
            sb.append(linePrefix).append(getCaller(callStack, i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * @return a String describing the immediate caller of the calling method.
     * {@hide}
     */
    public static String getCaller() {
        return getCaller(Thread.currentThread().getStackTrace(), 0);
    }
}
