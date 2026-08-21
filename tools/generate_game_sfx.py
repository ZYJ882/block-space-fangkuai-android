from __future__ import annotations

import math
import struct
import wave
from pathlib import Path
from typing import Iterable

SAMPLE_RATE = 22_050
OUTPUT_DIRECTORY = Path("/home/ubuntu/TetrisNative/app/src/main/res/raw")


def envelope(index: int, length: int, attack: float = 0.012, release: float = 0.24) -> float:
    attack_samples = max(1, int(length * attack))
    release_samples = max(1, int(length * release))
    if index < attack_samples:
        return index / attack_samples
    if index >= length - release_samples:
        return max(0.0, (length - index) / release_samples)
    return 1.0


def render_tone(
    frequency: float,
    duration: float,
    amplitude: float = 0.24,
    waveform: str = "sine",
    start_frequency: float | None = None,
) -> list[float]:
    count = max(1, int(duration * SAMPLE_RATE))
    start = start_frequency if start_frequency is not None else frequency
    samples: list[float] = []
    phase = 0.0
    for index in range(count):
        progress = index / max(1, count - 1)
        current_frequency = start + (frequency - start) * progress
        phase += 2.0 * math.pi * current_frequency / SAMPLE_RATE
        base = math.sin(phase)
        if waveform == "square":
            base = 1.0 if base >= 0.0 else -1.0
        elif waveform == "triangle":
            base = (2.0 / math.pi) * math.asin(math.sin(phase))
        samples.append(base * amplitude * envelope(index, count))
    return samples


def mix_click(samples: list[float], amplitude: float = 0.15, duration: float = 0.018) -> None:
    count = min(len(samples), int(duration * SAMPLE_RATE))
    for index in range(count):
        decay = 1.0 - index / max(1, count)
        noise = math.sin(index * 0.91) * math.sin(index * 0.37)
        samples[index] += noise * amplitude * decay


def concatenate(parts: Iterable[list[float]]) -> list[float]:
    output: list[float] = []
    for part in parts:
        output.extend(part)
    return output


def write_wav(name: str, samples: list[float]) -> None:
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    destination = OUTPUT_DIRECTORY / f"{name}.wav"
    pcm = bytearray()
    for value in samples:
        bounded = max(-1.0, min(1.0, value))
        pcm.extend(struct.pack("<h", int(bounded * 32767)))
    with wave.open(str(destination), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(bytes(pcm))
    print(f"{destination.name}: {len(samples) / SAMPLE_RATE:.3f}s")


def main() -> None:
    move = render_tone(610, 0.035, amplitude=0.16, waveform="triangle")
    write_wav("sfx_move", move)

    rotate = render_tone(860, 0.075, amplitude=0.22, waveform="triangle", start_frequency=480)
    write_wav("sfx_rotate", rotate)

    soft_drop = render_tone(420, 0.042, amplitude=0.15, waveform="sine", start_frequency=470)
    write_wav("sfx_soft_drop", soft_drop)

    hard_drop = render_tone(150, 0.105, amplitude=0.28, waveform="triangle", start_frequency=580)
    mix_click(hard_drop, amplitude=0.23)
    write_wav("sfx_hard_drop", hard_drop)

    lock = render_tone(185, 0.075, amplitude=0.22, waveform="triangle", start_frequency=230)
    mix_click(lock, amplitude=0.11, duration=0.012)
    write_wav("sfx_lock", lock)

    line_clear = concatenate([
        render_tone(523.25, 0.052, amplitude=0.19, waveform="triangle"),
        render_tone(659.25, 0.052, amplitude=0.20, waveform="triangle"),
        render_tone(783.99, 0.090, amplitude=0.23, waveform="triangle"),
    ])
    write_wav("sfx_line_clear", line_clear)

    special_clear = concatenate([
        render_tone(523.25, 0.045, amplitude=0.18, waveform="triangle"),
        render_tone(659.25, 0.045, amplitude=0.20, waveform="triangle"),
        render_tone(783.99, 0.050, amplitude=0.22, waveform="triangle"),
        render_tone(1046.50, 0.120, amplitude=0.25, waveform="triangle"),
    ])
    write_wav("sfx_special_clear", special_clear)

    pause = concatenate([
        render_tone(390, 0.055, amplitude=0.17, waveform="sine"),
        render_tone(310, 0.075, amplitude=0.17, waveform="sine"),
    ])
    write_wav("sfx_pause", pause)

    start = concatenate([
        render_tone(392, 0.050, amplitude=0.17, waveform="triangle"),
        render_tone(523.25, 0.050, amplitude=0.19, waveform="triangle"),
        render_tone(783.99, 0.095, amplitude=0.22, waveform="triangle"),
    ])
    write_wav("sfx_start", start)

    game_over = concatenate([
        render_tone(392, 0.085, amplitude=0.20, waveform="triangle"),
        render_tone(329.63, 0.085, amplitude=0.19, waveform="triangle"),
        render_tone(220.00, 0.150, amplitude=0.18, waveform="triangle"),
    ])
    write_wav("sfx_game_over", game_over)


if __name__ == "__main__":
    main()
