package com.capsule.app.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Composable mounted by [com.capsule.app.service.CapsuleOverlayService] into
 * the post-capture overlay window. Renders the chip row, silent-wrap pill,
 * undo pill, or confirmation based on [OverlayViewModel.postCaptureUi].
 *
 * The bubble itself, the dismiss-target, and the capture sheet all remain
 * in their own windows / composables (see [BubbleUI], [DismissTargetUI],
 * [CaptureSheetUI]). This root handles ONLY the post-capture UX layer.
 */
@Composable
fun PostCaptureOverlay(
    viewModel: OverlayViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.postCaptureUi.collectAsState()

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(OverlayMotion.enterTween()) togetherWith fadeOut(OverlayMotion.exitTween()))
        },
        contentKey = { it::class },
        label = "postCaptureUi",
        modifier = modifier.fillMaxWidth()
    ) { ui ->
        when (ui) {
            is PostCaptureUi.None -> Box(Modifier)
            is PostCaptureUi.ChipRow -> SwipeToDismissBox(
                onDismiss = viewModel::onChipRowTimeout
            ) {
                ChipRow(
                    previewText = ui.previewText,
                    onChipTap = viewModel::onChipTapped,
                    onTimeout = viewModel::onChipRowTimeout
                )
            }
            is PostCaptureUi.SilentWrapPill -> SwipeToDismissBox(
                onDismiss = viewModel::onSilentWrapPillExpired
            ) {
                SilentWrapPill(
                    intent = ui.intent,
                    onUndo = { viewModel.onUndoTapped(ui.envelopeId) },
                    onExpire = viewModel::onSilentWrapPillExpired
                )
            }
            is PostCaptureUi.UndoPill -> SwipeToDismissBox(
                onDismiss = viewModel::onUndoPillExpired
            ) {
                UndoPill(
                    intent = ui.intent,
                    onUndo = { viewModel.onUndoTapped(ui.envelopeId) },
                    onExpire = viewModel::onUndoPillExpired
                )
            }
            is PostCaptureUi.RemovedConfirmation -> RemovedConfirmationPill(
                onExpire = viewModel::onConfirmationExpired
            )
            is PostCaptureUi.AlreadyInDiary -> AlreadyInDiaryPill(
                onExpire = viewModel::onConfirmationExpired
            )
        }
    }
}
