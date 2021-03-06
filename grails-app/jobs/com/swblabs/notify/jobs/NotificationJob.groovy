package com.swblabs.notify.jobs

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext

class NotificationJob {

	GrailsApplication grailsApplication
	def NotifyService

	void execute(JobExecutionContext context) {
		JobDataMap dataMap=context.getJobDetail().getJobDataMap()
		try {
			NotifyService.handleNotifications()
		} catch (Exception e) {
			e.printStackTrace()
		}
	}
}
