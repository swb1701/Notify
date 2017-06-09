package com.swblabs.notify

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

@Secured(["ROLE_ADMIN"])
class AdminController {
	
	def NotifyService
	def BoardBotService

    def bt() {
		Map nameMap=[:]
		Address.all.each { add ->
			nameMap[add.address]=add.name
		}
		[btMap:NotifyService.btMap,nameMap:nameMap]
	}
	
    def bt2() {
		Map nameMap=[:]
		Address.all.each { add ->
			nameMap[add.address]=add.name
		}
		[btlist:NotifyService.btMap.values().flatten(),nameMap:nameMap]
	}
	
	def bbdemo() {
	}
	
	def test() {
		BoardBotService.test()
		render(text:"OK")
	}
	
	def clear() {
		BoardBotService.clear()
		render(text:"OK")
	}

	def bbtest(int ext) {
		def blocks=BoardBotService.receiver(ext)
		println("returning:"+blocks)
		def map=[blocks:blocks]
		render(map as JSON)
	}
	
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
