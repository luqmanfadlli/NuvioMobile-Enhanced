import SwiftUI
import UIKit
import ComposeApp

/// Foldable (iPhone Duo) posture. Everything here is inert on regular iPhones and iPads: `UIHingeInteraction`
/// never fires without a hinge, so `isHalfOpen` stays false, the rail inset stays 0 and no layout changes.
final class NuvioFoldPosture: ObservableObject {
    static let shared = NuvioFoldPosture()
    static let railInsetDidChange = Notification.Name("NuvioFoldRailInsetDidChange")

    static let railWidth: CGFloat = 76
    static let railMargin: CGFloat = 8

    /// DEBUG-only: launch with `-NuvioSimulateHalfOpen YES` to exercise the half-open layouts on a simulator that
    /// cannot fold. Always false in Release builds.
    static let simulated: Bool = {
        #if DEBUG
        return UserDefaults.standard.bool(forKey: "NuvioSimulateHalfOpen")
        #else
        return false
        #endif
    }()

    @Published private(set) var isHalfOpen = NuvioFoldPosture.simulated
    /// Top edge (window coordinates) of the fold, or 0 when there is no horizontal fold to stay clear of.
    @Published private(set) var foldTop: CGFloat = 0

    /// Width Compose content keeps clear on the trailing edge while the tab rail is showing.
    private(set) var railInset: CGFloat = 0

    func update(isHalfOpen: Bool, foldTop: CGFloat) {
        if self.isHalfOpen != isHalfOpen { self.isHalfOpen = isHalfOpen }
        if self.foldTop != foldTop { self.foldTop = foldTop }
    }

    func setRailVisible(_ visible: Bool) {
        let inset = visible ? Self.railWidth + Self.railMargin * 2 : 0
        guard inset != railInset else { return }
        railInset = inset
        NotificationCenter.default.post(name: Self.railInsetDidChange, object: nil)
    }
}

/// Invisible view that owns the hinge interaction and publishes posture changes to `NuvioFoldPosture`.
struct NuvioFoldPostureReader: UIViewRepresentable {
    func makeUIView(context: Context) -> NuvioFoldPostureView { NuvioFoldPostureView() }
    func updateUIView(_ uiView: NuvioFoldPostureView, context: Context) {}
}

final class NuvioFoldPostureView: UIView {
    private var halfOpen = NuvioFoldPosture.simulated

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        if #available(iOS 27.1, *) {
            addInteraction(UIHingeInteraction { [weak self] _, update in
                self?.halfOpen = update.hinge?.status == .partiallyOpen
                self?.publish()
            })
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        publish()
    }

    private func publish() {
        var top: CGFloat = 0
        if NuvioFoldPosture.simulated {
            top = bounds.height / 2 - 12
        } else if #available(iOS 27.1, *), halfOpen,
                  let fold = reservedRegions(kind: .division).first(where: { $0.frame.width > $0.frame.height }) {
            top = fold.frame.minY
        }
        NuvioFoldPosture.shared.update(isHalfOpen: halfOpen || NuvioFoldPosture.simulated, foldTop: top)
    }
}

/// Vertical tab rail on the trailing edge, shown instead of the bottom glass bar while the Duo is half-open.
/// It stays in the screen half above the fold so it never straddles the hinge.
@available(iOS 26.0, *)
struct NuvioFoldTabRail: View {
    @ObservedObject var appCoordinator: AppNavigationCoordinator
    @ObservedObject var iconStore: NativeTabIconStore
    @ObservedObject private var posture = NuvioFoldPosture.shared

    private var trailingSafeInset: CGFloat {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene?.windows.first(where: { $0.isKeyWindow }) ?? scene?.windows.first
        return window?.safeAreaInsets.right ?? 0
    }

    var body: some View {
        let accent = Color(uiColor: iconStore.accentColor)
        GeometryReader { proxy in
            let availableHeight = posture.foldTop > 0 ? posture.foldTop : proxy.size.height
            VStack(spacing: 2) {
                ForEach(appCoordinator.barTabs, id: \.self) { tab in
                    item(tab, accent: accent)
                }
            }
            .padding(6)
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
            .padding(.trailing, NuvioFoldPosture.railMargin + trailingSafeInset)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .frame(height: availableHeight, alignment: .center)
            .padding(.top, 52)
            .frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private func item(_ tab: NuvioAppTab, accent: Color) -> some View {
        let selected = tab == appCoordinator.selectedTab
        let isSettings = tab == .settings
        return VStack(spacing: 3) {
            Image(uiImage: iconStore.image(for: tab, selected: selected))
                .renderingMode(isSettings ? .original : .template)
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
            Text(appCoordinator.title(for: tab))
                .font(.system(size: 10, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .foregroundStyle(selected ? accent : Color.white.opacity(0.72))
        .frame(width: NuvioFoldPosture.railWidth - 12, height: 48)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(selected ? Color.white.opacity(0.14) : Color.clear)
        )
        .contentShape(Rectangle())
        .onTapGesture {
            if selected {
                NativeTabBridgeKt.nativeTabSelect(tabName: tab.rawValue)
            } else {
                appCoordinator.selectedTab = tab
            }
        }
        .onLongPressGesture(minimumDuration: 0.45) {
            guard tab == .settings, appCoordinator.isAppReady else { return }
            appCoordinator.isProfileSwitcherPresented = true
        }
        .accessibilityLabel(appCoordinator.title(for: tab))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
