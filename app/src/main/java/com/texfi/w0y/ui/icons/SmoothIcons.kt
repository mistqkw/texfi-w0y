/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Контуры иконок взяты из Material Icons (androidx.compose.material:material-icons-*:1.7.8,
// набор Rounded), Apache License 2.0 — заголовок выше сохранён по условиям лицензии.
// Скопированы только нужные ~40 контуров, чтобы не тянуть библиотеку на тысячи иконок.

package com.texfi.w0y.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Иконки плавного стиля. Имена — те же, что у пиксельных спрайтов в
 * [com.texfi.w0y.ui.components.Sprites]: экран просит «download», а какой
 * набор нарисует — решает стиль.
 */
object SmoothIcons {
    private val bySprite: Map<List<String>, ImageVector> by lazy {
        val s = com.texfi.w0y.ui.components.Sprites
        mapOf(
            s.home to home, s.search to search, s.library to library, s.play to play,
            s.next to next, s.previous to previous, s.shuffle to shuffle, s.repeat to repeat,
            s.collapse to collapse, s.gear to gear, s.plus to plus, s.heart to heart,
            s.download to download, s.trash to trash, s.timer to timer, s.pause to pause,
            s.pin to pin, s.artist to artist, s.release to release, s.chevronRight to chevronRight,
            s.chevronLeft to chevronLeft, s.sliders to sliders, s.wave to wave, s.stats to stats,
            s.close to close, s.check to check, s.github to github, s.license to license,
            s.globe to globe, s.headphones to headphones, s.sync to sync, s.palette to palette,
            s.shield to shield, s.rocket to rocket, s.box to box, s.share to share,
            s.chevronUp to chevronUp, s.note to note, s.grip to grip,
        )
    }

    /** Плавная пара к пиксельному спрайту; null — у спрайта её нет (фирменные знаки). */
    fun forSprite(rows: List<String>): ImageVector? = bySprite[rows]

    /** Сколько спрайтов имеют пару — для теста полноты набора. */
    internal val mappedCount: Int get() = bySprite.size

    /** Rounded.Home */
    val home: ImageVector by lazy {
        icon("Home") {
            materialPath {
                moveTo(10.0f, 19.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(1.7f)
                curveToRelative(0.46f, 0.0f, 0.68f, -0.57f, 0.33f, -0.87f)
                lineTo(12.67f, 3.6f)
                curveToRelative(-0.38f, -0.34f, -0.96f, -0.34f, -1.34f, 0.0f)
                lineToRelative(-8.36f, 7.53f)
                curveToRelative(-0.34f, 0.3f, -0.13f, 0.87f, 0.33f, 0.87f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                close()
            }
        }
    }

    /** Rounded.Search */
    val search: ImageVector by lazy {
        icon("Search") {
            materialPath {
                moveTo(15.5f, 14.0f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveToRelative(1.2f, -1.4f, 1.82f, -3.31f, 1.48f, -5.34f)
                curveToRelative(-0.47f, -2.78f, -2.79f, -5.0f, -5.59f, -5.34f)
                curveToRelative(-4.23f, -0.52f, -7.79f, 3.04f, -7.27f, 7.27f)
                curveToRelative(0.34f, 2.8f, 2.56f, 5.12f, 5.34f, 5.59f)
                curveToRelative(2.03f, 0.34f, 3.94f, -0.28f, 5.34f, -1.48f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(0.41f, 0.41f, 1.08f, 0.41f, 1.49f, 0.0f)
                curveToRelative(0.41f, -0.41f, 0.41f, -1.08f, 0.0f, -1.49f)
                lineTo(15.5f, 14.0f)
                close()
                moveTo(9.5f, 14.0f)
                curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                close()
            }
        }
    }

    /** Rounded.LibraryMusic */
    val library: ImageVector by lazy {
        icon("LibraryMusic") {
            materialPath {
                moveTo(20.0f, 2.0f)
                lineTo(8.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(17.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.37f)
                curveToRelative(0.0f, 1.27f, -0.9f, 2.44f, -2.16f, 2.6f)
                curveToRelative(-1.69f, 0.23f, -3.11f, -1.25f, -2.8f, -2.95f)
                curveToRelative(0.2f, -1.1f, 1.18f, -1.95f, 2.3f, -2.02f)
                curveToRelative(0.63f, -0.04f, 1.2f, 0.16f, 1.66f, 0.51f)
                lineTo(14.0f, 6.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(3.0f, 6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(13.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(5.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(4.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }
    }

    /** Rounded.PlayArrow */
    val play: ImageVector by lazy {
        icon("PlayArrow") {
            materialPath {
                moveTo(8.0f, 6.82f)
                verticalLineToRelative(10.36f)
                curveToRelative(0.0f, 0.79f, 0.87f, 1.27f, 1.54f, 0.84f)
                lineToRelative(8.14f, -5.18f)
                curveToRelative(0.62f, -0.39f, 0.62f, -1.29f, 0.0f, -1.69f)
                lineTo(9.54f, 5.98f)
                curveTo(8.87f, 5.55f, 8.0f, 6.03f, 8.0f, 6.82f)
                close()
            }
        }
    }

    /** Rounded.SkipNext */
    val next: ImageVector by lazy {
        icon("SkipNext") {
            materialPath {
                moveTo(7.58f, 16.89f)
                lineToRelative(5.77f, -4.07f)
                curveToRelative(0.56f, -0.4f, 0.56f, -1.24f, 0.0f, -1.63f)
                lineTo(7.58f, 7.11f)
                curveTo(6.91f, 6.65f, 6.0f, 7.12f, 6.0f, 7.93f)
                verticalLineToRelative(8.14f)
                curveToRelative(0.0f, 0.81f, 0.91f, 1.28f, 1.58f, 0.82f)
                close()
                moveTo(16.0f, 7.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }
    }

    /** Rounded.SkipPrevious */
    val previous: ImageVector by lazy {
        icon("SkipPrevious") {
            materialPath {
                moveTo(7.0f, 6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(6.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(10.66f, 12.82f)
                lineToRelative(5.77f, 4.07f)
                curveToRelative(0.66f, 0.47f, 1.58f, -0.01f, 1.58f, -0.82f)
                lineTo(18.01f, 7.93f)
                curveToRelative(0.0f, -0.81f, -0.91f, -1.28f, -1.58f, -0.82f)
                lineToRelative(-5.77f, 4.07f)
                curveToRelative(-0.57f, 0.4f, -0.57f, 1.24f, 0.0f, 1.64f)
                close()
            }
        }
    }

    /** Rounded.Shuffle */
    val shuffle: ImageVector by lazy {
        icon("Shuffle") {
            materialPath {
                moveTo(10.59f, 9.17f)
                lineTo(6.12f, 4.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.46f, 4.46f)
                lineToRelative(1.42f, -1.4f)
                close()
                moveTo(15.35f, 4.85f)
                lineToRelative(1.19f, 1.19f)
                lineTo(4.7f, 17.88f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(17.96f, 7.46f)
                lineToRelative(1.19f, 1.19f)
                curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
                lineTo(20.0f, 4.5f)
                curveToRelative(0.0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
                horizontalLineToRelative(-3.79f)
                curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.36f, 0.85f)
                close()
                moveTo(14.83f, 13.41f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(3.13f, 3.13f)
                lineToRelative(-1.2f, 1.2f)
                curveToRelative(-0.31f, 0.31f, -0.09f, 0.85f, 0.36f, 0.85f)
                horizontalLineToRelative(3.79f)
                curveToRelative(0.28f, 0.0f, 0.5f, -0.22f, 0.5f, -0.5f)
                verticalLineToRelative(-3.79f)
                curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineToRelative(-1.19f, 1.19f)
                lineToRelative(-3.13f, -3.14f)
                close()
            }
        }
    }

    /** Rounded.Repeat */
    val repeat: ImageVector by lazy {
        icon("Repeat") {
            materialPath {
                moveTo(7.0f, 7.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(1.79f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
                lineTo(17.0f, 5.0f)
                lineTo(6.0f, 5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(7.0f, 7.0f)
                close()
                moveTo(17.0f, 17.0f)
                lineTo(7.0f, 17.0f)
                verticalLineToRelative(-1.79f)
                curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineToRelative(-2.79f, 2.79f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
                lineTo(7.0f, 19.0f)
                horizontalLineToRelative(11.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(3.0f)
                close()
            }
        }
    }

    /** Rounded.KeyboardArrowDown */
    val collapse: ImageVector by lazy {
        icon("KeyboardArrowDown") {
            materialPath {
                moveTo(8.12f, 9.29f)
                lineTo(12.0f, 13.17f)
                lineToRelative(3.88f, -3.88f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(-4.59f, 4.59f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                lineTo(6.7f, 10.7f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(0.39f, -0.38f, 1.03f, -0.39f, 1.42f, 0.0f)
                close()
            }
        }
    }

    /** Rounded.Settings */
    val gear: ImageVector by lazy {
        icon("Settings") {
            materialPath {
                moveTo(19.5f, 12.0f)
                curveToRelative(0.0f, -0.23f, -0.01f, -0.45f, -0.03f, -0.68f)
                lineToRelative(1.86f, -1.41f)
                curveToRelative(0.4f, -0.3f, 0.51f, -0.86f, 0.26f, -1.3f)
                lineToRelative(-1.87f, -3.23f)
                curveToRelative(-0.25f, -0.44f, -0.79f, -0.62f, -1.25f, -0.42f)
                lineToRelative(-2.15f, 0.91f)
                curveToRelative(-0.37f, -0.26f, -0.76f, -0.49f, -1.17f, -0.68f)
                lineToRelative(-0.29f, -2.31f)
                curveTo(14.8f, 2.38f, 14.37f, 2.0f, 13.87f, 2.0f)
                horizontalLineToRelative(-3.73f)
                curveTo(9.63f, 2.0f, 9.2f, 2.38f, 9.14f, 2.88f)
                lineTo(8.85f, 5.19f)
                curveToRelative(-0.41f, 0.19f, -0.8f, 0.42f, -1.17f, 0.68f)
                lineTo(5.53f, 4.96f)
                curveToRelative(-0.46f, -0.2f, -1.0f, -0.02f, -1.25f, 0.42f)
                lineTo(2.41f, 8.62f)
                curveToRelative(-0.25f, 0.44f, -0.14f, 0.99f, 0.26f, 1.3f)
                lineToRelative(1.86f, 1.41f)
                curveTo(4.51f, 11.55f, 4.5f, 11.77f, 4.5f, 12.0f)
                reflectiveCurveToRelative(0.01f, 0.45f, 0.03f, 0.68f)
                lineToRelative(-1.86f, 1.41f)
                curveToRelative(-0.4f, 0.3f, -0.51f, 0.86f, -0.26f, 1.3f)
                lineToRelative(1.87f, 3.23f)
                curveToRelative(0.25f, 0.44f, 0.79f, 0.62f, 1.25f, 0.42f)
                lineToRelative(2.15f, -0.91f)
                curveToRelative(0.37f, 0.26f, 0.76f, 0.49f, 1.17f, 0.68f)
                lineToRelative(0.29f, 2.31f)
                curveTo(9.2f, 21.62f, 9.63f, 22.0f, 10.13f, 22.0f)
                horizontalLineToRelative(3.73f)
                curveToRelative(0.5f, 0.0f, 0.93f, -0.38f, 0.99f, -0.88f)
                lineToRelative(0.29f, -2.31f)
                curveToRelative(0.41f, -0.19f, 0.8f, -0.42f, 1.17f, -0.68f)
                lineToRelative(2.15f, 0.91f)
                curveToRelative(0.46f, 0.2f, 1.0f, 0.02f, 1.25f, -0.42f)
                lineToRelative(1.87f, -3.23f)
                curveToRelative(0.25f, -0.44f, 0.14f, -0.99f, -0.26f, -1.3f)
                lineToRelative(-1.86f, -1.41f)
                curveTo(19.49f, 12.45f, 19.5f, 12.23f, 19.5f, 12.0f)
                close()
                moveTo(12.04f, 15.5f)
                curveToRelative(-1.93f, 0.0f, -3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveToRelative(1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveTo(13.97f, 15.5f, 12.04f, 15.5f)
                close()
            }
        }
    }

    /** Rounded.Add */
    val plus: ImageVector by lazy {
        icon("Add") {
            materialPath {
                moveTo(18.0f, 13.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(5.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }
    }

    /** Rounded.Favorite */
    val heart: ImageVector by lazy {
        icon("Favorite") {
            materialPath {
                moveTo(13.35f, 20.13f)
                curveToRelative(-0.76f, 0.69f, -1.93f, 0.69f, -2.69f, -0.01f)
                lineToRelative(-0.11f, -0.1f)
                curveTo(5.3f, 15.27f, 1.87f, 12.16f, 2.0f, 8.28f)
                curveToRelative(0.06f, -1.7f, 0.93f, -3.33f, 2.34f, -4.29f)
                curveToRelative(2.64f, -1.8f, 5.9f, -0.96f, 7.66f, 1.1f)
                curveToRelative(1.76f, -2.06f, 5.02f, -2.91f, 7.66f, -1.1f)
                curveToRelative(1.41f, 0.96f, 2.28f, 2.59f, 2.34f, 4.29f)
                curveToRelative(0.14f, 3.88f, -3.3f, 6.99f, -8.55f, 11.76f)
                lineToRelative(-0.1f, 0.09f)
                close()
            }
        }
    }

    /** Rounded.Download */
    val download: ImageVector by lazy {
        icon("Download") {
            materialPath {
                moveTo(16.59f, 9.0f)
                horizontalLineTo(15.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(5.0f)
                horizontalLineTo(7.41f)
                curveToRelative(-0.89f, 0.0f, -1.34f, 1.08f, -0.71f, 1.71f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.63f, -0.63f, 0.19f, -1.71f, -0.7f, -1.71f)
                close()
                moveTo(5.0f, 19.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }
    }

    /** Rounded.Delete */
    val trash: ImageVector by lazy {
        icon("Delete") {
            materialPath {
                moveTo(6.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(18.0f, 4.0f)
                horizontalLineToRelative(-2.5f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
                horizontalLineTo(9.91f)
                curveToRelative(-0.26f, 0.0f, -0.52f, 0.11f, -0.7f, 0.29f)
                lineTo(8.5f, 4.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }
    }

    /** Rounded.Timer */
    val timer: ImageVector by lazy {
        icon("Timer") {
            materialPath {
                moveTo(10.0f, 3.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                curveTo(9.45f, 1.0f, 9.0f, 1.45f, 9.0f, 2.0f)
                curveTo(9.0f, 2.55f, 9.45f, 3.0f, 10.0f, 3.0f)
                close()
            }
            materialPath {
                moveTo(19.03f, 7.39f)
                lineToRelative(0.75f, -0.75f)
                curveToRelative(0.38f, -0.38f, 0.39f, -1.01f, 0.0f, -1.4f)
                curveToRelative(0.0f, 0.0f, -0.01f, -0.01f, -0.01f, -0.01f)
                curveToRelative(-0.39f, -0.39f, -1.01f, -0.38f, -1.4f, 0.0f)
                lineToRelative(-0.75f, 0.75f)
                curveTo(16.07f, 4.74f, 14.12f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-4.8f, 0.0f, -8.88f, 3.96f, -9.0f, 8.76f)
                curveTo(2.87f, 17.84f, 6.94f, 22.0f, 12.0f, 22.0f)
                curveToRelative(4.98f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                curveTo(21.0f, 10.88f, 20.26f, 8.93f, 19.03f, 7.39f)
                close()
                moveTo(13.0f, 13.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineTo(13.0f)
                close()
            }
        }
    }

    /** Rounded.Pause */
    val pause: ImageVector by lazy {
        icon("Pause") {
            materialPath {
                moveTo(8.0f, 19.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(10.0f, 7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(14.0f, 7.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(18.0f, 7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                close()
            }
        }
    }

    /** Rounded.PushPin */
    val pin: ImageVector by lazy {
        icon("PushPin") {
            materialPath(pathFillType = EvenOdd) {
                moveTo(19.0f, 12.87f)
                curveToRelative(0.0f, -0.47f, -0.34f, -0.85f, -0.8f, -0.98f)
                curveTo(16.93f, 11.54f, 16.0f, 10.38f, 16.0f, 9.0f)
                verticalLineTo(4.0f)
                lineToRelative(1.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(7.0f)
                curveTo(6.45f, 2.0f, 6.0f, 2.45f, 6.0f, 3.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                lineToRelative(1.0f, 0.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 1.38f, -0.93f, 2.54f, -2.2f, 2.89f)
                curveTo(5.34f, 12.02f, 5.0f, 12.4f, 5.0f, 12.87f)
                verticalLineTo(13.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(4.98f)
                lineTo(11.0f, 21.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineToRelative(-0.02f, -7.0f)
                horizontalLineTo(18.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(12.87f)
                close()
            }
        }
    }

    /** Rounded.Person */
    val artist: ImageVector by lazy {
        icon("Person") {
            materialPath {
                moveTo(12.0f, 12.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                close()
                moveTo(12.0f, 14.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
                close()
            }
        }
    }

    /** Rounded.Album */
    val release: ImageVector by lazy {
        icon("Album") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 16.5f)
                curveToRelative(-2.49f, 0.0f, -4.5f, -2.01f, -4.5f, -4.5f)
                reflectiveCurveTo(9.51f, 7.5f, 12.0f, 7.5f)
                reflectiveCurveToRelative(4.5f, 2.01f, 4.5f, 4.5f)
                reflectiveCurveToRelative(-2.01f, 4.5f, -4.5f, 4.5f)
                close()
                moveTo(12.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }
    }

    /** Rounded.ChevronRight */
    val chevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            materialPath {
                moveTo(9.29f, 6.71f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineTo(13.17f, 12.0f)
                lineToRelative(-3.88f, 3.88f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(10.7f, 6.7f)
                curveToRelative(-0.38f, -0.38f, -1.02f, -0.38f, -1.41f, 0.01f)
                close()
            }
        }
    }

    /** Rounded.ChevronLeft */
    val chevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            materialPath {
                moveTo(14.71f, 6.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(8.71f, 11.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(10.83f, 12.0f)
                lineToRelative(3.88f, -3.88f)
                curveToRelative(0.39f, -0.39f, 0.38f, -1.03f, 0.0f, -1.41f)
                close()
            }
        }
    }

    /** Rounded.Tune */
    val sliders: ImageVector by lazy {
        icon("Tune") {
            materialPath {
                moveTo(3.0f, 18.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-2.0f)
                lineTo(4.0f, 17.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(3.0f, 6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(9.0f)
                lineTo(13.0f, 5.0f)
                lineTo(4.0f, 5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(13.0f, 20.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                close()
                moveTo(7.0f, 10.0f)
                verticalLineToRelative(1.0f)
                lineTo(4.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(21.0f, 12.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(9.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                close()
                moveTo(16.0f, 9.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(17.0f, 4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
            }
        }
    }

    /** Rounded.GraphicEq */
    val wave: ImageVector by lazy {
        icon("GraphicEq") {
            materialPath {
                moveTo(8.0f, 18.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(9.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(12.0f, 22.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(13.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(4.0f, 14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(16.0f, 18.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(17.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(19.0f, 11.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }
    }

    /** Rounded.BarChart */
    val stats: ImageVector by lazy {
        icon("BarChart") {
            materialPath {
                moveTo(6.0f, 20.0f)
                lineTo(6.0f, 20.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(7.0f)
                curveTo(4.0f, 19.1f, 4.9f, 20.0f, 6.0f, 20.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 15.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(0.0f)
                curveTo(16.9f, 13.0f, 16.0f, 13.9f, 16.0f, 15.0f)
                close()
            }
            materialPath {
                moveTo(12.0f, 20.0f)
                lineTo(12.0f, 20.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveTo(10.0f, 19.1f, 10.9f, 20.0f, 12.0f, 20.0f)
                close()
            }
        }
    }

    /** Rounded.Close */
    val close: ImageVector by lazy {
        icon("Close") {
            materialPath {
                moveTo(18.3f, 5.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(12.0f, 10.59f)
                lineTo(7.11f, 5.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.7f, 16.89f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(12.0f, 13.41f)
                lineToRelative(4.89f, 4.89f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(13.41f, 12.0f)
                lineToRelative(4.89f, -4.89f)
                curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0.0f, -1.4f)
                close()
            }
        }
    }

    /** Rounded.Check */
    val check: ImageVector by lazy {
        icon("Check") {
            materialPath {
                moveTo(9.0f, 16.17f)
                lineTo(5.53f, 12.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(20.29f, 7.71f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(9.0f, 16.17f)
                close()
            }
        }
    }

    /** Rounded.Code */
    val github: ImageVector by lazy {
        icon("Code") {
            materialPath {
                moveTo(8.7f, 15.9f)
                lineTo(4.8f, 12.0f)
                lineToRelative(3.9f, -3.9f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
                curveToRelative(-0.39f, -0.39f, -1.01f, -0.39f, -1.4f, 0.0f)
                lineToRelative(-4.59f, 4.59f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.6f)
                curveToRelative(0.39f, 0.39f, 1.01f, 0.39f, 1.4f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
                close()
                moveTo(15.3f, 15.9f)
                lineToRelative(3.9f, -3.9f)
                lineToRelative(-3.9f, -3.9f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.01f, 0.0f, -1.4f)
                curveToRelative(0.39f, -0.39f, 1.01f, -0.39f, 1.4f, 0.0f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(-4.59f, 4.6f)
                curveToRelative(-0.39f, 0.39f, -1.01f, 0.39f, -1.4f, 0.0f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.01f, 0.0f, -1.4f)
                close()
            }
        }
    }

    /** Rounded.Description */
    val license: ImageVector by lazy {
        icon("Description") {
            materialPath {
                moveTo(14.59f, 2.59f)
                curveToRelative(-0.38f, -0.38f, -0.89f, -0.59f, -1.42f, -0.59f)
                lineTo(6.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                lineTo(18.0f, 22.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 8.83f)
                curveToRelative(0.0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.82f, -4.83f)
                close()
                moveTo(15.0f, 18.0f)
                lineTo(9.0f, 18.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(15.0f, 14.0f)
                lineTo(9.0f, 14.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(13.0f, 8.0f)
                lineTo(13.0f, 3.5f)
                lineTo(18.5f, 9.0f)
                lineTo(14.0f, 9.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                close()
            }
        }
    }

    /** Rounded.Language */
    val globe: ImageVector by lazy {
        icon("Language") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(18.92f, 8.0f)
                horizontalLineToRelative(-2.95f)
                curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
                curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
                close()
                moveTo(12.0f, 4.04f)
                curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
                horizontalLineToRelative(-3.82f)
                curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
                close()
                moveTo(4.26f, 14.0f)
                curveTo(4.1f, 13.36f, 4.0f, 12.69f, 4.0f, 12.0f)
                reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2.0f)
                horizontalLineToRelative(3.38f)
                curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2.0f)
                reflectiveCurveToRelative(0.06f, 1.34f, 0.14f, 2.0f)
                lineTo(4.26f, 14.0f)
                close()
                moveTo(5.08f, 16.0f)
                horizontalLineToRelative(2.95f)
                curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
                curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
                close()
                moveTo(8.03f, 8.0f)
                lineTo(5.08f, 8.0f)
                curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
                curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8.0f)
                close()
                moveTo(12.0f, 19.96f)
                curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
                horizontalLineToRelative(3.82f)
                curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
                close()
                moveTo(14.34f, 14.0f)
                lineTo(9.66f, 14.0f)
                curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2.0f)
                reflectiveCurveToRelative(0.07f, -1.35f, 0.16f, -2.0f)
                horizontalLineToRelative(4.68f)
                curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2.0f)
                reflectiveCurveToRelative(-0.07f, 1.34f, -0.16f, 2.0f)
                close()
                moveTo(14.59f, 19.56f)
                curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
                horizontalLineToRelative(2.95f)
                curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
                close()
                moveTo(16.36f, 14.0f)
                curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2.0f)
                reflectiveCurveToRelative(-0.06f, -1.34f, -0.14f, -2.0f)
                horizontalLineToRelative(3.38f)
                curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2.0f)
                reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2.0f)
                horizontalLineToRelative(-3.38f)
                close()
            }
        }
    }

    /** Rounded.Headphones */
    val headphones: ImageVector by lazy {
        icon("Headphones") {
            materialPath {
                moveTo(3.0f, 12.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-7.0f)
                curveToRelative(0.0f, -4.97f, -4.03f, -9.0f, -9.0f, -9.0f)
                reflectiveCurveTo(3.0f, 7.03f, 3.0f, 12.0f)
                close()
            }
        }
    }

    /** Rounded.Sync */
    val sync: ImageVector by lazy {
        icon("Sync") {
            materialPath {
                moveTo(12.0f, 4.0f)
                lineTo(12.0f, 2.21f)
                curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineToRelative(-2.8f, 2.79f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.32f, 0.31f, 0.86f, 0.09f, 0.86f, -0.36f)
                lineTo(12.0f, 6.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 0.79f, -0.15f, 1.56f, -0.44f, 2.25f)
                curveToRelative(-0.15f, 0.36f, -0.04f, 0.77f, 0.23f, 1.04f)
                curveToRelative(0.51f, 0.51f, 1.37f, 0.33f, 1.64f, -0.34f)
                curveToRelative(0.37f, -0.91f, 0.57f, -1.91f, 0.57f, -2.95f)
                curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                close()
                moveTo(12.0f, 18.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                curveToRelative(0.0f, -0.79f, 0.15f, -1.56f, 0.44f, -2.25f)
                curveToRelative(0.15f, -0.36f, 0.04f, -0.77f, -0.23f, -1.04f)
                curveToRelative(-0.51f, -0.51f, -1.37f, -0.33f, -1.64f, 0.34f)
                curveTo(4.2f, 9.96f, 4.0f, 10.96f, 4.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                verticalLineToRelative(1.79f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
                lineTo(12.0f, 18.0f)
                close()
            }
        }
    }

    /** Rounded.Palette */
    val palette: ImageVector by lazy {
        icon("Palette") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.49f, 2.0f, 2.0f, 6.49f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.49f, 10.0f, 10.0f, 10.0f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                curveToRelative(0.0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
                curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineTo(16.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, -2.69f, 6.0f, -6.0f)
                curveTo(22.0f, 6.04f, 17.51f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(17.5f, 13.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                curveToRelative(0.0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                curveTo(19.0f, 12.33f, 18.33f, 13.0f, 17.5f, 13.0f)
                close()
                moveTo(14.5f, 9.0f)
                curveTo(13.67f, 9.0f, 13.0f, 8.33f, 13.0f, 7.5f)
                curveTo(13.0f, 6.67f, 13.67f, 6.0f, 14.5f, 6.0f)
                reflectiveCurveTo(16.0f, 6.67f, 16.0f, 7.5f)
                curveTo(16.0f, 8.33f, 15.33f, 9.0f, 14.5f, 9.0f)
                close()
                moveTo(5.0f, 11.5f)
                curveTo(5.0f, 10.67f, 5.67f, 10.0f, 6.5f, 10.0f)
                reflectiveCurveTo(8.0f, 10.67f, 8.0f, 11.5f)
                curveTo(8.0f, 12.33f, 7.33f, 13.0f, 6.5f, 13.0f)
                reflectiveCurveTo(5.0f, 12.33f, 5.0f, 11.5f)
                close()
                moveTo(11.0f, 7.5f)
                curveTo(11.0f, 8.33f, 10.33f, 9.0f, 9.5f, 9.0f)
                reflectiveCurveTo(8.0f, 8.33f, 8.0f, 7.5f)
                curveTo(8.0f, 6.67f, 8.67f, 6.0f, 9.5f, 6.0f)
                reflectiveCurveTo(11.0f, 6.67f, 11.0f, 7.5f)
                close()
            }
        }
    }

    /** Rounded.Shield */
    val shield: ImageVector by lazy {
        icon("Shield") {
            materialPath {
                moveTo(11.3f, 2.26f)
                lineToRelative(-6.0f, 2.25f)
                curveTo(4.52f, 4.81f, 4.0f, 5.55f, 4.0f, 6.39f)
                verticalLineToRelative(4.7f)
                curveToRelative(0.0f, 4.83f, 3.13f, 9.37f, 7.43f, 10.75f)
                curveToRelative(0.37f, 0.12f, 0.77f, 0.12f, 1.14f, 0.0f)
                curveToRelative(4.3f, -1.38f, 7.43f, -5.91f, 7.43f, -10.75f)
                verticalLineToRelative(-4.7f)
                curveToRelative(0.0f, -0.83f, -0.52f, -1.58f, -1.3f, -1.87f)
                lineToRelative(-6.0f, -2.25f)
                curveTo(12.25f, 2.09f, 11.75f, 2.09f, 11.3f, 2.26f)
                close()
            }
        }
    }

    /** Rounded.RocketLaunch */
    val rocket: ImageVector by lazy {
        icon("RocketLaunch") {
            materialPath {
                moveTo(9.19f, 6.35f)
                curveToRelative(-2.04f, 2.29f, -3.44f, 5.58f, -3.57f, 5.89f)
                lineToRelative(-2.26f, -0.97f)
                curveToRelative(-0.65f, -0.28f, -0.81f, -1.13f, -0.31f, -1.63f)
                lineToRelative(3.01f, -3.01f)
                curveToRelative(0.47f, -0.47f, 1.15f, -0.68f, 1.81f, -0.55f)
                lineTo(9.19f, 6.35f)
                lineTo(9.19f, 6.35f)
                close()
                moveTo(10.68f, 16.51f)
                curveToRelative(0.3f, 0.3f, 0.74f, 0.38f, 1.12f, 0.2f)
                curveToRelative(1.16f, -0.54f, 3.65f, -1.81f, 5.26f, -3.42f)
                curveToRelative(4.59f, -4.59f, 4.63f, -8.33f, 4.36f, -9.93f)
                curveToRelative(-0.07f, -0.4f, -0.39f, -0.72f, -0.79f, -0.79f)
                curveToRelative(-1.6f, -0.27f, -5.34f, -0.23f, -9.93f, 4.36f)
                curveToRelative(-1.61f, 1.61f, -2.87f, 4.1f, -3.42f, 5.26f)
                curveToRelative(-0.18f, 0.38f, -0.09f, 0.83f, 0.2f, 1.12f)
                lineTo(10.68f, 16.51f)
                close()
                moveTo(17.65f, 14.81f)
                curveToRelative(-2.29f, 2.04f, -5.58f, 3.44f, -5.89f, 3.57f)
                lineToRelative(0.97f, 2.26f)
                curveToRelative(0.28f, 0.65f, 1.13f, 0.81f, 1.63f, 0.31f)
                lineToRelative(3.01f, -3.01f)
                curveToRelative(0.47f, -0.47f, 0.68f, -1.15f, 0.55f, -1.81f)
                lineTo(17.65f, 14.81f)
                lineTo(17.65f, 14.81f)
                close()
                moveTo(8.94f, 17.41f)
                curveToRelative(0.2f, 1.06f, -0.15f, 2.04f, -0.82f, 2.71f)
                curveToRelative(-0.77f, 0.77f, -3.16f, 1.34f, -4.71f, 1.64f)
                curveToRelative(-0.69f, 0.13f, -1.3f, -0.48f, -1.17f, -1.17f)
                curveToRelative(0.3f, -1.55f, 0.86f, -3.94f, 1.64f, -4.71f)
                curveToRelative(0.67f, -0.67f, 1.65f, -1.02f, 2.71f, -0.82f)
                curveTo(7.76f, 15.28f, 8.72f, 16.24f, 8.94f, 17.41f)
                close()
                moveTo(13.0f, 9.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(13.0f, 10.1f, 13.0f, 9.0f)
                close()
            }
        }
    }

    /** Rounded.Inventory2 */
    val box: ImageVector by lazy {
        icon("Inventory2") {
            materialPath {
                moveTo(20.0f, 2.0f)
                horizontalLineTo(4.0f)
                curveTo(3.0f, 2.0f, 2.0f, 2.9f, 2.0f, 4.0f)
                verticalLineToRelative(3.01f)
                curveTo(2.0f, 7.73f, 2.43f, 8.35f, 3.0f, 8.7f)
                verticalLineTo(20.0f)
                curveToRelative(0.0f, 1.1f, 1.1f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.9f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.7f)
                curveToRelative(0.57f, -0.35f, 1.0f, -0.97f, 1.0f, -1.69f)
                verticalLineTo(4.0f)
                curveTo(22.0f, 2.9f, 21.0f, 2.0f, 20.0f, 2.0f)
                close()
                moveTo(14.0f, 14.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(0.0f)
                curveTo(15.0f, 13.55f, 14.55f, 14.0f, 14.0f, 14.0f)
                close()
                moveTo(20.0f, 7.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(7.0f)
                close()
            }
        }
    }

    /** Rounded.Share */
    val share: ImageVector by lazy {
        icon("Share") {
            materialPath {
                moveTo(18.0f, 16.08f)
                curveToRelative(-0.76f, 0.0f, -1.44f, 0.3f, -1.96f, 0.77f)
                lineTo(8.91f, 12.7f)
                curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
                reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
                lineToRelative(7.05f, -4.11f)
                curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
                curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                curveToRelative(0.0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
                lineTo(8.04f, 9.81f)
                curveTo(7.5f, 9.31f, 6.79f, 9.0f, 6.0f, 9.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                curveToRelative(0.79f, 0.0f, 1.5f, -0.31f, 2.04f, -0.81f)
                lineToRelative(7.12f, 4.16f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0.0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
                reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
                reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
                close()
            }
        }
    }

    /** Rounded.KeyboardArrowUp */
    val chevronUp: ImageVector by lazy {
        icon("KeyboardArrowUp") {
            materialPath {
                moveTo(8.12f, 14.71f)
                lineTo(12.0f, 10.83f)
                lineToRelative(3.88f, 3.88f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(12.7f, 8.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(6.7f, 13.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.38f, 1.03f, 0.39f, 1.42f, 0.0f)
                close()
            }
        }
    }

    /** Rounded.MusicNote */
    val note: ImageVector by lazy {
        icon("MusicNote") {
            materialPath {
                moveTo(12.0f, 5.0f)
                verticalLineToRelative(8.55f)
                curveToRelative(-0.94f, -0.54f, -2.1f, -0.75f, -3.33f, -0.32f)
                curveToRelative(-1.34f, 0.48f, -2.37f, 1.67f, -2.61f, 3.07f)
                curveToRelative(-0.46f, 2.74f, 1.86f, 5.08f, 4.59f, 4.65f)
                curveToRelative(1.96f, -0.31f, 3.35f, -2.11f, 3.35f, -4.1f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                close()
            }
        }
    }

    /** Rounded.DragHandle */
    val grip: ImageVector by lazy {
        icon("DragHandle") {
            materialPath {
                moveTo(19.0f, 9.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                close()
                moveTo(5.0f, 15.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                close()
            }
        }
    }

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector
            .Builder(
                name = "Rounded.$name",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply(block)
            .build()

    private inline fun ImageVector.Builder.materialPath(
        fillAlpha: Float = 1f,
        strokeAlpha: Float = 1f,
        pathFillType: PathFillType = PathFillType.NonZero,
        pathBuilder: PathBuilder.() -> Unit,
    ) = path(
        fill = SolidColor(Color.Black),
        fillAlpha = fillAlpha,
        stroke = null,
        strokeAlpha = strokeAlpha,
        strokeLineWidth = 1f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Bevel,
        strokeLineMiter = 1f,
        pathFillType = pathFillType,
        pathBuilder = pathBuilder,
    )
}
