package org.openscanner.ui.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.openscanner.data.model.Page
import org.openscanner.ui.theme.ScannerTheme
import java.io.File

@Composable
fun PageGridCard(
    page: Page,
    onClick: () -> Unit
) {
    val colors = ScannerTheme.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.5.dp, colors.cardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val pageFile = File(page.processedImagePath)
            if (pageFile.exists()) {
                AsyncImage(
                    model = pageFile,
                    contentDescription = "Page ${page.pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom strip with formatted page number "01", "02", etc.
            val formattedPageNum = String.format("%02d", page.pageIndex + 1)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.60f)
            ) {
                Text(
                    text = formattedPageNum,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
