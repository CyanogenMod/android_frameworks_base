package com.android.systemui;

import android.app.Activity;
import android.os.Handler;

public class Transparent extends Activity {

    @Override
    public void onStart() {
        super.onStart();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                Transparent.this.finish();
            }
        }, 500);
    }
} 
