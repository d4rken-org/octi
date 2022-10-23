package eu.darken.octi.main.ui.dashboard.items

import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.octi.R
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.common.lists.binding
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.descriptionRes
import eu.darken.octi.common.permissions.labelRes
import eu.darken.octi.databinding.DashboardPermissionItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class PermissionVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<PermissionVH.Item, DashboardPermissionItemBinding>(
        R.layout.dashboard_permission_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardPermissionItemBinding.bind(itemView) }

    override val onBindData: DashboardPermissionItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        bindItem(
            item.permission,
            item.onDismiss,
            item.onGrant,
        )
    }

    data class Item(
        val permission: Permission,
        val onDismiss: (Permission) -> Unit,
        val onGrant: (Permission) -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}

fun DashboardPermissionItemBinding.bindItem(
    permission: Permission,
    onDismiss: (Permission) -> Unit,
    onGrant: (Permission) -> Unit,
) {
    dismissAction.setOnClickListener { onDismiss(permission) }
    grantAction.setOnClickListener { onGrant(permission) }

    permissionTitle.text = root.context.getString(permission.labelRes)
    permissionBody.text = root.context.getString(permission.descriptionRes)

    privacyPolicyLink.apply {
        movementMethod = LinkMovementMethod.getInstance()
        val ppp = setOf(
            Permission.ACCESS_COARSE_LOCATION,
            Permission.ACCESS_FINE_LOCATION,
        )

        if (hasApiLevel(24)) {
            val ppText = root.context.getString(R.string.settings_privacy_policy_label)
            val ppLink = PrivacyPolicy.URL
            text = Html.fromHtml("<html><a href=\"$ppLink\">$ppText</a></html>", 0)
        } else {
            setOnClickListener { WebpageTool(context).open(PrivacyPolicy.URL) }
        }

        isGone = !ppp.contains(permission)
    }
}