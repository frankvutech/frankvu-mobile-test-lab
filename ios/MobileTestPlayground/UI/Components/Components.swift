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
}

/// A label/value pair. The value carries the identifier so tests assert on exactly the state text.
struct ValueRow: View {
    let label: String
    let value: String
    let valueID: String

    var body: some View {
        HStack {
            Text(label)
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
