package com.awareframework.android.sensor.camera.example.adapters

import android.app.AlertDialog
import android.media.ThumbnailUtils
import android.provider.MediaStore.Video.Thumbnails.MINI_KIND
import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.awareframework.android.sensor.camera.example.R
import com.awareframework.android.sensor.camera.model.VideoData
import kotlinx.android.synthetic.main.video_dialog.*
import kotlinx.android.synthetic.main.video_preview.view.*

/**
 * View adapter for listing videos on recycler view
 *
 * @author  sercant
 * @date 16/05/2018
 */
class VideoListAdapter(private val data: List<VideoData>) :
        RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {

    val selectionList: ArrayList<VideoData> = ArrayList()

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
            val preview = ThumbnailUtils.createVideoThumbnail(videoData.parentFilePath
                    ?: videoData.filePath, MINI_KIND)
            thumbnail.setImageBitmap(preview)

            checkbox.isChecked = selectionList.contains(videoData)

            setOnClickListener {
                onPreviewClicked(it, videoData)
            }

            setOnLongClickListener {
                onPreviewLongClicked(it, videoData)
                return@setOnLongClickListener true
            }

            requestLayout()
        }
    }

    private fun onPreviewClicked(view: View?, data: VideoData) {
        view ?: return
        view.checkbox.isChecked = !view.checkbox.isChecked

        if (view.checkbox.isChecked) {
            if (!selectionList.contains(data))
                selectionList.add(data)
        } else {
            selectionList.remove(data)
        }
    }

    private fun onPreviewLongClicked(view: View?, data: VideoData) {
        view ?: return
        val context = view.context

        val dialog = AlertDialog.Builder(context)
                .setView(R.layout.video_dialog)
                .create()
        dialog.show()

        dialog.dialog_primary_video_view?.apply {
            setVideoPath(data.parentFilePath ?: data.filePath)
            setOnPreparedListener {
                it.isLooping = true
                it.start()
            }
        }

        val primaryVideo = data.parentFilePath ?: data.filePath
        val secondaryVideo = if (data.parentFilePath != null) data.filePath else null

        val changeTab: (View) -> Unit = {
            val primaryVideoView = dialog.dialog_primary_video_view
            val secondaryVideoView = dialog.dialog_secondary_video_view

            primaryVideoView.visibility = if (primaryVideoView.visibility == View.GONE) View.VISIBLE else View.GONE
            secondaryVideoView.visibility = if (secondaryVideoView.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        dialog.dialog_primary_video_button?.setOnClickListener(changeTab)
        dialog.dialog_secondary_video_button?.setOnClickListener(changeTab)

        dialog.dialog_primary_video_view?.apply {
            setVideoPath(primaryVideo)
            setOnPreparedListener {
                it.isLooping = true
                it.start()
            }
        }

        if (secondaryVideo != null) {
            dialog.dialog_secondary_video_view?.apply {
                setVideoPath(secondaryVideo)
                setOnPreparedListener {
                    it.isLooping = true
                    it.start()
                }
            }
            dialog.tab_control?.visibility = View.VISIBLE
        } else {
            dialog.dialog_secondary_video_view?.visibility = View.GONE
            dialog.tab_control?.visibility = View.GONE
        }
    }

    fun clearSelections() {
        selectionList.clear()
    }

    override fun getItemCount() = data.size
}