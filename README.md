
# Tien-Signs: Real-Time American Sign Language Detection

Tien-Signs is an Android application developed using Android Studio that enables real-time detection and translation of American Sign Language (ASL) gestures. By leveraging deep learning and computer vision techniques, the app facilitates seamless communication between ASL users and non-signers.

## Features

- **Real-Time ASL Detection**: Utilizes the device's camera to capture and interpret ASL gestures instantaneously.
- **User-Friendly Interface**: Designed with an intuitive interface for easy interaction.
- **Low-end machine compatible**.

## Installation

To build and run Tien-Signs on your Android device, follow these steps:

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/TienVM2004/Tien-Signs.git
   ```
2. **Open in Android Studio**:
   - Launch Android Studio.
   - Select **File > Open** and navigate to the cloned repository directory.
3. **Build the Project**:
   - Click on the "Build" menu and select "Make Project" to compile the application.
4. **Run on Device**:
   - Connect your Android device via USB with USB debugging enabled.
   - Click the "Run" button in Android Studio and choose your device as the deployment target.

## Usage

Once installed:

1. Open the Tien-Signs app on your device.
2. Grant camera permissions when prompted.
3. Position your hand within the camera's view, ensuring clear visibility of the gesture.
4. The app will detect and display the corresponding ASL gesture in real-time.

## Contributing

Contributions to enhance Tien-Signs are welcome. To contribute:

1. Fork the repository.
2. Create a new branch for your feature or bug fix:
   ```bash
   git checkout -b feature-name
   ```
3. Commit your changes:
   ```bash
   git commit -m "Description of changes"
   ```
4. Push to your branch:
   ```bash
   git push origin feature-name
   ```
5. Open a pull request detailing your changes.


## Acknowledgements

- **Mediapipe**: Utilized for image processing and computer vision tasks.
- **TensorFlow Lite**: Employed for on-device machine learning inference.


