package com.byd.wjx.lyrics

/** Uses the store-stabilized track metadata while still distinguishing real track changes. */
internal fun MusicPlaybackSnapshot.lyricKey(): String =
    songKey()
