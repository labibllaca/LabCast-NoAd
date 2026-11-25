package com.example.data

/**
 * Robolectric reuses its sandbox (and therefore static state) across test classes, while each
 * test gets a fresh application data directory. A [PodcastDatabase] cached by
 * [PodcastDatabase.getDatabase] in an earlier class then points at a stale file. Reset it so a
 * test class starts and ends with a database bound to its own application.
 */
internal fun resetPodcastDatabaseSingleton() {
    val field = PodcastDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    (field.get(null) as PodcastDatabase?)?.close()
    field.set(null, null)
}
