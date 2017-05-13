package com.swblabs.notify

class Token {
	
	String name //unique token name for access
	String queue //SQS queue with notification
	String user=null //optional credentials to get queue entry from another AWS account (and used for polly also if provided)
	String pass=null //if no credentials we'll assume a role account

    static constraints = {
		name()
		queue()
		user()
		pass()
    }
}
