package android.service.pie;

import android.service.pie.IPieActivationListener;
import android.service.pie.IPieHostCallback;

/** @hide */
interface IPieService {

    /** Register a listener for pie activation gestures. Initially the listener
     * is set to listen for no position. Use updatePieActivationListener() to
     * bind the listener to positions.
     * Use the returned IPieHostCallback to manipulate the state after activation.
     */
    IPieHostCallback registerPieActivationListener(in IPieActivationListener listener);

    /** Update the listener to react on gestures in the given positions.
     */
    void updatePieActivationListener(in IBinder listener, int positionFlags);

}