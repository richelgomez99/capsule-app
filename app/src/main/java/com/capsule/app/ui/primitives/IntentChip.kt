package com.capsule.app.ui.primitives

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.tokens.CapsulePalette
import com.capsule.app.ui.tokens.CapsuleType

@Immutable
enum class IntentChipKind(val label: String) {
    remindMe("Remind me"),
    inspiration("Inspiration"),
    reference("Reference"),
    readLater("Read later"),
    forSomeone("For someone"),
}

@Composable
fun IntentChip(
    intent: IntentChipKind,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val chipColor = intent.color(palette)
    val shape = RoundedCornerShape(percent = 50)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (active) chipColor.copy(alpha = 0.14f) else Color.Transparent)
            .border(width = 1.dp, color = if (active) chipColor else palette.rule, shape = shape)
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(chipColor),
        )
        Text(
            text = intent.label,
            color = if (active) chipColor else palette.ink,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.bodySans,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        )
    }
}

private fun IntentChipKind.color(palette: CapsulePalette.Tokens): Color = when (this) {
    IntentChipKind.remindMe -> palette.brandAccent
    IntentChipKind.inspiration -> Color(0xFFC8A4DC)
    IntentChipKind.reference -> Color(0xFFA4C8A4)
    IntentChipKind.readLater -> Color(0xFFDCC384)
    IntentChipKind.forSomeone -> Color(0xFF84B8D6)
}

@Preview(name = "intents - light")
@Preview(name = "intents - dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IntentChipPreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IntentChipKind.entries.forEachIndexed { index, kind ->
            IntentChip(intent = kind, active = index == 0)
        }
    }
}