// File: ModelHandler.java
package com.example.tiensigns;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelHandler {
    private static final String TAG = "ModelHandler";
    private Interpreter tflite;
    private String[] labels;

    public ModelHandler(Context context) {
        initializeInterpreter(context);
        loadLabels(context);
    }

    private void initializeInterpreter(Context context) {
        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(context, "keypoint_classifier.tflite"));
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TensorFlow Lite interpreter: " + e.getMessage());
        }
    }

    private void loadLabels(Context context) {
        try {
            // Open the CSV file from the assets folder
            InputStream is = context.getAssets().open("keypoint_classifier_label.csv");
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

    public Interpreter getInterpreter() {
        return tflite;
    }

    public String[] getLabels() {
        return labels;
    }
}
