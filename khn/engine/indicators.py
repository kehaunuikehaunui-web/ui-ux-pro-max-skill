"""Indicateurs techniques sur des listes de floats. NaN = pas encore calculable."""
from __future__ import annotations

import math

NAN = float("nan")


def ema(a: list[float], n: int) -> list[float]:
    out = [NAN] * len(a)
    if n <= 0 or len(a) < n:
        return out
    k = 2 / (n + 1)
    e = sum(a[:n]) / n
    out[n - 1] = e
    for i in range(n, len(a)):
        e = a[i] * k + e * (1 - k)
        out[i] = e
    return out


def sma(a: list[float], n: int) -> list[float]:
    out = [NAN] * len(a)
    s = 0.0
    for i, v in enumerate(a):
        s += v
        if i >= n:
            s -= a[i - n]
        if i >= n - 1:
            out[i] = s / n
    return out


def rsi(a: list[float], n: int) -> list[float]:
    out = [NAN] * len(a)
    if len(a) <= n:
        return out
    g = l = 0.0
    for i in range(1, len(a)):
        d = a[i] - a[i - 1]
        up, dn = max(d, 0.0), max(-d, 0.0)
        if i <= n:
            g += up
            l += dn
            if i == n:
                g /= n
                l /= n
                out[i] = 100.0 if l == 0 else 100 - 100 / (1 + g / l)
        else:
            g = (g * (n - 1) + up) / n
            l = (l * (n - 1) + dn) / n
            out[i] = 100.0 if l == 0 else 100 - 100 / (1 + g / l)
    return out


def atr(h: list[float], l: list[float], c: list[float], n: int) -> list[float]:
    m = len(c)
    out = [NAN] * m
    if m < n:
        return out
    tr = [h[0] - l[0]] + [max(h[i] - l[i], abs(h[i] - c[i - 1]), abs(l[i] - c[i - 1])) for i in range(1, m)]
    a = sum(tr[:n]) / n
    out[n - 1] = a
    for i in range(n, m):
        a = (a * (n - 1) + tr[i]) / n
        out[i] = a
    return out


def bollinger(a: list[float], n: int, k: float) -> tuple[list[float], list[float], list[float]]:
    mid = sma(a, n)
    up, lo = [NAN] * len(a), [NAN] * len(a)
    for i in range(n - 1, len(a)):
        win = a[i - n + 1 : i + 1]
        sd = math.sqrt(sum((x - mid[i]) ** 2 for x in win) / n)
        up[i] = mid[i] + k * sd
        lo[i] = mid[i] - k * sd
    return mid, up, lo


def donchian(h: list[float], l: list[float], n: int, high: bool) -> list[float]:
    """Plus haut (ou plus bas) des n bougies PRÉCÉDENTES (bougie courante exclue)."""
    out = [NAN] * len(h)
    src = h if high else l
    for i in range(n, len(src)):
        win = src[i - n : i]
        out[i] = max(win) if high else min(win)
    return out


def efficiency_ratio(a: list[float], n: int) -> list[float]:
    """Force de tendance (Kaufman) : 1 = tendance pure, 0 = bruit."""
    out = [NAN] * len(a)
    for i in range(n, len(a)):
        path = sum(abs(a[j] - a[j - 1]) for j in range(i - n + 1, i + 1))
        out[i] = abs(a[i] - a[i - n]) / path if path else 0.0
    return out


def macd(a: list[float]) -> tuple[list[float], list[float]]:
    f, s = ema(a, 12), ema(a, 26)
    line = [x - y for x, y in zip(f, s)]
    first = next((i for i, v in enumerate(line) if not math.isnan(v)), len(line))
    sig = [NAN] * first + ema(line[first:], 9)
    return line, sig
