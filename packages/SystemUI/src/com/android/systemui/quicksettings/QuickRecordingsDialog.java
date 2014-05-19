/*
 * Copyright 2014 SlimRom
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

package com.android.systemui.quicksettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

import com.android.systemui.R;

public class QuickRecordingsDialog extends Activity  {

    private static final String PLAY_RECORDING =
            "com.android.systemui.quicksettings.PLAY_RECORDING";
    private static final String RECORDING_NAME = "QuickRecord ";
    private static final String RECORDING_TYPE = ".3gp";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        final File[] recordings = currentRecordings();
        String[] dialogEntries = new String[recordings.length];
        for (int i = 0; i < recordings.length; i++) {
            String[] split = recordings[i].toString().split("\\|");
            if (split.length > 1) {
                dialogEntries[i] = split[1];
            } else {
                dialogEntries[i] = split[0];
            }
        }

        ListView list = new ListView(this);
        list.setAdapter(new ArrayAdapter<String> (this,
                android.R.layout.select_dialog_item, dialogEntries));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View view, int item, long id) {
                Intent intent = new Intent(PLAY_RECORDING);
                intent.putExtra("file", recordings[item].getAbsolutePath());
                QuickRecordingsDialog.this.sendBroadcast(intent);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> arg0, View view, int item, long id) {
                recordings[item].delete();
                Intent intent = new Intent(PLAY_RECORDING);
                QuickRecordingsDialog.this.sendBroadcast(intent);
                QuickRecordingsDialog.this.finish();
                return true;
            }
        });
        AlertDialog.Builder action = new AlertDialog.Builder(this);
        action.setTitle(R.string.quick_settings_quick_record_dialog)
        .setView(list)
        .setNegativeButton(R.string.quick_settings_quick_record_dialog_cancel,
            new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                QuickRecordingsDialog.this.finish();
            }
        });
        AlertDialog alert = action.create();
        alert.setCanceledOnTouchOutside(false);
        alert.setCancelable(false);
        alert.show();
    }

    private File[] currentRecordings() {
        File location = new File(this.getFilesDir().toString());
        File[] files = location.listFiles(new FilenameFilter() {
            public boolean accept(File directory, String file) {
                return file.startsWith(RECORDING_NAME)
                        && file.endsWith(RECORDING_TYPE);
            }
        });

        Arrays.sort(files, new Comparator<File>(){
            public int compare(File f1, File f2) {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            }
        });
        return files;
    }

}
