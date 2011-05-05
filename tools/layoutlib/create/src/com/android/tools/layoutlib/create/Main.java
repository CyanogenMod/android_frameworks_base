/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.tools.layoutlib.create;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;


/**
 * Entry point for the layoutlib_create tool.
 * <p/>
 * The tool does not currently rely on any external configuration file.
 * Instead the configuration is mostly done via the {@link CreateInfo} class.
 * <p/>
 * For a complete description of the tool and its implementation, please refer to
 * the "README.txt" file at the root of this project.
 * <p/>
 * For a quick test, invoke this as follows:
 * <pre>
 * $ make layoutlib
 * </pre>
 * which does:
 * <pre>
 * $ make layoutlib_create &lt;bunch of framework jars&gt;
 * $ out/host/linux-x86/framework/bin/layoutlib_create \
 *        out/host/common/obj/JAVA_LIBRARIES/temp_layoutlib_intermediates/javalib.jar \
 *        out/target/common/obj/JAVA_LIBRARIES/core_intermediates/classes.jar \
 *        out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar
 * </pre>
 */
public class Main {

    public static void main(String[] args) {

        Log log = new Log();

        ArrayList<String> osJarPath = new ArrayList<String>();
        String[] osDestJar = { null };

        if (!processArgs(log, args, osJarPath, osDestJar)) {
            log.error("Usage: layoutlib_create [-v] output.jar input.jar ...");
            System.exit(1);
        }

        log.info("Output: %1$s", osDestJar[0]);
        for (String path : osJarPath) {
            log.info("Input :      %1$s", path);
        }

        try {
            AsmGenerator agen = new AsmGenerator(log, osDestJar[0], new CreateInfo());

            AsmAnalyzer aa = new AsmAnalyzer(log, osJarPath, agen,
                    new String[] {                          // derived from
                        "android.view.View",
                    },
                    new String[] {                          // include classes
                        "android.*", // for android.R
                        "android.util.*",
                        "com.android.internal.util.*",
                        "android.view.*",
                        "android.widget.*",
                        "com.android.internal.widget.*",
                        "android.text.**",
                        "android.graphics.*",
                        "android.graphics.drawable.*",
                        "android.content.*",
                        "android.content.res.*",
                        "org.apache.harmony.xml.*",
                        "com.android.internal.R**",
                        "android.pim.*", // for datepicker
                        "android.os.*",  // for android.os.Handler
                        "android.database.ContentObserver", // for Digital clock
                        });
            aa.analyze();
            agen.generate();

            // Throw an error if any class failed to get renamed by the generator
            //
            // IMPORTANT: if you're building the platform and you get this error message,
            // it means the renameClasses[] array in AsmGenerator needs to be updated: some
            // class should have been renamed but it was not found in the input JAR files.
            Set<String> notRenamed = agen.getClassesNotRenamed();
            if (notRenamed.size() > 0) {
                // (80-column guide below for error formatting)
                // 01234567890123456789012345678901234567890123456789012345678901234567890123456789
                log.error(
                  "ERROR when running layoutlib_create: the following classes are referenced\n" +
                  "by tools/layoutlib/create but were not actually found in the input JAR files.\n" +
                  "This may be due to some platform classes having been renamed.");
                for (String fqcn : notRenamed) {
                    log.error("- Class not found: %s", fqcn.replace('/', '.'));
                }
                for (String path : osJarPath) {
                    log.info("- Input JAR : %1$s", path);
                }
                System.exit(1);
            }

            System.exit(0);
        } catch (IOException e) {
            log.exception(e, "Failed to load jar");
        } catch (LogAbortException e) {
            e.error(log);
        }

        System.exit(1);
    }

    /**
     * Returns true if args where properly parsed.
     * Returns false if program should exit with command-line usage.
     * <p/>
     * Note: the String[0] is an output parameter wrapped in an array, since there is no
     * "out" parameter support.
     */
    private static boolean processArgs(Log log, String[] args,
            ArrayList<String> osJarPath, String[] osDestJar) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("-v")) {
                log.setVerbose(true);
            } else if (!s.startsWith("-")) {
                if (osDestJar[0] == null) {
                    osDestJar[0] = s;
                } else {
                    osJarPath.add(s);
                }
            } else {
                log.error("Unknow argument: %s", s);
                return false;
            }
        }

        if (osJarPath.isEmpty()) {
            log.error("Missing parameter: path to input jar");
            return false;
        }
        if (osDestJar[0] == null) {
            log.error("Missing parameter: path to output jar");
            return false;
        }

        return true;
    }

}
