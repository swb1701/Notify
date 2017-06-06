package com.swblabs.notify

import grails.transaction.Transactional

@Transactional
class BoardBotService {
	
	String pollURL="http://ibb.jjrobots.com/ibbsvr/ibb.php"
	
	def receiver() { //test receiver on first board bot
		BoardBot bb=BoardBot.first()
		receiver(bb.mac)
	}

	def receiver(String mac) {
		int blockNumber=-1
		while(true) {
			String url=pollURL+"?ID_IWBB="+mac
			if (blockNumber==-1) {
				url+="&STATUS=READY"
			} else {
				url+="&STATUS=ACK&NUM="+blockNumber
			}
			def data=new URL(url).getBytes()
			println("size="+data.size())
			if (data.size()>6) {
				def block=[]
				for(int i=0;i<data.size();i=i+3) {
					block<<((0xFF&data[i])<<4)|((0xF0&data[i+1])>>4) //decode high 12-bits of 3 bytes
					block<<((data[i+1]&0xF)<<8)|(0xFF&data[i+2]) //decode low 12-bits of 3 bytes
				}
				println(block)
				blockNumber=block[3]
			} else {
				//ER=reset, OK=ok
				print(new String(data))
				return
			}
		}
    }
}
