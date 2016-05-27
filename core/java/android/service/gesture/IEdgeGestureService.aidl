package android.service.gesture;

import android.service.gesture.IEdgeGestureActivationListener;
import android.service.gesture.IEdgeGestureHostCallback;

/** @hide */
interface IEdgeGestureService {

    /** Register a listener for activation gestures. Initially the listener
     * is set to listen for no position. Use updateEdgeGestureActivationListener() to
     * bind the listener to positions.
     * Use the returned IEdgeGestureHostCallback to manipulate the state after activation.
     */
    IEdgeGestureHostCallback registerEdgeGestureActivationListener(in IEdgeGestureActivationListener listener);

    /** Update the listener to react on gestures in the given positions.
     */
    void updateEdgeGestureActivationListener(in IBinder listener, int positionFlags);

}