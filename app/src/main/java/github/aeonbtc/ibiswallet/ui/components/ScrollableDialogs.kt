package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface

@Composable
fun ScrollableDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    bottomSpacing: Dp = 24.dp,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(16.dp)
                        .heightIn(max = 720.dp)
                        .then(modifier),
                shape = shape,
                color = containerColor,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }

                    if (actions != null) {
                        Spacer(modifier = Modifier.height(bottomSpacing))
                        actions()
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    ScrollableDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        containerColor = containerColor,
        shape = shape,
        actions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                confirmButton()
                if (dismissButton != null) {
                    dismissButton()
                }
            }
        },
    ) {
        if (icon != null) {
            icon()
        }

        if (title != null) {
            if (icon != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            title()
        }

        if (text != null) {
            if (title != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            text()
        }
    }
}
