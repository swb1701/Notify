package com.swblabs.notify

import grails.transaction.Transactional

@Transactional
class BoardBotService {
	
	String pollURL="http://ibb.jjrobots.com/ibbsvr/ibb.php"
	
	def receiver() { //test receiver on first board bot
		BoardBot bb=BoardBot.first()
		def blocks=receiver(bb.mac)
		return(blocks)
	}

	//examples of raw block sequences for decoding practice	
	def clearBoard=[[4009,4001,4009,11,4001,4001,4003,0,1,1,10,10,4005,0,3580,10,3580,120,10,120,10,230,3580,230,3580,340,10,340,10,450,3580,450,3580,560,10,560,10,670,3580,670,3580,780,10,780,10,890,3580,890,3580,1000,10,1000,10,1110,3580,1110,3580,1000,10,1000,10,890,3580,890,3580,780,10,780,10,670,3580,670,3580,560,10,560,10,450,3580,450,3580,340,10,340,10,230,3580,230,3580,120,10,120,10,10,3580,10,3580,0,10,0,10,0,4003,0,1,1,4002,4002]]
	
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
}
