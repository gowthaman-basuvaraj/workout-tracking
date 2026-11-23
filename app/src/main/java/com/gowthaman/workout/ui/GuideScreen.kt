package com.gowthaman.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * How the app works, as content rather than markup.
 *
 * This was nearly an HTML file in a WebView, which would have been easier to write and
 * worse to keep: the palette would have to be restated in CSS to get dark mode right, the
 * system font size would need wiring up by hand, and a browser engine is a strange thing
 * to carry in an app that has no network permission. It is headings, paragraphs and
 * bullets -- a list and forty lines of Compose render it in the app's own theme, and dark
 * mode and text scaling come free.
 *
 * Same discipline as everywhere else: say what the app does, never what the reader should
 * do about their training or their eating.
 */

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
}

private data class GuideSection(val title: String, val blocks: List<Block>)

private fun para(text: String): Block = Block.Para(text)
private fun bullets(vararg items: String): Block = Block.Bullets(items.toList())

private val GUIDE: List<GuideSection> = listOf(
    GuideSection(
        "What this is",
        listOf(
            para(
                "A log. You write down what you did and what you ate, and later you can " +
                    "look at what you wrote."
            ),
            para(
                "It does not grade any of it. There are no goals, no targets and no score, " +
                    "and nothing in here will ever tell you that you are behind. The one " +
                    "thing it does volunteer is a suggested next set, because that is " +
                    "arithmetic on your own numbers rather than an opinion."
            ),
        ),
    ),
    GuideSection(
        "Where your data lives",
        listOf(
            para(
                "In the folder you picked on first run, as plain CSV — one file per month " +
                    "per kind of thing. Nothing is sent anywhere; the app has no network " +
                    "permission at all."
            ),
            para(
                "Point Syncthing, Dropbox or Nextcloud at that folder and the same log is " +
                    "on your laptop, where a spreadsheet or pandas can read it. The folder " +
                    "also gets a README.md describing every column, and an analyze.py to " +
                    "start from."
            ),
            para(
                "The app re-reads the folder whenever it comes back to the foreground, so " +
                    "edits you make elsewhere show up. You can change folders in Settings; " +
                    "nothing is copied or deleted when you do."
            ),
        ),
    ),
    GuideSection(
        "Logging something",
        listOf(
            para(
                "Tap Log, choose what kind of thing it is, then type or tap a name. The " +
                    "pickers start empty and fill themselves in from whatever you type, so " +
                    "the second time you log something it is one tap."
            ),
            para(
                "The button at the bottom of the form is what saves it. Done at the top " +
                    "only closes the screen — if you have filled in a form and not saved " +
                    "it, Done says so before it leaves."
            ),
            para(
                "Every save confirms what it saved, and the form clears itself ready for " +
                    "the next thing. Strength is the exception: it keeps the exercise so " +
                    "you can add set 2 and set 3 without retyping."
            ),
            para(
                "The arrows either side of the date move between days, and tapping the " +
                    "date opens a calendar, so logging yesterday is no harder than logging " +
                    "today."
            ),
        ),
    ),
    GuideSection(
        "Strength, and the overload suggestion",
        listOf(
            para(
                "Pick an exercise and you see your last five sessions and your best set " +
                    "ever before you type anything. Underneath is a concrete target for " +
                    "today, with its reasoning spelled out."
            ),
            para("The rule is double progression on a clock you set:"),
            bullets(
                "Inside your overload window it asks you to repeat the last session. " +
                    "Adding load every single session is how people stall.",
                "Once the window has passed since your last actual improvement, it " +
                    "proposes the smallest next step: one more rep, until you reach the " +
                    "top of your rep range, then one weight increment with reps back to " +
                    "the bottom.",
                "For bodyweight movements it adds reps up to a ceiling, then suggests " +
                    "adding load.",
            ),
            para(
                "The clock runs from your last improvement rather than your last session, " +
                    "so a missed week does not quietly reset your progression. The window, " +
                    "the increment and the rep range are all in Settings."
            ),
            para("Use is a suggestion, not an instruction. Type whatever you actually did."),
        ),
    ),
    GuideSection(
        "Cardio",
        listOf(
            para(
                "An activity name, a duration and optionally a distance. There is a " +
                    "stopwatch on the form that fills the duration in when you stop it, " +
                    "and pace is worked out for you."
            ),
        ),
    ),
    GuideSection(
        "Food",
        listOf(
            para(
                "What you had, what it was for — breakfast, lunch, dinner, snack, drink " +
                    "— and where it sat on a three-point scale: healthy, everyday, " +
                    "indulgent. That is the whole schema. No calories, no macros, no " +
                    "portion weights, no barcode scanner."
            ),
            para(
                "Optionally what was in it: plants, egg, dairy, meat, as many as apply. " +
                    "Four tags is deliberately too few to describe a plate; they are there " +
                    "so the dashboard can count how often you reach for each. Leave them " +
                    "empty and the item simply goes uncounted there."
            ),
            para(
                "Each item remembers how you last logged it, so the hundredth coffee is " +
                    "one tap. Picking an item also shows how often it has come up in the " +
                    "last 30 days, before you log it rather than after."
            ),
        ),
    ),
    GuideSection(
        "Sleep",
        listOf(
            para(
                "A duration and a three-point quality. 7h30, 7:30, 7.5 and 450m are all " +
                    "understood."
            ),
            para(
                "A night is filed under the morning you woke up, so logging it over " +
                    "breakfast needs no date fiddling. One night per day: logging again " +
                    "replaces it rather than adding a second."
            ),
            para(
                "The dashboard reports the average next to how many nights it came from, " +
                    "because an average over 11 logged nights in a month is a different " +
                    "claim from 29. There is no sleep score."
            ),
        ),
    ),
    GuideSection(
        "Stress",
        listOf(
            para(
                "Events, not a mood scale. Name the thing that wound you up or down, and " +
                    "how much it counted. Direction and weight are remembered per item, so " +
                    "a deadline is always an enhancer and a walk is always a buster, and " +
                    "repeats are one tap."
            ),
            para(
                "The dashboard leads with the busters you reached for and on how many " +
                    "days. Knowing work is stressful is not news; what you reached for is " +
                    "the part you have any say in. There is no enhancer-to-buster ratio " +
                    "anywhere, because you log stressors when you are least able to log " +
                    "anything and that number would claim a precision the data has not got."
            ),
        ),
    ),
    GuideSection(
        "The library",
        listOf(
            para(
                "Everything you have ever logged, filtered by kind and searchable. Open an " +
                    "exercise to see every session, your best set, the estimated 1RM trend " +
                    "and what is due next; tap a session to jump to that day."
            ),
            para(
                "Names can be renamed, and the history is rewritten with them. Add a name " +
                    "ahead of time if you like, though you never have to — logging " +
                    "something new creates it."
            ),
        ),
    ),
    GuideSection(
        "The dashboard",
        listOf(
            para(
                "Pick a window — week, month, quarter or year — and the whole page " +
                    "re-scopes to it. Windows are rolling (\"the last 30 days\"), not " +
                    "calendar-aligned, so the comparison against the previous window is " +
                    "always like-for-like and the page never looks empty on the 1st."
            ),
            para(
                "Three things ignore the window on purpose: the streak and the all-time " +
                    "footer are absolute, and the due-for-an-overload list follows your " +
                    "overload setting instead."
            ),
            para(
                "The share icon hands the window's figures to the share sheet as plain " +
                    "text, so it pastes into a message or your own notes. Nothing leaves " +
                    "the device until you pick where it goes."
            ),
        ),
    ),
    GuideSection(
        "Reminders",
        listOf(
            para(
                "Off until you turn them on in Settings, and silent until you have also " +
                    "picked at least one day. Nothing is scheduled before that."
            ),
            para(
                "The notification asks whether there is anything to write down. It does " +
                    "not check whether you logged anything and it will never tell you that " +
                    "you missed a day — it is a door, not a verdict."
            ),
            para(
                "It is scheduled on the phone itself. There is no push service and no " +
                    "server involved, which is why the app still asks for no network " +
                    "permission."
            ),
        ),
    ),
    GuideSection(
        "From a laptop",
        listOf(
            para(
                "There is a command-line version that writes into the same folder, for " +
                    "logging a coffee without picking up your phone. It uses the same code " +
                    "as the app, so names cannot drift into two spellings."
            ),
            para(
                "The app also publishes a copy of your settings into the folder so desktop " +
                    "tools agree with it about units and the overload window. That copy is " +
                    "read-only: the app overwrites it whenever a setting changes, so change " +
                    "settings here, not there."
            ),
        ),
    ),
)

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("How it works", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Uncomplicated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, top = 12.dp, bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(GUIDE) { section ->
                SectionCard(title = section.title.lowercase()) {
                    section.blocks.forEachIndexed { i, block ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        when (block) {
                            is Block.Para -> Text(
                                block.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            is Block.Bullets -> Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                block.items.forEach { item ->
                                    Row {
                                        Text(
                                            "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            item,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "That is all of it. Nothing here is required reading twice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
