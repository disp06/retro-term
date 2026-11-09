package com.disp06.retroterm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

/** Paired Bluetooth devices and whether each is currently linked. */
object BtInfo {
    class Dev(val name: String, val connected: Boolean)
    class State(val radioOn: Boolean?, val devices: List<Dev>)

    fun read(): State {
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return State(null, emptyList())
        val on = runCatching { adapter.isEnabled }.getOrDefault(false)
        val devs = runCatching { adapter.bondedDevices?.toList() ?: emptyList() }.getOrDefault(emptyList())
            .map { d -> Dev((runCatching { d.name }.getOrNull() ?: d.address ?: "?").uppercase(), on && isConnected(d)) }
            .sortedWith(compareByDescending<Dev> { it.connected }.thenBy { it.name })
        return State(on, devs)
    }

    // BluetoothDevice.isConnected() is hidden but reachable for targetSdk 28 apps
    private fun isConnected(d: BluetoothDevice): Boolean = runCatching {
        d.javaClass.getMethod("isConnected").invoke(d) as Boolean
    }.getOrDefault(false)
}
