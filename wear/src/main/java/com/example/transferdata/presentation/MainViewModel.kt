package com.example.transferdata.presentation

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.SystemClock
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.commons.AmbientTemperatureData
import com.example.commons.CommunicationPaths.Companion.ACCELEROMETER_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.AMBIENT_TEMPERATURE_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.GRAVITY_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.GYROSCOPE_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.HEART_RATE_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.INIT_TRANSFER_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.LINEAR_ACCELERATION_DATA_PATH
import com.example.commons.CommunicationPaths.Companion.PING_PATH
import com.example.commons.CommunicationPaths.Companion.PONG_PATH
import com.example.commons.CommunicationPaths.Companion.STOP_TRANSFER_DATA_PATH
import com.example.commons.HeartRateData
import com.example.commons.ThreeAxisData
import com.example.transferdata.common.WearableState
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainViewModel(
    application: Application
) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener,
    SensorEventListener,
    MeasureCallback {

    private val messageClient = Wearable.getMessageClient(application)

    private val _sensorsListenerState = MutableLiveData<Boolean>()
    val sensorsListenerState: LiveData<Boolean> = _sensorsListenerState

    private var currentNodeId: String? = null

    private val _hrData = MutableStateFlow<Int?>(null)
    val hrData = _hrData.asStateFlow()

    private val _wearableState = MutableStateFlow<WearableState>(WearableState.Waiting)
    val wearableState = _wearableState.asStateFlow()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PING_PATH -> {
                val t2 = SystemClock.elapsedRealtimeNanos()
                val data = ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(t2)
                    .array()

                sendPongMessage(messageEvent.sourceNodeId, messageEvent.data + data)
            }

            INIT_TRANSFER_DATA_PATH -> {
                startSensorsListener(messageEvent.sourceNodeId)
            }

            STOP_TRANSFER_DATA_PATH -> {
                stopSensorListeners(messageEvent.sourceNodeId)
            }
        }
    }

    private fun sendPongMessage(sourceNodeId: String, bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            messageClient.sendMessage(
                sourceNodeId,
                PONG_PATH,
                bytes
            )
        }
    }

    private fun startSensorsListener(nodeId: String) {
        currentNodeId = nodeId
        _wearableState.value = WearableState.Transferring
        _sensorsListenerState.postValue(true)
    }

    private fun stopSensorListeners(nodeId: String) {
        if (nodeId == currentNodeId) {
            currentNodeId = null
            _hrData.value = null
            _wearableState.value = WearableState.Waiting
            _sensorsListenerState.postValue(false)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val data = ThreeAxisData(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestamp = event.timestamp,
                )
                sendAccelerometerData(data)
            }

            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                val data = AmbientTemperatureData(
                    temperature = event.values[0],
                    timestamp = event.timestamp,
                )
                sendAmbientTemperatureData(data)
            }

            Sensor.TYPE_GYROSCOPE -> {
                val data = ThreeAxisData(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestamp = event.timestamp,
                )
                sendGyroscopeData(data)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val data = ThreeAxisData(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestamp = event.timestamp,
                )
                sendLinearAccelerationData(data)
            }

            Sensor.TYPE_GRAVITY -> {
                val data = ThreeAxisData(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestamp = event.timestamp,
                )
                sendGravityData(data)
            }
        }
    }

    override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
        // Handle availability changes if needed
    }

    override fun onDataReceived(data: DataPointContainer) {
        val heartRateBpm = data.getData(DataType.HEART_RATE_BPM)
        heartRateBpm.map { dataPoint ->
            sendHeartRateData(
                HeartRateData(
                    heartRate = dataPoint.value.toInt(),
                    timestamp = dataPoint.timeDurationFromBoot.toNanos()
                )
            )
            _hrData.value = dataPoint.value.toInt()
        }
    }

    private fun sendAccelerometerData(data: ThreeAxisData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    ACCELEROMETER_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    private fun sendLinearAccelerationData(data: ThreeAxisData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    LINEAR_ACCELERATION_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    private fun sendAmbientTemperatureData(data: AmbientTemperatureData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    AMBIENT_TEMPERATURE_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    private fun sendHeartRateData(data: HeartRateData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    HEART_RATE_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    private fun sendGyroscopeData(data: ThreeAxisData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    GYROSCOPE_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    private fun sendGravityData(data: ThreeAxisData) {
        viewModelScope.launch(Dispatchers.IO) {
            val dataBytes = data.toByteArray()
            currentNodeId?.let { nodeId ->
                messageClient.sendMessage(
                    nodeId,
                    GRAVITY_DATA_PATH,
                    dataBytes
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY]!!
                MainViewModel(
                    application
                )
            }
        }
    }
}