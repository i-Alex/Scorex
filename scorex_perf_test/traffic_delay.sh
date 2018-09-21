tc qdisc add dev lo root handle 1: prio
# To change delay time, raplace '1s' below to valued you want. Then do the same in 'remove_traffic_delay.sh'
tc qdisc add dev lo parent 1:3 handle 30: netem delay 1s
tc filter add dev lo protocol ip parent 1:0 u32 match ip dport 8300 0xffff flowid 1:3
tc filter add dev lo protocol ip parent 1:0 u32 match ip dport 8301 0xffff flowid 1:3
tc filter add dev lo protocol ip parent 1:0 u32 match ip dport 8302 0xffff flowid 1:3
