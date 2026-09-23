#!/usr/bin/env python3
"""Generate Philips RC-6 mode 0 pulse trains for commands.csv.

The HTS6510 remote speaks RC-6 mode 0 with an 8 bit address and an 8 bit
command.  Decoding the captured codes already in commands.csv gives:

    0x04  DVD section   (power, mp3, disc transport, cursor, menu)
    0x10  amplifier     (volume, mute, surround, sound)
    0x11  tuner         (radio source)
    0x15  aux           (aux source)

Usage: tools/rc6gen.py                  # print the generated commands.csv lines
       tools/rc6gen.py 0x04 0x58        # print one address/command as durations
       tools/rc6gen.py 0x04 0x58 --hex  # print it as a raw "freq;hex" message,
                                        # ready for: curl -X PUT "$URL?msg=..."
"""
import sys

T = 444          # RC-6 unit time in microseconds
FREQ = 37037     # carrier used by every captured HTS6510 code
GAP = 84888      # inter-frame gap, matching the captured codes


def units(addr, cmd, toggle):
    """RC-6 mode 0 frame as a list of T-long levels (1 = carrier on)."""
    u = [1] * 6 + [0] * 2                      # leader: 6T mark, 2T space
    for bit in [1, 0, 0, 0]:                   # start bit + 3 mode bits (mode 0)
        u += [1, 0] if bit else [0, 1]
    u += [1, 1, 0, 0] if toggle else [0, 0, 1, 1]   # double width trailer bit
    for value in (addr, cmd):
        for i in range(7, -1, -1):             # MSB first
            u += [1, 0] if value >> i & 1 else [0, 1]
    return u


def durations(u):
    """Collapse a level list into alternating on/off microsecond durations."""
    out, run = [], 1
    for prev, cur in zip(u, u[1:]):
        if cur == prev:
            run += 1
        else:
            out.append(run * T)
            run = 1
    out.append(run * T)
    return out


def signal(addr, cmd):
    """Both toggle states, the way the captured two frame codes are stored."""
    return (durations(units(addr, cmd, 0)) + [GAP]
            + durations(units(addr, cmd, 1)) + [GAP])


def csv_line(code_id, device, name, category, addr, cmd):
    return "%d;%s;%s;%s;Philips;%d;%s" % (
        code_id, device, name, category, FREQ,
        ", ".join(str(d) for d in signal(addr, cmd)))


# Button, address and RC-6 command number for each key of page 11 of the manual
# that was not captured yet.  The protocol and framing are derived from the
# captured codes, so the waveforms are right; the command numbers are the
# standard Philips RC-5/RC-6 values for those functions.
#
# Only Sound is confirmed against the real unit, found by sweeping: it is
# 0x8A, not the 0x53 first guessed.  The rest are still untested.
#
# This unit acts on the command number and ignores the address, so two entries
# must never share a command number.  RETURN is deliberately absent: 0x8A turned
# out to be Sound, so it needs its own sweep.
BUTTONS = [
    ("Sound",      "PhilpHT2",  "HomeTheater", 0x10, 0x8A),   # confirmed
    ("Up",         "PhilpDVD2", "DVDPlayers",  0x04, 0x58),
    ("Down",       "PhilpDVD2", "DVDPlayers",  0x04, 0x59),
    ("Left",       "PhilpDVD2", "DVDPlayers",  0x04, 0x5A),
    ("Right",      "PhilpDVD2", "DVDPlayers",  0x04, 0x5B),
    ("Ok",         "PhilpDVD2", "DVDPlayers",  0x04, 0x5C),
    ("Disc_Menu",  "PhilpDVD2", "DVDPlayers",  0x04, 0x54),
    ("Subtitle",   "PhilpDVD2", "DVDPlayers",  0x04, 0x4B),
    ("Program",    "PhilpDVD2", "DVDPlayers",  0x04, 0x4C),
    ("Play",       "PhilpDVD2", "DVDPlayers",  0x04, 0x2C),
    ("Pause",      "PhilpDVD2", "DVDPlayers",  0x04, 0x30),
    ("Stop",       "PhilpDVD2", "DVDPlayers",  0x04, 0x31),
    ("Previous",   "PhilpDVD2", "DVDPlayers",  0x04, 0x21),
    ("Next",       "PhilpDVD2", "DVDPlayers",  0x04, 0x20),
]


def main():
    if len(sys.argv) in (3, 4):
        addr, cmd = (int(a, 0) for a in sys.argv[1:3])
        s = signal(addr, cmd)
        if sys.argv[-1] == "--hex":
            print("%d;%s" % (FREQ, ",".join("%x" % d for d in s)))
        else:
            print(", ".join(str(d) for d in s))
        return
    for i, (name, device, category, addr, cmd) in enumerate(BUTTONS):
        print(csv_line(90000 + i, device, name, category, addr, cmd))


if __name__ == "__main__":
    main()
