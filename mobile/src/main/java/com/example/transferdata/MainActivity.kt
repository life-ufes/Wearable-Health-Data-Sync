package com.example.transferdata

import BluetoothStateReceiver
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.lifecycleScope
import com.example.commons.Capabilities.Companion.ACCELEROMETER_CAPABILITY
import com.example.commons.Capabilities.Companion.AMBIENT_TEMPERATURE_CAPABILITY
import com.example.commons.Capabilities.Companion.GYROSCOPE_CAPABILITY
import com.example.commons.Capabilities.Companion.HEART_RATE_CAPABILITY
import com.example.commons.Capabilities.Companion.WEAR_CAPABILITY
import com.example.transferdata.database.repository.RecordDatabase
import com.example.transferdata.dataset.DatasetGenerator
import com.example.transferdata.navigation.MainNavHost
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver
    private val mainViewModel: MainViewModel by viewModels()

    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }

    @Inject
    lateinit var recordDatabase: RecordDatabase
    private lateinit var datasetGenerator: DatasetGenerator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        datasetGenerator = DatasetGenerator(recordDatabase)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorResource(id = R.color.background_color)
                ) {
                    MainNavHost(
                        onBackPressed = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                        onClosePressed = { finish() },
                        mainViewModel = mainViewModel,
                        setKeepScreenFlag = ::setKeepScreenFlag,
                        createDatasetFile = ::createDatasetFile,
                    )
                }
            }
        }

        configureBluetooth()
    }

    private fun configureBluetooth() {
        bluetoothStateReceiver = BluetoothStateReceiver(mainViewModel.bluetoothStatus)
        mainViewModel.bluetoothStatus.checkBluetoothState(
            (applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        )
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        mainViewModel.polarStatus.foregroundEntered()

        messageClient.addListener(mainViewModel.wearableStatus)
        registerCapabilityListener()
    }

    private fun registerCapabilityListener() {
        capabilityClient.addListener(
            mainViewModel.wearableStatus,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        setCapabilitiesListeners(
            listOf(
                WEAR_CAPABILITY,
                ACCELEROMETER_CAPABILITY,
                HEART_RATE_CAPABILITY,
                GYROSCOPE_CAPABILITY,
                AMBIENT_TEMPERATURE_CAPABILITY
            )
        )
    }

    private fun setCapabilitiesListeners(capabilities: List<String>) {
        capabilities.forEach { capability ->
            capabilityClient.getCapability(
                capability,
                CapabilityClient.FILTER_REACHABLE
            ).addOnSuccessListener { capabilityInfo ->
                mainViewModel.wearableStatus.updateCapabilityInfo(
                    mapOf(
                        capabilityInfo.name to capabilityInfo.nodes
                    )
                )
                Log.d(TAG, "Capability info: ${capabilityInfo.nodes} for ${capabilityInfo.name}")
            }.addOnFailureListener { exception ->
                Log.d(TAG, "Failed to get capability info: $exception")
            }
        }
    }

    private fun setKeepScreenFlag(value: Boolean) {
        if (value) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun createDatasetFile(recordId: Long) {
        lifecycleScope.launch {
            val record = recordDatabase.recordDao().getById(recordId)
            if (record == null) {
                Toast.makeText(
                    this@MainActivity,
                    applicationContext.getText(R.string.toast_record_not_found),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val file = File(applicationContext.filesDir, "${record.title}_${System.currentTimeMillis()}.csv")
            Log.d(TAG, "Creating dataset file: ${file.absolutePath}")
            datasetGenerator.generateDataset(recordId,file)
        }
    }

    override fun onPause() {
        super.onPause()
        messageClient.removeListener(mainViewModel.wearableStatus)
        capabilityClient.removeListener(mainViewModel.wearableStatus)
    }

    public override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        mainViewModel.polarStatus.shutDown()
        mainViewModel.onScreenDestroy()
    }
}
