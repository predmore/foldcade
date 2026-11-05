package app.foldcade.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.foldcade.FoldcadeApp
import app.foldcade.language.ConnectField
import app.foldcade.language.ConnectKind
import app.foldcade.language.Copy
import app.foldcade.language.Effect
import app.foldcade.language.HostScreen
import app.foldcade.language.KeyDrop
import app.foldcade.language.Meaning
import app.foldcade.language.PairingCode
import app.foldcade.language.PickerModel
import app.foldcade.language.PromptKey
import app.foldcade.language.RommPhoneCopy
import app.foldcade.language.QrCode
import app.foldcade.language.Theme
import app.foldcade.language.connectFields
import app.foldcade.language.keyDropLine
import app.foldcade.language.keyDropQr
import app.foldcade.language.cursorBrush
import app.foldcade.language.promptKeyOf
import app.foldcade.language.rommConnectAction
import app.foldcade.language.rommWaitsForPhone

/**
 * Connecting RomM, or adding a SteamGridDB key, as a Settings page: the header with its Back
 * glyph over one card. RomM's card has its fields on the left and, on the right, the code a
 * phone approves, large and in fours, with a QR code of the page that approves it. The right
 * side keeps its size while the code comes and goes, so the fields never move.
 */
@Composable
internal fun ConnectScreen(
    app: FoldcadeApp,
    screen: HostScreen,
    onEffect: (Effect?) -> Unit,
    takeKeyboardFocus: suspend () -> Unit,
    dispatchFormKeyEvent: (android.view.KeyEvent) -> Boolean?,
) {
    val theme = LocalFoldTheme.current.theme
    val design = theme.style
    val scale = theme.motion.animatorScale(LocalContext.current.contentResolver)
    val model = app.shell.model
    val artKey = model.connectKind == ConnectKind.ArtKey
    val fields = connectFields(model.connectKind, model.connectFoundOrigin != null)
    val ringed = fields[model.connectIndex.coerceIn(fields.indices)]
    val origin = remember { FocusRequester() }
    val token = remember { FocusRequester() }
    // Leaving a field parks focus on the form. Cleared focus would land on the first field again.
    val form = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var typingIn by remember { mutableStateOf<ConnectField?>(null) }
    // Presses from an earlier visit to the form don't count.
    val opened = remember { app.shell.typingRequest }
    // A on a text row: the cursor goes into that field and the keyboard comes up.
    // A again brings the keyboard back after it was put away.
    LaunchedEffect(app.shell.typingRequest) {
        if (app.shell.typingRequest == opened) return@LaunchedEffect
        val field = when (ringed) {
            ConnectField.Origin -> origin
            ConnectField.Token -> token
            ConnectField.Save, ConnectField.FoundServer -> return@LaunchedEffect
        }
        takeKeyboardFocus()
        field.requestFocus()
        keyboard?.show()
    }
    // The D-pad moved the ring off the field being typed in, so typing stops there.
    LaunchedEffect(ringed) {
        if (typingIn != null && typingIn != ringed) form.requestFocus()
    }
    val back = promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB
    val title = when {
        artKey -> Copy.steamGridDb
        model.connectReturnPanel != null && !model.rommSignIn -> Copy.editRommConnection
        app.store.rommOrigin() != null -> Copy.signInRomm
        else -> Copy.addRommServer
    }
    val save = { onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.Save), screen)) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Gboard's fullscreen editor takes arrows before the activity's normal dispatch.
            // Give the form its vertical navigation first, even while a text row is editing.
            .onPreInterceptKeyBeforeSoftKeyboard { dispatchFormKeyEvent(it.nativeKeyEvent) != null }
            .focusRequester(form)
            .focusable()
            .background(theme.background),
    ) {
        // The bottom screen is a third narrower: the page keeps both halves, closer together.
        val compact = maxWidth < COMPACT_BELOW
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeader(theme, back, title) { onEffect(app.shell.onMeaning(Meaning.Back, screen)) }
            val card = RoundedCornerShape(design.metrics.cardCornerDp.dp)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(card)
                    .background(cardFill(theme, design.settings.cardLift))
                    .border(design.settings.borderDp.dp, theme.onBackground.copy(alpha = design.settings.borderAlpha), card)
                    .padding(horizontal = if (compact) 16.dp else 28.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 32.dp),
            ) {
                val side = Modifier.width(if (compact) PANE_COMPACT else PANE).fillMaxHeight()
                // RomM leads with the phone, the way in that needs no typing; the fields are its fallback.
                if (!artKey) PairingSide(model, app.rommPhone.state, app.shell.connectToken.isNotBlank(), theme, compact, side)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!artKey) {
                        BasicText(
                            RommPhoneCopy.orType,
                            modifier = Modifier.padding(start = 6.dp),
                            style = formStyle(theme.muted, design.type.hint, theme),
                            maxLines = 1,
                        )
                        model.connectFoundOrigin?.let { found ->
                            BasicText(
                                "${RommPhoneCopy.useServer}: ${found.substringAfter("://")}",
                                modifier = Modifier.fillMaxWidth().chip(ringed == ConnectField.FoundServer)
                                    .hostPress { onEffect(app.shell.touchConnect(fields.indexOf(ConnectField.FoundServer), screen)) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                style = formStyle(theme.onBackground, design.type.dialogBody, theme, bold = true),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FieldLabel(Copy.server, theme)
                        BasicTextField(
                            value = model.connectOrigin,
                            onValueChange = { app.shell.editOrigin(it) },
                            singleLine = true,
                            textStyle = formStyle(theme.onBackground, design.type.dialogBody, theme),
                            cursorBrush = cursorBrush(theme),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(origin)
                                .chip(ringed == ConnectField.Origin)
                                .onFocusChanged { state ->
                                    typingIn = typing(typingIn, ConnectField.Origin, state.isFocused)
                                    if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Origin), screen)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                        model.connectWarning?.let { Note(it, theme, attention = true) }
                        Spacer(Modifier.size(4.dp))
                    }
                    FieldLabel(if (artKey) Copy.apiKey else Copy.tokenOptional, theme)
                    BasicTextField(
                        value = app.shell.connectToken,
                        onValueChange = { app.shell.editToken(it) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = formStyle(theme.onBackground, design.type.dialogBody, theme),
                        cursorBrush = cursorBrush(theme),
                        // A password field to the keyboard too, so it neither shows nor learns the token.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        // Done does what the button under it says, rather than only closing the keyboard.
                        keyboardActions = KeyboardActions(onDone = { if (!model.connectBusy) save() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(token)
                            .chip(ringed == ConnectField.Token)
                            .onFocusChanged { state ->
                                typingIn = typing(typingIn, ConnectField.Token, state.isFocused)
                                if (state.isFocused) app.shell.touchConnect(fields.indexOf(ConnectField.Token), screen)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    val action = when {
                        artKey -> if (model.connectBusy) Copy.checkingKey else Copy.saveKey
                        else -> rommConnectAction(
                            busy = model.connectBusy,
                            tokenTyped = app.shell.connectToken.isNotBlank(),
                            originTyped = model.connectOrigin.isNotBlank(),
                            code = model.connectCode,
                            retry = model.connectRetry,
                        )
                    }
                    // Waiting on the phone is not a press: Connect reads quieter until a token is typed or the code ends.
                    val waiting = model.connectBusy || !artKey && rommWaitsForPhone(
                        busy = model.connectBusy,
                        tokenTyped = app.shell.connectToken.isNotBlank(),
                        originTyped = model.connectOrigin.isNotBlank(),
                        code = model.connectCode,
                        retry = model.connectRetry,
                    )
                    SlideSwap(target = action, scale = scale, distance = 10.dp, vertical = true, direction = { _, _ -> 1 }) { label ->
                        BasicText(
                            text = label,
                            modifier = Modifier
                                .chip(ringed == ConnectField.Save)
                                .hostPress { if (!model.connectBusy) save() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            style = formStyle(if (waiting) theme.muted else theme.onBackground, design.type.menuRow, theme, bold = !waiting),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // A token check's answer, or a refused key, under the button that asked.
                    model.connectHint?.takeIf { artKey || model.connectCode == null && !pairingNote(it) }?.let { Note(it, theme, attention = false) }
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        text = if (artKey) Copy.artKeyHelp else Copy.rommTokenHelp,
                        style = formStyle(theme.muted, design.type.hint, theme),
                        maxLines = if (artKey) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (artKey) KeyDropSide(app.keyDrop.state, theme, compact, side)
            }
        }
    }
}

/** True for the notes that end a pairing attempt. They sit with the code they replaced. */
private fun pairingNote(hint: String): Boolean = hint == Copy.rommCodeExpired || hint == Copy.rommCodeDenied || hint == Copy.rommCodeUnavailable

/**
 * The phone's side: the QR code over the code itself and the page to open, or, before there is
 * a code, the same square kept empty with one line about why.
 */
@Composable
private fun PairingSide(model: PickerModel, phone: app.foldcade.language.KeyDrop?, tokenTyped: Boolean, theme: Theme, compact: Boolean, modifier: Modifier) {
    val design = theme.style
    val code = model.connectCode
    val scale = theme.motion.animatorScale(LocalContext.current.contentResolver)
    BoxWithConstraints(modifier) {
        // The square takes what the words under it leave, and never more than the side's width.
        // A code adds its digits and its address under the square, so the square gives them room.
        val square = minOf(maxWidth, maxHeight * if (code != null) SQUARE_WITH_CODE else if (compact) 0.52f else 0.56f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            SlideSwap(target = code, scale = scale, distance = 16.dp, vertical = true, direction = { _, _ -> 1 }, key = { it?.code }) { shown ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shown != null) {
                        QrSquare(shown.url, shown.shortUrl, theme, Modifier.size(square))
                        BasicText(
                            shown.spaced,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = shown.code },
                            style = formStyle(theme.onBackground, design.type.heroTitle, theme, bold = true, display = true)
                                .copy(textAlign = TextAlign.Center, letterSpacing = CODE_SPACING),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = design.type.dialogBody, maxFontSize = design.type.heroTitle),
                        )
                        BasicText(
                            Copy.rommPairScan,
                            modifier = Modifier.fillMaxWidth(),
                            style = formStyle(theme.muted, design.type.hint, theme).copy(textAlign = TextAlign.Center),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // The address on a line of its own, smaller before it would break mid-path.
                        BasicText(
                            shown.shortUrl,
                            modifier = Modifier.fillMaxWidth(),
                            style = formStyle(theme.onBackground, design.type.hint, theme).copy(textAlign = TextAlign.Center),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = design.type.hint * URL_SHRINK, maxFontSize = design.type.hint),
                        )
                        if (!model.connectRetry && !model.connectBusy && !tokenTyped) WaitingChip(Copy.waitingForPhone, theme, scale)
                    } else {
                        val waitingForCode = model.connectOrigin.isNotBlank() && !model.connectRetry && !model.connectBusy
                        val phoneUrl = (phone as? app.foldcade.language.KeyDrop.Open)?.url?.takeIf { app.foldcade.language.rommNeedsAddress(model) }
                        if (phoneUrl != null) QrSquare(phoneUrl, RommPhoneCopy.scan, theme, Modifier.size(square))
                        else EmptySquare(theme, pulse = waitingForCode, scale = scale, modifier = Modifier.size(square))
                        val hint = model.connectHint?.takeIf(::pairingNote)
                        // The phone's QR leads the form, so its line is the form's instruction, as bold as a field's name.
                        val leads = hint == null && !waitingForCode && phoneUrl != null
                        BasicText(
                            when {
                                hint != null -> hint
                                waitingForCode -> Copy.rommGettingCode
                                phoneUrl != null -> if ((phone as? app.foldcade.language.KeyDrop.Open)?.phoneHere == true) RommPhoneCopy.phoneHere else RommPhoneCopy.scan
                                phone == app.foldcade.language.KeyDrop.Offline && app.foldcade.language.rommNeedsAddress(model) -> RommPhoneCopy.offline
                                phone == app.foldcade.language.KeyDrop.Sent -> RommPhoneCopy.sent
                                model.connectOrigin.isBlank() -> RommPhoneCopy.opening
                                else -> Copy.rommPairAgain
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = formStyle(if (hint != null || leads) theme.onBackground else theme.muted, if (leads) design.type.menuRow else design.type.hint, theme, bold = hint != null || leads)
                                .copy(textAlign = TextAlign.Center),
                            maxLines = if (leads) 1 else 3,
                            // The lead line stays one line, a little smaller, rather than leave a word alone under it.
                            autoSize = if (leads) TextAutoSize.StepBased(minFontSize = design.type.hint, maxFontSize = design.type.menuRow) else null,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Under the phone's QR: that Foldcade waits for it, or looks for a RomM nearby meanwhile.
                        if (leads && !tokenTyped) WaitingChip(if (model.connectSearching) RommPhoneCopy.searching else Copy.waitingForPhone, theme, scale)
                        else if (model.connectOrigin.isBlank() && model.connectFoundOrigin == null) BasicText(
                            if (model.connectSearching) RommPhoneCopy.searching else RommPhoneCopy.notFound,
                            style = formStyle(theme.muted, design.type.hint, theme).copy(textAlign = TextAlign.Center),
                            maxLines = 2,
                        )
                        if (hint != null) BasicText(
                            if (phoneUrl != null) RommPhoneCopy.scan else Copy.rommPairAgain,
                            modifier = Modifier.fillMaxWidth(),
                            style = formStyle(theme.muted, design.type.hint, theme).copy(textAlign = TextAlign.Center),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The QR code of the approval page: the theme's darkest ink on its lightest, with the quiet
 * margin scanners need, on a rounded tile so it sits with the page rather than on it.
 */
@Composable
private fun QrSquare(url: String, label: String, theme: Theme, modifier: Modifier) {
    val qr = remember(url) { QrCode.encode(url) }
    val light = theme.onBackground
    val ink = theme.background
    Box(
        modifier
            .clip(RoundedCornerShape(theme.style.shape.highlightCornerDp.dp))
            .background(light)
            .semantics { contentDescription = label },
    ) {
        if (qr == null) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val cells = qr.size + QUIET * 2
            val cell = size.minDimension / cells
            // Whole pixels per module keep the edges crisp for a camera.
            val step = kotlin.math.floor(cell).coerceAtLeast(1f)
            val origin = (size.minDimension - step * qr.size) / 2f
            for (y in 0 until qr.size) for (x in 0 until qr.size) {
                if (qr.dark(x, y)) drawRect(ink, Offset(origin + x * step, origin + y * step), Size(step, step))
            }
        }
    }
}

/**
 * The phone's side of the SteamGridDB form: a QR code of the page Foldcade serves on this
 * Wi-Fi, where a phone pastes the key, and one line that follows the phone along. Without
 * Wi-Fi the code is SteamGridDB's own page, to read the key from and type it here. A new
 * page, as after a refused key, rises in as RomM's next code does.
 */
@Composable
private fun KeyDropSide(drop: KeyDrop?, theme: Theme, compact: Boolean, modifier: Modifier) {
    val design = theme.style
    val scale = theme.motion.animatorScale(LocalContext.current.contentResolver)
    val url = keyDropQr(drop)
    BoxWithConstraints(modifier) {
        val square = minOf(maxWidth, maxHeight * if (compact) 0.62f else 0.66f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            SlideSwap(target = url, scale = scale, distance = 16.dp, vertical = true, direction = { _, _ -> 1 }) { shown ->
                // While the page opens, or a sent key is checked, the square waits, breathing: no code shows that cannot answer.
                if (shown == null) EmptySquare(theme, pulse = true, scale = scale, modifier = Modifier.size(square))
                else QrSquare(shown, Copy.steamGridDb, theme, Modifier.size(square))
            }
            SlideSwap(target = keyDropLine(drop), scale = scale, distance = 10.dp, vertical = true, direction = { _, _ -> 1 }) { line ->
                BasicText(
                    line,
                    modifier = Modifier.fillMaxWidth(),
                    style = formStyle(if (drop is KeyDrop.Open && drop.phoneHere) theme.onBackground else theme.muted, design.type.hint, theme,
                        bold = drop is KeyDrop.Open && drop.phoneHere)
                        .copy(textAlign = TextAlign.Center),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A small breathing note that the form waits on the phone, where the button once said it. */
@Composable
private fun WaitingChip(label: String, theme: Theme, scale: Float) {
    val breath = rememberInfiniteTransition().animateFloat(theme.style.menu.labelRestAlpha, 1f, theme.motion.pulse(scale))
    BasicText(
        label,
        modifier = Modifier
            .graphicsLayer { alpha = breath.value }
            .clip(theme.style.shape.control())
            .background(theme.onBackground.copy(alpha = theme.style.menu.quickTileAlpha))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = formStyle(theme.muted, theme.style.type.hint, theme).copy(textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The code's place before there is one: the same rounded square, breathing while a code is on its way. */
@Composable
private fun EmptySquare(theme: Theme, pulse: Boolean, scale: Float, modifier: Modifier) {
    val breath = if (pulse) {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(theme.style.menu.labelRestAlpha, 1f, theme.motion.pulse(scale))
    } else null
    Box(
        modifier
            .graphicsLayer { alpha = breath?.value ?: 1f }
            .clip(RoundedCornerShape(theme.style.shape.highlightCornerDp.dp))
            .background(theme.onBackground.copy(alpha = theme.style.menu.quickTileAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(0.42f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            SymbolIcon("cloud", 1f, theme.muted)
        }
    }
}

/** A field's name over it, in the rows' type, so it reads as a setting and not fine print. */
@Composable
private fun FieldLabel(text: String, theme: Theme) {
    BasicText(
        text,
        modifier = Modifier.padding(start = 6.dp),
        style = formStyle(theme.onBackground, theme.style.type.menuRow, theme, bold = true),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Note(text: String, theme: Theme, attention: Boolean) {
    BasicText(
        text,
        modifier = Modifier.padding(start = 6.dp),
        style = formStyle(if (attention) theme.style.colors.attention else theme.onBackground, theme.style.type.dialogBody, theme),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The page's name with the Back glyph, as Settings wears its own. A tap goes back. */
@Composable
private fun PageHeader(theme: Theme, back: PromptKey, title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = HEADER_MAX)
            .clip(theme.style.shape.control())
            .background(theme.surface)
            .hostPress(onBack)
            .padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptImage(promptGlyph(back), 32.dp)
        BasicText(
            title,
            style = formStyle(theme.onBackground, theme.style.type.heroTitle, theme, bold = true, display = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The field taking text after [field] gains or loses focus. */
private fun typing(current: ConnectField?, field: ConnectField, focused: Boolean): ConnectField? = when {
    focused -> field
    current == field -> null
    else -> current
}

private fun formStyle(color: Color, size: TextUnit, theme: Theme, bold: Boolean = false, display: Boolean = false): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = if (display) theme.displayFont else theme.font,
    fontWeight = if (bold) theme.style.type.strongWeight else theme.style.type.bodyWeight,
)

/** Narrower than this, as the Thor's bottom screen, the two halves sit closer and the code's side narrows. */
private val COMPACT_BELOW = 640.dp

/**
 * The phone's side: on the top screen (~833 × 468dp) 240dp leaves the fields ~460dp; on the
 * bottom (~538dp) 190dp leaves them ~270dp. The square is about 190 or 150dp tall.
 */
private val PANE = 240.dp

/** The square's share of the side's height while a code, its address and the waiting note sit under it. */
private const val SQUARE_WITH_CODE = 0.46f
private val PANE_COMPACT = 190.dp
private val HEADER_MAX = 560.dp

/** Modules of light margin each side of the code, as the spec asks. */
private const val QUIET = 4
private val CODE_SPACING = 0.12.em
/** How far a long address may shrink to stay on one line. */
private const val URL_SHRINK = 0.7f
