![Affectiva Logo](http://developer.affectiva.com/images/logo.png)

**AffdexMe** is an Android app that demonstrates the use of the Affdex SDK.  It uses the camera on your Android device to view, process and analyze live video of your face. Start the app and you will see your face on the screen and metrics describing your expressions. Tapping the screen will bring up a menu with options to display the Processed Frames Per Second metric, display facial tracking points, and control the rate at which frames are processed by the SDK.

To use this project, you will need to:
- Build the project using Android Studio
- Run the app and smile!

If you are interested in learning how the Affectiva SDK works, you will find the calls relevant to the use of the SDK in the initializeCameraDetector(), startCamera(), stopCamera(), and onImageResults() methods.  See the comment section at the top of the MainActivity.java file for more information.

[![Build Status](https://travis-ci.org/Affectiva/affdexme-android.svg)](https://travis-ci.org/Affectiva/affdexme-android)

***
Copyright (c) 2016 Affectiva Inc. <br> See the file [license.txt](license.txt) for copying permission.

This app uses some of the excellent [Emoji One emojis](http://emojione.com).
