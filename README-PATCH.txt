Sygic 26.0.7 OutSystems/Cordova manifest patch

Changes made to the original AAR:
- AndroidManifest.xml provider android:name changed from:
  androidx.core.content.FileProvider
  to:
  com.vormer.sygicfleet.SygicFileProvider
- No other AAR files were intentionally modified.

Included:
- libs/embedded-with-res-26.0.7-outsystems.aar
- src/android/SygicFileProvider.java
- src/android/sygic.gradle

Update plugin.xml to reference embedded-with-res-26.0.7-outsystems.aar and include SygicFileProvider.java.
The application icon/label conflict still requires tools:replace="android:icon,android:label" in the main manifest via plugin.xml/edit-config.
