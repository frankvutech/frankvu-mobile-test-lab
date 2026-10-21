import SwiftUI

struct AuthScenarioView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router

    var body: some View {
        let session = env.auth.session
        Form {
            ValueRow(label: L10n.tr("auth_status"), value: L10n.tr(session != nil ? "auth_signed_in" : "auth_signed_out"),
                valueID: TestIDs.authStatus)
            ValueRow(label: L10n.tr("login_email"), value: session?.user.email ?? L10n.tr("value_none"), valueID: TestIDs.authUserEmail)
            ValueRow(label: L10n.tr("profile_role"), value: session?.user.role.rawValue ?? L10n.tr("value_none"),
                valueID: TestIDs.authUserRole)
            ValueRow(label: L10n.tr("login_remember_me"), value: L10n.tr(session?.rememberMe == true ? "value_on" : "value_off"),
                     valueID: TestIDs.authRememberState)
            Section(key: "auth_authorization") {
                KeyText("auth_authorization_hint").font(.footnote)
                Button(L10n.tr("admin_title")) { router.homePath.append(.admin) }.accessibilityIdentifier(TestIDs.authOpenAdmin)
                if session != nil {
                    Button(L10n.tr("profile_logout"), role: .destructive) { Task { await env.auth.endSession() } }
                        .accessibilityIdentifier(TestIDs.authLogout)
                }
            }
        }
        .screenTitle(L10n.tr("scenario_auth"))
        .screenRoot(TestIDs.authScreen)
    }
}

// MARK: - Forms

@Observable
final class FormsViewModel {
    var input = FormInput() {
        didSet {
            if showErrors { errors = FormValidator.validate(input) }
            if oldValue != input { submitted = false }
        }
    }
    private(set) var errors: [FormField: FieldError] = [:]
    private(set) var showErrors = false
    private(set) var submitted = false

    static let countries = ["de", "fr", "gb", "us"]

    private static func maxLength(_ field: FormField) -> Int {
        switch field {
        case .password: return FormValidator.passwordMax
        case .comments: return FormValidator.commentsMax
        default: return FormValidator.nameMax
        }
    }
    static let plans = ["basic", "pro", "enterprise"]

    func submit() {
        showErrors = true
        errors = FormValidator.validate(input)
        submitted = errors.isEmpty
    }

    func reset() {
        showErrors = false
        input = FormInput()
        errors = [:]
        submitted = false
    }

    func message(_ field: FormField) -> String? {
        guard let error = errors[field] else { return nil }
        switch error {
        case .required: return L10n.tr("error_required")
        case .invalidEmail: return L10n.tr("login_error_invalid_email")
        case .tooShort: return L10n.tr("error_too_short", field == .password ? FormValidator.passwordMin : FormValidator.nameMin)
        case .tooLong: return L10n.tr("error_too_long", Self.maxLength(field))
        case .notANumber: return L10n.tr("error_not_a_number")
        case .outOfRange: return L10n.tr("error_out_of_range", FormValidator.ageMin, FormValidator.ageMax)
        case .mustAccept: return L10n.tr("error_must_accept")
        }
    }
}

struct FormsView: View {
    @State private var model = FormsViewModel()
    @State private var pickedDate = DateMath.isoFormatter.date(from: DateMath.referenceDate) ?? Date()
    @State private var pickedTime = Calendar.current.date(bySettingHour: 9, minute: 30, second: 0, of: Date()) ?? Date()

    var body: some View {
        Form {
            field("form_full_name", TestIDs.formFullName, .fullName, text: $model.input.fullName)
            field("login_email", TestIDs.formEmail, .email, text: $model.input.email, keyboard: .emailAddress)
            Group {
                SecureField(L10n.tr("login_password"), text: $model.input.password).accessibilityIdentifier(TestIDs.formPassword)
                error(.password, TestIDs.formPassword)
            }
            field("form_age", TestIDs.formAge, .age, text: $model.input.age, keyboard: .numberPad)
            Picker(selection: $model.input.country) {
                Text(L10n.tr("value_not_set")).tag(String?.none)
                ForEach(FormsViewModel.countries, id: \.self) { code in
                    Text(L10n.tr("country_\(code)")).tag(String?.some(code)).accessibilityIdentifier(TestIDs.formCountryOption(code))
                }
            } label: {
                KeyText("form_country")
            }
            .accessibilityIdentifier(TestIDs.formCountry)
            error(.country, TestIDs.formCountry)

            Picker(L10n.tr("form_plan"), selection: $model.input.plan) {
                ForEach(FormsViewModel.plans, id: \.self) { plan in
                    Text(L10n.tr("plan_\(plan)")).tag(String?.some(plan)).accessibilityIdentifier(TestIDs.formPlan(plan))
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier(TestIDs.formPlan)
            error(.plan, TestIDs.formPlan)

            Toggle(L10n.tr("form_newsletter"), isOn: $model.input.newsletter).accessibilityIdentifier(TestIDs.formNewsletter)
            ValueRow(label: L10n.tr("form_satisfaction"), value: "\(model.input.satisfaction)", valueID: TestIDs.formSatisfactionValue)
            Slider(value: Binding(get: { Double(model.input.satisfaction) }, set: { model.input.satisfaction = Int($0.rounded()) }),
                   in: 0...10, step: 1)
                .accessibilityLabel(L10n.tr("form_satisfaction"))
                .accessibilityIdentifier(TestIDs.formSatisfaction)

            DatePicker(selection: $pickedDate, displayedComponents: .date) { KeyText("form_pick_date") }
                .accessibilityIdentifier(TestIDs.formDate)
                .onChange(of: pickedDate) { _, value in model.input.date = DateMath.isoFormatter.string(from: value) }
            HStack {
                Button(L10n.tr("form_pick_date")) { model.input.date = DateMath.isoFormatter.string(from: pickedDate) }
                    .accessibilityIdentifier(TestIDs.formDateConfirm)
                Spacer()
                Text(model.input.date ?? L10n.tr("value_not_set")).accessibilityIdentifier(TestIDs.formDateValue)
            }
            error(.date, TestIDs.formDate)
            DatePicker(selection: $pickedTime, displayedComponents: .hourAndMinute) { KeyText("form_pick_time") }
                .accessibilityIdentifier(TestIDs.formTime)
                .onChange(of: pickedTime) { _, value in model.input.time = Self.timeString(value) }
            ValueRow(label: L10n.tr("form_pick_time"), value: model.input.time ?? L10n.tr("value_not_set"), valueID: TestIDs.formTimeValue)

            Section {
                TextField(L10n.tr("form_comments"), text: $model.input.comments, axis: .vertical)
                    .lineLimit(3...6)
                    .accessibilityIdentifier(TestIDs.formComments)
                Text(L10n.tr("form_comments_counter", model.input.comments.count, FormValidator.commentsMax))
                    .font(.caption)
                    .accessibilityIdentifier(TestIDs.formCommentsCounter)
                error(.comments, TestIDs.formComments)
            }
            Toggle(L10n.tr("form_terms"), isOn: $model.input.acceptTerms).accessibilityIdentifier(TestIDs.formTerms)
            error(.terms, TestIDs.formTerms)

            Section {
                if model.showErrors && !model.errors.isEmpty {
                    ErrorLabel(text: L10n.plural("form_error_summary", model.errors.count), id: TestIDs.formErrorSummary)
                }
                if model.submitted {
                    Text(L10n.tr("form_success")).foregroundStyle(.tint).accessibilityIdentifier(TestIDs.formSuccess)
                }
                PrimaryButton(title: L10n.tr("form_submit"), id: TestIDs.formSubmit) { model.submit() }
                SecondaryButton(title: L10n.tr("form_reset"), id: TestIDs.formReset) { model.reset() }
            }
        }
        .screenTitle(L10n.tr("scenario_forms"))
        .scrollDismissesKeyboard(.interactively)
        .screenRoot(TestIDs.formsScreen)
    }

    private func field(_ key: String, _ id: String, _ formField: FormField, text: Binding<String>,
                       keyboard: UIKeyboardType = .default) -> some View {
        Group {
            TextField(L10n.tr(key), text: text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier(id)
            error(formField, id)
        }
    }

    @ViewBuilder private func error(_ field: FormField, _ id: String) -> some View {
        if let message = model.message(field) { ErrorLabel(text: message, id: TestIDs.formError(id)) }
    }

    static func timeString(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }
}

// MARK: - Controls

struct ControlsView: View {
    @SceneStorage("controls.tap") private var tapCount = 0
    @SceneStorage("controls.toggle") private var toggled = false
    @SceneStorage("controls.checked") private var checked = false
    @SceneStorage("controls.radio") private var radio = "small"
    @SceneStorage("controls.slider") private var slider = 50.0
    @SceneStorage("controls.stepper") private var stepper = 0
    @SceneStorage("controls.switch") private var switchOn = false
    @SceneStorage("controls.progress") private var progress = 0

    private func onOff(_ value: Bool) -> String { L10n.tr(value ? "value_on" : "value_off") }

    var body: some View {
        Form {
            Section(key: "controls_buttons") {
                PrimaryButton(title: L10n.tr("controls_primary"), id: TestIDs.controlsPrimaryButton) { tapCount += 1 }
                SecondaryButton(title: L10n.tr("controls_secondary"), id: TestIDs.controlsSecondaryButton) { tapCount += 1 }
                Button(L10n.tr("controls_disabled")) { tapCount += 1 }.disabled(true)
                    .accessibilityIdentifier(TestIDs.controlsDisabledButton)
                ValueRow(label: L10n.tr("controls_tap_count"), value: "\(tapCount)", valueID: TestIDs.controlsTapCount)
            }
            Section(key: "controls_toggles") {
                Button { toggled.toggle() } label: { Image(systemName: toggled ? "heart.fill" : "heart") }
                    .accessibilityLabel(L10n.tr("controls_favorite"))
                    .accessibilityValue(onOff(toggled))
                    .accessibilityAddTraits(toggled ? .isSelected : [])
                    .accessibilityIdentifier(TestIDs.controlsToggle)
                ValueRow(label: L10n.tr("controls_favorite"), value: onOff(toggled), valueID: TestIDs.controlsToggleState)
                Button { checked.toggle() } label: {
                    Label(L10n.tr("controls_checkbox"), systemImage: checked ? "checkmark.square.fill" : "square")
                }
                .accessibilityValue(onOff(checked))
                .accessibilityIdentifier(TestIDs.controlsCheckbox)
                ValueRow(label: L10n.tr("controls_checkbox"), value: onOff(checked), valueID: TestIDs.controlsCheckboxState)
                Toggle(L10n.tr("controls_switch"), isOn: $switchOn).accessibilityIdentifier(TestIDs.controlsSwitch)
                ValueRow(label: L10n.tr("controls_switch"), value: onOff(switchOn), valueID: TestIDs.controlsSwitchState)
            }
            Section(key: "controls_size") {
                ForEach(["small", "medium", "large"], id: \.self) { size in
                    Button { radio = size } label: {
                        Label(L10n.tr("size_\(size)"), systemImage: radio == size ? "largecircle.fill.circle" : "circle")
                    }
                    .accessibilityAddTraits(radio == size ? .isSelected : [])
                    .accessibilityIdentifier(TestIDs.controlsRadio(size))
                }
                ValueRow(label: L10n.tr("controls_size"), value: L10n.tr("size_\(radio)"), valueID: TestIDs.controlsRadioState)
            }
            Section(key: "controls_slider") {
                Slider(value: $slider, in: 0...100, step: 10)
                    .accessibilityLabel(L10n.tr("controls_slider"))
                    .accessibilityIdentifier(TestIDs.controlsSlider)
                ValueRow(label: L10n.tr("controls_slider"), value: "\(Int(slider))", valueID: TestIDs.controlsSliderValue)
            }
            Section(key: "controls_stepper") {
                Stepper(value: $stepper, in: 0...10) {
                    Text("\(stepper)").accessibilityIdentifier(TestIDs.controlsStepperValue)
                }
                .accessibilityIdentifier(TestIDs.controlsStepper)
            }
            Section(key: "controls_progress") {
                // Combined: otherwise the identifier lands on a wrapper and the indicator itself has none.
                ProgressView(value: Double(progress), total: 100)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier(TestIDs.controlsProgress)
                ValueRow(label: L10n.tr("controls_progress"), value: "\(progress)%", valueID: TestIDs.controlsProgressValue)
                Button(L10n.tr("controls_progress_advance")) { progress = min(progress + 25, 100) }
                    .accessibilityIdentifier(TestIDs.controlsProgressAdvance)
                Button(L10n.tr("action_reset")) { progress = 0 }.accessibilityIdentifier(TestIDs.controlsProgressReset)
                ProgressView().accessibilityElement(children: .combine).accessibilityIdentifier(TestIDs.controlsSpinner)
            }
        }
        .screenTitle(L10n.tr("scenario_controls"))
        .screenRoot(TestIDs.controlsScreen)
    }
}

// MARK: - Lists

struct ListsView: View {
    @SceneStorage("lists.tab") private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $tab) {
                Text(L10n.tr("lists_static")).tag(0).accessibilityIdentifier(TestIDs.listsTabStatic)
                Text(L10n.tr("lists_dynamic")).tag(1).accessibilityIdentifier(TestIDs.listsTabDynamic)
                Text(L10n.tr("lists_infinite")).tag(2).accessibilityIdentifier(TestIDs.listsTabInfinite)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier(TestIDs.listsTabs)
            .padding()
            switch tab {
            case 0: StaticListView()
            case 1: DynamicListView()
            default: InfiniteListView()
            }
        }
        .screenTitle(L10n.tr("scenario_lists"))
        .screenRoot(TestIDs.listsScreen)
    }
}

struct StaticListView: View {
    var body: some View {
        List(Array(SeedData.staticListItems.enumerated()), id: \.offset) { index, title in
            VStack(alignment: .leading) {
                Text(title)
                Text(L10n.tr("lists_static_subtitle", index + 1)).font(.caption)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(TestIDs.listItem(index + 1))
        }
        .accessibilityIdentifier(TestIDs.staticList)
    }
}

struct DynamicListView: View {
    @Environment(AppEnvironment.self) private var env
    @SceneStorage("dynamic.query") private var query = ""
    @SceneStorage("dynamic.sort") private var sort = SortOrder.byId.rawValue
    @State private var newTitle = ""
    @State private var editing: DynamicItem?
    @State private var editTitle = ""
    @State private var deleting: DynamicItem?

    var body: some View {
        let order = SortOrder(rawValue: sort) ?? .byId
        let items = DynamicListQuery.apply(env.dynamicItems.items, query: query, sort: order)
        List {
            Section {
                HStack {
                    TextField(L10n.tr("lists_new_item"), text: $newTitle).accessibilityIdentifier(TestIDs.dynamicListInput)
                    Button(L10n.tr("action_add")) {
                        if env.dynamicItems.add(newTitle) != nil { newTitle = "" }
                    }
                    .disabled(newTitle.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityIdentifier(TestIDs.dynamicListAdd)
                }
                TextField(L10n.tr("lists_search"), text: $query)
                    .textInputAutocapitalization(.never)
                    .accessibilityIdentifier(TestIDs.dynamicListSearch)
                Picker(L10n.tr("lists_sort_id"), selection: $sort) {
                    ForEach(SortOrder.allCases) { option in
                        Text(L10n.tr("lists_sort_\(option.rawValue)")).tag(option.rawValue)
                            .accessibilityIdentifier(TestIDs.dynamicListSort(option.rawValue))
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier(TestIDs.dynamicListSort)
                Text(L10n.tr("lists_count", items.count, env.dynamicItems.items.count)).accessibilityIdentifier(TestIDs.dynamicListCount)
                if items.isEmpty { Text(L10n.tr("lists_empty")).accessibilityIdentifier(TestIDs.dynamicListEmpty) }
            }
            Section {
                ForEach(items) { item in
                    HStack {
                        Text(item.title).accessibilityIdentifier(TestIDs.title(TestIDs.dynamicListItem(item.id)))
                        Spacer()
                        Button {
                            editTitle = item.title
                            editing = item
                        } label: { Image(systemName: "pencil") }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(L10n.tr("lists_edit", item.title))
                            .accessibilityIdentifier(TestIDs.dynamicListEdit(item.id))
                        Button { deleting = item } label: { Image(systemName: "trash") }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(L10n.tr("lists_delete", item.title))
                            .accessibilityIdentifier(TestIDs.dynamicListDelete(item.id))
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier(TestIDs.dynamicListItem(item.id))
                }
            }
        }
        .accessibilityIdentifier(TestIDs.dynamicList)
        .alert(L10n.tr("lists_edit_title"), isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            TextField("", text: $editTitle).accessibilityIdentifier(TestIDs.dynamicListEditInput)
            Button(L10n.tr("action_save")) {
                if let editing { env.dynamicItems.rename(id: editing.id, title: editTitle) }
                editing = nil
            }
            .accessibilityIdentifier(TestIDs.dialogConfirm)
            Button(L10n.tr("action_cancel"), role: .cancel) { editing = nil }.accessibilityIdentifier(TestIDs.dialogCancel)
        }
        .alert(L10n.tr("dialog_delete_title"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
               presenting: deleting) { item in
            Button(L10n.tr("action_delete"), role: .destructive) {
                env.dynamicItems.delete(id: item.id)
                deleting = nil
            }
            .accessibilityIdentifier(TestIDs.dialogConfirm)
            Button(L10n.tr("action_cancel"), role: .cancel) { deleting = nil }.accessibilityIdentifier(TestIDs.dialogCancel)
        } message: { item in
            Text(L10n.tr("dialog_delete_message", item.title))
        }
    }
}

@Observable
final class InfiniteListModel {
    private(set) var items: [PagedItem] = []
    private(set) var isLoading = false
    private(set) var endReached = false
    private var nextPage = 0

    @MainActor
    func loadMore(delay: TimeInterval) async {
        guard !isLoading, !endReached else { return }
        isLoading = true
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        let page = PagedItems.page(nextPage)
        nextPage += 1
        items += page.items
        endReached = !page.hasMore
        isLoading = false
    }
}

struct InfiniteListView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var model = InfiniteListModel()

    var body: some View {
        List {
            Text(L10n.plural("lists_loaded", model.items.count)).accessibilityIdentifier(TestIDs.infiniteCount)
            ForEach(model.items) { item in
                Text(item.title)
                    .accessibilityIdentifier(TestIDs.infiniteItem(item.index))
                    .onAppear {
                        if item.index >= model.items.count - 3 { Task { await model.loadMore(delay: env.timing.pageLoad) } }
                    }
            }
            if model.isLoading { ProgressView().accessibilityIdentifier(TestIDs.infiniteLoading) }
            if model.endReached { Text(L10n.tr("lists_end")).accessibilityIdentifier(TestIDs.infiniteEnd) }
        }
        .accessibilityIdentifier(TestIDs.infiniteList)
        .task { if model.items.isEmpty { await model.loadMore(delay: env.timing.pageLoad) } }
    }
}

// MARK: - Scrolling

struct ScrollingView: View {
    @State private var firstVisible = 0

    var body: some View {
        ScrollViewReader { proxy in
            VStack(spacing: 0) {
                HStack {
                    Button(L10n.tr("scrolling_top")) { withAnimation { proxy.scrollTo("top", anchor: .top) } }
                        .accessibilityIdentifier(TestIDs.scrollToTop)
                    Spacer()
                    Button(L10n.tr("scrolling_bottom")) { withAnimation { proxy.scrollTo(TestIDs.scrollBottomMarker, anchor: .bottom) } }
                        .accessibilityIdentifier(TestIDs.scrollToBottom)
                }
                .padding(.horizontal)
                Text(L10n.tr("scrolling_position", firstVisible)).accessibilityIdentifier(TestIDs.scrollPosition)
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ScrollView(.horizontal) {
                            LazyHStack {
                                ForEach(1...20, id: \.self) { number in
                                    Text(L10n.tr("scrolling_card", number))
                                        .frame(width: 120, height: 80)
                                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                                        .accessibilityIdentifier(TestIDs.scrollCard(number))
                                }
                            }
                        }
                        .accessibilityIdentifier(TestIDs.scrollHorizontal)
                        .id("top")
                        ForEach(1...40, id: \.self) { number in
                            Text(L10n.tr("scrolling_section", number))
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
                                .padding(.horizontal)
                                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                                .accessibilityIdentifier(TestIDs.scrollSection(number))
                                .onAppear { firstVisible = max(0, number - 3) }
                        }
                        Text(L10n.tr("scrolling_end")).padding().id(TestIDs.scrollBottomMarker)
                            .accessibilityIdentifier(TestIDs.scrollBottomMarker)
                    }
                    .padding()
                }
                .accessibilityIdentifier(TestIDs.scrollContainer)
            }
        }
        .screenTitle(L10n.tr("scenario_scrolling"))
        .screenRoot(TestIDs.scrollingScreen)
    }
}
