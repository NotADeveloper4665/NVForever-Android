package com.notadeveloper4665.nvforever

import android.os.Bundle
import android.system.Os
import com.google.androidgamesdk.GameActivity
import java.io.File

/**
 * Native Bevy/GameActivity host. Environment paths are set before GameActivity starts
 * the Rust entry point, so the engine never guesses Android storage locations.
 */
class EngineActivity : GameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val dataDir = intent.getStringExtra("data_dir")
            ?: File(filesDir, "game-data").absolutePath
        val homeDir = File(filesDir, "nvforever").absolutePath
        val schemaDir = File(filesDir, "engine-schemas").absolutePath
        copyAssetTree("schemas", File(schemaDir))

        // Os.setenv changes this process environment, which Rust std::env reads after
        // the native library is started by GameActivity.
        Os.setenv("NVFOREVER_DATA", dataDir, true)
        Os.setenv("NVFOREVER_HOME", homeDir, true)
        Os.setenv("NVFOREVER_ESM_SCHEMA", File(schemaDir, "fnv").absolutePath, true)
        Os.setenv("NVFOREVER_NIF_SCHEMA", File(schemaDir, "nif").absolutePath, true)
        Os.setenv("NVF_PLAY_SCRIPT_FUNCTIONS", File(schemaDir, "script/functions.json").absolutePath, true)
        super.onCreate(savedInstanceState)
    }

    /** Copy bundled schemas once; never copy or modify the user's game files here. */
    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            if (!destination.exists()) {
                destination.parentFile?.mkdirs()
                assets.open(assetPath).use { input -> destination.outputStream().use(input::copyTo) }
            }
            return
        }
        destination.mkdirs()
        for (child in children) copyAssetTree("$assetPath/$child", File(destination, child))
    }
}
