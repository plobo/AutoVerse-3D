// Offscreen preview renderer for USDZ assets (macOS, SceneKit). Used for asset review only.
// Usage: swift tools/preview/render_preview.swift <in.usdz> <out.png> [yawDeg] [pitchDeg] [distance] [hideNodes,comma,separated]
import AppKit
import SceneKit

let args = CommandLine.arguments
let input = URL(fileURLWithPath: args[1])
let output = URL(fileURLWithPath: args[2])
let yaw = (args.count > 3 ? Double(args[3])! : 38) * .pi / 180
let pitch = (args.count > 4 ? Double(args[4])! : 14) * .pi / 180
let distance = args.count > 5 ? Double(args[5])! : 7.0
let hidden = args.count > 6 ? args[6].split(separator: ",").map(String.init) : ["Hotspots", "Chassis", "Suspension", "EngineOrMotor", "WheelsAndBrakes"]

let scene = try SCNScene(url: input, options: nil)
for name in hidden { scene.rootNode.childNode(withName: name, recursively: true)?.isHidden = true }
let target = SCNVector3(0, 0.75, 0)
let cam = SCNNode()
cam.camera = SCNCamera()
cam.camera?.fieldOfView = 40
cam.camera?.zFar = 100
cam.position = SCNVector3(target.x + CGFloat(distance * cos(pitch) * sin(yaw)), target.y + CGFloat(distance * sin(pitch)), target.z + CGFloat(distance * cos(pitch) * cos(yaw)))
cam.look(at: target)
scene.rootNode.addChildNode(cam)
let sun = SCNNode(); sun.light = SCNLight(); sun.light?.type = .directional; sun.light?.intensity = 1200
sun.eulerAngles = SCNVector3(-0.9, 0.6, 0); scene.rootNode.addChildNode(sun)
let amb = SCNNode(); amb.light = SCNLight(); amb.light?.type = .ambient; amb.light?.intensity = 500; scene.rootNode.addChildNode(amb)
scene.background.contents = NSColor(calibratedWhite: 0.85, alpha: 1)
let renderer = SCNRenderer(device: MTLCreateSystemDefaultDevice(), options: nil)
renderer.scene = scene
renderer.pointOfView = cam
let image = renderer.snapshot(atTime: 0, with: CGSize(width: 1200, height: 800), antialiasingMode: .multisampling4X)
let rep = NSBitmapImageRep(data: image.tiffRepresentation!)!
try rep.representation(using: .png, properties: [:])!.write(to: output)
print("wrote \(output.path)")
