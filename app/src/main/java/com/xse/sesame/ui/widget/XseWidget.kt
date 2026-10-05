package com.xse.sesame.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xse.sesame.R
import com.xse.sesame.util.Statistics

/**
 * 桌面小部件：显示今日收取/帮收/浇水。
 * <p>
 * 数据读取走 {@link Statistics} 静态快照：小部件与 App 同进程时直接读内存；
 * 由 App 首页刷新（refreshStatistics）与激活状态广播（com.xse.sesame.status）
 * 两种时机触发刷新。点击小部件打开 App 主页。
 */
class XseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // status（模块注入成功）或 update 广播都触发刷新
        refreshAll(context)
    }

    companion object {
        /** 供 App 进程（首页刷新/收到模块广播）调用：刷新全部小部件实例 */
        @JvmStatic
        fun pushUpdate(context: Context) {
            refreshAll(context)
        }

        private fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, XseWidget::class.java))
            for (id in ids) updateOne(context, manager, id)
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_energy)
            val collected = Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.COLLECTED)
            val helped = Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.HELPED)
            val watered = Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.WATERED)
            views.setTextViewText(R.id.tvCollected, collected.toString())
            views.setTextViewText(R.id.tvHelped, helped.toString())
            views.setTextViewText(R.id.tvWatered, watered.toString())

            // 点击打开主界面
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                val pi = PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widgetRoot, pi)
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
