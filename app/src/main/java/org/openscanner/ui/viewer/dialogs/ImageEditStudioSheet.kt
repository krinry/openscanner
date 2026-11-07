package org.openscanner.ui.viewer.dialogs

import androidx.compose.runtime.Composable
import org.openscanner.data.model.Page
import org.openscanner.ui.editor.UniversalImageEditStudio

/**
 * Compatibility wrapper delegating to [UniversalImageEditStudio].
 */
@Composable
fun ImageEditStudioSheet(
    page: Page,
    onDismiss: () -> Unit,
    onPageUpdated: () -> Unit
) {
    UniversalImageEditStudio(
        page = page,
        onDismiss = onDismiss,
        onPageUpdated = onPageUpdated
    )
}
