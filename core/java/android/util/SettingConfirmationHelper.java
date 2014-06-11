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
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.R;

/**
 * Hide from public API
 * @hide
 */
public class SettingConfirmationHelper {

    private static final int NOT_SET = 0;
    private static final int ENABLED = 1;
    private static final int DISABLED = 2;
    private static final int ASK_LATER = 3;

    /**
     * Hide from public API
     * @hide
     */
    public static interface OnSelectListener {
        void onSelect(boolean enabled);
    }

    /**
     * Hide from public API
     * @hide
     */
    public static void showConfirmationDialogForSetting(final Context mContext, String title, String msg, Drawable hint,
                                                        final String setting, final OnSelectListener mListener) {
        int mCurrentStatus = Settings.System.getInt(mContext.getContentResolver(), setting, NOT_SET);
        if (mCurrentStatus == ENABLED || mCurrentStatus == DISABLED) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        View dialogLayout = layoutInflater.inflate(R.layout.setting_confirmation_dialog, null);
        final ImageView visualHint = (ImageView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_visual_hint);
        visualHint.setImageDrawable(hint);
        final TextView resetHintTitle =  (TextView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_how_to_reset_hint_title);
        resetHintTitle.setText(mContext.getString(R.string.setting_reset_hint_title));
        final TextView resetHintMessage =  (TextView)
                dialogLayout.findViewById(R.id.setting_confirmation_dialog_how_to_reset_hint_message);
        Spanned formattedResetHintMessageText = Html.fromHtml(mContext.getString(R.string.setting_reset_hint_message));
        resetHintMessage.setText(formattedResetHintMessageText);
        builder.setView(dialogLayout, 10, 10, 10, 10);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.setting_confirmation_yes,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putInt(mContext.getContentResolver(), setting, ENABLED);
                        if (mListener == null) return;
                        mListener.onSelect(true);
                    }
                }
        );
        builder.setNeutralButton(R.string.setting_confirmation_ask_me_later,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putInt(mContext.getContentResolver(), setting, ASK_LATER);
                        if (mListener == null) return;
                        mListener.onSelect(false);
                    }
                }
        );
        builder.setNegativeButton(R.string.setting_confirmation_no,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Settings.System.putInt(mContext.getContentResolver(), setting, DISABLED);
                        if (mListener == null) return;
                        mListener.onSelect(false);
                    }
                }
        );
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);

        dialog.show();
    }

}
