package com.example.producepal;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import com.example.producepal.ml.Fruits;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 3;
    private static final int GALLERY_REQUEST_CODE = 1;
    private static final int PERMISSION_CODE = 100;

    Button btnCamera, btnGallery, btnSearch;
    ImageView imageView;
    TextView tvResult;
    int imageSize = 32;
    List<String> labels;
    String currentPrediction = null;

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
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load labels", Toast.LENGTH_LONG).show();
            labels = new ArrayList<>();
        }

        btnCamera.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
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
                image = (Bitmap) data.getExtras().get("data");
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
            } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                Uri dat = data.getData();
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), dat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (image != null) {
                imageView.setImageBitmap(image);
                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }
        }
    }

    private void classifyImage(Bitmap image) {
        try {
            Fruits model = Fruits.newInstance(getApplicationContext());

            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, imageSize, imageSize, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF));
                    byteBuffer.putFloat(((val >> 8) & 0xFF));
                    byteBuffer.putFloat((val & 0xFF));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            Fruits.Outputs outputs = model.process(inputFeature0);
            float[] confidences = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();

            int maxIdx = 0;
            float maxConf = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConf) {
                    maxConf = confidences[i];
                    maxIdx = i;
                }
            }

            String resultLabel = (labels != null && labels.size() > maxIdx) ? labels.get(maxIdx) : "Unknown";
            tvResult.setText(resultLabel);
            currentPrediction = resultLabel;
            btnSearch.setEnabled(true);

            model.close();
        } catch (IOException e) {
            Toast.makeText(this, "Model inference failed", Toast.LENGTH_SHORT).show();
        }
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
}
