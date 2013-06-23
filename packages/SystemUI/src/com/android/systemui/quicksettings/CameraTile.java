package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class CameraTile extends QuickSettingsTile {
    private static final String IMAGE_FORMAT = "'IMG'_yyyyMMdd_HHmmss";

    private Handler mHandler;
    private TextView mTextView;
    private FrameLayout mSurfaceLayout;
    private SurfaceView mSurfaceView;
    private View mFlashView;

    private Camera mCamera;
    private CameraOrientationListener mCameraOrientationListener = null;
    private int mCameraOrientation;
    private Camera.Size mCameraSize;
    private boolean mCameraReady;

    private Storage mStorage = new Storage();
    private SimpleDateFormat mImageNameFormatter = new SimpleDateFormat(IMAGE_FORMAT);

    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera != null) return;

            try {
                mCamera = Camera.open(0);
            } catch (Exception e) {
                return;
            }

            if (mCameraOrientationListener == null) {
                mCameraOrientationListener = new CameraOrientationListener(mContext);
            }
            mCameraOrientationListener.enable();

            Camera.Parameters params = mCamera.getParameters();

            // Use smallest preview size that is bigger than the tile view
            Camera.Size previewSize = params.getPreviewSize();
            for (Camera.Size size : params.getSupportedPreviewSizes()) {
                if ((size.width > mTile.getWidth() && size.height > mTile.getHeight()) &&
                    (size.width < previewSize.width && size.height < previewSize.height)) {
                    previewSize = size;
                }
            }
            params.setPreviewSize(previewSize.width, previewSize.height);

            // Use largest picture size
            Camera.Size pictureSize = params.getPictureSize();
            for (Camera.Size size : params.getSupportedPictureSizes()) {
                if (size.width > pictureSize.width && size.height > pictureSize.height) {
                    pictureSize = size;
                }
            }
            mCameraSize = pictureSize;
            params.setPictureSize(pictureSize.width, pictureSize.height);

            // Try autofocus
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            mCameraOrientation = getCameraDisplayOrientation(0);
            mCamera.setDisplayOrientation(mCameraOrientation);

            mCamera.setParameters(params);

            mTextView.setVisibility(View.GONE);
            mSurfaceView = new CameraPreview(mContext, mCamera);
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceLayout.addView(mSurfaceView, 0);
        }
    };

    private Runnable mTakePictureRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) return;
            if (!mCameraReady) {
                mHandler.postDelayed(this, 100);
                return;
            }

            mFlashView.setVisibility(View.VISIBLE);
            mFlashView.animate().alpha(0f).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mFlashView.setVisibility(View.GONE);
                        mFlashView.setAlpha(1f);
                    }
            });

            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        long time = System.currentTimeMillis();
                        mStorage.addImage(mContext.getContentResolver(), mImageNameFormatter.format(new Date(time)),
                                time, mCameraOrientation, data, mCameraSize.width, mCameraSize.height);

                        mHandler.removeCallbacks(mReleaseCameraRunnable);
                        mHandler.postDelayed(mReleaseCameraRunnable, 2000);
                    }
            });
        }
    };

    private Runnable mReleaseCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) return;
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mCameraReady = false;
            mCameraOrientationListener.disable();

            mTextView.setVisibility(View.VISIBLE);
            mSurfaceView.setVisibility(View.GONE);
            mSurfaceLayout.removeView(mSurfaceView);
            mSurfaceView = null;
        }
    };

    public CameraTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc, R.layout.quick_settings_tile_camera);

        mHandler = handler;
    }

    @Override
    void onPostCreate() {
        mTile.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mHandler.post(mStartRunnable);
                        break;
                    case MotionEvent.ACTION_UP:
                        mHandler.post(mTakePictureRunnable);
                        break;
                }
                return false;
            }
        });
        mTextView = (TextView) mTile.findViewById(R.id.camera_text);
        mSurfaceLayout = (FrameLayout) mTile.findViewById(R.id.camera_surface_holder);
        mFlashView = mTile.findViewById(R.id.camera_surface_flash_overlay);

        super.onPostCreate();
    }

    private int getCameraDisplayOrientation(int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    private class CameraOrientationListener extends OrientationEventListener {

        public CameraOrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (mCamera != null) {
                mCameraOrientation = getCameraDisplayOrientation(0);
                mCamera.setDisplayOrientation(mCameraOrientation);
            }
        }
    }

    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
                if (mCamera.getParameters().getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    mCamera.autoFocus(null);
                }
                mCameraReady = true;
            } catch (IOException e) {
                // Ignore
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        }
    }

    public class Storage {
        private static final String TAG = "CameraStorage";

        private String mRoot = Environment.getExternalStorageDirectory().toString();

        private Storage() {}

        public String writeFile(String title, byte[] data) {
            String path = generateFilepath(title);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(path);
                out.write(data);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            } finally {
                try {
                    out.close();
                } catch (Exception e) {
                }
            }
            return path;
        }

        // Save the image and add it to media store.
        public Uri addImage(ContentResolver resolver, String title,
                long date, int orientation, byte[] jpeg,
                int width, int height) {
            // Save the image.
            String path = writeFile(title, jpeg);
            return addImage(resolver, title, date, orientation,
                    jpeg.length, path, width, height);
        }

        // Add the image to media store.
        public Uri addImage(ContentResolver resolver, String title,
                long date, int orientation, int jpegLength, String path, 
                int width, int height) {
            // Insert into MediaStore.
            ContentValues values = new ContentValues(9);
            values.put(ImageColumns.TITLE, title);
            values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
            values.put(ImageColumns.DATE_TAKEN, date);
            values.put(ImageColumns.MIME_TYPE, "image/jpeg");
            // Clockwise rotation in degrees. 0, 90, 180, or 270.
            values.put(ImageColumns.ORIENTATION, orientation);
            values.put(ImageColumns.DATA, path);
            values.put(ImageColumns.SIZE, jpegLength);
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);

            Uri uri = null;
            try {
                uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
            } catch (Throwable th)  {
                // This can happen when the external volume is already mounted, but
                // MediaScanner has not notify MediaProvider to add that volume.
                // The picture is still safe and MediaScanner will find it and
                // insert it into MediaProvider. The only problem is that the user
                // cannot click the thumbnail to review the picture.
                Log.e(TAG, "Failed to write MediaStore" + th);
            }
            return uri;
        }

        private String generateDCIM() {
            return new File(mRoot, Environment.DIRECTORY_DCIM).toString();
        }

        public String generateDirectory() {
            return generateDCIM() + "/Camera";
        }

        private String generateFilepath(String title) {
            return generateDirectory() + '/' + title + ".jpg";
        }

        public String generateBucketId() {
            return String.valueOf(generateDirectory().toLowerCase().hashCode());
        }

        public int generateBucketIdInt() {
            return generateDirectory().toLowerCase().hashCode();
        }
    }
}
