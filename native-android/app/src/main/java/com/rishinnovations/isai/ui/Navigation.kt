package com.rishinnovations.isai.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

/** Screens that sit on top of the five main tabs. Encoded as strings so they survive process death. */
sealed interface Route {
    data class Album(val key: String) : Route
    data class Artist(val name: String) : Route
    data class Genre(val name: String) : Route
    data class Playlist(val id: Long) : Route
    /** Built-in lists: favorites, recent, added, most, rec:<id>, decade:<year> */
    data class Collection(val kind: String) : Route
    data object Search : Route
    data object Equalizer : Route
    data object Stats : Route
    data object About : Route
    data object Licenses : Route
    data object Privacy : Route
}

fun Route.encode(): String = when (this) {
    is Route.Album -> "album|$key"
    is Route.Artist -> "artist|$name"
    is Route.Genre -> "genre|$name"
    is Route.Playlist -> "playlist|$id"
    is Route.Collection -> "collection|$kind"
    Route.Search -> "search"
    Route.Equalizer -> "equalizer"
    Route.Stats -> "stats"
    Route.About -> "about"
    Route.Licenses -> "licenses"
    Route.Privacy -> "privacy"
}

fun decodeRoute(text: String): Route? {
    val kind = text.substringBefore('|')
    val arg = text.substringAfter('|', "")
    return when (kind) {
        "album" -> Route.Album(arg)
        "artist" -> Route.Artist(arg)
        "genre" -> Route.Genre(arg)
        "playlist" -> arg.toLongOrNull()?.let { Route.Playlist(it) }
        "collection" -> Route.Collection(arg)
        "search" -> Route.Search
        "equalizer" -> Route.Equalizer
        "stats" -> Route.Stats
        "about" -> Route.About
        "licenses" -> Route.Licenses
        "privacy" -> Route.Privacy
        else -> null
    }
}

object Tabs {
    const val HOME = 0
    const val EXPLORE = 1
    const val LIBRARY = 2
    const val PLAYLISTS = 3
    const val SETTINGS = 4
}

@Stable
class NavState(initialTab: Int = Tabs.HOME, initialLibraryTab: Int = 0, initialStack: List<Route> = emptyList()) {
    var tab by mutableIntStateOf(initialTab)
        private set
    var libraryTab by mutableIntStateOf(initialLibraryTab)
    val stack: SnapshotStateList<Route> = mutableStateListOf<Route>().apply { addAll(initialStack) }

    val current: Route? get() = stack.lastOrNull()

    fun selectTab(index: Int) {
        tab = index
        stack.clear()
    }

    fun openLibrary(subTab: Int) {
        libraryTab = subTab
        selectTab(Tabs.LIBRARY)
    }

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false

    companion object {
        val Saver = listSaver<NavState, String>(
            save = { listOf(it.tab.toString(), it.libraryTab.toString()) + it.stack.map(Route::encode) },
            restore = { saved ->
                NavState(saved[0].toInt(), saved[1].toInt(), saved.drop(2).mapNotNull(::decodeRoute))
            }
        )
    }
}
