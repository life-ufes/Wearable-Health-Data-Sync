package com.example.transferdata

import BluetoothStateReceiver
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.commons.Capabilities.Companion.ACCELEROMETER_CAPABILITY
import com.example.commons.Capabilities.Companion.AMBIENT_TEMPERATURE_CAPABILITY
import com.example.commons.Capabilities.Companion.GRAVITY_CAPABILITY
import com.example.commons.Capabilities.Companion.GYROSCOPE_CAPABILITY
import com.example.commons.Capabilities.Companion.HEART_RATE_CAPABILITY
import com.example.commons.Capabilities.Companion.LINEAR_ACCELERATION_CAPABILITY
import com.example.commons.Capabilities.Companion.WEAR_CAPABILITY
import com.example.transferdata.common.utils.formatToValidFileName
import com.example.transferdata.database.repository.RecordDatabase
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = resources.getColor(R.color.toolbar_background, theme)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorResource(id = R.color.background_color)
                ) {
                    MainNavHost(
                        onBackPressed = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                        mainViewModel = mainViewModel,
                        setKeepScreenFlag = ::setKeepScreenFlag,
                        createDatasetFile = ::createDatasetFile,
                        shareFile = ::shareFile
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
        // testar se o problema de não carregar a tela acontece apenas no meu POCO X3
        // ou se é um problema geral
        window.decorView.post {
            window.decorView.invalidate()
            window.decorView.requestLayout()
        }
    }

    private fun registerCapabilityListener() {
        capabilityClient.addListener(
            mainViewModel.wearableStatus,
            "wear://".toUri(),
            CapabilityClient.FILTER_REACHABLE
        )
        setCapabilitiesListeners(
            listOf(
                WEAR_CAPABILITY,
                ACCELEROMETER_CAPABILITY,
                LINEAR_ACCELERATION_CAPABILITY,
                HEART_RATE_CAPABILITY,
                GYROSCOPE_CAPABILITY,
                GRAVITY_CAPABILITY,
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

    private fun createDatasetFile(recordId: Long, onFileCreated: (File) -> Unit) {
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

            val downloadsFile =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsFile.mkdirs()
            val file = File(
                downloadsFile,
                "${record.title.formatToValidFileName()}_${System.currentTimeMillis()}.csv"
            )

            onFileCreated(file)
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "text/csv"
        }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing file: ${e.message}")
            Toast.makeText(this, getString(R.string.toast_error_sharing_file), Toast.LENGTH_SHORT)
                .show()
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
