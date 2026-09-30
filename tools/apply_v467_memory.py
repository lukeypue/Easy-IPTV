#!/usr/bin/env python3
"""Extract the 4.66 load control and fix low-RAM buffering and mini-view ownership.

Run after the complete 4.66 generation chain. --baseline performs only the
behavior-preserving factory extraction, allowing the same allocator tests to
demonstrate the old time-priority bug before testing the default fixed mode.
Both modes require a fresh generated 4.66 MainActivity.
"""
import argparse
from pathlib import Path


OLD_LOAD_CONTROL = '''        val targetBufferBytes = if (lowRam) 32 * 1024 * 1024 else C.LENGTH_UNSET
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                safeStartMs,                               // steady mode banks only a valid reserve before picture
                safeRebufferMs                             // recovery cushion is guaranteed <= minBufferMs
            )
            .setBackBuffer(10_000, false)
            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
'''

NEW_LOAD_CONTROL = '''        val loadControl = PlaybackMemoryPolicy.create(
            lowRam = lowRam,
            minBufferMs = minBufferMs,
            maxBufferMs = maxBufferMs,
            startBufferMs = safeStartMs,
            rebufferMs = safeRebufferMs
        )
'''

OLD_MINI_VIEW = '''                                        update = { it.player = Playback.player },
                                        modifier = Modifier.fillMaxSize()
'''

NEW_MINI_VIEW = '''                                        update = { it.player = Playback.player },
                                        // The shared player outlives this corner view. Remove
                                        // its listeners when returning to full-screen playback.
                                        onRelease = { it.player = null },
                                        modifier = Modifier.fillMaxSize()
'''


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise ValueError(f'Expected one 4.66 {label} anchor, found {count}')
    return text.replace(old, new, 1)


def patch_main(text: str, baseline: bool = False) -> str:
    text = once(text, OLD_LOAD_CONTROL, NEW_LOAD_CONTROL, 'load-control factory')
    text = once(text, 'import androidx.media3.exoplayer.DefaultLoadControl\n', '', 'load-control import')
    # Validate the mini-view anchor even in baseline mode. No writes occur until
    # all anchors have been checked, so an unexpected source cannot be half-patched.
    return once(text, OLD_MINI_VIEW, OLD_MINI_VIEW if baseline else NEW_MINI_VIEW, 'mini-player view')


def apply(root: Path = Path('.'), baseline: bool = False) -> None:
    source_dir = root / 'app/src/main/java/com/easyiptv/player'
    activity = source_dir / 'MainActivity.kt'
    patched = patch_main(activity.read_text(), baseline)
    policy = (Path(__file__).resolve().parent / 'v467/PlaybackMemoryPolicy.kt').read_text()
    if baseline:
        policy = once(policy, '.setPrioritizeTimeOverSizeThresholds(!lowRam)',
                      '.setPrioritizeTimeOverSizeThresholds(true)', 'baseline time priority')
    (source_dir / 'PlaybackMemoryPolicy.kt').write_text(policy)
    activity.write_text(patched)
    print('Applied RYZOD 4.67 memory ' + ('baseline extraction' if baseline else 'policy and mini-player detach'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', action='store_true', help='extract the unchanged 4.66 buffer policy for regression tests')
    args = parser.parse_args()
    apply(baseline=args.baseline)
