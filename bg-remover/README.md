# 배경 지우개

사진의 배경을 AI로 지워 **투명 PNG**로 저장하는 웹앱(PWA)이에요.
AI가 **내 폰·컴퓨터 브라우저 안에서** 돌아가서, 사진이 서버로 올라가지 않아요.

## 기능

- 사진 고르기 / 카메라로 찍기 / 끌어다 놓기 / 붙여넣기(Ctrl+V)
- AI 모델 두 가지
  - **모든 사물** (기본, 추천): 사람·동물·물건 모두. BiRefNet lite 512, 약 100MB
  - **사람만**: 인물 전용, 가볍고 빠름. MODNet, 약 7MB
  - 모델은 처음 한 번만 받고 브라우저에 저장돼서 다음부터는 바로 시작해요
- **손으로 다듬기**: 지우기 / 되살리기 붓, 붓 크기·부드럽기, 되돌리기·다시, 처음 결과로
- 확대·이동: 두 손가락 핀치 / 마우스 휠, 스페이스바+드래그
- **원본** 버튼을 누르고 있으면 원래 사진을 비교해서 볼 수 있어요
- 배경 바꾸기: 투명, 단색(직접 고르기 포함), 원본 흐리게, 다른 사진
- 피사체에 딱 맞게 자르기, 그림자 넣기
- 저장(투명이면 PNG, 배경을 넣으면 JPG) · 공유(지원하는 폰에서)
- 홈 화면에 앱으로 설치, 한 번 열어 본 뒤에는 오프라인에서도 동작
- 가능하면 그래픽 가속(WebGPU)을 쓰고, 안 되면 CPU(WASM)로 처리해요

단축키(컴퓨터): `E` 지우기 · `R` 되살리기 · `H` 이동 · `[` `]` 붓 크기 · `Ctrl+Z` 되돌리기 · `Ctrl+Shift+Z` 다시 · `Ctrl+S` 저장

## 파일 구성

| 파일 | 역할 |
|---|---|
| `index.html` | 화면 구조 |
| `style.css` | 스타일 (라이트/다크 모드, 폰·컴퓨터 레이아웃) |
| `app.js` | 사진 열기, 화면 그리기, 확대·이동, 붓, 되돌리기, 배경 합성, 저장·공유 |
| `worker.js` | AI 배경 인식 (Web Worker, [transformers.js](https://github.com/huggingface/transformers.js) 4.3.2) |
| `manifest.webmanifest`, `sw.js`, `icons/` | PWA 설치 및 오프라인 지원 |

빌드 과정 없이 정적 파일만으로 동작해요. AI 엔진은 jsDelivr CDN에서, 모델 파일은 Hugging Face에서 받아요.

## 로컬에서 실행

```bash
cd bg-remover
python3 -m http.server 8000
```

브라우저에서 <http://localhost:8000> 을 열어요. (Web Worker와 서비스 워커 때문에 `file://`로 직접 열면 안 돼요.)

폰에서 쓰려면 HTTPS로 올려야 해요. 예: GitHub Pages(저장소 Settings → Pages), Netlify, Cloudflare Pages 등에 `bg-remover/` 폴더를 그대로 올리면 돼요.

## 처리 속도 (참고)

4코어 CPU(WebGPU 없음)에서 측정한 값이에요. 첫 실행 때는 모델 받는 시간이 더해져요.

- 사람만: 사진 한 장에 약 2초
- 모든 사물: 사진 한 장에 약 10초. 폰 CPU로는 더 걸릴 수 있고, WebGPU를 지원하는 최신 크롬·사파리에서는 더 빨라요.
- 편집·저장 해상도는 긴 변 4096px까지예요 (더 큰 사진은 줄여서 처리).

## 모델 라이선스

- [studioludens/birefnet-lite-512](https://huggingface.co/studioludens/birefnet-lite-512) (BiRefNet) — MIT
- [Xenova/modnet](https://huggingface.co/Xenova/modnet) (MODNet) — Apache-2.0

`worker.js`에서 두 모델 모두 특정 커밋(revision)으로 고정해 두었어요.

두 모델 모두 상업적으로 써도 되는 라이선스예요.
