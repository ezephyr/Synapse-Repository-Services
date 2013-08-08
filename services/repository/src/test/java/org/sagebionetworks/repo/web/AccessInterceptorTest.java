package org.sagebionetworks.repo.web;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.UserGroup;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.audit.AccessRecord;
import org.sagebionetworks.repo.model.audit.Method;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for AccessInterceptor.
 * 
 * @author jmhill
 *
 */
public class AccessInterceptorTest {
	
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse;
	Object mockHandler;
	UserInfo mockUserInfo;
	StubAccessRecorder stubRecorder;
	UserManager mockUserManager;
	AccessInterceptor interceptor;
	String userName;

	@Before
	public void before() throws Exception {
		userName = "user@users.r.us.gov";
		mockRequest = Mockito.mock(HttpServletRequest.class);
		mockResponse = Mockito.mock(HttpServletResponse.class);
		mockHandler = Mockito.mock(Object.class);
		mockUserManager = Mockito.mock(UserManager.class);
		mockUserInfo = new UserInfo(false);
		mockUserInfo.setIndividualGroup(new UserGroup());
		mockUserInfo.getIndividualGroup().setId("123");
		stubRecorder = new StubAccessRecorder();
		interceptor = new AccessInterceptor();
		ReflectionTestUtils.setField(interceptor, "accessRecorder", stubRecorder);
		ReflectionTestUtils.setField(interceptor, "userManager", mockUserManager);
		// Setup the happy mock
		when(mockUserManager.getUserInfo(userName)).thenReturn(mockUserInfo);
		when(mockRequest.getParameter(AuthorizationConstants.USER_ID_PARAM)).thenReturn(userName);
		when(mockRequest.getRequestURI()).thenReturn("/entity/syn789");
		when(mockRequest.getMethod()).thenReturn("DELETE");
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void testMissingUserId() throws Exception{
		// return null for the userId
		when(mockRequest.getParameter(AuthorizationConstants.USER_ID_PARAM)).thenReturn(null);
		interceptor.preHandle(mockRequest, mockResponse, mockHandler);
	}
	
	
	@Test
	public void testHappyCase() throws Exception{
		long start = System.currentTimeMillis();
		// Start
		interceptor.preHandle(mockRequest, mockResponse, mockHandler);
		// Wait to add some elapse time
		Thread.sleep(100);
		// finish the call
		Exception exception = null;
		interceptor.afterCompletion(mockRequest, mockResponse, mockHandler, exception);
		// Now get the results from the stub
		assertNotNull(stubRecorder.getSavedRecords());
		assertEquals(1, stubRecorder.getSavedRecords().size());
		AccessRecord result = stubRecorder.getSavedRecords().get(0);
		assertNotNull(result);
		assertTrue(result.getTimestamp() >= start);
		assertTrue(result.getElapseMS() > 99);
		assertEquals(null, result.getErrorMessage());
		assertEquals("/entity/syn789", result.getRequestURL());
		assertEquals(Method.DELETE, result.getMethod());
	}
	
	@Test
	public void testHappyCaseWithException() throws Exception{
		long start = System.currentTimeMillis();
		// Start
		interceptor.preHandle(mockRequest, mockResponse, mockHandler);
		// Wait to add some elapse time
		Thread.sleep(100);
		// finish the call
		String error = "Something went horribly wrong!!!";
		Exception exception = new IllegalArgumentException(error);
		interceptor.afterCompletion(mockRequest, mockResponse, mockHandler, exception);
		// Now get the results from the stub
		assertNotNull(stubRecorder.getSavedRecords());
		assertEquals(1, stubRecorder.getSavedRecords().size());
		AccessRecord result = stubRecorder.getSavedRecords().get(0);
		assertNotNull(result);
		assertTrue(result.getTimestamp() >= start);
		assertTrue(result.getElapseMS() > 99);
		assertNotNull(result.getErrorMessage());
		assertTrue(result.getErrorMessage().indexOf(error) > 0);
	}
}
