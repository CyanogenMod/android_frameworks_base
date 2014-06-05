/*
 * Copyright (C) 2013 ParanoidAndroid Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.provider.Settings;

import com.android.internal.R;

public class SettingConfirmationHelper {

    private static final int NOT_SET = 0;
    private static final int ENABLED = 1;
    private static final int DISABLED = 2;
    private static final int ASK_LATER = 3;

    private int mCurrentStatus;
    private Context mContext;

    public SettingConfirmationHelper(Context context) {
        mContext = context;
    }

    public void showConfirmationDialogForSetting(String title, String msg, Drawable hint, final String setting) {
        mCurrentStatus = Settings.System.getInt(mContext.getContentResolver(), setting, NOT_SET);
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        View dialogLayout = layoutInflater.inflate(R.layout.setting_confirmation_dialog, null);
        final ImageView visualHint = (ImageView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_visual_hint);
        visualHint.setImageDrawable(hint);
        builder.setView(dialogLayout, 10, 10, 10, 20);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.setting_confirmation_yes,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCurrentStatus = ENABLED;
                Settings.System.putInt(mContext.getContentResolver(), setting, mCurrentStatus);
            }
        });
        builder.setNeutralButton(R.string.setting_confirmation_ask_me_later,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCurrentStatus = ASK_LATER;
                Settings.System.putInt(mContext.getContentResolver(), setting, mCurrentStatus);
            }
        });
        builder.setNegativeButton(R.string.setting_confirmation_no,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCurrentStatus = DISABLED;
                Settings.System.putInt(mContext.getContentResolver(), setting, mCurrentStatus);
            }
        });
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);

        if(mCurrentStatus == NOT_SET || mCurrentStatus == ASK_LATER) {
            dialog.show();
        }
    }

}