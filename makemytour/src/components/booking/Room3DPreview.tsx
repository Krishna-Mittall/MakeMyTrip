import { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";

type Props = { roomType: string; autoRotate?: boolean };

const palettes: Record<string, { wall: number; floor: number; accent: number; fabric: number; wood: number }> = {
  Standard: { wall: 0xf2e8da, floor: 0xb08968, accent: 0x6d8ea0, fabric: 0xf8f4ec, wood: 0x8a5a3c },
  Deluxe: { wall: 0xdbe4ee, floor: 0x9c7a5b, accent: 0x2f5d8a, fabric: 0xffffff, wood: 0x6e4a32 },
  Suite: { wall: 0xe9dcc8, floor: 0x5c3d2b, accent: 0xb8892b, fabric: 0xfdf6e8, wood: 0x3d2518 },
};

const mat = (color: number, rough = 0.8) => new THREE.MeshStandardMaterial({ color, roughness: rough, metalness: 0.05 });

function box(w: number, h: number, d: number, color: number, x: number, y: number, z: number, rough = 0.8) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color, rough));
  m.position.set(x, y, z);
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}

function buildRoom(type: string) {
  const p = palettes[type] || palettes.Standard;
  const size = type === "Suite" ? { w: 9, d: 7 } : type === "Deluxe" ? { w: 7, d: 5.6 } : { w: 5.8, d: 4.8 };
  const H = 3;
  const g = new THREE.Group();

  // floor and walls (the front wall and the ceiling are left out so that we can look in)
  g.add(box(size.w, 0.15, size.d, p.floor, 0, -0.075, 0, 0.6));
  g.add(box(size.w, H, 0.15, p.wall, 0, H / 2, -size.d / 2));
  g.add(box(0.15, H, size.d, p.wall, -size.w / 2, H / 2, 0));
  const right = box(0.15, H, size.d, p.wall, size.w / 2, H / 2, 0);
  g.add(right);
  g.add(box(size.w, 0.12, 0.2, 0xffffff, 0, 0.06, -size.d / 2 + 0.1));

  // window with a view
  const win = new THREE.Mesh(
    new THREE.PlaneGeometry(2.2, 1.5),
    new THREE.MeshStandardMaterial({ color: 0x9fd3f5, emissive: 0x6bb8ea, emissiveIntensity: 0.55 })
  );
  win.position.set(type === "Standard" ? 1.4 : 1.8, 1.65, -size.d / 2 + 0.09);
  g.add(win);
  g.add(box(2.4, 0.08, 0.12, 0xffffff, win.position.x, 2.42, -size.d / 2 + 0.1));
  g.add(box(2.4, 0.08, 0.12, 0xffffff, win.position.x, 0.88, -size.d / 2 + 0.1));
  g.add(box(0.5, 2.0, 0.08, p.accent, win.position.x - 1.35, 1.6, -size.d / 2 + 0.13));
  g.add(box(0.5, 2.0, 0.08, p.accent, win.position.x + 1.35, 1.6, -size.d / 2 + 0.13));

  // rug
  const rug = new THREE.Mesh(new THREE.CylinderGeometry(1.6, 1.6, 0.03, 40), mat(p.accent, 1));
  rug.position.set(-0.4, 0.02, 0.9);
  rug.scale.z = 0.75;
  rug.receiveShadow = true;
  g.add(rug);

  // bed
  const bedX = type === "Standard" ? -0.6 : -1.2;
  const bedZ = -size.d / 2 + 1.55;
  const bw = type === "Standard" ? 1.7 : 2.1;
  g.add(box(bw + 0.15, 0.45, 2.3, p.wood, bedX, 0.25, bedZ));
  g.add(box(bw, 0.28, 2.15, p.fabric, bedX, 0.62, bedZ + 0.02, 1));
  g.add(box(bw + 0.1, 0.12, 1.1, p.accent, bedX, 0.78, bedZ + 0.55, 1));
  g.add(box(bw + 0.25, 1.15, 0.12, p.wood, bedX, 0.95, bedZ - 1.18));
  for (const dx of [-bw / 4, bw / 4]) {
    g.add(box(bw / 2 - 0.15, 0.14, 0.5, 0xffffff, bedX + dx, 0.82, bedZ - 0.8, 1));
  }
  // night stands with lamps
  for (const dx of [-(bw / 2 + 0.55), bw / 2 + 0.55]) {
    g.add(box(0.55, 0.55, 0.5, p.wood, bedX + dx, 0.28, bedZ - 0.95));
    const lamp = new THREE.Mesh(
      new THREE.SphereGeometry(0.17, 16, 16),
      new THREE.MeshStandardMaterial({ color: 0xfff1c9, emissive: 0xffd27a, emissiveIntensity: 0.9 })
    );
    lamp.position.set(bedX + dx, 0.82, bedZ - 0.95);
    g.add(lamp);
  }

  // wall mounted tv on the right wall
  g.add(box(0.08, 0.8, 1.4, 0x111111, size.w / 2 - 0.12, 1.6, 0.4, 0.3));
  g.add(box(0.5, 0.45, 1.8, p.wood, size.w / 2 - 0.35, 0.25, 0.4));

  if (type !== "Standard") {
    // work desk and chair
    g.add(box(1.7, 0.08, 0.7, p.wood, -size.w / 2 + 1.1, 0.78, -size.d / 2 + 0.6));
    g.add(box(0.08, 0.78, 0.6, p.wood, -size.w / 2 + 0.3, 0.39, -size.d / 2 + 0.6));
    g.add(box(0.08, 0.78, 0.6, p.wood, -size.w / 2 + 1.9, 0.39, -size.d / 2 + 0.6));
    g.add(box(0.5, 0.08, 0.5, p.accent, -size.w / 2 + 1.1, 0.5, -size.d / 2 + 1.15));
    g.add(box(0.5, 0.55, 0.08, p.accent, -size.w / 2 + 1.1, 0.82, -size.d / 2 + 1.37));
    g.add(box(0.55, 0.04, 0.38, 0x222222, -size.w / 2 + 1.1, 0.84, -size.d / 2 + 0.55, 0.3)); // laptop
    // armchair
    g.add(box(0.9, 0.45, 0.9, p.accent, size.w / 2 - 1.3, 0.28, size.d / 2 - 1.2));
    g.add(box(0.9, 0.7, 0.18, p.accent, size.w / 2 - 1.3, 0.62, size.d / 2 - 1.62));
    // plant
    g.add(box(0.3, 0.35, 0.3, 0xaa6f4d, size.w / 2 - 0.5, 0.18, -size.d / 2 + 0.5));
    const plant = new THREE.Mesh(new THREE.SphereGeometry(0.4, 16, 16), mat(0x3f8f4a));
    plant.position.set(size.w / 2 - 0.5, 0.75, -size.d / 2 + 0.5);
    plant.castShadow = true;
    g.add(plant);
  }

  if (type === "Suite") {
    // lounge area: sofa, coffee table, floor lamp, painting, balcony door
    g.add(box(2.6, 0.5, 0.95, p.accent, -size.w / 2 + 1.8, 0.28, size.d / 2 - 1.1));
    g.add(box(2.6, 0.75, 0.2, p.accent, -size.w / 2 + 1.8, 0.65, size.d / 2 - 1.55));
    g.add(box(0.25, 0.65, 0.95, p.accent, -size.w / 2 + 0.6, 0.45, size.d / 2 - 1.1));
    g.add(box(0.25, 0.65, 0.95, p.accent, -size.w / 2 + 3.0, 0.45, size.d / 2 - 1.1));
    g.add(box(1.3, 0.1, 0.7, p.wood, -size.w / 2 + 1.8, 0.4, size.d / 2 - 2.2));
    g.add(box(0.08, 0.4, 0.08, p.wood, -size.w / 2 + 1.8, 0.2, size.d / 2 - 2.2));
    const pole = box(0.06, 1.7, 0.06, 0x333333, size.w / 2 - 0.5, 0.85, size.d / 2 - 0.5);
    g.add(pole);
    const shade = new THREE.Mesh(
      new THREE.ConeGeometry(0.3, 0.4, 20, 1, true),
      new THREE.MeshStandardMaterial({ color: 0xfff1c9, emissive: 0xffd27a, emissiveIntensity: 0.8, side: THREE.DoubleSide })
    );
    shade.position.set(size.w / 2 - 0.5, 1.8, size.d / 2 - 0.5);
    g.add(shade);
    g.add(box(2.2, 1.1, 0.05, p.accent, -2.2, 1.9, -size.d / 2 + 0.1)); // painting
    g.add(box(2.0, 0.9, 0.06, 0xf4d58d, -2.2, 1.9, -size.d / 2 + 0.12, 0.4));
    // bathtub corner
    g.add(box(1.8, 0.55, 0.9, 0xffffff, size.w / 2 - 1.2, 0.28, -size.d / 2 + 0.7, 0.3));
  }

  // soft lights
  const lights: THREE.Light[] = [];
  const ambient = new THREE.AmbientLight(0xffffff, 0.55);
  const hemi = new THREE.HemisphereLight(0xffffff, 0xc8b79e, 0.5);
  const sun = new THREE.DirectionalLight(0xffffff, 1.0);
  sun.position.set(4, 8, 6);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  sun.shadow.camera.left = -8;
  sun.shadow.camera.right = 8;
  sun.shadow.camera.top = 8;
  sun.shadow.camera.bottom = -8;
  const warm = new THREE.PointLight(0xffc977, 8, 7);
  warm.position.set(bedX, 1.4, bedZ - 0.4);
  lights.push(ambient, hemi, sun, warm);
  lights.forEach((l) => g.add(l));
  return { group: g, size };
}

/** A turntable 3D model of a room type: drag to rotate, scroll / pinch to zoom. */
const Room3DPreview = ({ roomType, autoRotate = true }: Props) => {
  const mount = useRef<HTMLDivElement>(null);
  const controlsRef = useRef<OrbitControls | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const el = mount.current;
    if (!el) return;
    let renderer: THREE.WebGLRenderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    } catch (e) {
      setFailed(true);
      return;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    el.appendChild(renderer.domElement);
    renderer.domElement.style.display = "block";
    renderer.domElement.style.width = "100%";
    renderer.domElement.style.height = "100%";

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0xeef2f7);
    const { group, size } = buildRoom(roomType);
    scene.add(group);

    const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
    const radius = Math.max(size.w, size.d) * 1.35;
    camera.position.set(radius * 0.55, radius * 0.6, radius * 0.95);
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.target.set(0, 1.0, 0);
    controls.enableDamping = true;
    controls.minDistance = radius * 0.5;
    controls.maxDistance = radius * 1.6;
    controls.maxPolarAngle = Math.PI / 2.05;
    controls.minAzimuthAngle = -Math.PI / 2.2;
    controls.maxAzimuthAngle = Math.PI / 2.2;
    controls.autoRotate = autoRotate;
    controls.autoRotateSpeed = 1.2;
    controlsRef.current = controls;

    const resize = () => {
      const w = el.clientWidth || 300;
      const h = el.clientHeight || 300;
      renderer.setSize(w, h, false);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
    };
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(el);

    let frame = 0;
    let direction = 1;
    const loop = () => {
      frame = requestAnimationFrame(loop);
      // swing between the two side limits instead of spinning all the way round
      const az = controls.getAzimuthalAngle();
      if (az > controls.maxAzimuthAngle - 0.05) direction = -1;
      if (az < controls.minAzimuthAngle + 0.05) direction = 1;
      controls.autoRotateSpeed = 1.2 * direction;
      controls.update();
      renderer.render(scene, camera);
    };
    loop();

    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      controls.dispose();
      scene.traverse((o) => {
        const mesh = o as THREE.Mesh;
        if (mesh.geometry) mesh.geometry.dispose();
        const m = mesh.material as THREE.Material | THREE.Material[] | undefined;
        if (Array.isArray(m)) m.forEach((x) => x.dispose());
        else m?.dispose();
      });
      renderer.dispose();
      if (renderer.domElement.parentNode === el) el.removeChild(renderer.domElement);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomType]);

  useEffect(() => {
    if (controlsRef.current) controlsRef.current.autoRotate = autoRotate;
  }, [autoRotate]);

  if (failed) {
    return (
      <div className="h-full min-h-[240px] flex items-center justify-center text-sm text-gray-500 bg-gray-100 rounded-lg p-4 text-center">
        3D preview is not supported by this browser. Please use the photos.
      </div>
    );
  }
  return <div ref={mount} className="w-full h-full min-h-[260px] rounded-lg overflow-hidden touch-pan-y" aria-label={`3D model of the ${roomType} room`} />;
};

export default Room3DPreview;
