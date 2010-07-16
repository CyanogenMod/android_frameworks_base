package android.hardware;

/**
 * For compatibility with apps written for the EVO 4G.
 * 
 * @hide
 */
public class HtcFrontFacingCamera extends Camera {

    public static Camera getCamera() {
        return Camera.open(CameraSwitch.SWITCH_CAMERA_SECONDARY);
    }
    
}
