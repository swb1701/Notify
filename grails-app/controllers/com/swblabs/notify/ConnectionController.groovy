package com.swblabs.notify

class ConnectionController {
	
	def BoardBotService
	
	def bbPoll() {
		String mac=params.ID_IWBB
		int ack=-1
		if (params.NUM!=null) {
			ack=Integer.parseInt(params.NUM)
		}
		def result=BoardBotService.poll(mac,ack)
		response.outputStream.withStream { it << result }
	}

}
