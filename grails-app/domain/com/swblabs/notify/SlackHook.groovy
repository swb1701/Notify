package com.swblabs.notify

class SlackHook {
	
	String name
	String slackUrl

    static constraints = {
		name()
		slackUrl()
    }
}
