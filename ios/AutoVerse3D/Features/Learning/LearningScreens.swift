import SwiftUI

struct LearnScreen: View {
    @Environment(AppDependencies.self) private var deps
    let open: (Route) -> Void

    var body: some View {
        let _ = deps.dataVersion
        let learning = try? deps.content.content().learning
        let progress = deps.progress.all()
        List {
            if let learning {
                Section {
                    let pct = LearningProgress.overallPercent(learning.modules, progress: progress)
                    ProgressView(value: Double(pct), total: 100)
                    Text("\(pct)% complete · works offline").font(.footnote)
                }
                ForEach(learning.modules) { m in
                    let p = progress[m.id]
                    Button { open(.module(m.id)) } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: p?.completed == true ? "checkmark.circle.fill" : "circle").foregroundStyle(Tokens.primary)
                                .accessibilityLabel(p?.completed == true ? "Completed" : "Not completed")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(m.title).font(.headline).foregroundStyle(.primary)
                                Text(m.summary).font(.footnote).foregroundStyle(.secondary)
                                Text("\(m.estimatedMinutes) min · \(m.sections.count) sections · \(m.quiz.count) quiz questions" + (p?.bestQuizPercent.map { " · best \($0)%" } ?? ""))
                                    .font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Section { Disclaimer(text: learning.disclaimer) }
            }
        }
        .navigationTitle("Learn")
    }
}

struct ModuleScreen: View {
    @Environment(AppDependencies.self) private var deps
    let moduleId: String
    @State private var quizMode = false
    @State private var answers: [String: Int] = [:]
    @State private var result: QuizResult?

    var body: some View {
        let _ = deps.dataVersion
        if let content = try? deps.content.content(), let m = content.learning.modules.first(where: { $0.id == moduleId }) {
            let progress = deps.progress.get(m.id)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !quizMode {
                        ForEach(m.sections) { s in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(s.heading).font(.headline)
                                Text(s.body)
                                if progress.sectionsRead.contains(s.id) { Label("Read", systemImage: "checkmark").foregroundStyle(Tokens.primary).font(.footnote) }
                                else {
                                    Button("Mark as read") {
                                        var p = LearningProgress.markSectionRead(progress, module: m, sectionId: s.id); p.contentVersion = content.learning.contentVersion
                                        deps.progress.save(p); deps.touch()
                                    }.buttonStyle(.bordered)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading).padding().background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
                        }
                        if !m.quiz.isEmpty { Button("Take the quiz (optional)") { quizMode = true; answers = [:]; result = nil }.buttonStyle(.borderedProminent) }
                    } else {
                        SectionHeader("Quiz")
                        ForEach(Array(m.quiz.enumerated()), id: \.element.id) { qi, q in
                            VStack(alignment: .leading, spacing: 6) {
                                Text("\(qi + 1). \(q.prompt)").font(.headline)
                                ForEach(Array(q.choices.enumerated()), id: \.offset) { ci, choice in
                                    Button { if result == nil { answers[q.id] = ci } } label: {
                                        HStack { Image(systemName: answers[q.id] == ci ? "largecircle.fill.circle" : "circle"); Text(choice); Spacer() }.contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityAddTraits(answers[q.id] == ci ? [.isSelected, .isButton] : .isButton)
                                }
                                if result != nil {
                                    let ok = answers[q.id] == q.correctIndex
                                    Text((ok ? "Correct. " : "Not quite — answer: \(q.choices[q.correctIndex]). ") + q.explanation).font(.footnote).foregroundStyle(ok ? Tokens.primary : .red)
                                }
                            }
                            .padding().background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
                        }
                        if let r = result {
                            Text("You scored \(r.correct)/\(r.total) (\(r.percent)%) — \(r.passed ? "passed!" : "try again to pass (70%).")").font(.headline)
                            HStack { Button("Retry") { result = nil; answers = [:] }.buttonStyle(.bordered); Button("Back to lesson") { quizMode = false }.buttonStyle(.borderedProminent) }
                        } else {
                            Button("Check answers") {
                                let r = QuizScoring.score(m, answers: answers)
                                result = r
                                deps.progress.save(LearningProgress.recordQuiz(deps.progress.get(m.id), module: m, result: r)); deps.touch()
                            }
                            .buttonStyle(.borderedProminent).disabled(answers.count < m.quiz.count)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle(m.title)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
