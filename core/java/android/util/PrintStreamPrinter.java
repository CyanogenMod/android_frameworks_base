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

package android.util;

import java.io.PrintStream;

/**
 * Implementation of a {@link android.util.Printer} that sends its output
 * to a {@link java.io.PrintStream}.
 */
public class PrintStreamPrinter implements Printer {
    private final PrintStream mPS;

    /**
     * Create a new Printer that sends to a PrintWriter object.
     *
     * @param pw The PrintWriter where you would like output to go.
     */
    public PrintStreamPrinter(PrintStream pw) {
        mPS = pw;
    }

    public void println(String x) {
        mPS.println(x);
    }
}
