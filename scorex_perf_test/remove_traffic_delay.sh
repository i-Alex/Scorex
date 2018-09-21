tc filter del dev lo
tc qdisc del dev lo parent 1:3 handle 30: netem delay 1s
tc qdisc del dev lo root handle 1: prio
