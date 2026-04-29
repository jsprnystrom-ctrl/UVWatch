package com.example.uvwatch.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.uvwatch.R
import com.example.uvwatch.UVRepository
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService

private const val RESOURCES_VERSION = "1"
private const val ID_LOGO = "logo"
private const val ID_IMAGE = "tile_image"

@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ) = ResourceBuilders.Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .addIdToImageMapping(
            ID_LOGO,
            ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.uv_watch_logo)
                        .build()
                )
                .build()
        )
        .addIdToImageMapping(
            ID_IMAGE,
            ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.tile_preview)
                        .build()
                )
                .build()
        )
        .build()

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
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
    val uvColor = getUVColorInt(uvIndex.toFloat())

    return PrimaryLayout.Builder(requestParams.deviceConfiguration)
        .setResponsiveContentInsetEnabled(true)
        .setContent(
            Box.Builder()
                .addContent(
                    // Bakgrundsbild (tile_preview)
                    Image.Builder()
                        .setResourceId(ID_IMAGE)
                        .setWidth(dp(160f))
                        .setHeight(dp(160f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setOpacity(0.4f) // Gör bilden lite transparent så texten syns
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Column.Builder()
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(
                            Image.Builder()
                                .setResourceId(ID_LOGO)
                                .setWidth(dp(32f))
                                .setHeight(dp(32f))
                                .build()
                        )
                        .addContent(Spacer.Builder().setHeight(dp(8f)).build())
                        .addContent(
                            Text.Builder(context, "UV $uvIndex")
                                .setColor(argb(uvColor))
                                .setTypography(Typography.TYPOGRAPHY_TITLE2)
                                .build()
                        )
                        .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                        .addContent(
                            Text.Builder(context, "Din position")
                                .setColor(argb(0xCCFFFFFF.toInt()))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .build()
                        )
                        .build()
                )
                .build()
        ).build()
}

private fun getUVColorInt(uv: Float): Int {
    return when {
        uv < 3 -> 0xFF4CAF50.toInt()
        uv < 6 -> 0xFFFFEB3B.toInt()
        uv < 8 -> 0xFFFFA500.toInt()
        uv < 11 -> 0xFFF44336.toInt()
        else -> 0xFF9132A8.toInt()
    }
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData({
    ResourceBuilders.Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .addIdToImageMapping(
            ID_LOGO,
            ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.uv_watch_logo)
                        .build()
                )
                .build()
        )
        .addIdToImageMapping(
            ID_IMAGE,
            ResourceBuilders.ImageResource.Builder()
                .setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder()
                        .setResourceId(R.drawable.tile_preview)
                        .build()
                )
                .build()
        )
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
