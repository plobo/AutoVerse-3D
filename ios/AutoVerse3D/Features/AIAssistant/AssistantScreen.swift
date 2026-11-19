import SwiftUI

struct ChatMessage: Identifiable, Equatable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    var notice: String? = nil
    var sources: [String] = []
    var offline = false
}

struct AssistantScreen: View {
    let deps: AppDependencies
    let vehicleId: String?
    let componentId: String?
    @State private var messages: [ChatMessage] = []
    @State private var input = ""
    @State private var sending = false
    @Environment(\.dismiss) private var dismiss
    private let suggestions = ["How do disc brakes work?", "What does an inverter do?", "Why do cars have crumple zones?"]

    private var contextLabel: String {
        let c = try? deps.content.content()
        let parts = [c?.vehicles.vehicles.first { $0.id == vehicleId }?.name, c?.components.components.first { $0.id == componentId }?.name].compactMap { $0 }
        return parts.isEmpty ? "General automotive questions" : parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Context: \(contextLabel)").font(.subheadline.weight(.semibold)).foregroundStyle(Tokens.primary)
                        Disclaimer(text: "Answers can be wrong. Educational information only — not repair advice. Needs an internet connection; offline lessons are used as a fallback.")
                        if messages.isEmpty {
                            Text("Try asking:").font(.subheadline.weight(.semibold))
                            ForEach(suggestions, id: \.self) { s in Chip(title: s, selected: false) { ask(s) } }
                        }
                        ForEach(messages) { bubble($0).id($0.id) }
                        if sending { ProgressView().padding() }
                    }
                    .padding()
                }
                .onChange(of: messages.count) { if let last = messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } } }
            }
            HStack {
                TextField("Ask about a vehicle system…", text: $input, axis: .vertical).lineLimit(1...3).textFieldStyle(.roundedBorder)
                    .onChange(of: input) { if input.count > 500 { input = String(input.prefix(500)) } }
                    .accessibilityIdentifier("assistantInput")
                Button { ask(input); input = "" } label: { Image(systemName: "paperplane.fill") }
                    .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                    .accessibilityLabel("Send question")
            }
            .padding()
        }
        .navigationTitle("AI Assistant")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
    }

    private func bubble(_ m: ChatMessage) -> some View {
        HStack {
            if m.fromUser { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 6) {
                if m.offline { Text("OFFLINE ANSWER").font(.caption2.weight(.bold)).foregroundStyle(Tokens.accent) }
                Text(m.text).foregroundStyle(m.fromUser ? .white : .primary)
                if let n = m.notice { Text(n).font(.footnote).foregroundStyle(.secondary) }
                if !m.sources.isEmpty { Text("Related: " + m.sources.joined(separator: "; ")).font(.caption2).foregroundStyle(.secondary) }
            }
            .padding(12)
            .background(m.fromUser ? Tokens.primary : Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 16))
            if !m.fromUser { Spacer(minLength: 40) }
        }
    }

    private func ask(_ question: String) {
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, !sending else { return }
        messages.append(ChatMessage(fromUser: true, text: q))
        sending = true
        Task {
            let reply: ChatMessage
            switch await deps.assistant.ask(q, vehicleId: vehicleId, componentId: componentId) {
            case let .online(r):
                let notice = [r.safetyNotice, r.scope == "general" ? "General information — not specific to a vehicle." : nil].compactMap { $0 }.joined(separator: "\n")
                reply = ChatMessage(fromUser: false, text: r.answer, notice: notice.isEmpty ? nil : notice, sources: r.sources.map(\.title))
            case let .offline(reason, matches): reply = offlineReply(reason, matches)
            case let .rateLimited(retry, matches): reply = offlineReply("You've asked a lot of questions quickly. Try again in \(retry ?? 60) seconds.", matches)
            case let .invalid(msg): reply = ChatMessage(fromUser: false, text: msg)
            }
            messages.append(reply)
            sending = false
        }
    }

    private func offlineReply(_ reason: String, _ matches: [SearchHit]) -> ChatMessage {
        ChatMessage(fromUser: false,
                    text: matches.first.map { "\($0.document.title): \($0.document.text)" } ?? "No matching offline lessons found. Try words like brakes, battery, airbag or suspension.",
                    notice: "\(reason) Showing matches from offline learning content instead.",
                    sources: matches.dropFirst().map(\.document.title), offline: true)
    }
}
