
import grails.util.Environment

import com.swblabs.notify.Role
import com.swblabs.notify.User
import com.swblabs.notify.UserRole
import com.swblabs.notify.jobs.CleanupJob
import com.swblabs.notify.jobs.NotificationJob

class BootStrap {

	def springSecurityService
	def searchableService
	def NotifyService

	def createUser(user,pass,role) {
		def theUser = new User(username:user,password:pass)
		theUser.save(flush:true)
		UserRole.create theUser, role, true
	}

	def init = { servletContext ->
		log.info 'Boostrapping'
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
		try {
			if (UserRole.list().size() == 0) { //only load on empty DB
				def adminRole = new Role(authority:'ROLE_ADMIN').save(flush:true)
				def userRole = new Role(authority:'ROLE_USER').save(flush:true)

				createUser('admin','adminnf',adminRole)
				createUser('user','usernf',userRole)
			}
			if (Environment.current != Environment.DEVELOPMENT) {
				NotifyService.slack("Notify Server Started")
			}
			println("Starting Notification Job...")
			NotificationJob.schedule("0 0/1 * * * ?") //every minute on the minute
			println("Starting Thread Cleaner Job...")
			CleanupJob.schedule("30 0/5 * * * ?") //every 5 minutes (30 seconds off)
		} catch (Exception e) {
			log.error("Exception during bootstrap init", e)
		}
	}
}
