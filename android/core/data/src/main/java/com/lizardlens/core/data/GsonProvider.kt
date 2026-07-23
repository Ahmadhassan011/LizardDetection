package com.lizardlens.core.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal object GsonProvider {
    val gson: Gson = GsonBuilder().create()
}
