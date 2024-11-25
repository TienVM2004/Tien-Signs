//package com.example.tiensigns;
//
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.graphics.Matrix;
//import android.os.Bundle;
//import android.provider.MediaStore;
//import androidx.appcompat.app.AppCompatActivity;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.FrameLayout;
//import android.widget.TextView;
//
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.exifinterface.media.ExifInterface;
//// ContentResolver dependency
//import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
//import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
//import com.google.mediapipe.solutioncore.CameraInput;
//import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
//import com.google.mediapipe.solutioncore.VideoInput;
//import com.google.mediapipe.solutions.hands.HandLandmark;
//import com.google.mediapipe.solutions.hands.Hands;
//import com.google.mediapipe.solutions.hands.HandsOptions;
//import com.google.mediapipe.solutions.hands.HandsResult;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Deque;
//import java.util.List;
//
//import org.tensorflow.lite.Interpreter;
//import org.tensorflow.lite.support.common.FileUtil;
//
///** Main activity of MediaPipe Hands app. */
//public class MainActivity extends AppCompatActivity {
//    private static final String TAG = "MainActivity";
//
//    private Interpreter tflite;
//    private String[] labels;
//    private Deque<String> detectedSigns = new ArrayDeque<>(50);
//    // Variables to keep track of the current sign and the time it was first detected
//    private String currentSign = null;
//    private long signStartTime = 0;
//    private long lastHandDetectedTime = 0;
//    private static final long SIGN_HOLD_THRESHOLD = 1000; // 1 second in milliseconds
//    private static final long NO_SIGN_THRESHOLD = 2000; // 2 seconds in milliseconds
//    // TextViews to display the current sign and the queue of registered signs
//    private TextView textInferredLetter;
//    private TextView textDetectedSignsQueue;
//    // Arrays for TFLite input and output to avoid reallocating them every frame
//    private float[][] inputArray;
//    private float[][] outputArray;
//    private Hands hands;
//    // Run the pipeline and the model inference on GPU or CPU.
//    private static final boolean RUN_ON_GPU = true;
//
//    private enum InputSource {
//        UNKNOWN,
//        IMAGE,
//        VIDEO,
//        CAMERA,
//    }
//    private InputSource inputSource = InputSource.UNKNOWN;
//
//    // Image demo UI and image loader components.
//    private ActivityResultLauncher<Intent> imageGetter;
//    private HandsResultImageView imageView;
//    // Video demo UI and video loader components.
//    private VideoInput videoInput;
//    private ActivityResultLauncher<Intent> videoGetter;
//    // Live camera demo UI and camera components.
//    private CameraInput cameraInput;
//
//    private SolutionGlSurfaceView<HandsResult> glSurfaceView;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        initializeInterpreter();
//        loadLabels();
//        textInferredLetter = findViewById(R.id.text_inferred_letter);
//        textDetectedSignsQueue = findViewById(R.id.text_detected_signs_queue);
//        imageView = new HandsResultImageView(this);
//        setupLiveDemoUiComponents();
//
//        inputArray = new float[1][42]; // 21 landmarks x 2 coordinates (x, y)
//        outputArray = new float[1][labels.length];
//    }
//
//    private void initializeInterpreter() {
//        try {
//            tflite = new Interpreter(FileUtil.loadMappedFile(this, "keypoint_classifier.tflite"));
//        } catch (Exception e) {
//            Log.e(TAG, "Error initializing TensorFlow Lite interpreter: " + e.getMessage());
//        }
//    }
//
//    private void loadLabels() {
//        try {
//            // Open the CSV file from the assets folder
//            InputStream is = getAssets().open("keypoint_classifier_label.csv");
//            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
//
//            // Read each line from the file and add it to a list
//            List<String> labelList = new ArrayList<>();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                // Add only non-empty lines, trimming whitespace
//                if (!line.trim().isEmpty()) {
//                    labelList.add(line.trim());
//                }
//            }
//            reader.close();
//
//            // Convert the list to an array
//            labels = labelList.toArray(new String[0]);
//
//            // Log the loaded labels for debugging
//            Log.i(TAG, "Labels loaded: " + Arrays.toString(labels));
//        } catch (IOException e) {
//            Log.e(TAG, "Error reading label file: " + e.getMessage());
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (inputSource == InputSource.CAMERA) {
//            // Restarts the camera and the opengl surface rendering.
//            cameraInput = new CameraInput(this);
//            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
//            glSurfaceView.post(this::startCamera);
//            glSurfaceView.setVisibility(View.VISIBLE);
//        } else if (inputSource == InputSource.VIDEO) {
//            videoInput.resume();
//        }
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (inputSource == InputSource.CAMERA) {
//            glSurfaceView.setVisibility(View.GONE);
//            cameraInput.close();
//        } else if (inputSource == InputSource.VIDEO) {
//            videoInput.pause();
//        }
//    }
//
//    /** Sets up the UI components for the live demo with camera input. */
//    private void setupLiveDemoUiComponents() {
//        Button startCameraButton = findViewById(R.id.button_start_camera);
//        startCameraButton.setOnClickListener(
//                v -> {
//                    if (inputSource == InputSource.CAMERA) {
//                        return;
//                    }
//                    stopCurrentPipeline();
//                    setupStreamingModePipeline(InputSource.CAMERA);
//                });
//    }
//
//    /** Sets up core workflow for streaming mode. */
//    private void setupStreamingModePipeline(InputSource inputSource) {
//        this.inputSource = inputSource;
//        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
//        hands =
//                new Hands(
//                        this,
//                        HandsOptions.builder()
//                                .setStaticImageMode(false)
//                                .setMaxNumHands(2)
//                                .setRunOnGpu(RUN_ON_GPU)
//                                .build());
//        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));
//
//        if (inputSource == InputSource.CAMERA) {
//            cameraInput = new CameraInput(this);
//            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
//        } else if (inputSource == InputSource.VIDEO) {
//            videoInput = new VideoInput(this);
//            videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
//        }
//
//        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
//        glSurfaceView =
//                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
//        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
//        glSurfaceView.setRenderInputImage(true);
//        hands.setResultListener(
//                handsResult -> {
//                    glSurfaceView.setRenderData(handsResult);
//                    glSurfaceView.requestRender();
//                    if (!handsResult.multiHandLandmarks().isEmpty()) {
//                        List<float[]> landmarkList = extractLandmarkList(handsResult);
//
//                        float[] preprocessedLandmarks = preProcessLandmark(landmarkList);
//
//                        float[][] inputArray = new float[1][preprocessedLandmarks.length];
//                        inputArray[0] = preprocessedLandmarks;
//
//                        int NUM_CLASSES = labels.length;
//                        float[][] outputArray = new float[1][NUM_CLASSES];
//
//                        tflite.run(inputArray, outputArray);
//
//                        float[] probabilities = outputArray[0];
//                        int resultIndex = getMaxIndex(probabilities);
//
//                        String detectedSign = labels[resultIndex];
//                        long currentTime = System.currentTimeMillis();
//
//                        // Update the TextView with the current detected sign
//                        runOnUiThread(() -> {
//                            textInferredLetter.setText(detectedSign);
//                        });
//
//                        if (detectedSign.equals(currentSign)) {
//                            if (currentTime - signStartTime >= SIGN_HOLD_THRESHOLD) {
//                                // Add the sign to the queue if held for more than 1 second
//                                if (detectedSigns.size() == 50) {
//                                    detectedSigns.pollFirst(); // Remove the oldest sign
//                                }
//                                detectedSigns.addLast(detectedSign);
//
//                                // Build the string from the queue
//                                StringBuilder sb = new StringBuilder();
//                                for (String s : detectedSigns) {
//                                    sb.append(s);
//                                }
//                                String detectedSignsString = sb.toString();
//
//                                // Update the TextView to display the queue
//                                runOnUiThread(() -> {
//                                    textDetectedSignsQueue.setText(detectedSignsString);
//                                });
//
//                                // Reset the sign start time
//                                signStartTime = currentTime;
//                            }
//                        } else {
//                            // Update currentSign and reset signStartTime
//                            currentSign = detectedSign;
//                            signStartTime = currentTime;
//                        }
//                    } else {
//                        // No hand detected, clear the current sign and reset timers
//                        runOnUiThread(() -> {
//                            textInferredLetter.setText("");
//                        });
//                        currentSign = null;
//                        signStartTime = 0;
//                    }
//                });
//
//        // The runnable to start camera after the gl surface view is attached.
//        // For video input source, videoInput.start() will be called when the video uri is available.
//        if (inputSource == InputSource.CAMERA) {
//            glSurfaceView.post(this::startCamera);
//        }
//
//        // Updates the preview layout.
//        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
//        imageView.setVisibility(View.GONE);
//        frameLayout.removeAllViewsInLayout();
//        frameLayout.addView(glSurfaceView);
//        glSurfaceView.setVisibility(View.VISIBLE);
//        frameLayout.requestLayout();
//    }
//
//    private List<float[]> extractLandmarkList(HandsResult handsResult) {
//        List<float[]> landmarkPoint = new ArrayList<>();
//        int imageWidth = handsResult.inputBitmap().getWidth();
//        int imageHeight = handsResult.inputBitmap().getHeight();
//
//        for (NormalizedLandmark landmark : handsResult.multiHandLandmarks().get(0).getLandmarkList()) {
//            float landmarkX = Math.min(landmark.getX() * imageWidth, imageWidth - 1);
//            float landmarkY = Math.min(landmark.getY() * imageHeight, imageHeight - 1);
//            landmarkPoint.add(new float[]{landmarkX, landmarkY});
//        }
//        return landmarkPoint;
//    }
//
//    private float[] preProcessLandmark(List<float[]> landmarkList) {
//        List<float[]> tempLandmarkList = new ArrayList<>(landmarkList);
//
//        // Step 1: Normalize landmarks by subtracting the base point (first landmark)
//        float baseX = tempLandmarkList.get(0)[0];
//        float baseY = tempLandmarkList.get(0)[1];
//        for (int i = 0; i < tempLandmarkList.size(); i++) {
//            tempLandmarkList.get(i)[0] -= baseX;
//            tempLandmarkList.get(i)[1] -= baseY;
//        }
//
//        // Step 2: Flatten the list
//        List<Float> flattenedList = new ArrayList<>();
//        for (float[] point : tempLandmarkList) {
//            flattenedList.add(point[0]);
//            flattenedList.add(point[1]);
//        }
//
//        // Step 3: Normalize to -1 to 1 range
//        float maxAbsValue = 0;
//        for (Float value : flattenedList) {
//            maxAbsValue = Math.max(maxAbsValue, Math.abs(value));
//        }
//        final float normalizationFactor = maxAbsValue == 0 ? 1 : maxAbsValue;
//
//        for (int i = 0; i < flattenedList.size(); i++) {
//            flattenedList.set(i, flattenedList.get(i) / normalizationFactor);
//        }
//
//        // Convert List<Float> to float[]
//        float[] preprocessedLandmarks = new float[flattenedList.size()];
//        for (int i = 0; i < flattenedList.size(); i++) {
//            preprocessedLandmarks[i] = flattenedList.get(i);
//        }
//
//        return preprocessedLandmarks;
//    }
//
//    private int getMaxIndex(float[] probabilities) {
//        int maxIndex = 0;
//        float maxProbability = probabilities[0];
//        for (int i = 1; i < probabilities.length; i++) {
//            if (probabilities[i] > maxProbability) {
//                maxProbability = probabilities[i];
//                maxIndex = i;
//            }
//        }
//        return maxIndex;
//    }
//
//    private void startCamera() {
//        cameraInput.start(
//                this,
//                hands.getGlContext(),
//                CameraInput.CameraFacing.BACK,
//                glSurfaceView.getWidth(),
//                glSurfaceView.getHeight());
//    }
//
//    private void stopCurrentPipeline() {
//        if (cameraInput != null) {
//            cameraInput.setNewFrameListener(null);
//            cameraInput.close();
//        }
//        if (videoInput != null) {
//            videoInput.setNewFrameListener(null);
//            videoInput.close();
//        }
//        if (glSurfaceView != null) {
//            glSurfaceView.setVisibility(View.GONE);
//        }
//        if (hands != null) {
//            hands.close();
//        }
//    }
//
//    private void logWristLandmark(HandsResult result, boolean showPixelValues) {
//        if (result.multiHandLandmarks().isEmpty()) {
//            return;
//        }
//        NormalizedLandmark wristLandmark =
//                result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
//        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
//        if (showPixelValues) {
//            int width = result.inputBitmap().getWidth();
//            int height = result.inputBitmap().getHeight();
//            Log.i(
//                    TAG,
//                    String.format(
//                            "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
//                            wristLandmark.getX() * width, wristLandmark.getY() * height));
//        } else {
//            Log.i(
//                    TAG,
//                    String.format(
//                            "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
//                            wristLandmark.getX(), wristLandmark.getY()));
//        }
//        if (result.multiHandWorldLandmarks().isEmpty()) {
//            return;
//        }
//        Landmark wristWorldLandmark =
//                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
//        Log.i(
//                TAG,
//                String.format(
//                        "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
//                                + " approximate geometric center): x=%f m, y=%f m, z=%f m",
//                        wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
//    }
//}


package com.example.tiensigns;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;
// ContentResolver dependency
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import io.github.cdimascio.dotenv.Dotenv;

import com.google.ai.client.generativeai.GenerativeModel;


/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static String GEMINI_API_KEY = "";
    private static String GEMINI_API_URL = "";

    private Interpreter tflite;
    private String[] labels;
    private int MAX_QUEUE_LENGTH = 50;
    private Deque<String> detectedSigns = new ArrayDeque<>(MAX_QUEUE_LENGTH);
    // Variables to keep track of the current sign and the time it was first detected
    private String currentSign = null;
    private long signStartTime = 0;
    private long lastHandDetectedTime = 0;
    private static final long SIGN_HOLD_THRESHOLD = 1000; // 1 second in milliseconds
    private static final long NO_SIGN_THRESHOLD = 2000; // 2 seconds in milliseconds
    private static final long PROCESS_SIGN_THRESHOLD = 3000; // 3 seconds
    // TextViews to display the current sign and the queue of registered signs
    private TextView textInferredLetter;
    private TextView textDetectedSignsQueue;
    // Arrays for TFLite input and output to avoid reallocating them every frame
    private float[][] inputArray;
    private float[][] outputArray;


    private Hands hands;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    private enum InputSource {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA,
    }
    private InputSource inputSource = InputSource.UNKNOWN;

    // Image demo UI and image loader components.
    private ActivityResultLauncher<Intent> imageGetter;
    private HandsResultImageView imageView;
    // Video demo UI and video loader components.
    private VideoInput videoInput;
    private ActivityResultLauncher<Intent> videoGetter;
    // Live camera demo UI and camera components.
    private CameraInput cameraInput;

    private SolutionGlSurfaceView<HandsResult> glSurfaceView;
    private GenerativeModelFutures generativeModelFutures = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeInterpreter();
        loadLabels();
        textInferredLetter = findViewById(R.id.text_inferred_letter);
        textDetectedSignsQueue = findViewById(R.id.text_detected_signs_queue);
        imageView = new HandsResultImageView(this);
        setupLiveDemoUiComponents();
        Dotenv dotenv = Dotenv.configure()
                .directory("/assets")
                .filename("env") // instead of '.env', use 'env'
                .load();
        GEMINI_API_KEY = dotenv.get("GEMINI_API_KEY");
        GEMINI_API_URL = dotenv.get("GEMINI_API_URL");
        Log.i("EnvConfig", "API Key: " + GEMINI_API_KEY);
        Log.i("EnvConfig", "API URL: " + GEMINI_API_URL);
        GenerativeModel gm = new GenerativeModel(
                /* modelName */ "gemini-1.5-flash",
                /* apiKey */ GEMINI_API_KEY);
        this.generativeModelFutures = GenerativeModelFutures.from(gm);
        inputArray = new float[1][42]; // 21 landmarks x 2 coordinates (x, y)
        outputArray = new float[1][labels.length];
    }

    private void initializeInterpreter() {
        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(this, "keypoint_classifier.tflite"));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TensorFlow Lite interpreter: " + e.getMessage());
        }
    }

    private void loadLabels() {
        try {
            // Open the CSV file from the assets folder
            InputStream is = getAssets().open("keypoint_classifier_label.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // Read each line from the file and add it to a list
            List<String> labelList = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // Add only non-empty lines, trimming whitespace
                if (!line.trim().isEmpty()) {
                    labelList.add(line.trim());
                }
            }
            reader.close();

            // Convert the list to an array
            labels = labelList.toArray(new String[0]);

            // Log the loaded labels for debugging
            Log.i(TAG, "Labels loaded: " + Arrays.toString(labels));
        } catch (IOException e) {
            Log.e(TAG, "Error reading label file: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.pause();
        }
    }

    /** Sets up the UI components for the live demo with camera input. */
    private void setupLiveDemoUiComponents() {
        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setOnClickListener(
                v -> {
                    if (inputSource == InputSource.CAMERA) {
                        return;
                    }
                    stopCurrentPipeline();
                    setupStreamingModePipeline(InputSource.CAMERA);
                });
    }

    /** Sets up core workflow for streaming mode. */
    private void setupStreamingModePipeline(InputSource inputSource) {
        this.inputSource = inputSource;
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(false)
                                .setMaxNumHands(2)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        if (inputSource == InputSource.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        } else if (inputSource == InputSource.VIDEO) {
            videoInput = new VideoInput(this);
            videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        }

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);

        // Set the result listener to the refactored method
        hands.setResultListener(this::processHandsResult);

        // The runnable to start camera after the gl surface view is attached.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        imageView.setVisibility(View.GONE);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void processHandsResult(HandsResult handsResult) {
        glSurfaceView.setRenderData(handsResult);
        glSurfaceView.requestRender();

        long currentTime = System.currentTimeMillis();

        if (!handsResult.multiHandLandmarks().isEmpty()) {
            lastHandDetectedTime = currentTime; // Update the last time hands were detected
            String detectedSign = performInference(handsResult);

            updateDetectedSigns(detectedSign, currentTime);

            // Update the TextView with the current detected sign
            runOnUiThread(() -> {
                textInferredLetter.setText(detectedSign);
            });

        } else {
            // No hand detected
            handleNoHandsDetected(currentTime);
        }
    }

    private String performInference(HandsResult handsResult) {
        List<float[]> landmarkList = extractLandmarkList(handsResult);

        float[] preprocessedLandmarks = preProcessLandmark(landmarkList);

        // Copy preprocessedLandmarks into inputArray[0]
        System.arraycopy(preprocessedLandmarks, 0, inputArray[0], 0, preprocessedLandmarks.length);

        // Run inference
        tflite.run(inputArray, outputArray);

        float[] probabilities = outputArray[0];
        int resultIndex = getMaxIndex(probabilities);

        String detectedSign = labels[resultIndex];
        return detectedSign;
    }

    private void updateDetectedSigns(String detectedSign, long currentTime) {
        if (detectedSign.equals(currentSign)) {
            if (currentTime - signStartTime >= SIGN_HOLD_THRESHOLD) {
                // Add the sign to the queue if held for more than 1 second
                if (detectedSigns.size() == MAX_QUEUE_LENGTH) {
                    detectedSigns.pollFirst(); // Remove the oldest sign
                }
                detectedSigns.addLast(detectedSign);

                updateDetectedSignsTextView();

                // Reset the sign start time
                signStartTime = currentTime;
            }
        } else {
            // Update currentSign and reset signStartTime
            currentSign = detectedSign;
            signStartTime = currentTime;
        }
    }

    private void updateDetectedSignsTextView() {
        StringBuilder sb = new StringBuilder();
        for (String s : detectedSigns) {
            sb.append(s);
        }
        String detectedSignsString = sb.toString();

        // Update the TextView to display the queue
        runOnUiThread(() -> {
            textDetectedSignsQueue.setText(detectedSignsString);
        });
    }

    private void handleNoHandsDetected(long currentTime) {
        // Update the UI to clear the current sign
        runOnUiThread(() -> {
            textInferredLetter.setText("");
        });

        currentSign = null;
        signStartTime = 0;

        if (lastHandDetectedTime == 0) {
            // Initialize lastHandDetectedTime if it's zero
            lastHandDetectedTime = currentTime;
            return;
        }

        long timeSinceLastHandDetected = currentTime - lastHandDetectedTime;

        // Check if more than PROCESS_SIGN_THRESHOLD has passed since last hand detection
        if (timeSinceLastHandDetected >= PROCESS_SIGN_THRESHOLD) {
            Log.i("ProcessQueue", "processDetectedSignsQueue called");
            // Trigger the API call to process the queue
            processDetectedSignsQueue();
            // Reset lastHandDetectedTime to prevent multiple processing
            lastHandDetectedTime = 0;
        }
        // Check if more than NO_SIGN_THRESHOLD has passed since last hand detection
        else if (timeSinceLastHandDetected >= NO_SIGN_THRESHOLD) {
            // Add "_" to the queue as a space if the last element is not "_"
            if (detectedSigns.isEmpty() || !detectedSigns.peekLast().equals("_")) {
                if (detectedSigns.size() == MAX_QUEUE_LENGTH) {
                    detectedSigns.pollFirst();
                }
                detectedSigns.addLast("_");
                updateDetectedSignsTextView();
            }
            // Do not reset lastHandDetectedTime here
        }
    }


    private void processDetectedSignsQueue() {
        Log.i("ProcessQueue", "processDetectedSignsQueue called");
        // Get the detected sentence from the queue
        String detectedSentence = String.join("", detectedSigns);

        // Prepare the prompt
        String prompt = "You are a well-made Sign Language Processor. Your task is to receive a string of Sign Language, which has the form of \"AN_UPPER_CASED_AND_SNAKE_CASED_STRING\", and interpret what the string means. You should be able to interpret the string and convert it into a human-readable format.\n\nFor example, if you receive the string \"HELLO_WORLD_\", you should be able to interpret it as \"Hello, World!\".\n\nHere are a few rules that you must strictly follow: The given string is most likely not syntactically correct, hence you should not rely on the correctness of the string. You should be able to interpret the string even if it is not syntactically correct. Some errors that may exist in the string include the appearance of 1 or many characters that disrupt the syntax of a word, the reappearance of many characters that do not belong to the syntax of a word, or the absence of a character that is necessary for the syntax of a word. In sign language, the sign of some letters may be similar, which leads to errors in detection. Here are some pairs of letters that may be used mistakenly in place of each other: \"S\" and \"T\", \"S\" and \"A\", \"M\" and \"A\", \"M\" and \"N\", \"R\" and \"U\".\n\nHere are a few examples of errors that may exist in the string, along with how you should interpret them:\n\"HHHEEELLLLOOO_WOOOORRRLLD_\" -> \"HELLO_WORLD_\"\n\"I_MMISTSS_YOUUU_\" -> \"I_MISS_YOU_\"\n\"CCCCCCC_HOWWW_ARE_YOUUU_\" -> \"HOW_ARE_YOU_\"\n\"THIMK_ILL_NISST_YOU_\" -> \"THINK_ILL_MISS_YOU_\"\n\n. Always remember one extra '_' token at the end of your interpretation. Finally, you must only return the corrected string, and no other information. Answer with the corrected string only, do not include any other information in your answer. Here is your string:\n" + detectedSentence;

        // Send the prompt and detected sentence to the Gemini API
        Log.i("yes", prompt);
        sendPromptToGemini(prompt);
    }

    private void sendPromptToGemini(String prompt) {
        // Use a single-threaded executor for handling the asynchronous call
        Executor executor = Executors.newSingleThreadExecutor();

        // Create a content object with the prompt
        Content content = new Content.Builder().addText(prompt).build();

        // Call the generative model to generate content
        ListenableFuture<GenerateContentResponse> response = generativeModelFutures.generateContent(content);

        // Add a callback to handle success and failure
        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(@Nullable GenerateContentResponse result) {
                        if (result != null) {
                            String generatedText = result.getText();

                            // Remove redundant spaces from the generated text
                            String cleanedText = generatedText.replaceAll("\\s+", "");

                            // Update the detectedSigns queue and UI on the main thread
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> {
                                // Clear the detectedSigns queue
                                detectedSigns.clear();

                                // Redistribute characters of the cleaned text into the queue
                                for (char c : cleanedText.toCharArray()) {
                                    if (detectedSigns.size() == MAX_QUEUE_LENGTH) {
                                        detectedSigns.pollFirst(); // Remove the oldest element
                                    }
                                    detectedSigns.addLast(String.valueOf(c)); // Add the new character
                                }

                                // Update the UI to reflect the changes in the queue
                                updateDetectedSignsTextView();
                            });
                        } else {
                            Log.e("GeminiAPI", "Received null response from the API.");
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Log the error
                        Log.e("GeminiAPI", "Error in generating content: " + t.getMessage());
                        t.printStackTrace();
                    }
                },
                executor
        );
    }




    private List<float[]> extractLandmarkList(HandsResult handsResult) {
        List<float[]> landmarkPoint = new ArrayList<>();
        int imageWidth = handsResult.inputBitmap().getWidth();
        int imageHeight = handsResult.inputBitmap().getHeight();

        for (NormalizedLandmark landmark : handsResult.multiHandLandmarks().get(0).getLandmarkList()) {
            float landmarkX = Math.min(landmark.getX() * imageWidth, imageWidth - 1);
            float landmarkY = Math.min(landmark.getY() * imageHeight, imageHeight - 1);
            landmarkPoint.add(new float[]{landmarkX, landmarkY});
        }
        return landmarkPoint;
    }

    private float[] preProcessLandmark(List<float[]> landmarkList) {
        List<float[]> tempLandmarkList = new ArrayList<>(landmarkList);

        // Step 1: Normalize landmarks by subtracting the base point (first landmark)
        float baseX = tempLandmarkList.get(0)[0];
        float baseY = tempLandmarkList.get(0)[1];
        for (int i = 0; i < tempLandmarkList.size(); i++) {
            tempLandmarkList.get(i)[0] -= baseX;
            tempLandmarkList.get(i)[1] -= baseY;
        }

        // Step 2: Flatten the list
        List<Float> flattenedList = new ArrayList<>();
        for (float[] point : tempLandmarkList) {
            flattenedList.add(point[0]);
            flattenedList.add(point[1]);
        }

        // Step 3: Normalize to -1 to 1 range
        float maxAbsValue = 0;
        for (Float value : flattenedList) {
            maxAbsValue = Math.max(maxAbsValue, Math.abs(value));
        }
        final float normalizationFactor = maxAbsValue == 0 ? 1 : maxAbsValue;

        for (int i = 0; i < flattenedList.size(); i++) {
            flattenedList.set(i, flattenedList.get(i) / normalizationFactor);
        }

        // Convert List<Float> to float[]
        float[] preprocessedLandmarks = new float[flattenedList.size()];
        for (int i = 0; i < flattenedList.size(); i++) {
            preprocessedLandmarks[i] = flattenedList.get(i);
        }

        return preprocessedLandmarks;
    }

    private int getMaxIndex(float[] probabilities) {
        int maxIndex = 0;
        float maxProbability = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxProbability) {
                maxProbability = probabilities[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private void startCamera() {
        cameraInput.start(
                this,
                hands.getGlContext(),
                CameraInput.CameraFacing.BACK,
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (videoInput != null) {
            videoInput.setNewFrameListener(null);
            videoInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }

    private void logWristLandmark(HandsResult result, boolean showPixelValues) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        NormalizedLandmark wristLandmark =
                result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            int width = result.inputBitmap().getWidth();
            int height = result.inputBitmap().getHeight();
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                            wristLandmark.getX() * width, wristLandmark.getY() * height));
        } else {
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                            wristLandmark.getX(), wristLandmark.getY()));
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return;
        }
        Landmark wristWorldLandmark =
                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        Log.i(
                TAG,
                String.format(
                        "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                                + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                        wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
    }
}