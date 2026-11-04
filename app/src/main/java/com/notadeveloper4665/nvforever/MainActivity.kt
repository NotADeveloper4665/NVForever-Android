package com.notadeveloper4665.nvforever

import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

/**
 * Android launcher and platform boundary.
 *
 * SAF URIs remain URIs: this activity never treats a content:// URI as a
 * filesystem path and never writes into the selected game installation.
 * The native NVForever engine is not wired yet, so Launch stays disabled.
 */
class MainActivity : ComponentActivity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var selectedPath: TextView
    private lateinit var controllerStatus: TextView
    private var gameTree: Uri? = null
    private var lastInputSummary: String = "No controller input observed"
    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
                gameTree = uri
                getSharedPreferences("installation", MODE_PRIVATE)
                    .edit().putString("tree_uri", uri.toString()).apply()
                showInstallation(uri)
            } catch (e: SecurityException) {
                status.text = "Android could not save folder access: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLauncher()
        restoreInstallation()
        refreshControllers()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refreshControllers()
        gameTree?.let { showInstallation(it) }
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }

    private fun buildLauncher() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(0xFF10151B.toInt())
        }

        val title = TextView(this).apply {
            text = "NVFOREVER"
            textSize = 30f
            setTextColor(0xFFE8D7B0.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "FALLOUT: NEW VEGAS · ANDROID PORT"
            textSize = 12f
            setTextColor(0xFF9BA7B4.toInt())
            gravity = Gravity.CENTER
        }
        selectedPath = TextView(this).apply {
            text = "No game folder selected"
            textSize = 14f
            setTextColor(0xFFE0E6EB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 12)
        }
        val choose = Button(this).apply {
            text = "SELECT GAME FOLDER"
            setOnClickListener { folderPicker.launch(null) }
        }
        status = TextView(this).apply {
            text = "Select the folder containing FalloutNV.esm and the Data directory."
            textSize = 13f
            setTextColor(0xFFB8C2CC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        controllerStatus = TextView(this).apply {
            text = "Checking controllers…"
            textSize = 12f
            setTextColor(0xFF9BA7B4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        }
        val launch = Button(this).apply {
            text = "ENGINE NOT INTEGRATED YET"
            isEnabled = false
            contentDescription = "Launch is unavailable until the native engine is integrated"
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(selectedPath)
        root.addView(choose)
        root.addView(status)
        root.addView(controllerStatus)
        root.addView(launch)
        setContentView(root)
    }

    private fun restoreInstallation() {
        val saved = getSharedPreferences("installation", MODE_PRIVATE)
            .getString("tree_uri", null) ?: return
        runCatching { Uri.parse(saved) }.onSuccess { uri ->
            gameTree = uri
            showInstallation(uri)
        }.onFailure {
            status.text = "Saved folder reference could not be restored. Select it again."
        }
    }

    private fun showInstallation(uri: Uri) {
        try {
            val tree = DocumentFile.fromTreeUri(this, uri)
            if (tree == null || !tree.canRead()) {
                selectedPath.text = "Selected folder is no longer accessible"
                status.text = "Grant access again using Select Game Folder."
                return
            }

            // Accept either the game root or the Data directory itself.
            val data = tree.findFile("Data")?.takeIf { it.isDirectory } ?: tree
            val master = data.findFile("FalloutNV.esm")
            val archives = data.listFiles().count {
                it.isFile && it.name?.lowercase(Locale.ROOT)?.endsWith(".bsa") == true
            }
            selectedPath.text = tree.name ?: uri.lastPathSegment ?: "Selected game folder"
            status.text = when {
                master?.isFile != true ->
                    "FalloutNV.esm not found. Select the game root or its Data folder."
                archives == 0 ->
                    "FalloutNV.esm found, but no BSA archives were detected in Data. Check the installation."
                else ->
                    "Installation detected: FalloutNV.esm and $archives BSA archive(s). Access is read-only."
            }
        } catch (e: SecurityException) {
            selectedPath.text = "Selected folder is no longer accessible"
            status.text = "Folder permission was revoked. Select the game folder again."
        } catch (e: Exception) {
            selectedPath.text = "Could not inspect selected folder"
            status.text = "Storage provider error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun isController(device: InputDevice?): Boolean {
        if (device == null) return false
        return device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun refreshControllers() {
        val devices = inputManager.inputDeviceIds
            .map { id -> inputManager.getInputDevice(id) }
            .filterNotNull()
            .filter { device -> isController(device) }
        controllerStatus.text = if (devices.isEmpty()) {
            "CONTROLLER: none connected · native gameplay mapping not integrated"
        } else {
            "CONTROLLER: " + devices.joinToString { it.name } +
                " · input diagnostics only, engine mapping pending"
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshControllers()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshControllers()
    override fun onInputDeviceChanged(deviceId: Int) = refreshControllers()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && KeyEvent.isGamepadButton(event.keyCode)) {
            val device = inputManager.getInputDevice(event.deviceId)
            if (isController(device)) {
                lastInputSummary = "${device?.name ?: "Controller"} · ${KeyEvent.keyCodeToString(event.keyCode)}"
                updateControllerInputSummary()
            }
        }
        // Do not consume controller events: Android navigation and future engine
        // mappings must continue to receive them until an explicit input bridge exists.
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE &&
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                event.isFromSource(InputDevice.SOURCE_GAMEPAD))) {
            val device = inputManager.getInputDevice(event.deviceId)
            if (isController(device)) {
                val lx = event.getAxisValue(MotionEvent.AXIS_X)
                val ly = event.getAxisValue(MotionEvent.AXIS_Y)
                val rx = event.getAxisValue(MotionEvent.AXIS_Z)
                val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
                val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
                val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
                lastInputSummary = String.format(
                    Locale.US, "%s · L %.2f, %.2f · R %.2f, %.2f · T %.2f/%.2f",
                    device?.name ?: "Controller", lx, ly, rx, ry, lt, rt
                )
                updateControllerInputSummary()
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun updateControllerInputSummary() {
        controllerStatus.text = "CONTROLLER INPUT: $lastInputSummary\n" +
            "Diagnostics only · gameplay bridge not integrated"
    }
}
