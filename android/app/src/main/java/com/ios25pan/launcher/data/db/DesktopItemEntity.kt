package com.ios25pan.launcher.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.Zone

/** 桌面布局表：每个元素一行（x/y 即 row/col，外加 rotation、type、action）。 */
@Entity(tableName = "desktop_items")
data class DesktopItemEntity(
    @PrimaryKey val id: String,
    val zone: String,
    val page: Int,
    val parentId: String?,
    val type: String,
    val title: String,
    val action: String,
    val iconRes: String?,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val rotation: Float,
    val appWidgetId: Int?,
    val sortOrder: Int,
) {
    fun toDomain() = DesktopItem(
        id = id,
        zone = Zone.valueOf(zone),
        page = page,
        parentId = parentId,
        type = ItemType.valueOf(type),
        title = title,
        action = action,
        iconRes = iconRes,
        row = row,
        col = col,
        rowSpan = rowSpan,
        colSpan = colSpan,
        rotation = rotation,
        appWidgetId = appWidgetId,
        order = sortOrder,
    )
}
