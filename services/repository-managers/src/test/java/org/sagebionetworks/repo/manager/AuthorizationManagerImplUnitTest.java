package org.sagebionetworks.repo.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.repo.manager.team.TeamConstants;
import org.sagebionetworks.repo.manager.trash.EntityInTrashCanException;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessApproval;
import org.sagebionetworks.repo.model.AccessApprovalDAO;
import org.sagebionetworks.repo.model.AccessControlListDAO;
import org.sagebionetworks.repo.model.AccessRequirement;
import org.sagebionetworks.repo.model.AccessRequirementDAO;
import org.sagebionetworks.repo.model.ActivityDAO;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.Reference;
import org.sagebionetworks.repo.model.RestrictableObjectDescriptor;
import org.sagebionetworks.repo.model.RestrictableObjectType;
import org.sagebionetworks.repo.model.TermsOfUseAccessApproval;
import org.sagebionetworks.repo.model.TermsOfUseAccessRequirement;
import org.sagebionetworks.repo.model.UserGroupDAO;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.dao.FileHandleDao;
import org.sagebionetworks.repo.model.evaluation.EvaluationDAO;
import org.sagebionetworks.repo.model.provenance.Activity;
import org.sagebionetworks.repo.model.table.AsynchDownloadRequestBody;
import org.sagebionetworks.repo.model.table.AsynchUploadRequestBody;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public class AuthorizationManagerImplUnitTest {

	private AccessRequirementDAO  mockAccessRequirementDAO;
	private AccessApprovalDAO mockAccessApprovalDAO;
	private ActivityDAO mockActivityDAO;
	private UserGroupDAO mockUserGroupDAO;
	private FileHandleDao mockFileHandleDao;
	private EvaluationDAO mockEvaluationDAO;
	private UserManager mockUserManager;
	private EntityPermissionsManager mockEntityPermissionsManager;
	private AccessControlListDAO mockAclDAO;

	private static String USER_PRINCIPAL_ID = "123";
	private static String EVAL_OWNER_PRINCIPAL_ID = "987";
	private static String EVAL_ID = "1234567";

	private AuthorizationManagerImpl authorizationManager;
	private UserInfo userInfo;
	private UserInfo adminUser;
	private Evaluation evaluation;
	private NodeDAO mockNodeDao;

	@Before
	public void setUp() throws Exception {

		mockAccessRequirementDAO = mock(AccessRequirementDAO.class);
		mockAccessApprovalDAO = mock(AccessApprovalDAO.class);
		mockActivityDAO = mock(ActivityDAO.class);
		mockUserManager = mock(UserManager.class);
		mockEntityPermissionsManager = mock(EntityPermissionsManager.class);
		mockFileHandleDao = mock(FileHandleDao.class);
		mockEvaluationDAO = mock(EvaluationDAO.class);
		mockUserGroupDAO = Mockito.mock(UserGroupDAO.class);
		mockAclDAO = Mockito.mock(AccessControlListDAO.class);
		mockNodeDao = Mockito.mock(NodeDAO.class);

		authorizationManager = new AuthorizationManagerImpl();
		ReflectionTestUtils.setField(authorizationManager, "accessRequirementDAO", mockAccessRequirementDAO);
		ReflectionTestUtils.setField(authorizationManager, "accessApprovalDAO", mockAccessApprovalDAO);
		ReflectionTestUtils.setField(authorizationManager, "activityDAO", mockActivityDAO);
		ReflectionTestUtils.setField(authorizationManager, "userManager", mockUserManager);
		ReflectionTestUtils.setField(authorizationManager, "entityPermissionsManager", mockEntityPermissionsManager);
		ReflectionTestUtils.setField(authorizationManager, "fileHandleDao", mockFileHandleDao);
		ReflectionTestUtils.setField(authorizationManager, "evaluationDAO", mockEvaluationDAO);
		ReflectionTestUtils.setField(authorizationManager, "userGroupDAO", mockUserGroupDAO);
		ReflectionTestUtils.setField(authorizationManager, "aclDAO", mockAclDAO);
		ReflectionTestUtils.setField(authorizationManager, "nodeDao", mockNodeDao);

		userInfo = new UserInfo(false, USER_PRINCIPAL_ID);

		adminUser = new UserInfo(true, 456L);

		evaluation = new Evaluation();
		evaluation.setId(EVAL_ID);
		evaluation.setOwnerId(EVAL_OWNER_PRINCIPAL_ID);
		when(mockEvaluationDAO.get(EVAL_ID)).thenReturn(evaluation);

		List<ACCESS_TYPE> participateAndDownload = new ArrayList<ACCESS_TYPE>();
		participateAndDownload.add(ACCESS_TYPE.DOWNLOAD);
		participateAndDownload.add(ACCESS_TYPE.PARTICIPATE);

		when(mockAccessRequirementDAO.unmetAccessRequirements(
				any(List.class),
				any(RestrictableObjectType.class), any(Collection.class), eq(participateAndDownload))).
				thenReturn(new ArrayList<Long>());
	}

	private PaginatedResults<Reference> generateQueryResults(int numResults, int total) {
		PaginatedResults<Reference> results = new PaginatedResults<Reference>();
		List<Reference> resultList = new ArrayList<Reference>();		
		for(int i=0; i<numResults; i++) {
			Reference ref = new Reference();
			ref.setTargetId("nodeId");
			resultList.add(ref);
		}
		results.setResults(resultList);
		results.setTotalNumberOfResults(total);
		return results;
	}

	@Test
	public void testCanAccessActivityPagination() throws Exception {		 
		Activity act = new Activity();
		String actId = "1";
		int limit = 1000;
		int total = 2001;
		int offset = 0;
		// create as admin, try to access as user so fails access and tests pagination
		act.setId(actId);
		act.setCreatedBy(adminUser.getId().toString());
		when(mockActivityDAO.get(actId)).thenReturn(act);
		PaginatedResults<Reference> results1 = generateQueryResults(limit, total);
		PaginatedResults<Reference> results2 = generateQueryResults(total-limit, total);		
		PaginatedResults<Reference> results3 = generateQueryResults(total-(2*limit), total);
		when(mockActivityDAO.getEntitiesGeneratedBy(actId, limit, offset)).thenReturn(results1);
		when(mockActivityDAO.getEntitiesGeneratedBy(actId, limit, offset+limit)).thenReturn(results2);		
		when(mockActivityDAO.getEntitiesGeneratedBy(actId, limit, offset+(2*limit))).thenReturn(results3);

		boolean canAccess = authorizationManager.canAccessActivity(userInfo, actId);
		verify(mockActivityDAO).getEntitiesGeneratedBy(actId, limit, offset);
		verify(mockActivityDAO).getEntitiesGeneratedBy(actId, limit, offset+limit);
		verify(mockActivityDAO).getEntitiesGeneratedBy(actId, limit, offset+(2*limit));
		assertFalse(canAccess);
	}

	@Test
	public void testCanAccessActivityPaginationSmallResultSet() throws Exception {		 
		Activity act = new Activity();
		String actId = "1";
		int limit = 1000;
		int offset = 0;
		// create as admin, try to access as user so fails access and tests pagination
		act.setId(actId);
		act.setCreatedBy(adminUser.getId().toString());
		when(mockActivityDAO.get(actId)).thenReturn(act);
		PaginatedResults<Reference> results1 = generateQueryResults(1, 1);		
		when(mockActivityDAO.getEntitiesGeneratedBy(actId, limit, offset)).thenReturn(results1);		

		boolean canAccess = authorizationManager.canAccessActivity(userInfo, actId);
		verify(mockActivityDAO).getEntitiesGeneratedBy(actId, limit, offset);
		assertFalse(canAccess);
	}

	@Test
	public void testCanAccessRawFileHandleByCreator(){
		// The admin can access anything
		String creator = userInfo.getId().toString();
		assertTrue("Admin should have access to all FileHandles",authorizationManager.canAccessRawFileHandleByCreator(adminUser, creator));
		assertTrue("Creator should have access to their own FileHandles", authorizationManager.canAccessRawFileHandleByCreator(userInfo, creator));
		// Set the creator to be the admin this time.
		creator = adminUser.getId().toString();
		assertFalse("Only the creator (or admin) should have access a FileHandle", authorizationManager.canAccessRawFileHandleByCreator(userInfo, creator));
	}

	@Test
	public void testCanAccessRawFileHandleById() throws NotFoundException{
		// The admin can access anything
		String creator = userInfo.getId().toString();
		String fileHandlId = "3333";
		when(mockFileHandleDao.getHandleCreator(fileHandlId)).thenReturn(creator);
		assertTrue("Admin should have access to all FileHandles",authorizationManager.canAccessRawFileHandleById(adminUser, fileHandlId));
		assertTrue("Creator should have access to their own FileHandles", authorizationManager.canAccessRawFileHandleById(userInfo, fileHandlId));
		// change the users id
		UserInfo notTheCreatoro = new UserInfo(false, "999999");
		assertFalse("Only the creator (or admin) should have access a FileHandle", authorizationManager.canAccessRawFileHandleById(notTheCreatoro, fileHandlId));
		verify(mockFileHandleDao, times(2)).getHandleCreator(fileHandlId);
	}

	@Test
	public void testCanAccessRawFileHandlesByIds() throws NotFoundException {
		// The admin can access anything
		Multimap<String, String> creators = ArrayListMultimap.create();
		creators.put(userInfo.getId().toString(), "3333");
		creators.put(userInfo.getId().toString(), "4444");
		List<String> fileHandlIds = Lists.newArrayList("3333", "4444");
		when(mockFileHandleDao.getHandleCreators(fileHandlIds)).thenReturn(creators);
		Set<String> allowed = Sets.newHashSet();
		Set<String> disallowed = Sets.newHashSet();
		authorizationManager.canAccessRawFileHandlesByIds(adminUser, fileHandlIds, allowed, disallowed);
		assertEquals("Admin should have access to all FileHandles", 2, allowed.size());
		assertEquals("Admin should have access to all FileHandles", 0, disallowed.size());

		allowed.clear();
		disallowed.clear();
		authorizationManager.canAccessRawFileHandlesByIds(userInfo, fileHandlIds, allowed, disallowed);
		assertEquals("Creator should have access to their own FileHandles", 2, allowed.size());
		assertEquals("Creator should have access to their own FileHandles", 0, disallowed.size());

		// change the users id
		UserInfo notTheCreator = new UserInfo(false, "999999");
		allowed.clear();
		disallowed.clear();
		authorizationManager.canAccessRawFileHandlesByIds(notTheCreator, fileHandlIds, allowed, disallowed);
		assertEquals("Only the creator (or admin) should have access a FileHandle", 0, allowed.size());
		assertEquals("Only the creator (or admin) should have access a FileHandle", 2, disallowed.size());

		verify(mockFileHandleDao, times(2)).getHandleCreators(fileHandlIds);
	}

	@Test
	public void testCanAccessRawFileHandlesByIdsEmptyList() throws NotFoundException {
		authorizationManager.canAccessRawFileHandlesByIds(adminUser, Lists.<String> newArrayList(), null, null);
		verifyZeroInteractions(mockFileHandleDao);
	}

	@Test
	public void testCanAccessWithObjectTypeEntityAllow() throws DatastoreException, NotFoundException{
		String entityId = "syn123";
		when(mockEntityPermissionsManager.hasAccess(any(String.class), any(ACCESS_TYPE.class), eq(userInfo))).thenReturn(true);
		assertTrue("User should have acces to do anything with this entity", authorizationManager.canAccess(userInfo, entityId, ObjectType.ENTITY, ACCESS_TYPE.DELETE));
	}

	@Test
	public void testCanAccessWithObjectTypeEntityDeny() throws DatastoreException, NotFoundException{
		String entityId = "syn123";
		when(mockEntityPermissionsManager.hasAccess(any(String.class), any(ACCESS_TYPE.class), eq(userInfo))).thenReturn(false);
		assertFalse("User should not have acces to do anything with this entity", authorizationManager.canAccess(userInfo, entityId, ObjectType.ENTITY, ACCESS_TYPE.DELETE));
	}

	@Test(expected=EntityInTrashCanException.class)
	public void testCanAccessWithTrashCanException() throws DatastoreException, NotFoundException{
		when(mockEntityPermissionsManager.hasAccess(eq("syn123"), any(ACCESS_TYPE.class), eq(userInfo))).thenThrow(new EntityInTrashCanException(""));
		authorizationManager.canAccess(userInfo, "syn123", ObjectType.ENTITY, ACCESS_TYPE.READ);
	}

	@Test
	public void testVerifyACTTeamMembershipOrIsAdmin_Admin() {
		UserInfo adminInfo = new UserInfo(true);
		assertTrue(authorizationManager.isACTTeamMemberOrAdmin(adminInfo));
	}

	@Test
	public void testVerifyACTTeamMembershipOrIsAdmin_ACT() {
		userInfo.getGroups().add(TeamConstants.ACT_TEAM_ID);
		assertTrue(authorizationManager.isACTTeamMemberOrAdmin(userInfo));
	}

	@Test
	public void testVerifyACTTeamMembershipOrIsAdmin_NONE() {
		assertFalse(authorizationManager.isACTTeamMemberOrAdmin(userInfo));
	}

	private static RestrictableObjectDescriptor createEntitySubjectId() {
		RestrictableObjectDescriptor subjectId = new RestrictableObjectDescriptor();
		subjectId.setType(RestrictableObjectType.ENTITY);
		subjectId.setId("101");
		return subjectId;
	}

	private AccessRequirement createEntityAccessRequirement() throws Exception {
		TermsOfUseAccessRequirement ar = new TermsOfUseAccessRequirement();
		ar.setSubjectIds(Arrays.asList(new RestrictableObjectDescriptor[]{createEntitySubjectId()}));
		ar.setId(1234L);
		when(mockAccessRequirementDAO.get(ar.getId().toString())).thenReturn(ar);
		return ar;
	}

	private AccessApproval createEntityAccessApproval() throws Exception {
		AccessRequirement ar = createEntityAccessRequirement();
		TermsOfUseAccessApproval aa = new TermsOfUseAccessApproval();
		aa.setAccessorId(userInfo.getId().toString());
		aa.setId(656L);
		aa.setRequirementId(ar.getId());
		when(mockAccessApprovalDAO.get(aa.getId().toString())).thenReturn(aa);
		return aa;
	}

	private static RestrictableObjectDescriptor createEvaluationSubjectId() {
		RestrictableObjectDescriptor subjectId = new RestrictableObjectDescriptor();
		subjectId.setType(RestrictableObjectType.EVALUATION);
		subjectId.setId(EVAL_ID);
		return subjectId;
	}

	private AccessRequirement createEvaluationAccessRequirement() throws Exception {
		TermsOfUseAccessRequirement ar = new TermsOfUseAccessRequirement();
		ar.setSubjectIds(Arrays.asList(new RestrictableObjectDescriptor[]{createEvaluationSubjectId()}));
		ar.setId(1234L);
		when(mockAccessRequirementDAO.get(ar.getId().toString())).thenReturn(ar);
		return ar;
	}

	private AccessApproval createEvaluationAccessApproval() throws Exception {
		AccessRequirement ar = createEvaluationAccessRequirement();
		TermsOfUseAccessApproval aa = new TermsOfUseAccessApproval();
		aa.setAccessorId(userInfo.getId().toString());
		aa.setId(656L);
		aa.setRequirementId(ar.getId());
		when(mockAccessApprovalDAO.get(aa.getId().toString())).thenReturn(aa);
		return aa;
	}

	@Test
	public void testCanAccessEntityAccessRequirement() throws Exception {
		AccessRequirement ar = createEntityAccessRequirement();
		assertFalse(authorizationManager.canAccess(userInfo, ar.getId().toString(), ObjectType.ACCESS_REQUIREMENT, ACCESS_TYPE.UPDATE));
		userInfo.getGroups().add(TeamConstants.ACT_TEAM_ID);
		assertTrue(authorizationManager.canAccess(userInfo, "1234", ObjectType.ACCESS_REQUIREMENT, ACCESS_TYPE.UPDATE));
	}

	@Test
	public void testCanAccessEvaluationAccessRequirement() throws Exception {
		AccessRequirement ar = createEvaluationAccessRequirement();
		assertFalse(authorizationManager.canAccess(userInfo, ar.getId().toString(), ObjectType.ACCESS_REQUIREMENT, ACCESS_TYPE.UPDATE));
		userInfo.setId(Long.parseLong(EVAL_OWNER_PRINCIPAL_ID));
		// only ACT may update an access requirement
		assertFalse(authorizationManager.canAccess(userInfo, ar.getId().toString(), ObjectType.ACCESS_REQUIREMENT, ACCESS_TYPE.UPDATE));
	}

	@Test
	public void testCanAccessEntityAccessApproval() throws Exception {
		AccessApproval aa = createEntityAccessApproval();
		assertFalse(authorizationManager.canAccess(userInfo, aa.getId().toString(), ObjectType.ACCESS_APPROVAL, ACCESS_TYPE.READ));
		userInfo.getGroups().add(TeamConstants.ACT_TEAM_ID);
		assertTrue(authorizationManager.canAccess(userInfo, aa.getId().toString(), ObjectType.ACCESS_APPROVAL, ACCESS_TYPE.READ));
	}

	@Test
	public void testCanAccessEvaluationAccessApproval() throws Exception {
		AccessApproval aa = createEvaluationAccessApproval();
		assertFalse(authorizationManager.canAccess(userInfo, aa.getId().toString(), ObjectType.ACCESS_APPROVAL, ACCESS_TYPE.READ));
		userInfo.setId(Long.parseLong(EVAL_OWNER_PRINCIPAL_ID));
		// only ACT may review access approvals
		assertFalse(authorizationManager.canAccess(userInfo, aa.getId().toString(), ObjectType.ACCESS_APPROVAL, ACCESS_TYPE.READ));
	}

	@Test
	public void testCanCreateEntityAccessRequirement() throws Exception {
		AccessRequirement ar = createEntityAccessRequirement();
		assertFalse(authorizationManager.canCreateAccessRequirement(userInfo, ar));
		userInfo.getGroups().add(TeamConstants.ACT_TEAM_ID); 
		assertTrue(authorizationManager.canCreateAccessRequirement(userInfo, ar));
		userInfo.getGroups().remove(TeamConstants.ACT_TEAM_ID);
		assertFalse(authorizationManager.canCreateAccessRequirement(userInfo, ar));
		// give user edit ability on entity 101
		when(mockEntityPermissionsManager.hasAccess(eq("101"), any(ACCESS_TYPE.class), eq(userInfo))).thenReturn(true);
		// only ACT may create access requirements
		assertFalse(authorizationManager.canCreateAccessRequirement(userInfo, ar));
	}

	@Test
	public void testCanAccessEntityAccessApprovalsForSubject() throws Exception {
		assertFalse(authorizationManager.canAccessAccessApprovalsForSubject(userInfo, createEntitySubjectId(), ACCESS_TYPE.READ));
		userInfo.getGroups().add(TeamConstants.ACT_TEAM_ID);
		assertTrue(authorizationManager.canAccessAccessApprovalsForSubject(userInfo, createEntitySubjectId(), ACCESS_TYPE.READ));
	}

	@Test
	public void testCanAccessEvaluationAccessApprovalsForSubject() throws Exception {
		assertFalse(authorizationManager.canAccessAccessApprovalsForSubject(userInfo, createEvaluationSubjectId(), ACCESS_TYPE.READ));
		userInfo.setId(Long.parseLong(EVAL_OWNER_PRINCIPAL_ID));
		// only ACT may review access approvals
		assertFalse(authorizationManager.canAccessAccessApprovalsForSubject(userInfo, createEvaluationSubjectId(), ACCESS_TYPE.READ));
	}
	
	@Test
	public void testCanAccessTeam() throws Exception {
		String teamId = "123";
		ACCESS_TYPE accessType = ACCESS_TYPE.TEAM_MEMBERSHIP_UPDATE;
		// admin can always access
		assertTrue(authorizationManager.canAccess(adminUser, teamId, ObjectType.TEAM, accessType));
		// non admin can access if acl says so
		when(mockAclDAO.canAccess(userInfo.getGroups(), teamId, ObjectType.TEAM, accessType)).thenReturn(true);
		assertTrue(authorizationManager.canAccess(userInfo, teamId, ObjectType.TEAM, accessType));
		// otherwise not
		when(mockAclDAO.canAccess(userInfo.getGroups(), teamId, ObjectType.TEAM, accessType)).thenReturn(false);
		assertFalse(authorizationManager.canAccess(userInfo, teamId, ObjectType.TEAM, accessType));
	}
	
	private static void addEntityHeaderTo(String id, Collection<EntityHeader>c) {
		EntityHeader h = new EntityHeader(); 
		h.setId(id); 
		c.add(h);
	}
	
	@Test
	public void testCanMoveEntity() throws Exception {
		// mock nodeDao
		String parentId = "syn12345";
		List<String> ancestorIds = new ArrayList<String>();
		ancestorIds.add(parentId);
		ancestorIds.add("syn999");
		List<EntityHeader> parentAncestors = new ArrayList<EntityHeader>();
		for (String id: ancestorIds) {
			addEntityHeaderTo(id, parentAncestors);
		}
		when(mockNodeDao.getEntityPath(parentId)).thenReturn(parentAncestors);
		
		String newParentId = "syn6789";
		List<String> newAncestorIds = new ArrayList<String>();
		newAncestorIds.add(newParentId);
		newAncestorIds.add("syn888");
		List<EntityHeader> newParentAncestors = new ArrayList<EntityHeader>();
		for (String id: newAncestorIds) {
			addEntityHeaderTo(id, newParentAncestors);
		}
		when(mockNodeDao.getEntityPath(newParentId)).thenReturn(newParentAncestors);
		
		// mock accessRequirementDAO
		List<AccessRequirement> ars = new ArrayList<AccessRequirement>();
		AccessRequirement ar = new TermsOfUseAccessRequirement();
		ars.add(ar);
		when(mockAccessRequirementDAO.getForSubject(ancestorIds, RestrictableObjectType.ENTITY)).thenReturn(ars);
		when(mockAccessRequirementDAO.getForSubject(newAncestorIds, RestrictableObjectType.ENTITY)).thenReturn(ars);
		
		// since 'ars' list doesn't change, will return true
		assertTrue(authorizationManager.canUserMoveRestrictedEntity(userInfo, parentId, newParentId));
		verify(mockNodeDao).getEntityPath(parentId);
		verify(mockAccessRequirementDAO).getForSubject(ancestorIds, RestrictableObjectType.ENTITY);
		
		// making MORE restrictive is OK
		List<AccessRequirement> mt = new ArrayList<AccessRequirement>(); // i.e, an empty list
		when(mockAccessRequirementDAO.getForSubject(ancestorIds, RestrictableObjectType.ENTITY)).thenReturn(mt);
		assertTrue(authorizationManager.canUserMoveRestrictedEntity(userInfo, parentId, newParentId));

		// but making less restrictive is NOT OK
		when(mockAccessRequirementDAO.getForSubject(ancestorIds, RestrictableObjectType.ENTITY)).thenReturn(ars);
		when(mockAccessRequirementDAO.getForSubject(newAncestorIds, RestrictableObjectType.ENTITY)).thenReturn(mt);
		assertFalse(authorizationManager.canUserMoveRestrictedEntity(userInfo, parentId, newParentId));
		
		// but if the user is an admin, will be true
		assertTrue(authorizationManager.canUserMoveRestrictedEntity(adminUser, parentId, newParentId));
	}
	
	@Test
	public void testCanUserStartJobUploadJobHappyCase() throws DatastoreException, NotFoundException{
		AsynchUploadRequestBody body = new AsynchUploadRequestBody();
		body.setTableId("syn123");
		body.setUploadFileHandleId("456");
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(body.getTableId(), ACCESS_TYPE.UPDATE, userInfo)).thenReturn(true);
		when(mockFileHandleDao.getHandleCreator(body.getUploadFileHandleId())).thenReturn(userInfo.getId().toString());
		// make the call
		assertTrue(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	@Test
	public void testCanUserStartJobUploadJobNoTableUpdate() throws DatastoreException, NotFoundException{
		AsynchUploadRequestBody body = new AsynchUploadRequestBody();
		body.setTableId("syn123");
		body.setUploadFileHandleId("456");
		// the user cannot update the entity
		when(mockEntityPermissionsManager.hasAccess(body.getTableId(), ACCESS_TYPE.UPDATE, userInfo)).thenReturn(false);
		when(mockFileHandleDao.getHandleCreator(body.getUploadFileHandleId())).thenReturn(userInfo.getId().toString());
		// make the call
		assertFalse(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	@Test
	public void testCanUserStartJobUploadJobNotFileHandleOwner() throws DatastoreException, NotFoundException{
		AsynchUploadRequestBody body = new AsynchUploadRequestBody();
		body.setTableId("syn123");
		body.setUploadFileHandleId("456");
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(body.getTableId(), ACCESS_TYPE.UPDATE, userInfo)).thenReturn(true);
		// Set the owner to someone else
		when(mockFileHandleDao.getHandleCreator(body.getUploadFileHandleId())).thenReturn("-9999");
		// make the call
		assertFalse(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	
	@Test
	public void testCanUserStartJobUploadJobAnonymous() throws DatastoreException, NotFoundException{
		AsynchUploadRequestBody body = new AsynchUploadRequestBody();
		body.setTableId("syn123");
		body.setUploadFileHandleId("456");
		userInfo.setId(BOOTSTRAP_PRINCIPAL.ANONYMOUS_USER.getPrincipalId());
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(body.getTableId(), ACCESS_TYPE.UPDATE, userInfo)).thenReturn(true);
		when(mockFileHandleDao.getHandleCreator(body.getUploadFileHandleId())).thenReturn(userInfo.getId().toString());
		// make the call
		assertFalse(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	@Test
	public void testCanUserStartJobDownloadJobAnonymous() throws DatastoreException, NotFoundException{
		AsynchDownloadRequestBody body = new AsynchDownloadRequestBody();
		String tableId = "syn123";
		body.setSql("select * from "+tableId);
		userInfo.setId(BOOTSTRAP_PRINCIPAL.ANONYMOUS_USER.getPrincipalId());
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(tableId, ACCESS_TYPE.READ, userInfo)).thenReturn(true);
		// make the call
		assertFalse(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	@Test
	public void testCanUserStartJobDownloadJobNoRead() throws DatastoreException, NotFoundException{
		AsynchDownloadRequestBody body = new AsynchDownloadRequestBody();
		String tableId = "syn123";
		body.setSql("select * from "+tableId);
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(tableId, ACCESS_TYPE.READ, userInfo)).thenReturn(false);
		// make the call
		assertFalse(this.authorizationManager.canUserStartJob(userInfo, body));
	}
	
	@Test
	public void testCanUserStartJobDownloadJobCanRead() throws DatastoreException, NotFoundException{
		AsynchDownloadRequestBody body = new AsynchDownloadRequestBody();
		String tableId = "syn123";
		body.setSql("select * from "+tableId);
		// the user can update the entity
		when(mockEntityPermissionsManager.hasAccess(tableId, ACCESS_TYPE.READ, userInfo)).thenReturn(true);
		// make the call
		assertTrue(this.authorizationManager.canUserStartJob(userInfo, body));
	}
}
