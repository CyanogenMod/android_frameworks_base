package com.android.internal.telephony;

import android.os.Message;

public interface LGPecanCommandsInterface extends CommandsInterface {

	void setNetworkSelectionModeManual(String operatorNumeric, String operatorRAT, Message response);

}
