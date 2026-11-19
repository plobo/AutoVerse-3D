import SwiftData
import SwiftUI

@main
struct AutoVerse3DApp: App {
    private let container: ModelContainer
    @State private var deps: AppDependencies

    init() {
        let inMemory = ProcessInfo.processInfo.arguments.contains("-uiTestingInMemoryStore")
        do { container = try Persistence.makeContainer(inMemory: inMemory) } catch { fatalError("SwiftData container failed: \(error)") }
        _deps = State(initialValue: AppDependencies(container: container))
    }

    var body: some Scene {
        WindowGroup {
            RootView().environment(deps).tint(Tokens.primary)
        }
        .modelContainer(container)
    }
}

/// Destinations shared by every tab's NavigationStack.
enum Route: Hashable {
    case viewer(vehicleId: String, mode: ViewerMode, configId: String?)
    case module(String)
    case evLab
}

enum Tab: Hashable { case home, showroom, explore, garage, learn }

/// Main navigation (§4): Home, Showroom, Explore, My Garage, Learn. The AI Assistant is a toolbar action on every
/// tab and inside the viewer (iOS shows at most five tabs without a "More" overflow — see docs/IOS_SETUP.md).
struct RootView: View {
    @Environment(AppDependencies.self) private var deps
    @State private var tab: Tab = .home
    @State private var paths: [Tab: NavigationPath] = [:]
    @State private var showAssistant = false

    var body: some View {
        TabView(selection: $tab) {
            stack(.home) { HomeScreen(open: { open($0) }, openAssistant: { showAssistant = true }) }
                .tabItem { Label("Home", systemImage: "house") }.tag(Tab.home)
            stack(.showroom) { ShowroomScreen(open: { open($0) }) }
                .tabItem { Label("Showroom", systemImage: "car") }.tag(Tab.showroom)
            stack(.explore) { ExploreScreen(open: { open($0) }) }
                .tabItem { Label("Explore", systemImage: "safari") }.tag(Tab.explore)
            stack(.garage) { GarageScreen(open: { open($0) }) }
                .tabItem { Label("Garage", systemImage: "door.garage.closed") }.tag(Tab.garage)
            stack(.learn) { LearnScreen(open: { open($0) }) }
                .tabItem { Label("Learn", systemImage: "graduationcap") }.tag(Tab.learn)
        }
        .sheet(isPresented: $showAssistant) { NavigationStack { AssistantScreen(deps: deps, vehicleId: nil, componentId: nil) } }
    }

    private func open(_ route: Route) { paths[tab, default: NavigationPath()].append(route) }

    @ViewBuilder
    private func stack<Content: View>(_ t: Tab, @ViewBuilder content: () -> Content) -> some View {
        NavigationStack(path: Binding(get: { paths[t] ?? NavigationPath() }, set: { paths[t] = $0 })) {
            content()
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showAssistant = true } label: { Image(systemName: "sparkles") }
                            .accessibilityLabel("AI Assistant").accessibilityIdentifier("assistantButton")
                    }
                }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case let .viewer(v, m, c): VehicleViewerScreen(deps: deps, vehicleId: v, mode: m, configId: c)
                    case let .module(id): ModuleScreen(moduleId: id)
                    case .evLab: EvLabScreen()
                    }
                }
        }
    }
}
