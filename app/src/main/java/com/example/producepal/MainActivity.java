package com.example.producepal;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private Button btnCapture, btnSearch;
    private TextView tvResult;
    private ImageCapture imageCapture;
    private Interpreter interpreter;
    private List<String> labels;
    private ExecutorService cameraExecutor;
    private String identifiedFruit = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        btnSearch = findViewById(R.id.btnSearch);
        tvResult = findViewById(R.id.tvResult);

        btnSearch.setEnabled(false); // disabled until fruit identified

        try {
            interpreter = new Interpreter(loadModelFile("fruits.tflite"));
            labels = loadLabels("labels.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }

        startCamera();
        cameraExecutor = Executors.newSingleThreadExecutor();

        btnCapture.setOnClickListener(v -> captureImage());

        btnSearch.setOnClickListener(v -> {
            if (identifiedFruit != null) {
                String query = identifiedFruit + " healthy vs rotten";
                Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query) + "&tbm=isch");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        File photoFile = new File(getExternalFilesDir(null), "pic.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                classifyImage(bitmap);
            }

            @Override
            public void onError(ImageCaptureException exception) {
                exception.printStackTrace();
            }
        });
    }

    private void classifyImage(Bitmap bitmap) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
        float[][][][] input = new float[1][100][100][3];
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                int pixel = scaled.getPixel(x, y);
                input[0][x][y][0] = ((pixel >> 16) & 0xFF) / 255f;
                input[0][x][y][1] = ((pixel >> 8) & 0xFF) / 255f;
                input[0][x][y][2] = (pixel & 0xFF) / 255f;
            }
        }

        float[][] output = new float[1][labels.size()];
        interpreter.run(input, output);

        int maxIdx = 0;
        float maxProb = 0;
        for (int i = 0; i < labels.size(); i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                maxIdx = i;
            }
        }

        identifiedFruit = labels.get(maxIdx);
        tvResult.setText(identifiedFruit);

        btnSearch.setEnabled(true); // Enable the search button now that fruit is identified
    }

    private MappedByteBuffer loadModelFile(String modelFile) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd(modelFile).getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd(modelFile).getStartOffset();
        long declaredLength = getAssets().openFd(modelFile).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels(String filename) throws IOException {
        List<String> labelList = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open(filename)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        }
        return labelList;
    }
}
