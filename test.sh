#!/usr/bin/env bash
# Hunt for an unknown RC-6 command number on the home theatre.
#
#   ./test.sh 10 20 3f          fire a range, bracketed by a visible ping
#   ./test.sh 10 20 3f step     one code per keypress
#
# Goes over USB (adb forward), not wifi: the phone's wifi drops out when it
# idles, which silently swallows codes and makes a negative result worthless.
# Set URL=http://<phone-ip>:6510 to force wifi instead.
#
# Each block is bracketed by a ping: volume up then down (net zero, but "VOL"
# shows on the display). No ping seen => the block never landed, retry it.
#
# Address: 10 = amplifier, 04 = DVD section, 11 = tuner, 15 = aux. This unit
# appears to ignore the address, so one pass over the commands should do.
#
# SKIP lists commands to step over. 0x0C is standby and shuts the unit down.
# Add more as you identify disruptive ones, e.g. SKIP=0c,0d ./test.sh ...
set -u
addr=$1; from=$((16#$2)); to=$((16#$3)); mode=${4:-burst}
SKIP="${SKIP:-0c}"

if [ -z "${URL:-}" ]; then
  adb forward tcp:16510 tcp:6510 >/dev/null || { echo "adb forward failed; plug the phone in"; exit 1; }
  URL="http://127.0.0.1:16510"
fi

send() {   # send <0xaddr> <decimal command>
  local msg code
  msg=$(python3 tools/rc6gen.py "$1" "$2" --hex) || return 1
  code=$(curl -sS -o /dev/null -w '%{http_code}' -m 8 -X PUT -G \
         --data-urlencode "msg=$msg" "$URL" 2>/dev/null) || { echo "  UNREACHABLE"; return 1; }
  [ "$code" = 200 ] && return 0
  echo "  http $code"; return 1
}

ping_unit() {
  echo "--- ping: volume up then down, watch for VOL on the display"
  send 0x10 16 && sleep 1 && send 0x10 17 || echo "    PING FAILED"
  sleep 1
}

ping_unit
skipped() {
  local s
  for s in ${SKIP//,/ }; do [ "$((16#$s))" = "$1" ] && return 0; done
  return 1
}

for ((c=from; c<=to; c++)); do
  if skipped "$c"; then printf 'addr 0x%s cmd 0x%02x  SKIPPED\n' "$addr" "$c"; continue; fi
  printf 'addr 0x%s cmd 0x%02x\n' "$addr" "$c"
  send "0x$addr" "$c" || continue
  if [ "$mode" = step ]; then read -r _ </dev/tty; else sleep 1; fi
done
ping_unit

cat <<'TXT'
--- Did the sound change?
    Keep the radio playing while sweeping. The sound effect is persistent state,
    so judge a whole block by whether the tone changed - much easier than
    catching a name flash past on the display.
    Tone changed -> SOUND is in this block; rerun that block in step mode.
TXT
