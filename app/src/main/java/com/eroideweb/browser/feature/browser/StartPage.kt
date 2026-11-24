package com.eroideweb.browser.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.ColorFilter
import com.eroideweb.browser.R
import com.eroideweb.browser.ui.theme.BrandBlue
import com.eroideweb.browser.ui.theme.BrandCyan
import com.eroideweb.browser.ui.theme.CyanDark
import com.eroideweb.browser.ui.theme.Ink950
import com.eroideweb.browser.ui.theme.LocalDarkTheme
import com.eroideweb.browser.ui.theme.Mist300
import com.eroideweb.browser.ui.theme.OnPaper
import com.eroideweb.browser.ui.theme.Paper
import com.eroideweb.browser.ui.theme.Slate600

/**
 * Pagina iniziale: nessuna richiesta di rete, solo UI. Segue il tema dell'app
 * (scuro: notte blu del marchio; chiaro: carta con il glifo in blu).
 */
@Composable
fun StartPage(
    isPrivate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current || isPrivate
    val background = if (dark) {
        listOf(Ink950, Color(0xFF0A1A33), Color(0xFF062A45))
    } else {
        listOf(Paper, Color(0xFFEAF1FB), Color(0xFFDCEBFA))
    }
    val titleColor = if (dark) Color.White else OnPaper
    val taglineColor = if (dark) Mist300 else Slate600
    val accent = if (dark) BrandCyan else CyanDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors = background)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                // Il glifo è bianco: sul chiaro diventa blu del marchio.
                colorFilter = if (dark) null else ColorFilter.tint(BrandBlue),
                modifier = Modifier.size(220.dp),
            )
            Text(
                text = stringResource(
                    if (isPrivate) R.string.private_title else R.string.app_name,
                ),
                style = MaterialTheme.typography.displaySmall,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (isPrivate) R.string.private_tagline else R.string.start_tagline,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = taglineColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (dark) Color.White.copy(alpha = 0.08f) else BrandBlue.copy(alpha = 0.08f),
            ) {
                Text(
                    text = stringResource(R.string.start_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
