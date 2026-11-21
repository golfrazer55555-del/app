// 배경 인식 전용 작업자(Web Worker).
// AI 모델을 여기서 돌려서 화면이 멈추지 않게 해요.
// 사진은 이 기기 안에서만 처리되고 어디에도 보내지 않아요.

import { pipeline, env, RawImage } from 'https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.3.2/dist/transformers.min.js';

env.allowLocalModels = false; // 모델은 Hugging Face에서 받아 브라우저 캐시에 보관

// revision: 모델 저장소의 특정 커밋으로 고정해서, 저장소가 바뀌어도 앱 동작이 달라지지 않게 해요.
const MODELS = {
  // 사람·동물·물건 등 모든 피사체 (ormbg, Apache-2.0, 약 44MB — 처음 한 번만 받아요)
  // BiRefNet은 품질이 조금 더 좋지만 메모리를 2GB 넘게 써서 폰에서 탭이 꺼졌어요. ormbg는 약 1GB.
  general: { id: 'onnx-community/ormbg-ONNX', revision: '034e2d884afbab897e10e78fc5bb566b29533fd6', dtype: { wasm: 'q8', webgpu: 'fp16' } },
  // 인물 전용: 작고 빨라요 (MODNet, Apache-2.0, 약 7MB). 사람이 아니면 잘 못 찾아요.
  portrait: { id: 'Xenova/modnet', revision: 'fa2fa546052fba4c08921230a26cc69a333fca12', dtype: { wasm: 'q8', webgpu: 'fp32' } },
};

const pipes = new Map();
let devicePromise;

function pickDevice() {
  devicePromise ??= (async () => {
    // 폰은 WebGPU 동작이 기기마다 달라서, 확인된 CPU(WASM) 방식만 써요
    const ua = self.navigator?.userAgent || '';
    const mobile = self.navigator?.userAgentData?.mobile ?? /Android|iPhone|iPad|Mobile/i.test(ua);
    if (mobile) return 'wasm';
    try {
      if (self.navigator?.gpu) {
        const adapter = await self.navigator.gpu.requestAdapter();
        if (adapter) return 'webgpu';
      }
    } catch { /* WebGPU를 못 쓰면 WASM으로 */ }
    return 'wasm';
  })();
  return devicePromise;
}

async function loadPipe(key) {
  if (pipes.has(key)) return pipes.get(key);
  const model = MODELS[key];
  if (!model) throw new Error(`알 수 없는 모델: ${key}`);

  const files = new Map(); // 파일별 진행률 → 전체 진행률
  const onProgress = (p) => {
    if (p.status === 'progress' && p.total) {
      files.set(p.file, { loaded: p.loaded, total: p.total });
      let loaded = 0;
      let total = 0;
      for (const f of files.values()) { loaded += f.loaded; total += f.total; }
      self.postMessage({ type: 'download', key, loaded, total });
    }
  };

  const make = async (device) => pipeline('background-removal', model.id, {
    device,
    revision: model.revision,
    dtype: model.dtype[device],
    progress_callback: onProgress,
  });

  const promise = (async () => {
    const device = await pickDevice();
    try {
      const pipe = await make(device);
      return { pipe, device };
    } catch (err) {
      if (device === 'wasm') throw err;
      // 일부 기기는 WebGPU가 있어도 모델을 못 돌려요 → WASM으로 다시 시도
      console.warn('WebGPU 실패, WASM으로 전환', err);
      devicePromise = Promise.resolve('wasm');
      return { pipe: await make('wasm'), device: 'wasm' };
    }
  })();

  pipes.set(key, promise);
  promise.catch(() => pipes.delete(key));
  return promise;
}

// pixels: 화면 쪽에서 보낸 RGBA 픽셀 (app.js의 DETECT_SIDE 이하로 줄인 것)
async function run({ id, key, pixels, width: w, height: h }) {
  const { pipe, device } = await loadPipe(key);
  self.postMessage({ type: 'status', id, stage: 'infer', device });

  const image = new RawImage(pixels, w, h, 4).rgb();

  const out = await pipe(image); // RGBA, 알파 = 피사체일 확률
  const { width, height, data } = out;
  const mask = new Uint8ClampedArray(width * height);
  for (let i = 0, j = 3; i < mask.length; i++, j += 4) mask[i] = data[j];

  self.postMessage({ type: 'mask', id, width, height, mask, device }, [mask.buffer]);
}

self.addEventListener('message', async (e) => {
  const msg = e.data;
  try {
    if (msg.type === 'warmup') await loadPipe(msg.key);
    else if (msg.type === 'run') await run(msg);
  } catch (err) {
    console.error(err);
    self.postMessage({ type: 'error', id: msg.id, key: msg.key, message: String(err?.message || err) });
  }
});
