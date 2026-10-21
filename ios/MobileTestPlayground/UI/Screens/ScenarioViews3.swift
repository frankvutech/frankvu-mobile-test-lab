import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

// MARK: - Accessibility

struct AccessibilityView: View {
    @State private var favorite = false
    @State private var announcements = 0
    @State private var lastAction: String?

    var body: some View {
        Form {
            Text(L10n.tr("a11y_heading")).font(.title2).accessibilityAddTraits(.isHeader).accessibilityIdentifier(TestIDs.a11yHeading)
            KeyText("a11y_intro")
            Section(key: "a11y_icon_button") {
                Button { favorite.toggle() } label: { Image(systemName: favorite ? "heart.fill" : "heart") }
                    .accessibilityLabel(L10n.tr("a11y_favorite"))
                    .accessibilityValue(L10n.tr(favorite ? "a11y_favorite_on" : "a11y_favorite_off"))
                    .accessibilityIdentifier(TestIDs.a11yFavorite)
            }
            Section(key: "a11y_merged") {
                // Combined: VoiceOver reads the row as one element instead of three fragments.
                HStack {
                    Image(systemName: "star.fill")
                    VStack(alignment: .leading) {
                        Text("Product 001").font(.headline)
                        Text(L10n.tr("a11y_rating"))
                    }
                }
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier(TestIDs.a11yMergedCard)
            }
            Section(key: "a11y_custom_actions") {
                Text(L10n.tr("a11y_custom_actions_body"))
                    .accessibilityAction(named: L10n.tr("dialogs_action_archive")) { lastAction = "archive" }
                    .accessibilityAction(named: L10n.tr("dialogs_action_share")) { lastAction = "share" }
                    .accessibilityIdentifier(TestIDs.a11yCustomActionItem)
                ValueRow(label: L10n.tr("a11y_last_action"), value: lastAction ?? L10n.tr("value_none"),
                    valueID: TestIDs.a11yCustomActionResult)
            }
            Section(key: "a11y_live_region") {
                Text(L10n.tr("a11y_announcements", announcements)).accessibilityIdentifier(TestIDs.a11yLiveRegion)
                Button(L10n.tr("a11y_announce")) {
                    announcements += 1
                    UIAccessibility.post(notification: .announcement, argument: L10n.tr("a11y_announcements", announcements))
                }
                .accessibilityIdentifier(TestIDs.a11yLiveRegionTrigger)
            }
            Section(key: "a11y_touch_target") {
                Button(L10n.tr("a11y_large_target")) {}
                    .font(.caption2)
                    .frame(minWidth: 160, minHeight: 56)
                    .contentShape(Rectangle())
                    .accessibilityIdentifier(TestIDs.a11yLargeTarget)
            }
            Section(key: "a11y_images") {
                HStack {
                    Image(systemName: "star.circle.fill").font(.largeTitle)
                        .accessibilityLabel(L10n.tr("a11y_image_description"))
                        .accessibilityIdentifier(TestIDs.a11yInformativeImage)
                    // Decorative: hidden from VoiceOver.
                    Image(systemName: "photo").font(.largeTitle).accessibilityHidden(true)
                }
            }
        }
        .screenTitle(L10n.tr("scenario_accessibility"))
        .screenRoot(TestIDs.accessibilityScreen)
    }
}

// MARK: - Error handling

struct ErrorHandlingView: View {
    @Environment(AppEnvironment.self) private var env
    @SceneStorage("errors.state") private var state = "NONE"
    @State private var inline = ""

    private var inlineError: String? {
        if inline.isEmpty { return nil }
        if !inline.allSatisfy(\.isNumber) { return L10n.tr("errors_inline_digits") }
        if inline.count > 5 { return L10n.tr("errors_inline_length") }
        return nil
    }

    var body: some View {
        Form {
            KeyText("errors_intro")
            Button(L10n.tr("errors_network")) { triggerNetwork() }.accessibilityIdentifier(TestIDs.errorsTriggerNetwork)
            Button(L10n.tr("errors_unexpected")) {
                // Simulated failure caught at the boundary; the app never crashes.
                do { throw NetworkError.connectivity } catch { state = "UNEXPECTED" }
            }
            .accessibilityIdentifier(TestIDs.errorsTriggerUnexpected)
            Button(L10n.tr("errors_empty")) { state = "EMPTY" }.accessibilityIdentifier(TestIDs.errorsTriggerEmpty)
            Button(L10n.tr("errors_recoverable")) { state = "RECOVERABLE" }.accessibilityIdentifier(TestIDs.errorsTriggerRecoverable)
            Text(state).font(.caption2).accessibilityIdentifier(TestIDs.errorsState)
            panel
            Section(key: "errors_inline_title") {
                TextField(L10n.tr("errors_inline_label"), text: $inline).accessibilityIdentifier(TestIDs.errorsInlineInput)
                if let inlineError { ErrorLabel(text: inlineError, id: TestIDs.errorsInlineError) }
            }
        }
        .screenTitle(L10n.tr("scenario_errors"))
        .screenRoot(TestIDs.errorsScreen)
    }

    @ViewBuilder private var panel: some View {
        let messages = ["NETWORK": "errors_message_network", "UNEXPECTED": "errors_message_unexpected", "EMPTY": "errors_message_empty",
                        "RECOVERABLE": "errors_message_recoverable", "RECOVERED": "errors_message_recovered"]
        if state == "LOADING" {
            ProgressView().accessibilityIdentifier(TestIDs.errorsLoading)
        } else if let key = messages[state] {
            Section {
                Text(L10n.tr(key)).accessibilityIdentifier(TestIDs.errorsMessage)
                if state == "NETWORK" || state == "RECOVERABLE" {
                    Button(L10n.tr("action_retry")) {
                        if state == "NETWORK" { triggerNetwork() } else { state = "RECOVERED" }
                    }
                    .accessibilityIdentifier(TestIDs.errorsRetry)
                }
                Button(L10n.tr("action_dismiss")) { state = "NONE" }.accessibilityIdentifier(TestIDs.errorsDismiss)
            }
        }
    }

    private func triggerNetwork() {
        state = "LOADING"
        Task { @MainActor in
            let result = await env.network.fetchProducts(.serverError)
            if case .failure = result { state = "NETWORK" } else { state = "NONE" }
        }
    }
}

// MARK: - Dynamic content

struct DynamicContentView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var loadState = 0 // 0 idle, 1 loading, 2 loaded
    @State private var visible = true
    @State private var delayedEnabled = false
    @State private var countdown: Int?
    @State private var textUpdated = false

    var body: some View {
        Form {
            Section(key: "dynamic_load_title") {
                Button(L10n.tr("dynamic_load")) {
                    guard loadState != 1 else { return }
                    loadState = 1
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: UInt64(env.timing.dynamicLoad * 1_000_000_000))
                        loadState = 2
                    }
                }
                .accessibilityIdentifier(TestIDs.dynamicContentLoad)
                if loadState == 1 { ProgressView().accessibilityIdentifier(TestIDs.dynamicContentLoading) }
                if loadState == 2 { Text(L10n.tr("dynamic_loaded")).accessibilityIdentifier(TestIDs.dynamicContentLoaded) }
            }
            Section(key: "dynamic_toggle_title") {
                Button(L10n.tr(visible ? "dynamic_hide" : "dynamic_show")) { withAnimation { visible.toggle() } }
                    .accessibilityIdentifier(TestIDs.dynamicContentToggle)
                if visible { Text(L10n.tr("dynamic_toggle_target")).accessibilityIdentifier(TestIDs.dynamicContentToggleTarget) }
            }
            Section(key: "dynamic_delayed_title") {
                Button(L10n.tr("dynamic_delayed_button")) {}.disabled(!delayedEnabled)
                    .accessibilityIdentifier(TestIDs.dynamicContentDelayedButton)
                ValueRow(label: L10n.tr("dynamic_delayed_title"), value: L10n.tr(delayedEnabled ? "dynamic_enabled" : "dynamic_disabled"),
                         valueID: TestIDs.dynamicContentDelayedStatus)
            }
            Section(key: "dynamic_countdown_title") {
                Button(L10n.tr("dynamic_countdown_start")) { startCountdown() }
                    .accessibilityIdentifier(TestIDs.dynamicContentCountdownStart)
                ValueRow(label: L10n.tr("dynamic_countdown_title"),
                         value: countdown.map { $0 == 0 ? L10n.tr("dynamic_countdown_done") : "\($0)" } ?? L10n.tr("value_not_started"),
                         valueID: TestIDs.dynamicContentCountdownValue)
            }
            Section(key: "dynamic_text_title") {
                Button(L10n.tr("dynamic_change_text")) {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        textUpdated.toggle()
                    }
                }
                .accessibilityIdentifier(TestIDs.dynamicContentChangeText)
                Text(L10n.tr(textUpdated ? "dynamic_text_updated" : "dynamic_text_original"))
                    .accessibilityIdentifier(TestIDs.dynamicContentText)
            }
        }
        .screenTitle(L10n.tr("scenario_dynamic"))
        .task {
            try? await Task.sleep(nanoseconds: UInt64(env.timing.delayedEnable * 1_000_000_000))
            delayedEnabled = true
        }
        .screenRoot(TestIDs.dynamicContentScreen)
    }

    private func startCountdown() {
        Task { @MainActor in
            for value in stride(from: 5, through: 0, by: -1) {
                countdown = value
                if value > 0 { try? await Task.sleep(nanoseconds: 1_000_000_000) }
            }
        }
    }
}

// MARK: - Test data

struct TestDataView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var status: String?

    var body: some View {
        Form {
            Section(key: "test_data_users") {
                ForEach(SeedData.accounts, id: \.user.id) { account in
                    ValueRow(label: account.user.email, value: account.locked ? L10n.tr("admin_user_locked") : account.user.role.rawValue,
                             valueID: TestIDs.testDataUser(account.user.id))
                }
            }
            Section(key: "test_data_products") {
                ForEach(SeedData.products.prefix(5)) { product in
                    ValueRow(label: product.name, value: PriceFormatter.format(product.priceCents),
                        valueID: TestIDs.testDataProduct(product.id))
                }
                KeyText("test_data_more_products", SeedData.products.count).font(.footnote)
            }
            Section(key: "lists_dynamic") {
                ValueRow(label: L10n.tr("test_data_item_count"), value: "\(env.dynamicItems.items.count)",
                    valueID: TestIDs.testDataItemCount)
                Button(L10n.tr("test_mode_generate_data")) {
                    env.dynamicItems.generate()
                    status = L10n.tr("test_mode_done_generate_data")
                }
                .accessibilityIdentifier(TestIDs.testDataGenerate)
                Button(L10n.tr("test_mode_reset_data")) {
                    env.dynamicItems.reset()
                    status = L10n.tr("test_mode_done_reset_data")
                }
                .accessibilityIdentifier(TestIDs.testDataReset)
                if let status { Text(status).accessibilityIdentifier(TestIDs.testDataStatus) }
            }
        }
        .screenTitle(L10n.tr("scenario_test_data"))
        .screenRoot(TestIDs.testDataScreen)
    }
}

// MARK: - Deep links

struct DeepLinksView: View {
    @Environment(Router.self) private var router
    @State private var input = "mtp://playground/"
    @State private var invalid = false

    static let examples = [
        ("home", "mtp://playground/home"), ("forms", "mtp://playground/scenario/forms"),
        ("product", "mtp://playground/product/product_003"), ("cart", "mtp://playground/cart"),
        ("admin", "mtp://playground/admin"), ("detail", "mtp://playground/detail/3")
    ]

    var body: some View {
        Form {
            KeyText("deep_links_intro")
            Section(key: "deep_links_examples") {
                ForEach(Self.examples, id: \.0) { name, link in
                    Button(link) { if let parsed = DeepLinkParser.parse(link) { router.open(parsed) } }
                        .font(.system(.footnote, design: .monospaced))
                        .accessibilityIdentifier(TestIDs.deepLinkExample(name))
                }
            }
            Section(key: "deep_links_custom") {
                TextField(L10n.tr("deep_links_url"), text: $input)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier(TestIDs.deepLinkInput)
                    .onChange(of: input) { invalid = false }
                if invalid { ErrorLabel(text: L10n.tr("deep_links_invalid"), id: TestIDs.deepLinkResult) }
                Button(L10n.tr("deep_links_open")) {
                    if let parsed = DeepLinkParser.parse(input.trimmingCharacters(in: .whitespaces)) {
                        router.open(parsed)
                    } else {
                        invalid = true
                    }
                }
                .accessibilityIdentifier(TestIDs.deepLinkOpen)
            }
        }
        .screenTitle(L10n.tr("scenario_deep_links"))
        .screenRoot(TestIDs.deepLinksScreen)
    }
}

// MARK: - Date and time

struct DateTimeView: View {
    @State private var date = DateMath.isoFormatter.date(from: DateMath.referenceDate) ?? Date()
    @State private var time = Calendar.current.date(bySettingHour: 9, minute: 30, second: 0, of: Date()) ?? Date()
    @SceneStorage("datetime.date") private var dateValue = ""
    @SceneStorage("datetime.time") private var timeValue = ""

    var body: some View {
        Form {
            ValueRow(label: L10n.tr("date_time_reference"), value: DateMath.referenceDate, valueID: TestIDs.dateTimeReference)
            DatePicker(selection: $date, displayedComponents: .date) { KeyText("form_pick_date") }
                .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                .accessibilityIdentifier(TestIDs.dateTimePickDate)
                .onChange(of: date) { _, value in dateValue = DateMath.isoFormatter.string(from: value) }
            ValueRow(label: L10n.tr("date_time_selected_date"), value: dateValue.isEmpty ? L10n.tr("value_not_set") : dateValue,
                     valueID: TestIDs.dateTimeDateValue)
            ValueRow(label: L10n.tr("date_time_days_from_reference"),
                     value: DateMath.daysFromReference(dateValue).map { L10n.plural("date_time_days", $0) } ?? L10n.tr("value_not_set"),
                     valueID: TestIDs.dateTimeDaysFromReference)
            DatePicker(selection: $time, displayedComponents: .hourAndMinute) { KeyText("form_pick_time") }
                .accessibilityIdentifier(TestIDs.dateTimePickTime)
                .onChange(of: time) { _, value in timeValue = FormsView.timeString(value) }
            ValueRow(label: L10n.tr("date_time_selected_time"), value: timeValue.isEmpty ? L10n.tr("value_not_set") : timeValue,
                     valueID: TestIDs.dateTimeTimeValue)
        }
        .screenTitle(L10n.tr("scenario_date_time"))
        .screenRoot(TestIDs.dateTimeScreen)
    }
}

// MARK: - File picker

struct FilePickerView: View {
    @State private var photoItem: PhotosPickerItem?
    @State private var importing = false
    @State private var name: String?
    @State private var type: String?
    @State private var size: Int?

    var body: some View {
        Form {
            KeyText("file_picker_intro")
            PhotosPicker(selection: $photoItem, matching: .images) { Text(L10n.tr("file_picker_image")) }
                .accessibilityIdentifier(TestIDs.filePickerPickImage)
            Button(L10n.tr("file_picker_document")) { importing = true }.accessibilityIdentifier(TestIDs.filePickerPickDocument)
            if let name {
                ValueRow(label: L10n.tr("file_picker_name"), value: name, valueID: TestIDs.filePickerResultName)
                ValueRow(label: L10n.tr("file_picker_type"), value: type ?? "", valueID: TestIDs.filePickerResultType)
                ValueRow(label: L10n.tr("file_picker_size"), value: size.map(String.init) ?? L10n.tr("value_unknown"),
                         valueID: TestIDs.filePickerResultSize)
                Button(L10n.tr("action_clear")) { self.name = nil; type = nil; size = nil }.accessibilityIdentifier(TestIDs.filePickerClear)
            } else {
                Text(L10n.tr("file_picker_empty")).accessibilityIdentifier(TestIDs.filePickerEmpty)
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item]) { result in
            guard case .success(let url) = result else { return }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let values = try? url.resourceValues(forKeys: [.fileSizeKey, .contentTypeKey])
            name = url.lastPathComponent
            type = values?.contentType?.preferredMIMEType ?? "application/octet-stream"
            size = values?.fileSize
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { @MainActor in
                let data = try? await item.loadTransferable(type: Data.self)
                let contentType = item.supportedContentTypes.first
                name = item.itemIdentifier ?? "image"
                type = contentType?.preferredMIMEType ?? "image/*"
                size = data?.count
            }
        }
        .screenTitle(L10n.tr("scenario_file_picker"))
        .screenRoot(TestIDs.filePickerScreen)
    }
}

// MARK: - Notifications

struct NotificationsView: View {
    @State private var status = "notifications_status_idle"
    @State private var permission = PermissionStatus.notRequested

    var body: some View {
        Form {
            KeyText("notifications_intro")
            ValueRow(label: L10n.tr("permission_notifications"), value: L10n.tr(permission.labelKey),
                valueID: TestIDs.notificationsPermissionStatus)
            if permission == .notRequested {
                Button(L10n.tr("permissions_request")) {
                    Task { @MainActor in
                        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
                        await refresh()
                    }
                }
                .accessibilityIdentifier(TestIDs.notificationsRequestPermission)
            }
            Button(L10n.tr("notifications_send_now")) { Task { await schedule(after: 1) } }
                .accessibilityIdentifier(TestIDs.notificationsSendNow)
            Button(L10n.tr("notifications_schedule")) { Task { await schedule(after: 5) } }
                .accessibilityIdentifier(TestIDs.notificationsSchedule)
            ValueRow(label: L10n.tr("notifications_status"), value: L10n.tr(status), valueID: TestIDs.notificationsStatus)
        }
        .screenTitle(L10n.tr("scenario_notifications"))
        .task { await refresh() }
        .screenRoot(TestIDs.notificationsScreen)
    }

    @MainActor
    private func refresh() async {
        permission = PermissionsModel.map(await UNUserNotificationCenter.current().notificationSettings().authorizationStatus)
    }

    /// iOS requires a trigger of at least one second, so "send now" uses one second.
    @MainActor
    private func schedule(after seconds: TimeInterval) async {
        await refresh()
        guard permission == .granted else {
            status = "notifications_status_blocked"
            return
        }
        let content = UNMutableNotificationContent()
        content.title = L10n.tr("notifications_title")
        content.body = L10n.tr("notifications_body")
        content.userInfo = ["link": "mtp://playground/scenario/notifications"]
        let request = UNNotificationRequest(identifier: "playground", content: content,
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false))
        do {
            try await UNUserNotificationCenter.current().add(request)
            status = seconds > 1 ? "notifications_status_scheduled" : "notifications_status_sent"
        } catch {
            status = "notifications_status_blocked"
        }
    }
}
