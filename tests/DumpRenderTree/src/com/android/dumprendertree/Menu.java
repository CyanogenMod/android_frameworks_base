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

package com.android.dumprendertree;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class Menu extends FileList {

    private static final int MENU_START = 0x01;
    private static String LOGTAG = "MenuActivity";
    static final String LAYOUT_TESTS_LIST_FILE = "/sdcard/android/layout_tests_list.txt";

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    boolean fileFilter(File f) {
        if (f.getName().startsWith("."))
                return false;
        if (f.getName().equalsIgnoreCase("resources"))
                return false;
        if (f.isDirectory())
                return true;
        if (f.getPath().toLowerCase().endsWith("ml"))
                return true;
        return false;
    }

    void processFile(String filename, boolean selection) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, TestShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(TestShellActivity.TEST_URL, "file://" + filename);
        startActivity(intent);
    }

    @Override
    void processDirectory(String path, boolean selection) {
        generateTestList(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, TestShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(TestShellActivity.UI_AUTO_TEST, LAYOUT_TESTS_LIST_FILE);
        startActivity(intent);
    }

    private void generateTestList(String path) {
        try {
            File tests_list = new File(LAYOUT_TESTS_LIST_FILE);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tests_list, false));
            FsUtils.findLayoutTestsRecursively(bos, path, false); // Don't ignore results
            bos.flush();
            bos.close();
       } catch (Exception e) {
           Log.e(LOGTAG, "Error when creating test list: " + e.getMessage());
       }
    }

}
