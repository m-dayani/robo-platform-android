# Robo Platform Android App

This project was initially developed for robot control as a component of a bigger project.
It can be used to record sensors datasets as well.

Since user's privacy and safety is a critical priority for the Android platform, app's access to device resources is severely restricted.
For this reason, this app for most part must deal with the sensor availability, access, and permissions.

### WARNING:
This app requires full access and control over the necessary resources,
and since it works with power hungry sensors, it might quickly drain the battery.

### DISCLAIMER:
Although this app is tested on a variety of emulated and physical devices,
some specific hardware or unpredicted practical situations might affect its performance.
Use it at your own risk!

## Advantages:
- The design pattern is robust and uses the Android best practices, robotics guidelines, and accuracy (doesn't record raw video, saves raw timestamps instead)
- Enables recording sensors for offline dev and analysis
- Online SLAM and ... applications

- Provides sensors' information including calibration data

## Disadvantages:
- Requires access to protected resources
- Needs to handle permissions and availability
- Resource intensive for large datasets -> drains battery quickly

## App Architecture
- Consists of different independent tasks: Record sensors, ... which is selected by entry level activity (MainActivity)
- Each task has several steps: Sensor selection, requirements, ...

## Development:
Install android studio and ...
Tested under ...

## Record Raw Sensor Data


## Robot Remote Control

## Related Work

## References