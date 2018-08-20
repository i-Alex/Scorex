#!/usr/bin/env python2

import os
import shutil
import subprocess

# initial configs values
nodes = 4
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

def createConfigs(nodeNumber):
	print('Creating settings files...')
	templateFile = open('./template.conf', 'r')
	tmpConfig = templateFile.read()
	templateFile.close()
	
	os.makedirs('./configs')

	for i in range(0, nodeNumber):
		config = tmpConfig % {
			'NODE_NUMBER': i,
			'API_ADDRESS' : apiAddress,
			'API_PORT' : apiPort + i,
			'BIND_PORT' : bindPort + i,
			'KNOWN_PEERS' : getKnownPeers(i),
			'OFFLINE_GENERATION' : getOfflineGeneration(i)
			}
		configFile = open('./configs/settings'  + str(i) + ".conf", "w+")
		configFile.write(config)
		configFile.close()		 


def runScorexNodes(nodeNumber):
	bashCmd = 'gnome-terminal -x java -cp ../examples/target/scala-2.12/twinsChain.jar examples.hybrid.HybridApp ./configs/settings%(NODE_NUMBER)s.conf'
	for i in range(0, nodeNumber):
		cmd = bashCmd % {'NODE_NUMBER' : i}
		print(cmd)
		subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
		



		
clear()
createConfigs(nodes)
runScorexNodes(nodes)
