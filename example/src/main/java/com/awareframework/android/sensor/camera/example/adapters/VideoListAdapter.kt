package com.awareframework.android.sensor.camera.example.adapters

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.RelativeLayout
import android.widget.VideoView
import com.awareframework.android.sensor.camera.example.R
import com.awareframework.android.sensor.camera.model.VideoData
import kotlinx.android.synthetic.main.video_preview.view.*

/**
 * View adapter for listing videos on recycler view
 *
 * @author  sercant
 * @date 16/05/2018
 */
class VideoListAdapter(private val data: List<VideoData>) :
        RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    val selectionList: ArrayList<String> = ArrayList()

    class ViewHolder(val view: CardView) : RecyclerView.ViewHolder(view)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): VideoListAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.video_preview, parent, false) as CardView

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val videoData = data[position]

        holder.view.apply {
            thumbnail.apply {
                setVideoPath(videoData.filePath)
                setOnPreparedListener {
                    seekTo(100)

                    // keep aspect ratio
                    layoutParams.height = it.videoWidth / it.videoHeight * layoutParams.width
                    requestLayout()
                }
            }

            checkbox.isChecked = selectionList.contains(videoData.filePath)

            setOnClickListener {
                onPreviewClicked(it, videoData)
            }

            setOnLongClickListener {
                onPreviewLongClicked(it, videoData)
                return@setOnLongClickListener true
            }
        }
    }

    fun onPreviewClicked(view: View?, data: VideoData) {
        view ?: return
        view.checkbox.isChecked = !view.checkbox.isChecked

        if (view.checkbox.isChecked) {
            if (!selectionList.contains(data.filePath))
                selectionList.add(data.filePath)
        } else {
            selectionList.remove(data.filePath)
        }
    }

    fun onPreviewLongClicked(view: View?, data: VideoData) {
        view ?: return
        val context = view.context

        val videoView = VideoView(context)

        Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addContentView(videoView, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }.show()

        videoView.apply {
            setVideoPath(data.filePath)
            setOnPreparedListener {
                it.isLooping = true
                it.start()
            }
        }
    }

    fun clearSelections() {
        selectionList.clear()
    }

    override fun getItemCount() = data.size
}