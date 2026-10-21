import SwiftUI

// MARK: - Navigation

struct NavigationRootView: View {
    @Environment(Router.self) private var router
    @SceneStorage("navigation.counter") private var counter = 0

    var body: some View {
        @Bindable var router = router
        Form {
            KeyText("navigation_intro")
            Button(L10n.tr("navigation_push")) { router.homePath.append(.navDetail(1)) }.accessibilityIdentifier(TestIDs.navPushDetail)
            Button(L10n.tr("navigation_modal")) { router.showNavModal = true }.accessibilityIdentifier(TestIDs.navOpenModal)
            Button(L10n.tr("navigation_nested")) { router.homePath.append(.navNested) }.accessibilityIdentifier(TestIDs.navOpenNested)
            Section(key: "navigation_state") {
                KeyText("navigation_state_hint").font(.footnote)
                ValueRow(label: L10n.tr("navigation_counter"), value: "\(counter)", valueID: TestIDs.navCounterValue)
                Button(L10n.tr("navigation_increment")) { counter += 1 }.accessibilityIdentifier(TestIDs.navCounterIncrement)
            }
        }
        .screenTitle(L10n.tr("scenario_navigation"))
        .fullScreenCover(isPresented: $router.showNavModal) {
            NavigationStack {
                VStack(alignment: .leading, spacing: 16) {
                    KeyText("navigation_modal_title").font(.title2)
                    KeyText("navigation_modal_body")
                    Spacer()
                }
                .padding()
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(L10n.tr("action_close")) { router.showNavModal = false }.accessibilityIdentifier(TestIDs.navModalClose)
                    }
                }
                .screenRoot(TestIDs.navModalScreen)
            }
        }
        .screenRoot(TestIDs.navigationScreen)
    }
}

struct NavigationDetailView: View {
    @Environment(Router.self) private var router
    let level: Int

    var body: some View {
        Form {
            Text(L10n.tr("navigation_level", level)).font(.title2).accessibilityIdentifier(TestIDs.navDetailLevel)
            Button(L10n.tr("navigation_push_next")) { router.homePath.append(.navDetail(level + 1)) }
                .accessibilityIdentifier(TestIDs.navDetailPushNext)
            Button(L10n.tr("navigation_pop_to_root")) {
                if let root = router.homePath.firstIndex(of: .scenario(.navigation)) {
                    router.homePath = Array(router.homePath.prefix(through: root))
                } else {
                    router.homePath = [.scenario(.navigation)]
                }
            }
            .accessibilityIdentifier(TestIDs.navDetailPopToRoot)
        }
        .screenTitle(L10n.tr("navigation_detail_title", level))
        .screenRoot(TestIDs.navDetailScreen)
    }
}

struct NavigationNestedView: View {
    @State private var tab = "a"
    @State private var path: [String] = []

    var body: some View {
        VStack {
            Picker("", selection: $tab) {
                ForEach(["a", "b", "c"], id: \.self) { name in
                    Text(L10n.tr("navigation_tab", name.uppercased())).tag(name).accessibilityIdentifier(TestIDs.navNestedTab(name))
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier(TestIDs.navNestedTabs)
            .padding()
            Text(L10n.tr("navigation_tab_content", tab.uppercased()))
                .font(.title2)
                .accessibilityIdentifier(TestIDs.navNestedContent)
            Spacer()
        }
        .screenTitle(L10n.tr("navigation_nested"))
        .screenRoot(TestIDs.navNestedScreen)
    }
}
