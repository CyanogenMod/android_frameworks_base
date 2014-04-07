/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

public class Helpers {

    public static final String MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
    public static final String TEGRA_MAX_FREQ_PATH = "/sys/module/cpu_tegra/parameters/cpu_user_cap";
    public static final String STEPS_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies";
    public static final String DYN_MAX_FREQ_PATH = "/sys/power/cpufreq_max_limit";
    public static final String DYN_MIN_FREQ_PATH = "/sys/power/cpufreq_min_limit";
    public static final String NUM_OF_CPUS_PATH = "/sys/devices/system/cpu/present";

    /**
     * Read one line from file
     *
     * @param fname
     * @return line
     */
    public static String readOneLine(String fname) {
        String line = null;
        if (new File(fname).exists()) {
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader(fname), 512);
                try {
                    line = br.readLine();
                } finally {
                    br.close();
                }
            } catch (Exception e) {
                return null;
            }
        }
        return line;
    }

    /**
     * Write one line to a file
     *
     * @param fname
     * @param value
     * @return if line was written
     */
    public static boolean writeOneLine(String fname, String value) {
        if (!new File(fname).exists()) {
            return false;
        }
        try {
            FileWriter fw = new FileWriter(fname);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Get total number of cpus
     *
     * @return total number of cpus
     */
    public static int getNumOfCpus() {
        int numOfCpu = 1;
        String numOfCpus = readOneLine(NUM_OF_CPUS_PATH);
        String[] cpuCount = numOfCpus.split("-");
        if (cpuCount.length > 1) {
            try {
                int cpuStart = Integer.parseInt(cpuCount[0]);
                int cpuEnd = Integer.parseInt(cpuCount[1]);

                numOfCpu = cpuEnd - cpuStart + 1;

                if (numOfCpu < 0)
                    numOfCpu = 1;
            } catch (NumberFormatException ex) {
                numOfCpu = 1;
            }
        }
        return numOfCpu;
    }

    /**
     * Convert to MHz and append a tag
     *
     * @param mhzString
     * @return tagged and converted String
     */
    public static String toMHz(String mhzString) {
        if (mhzString == null) {
            return "Unknown";
        }
        return String.valueOf(Integer.parseInt(mhzString) / 1000) + " MHz";
    }

    public static boolean fileExists(String fname) {
        return new File(fname).exists();
    }

}
