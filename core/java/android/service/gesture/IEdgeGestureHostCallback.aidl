package android.service.gesture;

/** @hide */
interface IEdgeGestureHostCallback {

    /** After being activated, this allows to steal focus from the current
     * window
     */
    boolean gainTouchFocus(IBinder windowToken);

    /** Turns listening for activation gestures on again, after it was disabled during
     * the call to the listener.
     */
    oneway void restoreListenerState();

    /*
     * Tells filter to drop all events till touch up
     */
    boolean dropEventsUntilLift();
}