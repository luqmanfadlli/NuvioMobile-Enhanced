import SwiftUI
import UIKit
import ComposeApp

enum NuvioTabBarBehavior: String, CaseIterable {
    case off
    case `static`
    case autoHide = "auto_hide"
    case morphed

    static let storageKey = "NuvioNativeTabBarBehavior"
    static let fallback: NuvioTabBarBehavior = .morphed

    static func current() -> NuvioTabBarBehavior {
        guard let raw = UserDefaults.standard.string(forKey: storageKey) else { return fallback }
        return NuvioTabBarBehavior(rawValue: raw) ?? fallback
    }

    var isEnabled: Bool { self != .off }

    var usesCompactPill: Bool { self == .morphed }

    var respondsToScroll: Bool { self == .autoHide || self == .morphed }
}

struct NuvioTabBarItemMetrics: Equatable {
    var buttonFrame: CGRect
    var iconFrame: CGRect
    var labelFrame: CGRect?
    var labelFont: UIFont?
}

struct NuvioTabBarMetrics: Equatable {
    var windowSize: CGSize
    var leadingInset: CGFloat
    var trailingInset: CGFloat
    var bottomInset: CGFloat
    var height: CGFloat
    var items: [NuvioTabBarItemMetrics] = []
    var selectionFrame: CGRect? = nil
    var selectionItemIndex: Int? = nil

    func selectionFrame(forItemAt index: Int) -> CGRect? {
        guard let selectionFrame,
              let measuredIndex = selectionItemIndex,
              items.indices.contains(measuredIndex),
              items.indices.contains(index) else { return nil }
        let dx = items[index].buttonFrame.midX - items[measuredIndex].buttonFrame.midX
        return selectionFrame.offsetBy(dx: dx, dy: 0)
    }

    var width: CGFloat { windowSize.width - leadingInset - trailingInset }

    static func key(for size: CGSize) -> String {
        "\(Int(size.width.rounded()))x\(Int(size.height.rounded()))"
    }
}

@available(iOS 26.0, *)
struct NuvioGlassTabBar: View {
    @ObservedObject var appCoordinator: AppNavigationCoordinator
    @ObservedObject var iconStore: NativeTabIconStore
    var expandedMetrics: NuvioTabBarMetrics? = nil

    @Namespace private var glassNamespace
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    private static let barGlassID = "nuvio.tabbar"

    static let portraitBottomInset: CGFloat = 20
    static let landscapeBottomInset: CGFloat = 16

    private var bottomInset: CGFloat {
        if isExpanded, let expandedMetrics { return expandedMetrics.bottomInset }
        return verticalSizeClass == .compact ? Self.landscapeBottomInset : Self.portraitBottomInset
    }

    private var expandedHeight: CGFloat? {
        isExpanded ? expandedMetrics?.height : nil
    }

    private var selectedTab: NuvioAppTab {
        appCoordinator.selectedTab
    }

    private var isExpanded: Bool {
        appCoordinator.isTabBarVisible
    }

    private var visibleTabs: [NuvioAppTab] {
        isExpanded ? appCoordinator.barTabs : [selectedTab == .settings ? .home : selectedTab]
    }

    private var mirroredItems: [NuvioTabBarItemMetrics]? {
        guard isExpanded,
              let items = expandedMetrics?.items,
              !items.isEmpty,
              items.count == visibleTabs.count else { return nil }
        return items
    }

    private static let tapFeedback: UIImpactFeedbackGenerator = {
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.prepare()
        return generator
    }()

    private static var tapHapticsEnabled: Bool {
        let defaults = UserDefaults.standard
        let key = "NuvioTabBarHapticsEnabled"
        return defaults.object(forKey: key) == nil ? true : defaults.bool(forKey: key)
    }

    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    private var isCompactHeight: Bool { verticalSizeClass == .compact || isPad }

    private var hidesWhenCollapsed: Bool {
        isPad && appCoordinator.tabBarBehavior == .autoHide
    }

    private var isHidden: Bool { hidesWhenCollapsed && !isExpanded }

    var body: some View {
        segmentedBar
            .padding(.horizontal, 16)
            .padding(.bottom, bottomInset)
            .offset(y: isHidden ? NuvioSegmentedTabBar.height(compact: true) + bottomInset + 24 : 0)
            .opacity(isHidden ? 0 : 1)
            .allowsHitTesting(!isHidden)
            .animation(.easeInOut(duration: 0.22), value: isHidden)
            .ignoresSafeArea(.container, edges: .bottom)
    }

    private var segmentedBar: some View {
        let tabs = appCoordinator.barTabs
        let singleAccent = iconStore.accentColors.count <= 1
        let items = tabs.map { tab in
            NuvioSegmentedTabBarItem(
                title: appCoordinator.title(for: tab),
                baseImage: iconStore.image(for: tab, selected: false),
                accentImage: iconStore.image(for: tab, selected: true),
                tintsBaseImage: !(tab == .settings),
                tintsAccentImage: !(tab == .settings) && singleAccent
            )
        }
        let contentKey = tabs.map { "\($0.rawValue):\(appCoordinator.title(for: $0))" }.joined(separator: "|")
            + "#\(iconStore.revision)#\(isCompactHeight)"
        return NuvioSegmentedTabBar(
            items: items,
            contentKey: contentKey,
            selectedIndex: tabs.firstIndex(of: selectedTab) ?? UISegmentedControl.noSegment,
            isExpanded: isExpanded || hidesWhenCollapsed,
            isCompact: isCompactHeight,
            accentColor: iconStore.accentColor,
            indicatorColor: iconStore.accentColors.isEmpty
                ? iconStore.accentColor
                : iconStore.accentColors[iconStore.accentColors.count / 2],
            hapticsEnabled: { Self.tapHapticsEnabled },
            onSelect: { index in
                guard tabs.indices.contains(index) else { return }
                appCoordinator.selectedTab = tabs[index]
            },
            onReselect: { index in
                guard tabs.indices.contains(index) else { return }
                NativeTabBridgeKt.nativeTabSelect(tabName: tabs[index].rawValue)
            },
            onLongPress: { index in
                guard tabs.indices.contains(index), tabs[index] == .settings, appCoordinator.isAppReady else { return }
                appCoordinator.isProfileSwitcherPresented = true
            },
            onCollapsedTap: {
                if Self.tapHapticsEnabled {
                    Self.tapFeedback.impactOccurred()
                    Self.tapFeedback.prepare()
                }
                appCoordinator.requestTabBarVisible(true)
            }
        )
        .frame(height: NuvioSegmentedTabBar.height(compact: isCompactHeight))
    }

    private func item(for tab: NuvioAppTab) -> some View {
        let selected = tab == selectedTab
        let content = Group {
            if verticalSizeClass == .compact {
                HStack(spacing: 6) {
                    icon(for: tab, selected: selected)
                    if isExpanded {
                        label(for: tab, selected: selected)
                    }
                }
            } else {
                VStack(spacing: 3) {
                    icon(for: tab, selected: selected)
                    if isExpanded {
                        label(for: tab, selected: selected)
                    }
                }
            }
        }
        .padding(.vertical, verticalSizeClass == .compact ? 6 : 7)
        .padding(.horizontal, verticalSizeClass == .compact ? 12 : (isExpanded ? 4 : 10))
        .frame(maxWidth: isExpanded && verticalSizeClass != .compact ? .infinity : nil)
        .contentShape(Capsule())
        .background {
            if selected && isExpanded {
                Capsule()
                    .fill(Color.black.opacity(0.34))
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.28), lineWidth: 1))
            }
        }

        let button = Button {
            appCoordinator.requestTabBarVisible(true)
        } label: {
            content
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(appCoordinator.title(for: tab)))
        .accessibilityAddTraits(selected ? [.isSelected] : [])

        return Group {
            if tab == .settings {
                button.simultaneousGesture(
                    LongPressGesture(minimumDuration: 0.45)
                        .onEnded { _ in
                            guard appCoordinator.isAppReady else { return }
                            appCoordinator.isProfileSwitcherPresented = true
                        }
                )
            } else {
                button
            }
        }
    }

    private func mirroredContent(
        items: [NuvioTabBarItemMetrics],
        metrics: NuvioTabBarMetrics
    ) -> some View {
        let selectedIndex = visibleTabs.firstIndex(of: selectedTab)
        return ZStack {
            if let selectedIndex, let selection = metrics.selectionFrame(forItemAt: selectedIndex) {
                Capsule()
                    .fill(Color.black.opacity(0.42))
                    .frame(width: selection.width, height: selection.height)
                    .position(x: selection.midX, y: selection.midY)
            }
            ForEach(Array(visibleTabs.enumerated()), id: \.element) { entry in
                mirroredItem(tab: entry.element, item: items[entry.offset])
            }
        }
        .frame(width: metrics.width, height: metrics.height)
    }

    @ViewBuilder
    private func mirroredItem(tab: NuvioAppTab, item: NuvioTabBarItemMetrics) -> some View {
        let selected = tab == selectedTab
        nativeIcon(for: tab, selected: selected)
            .frame(width: item.iconFrame.width, height: item.iconFrame.height)
            .position(x: item.iconFrame.midX, y: item.iconFrame.midY)
            .transition(.opacity)
        if let labelFrame = item.labelFrame {
            Text(appCoordinator.title(for: tab))
                .font(item.labelFont.map { Font($0 as CTFont) } ?? .system(size: 10, weight: .semibold))
                .lineLimit(1)
                .fixedSize()
                .foregroundStyle(selected ? iconStore.accentStyle() : AnyShapeStyle(Color.white))
                .position(x: labelFrame.midX, y: labelFrame.midY)
                .transition(.opacity)
        }
    }

    private func nativeIcon(for tab: NuvioAppTab, selected: Bool) -> some View {
        let image = Image(uiImage: iconStore.image(for: tab, selected: selected))
        return Group {
            if tab == .settings {
                image.renderingMode(.original).resizable().scaledToFit()
            } else if selected {
                image.renderingMode(.template).resizable().scaledToFit()
                    .foregroundStyle(iconStore.accentStyle())
            } else {
                image.renderingMode(.template).resizable().scaledToFit()
                    .foregroundStyle(Color.white)
            }
        }
    }

    private func label(for tab: NuvioAppTab, selected: Bool) -> some View {
        Text(appCoordinator.title(for: tab))
            .font(.system(size: 11, weight: .medium))
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .foregroundStyle(
                selected ? iconStore.accentStyle() : AnyShapeStyle(Color.white)
            )
            .legibleOverGlass(enabled: !selected)
    }

    private func icon(for tab: NuvioAppTab, selected: Bool) -> some View {
        let image = Image(uiImage: iconStore.image(for: tab, selected: selected))

        return Group {
            if tab == .settings {
                image
                    .renderingMode(.original)
                    .resizable()
                    .scaledToFit()
            } else if selected {
                image
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(iconStore.accentStyle())
            } else {
                image
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(Color.white)
            }
        }
        .frame(width: 24, height: 24)
        .legibleOverGlass(enabled: !selected)
    }
}

@available(iOS 26.0, *)
private extension View {
    func legibleOverGlass(enabled: Bool) -> some View {
        shadow(color: .black.opacity(enabled ? 0.35 : 0), radius: 2, x: 0, y: 0)
            .shadow(color: .black.opacity(enabled ? 0.22 : 0), radius: 5, x: 0, y: 1)
    }
}
