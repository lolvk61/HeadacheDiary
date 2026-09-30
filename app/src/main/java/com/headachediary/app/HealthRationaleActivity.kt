package com.headachediary.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.settings.localized
import com.headachediary.app.ui.HeadacheTheme

/**
 * Экран «Зачем приложению данные Health Connect». Его обязательно должно уметь показать приложение,
 * которое запрашивает права Health Connect: система открывает его по ссылке «Политика конфиденциальности».
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.localized())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeadacheTheme(isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.health_rationale_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.health_rationale_body), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { finish() }) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
