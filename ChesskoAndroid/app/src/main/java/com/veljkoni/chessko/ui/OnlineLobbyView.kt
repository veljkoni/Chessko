package com.veljkoni.chessko.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veljkoni.chessko.logic.loc
import com.veljkoni.chessko.online.OnlineCode
import com.veljkoni.chessko.online.OnlineNotice
import com.veljkoni.chessko.online.OnlineState
import com.veljkoni.chessko.online.OnlineViewModel
import com.veljkoni.chessko.ui.theme.ButtonShape
import com.veljkoni.chessko.ui.theme.DS
import com.veljkoni.chessko.ui.theme.DialogShape
import com.veljkoni.chessko.ui.theme.Type
import com.veljkoni.chessko.ui.theme.dsCard

// Lobi igre preko interneta (Android strana iOS `OnlineLobbyView`). Crta se unutar
// `OnlineGameView` dok partije nema (Idle, Connecting, Lobby, Inviting, Invited).
// Sve odluke donosi `OnlineViewModel`; ovde se samo crta i prosledjuju radnje.
// `Idle` dok je ekran otvoren znaci „povezujem".

/** Iz serverskog obavestenja u tekst (isti ključevi kao iOS `OnlineNoticeText`). */
fun onlineNoticeText(notice: OnlineNotice): String = when (notice) {
    OnlineNotice.OFFLINE -> loc("Prijatelj trenutno nije dostupan. Neka otvori „Igraj preko interneta“.")
    OnlineNotice.BUSY -> loc("Prijatelj je već u partiji.")
    OnlineNotice.SELF_CODE -> loc("To je tvoj kod.")
    OnlineNotice.RATE_LIMITED -> loc("Previše poziva, pokušaj za minut.")
    OnlineNotice.BAD_CODE -> loc("Neispravan kod.")
    OnlineNotice.DECLINED -> loc("Prijatelj je odbio poziv.")
    OnlineNotice.UPDATE_APP -> loc("Ažuriraj aplikaciju da bi igrao preko interneta.")
    OnlineNotice.INVITE_CANCELLED -> loc("Poziv je otkazan.")
    OnlineNotice.DRAW_DECLINED -> loc("Prijatelj je odbio remi.")
    // Masina ga ne emituje (tiho se resava); grana postoji da `when` bude iscrpan.
    OnlineNotice.CODE_TAKEN -> ""
}

/** Velika slova, samo znaci azbuke koda, najvise 6, crtica posle treceg. */
fun formatOnlineCodeInput(input: String): String {
    val raw = input.uppercase().filter { OnlineCode.ALPHABET.contains(it) }.take(OnlineCode.LENGTH)
    return if (raw.length > 3) raw.take(3) + "-" + raw.drop(3) else raw
}

// Velicine koda kao na iOS-u (`OnlineLobbyView.swift`: 40 i 28, monospaced) — van skale.
private val MyCodeSize = 36.sp
private val FriendCodeSize = 28.sp

@Composable
fun OnlineLobbyView(vm: OnlineViewModel, modifier: Modifier = Modifier) {
    val state = vm.state
    val isConnected = state is OnlineState.Lobby || state is OnlineState.Inviting || state is OnlineState.Invited
    val isInviting = state is OnlineState.Inviting
    val invitedFrom = (state as? OnlineState.Invited)?.from
    var friendInput by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DS.Space.l, vertical = DS.Space.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DS.Space.l)
        ) {
            MyCodeCard(vm)
            InviteCard(
                vm = vm,
                isConnected = isConnected,
                isInviting = isInviting,
                input = friendInput,
                onInputChange = { friendInput = it }
            )
            if (!isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DS.Space.s, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DS.inkMuted)
                    Text(text = loc("Povezujem…"), color = DS.inkMuted, style = Type.body)
                }
            }
            vm.notice?.let { notice ->
                Text(
                    text = onlineNoticeText(notice),
                    color = DS.ink,
                    style = Type.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DS.fill, RoundedCornerShape(DS.Radius.m))
                        .padding(DS.Space.m)
                        .testTag("online.notice")
                )
            }
        }
    }

    if (invitedFrom != null) {
        // Bez `onDismissRequest`: poziv se zatvara samo izborom (kao iOS alert).
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = loc("Kod %@ te poziva na partiju").replace("%@", OnlineCode.format(invitedFrom)),
                    color = DS.ink,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.accept() }) {
                    Text(text = loc("Prihvati"), color = DS.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.decline() }) {
                    Text(text = loc("Odbij"), color = DS.accent)
                }
            },
            containerColor = DS.surface,
            shape = DialogShape
        )
    }
}

@Composable
private fun MyCodeCard(vm: OnlineViewModel) {
    val context = LocalContext.current
    val formatted = OnlineCode.format(vm.myCode)
    Column(
        modifier = Modifier.fillMaxWidth().dsCard().padding(DS.Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DS.Space.m)
    ) {
        Text(text = loc("Tvoj kod"), color = DS.inkMuted, style = Type.caption, fontWeight = FontWeight.Medium)
        Text(
            text = formatted,
            color = DS.ink,
            style = Type.title,
            fontSize = MyCodeSize,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.testTag("online.myCode")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DS.Space.s), modifier = Modifier.fillMaxWidth()) {
            ActionBarButton(
                icon = Icons.Default.ContentCopy,
                label = loc("Kopiraj"),
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Chessko", formatted))
                },
                modifier = Modifier.weight(1f)
            )
            ActionBarButton(
                icon = Icons.Default.Share,
                label = loc("Podeli kod"),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, formatted)
                    }
                    context.startActivity(Intent.createChooser(send, loc("Podeli kod")))
                },
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = { vm.newCode() }, shape = ButtonShape) {
            Text(text = loc("Novi kod"), color = DS.accent, style = Type.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InviteCard(
    vm: OnlineViewModel,
    isConnected: Boolean,
    isInviting: Boolean,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit
) {
    val invite = { if (isConnected) vm.invite(input.text) }
    Column(
        modifier = Modifier.fillMaxWidth().dsCard().padding(DS.Space.l),
        verticalArrangement = Arrangement.spacedBy(DS.Space.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isInviting) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(DS.Space.s, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DS.accent)
                Text(text = loc("Čekam da prijatelj prihvati…"), color = DS.ink, style = Type.body)
            }
            TextButton(onClick = { vm.cancelInvite() }, shape = ButtonShape) {
                Text(text = loc("Otkaži"), color = DS.accent, style = Type.body, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                text = loc("Kod prijatelja"),
                color = DS.inkMuted,
                style = Type.caption,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            BasicTextField(
                value = input,
                onValueChange = { v ->
                    val formatted = formatOnlineCodeInput(v.text)
                    onInputChange(TextFieldValue(formatted, TextRange(formatted.length)))
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = DS.ink,
                    fontSize = FriendCodeSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(DS.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { invite() }),
                modifier = Modifier.fillMaxWidth().testTag("online.friendCode"),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(DS.fill, RoundedCornerShape(DS.Radius.m))
                            .padding(horizontal = DS.Space.m),
                        contentAlignment = Alignment.Center
                    ) {
                        if (input.text.isEmpty()) {
                            Text(
                                text = "K7M-4QX",
                                color = DS.inkMuted,
                                fontSize = FriendCodeSize,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        inner()
                    }
                }
            )
            val enabled = isConnected && input.text.isNotEmpty()
            Button(
                onClick = { invite() },
                enabled = enabled,
                shape = ButtonShape,
                // Nedostupno: ista boja, 38 % providnosti (kao iOS i `ActionBarButton`).
                colors = ButtonDefaults.buttonColors(
                    containerColor = DS.accent, contentColor = DS.onAccent,
                    disabledContainerColor = DS.accent, disabledContentColor = DS.onAccent
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.38f)
            ) {
                Text(text = loc("Pozovi"), style = Type.body, fontWeight = FontWeight.Bold)
            }
        }
    }
}
