package com.kharon.messenger.model

enum class ReceptionMode(val label: String, val minutes: Int) {
    LIVE       ("LIVE — всегда на связи",    0),
    PULSE_5    ("Окно связи раз в 5 мин",    5),
    PULSE_15   ("Окно связи раз в 15 мин",   15),
    PULSE_30   ("Окно связи раз в 30 мин",   30),
    PULSE_60   ("Окно связи раз в 1 час",    60),
    PULSE_240  ("Окно связи раз в 4 часа",   240),
    PULSE_360  ("Окно связи раз в 6 часов",  360),
    PULSE_720  ("Окно связи раз в 12 часов", 720),
    PULSE_1440 ("Окно связи раз в 24 часа",  1440),
    SILENT     ("Тишина — нет окна связи",   -1);

    fun toUiString(): String = label
}
