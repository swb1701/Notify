package com.swblabs.notify

import grails.transaction.Transactional
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovyx.net.http.HTTPBuilder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import org.quartz.CronExpression

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.polly.AmazonPollyClient
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest
import com.amazonaws.services.polly.model.SynthesizeSpeechResult
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageResult

@Transactional
class NotifyService {

	static int longPollTime=20000; //how long to wait on a long poll
	static int expirationTime=40000; //when a client expires
	def grailsApplication
	static Object lock=new Object();
	static Map keyToIP=[:]
	static File dataFile=null
	static File sensorFile=null
	def BoardBotService

	class Client {
		String awsQueue
		long lastRead=System.currentTimeMillis() //the last time the client was accessed
		LinkedBlockingQueue<String> queue=new LinkedBlockingQueue<String>() //queue of messages for the client

		public Client(String awsQueue) {
			this.awsQueue=awsQueue
		}

		//queue a message to send to the client
		public addMessage(String message) {
			queue.offer(message)
		}

		public String getMessage() {
			lastRead=System.currentTimeMillis()
			//just block the thread for the planned small number of clients here (could use comet, atmosphere, etc... if we wanted to save server threads)
			return(queue.poll(longPollTime,TimeUnit.MILLISECONDS))
		}
	}

	class QueueReader {

		boolean running=true //true while running

		def shutdown() {
			running=false //this will shut down the thread
		}

		def readQueue(String awsQueue,String user,String pass) {
			Thread.start {
				//run as a background thread
				AmazonSQSClient sqs=null
				if (user!=null) {
					sqs=new AmazonSQSClient(new BasicAWSCredentials(user,pass)) //use the provided credentials
				} else {
					sqs=new AmazonSQSClient() //use role credentials for the VM (or environment credentials)
				}
				while(running) {
					ReceiveMessageResult result=sqs.receiveMessage(awsQueue) //this itself is a long poll (recommend 20 seconds in queue settings)
					//maybe we should force it to 20 seconds above but set queue definition on AWS SQS instead to control it
					def handles=[]
					for(Message mess:result.getMessages()) {
						relayMessage(awsQueue,mess.getBody())
						handles<<mess.getReceiptHandle() //collect our handles
					}
					handles.each { handle ->
						sqs.deleteMessage(awsQueue,handle) //remove the messages we processed
					}
				}
			}
		}
	}

	String maskKey(String key) {
		return("..."+key.substring(key.lastIndexOf("/")))
	}

	//a map from token/session keys to clients
	ConcurrentHashMap<String,Client> clientMap=new ConcurrentHashMap<String,Client>()

	//a map from aws queue name to readers to track the queue readers
	ConcurrentHashMap<String,QueueReader> readerMap=new ConcurrentHashMap<String,QueueReader>()
	
	static ConcurrentHashMap<String,Map> btMap=new ConcurrentHashMap<String,Map>()
	
	def cleanupThreads() {
		relayMessage2(null,null)
	}
	
	def logData(String line) {
		if (dataFile==null) {
			dataFile=new File("btlog.json")
		}
		dataFile<<line<<'\n'
	}
	
	def logSensors(String line) {
		if (sensorFile==null) {
			sensorFile=new File("sensors.json")
		}
		sensorFile<<line<<'\n'
	}

	synchronized updateBluetooth(String sessionId,String ip,String data) {
		try {
			def jsonSlurper=new JsonSlurper()
			def json=jsonSlurper.parseText(data)
			try {
				logData(JsonOutput.toJson([key:sessionId,ip:ip,data:json]))
			} catch (Exception e) {
				e.printStackTrace()
			}
			json.each {
				def olist=btMap[it.addr]
				if (olist==null) {
					Map map0=[ip:ip,key:sessionId,rssi:it.rssi,time:it.time,addr:it.addr]
					ConcurrentHashMap map=new ConcurrentHashMap(map0)
					olist=[map]
					btMap[it.addr]=olist
				} else {
					Map map=olist.find{it.key==sessionId && it.ip==ip}
					if (map==null) {
						map=[ip:ip,key:sessionId,rssi:it.rssi,time:it.time,addr:it.addr]
						olist<<new ConcurrentHashMap(map)
					} else {
						map.rssi=it.rssi
						map.time=it.time
					}
					//candidate for arriving event if previous time greater than threshold
				}
			}
		} catch (Exception e) {
			e.printStackTrace()
		}
	}
	
	synchronized updateSensors(String sessionId,String ip,String sensors) {
		try {
			def jsonSlurper=new JsonSlurper()
			def json=jsonSlurper.parseText(sensors)
			try {
				logSensors(JsonOutput.toJson([key:sessionId,ip:ip,data:json]))
			} catch (Exception e) {
				e.printStackTrace()
			}
		} catch (Exception e) {
			e.printStackTrace()
		}
	}

	synchronized relayMessage(String awsQueue,String message) {
		if (message.indexOf("bbdraw")>-1) { //key bbdraw will trigger
			BoardBot bb=BoardBot.findByQueue(awsQueue)
			if (bb!=null) {
				//now do full parse -- will go to all boardbots for now (e.g. mine)
				Map cmd=null
				try {
					def jsonSlurper=new JsonSlurper()
					cmd=jsonSlurper.parseText(message)
					println("bb json:"+cmd)
					if (cmd.cmd=="bbdraw") {
						BoardBotService.runCommand(cmd)
						return //a real bbdraw command, don't send it beyond boardbot
					}
				} catch (Exception e) {
				}
			}
		}
		relayMessage2(awsQueue,message)
	}

	synchronized relayMessage2(String awsQueue,String message) { //since we may have lots of queues, let's synchronized this so we don't conflict during cleanup
		if (awsQueue!=null) println("Relaying message "+message)
		def expired=[] //client we want to shut down
		long now=System.currentTimeMillis() //get the current time
		clientMap.each { k, v ->
			//look at all the clients
			if ((now-v.lastRead)>expirationTime) { //if it's been too long, schedule client to be shut down
				expired<<k
			} else if (awsQueue!=null) {
				if (v.awsQueue==awsQueue) { //if the client is listening on our queue
					v.addMessage(message) //then send it the message
				}
			}
		}
		expired.each { key ->
			//lets clean up expired stuff
			println("Shutting down idle client "+key+" at "+keyToIP[key])
			slack("Shutting down idle client "+maskKey(key)+" at "+keyToIP[key])
			Client client=clientMap[key] //find the expired client
			if (client!=null) {
				clientMap.remove(key) //remove from the map
				if (!clientMap.values().find{it.awsQueue=client.awsQueue}) { //look if anyone else is still reading its awsQueue
					println("Shutting down idle queue reader for "+client.awsQueue)
					slack("Shutting down idle queue reader for "+maskKey(client.awsQueue))
					QueueReader reader=readerMap[client.awsQueue] //if so, find that reader
					if (reader!=null) {
						reader.shutdown() //signal it to shutdown
						readerMap.remove(client.awsQueue) //and remove it from our list
					}
				}
			}
		}
	}

	/*
	 * Get a message using a given token and session id
	 */
	String getMessage(String tokstr,String sessionId,String ip,String btle=null,String sensors=null) {
		if (btle!=null) {
			println("sessionId=${sessionId} ip=${ip} btle=${btle}")
			updateBluetooth(sessionId,ip,btle)
		}
		if (sensors!=null) {
			println("sessionId=${sessionId} ip=${ip} sensors=${sensors}")
			updateSensors(sessionId,ip,sensors)
		}
		Token token=Token.findByName(tokstr)
		if (token==null) {
			return(/{"cmd":"speak","text":"Sorry, access denied"}/)
		} else {
			Client client
			synchronized(lock) { //only let one thread through here at once to avoid race condition (and two readers for the same queue)
				String key=token.name+"/"+sessionId //give unique stream for each session
				keyToIP[key]=ip
				client=clientMap.get(key)
				if (client==null) {
					println("Making a new client for "+key+" at "+ip)
					slack("Making new client for "+maskKey(key)+" at "+ip)
					client=new Client(token.queue) //make a new client
					clientMap.put(key,client)
					if (readerMap[token.queue]==null) { //if we don't have anyone reading the desired queue
						println("Making a new reader for "+token.queue)
						slack("Making new reader for "+maskKey(token.queue))
						QueueReader reader=new QueueReader() //make a reader
						reader.readQueue(token.queue,token.user,token.pass) //kick off a thread to read the queue
						readerMap[token.queue]=reader //make a record of it for later cleanup/shutdown
					}
				}
			}
			String result=client.getMessage()
			return(result)
		}
	}

	def getAudio(String tokstr,String sessionId,String ip,OutputStream out,String btle=null,String sensors=null) {
		String msg=getMessage(tokstr,sessionId,ip,btle,sensors)
		if (msg!=null) {
			Token token=Token.findByName(tokstr)
			if (token!=null) {
				Map cmd=[:]
				try {
					def jsonSlurper=new JsonSlurper()
					cmd=jsonSlurper.parseText(msg)
					println("json:"+cmd)
				} catch (Exception e) {
					cmd=[cmd:"speak",text:msg]
				}
				if (cmd.cmd.toLowerCase()=="speak") {
					AmazonPollyClient polly=null
					if (token.user!=null) {
						polly=new AmazonPollyClient(new BasicAWSCredentials(token.user,token.pass)) //use the provided credentials
					} else {
						polly=new AmazonPollyClient() //use role credentials for the VM (or environment credentials)
					}
					SynthesizeSpeechRequest req=new SynthesizeSpeechRequest().withVoiceId("Salli").withTextType('ssml').withText('<prosody volume="x-loud">'+cmd.text+'</prosody>').withOutputFormat("mp3")
					SynthesizeSpeechResult res=polly.synthesizeSpeech(req)
					out<<res.getAudioStream()
					out.flush()
					return
				}
			}
		}
		def file=grailsApplication.parentContext.getResource("250mil.mp3").file //250 millis of silence
		out<<file.bytes
		out.flush()
	}
	
	def slack(String message) {
		slack("default",message)
	}

	def slack(String name,String message,String channel=null) {
		SlackHook hook=SlackHook.findByName(name)
		if (hook!=null) {
			try {
				def http = new HTTPBuilder(hook.slackUrl)
				def map = [
						username: "Notifier",
						text: message
				]
				if (channel!=null) {
					map["channel"]=channel
				}
				http.request(groovyx.net.http.Method.POST, groovyx.net.http.ContentType.JSON) { req ->
					body = map
					response.success = { resp ->
					}
				}
			} catch (Exception e) {
				e.printStackTrace()
			}
		} else {
			println("Slack Hook ${name} Not Found")
		}
	}
	
	def handleNotifications() {
		Date now=new Date() //grab the current date/time
		Notification.all.each { note ->
			if (note.cronString!=null) {
				try {
					CronExpression ce=new CronExpression('* '+note.cronString)
					if (ce.isSatisfiedBy(now)) {
						println(note.notifyMessage+" is satisfied at "+now)
						if (note.slackHookName!=null && note.slackMessage!=null) {
							slack(note.slackHookName,note.slackMessage,note.slackChannel) //send slack message
						}
						if (note.queue!=null) {
							relayMessage(note.queue,note.notifyMessage) //send notify message
						}
					}
				} catch (Exception e) {
					e.printStackTrace()
				}
			}
		}
	}

}
