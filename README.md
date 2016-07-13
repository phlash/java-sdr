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
