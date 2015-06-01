/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.qs.DataUsageGraph;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.NetworkController;


import java.text.DecimalFormat;

/**
 * Layout for the data usage detail in quick settings.
 */
public class DataUsageDetailView extends LinearLayout {

    private static final double KB = 1024;
    private static final double MB = 1024 * KB;
    private static final double GB = 1024 * MB;

    private final DecimalFormat FORMAT = new DecimalFormat("#.##");

    public DataUsageDetailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(this, android.R.id.title, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_text, R.dimen.qs_data_usage_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_carrier_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_top_text,
                R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_period_text, R.dimen.qs_data_usage_text_size);
        FontSizeUtils.updateFontSize(this, R.id.usage_info_bottom_text,
                R.dimen.qs_data_usage_text_size);
    }

    public void bind(QSTile.Host host, NetworkController.MobileDataController.DataUsageInfo info) {
        final QSTile.Host mHost = host;
        final Resources res = mContext.getResources();
        final int titleId;
        final long bytes;
        int usageColor = R.color.system_accent_color;
        final String top;
        String bottom = null;
        if (info.usageLevel < info.warningLevel || info.limitLevel <= 0) {
            // under warning, or no limit
            titleId = R.string.quick_settings_cellular_detail_data_usage;
            bytes = info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_warning,
                    formatBytes(info.warningLevel));
        } else if (info.usageLevel <= info.limitLevel) {
            // over warning, under limit
            titleId = R.string.quick_settings_cellular_detail_remaining_data;
            bytes = info.limitLevel - info.usageLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
        } else {
            // over limit
            titleId = R.string.quick_settings_cellular_detail_over_limit;
            bytes = info.usageLevel - info.limitLevel;
            top = res.getString(R.string.quick_settings_cellular_detail_data_used,
                    formatBytes(info.usageLevel));
            bottom = res.getString(R.string.quick_settings_cellular_detail_data_limit,
                    formatBytes(info.limitLevel));
            usageColor = R.color.system_warning_color;
        }

        final TextView title = (TextView) findViewById(android.R.id.title);
        title.setText(titleId);
        final TextView usage = (TextView) findViewById(R.id.usage_text);
        usage.setText(formatBytes(bytes));
        usage.setTextColor(res.getColor(usageColor));
        final DataUsageGraph graph = (DataUsageGraph) findViewById(R.id.usage_graph);
        graph.setLevels(info.limitLevel, info.warningLevel, info.usageLevel);
        final Button carrier = (Button) findViewById(R.id.usage_carrier_text);
        carrier.setText(info.carrier);
        carrier.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                // Use NetworkSetting to handle the selection intent
                intent.setComponent(new ComponentName("com.android.phone",
                        "com.android.phone.NetworkSetting"));
                mHost.startSettingsActivity(intent);
            }
        });
        final TextView period = (TextView) findViewById(R.id.usage_period_text);
        period.setText(info.period);
        final TextView infoTop = (TextView) findViewById(R.id.usage_info_top_text);
        infoTop.setVisibility(top != null ? View.VISIBLE : View.GONE);
        infoTop.setText(top);
        final TextView infoBottom = (TextView) findViewById(R.id.usage_info_bottom_text);
        infoBottom.setVisibility(bottom != null ? View.VISIBLE : View.GONE);
        infoBottom.setText(bottom);
    }

    private String formatBytes(long bytes) {
        final long b = Math.abs(bytes);
        double val;
        String suffix;
        if (b > 100 * MB) {
            val = b / GB;
            suffix = "GB";
        } else if (b > 100 * KB) {
            val = b / MB;
            suffix = "MB";
        } else {
            val = b / KB;
            suffix = "KB";
        }
        return FORMAT.format(val * (bytes < 0 ? -1 : 1)) + " " + suffix;
    }
}
