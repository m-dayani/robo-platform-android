# ROBO Platform | Android App

This project was initially developed for robot control as a component of an experimental project. 
Later, it was extended to record sensor datasets.

Since user privacy and safety are critical for the Android platform, 
the app's access to device resources is restricted. 
For this reason, this app must also deal with sensor availability, access, and permissions.

*WARNING*:
This app requires full access and control over the necessary resources,
and since it works with various sensors continuously, it might quickly drain the battery.

*DISCLAIMER*:
Although this app is tested on various emulated and physical devices,
some specific hardware or unpredicted practical situations might affect its performance. Use it at your own risk!

### Applications

- 3D reconstruction
- Online/offline sensor fusion and SLAM apps
- Low-cost test and development
- A flexible platform for more advanced robotic applications
- Support for older versions of Android (tested on Android 5, 6, and 11)

### Disadvantages

- Requires access to protected resources
- Resource intensive for collecting large datasets: needs a sufficient amount of storage space and drains the battery

## App Architecture

The app's entry presents a list of activities from which the user can choose. 
Depending on the selected task, the user interacts with two different architectures:

### Robot Manual Control (Old Architecture)

1. A list of different requirements (permissions, attach USB devices, etc.) is presented to the user.
2. The user selects a requirement and follows the instructions to provide access.
3. When all requirements are resolved, the user can start the desired activity.

### Record Sensors (Updated Version)

Following the Android best practices, the user can permit the features when they are needed.

1. If the user selects the record sensors task, they see a list of available sensors.
2. Depending on the permissions and requirements, a module might be disabled.
3. To unlock a feature, the user clicks it and follows the instructions.
4. The recording task can begin once the user is satisfied with the selected sensors.

<!-- ## Data Formats -->

## Usage

Unfortunately, at the moment, I can't share this app on Google Play. You have two options to install and use this project on your phone.

### Install the generated APK file

Note that

- To install the app on your phone, you have to enable the option that allows the installation of apps from unknown sources.
- The app might not work properly on a specific platform.

### Set up your development environment

Follow these instructions to set up a development environment, compile the source code, and download it to your phone:

1. [Download and install Android Studio](https://developer.android.com/studio/install).
2. You need a fast and stable connection to download the required SDK and other dependencies.
3. Clone the source code.
4. Open and build the project in Android Studio.
5. [Enable developer options](https://developer.android.com/studio/debug/dev-options) on your phone and connect it to your development platform.
6. Wait until Android Studio detects the device, and then you can install and run the app on your phone.

This app is tested using the following tools:

- Ubuntu 20.04
- Android Studio 2021.1.1
- Gradle 7.2
- Java Runtime Environment (JRE) 11
- Samsung Galaxy A20, Android 11 (physical device)
- Samsung Galaxy J5 and J7, Android 5 and 6 (physical device)
- Pixel 4 XL, API 30 (emulated)
- Pixel 5, API 25 (emulated)

## Related Work

Most relevant applications can only process or record specific types of sensors. 
Some are designed to test and analyze inertial and environmental measurements. 
Most apps do not provide sensor calibration data, and the recorded format needs further post-processing to suit SLAM projects.

- [Google's GnssLogger](https://github.com/google/gps-measurement-tools) 
only supports newer Android devices and records GNSS-related measurements.
- [Physics Toolbox Suit](https://play.google.com/store/apps/details?id=com.chrystianvieyra.physicstoolboxsuite&hl=en&gl=US)
can record a variety of inertial and position sensors but doesn't support the camera.
- [VINS-Mobile](https://github.com/HKUST-Aerial-Robotics/VINS-Mobile)
implements the [VINS-Mono](https://github.com/HKUST-Aerial-Robotics/VINS-Mono) SLAM pipeline for iOS devices
([VINS-Mobile-Android](https://github.com/jannismoeller/VINS-Mobile-Android) is developed for Android devices).
Sensor data is processed for localization and mapping and cannot be recorded.

<!-- ## References -->
