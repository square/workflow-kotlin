#!/usr/bin/env python3
"""Analyze runtime microbenchmark variance and normality from benchmarkData.json files.

The AndroidX benchmark runner writes per-iteration samples to `*benchmarkData.json`.
This script aggregates those samples across one or more files and reports:

1. Variance metrics (stddev, coefficient of variation).
2. Normality metrics (Jarque-Bera p-value).
3. Coefficient of derivation (R² from normal Q-Q fit).
4. Simple model comparison (Normal vs LogNormal AIC).
5. Indexed backend deltas vs IndexedStdlib when both are present.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


CLASS_PATTERN = re.compile(
    r"WorkflowRuntimeMicrobenchmark_(?P<tree>[^_]+)_(?P<runtime>[^.]+)$"
)


@dataclass
class BenchmarkRecord:
    source: str
    tree: str
    runtime: str
    benchmark: str
    runs: list[float]


@dataclass
class StatsSummary:
    tree: str
    runtime: str
    benchmark: str
    run_file_count: int
    sample_count: int
    mean_ns: float
    median_ns: float
    stddev_ns: float
    cv_percent: float
    skewness: float
    excess_kurtosis: float
    jarque_bera: float
    jarque_bera_p: float
    normality_cod_r2: float
    drift_cod_r2: float
    drift_slope_ns_per_iteration: float
    aic_normal: float | None
    aic_lognormal: float | None
    preferred_model: str


def _find_benchmark_files(inputs: Iterable[str]) -> list[Path]:
    files: list[Path] = []
    for raw in inputs:
        path = Path(raw).expanduser()
        if not path.exists():
            continue
        if path.is_file():
            files.append(path)
            continue
        files.extend(path.rglob("*benchmarkData*.json"))
    return sorted(set(files))


def _infer_tree_runtime(class_name: str) -> tuple[str, str]:
    simple_name = class_name.split(".")[-1]
    match = CLASS_PATTERN.match(simple_name)
    if not match:
        return "UnknownTree", "UnknownRuntime"
    return match.group("tree"), match.group("runtime")


def _load_records(files: Iterable[Path]) -> list[BenchmarkRecord]:
    records: list[BenchmarkRecord] = []
    for path in files:
        data = json.loads(path.read_text())
        for benchmark in data.get("benchmarks", []):
            class_name = benchmark.get("className", "")
            tree, runtime = _infer_tree_runtime(class_name)
            runs = benchmark.get("metrics", {}).get("timeNs", {}).get("runs", [])
            if not runs:
                continue
            records.append(
                BenchmarkRecord(
                    source=str(path),
                    tree=tree,
                    runtime=runtime,
                    benchmark=benchmark.get("name", "unknown"),
                    runs=[float(v) for v in runs],
                )
            )
    return records


def _linear_regression_r2(x: list[float], y: list[float]) -> tuple[float, float]:
    if len(x) != len(y) or len(x) < 2:
        return 0.0, 0.0
    mean_x = statistics.fmean(x)
    mean_y = statistics.fmean(y)
    ss_x = sum((v - mean_x) ** 2 for v in x)
    if ss_x == 0.0:
        return 0.0, 0.0
    cov_xy = sum((vx - mean_x) * (vy - mean_y) for vx, vy in zip(x, y))
    slope = cov_xy / ss_x
    intercept = mean_y - slope * mean_x
    predictions = [intercept + slope * vx for vx in x]
    ss_res = sum((vy - py) ** 2 for vy, py in zip(y, predictions))
    ss_tot = sum((vy - mean_y) ** 2 for vy in y)
    r2 = 0.0 if ss_tot == 0.0 else max(0.0, 1.0 - (ss_res / ss_tot))
    return slope, r2


def _qq_normal_r2(samples: list[float]) -> float:
    n = len(samples)
    if n < 3:
        return 0.0
    normal = statistics.NormalDist()
    sorted_samples = sorted(samples)
    quantiles = [normal.inv_cdf((i - 0.5) / n) for i in range(1, n + 1)]
    _, r2 = _linear_regression_r2(quantiles, sorted_samples)
    return r2


def _jarque_bera(samples: list[float]) -> tuple[float, float, float, float]:
    n = len(samples)
    if n < 3:
        return 0.0, 1.0, 0.0, 0.0
    mean = statistics.fmean(samples)
    centered = [x - mean for x in samples]
    m2 = statistics.fmean([x * x for x in centered])
    if m2 == 0.0:
        return 0.0, 1.0, 0.0, 0.0
    m3 = statistics.fmean([x**3 for x in centered])
    m4 = statistics.fmean([x**4 for x in centered])
    skewness = m3 / (m2 ** 1.5)
    excess_kurtosis = (m4 / (m2 * m2)) - 3.0
    jb = (n / 6.0) * ((skewness * skewness) + 0.25 * (excess_kurtosis * excess_kurtosis))
    # For JB, asymptotic p-value against chi-square(2): p = exp(-jb/2).
    p_value = math.exp(-jb / 2.0)
    return jb, p_value, skewness, excess_kurtosis


def _aic_models(samples: list[float]) -> tuple[float | None, float | None, str]:
    n = len(samples)
    if n < 2:
        return None, None, "insufficient-data"

    mean = statistics.fmean(samples)
    var_mle = sum((x - mean) ** 2 for x in samples) / n
    aic_normal = None
    if var_mle > 0.0:
        ll_normal = -0.5 * n * (math.log(2.0 * math.pi * var_mle) + 1.0)
        aic_normal = 4.0 - (2.0 * ll_normal)

    aic_lognormal = None
    if all(x > 0.0 for x in samples):
        logs = [math.log(x) for x in samples]
        mean_log = statistics.fmean(logs)
        var_log = sum((x - mean_log) ** 2 for x in logs) / n
        if var_log > 0.0:
            # LogNormal log-likelihood for x > 0.
            ll_lognormal = -sum(math.log(x) for x in samples)
            ll_lognormal -= 0.5 * n * (math.log(2.0 * math.pi * var_log) + 1.0)
            aic_lognormal = 4.0 - (2.0 * ll_lognormal)

    if aic_normal is not None and aic_lognormal is not None:
        preferred = "normal" if aic_normal <= aic_lognormal else "lognormal"
    elif aic_normal is not None:
        preferred = "normal"
    elif aic_lognormal is not None:
        preferred = "lognormal"
    else:
        preferred = "insufficient-data"

    return aic_normal, aic_lognormal, preferred


def _summarize(records: list[BenchmarkRecord]) -> list[StatsSummary]:
    grouped: dict[tuple[str, str, str], list[BenchmarkRecord]] = {}
    for record in records:
        grouped.setdefault((record.tree, record.runtime, record.benchmark), []).append(record)

    summaries: list[StatsSummary] = []
    for (tree, runtime, benchmark), recs in sorted(grouped.items()):
        pooled = [sample for rec in recs for sample in rec.runs]
        sample_count = len(pooled)
        if sample_count < 2:
            continue

        mean = statistics.fmean(pooled)
        median = statistics.median(pooled)
        stddev = statistics.stdev(pooled)
        cv_percent = 0.0 if mean == 0.0 else (stddev / mean) * 100.0
        jb, jb_p, skewness, excess_kurtosis = _jarque_bera(pooled)
        qq_r2 = _qq_normal_r2(pooled)

        drift_x = [float(i) for i in range(sample_count)]
        drift_slope, drift_r2 = _linear_regression_r2(drift_x, pooled)

        aic_normal, aic_lognormal, preferred_model = _aic_models(pooled)

        summaries.append(
            StatsSummary(
                tree=tree,
                runtime=runtime,
                benchmark=benchmark,
                run_file_count=len(recs),
                sample_count=sample_count,
                mean_ns=mean,
                median_ns=median,
                stddev_ns=stddev,
                cv_percent=cv_percent,
                skewness=skewness,
                excess_kurtosis=excess_kurtosis,
                jarque_bera=jb,
                jarque_bera_p=jb_p,
                normality_cod_r2=qq_r2,
                drift_cod_r2=drift_r2,
                drift_slope_ns_per_iteration=drift_slope,
                aic_normal=aic_normal,
                aic_lognormal=aic_lognormal,
                preferred_model=preferred_model,
            )
        )

    return summaries


def _print_summary_table(summaries: list[StatsSummary]) -> None:
    print("# Benchmark Variance And Normality")
    print(
        "# `normality_cod_r2` is the coefficient of derivation (R²) from a normal Q-Q fit."
    )
    print(
        "| Tree | Runtime | Benchmark | Files | Samples | Mean ns | CV% | JB p-value | "
        "Normality CoD (R²) | Drift CoD (R²) | Preferred Model |"
    )
    print("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|")
    for item in summaries:
        print(
            "| "
            f"{item.tree} | {item.runtime} | {item.benchmark} | {item.run_file_count} | "
            f"{item.sample_count} | {item.mean_ns:.2f} | {item.cv_percent:.3f} | "
            f"{item.jarque_bera_p:.4f} | {item.normality_cod_r2:.4f} | "
            f"{item.drift_cod_r2:.4f} | {item.preferred_model} |"
        )


def _print_runtime_deltas(summaries: list[StatsSummary]) -> None:
    grouped: dict[tuple[str, str], list[StatsSummary]] = {}
    for summary in summaries:
        grouped.setdefault((summary.tree, summary.benchmark), []).append(summary)

    rows: list[tuple[str, str, str, float]] = []
    for (tree, benchmark), candidates in sorted(grouped.items()):
        stdlib = next((s for s in candidates if s.runtime == "IndexedStdlib"), None)
        if stdlib is None or stdlib.mean_ns == 0.0:
            continue

        for candidate in sorted(candidates, key=lambda item: item.runtime):
            if not candidate.runtime.startswith("Indexed"):
                continue
            if candidate.runtime == "IndexedStdlib":
                continue
            delta_pct = ((candidate.mean_ns - stdlib.mean_ns) / stdlib.mean_ns) * 100.0
            rows.append((tree, benchmark, candidate.runtime, delta_pct))

    if not rows:
        return

    print("\n# Indexed Backend Deltas Vs IndexedStdlib")
    print("| Tree | Benchmark | Runtime | Delta Vs IndexedStdlib (mean ns) |")
    print("|---|---|---|---:|")
    for tree, benchmark, runtime, delta_pct in rows:
        print(f"| {tree} | {benchmark} | {runtime} | {delta_pct:+.2f}% |")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        nargs="+",
        required=True,
        help="benchmarkData.json files and/or directories to scan recursively.",
    )
    parser.add_argument(
        "--benchmark-filter",
        default="",
        help="Regex to include only matching benchmark names.",
    )
    parser.add_argument(
        "--output-json",
        default="",
        help="Optional path to write machine-readable JSON summary.",
    )
    args = parser.parse_args()

    benchmark_files = _find_benchmark_files(args.input)
    if not benchmark_files:
        raise SystemExit("No benchmarkData.json files found for the provided --input paths.")

    records = _load_records(benchmark_files)
    if args.benchmark_filter:
        include = re.compile(args.benchmark_filter)
        records = [record for record in records if include.search(record.benchmark)]

    if not records:
        raise SystemExit("No benchmark records matched the selected inputs/filter.")

    summaries = _summarize(records)
    _print_summary_table(summaries)
    _print_runtime_deltas(summaries)

    if args.output_json:
        payload = {
            "inputs": [str(path) for path in benchmark_files],
            "summaries": [asdict(item) for item in summaries],
        }
        Path(args.output_json).write_text(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
