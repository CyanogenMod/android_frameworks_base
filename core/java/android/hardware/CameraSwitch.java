package android.hardware;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Handle switching for HTC devices with dual cameras.
 */
public class CameraSwitch {
    
    public static final String SWITCH_CAMERA_MAIN = "main";
    
    public static final String SWITCH_CAMERA_SECONDARY = "secondary";
    
    private static final String SWITCH_CAMERA_FILE_PATH = "/sys/android_camera2/htcwc";
    
    private static final String LOG_TAG = "CameraSwitch";
    
    private static final boolean HAS_CAMERA_SWITCH;
    
    static {
        final File file = new File(SWITCH_CAMERA_FILE_PATH);
        HAS_CAMERA_SWITCH = file.exists();
    }
    
    private static boolean setHTCCameraSwitch(String cameraSwitch) {
        if (!HAS_CAMERA_SWITCH) {
            return false;
        }
        
        final String node;
        if (SWITCH_CAMERA_MAIN.equals(cameraSwitch)) {
            node = "0";
            Log.d(LOG_TAG, "Open main camera");
        } else if (SWITCH_CAMERA_SECONDARY.equals(cameraSwitch)) {
            node = "1";
            Log.d(LOG_TAG, "Open secondary camera");
        } else {
            Log.e(LOG_TAG, "Unknown camera node: " + cameraSwitch + ", using main");
            node = "0";
        }
        
        final File file = new File(SWITCH_CAMERA_FILE_PATH);
        BufferedWriter writer = null;
        try {
             writer = new BufferedWriter(new FileWriter(file));
             writer.write(node);
             writer.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Can't open " + SWITCH_CAMERA_FILE_PATH, e);
            return false;
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error closing " + SWITCH_CAMERA_FILE_PATH, e);
                return false;
            }
        }
        return true;
    }
    
    public static void openCamera(String cameraNode) {
        setHTCCameraSwitch(cameraNode);
    }
    
    public static void openMainCamera() {
        setHTCCameraSwitch(SWITCH_CAMERA_MAIN);
    }
    
    public static boolean hasCameraSwitch() {
        return HAS_CAMERA_SWITCH;
    }
}
