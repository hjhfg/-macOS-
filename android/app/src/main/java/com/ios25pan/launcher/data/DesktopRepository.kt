package com.ios25pan.launcher.data

import com.ios25pan.launcher.data.db.DesktopDao
import com.ios25pan.launcher.data.db.DesktopItemEntity
import com.ios25pan.launcher.domain.DesktopItem
import com.ios25pan.launcher.domain.ItemType
import com.ios25pan.launcher.domain.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopRepository @Inject constructor(private val dao: DesktopDao) {

    fun observe(): Flow<List<DesktopItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun remove(itemId: String) = dao.deleteTree(itemId)

    /** 把一个 AppWidget 放到某个原始页组的末尾（默认 2x2）。 */
    suspend fun addWidget(appWidgetId: Int, providerFlat: String, group: Int, title: String) {
        dao.upsert(
            DesktopItemEntity(
                id = "widget:$appWidgetId",
                zone = Zone.PAGE.name,
                page = group,
                parentId = null,
                type = ItemType.WIDGET.name,
                title = title,
                action = "appwidget:$providerFlat",
                iconRes = null,
                row = 0,
                col = 0,
                rowSpan = 2,
                colSpan = 2,
                rotation = 0f,
                appWidgetId = appWidgetId,
                sortOrder = dao.maxOrder() + 1,
            ),
        )
    }
}
