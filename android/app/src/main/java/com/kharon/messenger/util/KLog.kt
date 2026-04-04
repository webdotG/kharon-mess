package com.kharon.messenger.util

import android.util.Log

object KLog {
    private const val TAG = "KHARON"

    fun socket(msg: String) = Log.d(TAG, "[SOCKET] $msg")
    fun vm(msg: String)     = Log.d(TAG, "[VM]     $msg")
    fun ui(msg: String)     = Log.d(TAG, "[UI]     $msg")
    fun svc(msg: String)    = Log.d(TAG, "[SVC]    $msg")
    fun sec(msg: String)    = Log.w(TAG, "[SEC]    $msg")
    fun err(msg: String)    = Log.e(TAG, "[ERR]    $msg")
}
