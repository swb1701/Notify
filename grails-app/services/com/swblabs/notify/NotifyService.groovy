package com.swblabs.notify

import grails.transaction.Transactional
import groovy.json.JsonSlurper

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

	//a map from token/session keys to clients
	ConcurrentHashMap<String,Client> clientMap=new ConcurrentHashMap<String,Client>()

	//a map from aws queue name to readers to track the queue readers
	ConcurrentHashMap<String,QueueReader> readerMap=new ConcurrentHashMap<String,QueueReader>()

	synchronized relayMessage(String awsQueue,String message) { //since we may have lots of queues, let's synchronized this so we don't conflict during cleanup
		println("Relaying message "+message)
		def expired=[] //client we want to shut down
		long now=System.currentTimeMillis() //get the current time
		clientMap.each { k, v ->
			//look at all the clients
			if ((now-v.lastRead)>expirationTime) { //if it's been too long, schedule client to be shut down
				expired<<k
			} else {
				if (v.awsQueue==awsQueue) { //if the client is listening on our queue
					v.addMessage(message) //then send it the message
				}
			}
		}
		expired.each { key ->
			//lets clean up expired stuff
			println("Shutting down idle client "+key)
			Client client=clientMap[key] //find the expired client
			if (client!=null) {
				clientMap.remove(key) //remove from the map
				if (!clientMap.values().find{it.awsQueue=awsQueue}) { //look if anyone else is still reading its awsQueue
					println("Shutting down idle queue reader for "+awsQueue)
					QueueReader reader=readerMap[awsQueue] //if so, find that reader
					if (reader!=null) {
						reader.shutdown() //signal it to shutdown
						readerMap.remove(awsQueue) //and remove it from our list
					}
				}
			}
		}
	}

	/*
	 * Get a message using a given token and session id
	 */
	String getMessage(String tokstr,String sessionId) {
		Token token=Token.findByName(tokstr)
		if (token==null) {
			return(/{"cmd":"speak","text":"Sorry, access denied"}/)
		} else {
			String key=token.name+"/"+sessionId //give unique stream for each session
			Client client=clientMap.get(key)
			if (client==null) {
				println("Making a new client for "+key)
				client=new Client(token.queue) //make a new client
				clientMap.put(key,client)
				if (readerMap[token.queue]==null) { //if we don't have anyone reading the desired queue
					println("Making a new reader for "+token.queue)
					QueueReader reader=new QueueReader() //make a reader
					reader.readQueue(token.queue,token.user,token.pass) //kick off a thread to read the queue
					readerMap[token.queue]=reader //make a record of it for later cleanup/shutdown
				}
			}
			String result=client.getMessage()
			return(result)
		}
	}

	def getAudio(String tokstr,String sessionId,OutputStream out) {
		String msg=getMessage(tokstr,sessionId)
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

}
