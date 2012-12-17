/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.PowerManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;


public class RebootButton extends PowerButton {

    public RebootButton() { mType = BUTTON_REBOOT; }

    @Override
    protected void updateState(Context context) {
        mIcon = R.drawable.stat_reboot;
        mState = STATE_DISABLED;
    }

    @Override
    protected void toggleState(Context context) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot("");
    /*
    // this code may be uncommented later if we want a confirmation before reboot
    if(mContext!=null){
              AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("Power Menu");
        builder.setItems(item_entries, new RebootButtonListener(mContext));
        builder.setNegativeButton("Cancel", new Dialog.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // TODO Auto-generated method stub
                dialog.dismiss();
            }
        });
        builder.create().show();
    }
    else
    {
        Log.e("RebootButton.java", "Error creating reboot dialog: mContext is null");
    }*/
    }

    @Override
    protected boolean handleLongClick(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot("recovery");
        return true;
    }

}
