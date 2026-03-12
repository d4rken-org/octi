package eu.darken.octi.common.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import eu.darken.octi.common.R

@Composable
fun OctiMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_octi_mascot),
        contentDescription = null,
        modifier = modifier,
    )
}
