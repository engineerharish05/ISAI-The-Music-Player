@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.rishinnovations.isai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.rishinnovations.isai.R
import com.rishinnovations.isai.data.library.ArtworkModel
import com.rishinnovations.isai.logic.formatDuration
import com.rishinnovations.isai.model.Song
import com.rishinnovations.isai.ui.theme.IsaiPalette
import com.rishinnovations.isai.ui.theme.isai

val LocalFavorites = compositionLocalOf<Set<Long>> { emptySet() }
val LocalNowPlaying = compositionLocalOf<String?> { null }

/** Callbacks shared by every song row so screens don't each need a dozen parameters. */
class SongRowActions(
    val toggleFavorite: (Song) -> Unit,
    val openMenu: (Song) -> Unit
)

val LocalSongRowActions = compositionLocalOf { SongRowActions({}, {}) }

/** Branded placeholder used wherever artwork is missing: the ISAI emblem on a dark tile. */
@Composable
fun EmblemTile(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(IsaiPalette.CharcoalHigh, IsaiPalette.Black))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.isai_emblem),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.8f).alpha(0.9f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun Artwork(song: Song?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape)) {
        if (song == null) {
            EmblemTile(Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = ArtworkModel(song.id, song.uriString),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { EmblemTile(Modifier.fillMaxSize()) },
                error = { EmblemTile(Modifier.fillMaxSize()) }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.isai.goldSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.isai.gold, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, confirmLabel: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it.take(80) }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A song in a vertical list. Tap plays, long-press or the overflow button opens the song menu. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "${song.artist} · ${song.album}",
    leadingNumber: Int? = null,
    showFavorite: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val favorites = LocalFavorites.current
    val actions = LocalSongRowActions.current
    val active = LocalNowPlaying.current == song.id.toString()
    val isFavorite = song.id in favorites
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { actions.openMenu(song) })
            .background(if (active) MaterialTheme.isai.goldSoft else Color.Transparent)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingNumber != null) {
            Text(
                leadingNumber.toString(), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start
            )
        } else {
            Artwork(song, Modifier.size(52.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDuration(song.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        if (showFavorite) {
            IconButton(onClick = { actions.toggleFavorite(song) }) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove ${song.title} from favorites" else "Add ${song.title} to favorites",
                    tint = if (isFavorite) MaterialTheme.isai.gold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing() else {
            IconButton(onClick = { actions.openMenu(song) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${song.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Adds a list of songs; tapping plays the list starting at that song. */
fun LazyListScope.songItems(songs: List<Song>, onPlayAt: (Int) -> Unit, keyPrefix: String = "song") {
    itemsIndexed(songs, key = { index, song -> "$keyPrefix-${song.id}-$index" }) { index, song ->
        SongRow(song, onClick = { onPlayAt(index) })
    }
}

/** Square card used in horizontal rows: artwork, title, subtitle. */
@Composable
fun MediaCard(
    song: Song?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    Column(
        modifier.width(size).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).semantics { contentDescription = "$title, $subtitle" },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Artwork(song, Modifier.size(size), shape)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tile with an icon, used for playlists and special collections that have no single cover. */
@Composable
fun IconTile(icon: ImageVector = Icons.Filled.MusicNote, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)) {
    Box(modifier.clip(shape).background(MaterialTheme.isai.goldSoft), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.isai.gold, modifier = Modifier.fillMaxSize(0.4f))
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

val ListBottomPadding = PaddingValues(bottom = 24.dp)

@Composable
fun BoldLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.isai.gold)

@Composable
fun ProvideRowState(favorites: Set<Long>, nowPlayingId: String?, actions: SongRowActions, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFavorites provides favorites,
        LocalNowPlaying provides nowPlayingId,
        LocalSongRowActions provides actions,
        content = content
    )
}
