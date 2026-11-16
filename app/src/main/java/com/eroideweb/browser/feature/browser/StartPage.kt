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
import com.eroideweb.browser.R
import com.eroideweb.browser.ui.theme.BrandCyan
import com.eroideweb.browser.ui.theme.Ink950
import com.eroideweb.browser.ui.theme.Mist300

/** Pagina iniziale: nessuna richiesta di rete, solo UI. */
@Composable
fun StartPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Ink950,
                        Color(0xFF0A1A33),
                        Color(0xFF062A45),
                    ),
                ),
            ),
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
                modifier = Modifier.size(220.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.start_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist300,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Text(
                    text = stringResource(R.string.start_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
