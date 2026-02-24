package com.srk.wifianalyzer.data.mapper

import android.net.wifi.ScanResult
import android.os.Build
import com.srk.wifianalyzer.domain.model.WifiAccessPoint
import com.srk.wifianalyzer.domain.model.WifiBand
import com.srk.wifianalyzer.domain.model.WifiChannelWidth
import com.srk.wifianalyzer.domain.model.WifiSecurity
import com.srk.wifianalyzer.domain.model.WifiStandard

internal fun ScanResult.toDomain(nowMs: Long): WifiAccessPoint {
    @Suppress("NewApi")
    val rawSsid = if (Build.VERSION.SDK_INT >= 33) {
        try {
            wifiSsid?.toString()
        } catch (_: Throwable) {
            null
        }
    } else {
        null
    }

    val ssid = (rawSsid ?: SSID.orEmpty())
        .trim()
        .trim('"')
        .takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
        .orEmpty()

    val frequencyMhz = frequency
    val center0Mhz = centerFreq0.takeIf { it > 0 }
    val center1Mhz = centerFreq1.takeIf { it > 0 }
    val band = when {
        frequencyMhz in 2400..2500 -> WifiBand.Band2G
        frequencyMhz in 4900..5900 -> WifiBand.Band5G
        frequencyMhz in 5925..7125 || frequencyMhz == 5935 -> WifiBand.Band6G
        else -> WifiBand.Unknown
    }

    val channel = frequencyMhzToChannel(frequencyMhz)

    val channelWidth = when (channelWidth) {
        0 -> WifiChannelWidth.W20
        1 -> WifiChannelWidth.W40
        2 -> WifiChannelWidth.W80
        3 -> WifiChannelWidth.W160
        4 -> WifiChannelWidth.W80P80
        else -> WifiChannelWidth.Unknown
    }

    val security = parseSecurity(capabilities)

    val standard = if (Build.VERSION.SDK_INT >= 30) {
        when (wifiStandard) {
            ScanResult.WIFI_STANDARD_11N -> WifiStandard.N
            ScanResult.WIFI_STANDARD_11AC -> WifiStandard.Ac
            ScanResult.WIFI_STANDARD_11AX -> WifiStandard.Ax
            ScanResult.WIFI_STANDARD_LEGACY -> {
                when (band) {
                    WifiBand.Band2G -> WifiStandard.G
                    WifiBand.Band5G -> WifiStandard.A
                    else -> WifiStandard.Unknown
                }
            }
            else -> WifiStandard.Unknown
        }
    } else {
        WifiStandard.Unknown
    }

    return WifiAccessPoint(
        ssid = ssid,
        bssid = BSSID ?: "",
        rssiDbm = level,
        frequencyMhz = frequencyMhz,
        centerFreq0Mhz = center0Mhz,
        centerFreq1Mhz = center1Mhz,
        band = band,
        channel = channel,
        channelWidth = channelWidth,
        security = security,
        standard = standard,
        lastSeenMs = nowMs,
    )
}

private fun frequencyMhzToChannel(freq: Int): Int? {
    if (freq == 2484) return 14
    if (freq in 2412..2472) return (freq - 2407) / 5
    if (freq in 5000..5900) return (freq - 5000) / 5
    if (freq == 5935) return 2
    if (freq in 5955..7115) return (freq - 5950) / 5
    return null
}

private fun parseSecurity(cap: String?): WifiSecurity {
    val c = (cap ?: "").uppercase()
    return when {
        "OWE" in c -> WifiSecurity.Owe
        "SAE" in c || "WPA3" in c -> WifiSecurity.Wpa3
        "WPA2" in c -> WifiSecurity.Wpa2
        "WPA" in c -> WifiSecurity.Wpa
        "WEP" in c -> WifiSecurity.Wep
        c.isBlank() -> WifiSecurity.Open
        else -> WifiSecurity.Unknown
    }
}
