package com.oguzozsahin.ucusmodu.data

/** Sayacin icinde bulundugu asama. */
enum class Phase(val key: String) {
    FOCUS("focus"),
    BREAK("break"),
    LONG("long");

    companion object {
        fun fromKey(key: String): Phase = entries.firstOrNull { it.key == key } ?: FOCUS
    }
}
