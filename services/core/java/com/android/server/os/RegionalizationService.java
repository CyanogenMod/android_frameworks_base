/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server.os;

import android.text.TextUtils;
import android.util.Log;

import com.android.internal.os.IRegionalizationService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * The implementation of the regionalization service interface.
 *
 * @hide
 */
public class RegionalizationService extends IRegionalizationService.Stub {

    private static final String TAG = "RegionalizationService";

    public RegionalizationService() {
    }

    @Override
    public boolean checkFileExists(String filepath) {
        File file = new File(filepath);
        if (file == null || !file.exists())
            return false;

        return true;
    }

    @Override
    public ArrayList<String> readFile(String filepath, String regularExpression) {
        File file = new File(filepath);
        if (file == null || !file.exists() || !file.canRead()) return null;


        ArrayList<String> contents = new ArrayList<String>();

        FileReader fr = null;
        BufferedReader br = null;
        try {
            fr = new FileReader(file);
            br = new BufferedReader(fr);
            String line = null;
            while ((line = br.readLine()) != null && (line = line.trim()) != null) {
                if (!TextUtils.isEmpty(regularExpression)) {
                    if (line.matches(regularExpression)) {
                        contents.add(line);
                    }
                } else {
                    contents.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Read File error, caused by: " + e.getMessage());
        } finally {
            try {
                if (br != null) br.close();
                if (fr != null) fr.close();
            } catch (IOException e) {
                Log.e(TAG, "Close the reader error, caused by: " + e.getMessage());
            }
        }

        return contents;
    }

    @Override
    public boolean writeFile(String filepath, String content, boolean append) {
        File file = new File(filepath);
        if (file == null || !file.exists() || !file.canWrite()) return false;

        if (TextUtils.isEmpty(content)) return false;

        // Write the file with the content.
        FileWriter fw = null;
        try {
            fw = new FileWriter(file, append);
            fw.write(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fw != null) try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    @Override
    public void deleteFilesUnderDir(String dirPath, String ext, boolean delDir) {
        File file = new File(dirPath);
        if (file == null || !file.exists()) return;

        deleteFiles(file, ext, delDir);
    }

    // Delete all files under this folder and its subfolders
    private void deleteFiles(File dir, String ext, boolean delDir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null) return;

            for (int i = 0; i < children.length; i++) {
                deleteFiles(new File(dir, children[i]), ext, delDir);
            }
            if (delDir) {
                dir.delete();
            }
        } else {
            if (dir.isFile() && (ext.isEmpty() || dir.getName().endsWith(ext))) {
                dir.delete();
            }
        }
    }
}
