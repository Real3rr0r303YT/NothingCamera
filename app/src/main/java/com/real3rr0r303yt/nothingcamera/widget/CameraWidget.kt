/*
 * NothingCamera – private, offline camera for Android
 * Copyright (C) 2026 Real3rr0r303YT – Made by 3rr0r303YT on TikTok
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.real3rr0r303yt.nothingcamera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.RemoteViews
import com.real3rr0r303yt.nothingcamera.MainActivity
import com.real3rr0r303yt.nothingcamera.R

/** Shutter widget: opens NothingCamera straight in photo or video mode. Shows no media. */
class CameraWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val density = context.resources.displayMetrics.density
        val views = RemoteViews(context.packageName, R.layout.widget_camera)
        views.setImageViewBitmap(R.id.widget_title, DotRender.text("CAMERA", 4f * density) { i, _ ->
            if (i == 0) DotRender.RED else DotRender.WHITE
        })
        val photo = open(context, 0, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        views.setOnClickPendingIntent(R.id.widget_shutter, photo)
        views.setOnClickPendingIntent(R.id.widget_photo, photo)
        views.setOnClickPendingIntent(R.id.widget_video, open(context, 1, MediaStore.INTENT_ACTION_VIDEO_CAMERA))
        views.setOnClickPendingIntent(R.id.widget_root, photo)
        manager.updateAppWidget(ids, views)
    }

    private fun open(context: Context, request: Int, action: String): PendingIntent = PendingIntent.getActivity(
        context, request,
        Intent(action)
            .setClass(context, MainActivity::class.java)
            // Fresh activity, so the mode (photo / video) from this button is used.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
