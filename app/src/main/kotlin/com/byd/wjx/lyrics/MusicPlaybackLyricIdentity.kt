package com.byd.wjx.lyrics

import java.util.Locale

/** Uses the store-stabilized track metadata while still distinguishing real track changes. */
internal fun MusicPlaybackSnapshot.lyricKey(): String =
    "${packageName.trim().lowercase(Locale.ROOT)}\u0000${songKey()}"
