package com.mobiletestplayground.core.ui

/**
 * The automation-ID catalogue: every stable identifier the app exposes.
 *
 * Rules (see docs/test-ids.md): lowercase snake_case, independent of visible text and locale,
 * unique among simultaneously visible elements, and identical to the iOS `TestIDs` catalogue
 * wherever the element exists on both platforms. The root composable sets
 * `testTagsAsResourceId = true`, so UIAutomator finds these as resource ids.
 */
@Suppress("TooManyFunctions") // A catalogue: one accessor per repeated element.
object TestTags {
    // --- Derived IDs (rules shared with iOS) ---
    /** Static text from the shared string table (headings, labels, help text): `<string key>_text`. */
    fun text(key: String) = "${key}_text"

    /** A screen's scroll container: the screen ID without `_screen`, plus `_scroll`. */
    fun scroll(screen: String) = "${screen}_scroll"

    /** The label of a label/value row, next to the value it describes. */
    fun label(valueTag: String) = "${valueTag}_label"

    /** The visible title inside a tagged, non-clickable container (a card, a section, a target). */
    fun title(containerTag: String) = "${containerTag}_title"

    // --- App chrome ---
    const val TOP_BAR_BACK = "top_bar_back"
    const val TOP_BAR_TITLE = "top_bar_title"
    const val BOTTOM_NAV_HOME = "bottom_nav_home"
    const val BOTTOM_NAV_CATALOG = "bottom_nav_catalog"
    const val BOTTOM_NAV_CART = "bottom_nav_cart"
    const val BOTTOM_NAV_PROFILE = "bottom_nav_profile"
    const val APP_LOADING = "app_loading"

    // --- Login ---
    const val LOGIN_SCREEN = "login_screen"
    const val LOGIN_EMAIL = "login_email"
    const val LOGIN_PASSWORD = "login_password"
    const val LOGIN_PASSWORD_TOGGLE = "login_password_toggle"
    const val LOGIN_REMEMBER_ME = "login_remember_me"
    const val LOGIN_BUTTON = "login_button"
    const val LOGIN_FORGOT_PASSWORD = "login_forgot_password"
    const val LOGIN_REGISTER = "login_register"
    const val LOGIN_ERROR = "login_error"
    const val LOGIN_LOADING = "login_loading"
    const val LOGIN_SETTINGS = "login_settings"
    const val LOGIN_REDIRECT_MESSAGE = "login_redirect_message"
    const val LOGIN_CONTINUE_AS_GUEST = "login_continue_as_guest"

    // --- Register ---
    const val REGISTER_SCREEN = "register_screen"
    const val REGISTER_NAME = "register_name"
    const val REGISTER_EMAIL = "register_email"
    const val REGISTER_PASSWORD = "register_password"
    const val REGISTER_CONFIRM_PASSWORD = "register_confirm_password"
    const val REGISTER_BUTTON = "register_button"
    const val REGISTER_ERROR = "register_error"
    const val REGISTER_SUCCESS = "register_success"

    // --- Forgot / reset password ---
    const val FORGOT_SCREEN = "forgot_password_screen"
    const val FORGOT_EMAIL = "forgot_email"
    const val FORGOT_SUBMIT = "forgot_submit"
    const val FORGOT_ERROR = "forgot_error"
    const val FORGOT_CONFIRMATION = "forgot_confirmation"
    const val FORGOT_CONTINUE = "forgot_continue"
    const val RESET_SCREEN = "reset_password_screen"
    const val RESET_EMAIL = "reset_email"
    const val RESET_CODE = "reset_code"
    const val RESET_PASSWORD = "reset_password"
    const val RESET_CONFIRM_PASSWORD = "reset_confirm_password"
    const val RESET_SUBMIT = "reset_submit"
    const val RESET_ERROR = "reset_error"
    const val RESET_SUCCESS = "reset_success"
    const val RESET_BACK_TO_LOGIN = "reset_back_to_login"

    // --- Home / dashboard ---
    const val HOME_SCREEN = "home_screen"
    const val HOME_WELCOME = "home_welcome"
    const val HOME_SEARCH = "home_search"
    const val HOME_CART = "home_cart"
    const val HOME_PROFILE = "home_profile"
    const val HOME_SETTINGS = "home_settings"
    const val HOME_SCENARIO_LIST = "home_scenario_list"
    const val HOME_EMPTY_SEARCH = "home_empty_search"
    fun scenario(id: String) = "scenario_$id"

    // --- Catalog, product, cart, profile ---
    const val CATALOG_SCREEN = "catalog_screen"
    const val CATALOG_LIST = "catalog_list"
    const val CATALOG_LOADING = "catalog_loading"
    const val CATALOG_ERROR = "catalog_error"
    const val CATALOG_ACTION_ERROR = "catalog_action_error"
    const val PRODUCT_LOADING = "product_loading"
    const val PRODUCT_ERROR = "product_error"
    const val CART_ERROR = "cart_error"

    /** Retry after a failed load (catalog, product). */
    const val DATA_RETRY = "data_retry"
    fun product(productId: String) = productId
    fun productAddToCart(productId: String) = "${productId}_add_to_cart"
    const val PRODUCT_DETAIL_SCREEN = "product_detail_screen"
    const val PRODUCT_NAME = "product_name"
    const val PRODUCT_PRICE = "product_price"
    const val PRODUCT_CATEGORY = "product_category"
    const val PRODUCT_ADD_TO_CART = "product_add_to_cart"
    const val PRODUCT_NOT_FOUND = "product_not_found"
    const val CART_SCREEN = "cart_screen"
    const val CART_BADGE = "cart_badge"
    const val CART_EMPTY = "cart_empty"
    const val CART_TOTAL = "cart_total"
    const val CART_CHECKOUT_BUTTON = "cart_checkout_button"
    fun cartItem(productId: String) = "cart_item_$productId"
    fun cartItemQuantity(productId: String) = "cart_item_quantity_$productId"
    fun cartRemove(productId: String) = "cart_remove_$productId"
    const val ORDER_CONFIRMATION = "order_confirmation"
    const val ORDER_NUMBER = "order_number"
    const val PROFILE_SCREEN = "profile_screen"
    const val PROFILE_NAME = "profile_name"
    const val PROFILE_EMAIL = "profile_email"
    const val PROFILE_ROLE = "profile_role"
    const val PROFILE_LOGOUT = "profile_logout"
    const val PROFILE_ADMIN = "profile_admin"
    const val PROFILE_SETTINGS = "profile_settings"

    // --- Authorization ---
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_PRODUCTS = "admin_products"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_OPEN_PRODUCTS = "admin_open_products"
    const val ADMIN_OPEN_USERS = "admin_open_users"
    fun adminProduct(productId: String) = "admin_product_$productId"
    fun adminUser(userId: String) = "admin_$userId"
    const val ACCESS_DENIED_SCREEN = "access_denied_screen"
    const val ACCESS_DENIED_MESSAGE = "access_denied_message"
    const val ACCESS_DENIED_BACK = "access_denied_back"

    // --- Authentication scenario ---
    const val AUTH_SCREEN = "auth_scenario_screen"
    const val AUTH_STATUS = "auth_status"
    const val AUTH_USER_EMAIL = "auth_user_email"
    const val AUTH_USER_ROLE = "auth_user_role"
    const val AUTH_REMEMBER_STATE = "auth_remember_state"
    const val AUTH_LOGOUT = "auth_logout"
    const val AUTH_OPEN_ADMIN = "auth_open_admin"

    // --- Settings / Test Mode ---
    const val SETTINGS_SCREEN = "settings_screen"
    fun settingsTheme(mode: String) = "settings_theme_$mode"
    const val SETTINGS_TEST_MODE = "settings_test_mode"
    const val SETTINGS_TEST_MODE_UNAVAILABLE = "settings_test_mode_unavailable"
    const val SETTINGS_VERSION = "settings_version"
    const val TEST_MODE_SCREEN = "test_mode_screen"
    const val TEST_MODE_DISABLED = "test_mode_disabled"
    const val TEST_MODE_RESET_APP = "test_mode_reset_app"
    const val TEST_MODE_CLEAR_STORAGE = "test_mode_clear_storage"
    const val TEST_MODE_RESET_DATA = "test_mode_reset_data"
    const val TEST_MODE_GENERATE_DATA = "test_mode_generate_data"
    const val TEST_MODE_NETWORK_ERROR = "test_mode_network_error"
    const val TEST_MODE_SLOW_NETWORK = "test_mode_slow_network"
    const val TEST_MODE_AUTH_REQUIRED = "test_mode_auth_required"
    const val TEST_MODE_STATUS = "test_mode_status"
    const val TEST_MODE_USE_BACKEND = "test_mode_use_backend"
    const val TEST_MODE_BACKEND_URL = "test_mode_backend_url"
    const val TEST_MODE_BACKEND_URL_SAVE = "test_mode_backend_url_save"
    const val TEST_MODE_BACKEND_ACTIVE_URL = "test_mode_backend_active_url"
    const val SETTINGS_DATA_SOURCE = "settings_data_source"

    // --- Forms ---
    const val FORMS_SCREEN = "forms_screen"
    const val FORM_FULL_NAME = "form_full_name"
    const val FORM_EMAIL = "form_email"
    const val FORM_PASSWORD = "form_password"
    const val FORM_AGE = "form_age"
    const val FORM_COUNTRY = "form_country"
    fun formCountryOption(code: String) = "form_country_option_$code"
    const val FORM_PLAN = "form_plan"
    fun formPlan(plan: String) = "form_plan_$plan"
    const val FORM_TERMS = "form_terms"
    const val FORM_NEWSLETTER = "form_newsletter"
    const val FORM_SATISFACTION = "form_satisfaction"
    const val FORM_SATISFACTION_VALUE = "form_satisfaction_value"
    const val FORM_DATE = "form_date"
    const val FORM_DATE_VALUE = "form_date_value"

    /** iOS only: applies the inline date picker's value (Android confirms in the picker dialog). */
    const val FORM_DATE_CONFIRM = "form_date_confirm"
    const val FORM_TIME = "form_time"
    const val FORM_TIME_VALUE = "form_time_value"
    const val FORM_COMMENTS = "form_comments"
    const val FORM_COMMENTS_COUNTER = "form_comments_counter"
    const val FORM_SUBMIT = "form_submit"
    const val FORM_RESET = "form_reset"
    const val FORM_SUCCESS = "form_success"
    const val FORM_ERROR_SUMMARY = "form_error_summary"

    /** Error label for a form field, e.g. `form_email_error`. */
    fun formError(fieldTag: String) = "${fieldTag}_error"

    // --- Buttons and controls ---
    const val CONTROLS_SCREEN = "controls_screen"
    const val CONTROLS_PRIMARY_BUTTON = "controls_primary_button"
    const val CONTROLS_SECONDARY_BUTTON = "controls_secondary_button"
    const val CONTROLS_DISABLED_BUTTON = "controls_disabled_button"
    const val CONTROLS_TAP_COUNT = "controls_tap_count"
    const val CONTROLS_TOGGLE = "controls_toggle"
    const val CONTROLS_TOGGLE_STATE = "controls_toggle_state"
    const val CONTROLS_CHECKBOX = "controls_checkbox"
    const val CONTROLS_CHECKBOX_STATE = "controls_checkbox_state"
    fun controlsRadio(option: String) = "controls_radio_$option"
    const val CONTROLS_RADIO_STATE = "controls_radio_state"
    const val CONTROLS_SLIDER = "controls_slider"
    const val CONTROLS_SLIDER_VALUE = "controls_slider_value"
    const val CONTROLS_STEPPER_DECREMENT = "controls_stepper_decrement"
    const val CONTROLS_STEPPER_INCREMENT = "controls_stepper_increment"
    const val CONTROLS_STEPPER_VALUE = "controls_stepper_value"

    /** iOS only: the native Stepper (Android uses the separate increment/decrement buttons). */
    const val CONTROLS_STEPPER = "controls_stepper"
    const val CONTROLS_SWITCH = "controls_switch"
    const val CONTROLS_SWITCH_STATE = "controls_switch_state"
    const val CONTROLS_PROGRESS = "controls_progress"
    const val CONTROLS_PROGRESS_VALUE = "controls_progress_value"
    const val CONTROLS_PROGRESS_ADVANCE = "controls_progress_advance"
    const val CONTROLS_PROGRESS_RESET = "controls_progress_reset"
    const val CONTROLS_SPINNER = "controls_spinner"

    // --- Lists ---
    const val LISTS_SCREEN = "lists_screen"
    const val LISTS_TAB_STATIC = "lists_tab_static"
    const val LISTS_TAB_DYNAMIC = "lists_tab_dynamic"
    const val LISTS_TAB_INFINITE = "lists_tab_infinite"
    const val STATIC_LIST = "static_list"

    /** `list_item_001` … `list_item_030`. */
    fun listItem(number: Int) = "list_item_" + number.toString().padStart(3, '0')
    const val DYNAMIC_LIST = "dynamic_list"
    const val DYNAMIC_LIST_INPUT = "dynamic_list_input"
    const val DYNAMIC_LIST_ADD = "dynamic_list_add"
    const val DYNAMIC_LIST_SEARCH = "dynamic_list_search"
    fun dynamicListSort(order: String) = "dynamic_list_sort_$order"
    const val DYNAMIC_LIST_COUNT = "dynamic_list_count"
    const val DYNAMIC_LIST_EMPTY = "dynamic_list_empty"
    fun dynamicListItem(id: Int) = "dynamic_list_item_$id"
    fun dynamicListEdit(id: Int) = "dynamic_list_edit_$id"
    fun dynamicListDelete(id: Int) = "dynamic_list_delete_$id"
    const val DYNAMIC_LIST_EDIT_DIALOG = "dynamic_list_edit_dialog"
    const val DYNAMIC_LIST_EDIT_INPUT = "dynamic_list_edit_input"
    const val INFINITE_LIST = "infinite_list"

    /** `infinite_item_001` … `infinite_item_100`. */
    fun infiniteItem(index: Int) = "infinite_item_" + index.toString().padStart(3, '0')
    const val INFINITE_LOADING = "infinite_loading"
    const val INFINITE_END = "infinite_end"
    const val INFINITE_COUNT = "infinite_count"

    // --- Scrolling ---
    const val SCROLLING_SCREEN = "scrolling_screen"
    const val SCROLL_CONTAINER = "scroll_container"
    fun scrollSection(number: Int) = "scroll_section_" + number.toString().padStart(2, '0')
    const val SCROLL_TO_TOP = "scroll_to_top"
    const val SCROLL_TO_BOTTOM = "scroll_to_bottom"
    const val SCROLL_BOTTOM_MARKER = "scroll_bottom_marker"
    const val SCROLL_HORIZONTAL = "scroll_horizontal"
    fun scrollCard(number: Int) = "scroll_card_" + number.toString().padStart(2, '0')
    const val SCROLL_POSITION = "scroll_position"

    // --- Gestures ---
    const val GESTURES_SCREEN = "gestures_screen"
    const val GESTURE_RESULT = "gesture_result"
    const val GESTURE_TAP_TARGET = "gesture_tap_target"
    const val GESTURE_DOUBLE_TAP_TARGET = "gesture_double_tap_target"
    const val GESTURE_LONG_PRESS_TARGET = "gesture_long_press_target"
    const val GESTURE_SWIPE_AREA = "gesture_swipe_area"
    const val GESTURE_DRAG_ITEM = "gesture_drag_item"
    const val GESTURE_DROP_ZONE = "gesture_drop_zone"
    const val GESTURE_DROP_STATUS = "gesture_drop_status"
    const val GESTURE_ZOOM_AREA = "gesture_zoom_area"
    const val GESTURE_ZOOM_LEVEL = "gesture_zoom_level"
    const val GESTURE_ZOOM_IN = "gesture_zoom_in"
    const val GESTURE_ZOOM_OUT = "gesture_zoom_out"
    const val GESTURE_PAN_AREA = "gesture_pan_area"
    const val GESTURE_PAN_OFFSET = "gesture_pan_offset"
    const val GESTURE_RESET = "gesture_reset"

    // --- Navigation ---
    const val NAVIGATION_SCREEN = "navigation_screen"
    const val NAV_PUSH_DETAIL = "nav_push_detail"
    const val NAV_OPEN_MODAL = "nav_open_modal"
    const val NAV_OPEN_NESTED = "nav_open_nested"
    const val NAV_COUNTER_VALUE = "nav_counter_value"
    const val NAV_COUNTER_INCREMENT = "nav_counter_increment"
    const val NAV_DETAIL_SCREEN = "nav_detail_screen"
    const val NAV_DETAIL_LEVEL = "nav_detail_level"
    const val NAV_DETAIL_PUSH_NEXT = "nav_detail_push_next"
    const val NAV_DETAIL_POP_TO_ROOT = "nav_detail_pop_to_root"
    const val NAV_MODAL_SCREEN = "nav_modal_screen"
    const val NAV_MODAL_CLOSE = "nav_modal_close"
    const val NAV_NESTED_SCREEN = "nav_nested_screen"
    fun navNestedTab(tab: String) = "nav_nested_tab_$tab"
    const val NAV_NESTED_CONTENT = "nav_nested_content"

    // --- Dialogs ---
    const val DIALOGS_SCREEN = "dialogs_screen"
    const val DIALOGS_OPEN_CONFIRMATION = "dialogs_open_confirmation"
    const val DIALOGS_OPEN_INFORMATION = "dialogs_open_information"
    const val DIALOGS_OPEN_WARNING = "dialogs_open_warning"
    const val DIALOGS_OPEN_ERROR = "dialogs_open_error"
    const val DIALOGS_OPEN_ACTION_SHEET = "dialogs_open_action_sheet"
    const val DIALOGS_OPEN_BOTTOM_SHEET = "dialogs_open_bottom_sheet"
    const val DIALOGS_SHOW_SNACKBAR = "dialogs_show_snackbar"
    const val DIALOGS_RESULT = "dialogs_result"
    const val DIALOG_CONFIRMATION = "dialog_confirmation"
    const val DIALOG_INFORMATION = "dialog_information"
    const val DIALOG_WARNING = "dialog_warning"
    const val DIALOG_ERROR = "dialog_error"
    const val DIALOG_CONFIRM = "dialog_confirm"
    const val DIALOG_CANCEL = "dialog_cancel"
    const val DIALOG_OK = "dialog_ok"
    const val ACTION_SHEET = "action_sheet"
    fun actionSheetOption(option: String) = "action_sheet_option_$option"
    const val ACTION_SHEET_CANCEL = "action_sheet_cancel"
    const val BOTTOM_SHEET = "bottom_sheet"
    const val BOTTOM_SHEET_CLOSE = "bottom_sheet_close"
    const val SNACKBAR = "snackbar"

    // --- Permissions ---
    const val PERMISSIONS_SCREEN = "permissions_screen"
    fun permissionStatus(permission: String) = "permission_${permission}_status"
    fun permissionRequest(permission: String) = "permission_${permission}_request"
    const val PERMISSION_OPEN_SETTINGS = "permission_open_settings"

    // --- WebView ---
    const val WEBVIEW_SCREEN = "webview_screen"
    const val WEBVIEW = "webview"
    const val WEBVIEW_MESSAGE = "webview_message"
    const val WEBVIEW_SEND_TO_WEB = "webview_send_to_web"
    const val WEBVIEW_RELOAD = "webview_reload"

    // --- Network ---
    const val NETWORK_SCREEN = "network_screen"
    fun networkScenario(id: String) = "network_scenario_$id"
    const val NETWORK_IDLE = "network_idle"
    const val NETWORK_LOADING = "network_loading"
    const val NETWORK_SUCCESS = "network_success"
    const val NETWORK_EMPTY = "network_empty"
    const val NETWORK_ERROR = "network_error"
    const val NETWORK_ERROR_MESSAGE = "network_error_message"
    const val NETWORK_RETRY = "network_retry"
    fun networkResultItem(productId: String) = "network_result_$productId"

    // --- Accessibility ---
    const val ACCESSIBILITY_SCREEN = "accessibility_screen"
    const val A11Y_HEADING = "a11y_heading"
    const val A11Y_FAVORITE = "a11y_favorite"
    const val A11Y_MERGED_CARD = "a11y_merged_card"
    const val A11Y_CUSTOM_ACTION_ITEM = "a11y_custom_action_item"
    const val A11Y_CUSTOM_ACTION_RESULT = "a11y_custom_action_result"
    const val A11Y_LIVE_REGION = "a11y_live_region"
    const val A11Y_LIVE_REGION_TRIGGER = "a11y_live_region_trigger"
    const val A11Y_LARGE_TARGET = "a11y_large_target"
    const val A11Y_INFORMATIVE_IMAGE = "a11y_informative_image"
    const val A11Y_DECORATIVE_IMAGE = "a11y_decorative_image"

    // --- Error handling ---
    const val ERRORS_SCREEN = "errors_screen"
    const val ERRORS_TRIGGER_NETWORK = "errors_trigger_network"
    const val ERRORS_TRIGGER_UNEXPECTED = "errors_trigger_unexpected"
    const val ERRORS_TRIGGER_EMPTY = "errors_trigger_empty"
    const val ERRORS_TRIGGER_RECOVERABLE = "errors_trigger_recoverable"
    const val ERRORS_STATE = "errors_state"
    const val ERRORS_LOADING = "errors_loading"
    const val ERRORS_MESSAGE = "errors_message"
    const val ERRORS_RETRY = "errors_retry"
    const val ERRORS_DISMISS = "errors_dismiss"
    const val ERRORS_INLINE_INPUT = "errors_inline_input"
    const val ERRORS_INLINE_ERROR = "errors_inline_error"

    // --- Dynamic content ---
    const val DYNAMIC_CONTENT_SCREEN = "dynamic_content_screen"
    const val DYNAMIC_CONTENT_LOAD = "dynamic_content_load"
    const val DYNAMIC_CONTENT_LOADING = "dynamic_content_loading"
    const val DYNAMIC_CONTENT_LOADED = "dynamic_content_loaded"
    const val DYNAMIC_CONTENT_TOGGLE = "dynamic_content_toggle"
    const val DYNAMIC_CONTENT_TOGGLE_TARGET = "dynamic_content_toggle_target"
    const val DYNAMIC_CONTENT_DELAYED_BUTTON = "dynamic_content_delayed_button"
    const val DYNAMIC_CONTENT_DELAYED_STATUS = "dynamic_content_delayed_status"
    const val DYNAMIC_CONTENT_COUNTDOWN_START = "dynamic_content_countdown_start"
    const val DYNAMIC_CONTENT_COUNTDOWN_VALUE = "dynamic_content_countdown_value"
    const val DYNAMIC_CONTENT_CHANGE_TEXT = "dynamic_content_change_text"
    const val DYNAMIC_CONTENT_TEXT = "dynamic_content_text"

    // --- Test data ---
    const val TEST_DATA_SCREEN = "test_data_screen"
    fun testDataUser(userId: String) = "test_data_$userId"
    fun testDataProduct(productId: String) = "test_data_$productId"
    const val TEST_DATA_RESET = "test_data_reset"
    const val TEST_DATA_GENERATE = "test_data_generate"
    const val TEST_DATA_STATUS = "test_data_status"
    const val TEST_DATA_ITEM_COUNT = "test_data_item_count"

    // --- Deep links ---
    const val DEEP_LINKS_SCREEN = "deep_links_screen"
    fun deepLinkExample(name: String) = "deep_link_example_$name"
    const val DEEP_LINK_INPUT = "deep_link_input"
    const val DEEP_LINK_OPEN = "deep_link_open"
    const val DEEP_LINK_RESULT = "deep_link_result"

    // --- Date and time ---
    const val DATE_TIME_SCREEN = "date_time_screen"
    const val DATE_TIME_REFERENCE = "date_time_reference"
    const val DATE_TIME_PICK_DATE = "date_time_pick_date"
    const val DATE_TIME_DATE_VALUE = "date_time_date_value"
    const val DATE_TIME_DAYS_FROM_REFERENCE = "date_time_days_from_reference"
    const val DATE_TIME_PICK_TIME = "date_time_pick_time"
    const val DATE_TIME_TIME_VALUE = "date_time_time_value"
    const val DATE_PICKER_DIALOG = "date_picker_dialog"
    const val DATE_PICKER_CONFIRM = "date_picker_confirm"
    const val DATE_PICKER_CANCEL = "date_picker_cancel"
    const val TIME_PICKER_DIALOG = "time_picker_dialog"
    const val TIME_PICKER_CONFIRM = "time_picker_confirm"
    const val TIME_PICKER_CANCEL = "time_picker_cancel"

    // --- File and image picker ---
    const val FILE_PICKER_SCREEN = "file_picker_screen"
    const val FILE_PICKER_PICK_IMAGE = "file_picker_pick_image"
    const val FILE_PICKER_PICK_DOCUMENT = "file_picker_pick_document"
    const val FILE_PICKER_EMPTY = "file_picker_empty"
    const val FILE_PICKER_RESULT_NAME = "file_picker_result_name"
    const val FILE_PICKER_RESULT_TYPE = "file_picker_result_type"
    const val FILE_PICKER_RESULT_SIZE = "file_picker_result_size"
    const val FILE_PICKER_CLEAR = "file_picker_clear"

    // --- Notifications ---
    const val NOTIFICATIONS_SCREEN = "notifications_screen"
    const val NOTIFICATIONS_PERMISSION_STATUS = "notifications_permission_status"
    const val NOTIFICATIONS_REQUEST_PERMISSION = "notifications_request_permission"
    const val NOTIFICATIONS_SEND_NOW = "notifications_send_now"
    const val NOTIFICATIONS_SCHEDULE = "notifications_schedule"
    const val NOTIFICATIONS_STATUS = "notifications_status"
}
