package android.service.pie;

/** @hide */
interface IPieHostCallback {

    /** After being activated, this allows the pie control to steal focus from the current
     * window
     */
    boolean gainTouchFocus(IBinder windowToken);

    /** Turns listening for pie activation gestures on again, after it was disabled during
     * the call to the listener.
     */
    oneway void restoreListenerState();
}