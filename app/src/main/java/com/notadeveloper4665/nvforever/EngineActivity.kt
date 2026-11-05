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
        val dataRoot = File(intent.getStringExtra("data_dir") ?: File(filesDir, "game-data").absolutePath)
        val dataDir = dataRoot.canonicalPath
        val dataFiles = File(dataRoot, "Data")
        check(dataFiles.isDirectory && File(dataFiles, "FalloutNV.esm").isFile) { "Fallout: New Vegas Data directory is missing or invalid" }
        val homeRoot = File(intent.getStringExtra("home_dir") ?: File(filesDir, "nvforever").absolutePath)
        if (!homeRoot.exists() && !homeRoot.mkdirs()) throw IllegalStateException("NVForever could not create writable app-private storage")
        val homeDir = homeRoot.canonicalPath
        val schemaDir = File(filesDir, "engine-schemas").absolutePath
        val schemaRoot = File(schemaDir)
        val schemaMarker = File(schemaRoot, ".schema-version")
        val schemaVersion = BuildConfig.VERSION_CODE.toString()
        if (schemaMarker.takeIf { it.isFile }?.readText() != schemaVersion ||
            !File(schemaRoot, "fnv").isDirectory ||
            !File(schemaRoot, "nif").isDirectory ||
            !File(schemaRoot, "script/functions.json").isFile) {
            schemaRoot.deleteRecursively()
            copyAssetTree("", schemaRoot)
            schemaMarker.writeText(schemaVersion)
        }

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
