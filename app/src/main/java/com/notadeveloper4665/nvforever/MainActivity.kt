package com.notadeveloper4665.nvforever

import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android launcher and platform boundary.
 *
 * SAF URIs remain URIs: this activity never treats a content:// URI as a
 * filesystem path and never writes into the selected game installation.
 * The native NVForever engine runs in a dedicated GameActivity after game data is staged.
 */
class MainActivity : ComponentActivity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var selectedPath: TextView
    private lateinit var controllerStatus: TextView
    private lateinit var importButton: Button
    private lateinit var launchButton: Button
    private val importCancelled = AtomicBoolean(false)
    private var importWorker: Thread? = null
    private var gameTree: Uri? = null
    private var lastInputSummary: String = "No controller input observed"
    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // takePersistableUriPermission accepts READ/WRITE mode flags, not the
                // persistable-grant request flag. Keep the game tree read-only.
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
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
        refreshLaunchAvailability()
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
        importButton = Button(this).apply {
            text = "IMPORT GAME DATA TO DEVICE"
            isEnabled = false
            setOnClickListener { startOrCancelImport() }
        }
        launchButton = Button(this).apply {
            text = "LAUNCH NEW VEGAS"
            isEnabled = false
            contentDescription = "Launch NVForever using the imported game data"
            setOnClickListener { launchEngine() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(selectedPath)
        root.addView(choose)
        root.addView(status)
        root.addView(importButton)
        root.addView(controllerStatus)
        root.addView(launchButton)
        setContentView(root)
    }

    private fun restoreInstallation() {
        val saved = getSharedPreferences("installation", MODE_PRIVATE)
            .getString("tree_uri", null) ?: return
        runCatching { Uri.parse(saved) }.onSuccess { uri ->
            gameTree = uri
            showInstallation(uri)
            refreshLaunchAvailability()
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
            val missing = GameDataImporter.REQUIRED_DATA_FILES.filter { name ->
                data.findFile(name)?.let { it.isFile && it.length() > 0L } != true
            }
            selectedPath.text = tree.name ?: uri.lastPathSegment ?: "Selected game folder"
            importButton.isEnabled = missing.isEmpty() && importWorker?.isAlive != true
            status.text = if (missing.isEmpty()) {
                "All ${GameDataImporter.REQUIRED_DATA_FILES.size} required base-game files detected. Source access is read-only."
            } else {
                "Missing or empty required files: ${missing.joinToString(", ")}"
            }
        } catch (e: SecurityException) {
            selectedPath.text = "Selected folder is no longer accessible"
            status.text = "Folder permission was revoked. Select the game folder again."
        } catch (e: Exception) {
            selectedPath.text = "Could not inspect selected folder"
            status.text = "Storage provider error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Copies multi-gigabyte game data on a worker thread into app-private storage.
     * This is a staging boundary for the path-based Rust VFS, not yet a game launch.
     */
    private fun startOrCancelImport() {
        if (importWorker?.isAlive == true) {
            importCancelled.set(true)
            importButton.text = "CANCELLING…"
            status.text = "Stopping import safely. The previous completed import is preserved."
            return
        }
        val uri = gameTree ?: run { status.text = "Select the game folder first."; return }
        importCancelled.set(false)
        importButton.isEnabled = true
        importButton.text = "CANCEL IMPORT"
        status.text = "Preparing private game-data import…"
        val lastUiUpdate = AtomicLong(0L)
        importWorker = Thread {
            try {
                val destination = GameDataImporter.import(applicationContext, uri, importCancelled) { p ->
                    // Limit main-thread UI work while a large archive tree is being copied.
                    val now = SystemClock.uptimeMillis()
                    val previous = lastUiUpdate.get()
                    if (now - previous >= 150L || p.percent >= 100) {
                        if (lastUiUpdate.compareAndSet(previous, now)) {
                            runOnUiThread {
                                if (!isDestroyed) status.text = "Importing game data: ${p.percent}% · ${p.filesCopied}/${p.totalFiles} files"
                            }
                        }
                    }
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        getSharedPreferences("installation", MODE_PRIVATE).edit()
                            .putString("imported_data_path", destination.absolutePath).apply()
                        status.text = "Game data staged in app-private storage. The source installation remains untouched."
                        importButton.text = "RE-IMPORT GAME DATA"
                        importButton.isEnabled = true
                        refreshLaunchAvailability()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isDestroyed) {
                        status.text = e.message ?: "Game-data import failed."
                        importButton.text = "RETRY GAME DATA IMPORT"
                        importButton.isEnabled = gameTree != null
                    }
                }
            }
        }.apply { name = "NVForever-GameDataImport"; start() }
    }

    override fun onDestroy() {
        // Avoid leaving a large background copy running after the launcher is destroyed.
        importCancelled.set(true)
        super.onDestroy()
    }

    /**
     * Resolve the synthetic install root expected by fnv-install:
     * filesDir/game-install/Data and filesDir/nvforever must be siblings.
     *
     * Older APKs stored Data directly in filesDir/game-data. Move that completed import
     * into the new layout on upgrade so users do not need to copy several GB again.
     */
    private fun resolveImportedGameRoot(): java.io.File? {
        val prefs = getSharedPreferences("installation", MODE_PRIVATE)
        val saved = prefs.getString("imported_data_path", null) ?: return null
        val savedFile = java.io.File(saved)
        val legacyData = java.io.File(filesDir, "game-data")
        val installRoot = java.io.File(filesDir, "game-install")
        val installData = java.io.File(installRoot, "Data")

        val isLegacy = runCatching { savedFile.canonicalFile == legacyData.canonicalFile }.getOrDefault(false)
        if (isLegacy && java.io.File(legacyData, "FalloutNV.esm").isFile) {
            if (!java.io.File(installData, "FalloutNV.esm").isFile) {
                if (!installRoot.exists() && !installRoot.mkdirs()) return null
                if (!installData.exists() && !legacyData.renameTo(installData)) return null
            }
            if (java.io.File(installData, "FalloutNV.esm").isFile) {
                prefs.edit().putString("imported_data_path", installRoot.absolutePath).apply()
                return installRoot
            }
        }

        return savedFile.takeIf { java.io.File(it, "Data/FalloutNV.esm").isFile }
    }

    private fun refreshLaunchAvailability() {
        launchButton.isEnabled = resolveImportedGameRoot() != null
    }

    private fun launchEngine() {
        val root = resolveImportedGameRoot() ?: run {
            status.text = "Import game data before launching, or re-select the game folder."
            launchButton.isEnabled = false
            return
        }
        // Pass the install root, not Data itself. This keeps the game install sibling to
        // NVForever's writable home and satisfies the engine's overlap guard.
        startActivity(Intent(this, EngineActivity::class.java).putExtra("data_dir", root.absolutePath))
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
