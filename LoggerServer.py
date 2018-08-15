import socket
import threading
import datetime

lock = threading.Lock()

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

    def listen(self):
		print("Start listen for connections.")
		self.sock.listen(20)
		while True:
			client, address = self.sock.accept()
			client.settimeout(60)
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

LoggerServer('', 7879).listen()