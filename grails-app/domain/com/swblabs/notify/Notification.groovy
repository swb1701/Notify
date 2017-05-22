package com.swblabs.notify

class Notification {
	
	String cronString //cron string as to when this should trigger
	String queue //aws queue for which message should be associated
	String notifyMessage //message to send to notify
	
	String slackHookName="default" //slack hook name to use
	String slackChannel //slack channel to post to
	String slackMessage //slack message to send

    static constraints = {
		cronString()
		queue nullable:true
		notifyMessage()
		
		slackHookName()
		slackChannel()
		slackMessage nullable:true
    }
}
