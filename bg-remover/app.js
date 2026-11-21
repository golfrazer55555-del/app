// 배경 지우개 — 화면, 다듬기 붓, 배경 합성, 저장
// AI 인식은 worker.js에서 해요.

const WORK_SIDE = 4096;   // 편집·저장 해상도 상한 (긴 변)
const DETECT_SIDE = 1536; // AI에 넘길 사진 크기 상한 (모델은 어차피 512~1024로 줄여서 봐요)
const MAX_HISTORY = 40;
const MODEL_LABEL = { general: '모든 사물', portrait: '사람만' };

const $ = (id) => document.getElementById(id);
const app = $('app');
const stage = $('stage');
const view = $('view');
const vctx = view.getContext('2d');

// ---------- 상태 ----------
const S = {
  name: 'image',
  W: 0, H: 0,
  src: null,      // 원본(작업 해상도) 캔버스
  mask: null,     // 흰색 + 알파(=남길 정도) 캔버스, W×H
  auto: null,     // AI가 만든 처음 마스크 (처음 결과로 되돌리기용)
  backup: null,   // 붓질 시작 직전 마스크 사본 (되돌리기 조각 만들 때 사용)
  blur: null,     // 흐린 배경 (필요할 때 만듦)
  bgImage: null,  // 사용자가 고른 배경 사진
  bg: 'transparent',
  model: 'general',
  tool: 'move',
  brush: 40,
  soft: 50,
  hasMask: false,
  busy: false,
  peek: false,
  // 화면 변환: 화면 픽셀 = o + 이미지 픽셀 × z (기기 픽셀 기준)
  z: 1, ox: 0, oy: 0, userZoomed: false,
  undo: [], redo: [],
  reqId: 0,
};

try { S.model = localStorage.getItem('bgr-model') || 'general'; } catch { /* 기본값 사용 */ }
if (!MODEL_LABEL[S.model]) S.model = 'general';

// ---------- 도우미 ----------
function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(w));
  c.height = Math.max(1, Math.round(h));
  return c;
}

let toastTimer;
function toast(msg, ms = 2200) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), ms);
}

function save(key, val) { try { localStorage.setItem(key, val); } catch { /* 저장소를 못 쓰면 무시 */ } }

const dpr = () => Math.min(window.devicePixelRatio || 1, 3);

// ---------- AI 작업자 ----------
let worker;
const pending = new Map();

function getWorker() {
  if (worker) return worker;
  worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
  worker.addEventListener('message', (e) => {
    const m = e.data;
    if (m.type === 'download') {
      showProgress(m.loaded, m.total, m.key);
    } else if (m.type === 'status') {
      if (m.id !== S.reqId) return;
      setBusy(true, '배경을 찾는 중…', m.device === 'webgpu' ? '그래픽 가속(WebGPU) 사용 중' : '');
    } else {
      const p = pending.get(m.id);
      if (!p) return;
      pending.delete(m.id);
      if (m.type === 'mask') p.resolve(m);
      else p.reject(new Error(m.message));
    }
  });
  worker.addEventListener('error', (e) => {
    console.error(e);
    for (const p of pending.values()) p.reject(new Error('AI 엔진을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.'));
    pending.clear();
    worker = null;
  });
  return worker;
}

function detect(key, id) {
  const s = Math.min(1, DETECT_SIDE / Math.max(S.W, S.H));
  const c = canvas(S.W * s, S.H * s);
  const ctx = c.getContext('2d');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(S.src, 0, 0, c.width, c.height);
  const { data } = ctx.getImageData(0, 0, c.width, c.height);
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    getWorker().postMessage({ type: 'run', id, key, pixels: data, width: c.width, height: c.height }, [data.buffer]);
  });
}

// ---------- 진행 표시 ----------
function setBusy(on, text = '', sub = '') {
  S.busy = on;
  $('busy').hidden = !on;
  if (on) {
    $('busyText').textContent = text;
    $('busySub').textContent = sub;
  }
  $('bar').hidden = true;
  updateButtons();
}

function showProgress(loaded, total, key) {
  if (!S.busy) return;
  const mb = (n) => (n / 1e6).toFixed(n > 1e8 ? 0 : 1);
  $('busyText').textContent = `AI 모델 받는 중 (${MODEL_LABEL[key] || ''})`;
  $('busySub').textContent = `${mb(loaded)} / ${mb(total)}MB · 처음 한 번만 받아요`;
  $('bar').hidden = false;
  $('barFill').style.width = `${Math.min(100, (loaded / total) * 100).toFixed(1)}%`;
}

// ---------- 사진 열기 ----------
async function openFile(file) {
  if (!file || !file.type.startsWith('image/')) {
    toast('사진 파일을 골라 주세요');
    return;
  }
  let bmp;
  try {
    bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    try { bmp = await loadViaImg(file); } catch {
      toast('이 사진은 열 수 없어요');
      return;
    }
  }
  S.name = (file.name || 'image').replace(/\.[^.]+$/, '') || 'image';
  const s = Math.min(1, WORK_SIDE / Math.max(bmp.width, bmp.height));
  S.W = Math.round(bmp.width * s);
  S.H = Math.round(bmp.height * s);
  S.src = canvas(S.W, S.H);
  const ctx = S.src.getContext('2d');
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bmp, 0, 0, S.W, S.H);
  bmp.close?.();

  S.mask = canvas(S.W, S.H);
  S.backup = canvas(S.W, S.H);
  S.auto = null;
  S.blur = null;
  S.hasMask = false;
  S.undo = [];
  S.redo = [];
  S.userZoomed = false;

  app.dataset.state = 'editor';
  $('info').textContent = '';
  resizeView();
  await runDetect();
}

function loadViaImg(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('decode')); };
    img.src = url;
  });
}

async function runDetect() {
  const key = S.model;
  S.hasMask = false;
  render();
  setBusy(true, '준비 중…', `${MODEL_LABEL[key]} 모델`);
  const t0 = performance.now();
  const id = ++S.reqId;
  try {
    const m = await detect(key, id);
    if (id !== S.reqId) return; // 그새 다른 사진을 골랐어요
    applyAutoMask(m.mask, m.width, m.height);
    const sec = ((performance.now() - t0) / 1000).toFixed(1);
    $('info').textContent = `${S.W}×${S.H} · ${MODEL_LABEL[key]} 모델 · ${m.device === 'webgpu' ? 'WebGPU' : 'CPU'} · ${sec}초`;
    setBusy(false);
    if (isEmptyMask()) toast(key === 'portrait' ? '사람을 못 찾았어요. \'모든 사물\' 모델로 해 보세요' : '남길 피사체를 못 찾았어요', 3500);
  } catch (err) {
    if (id !== S.reqId) return;
    setBusy(false);
    console.error(err);
    $('info').textContent = '';
    toast(`배경 인식 실패: ${err.message}`, 5000);
  }
}

function applyAutoMask(alpha, w, h) {
  const small = canvas(w, h);
  const sctx = small.getContext('2d');
  const img = sctx.createImageData(w, h);
  for (let i = 0, j = 0; i < alpha.length; i++, j += 4) {
    img.data[j] = img.data[j + 1] = img.data[j + 2] = 255;
    img.data[j + 3] = alpha[i];
  }
  sctx.putImageData(img, 0, 0);
  const mctx = S.mask.getContext('2d');
  mctx.clearRect(0, 0, S.W, S.H);
  mctx.imageSmoothingEnabled = true;
  mctx.imageSmoothingQuality = 'high';
  mctx.drawImage(small, 0, 0, S.W, S.H);
  S.auto = canvas(S.W, S.H);
  S.auto.getContext('2d').drawImage(S.mask, 0, 0);
  S.hasMask = true;
  S.undo = [];
  S.redo = [];
  render();
}

function isEmptyMask() {
  const c = canvas(64, 64);
  const ctx = c.getContext('2d');
  ctx.drawImage(S.mask, 0, 0, 64, 64);
  const d = ctx.getImageData(0, 0, 64, 64).data;
  for (let i = 3; i < d.length; i += 4) if (d[i] > 40) return false;
  return true;
}

// ---------- 화면 그리기 ----------
let cut; // 화면 크기 합성용 캔버스
let checker;
let raf = 0;

function resizeView() {
  const r = stage.getBoundingClientRect();
  const k = dpr();
  view.width = Math.max(1, Math.round(r.width * k));
  view.height = Math.max(1, Math.round(r.height * k));
  cut = canvas(view.width, view.height);
  checker = null;
  if (!S.userZoomed) fitView();
  else clampView();
  render();
}

function fitView() {
  if (!S.W) return;
  const pad = 12 * dpr();
  const z = Math.min((view.width - pad * 2) / S.W, (view.height - pad * 2) / S.H);
  S.z = z;
  S.ox = (view.width - S.W * z) / 2;
  S.oy = (view.height - S.H * z) / 2;
  S.userZoomed = false;
  $('fit').hidden = true;
}

function fitZoom() {
  const pad = 12 * dpr();
  return Math.min((view.width - pad * 2) / S.W, (view.height - pad * 2) / S.H);
}

function clampView() {
  // 사진이 화면 밖으로 완전히 사라지지 않게
  const m = 60 * dpr();
  const w = S.W * S.z;
  const h = S.H * S.z;
  S.ox = Math.min(view.width - m, Math.max(m - w, S.ox));
  S.oy = Math.min(view.height - m, Math.max(m - h, S.oy));
}

function render() {
  if (raf) return;
  raf = requestAnimationFrame(() => { raf = 0; draw(); });
}

function checkerPattern(ctx) {
  if (checker) return checker;
  const cs = getComputedStyle(document.documentElement);
  const a = cs.getPropertyValue('--check-a').trim() || '#ffffff';
  const b = cs.getPropertyValue('--check-b').trim() || '#e5e7eb';
  const n = Math.round(10 * dpr());
  const c = canvas(n * 2, n * 2);
  const x = c.getContext('2d');
  x.fillStyle = a; x.fillRect(0, 0, n * 2, n * 2);
  x.fillStyle = b; x.fillRect(0, 0, n, n); x.fillRect(n, n, n, n);
  checker = ctx.createPattern(c, 'repeat');
  return checker;
}

// 배경을 ctx의 (x,y,w,h)에 그려요 (w,h = 사진이 놓인 영역)
function drawBackground(ctx, x, y, w, h) {
  const bg = S.bg;
  if (bg === 'transparent') return false;
  if (bg === 'blur') {
    ctx.drawImage(getBlur(), x, y, w, h);
  } else if (bg === 'image' && S.bgImage) {
    const b = S.bgImage;
    const s = Math.max(w / b.width, h / b.height);
    const bw = b.width * s;
    const bh = b.height * s;
    ctx.save();
    ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
    ctx.drawImage(b, x + (w - bw) / 2, y + (h - bh) / 2, bw, bh);
    ctx.restore();
  } else {
    ctx.fillStyle = bg;
    ctx.fillRect(x, y, w, h);
  }
  return true;
}

function getBlur() {
  if (S.blur) return S.blur;
  // 작게 줄였다가 다시 키우면 부드럽게 흐려져요
  let cur = S.src;
  let w = S.W;
  let h = S.H;
  const target = 48;
  while (Math.max(w, h) / 2 > target) {
    w = Math.max(1, Math.round(w / 2));
    h = Math.max(1, Math.round(h / 2));
    const c = canvas(w, h);
    const x = c.getContext('2d');
    x.imageSmoothingQuality = 'high';
    x.drawImage(cur, 0, 0, w, h);
    cur = c;
  }
  const k = Math.min(1, 1024 / Math.max(S.W, S.H));
  const out = canvas(S.W * k, S.H * k);
  const o = out.getContext('2d');
  o.imageSmoothingQuality = 'high';
  o.drawImage(cur, 0, 0, out.width, out.height);
  S.blur = out;
  return out;
}

function shadowParams(scale) {
  const base = Math.max(S.W, S.H) * scale;
  return { blur: base * 0.025, dy: base * 0.012 };
}

function draw() {
  const ctx = vctx;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, view.width, view.height);
  if (!S.src) return;
  const x = S.ox;
  const y = S.oy;
  const w = S.W * S.z;
  const h = S.H * S.z;
  ctx.imageSmoothingEnabled = S.z < 2;
  ctx.imageSmoothingQuality = 'high';

  if (S.peek || !S.hasMask) {
    ctx.drawImage(S.src, x, y, w, h);
    return;
  }

  if (!drawBackground(ctx, x, y, w, h)) {
    ctx.fillStyle = checkerPattern(ctx);
    ctx.fillRect(x, y, w, h);
  }

  // 원본 × 마스크
  const c = cut.getContext('2d');
  c.globalCompositeOperation = 'source-over';
  c.clearRect(0, 0, cut.width, cut.height);
  c.imageSmoothingEnabled = S.z < 2;
  c.imageSmoothingQuality = 'high';
  c.drawImage(S.src, x, y, w, h);
  c.globalCompositeOperation = 'destination-in';
  c.drawImage(S.mask, x, y, w, h);
  c.globalCompositeOperation = 'source-over';

  ctx.save();
  ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip();
  if ($('shadow').checked) {
    const sp = shadowParams(S.z);
    ctx.shadowColor = 'rgba(0,0,0,.45)';
    ctx.shadowBlur = sp.blur;
    ctx.shadowOffsetY = sp.dy;
  }
  ctx.drawImage(cut, 0, 0);
  ctx.restore();
}

// ---------- 붓 ----------
let stamp = null;
function getStamp() {
  // 지름 256px 붓 도장, 부드럽기에 따라 가장자리가 흐려져요
  const key = S.soft;
  if (stamp && stamp.key === key) return stamp.c;
  const n = 256;
  const c = canvas(n, n);
  const x = c.getContext('2d');
  const r = n / 2;
  const hard = 1 - S.soft / 100; // 0(아주 부드러움) ~ 1(딱딱)
  const g = x.createRadialGradient(r, r, 0, r, r, r);
  g.addColorStop(0, 'rgba(255,255,255,1)');
  g.addColorStop(Math.max(0.01, Math.min(0.99, hard * 0.98)), 'rgba(255,255,255,1)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = g;
  x.fillRect(0, 0, n, n);
  stamp = { key, c };
  return c;
}

let stroke = null;

function toImage(px, py) {
  return { x: (px - S.ox) / S.z, y: (py - S.oy) / S.z };
}

function beginStroke(p) {
  if (!S.hasMask || S.busy) return;
  S.backup.getContext('2d').clearRect(0, 0, S.W, S.H);
  S.backup.getContext('2d').drawImage(S.mask, 0, 0);
  const r = (S.brush * dpr()) / S.z / 2; // 화면 기준 붓 크기 → 사진 픽셀
  stroke = { mode: S.tool, r, last: null, minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity };
  strokeTo(p);
}

function strokeTo(p) {
  if (!stroke) return;
  const q = toImage(p.x, p.y);
  const ctx = S.mask.getContext('2d');
  const st = getStamp();
  const { r } = stroke;
  ctx.globalCompositeOperation = stroke.mode === 'erase' ? 'destination-out' : 'source-over';
  const from = stroke.last || q;
  const dx = q.x - from.x;
  const dy = q.y - from.y;
  const dist = Math.hypot(dx, dy);
  const step = Math.max(0.5, r * 0.15);
  const n = stroke.last ? Math.max(1, Math.ceil(dist / step)) : 1;
  for (let i = stroke.last ? 1 : 0; i <= n; i++) {
    const t = n === 0 ? 0 : i / n;
    const cx = from.x + dx * t;
    const cy = from.y + dy * t;
    ctx.drawImage(st, cx - r, cy - r, r * 2, r * 2);
  }
  ctx.globalCompositeOperation = 'source-over';
  stroke.last = q;
  stroke.minX = Math.min(stroke.minX, q.x - r, from.x - r);
  stroke.minY = Math.min(stroke.minY, q.y - r, from.y - r);
  stroke.maxX = Math.max(stroke.maxX, q.x + r, from.x + r);
  stroke.maxY = Math.max(stroke.maxY, q.y + r, from.y + r);
  render();
}

function strokeRect() {
  const x = Math.max(0, Math.floor(stroke.minX) - 1);
  const y = Math.max(0, Math.floor(stroke.minY) - 1);
  const x2 = Math.min(S.W, Math.ceil(stroke.maxX) + 1);
  const y2 = Math.min(S.H, Math.ceil(stroke.maxY) + 1);
  return { x, y, w: x2 - x, h: y2 - y };
}

function endStroke() {
  if (!stroke) return;
  const r = strokeRect();
  stroke = null;
  if (r.w <= 0 || r.h <= 0) return;
  const before = S.backup.getContext('2d').getImageData(r.x, r.y, r.w, r.h);
  const after = S.mask.getContext('2d').getImageData(r.x, r.y, r.w, r.h);
  S.undo.push({ ...r, before, after });
  if (S.undo.length > MAX_HISTORY) S.undo.shift();
  S.redo = [];
  updateButtons();
}

function cancelStroke() {
  // 두 번째 손가락이 닿으면 방금 그은 붓질은 취소하고 확대/이동으로 바꿔요
  if (!stroke) return;
  const r = strokeRect();
  stroke = null;
  if (r.w > 0 && r.h > 0) {
    const ctx = S.mask.getContext('2d');
    ctx.clearRect(r.x, r.y, r.w, r.h);
    ctx.drawImage(S.backup, r.x, r.y, r.w, r.h, r.x, r.y, r.w, r.h);
  }
  render();
}

function undo() {
  const e = S.undo.pop();
  if (!e) return;
  S.mask.getContext('2d').putImageData(e.before, e.x, e.y);
  S.redo.push(e);
  updateButtons();
  render();
}

function redo() {
  const e = S.redo.pop();
  if (!e) return;
  S.mask.getContext('2d').putImageData(e.after, e.x, e.y);
  S.undo.push(e);
  updateButtons();
  render();
}

function resetMask() {
  if (!S.auto) return;
  const ctx = S.mask.getContext('2d');
  ctx.clearRect(0, 0, S.W, S.H);
  ctx.drawImage(S.auto, 0, 0);
  S.undo = [];
  S.redo = [];
  updateButtons();
  render();
  toast('AI가 처음 만든 결과로 돌아왔어요');
}

// ---------- 손가락·마우스 ----------
const pointers = new Map();
let gesture = null; // { kind: 'pan' | 'pinch', ... }
let spaceDown = false;

function localPoint(e) {
  const r = view.getBoundingClientRect();
  const k = view.width / r.width;
  return { x: (e.clientX - r.left) * k, y: (e.clientY - r.top) * k };
}

function startPinch() {
  const [a, b] = [...pointers.values()];
  gesture = {
    kind: 'pinch',
    d: Math.hypot(a.x - b.x, a.y - b.y) || 1,
    cx: (a.x + b.x) / 2,
    cy: (a.y + b.y) / 2,
    z: S.z, ox: S.ox, oy: S.oy,
  };
}

view.addEventListener('pointerdown', (e) => {
  if (!S.src) return;
  view.setPointerCapture(e.pointerId);
  const p = localPoint(e);
  pointers.set(e.pointerId, p);
  if (pointers.size === 2) {
    cancelStroke();
    startPinch();
    return;
  }
  if (pointers.size > 2) return;
  const panning = S.tool === 'move' || e.button === 1 || e.button === 2 || spaceDown || !S.hasMask;
  if (panning) gesture = { kind: 'pan', x: p.x, y: p.y, ox: S.ox, oy: S.oy };
  else { gesture = { kind: 'draw' }; beginStroke(p); }
  e.preventDefault();
});

view.addEventListener('pointermove', (e) => {
  const p = localPoint(e);
  moveCursor(e, p);
  if (!pointers.has(e.pointerId)) return;
  pointers.set(e.pointerId, p);
  if (!gesture) return;
  if (gesture.kind === 'draw') {
    const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
    for (const ce of evs.length ? evs : [e]) strokeTo(localPoint(ce));
  } else if (gesture.kind === 'pan') {
    S.ox = gesture.ox + (p.x - gesture.x);
    S.oy = gesture.oy + (p.y - gesture.y);
    S.userZoomed = true;
    clampView();
    $('fit').hidden = false;
    render();
  } else if (gesture.kind === 'pinch' && pointers.size >= 2) {
    const [a, b] = [...pointers.values()];
    const d = Math.hypot(a.x - b.x, a.y - b.y) || 1;
    const cx = (a.x + b.x) / 2;
    const cy = (a.y + b.y) / 2;
    const z = clampZoom(gesture.z * (d / gesture.d));
    // 처음 두 손가락 가운데 있던 사진 지점이 지금 가운데로 오게
    const ix = (gesture.cx - gesture.ox) / gesture.z;
    const iy = (gesture.cy - gesture.oy) / gesture.z;
    S.z = z;
    S.ox = cx - ix * z;
    S.oy = cy - iy * z;
    S.userZoomed = true;
    clampView();
    $('fit').hidden = false;
    render();
  }
});

function endPointer(e) {
  if (!pointers.has(e.pointerId)) return;
  pointers.delete(e.pointerId);
  if (gesture?.kind === 'draw') endStroke();
  if (pointers.size === 1 && gesture?.kind === 'pinch') {
    // 한 손가락이 남으면 이동으로 이어가요
    const p = [...pointers.values()][0];
    gesture = { kind: 'pan', x: p.x, y: p.y, ox: S.ox, oy: S.oy };
  } else if (pointers.size === 0) {
    gesture = null;
  }
}
view.addEventListener('pointerup', endPointer);
view.addEventListener('pointercancel', (e) => { if (gesture?.kind === 'draw') cancelStroke(); endPointer(e); });
view.addEventListener('pointerleave', () => { $('cursor').hidden = true; });
view.addEventListener('contextmenu', (e) => e.preventDefault());

function clampZoom(z) {
  const f = fitZoom();
  return Math.min(Math.max(z, f * 0.5), Math.max(f * 30, 12));
}

view.addEventListener('wheel', (e) => {
  if (!S.src) return;
  e.preventDefault();
  const p = localPoint(e);
  // 휠(또는 노트북 터치패드 핀치)로 커서 위치를 중심으로 확대·축소
  const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? 400 : 1;
  const z = clampZoom(S.z * Math.exp(-e.deltaY * unit * (e.ctrlKey ? 0.01 : 0.0015)));
  const ix = (p.x - S.ox) / S.z;
  const iy = (p.y - S.oy) / S.z;
  S.z = z;
  S.ox = p.x - ix * z;
  S.oy = p.y - iy * z;
  S.userZoomed = true;
  clampView();
  $('fit').hidden = false;
  render();
  moveCursor(e, p);
}, { passive: false });

function moveCursor(e, p) {
  const c = $('cursor');
  const show = S.hasMask && (S.tool === 'erase' || S.tool === 'restore') && !spaceDown && (e.pointerType !== 'touch' || pointers.size === 1);
  c.hidden = !show;
  if (!show) return;
  const k = view.width / view.getBoundingClientRect().width;
  const d = S.brush;
  c.style.width = c.style.height = `${d}px`;
  c.style.transform = `translate(${p.x / k - d / 2}px, ${p.y / k - d / 2}px)`;
  c.dataset.mode = S.tool;
}

// ---------- 저장 ----------
function maskBounds() {
  const ctx = S.mask.getContext('2d');
  const { data } = ctx.getImageData(0, 0, S.W, S.H);
  let minX = S.W; let minY = S.H; let maxX = -1; let maxY = -1;
  for (let y = 0; y < S.H; y++) {
    let row = y * S.W * 4 + 3;
    for (let x = 0; x < S.W; x++, row += 4) {
      if (data[row] > 10) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  if (maxX < 0) return null;
  return { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
}

async function exportBlob() {
  const full = canvas(S.W, S.H);
  const f = full.getContext('2d');
  f.imageSmoothingQuality = 'high';
  const opaque = drawBackground(f, 0, 0, S.W, S.H);

  const cutFull = canvas(S.W, S.H);
  const c = cutFull.getContext('2d');
  c.drawImage(S.src, 0, 0);
  c.globalCompositeOperation = 'destination-in';
  c.drawImage(S.mask, 0, 0);

  const withShadow = $('shadow').checked;
  if (withShadow) {
    const sp = shadowParams(1);
    f.shadowColor = 'rgba(0,0,0,.45)';
    f.shadowBlur = sp.blur;
    f.shadowOffsetY = sp.dy;
  }
  f.drawImage(cutFull, 0, 0);
  f.shadowColor = 'transparent';

  let out = full;
  if ($('trim').checked) {
    const b = maskBounds();
    if (b) {
      const pad = Math.round(withShadow ? Math.max(S.W, S.H) * 0.05 : Math.max(S.W, S.H) * 0.01);
      const x = Math.max(0, b.x - pad);
      const y = Math.max(0, b.y - pad);
      const w = Math.min(S.W, b.x + b.w + pad) - x;
      const h = Math.min(S.H, b.y + b.h + pad) - y;
      out = canvas(w, h);
      out.getContext('2d').drawImage(full, x, y, w, h, 0, 0, w, h);
    }
  }
  const type = opaque ? 'image/jpeg' : 'image/png';
  const blob = await new Promise((res) => out.toBlob(res, type, 0.95));
  const ext = opaque ? 'jpg' : 'png';
  return { blob, filename: `${S.name}-nobg.${ext}` };
}

async function doSave() {
  if (!S.hasMask) return;
  const btn = $('save');
  btn.disabled = true;
  try {
    const { blob, filename } = await exportBlob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
    toast('저장했어요');
  } catch (err) {
    console.error(err);
    toast('저장하지 못했어요');
  } finally {
    updateButtons();
  }
}

async function doShare() {
  if (!S.hasMask) return;
  try {
    const { blob, filename } = await exportBlob();
    const file = new File([blob], filename, { type: blob.type });
    await navigator.share({ files: [file] });
  } catch (err) {
    if (err?.name !== 'AbortError') toast('공유하지 못했어요');
  }
}

function canShareFiles() {
  try {
    return !!navigator.canShare?.({ files: [new File([new Blob(['x'], { type: 'image/png' })], 'a.png', { type: 'image/png' })] });
  } catch { return false; }
}

// ---------- 버튼·입력 연결 ----------
function updateButtons() {
  const ready = S.hasMask && !S.busy;
  $('save').disabled = !ready;
  $('share').disabled = !ready;
  $('undo').disabled = !ready || !S.undo.length;
  $('redo').disabled = !ready || !S.redo.length;
  $('reset').disabled = !ready || !S.auto;
  for (const i of document.querySelectorAll('input[name="model2"]')) i.disabled = S.busy;
}

function setModel(key, rerun) {
  if (!MODEL_LABEL[key]) return;
  S.model = key;
  save('bgr-model', key);
  for (const i of document.querySelectorAll('input[name="model"], input[name="model2"]')) i.checked = i.value === key;
  if (rerun && S.src) runDetect();
}

function setTool(t) {
  S.tool = t;
  for (const i of document.querySelectorAll('input[name="tool"]')) i.checked = i.value === t;
  stage.dataset.tool = t;
  $('brushRow').classList.toggle('dim', t === 'move');
  $('softRow').classList.toggle('dim', t === 'move');
}

function setBg(bg) {
  S.bg = bg;
  stage.dataset.bg = bg;
  for (const b of document.querySelectorAll('#bgSwatches .sw')) {
    const v = b.dataset.bg;
    const on = v === bg || (b.classList.contains('sw-pick') && bg.startsWith('#') && ![...document.querySelectorAll('#bgSwatches [data-bg]')].some((x) => x.dataset.bg === bg));
    b.setAttribute('aria-pressed', String(on));
  }
  $('bgFile').parentElement.setAttribute('aria-pressed', String(bg === 'image'));
  render();
}

$('file').addEventListener('change', (e) => {
  const f = e.target.files?.[0];
  e.target.value = '';
  if (f) openFile(f);
});

$('again').addEventListener('click', () => $('file').click());
$('save').addEventListener('click', doSave);
$('share').addEventListener('click', doShare);
$('share').hidden = !canShareFiles();
$('undo').addEventListener('click', undo);
$('redo').addEventListener('click', redo);
$('reset').addEventListener('click', resetMask);
$('fit').addEventListener('click', () => { fitView(); render(); });

for (const i of document.querySelectorAll('input[name="model"]')) {
  i.addEventListener('change', () => setModel(i.value, false));
}
for (const i of document.querySelectorAll('input[name="model2"]')) {
  i.addEventListener('change', () => setModel(i.value, true));
}
for (const i of document.querySelectorAll('input[name="tool"]')) {
  i.addEventListener('change', () => setTool(i.value));
}

$('brush').addEventListener('input', (e) => {
  S.brush = +e.target.value;
  $('brushOut').textContent = S.brush;
  save('bgr-brush', S.brush);
});
$('soft').addEventListener('input', (e) => {
  S.soft = +e.target.value;
  $('softOut').textContent = S.soft;
  save('bgr-soft', S.soft);
});

for (const b of document.querySelectorAll('#bgSwatches button[data-bg]')) {
  b.addEventListener('click', () => setBg(b.dataset.bg));
}
$('bgColor').addEventListener('input', (e) => setBg(e.target.value));
$('bgColor').addEventListener('click', (e) => setBg(e.target.value));
$('bgFile').addEventListener('change', async (e) => {
  const f = e.target.files?.[0];
  e.target.value = '';
  if (!f) return;
  try {
    S.bgImage = await createImageBitmap(f, { imageOrientation: 'from-image' });
  } catch {
    try { S.bgImage = await loadViaImg(f); } catch { toast('이 사진은 열 수 없어요'); return; }
  }
  setBg('image');
});
$('shadow').addEventListener('change', render);

// 원본 잠깐 보기 (누르고 있는 동안)
const peek = $('peek');
const peekOn = (e) => { e.preventDefault(); S.peek = true; peek.classList.add('on'); render(); };
const peekOff = () => { if (!S.peek) return; S.peek = false; peek.classList.remove('on'); render(); };
peek.addEventListener('pointerdown', peekOn);
peek.addEventListener('pointerup', peekOff);
peek.addEventListener('pointerleave', peekOff);
peek.addEventListener('pointercancel', peekOff);
peek.addEventListener('contextmenu', (e) => e.preventDefault());

// 끌어다 놓기·붙여넣기
const drop = $('drop');
window.addEventListener('dragover', (e) => { e.preventDefault(); drop.classList.add('over'); });
window.addEventListener('dragleave', (e) => { if (!e.relatedTarget) drop.classList.remove('over'); });
window.addEventListener('drop', (e) => {
  e.preventDefault();
  drop.classList.remove('over');
  const f = [...(e.dataTransfer?.files || [])].find((x) => x.type.startsWith('image/'));
  if (f) openFile(f);
});
window.addEventListener('paste', (e) => {
  const item = [...(e.clipboardData?.items || [])].find((x) => x.type.startsWith('image/'));
  const f = item?.getAsFile();
  if (f) openFile(new File([f], f.name || 'pasted.png', { type: f.type }));
});

// 단축키
window.addEventListener('keydown', (e) => {
  if (e.target.closest?.('input, textarea, select') && e.target.type !== 'range' && e.target.type !== 'radio') return;
  const mod = e.ctrlKey || e.metaKey;
  if (mod && e.key.toLowerCase() === 'z') { e.preventDefault(); if (e.shiftKey) redo(); else undo(); }
  else if (mod && e.key.toLowerCase() === 'y') { e.preventDefault(); redo(); }
  else if (mod && e.key.toLowerCase() === 's') { e.preventDefault(); doSave(); }
  else if (e.key === ' ' && app.dataset.state === 'editor') { spaceDown = true; $('cursor').hidden = true; e.preventDefault(); }
  else if (!mod && e.key === '[') { $('brush').value = S.brush = Math.max(4, S.brush - 6); $('brushOut').textContent = S.brush; }
  else if (!mod && e.key === ']') { $('brush').value = S.brush = Math.min(200, S.brush + 6); $('brushOut').textContent = S.brush; }
  else if (!mod && (e.key === 'e' || e.key === 'E')) setTool('erase');
  else if (!mod && (e.key === 'r' || e.key === 'R')) setTool('restore');
  else if (!mod && (e.key === 'h' || e.key === 'H' || e.key === 'v' || e.key === 'V')) setTool('move');
});
window.addEventListener('keyup', (e) => { if (e.key === ' ') spaceDown = false; });

new ResizeObserver(() => { if (app.dataset.state === 'editor') resizeView(); }).observe(stage);
matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => { checker = null; render(); });

// ---------- 시작 ----------
(function init() {
  try {
    const b = +localStorage.getItem('bgr-brush');
    const s = localStorage.getItem('bgr-soft');
    if (b >= 4 && b <= 200) S.brush = b;
    if (s !== null && +s >= 0 && +s <= 100) S.soft = +s;
  } catch { /* 무시 */ }
  $('brush').value = S.brush; $('brushOut').textContent = S.brush;
  $('soft').value = S.soft; $('softOut').textContent = S.soft;
  setModel(S.model, false);
  setTool('move');
  setBg('transparent');
  if (matchMedia('(pointer: coarse)').matches) $('dropHint').textContent = '사진첩에서 고르거나 바로 찍을 수 있어요';
  else $('touchHint').textContent = '휠로 확대, 스페이스바를 누른 채 끌면 이동. 단축키: E 지우기 · R 되살리기 · [ ] 붓 크기 · Ctrl+Z 되돌리기';
  updateButtons();

  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
})();
