import SwiftUI
import UIKit
import ComposeApp

@MainActor
final class PlayerPictureInPictureParking {
    static let shared = PlayerPictureInPictureParking()

    private struct Parked {
        let route: AppRoute
        let content: UIViewController
        weak var coordinator: TabNavigationCoordinator?
        weak var player: MPVPlayerViewController?
    }

    weak var appCoordinator: AppNavigationCoordinator?

    private var parked: Parked?
    private var hostView: UIView?
    private var pendingRestore: ((Bool) -> Void)?
    private var restoreTimeout: DispatchWorkItem?

    private init() {}

    var hasParkedPlayer: Bool { parked != nil }

    func isParked(_ route: AppRoute) -> Bool { parked?.route === route }

    func isParked(player: MPVPlayerViewController) -> Bool {
        guard let parked else { return false }
        return parked.player === player
    }

    @discardableResult
    func park(player: MPVPlayerViewController) -> Bool {
        guard parked == nil else { return false }
        var candidate: UIViewController? = player
        while let current = candidate, !(current.parent is PlayerRouteContainerViewController) {
            candidate = current.parent
        }
        guard let content = candidate,
              let container = content.parent as? PlayerRouteContainerViewController,
              let coordinator = container.coordinator,
              let window = container.view.window else { return false }

        let host = hostView(in: window)
        (content as? RootComposeViewController)?.prepareForPictureInPictureParking()
        container.releaseContent()
        host.addSubview(content.view)
        content.view.frame = host.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]

        parked = Parked(route: container.route, content: content, coordinator: coordinator, player: player)
        player.setParkedInPictureInPicture(true)
        OrientationLockCoordinator.shared.setParkedPlayerSuspension(true)
        AppKt.setPlayerParkedInPictureInPicture(parked: true)

        let route = container.route
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            coordinator.setPath(coordinator.path.filter { $0.route !== route })
        }
        InAppLogBridge.shared.info(tag: "Player/PiP", message: "player parked; browsing with PiP")
        return true
    }

    func restore(player: MPVPlayerViewController, completion: @escaping (Bool) -> Void) -> Bool {
        guard let parked, parked.player === player,
              let coordinator = parked.coordinator,
              let appCoordinator else { return false }
        finishPendingRestore(false)
        pendingRestore = completion
        let timeout = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.finishPendingRestore(self.parked == nil)
        }
        restoreTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: timeout)

        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            appCoordinator.showParkedPlayerRoute(parked.route, in: coordinator)
        }
        return true
    }

    func unpark(route: AppRoute, into container: PlayerRouteContainerViewController) -> Bool {
        guard let parked, parked.route === route else { return false }
        self.parked = nil
        container.embed(parked.content)
        (parked.content as? RootComposeViewController)?.restoreAfterPictureInPictureParking()
        OrientationLockCoordinator.shared.setParkedPlayerSuspension(false)
        AppKt.setPlayerParkedInPictureInPicture(parked: false)
        parked.player?.setParkedInPictureInPicture(false)
        InAppLogBridge.shared.info(tag: "Player/PiP", message: "parked player restored")
        return true
    }

    func containerDidAppear(_ container: PlayerRouteContainerViewController) {
        guard pendingRestore != nil, parked == nil, container.hasContent else { return }
        finishPendingRestore(true)
    }

    func closeParked() {
        guard let parked else { return }
        self.parked = nil
        finishPendingRestore(false)
        parked.player?.pausePlayback()
        parked.player?.endPictureInPictureSession()
        parked.content.view.removeFromSuperview()
        parked.content.removeFromParent()
        AppKt.setPlayerParkedInPictureInPicture(parked: false)
        AppKt.disposeRoute(route: parked.route)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            OrientationLockCoordinator.shared.setParkedPlayerSuspension(false)
        }
        InAppLogBridge.shared.info(tag: "Player/PiP", message: "parked player closed")
    }

    private func finishPendingRestore(_ restored: Bool) {
        restoreTimeout?.cancel()
        restoreTimeout = nil
        guard let completion = pendingRestore else { return }
        pendingRestore = nil
        completion(restored)
    }

    private func hostView(in window: UIWindow) -> UIView {
        if let hostView, hostView.window === window { return hostView }
        hostView?.removeFromSuperview()
        let view = UIView(frame: window.bounds)
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.isHidden = true
        view.isUserInteractionEnabled = false
        view.accessibilityElementsHidden = true
        window.insertSubview(view, at: 0)
        hostView = view
        return view
    }
}

final class PlayerRouteContainerViewController: UIViewController {
    let route: AppRoute
    weak var coordinator: TabNavigationCoordinator?
    private let makeContent: @MainActor () -> UIViewController
    private var content: UIViewController?
    private var awaitsParkedContent: Bool

    var hasContent: Bool { content != nil }

    init(route: AppRoute, coordinator: TabNavigationCoordinator, makeContent: @escaping @MainActor () -> UIViewController) {
        self.route = route
        self.coordinator = coordinator
        self.makeContent = makeContent
        self.awaitsParkedContent = PlayerPictureInPictureParking.shared.isParked(route)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        let view = WindowObservingView()
        view.backgroundColor = .black
        view.onWindowChange = { [weak self] in self?.adoptParkedContentIfReady() }
        self.view = view
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        if !awaitsParkedContent { embed(makeContent()) }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        PlayerPictureInPictureParking.shared.containerDidAppear(self)
    }

    override var childForStatusBarHidden: UIViewController? { content }
    override var childForHomeIndicatorAutoHidden: UIViewController? { content }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { content }

    func embed(_ controller: UIViewController) {
        if controller.parent != nil {
            controller.willMove(toParent: nil)
            controller.removeFromParent()
        }
        addChild(controller)
        view.addSubview(controller.view)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        controller.didMove(toParent: self)
        content = controller
    }

    func releaseContent() {
        guard let content else { return }
        content.willMove(toParent: nil)
        content.removeFromParent()
        self.content = nil
    }

    private func adoptParkedContentIfReady() {
        guard awaitsParkedContent, viewIfLoaded?.window != nil else { return }
        awaitsParkedContent = false
        if !PlayerPictureInPictureParking.shared.unpark(route: route, into: self) {
            coordinator?.pop()
        }
    }
}

private final class WindowObservingView: UIView {
    var onWindowChange: (() -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        onWindowChange?()
    }
}
