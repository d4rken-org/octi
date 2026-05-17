package eu.darken.octi.modules.meta.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Public
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.octi.modules.meta.R
import eu.darken.octi.modules.meta.core.MetaInfo

fun MetaInfo.DeviceType?.materialIcon(): ImageVector = when (this) {
    MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
    MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
    MetaInfo.DeviceType.DESKTOP -> Icons.TwoTone.Computer
    MetaInfo.DeviceType.BROWSER -> Icons.TwoTone.Public
    MetaInfo.DeviceType.UNKNOWN, null -> Icons.TwoTone.QuestionMark
}

@StringRes
fun MetaInfo.DeviceType.labelRes(): Int = when (this) {
    MetaInfo.DeviceType.PHONE -> R.string.module_meta_detail_device_type_phone
    MetaInfo.DeviceType.TABLET -> R.string.module_meta_detail_device_type_tablet
    MetaInfo.DeviceType.DESKTOP -> R.string.module_meta_detail_device_type_desktop
    MetaInfo.DeviceType.BROWSER -> R.string.module_meta_detail_device_type_browser
    MetaInfo.DeviceType.UNKNOWN -> R.string.module_meta_detail_device_type_unknown
}
