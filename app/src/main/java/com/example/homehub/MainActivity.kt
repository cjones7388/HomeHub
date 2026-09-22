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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

private const val SPREADSHEET_ID =
    "1C-vyDVpJHEuQlkSGV3G2Pd_5LqyUKhns-fxHe_F-PeQ"

private const val TARGET_SHEET_ID =
    1903279729

private const val SHEETS_SCOPE =
    "https://www.googleapis.com/auth/spreadsheets.readonly"


data class Bill(
    val name: String,
    val dueDay: Int,
    val amount: Double,
    val dueDate: LocalDate
)


class MainActivity : ComponentActivity() {

    private var googleConnected by mutableStateOf(false)

    private var loading by mutableStateOf(false)

    private var sheetStatus by mutableStateOf<String?>(null)

    private var thisWeeksBills by mutableStateOf<List<Bill>>(emptyList())


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

        super.onCreate(
            savedInstanceState
        )


        setContent {

            HomeHubApp(
                googleConnected =
                    googleConnected,

                loading =
                    loading,

                sheetStatus =
                    sheetStatus,

                thisWeeksBills =
                    thisWeeksBills,

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
                        Scope(
                            SHEETS_SCOPE
                        )
                    )
                )
                .build()


        Identity
            .getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->

                if (
                    result.hasResolution()
                ) {

                    val pendingIntent =
                        result.pendingIntent


                    if (
                        pendingIntent == null
                    ) {

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


        if (
            accessToken.isNullOrBlank()
        ) {

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
                     * Find the correct Google Sheets tab
                     * using its numeric sheet ID.
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
                            .getJSONArray(
                                "sheets"
                            )


                    var sheetTitle: String? =
                        null


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


                    if (
                        sheetTitle == null
                    ) {

                        throw Exception(
                            "Couldn't find the Bills sheet tab."
                        )
                    }


                    /*
                     * Read columns A:E.
                     *
                     * A = Bill name
                     * B = Direct Debit day
                     * C = Rate
                     * D = Unit
                     * E = Cost
                     */

                    val range =
                        "'$sheetTitle'!A:E"


                    val encodedRange =
                        URLEncoder
                            .encode(
                                range,
                                "UTF-8"
                            )
                            .replace(
                                "+",
                                "%20"
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


                    val bills =
                        mutableListOf<Bill>()


                    /*
                     * These categories are not household
                     * bills and are deliberately excluded.
                     *
                     * Matching is case-insensitive.
                     */

                    val excludedCategories =
                        setOf(
                            "food",
                            "going out",
                            "travelling"
                        )


                    if (
                        rows != null
                    ) {

                        for (
                        i in 1 until rows.length()
                        ) {

                            val row =
                                rows.getJSONArray(i)


                            /*
                             * We need:
                             *
                             * A = Bill name
                             * B = Direct Debit day
                             *
                             * Column D (Unit) is completely
                             * ignored.
                             */

                            if (
                                row.length() < 2
                            ) {
                                continue
                            }


                            val name =
                                row.optString(0)
                                    .trim()


                            val dueDayText =
                                row.optString(1)
                                    .trim()


                            /*
                             * Ignore Food, Going out
                             * and Travelling.
                             */

                            if (
                                excludedCategories.contains(
                                    name.lowercase()
                                )
                            ) {

                                continue
                            }


                            /*
                             * Column E = Cost.
                             *
                             * This is ALWAYS the amount
                             * reported by HomeHub.
                             *
                             * Unit does not matter.
                             */

                            val amountText =
                                if (
                                    row.length() > 2
                                ) {

                                    // Column C = Rate
                                    // This is the amount HomeHub reports.
                                    // Column D (Unit) and Column E (Cost) are ignored.

                                    row.optString(2)
                                        .trim()

                                } else {

                                    ""
                                }

                            /*
                             * A missing Cost field is ignored.
                             *
                             * £0.00 IS VALID and is included.
                             */

                            if (
                                name.isBlank() ||
                                dueDayText.isBlank() ||
                                amountText.isBlank()
                            ) {

                                continue
                            }


                            val dueDay =
                                dueDayText
                                    .toDoubleOrNull()
                                    ?.toInt()


                            val amount =
                                amountText
                                    .replace(
                                        "£",
                                        ""
                                    )
                                    .replace(
                                        ",",
                                        ""
                                    )
                                    .toDoubleOrNull()


                            if (
                                dueDay == null ||
                                amount == null ||
                                dueDay !in 1..31
                            ) {

                                continue
                            }


                            /*
                             * Find this month's occurrence
                             * of the bill.
                             */

                            val today =
                                LocalDate.now()


                            val lastDay =
                                today
                                    .withDayOfMonth(1)
                                    .lengthOfMonth()


                            /*
                             * If the due day doesn't exist
                             * in this month, ignore it.
                             */

                            if (
                                dueDay > lastDay
                            ) {

                                continue
                            }


                            val dueDate =
                                LocalDate.of(
                                    today.year,
                                    today.month,
                                    dueDay
                                )


                            bills.add(
                                Bill(
                                    name =
                                        name,

                                    dueDay =
                                        dueDay,

                                    amount =
                                        amount,

                                    dueDate =
                                        dueDate
                                )
                            )
                        }
                    }


                    /*
                     * Work out Monday -> Sunday
                     * for the current week.
                     */

                    val today =
                        LocalDate.now()


                    val monday =
                        today.with(
                            DayOfWeek.MONDAY
                        )


                    val sunday =
                        monday.plusDays(6)


                    /*
                     * Only bills whose due date falls
                     * between Monday and Sunday are shown.
                     */

                    val weeklyBills =
                        bills
                            .filter {

                                !it.dueDate.isBefore(
                                    monday
                                ) &&
                                        !it.dueDate.isAfter(
                                            sunday
                                        )
                            }
                            .sortedBy {
                                it.dueDate
                            }


                    /*
                     * The total is calculated from
                     * Column E (Cost).
                     */

                    val total =
                        weeklyBills.sumOf {
                            it.amount
                        }


                    runOnUiThread {

                        googleConnected = true

                        loading = false

                        thisWeeksBills =
                            weeklyBills


                        sheetStatus =
                            if (
                                weeklyBills.isEmpty()
                            ) {

                                "Google Sheet connected — no bills due this week."

                            } else {

                                "${weeklyBills.size} bill(s) due this week — " +
                                        formatMoney(total)
                            }
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

            connection.requestMethod =
                "GET"


            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )


            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            connection.connectTimeout =
                15000


            connection.readTimeout =
                15000


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
    thisWeeksBills: List<Bill>,
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
                        currentScreen =
                            "bills"
                    },

                    onNotesClick = {
                        currentScreen =
                            "notes"
                    }
                )
            }


            "bills" -> {

                BillsScreen(
                    onBack = {
                        currentScreen =
                            "home"
                    },

                    googleConnected =
                        googleConnected,

                    loading =
                        loading,

                    sheetStatus =
                        sheetStatus,

                    thisWeeksBills =
                        thisWeeksBills,

                    onConnectGoogle =
                        onConnectGoogle
                )
            }


            "notes" -> {

                NotesScreen(
                    onBack = {
                        currentScreen =
                            "home"
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
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        Text(
            text =
                "HomeHub",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge
        )


        Text(
            text =
                "Your household at a glance",

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        HomeCard(
            emoji =
                "💷",

            title =
                "Bills",

            description =
                "Your household bills from Google Sheets",

            onClick =
                onBillsClick
        )


        HomeCard(
            emoji =
                "📝",

            title =
                "Notes",

            description =
                "Your notes and lists",

            onClick =
                onNotesClick
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "More features coming soon",

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
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
        modifier =
            Modifier
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    emoji,

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,

                modifier =
                    Modifier.size(50.dp)
            )


            Spacer(
                modifier =
                    Modifier.size(16.dp)
            )


            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        title,

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge
                )


                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )


                Text(
                    text =
                        description,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }


            Text(
                text =
                    "›",

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
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
    thisWeeksBills: List<Bill>,
    onConnectGoogle: () -> Unit
) {

    val context =
        LocalContext.current


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
    ) {

        Text(
            text =
                "‹  Bills",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge,

            modifier =
                Modifier.clickable {
                    onBack()
                }
        )


        Spacer(
            modifier =
                Modifier.height(25.dp)
        )


        Text(
            text =
                "💷",

            style =
                MaterialTheme
                    .typography
                    .displaySmall
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "Household Bills",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )


        Spacer(
            modifier =
                Modifier.height(6.dp)
        )


        Text(
            text =
                "Bills - Google Sheets",

            style =
                MaterialTheme
                    .typography
                    .titleMedium
        )


        Spacer(
            modifier =
                Modifier.height(20.dp)
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
                text =
                    "OPEN & EDIT GOOGLE SHEET"
            )
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
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

            if (
                loading
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.size(22.dp)
                )

            } else {

                Text(
                    text =
                        if (
                            googleConnected
                        ) {

                            "REFRESH THIS WEEK'S BILLS"

                        } else {

                            "THIS WEEK'S BILLS"
                        }
                )
            }
        }


        if (
            sheetStatus != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Text(
                text =
                    sheetStatus,

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        }


        Spacer(
            modifier =
                Modifier.height(25.dp)
        )


        if (
            googleConnected &&
            !loading
        ) {

            ThisWeeksBillsSection(
                bills =
                    thisWeeksBills
            )
        }
    }
}


/* -------------------------------------------------- */
/* THIS WEEK'S BILLS                                  */
/* -------------------------------------------------- */

@Composable
fun ThisWeeksBillsSection(
    bills: List<Bill>
) {

    val total =
        bills.sumOf {
            it.amount
        }


    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text =
                "This Week's Bills",

            style =
                MaterialTheme
                    .typography
                    .headlineSmall
        )


        Spacer(
            modifier =
                Modifier.height(5.dp)
        )


        if (
            bills.isEmpty()
        ) {

            Text(
                text =
                    "Nothing is due this week.",

                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

        } else {

            Text(
                text =
                    "${bills.size} bill(s) • ${formatMoney(total)} total",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            (bills.size * 72)
                                .coerceAtMost(360)
                                .dp
                        )
            ) {

                items(
                    bills
                ) { bill ->

                    BillRow(
                        bill =
                            bill
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------- */
/* BILL ROW                                           */
/* -------------------------------------------------- */

@Composable
fun BillRow(
    bill: Bill
) {

    val dateFormatter =
        DateTimeFormatter.ofPattern(
            "EEE d MMM"
        )


    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(14.dp),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        bill.dueDate.format(
                            dateFormatter
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Text(
                    text =
                        bill.name,

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }


            Text(
                text =
                    formatMoney(
                        bill.amount
                    ),

                style =
                    MaterialTheme
                        .typography
                        .titleMedium
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
/* MONEY                                              */
/* -------------------------------------------------- */

fun formatMoney(
    amount: Double
): String {

    return "£" +
            String.format(
                java.util.Locale.UK,
                "%.2f",
                amount
            )
}


/* -------------------------------------------------- */
/* NOTES SCREEN                                       */
/* -------------------------------------------------- */

@Composable
fun NotesScreen(
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
    ) {

        Text(
            text =
                "‹  Notes",

            style =
                MaterialTheme
                    .typography
                    .headlineLarge,

            modifier =
                Modifier.clickable {
                    onBack()
                }
        )


        Spacer(
            modifier =
                Modifier.height(30.dp)
        )


        Text(
            text =
                "📝",

            style =
                MaterialTheme
                    .typography
                    .displaySmall
        )


        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        Text(
            text =
                "Notes",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "Your Samsung Notes integration will be added here.",

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )


        Spacer(
            modifier =
                Modifier.height(30.dp)
        )


        Text(
            text =
                "Coming next.",

            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )
    }
}