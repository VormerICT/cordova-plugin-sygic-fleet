package com.vormer.sygicfleet;

import androidx.core.content.FileProvider;

/**
 * Separate FileProvider class for Sygic.
 *
 * Android manifest merging identifies providers by android:name.
 * Cordova already uses androidx.core.content.FileProvider, and the
 * Sygic AAR uses the same class.
 *
 * Using a subclass gives Sygic a distinct manifest identity while
 * retaining normal FileProvider behaviour.
 */
public class SygicFileProvider extends FileProvider {
}
