package com.example.siyangzhang.steganography;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static android.graphics.Color.HSVToColor;
import static android.graphics.Color.colorToHSV;
import static android.support.v4.graphics.ColorUtils.HSLToColor;
import static android.support.v4.graphics.ColorUtils.colorToHSL;

/**
 * Created by siyangzhang on 7/16/17.
 */

public class EmbedActivity extends Activity {
    private float offset;
    private static int ch = 2;
    private static final String TAG = "EmbedActivity";
    private final int RESULT_LOAD_IMG = 1;
    private ImageView imageView;
    private Button embedBtn;
    private Button qrcodeBtn;
    private Bitmap bmp;
    private Bitmap ebmp;
    private Bitmap qrbmp;
    private EditText editText;
    private EditText editText2;
    private String message;
    private String content = "https://github.com/journeyapps/zxing-android-embedded";

    private OnClickListener imageViewOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG);
        }
    };

    private OnClickListener embedBtnOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            message = editText.getText().toString();
            try {
                embedMessage(bmp,message);
                File saveFile = openFileForImage();
                if (saveFile != null) {
                    saveImageToFile(saveFile);
                } else {
                    Toast.makeText(EmbedActivity.this, "Unable to open file for saving image.",
                            Toast.LENGTH_LONG).show();
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.d(TAG,"UnsupportedEncodingException");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embed);
        imageView = (ImageView) findViewById(R.id.imageView);
        imageView.setOnClickListener(imageViewOnClickListener);
        editText = (EditText) findViewById(R.id.editText);
        message = editText.getText().toString();
        editText2 = (EditText) findViewById(R.id.editText2);
        offset = Float.valueOf(editText2.getText().toString());
        embedBtn = (Button) findViewById(R.id.embed_btn);
        embedBtn.setOnClickListener(embedBtnOnClickListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        message = editText.getText().toString();
        offset = Float.valueOf(editText2.getText().toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RESULT_LOAD_IMG && resultCode==RESULT_OK && data!=null){
            try {
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri imageUri = data.getData();
                mediaScanIntent.setData(imageUri);
                this.sendBroadcast(mediaScanIntent);
                InputStream imageStream = getContentResolver().openInputStream(imageUri);
                Bitmap picked = BitmapFactory.decodeStream(imageStream);
                if(picked.getHeight()<800 || picked.getWidth()<800) {
                    picked = getResizedBitmap(picked, 800, 800);
                }
                bmp = Bitmap.createBitmap(picked, picked.getWidth()/2-400, picked.getHeight()/2-400, 800, 800);
                picked.recycle();
                Drawable drawable = new BitmapDrawable(getResources(), bmp);
                if(imageView.getDrawable() != null) ((BitmapDrawable)imageView.getDrawable()).getBitmap().recycle();
                imageView.setImageDrawable(drawable);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(EmbedActivity.this, "Something went wrong",Toast.LENGTH_LONG).show();
            }
        } else {
                Toast.makeText(EmbedActivity.this, "You haven't picked image",Toast.LENGTH_LONG).show();
            }
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

    /**
    Embed text message into bitmap with revised fujitsu method
     */
    private void embedMessage(Bitmap bitmap, String string) throws UnsupportedEncodingException {
        Toast.makeText(EmbedActivity.this, "Processing...", Toast.LENGTH_LONG).show();

        //String to binary
        String bString = "";
        String temp = "";
        while(string.length()<36) {
            string += ' ';
        }
        for(int i=0; i<string.length(); i++) {
            temp = Integer.toBinaryString(string.charAt(i));
            for(int j=temp.length();j<8;j++) {
                temp="0"+temp;
            }
            bString += temp + " ";
        }
        System.out.println(bString);

        ebmp = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int bWidth = ebmp.getWidth();
        int bHeight = ebmp.getHeight();
        int[] pixels = new int[bWidth*bHeight];
        float[][] hsv = new float[pixels.length][3];

        ebmp.getPixels(pixels, 0, bWidth, 0, 0, bWidth, bHeight);

        //Set location blocks
        for(int y=bHeight/2-400; y<bHeight/2-360; y++) {
            for(int x=bWidth/2-400; x<bWidth/2-360; x++) {
                int index = y*bWidth+x;
                pixels[index] = Color.BLACK;
            }
        }
        for(int y=bHeight/2-400; y<bHeight/2-360; y++) {
            for(int x=bWidth/2+360; x<bWidth/2+400; x++) {
                int index = y*bWidth+x;
                pixels[index] = Color.BLACK;
            }
        }
        for(int y=bHeight/2+360; y<bHeight/2+400; y++) {
            for(int x=bWidth/2-400; x<bWidth/2-360; x++) {
                int index = y*bWidth+x;
                pixels[index] = Color.BLACK;
            }
        }
        for(int y=bHeight/2+360; y<bHeight/2+400; y++) {
            for(int x=bWidth/2+360; x<bWidth/2+400; x++) {
                int index = y*bWidth+x;
                pixels[index] = Color.BLACK;
            }
        }

        //Get HSV from color
        for (int i = 0; i < pixels.length; i++) {
            colorToHSV(pixels[i], hsv[i]);
        }

        int counter = 0;
        for (int y = bHeight / 2 - 360; y < bHeight/2 + 360; y = y + 40) {
            for (int x = bWidth / 2 - 360; x < bWidth/2 + 360; x = x + 40) {
                float total = 0.0f, ctr=0.0f, rst = 0.0f;
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

                System.out.println("ctr: " + ctr + "  rst : " + rst);

                //Embed function
                if(counter<bString.length()) {
                    if (bString.charAt(counter) == '1' && ctr <= rst + offset) {
                        for (int j = 15; j < 25; j++) {
                            for (int i = 15; i < 25; i++) {
                                int index = (j + y) * bWidth + i + x;
                                hsv[index][ch] += (rst+offset) - ctr;
                            }
                        }
                    } else if (bString.charAt(counter) == '0' && ctr >= rst - offset) {
                        for (int j = 15; j < 25; j++) {
                            for (int i = 15; i < 25; i++) {
                                int index = (j + y) * bWidth + i + x;
                                hsv[index][ch] -= ctr - (rst-offset);
                            }
                        }
                    }
                    counter++;
                }
            }
        }

        //Restore color from HSV
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = HSVToColor(hsv[i]);
        }

        ebmp.setPixels(pixels, 0, bWidth, 0, 0, bWidth, bHeight);

        /**
         * Generate QR code from content string
         */
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            qrbmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    qrbmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
        } catch (WriterException e) {
            e.printStackTrace();
        }
        qrbmp = getResizedBitmap(qrbmp, 100, 100);
        ebmp = overlay(ebmp, qrbmp, 0, ebmp.getHeight()-qrbmp.getHeight());
        imageView.setImageBitmap(ebmp);

    }

    public static Bitmap overlay(Bitmap bmp1, Bitmap bmp2, int x, int y) {
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        canvas.drawBitmap(bmp2, x, y, null);
        return bmOverlay;
    }

    private File openFileForImage() {
        File imageDirectory = null;
        String storageState = Environment.getExternalStorageState();
        if (storageState.equals(Environment.MEDIA_MOUNTED)) {
            imageDirectory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Embedded");
            if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
                imageDirectory = null;
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM_dd_hh_mm_ss",
                        Locale.getDefault());

                return new File(imageDirectory.getPath() +
                        File.separator + editText.getText().toString() + editText2.getText().toString() + ".png");
            }
        }

        //MediaScannerConnection.scanFile(this, new String[] {imageDirectory.getPath() }, new String[] {"image/*"}, null);

        return null;
    }

    private void saveImageToFile(File file) {
        if (ebmp != null) {
            FileOutputStream outStream = null;
            try {
                outStream = new FileOutputStream(file);
                if (!ebmp.compress(Bitmap.CompressFormat.PNG, 100, outStream)) {
                    Toast.makeText(EmbedActivity.this, "Unable to save image to file(1).",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(EmbedActivity.this, "Saved image to: " + file.getPath(),
                            Toast.LENGTH_LONG).show();
                }
                outStream.close();
            } catch (Exception e) {
                Toast.makeText(EmbedActivity.this, "Unable to save image to file(2).",
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(this,
                    new String[] { file.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });
        }
    }

}
