/*
 * Copyright (C) 2013-2014 Gwon Hyeok for Dokdo Project
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

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class InternalMemory extends QuickSettingsTile {
    private final String TAG = "MemoryTile";

    public InternalMemory(Context context, QuickSettingsController qsc) {
        super(context, qsc);
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
        		updateTile();
            }
        };
    }

    private void updateTile() {
        mDrawable = R.drawable.ic_qs_internalmemory;
        mLabel = mContext.getString(R.string.quick_settings_internal_memory);
	    String mUse;
	    String mTotal;
        mTotal = formatSize(getTotalInternalMemorySize());
	    mUse = formatSize(getInternalMemorySize());
	    TextView tv = (TextView) mTile.findViewById(R.id.text);
        ImageView iv = (ImageView)mTile.findViewById(R.id.image);
	    tv.setText(mUse+"/"+mTotal);
	    iv.setImageResource(mDrawable);
    }

    private long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    private long getInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    private long getTotalExternalMemorySize() {
        if (isStorage(true) == true) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();

            return totalBlocks * blockSize;
        } else {
            return -1;
        }
    }

    private long getExternalMemorySize() {
        if (isStorage(true) == true) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } else {
            return -1;
        }
    }

    private String formatSize(long size) {
        String suffix = null;

        if (size >= 1024) {
            suffix = "KB";
            size /= 1024;
            if (size >= 1024) {
                suffix = "MB";
                size /= 1024;
            }
        }
        StringBuilder resultBuffer = new StringBuilder(Long.toString(size));

        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }
  
        if (suffix != null) {
            resultBuffer.append(suffix);
        }
        return resultBuffer.toString();
    }

    private boolean isStorage(boolean requireWriteAccess) {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        } else if (!requireWriteAccess &&
            Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
     }

    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    void updateQuickSettings() {
        mDrawable = R.drawable.ic_qs_internalmemory;
        mLabel = mContext.getString(R.string.quick_settings_internal_memory);
        String mUse;
        String mTotal;
        mTotal = formatSize(getTotalInternalMemorySize());
        mUse = formatSize(getInternalMemorySize());
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        ImageView iv = (ImageView)mTile.findViewById(R.id.image);
        tv.setText(mUse+"/"+mTotal);
        iv.setImageResource(mDrawable);
    }
}
