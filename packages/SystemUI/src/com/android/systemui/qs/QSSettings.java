/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.systemui.qs;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.phone.SystemUIDialog;

public class QSSettings extends LinearLayout {
    private QSTileHost mHost;

    private boolean mAdapterEditingState;

    public QSSettings(Context context) {
        super(context);
    }

    public QSSettings(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        findViewById(R.id.reset_tiles).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                initiateTileReset();
            }
        });
    }

    private void initiateTileReset() {
        final AlertDialog d = new AlertDialog.Builder(mContext)
                .setMessage(R.string.qs_tiles_reset_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(com.android.internal.R.string.reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mHost.initiateReset();
                            }
                        }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    public void setHost(QSTileHost host) {
        mHost = host;
    }

    public boolean getAdapterEditingState() {
        return mAdapterEditingState;
    }

    public void setAdapterEditingState(boolean editing) {
        this.mAdapterEditingState = editing;
    }
}
