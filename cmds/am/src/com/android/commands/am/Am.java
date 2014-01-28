/*
**
** Copyright 2007, The Android Open Source Project
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


package com.android.commands.am;

import android.app.ActivityManager;
import android.app.ActivityManager.StackBoxInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityController;
import android.app.IActivityManager;
import android.app.IInstrumentationWatcher;
import android.app.Instrumentation;
import android.app.UiAutomationConnection;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.AndroidException;
import android.view.IWindowManager;
import com.android.internal.os.BaseCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;

public class Am extends BaseCommand {

    private IActivityManager mAm;

    private int mStartFlags = 0;
    private boolean mWaitOption = false;
    private boolean mStopOption = false;

    private int mRepeat = 0;
    private int mUserId;
    private String mReceiverPermission;

    private String mProfileFile;

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        (new Am()).run(args);
    }

    @Override
    public void onShowUsage(PrintStream out) {
        out.println(
                "usage: am [subcommand] [options]\n" +
                "usage: am start [-D] [-W] [-P <FILE>] [--start-profiler <FILE>]\n" +
                "               [--R COUNT] [-S] [--opengl-trace]\n" +
                "               [--user <USER_ID> | current] <INTENT>\n" +
                "       am startservice [--user <USER_ID> | current] <INTENT>\n" +
                "       am stopservice [--user <USER_ID> | current] <INTENT>\n" +
                "       am force-stop [--user <USER_ID> | all | current] <PACKAGE>\n" +
                "       am kill [--user <USER_ID> | all | current] <PACKAGE>\n" +
                "       am kill-all\n" +
                "       am broadcast [--user <USER_ID> | all | current] <INTENT>\n" +
                "       am instrument [-r] [-e <NAME> <VALUE>] [-p <FILE>] [-w]\n" +
                "               [--user <USER_ID> | current]\n" +
                "               [--no-window-animation] <COMPONENT>\n" +
                "       am profile start [--user <USER_ID> current] <PROCESS> <FILE>\n" +
                "       am profile stop [--user <USER_ID> current] [<PROCESS>]\n" +
                "       am dumpheap [--user <USER_ID> current] [-n] <PROCESS> <FILE>\n" +
                "       am set-debug-app [-w] [--persistent] <PACKAGE>\n" +
                "       am clear-debug-app\n" +
                "       am monitor [--gdb <port>]\n" +
                "       am hang [--allow-restart]\n" +
                "       am restart\n" +
                "       am idle-maintenance\n" +
                "       am screen-compat [on|off] <PACKAGE>\n" +
                "       am to-uri [INTENT]\n" +
                "       am to-intent-uri [INTENT]\n" +
                "       am switch-user <USER_ID>\n" +
                "       am stop-user <USER_ID>\n" +
                "       am stack create <TASK_ID> <RELATIVE_STACK_BOX_ID> <POSITION> <WEIGHT>\n" +
                "       am stack movetask <TASK_ID> <STACK_ID> [true|false]\n" +
                "       am stack resize <STACK_ID> <WEIGHT>\n" +
                "       am stack boxes\n" +
                "       am stack box <STACK_BOX_ID>\n" +
                "\n" +
                "am start: start an Activity.  Options are:\n" +
                "    -D: enable debugging\n" +
                "    -W: wait for launch to complete\n" +
                "    --start-profiler <FILE>: start profiler and send results to <FILE>\n" +
                "    -P <FILE>: like above, but profiling stops when app goes idle\n" +
                "    -R: repeat the activity launch <COUNT> times.  Prior to each repeat,\n" +
                "        the top activity will be finished.\n" +
                "    -S: force stop the target app before starting the activity\n" +
                "    --opengl-trace: enable tracing of OpenGL functions\n" +
                "    --user <USER_ID> | current: Specify which user to run as; if not\n" +
                "        specified then run as the current user.\n" +
                "\n" +
                "am startservice: start a Service.  Options are:\n" +
                "    --user <USER_ID> | current: Specify which user to run as; if not\n" +
                "        specified then run as the current user.\n" +
                "\n" +
                "am stopservice: stop a Service.  Options are:\n" +
                "    --user <USER_ID> | current: Specify which user to run as; if not\n" +
                "        specified then run as the current user.\n" +
                "\n" +
                "am force-stop: force stop everything associated with <PACKAGE>.\n" +
                "    --user <USER_ID> | all | current: Specify user to force stop;\n" +
                "        all users if not specified.\n" +
                "\n" +
                "am kill: Kill all processes associated with <PACKAGE>.  Only kills.\n" +
                "  processes that are safe to kill -- that is, will not impact the user\n" +
                "  experience.\n" +
                "    --user <USER_ID> | all | current: Specify user whose processes to kill;\n" +
                "        all users if not specified.\n" +
                "\n" +
                "am kill-all: Kill all background processes.\n" +
                "\n" +
                "am broadcast: send a broadcast Intent.  Options are:\n" +
                "    --user <USER_ID> | all | current: Specify which user to send to; if not\n" +
                "        specified then send to all users.\n" +
                "    --receiver-permission <PERMISSION>: Require receiver to hold permission.\n" +
                "\n" +
                "am instrument: start an Instrumentation.  Typically this target <COMPONENT>\n" +
                "  is the form <TEST_PACKAGE>/<RUNNER_CLASS>.  Options are:\n" +
                "    -r: print raw results (otherwise decode REPORT_KEY_STREAMRESULT).  Use with\n" +
                "        [-e perf true] to generate raw output for performance measurements.\n" +
                "    -e <NAME> <VALUE>: set argument <NAME> to <VALUE>.  For test runners a\n" +
                "        common form is [-e <testrunner_flag> <value>[,<value>...]].\n" +
                "    -p <FILE>: write profiling data to <FILE>\n" +
                "    -w: wait for instrumentation to finish before returning.  Required for\n" +
                "        test runners.\n" +
                "    --user <USER_ID> | current: Specify user instrumentation runs in;\n" +
                "        current user if not specified.\n" +
                "    --no-window-animation: turn off window animations while running.\n" +
                "\n" +
                "am profile: start and stop profiler on a process.  The given <PROCESS> argument\n" +
                "  may be either a process name or pid.  Options are:\n" +
                "    --user <USER_ID> | current: When supplying a process name,\n" +
                "        specify user of process to profile; uses current user if not specified.\n" +
                "\n" +
                "am dumpheap: dump the heap of a process.  The given <PROCESS> argument may\n" +
                "  be either a process name or pid.  Options are:\n" +
                "    -n: dump native heap instead of managed heap\n" +
                "    --user <USER_ID> | current: When supplying a process name,\n" +
                "        specify user of process to dump; uses current user if not specified.\n" +
                "\n" +
                "am set-debug-app: set application <PACKAGE> to debug.  Options are:\n" +
                "    -w: wait for debugger when application starts\n" +
                "    --persistent: retain this value\n" +
                "\n" +
                "am clear-debug-app: clear the previously set-debug-app.\n" +
                "\n" +
                "am bug-report: request bug report generation; will launch UI\n" +
                "    when done to select where it should be delivered.\n" +
                "\n" +
                "am monitor: start monitoring for crashes or ANRs.\n" +
                "    --gdb: start gdbserv on the given port at crash/ANR\n" +
                "\n" +
                "am hang: hang the system.\n" +
                "    --allow-restart: allow watchdog to perform normal system restart\n" +
                "\n" +
                "am restart: restart the user-space system.\n" +
                "\n" +
                "am idle-maintenance: perform idle maintenance now.\n" +
                "\n" +
                "am screen-compat: control screen compatibility mode of <PACKAGE>.\n" +
                "\n" +
                "am to-uri: print the given Intent specification as a URI.\n" +
                "\n" +
                "am to-intent-uri: print the given Intent specification as an intent: URI.\n" +
                "\n" +
                "am switch-user: switch to put USER_ID in the foreground, starting\n" +
                "  execution of that user if it is currently stopped.\n" +
                "\n" +
                "am stop-user: stop execution of USER_ID, not allowing it to run any\n" +
                "  code until a later explicit switch to it.\n" +
                "\n" +
                "am stack create: create a new stack relative to an existing one.\n" +
                "   <TASK_ID>: the task to populate the new stack with. Must exist.\n" +
                "   <RELATIVE_STACK_BOX_ID>: existing stack box's id.\n" +
                "   <POSITION>: 0: before <RELATIVE_STACK_BOX_ID>, per RTL/LTR configuration,\n" +
                "               1: after <RELATIVE_STACK_BOX_ID>, per RTL/LTR configuration,\n" +
                "               2: to left of <RELATIVE_STACK_BOX_ID>,\n" +
                "               3: to right of <RELATIVE_STACK_BOX_ID>," +
                "               4: above <RELATIVE_STACK_BOX_ID>, 5: below <RELATIVE_STACK_BOX_ID>\n" +
                "   <WEIGHT>: float between 0.2 and 0.8 inclusive.\n" +
                "\n" +
                "am stack movetask: move <TASK_ID> from its current stack to the top (true) or" +
                "   bottom (false) of <STACK_ID>.\n" +
                "\n" +
                "am stack resize: change <STACK_ID> relative size to new <WEIGHT>.\n" +
                "\n" +
                "am stack boxes: list the hierarchy of stack boxes and their contents.\n" +
                "\n" +
                "am stack box: list the hierarchy of stack boxes rooted at <STACK_BOX_ID>.\n" +
                "\n" +
                "<INTENT> specifications include these flags and arguments:\n" +
                "    [-a <ACTION>] [-d <DATA_URI>] [-t <MIME_TYPE>]\n" +
                "    [-c <CATEGORY> [-c <CATEGORY>] ...]\n" +
                "    [-e|--es <EXTRA_KEY> <EXTRA_STRING_VALUE> ...]\n" +
                "    [--esn <EXTRA_KEY> ...]\n" +
                "    [--ez <EXTRA_KEY> <EXTRA_BOOLEAN_VALUE> ...]\n" +
                "    [--ei <EXTRA_KEY> <EXTRA_INT_VALUE> ...]\n" +
                "    [--el <EXTRA_KEY> <EXTRA_LONG_VALUE> ...]\n" +
                "    [--ef <EXTRA_KEY> <EXTRA_FLOAT_VALUE> ...]\n" +
                "    [--eu <EXTRA_KEY> <EXTRA_URI_VALUE> ...]\n" +
                "    [--ecn <EXTRA_KEY> <EXTRA_COMPONENT_NAME_VALUE>]\n" +
                "    [--eia <EXTRA_KEY> <EXTRA_INT_VALUE>[,<EXTRA_INT_VALUE...]]\n" +
                "    [--ela <EXTRA_KEY> <EXTRA_LONG_VALUE>[,<EXTRA_LONG_VALUE...]]\n" +
                "    [--efa <EXTRA_KEY> <EXTRA_FLOAT_VALUE>[,<EXTRA_FLOAT_VALUE...]]\n" +
                "    [-n <COMPONENT>] [-f <FLAGS>]\n" +
                "    [--grant-read-uri-permission] [--grant-write-uri-permission]\n" +
                "    [--debug-log-resolution] [--exclude-stopped-packages]\n" +
                "    [--include-stopped-packages]\n" +
                "    [--activity-brought-to-front] [--activity-clear-top]\n" +
                "    [--activity-clear-when-task-reset] [--activity-exclude-from-recents]\n" +
                "    [--activity-launched-from-history] [--activity-multiple-task]\n" +
                "    [--activity-no-animation] [--activity-no-history]\n" +
                "    [--activity-no-user-action] [--activity-previous-is-top]\n" +
                "    [--activity-reorder-to-front] [--activity-reset-task-if-needed]\n" +
                "    [--activity-single-top] [--activity-clear-task]\n" +
                "    [--activity-task-on-home]\n" +
                "    [--receiver-registered-only] [--receiver-replace-pending]\n" +
                "    [--selector]\n" +
                "    [<URI> | <PACKAGE> | <COMPONENT>]\n"
                );
    }

    @Override
    public void onRun() throws Exception {

        mAm = ActivityManagerNative.getDefault();
        if (mAm == null) {
            System.err.println(NO_SYSTEM_ERROR_CODE);
            throw new AndroidException("Can't connect to activity manager; is the system running?");
        }

        String op = nextArgRequired();

        if (op.equals("start")) {
            runStart();
        } else if (op.equals("startservice")) {
            runStartService();
        } else if (op.equals("stopservice")) {
            runStopService();
        } else if (op.equals("force-stop")) {
            runForceStop();
        } else if (op.equals("kill")) {
            runKill();
        } else if (op.equals("kill-all")) {
            runKillAll();
        } else if (op.equals("instrument")) {
            runInstrument();
        } else if (op.equals("broadcast")) {
            sendBroadcast();
        } else if (op.equals("profile")) {
            runProfile();
        } else if (op.equals("dumpheap")) {
            runDumpHeap();
        } else if (op.equals("set-debug-app")) {
            runSetDebugApp();
        } else if (op.equals("clear-debug-app")) {
            runClearDebugApp();
        } else if (op.equals("bug-report")) {
            runBugReport();
        } else if (op.equals("monitor")) {
            runMonitor();
        } else if (op.equals("hang")) {
            runHang();
        } else if (op.equals("restart")) {
            runRestart();
        } else if (op.equals("idle-maintenance")) {
            runIdleMaintenance();
        } else if (op.equals("screen-compat")) {
            runScreenCompat();
        } else if (op.equals("to-uri")) {
            runToUri(false);
        } else if (op.equals("to-intent-uri")) {
            runToUri(true);
        } else if (op.equals("switch-user")) {
            runSwitchUser();
        } else if (op.equals("stop-user")) {
            runStopUser();
        } else if (op.equals("stack")) {
            runStack();
        } else {
            showError("Error: unknown command '" + op + "'");
        }
    }

    int parseUserArg(String arg) {
        int userId;
        if ("all".equals(arg)) {
            userId = UserHandle.USER_ALL;
        } else if ("current".equals(arg) || "cur".equals(arg)) {
            userId = UserHandle.USER_CURRENT;
        } else {
            userId = Integer.parseInt(arg);
        }
        return userId;
    }

    private Intent makeIntent(int defUser) throws URISyntaxException {
        Intent intent = new Intent();
        Intent baseIntent = intent;
        boolean hasIntentInfo = false;

        mStartFlags = 0;
        mWaitOption = false;
        mStopOption = false;
        mRepeat = 0;
        mProfileFile = null;
        mUserId = defUser;
        Uri data = null;
        String type = null;

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("-a")) {
                intent.setAction(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-d")) {
                data = Uri.parse(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-t")) {
                type = nextArgRequired();
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-c")) {
                intent.addCategory(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-e") || opt.equals("--es")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, value);
            } else if (opt.equals("--esn")) {
                String key = nextArgRequired();
                intent.putExtra(key, (String) null);
            } else if (opt.equals("--ei")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, Integer.valueOf(value));
            } else if (opt.equals("--eu")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, Uri.parse(value));
            } else if (opt.equals("--ecn")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                ComponentName cn = ComponentName.unflattenFromString(value);
                if (cn == null) throw new IllegalArgumentException("Bad component name: " + value);
                intent.putExtra(key, cn);
            } else if (opt.equals("--eia")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                String[] strings = value.split(",");
                int[] list = new int[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Integer.valueOf(strings[i]);
                }
                intent.putExtra(key, list);
            } else if (opt.equals("--el")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, Long.valueOf(value));
            } else if (opt.equals("--ela")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                String[] strings = value.split(",");
                long[] list = new long[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Long.valueOf(strings[i]);
                }
                intent.putExtra(key, list);
                hasIntentInfo = true;
            } else if (opt.equals("--ef")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, Float.valueOf(value));
                hasIntentInfo = true;
            } else if (opt.equals("--efa")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                String[] strings = value.split(",");
                float[] list = new float[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Float.valueOf(strings[i]);
                }
                intent.putExtra(key, list);
                hasIntentInfo = true;
            } else if (opt.equals("--ez")) {
                String key = nextArgRequired();
                String value = nextArgRequired();
                intent.putExtra(key, Boolean.valueOf(value));
            } else if (opt.equals("-n")) {
                String str = nextArgRequired();
                ComponentName cn = ComponentName.unflattenFromString(str);
                if (cn == null) throw new IllegalArgumentException("Bad component name: " + str);
                intent.setComponent(cn);
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-f")) {
                String str = nextArgRequired();
                intent.setFlags(Integer.decode(str).intValue());
            } else if (opt.equals("--grant-read-uri-permission")) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if (opt.equals("--grant-write-uri-permission")) {
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else if (opt.equals("--exclude-stopped-packages")) {
                intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
            } else if (opt.equals("--include-stopped-packages")) {
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            } else if (opt.equals("--debug-log-resolution")) {
                intent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
            } else if (opt.equals("--activity-brought-to-front")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            } else if (opt.equals("--activity-clear-top")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            } else if (opt.equals("--activity-clear-when-task-reset")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            } else if (opt.equals("--activity-exclude-from-recents")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            } else if (opt.equals("--activity-launched-from-history")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
            } else if (opt.equals("--activity-multiple-task")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            } else if (opt.equals("--activity-no-animation")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            } else if (opt.equals("--activity-no-history")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            } else if (opt.equals("--activity-no-user-action")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            } else if (opt.equals("--activity-previous-is-top")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
            } else if (opt.equals("--activity-reorder-to-front")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            } else if (opt.equals("--activity-reset-task-if-needed")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            } else if (opt.equals("--activity-single-top")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else if (opt.equals("--activity-clear-task")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            } else if (opt.equals("--activity-task-on-home")) {
                intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            } else if (opt.equals("--receiver-registered-only")) {
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            } else if (opt.equals("--receiver-replace-pending")) {
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            } else if (opt.equals("--selector")) {
                intent.setDataAndType(data, type);
                intent = new Intent();
            } else if (opt.equals("-D")) {
                mStartFlags |= ActivityManager.START_FLAG_DEBUG;
            } else if (opt.equals("-W")) {
                mWaitOption = true;
            } else if (opt.equals("-P")) {
                mProfileFile = nextArgRequired();
                mStartFlags |= ActivityManager.START_FLAG_AUTO_STOP_PROFILER;
            } else if (opt.equals("--start-profiler")) {
                mProfileFile = nextArgRequired();
                mStartFlags &= ~ActivityManager.START_FLAG_AUTO_STOP_PROFILER;
            } else if (opt.equals("-R")) {
                mRepeat = Integer.parseInt(nextArgRequired());
            } else if (opt.equals("-S")) {
                mStopOption = true;
            } else if (opt.equals("--opengl-trace")) {
                mStartFlags |= ActivityManager.START_FLAG_OPENGL_TRACES;
            } else if (opt.equals("--user")) {
                mUserId = parseUserArg(nextArgRequired());
            } else if (opt.equals("--receiver-permission")) {
                mReceiverPermission = nextArgRequired();
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return null;
            }
        }
        intent.setDataAndType(data, type);

        final boolean hasSelector = intent != baseIntent;
        if (hasSelector) {
            // A selector was specified; fix up.
            baseIntent.setSelector(intent);
            intent = baseIntent;
        }

        String arg = nextArg();
        baseIntent = null;
        if (arg == null) {
            if (hasSelector) {
                // If a selector has been specified, and no arguments
                // have been supplied for the main Intent, then we can
                // assume it is ACTION_MAIN CATEGORY_LAUNCHER; we don't
                // need to have a component name specified yet, the
                // selector will take care of that.
                baseIntent = new Intent(Intent.ACTION_MAIN);
                baseIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            }
        } else if (arg.indexOf(':') >= 0) {
            // The argument is a URI.  Fully parse it, and use that result
            // to fill in any data not specified so far.
            baseIntent = Intent.parseUri(arg, Intent.URI_INTENT_SCHEME);
        } else if (arg.indexOf('/') >= 0) {
            // The argument is a component name.  Build an Intent to launch
            // it.
            baseIntent = new Intent(Intent.ACTION_MAIN);
            baseIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            baseIntent.setComponent(ComponentName.unflattenFromString(arg));
        } else {
            // Assume the argument is a package name.
            baseIntent = new Intent(Intent.ACTION_MAIN);
            baseIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            baseIntent.setPackage(arg);
        }
        if (baseIntent != null) {
            Bundle extras = intent.getExtras();
            intent.replaceExtras((Bundle)null);
            Bundle uriExtras = baseIntent.getExtras();
            baseIntent.replaceExtras((Bundle)null);
            if (intent.getAction() != null && baseIntent.getCategories() != null) {
                HashSet<String> cats = new HashSet<String>(baseIntent.getCategories());
                for (String c : cats) {
                    baseIntent.removeCategory(c);
                }
            }
            intent.fillIn(baseIntent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_SELECTOR);
            if (extras == null) {
                extras = uriExtras;
            } else if (uriExtras != null) {
                uriExtras.putAll(extras);
                extras = uriExtras;
            }
            intent.replaceExtras(extras);
            hasIntentInfo = true;
        }

        if (!hasIntentInfo) throw new IllegalArgumentException("No intent supplied");
        return intent;
    }

    private void runStartService() throws Exception {
        Intent intent = makeIntent(UserHandle.USER_CURRENT);
        if (mUserId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't start activity with user 'all'");
            return;
        }
        System.out.println("Starting service: " + intent);
        ComponentName cn = mAm.startService(null, intent, intent.getType(), mUserId);
        if (cn == null) {
            System.err.println("Error: Not found; no service started.");
        } else if (cn.getPackageName().equals("!")) {
            System.err.println("Error: Requires permission " + cn.getClassName());
        } else if (cn.getPackageName().equals("!!")) {
            System.err.println("Error: " + cn.getClassName());
        }
    }

    private void runStopService() throws Exception {
        Intent intent = makeIntent(UserHandle.USER_CURRENT);
        if (mUserId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't stop activity with user 'all'");
            return;
        }
        System.out.println("Stopping service: " + intent);
        int result = mAm.stopService(null, intent, intent.getType(), mUserId);
        if (result == 0) {
            System.err.println("Service not stopped: was not running.");
        } else if (result == 1) {
            System.err.println("Service stopped");
        } else if (result == -1) {
            System.err.println("Error stopping service");
        }
    }

    private void runStart() throws Exception {
        Intent intent = makeIntent(UserHandle.USER_CURRENT);

        if (mUserId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't start service with user 'all'");
            return;
        }

        String mimeType = intent.getType();
        if (mimeType == null && intent.getData() != null
                && "content".equals(intent.getData().getScheme())) {
            mimeType = mAm.getProviderMimeType(intent.getData(), mUserId);
        }

        do {
            if (mStopOption) {
                String packageName;
                if (intent.getComponent() != null) {
                    packageName = intent.getComponent().getPackageName();
                } else {
                    IPackageManager pm = IPackageManager.Stub.asInterface(
                            ServiceManager.getService("package"));
                    if (pm == null) {
                        System.err.println("Error: Package manager not running; aborting");
                        return;
                    }
                    List<ResolveInfo> activities = pm.queryIntentActivities(intent, mimeType, 0,
                            mUserId);
                    if (activities == null || activities.size() <= 0) {
                        System.err.println("Error: Intent does not match any activities: "
                                + intent);
                        return;
                    } else if (activities.size() > 1) {
                        System.err.println("Error: Intent matches multiple activities; can't stop: "
                                + intent);
                        return;
                    }
                    packageName = activities.get(0).activityInfo.packageName;
                }
                System.out.println("Stopping: " + packageName);
                mAm.forceStopPackage(packageName, mUserId);
                Thread.sleep(250);
            }

            System.out.println("Starting: " + intent);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ParcelFileDescriptor fd = null;

            if (mProfileFile != null) {
                try {
                    fd = ParcelFileDescriptor.open(
                            new File(mProfileFile),
                            ParcelFileDescriptor.MODE_CREATE |
                            ParcelFileDescriptor.MODE_TRUNCATE |
                            ParcelFileDescriptor.MODE_READ_WRITE);
                } catch (FileNotFoundException e) {
                    System.err.println("Error: Unable to open file: " + mProfileFile);
                    return;
                }
            }

            IActivityManager.WaitResult result = null;
            int res;
            if (mWaitOption) {
                result = mAm.startActivityAndWait(null, null, intent, mimeType,
                            null, null, 0, mStartFlags, mProfileFile, fd, null, mUserId);
                res = result.result;
            } else {
                res = mAm.startActivityAsUser(null, null, intent, mimeType,
                        null, null, 0, mStartFlags, mProfileFile, fd, null, mUserId);
            }
            PrintStream out = mWaitOption ? System.out : System.err;
            boolean launched = false;
            switch (res) {
                case ActivityManager.START_SUCCESS:
                    launched = true;
                    break;
                case ActivityManager.START_SWITCHES_CANCELED:
                    launched = true;
                    out.println(
                            "Warning: Activity not started because the "
                            + " current activity is being kept for the user.");
                    break;
                case ActivityManager.START_DELIVERED_TO_TOP:
                    launched = true;
                    out.println(
                            "Warning: Activity not started, intent has "
                            + "been delivered to currently running "
                            + "top-most instance.");
                    break;
                case ActivityManager.START_RETURN_INTENT_TO_CALLER:
                    launched = true;
                    out.println(
                            "Warning: Activity not started because intent "
                            + "should be handled by the caller");
                    break;
                case ActivityManager.START_TASK_TO_FRONT:
                    launched = true;
                    out.println(
                            "Warning: Activity not started, its current "
                            + "task has been brought to the front");
                    break;
                case ActivityManager.START_INTENT_NOT_RESOLVED:
                    out.println(
                            "Error: Activity not started, unable to "
                            + "resolve " + intent.toString());
                    break;
                case ActivityManager.START_CLASS_NOT_FOUND:
                    out.println(NO_CLASS_ERROR_CODE);
                    out.println("Error: Activity class " +
                            intent.getComponent().toShortString()
                            + " does not exist.");
                    break;
                case ActivityManager.START_FORWARD_AND_REQUEST_CONFLICT:
                    out.println(
                            "Error: Activity not started, you requested to "
                            + "both forward and receive its result");
                    break;
                case ActivityManager.START_PERMISSION_DENIED:
                    out.println(
                            "Error: Activity not started, you do not "
                            + "have permission to access it.");
                    break;
                default:
                    out.println(
                            "Error: Activity not started, unknown error code " + res);
                    break;
            }
            if (mWaitOption && launched) {
                if (result == null) {
                    result = new IActivityManager.WaitResult();
                    result.who = intent.getComponent();
                }
                System.out.println("Status: " + (result.timeout ? "timeout" : "ok"));
                if (result.who != null) {
                    System.out.println("Activity: " + result.who.flattenToShortString());
                }
                if (result.thisTime >= 0) {
                    System.out.println("ThisTime: " + result.thisTime);
                }
                if (result.totalTime >= 0) {
                    System.out.println("TotalTime: " + result.totalTime);
                }
                System.out.println("Complete");
            }
            mRepeat--;
            if (mRepeat > 1) {
                mAm.unhandledBack();
            }
        } while (mRepeat > 1);
    }

    private void runForceStop() throws Exception {
        int userId = UserHandle.USER_ALL;

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
        mAm.forceStopPackage(nextArgRequired(), userId);
    }

    private void runKill() throws Exception {
        int userId = UserHandle.USER_ALL;

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
        mAm.killBackgroundProcesses(nextArgRequired(), userId);
    }

    private void runKillAll() throws Exception {
        mAm.killAllBackgroundProcesses();
    }

    private void sendBroadcast() throws Exception {
        Intent intent = makeIntent(UserHandle.USER_ALL);
        IntentReceiver receiver = new IntentReceiver();
        System.out.println("Broadcasting: " + intent);
        mAm.broadcastIntent(null, intent, null, receiver, 0, null, null, mReceiverPermission,
                android.app.AppOpsManager.OP_NONE, true, false, mUserId);
        receiver.waitForFinish();
    }

    private void runInstrument() throws Exception {
        String profileFile = null;
        boolean wait = false;
        boolean rawMode = false;
        boolean no_window_animation = false;
        int userId = UserHandle.USER_CURRENT;
        Bundle args = new Bundle();
        String argKey = null, argValue = null;
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("-p")) {
                profileFile = nextArgRequired();
            } else if (opt.equals("-w")) {
                wait = true;
            } else if (opt.equals("-r")) {
                rawMode = true;
            } else if (opt.equals("-e")) {
                argKey = nextArgRequired();
                argValue = nextArgRequired();
                args.putString(argKey, argValue);
            } else if (opt.equals("--no_window_animation")
                    || opt.equals("--no-window-animation")) {
                no_window_animation = true;
            } else if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }

        if (userId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't start instrumentation with user 'all'");
            return;
        }

        String cnArg = nextArgRequired();
        ComponentName cn = ComponentName.unflattenFromString(cnArg);
        if (cn == null) throw new IllegalArgumentException("Bad component name: " + cnArg);

        InstrumentationWatcher watcher = null;
        UiAutomationConnection connection = null;
        if (wait) {
            watcher = new InstrumentationWatcher();
            watcher.setRawOutput(rawMode);
            connection = new UiAutomationConnection();
        }

        float[] oldAnims = null;
        if (no_window_animation) {
            oldAnims = wm.getAnimationScales();
            wm.setAnimationScale(0, 0.0f);
            wm.setAnimationScale(1, 0.0f);
        }

        if (!mAm.startInstrumentation(cn, profileFile, 0, args, watcher, connection, userId)) {
            throw new AndroidException("INSTRUMENTATION_FAILED: " + cn.flattenToString());
        }

        if (watcher != null) {
            if (!watcher.waitForFinish()) {
                System.out.println("INSTRUMENTATION_ABORTED: System has crashed.");
            }
        }

        if (oldAnims != null) {
            wm.setAnimationScales(oldAnims);
        }
    }

    static void removeWallOption() {
        String props = SystemProperties.get("dalvik.vm.extra-opts");
        if (props != null && props.contains("-Xprofile:wallclock")) {
            props = props.replace("-Xprofile:wallclock", "");
            props = props.trim();
            SystemProperties.set("dalvik.vm.extra-opts", props);
        }
    }

    private void runProfile() throws Exception {
        String profileFile = null;
        boolean start = false;
        boolean wall = false;
        int userId = UserHandle.USER_CURRENT;
        int profileType = 0;

        String process = null;

        String cmd = nextArgRequired();

        if ("start".equals(cmd)) {
            start = true;
            String opt;
            while ((opt=nextOption()) != null) {
                if (opt.equals("--user")) {
                    userId = parseUserArg(nextArgRequired());
                } else if (opt.equals("--wall")) {
                    wall = true;
                } else {
                    System.err.println("Error: Unknown option: " + opt);
                    return;
                }
            }
            process = nextArgRequired();
        } else if ("stop".equals(cmd)) {
            String opt;
            while ((opt=nextOption()) != null) {
                if (opt.equals("--user")) {
                    userId = parseUserArg(nextArgRequired());
                } else {
                    System.err.println("Error: Unknown option: " + opt);
                    return;
                }
            }
            process = nextArg();
        } else {
            // Compatibility with old syntax: process is specified first.
            process = cmd;
            cmd = nextArgRequired();
            if ("start".equals(cmd)) {
                start = true;
            } else if (!"stop".equals(cmd)) {
                throw new IllegalArgumentException("Profile command " + process + " not valid");
            }
        }

        if (userId == UserHandle.USER_ALL) {
            System.err.println("Error: Can't profile with user 'all'");
            return;
        }

        ParcelFileDescriptor fd = null;

        if (start) {
            profileFile = nextArgRequired();
            try {
                fd = ParcelFileDescriptor.open(
                        new File(profileFile),
                        ParcelFileDescriptor.MODE_CREATE |
                        ParcelFileDescriptor.MODE_TRUNCATE |
                        ParcelFileDescriptor.MODE_READ_WRITE);
            } catch (FileNotFoundException e) {
                System.err.println("Error: Unable to open file: " + profileFile);
                return;
            }
        }

        try {
            if (wall) {
                // XXX doesn't work -- this needs to be set before booting.
                String props = SystemProperties.get("dalvik.vm.extra-opts");
                if (props == null || !props.contains("-Xprofile:wallclock")) {
                    props = props + " -Xprofile:wallclock";
                    //SystemProperties.set("dalvik.vm.extra-opts", props);
                }
            } else if (start) {
                //removeWallOption();
            }
            if (!mAm.profileControl(process, userId, start, profileFile, fd, profileType)) {
                wall = false;
                throw new AndroidException("PROFILE FAILED on process " + process);
            }
        } finally {
            if (!wall) {
                //removeWallOption();
            }
        }
    }

    private void runDumpHeap() throws Exception {
        boolean managed = true;
        int userId = UserHandle.USER_CURRENT;

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
                if (userId == UserHandle.USER_ALL) {
                    System.err.println("Error: Can't dump heap with user 'all'");
                    return;
                }
            } else if (opt.equals("-n")) {
                managed = false;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
        String process = nextArgRequired();
        String heapFile = nextArgRequired();
        ParcelFileDescriptor fd = null;

        try {
            File file = new File(heapFile);
            file.delete();
            fd = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_CREATE |
                    ParcelFileDescriptor.MODE_TRUNCATE |
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (FileNotFoundException e) {
            System.err.println("Error: Unable to open file: " + heapFile);
            return;
        }

        if (!mAm.dumpHeap(process, userId, managed, heapFile, fd)) {
            throw new AndroidException("HEAP DUMP FAILED on process " + process);
        }
    }

    private void runSetDebugApp() throws Exception {
        boolean wait = false;
        boolean persistent = false;

        String opt;
        while ((opt=nextOption()) != null) {
            if (opt.equals("-w")) {
                wait = true;
            } else if (opt.equals("--persistent")) {
                persistent = true;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }

        String pkg = nextArgRequired();
        mAm.setDebugApp(pkg, wait, persistent);
    }

    private void runClearDebugApp() throws Exception {
        mAm.setDebugApp(null, false, true);
    }

    private void runBugReport() throws Exception {
        mAm.requestBugReport();
        System.out.println("Your lovely bug report is being created; please be patient.");
    }

    private void runSwitchUser() throws Exception {
        String user = nextArgRequired();
        mAm.switchUser(Integer.parseInt(user));
    }

    private void runStopUser() throws Exception {
        String user = nextArgRequired();
        int res = mAm.stopUser(Integer.parseInt(user), null);
        if (res != ActivityManager.USER_OP_SUCCESS) {
            String txt = "";
            switch (res) {
                case ActivityManager.USER_OP_IS_CURRENT:
                    txt = " (Can't stop current user)";
                    break;
                case ActivityManager.USER_OP_UNKNOWN_USER:
                    txt = " (Unknown user " + user + ")";
                    break;
            }
            System.err.println("Switch failed: " + res + txt);
        }
    }

    class MyActivityController extends IActivityController.Stub {
        final String mGdbPort;

        static final int STATE_NORMAL = 0;
        static final int STATE_CRASHED = 1;
        static final int STATE_EARLY_ANR = 2;
        static final int STATE_ANR = 3;

        int mState;

        static final int RESULT_DEFAULT = 0;

        static final int RESULT_CRASH_DIALOG = 0;
        static final int RESULT_CRASH_KILL = 1;

        static final int RESULT_EARLY_ANR_CONTINUE = 0;
        static final int RESULT_EARLY_ANR_KILL = 1;

        static final int RESULT_ANR_DIALOG = 0;
        static final int RESULT_ANR_KILL = 1;
        static final int RESULT_ANR_WAIT = 1;

        int mResult;

        Process mGdbProcess;
        Thread mGdbThread;
        boolean mGotGdbPrint;

        MyActivityController(String gdbPort) {
            mGdbPort = gdbPort;
        }

        @Override
        public boolean activityResuming(String pkg) {
            synchronized (this) {
                System.out.println("** Activity resuming: " + pkg);
            }
            return true;
        }

        @Override
        public boolean activityStarting(Intent intent, String pkg) {
            synchronized (this) {
                System.out.println("** Activity starting: " + pkg);
            }
            return true;
        }

        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg,
                long timeMillis, String stackTrace) {
            synchronized (this) {
                System.out.println("** ERROR: PROCESS CRASHED");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("shortMsg: " + shortMsg);
                System.out.println("longMsg: " + longMsg);
                System.out.println("timeMillis: " + timeMillis);
                System.out.println("stack:");
                System.out.print(stackTrace);
                System.out.println("#");
                int result = waitControllerLocked(pid, STATE_CRASHED);
                return result == RESULT_CRASH_KILL ? false : true;
            }
        }

        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation) {
            synchronized (this) {
                System.out.println("** ERROR: EARLY PROCESS NOT RESPONDING");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("annotation: " + annotation);
                int result = waitControllerLocked(pid, STATE_EARLY_ANR);
                if (result == RESULT_EARLY_ANR_KILL) return -1;
                return 0;
            }
        }

        @Override
        public int appNotResponding(String processName, int pid, String processStats) {
            synchronized (this) {
                System.out.println("** ERROR: PROCESS NOT RESPONDING");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("processStats:");
                System.out.print(processStats);
                System.out.println("#");
                int result = waitControllerLocked(pid, STATE_ANR);
                if (result == RESULT_ANR_KILL) return -1;
                if (result == RESULT_ANR_WAIT) return 1;
                return 0;
            }
        }

        @Override
        public int systemNotResponding(String message) {
            synchronized (this) {
                System.out.println("** ERROR: PROCESS NOT RESPONDING");
                System.out.println("message: " + message);
                System.out.println("#");
                System.out.println("Allowing system to die.");
                return -1;
            }
        }

        void killGdbLocked() {
            mGotGdbPrint = false;
            if (mGdbProcess != null) {
                System.out.println("Stopping gdbserver");
                mGdbProcess.destroy();
                mGdbProcess = null;
            }
            if (mGdbThread != null) {
                mGdbThread.interrupt();
                mGdbThread = null;
            }
        }

        int waitControllerLocked(int pid, int state) {
            if (mGdbPort != null) {
                killGdbLocked();

                try {
                    System.out.println("Starting gdbserver on port " + mGdbPort);
                    System.out.println("Do the following:");
                    System.out.println("  adb forward tcp:" + mGdbPort + " tcp:" + mGdbPort);
                    System.out.println("  gdbclient app_process :" + mGdbPort);

                    mGdbProcess = Runtime.getRuntime().exec(new String[] {
                            "gdbserver", ":" + mGdbPort, "--attach", Integer.toString(pid)
                    });
                    final InputStreamReader converter = new InputStreamReader(
                            mGdbProcess.getInputStream());
                    mGdbThread = new Thread() {
                        @Override
                        public void run() {
                            BufferedReader in = new BufferedReader(converter);
                            String line;
                            int count = 0;
                            while (true) {
                                synchronized (MyActivityController.this) {
                                    if (mGdbThread == null) {
                                        return;
                                    }
                                    if (count == 2) {
                                        mGotGdbPrint = true;
                                        MyActivityController.this.notifyAll();
                                    }
                                }
                                try {
                                    line = in.readLine();
                                    if (line == null) {
                                        return;
                                    }
                                    System.out.println("GDB: " + line);
                                    count++;
                                } catch (IOException e) {
                                    return;
                                }
                            }
                        }
                    };
                    mGdbThread.start();

                    // Stupid waiting for .5s.  Doesn't matter if we end early.
                    try {
                        this.wait(500);
                    } catch (InterruptedException e) {
                    }

                } catch (IOException e) {
                    System.err.println("Failure starting gdbserver: " + e);
                    killGdbLocked();
                }
            }
            mState = state;
            System.out.println("");
            printMessageForState();

            while (mState != STATE_NORMAL) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

            killGdbLocked();

            return mResult;
        }

        void resumeController(int result) {
            synchronized (this) {
                mState = STATE_NORMAL;
                mResult = result;
                notifyAll();
            }
        }

        void printMessageForState() {
            switch (mState) {
                case STATE_NORMAL:
                    System.out.println("Monitoring activity manager...  available commands:");
                    break;
                case STATE_CRASHED:
                    System.out.println("Waiting after crash...  available commands:");
                    System.out.println("(c)ontinue: show crash dialog");
                    System.out.println("(k)ill: immediately kill app");
                    break;
                case STATE_EARLY_ANR:
                    System.out.println("Waiting after early ANR...  available commands:");
                    System.out.println("(c)ontinue: standard ANR processing");
                    System.out.println("(k)ill: immediately kill app");
                    break;
                case STATE_ANR:
                    System.out.println("Waiting after ANR...  available commands:");
                    System.out.println("(c)ontinue: show ANR dialog");
                    System.out.println("(k)ill: immediately kill app");
                    System.out.println("(w)ait: wait some more");
                    break;
            }
            System.out.println("(q)uit: finish monitoring");
        }

        void run() throws RemoteException {
            try {
                printMessageForState();

                mAm.setActivityController(this);
                mState = STATE_NORMAL;

                InputStreamReader converter = new InputStreamReader(System.in);
                BufferedReader in = new BufferedReader(converter);
                String line;

                while ((line = in.readLine()) != null) {
                    boolean addNewline = true;
                    if (line.length() <= 0) {
                        addNewline = false;
                    } else if ("q".equals(line) || "quit".equals(line)) {
                        resumeController(RESULT_DEFAULT);
                        break;
                    } else if (mState == STATE_CRASHED) {
                        if ("c".equals(line) || "continue".equals(line)) {
                            resumeController(RESULT_CRASH_DIALOG);
                        } else if ("k".equals(line) || "kill".equals(line)) {
                            resumeController(RESULT_CRASH_KILL);
                        } else {
                            System.out.println("Invalid command: " + line);
                        }
                    } else if (mState == STATE_ANR) {
                        if ("c".equals(line) || "continue".equals(line)) {
                            resumeController(RESULT_ANR_DIALOG);
                        } else if ("k".equals(line) || "kill".equals(line)) {
                            resumeController(RESULT_ANR_KILL);
                        } else if ("w".equals(line) || "wait".equals(line)) {
                            resumeController(RESULT_ANR_WAIT);
                        } else {
                            System.out.println("Invalid command: " + line);
                        }
                    } else if (mState == STATE_EARLY_ANR) {
                        if ("c".equals(line) || "continue".equals(line)) {
                            resumeController(RESULT_EARLY_ANR_CONTINUE);
                        } else if ("k".equals(line) || "kill".equals(line)) {
                            resumeController(RESULT_EARLY_ANR_KILL);
                        } else {
                            System.out.println("Invalid command: " + line);
                        }
                    } else {
                        System.out.println("Invalid command: " + line);
                    }

                    synchronized (this) {
                        if (addNewline) {
                            System.out.println("");
                        }
                        printMessageForState();
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mAm.setActivityController(null);
            }
        }
    }

    private void runMonitor() throws Exception {
        String opt;
        String gdbPort = null;
        while ((opt=nextOption()) != null) {
            if (opt.equals("--gdb")) {
                gdbPort = nextArgRequired();
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }

        MyActivityController controller = new MyActivityController(gdbPort);
        controller.run();
    }

    private void runHang() throws Exception {
        String opt;
        boolean allowRestart = false;
        while ((opt=nextOption()) != null) {
            if (opt.equals("--allow-restart")) {
                allowRestart = true;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }

        System.out.println("Hanging the system...");
        mAm.hang(new Binder(), allowRestart);
    }

    private void runRestart() throws Exception {
        String opt;
        while ((opt=nextOption()) != null) {
            System.err.println("Error: Unknown option: " + opt);
            return;
        }

        System.out.println("Restart the system...");
        mAm.restart();
    }

    private void runIdleMaintenance() throws Exception {
        String opt;
        while ((opt=nextOption()) != null) {
            System.err.println("Error: Unknown option: " + opt);
            return;
        }

        System.out.println("Performing idle maintenance...");
        Intent intent = new Intent(
                "com.android.server.IdleMaintenanceService.action.FORCE_IDLE_MAINTENANCE");
        mAm.broadcastIntent(null, intent, null, null, 0, null, null, null,
                android.app.AppOpsManager.OP_NONE, true, false, UserHandle.USER_ALL);
    }

    private void runScreenCompat() throws Exception {
        String mode = nextArgRequired();
        boolean enabled;
        if ("on".equals(mode)) {
            enabled = true;
        } else if ("off".equals(mode)) {
            enabled = false;
        } else {
            System.err.println("Error: enabled mode must be 'on' or 'off' at " + mode);
            return;
        }

        String packageName = nextArgRequired();
        do {
            try {
                mAm.setPackageScreenCompatMode(packageName, enabled
                        ? ActivityManager.COMPAT_MODE_ENABLED
                        : ActivityManager.COMPAT_MODE_DISABLED);
            } catch (RemoteException e) {
            }
            packageName = nextArg();
        } while (packageName != null);
    }

    private void runToUri(boolean intentScheme) throws Exception {
        Intent intent = makeIntent(UserHandle.USER_CURRENT);
        System.out.println(intent.toUri(intentScheme ? Intent.URI_INTENT_SCHEME : 0));
    }

    private class IntentReceiver extends IIntentReceiver.Stub {
        private boolean mFinished = false;

        @Override
        public void performReceive(Intent intent, int resultCode, String data, Bundle extras,
                boolean ordered, boolean sticky, int sendingUser) {
            String line = "Broadcast completed: result=" + resultCode;
            if (data != null) line = line + ", data=\"" + data + "\"";
            if (extras != null) line = line + ", extras: " + extras;
            System.out.println(line);
            synchronized (this) {
              mFinished = true;
              notifyAll();
            }
        }

        public synchronized void waitForFinish() {
            try {
                while (!mFinished) wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private class InstrumentationWatcher extends IInstrumentationWatcher.Stub {
        private boolean mFinished = false;
        private boolean mRawMode = false;

        /**
         * Set or reset "raw mode".  In "raw mode", all bundles are dumped.  In "pretty mode",
         * if a bundle includes Instrumentation.REPORT_KEY_STREAMRESULT, just print that.
         * @param rawMode true for raw mode, false for pretty mode.
         */
        public void setRawOutput(boolean rawMode) {
            mRawMode = rawMode;
        }

        @Override
        public void instrumentationStatus(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                // pretty printer mode?
                String pretty = null;
                if (!mRawMode && results != null) {
                    pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
                }
                if (pretty != null) {
                    System.out.print(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println(
                                    "INSTRUMENTATION_STATUS: " + key + "=" + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_STATUS_CODE: " + resultCode);
                }
                notifyAll();
            }
        }

        @Override
        public void instrumentationFinished(ComponentName name, int resultCode,
                Bundle results) {
            synchronized (this) {
                // pretty printer mode?
                String pretty = null;
                if (!mRawMode && results != null) {
                    pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
                }
                if (pretty != null) {
                    System.out.println(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println(
                                    "INSTRUMENTATION_RESULT: " + key + "=" + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_CODE: " + resultCode);
                }
                mFinished = true;
                notifyAll();
            }
        }

        public boolean waitForFinish() {
            synchronized (this) {
                while (!mFinished) {
                    try {
                        if (!mAm.asBinder().pingBinder()) {
                            return false;
                        }
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return true;
        }
    }

    private void runStack() throws Exception {
        String op = nextArgRequired();
        if (op.equals("create")) {
            runStackCreate();
        } else if (op.equals("movetask")) {
            runStackMoveTask();
        } else if (op.equals("resize")) {
            runStackBoxResize();
        } else if (op.equals("boxes")) {
            runStackBoxes();
        } else if (op.equals("box")) {
            runStackBoxInfo();
        } else {
            showError("Error: unknown command '" + op + "'");
            return;
        }
    }

    private void runStackCreate() throws Exception {
        String taskIdStr = nextArgRequired();
        int taskId = Integer.valueOf(taskIdStr);
        String relativeToStr = nextArgRequired();
        int relativeTo = Integer.valueOf(relativeToStr);
        String positionStr = nextArgRequired();
        int position = Integer.valueOf(positionStr);
        String weightStr = nextArgRequired();
        float weight = Float.valueOf(weightStr);

        try {
            int stackId = mAm.createStack(taskId, relativeTo, position, weight);
            System.out.println("createStack returned new stackId=" + stackId + "\n\n");
        } catch (RemoteException e) {
        }
    }

    private void runStackMoveTask() throws Exception {
        String taskIdStr = nextArgRequired();
        int taskId = Integer.valueOf(taskIdStr);
        String stackIdStr = nextArgRequired();
        int stackId = Integer.valueOf(stackIdStr);
        String toTopStr = nextArgRequired();
        final boolean toTop;
        if ("true".equals(toTopStr)) {
            toTop = true;
        } else if ("false".equals(toTopStr)) {
            toTop = false;
        } else {
            System.err.println("Error: bad toTop arg: " + toTopStr);
            return;
        }

        try {
            mAm.moveTaskToStack(taskId, stackId, toTop);
        } catch (RemoteException e) {
        }
    }

    private void runStackBoxResize() throws Exception {
        String stackBoxIdStr = nextArgRequired();
        int stackBoxId = Integer.valueOf(stackBoxIdStr);
        String weightStr = nextArgRequired();
        float weight = Float.valueOf(weightStr);

        try {
            mAm.resizeStackBox(stackBoxId, weight);
        } catch (RemoteException e) {
        }
    }

    private void runStackBoxes() throws Exception {
        try {
            List<StackBoxInfo> stackBoxes = mAm.getStackBoxes();
            for (StackBoxInfo info : stackBoxes) {
                System.out.println(info);
            }
        } catch (RemoteException e) {
        }
    }

    private void runStackBoxInfo() throws Exception {
        try {
            String stackBoxIdStr = nextArgRequired();
            int stackBoxId = Integer.valueOf(stackBoxIdStr);
            StackBoxInfo stackBoxInfo = mAm.getStackBoxInfo(stackBoxId);
            System.out.println(stackBoxInfo);
        } catch (RemoteException e) {
        }
    }
}
