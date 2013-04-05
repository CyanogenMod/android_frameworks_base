
package android.service.pie;

import android.view.InputEvent;

/** @hide */
interface IPieActivationListener {

    /** Called when a gesture is detected that fits to the pie activation gesture. At this point in
     * time gesture detection is disabled. Call IPieHostCallback.restoreState() to
     * recover from this.
     */
    oneway void onPieActivation(int touchX, int touchY, int positionIndex, int flags);
}