# java-sdr
Java software radio based on FUNcube Dongle

For those who don't already know.. I'm part of the FUNcube satellite software team,
and this is a port of our original telemetry decoder logic, including software-defined
radio techniques for demodulating raw IQ input from a FUNcube Dongle (or other IQ
receiver) to baseband bits, then the decoding of AO-40 data packets with error
correction bits, courtesy of James Miller and Phil Karn's original C code, as used
in the official Dashboard software.

It's all very experimental.. and unofficial.

Phil.

## Refactoring plans..

Time to clean up my act :)

 * _done_: Break up jsdr.java, introduce some interfaces for:
   * Configuration I/O
   * Audio I/O
   * Logging
   * UI setup (menus, hot keys)
 * _done_: Introduce dynamic config (menu/dialogs) for audio input.
 * _done(ish)_: Improve UI design through use of menus + put hotkey list in a help popup (hotkeys prb, dead)
 * _plan change_: Remove unused audio I/O from FCD.java, add other FCD controls.
   * eventually abandoned direct tuning of FCD, now calling out to [fcdctl](https://github.com/phlash/fcdctl)
 * _done_: Waterfall display (in recovered space from hotkey list)!
 * _todo_: Break up FUNcubeBPSKdemod.java, display/debug & actual demod
 * _todo_: Move auto-tuning into a separate module (cf: Linux decoder)

## Other Java SDRs

In the process of poking about for inspiration and performance improvements,
I came across a couple of other pure Java SDR projects, which are both much
more mature than this - do please take a look at:

 * [Denny Sheirer's sdrtrunk](https://github.com/DSheirer/sdrtrunk)
 * [Dennis Mantz's RF Analyzer](https://github.com/demantz/RFAnalyzer) for Android

I am now indebted to these folks for going before me and learning how to
code for Java such that it's usable for DSP - thanks y'all!
