/*
 * Created by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
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

package android.provider;

import android.content.Context;
import android.view.KeyEvent;

import com.android.internal.R;

/**
 * CmSystem
 * @hide
 */
public final class CmSystem {
    // explaination of each default value in detail at
    // frameworks/base/core/res/res/value/config.xml
    // can be overridden system wide by overlay usage

    // sorted in order of appearance in tablet tweaks options menu
    public static final int CM_IS_TABLET=0;
    public static final int CM_HAS_SOFT_BUTTONS=1;

    public static final int CM_DEFAULT_BOTTOM_STATUS_BAR=2;
    public static final int CM_DEFAULT_USE_DEAD_ZONE=3;
    public static final int CM_DEFAULT_SOFT_BUTTONS_LEFT=4;

    public static final int CM_DEFAULT_SHOW_SOFT_HOME=5;
    public static final int CM_DEFAULT_SHOW_SOFT_MENU=6;
    public static final int CM_DEFAULT_SHOW_SOFT_BACK=7;
    public static final int CM_DEFAULT_SHOW_SOFT_SEARCH=8;
    public static final int CM_DEFAULT_SHOW_SOFT_QUICK_NA=9;

    public static final int CM_DEFAULT_DISABLE_LOCKSCREEN=10;
    public static final int CM_DEFAULT_DISABLE_FULLSCREEN=11;

    public static final int CM_DEFAULT_UNHIDE_BUTTON_INDEX=12;

    public static final int CM_DEFAULT_EXTEND_POWER_MENU=13;

    public static final int CM_DEFAULT_POWER_MENU_HOME=14;
    public static final int CM_DEFAULT_POWER_MENU_MENU=15;
    public static final int CM_DEFAULT_POWER_MENU_BACK=16;

    public static final int CM_DEFAULT_REVERSE_VOLUME_BEHAVIOR=17;
    public static final int CM_DEFAULT_REMAPPED_LONG_VOL_UP_INDEX=18;
    public static final int CM_DEFAULT_REMAPPED_LONG_VOL_DOWN_INDEX=19;
    public static final int CM_DEFAULT_REMAPPED_BOTH_VOL_INDEX=20;
    public static final int CM_DEFAULT_REMAPPED_LONG_BOTH_VOL_INDEX=21;

    // not a keycode, so negative value can never be considered keycode - user -1, -2, -3...
    // used in PhoneWindowManager
    public static final int VOLUME_ACTION_LONG_HOME = -1;
    public static final int VOLUME_ACTION_NONE = 0;
    public static final int KEYCODE_NONE = -1;


    public enum LockscreenStyle {
        Slider,
        Rotary,
        Lense,
        Ring;

        static public LockscreenStyle getStyleById(int id) {
            switch (id) {
                case 1:
                    return Slider;
                case 2:
                    return Rotary;
                case 3:
                    /* backwards compat */
                    return Rotary;
                case 4:
                    return Lense;
                case 5:
                    return Ring;
                default:
                    return Ring;
            }
        }

        static public LockscreenStyle getStyleById(String id) {
            return getStyleById(Integer.valueOf(id));
        }

        static public int getIdByStyle(LockscreenStyle lockscreenstyle) {
            switch (lockscreenstyle){
                case Slider:
                    return 1;
                case Rotary:
                    return 2;
                case Lense:
                    return 4;
                case Ring:
                    return 5;
                default:
                    return 5;
            }
        }
    }

    public enum InCallStyle {
        Slider,
        Rotary,
        Ring;

        static public InCallStyle getStyleById(int id) {
            switch (id) {
                case 1:
                    return Slider;
                case 2:
                    return Rotary;
                case 3:
                    /* backwards compat */
                    return Rotary;
                case 4:
                    return Ring;
                default:
                    return Ring;
            }
        }

        static public InCallStyle getStyleById(String id) {
            return getStyleById(Integer.valueOf(id));
        }

        static public int getIdByStyle(InCallStyle inCallStyle) {
            switch (inCallStyle) {
                case Slider:
                    return 1;
                case Rotary:
                    return 2;
                case Ring:
                    return 4;
                default:
                    return 4;
            }
        }
    }

    public enum RotaryStyle {
        Normal,
        Revamped;

        static public RotaryStyle getStyleById(int id) {
            switch (id) {
                case 1:
                    return Normal;
                case 2:
                    return Revamped;
                default:
                    return Normal;
            }
        }

        static public RotaryStyle getStyleById(String id) {
            return getStyleById(Integer.valueOf(id));
        }

        static public int getIdByStyle(RotaryStyle style) {
            switch (style) {
                case Normal:
                    return 1;
                case Revamped:
                    return 2;
                default:
                    return 1;
            }
        }
    }

    public enum RinglockStyle {
        Bubble,
        Revamped,
        Holo,
        Blade;

        static public RinglockStyle getStyleById(int id) {
            switch (id) {
                case 1:
                    return Bubble;
                case 2:
                    return Revamped;
                case 3:
                    return Holo;
                case 4:
                    return Blade;
                default:
                    return Bubble;
            }
        }

        static public RinglockStyle getStyleById(String id) {
            return getStyleById(Integer.valueOf(id));
        }

        static public int getIdByStyle(RinglockStyle style) {
            switch (style) {
                case Bubble:
                    return 1;
                case Revamped:
                    return 2;
                case Holo:
                    return 3;
                case Blade:
                    return 4;
                default:
                    return 1;
            }
        }
    }

    public CmSystem(){
        //nothing to be done, as long as only static functions in here
    }

    public static boolean getDefaultBool(Context context, int which){
        int res=getResourceId(which);

        if(res!=0)
            return context.getResources().getBoolean(res);

        // default if id not found
        return false;
    }

    public static int getDefaultInt(Context context, int which){
        int res=getResourceId(which);

        if(res!=0)
            return context.getResources().getInteger(res);

        // default if id not found
        return 0;
    }

    private static int getResourceId(int which){
        int resId=0;

        switch(which){
            case CM_IS_TABLET:
                resId=R.bool.cm_default_is_tablet;
                break;
            case CM_HAS_SOFT_BUTTONS:
                resId=R.bool.cm_default_has_soft_buttons;
                break;

            case CM_DEFAULT_BOTTOM_STATUS_BAR:
                resId=R.bool.cm_default_bottom_status_bar;
                break;
            case CM_DEFAULT_USE_DEAD_ZONE:
                resId=R.bool.cm_default_use_dead_zone;
                break;
            case CM_DEFAULT_SOFT_BUTTONS_LEFT:
                resId=R.bool.cm_default_soft_buttons_left;
                break;

            case CM_DEFAULT_SHOW_SOFT_HOME:
                resId=R.bool.cm_default_show_soft_home;
                break;
            case CM_DEFAULT_SHOW_SOFT_MENU:
                resId=R.bool.cm_default_show_soft_menu;
                break;
            case CM_DEFAULT_SHOW_SOFT_BACK:
                resId=R.bool.cm_default_show_soft_back;
                break;
            case CM_DEFAULT_SHOW_SOFT_SEARCH:
                resId=R.bool.cm_default_show_soft_search;
                break;
            case CM_DEFAULT_SHOW_SOFT_QUICK_NA:
                resId=R.bool.cm_default_show_soft_quick_na;
                break;

            case CM_DEFAULT_DISABLE_LOCKSCREEN:
                resId=R.bool.cm_default_disable_lockscreen;
                break;
            case CM_DEFAULT_DISABLE_FULLSCREEN:
                resId=R.bool.cm_default_disable_fullscreen;
                break;

            case CM_DEFAULT_EXTEND_POWER_MENU:
                resId=R.bool.cm_default_extend_power_menu;
                break;

            case CM_DEFAULT_POWER_MENU_HOME:
                resId=R.bool.cm_default_power_menu_home;
                break;
            case CM_DEFAULT_POWER_MENU_MENU:
                resId=R.bool.cm_default_power_menu_menu;
                break;
            case CM_DEFAULT_POWER_MENU_BACK:
                resId=R.bool.cm_default_power_menu_back;
                break;

            case CM_DEFAULT_REVERSE_VOLUME_BEHAVIOR:
                resId=R.bool.cm_default_reverse_volume_behavior;
                break;

            // integer values
            case CM_DEFAULT_UNHIDE_BUTTON_INDEX:
                resId=R.integer.cm_default_unhide_button_index;
                break;
            case CM_DEFAULT_REMAPPED_LONG_VOL_UP_INDEX:
                resId=R.integer.cm_default_remapped_long_vol_up_index;
                break;
            case CM_DEFAULT_REMAPPED_LONG_VOL_DOWN_INDEX:
                resId=R.integer.cm_default_remapped_long_vol_down_index;
                break;
            case CM_DEFAULT_REMAPPED_BOTH_VOL_INDEX:
                resId=R.integer.cm_default_remapped_both_vol_index;
                break;
            case CM_DEFAULT_REMAPPED_LONG_BOTH_VOL_INDEX:
                resId=R.integer.cm_default_remapped_long_both_vol_index;
                break;
        }

        return resId;
    }

    // remaped indexes of CmParts' array.xml file
    public static int translateActionToKeycode(int longActionIndex) {
        switch (longActionIndex){
            case 1: return KeyEvent.KEYCODE_HOME;
            case 2: return KeyEvent.KEYCODE_MENU;
            case 3: return KeyEvent.KEYCODE_BACK;
            case 4: return KeyEvent.KEYCODE_SEARCH;
            case 5: return KeyEvent.KEYCODE_POWER;
            case 6: return KeyEvent.KEYCODE_CALL;
            case 7: return KeyEvent.KEYCODE_ENDCALL;
            case 8: return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case 9: return KeyEvent.KEYCODE_MEDIA_NEXT;
            case 10: return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case 11: return VOLUME_ACTION_LONG_HOME;
            default: return VOLUME_ACTION_NONE;
        }
    }

    // remaped indexes of CmParts' array.xml file
    public static final int translateUnhideIndexToKeycode(int unhideButtonIndex) {
        switch (unhideButtonIndex){
            case 1: return KeyEvent.KEYCODE_MENU;
            case 2: return KeyEvent.KEYCODE_BACK;
            case 3: return KeyEvent.KEYCODE_SEARCH;
            case 4: return KeyEvent.KEYCODE_CAMERA;
            case 5: return KeyEvent.KEYCODE_VOLUME_UP;
            case 6: return KeyEvent.KEYCODE_VOLUME_DOWN;
            default: return KeyEvent.KEYCODE_HOME;
        }
    }
}
