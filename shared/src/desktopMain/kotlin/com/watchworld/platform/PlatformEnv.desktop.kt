package com.watchworld.platform

actual fun platformEnv(name: String): String? = System.getenv(name)
