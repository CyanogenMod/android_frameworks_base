
package android.service.gesture;

import android.view.InputEvent;

/** @hide */
interface IEdgeGestureActivationListener {

    /** Called when a gesture is detected that fits to the activation gesture. At this point in
     * time gesture detection is disabled. Call IEdgeGestureHostCallback.restoreState() to
     * recover from this.
     */
    oneway void onEdgeGestureActivation(int touchX, int touchY, int positionIndex, int flags);
}