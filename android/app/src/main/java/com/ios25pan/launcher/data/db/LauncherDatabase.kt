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
                // 这是启动器自己的桌面布局表，不是不可再生的用户文档——真出现"旧安装包建的表
                // 结构和当前代码的 Entity 对不上"（比如开发过程中改过字段但忘了同步 version 号），
                // 没有这一行 Room 会直接抛 IllegalStateException 崩给用户看，而且是"一打开就崩、
                // 永远进不了桌面"这种最糟糕的崩法——作为 HOME 应用，这意味着用户连设置里换回
                // 别的桌面都要多绕一步。加上它之后，遇到这种情况就老老实实删表重建，重建后
                // SeedCallback.onCreate 会重新从 desktop_seed.json 灌一遍默认布局，用户顶多是
                // 自己拖动过的图标位置丢了，而不是直接打不开。
                // Room 2.7 起无参的 fallbackToDestructiveMigration() 已过时，
                // 改用带显式参数的版本（dropAllTables=true：旧表结构对不上就整个删掉重建）。
                .fallbackToDestructiveMigration(dropAllTables = true)
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
