package eu.darken.octi.syncs.octiserver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.syncs.octiserver.R as OctiServerR

@Composable
fun OctiServerIcon(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(OctiServerR.drawable.ic_octiserver),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.clip(RoundedCornerShape(6.dp)),
    )
}

@Preview2
@Composable
private fun OctiServerIconPreview() = PreviewWrapper {
    OctiServerIcon(modifier = Modifier.size(24.dp))
}
