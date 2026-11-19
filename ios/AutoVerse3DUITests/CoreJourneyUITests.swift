import XCTest

/// Critical journeys: main navigation, open UrbanX, switch explorer modes, save a configuration.
final class CoreJourneyUITests: XCTestCase {
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-uiTestingInMemoryStore"]
        app.launch()
        return app
    }

    @MainActor
    func testMainNavigation() {
        let app = launch()
        XCTAssertTrue(app.buttons["exploreFeatured"].waitForExistence(timeout: 10))
        for (tab, marker) in [("Showroom", "TerraX"), ("Explore", "Mechanical explorer"), ("Garage", "Saved configurations"), ("Learn", "Electric vehicle fundamentals")] {
            app.tabBars.buttons[tab].tap()
            XCTAssertTrue(app.staticTexts[marker].waitForExistence(timeout: 5), "\(tab) should show \(marker)")
        }
        app.buttons["assistantButton"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Try asking:"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testExploreAndSaveConfiguration() {
        let app = launch()
        app.buttons["exploreFeatured"].tap()
        XCTAssertTrue(app.staticTexts["Paint"].waitForExistence(timeout: 20))
        app.segmentedControls.buttons["Mechanical"].tap()
        XCTAssertTrue(app.buttons["Explode: Full mechanical assembly"].waitForExistence(timeout: 5))
        app.buttons["Explode: Full mechanical assembly"].tap()
        XCTAssertTrue(app.buttons["Assemble full mechanical assembly"].waitForExistence(timeout: 5))
        app.segmentedControls.buttons["Customize"].tap()
        app.buttons["Ember Red"].tap()
        app.buttons["saveConfiguration"].tap()
        let field = app.alerts.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap(); field.typeText(" Test")
        app.alerts.buttons["Save"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons["Garage"].tap()
        XCTAssertTrue(app.staticTexts["My UrbanX Test"].waitForExistence(timeout: 5))
    }
}
