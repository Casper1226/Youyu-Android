package com.youyu.android.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.youyu.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AccountDraft(
    val id: String, val name: String, val kind: AccountKind, val currency: Currency, val opening: String,
    val archived: Boolean, val calibrate: Boolean, val currentBalance: String, val icon: String?, val cardFace: String,
) {
    override fun toString() = "AccountDraft(redacted)"
    companion object {
        fun from(item: Account?, state: LedgerSnapshot) = AccountDraft(item?.id ?: newId(), item?.name.orEmpty(),
            item?.kind ?: AccountKind.DEBIT, item?.currency ?: state.settings.reportingCurrency,
            item?.openingBalance?.let(Amounts::encode) ?: "0", item?.archived ?: false, false,
            item?.let { Amounts.encode(LedgerRules.balance(it, state.entries)) }.orEmpty(), item?.icon, item?.cardFace ?: "quiet")
    }
}

internal data class PhotoRequest(val kind: ArtworkKind, val draft: AccountDraft?, val uri: Uri? = null, val returned: Boolean = false) {
    override fun toString() = "PhotoRequest(redacted)"
}

private val StartPhoto = staticCompositionLocalOf<(PhotoRequest) -> Unit> { error("Missing photo launcher") }

@Composable
internal fun PhotoHost(model: LedgerViewModel, content: @Composable () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        model.photo = model.photo?.copy(uri = uri, returned = true)
    }
    CompositionLocalProvider(StartPhoto provides { request ->
        model.photo = request
        try { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: android.content.ActivityNotFoundException) {
            model.photo = null
            model.perform { Result.failure(LedgerException("系统图片选择器不可用")) }
        }
    }, content = content)
}

@Composable
internal fun ArtworkImage(key: String, model: LedgerViewModel, modifier: Modifier, label: String? = null, small: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, key, small, model) {
        value = null
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = if (small && !key.startsWith("bank:")) 4 else 1 }
            when {
                key.startsWith("bank:") -> context.assets.open("institutions/" + key.removePrefix("bank:") + ".png").use { BitmapFactory.decodeStream(it, null, opts) }
                key.startsWith("photo:") -> model.repository.image(key.removePrefix("photo:"))?.let { BitmapFactory.decodeByteArray(it, 0, it.size, opts) }
                AccountAppearance.faces.any { it.id == key && key != "quiet" } -> context.assets.open("cards/$key.jpg").use { BitmapFactory.decodeStream(it, null, opts) }
                else -> null
            }
        }
    }
    Box(modifier.background(if (key.startsWith("bank:")) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxSize(), contentScale = if (key.startsWith("bank:")) ContentScale.Fit else ContentScale.Crop) }
    }
}

@Composable
internal fun AccountIcon(key: String, model: LedgerViewModel, modifier: Modifier = Modifier.size(42.dp)) {
    val shape = RoundedCornerShape(12.dp)
    if (key.startsWith("bank:") || key.startsWith("photo:")) ArtworkImage(key, model, modifier.clip(shape))
    else Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(symbol(key.removePrefix("symbol:")), null, Modifier.fillMaxSize(.6f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun AccountSurface(face: String, model: LedgerViewModel, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (face != "quiet") {
            ArtworkImage(face, model, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .30f)))))
        }
        CompositionLocalProvider(LocalContentColor provides if (face == "quiet") MaterialTheme.colorScheme.onSurface else Color.White, content = content)
    }
}

@Composable
internal fun AccountAppearanceControls(draft: AccountDraft, model: LedgerViewModel, library: (ArtworkKind) -> Unit) {
    AccountSurface(draft.cardFace, model, Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountIcon(AccountAppearance.icon(draft.name, draft.kind, draft.icon), model)
            Column { Text(draft.name.ifBlank { "账户预览" }, style = MaterialTheme.typography.titleMedium); Text(draft.kind.title, style = MaterialTheme.typography.bodySmall) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ library(ArtworkKind.ICON) }, Modifier.weight(1f)) { Text("选择图标") }
        OutlinedButton({ library(ArtworkKind.CARD) }, Modifier.weight(1f)) { Text("选择卡面") }
    }
}

private data class ArtOption(val key: String, val name: String, val group: String, val terms: String = "")

@Composable
internal fun ArtworkLibrary(kind: ArtworkKind, state: LedgerSnapshot, model: LedgerViewModel, op: OperationState,
                            draft: AccountDraft?, dismiss: () -> Unit, select: ((String?) -> Unit)? = null) {
    val isIcon = kind == ArtworkKind.ICON
    val noun = if (isIcon) "图标" else "卡面"
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("全部") }
    var rename by remember { mutableStateOf<Artwork?>(null) }
    var remove by remember { mutableStateOf<Artwork?>(null) }
    var preview by remember { mutableStateOf<ArtOption?>(null) }
    val launch = StartPhoto.current
    val builtin = remember(kind) {
        if (isIcon) AccountAppearance.institutions.map { ArtOption("bank:" + it.id, it.name, it.group, it.terms.joinToString(" ")) } +
            symbols.keys.map { ArtOption("symbol:$it", symbolLabels.getValue(it), "通用") }
        else AccountAppearance.faces.map { ArtOption(it.id, it.name, it.group) }
    }
    val mine = state.artwork.filter { it.kind == kind && it.listed }
    val all = mine.map { ArtOption(it.key, it.name, "我的") } + builtin
    val used = state.accounts.groupBy { if (isIcon) AccountAppearance.icon(it.name, it.kind, it.icon) else it.cardFace }
    val groups = listOf("全部", "用过", "我的") + builtin.map { it.group }.distinct()
    val choices = (if (group == "用过") used.mapNotNull { (key, accounts) ->
        val option = all.find { it.key == key } ?: state.artwork.find { it.key == key }?.let { ArtOption(key, it.name, "我的") }
        option?.copy(terms = option.terms + " " + accounts.joinToString(" ") { it.name })
    } else all.filter { group == "全部" || group == it.group }).filter {
        val search = query.trim()
        search.isEmpty() || (it.name + " " + it.terms).contains(search, true)
    }
    FormPage(if (isIcon) "账户图标库" else "卡面管理", op.busy, op.error, dismiss, null) {
        Field("搜索$noun", query, { query = it })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEach { g -> FilterChip(group == g, { group = g }, { Text(g) }) }
        }
        OutlinedButton({ launch(PhotoRequest(kind, draft)) }, Modifier.fillMaxWidth()) { Text("从相册裁剪$noun") }
        if (isIcon && select != null) TextButton({ select(null) }) { Text("按账户名称自动匹配") }
        if (choices.isEmpty()) Text(if (query.isBlank()) "这里还没有$noun" else "没有匹配的$noun")
        val columns = if (isIcon) 3 else 2
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { art ->
                    val custom = mine.find { it.key == art.key }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                        if (select == null) preview = art else select(art.key)
                    }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isIcon) AccountIcon(art.key, model, Modifier.size(46.dp))
                        else ArtworkImage(art.key, model, Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp)), small = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(art.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (custom != null) {
                                var menu by remember(art.key) { mutableStateOf(false) }
                                Box {
                                    IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Icons.Outlined.MoreVert, "管理${art.name}") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem({ Text("重命名") }, { menu = false; rename = custom })
                                        DropdownMenuItem({ Text("移除") }, { menu = false; remove = custom })
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    preview?.let { art -> AlertDialog({ preview = null }, title = { Text(art.name) }, text = {
        if (isIcon) AccountIcon(art.key, model, Modifier.size(96.dp))
        else ArtworkImage(art.key, model, Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp)))
    }, confirmButton = { TextButton({ preview = null }) { Text("完成") } }) }
    rename?.let { art ->
        var name by remember(art.id) { mutableStateOf(art.name) }
        AlertDialog({ rename = null }, title = { Text("重命名$noun") }, text = { Field("名称", name, { name = it }) },
            confirmButton = { TextButton({ model.perform({ rename = null }) { model.repository.renameArtwork(art.id, name) } }, enabled = name.isNotBlank() && name.length <= 40 && !op.busy) { Text("保存") } },
            dismissButton = { TextButton({ rename = null }) { Text("取消") } })
    }
    remove?.let { art -> AlertDialog({ remove = null }, title = { Text("移除「${art.name}」？") }, text = { Text("已使用此图片的账户会继续显示原图。") },
        confirmButton = { TextButton({ model.perform({ remove = null }) { model.repository.removeArtwork(art) } }, enabled = !op.busy) { Text("移除") } },
        dismissButton = { TextButton({ remove = null }) { Text("取消") } }) }
    val request = model.photo
    if (request?.kind == kind && request.returned) {
        val uri = request.uri
        if (uri == null) LaunchedEffect(request) { model.photo = null }
        else CropPhoto(uri, kind, model, op, { model.photo = null }) { art ->
            model.photo = null
            if (select != null) select(art.key) else { group = "我的"; query = "" }
        }
    }
}
