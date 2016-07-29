package com.mediatek.internal.telephony;

import java.util.List;

import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

/**
 * This class is define the default voice call sub setting rule.
 * 1. If there is no Sub inserted, the default voice call sub will be DEFAULT_SIM_NOT_SET.
 * 2. If there is only one Sub, the default voice call sub will be that one.
 * 3. If there are more than one Sub:
 *      a. If the old default voice call sub is still available, the settings will not change.
 *      b. If the old default voice call sub is not available, the settings will be ALWAYS_ASK.
 */
public class DefaultVoiceCallSubSettings {

    private static final String LOG_TAG = "DefaultVoiceCallSubSettings";

    public static void setVoiceCallDefaultSub(List<SubscriptionInfo> subInfos) {
        if (!isMTKBspSupported()) {
            int oldDefaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubId();
            logi("oldDefaultVoiceSubId = " + oldDefaultVoiceSubId);

            if (subInfos == null) {
                logi("subInfos == null, set to : INVALID_SUBSCRIPTION_ID");
                // TODO: Fix this
                //SubscriptionManager.setDefaultVoiceSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            } else {
                logi("subInfos size = " + subInfos.size());
                if (subInfos.size() > 1) {
                    if (isoldDefaultVoiceSubIdActive(subInfos)) {
                        logi("subInfos size > 1 & old available, set to :" + oldDefaultVoiceSubId);
                        //SubscriptionManager.setDefaultVoiceSubId(oldDefaultVoiceSubId);
                    } else {
                        logi("subInfos size > 1, set to : ASK_USER");
                        //SubscriptionManager.setDefaultVoiceSubId(SubscriptionManager.ASK_USER_SUB_ID);
                    }
                } else if (subInfos.size() == 1) {
                    logi("subInfos size == 1, set to :" + subInfos.get(0).getSubscriptionId());
                    //SubscriptionManager.setDefaultVoiceSubId(subInfos.get(0).subId);
                } else {
                    logi("subInfos size = 0 set of : INVALID_SUBSCRIPTION_ID");
                    //SubscriptionManager.setDefaultVoiceSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                }
            }
        }
    }

    private static boolean isMTKBspSupported() {
        boolean isSupport = "1".equals(SystemProperties.get("ro.mtk_bsp_package"));
        logi("isMTKBspSupported(): " + isSupport);
        return isSupport;
    }

    private static boolean isoldDefaultVoiceSubIdActive(List<SubscriptionInfo> subInfos) {
        int oldDefaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubId();
        for (SubscriptionInfo subInfo : subInfos) {
            if (subInfo.getSubscriptionId() == oldDefaultVoiceSubId) {
                return true;
            }
        }
        return false;
    }

    private static void logi(String msg) {
        Log.i(LOG_TAG, msg);
    }
}
