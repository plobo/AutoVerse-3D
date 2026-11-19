import XCTest

/// Walks the core journeys and saves screenshots. Run with TEST_RUNNER_SCREENSHOT_DIR=/abs/path (see docs/TESTING_STRATEGY.md).
final class ScreenshotTour: XCTestCase {
    private var dir: String? { ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] }

    @MainActor private func snap(_ name: String, wait: TimeInterval = 1.5) {
        Thread.sleep(forTimeInterval: wait)
        let shot = XCUIScreen.main.screenshot()
        let att = XCTAttachment(screenshot: shot); att.name = name; att.lifetime = .keepAlways; add(att)
        if let dir { try? shot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png")) }
    }

    @MainActor private func scrollUp(_ app: XCUIApplication, times: Int = 1) { for _ in 0..<times { app.swipeUp(velocity: .slow) } }

    @MainActor
    func testTour() throws {
        try XCTSkipIf(dir == nil, "Set TEST_RUNNER_SCREENSHOT_DIR to capture the screenshot tour")
        let app = XCUIApplication()
        app.launchArguments = ["-uiTestingInMemoryStore"]
        app.launch()
        snap("01_home", wait: 3)
        app.buttons["exploreFeatured"].tap()
        _ = app.staticTexts["Paint"].waitForExistence(timeout: 20)
        snap("02_exterior", wait: 6)
        app.buttons["Midnight Blue"].tap()
        scrollUp(app)
        app.buttons["Open front-left door"].tap(); app.buttons["Open hood"].tap(); app.buttons["Lights off"].tap()
        snap("03_exterior_paint_doors_lights", wait: 2)
        app.buttons["Side"].tap(); snap("04_side_view", wait: 2)
        app.segmentedControls.buttons["Interior"].tap(); snap("05_interior", wait: 3)
        app.buttons["Steering wheel"].firstMatch.tap(); snap("06_interior_hotspot")
        app.segmentedControls.buttons["Mechanical"].tap()
        app.buttons["Three-quarter"].tap(); snap("07_mechanical", wait: 3)
        app.buttons["Explode: Full mechanical assembly"].tap()
        scrollUp(app, times: 2)
        if app.buttons["Front brake assembly"].exists { app.buttons["Front brake assembly"].tap() }
        app.swipeDown(velocity: .fast); app.swipeDown(velocity: .fast)
        snap("08_mechanical_exploded", wait: 2)
        app.segmentedControls.buttons["Safety"].tap(); snap("10_safety", wait: 3)
        app.buttons["Seat belts"].firstMatch.tap(); snap("11_safety_hotspot")
        app.segmentedControls.buttons["Customize"].tap()
        app.buttons["Forest Green"].tap(); app.buttons["Mesh Ten"].tap()
        scrollUp(app)
        app.buttons["Satin Silver"].tap(); app.buttons["Sand"].tap()
        app.buttons["saveConfiguration"].tap()
        let field = app.alerts.textFields.firstMatch
        _ = field.waitForExistence(timeout: 5)
        field.tap(); field.clearAndType("Forest Weekender")
        snap("12a_customize_save_dialog", wait: 1)
        app.alerts.buttons["Save"].tap()
        if app.alerts.buttons["OK"].waitForExistence(timeout: 3) { snap("12_customize_saved", wait: 0.5); app.alerts.buttons["OK"].tap() }
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons["Showroom"].tap(); snap("13_showroom")
        app.tabBars.buttons["Explore"].tap(); snap("14_explore")
        app.buttons["EV Lab"].tap(); snap("15_ev_lab", wait: 2)
        app.segmentedControls.buttons["Regenerative braking"].tap(); snap("16_ev_lab_regen", wait: 2)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons["Garage"].tap(); snap("17_garage", wait: 4)
        let download = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Download v'")).firstMatch
        if download.waitForExistence(timeout: 8) { download.tap(); snap("17b_garage_downloaded", wait: 8) }
        app.buttons["config_Forest Weekender"].tap(); snap("17c_reopened_from_garage", wait: 8)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons["Learn"].tap(); snap("18_learn")
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'Electric vehicle fundamentals'")).firstMatch.tap(); snap("19_module")
        scrollUp(app, times: 5)
        app.buttons["Take the quiz (optional)"].tap()
        for _ in 0..<6 { app.swipeDown(velocity: .fast) }
        for choice in ["Converts DC from the battery to AC for the motor", "From the wheels back into the battery", "Kilowatt-hours (kWh)", "To protect the battery cells"] {
            let b = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", choice)).firstMatch
            var tries = 0
            while !b.isHittable && tries < 4 { scrollUp(app); tries += 1 }
            b.tap()
        }
        scrollUp(app, times: 2)
        app.buttons["Check answers"].tap(); scrollUp(app, times: 2); snap("20_quiz_result")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["assistantButton"].firstMatch.tap()
        app.buttons["How do disc brakes work?"].tap(); snap("21_assistant", wait: 5)
    }

    /// Run with the backend stopped: the assistant must fall back to offline learning content.
    @MainActor
    func testOfflineAssistant() throws {
        try XCTSkipIf(ProcessInfo.processInfo.environment["OFFLINE_SHOTS"] == nil, "Set TEST_RUNNER_OFFLINE_SHOTS=1 with the backend stopped")
        let app = XCUIApplication()
        app.launchArguments = ["-uiTestingInMemoryStore"]
        app.launch()
        app.buttons["assistantButton"].firstMatch.tap()
        app.buttons["What does an inverter do?"].tap()
        XCTAssertTrue(app.staticTexts["OFFLINE ANSWER"].waitForExistence(timeout: 15))
        snap("23_offline_assistant_fallback", wait: 1)
    }
}

@MainActor
extension XCUIElement {
    func clearAndType(_ text: String) {
        if let current = value as? String, !current.isEmpty {
            typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        typeText(text)
    }
}
