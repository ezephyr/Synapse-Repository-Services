package org.sagebionetworks.repo.manager;

import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.dao.semaphore.SemaphoreDao;
import org.springframework.beans.factory.annotation.Autowired;

public class SemaphoreManagerImpl implements SemaphoreManager {
	
	@Autowired
	SemaphoreDao semaphoreDao;

	@Override
	public void releaseAllLocksAsAdmin(UserInfo admin) {
		if(admin == null){
			throw new IllegalArgumentException("UserInfo cannot be null");
		}
		// Only an admin can make this call
		if(!admin.isAdmin()){
			throw new UnauthorizedException("Only an administrator can make this call");
		}
		// Release all locks
		semaphoreDao.forceReleaseAllLocks();
	}
	

}
