package com.swblabs.notify

class BoardBot {
	
	String queue //queue for which messages may be relayed to bot
	String mac

    static constraints = {
		mac()
		queue nullable:true
    }
}
