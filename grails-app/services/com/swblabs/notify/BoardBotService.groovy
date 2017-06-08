package com.swblabs.notify

import grails.transaction.Transactional

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Transactional
class BoardBotService {

	String pollURL="http://ibb.jjrobots.com/ibbsvr/ibb.php"
	Map botHandlerMap=[:]

	class BotHandler {
		String errCode=null //if set transmit error code on next poll
		LinkedBlockingQueue blockQueue=new LinkedBlockingQueue()
		byte[] last=null
		int seq=0 //block sequence counter

		def sendBlock(cmds) { //send block of commands as int array
			cmds[3]=seq
			seq=(seq+1)%256 //just keep to 8-bits
			blockQueue.add(packBlock(cmds))
		}

		def sendError(String code) { //send error -- in particular "ER"
			errCode=code
		}

		def poll() {
			poll(-1)
		}

		def poll(int ack) {
			if (errCode!=null) { //mainly for "ER" to reset everything
				def result=errCode
				errCode=null
				blockQueue.clear() //flush queue
				last=null //no packet to ack
				return(result)
			} else {
				if (last!=null) {
					if (ack==-1 || ((0xFF&last[5])!=(0xFF&ack))) { //if no ack, or ack not satisfactory return last one again
						return(last)
					}
				}
				def result=blockQueue.poll(60,TimeUnit.SECONDS) //60 second long poll
				if (result==null) { //if nothing
					last=null //clear last
					return("OK") //return OK to begin next poll cycle
				} else {
					last=result //save last block for possible retransmit
					return(result) //return it
				}
			}
		}
	}
	
	def poll(String mac) {
		poll(mac,-1)
	}
	
	synchronized getBotHandler(String mac) {
		BotHandler bh=botHandlerMap[mac]
		if (bh==null) {
			bh=new BotHandler()
			botHandlerMap[mac]=bh
		}
		return(bh)
	}
	
	def poll(String mac,int ack) {
		BotHandler bh=getBotHandler(mac)
		bh.poll(ack)
	}
	
	def sendBlock(String mac,cmds) {
		BotHandler bh=getBotHandler(mac)
		bh.sendBlock(cmds)
	}

	def test() {
		BoardBot bb=BoardBot.first()
		sendBlock(bb.mac,clearBoard[0])
	}

	def receiver() { //test receiver on first board bot
		BoardBot bb=BoardBot.first()
		def blocks=receiver(bb.mac)
		return(blocks)
	}

	//examples of raw block sequences for decoding practice
	def clearBoard=[
		[
			4009,
			4001,
			4009,
			11,
			4001,
			4001,
			4003,
			0,
			1,
			1,
			10,
			10,
			4005,
			0,
			3580,
			10,
			3580,
			120,
			10,
			120,
			10,
			230,
			3580,
			230,
			3580,
			340,
			10,
			340,
			10,
			450,
			3580,
			450,
			3580,
			560,
			10,
			560,
			10,
			670,
			3580,
			670,
			3580,
			780,
			10,
			780,
			10,
			890,
			3580,
			890,
			3580,
			1000,
			10,
			1000,
			10,
			1110,
			3580,
			1110,
			3580,
			1000,
			10,
			1000,
			10,
			890,
			3580,
			890,
			3580,
			780,
			10,
			780,
			10,
			670,
			3580,
			670,
			3580,
			560,
			10,
			560,
			10,
			450,
			3580,
			450,
			3580,
			340,
			10,
			340,
			10,
			230,
			3580,
			230,
			3580,
			120,
			10,
			120,
			10,
			10,
			3580,
			10,
			3580,
			0,
			10,
			0,
			10,
			0,
			4003,
			0,
			1,
			1,
			4002,
			4002
		]
	]

	static Map blockNumberMap=[:]

	/*
	 * Receive the next drawing for the bot as a list of blocks (or empty if a reset is sent).  We
	 * might retransmit the set (possibly altering sequence numbers).
	 */
	def receiver(String mac) {
		//return(clearBoard)
		def blockNumber=blockNumberMap[mac]
		if (blockNumber==null) {
			blockNumber=-1
		}
		def blocks=[]
		while(true) {
			String url=pollURL+"?ID_IWBB="+mac
			if (blockNumber==-1) {
				url+="&STATUS=READY"
			} else {
				url+="&STATUS=ACK&NUM="+blockNumber
			}
			def data=new URL(url).getBytes()
			//println("size="+data.size())
			if (data.size()>6) {
				def block=[]
				for(int i=0;i<data.size();i=i+3) {
					block<<(((0xFF&data[i])<<4)|((0xF0&data[i+1])>>4)) //decode high 12-bits of 3 bytes
					block<<(((data[i+1]&0xF)<<8)|(0xFF&data[i+2])) //decode low 12-bits of 3 bytes
				}
				//println(block)
				blockNumber=block[3]
				if (block[4]==4001 && block[5]==4001) {
					blocks=[block] //first block
				} else {
					blocks<<block
				}
				int size=block.size()
				if (block[size-2]==4002 && block[size-1]==4002) {  //watches for end of draw so web page can clear and draw at once
					blockNumberMap[mac]=blockNumber				   //could relax this to simulate interactive plotting of blocks
					return(blocks) //end of drawing return set of blocks
				}
			} else {
				//ER=reset, OK=ok
				String resp=new String(data)
				print(resp)
				blockNumberMap[mac]=-1
				if (resp=="ER") return([])
				if (resp=="OK") return(blocks)
			}
		}
	}

	byte[] packBlock(cmds) {
		int len=cmds.size()
		if (len%2==0) {
			byte[] buf=new byte[len/2*3]
			for(int i=0;i<len;i=i+2) {
				int pos=i/2*3
				buf[pos]=0xFF&(cmds[i]>>4)
				buf[pos+1]=((0xF&cmds[i])<<4)|(0xF&(cmds[i+1]>>8))
				buf[pos+2]=0xFF&cmds[i+1]
			}
			return(buf)
		} else {
			println("Block must be multiple of two to pack")
			return(null)
		}
	}

	def showPackedBlock(byte[] buf) {
		for(int i=0;i<buf.size();i=i+3) {
			println(String.format("%02X%02X%02X",buf[i],buf[i+1],buf[i+2]))
		}
	}
}
