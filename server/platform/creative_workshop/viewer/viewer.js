(function () {
  "use strict";

  const root = document.getElementById("viewer");
  const params = new URLSearchParams(window.location.search);
  const state = {
    color: normalizeColor(params.get("color") || "#C6424A"),
    windowOpacity: normalizeOpacity(params.get("window") || "0.72"),
    wrapUrl: params.get("wrap") || "",
  };

  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(root.clientWidth, root.clientHeight, false);
  renderer.setClearColor(0x000000, 0);
  renderer.outputEncoding = THREE.sRGBEncoding;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.1;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  root.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(34, root.clientWidth / Math.max(root.clientHeight, 1), 0.01, 500);
  camera.position.set(5.4, 2.8, 7.4);

  const controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.075;
  controls.enablePan = false;
  controls.minDistance = 2.6;
  controls.maxDistance = 13;
  controls.maxPolarAngle = Math.PI * 0.52;
  controls.minPolarAngle = Math.PI * 0.22;
  controls.target.set(0, 0.75, 0);

  scene.add(new THREE.HemisphereLight(0xeaf8ff, 0x263038, 2.2));
  const key = new THREE.DirectionalLight(0xffffff, 3.1);
  key.position.set(-4, 7, 6);
  key.castShadow = true;
  key.shadow.mapSize.set(2048, 2048);
  scene.add(key);
  const fill = new THREE.DirectionalLight(0x8fc9ff, 1.8);
  fill.position.set(6, 3, -5);
  scene.add(fill);
  const rim = new THREE.DirectionalLight(0xffdfbf, 1.2);
  rim.position.set(-5, 2, -4);
  scene.add(rim);

  const shadow = new THREE.Mesh(
    new THREE.CircleGeometry(3.8, 96),
    new THREE.ShadowMaterial({ color: 0x061117, opacity: 0.34 })
  );
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.y = -0.012;
  shadow.receiveShadow = true;
  scene.add(shadow);

  const paintMaterials = [];
  const glassMaterials = [];
  let vehicle = null;
  let wrapTexture = null;
  let initialCamera = null;

  const skipPattern = /(wheel|rim|tire|tyre|brake|disc|lamp|light|chrome|metal|logo|badge|license|plate|grill|wiper|mirror|interior|seat|dash|steer|screen|轮|胎|刹车|灯|镀铬|标|牌|格栅|雨刷|后视镜|内饰|座椅|方向盘)/i;
  const paintPattern = /(body|paint|shell|door|hood|bonnet|roof|fender|quarter|bumper|trunk|boot|车身|车门|车顶|机盖|引擎盖|翼子板|保险杠|尾门)/i;
  const glassPattern = /(glass|window|windscreen|windshield|玻璃|车窗|风挡)/i;

  function notify(type, message) {
    const payload = JSON.stringify({ type: type, message: message || "" });
    if (window.WorkshopBridge && typeof window.WorkshopBridge.onState === "function") {
      window.WorkshopBridge.onState(payload);
    }
    window.parent.postMessage(payload, "*");
  }

  function normalizeColor(value) {
    return /^#[0-9a-f]{6}$/i.test(value || "") ? value.toUpperCase() : "#C6424A";
  }

  function normalizeOpacity(value) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.max(0.12, Math.min(number, 0.92)) : 0.72;
  }

  function materialName(mesh, material) {
    return [mesh.name, material && material.name].filter(Boolean).join(" ");
  }

  function cloneMaterials(mesh) {
    const source = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    const cloned = source.map(function (material) { return material ? material.clone() : material; });
    mesh.material = Array.isArray(mesh.material) ? cloned : cloned[0];
    return cloned;
  }

  function collectMaterials(model) {
    paintMaterials.length = 0;
    glassMaterials.length = 0;
    const fallback = [];
    model.traverse(function (node) {
      if (!node.isMesh || !node.material) return;
      node.castShadow = true;
      node.receiveShadow = true;
      cloneMaterials(node).forEach(function (material) {
        if (!material) return;
        const name = materialName(node, material);
        if (glassPattern.test(name) || material.transparent || material.opacity < 0.88) {
          glassMaterials.push(material);
          return;
        }
        if (skipPattern.test(name)) return;
        if (paintPattern.test(name)) paintMaterials.push(material);
        else if (material.color && material.roughness !== undefined) fallback.push(material);
      });
    });
    if (paintMaterials.length === 0) {
      fallback.slice(0, Math.min(fallback.length, 12)).forEach(function (material) {
        paintMaterials.push(material);
      });
    }
  }

  function applyPaint() {
    const color = new THREE.Color(state.color);
    paintMaterials.forEach(function (material) {
      material.color.copy(color);
      material.metalness = Math.max(Number(material.metalness) || 0, 0.46);
      material.roughness = Math.min(Number(material.roughness) || 0.42, 0.34);
      material.map = wrapTexture;
      material.needsUpdate = true;
    });
  }

  function applyWindows() {
    glassMaterials.forEach(function (material) {
      material.transparent = true;
      material.opacity = state.windowOpacity;
      material.color = material.color || new THREE.Color();
      material.color.set(0x11181d);
      material.depthWrite = false;
      material.needsUpdate = true;
    });
  }

  function fitCamera(model) {
    const box = new THREE.Box3().setFromObject(model);
    const size = box.getSize(new THREE.Vector3());
    const center = box.getCenter(new THREE.Vector3());
    const scale = 4.8 / Math.max(size.x, size.y, size.z, 0.01);
    model.scale.setScalar(scale);
    const scaledBox = new THREE.Box3().setFromObject(model);
    const scaledCenter = scaledBox.getCenter(new THREE.Vector3());
    model.position.x -= scaledCenter.x;
    model.position.z -= scaledCenter.z;
    model.position.y -= scaledBox.min.y;
    const target = new THREE.Vector3(0, Math.max(0.5, size.y * scale * 0.38), 0);
    controls.target.copy(target);
    camera.position.set(5.6, 2.8, 7.6);
    camera.lookAt(target);
    initialCamera = { position: camera.position.clone(), target: target.clone() };
    controls.update();
  }

  function setWrap(url) {
    state.wrapUrl = url || "";
    if (wrapTexture) {
      wrapTexture.dispose();
      wrapTexture = null;
    }
    if (!state.wrapUrl) {
      applyPaint();
      return;
    }
    new THREE.TextureLoader().load(
      state.wrapUrl,
      function (texture) {
        texture.encoding = THREE.sRGBEncoding;
        texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
        texture.repeat.set(2.2, 1.2);
        wrapTexture = texture;
        applyPaint();
      },
      undefined,
      function () { notify("error", "彩绘加载失败"); }
    );
  }

  function loadModel(url) {
    if (!url) {
      notify("error", "缺少车型资源地址");
      return;
    }
    notify("loading", "正在加载车型");
    const loader = new THREE.GLTFLoader();
    const draco = new THREE.DRACOLoader();
    draco.setDecoderPath("../vendor/draco/");
    loader.setDRACOLoader(draco);
    loader.load(
      url,
      function (gltf) {
        if (vehicle) scene.remove(vehicle);
        vehicle = gltf.scene;
        scene.add(vehicle);
        collectMaterials(vehicle);
        fitCamera(vehicle);
        applyPaint();
        applyWindows();
        if (state.wrapUrl) setWrap(state.wrapUrl);
        notify("ready", "车型加载完成");
      },
      undefined,
      function (error) {
        notify("error", error && error.message ? error.message : "车型加载失败");
      }
    );
  }

  window.workshop = {
    setColor: function (color) {
      state.color = normalizeColor(color);
      applyPaint();
    },
    setWrap: setWrap,
    setWindowOpacity: function (opacity) {
      state.windowOpacity = normalizeOpacity(opacity);
      applyWindows();
    },
    resetView: function () {
      if (!initialCamera) return;
      camera.position.copy(initialCamera.position);
      controls.target.copy(initialCamera.target);
      controls.update();
    },
    rotate: function (radians) {
      if (vehicle) vehicle.rotation.y += Number(radians) || 0;
    },
  };

  window.addEventListener("resize", function () {
    const width = Math.max(root.clientWidth, 1);
    const height = Math.max(root.clientHeight, 1);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height, false);
  });

  function render() {
    requestAnimationFrame(render);
    controls.update();
    renderer.render(scene, camera);
  }

  loadModel(params.get("model") || "");
  render();
})();
