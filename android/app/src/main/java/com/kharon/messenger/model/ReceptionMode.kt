package com.kharon.messenger.model

enum class ReceptionMode(val label: String, val minutes: Int) {
    LIVE("В ЭФИРЕ (LIVE)", 0),
    PULSE_15("ОКНО СВЯЗИ раз в : 15 МИН", 15),
    PULSE_30("ОКНО СВЯЗИ раз в : 30 МИН", 30),
    PULSE_60("ОКНО СВЯЗИ раз в : 1 ЧАС", 60),
    PULSE_240("ОКНО СВЯЗИ раз в : 4 ЧАСА", 240);

    fun toUiString(): String = label
}