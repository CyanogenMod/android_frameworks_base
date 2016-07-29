
package com.mediatek.internal.telephony;

import android.util.Log;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.os.SystemProperties;
import java.util.List;
import android.content.Context;

public class DefaultSmsSimSettings {
    private static final String TAG = "DefaultSmsSimSettings";
    public static final int ASK_USER_SUB_ID = -2;

    public static void setSmsTalkDefaultSim(List<SubscriptionInfo> subInfos, Context context) {
        /*
        if (!"1".equals(SystemProperties.get("ro.mtk_bsp_package"))) {
            int oldSmsDefaultSIM = SubscriptionManager.getDefaultSmsSubId();
            Log.i(TAG, "oldSmsDefaultSIM" + oldSmsDefaultSIM);

            if (subInfos == null) {
                Log.i(TAG, "subInfos == null, return");
                //SubscriptionManager.from(context).setDefaultSmsSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            } else {
                Log.i(TAG, "subInfos size = " + subInfos.size());
                if (subInfos.size() > 1) {
                    if (isoldDefaultSMSSubIdActive(subInfos)) {
                        Log.i(TAG, "subInfos size > 1 & old available, set to :"
                                + oldSmsDefaultSIM);
                        //SubscriptionManager.from(context).setDefaultSmsSubId(oldSmsDefaultSIM);
                    } else {
                        if ("OP01".equals(SystemProperties.get("ro.operator.optr"))) {
                            Log.i(TAG, "subInfos size > 1, set to : AUTO");
                            SubscriptionManager.from(context)
                                .setDefaultSmsSubId((int) Settings.System.SMS_SIM_SETTING_AUTO);
                        }else if ("OP09".equals(SystemProperties.get("ro.operator.optr"))) {
                            int firstSubId = SubscriptionManager.from(context)
                                   .getActiveSubscriptionInfoForSimSlotIndex(0).getSubscriptionId();
                            SubscriptionManager.from(context).setDefaultSmsSubId(firstSubId);
                            Log.i(TAG, "subInfos size > 1, set to " + firstSubId);
                        } else {
                            Log.i(TAG, "subInfos size > 1, set to : ASK_USER_SUB_ID");
                            //SubscriptionManager.from(context).setDefaultSmsSubId(ASK_USER_SUB_ID);
                        }
                    }
                } else if (subInfos.size() == 1) {
                    if ("OP09".equals(SystemProperties.get("ro.operator.optr"))) {
                        int defaultSubId = subInfos.get(0).getSubscriptionId();
                        SubscriptionManager.from(context).setDefaultSmsSubId(defaultSubId);
                        Log.i(TAG, "subInfos size = 1, set to " + defaultSubId);
                    }
                    //SubscriptionManager.from(context).setDefaultSmsSubId(subInfos.get(0).getSubscriptionId());
                } else {
                    Log.i(TAG, "setSmsTalkDefaultSim SIM not insert");
                    //SubscriptionManager.from(context).setDefaultSmsSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                }
            }
        }
        */
    }

    private static boolean isoldDefaultSMSSubIdActive(List<SubscriptionInfo> subInfos) {
        int oldSmsDefaultSIM = SubscriptionManager.getDefaultSmsSubId();

        for (SubscriptionInfo subInfo : subInfos) {
            if (subInfo.getSubscriptionId() == oldSmsDefaultSIM) {
                return true;
            }
        }
        /*
        if ("OP01".equals(SystemProperties.get("ro.operator.optr"))) {
            if (oldSmsDefaultSIM == ASK_USER_SUB_ID ||
                       oldSmsDefaultSIM == (int) Settings.System.SMS_SIM_SETTING_AUTO) {
                return true;
            }
        }
        */
        return false;
    }
}
