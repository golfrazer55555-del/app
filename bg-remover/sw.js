'use strict';

// 앱 파일을 바꾸면 버전을 올려주세요.
const CACHE = 'bg-remover-v2';
// AI 엔진(jsdelivr)도 캐시해서 오프라인에서도 열리게 해요. 모델 파일은 transformers.js가 따로 캐시해요.
const CDN = 'https://cdn.jsdelivr.net/';
const ASSETS = [
  './',
  './index.html',
  './style.css',
  './app.js',
  './worker.js',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('bg-remover-') && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // 버전이 고정된 CDN 파일: 캐시 우선
  if (req.url.startsWith(CDN)) {
    event.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })),
    );
    return;
  }

  if (url.origin !== self.location.origin) return;

  // 앱 파일: 네트워크 우선, 실패하면 캐시
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('./index.html'))),
  );
});
