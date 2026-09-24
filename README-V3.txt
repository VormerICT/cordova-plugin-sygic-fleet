Sygic Fleet Embedded 26.0.7 - OutSystems/Cordova test package v1.3.0

Changes in this package:
- Uses patched 26.0.7 AAR (FileProvider collision and icon/label merge fixes).
- AAR is copied to app/libs and referenced from sygic.gradle.
- jniLibs.useLegacyPackaging = true, matching Sygic's official embedded demo.
- Uses PermissionsUtils.INSTANCE.getAllPermissions(...).
- Uses SygicFleetFragment extending the native SygicFragment so it remains compatible with a standard Cordova Activity.
- Calls startNavi() from fragment onResume(), matching the lifecycle order in Sygic's official demo.
- Includes detailed SygicFleetPlugin/SygicFleetFragment logcat logging.
- Does NOT include the obsolete com.sygic.driving:driving-lib dependency.

Test from a clean install. Useful log command:
adb logcat | findstr /i "SygicFleetPlugin SygicFleetFragment"

Expected startup path:
SYGIC_PERMISSIONS_GRANTED -> fragment onResume -> SERVICE_CONNECTED -> EVENT_APP_STARTED
