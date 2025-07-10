# UAV-Recon-Tool
ATAK plug-in that processes the live video stream from a DJI drone using AI models. 
Plugin allows you to display the video stream from the drone in the ATAK app and has also many useful features.
You can analyze video frame with the AI model of your choice ( in the case of the plugin, these are modified YOLO models, but you can upload your models as well).
Then, after analysis, the distance to the object is calculated based on the telemetry from the drone, and the detected objects are located on the ATAK map. 
Enhance situational awareness and accelerate decision-making by unifying live video, AI-driven analysis, and precise geospatial mapping into one tactical toolkit. The operator himself decides what which frame of the stream should be processed. He is responsible for the accuracy of estimating the distance to objects by selecting their heights, and decides which objects are to be detected and what the minimum accuracy must be , to be plotted on the map.


# Table of Contents
- [Key Features](#Key-Features)
- [Supported Drones](#Supported-Drones)
- [Requirements](#Requirements)
- [Example Of Use](#Example-Of-Use)
- [Plugin Tabs Overview](#Plugin-Tabs-Overview)
- [App example](#App-example)

# Key Features
- Embed the drone’s camera feed directly within ATAK without switching apps.
- On-Device AI Analysis
- Distance Estimation
- Map Annotation
- Extensible Architecture
- Entirely self-contained—no external servers required.
- Operator coordinates
- Drone coordinates
- Distance from the drone to the operator
- Archiving of flights made along with detected objects
- Customization of the detection options of the selected AI model

# Supported Drones
![image](https://github.com/user-attachments/assets/a3145583-e01d-4f9f-9a52-827c33d0abe9)


# Requirements

  UAVReconToolApp Requirements
  -	Gradle JDK : correto-11 \ Amazon Corretto 11.0.27
  -	Android Gradle Plugin Version : 7.4.2
  -	Gradle Version: 7.5
    
  UAVReconToolPlugin Requirements:
  - Gradle JDK : correto-11 \ Amazon Corretto 11.0.27
  - Android Gradle Plugin Version : 7.4.2
  - Gradle Version: 7.5
  - Building Options: civDebug

Also you have to define the debug.keystore and release.keystore:

**debug.keystore:**
```
& "C:\Users\YourUserName\.jdks\corretto-11.0.27\bin\keytool.exe" -genkeypair `
  -dname "CN=Android Debug,O=Android,C=US" `
  -validity 9999 `
  -keystore debug.keystore `
  -alias androiddebugkey `
  -keypass android `
  -storepass android
```
**release.keystore:**
```
& "C:\Users\YourUserName\.jdks\corretto-11.0.27\bin\keytool.exe" -genkeypair `
  -dname "CN=Android Release,O=Android,C=US" `
  -validity 9999 `
  -keystore release.keystore `
  -alias androidreleasekey `
  -keypass android `
  -storepass android
```

    
# Example Of Use
![Example](https://github.com/user-attachments/assets/1a476412-a710-49bf-afcd-595862f3cfcb)


# Plugin Tabs Overview
![image](https://github.com/user-attachments/assets/68e8cf2b-8cc0-49d5-a395-ea00de2d2497)
![image](https://github.com/user-attachments/assets/b69b4b44-c8f5-4321-b451-57b798d3197c)
![image](https://github.com/user-attachments/assets/46af7cec-1e50-4be1-9bc2-f35ae3430a99)

# App example
![AppExample](https://github.com/user-attachments/assets/b6e95847-379f-4c53-a4e1-fa731f7365b2)





