package com.example.tiensigns;

import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import org.tensorflow.lite.Interpreter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.cdimascio.dotenv.Dotenv;

import static com.example.tiensigns.Constants.*;

public class MainActivity extends AppCompatActivity implements SwipeGestureListener.SwipeListener {

    private static final String TAG = Constants.TAG;

    private static String GEMINI_API_KEY = "";
    private static String GEMINI_API_URL = "";

    private Interpreter tflite;
    private String[] labels;

    private Deque<String> detectedSigns = new ArrayDeque<>(MAX_QUEUE_LENGTH);
    private String currentSign = null;
    private long signStartTime = 0;
    private long lastHandDetectedTime = 0;
    private boolean hasProcessedQueue = false;

    private GestureDetector gestureDetector;
    private boolean isManualVisible = false;

    private TextView textInferredLetter;
    private TextView textDetectedSignsQueue;

    private float[][] inputArray;
    private float[][] outputArray;

    private Hands hands;
    private InputSource inputSource = InputSource.UNKNOWN;

    private CameraInput cameraInput;
    private SolutionGlSurfaceView<HandsResult> glSurfaceView;

    private GeminiApiClient geminiApiClient;
    private HandProcessor handProcessor;
    private ModelHandler modelHandler;

    private String selectedLanguage = "English"; // Default language

    private CameraInput.CameraFacing cameraFacing = CameraInput.CameraFacing.BACK; // Default to back camera

    private enum InputSource {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA,
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showMainLayout(); // Initialize with main layout

        // Load environment variables
        Dotenv dotenv = Dotenv.configure()
                .directory("/assets")
                .filename("env")
                .load();
        GEMINI_API_KEY = dotenv.get("GEMINI_API_KEY");
        GEMINI_API_URL = dotenv.get("GEMINI_API_URL");
        Log.i("EnvConfig", "API Key: " + GEMINI_API_KEY);
        Log.i("EnvConfig", "API URL: " + GEMINI_API_URL);

        // Initialize GeminiApiClient
        geminiApiClient = new GeminiApiClient(GEMINI_API_KEY);

        // Initialize ModelHandler, tflite interpreter, and labels
        modelHandler = new ModelHandler(this);
        tflite = modelHandler.getInterpreter();
        labels = modelHandler.getLabels();

        // Initialize HandProcessor
        handProcessor = new HandProcessor();

        // Initialize input and output arrays
        inputArray = new float[1][42]; // 21 landmarks x 2 coordinates
        outputArray = new float[1][labels.length];
    }

    private void setupLanguageSpinner() {
        Spinner languageSpinner = findViewById(R.id.language_spinner);

        // Create an array of languages
        String[] languages = {"English", "Spanish", "French", "Vietnamese"};

        // Create an ArrayAdapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Set the adapter to the spinner
        languageSpinner.setAdapter(adapter);

        // Set default selection based on selectedLanguage
        int defaultPosition = adapter.getPosition(selectedLanguage);
        languageSpinner.setSelection(defaultPosition);

        // Set the selected language listener
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = languages[position];
                Toast.makeText(MainActivity.this, "Selected Language: " + selectedLanguage, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Handle the case where no selection is made
            }
        });
    }

    private void initializeViews() {
        // Initialize GestureDetector
        gestureDetector = new GestureDetector(this, new SwipeGestureListener(this));

        // Initialize TextViews
        textInferredLetter = findViewById(R.id.text_inferred_letter);
        textDetectedSignsQueue = findViewById(R.id.text_detected_signs_queue);

        // Initialize Spinner
        setupLanguageSpinner();

        // Set up UI components
        setupLiveDemoUiComponents();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Delegate the touch event to GestureDetector
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public void onSwipeLeft() {
        if (!isManualVisible) {
            showHowToUseLayout();
        }
    }

    @Override
    public void onSwipeRight() {
        if (isManualVisible) {
            showMainLayout();
        }
    }

    private void showMainLayout() {
        setContentView(R.layout.activity_main);
        isManualVisible = false;

        // Initialize views
        initializeViews();

        // Re-initialize the camera pipeline unconditionally
        setupStreamingModePipeline(InputSource.CAMERA);
    }

    private void showHowToUseLayout() {
        // Stop the camera pipeline to release resources
        stopCurrentPipeline();

        setContentView(R.layout.how_to_use);
        isManualVisible = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isManualVisible) {
            return;
        }

        // Always restart the camera pipeline when resuming
        if (inputSource == InputSource.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        }
    }

    /** Sets up the UI components for the live demo with camera input. */
    private void setupLiveDemoUiComponents() {
        Button startCameraButton = findViewById(R.id.button_del_detection);
        startCameraButton.setOnClickListener(
                v -> {
                    detectedSigns.clear();

                    // Update the TextView displaying the detections
                    updateDetectedSignsTextView();

                    // Provide user feedback
                    Toast.makeText(this, "All detections deleted", Toast.LENGTH_SHORT).show();
                });

        // Initialize Change Camera Button
        Button changeCameraButton = findViewById(R.id.button_change_camera);
        changeCameraButton.setOnClickListener(
                v -> {
                    if (inputSource == InputSource.CAMERA) {
                        switchCamera();
                    } else {
                        Toast.makeText(this, "Camera is not running", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Switches the camera between front and back. */
    private void switchCamera() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
            cameraInput = null;
        }

        // Toggle the camera facing
        cameraFacing = (cameraFacing == CameraInput.CameraFacing.BACK)
                ? CameraInput.CameraFacing.FRONT
                : CameraInput.CameraFacing.BACK;

        // Reinitialize cameraInput with the new facing
        cameraInput = new CameraInput(this);
        cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        startCamera();
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
        }

        // Initializes a new GL surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);

        // Set the result listener to the refactored method
        hands.setResultListener(this::processHandsResult);

        // The runnable to start camera after the GL surface view is attached.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
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

            hasProcessedQueue = false; // Reset the flag because hands are detected again

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
        List<float[]> landmarkList = handProcessor.extractLandmarkList(handsResult);

        float[] preprocessedLandmarks = handProcessor.preProcessLandmark(landmarkList);

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
        if (!hasProcessedQueue && timeSinceLastHandDetected >= PROCESS_SIGN_THRESHOLD) {
            Log.i("ProcessQueue", "processDetectedSignsQueue called");
            // Trigger the API call to process the queue
            processDetectedSignsQueue();
            // Set the flag to prevent multiple processing
            hasProcessedQueue = true;
            // Do not reset lastHandDetectedTime here
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

        // Prepare the prompt based on the selected language
        String prompt = "You are a well-made Sign Language Processor. Your task is to receive a string of Sign Language with given language is " + selectedLanguage+ ", which has the form of \"AN_UPPER_CASED_AND_SNAKE_CASED_STRING\", and interpret what the string means. You should be able to interpret the string and convert it into a human-readable format.\n\nFor example, if you receive the string \"HELLO_WORLD_\", you should be able to interpret it as \"Hello, World!\".\n\nHere are a few rules that you must strictly follow: The given string is most likely not syntactically correct, hence you should not rely on the correctness of the string. You should be able to interpret the string even if it is not syntactically correct. Some errors that may exist in the string include the appearance of 1 or many characters that disrupt the syntax of a word, the reappearance of many characters that do not belong to the syntax of a word, or the absence of a character that is necessary for the syntax of a word. In sign language, the sign of some letters may be similar, which leads to errors in detection. Here are some pairs of letters that may be used mistakenly in place of each other: \"S\" and \"T\", \"S\" and \"A\", \"M\" and \"A\", \"M\" and \"N\", \"R\" and \"U\".\n\nHere are a few English examples of errors that may exist in the string, along with how you should interpret them:\n\"HHHEEELLLLOOO_WOOOORRRLLD_\" -> \"HELLO_WORLD_\"\n\"I_MMISTSS_YOUUU_\" -> \"I_MISS_YOU_\"\n\"CCCCCCC_HOWWW_ARE_YOUUU_\" -> \"HOW_ARE_YOU_\"\n\"THIMK_ILL_NISST_YOU_\" -> \"THINK_ILL_MISS_YOU_\"\n\nAlways remember one extra '_' token at the end of your interpretation, and your interpretation is based on the language, which is " + selectedLanguage + " in this case. Finally, you must only return the corrected string, and no other information. Answer with the corrected string only, do not include any other information in your answer. Here is your string:\n" + detectedSentence;


        // Send the prompt to the Gemini API
        sendPromptToGemini(prompt);
    }

    private void sendPromptToGemini(String prompt) {
        geminiApiClient.sendPrompt(prompt, new GeminiApiClient.GeminiResponseListener() {
            @Override
            public void onResponse(String cleanedText) {
                // Update the detectedSigns queue and UI on the main thread
                runOnUiThread(() -> {
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
            }

            @Override
            public void onError(Throwable t) {
                // Handle error
                Log.e("GeminiAPI", "Error in generating content: " + t.getMessage());
                t.printStackTrace();
            }
        });
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
                cameraFacing,
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
            cameraInput = null;
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
            hands = null;
        }
    }
}
