import com.swblabs.notify.Role
import com.swblabs.notify.Token
import com.swblabs.notify.User
import com.swblabs.notify.UserRole

class BootStrap {

	def springSecurityService
	def searchableService

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
		} catch (Exception e) {
			log.error("Exception during bootstrap init", e)
		}
	}
}
