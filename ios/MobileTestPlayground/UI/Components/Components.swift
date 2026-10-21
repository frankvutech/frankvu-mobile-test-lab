import SwiftUI

/// Localized strings from Localizable.strings (generated from shared/strings/strings.json).
enum L10n {
    static func tr(_ key: String, _ args: CVarArg...) -> String {
        let format = NSLocalizedString(key, comment: "")
        return args.isEmpty ? format : String(format: format, locale: Locale.current, arguments: args)
    }

    static func plural(_ key: String, _ count: Int) -> String {
        String.localizedStringWithFormat(NSLocalizedString(key, comment: ""), count)
    }
}

extension View {
    /// Marks a screen root: a container element that keeps its children individually accessible,
    /// so XCUITest can assert `app.otherElements["forms_screen"]` and still reach every control.
    func screenRoot(_ id: String) -> some View {
        accessibilityElement(children: .contain).accessibilityIdentifier(id)
    }

    /// The screen title, shown in the navigation bar as an element with the `top_bar_title` identifier
    /// (the same as Android's app bar title). The system title stays set for VoiceOver and back buttons.
    func screenTitle(_ title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier(TestIDs.topBarTitle)
                }
            }
    }
}

/// A static text from the string table, identified `<key>_text` (TestIDs.text): headings, labels,
/// help text. Use it for every text that is not part of a control.
struct KeyText: View {
    let key: String
    var args: [CVarArg] = []

    init(_ key: String, _ args: CVarArg...) {
        self.key = key
        self.args = args
    }

    var body: some View {
        Text(args.isEmpty ? L10n.tr(key) : String(format: L10n.tr(key), locale: Locale.current, arguments: args))
            .accessibilityIdentifier(TestIDs.text(key))
    }
}

/// A form section heading from the string table, identified `<key>_text`.
struct SectionTitle: View {
    let key: String

    var body: some View {
        KeyText(key).accessibilityAddTraits(.isHeader)
    }
}

extension Section where Parent == SectionTitle, Footer == EmptyView, Content: View {
    /// A form section whose header is the string-table text `key`, identified `<key>_text`
    /// (`Section("title")` gives its header no way to carry an identifier).
    init(key: String, @ViewBuilder content: () -> Content) {
        self.init(content: content, header: { SectionTitle(key: key) })
    }
}

/// A label/value pair. The value carries the identifier so tests assert on exactly the state text;
/// the label is `<value id>_label` (as on Android).
struct ValueRow: View {
    let label: String
    let value: String
    let valueID: String

    var body: some View {
        HStack {
            Text(label).accessibilityIdentifier(TestIDs.label(valueID))
            Spacer()
            Text(value).accessibilityIdentifier(valueID)
        }
    }
}

/// A validation message, announced by VoiceOver when it appears.
struct ErrorLabel: View {
    let text: String
    let id: String

    var body: some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(.red)
            .accessibilityIdentifier(id)
            .accessibilityAddTraits(.updatesFrequently)
            .onAppear { UIAccessibility.post(notification: .announcement, argument: text) }
    }
}

struct PrimaryButton: View {
    let title: String
    let id: String
    var role: ButtonRole?
    let action: () -> Void

    var body: some View {
        Button(role: role, action: action) {
            Text(title).frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .accessibilityIdentifier(id)
    }
}

struct SecondaryButton: View {
    let title: String
    let id: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier(id)
    }
}
