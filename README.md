# ProducePal

An Android app that identifies fruits and vegetables from a photo and classifies their condition, using an on-device TensorFlow Lite model. No network connection is needed for inference.

## How it works

1. The user captures a photo with the camera or selects one from the gallery.
2. The bitmap is scaled down to 100x100 and converted into a `ByteBuffer` of normalized RGB float values.
3. The buffer is fed into a TensorFlow Lite `Interpreter`, which runs a model bundled in the app's assets.
4. The model outputs a confidence score across 206 classes, covering different produce types, varieties, and conditions (for example, distinguishing `Apple Golden` from `Apple Rotten`, or `Avocado ripe` from `Avocado Black`).
5. The highest-confidence class and its score are shown to the user.
6. A search button opens a web image search for the predicted label, so the user can visually compare their result.

## Technical details

- **Language:** Java
- **Min SDK:** 28, **Target/Compile SDK:** 36
- **ML runtime:** TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.17.0`), running fully on-device with NNAPI acceleration enabled and 4 inference threads
- **Model:** `app/src/main/assets/ml/FruitsML.tflite`, a classifier over 206 labels (`app/src/main/assets/labels.txt`), taking 100x100x3 normalized float input
- **Image capture:** Standard camera intent (`MediaStore.ACTION_IMAGE_CAPTURE`) with output routed through a `FileProvider`, plus a gallery picker intent as an alternative source
- **Permissions:** Camera, and legacy external storage read/write for saving and loading images
- **Other dependencies:** CameraX (imported but not yet wired into the capture flow), AndroidX AppCompat, Material Components, ConstraintLayout

## Project structure

```
app/src/main/java/.../MainActivity.java   - UI, image capture/selection, inference logic
app/src/main/assets/ml/FruitsML.tflite    - trained classifier
app/src/main/assets/labels.txt            - class labels, one per line
app/src/main/res/                         - layouts and resources
```

## Getting started

1. Clone the repository
2. Open the project in Android Studio
3. Let Gradle sync and build
4. Run on a device or emulator with a camera

## Possible next steps

- Wire up CameraX for an in-app live preview instead of the system camera intent
- Add unit tests around the classification and label-loading logic
- Surface a friendlier "freshness" summary instead of the raw label and confidence score
