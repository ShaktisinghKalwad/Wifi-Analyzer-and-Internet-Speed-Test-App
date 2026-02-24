package com.srk.wifianalyzer.domain.model

enum class WifiChannelWidth(val mhz: Int) {
    W20(20),
    W40(40),
    W80(80),
    W160(160),
    W80P80(160),
    Unknown(0),
}
