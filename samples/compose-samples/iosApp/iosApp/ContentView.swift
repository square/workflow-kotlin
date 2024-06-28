import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    var onSwipe: () -> Void
    
    func makeUIViewController(context: Context) -> UIViewController {
        let mainViewController = MainViewControllerKt.MainViewController()
        
        let containerController = ContainerViewController(child: mainViewController) {
            context.coordinator.startPoint = $0
        }
        
        let swipeGestureRecognizer = UISwipeGestureRecognizer(
            target:
                context.coordinator, action: #selector(Coordinator.handleSwipe)
        )
        swipeGestureRecognizer.direction = .right
        swipeGestureRecognizer.numberOfTouchesRequired = 1
        containerController.view.addGestureRecognizer(swipeGestureRecognizer)
        return containerController
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onSwipe: onSwipe)
    }
    
    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onSwipe: () -> Void
        var startPoint: CGPoint?
        
        init(onSwipe: @escaping () -> Void) {
            self.onSwipe = onSwipe
        }
        
        @objc func handleSwipe(_ gesture: UISwipeGestureRecognizer) {
            if gesture.state == .ended, let startPoint = startPoint, startPoint.x < 50 {
                onSwipe()
            }
        }
        
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            true
        }
    }
}

struct ContentView: View {
    var body: some View {
            ComposeView {
                onBackGesture()
            }
            .ignoresSafeArea(.keyboard)
    }
}

public func onBackGesture() {
    MainViewControllerKt.onBackGesture()
}
