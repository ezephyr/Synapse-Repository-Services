package org.sagebionetworks.repo.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.audit.AccessRecord;
import org.sagebionetworks.repo.model.audit.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * This intercepter is used to audit all web-service access.
 * 
 * @author John
 * 
 */
public class AccessInterceptor implements HandlerInterceptor {
	
	/**
	 * This map keeps track of the current record for each thread.
	 */
	Map<Long, AccessRecord> threadToRecordMap = Collections.synchronizedMap(new HashMap<Long, AccessRecord>());
	
	@Autowired
	AccessRecorder accessRecorder;
	@Autowired
	UserManager userManager;
	
	
	/**
	 * This is called before a controller runs.
	 */
	@Override
	public boolean preHandle(HttpServletRequest request,
			HttpServletResponse response, Object handler) throws Exception {
		String userIdString = request.getParameter(AuthorizationConstants.USER_ID_PARAM);
		if(userIdString == null) throw new IllegalArgumentException("This interceptor must be after the Authentication Filter");
		// Build up the record
		AccessRecord data = new AccessRecord();
		try{
			UserInfo user = userManager.getUserInfo(userIdString);
			data.setUserId(Long.parseLong(user.getIndividualGroup().getId()));
		}catch(NumberFormatException e){
			 throw new IllegalArgumentException("UserId must be a number");
		}
		data.setTimestamp(System.currentTimeMillis());
		data.setRequestURL(request.getRequestURI());
		data.setMethod(Method.valueOf(request.getMethod()));
		// Bind this record to this thread.
		threadToRecordMap.put(Thread.currentThread().getId(), data);
		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request,
			HttpServletResponse response, Object handler, ModelAndView arg3)
			throws Exception {
		// Nothing to do here
	}

	/**
	 * This is called after a controller returns.
	 */
	@Override
	public void afterCompletion(HttpServletRequest request,
			HttpServletResponse response, Object handler, Exception exception)
			throws Exception {
		// Get the record for this thread
		AccessRecord data = threadToRecordMap.remove(Thread.currentThread().getId());
		if(data == null) throw new IllegalStateException("Failed to get the access record for this thread: "+Thread.currentThread().getId());
		// Calculate the elapse time
		data.setElapseMS(System.currentTimeMillis()-data.getTimestamp());
		// Record the exception if there is one.
		if(exception != null){
			StringWriter stringWriter = new StringWriter();
			PrintWriter writer = new PrintWriter(stringWriter);
			exception.printStackTrace(writer);
			data.setErrorMessage(stringWriter.toString());
		}
		// Save this record
		accessRecorder.save(data);
	}

}
