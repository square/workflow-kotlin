import SwiftUI

class ContainerViewController: UIViewController {
    private let onTouchDown: (CGPoint) -> Void
    
    init(child: UIViewController, onTouchDown: @escaping (CGPoint) -> Void) {
        self.onTouchDown = onTouchDown
        super.init(nibName: nil, bundle: nil)
        addChild(child)
        child.view.frame = view.frame
        view.addSubview(child.view)
        child.didMove(toParent: self)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        if let startPoint = touches.first?.location(in: nil) {
            onTouchDown(startPoint)
        }
    }
}
