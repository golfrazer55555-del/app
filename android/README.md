# 메모 배경 (안드로이드)

메모를 단색 이미지로 만들어 **잠금화면에 바로 적용**하는 앱이에요.
갤러리를 거치지 않고 버튼 한 번이면 바뀌어요.

## 기능

- **메모 프리셋 여러 개**: 카드를 누르면 바로 적용, 길게 누르거나 연필 버튼을 누르면 편집
- **적용할 곳 선택**: 잠금화면 / 홈화면 / 둘 다
- **편집**: 줄 앞 기호(없음·점·체크·번호), 배경색 16가지와 직접 고르기, 글자 크기, 굵게, 정렬, 세로 위치, 오늘 날짜 넣기
- **실시간 미리보기**: 갤럭시 잠금화면의 시계·바로가기에 가려지는 영역 표시와 겹침 경고
- **알림창 빠른 설정 타일 '메모 배경'**
  - 메모가 2개 이하이거나 폰이 잠겨 있으면: 누를 때마다 다음 메모로 바로 적용
  - 메모가 3개 이상이면: 목록에서 골라 적용
  - 타일을 길게 누르면 앱이 열려요
- **앱 아이콘 바로가기**: 아이콘을 길게 누르면 앞쪽 메모 4개가 나와 바로 적용돼요. 홈 화면에 끌어다 둘 수도 있어요
- 글자색은 배경 밝기에 맞춰 자동(흰색/검정), 폰 해상도에 맞춰 선명하게 렌더링

## 설치

1. `memo-wallpaper-<버전>.apk`를 폰에 받아요
2. 파일을 열고, 처음이면 **출처를 알 수 없는 앱 설치 허용**을 켜요 (파일을 연 앱, 예: 내 파일·크롬)
3. 설치 후 '메모 배경' 앱을 실행해요

> 같은 서명 키(`keystore/`)로 빌드하므로 새 버전 APK를 그냥 덮어 설치하면 메모가 유지돼요.

### 잠금화면이 안 바뀌면 (갤럭시)
설정 → 잠금화면 → 배경화면 서비스(다이내믹 잠금화면 등)를 **사용 안 함**으로 바꿔 주세요.

## 빌드

필요한 것: JDK 17+, Android SDK (platform 35, build-tools 35)

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # 렌더링·저장 테스트, 화면 캡처 → app/build/screenshots/
```

## 구조

| 파일 | 역할 |
|---|---|
| `WallpaperRenderer.kt` | 메모 → 이미지 그리기 (줄바꿈, 기호, 정렬, 글자색) |
| `WallpaperApplier.kt` | 화면 해상도로 렌더링 후 `WallpaperManager`로 잠금/홈 화면에 적용 |
| `MemoStore.kt` | 메모 목록·설정 저장 (SharedPreferences) |
| `MemoTileService.kt` | 알림창 빠른 설정 타일 |
| `Shortcuts.kt`, `ApplyShortcutActivity.kt` | 앱 아이콘 바로가기 |
| `ui/` | 목록·편집 화면 (Jetpack Compose) |

## 서명 키에 대해

`keystore/memo-wallpaper.keystore`는 개인 설치용 공용 키예요(비밀번호 `android`, 디버그 키와 같은 성격).
어디서 빌드해도 같은 서명이 나와서 업데이트가 쉬워요. 플레이스토어에 올릴 계획이 생기면 별도의 비공개 키로 바꿔야 해요.
