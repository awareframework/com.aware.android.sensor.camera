package com.awareframework.android.sensor.camera

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.support.v4.app.ActivityCompat

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.lang.Thread.sleep

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.awareframework.android.sensor.camera.test", appContext.packageName)

        val camera = Camera.Builder(appContext).build()

        camera.start()

//        appContext.startActivity()
//        ActivityCompat.requestPermissions(appContext,
//                new String[]{Manifest.permission.READ_CONTACTS},
//                MY_PERMISSIONS_REQUEST_READ_CONTACTS);

        sleep(20000)
    }
}
