package com.example.uvwatch.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.uvwatch.UVRepository
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService

private const val RESOURCES_VERSION = "1"

@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ) = ResourceBuilders.Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        // Initiera repository för att läsa den senaste sparade datan
        UVRepository.init(this)
        
        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(tileLayout(requestParams, this))
                            .build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(singleTileTimeline)
            .build()
    }
}

private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
): LayoutElementBuilders.LayoutElement {
    val uvIndex = UVRepository.getUVIndex()
    val fetchTime = UVRepository.getLastFetchTime(context)
    val isFallback = UVRepository.isLastFallback()
    val uvColor = getUVColorInt(uvIndex.toFloat())
    val hasData = fetchTime != "--:--"
    val isFresh = if (hasData) UVRepository.isDataFresh() else false

    // Modifierare för att göra hela kortet klickbart och starta appen
    val launchAppModifier = ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId("launch_app")
                .setOnClick(ActionBuilders.LaunchAction.Builder().build())
                .build()
        )
        .build()

    // Statusrad med prick och tid (likt appen)
    val statusRow = LayoutElementBuilders.Row.Builder()
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
    
    if (hasData) {
        statusRow.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(5f))
                .setHeight(dp(5f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(if (isFresh) 0xFF00FF00.toInt() else 0xFF808080.toInt()))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2.5f)).build())
                                .build()
                        )
                        .build()
                )
                .build()
        )
        statusRow.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
    }

    statusRow.addContent(
        Text.Builder(context, "Data: $fetchTime${if (isFallback && hasData) " (Std)" else ""}")
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(if (hasData) 0x80FFFFFF.toInt() else 0x40FFFFFF.toInt()))
            .build()
    )

    val content = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(
            Text.Builder(context, "UV $uvIndex")
                .setColor(argb(uvColor))
                .setTypography(Typography.TYPOGRAPHY_TITLE1)
                .build()
        )
        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
        .addContent(statusRow.build())
        .build()

    // Omslut allt i en Box som fyller hela skärmen och fångar klick
    return LayoutElementBuilders.Box.Builder()
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .addContent(
            PrimaryLayout.Builder(requestParams.deviceConfiguration)
                .setResponsiveContentInsetEnabled(true)
                .setContent(content)
                .build()
        )
        .setModifiers(launchAppModifier)
        .build()
}

private fun getUVColorInt(uv: Float): Int {
    return when {
        uv < 3 -> 0xFF00FF00.toInt() // Grön
        uv < 6 -> 0xFFFFFF00.toInt() // Gul
        uv < 8 -> 0xFFFFA500.toInt() // Orange
        uv < 11 -> 0xFFFF0000.toInt() // Röd
        else -> 0xFF9132A8.toInt() // Lila
    }
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData({
    ResourceBuilders.Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}) {
    TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(
                            LayoutElementBuilders.Layout.Builder()
                                .setRoot(tileLayout(it, context))
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()
}
