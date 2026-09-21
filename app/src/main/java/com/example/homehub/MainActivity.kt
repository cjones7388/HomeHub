package com.example.homehub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeHubApp()
        }
    }
}

@Composable
fun HomeHubApp() {

    var currentScreen by remember {
        mutableStateOf("home")
    }

    MaterialTheme {

        when (currentScreen) {

            "home" -> {
                HomeScreen(
                    onBillsClick = {
                        currentScreen = "bills"
                    },
                    onNotesClick = {
                        currentScreen = "notes"
                    }
                )
            }

            "bills" -> {
                BillsScreen(
                    onBack = {
                        currentScreen = "home"
                    }
                )
            }

            "notes" -> {
                NotesScreen(
                    onBack = {
                        currentScreen = "home"
                    }
                )
            }
        }
    }
}


/* ---------------------------------------------------------
   HOME SCREEN
   --------------------------------------------------------- */

@Composable
fun HomeScreen(
    onBillsClick: () -> Unit,
    onNotesClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "HomeHub",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Your household at a glance",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        HomeCard(
            emoji = "💷",
            title = "Bills",
            description = "Open and edit your bills spreadsheet",
            onClick = onBillsClick
        )

        HomeCard(
            emoji = "📝",
            title = "Notes",
            description = "Your notes and lists",
            onClick = onNotesClick
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "More features coming soon",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


/* ---------------------------------------------------------
   HOME CARD
   --------------------------------------------------------- */

@Composable
fun HomeCard(
    emoji: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },

        shape = RoundedCornerShape(20.dp),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.size(50.dp)
            )

            Spacer(
                modifier = Modifier.size(16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}


/* ---------------------------------------------------------
   BILLS SCREEN
   --------------------------------------------------------- */

@Composable
fun BillsScreen(
    onBack: () -> Unit
) {

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "‹  Bills",
            style = MaterialTheme.typography.headlineLarge,

            modifier = Modifier.clickable {
                onBack()
            }
        )

        Spacer(
            modifier = Modifier.height(35.dp)
        )

        Text(
            text = "💷",
            style = MaterialTheme.typography.displaySmall
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "Household Bills",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "bills.xlsx",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Open your spreadsheet to view and edit your bills.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Button(
            onClick = {
                openBillsSpreadsheet(context)
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {

            Text(
                text = "OPEN & EDIT BILLS"
            )
        }
    }
}


/* ---------------------------------------------------------
   OPEN GOOGLE SHEETS
   --------------------------------------------------------- */

fun openBillsSpreadsheet(context: Context) {

    val spreadsheetUrl =
        "https://docs.google.com/spreadsheets/d/" +
                "1Cssawo563I-sKyzpChpA9lgaWAxi9I3f" +
                "/edit?gid=1903279729"

    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(spreadsheetUrl)
    )

    try {

        context.startActivity(intent)

    } catch (e: ActivityNotFoundException) {

        Toast.makeText(
            context,
            "No browser or spreadsheet app was found.",
            Toast.LENGTH_LONG
        ).show()
    }
}


/* ---------------------------------------------------------
   NOTES SCREEN
   --------------------------------------------------------- */

@Composable
fun NotesScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "‹  Notes",
            style = MaterialTheme.typography.headlineLarge,

            modifier = Modifier.clickable {
                onBack()
            }
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Text(
            text = "📝",
            style = MaterialTheme.typography.displaySmall
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "Notes",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Your Samsung Notes integration will be added here.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Text(
            text = "Coming next.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
