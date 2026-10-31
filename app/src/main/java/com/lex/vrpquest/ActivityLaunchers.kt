package com.lex.vrpquest

import android.content.Context
import android.content.Intent

fun LaunchMainActivity(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(intent)
}