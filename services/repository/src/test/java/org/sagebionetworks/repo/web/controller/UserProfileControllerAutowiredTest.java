package org.sagebionetworks.repo.web.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserGroupHeader;
import org.sagebionetworks.repo.model.UserGroupHeaderResponsePage;
import org.sagebionetworks.repo.model.UserPreference;
import org.sagebionetworks.repo.model.UserPreferenceBoolean;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.repo.web.service.EntityService;
import org.sagebionetworks.repo.web.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

public class UserProfileControllerAutowiredTest extends AbstractAutowiredControllerTestBase {
	
	@Autowired
	private UserProfileService userProfileService;
	
	@Autowired 
	private EntityService entityService;

	private Long adminUserId;

	private List<String> favoritesToDelete;
	private List<String> entityIdsToDelete;

	HttpServletRequest mockRequest;

	@Before
	public void before() throws Exception{
		adminUserId = BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId();
		
		assertNotNull(userProfileService);
		favoritesToDelete = new ArrayList<String>();
		entityIdsToDelete = new ArrayList<String>();
		
		mockRequest = Mockito.mock(HttpServletRequest.class);
		when(mockRequest.getServletPath()).thenReturn("/repo/v1");
	}
	
	@After
	public void after() throws UnauthorizedException {
		if (userProfileService != null && favoritesToDelete != null) {
			for (String entityId : favoritesToDelete) {
				try {
					userProfileService.removeFavorite(adminUserId, entityId);
				} catch (NotFoundException e) {
					// nothing to do here
				} catch (DatastoreException e) {
					// nothing to do here.
				}
			}
		}
		if (entityService != null && entityIdsToDelete != null) {
			for (String idToDelete : entityIdsToDelete) {
				try {
					entityService.deleteEntity(adminUserId, idToDelete);
				} catch (NotFoundException e) {
					// nothing to do here
				} catch (DatastoreException e) {
					// nothing to do here.
				}
			}
		}		
	}
	
	
	@Test
	public void testGetUserGroupHeadersNoFilter() throws Exception {
		String prefix = "";
		int limit = 15;
		int offset = 0;
		
		@SuppressWarnings("static-access")
		UserGroupHeaderResponsePage response = servletTestHelper.getUserGroupHeadersByPrefix(prefix, limit, offset);
		assertNotNull(response);
		List<UserGroupHeader> children = response.getChildren();
		assertNotNull(children);
		
		// Should find AUTHENTICATED_USERS group in results.
		Set<String> names = new HashSet<String>();		
		for (UserGroupHeader ugh : children) {
			names.add(ugh.getUserName());
		}
		assertTrue("Expected 'AUTHENTICATED_USERS' group, but was not found.", names.contains("AUTHENTICATED_USERS"));
	}
	
	
	
	@Test
	public void testGeUserGroupHeadersWithFilter() throws Exception {
		String prefix = "dev";
		int limit = 10;
		int offset = 0;
		
		@SuppressWarnings("static-access")
		UserGroupHeaderResponsePage response = servletTestHelper.getUserGroupHeadersByPrefix(prefix, limit, offset);
		assertNotNull(response);
		List<UserGroupHeader> children = response.getChildren();
		assertNotNull(children);
		
		// Verify prefix filtering.
		for (UserGroupHeader ugh : children) {
			if (!ugh.getUserName().toLowerCase().startsWith(prefix.toLowerCase()))
				fail("Invalid user/group returned: '" + ugh.getUserName() + "' does not match prefix '" + prefix +"'.");
		}
		
	}
		
	@Test
	public void testFavoriteCRUD() throws Exception {
		// create an entity
		Project proj = new Project();
		proj.setEntityType(Project.class.getName());
		proj = entityService.createEntity(adminUserId, proj, null, mockRequest);
		entityIdsToDelete.add(proj.getId());
		
		// add favorite
		Map<String, String> extraParams = new HashMap<String, String>();
		EntityHeader fav = servletTestHelper.addFavorite(dispatchServlet, proj.getId(), adminUserId, extraParams);
		favoritesToDelete.add(fav.getId());
		assertNotNull(fav);

		// retrieve
		extraParams = new HashMap<String, String>();
		extraParams.put("offset", "0");
		extraParams.put("limit", Integer.toString(Integer.MAX_VALUE));
		PaginatedResults<EntityHeader> favs = servletTestHelper.getFavorites(dispatchServlet, adminUserId, extraParams);
		assertNotNull(favs);
		assertEquals(1, favs.getTotalNumberOfResults());
		assertEquals(1, favs.getResults().size());
		assertEquals(fav.getId(), favs.getResults().get(0).getId());

		// Shouldn't retrieve the favorite if the node in trash can
		servletTestHelper.deleteEntity(dispatchServlet, Project.class, proj.getId(), adminUserId);
		extraParams = new HashMap<String, String>();
		extraParams.put("offset", "0");
		extraParams.put("limit", Integer.toString(Integer.MAX_VALUE));
		favs = servletTestHelper.getFavorites(dispatchServlet, adminUserId, extraParams);
		assertEquals(0, favs.getTotalNumberOfResults());
		assertEquals(0, favs.getResults().size());
		servletTestHelper.restoreEntity(adminUserId, proj.getId());

		// test removal
		extraParams = new HashMap<String, String>();
		servletTestHelper.removeFavorite(dispatchServlet, proj.getId(), adminUserId, extraParams);
		// assure deletion
		extraParams = new HashMap<String, String>();
		extraParams.put("offset", "0");
		extraParams.put("limit", Integer.toString(Integer.MAX_VALUE));
		favs = servletTestHelper.getFavorites(dispatchServlet, adminUserId, extraParams);
		assertEquals(0, favs.getTotalNumberOfResults());
		assertEquals(0, favs.getResults().size());
	}
	
	@Test
	public void testPreferences() throws Exception {
		UserProfile userProfile = servletTestHelper.getUserProfile(dispatchServlet, adminUserId);
		userProfile.setUserName(BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.name());
		userProfile.setEmails(Collections.singletonList("migrationAdmin@sagebase.org"));
		Set<UserPreference> preferences = userProfile.getPreferences();
		if (preferences==null) {
			preferences = new HashSet<UserPreference>();
			userProfile.setPreferences(preferences);
		}
		{
			UserPreferenceBoolean pref = new UserPreferenceBoolean();
			pref.setName("testPref");
			pref.setValue(true);
			preferences.add(pref);
		}
		servletTestHelper.updateUserProfile(adminUserId, userProfile);
		userProfile = servletTestHelper.getUserProfile(dispatchServlet, adminUserId);
		boolean foundIt = false;
		for (UserPreference pref : userProfile.getPreferences()) {
			if (pref.getName().equals("testPref")) {
				foundIt=true;
				assertTrue(((UserPreferenceBoolean)pref).getValue());
				break;
			}
		}
		assertTrue(foundIt);
	}
	
}
