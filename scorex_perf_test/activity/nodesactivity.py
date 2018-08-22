#!/usr/bin/env python2

import threading
from apiclient import APIClient

class NodesActivity(object):

	def __init__(self, nodesInfo):
		for info in nodesInfo:
			threading.Thread(target = self.startNodeActivity, args = (info["name"], info["url"])).start()

	def startNodeActivity(self, name, url):
		client = APIClient(url)
		try:
			response = client.debugInfo()
			response2 = client.walletGenerateSecret()
		except Exception as e:
			print name + ": " + str(e)
			return

		print name + ": " + str(response)
		print name + ": " + str(response2)
		# todo: implement activity
	

