import SwiftUI

// MARK: - Gestures

struct GesturesView: View {
    @SceneStorage("gestures.last") private var lastGesture = ""
    @SceneStorage("gestures.zoom") private var zoom = 1.0
    @SceneStorage("gestures.dropped") private var dropped = false
    @State private var pan = CGSize.zero
    @State private var drag = CGSize.zero
    @State private var dropFrame = CGRect.zero

    private func record(_ type: GestureType) { lastGesture = type.rawValue }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(lastGesture.isEmpty ? L10n.tr("gesture_none") : L10n.tr("gesture_detected", lastGesture))
                    .font(.headline)
                    .accessibilityIdentifier(TestIDs.gestureResult)
                HStack {
                    target("gesture_tap", TestIDs.gestureTapTarget).onTapGesture { record(.tap) }
                        .accessibilityAction { record(.tap) }
                    target("gesture_double_tap", TestIDs.gestureDoubleTapTarget).onTapGesture(count: 2) { record(.doubleTap) }
                        .accessibilityAction { record(.doubleTap) }
                    target("gesture_long_press", TestIDs.gestureLongPressTarget).onLongPressGesture { record(.longPress) }
                        .accessibilityAction(named: L10n.tr("gesture_long_press")) { record(.longPress) }
                }
                KeyText("gesture_swipe").font(.headline)
                Text(L10n.tr("gesture_swipe_here")).accessibilityIdentifier(TestIDs.title(TestIDs.gestureSwipeArea))
                    .frame(maxWidth: .infinity, minHeight: 140)
                    .background(Color.secondary.opacity(0.15))
                    .gesture(DragGesture(minimumDistance: 20).onEnded { value in
                        if let type = SwipeClassifier.classify(dx: value.translation.width, dy: value.translation.height, threshold: 48) {
                            record(type)
                        }
                    })
                    .accessibilityElement()
                    .accessibilityLabel(L10n.tr("gesture_swipe_here"))
                    .accessibilityAction(named: L10n.tr("gesture_swipe_left")) { record(.swipeLeft) }
                    .accessibilityAction(named: L10n.tr("gesture_swipe_right")) { record(.swipeRight) }
                    .accessibilityIdentifier(TestIDs.gestureSwipeArea)

                KeyText("gesture_drag_and_drop").font(.headline)
                Text(L10n.tr(dropped ? "gesture_dropped" : "gesture_not_dropped")).accessibilityIdentifier(TestIDs.gestureDropStatus)
                HStack {
                    Text(L10n.tr("gesture_drag_me"))
                        .frame(width: 72, height: 72)
                        .background(Color.orange)
                        .offset(drag)
                        .gesture(DragGesture(coordinateSpace: .named("gestures")).onChanged { drag = $0.translation }.onEnded { value in
                            dropped = dropFrame.contains(value.location)
                            record(.dragAndDrop)
                            drag = .zero
                        })
                        .accessibilityIdentifier(TestIDs.gestureDragItem)
                    Spacer()
                    Text(L10n.tr("gesture_drop_here"))
                        .frame(width: 110, height: 110)
                        .border(Color.accentColor, width: 2)
                        .background(GeometryReader { geo in
                            Color.clear.onAppear { dropFrame = geo.frame(in: .named("gestures")) }
                        })
                        .accessibilityIdentifier(TestIDs.gestureDropZone)
                }

                KeyText("gesture_zoom").font(.headline)
                ValueRow(label: L10n.tr("gesture_zoom"), value: L10n.tr("gesture_zoom_level", ZoomRules.label(zoom)),
                    valueID: TestIDs.gestureZoomLevel)
                Text(L10n.tr("gesture_pinch_here"))
                    .scaleEffect(zoom)
                    .frame(maxWidth: .infinity, minHeight: 140)
                    .clipped()
                    .border(Color.secondary)
                    .gesture(MagnifyGesture().onChanged { value in
                        zoom = ZoomRules.clamp(value.magnification)
                        record(.pinch)
                    })
                    .accessibilityIdentifier(TestIDs.gestureZoomArea)
                HStack {
                    Button(L10n.tr("gesture_zoom_in")) { zoom = ZoomRules.clamp(zoom + 1); record(.pinch) }
                        .accessibilityIdentifier(TestIDs.gestureZoomIn)
                    Button(L10n.tr("gesture_zoom_out")) { zoom = ZoomRules.clamp(zoom - 1); record(.pinch) }
                        .accessibilityIdentifier(TestIDs.gestureZoomOut)
                }

                KeyText("gesture_pan").font(.headline)
                ValueRow(label: L10n.tr("gesture_pan"), value: "\(Int(pan.width)), \(Int(pan.height))", valueID: TestIDs.gesturePanOffset)
                ZStack(alignment: .topLeading) {
                    Color.secondary.opacity(0.1)
                    Rectangle().fill(Color.teal).frame(width: 40, height: 40).offset(pan)
                }
                .frame(height: 140)
                .clipped()
                .gesture(DragGesture().onChanged { value in
                    pan = value.translation
                    record(.pan)
                })
                .accessibilityIdentifier(TestIDs.gesturePanArea)
                Button(L10n.tr("action_reset")) {
                    lastGesture = ""
                    zoom = 1
                    dropped = false
                    pan = .zero
                }
                .accessibilityIdentifier(TestIDs.gestureReset)
            }
            .padding()
        }
        .coordinateSpace(name: "gestures")
        .screenTitle(L10n.tr("scenario_gestures"))
        .screenRoot(TestIDs.gesturesScreen)
    }

    private func target(_ key: String, _ id: String) -> some View {
        Text(L10n.tr(key))
            .font(.caption)
            .frame(width: 96, height: 96)
            .background(Color.accentColor.opacity(0.2))
            .accessibilityElement()
            .accessibilityLabel(L10n.tr(key))
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier(id)
    }
}
