package uk.pixxula.pawse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Pawse's own colours, the same on every phone: warm terracotta on cream by
// day, and on a dark brown by night. They match the launcher icon.

private val Light = lightColorScheme(
    primary = Color(0xFFA8452F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF775750),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD2),
    onSecondaryContainer = Color(0xFF2C1510),
    tertiary = Color(0xFF6E5D2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9E1A7),
    onTertiaryContainer = Color(0xFF231B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DDD8),
    onSurfaceVariant = Color(0xFF534340),
    outline = Color(0xFF85736F),
    outlineVariant = Color(0xFFD8C2BD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EC),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF7E4DF),
    surfaceContainerHighest = Color(0xFFF1DFDA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A1),
    onPrimary = Color(0xFF651A08),
    primaryContainer = Color(0xFF862F1B),
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Color(0xFFE7BDB4),
    onSecondary = Color(0xFF442A24),
    secondaryContainer = Color(0xFF5D3F39),
    onSecondaryContainer = Color(0xFFFFDAD2),
    tertiary = Color(0xFFDCC58D),
    onTertiary = Color(0xFF3C2F04),
    tertiaryContainer = Color(0xFF544519),
    onTertiaryContainer = Color(0xFFF9E1A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF534340),
    onSurfaceVariant = Color(0xFFD8C2BD),
    outline = Color(0xFFA08C88),
    outlineVariant = Color(0xFF534340),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3230),
)

private val Rounded = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun PawseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Rounded,
        content = content,
    )
}

/** The paw, in whatever colour the place calls for. */
@Composable
fun Paw(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_paw),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** The title of a screen that is reached from Home, with the way back. */
@Composable
fun TopBar(title: String, back: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = back) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** An installed app's own icon, loaded off the main thread. */
@Composable
fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    Box(modifier.size(size)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? =
    try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = Bitmap.createBitmap(ICON_PIXELS, ICON_PIXELS, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PIXELS, ICON_PIXELS)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }

private const val ICON_PIXELS = 96
