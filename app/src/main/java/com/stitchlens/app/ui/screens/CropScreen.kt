package com.stitchlens.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stitchlens.app.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: ScanViewModel,
    pageIndex: Int,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    if (pageIndex !in viewModel.pages.indices) {
        onBack()
        return
    }

    val page = viewModel.pages[pageIndex]
    val bitmap = page.originalBitmap  // Always crop against the original full photo

    // Auto-detect document bounds for initial crop position
    val autoBounds = remember(bitmap) {
        com.stitchlens.app.util.DocumentDetector.detectBoundsFractional(bitmap)
    }

    // Crop rect as fractions (0..1), initialized to detected bounds or full image
    var leftFrac by remember { mutableFloatStateOf(autoBounds?.left ?: 0.02f) }
    var topFrac by remember { mutableFloatStateOf(autoBounds?.top ?: 0.02f) }
    var rightFrac by remember { mutableFloatStateOf(autoBounds?.right ?: 0.98f) }
    var bottomFrac by remember { mutableFloatStateOf(autoBounds?.bottom ?: 0.98f) }

    // Image display size in pixels
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            // Image + crop overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { imageSize = it.size }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Document",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    if (imageSize != IntSize.Zero) {
                        // Calculate the actual image rect within the box (accounting for Fit scaling)
                        val imgAspect = bitmap.width.toFloat() / bitmap.height
                        val boxAspect = imageSize.width.toFloat() / imageSize.height
                        val (imgW, imgH) = if (imgAspect > boxAspect) {
                            imageSize.width.toFloat() to (imageSize.width / imgAspect)
                        } else {
                            (imageSize.height * imgAspect) to imageSize.height.toFloat()
                        }
                        val imgOffX = (imageSize.width - imgW) / 2f
                        val imgOffY = (imageSize.height - imgH) / 2f

                        // Crop overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cropLeft = imgOffX + leftFrac * imgW
                            val cropTop = imgOffY + topFrac * imgH
                            val cropRight = imgOffX + rightFrac * imgW
                            val cropBottom = imgOffY + bottomFrac * imgH

                            // Dim outside crop area
                            val dimColor = Color.Black.copy(alpha = 0.5f)
                            // Top
                            drawRect(dimColor, Offset.Zero, Size(size.width, cropTop))
                            // Bottom
                            drawRect(dimColor, Offset(0f, cropBottom), Size(size.width, size.height - cropBottom))
                            // Left
                            drawRect(dimColor, Offset(0f, cropTop), Size(cropLeft, cropBottom - cropTop))
                            // Right
                            drawRect(dimColor, Offset(cropRight, cropTop), Size(size.width - cropRight, cropBottom - cropTop))

                            // Crop border
                            drawRect(
                                Color(0xFF0078D4),
                                Offset(cropLeft, cropTop),
                                Size(cropRight - cropLeft, cropBottom - cropTop),
                                style = Stroke(width = 3f)
                            )

                            // Grid lines (rule of thirds)
                            val thirdW = (cropRight - cropLeft) / 3f
                            val thirdH = (cropBottom - cropTop) / 3f
                            val gridColor = Color.White.copy(alpha = 0.3f)
                            for (i in 1..2) {
                                drawLine(gridColor, Offset(cropLeft + thirdW * i, cropTop), Offset(cropLeft + thirdW * i, cropBottom), strokeWidth = 1f)
                                drawLine(gridColor, Offset(cropLeft, cropTop + thirdH * i), Offset(cropRight, cropTop + thirdH * i), strokeWidth = 1f)
                            }

                            // Corner handles
                            val handleRadius = 12f
                            val handleColor = Color.White
                            val handleBorder = Color(0xFF005FAA)
                            listOf(
                                Offset(cropLeft, cropTop),
                                Offset(cropRight, cropTop),
                                Offset(cropLeft, cropBottom),
                                Offset(cropRight, cropBottom)
                            ).forEach { pos ->
                                drawCircle(handleColor, handleRadius, pos)
                                drawCircle(handleBorder, handleRadius, pos, style = Stroke(3f))
                                drawCircle(handleBorder, 4f, pos)
                            }
                        }

                        // Drag handler — corners resize, inside area moves
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(imgW, imgH, imgOffX, imgOffY) {
                                    // -1 = move entire rect, 0-3 = corner handles
                                    var activeHandle: Int? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val cropLeft = imgOffX + leftFrac * imgW
                                            val cropTop = imgOffY + topFrac * imgH
                                            val cropRight = imgOffX + rightFrac * imgW
                                            val cropBottom = imgOffY + bottomFrac * imgH
                                            val handles = listOf(
                                                Offset(cropLeft, cropTop),
                                                Offset(cropRight, cropTop),
                                                Offset(cropLeft, cropBottom),
                                                Offset(cropRight, cropBottom)
                                            )
                                            val threshold = 60f
                                            val closest = handles
                                                .mapIndexed { i, h -> i to (h - offset).getDistance() }
                                                .filter { it.second < threshold }
                                                .minByOrNull { it.second }

                                            if (closest != null) {
                                                activeHandle = closest.first
                                            } else if (
                                                offset.x in cropLeft..cropRight &&
                                                offset.y in cropTop..cropBottom
                                            ) {
                                                // Touch is inside crop rect — move mode
                                                activeHandle = -1
                                            } else {
                                                activeHandle = null
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            val handle = activeHandle ?: return@detectDragGestures
                                            if (handle == -1) {
                                                // Move entire crop rect
                                                val dx = dragAmount.x / imgW
                                                val dy = dragAmount.y / imgH
                                                val w = rightFrac - leftFrac
                                                val h = bottomFrac - topFrac
                                                var newLeft = leftFrac + dx
                                                var newTop = topFrac + dy
                                                // Clamp to image bounds
                                                newLeft = newLeft.coerceIn(0f, 1f - w)
                                                newTop = newTop.coerceIn(0f, 1f - h)
                                                leftFrac = newLeft
                                                topFrac = newTop
                                                rightFrac = newLeft + w
                                                bottomFrac = newTop + h
                                            } else {
                                                val pos = change.position
                                                val fx = ((pos.x - imgOffX) / imgW).coerceIn(0f, 1f)
                                                val fy = ((pos.y - imgOffY) / imgH).coerceIn(0f, 1f)
                                                val minGap = 0.05f
                                                when (handle) {
                                                    0 -> {
                                                        leftFrac = fx.coerceAtMost(rightFrac - minGap)
                                                        topFrac = fy.coerceAtMost(bottomFrac - minGap)
                                                    }
                                                    1 -> {
                                                        rightFrac = fx.coerceAtLeast(leftFrac + minGap)
                                                        topFrac = fy.coerceAtMost(bottomFrac - minGap)
                                                    }
                                                    2 -> {
                                                        leftFrac = fx.coerceAtMost(rightFrac - minGap)
                                                        bottomFrac = fy.coerceAtLeast(topFrac + minGap)
                                                    }
                                                    3 -> {
                                                        rightFrac = fx.coerceAtLeast(leftFrac + minGap)
                                                        bottomFrac = fy.coerceAtLeast(topFrac + minGap)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = { activeHandle = null }
                                    )
                                }
                        )
                    }
                }
            }

            // Bottom actions
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        "DRAG CORNERS OR MOVE CROP AREA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-detect edges button
                    OutlinedButton(
                        onClick = {
                            val detected = com.stitchlens.app.util.DocumentDetector.detectBoundsFractional(bitmap)
                            if (detected != null) {
                                leftFrac = detected.left
                                topFrac = detected.top
                                rightFrac = detected.right
                                bottomFrac = detected.bottom
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.CropFree,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto Detect Edges", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.cropPage(pageIndex, leftFrac, topFrac, rightFrac, bottomFrac)
                            onDone()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apply Crop", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
