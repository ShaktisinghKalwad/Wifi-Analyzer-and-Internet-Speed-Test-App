package com.srk.wifianalyzer.domain.model

enum class WifiStandard(val label: String) {
    A("802.11a"),
    B("802.11b"),
    G("802.11g"),
    N("802.11n"),
    Ac("802.11ac"),
    Ax("802.11ax"),
    Unknown("Unknown"),
}
