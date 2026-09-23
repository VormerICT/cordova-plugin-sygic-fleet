Sygic Fleet 3D Embedded 26.0.7 - OutSystems/MABS patch v2

Changes from the original Sygic AAR:
1. Provider class changed from androidx.core.content.FileProvider to
   com.vormer.sygicfleet.SygicFileProvider, preserving Sygic's authority,
   metadata and @xml/file_path resource.
2. Removed android:icon="@drawable/sygic" from the AAR <application>.
3. Removed android:label="@string/com_sygic_app_name" from the AAR <application>.

Everything else in the AAR manifest is unchanged.

IMPORTANT: Remove the <edit-config> block that sets tools:replace for
android:icon/android:label from plugin.xml. It is no longer needed and conflicts
with OutSystems/MABS config.xml.
