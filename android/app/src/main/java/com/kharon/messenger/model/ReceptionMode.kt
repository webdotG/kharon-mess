package com.kharon.messenger.model

enum class ReceptionMode(val label: String, val minutes: Int) {
    LIVE("В ЭФИРЕ (LIVE)", 0),
    PULSE_15("СЕАНС: 15 МИН", 15),
    PULSE_30("СЕАНС: 30 МИН", 30),
    PULSE_60("СЕАНС: 1 ЧАС", 60),
    PULSE_240("СЕАНС: 4 ЧАСА", 240),
    PULSE_360("СЕАНС: 6 ЧАСОВ", 360),
    PULSE_720("СЕАНС: 12 ЧАСОВ", 720),
    SILENT("ОТДЫХ (ТИШИНА)", -1)
}