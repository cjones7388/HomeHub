
package com.example.homehub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

private const val SPREADSHEET_ID =
    "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ"

private const val TARGET_SHEET_ID = 1903279729

private const val SHEETS_SCOPE =
    "https://www.googleapis.com/auth/spreadsheets.readonly"


class MainActivity : ComponentActivity() {

    private var googleConnected by mutableStateOf(false)

    private var loading by mutableStateOf(false)

    private var sheetStatus by mutableStateOf<String?>(null)


    private val authorizationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (
                result.resultCode != RESULT_OK ||
                result.data == null
            ) {

                loading = false

                sheetStatus =
                    "Google authorization cancelled."

                return@registerForActivityResult
            }

            try {

                val authorizationResult =
                    Identity
                        .getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(
                            result.data
                        )

                handleAuthorizationResult(
                    authorizationResult
                )

            } catch (e: Exception) {

                loading = false

                sheetStatus =
                    "Authorization error: ${
                        e.message ?: "Unknown error"
                    }"
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContent {

            HomeHubApp(
                googleConnected = googleConnected,
                loading = loading,
                sheetStatus = sheetStatus,
                onConnectGoogle = {
                    connectGoogleSheets()
                }
            )
        }
    }


    private fun connectGoogleSheets() {

        loading = true

        sheetStatus = null


        val request =
            AuthorizationRequest
                .builder()
                .setRequestedScopes(
                    listOf(
                        Scope(SHEETS_SCOPE)
                    )
                )
                .build()


        Identity
            .getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->

                if (result.hasResolution()) {

                    val pendingIntent =
                        result.pendingIntent

                    if (pendingIntent == null) {

                        loading = false

                        sheetStatus =
                            "Google permission screen unavailable."

                        return@addOnSuccessListener
                    }


                    val intentSenderRequest =
                        IntentSenderRequest.Builder(
                            pendingIntent.intentSender
                        ).build()


                    authorizationLauncher.launch(
                        intentSenderRequest
                    )

                } else {

                    handleAuthorizationResult(
                        result
                    )
                }
            }
            .addOnFailureListener { exception ->

                loading = false

                sheetStatus =
                    "Couldn't connect to Google: ${
                        exception.message
                            ?: "Unknown error"
                    }"
            }
    }


    private fun handleAuthorizationResult(
        result: AuthorizationResult
    ) {

        val accessToken =
            result.accessToken


        if (accessToken.isNullOrBlank()) {

            loading = false

            sheetStatus =
                "Google didn't return an access token."

            return
        }


        readSpreadsheet(
            accessToken
        )
    }


    private fun readSpreadsheet(
        accessToken: String
    ) {

        Executors
            .newSingleThreadExecutor()
            .execute {

                try {

                    /*
                     * First find the sheet tab using
                     * its numeric sheet ID.
                     */

                    val spreadsheetUrl =
                        "https://sheets.googleapis.com/v4/" +
                                "spreadsheets/$SPREADSHEET_ID" +
                                "?fields=sheets.properties"


                    val spreadsheetJson =
                        makeGoogleRequest(
                            spreadsheetUrl,
                            accessToken
                        )


                    val sheetsArray =
                        spreadsheetJson
                            .getJSONArray("sheets")


                    var sheetTitle: String? = null


                    for (
                    i in 0 until sheetsArray.length()
                    ) {

                        val sheet =
                            sheetsArray
                                .getJSONObject(i)


                        val properties =
                            sheet.getJSONObject(
                                "properties"
                            )


                        val sheetId =
                            properties.getLong(
                                "sheetId"
                            )


                        if (
                            sheetId ==
                            TARGET_SHEET_ID.toLong()
                        ) {

                            sheetTitle =
                                properties.getString(
                                    "title"
                                )

                            break
                        }
                    }


                    if (sheetTitle == null) {

                        throw Exception(
                            "Couldn't find the Bills sheet tab."
                        )
                    }


                    /*
                     * Read columns A:E from the
                     * native Google Sheet.
                     */

                    val range =
                        "'$sheetTitle'!A:E"


                    val encodedRange =
                        URLEncoder.encode(
                            range,
                            "UTF-8"
                        )


                    val valuesUrl =
                        "https://sheets.googleapis.com/v4/" +
                                "spreadsheets/$SPREADSHEET_ID" +
                                "/values/$encodedRange"


                    val valuesJson =
                        makeGoogleRequest(
                            valuesUrl,
                            accessToken
                        )


                    val rows =
                        valuesJson.optJSONArray(
                            "values"
                        )


                    val rowCount =
                        rows?.length() ?: 0


                    val dataRows =
                        if (rowCount > 0) {

                            rowCount - 1

                        } else {

                            0
                        }


                    runOnUiThread {

                        googleConnected = true

                        loading = false

                        sheetStatus =
                            "Connected successfully — " +
                                    "$dataRows bill rows read."
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        loading = false

                        sheetStatus =
                            "Couldn't read Google Sheet: ${
                                e.message
                                    ?: "Unknown error"
                            }"
                    }
                }
            }
    }


    private fun makeGoogleRequest(
        urlString: String,
        accessToken: String
    ): JSONObject {

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection


        try {

            connection.requestMethod = "GET"


            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )


            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            connection.connectTimeout = 15000

            connection.readTimeout = 15000


            val responseCode =
                connection.responseCode


            if (
                responseCode !in 200..299
            ) {

                val errorText =
                    connection
                        .errorStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }


                throw Exception(
                    "Google returned HTTP $responseCode" +
                            if (
                                errorText.isNullOrBlank()
                            ) {

                                ""

                            } else {

                                ": $errorText"
                            }
                )
            }


            val responseText =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }


            return JSONObject(
                responseText
            )

        } finally {

            connection.disconnect()
        }
    }
}


/* -------------------------------------------------- */
/* HOME HUB                                           */
/* -------------------------------------------------- */

@Composable
fun HomeHubApp(
    googleConnected: Boolean,
    loading: Boolean,
    sheetStatus: String?,
    onConnectGoogle: () -> Unit
) {

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
                    },
                    googleConnected =
                        googleConnected,
                    loading = loading,
                    sheetStatus = sheetStatus,
                    onConnectGoogle =
                        onConnectGoogle
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


/* -------------------------------------------------- */
/* HOME SCREEN                                        */
/* -------------------------------------------------- */

@Composable
fun HomeScreen(
    onBillsClick: () -> Unit,
    onNotesClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Text(
            text = "HomeHub",
            style =
                MaterialTheme.typography.headlineLarge
        )


        Text(
            text = "Your household at a glance",
            style =
                MaterialTheme.typography.bodyLarge
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        HomeCard(
            emoji = "💷",
            title = "Bills",
            description =
                "Your household bills from Google Sheets",
            onClick = onBillsClick
        )


        HomeCard(
            emoji = "📝",
            title = "Notes",
            description =
                "Your notes and lists",
            onClick = onNotesClick
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(
            text = "More features coming soon",
            style =
                MaterialTheme.typography.bodyMedium
        )
    }
}


/* -------------------------------------------------- */
/* HOME CARD                                          */
/* -------------------------------------------------- */

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

        shape =
            RoundedCornerShape(20.dp),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = emoji,
                style =
                    MaterialTheme.typography.headlineMedium,

                modifier =
                    Modifier.size(50.dp)
            )


            Spacer(
                modifier = Modifier.size(16.dp)
            )


            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleLarge
                )


                Spacer(
                    modifier = Modifier.height(4.dp)
                )


                Text(
                    text = description,
                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }


            Text(
                text = "›",
                style =
                    MaterialTheme.typography.headlineMedium
            )
        }
    }
}


/* -------------------------------------------------- */
/* BILLS SCREEN                                       */
/* -------------------------------------------------- */

@Composable
fun BillsScreen(
    onBack: () -> Unit,
    googleConnected: Boolean,
    loading: Boolean,
    sheetStatus: String?,
    onConnectGoogle: () -> Unit
) {

    val context =
        LocalContext.current


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(
            text = "‹  Bills",
            style =
                MaterialTheme.typography.headlineLarge,

            modifier =
                Modifier.clickable {
                    onBack()
                }
        )


        Spacer(
            modifier = Modifier.height(35.dp)
        )


        Text(
            text = "💷",
            style =
                MaterialTheme.typography.displaySmall
        )


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        Text(
            text = "Household Bills",
            style =
                MaterialTheme.typography.headlineMedium
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(
            text = "Bills - Google Sheets",
            style =
                MaterialTheme.typography.titleMedium
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(
            text =
                "Your household bills are maintained in Google Sheets.",
            style =
                MaterialTheme.typography.bodyLarge
        )


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        Button(
            onClick = {

                openBillsSpreadsheet(
                    context
                )
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp)
        ) {

            Text(
                text = "OPEN & EDIT GOOGLE SHEET"
            )
        }


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        Button(
            onClick =
                onConnectGoogle,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp),

            enabled =
                !loading
        ) {

            if (loading) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.size(22.dp)
                )

            } else {

                Text(
                    text =
                        if (googleConnected) {

                            "REFRESH GOOGLE SHEETS"

                        } else {

                            "CONNECT GOOGLE SHEETS"
                        }
                )
            }
        }


        if (sheetStatus != null) {

            Spacer(
                modifier = Modifier.height(12.dp)
            )


            Text(
                text = sheetStatus,

                style =
                    MaterialTheme.typography.bodyMedium
            )
        }
    }
}


/* -------------------------------------------------- */
/* OPEN GOOGLE SHEET                                  */
/* -------------------------------------------------- */

fun openBillsSpreadsheet(
    context: Context
) {

    val spreadsheetUrl =
        "https://docs.google.com/spreadsheets/d/" +
                "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ" +
                "/edit?gid=1903279729"


    /*
     * Open directly in Google Sheets.
     */

    val sheetsIntent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                spreadsheetUrl
            )
        ).apply {

            setPackage(
                "com.google.android.apps.docs.editors.sheets"
            )
        }


    try {

        context.startActivity(
            sheetsIntent
        )

    } catch (
        e: ActivityNotFoundException
    ) {

        /*
         * Google Sheets isn't installed.
         * Open the native Google Sheet in
         * the normal browser instead.
         */

        val browserIntent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    spreadsheetUrl
                )
            )


        try {

            context.startActivity(
                browserIntent
            )

        } catch (
            e2: ActivityNotFoundException
        ) {

            Toast.makeText(
                context,

                "No browser or Google Sheets app was found.",

                Toast.LENGTH_LONG
            ).show()
        }
    }
}


/* -------------------------------------------------- */
/* NOTES SCREEN                                       */
/* -------------------------------------------------- */

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
            style =
                MaterialTheme.typography.headlineLarge,

            modifier =
                Modifier.clickable {
                    onBack()
                }
        )


        Spacer(
            modifier = Modifier.height(30.dp)
        )


        Text(
            text = "📝",
            style =
                MaterialTheme.typography.displaySmall
        )


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        Text(
            text = "Notes",
            style =
                MaterialTheme.typography.headlineMedium
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(
            text =
                "Your Samsung Notes integration will be added here.",

            style =
                MaterialTheme.typography.bodyLarge
        )


        Spacer(
            modifier = Modifier.height(30.dp)
        )


        Text(
            text = "Coming next.",
            style =
                MaterialTheme.typography.bodyMedium
        )
    }
}