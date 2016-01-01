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
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.tuner.TunerService;

public class QSSettings extends FrameLayout {
    private QSTileHost mHost;

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

        LinearLayout tunerSwitchRow = (LinearLayout) findViewById(R.id.tuner_switch_row);
        TextView title = (TextView) tunerSwitchRow.findViewById(R.id.title);
        final Switch tunerEnabled = (Switch) tunerSwitchRow.findViewById(R.id.switcher);
        title.setText(R.string.system_ui_tuner);
        tunerEnabled.setChecked(TunerService.isTunerEnabled(mContext));
        tunerSwitchRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tunerEnabled.setChecked(!tunerEnabled.isChecked());
            }
        });
        tunerEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TunerService.setTunerEnabled(buttonView.getContext(), isChecked);
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        getParent().requestDisallowInterceptTouchEvent(true);
        return super.onInterceptTouchEvent(ev);
    }

    private void initiateTileReset() {
        final AlertDialog d = new AlertDialog.Builder(mContext)
                .setMessage(R.string.qs_tiles_reset_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(com.android.internal.R.string.reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mHost.resetTiles();
                            }
                        }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    public void setHost(QSTileHost host) {
        mHost = host;
    }
}
