package com.ios25pan.launcher.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

@Database(entities = [DesktopItemEntity::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun desktopDao(): DesktopDao

    companion object {
        fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db")
                .addCallback(SeedCallback(context.applicationContext))
                .build()
    }
}

/**
 * 首次创建数据库时，把 assets/desktop_seed.json（由 tools/convert_web_to_android.py 从网页导出数据生成）
 * 写入 desktop_items。之后布局只由用户操作修改。
 */
private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    private companion object {
        const val TAG = "LauncherDatabase"
    }
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 种子数据写不进去也要让应用能起来（只是桌面是空的），不能把启动器搞崩
        runCatching { seed(db) }.onFailure { Log.e(TAG, "写入初始布局失败", it) }
    }

    private fun seed(db: SupportSQLiteDatabase) {
        val raw = context.assets.open("desktop_seed.json").bufferedReader().use { it.readText() }
        val items = JSONObject(raw).getJSONArray("items")
        db.beginTransaction()
        try {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.getString("id"))
                    put("zone", o.getString("zone").uppercase())
                    put("page", o.getInt("page"))
                    if (o.isNull("parentId")) putNull("parentId") else put("parentId", o.getString("parentId"))
                    put("type", o.getString("type"))
                    put("title", o.getString("title"))
                    put("action", o.getString("action"))
                    if (o.isNull("icon")) putNull("iconRes") else put("iconRes", o.getString("icon"))
                    put("row", o.getInt("row"))
                    put("col", o.getInt("col"))
                    put("rowSpan", o.getInt("rowSpan"))
                    put("colSpan", o.getInt("colSpan"))
                    put("rotation", 0f)
                    putNull("appWidgetId")
                    put("sortOrder", o.getInt("order"))
                }
                db.insert("desktop_items", SQLiteDatabase.CONFLICT_REPLACE, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
