package com.memowallpaper

import java.time.LocalDate

data class Template(val name: String, val description: String, val make: () -> Memo)

/** 새 메모를 만들 때 고르는 템플릿 */
val TEMPLATES: List<Template> = listOf(
    Template("빈 메모", "처음부터 직접 만들기") {
        Memo(text = "")
    },
    Template("체크리스트", "챙길 것·할 일을 줄마다") {
        Memo(text = "할 일 1\n할 일 2\n할 일 3", prefix = Prefix.CHECK, sizePct = 7f, posPct = 55f)
    },
    Template("한 줄 다짐", "가운데에 짧은 문장 하나") {
        Memo(
            text = "오늘도 한 걸음",
            prefix = Prefix.NONE,
            align = Align.CENTER,
            sizePct = 9f,
            posPct = 52f,
            bgColor = 0xFF0F172A.toInt(),
        )
    },
    Template("큰 글씨 한 단어", "멀리서도 보이는 큰 글씨") {
        Memo(
            text = "집중",
            prefix = Prefix.NONE,
            align = Align.CENTER,
            sizePct = 24f,
            posPct = 52f,
            bgColor = 0xFFFDE68A.toInt(),
        )
    },
    Template("D-day 카운트", "남은 날이 매일 자동으로 줄어요 (날짜는 편집에서 바꿔요)") {
        Memo(
            title = "D-day",
            text = "시험\n${TextTokens.dday(LocalDate.now().plusDays(30))}\n\n${TextTokens.TODAY}",
            prefix = Prefix.NONE,
            align = Align.CENTER,
            sizePct = 11f,
            posPct = 50f,
            bgColor = 0xFF7F1D1D.toInt(),
        )
    },
    Template("며칠째 (카운트업)", "시작한 날부터 며칠째인지 매일 늘어나요") {
        Memo(
            title = "며칠째",
            text = "금연\n${TextTokens.countUp(LocalDate.now())}",
            prefix = Prefix.NONE,
            align = Align.CENTER,
            sizePct = 11f,
            posPct = 50f,
            bgColor = 0xFF14532D.toInt(),
        )
    },
)
