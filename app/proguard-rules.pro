# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

# MainActivity accesses the following methods through reflection, so make sure they
# are kept
-keep class com.affectiva.android.affdex.sdk.detector.Face$Expressions { float get*(); }
-keep class com.affectiva.android.affdex.sdk.detector.Face$Emotions { float get*(); }
-keep class com.affectiva.android.affdex.sdk.detector.Face$Emojis { float get*(); }
-keep class com.affectiva.android.affdex.sdk.detector.Detector { void setDetect*(boolean); }
