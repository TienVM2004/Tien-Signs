package com.example.tiensigns;

import android.opengl.GLES20;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.hands.HandsResult;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/** A custom implementation of {@link ResultGlRenderer} to render {@link HandsResult}. */
public class HandsResultGlRenderer implements ResultGlRenderer<HandsResult> {
    private static final String TAG = "HandsResultGlRenderer";

    private static final float[] HAND_BORDER_COLOR = new float[]{0f, 0f, 0f, 1f};
    private static final float BORDER_THICKNESS = 15.0f;
    private static final String VERTEX_SHADER =
            "uniform mat4 uProjectionMatrix;\n"
                    + "attribute vec4 vPosition;\n"
                    + "void main() {\n"
                    + "  gl_Position = uProjectionMatrix * vPosition;\n"
                    + "}";
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform vec4 uColor;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = uColor;\n"
                    + "}";
    private int program;
    private int positionHandle;
    private int projectionMatrixHandle;
    private int colorHandle;

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void setupRendering() {
        program = GLES20.glCreateProgram();
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
        colorHandle = GLES20.glGetUniformLocation(program, "uColor");
    }

    @Override
    public void renderResult(HandsResult result, float[] projectionMatrix) {
        if (result == null) {
            return;
        }
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);
        GLES20.glLineWidth(BORDER_THICKNESS);

        int numHands = result.multiHandLandmarks().size();
        for (int i = 0; i < numHands; ++i) {
            // Draw border around the hand
            drawHandBorder(result.multiHandLandmarks().get(i).getLandmarkList(), HAND_BORDER_COLOR);
        }
    }

    /**
     * Deletes the shader program.
     *
     * <p>This is only necessary if one wants to release the program while keeping the context around.
     */
    public void release() {
        GLES20.glDeleteProgram(program);
    }

    private void drawHandBorder(List<NormalizedLandmark> handLandmarkList, float[] colorArray) {
        // Calculate the bounding box of the hand
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (NormalizedLandmark landmark : handLandmarkList) {
            float x = landmark.getX();
            float y = landmark.getY();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        float offset = 0.05f; // Offset to expand the border (adjust as needed)
        minX -= offset;
        minY -= offset;
        maxX += offset;
        maxY += offset;

        // Define the four corners of the rectangle
        float[] vertex = {
                minX, minY, 0.0f, // Bottom-left
                maxX, minY, 0.0f, // Bottom-right
                maxX, maxY, 0.0f, // Top-right
                minX, maxY, 0.0f, // Top-left
                minX, minY, 0.0f  // Back to Bottom-left to close the rectangle
        };

        // Create buffer for the vertices
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertex.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertex);
        vertexBuffer.position(0);

        // Set color and draw the rectangle
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 5); // Draw lines connecting the vertices
    }
}
