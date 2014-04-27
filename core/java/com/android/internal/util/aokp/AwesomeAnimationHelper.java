/*
 * Copyright (C) 2013 AOKP by Steve Spear - Stevespear426
 * This code has been modified. Portions copyright (C) 2013, XuiMod.
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

package com.android.internal.util.aokp;

import java.util.ArrayList;
import java.util.Random;

import android.content.Context;
import android.content.res.Resources;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import com.android.internal.R;

public class AwesomeAnimationHelper {

    public final static int ANIMATION_RANDOM = -1;
    public final static int ANIMATION_DEFAULT = 0;
    public final static int ANIMATION_FADE = 1;
    public final static int ANIMATION_SLIDE_RIGHT = 2;
    public final static int ANIMATION_SLIDE_LEFT = 3;
    public final static int ANIMATION_SLIDE_RIGHT_NO_FADE = 4;
    public final static int ANIMATION_SLIDE_LEFT_NO_FADE = 5;
    public final static int ANIMATION_SLIDE_UP = 6;
    public final static int ANIMATION_SLIDE_DOWN = 7;
    public final static int ANIMATION_TRANSLUCENT = 8;
    public final static int ANIMATION_GROW_SHRINK = 9;
    public final static int ANIMATION_GROW_SHRINK_CENTER = 10;
    public final static int ANIMATION_GROW_SHRINK_BOTTOM = 11;
    public final static int ANIMATION_GROW_SHRINK_LEFT = 12;
    public final static int ANIMATION_GROW_SHRINK_RIGHT = 13;

    public static final int INTERPOLATOR_NONE = 0;
    public static final int INTERPOLATOR_ACCELERATE = 1;
    public static final int INTERPOLATOR_DECELERATE = 2;
    public static final int INTERPOLATOR_ACCELERATE_DECELERATE = 3;
    public static final int INTERPOLATOR_ANTICIPATE = 4;
    public static final int INTERPOLATOR_OVERSHOOT = 5;
    public static final int INTERPOLATOR_ANTICIPATE_OVERSHOOT = 6;
    public static final int INTERPOLATOR_BOUNCE = 7;
    public static final int INTERPOLATOR_CYCLE = 8;
    public static final int INTERPOLATOR_LINEAR = 9;

    public static int[] getAnimationsList() {
        ArrayList<Integer> animList = new ArrayList<Integer>();
        animList.add(ANIMATION_DEFAULT);
        animList.add(ANIMATION_FADE);
        animList.add(ANIMATION_SLIDE_RIGHT);
        animList.add(ANIMATION_SLIDE_LEFT);
        animList.add(ANIMATION_SLIDE_RIGHT_NO_FADE);
        animList.add(ANIMATION_SLIDE_LEFT_NO_FADE);
        animList.add(ANIMATION_SLIDE_UP);
        animList.add(ANIMATION_SLIDE_DOWN);
        animList.add(ANIMATION_TRANSLUCENT);
        animList.add(ANIMATION_GROW_SHRINK);
        animList.add(ANIMATION_GROW_SHRINK_CENTER);
        animList.add(ANIMATION_GROW_SHRINK_BOTTOM);
        animList.add(ANIMATION_GROW_SHRINK_LEFT);
        animList.add(ANIMATION_GROW_SHRINK_RIGHT);
        animList.add(ANIMATION_RANDOM);
        int length = animList.size();
        int[] anim = new int[length];
        for (int i = 0; i < length; i++) {
            anim[i] = animList.get(i);
        }
        return anim;
    }

    public static int[] getAnimations(int mAnim) {
        if (mAnim == ANIMATION_RANDOM){
            mAnim = (new Random()).nextInt(14);
            // Random number from 0 to 13
        }
        int[] anim = new int[2];
        switch (mAnim) {
            case ANIMATION_FADE:
                anim[0] = R.anim.animcontrol_slow_fade_out;
                anim[1] = R.anim.animcontrol_slow_fade_in;
                break;
            case ANIMATION_SLIDE_RIGHT:
                anim[0] = R.anim.animcontrol_slide_out_right_ribbon;
                anim[1] = R.anim.animcontrol_slide_in_right_ribbon;
                break;
            case ANIMATION_SLIDE_LEFT:
                anim[0] = R.anim.animcontrol_slide_out_left_ribbon;
                anim[1] = R.anim.animcontrol_slide_in_left_ribbon;
                break;
            case ANIMATION_SLIDE_UP:
                anim[0] = R.anim.animcontrol_slide_out_down_ribbon;
                anim[1] = R.anim.animcontrol_slide_in_up_ribbon;
                break;
            case ANIMATION_SLIDE_DOWN:
                anim[0] = R.anim.animcontrol_slide_out_up;
                anim[1] = R.anim.animcontrol_slide_in_down;
                break;
            case ANIMATION_SLIDE_RIGHT_NO_FADE:
                anim[0] = R.anim.animcontrol_slide_out_right_no_fade;
                anim[1] = R.anim.animcontrol_slide_in_right_no_fade;
                break;
            case ANIMATION_SLIDE_LEFT_NO_FADE:
                anim[0] = R.anim.animcontrol_slide_out_left_no_fade;
                anim[1] = R.anim.animcontrol_slide_in_left_no_fade;
                break;
            case ANIMATION_TRANSLUCENT:
                anim[0] = R.anim.animcontrol_translucent_exit_ribbon;
                anim[1] = R.anim.animcontrol_translucent_enter_ribbon;
                break;
            case ANIMATION_GROW_SHRINK:
                anim[0] = R.anim.animcontrol_shrink_fade_out_ribbon;
                anim[1] = R.anim.animcontrol_grow_fade_in_ribbon;
                break;
            case ANIMATION_GROW_SHRINK_CENTER:
                anim[0] = R.anim.animcontrol_shrink_fade_out_center_ribbon;
                anim[1] = R.anim.animcontrol_grow_fade_in_center_ribbon;
                break;
            case ANIMATION_GROW_SHRINK_LEFT:
                anim[0] = R.anim.animcontrol_shrink_fade_out_left_ribbon;
                anim[1] = R.anim.animcontrol_grow_fade_in_left_ribbon;
                break;
            case ANIMATION_GROW_SHRINK_RIGHT:
                anim[0] = R.anim.animcontrol_shrink_fade_out_right_ribbon;
                anim[1] = R.anim.animcontrol_grow_fade_in_right_ribbon;
                break;
            case ANIMATION_GROW_SHRINK_BOTTOM:
                anim[0] = R.anim.animcontrol_shrink_fade_out_from_bottom_ribbon;
                anim[1] = R.anim.animcontrol_grow_fade_in_from_bottom_ribbon;
                break;
        }
        return anim;
    }

    public static String getProperName(Resources res, int index) {
        String[] str = res.getStringArray(R.array.anim_controls_entries);
        if (index == ANIMATION_RANDOM){
            index = str.length - 1;
        }
        return str[index];
    }

    public static Interpolator getInterpolator(Context ctx, int index) {
        Interpolator itplr = null;
        switch(index) {
           case INTERPOLATOR_ACCELERATE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.accelerate_interpolator);
                break;
           case INTERPOLATOR_DECELERATE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.decelerate_interpolator);
                break;
           case INTERPOLATOR_ACCELERATE_DECELERATE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.accelerate_decelerate_interpolator);
                break;
           case INTERPOLATOR_ANTICIPATE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.anticipate_interpolator);
                break;
           case INTERPOLATOR_OVERSHOOT:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.overshoot_interpolator);
                break;
           case INTERPOLATOR_ANTICIPATE_OVERSHOOT:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.anticipate_overshoot_interpolator);
                break;
           case INTERPOLATOR_BOUNCE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.bounce_interpolator);
                break;
           case INTERPOLATOR_CYCLE:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.cycle_interpolator);
                break;
           case INTERPOLATOR_LINEAR:
                itplr = AnimationUtils.loadInterpolator(ctx, android.R.anim.linear_interpolator);
                break;
        }
        return itplr;
    }
}
