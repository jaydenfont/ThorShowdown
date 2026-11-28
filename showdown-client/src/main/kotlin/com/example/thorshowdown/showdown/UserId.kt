package com.example.thorshowdown.showdown

/** Showdown's canonical ID for a name: lowercase letters and digits only, e.g. "Coffee Enjoyer" -> "coffeeenjoyer". */
public fun toId(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
