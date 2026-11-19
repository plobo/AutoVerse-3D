import SwiftUI

struct HomeScreen: View {
    @Environment(AppDependencies.self) private var deps
    let open: (Route) -> Void
    let openAssistant: () -> Void

    var body: some View {
        let content = try? deps.content.content()
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Your virtual automotive discovery studio").font(.subheadline).foregroundStyle(.secondary)
                if let featured = content?.vehicles.vehicles.first(where: \.isAvailable) {
                    VStack(alignment: .leading, spacing: 0) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("FEATURED").font(.caption2.weight(.bold))
                            Text(featured.name).font(.largeTitle.bold())
                            Text(featured.categoryLabel)
                        }
                        .foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 130, alignment: .bottomLeading).padding()
                        .background(LinearGradient(colors: [Tokens.primary, Tokens.primary.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        VStack(alignment: .leading, spacing: 10) {
                            Text(featured.description).font(.subheadline)
                            Button("Explore in 3D") { open(.viewer(vehicleId: featured.id, mode: .exterior, configId: nil)) }
                                .buttonStyle(.borderedProminent).accessibilityIdentifier("exploreFeatured")
                        }
                        .padding()
                    }
                    .background(.background, in: RoundedRectangle(cornerRadius: 16))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.08), radius: 8, y: 2)
                }
                if let last = deps.lastExplored, let v = content?.vehicles.vehicles.first(where: { $0.id == last.vehicleId }) {
                    Shortcut(icon: "arrow.forward.circle", title: "Continue exploring", subtitle: "\(v.name) · \(last.mode.capitalized) view") {
                        open(.viewer(vehicleId: v.id, mode: ViewerMode(rawValue: last.mode) ?? .exterior, configId: nil))
                    }
                }
                SectionHeader("Learning shortcuts")
                if let modules = content?.learning.modules {
                    let pct = { _ = deps.dataVersion; return LearningProgress.overallPercent(modules, progress: deps.progress.all()) }()
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Learning progress", systemImage: "graduationcap.fill").font(.headline).foregroundStyle(Tokens.primary)
                        ProgressView(value: Double(pct), total: 100)
                        Text("\(pct)% of \(modules.count) modules complete").font(.footnote)
                    }
                    .padding().background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
                }
                Shortcut(icon: "bolt.fill", title: "EV Lab", subtitle: "See how energy flows from battery to wheels") { open(.evLab) }
                Shortcut(icon: "sparkles", title: "Ask the assistant", subtitle: "Questions about UrbanX or how cars work", action: openAssistant)
                Text("All vehicles in AutoVerse 3D are original fictional designs.").font(.caption).foregroundStyle(.secondary)
            }
            .padding()
        }
        .navigationTitle("AutoVerse 3D")
    }
}

struct Shortcut: View {
    let icon: String, title: String, subtitle: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(Tokens.primary).font(.title3)
                VStack(alignment: .leading) { Text(title).font(.headline); Text(subtitle).font(.footnote).foregroundStyle(.secondary) }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .padding().background(Tokens.surfaceVariant, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityHint(subtitle)
    }
}
