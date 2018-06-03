package com.awareframework.android.sensor.camera

/**
 * Enum for camera facing
 *
 * @author  sercant
 * @date 28/05/2018
 */
enum class CameraFace {
    FRONT,
    BACK,
    NONE;

    fun toInt(): Int = when (this) {
        NONE -> -1
        FRONT -> 0
        BACK -> 1
    }

    companion object {
        fun fromInt(value: Int): CameraFace = when (value) {
            0 -> FRONT
            1 -> BACK
            else -> NONE
        }

    }
}