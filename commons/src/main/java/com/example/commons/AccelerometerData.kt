package com.example.commons

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AccelerometerData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
){
    fun toByteArray(): ByteArray {
        return ByteBuffer.allocate(3 * Float.SIZE_BYTES + Long.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(x)
            .putFloat(y)
            .putFloat(z)
            .putLong(timestamp)
            .array()
    }

    companion object {
        fun fromByteArray(data: ByteArray): AccelerometerData {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            return AccelerometerData(
                x = buffer.float,
                y = buffer.float,
                z = buffer.float,
                timestamp = buffer.long
            )
        }
    }
}