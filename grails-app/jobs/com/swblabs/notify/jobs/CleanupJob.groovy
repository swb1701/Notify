package com.swblabs.notify.jobs

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext

class CleanupJob {

	GrailsApplication grailsApplication
	def NotifyService

	void execute(JobExecutionContext context) {
		JobDataMap dataMap=context.getJobDetail().getJobDataMap()
		try {
			NotifyService.cleanupThreads()
		} catch (Exception e) {
			e.printStackTrace()
		}
	}
}
