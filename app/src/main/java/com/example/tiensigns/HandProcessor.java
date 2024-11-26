// File: HandProcessor.java
package com.example.tiensigns;

import android.graphics.Bitmap;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.ArrayList;
import java.util.List;

public class HandProcessor {

    public List<float[]> extractLandmarkList(HandsResult handsResult) {
        List<float[]> landmarkPoint = new ArrayList<>();
        Bitmap inputBitmap = handsResult.inputBitmap();
        int imageWidth = inputBitmap.getWidth();
        int imageHeight = inputBitmap.getHeight();

        for (NormalizedLandmark landmark : handsResult.multiHandLandmarks().get(0).getLandmarkList()) {
            float landmarkX = Math.min(landmark.getX() * imageWidth, imageWidth - 1);
            float landmarkY = Math.min(landmark.getY() * imageHeight, imageHeight - 1);
            landmarkPoint.add(new float[]{landmarkX, landmarkY});
        }
        return landmarkPoint;
    }

    public float[] preProcessLandmark(List<float[]> landmarkList) {
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
}
