package com.notadeveloper4665.nvforever

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile

/**
 * Initial Android shell. Owns the SAF permission and installation validation.
 * Native NVForever engine integration is a later milestone; this screen does not
 * claim to launch the game yet.
 */
class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var selectedPath: TextView
    private var gameTree: Uri? = null

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
                status.text = "Android did not grant persistent folder access: ${e.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildLauncher()
        restoreInstallation()
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
        val launch = Button(this).apply {
            text = "ENGINE NOT INTEGRATED YET"
            isEnabled = false
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(selectedPath)
        root.addView(choose)
        root.addView(status)
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
        val tree = DocumentFile.fromTreeUri(this, uri)
        if (tree == null || !tree.canRead()) {
            selectedPath.text = "Selected folder is no longer accessible"
            status.text = "Grant access again using Select Game Folder."
            return
        }

        val data = tree.findFile("Data") ?: tree
        val hasMaster = data.findFile("FalloutNV.esm")?.isFile == true
        selectedPath.text = tree.name ?: uri.lastPathSegment ?: "Selected game folder"
        status.text = if (hasMaster) {
            "Base game master found. Folder permission saved. Engine integration is in progress."
        } else {
            "FalloutNV.esm was not found here. Choose the game root or its Data folder."
        }
    }

    // Keep gamepad input observable in the shell; engine action mapping is not wired yet.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_GAMEPAD ==
            android.view.InputDevice.SOURCE_GAMEPAD) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = event.source and android.view.InputDevice.SOURCE_JOYSTICK ==
            android.view.InputDevice.SOURCE_JOYSTICK
        return if (isJoystick) true else super.onGenericMotionEvent(event)
    }
}
