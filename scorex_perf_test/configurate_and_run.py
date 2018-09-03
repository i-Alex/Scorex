#!/usr/bin/env python2

import os
import shutil
import subprocess
import time
from activity.nodesactivity import NodesActivity

# initial configs values
nodes = 3
apiAddress = "127.0.0.1"
apiPort = 8200
bindPort = 8300

def clear():
	print('Removing old settings...')
	shutil.rmtree('./configs', ignore_errors=True)
	print('Removing old data...')
	shutil.rmtree('/tmp/scorex_test', ignore_errors=True)


def getKnownPeers(node):
	if node == 0:
		return ""
	return "\"" + apiAddress + ":" + str(bindPort) + "\""
	
def getOfflineGeneration(node):
	return "false"

def getWalletSeed(nodeNumber):
	if nodeNumber < 3:
		return "minerNode" + str(nodeNumber + 1)
	return "node" + str(nodeNumber + 1)

def getGenesisAddresses(nodeNumber):
	if nodeNumber < 2:
		return 19
	elif nodeNumber == 2:
		return 9
	return 0

def createConfigs(nodeNumber):
	print('Creating settings files...')
	templateFile = open('./template.conf', 'r')
	tmpConfig = templateFile.read()
	templateFile.close()
	
	os.makedirs('./configs')

	configsData = []
	for i in range(0, nodeNumber):
		config = tmpConfig % {
			'NODE_NUMBER' : i,
			'WALLET_SEED' : getWalletSeed(i),
			'API_ADDRESS' : apiAddress,
			'API_PORT' : apiPort + i,
			'BIND_PORT' : bindPort + i,
			'KNOWN_PEERS' : getKnownPeers(i),
			'OFFLINE_GENERATION' : getOfflineGeneration(i)
			}
		configsData.append({
			"name" : "node" + str(i),
			"genesisAddresses" : getGenesisAddresses(i),
			"url" : "http://" + apiAddress + ":" + str(apiPort + i)
		})
		configFile = open('./configs/settings'  + str(i) + ".conf", "w+")
		configFile.write(config)
		configFile.close()
	return configsData	 


def runScorexNodes(nodeNumber):
	bashCmd = 'gnome-terminal -x java -cp ../examples/target/scala-2.12/twinsChain.jar examples.hybrid.HybridApp ./configs/settings%(NODE_NUMBER)s.conf'
	for i in range(0, nodeNumber):
		cmd = bashCmd % {'NODE_NUMBER' : i}
		print(cmd)
		subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
		



		
clear()
confData = createConfigs(nodes)
runScorexNodes(nodes)

time.sleep(40)
na = NodesActivity(confData)
