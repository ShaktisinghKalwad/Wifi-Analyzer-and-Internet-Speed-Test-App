package com.srk.wifianalyzer.domain.model

enum class WifiSecurity(val label: String) {
    Open("Open"),
    Wep("WEP"),
    Wpa("WPA"),
    Wpa2("WPA2"),
    Wpa3("WPA3"),
    Owe("OWE"),
    Unknown("Unknown"),
}
