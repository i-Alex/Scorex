import socket
import threading
import datetime
from sets import Set

lock = threading.Lock()

class StatisticsData(object):
	def __init__(self, who, what, dt, height):
		self.who = who
		self.what = what
		self.dt = dt
		self.height = height
		

class PerformanceInfo(object):
	miningMinTime = 1000000000.0
	miningMaxTime = 0.0
	miningAvgTime = 0.0
	
	propagationMinTime = 1000000000.0
	propagationMaxTime = 0.0
	propagationAvgTime = 0.0
	
	involvedNodes = 0

	def __str__(self):
		return 'Creation: avg=%f, min=%f, max=%f. Propagation: avg=%f, min=%f, max=%f. Nodes=%d' % (
			self.miningAvgTime,
			self.miningMinTime,
			self.miningMaxTime, 
			self.propagationAvgTime,
			self.propagationMinTime,
			self.propagationMaxTime,
			self.involvedNodes
		)


class Statistics(object):
	def __init__(self, groupSize, fname):
		self.blocksInGroup = groupSize * 2
		self.chainInfo = dict({0: [StatisticsData("genesis", "pre", datetime.datetime.now(), 0)]})
		try:
			self.logFile = open(fname, "a+")
			self.logFile.write("Group size = " + str(groupSize) + " (" + str(groupSize*2) + " Pow/Pos blocks)\n")
			self.logFile.flush()
		except:
			print("Error: can't open statistics log file")
			return
	def append(self, statsData):
		if self.chainInfo.has_key(statsData.height):
			self.chainInfo[statsData.height].append(statsData)
		else:
			self.chainInfo[statsData.height] = [statsData]
			if statsData.height % self.blocksInGroup == 1:
				self.calculateGroupStatistics(statsData.height)

	def calculateGroupStatistics(self, untilHeight):
		blockPairHeight = untilHeight - self.blocksInGroup
		if blockPairHeight < 1:
			return
		powInfo = PerformanceInfo()
		posInfo = PerformanceInfo()
		while blockPairHeight < untilHeight:
			powBlockHeight = blockPairHeight
			posBlockHeight = blockPairHeight + 1

	
			# aggregate PoW block info
			powStats = self.chainInfo[powBlockHeight]		
			miningTime = 0
			if powBlockHeight > 1:
				miningTime = (powStats[0].dt - self.chainInfo[powBlockHeight - 1][0].dt).total_seconds()
			if miningTime < powInfo.miningMinTime:
				powInfo.miningMinTime = miningTime
			if miningTime > powInfo.miningMaxTime:
				powInfo.miningMaxTime = miningTime
			powInfo.miningAvgTime += miningTime

			propagationTime = (powStats[-1].dt - powStats[0].dt).total_seconds()
			if propagationTime < powInfo.propagationMinTime:
				powInfo.propagationMinTime = propagationTime
			if propagationTime > powInfo.propagationMaxTime:
				powInfo.propagationMaxTime = propagationTime
			powInfo.propagationAvgTime += propagationTime

			# aggregate PoS block info
			posStats = self.chainInfo[posBlockHeight]
			miningTime = (posStats[0].dt - self.chainInfo[posBlockHeight - 1][0].dt).total_seconds()
			if miningTime != 0 and miningTime < posInfo.miningMinTime:
				posInfo.miningMinTime = miningTime
			if miningTime > posInfo.miningMaxTime:
				posInfo.miningMaxTime = miningTime
			posInfo.miningAvgTime += miningTime

			propagationTime = (posStats[-1].dt - posStats[0].dt).total_seconds()
			if propagationTime < posInfo.propagationMinTime:
				posInfo.propagationMinTime = propagationTime
			if propagationTime > posInfo.propagationMaxTime:
				posInfo.propagationMaxTime = propagationTime
			posInfo.propagationAvgTime += propagationTime

			# calculate nodes involved
			involvedNodes = Set()
			for s in powStats:
				involvedNodes.add(s.who)
			if powInfo.involvedNodes < len(involvedNodes):
				powInfo.involvedNodes = len(involvedNodes)
				posInfo.involvedNodes = len(involvedNodes)

			blockPairHeight += 2

		powInfo.miningAvgTime /= (self.blocksInGroup / 2)
		posInfo.miningAvgTime /= (self.blocksInGroup / 2)
		powInfo.propagationAvgTime /= (self.blocksInGroup / 2)
		posInfo.propagationAvgTime /= (self.blocksInGroup / 2)
		print "Pow info: " + str(powInfo)
		print "Pos info: " + str(posInfo)
		self.logFile.write("\nFrom block " + str(untilHeight - self.blocksInGroup) + " to " + str(untilHeight - 1) + "\n")
		self.logFile.write("Pow info: " + str(powInfo) + "\n")
		self.logFile.write("Pos info: " + str(posInfo) + "\n")
		self.logFile.flush()
	
class LoggerServer(object):
    def __init__(self, host, port):
		self.host = host
		self.port = port
		self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
		self.sock.bind((self.host, self.port))
		
		try:
			dt = datetime.datetime.now()
			self.logFile = open("log_" + dt.strftime("%Y-%m-%d_%H-%M") + ".txt","a+")
		except:
			print("Error: Can't open log file")
			return
		self.statistics = Statistics(20, "log_stats_" + dt.strftime("%Y-%m-%d_%H-%M") + ".txt")

    def listen(self):
		print("Start listen for connections.")
		self.sock.listen(20)
		while True:
			client, address = self.sock.accept()
			client.settimeout(60*10)
			threading.Thread(target = self.listenToClient,args = (client,address)).start()

    def listenToClient(self, client, address):
		print("Client connected, address = " + str(address))
		size = 1024
		while True:
			try:
				data = client.recv(size)
				if data:
					with lock:
						self.processMessage(data)
					
				else:
					print('Client disconnected')
					raise error('Client disconnected')
			except:
				print('close client connection')
				client.close()
				return False
				
    def processMessage(self, message):
		dt = datetime.datetime.now()
		print('Message received: ' + message)
		self.logFile.write(str(dt) + ": " + message + "\n")
		self.logFile.flush()
		parts = message.split(',')
		self.statistics.append(StatisticsData(parts[0], parts[1], dt, int(parts[2])))

LoggerServer('', 7879).listen()
