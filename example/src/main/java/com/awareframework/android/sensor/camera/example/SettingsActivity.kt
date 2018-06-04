package com.awareframework.android.sensor.camera.example

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.awareframework.android.sensor.camera.Camera
import com.awareframework.android.sensor.camera.CameraFace
import com.awareframework.android.sensor.camera.R.string.*

/**
 * A [PreferenceActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See [Android Design: Settings](http://developer.android.com/design/patterns/settings.html)
 * for design guidelines and the [Settings API Guide](http://developer.android.com/guide/topics/ui/settings.html)
 * for more information on developing a Settings UI.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        fragmentManager.beginTransaction().replace(android.R.id.content, CameraPreferenceFragment()).commit()
    }

    /**
     * Set up the [android.app.ActionBar], if the API is available.
     */
    private fun setupActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> {
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

//    /**
//     * {@inheritDoc}
//     */
//    override fun onIsMultiPane(): Boolean {
//        return isXLargeTablet(this)
//    }

//    /**
//     * {@inheritDoc}
//     */
//    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) {
//        loadHeadersFromResource(R.xml.pref_headers, target)
//    }

//    /**
//     * This method stops fragment injection in malicious applications.
//     * Make sure to deny any unknown fragments here.
//     */
//    override fun isValidFragment(fragmentName: String): Boolean {
//        return PreferenceFragment::class.java.name == fragmentName
//                || CameraPreferenceFragment::class.java.name == fragmentName
//    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    class CameraPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_camera)
            setHasOptionsMenu(true)

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            val config = Camera.CameraConfig()

//            bindPreferenceSummaryToValue(findPreference("dual_camera_switch"), config.secondaryFacing != CameraFace.NONE)
            bindPreferenceSummaryToValue(findPreference(getString(key_primary_camera)), config.facing.toInt().toString())
            bindPreferenceSummaryToValue(findPreference(getString(key_secondary_camera)), config.secondaryFacing.toInt().toString())
            bindPreferenceSummaryToValue(findPreference(getString(key_video_bitrate)), config.bitrate.toString())
            bindPreferenceSummaryToValue(findPreference(getString(key_video_frame_rate)), config.frameRate.toString())
            bindPreferenceSummaryToValue(findPreference(getString(key_video_length)), config.videoLength.toString())
            bindPreferenceSummaryToValue(findPreference(getString(key_data_label)), config.label)
        }

        /**
         * Binds a preference's summary to its value. More specifically, when the
         * preference's value is changed, its summary (line of text below the
         * preference title) is updated to reflect the value. The summary is also
         * immediately updated upon calling this method. The exact display format is
         * dependent on the type of preference.

         * @see .sBindPreferenceSummaryToValueListener
         */
        private fun bindPreferenceSummaryToValue(preference: Preference, default: String) {
            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager
                    .getDefaultSharedPreferences(preference.context)
                    .getString(preference.key, default)
            )
        }

        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()

            if (preference is ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                val index = preference.findIndexOfValue(stringValue)

                // Set the summary to reflect the new value.
                preference.setSummary(
                        if (index >= 0)
                            preference.entries[index]
                        else
                            null)

                ensurePrimaryAndSecondaryFaceIsDifferent(preference, stringValue)
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.summary = stringValue
            }

            true
        }

        private fun ensurePrimaryAndSecondaryFaceIsDifferent(preference: Preference, value: String) {
            val context = preference.context

            when (preference.key) {
                context.getString(key_primary_camera) -> context.getString(key_secondary_camera)
                context.getString(key_secondary_camera) -> context.getString(key_primary_camera)
                else -> null
            }?.let {
                val otherPreference = findPreference(it)
                if (otherPreference is ListPreference) {
                    if (value == otherPreference.value) {
                        val selectedFace = CameraFace.fromInt(value.toInt())
                        otherPreference.value = when (selectedFace) {
                            CameraFace.FRONT -> CameraFace.BACK
                            CameraFace.BACK -> CameraFace.FRONT
                            else -> CameraFace.fromInt(otherPreference.value.toInt())
                        }.toInt().toString()

                        otherPreference.onPreferenceChangeListener.onPreferenceChange(otherPreference, otherPreference.value)
                    }
                }
            }
        }
    }

    companion object {

        /**
         * A preference value change listener that updates the preference's summary
         * to reflect its new value.
         */


//        /**
//         * Helper method to determine if the device has an extra-large screen. For
//         * example, 10" tablets are extra-large.
//         */
//        private fun isXLargeTablet(context: Context): Boolean {
//            return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
//        }

    }
}
