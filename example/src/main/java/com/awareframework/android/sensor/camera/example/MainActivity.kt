package com.awareframework.android.sensor.camera.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.camera.Camera
import com.awareframework.android.sensor.camera.example.adapters.VideoListAdapter
import com.awareframework.android.sensor.camera.model.VideoData
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private val dataset: ArrayList<VideoData> = ArrayList()
    private lateinit var viewAdapter: VideoListAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    companion object {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        const val REQUEST_PERMISSION = 1

        const val SHARED_CAMERA_CONFIG = "camera_config"
        const val SHARED_CAMERA_CONFIG_KEY = "config"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.delete_selected -> {
                viewAdapter.selectionList.forEach {
                    File(it).delete()
                }

                viewAdapter.clearSelections()

                refreshVideos()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Camera.ACTION_VIDEO_RECORDED -> Handler().postDelayed({
                    refreshVideos()
                }, 1000)
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this,
                    permissions,
                    REQUEST_PERMISSION)
        }

        val config = getSharedPreferences(SHARED_CAMERA_CONFIG, Context.MODE_PRIVATE)
        val json: String? = config.getString(SHARED_CAMERA_CONFIG_KEY, null)

        if (json == null) {
            config.edit().putString(SHARED_CAMERA_CONFIG_KEY, Camera.CameraConfig().apply {
                dbType = Engine.DatabaseType.ROOM
                contentPath = getExternalFilesDir(Environment.DIRECTORY_MOVIES).absolutePath
            }.toJson()).commit()
        }

        viewManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        viewAdapter = VideoListAdapter(dataset)

        video_list_view.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        refreshVideos()

        registerReceiver(receiver, IntentFilter().apply {
            addAction(Camera.ACTION_VIDEO_RECORDED)
        })

        swipe_to_refresh.setOnRefreshListener {
            refreshVideos()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun hasPermissions(): Boolean {
        return permissions.none {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION) {
            if (hasPermissions())
                Toast.makeText(this, "Permissions are granted.", Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(this, "Unable to get permissions. Please grant them.", Toast.LENGTH_SHORT).show()

        }
    }

    private fun getAllMedia(block: (List<VideoData>) -> Unit) {
        val config = getSharedPreferences(SHARED_CAMERA_CONFIG, Context.MODE_PRIVATE)
        val json: String = config.getString(SHARED_CAMERA_CONFIG_KEY, "{}")
        val camera = Camera.Builder(this)
                .build(Camera.CameraConfig.fromJson(json))

        thread {
            block(camera.getVideoData())
        }
    }

    private fun refreshVideos() {
        getAllMedia({
            dataset.clear()

            // filter deleted videos
            dataset.addAll(it.filter {
                File(it.filePath).exists()
            })

            runOnUiThread {
                viewAdapter.notifyDataSetChanged()

                swipe_to_refresh.isRefreshing = false
            }
        })
    }
}
