import SwiftUI
import Observation

@Observable
final class LoginViewModel {
    var email = ""
    var password = ""
    var passwordVisible = false
    var rememberMe = false
    private(set) var isLoading = false
    private(set) var error: LoginError?

    func prefill(from env: AppEnvironment) {
        if email.isEmpty, let remembered = env.auth.rememberedEmail {
            email = remembered
            rememberMe = true
        }
    }

    @MainActor
    func submit(env: AppEnvironment) async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        // A fixed delay makes the loading state observable and identical on every run.
        try? await Task.sleep(nanoseconds: UInt64(env.timing.login * 1_000_000_000))
        switch await env.login(email: email, password: password, rememberMe: rememberMe) {
        case .success: password = ""
        case .failure(let failure): error = failure
        }
        isLoading = false
    }

    func clearError() { error = nil }
}

extension LoginError {
    var messageKey: String {
        switch self {
        case .emptyEmail: return "login_error_empty_email"
        case .invalidEmail: return "login_error_invalid_email"
        case .emptyPassword: return "login_error_empty_password"
        case .unknownUser: return "login_error_unknown_user"
        case .incorrectPassword: return "login_error_incorrect_password"
        case .accountLocked: return "login_error_locked"
        case .serviceUnavailable: return "data_error_unavailable"
        }
    }
}

struct LoginView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(Router.self) private var router
    @State private var model = LoginViewModel()
    let showRedirectMessage: Bool

    var body: some View {
        Form {
            Section {
                if showRedirectMessage {
                    Text(L10n.tr("login_redirect_message"))
                        .foregroundStyle(.tint)
                        .accessibilityIdentifier(TestIDs.loginRedirectMessage)
                }
                TextField(L10n.tr("login_email"), text: $model.email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier(TestIDs.loginEmail)
                    .onChange(of: model.email) { model.clearError() }
                HStack {
                    Group {
                        if model.passwordVisible {
                            TextField(L10n.tr("login_password"), text: $model.password)
                        } else {
                            SecureField(L10n.tr("login_password"), text: $model.password)
                        }
                    }
                    .textInputAutocapitalization(.never)
                    .accessibilityIdentifier(TestIDs.loginPassword)
                    .onSubmit { Task { await model.submit(env: env) } }
                    Button {
                        model.passwordVisible.toggle()
                    } label: {
                        Image(systemName: model.passwordVisible ? "eye.slash" : "eye")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(L10n.tr(model.passwordVisible ? "login_hide_password" : "login_show_password"))
                    .accessibilityIdentifier(TestIDs.loginPasswordToggle)
                }
                Toggle(L10n.tr("login_remember_me"), isOn: $model.rememberMe)
                    .accessibilityIdentifier(TestIDs.loginRememberMe)
                if let error = model.error {
                    ErrorLabel(text: L10n.tr(error.messageKey), id: TestIDs.loginError)
                }
            }
            Section {
                Button {
                    Task { await model.submit(env: env) }
                } label: {
                    HStack {
                        Spacer()
                        if model.isLoading {
                            ProgressView()
                                .accessibilityLabel(L10n.tr("login_loading"))
                                .accessibilityIdentifier(TestIDs.loginLoading)
                        } else {
                            Text(L10n.tr("login_button"))
                        }
                        Spacer()
                    }
                }
                .disabled(model.isLoading)
                .accessibilityIdentifier(TestIDs.loginButton)
                NavigationLink(L10n.tr("login_forgot_password")) { ForgotPasswordView() }
                    .accessibilityIdentifier(TestIDs.loginForgotPassword)
                NavigationLink(L10n.tr("login_register")) { RegisterView() }
                    .accessibilityIdentifier(TestIDs.loginRegister)
                if !env.testMode.settings.authenticationRequired && env.auth.session == nil {
                    Button(L10n.tr("login_continue_as_guest")) { router.guestMode = true }
                        .accessibilityIdentifier(TestIDs.loginContinueAsGuest)
                }
            } footer: {
                KeyText("login_hint")
            }
        }
        .screenTitle(L10n.tr("login_title"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink { SettingsView() } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel(L10n.tr("settings_title"))
                    .accessibilityIdentifier(TestIDs.loginSettings)
            }
        }
        .screenRoot(TestIDs.loginScreen)
        .onAppear { model.prefill(from: env) }
    }
}

extension PasswordPolicy.Violation {
    var messageKey: String {
        switch self {
        case .tooShort: return "password_too_short"
        case .tooLong: return "password_too_long"
        case .missingUppercase: return "password_missing_uppercase"
        case .missingDigit: return "password_missing_digit"
        case .missingSpecial: return "password_missing_special"
        }
    }
}

struct RegisterView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var error: RegisterError?
    @State private var registered = false

    var body: some View {
        Form {
            if registered {
                Text(L10n.tr("register_success")).accessibilityIdentifier(TestIDs.registerSuccess)
                Button(L10n.tr("register_back_to_login")) { dismiss() }
            } else {
                TextField(L10n.tr("register_name"), text: $name).accessibilityIdentifier(TestIDs.registerName)
                TextField(L10n.tr("login_email"), text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .accessibilityIdentifier(TestIDs.registerEmail)
                SecureField(L10n.tr("login_password"), text: $password).accessibilityIdentifier(TestIDs.registerPassword)
                SecureField(L10n.tr("register_confirm_password"), text: $confirm).accessibilityIdentifier(TestIDs.registerConfirmPassword)
                KeyText("password_rules").font(.footnote)
                if let error { ErrorLabel(text: message(error), id: TestIDs.registerError) }
                Button(L10n.tr("register_button")) {
                    Task { @MainActor in
                        switch await env.register(name: name, email: email, password: password, confirm: confirm) {
                        case .success: registered = true
                        case .failure(let failure): error = failure
                        }
                    }
                }
                .accessibilityIdentifier(TestIDs.registerButton)
            }
        }
        .screenTitle(L10n.tr("register_title"))
        .screenRoot(TestIDs.registerScreen)
    }

    private func message(_ error: RegisterError) -> String {
        switch error {
        case .emptyName: return L10n.tr("register_error_empty_name")
        case .emptyEmail: return L10n.tr("login_error_empty_email")
        case .invalidEmail: return L10n.tr("login_error_invalid_email")
        case .weakPassword(let violation): return L10n.tr(violation.messageKey)
        case .passwordsDoNotMatch: return L10n.tr("password_mismatch")
        case .emailAlreadyRegistered: return L10n.tr("register_error_email_taken")
        case .serviceUnavailable: return L10n.tr("data_error_unavailable")
        }
    }
}

struct ForgotPasswordView: View {
    @State private var email = ""
    @State private var invalid = false
    @State private var submitted = false

    var body: some View {
        Form {
            KeyText("forgot_instructions")
            TextField(L10n.tr("login_email"), text: $email)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier(TestIDs.forgotEmail)
            if invalid { ErrorLabel(text: L10n.tr("login_error_invalid_email"), id: TestIDs.forgotError) }
            if submitted {
                Text(L10n.tr("forgot_confirmation")).accessibilityIdentifier(TestIDs.forgotConfirmation)
                NavigationLink(L10n.tr("forgot_continue")) { ResetPasswordView(initialEmail: email) }
                    .accessibilityIdentifier(TestIDs.forgotContinue)
            } else {
                Button(L10n.tr("forgot_submit")) {
                    // Always succeeds for a well-formed email, so the screen never reveals registered emails.
                    invalid = !EmailValidator.isValid(email)
                    submitted = !invalid
                }
                .accessibilityIdentifier(TestIDs.forgotSubmit)
            }
        }
        .screenTitle(L10n.tr("forgot_title"))
        .screenRoot(TestIDs.forgotScreen)
    }
}

struct ResetPasswordView: View {
    @Environment(AppEnvironment.self) private var env
    @State private var email: String
    @State private var code = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var error: ResetPasswordError?
    @State private var done = false

    init(initialEmail: String) {
        _email = State(initialValue: initialEmail)
    }

    var body: some View {
        Form {
            if done {
                Text(L10n.tr("reset_success")).accessibilityIdentifier(TestIDs.resetSuccess)
            } else {
                KeyText("reset_instructions")
                TextField(L10n.tr("login_email"), text: $email)
                    .textInputAutocapitalization(.never)
                    .accessibilityIdentifier(TestIDs.resetEmail)
                TextField(L10n.tr("reset_code"), text: $code).keyboardType(.numberPad).accessibilityIdentifier(TestIDs.resetCode)
                SecureField(L10n.tr("reset_new_password"), text: $password).accessibilityIdentifier(TestIDs.resetPassword)
                SecureField(L10n.tr("register_confirm_password"), text: $confirm).accessibilityIdentifier(TestIDs.resetConfirmPassword)
                if let error { ErrorLabel(text: message(error), id: TestIDs.resetError) }
                Button(L10n.tr("reset_submit")) {
                    Task { @MainActor in
                        switch await env.resetPassword(email: email, code: code, password: password, confirm: confirm) {
                        case .success: done = true
                        case .failure(let failure): error = failure
                        }
                    }
                }
                .accessibilityIdentifier(TestIDs.resetSubmit)
            }
        }
        .screenTitle(L10n.tr("reset_title"))
        .screenRoot(TestIDs.resetScreen)
    }

    private func message(_ error: ResetPasswordError) -> String {
        switch error {
        case .invalidEmail: return L10n.tr("login_error_invalid_email")
        case .invalidCode: return L10n.tr("reset_error_code")
        case .weakPassword(let violation): return L10n.tr(violation.messageKey)
        case .passwordsDoNotMatch: return L10n.tr("password_mismatch")
        case .unknownUser: return L10n.tr("login_error_unknown_user")
        case .serviceUnavailable: return L10n.tr("data_error_unavailable")
        }
    }
}
