package com.deciboost.feature.boost

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OutputDeviceInfo(
    val label: String,
)

suspend fun resolveCurrentOutputDevice(context: Context): OutputDeviceInfo = withContext(Dispatchers.IO) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val device = resolveActiveOutputDevice(audioManager)
    OutputDeviceInfo(label = device?.displayLabel() ?: "Unknown output")
}

private fun resolveActiveOutputDevice(audioManager: AudioManager): AudioDeviceInfo? {
    val sinks = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
    if (sinks.isEmpty()) return null
    val priorityTypes = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    )
    for (type in priorityTypes) {
        sinks.firstOrNull { it.type == type }?.let { return it }
    }
    return sinks.firstOrNull()
}

private fun AudioDeviceInfo.displayLabel(): String {
    val typeName = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth speaker"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        else -> productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio output"
    }
    return if (!productName.isNullOrBlank() && typeName == "Audio output") {
        productName.toString()
    } else {
        typeName
    }
}