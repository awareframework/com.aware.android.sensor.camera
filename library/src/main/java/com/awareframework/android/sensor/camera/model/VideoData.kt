package com.awareframework.android.sensor.camera.model

import com.awareframework.android.core.model.AwareObject

/**
 * Video recording entry
 *
 * @author  sercant
 * @date 16/05/2018
 */
data class VideoData(
        val filePath: String,
        val length: Float,
        val parentFilePath: String?
) : AwareObject(jsonVersion = 2) {
    companion object {
        const val TABLE_NAME = "videoData"
    }
}