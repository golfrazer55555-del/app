'use strict';

/* =========================================================
 * 메모 배경화면 — 단색 배경에 메모를 그려 PNG로 내보냅니다.
 * ========================================================= */

const STORAGE_KEY = 'memo-wallpaper:v1';
const FONT_STACK =
  '"Pretendard", "Noto Sans KR", "Noto Sans CJK KR", system-ui, -apple-system, ' +
  '"Apple SD Gothic Neo", "Malgun Gothic", sans-serif';
const LINE_HEIGHT = 1.45;
const PAD_X_RATIO = 0.08;
const MAX_SIDE = 4096;

const PRESET_COLORS = [
  '#0f172a', // 거의 검정
  '#1e3a8a', // 남색
  '#14532d', // 진초록
  '#7f1d1d', // 와인
  '#4c1d95', // 보라
  '#374151', // 회색
  '#f5f5f4', // 미색
  '#fde68a', // 노랑
  '#bfdbfe', // 하늘
  '#fecdd3', // 분홍
  '#bbf7d0', // 민트
];

// 가림 영역(대략적인 값, 화면 높이에 대한 비율)
const GUIDES = {
  lock: {
    top: { from: 0, to: 0.30, label: '시계 · 알림 영역' },
    bottom: { from: 0.88, to: 1, label: '바로가기 · 지문' },
  },
  home: {
    top: { from: 0, to: 0.05, label: '상태 표시줄' },
    bottom: { from: 0.80, to: 1, label: '앱 아이콘 · 검색바' },
  },
};

const DEFAULTS = {
  text: '약 먹기\n지갑 · 열쇠 · 폰 챙기기\n나가기 전에 가스 밸브 확인',
  prefix: 'check',
  bg: '#1e3a8a',
  size: 7,
  bold: true,
  align: 'left',
  pos: 55,
  guide: 'lock',
  res: 'auto',
  customW: 1080,
  customH: 2400,
};

/* ---------- 상태 ---------- */

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS };
    return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULTS };
  }
}

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* 저장 불가 환경(시크릿 모드 등)은 무시 */
  }
}

const state = loadState();

/* ---------- 색상 ---------- */

function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex);
  if (!m) return [0, 0, 0];
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function luminance([r, g, b]) {
  const f = (c) => {
    c /= 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
}

function contrast(l1, l2) {
  const [a, b] = l1 > l2 ? [l1, l2] : [l2, l1];
  return (a + 0.05) / (b + 0.05);
}

/** 배경색 대비가 더 큰 글자색(흰색 / 거의 검정)을 고릅니다. */
function textColorFor(bg) {
  const L = luminance(hexToRgb(bg));
  const DARK = '#111111';
  const LIGHT = '#ffffff';
  return contrast(L, luminance(hexToRgb(LIGHT))) >= contrast(L, luminance(hexToRgb(DARK)))
    ? LIGHT
    : DARK;
}

/* ---------- 해상도 ---------- */

function detectScreen() {
  const dpr = window.devicePixelRatio || 1;
  let w = Math.round(window.screen.width * dpr);
  let h = Math.round(window.screen.height * dpr);
  if (w > h) [w, h] = [h, w]; // 항상 세로 방향
  return clampSize(w, h);
}

function clampSize(w, h) {
  w = Math.max(320, Math.min(MAX_SIDE, Math.round(w) || 1080));
  h = Math.max(320, Math.min(MAX_SIDE, Math.round(h) || 2400));
  return { w, h };
}

function getResolution() {
  if (state.res === 'auto') return detectScreen();
  if (state.res === 'custom') return clampSize(state.customW, state.customH);
  const [w, h] = state.res.split('x').map(Number);
  return clampSize(w, h);
}

/* ---------- 텍스트 줄바꿈 ---------- */

/** 공백 단위로 줄바꿈하고, 한 단어가 너무 길면 글자 단위로 자릅니다. */
function wrapText(ctx, text, maxW) {
  const tokens = text.split(/(\s+)/).filter(Boolean);
  const lines = [];
  let cur = '';

  const pushCur = () => {
    lines.push(cur.trimEnd());
    cur = '';
  };

  for (const tok of tokens) {
    if (ctx.measureText(cur + tok).width <= maxW) {
      cur += tok;
      continue;
    }
    if (/^\s+$/.test(tok)) {
      pushCur();
      continue;
    }
    if (cur.trim()) pushCur();
    if (ctx.measureText(tok).width <= maxW) {
      cur = tok;
      continue;
    }
    for (const ch of Array.from(tok)) {
      if (cur && ctx.measureText(cur + ch).width > maxW) {
        pushCur();
        cur = ch;
      } else {
        cur += ch;
      }
    }
  }
  if (cur || lines.length === 0) pushCur();
  return lines;
}

/* ---------- 그리기 ---------- */

function drawPrefix(ctx, kind, index, x, cy, fontPx, color, width) {
  ctx.save();
  ctx.fillStyle = color;
  ctx.strokeStyle = color;
  if (kind === 'dot') {
    ctx.beginPath();
    ctx.arc(x + fontPx * 0.25, cy, fontPx * 0.16, 0, Math.PI * 2);
    ctx.fill();
  } else if (kind === 'check') {
    const s = fontPx * 0.62;
    const r = s * 0.2;
    ctx.lineWidth = Math.max(2, fontPx * 0.075);
    ctx.beginPath();
    ctx.roundRect(x + ctx.lineWidth / 2, cy - s / 2, s, s, r);
    ctx.stroke();
  } else if (kind === 'num') {
    // 번호는 오른쪽 정렬해서 "9."과 "10."의 점 위치를 맞춤
    ctx.textAlign = 'right';
    ctx.fillText(`${index}.`, x + width - fontPx * 0.4, cy);
  }
  ctx.restore();
}

function prefixWidth(ctx, kind, count, fontPx) {
  if (kind === 'dot') return fontPx * 0.85;
  if (kind === 'check') return fontPx * 1.05;
  if (kind === 'num') return ctx.measureText(`${count}.`).width + fontPx * 0.4;
  return 0;
}

/**
 * 캔버스에 배경화면을 그립니다.
 * @returns {{top:number,bottom:number}} 글자 영역의 세로 범위(0~1 비율)
 */
function render(canvas, s) {
  const { w: W, h: H } = getResolution();
  if (canvas.width !== W) canvas.width = W;
  if (canvas.height !== H) canvas.height = H;

  const ctx = canvas.getContext('2d');
  ctx.fillStyle = s.bg;
  ctx.fillRect(0, 0, W, H);

  const fg = textColorFor(s.bg);
  const fontPx = Math.round(W * (s.size / 100));
  ctx.font = `${s.bold ? 700 : 400} ${fontPx}px ${FONT_STACK}`;
  ctx.textBaseline = 'middle';
  ctx.fillStyle = fg;

  const padX = Math.round(W * PAD_X_RATIO);
  const maxW = W - padX * 2;
  const lineH = fontPx * LINE_HEIGHT;

  const srcLines = s.text.replace(/\r/g, '').split('\n');
  // 앞뒤 빈 줄은 위치 계산을 망치므로 제거
  while (srcLines.length && !srcLines[0].trim()) srcLines.shift();
  while (srcLines.length && !srcLines[srcLines.length - 1].trim()) srcLines.pop();

  const itemCount = srcLines.filter((l) => l.trim()).length;
  const preW = prefixWidth(ctx, s.prefix, itemCount, fontPx);

  /** @type {{text:string, indent:number, prefixIndex:number}[]} */
  const rows = [];
  let n = 0;
  for (const line of srcLines) {
    const t = line.trim();
    if (!t) {
      rows.push({ text: '', indent: 0, prefixIndex: 0, blank: true });
      continue;
    }
    n += 1;
    const wrapped = wrapText(ctx, t, maxW - preW);
    // 왼쪽 정렬일 때만 이어지는 줄을 기호 너비만큼 들여씀
    const hang = s.align === 'left';
    wrapped.forEach((part, i) => {
      rows.push({ text: part, indent: i === 0 || hang ? preW : 0, prefixIndex: i === 0 ? n : 0 });
    });
  }

  if (!rows.length) return null;

  // 빈 줄은 절반 높이
  const rowH = (r) => (r.blank ? lineH * 0.5 : lineH);
  const totalH = rows.reduce((sum, r) => sum + rowH(r), 0);
  let y = H * (s.pos / 100) - totalH / 2;
  y = Math.max(H * 0.02, Math.min(y, H * 0.98 - totalH));
  const top = y;

  for (const r of rows) {
    const h = rowH(r);
    if (!r.blank) {
      const cy = y + h / 2;
      const lineW = r.indent + ctx.measureText(r.text).width;
      let x = padX;
      if (s.align === 'center') x = (W - lineW) / 2;
      else if (s.align === 'right') x = W - padX - lineW;

      if (r.prefixIndex && s.prefix !== 'none') {
        drawPrefix(ctx, s.prefix, r.prefixIndex, x, cy, fontPx, fg, preW);
      }
      ctx.textAlign = 'left';
      ctx.fillText(r.text, x + r.indent, cy);
    }
    y += h;
  }

  return { top: top / H, bottom: y / H };
}

/* ---------- DOM ---------- */

const $ = (id) => document.getElementById(id);
const canvas = $('canvas');
const phone = $('phone');
const els = {
  text: $('text'),
  size: $('size'),
  sizeOut: $('sizeOut'),
  pos: $('pos'),
  posOut: $('posOut'),
  bold: $('bold'),
  res: $('res'),
  resAutoOpt: $('resAutoOpt'),
  customRes: $('customRes'),
  customW: $('customW'),
  customH: $('customH'),
  swatches: $('swatches'),
  customColor: $('customColor'),
  zoneTop: $('zoneTop'),
  zoneBottom: $('zoneBottom'),
  zoneTopLabel: $('zoneTopLabel'),
  zoneBottomLabel: $('zoneBottomLabel'),
  overlapWarn: $('overlapWarn'),
  saveBtn: $('saveBtn'),
  shareBtn: $('shareBtn'),
  insertDate: $('insertDate'),
  toast: $('toast'),
};

function buildSwatches() {
  const customLabel = els.swatches.querySelector('.swatch-custom');
  for (const c of PRESET_COLORS) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'swatch';
    b.style.background = c;
    b.dataset.color = c;
    b.setAttribute('aria-label', `배경색 ${c}`);
    b.addEventListener('click', () => update({ bg: c }));
    els.swatches.insertBefore(b, customLabel);
  }
}

function syncControls() {
  els.text.value = state.text;
  els.size.value = state.size;
  els.pos.value = state.pos;
  els.bold.checked = state.bold;
  els.res.value = state.res;
  els.customW.value = state.customW;
  els.customH.value = state.customH;
  els.customColor.value = state.bg;
  for (const name of ['prefix', 'align', 'guide']) {
    const input = document.querySelector(`input[name="${name}"][value="${state[name]}"]`);
    if (input) input.checked = true;
  }
}

function updateIndicators() {
  els.sizeOut.textContent = `${state.size}`;
  const p = state.pos;
  els.posOut.textContent = p < 40 ? '위쪽' : p > 60 ? '아래쪽' : '가운데';
  els.customRes.hidden = state.res !== 'custom';

  let matched = false;
  els.swatches.querySelectorAll('button.swatch').forEach((b) => {
    const on = b.dataset.color.toLowerCase() === state.bg.toLowerCase();
    matched ||= on;
    b.setAttribute('aria-pressed', String(on));
  });
  els.swatches.querySelector('.swatch-custom').classList.toggle('active', !matched);

  const auto = detectScreen();
  els.resAutoOpt.textContent = `내 화면에 맞춤 (${auto.w} × ${auto.h})`;
}

function updateGuides(bounds) {
  const g = GUIDES[state.guide];
  const zones = [
    [els.zoneTop, els.zoneTopLabel, g?.top],
    [els.zoneBottom, els.zoneBottomLabel, g?.bottom],
  ];
  for (const [el, label, z] of zones) {
    el.hidden = !z;
    if (!z) continue;
    el.style.top = `${z.from * 100}%`;
    el.style.height = `${(z.to - z.from) * 100}%`;
    label.textContent = z.label;
  }

  let msg = '';
  if (g && bounds) {
    if (bounds.top < g.top.to) msg = `글자 일부가 ${g.top.label}에 가려질 수 있어요.`;
    else if (bounds.bottom > g.bottom.from) msg = `글자 일부가 ${g.bottom.label}에 가려질 수 있어요.`;
  }
  els.overlapWarn.hidden = !msg;
  els.overlapWarn.textContent = msg;
}

let rafId = 0;
function scheduleRender() {
  cancelAnimationFrame(rafId);
  rafId = requestAnimationFrame(() => {
    const { w, h } = getResolution();
    phone.style.setProperty('--ar', `${w} / ${h}`);
    const bounds = render(canvas, state);
    updateGuides(bounds);
  });
}

function update(patch) {
  Object.assign(state, patch);
  saveState();
  updateIndicators();
  scheduleRender();
}

/* ---------- 내보내기 ---------- */

function fileName() {
  const d = new Date();
  const p = (v) => String(v).padStart(2, '0');
  return `memo-wallpaper-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}.png`;
}

function canvasBlob() {
  render(canvas, state); // 최신 상태로 즉시 그리기
  return new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('이미지 생성 실패'))), 'image/png');
  });
}

function download(blob, name) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

let toastTimer = 0;
function toast(msg) {
  els.toast.textContent = msg;
  els.toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => els.toast.classList.remove('show'), 2800);
}

async function withBusy(btn, fn) {
  btn.disabled = true;
  try {
    await fn();
  } finally {
    btn.disabled = false;
  }
}

function supportsFileShare() {
  try {
    const f = new File([new Blob()], 'a.png', { type: 'image/png' });
    return !!(navigator.canShare && navigator.canShare({ files: [f] }));
  } catch {
    return false;
  }
}

function todayLabel() {
  const d = new Date();
  const days = ['일', '월', '화', '수', '목', '금', '토'];
  return `${d.getMonth() + 1}월 ${d.getDate()}일 (${days[d.getDay()]})`;
}

/* ---------- 이벤트 ---------- */

function bindEvents() {
  els.text.addEventListener('input', () => update({ text: els.text.value }));
  els.size.addEventListener('input', () => update({ size: Number(els.size.value) }));
  els.pos.addEventListener('input', () => update({ pos: Number(els.pos.value) }));
  els.bold.addEventListener('change', () => update({ bold: els.bold.checked }));
  els.res.addEventListener('change', () => update({ res: els.res.value }));
  els.customW.addEventListener('change', () => update({ customW: Number(els.customW.value) }));
  els.customH.addEventListener('change', () => update({ customH: Number(els.customH.value) }));
  els.customColor.addEventListener('input', () => update({ bg: els.customColor.value }));

  for (const name of ['prefix', 'align', 'guide']) {
    document.querySelectorAll(`input[name="${name}"]`).forEach((input) => {
      input.addEventListener('change', () => update({ [name]: input.value }));
    });
  }

  els.insertDate.addEventListener('click', () => {
    const ta = els.text;
    const label = todayLabel();
    const start = ta.selectionStart ?? ta.value.length;
    const end = ta.selectionEnd ?? ta.value.length;
    ta.setRangeText(label, start, end, 'end');
    ta.focus();
    update({ text: ta.value });
  });

  els.saveBtn.addEventListener('click', () =>
    withBusy(els.saveBtn, async () => {
      try {
        download(await canvasBlob(), fileName());
        toast('이미지를 저장했어요. 갤러리에서 배경화면으로 설정하세요.');
      } catch (e) {
        toast('저장에 실패했어요.');
        console.error(e);
      }
    }),
  );

  els.shareBtn.addEventListener('click', () =>
    withBusy(els.shareBtn, async () => {
      let blob;
      try {
        blob = await canvasBlob();
        const file = new File([blob], fileName(), { type: 'image/png' });
        await navigator.share({ files: [file], title: '메모 배경화면' });
      } catch (e) {
        if (e && e.name === 'AbortError') return; // 사용자가 취소
        console.error(e);
        if (blob) {
          download(blob, fileName());
          toast('공유가 안 돼서 대신 이미지를 저장했어요.');
        } else {
          toast('이미지를 만들지 못했어요.');
        }
      }
    }),
  );

  window.addEventListener('resize', () => {
    if (state.res === 'auto') {
      updateIndicators();
      scheduleRender();
    }
  });
}

/* ---------- 시작 ---------- */

function init() {
  buildSwatches();
  syncControls();
  updateIndicators();
  bindEvents();
  els.shareBtn.hidden = !supportsFileShare();

  // 웹폰트가 늦게 적용되는 경우 대비해 한 번 더 그리기
  scheduleRender();
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(scheduleRender);

  if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    navigator.serviceWorker.register('sw.js').catch((e) => console.warn('SW 등록 실패', e));
  }
}

init();
