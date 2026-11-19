package com.autoverse3d.feature.aiassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.autoverse3d.core.designsystem.Disclaimer
import com.autoverse3d.data.AssistantRepository
import com.autoverse3d.data.AssistantResult
import com.autoverse3d.data.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val notice: String? = null, val sources: List<String> = emptyList(), val offline: Boolean = false)

data class AssistantState(val messages: List<ChatMessage> = emptyList(), val sending: Boolean = false, val contextLabel: String = "General automotive questions")

@HiltViewModel
class AssistantViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: AssistantRepository, private val content: ContentRepository) : ViewModel() {
    private val vehicleId: String? = savedState.get<String>("vehicleId")?.takeIf { it.isNotBlank() }
    private val componentId: String? = savedState.get<String>("componentId")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(AssistantState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val c = content.content()
            val v = c.vehicles.vehicles.firstOrNull { it.id == vehicleId }
            val comp = c.components.components.firstOrNull { it.id == componentId }
            val label = listOfNotNull(v?.name, comp?.name).joinToString(" · ").ifBlank { "General automotive questions" }
            _state.update { it.copy(contextLabel = label) }
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || _state.value.sending) return
        _state.update { it.copy(messages = it.messages + ChatMessage(true, question.trim()), sending = true) }
        viewModelScope.launch {
            val reply = when (val r = repo.ask(question, vehicleId, componentId)) {
                is AssistantResult.Online -> ChatMessage(false, r.response.answer,
                    notice = listOfNotNull(r.response.safetyNotice, if (r.response.scope == "general") "General information — not specific to a vehicle." else null).joinToString("\n").ifBlank { null },
                    sources = r.response.sources.map { it.title })
                is AssistantResult.Offline -> offlineReply(r.reason, r.matches.map { it.document.title to it.document.text })
                is AssistantResult.RateLimited -> offlineReply("You've asked a lot of questions quickly. Try again in ${r.retryAfterSeconds ?: 60} seconds.", r.matches.map { it.document.title to it.document.text })
                is AssistantResult.Invalid -> ChatMessage(false, r.message)
            }
            _state.update { it.copy(messages = it.messages + reply, sending = false) }
        }
    }

    private fun offlineReply(reason: String, matches: List<Pair<String, String>>) = ChatMessage(
        fromUser = false,
        text = if (matches.isEmpty()) "No matching offline lessons found. Try words like brakes, battery, airbag or suspension."
        else matches.first().let { "${it.first}: ${it.second}" },
        notice = "$reason Showing matches from offline learning content instead.",
        sources = matches.drop(1).map { it.first },
        offline = true,
    )
}

private val suggestions = listOf("How do disc brakes work?", "What does an inverter do?", "Why do cars have crumple zones?")

@Composable
fun AssistantScreen(vm: AssistantViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(16.dp)) {
            Text("AI Assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Context: ${state.contextLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Disclaimer("Answers can be wrong. Educational information only — not repair advice. Needs an internet connection; offline lessons are used as a fallback.") }
            if (state.messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Try asking:", style = MaterialTheme.typography.labelLarge)
                    suggestions.forEach { s -> AssistChip(onClick = { vm.ask(s) }, label = { Text(s) }) }
                }
            }
            items(state.messages) { m -> Bubble(m) }
            if (state.sending) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(500) }, modifier = Modifier.weight(1f).testTag("assistant_input"), placeholder = { Text("Ask about a vehicle system…") }, maxLines = 3)
            IconButton(onClick = { vm.ask(input); input = "" }, enabled = input.isNotBlank() && !state.sending) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send question")
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (m.offline) Text("OFFLINE ANSWER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(m.text, color = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            m.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (m.sources.isNotEmpty()) Text("Related: " + m.sources.joinToString("; "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
