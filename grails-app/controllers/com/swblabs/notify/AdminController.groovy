package com.swblabs.notify

import grails.plugin.springsecurity.annotation.Secured

@Secured(["ROLE_ADMIN"])
class AdminController {
	
	def NotifyService

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
}
