package com.example.siyangzhang.steganography;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.support.v4.graphics.ColorUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import static android.graphics.Color.colorToHSV;

/**
 * Created by siyangzhang on 7/16/17.
 */

public class RevealActivity extends Activity implements PictureCallback, SurfaceHolder.Callback {

    private float offset;
    private static int ch = 2;
    private final int RESULT_LOAD_IMG = 1;

    private static  final int FOCUS_AREA_SIZE= 300;

    public static final String EXTRA_CAMERA_DATA = "camera_data";

    private static final String KEY_IS_CAPTURING = "is_capturing";

    private static final String TAG = "RevealActivity";

    private static final int focusAreaSize = 300;

    private Camera mCamera;
    private Bitmap mCameraBitmap;
    private ImageView mCameraImage;
    private ImageView mCameraLayer;
    private SurfaceView mCameraPreview;
    private Button mCaptureImageButton;
    private Button mPickImageButton;
    private Button mRevealButton;
    private EditText editText;
    private byte[] mCameraData;
    private boolean mIsCapturing;
    private Bitmap bmp;

    private OnClickListener mCaptureImageButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            captureImage();
        }
    };

    private OnClickListener mRecaptureImageButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            setupImageCapture();
        }
    };

    private OnClickListener mPickerOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG);
            setupPickImageDisplay();
        }
    };

    private OnClickListener mRevealOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if(bmp!=null) {
                String message = revealMessage(bmp);
                Toast.makeText(RevealActivity.this, message,
                        Toast.LENGTH_LONG).show();
            }
        }
    };

    private void ensurePermissions(String... permissions) {
        ArrayList<String> deniedPermissionList = new ArrayList<>();

        for (String permission : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission)) deniedPermissionList.add(permission);
        }

        if (!deniedPermissionList.isEmpty()) ActivityCompat.requestPermissions(this, deniedPermissionList.toArray(new String[0]), 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            //Check permissions
            ensurePermissions(Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        setContentView(R.layout.activity_reveal);

        editText = (EditText) findViewById(R.id.editText);
        offset = Float.valueOf(editText.getText().toString());

        mCameraImage = (ImageView) findViewById(R.id.camera_image_view);
        mCameraImage.setVisibility(View.INVISIBLE);

        mCameraLayer = (ImageView) findViewById(R.id.camera_layer);
        mCameraLayer.setVisibility(View.VISIBLE);

        mCameraPreview = (SurfaceView) findViewById(R.id.preview_view);
        final SurfaceHolder surfaceHolder = mCameraPreview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mCaptureImageButton = (Button) findViewById(R.id.capture_image_button);
        mCaptureImageButton.setOnClickListener(mCaptureImageButtonClickListener);

        mPickImageButton = (Button) findViewById(R.id.save_image_button);
        mPickImageButton.setOnClickListener(mPickerOnClickListener);

        mRevealButton = (Button) findViewById(R.id.reveal_image_button);
        mRevealButton.setOnClickListener(mRevealOnClickListener);
        mIsCapturing = true;

        mCameraPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mCamera != null) {
                    Camera camera = mCamera;
                    camera.cancelAutoFocus();
                    Rect focusRect = calculateTapArea(event.getX(), event.getY(), 1f);

                    Camera.Parameters parameters = camera.getParameters();
                    if (parameters.getFocusMode().equals(
                            Camera.Parameters.FOCUS_MODE_AUTO)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }

                    if (parameters.getMaxNumFocusAreas() > 0) {
                        List<Camera.Area> mylist = new ArrayList<Camera.Area>();
                        mylist.add(new Camera.Area(focusRect, 1000));
                        parameters.setFocusAreas(mylist);
                    }

                    try {
                        camera.cancelAutoFocus();
                        camera.setParameters(parameters);
                        camera.startPreview();
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                                if (camera.getParameters().getFocusMode().equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                                    Camera.Parameters parameters = camera.getParameters();
                                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                                    if (parameters.getMaxNumFocusAreas() > 0) {
                                        parameters.setFocusAreas(null);
                                    }
                                    camera.setParameters(parameters);
                                    camera.startPreview();
                                }
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(KEY_IS_CAPTURING, mIsCapturing);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsCapturing = savedInstanceState.getBoolean(KEY_IS_CAPTURING, mCameraData == null);
        if (mCameraData != null) {
            setupImageDisplay();
        } else {
            setupImageCapture();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        offset = Float.valueOf(editText.getText().toString());

        /*
        if(bmp!=null) {
            String message = revealMessage(bmp);
            Toast.makeText(RevealActivity.this, message,
                    Toast.LENGTH_LONG).show();
        }
        */
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
                mCamera.setPreviewDisplay(mCameraPreview.getHolder());
                if (mIsCapturing) {
                    mCamera.setDisplayOrientation(90);
                    mCamera.startPreview();
                }
            } catch (Exception e) {
                Toast.makeText(RevealActivity.this, "Unable to open camera. Please go to settings for camera permission", Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        mCameraData = data;
        setupImageDisplay();
    }

    /**
     * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
     */
    private Rect calculateTapArea(float x, float y, float coefficient) {
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int left = clamp((int) x - areaSize / 2, 0, mCameraPreview.getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, mCameraPreview.getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        Matrix matrix = new Matrix();
        matrix.mapRect(rectF);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            Camera.Parameters camParams = mCamera.getParameters();

            Rect newRect = new Rect(-100, -200, 100, 0);
            Camera.Area focusArea = new Camera.Area(newRect, 1000);
            List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(focusArea);

            // Flatten camera parameters
            String flattened = camParams.flatten();
            StringTokenizer tokenizer = new StringTokenizer(flattened, ";");
            Log.d(TAG, "Dump all camera parameters:");
            while (tokenizer.hasMoreElements()) {
                Log.d(TAG, tokenizer.nextToken());
            }


            Log.i(TAG, "Supported Focus Models:" + camParams.getSupportedFocusModes());
            Log.i(TAG, "Supported ISO Modes:" + camParams.get("iso-values"));
            Log.i(TAG, "Supported Exposure Modes:" + camParams.get("exposure-model-values"));
            Log.i(TAG, "Supported White Balance Modes:" + camParams.getSupportedWhiteBalance());
            Log.i(TAG, "Supported Preview Sizes: " + camParams.get("preview-size-values"));

            // Set camera parameters
            camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            camParams.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            camParams.set("orientation", "portrait");
            camParams.set("preview-size","1920x1080");
            mCamera.setParameters(camParams);

            try {
                mCamera.setPreviewDisplay(holder);
                if (mIsCapturing) {
                    mCamera.setDisplayOrientation(90);
                    mCamera.startPreview();
                }
            } catch (IOException e) {
                Toast.makeText(RevealActivity.this, "Unable to start camera preview.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { }

    private void captureImage() {
        mCamera.takePicture(null, null, this);
    }

    private void setupImageCapture() {
        mCameraImage.setVisibility(View.INVISIBLE);
        mCameraLayer.setVisibility(View.VISIBLE);
        mCameraPreview.setVisibility(View.VISIBLE);
        mCamera.setDisplayOrientation(90);
        mCamera.startPreview();
        mCaptureImageButton.setText(R.string.capture_image);
        mCaptureImageButton.setOnClickListener(mCaptureImageButtonClickListener);
    }

    private void setupImageDisplay() {
        Bitmap rotateBitmap= BitmapFactory.decodeByteArray(mCameraData, 0, mCameraData.length);
        Bitmap cropBitmap = RotateBitmap(rotateBitmap, 90);
        Log.d(TAG, "CROP Size: " + cropBitmap.getWidth() + " " + cropBitmap.getHeight());
        mCameraBitmap = Bitmap.createBitmap(cropBitmap, cropBitmap.getWidth()/2-400, cropBitmap.getHeight()/2-400, 800, 800);

        /**
         * Set boundary for crop bitmap
         */
        int mWidth = mCameraBitmap.getWidth();
        int mHeight = mCameraBitmap.getHeight();
        int[] pixels = new int[mWidth*mHeight];
        mCameraBitmap.getPixels(pixels, 0, mWidth, 0, 0, mWidth, mHeight);
        float[][] hsv = new float[pixels.length][3];
        mCameraBitmap.getPixels(pixels, 0, mWidth, 0, 0, mWidth, mHeight);


        int min_x = 1000, min_y = 1000, max_x = 0, max_y = 0;
        //Get HSV from color
        for (int i = 0; i < pixels.length; i++) {
            colorToHSV(pixels[i], hsv[i]);
        }

        //Top-left
        for (int y=80; y>0; y--) {
            for (int x=80; x>0; x--) {
                int index = y*mWidth + x;
                float tl = (float) 0.0;
                if(hsv[index][2]<offset) {
                    for (int i=0; i<10; i++) {
                        for (int j=0; j<10; j++) {
                            index = (y+j)*mWidth + (x+i);
                            tl += hsv[index][2];
                        }
                    }
                    if (tl/200 < offset) {
                        min_x = x;
                        min_y = y;
                    }
                }
            }
        }
        //Bottom-right
        for (int y=760; y<800; y++) {
            for (int x=760; x<800; x++) {
                int index = y*mWidth + x;
                float br = (float) 0.0;
                if(hsv[index][2]<offset) {
                    for (int i=0; i<10; i++) {
                        for (int j=0; j<10; j++) {
                            index = (y-j)*mWidth + (x-i);
                            br += hsv[index][2];
                        }
                    }
                    if (br/200 < offset) {
                        max_x = x;
                        max_y = y;
                    }
                }
            }
        }

        //Resize bitmap from cropped bitmap
        if(min_x<max_x-min_x && min_y<max_y-min_y) {
            Bitmap temp = Bitmap.createBitmap(mCameraBitmap, min_x, min_y, max_x - min_x, max_y - min_y);
            bmp = getResizedBitmap(temp, 800, 800);
            temp.recycle();
        } else {
            bmp = mCameraBitmap;
        }


        Drawable drawable = new BitmapDrawable(getResources(), bmp);
        if(mCameraImage.getDrawable() != null) ((BitmapDrawable)mCameraImage.getDrawable()).getBitmap().recycle();
        mCameraImage.setImageDrawable(drawable);

        /*
        if(bmp!=null) {
            String message = revealMessage(bmp);
            Toast.makeText(RevealActivity.this, message,
                    Toast.LENGTH_LONG).show();
        }
        */

        mCamera.stopPreview();
        mCameraImage.setVisibility(View.VISIBLE);
        mCameraLayer.setVisibility(View.INVISIBLE);
        mCameraPreview.setVisibility(View.INVISIBLE);
        mCaptureImageButton.setText(R.string.recapture_image);
        mCaptureImageButton.setOnClickListener(mRecaptureImageButtonClickListener);
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private void setupPickImageDisplay() {
        mCamera.stopPreview();
        mCameraImage.setVisibility(View.VISIBLE);
        mCameraLayer.setVisibility(View.INVISIBLE);
        mCameraPreview.setVisibility(View.INVISIBLE);
        mCaptureImageButton.setText(R.string.recapture_image);
        mCaptureImageButton.setOnClickListener(mRecaptureImageButtonClickListener);
    }

    private String revealMessage(Bitmap bitmap) {
        Bitmap rbmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        if (rbmp != null) {
            Log.d("RevealMessage", "rbmp size: " + rbmp.getWidth() + "-" + rbmp.getHeight());
        }
        int bWidth = rbmp.getWidth();
        int bHeight = rbmp.getHeight();
        int[] pixels = new int[bWidth*bHeight];
        rbmp.getPixels(pixels, 0, bWidth, 0, 0, bWidth, bHeight);
        String bString = "";

        float[][] hsv = new float[pixels.length][3];

        rbmp.getPixels(pixels, 0, bWidth, 0, 0, bWidth, bHeight);

        //Get HSV from color
        for (int i = 0; i < pixels.length; i++) {
            colorToHSV(pixels[i], hsv[i]);
        }

        int counter = 0;
        for (int y = bHeight / 2 - 360; y < bHeight/2 + 360; y = y + 40) {
            for (int x = bWidth / 2 - 360; x < bWidth/2 + 360; x = x + 40) {
                float total = 0, ctr=0, rst = 0;
                for(int j=0; j<40; j++) {
                    for(int i=0; i<40; i++) {
                        int index = (j + y) * bWidth + i + x;
                        total += hsv[index][ch];
                    }
                }
                for(int j=15; j<25; j++) {
                    for(int i=15; i<25; i++) {
                        int index = (j + y) * bWidth + i + x;
                        ctr += hsv[index][ch];
                    }
                }
                rst = total - ctr;
                ctr = (float) (ctr/100.0);
                rst = (float) (rst/1500.0);

                if((counter+1)%9 == 0) {
                    bString += ' ';
                } else if (ctr > rst) {
                    bString += '1';
                } else if (ctr < rst) {
                    bString += '0';
                }
                counter++;
            }
        }


        System.out.println("bString: " + bString);
        //Binary to string
        String[] code = bString.split(" ");
        String word = "";
        for(int i=0; i<code.length; i++) {
            word += (char)Integer.parseInt(code[i],2);
        }
        return word;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RESULT_LOAD_IMG && resultCode==RESULT_OK && data!=null){
            try {
                final Uri imageUri = data.getData();
                final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                bmp = BitmapFactory.decodeStream(imageStream);
                Drawable drawable = new BitmapDrawable(getResources(), bmp);
                if(mCameraImage.getDrawable() != null) ((BitmapDrawable)mCameraImage.getDrawable()).getBitmap().recycle();
                mCameraImage.setImageDrawable(drawable);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(RevealActivity.this, "Something went wrong",Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(RevealActivity.this, "You haven't picked image",Toast.LENGTH_LONG).show();
        }
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Calculates and returns optimal preview size from supported by each device.
     */
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        Camera.Size optimalSize = null;

        for (Camera.Size size : sizes) {
            if ((size.width <= width && size.height <= height) || (size.height <= width && size.width <= height)) {
                if (optimalSize == null) {
                    optimalSize = size;
                } else {
                    int resultArea = optimalSize.width * optimalSize.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) {
                        optimalSize = size;
                    }
                }
            }
        }

        return optimalSize;
    }
}