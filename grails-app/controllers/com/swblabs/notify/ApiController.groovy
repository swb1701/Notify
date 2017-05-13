package com.swblabs.notify

import grails.converters.JSON

class ApiController {
	
	def NotifyService

    def getMessage(String token) {
		String result=NotifyService.getMessage(token,session.getId())
		if (result!=null) {
			render(text:result)
		} else {
			render(text:/{"cmd":"none"}/)
		}
	}
}
