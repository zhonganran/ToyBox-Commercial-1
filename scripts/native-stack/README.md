# Native Stack Validation Suite

Cross-checks autoservice fid candidates against live D+ HTTP responses on Fangchengbao Bao 3.

## Prereqs
- Mac with `adb`, `python3`, `pip3 install pyyaml`
- DiLink reachable: `adb -s 192.168.2.68:5555 shell echo ok` returns `ok`
- SuperToyBox force-stopped: `adb -s 192.168.2.68:5555 shell am force-stop com.byd.wjx`
- D+ running (drives 8988 HTTP)

## Run
    ./validate.sh --state s2 --count 5

State codes: s2 (parked+IGN ON), s3 (driving), s4 (AC active), s5 (AC charging), s6 (sleep probe).

Output:
- raw snapshots → ../../.research/wjx-validation/native-stack-validation-YYYY-MM-DD-<state>.json
- public fid-map updates → ../../docs/native-data-stack/fid-map.md (manual review before commit)
