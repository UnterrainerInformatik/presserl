package info.unterrainer.presserl.admin.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.lead_image_no_preview
import info.unterrainer.presserl.admin.ui.editor.DraftLeadImage
import org.jetbrains.compose.resources.stringResource

/** Widest preview of a lead image. */
val PREVIEW_MAX_WIDTH = 240.dp

/**
 * The lead image's thumbnail at most [PREVIEW_MAX_WIDTH] wide, in the stored image's aspect ratio; a plain box
 * while it loads or when there is none yet.
 */
@Composable
fun LeadImagePreview(image: DraftLeadImage, thumbnails: Thumbnails, modifier: Modifier = Modifier) {
    LaunchedEffect(image.mediaId) { thumbnails.fetch(image.mediaId) }
    val ratio = if (image.width > 0 && image.height > 0) image.width.toFloat() / image.height else 1.5f
    val frame = modifier.widthIn(max = PREVIEW_MAX_WIDTH).aspectRatio(ratio)
    when (val thumbnail = thumbnails[image.mediaId]) {
        is Thumbnails.Result.Loaded -> Image(thumbnail.image, contentDescription = image.caption.ifEmpty { null },
            modifier = frame, contentScale = ContentScale.Fit)
        else -> Box(frame.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (thumbnail == Thumbnails.Result.Missing) {
                Text(stringResource(Res.string.lead_image_no_preview), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A read-only lead image: preview and caption. */
@Composable
fun LeadImageView(image: DraftLeadImage, thumbnails: Thumbnails) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LeadImagePreview(image, thumbnails)
        if (image.caption.isNotEmpty()) Text(image.caption, style = MaterialTheme.typography.bodySmall)
    }
}

/** A small picture symbol (frame, mountain, sun) drawn in the content colour; emoji fonts are not loaded on the canvas. */
@Composable
fun PictureIcon(size: Dp = 20.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension / 12
        drawRect(color, topLeft = Offset(stroke, stroke * 2), size = Size(this.size.width - 2 * stroke, this.size.height - 4 * stroke),
            style = Stroke(width = stroke))
        drawCircle(color, radius = this.size.minDimension / 10, center = Offset(this.size.width * 0.68f, this.size.height * 0.36f))
        val mountain = Path().apply {
            moveTo(this@Canvas.size.width * 0.18f, this@Canvas.size.height * 0.78f)
            lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.45f)
            lineTo(this@Canvas.size.width * 0.62f, this@Canvas.size.height * 0.78f)
            close()
        }
        drawPath(mountain, color)
    }
}
