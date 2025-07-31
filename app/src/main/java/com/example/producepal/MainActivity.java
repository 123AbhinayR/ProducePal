package com.example.producepal;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import androidx.core.content.FileProvider;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 3;
    private static final int GALLERY_REQUEST_CODE = 1;
    private static final int PERMISSION_CODE = 100;

    Button btnCamera, btnGallery, btnSearch;
    ImageView imageView;
    TextView tvResult;
    int imageSize = 100;
    List<String> labels;
    String currentPrediction = null;

    private Uri photoUri;
    private File photoFile;

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "ProducePal_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCamera = findViewById(R.id.btnCapture);
        btnGallery = findViewById(R.id.btnGallery);
        btnSearch = findViewById(R.id.btnSearch);
        tvResult = findViewById(R.id.tvResult);
        imageView = findViewById(R.id.imageView);

        try {
            labels = loadLabels("labels.txt");
            Log.d("Labels", "Loaded " + labels.size() + " labels");
            if (labels.size() != 206) {
                Toast.makeText(this, "Warning: Expected 206 labels, found " + labels.size(), Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load labels", Toast.LENGTH_LONG).show();
            labels = new ArrayList<>();
        }

        btnCamera.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    photoFile = createImageFile();
                    photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
                } catch (IOException e) {
                    Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show();
                }
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
            }
        });


        btnGallery.setOnClickListener(v -> {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
        });

        btnSearch.setOnClickListener(v -> {
            if (currentPrediction != null && !currentPrediction.isEmpty()) {
                String query = currentPrediction + " rotten vs healthy";
                String url = "https://www.google.com/search?tbm=isch&q=" + Uri.encode(query);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please classify a fruit first", Toast.LENGTH_SHORT).show();
            }
        });

        btnSearch.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Bitmap image = null;

            if (requestCode == CAMERA_REQUEST_CODE) {
                if (photoFile != null && photoFile.exists()) {
                    image = BitmapFactory.decodeFile(photoFile.getAbsolutePath());

                    // Save to gallery
                    MediaStore.Images.Media.insertImage(
                            getContentResolver(),
                            image,
                            "ProducePal_" + System.currentTimeMillis(),
                            "Captured using ProducePal"
                    );
                } else {
                    Toast.makeText(this, "Failed to load captured image", Toast.LENGTH_SHORT).show();
                }

            } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                Uri dat = data.getData();
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), dat);
                } catch (IOException e) {
                    Log.e("MainActivity", "Failed to load image from gallery", e);
                }
            }

            if (image != null) {
                imageView.setImageBitmap(image);
                Bitmap resizedImage = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(resizedImage);
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private void classifyImage(Bitmap image) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++];
                    float r = ((val >> 16) & 0xFF) / 255.0f;
                    float g = ((val >> 8) & 0xFF) / 255.0f;
                    float b = (val & 0xFF) / 255.0f;

                    byteBuffer.putFloat(r);
                    byteBuffer.putFloat(g);
                    byteBuffer.putFloat(b);
                }
            }

            float[] confidences = runModelInference(byteBuffer);

            int maxIdx = 0;
            float maxConf = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConf) {
                    maxConf = confidences[i];
                    maxIdx = i;
                }
            }

            String resultLabel = (labels != null && labels.size() > maxIdx) ? labels.get(maxIdx) : "Unknown";
            String displayText = String.format("%s (%.2f%%)", resultLabel, maxConf * 100);
            tvResult.setText(displayText);
            currentPrediction = resultLabel;
            btnSearch.setEnabled(true);

            Log.d("Prediction", "Predicted: " + resultLabel + " (Confidence: " + maxConf + ")");
        } catch (Exception e) {
            Log.e("MainActivity", "Model inference failed", e);
            Toast.makeText(this, "Model inference failed", Toast.LENGTH_SHORT).show();
        }
    }


    private float[] runModelInference(ByteBuffer inputBuffer) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        options.setUseNNAPI(true);
        Interpreter tflite = new Interpreter(loadModelFile("ml/FruitsML.tflite"), options);

        float[][] output = new float[1][206]; // Ensure matches model output
        tflite.run(inputBuffer, output);
        tflite.close();
        return output[0];
    }

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels(String filename) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(filename)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
