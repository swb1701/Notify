package com.swblabs.notify

import grails.converters.JSON

class ApiController {

	def NotifyService

	def pollTest(String token) {
		["token":token]
	}
	
	def pollAudio(String token) {
		["token":token]
	}

	def getMessage(String token) {
		String sessionId=(params.key==null)?session.getId():"fixed"+params.key //allow optional fixed key instead of session
		String result=NotifyService.getMessage(token,sessionId)
		if (result!=null) {
			render(text:result)
		} else {
			render(text:/{"cmd":"none"}/)
		}
	}
	
	/* deprecated -- switch to adding optional key parameter to getMessage */
	def getMessageNoSession(String token) {
		String result=NotifyService.getMessage(token,"nosession")
		if (result!=null) {
			render(text:result)
		} else {
			render(text:/{"cmd":"none"}/)
		}
	}

	def getAudio(String token) {
		response.setContentType('audio/mpeg')
		response.setHeader("Pragma","no-cache")
		response.setHeader("Cache-Control","no-store")
		response.setHeader("Content-Disposition","attachment; notify.mp3")
		//response.outputStream.flush()
		NotifyService.getAudio(token,session.getId(),response.outputStream)
	}
}
