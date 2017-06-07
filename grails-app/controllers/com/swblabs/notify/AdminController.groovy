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
	
	def bbtest() {
		def blocks=BoardBotService.receiver()
		println("returning:"+blocks)
		def map=[blocks:blocks]
		render(map as JSON)
	}
}
