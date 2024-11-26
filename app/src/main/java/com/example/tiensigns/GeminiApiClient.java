// File: GeminiApiClient.java
package com.example.tiensigns;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiApiClient {
    private static final String TAG = "GeminiApiClient";
    private GenerativeModelFutures generativeModelFutures;
    private Executor executor;

    public interface GeminiResponseListener {
        void onResponse(String cleanedText);
        void onError(Throwable t);
    }

    public GeminiApiClient(String apiKey) {
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", apiKey);
        this.generativeModelFutures = GenerativeModelFutures.from(gm);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void sendPrompt(String prompt, GeminiResponseListener listener) {
        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = generativeModelFutures.generateContent(content);

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(@Nullable GenerateContentResponse result) {
                        if (result != null) {
                            String generatedText = result.getText();
                            String cleanedText = generatedText.replaceAll("\\s+", "");

                            // Use Handler to switch to main thread if needed
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> listener.onResponse(cleanedText));
                        } else {
                            Log.e(TAG, "Received null response from the API.");
                            listener.onError(new Exception("Null response from Gemini API"));
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Error in generating content: " + t.getMessage());
                        listener.onError(t);
                    }
                },
                executor
        );
    }
}
