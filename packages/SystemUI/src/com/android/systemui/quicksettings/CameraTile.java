package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
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
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PanelView;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class CameraTile extends QuickSettingsTile {
    private static final String DEFAULT_IMAGE_FILE_NAME_FORMAT = "'IMG'_yyyyMMdd_HHmmss";
    private static final int CAMERA_ID = 0;

    private Handler mHandler;
    private TextView mTextView;
    private FrameLayout mSurfaceLayout;
    private SurfaceView mSurfaceView;
    private View mFlashView;

    private Camera mCamera;
    private CameraOrientationListener mCameraOrientationListener = null;
    private int mOrientation;
    private int mDisplayRotation;
    private Camera.Size mCameraSize;
    private boolean mCameraStarted;
    private boolean mCameraBusy;

    private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    private Camera.Parameters mParams;

    private Storage mStorage = new Storage();
    private SimpleDateFormat mImageNameFormatter;

    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera != null) {
                return;
            }

            Camera.getCameraInfo(CAMERA_ID, mCameraInfo);

            try {
                mCamera = Camera.open(CAMERA_ID);
            } catch (Exception e) {
                Toast.makeText(mContext, R.string.quick_settings_camera_error_connect,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Orientation listener to rotate the camera preview
            if (mCameraOrientationListener == null) {
                mCameraOrientationListener = new CameraOrientationListener(mContext);
            }
            mCameraOrientationListener.enable();

            mParams = mCamera.getParameters();

            // Use smallest preview size that is bigger than the tile view
            Camera.Size previewSize = mParams.getPreviewSize();
            for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
                if ((size.width > mTile.getWidth() && size.height > mTile.getHeight()) &&
                        (size.width < previewSize.width && size.height < previewSize.height)) {
                    previewSize = size;
                }
            }
            mParams.setPreviewSize(previewSize.width, previewSize.height);

            // Use largest picture size
            Camera.Size pictureSize = mParams.getPictureSize();
            for (Camera.Size size : mParams.getSupportedPictureSizes()) {
                if (size.width > pictureSize.width && size.height > pictureSize.height) {
                    pictureSize = size;
                }
            }
            mCameraSize = pictureSize;
            mParams.setPictureSize(pictureSize.width, pictureSize.height);

            // Try focus with continuous modes first, then basic autofocus
            List<String> focusModes = mParams.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (mParams.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            mCamera.setParameters(mParams);

            updateOrientation();

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    final PanelView panel = getContainingPanel();
                    if (panel != null && panel.isFullyExpanded()) {
                        mHandler.postDelayed(this, 100);
                    } else {
                        mHandler.post(mReleaseCameraRunnable);
                    }
                }
            }, 100);

            mTextView.setVisibility(View.GONE);
            mSurfaceView = new CameraPreview(mContext, mCamera);
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceLayout.addView(mSurfaceView, 0);
        }
    };

    private Runnable mTakePictureRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) {
                return;
            }

            // Repeat until the preview has started and we can
            // take picture
            if (!mCameraStarted) {
                mHandler.postDelayed(this, 200);
                return;
            }

            // To avoid crashes don't post new picture requests
            // if previous request has not returned
            if (mCameraBusy) {
                return;
            }
            mCameraBusy = true;

            // Display flash animation above the preview
            mFlashView.setVisibility(View.VISIBLE);
            mFlashView.animate().alpha(0f).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mFlashView.setVisibility(View.GONE);
                    mFlashView.setAlpha(1f);
                }
            });

            // Update the JPEG rotation
            final int rotation;

            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (mCameraInfo.orientation - mOrientation + 360) % 360;
            } else {
                rotation = (mCameraInfo.orientation + mOrientation) % 360;
            }

            mParams.setRotation(rotation);
            mCamera.setParameters(mParams);

            // Request a picture
            try {
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        mCameraBusy = false;

                        long time = System.currentTimeMillis();
                        int orientation = (mOrientation + mDisplayRotation) % 360;

                        mStorage.addImage(mContext.getContentResolver(),
                                mImageNameFormatter.format(new Date(time)),
                                time, orientation, data,
                                mCameraSize.width, mCameraSize.height);

                        mCamera.startPreview();
                    }
                });
            } catch (RuntimeException e) {
                // This can happen if user is pressing the
                // tile too fast, nothing we can do
            }
        }
    };

    private Runnable mReleaseCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) {
                return;
            }

            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mCameraStarted = false;
            mCameraOrientationListener.disable();

            mTextView.setVisibility(View.VISIBLE);
            mSurfaceView.setVisibility(View.GONE);
            mSurfaceLayout.removeView(mSurfaceView);
            mSurfaceView = null;
        }
    };

    private Runnable mAutoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCameraStarted) {
                mCamera.autoFocus(null);
            }
        }
    };

    public CameraTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc, R.layout.quick_settings_tile_camera);
        mHandler = handler;

        String imageFileNameFormat = DEFAULT_IMAGE_FILE_NAME_FORMAT;
        try {
            final Resources camRes = context.getPackageManager()
                    .getResourcesForApplication("com.android.gallery3d");
            int imageFileNameFormatResId = camRes.getIdentifier(
                    "image_file_name_format", "string", "com.android.gallery3d");
            imageFileNameFormat = camRes.getString(imageFileNameFormatResId);
        } catch (PackageManager.NameNotFoundException ex) {
            // Use default
        } catch (Resources.NotFoundException ex) {
            // Use default
        }
        mImageNameFormatter = new SimpleDateFormat(imageFileNameFormat);

    }

    @Override
    void onPostCreate() {
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mCamera != null) {
                    return false;
                }

                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                startSettingsActivity(intent);
                return true;
            }
        };

        mTextView = (TextView) mTile.findViewById(R.id.camera_text);
        mSurfaceLayout = (FrameLayout) mTile.findViewById(R.id.camera_surface_holder);
        mFlashView = mTile.findViewById(R.id.camera_surface_flash_overlay);

        super.onPostCreate();
    }

    @Override
    public void onClick(View v) {
        if (mCamera == null) {
            mHandler.post(mStartRunnable);
        } else {
            mHandler.post(mTakePictureRunnable);
        }
    }

    private PanelView getContainingPanel() {
        ViewParent parent = mContainer;
        while (parent != null) {
            if (parent instanceof PanelView) {
                return (PanelView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private void updateOrientation() {
        final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
            default:
                mDisplayRotation = 0;
                break;
            case Surface.ROTATION_90:
                mDisplayRotation = 90;
                break;
            case Surface.ROTATION_180:
                mDisplayRotation = 180;
                break;
            case Surface.ROTATION_270:
                mDisplayRotation = 270;
                break;
        }

        int cameraOrientation;

        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            cameraOrientation = (mCameraInfo.orientation + mDisplayRotation) % 360;
            cameraOrientation = (360 - cameraOrientation) % 360;  // compensate the mirror
        } else {
            cameraOrientation = (mCameraInfo.orientation - mDisplayRotation + 360) % 360;
        }

        mCamera.setDisplayOrientation(cameraOrientation);
    }

    private class CameraOrientationListener extends OrientationEventListener {
        public CameraOrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (mCamera == null || orientation == ORIENTATION_UNKNOWN) {
                return;
            }

            mOrientation = (orientation + 45) / 90 * 90;
            updateOrientation();
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
            // The Surface has been created, now tell the camera where
            // to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
                mCameraStarted = true;
                mCameraBusy = false;
                mHandler.postDelayed(mAutoFocusRunnable, 200);
            } catch (IOException e) {
                // Try release camera
                mCamera.release();
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
                    // Do nothing here
                }
            }
            return path;
        }

        // Save the image and add it to media store.
        public Uri addImage(ContentResolver resolver, String title, long date,
                int orientation, byte[] jpeg, int width, int height) {
            // Save the image.
            String path = writeFile(title, jpeg);
            return addImage(resolver, title, date, orientation, jpeg.length,
                    path, width, height);
        }

        // Add the image to media store.
        public Uri addImage(ContentResolver resolver, String title, long date,
            int orientation, int jpegLength, String path, int width, int height) {
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
