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
	
	def notificationTest() {
		NotifyService.handleNotifications()
		render(text:"OK")
	}

	def getMessage(String token) {
		String sessionId=(params.key==null)?session.getId():"fixed"+params.key //allow optional fixed key instead of session
		String ip=request.getHeader("x-forwarded-for")
		String result=NotifyService.getMessage(token,sessionId,ip)
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
		String ip=request.getHeader("x-forwarded-for")
		NotifyService.getAudio(token,session.getId(),ip,response.outputStream)
	}
}
