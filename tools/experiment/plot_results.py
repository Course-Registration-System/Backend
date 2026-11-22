"""성적 조회 실험 결과(build/experiment/<규모>/results.csv)를 규모별 응답 시간 차트로 그린다.

사용: python -X utf8 tools/experiment/plot_results.py [--scales S M L]
결과: build/experiment/response_time.png, response_time.svg
차트만 matplotlib 이 필요하다(pip install matplotlib). 측정 도구(grade_experiment.py)는 표준 라이브러리만 쓴다.
"""
import argparse
import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "build" / "experiment"

# 비교군마다 고정 색(순서대로, 돌려 쓰지 않음). 대비가 낮은 색이 있어 선 끝에 이름을 직접 붙이고 보고서에 표를 같이 넣는다
VARIANTS = {
    "V0": ("V0 인덱스 없음", "#2a78d6"),
    "V1": ("V1 현재 grade(lec_no)", "#eb6834"),
    "V1c": ("V1c grade(lec_no, ltr_grd)", "#1baf7a"),
    "V2": ("V2 요약 테이블", "#eda100"),
}
QUERIES = {"Q1": "Q1 학생 한 명 성적표", "Q2": "Q2 최대학점 산정 평점(앱 SQL)", "Q3": "Q3 학과·학기별 평점 분포"}
INK, MUTED, GRID, SURFACE = "#1f1f1e", "#6b6a64", "#e4e3de", "#fcfcfb"


def load(scales):
    rows, students = [], {}
    for scale in scales:
        with open(OUT / scale / "results.csv", encoding="utf-8") as f:
            rows += list(csv.DictReader(f))
        env = (OUT / scale / "env.txt").read_text(encoding="utf-8")
        students[scale] = int(next(line.split(": ")[1] for line in env.splitlines() if line.startswith("rows.student")))
    return rows, students


def fmt(ms):
    return f"{ms:,.0f}ms" if ms >= 10 else f"{ms:.1f}ms"


def place_labels(ax, x, ends, gap=0.22):
    """선 끝 이름표. 값이 거의 같은 선은 한 이름표로 묶고, 나머지는 로그 축에서 gap(자릿수)만큼 띄워 겹치지 않게 한다."""
    groups = []
    for y, vid in sorted(ends):
        if groups and math.log10(y) - math.log10(groups[-1][-1][0]) < 0.1:
            groups[-1].append((y, vid))
        else:
            groups.append([(y, vid)])
    placed = []
    for group in groups:
        ys = [y for y, _ in group]
        low, high = fmt(min(ys)), fmt(max(ys))
        text = "·".join(sorted(v for _, v in group)) + " " + (low if low == high else f"{low}~{high}")
        pos = math.log10(sum(ys) / len(ys))
        if placed and pos - placed[-1] < gap:
            pos = placed[-1] + gap
        placed.append(pos)
        ax.annotate(text, (x, 10 ** pos), xytext=(10, 0), textcoords="offset points", va="center", color=INK,
                    fontsize=9)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scales", nargs="+", default=["S", "M", "L"])
    args = parser.parse_args()
    rows, students = load(args.scales)

    plt.rcParams.update({"font.family": ["Malgun Gothic", "sans-serif"],
                         "axes.unicode_minus": False, "font.size": 10})
    # 세 쿼리를 같은 y축에 둬서 Q1 이 규모와 상관없이 1ms 아래에 머문다는 것이 그대로 보이게 한다
    fig, axes = plt.subplots(1, 3, figsize=(13, 4.6), facecolor=SURFACE, sharey=True)
    xs = [students[s] for s in args.scales]
    for ax, (qid, title) in zip(axes, QUERIES.items()):
        ax.set_facecolor(SURFACE)
        ends = []
        for vid, (label, color) in VARIANTS.items():
            ys = [next((float(r["median_ms"]) for r in rows
                        if r["scale"] == s and r["variant"] == vid and r["query"] == qid), None) for s in args.scales]
            if None in ys:
                continue
            ax.plot(xs, ys, color=color, linewidth=2, marker="o", markersize=7, markeredgecolor=SURFACE,
                    markeredgewidth=2, label=label, zorder=3)
            ends.append((ys[-1], vid))
        place_labels(ax, xs[-1], ends)
        ax.set_title(title, loc="left", color=INK, fontsize=11, pad=10)
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_xticks(xs, [f"{s}\n{n:,}명" for s, n in zip(args.scales, xs)])
        ax.set_ylim(0.3, 20_000)
        ax.set_yticks([1, 10, 100, 1_000, 10_000], ["1ms", "10ms", "100ms", "1s", "10s"])
        ax.minorticks_off()
        ax.set_xlim(xs[0] * 0.8, xs[-1] * 2.6)
        ax.grid(axis="y", color=GRID, linewidth=1)
        ax.tick_params(colors=MUTED, length=0)
        for side in ("top", "right", "left"):
            ax.spines[side].set_visible(False)
        ax.spines["bottom"].set_color(GRID)
    axes[0].set_ylabel("웜 30회 중앙값(ms, 로그 축)", color=MUTED)
    handles, labels = axes[1].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=4, frameon=False, bbox_to_anchor=(0.5, 1.0),
               labelcolor=INK)
    fig.tight_layout(rect=(0, 0, 1, 0.92))
    for ext in ("png", "svg"):
        fig.savefig(OUT / f"response_time.{ext}", dpi=200, facecolor=SURFACE)
    print(OUT / "response_time.png")


if __name__ == "__main__":
    main()
