package org.sagebionetworks.repo.manager.trash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.repo.manager.AccessRequirementManager;
import org.sagebionetworks.repo.manager.EntityPermissionsManager;
import org.sagebionetworks.repo.manager.NodeInheritanceManager;
import org.sagebionetworks.repo.manager.NodeManager;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.model.ACCESS_TYPE;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AccessRequirement;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.model.Node;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.QueryResults;
import org.sagebionetworks.repo.model.TrashedEntity;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.auth.NewUser;
import org.sagebionetworks.repo.model.dao.TrashCanDao;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.util.AccessControlListUtil;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class TrashManagerImplAutowiredTest {

	@Autowired 
	private TrashManager trashManager;
	
	@Autowired 
	private NodeManager nodeManager;
	
	@Autowired 
	private NodeInheritanceManager nodeInheritanceManager;
	
	@Autowired 
	private EntityPermissionsManager entityPermissionsManager;
	
	@Autowired 
	private TrashCanDao trashCanDao;
	
	@Autowired 
	private NodeDAO nodeDAO;
	
	@Autowired
	private UserManager userManager;
	
	@Autowired 
	private AccessRequirementManager accessRequirementManager;
	
	private UserInfo testAdminUserInfo;
	private UserInfo testUserInfo;
	private String trashCanId;
	private List<String> toClearList;
	
	private AccessRequirement accessRequirementToDelete;

	@Before
	public void before() throws Exception {
		testAdminUserInfo = userManager.getUserInfo(BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId());
		
		NewUser user = new NewUser();
		user.setEmail(UUID.randomUUID().toString() + "@test.com");
		user.setUserName(UUID.randomUUID().toString());
		testUserInfo = userManager.getUserInfo(userManager.createUser(user));
		assertNotNull(testUserInfo);
		assertFalse(testUserInfo.isAdmin());

		trashCanId = KeyFactory.keyToString(TrashConstants.TRASH_FOLDER_ID);
		assertNotNull(trashCanId);
		Node trashFolder = nodeDAO.getNode(trashCanId);
		assertNotNull(trashFolder);
		assertNotNull(trashFolder.getParentId());
		assertTrue(nodeDAO.isNodeRoot(trashFolder.getParentId()));
		String benefactorId = nodeInheritanceManager.getBenefactor(trashCanId);
		assertNotNull(benefactorId);
		assertEquals(trashCanId, benefactorId);

		toClearList = new ArrayList<String>();
		cleanUp(); // Clean up leftovers from other test cases
	}

	@After
	public void after() throws Exception {
		cleanUp();
		
		userManager.deletePrincipal(testAdminUserInfo, testUserInfo.getId());
	}
	
	private QueryResults<TrashedEntity> inspectUsersTrashCan(UserInfo userInfo, int expectedSize) throws Exception {
		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(userInfo, userInfo, 0L, 1000L);
		assertEquals((long)expectedSize, results.getTotalNumberOfResults());
		assertEquals(expectedSize, results.getResults().size());
		return results;
	}
	
	private Node createNode(final String name, EntityType type, String parentId) throws DatastoreException, InvalidModelException, UnauthorizedException, NotFoundException {
		Node node = new Node();
		node.setName(name);
		node.setNodeType(type.name());
		if (parentId!=null) node.setParentId(parentId);
		final String nodeId = nodeManager.createNewNode(node, testUserInfo);
		assertNotNull(nodeId);
		toClearList.add(nodeId);
		Node nodeRetrieved = nodeManager.get(testUserInfo, nodeId);
		assertNotNull(nodeRetrieved);
		if (parentId!=null) assertEquals(parentId, nodeRetrieved.getParentId());
		return nodeRetrieved;
	}

	@Test
	public void testSingleNodeRoundTrip() throws Exception {
		inspectUsersTrashCan(testUserInfo, 0);
		Node nodeParent = createNode("TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Parent",EntityType.project, null);
		final String nodeParentId = nodeParent.getId();
		String nodeChildName = "TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Child";
		Node nodeChild = createNode(nodeChildName, EntityType.dataset, nodeParentId);
		final String nodeChildId = nodeChild.getId();

		trashManager.moveToTrash(testUserInfo, nodeChildId);

		try {
			nodeChild = nodeManager.get(testUserInfo, nodeChildId);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		QueryResults<TrashedEntity> results = inspectUsersTrashCan(testUserInfo, 1);
		TrashedEntity trash = results.getResults().get(0);
		assertNotNull(trash);
		assertEquals(nodeChildId, trash.getEntityId());
		assertEquals(nodeChildName, trash.getEntityName());
		assertEquals(nodeParentId, trash.getOriginalParentId());
		assertEquals(testUserInfo.getId().toString(), trash.getDeletedByPrincipalId());
		assertNotNull(trash.getDeletedOn());

		trashManager.restoreFromTrash(testUserInfo, nodeChildId, nodeParentId);

		inspectUsersTrashCan(testUserInfo, 0);

		Node nodeChildRetrieved = nodeManager.get(testUserInfo, nodeChildId);
		assertNotNull(nodeChildRetrieved);
		assertEquals(nodeChildId, nodeChildRetrieved.getId());
		assertEquals(nodeChildName, nodeChildRetrieved.getName());
		assertEquals(nodeParentId, nodeChildRetrieved.getParentId());
		assertEquals(nodeParentId, nodeInheritanceManager.getBenefactor(nodeChildRetrieved.getId()));
	}
	
	@Test
	public void testRestrictedNodeRoundTrip() throws Exception {
		inspectUsersTrashCan(testUserInfo, 0);
		Node nodeParent = createNode("TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Parent",EntityType.project, null);
		final String nodeParentId = nodeParent.getId();
		String nodeChildName = "TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Child";
		Node nodeChild = createNode(nodeChildName, EntityType.dataset, nodeParentId);
		final String nodeChildId = nodeChild.getId();

		// add an access requirement to the parent
		accessRequirementToDelete = accessRequirementManager.createLockAccessRequirement(testUserInfo, nodeParentId);
		
		// delete and try to restore to some other (unrestricted) parent
		trashManager.moveToTrash(testUserInfo, nodeChildId);
		Node adoptiveParent = createNode("TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Adoptive Parent",EntityType.project, null);
		final String adoptiveParentId = adoptiveParent.getId();
		try {
			trashManager.restoreFromTrash(testUserInfo, nodeChildId, adoptiveParentId);
			fail();
		} catch (UnauthorizedException e) {
			// as expected
		}
		
		// restore to original parent
		trashManager.restoreFromTrash(testUserInfo, nodeChildId, nodeParentId);
	}

	@Test
	public void testRestrictedNodeDeleteOriginalParent() throws Exception {
		inspectUsersTrashCan(testUserInfo, 0);
		Node nodeParent = createNode("TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Parent",EntityType.project, null);
		final String nodeParentId = nodeParent.getId();
		String nodeChildName = "TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Child";
		Node nodeChild = createNode(nodeChildName, EntityType.dataset, nodeParentId);
		final String nodeChildId = nodeChild.getId();

		// delete and try to restore to some other (unrestricted) parent
		trashManager.moveToTrash(testUserInfo, nodeChildId);
		
		// now delete the original parent
		nodeManager.delete(testUserInfo, nodeParentId);
		toClearList.remove(nodeParentId);
		
		Node adoptiveParent = createNode("TrashManagerImplAutowiredTest.testSingleNodeRoundTrip() Adoptive Parent",EntityType.project, null);
		final String adoptiveParentId = adoptiveParent.getId();
		try {
			trashManager.restoreFromTrash(testUserInfo, nodeChildId, adoptiveParentId);
			fail("NotFoundException expected");
		} catch (NotFoundException e) {
			// as expected
		}
		// ACT member or Synapse administrator CAN do the operation though
		trashManager.restoreFromTrash(testAdminUserInfo, nodeChildId, adoptiveParentId);
	}

	@Test
	public void testSingleNodeRoundTripRestoreToRoot() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		Node node = new Node();
		node.setName("TrashManagerImplAutowiredTest.testSingleNodeRoundTripRestoreToRoot()");
		node.setNodeType(EntityType.project.name());
		final String nodeId = nodeManager.createNewNode(node, testUserInfo);
		assertNotNull(nodeId);
		toClearList.add(nodeId);
		Node nodeRetrieved = nodeManager.get(testUserInfo, nodeId);
		assertNotNull(nodeRetrieved);
		final String parentId = nodeRetrieved.getParentId();
		assertNotNull(parentId);

		trashManager.moveToTrash(testUserInfo, nodeId);

		try {
			nodeRetrieved = nodeManager.get(testUserInfo, nodeId);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(1L, results.getTotalNumberOfResults());
		assertEquals(1, results.getResults().size());
		TrashedEntity trash = results.getResults().get(0);
		assertNotNull(trash);
		assertEquals(nodeId, trash.getEntityId());
		assertEquals(parentId, trash.getOriginalParentId());
		assertEquals(testUserInfo.getId().toString(), trash.getDeletedByPrincipalId());
		assertNotNull(trash.getDeletedOn());

		trashManager.restoreFromTrash(testUserInfo, nodeId, parentId);

		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		nodeRetrieved = nodeManager.get(testUserInfo, nodeId);
		assertNotNull(nodeRetrieved);
		assertEquals(nodeId, nodeRetrieved.getId());
		assertEquals(nodeId, nodeInheritanceManager.getBenefactor(nodeRetrieved.getId()));
	}

	@Test
	public void testMultipleNodeRoundTrip() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		//
		// Create the following simple topoloy:
		//
		//                  root
		//                  /  \
		//            [nodeA] [nodeB]
		//              |        |
		//           node00    node01
		//           /     \
		//        node11 [node12]
		//          |       |
		//        node21  node22
		//
		// [] indicates benefactors. In this test, we will move node00 and node01 to trash can
		//
		final Node nodeA = new Node();
		final String nodeNameA = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() A";
		nodeA.setName(nodeNameA);
		nodeA.setNodeType(EntityType.project.name());

		final Node nodeB = new Node();
		final String nodeNameB = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() B";
		nodeB.setName(nodeNameB);
		nodeB.setNodeType(EntityType.project.name());

		final Node node00 = new Node();
		final String nodeName00 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 00 or 01";
		node00.setName(nodeName00);
		node00.setNodeType(EntityType.folder.name());

		final Node node01 = new Node();
		final String nodeName01 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 00 or 01";
		assertEquals(nodeName00, nodeName01); // PLFM-1760
		node01.setName(nodeName01);
		node01.setNodeType(EntityType.folder.name());

		final Node node11 = new Node();
		final String nodeName11 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 11";
		node11.setName(nodeName11);
		node11.setNodeType(EntityType.folder.name());

		final Node node12 = new Node();
		final String nodeName12 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 12";
		node12.setName(nodeName12);
		node12.setNodeType(EntityType.folder.name());

		final Node node21 = new Node();
		final String nodeName21 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 21";
		node21.setName(nodeName21);
		node21.setNodeType(EntityType.dataset.name());

		final Node node22 = new Node();
		final String nodeName22 = "TrashManagerImplAutowiredTest.testMultipleNodeRoundTrip() 22";
		node22.setName(nodeName22);
		node22.setNodeType(EntityType.dataset.name());

		// Create the nodes
		final String nodeIdA = nodeManager.createNewNode(nodeA, testUserInfo);
		assertNotNull(nodeIdA);
		toClearList.add(nodeIdA);

		final String nodeIdB = nodeManager.createNewNode(nodeB, testUserInfo);
		assertNotNull(nodeIdB);
		toClearList.add(nodeIdB);

		node00.setParentId(nodeIdA);
		final String nodeId00 = nodeManager.createNewNode(node00, testUserInfo);
		assertNotNull(nodeId00);
		toClearList.add(nodeId00);

		node01.setParentId(nodeIdB);
		final String nodeId01 = nodeManager.createNewNode(node01, testUserInfo);
		assertNotNull(nodeId01);
		toClearList.add(nodeId01);

		node11.setParentId(nodeId00);
		final String nodeId11 = nodeManager.createNewNode(node11, testUserInfo);
		assertNotNull(nodeId11);
		toClearList.add(nodeId11);

		node12.setParentId(nodeId00);
		final String nodeId12 = nodeManager.createNewNode(node12, testUserInfo);
		assertNotNull(nodeId12);
		toClearList.add(nodeId12);

		node21.setParentId(nodeId11);
		final String nodeId21 = nodeManager.createNewNode(node21, testUserInfo);
		assertNotNull(nodeId21);
		toClearList.add(nodeId21);

		node22.setParentId(nodeId12);
		final String nodeId22 = nodeManager.createNewNode(node22, testUserInfo);
		assertNotNull(nodeId22);
		toClearList.add(nodeId22);

		// Modify nodeId12 to be its own benefactor
		AccessControlList acl = AccessControlListUtil.createACLToGrantAll(nodeId12, testUserInfo);
		entityPermissionsManager.overrideInheritance(acl, testUserInfo);
		assertEquals(nodeId12, nodeInheritanceManager.getBenefactor(nodeId12));
		assertEquals(nodeId12, nodeInheritanceManager.getBenefactor(nodeId22));

		// Make sure we can read the nodes before moving the trash can
		Node nodeBack00 = nodeManager.get(testUserInfo, nodeId00);
		assertNotNull(nodeBack00);
		final String parentId00 = nodeBack00.getParentId();
		assertEquals(nodeIdA, parentId00);

		Node nodeBack01 = nodeManager.get(testUserInfo, nodeId01);
		assertNotNull(nodeBack01);
		final String parentId01 = nodeBack01.getParentId();
		assertEquals(nodeIdB, parentId01);

		Node nodeBack11 = nodeManager.get(testUserInfo, nodeId11);
		assertNotNull(nodeBack11);
		final String parentId11 = nodeBack11.getParentId();
		assertEquals(nodeId00, parentId11);

		Node nodeBack12 = nodeManager.get(testUserInfo, nodeId12);
		assertNotNull(nodeBack12);
		final String parentId12 = nodeBack12.getParentId();
		assertEquals(nodeId00, parentId12);

		Node nodeBack21 = nodeManager.get(testUserInfo, nodeId21);
		assertNotNull(nodeBack21);
		final String parentId21 = nodeBack21.getParentId();
		assertEquals(nodeId11, parentId21);

		Node nodeBack22 = nodeManager.get(testUserInfo, nodeId22);
		assertNotNull(nodeBack22);
		final String parentId22 = nodeBack22.getParentId();
		assertEquals(nodeId12, parentId22);

		trashManager.moveToTrash(testUserInfo, nodeId00);
		// node01 has the same name as node00 (PLFM-1760)
		trashManager.moveToTrash(testUserInfo, nodeId01);

		// After moved to trash, the nodes are not accessible any more
		try {
			nodeBack00 = nodeManager.get(testUserInfo, nodeId00);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		try {
			nodeBack01 = nodeManager.get(testUserInfo, nodeId01);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		try {
			nodeBack11 = nodeManager.get(testUserInfo, nodeId11);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		try {
			nodeBack12 = nodeManager.get(testUserInfo, nodeId12);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		try {
			nodeBack21 = nodeManager.get(testUserInfo, nodeId21);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		try {
			nodeBack22 = nodeManager.get(testUserInfo, nodeId22);
			fail();
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		// But we can see them in the trash can
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(6L, results.getTotalNumberOfResults());
		assertEquals(6, results.getResults().size());

		// Restore node00 and its descendants
		trashManager.restoreFromTrash(testUserInfo, nodeId00, parentId00);

		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(1L, results.getTotalNumberOfResults());
		assertEquals(1, results.getResults().size());

		nodeBack00 = nodeManager.get(testUserInfo, nodeId00);
		assertNotNull(nodeBack00);
		assertEquals(nodeId00, nodeBack00.getId());
		assertEquals(nodeName00, nodeBack00.getName());
		assertEquals(parentId00, nodeBack00.getParentId());

		nodeBack11 = nodeManager.get(testUserInfo, nodeId11);
		assertNotNull(nodeBack11);
		assertEquals(nodeId11, nodeBack11.getId());
		assertEquals(nodeName11, nodeBack11.getName());
		assertEquals(parentId11, nodeBack11.getParentId());

		nodeBack12 = nodeManager.get(testUserInfo, nodeId12);
		assertNotNull(nodeBack12);
		assertEquals(nodeId12, nodeBack12.getId());
		assertEquals(nodeName12, nodeBack12.getName());
		assertEquals(parentId12, nodeBack12.getParentId());

		nodeBack21 = nodeManager.get(testUserInfo, nodeId21);
		assertNotNull(nodeBack21);
		assertEquals(nodeId21, nodeBack21.getId());
		assertEquals(nodeName21, nodeBack21.getName());
		assertEquals(parentId21, nodeBack21.getParentId());

		nodeBack22 = nodeManager.get(testUserInfo, nodeId22);
		assertNotNull(nodeBack22);
		assertEquals(nodeId22, nodeBack22.getId());
		assertEquals(nodeName22, nodeBack22.getName());
		assertEquals(parentId22, nodeBack22.getParentId());

		// Restore node01
		trashManager.restoreFromTrash(testUserInfo, nodeId01, null);

		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		nodeBack01 = nodeManager.get(testUserInfo, nodeId01);
		assertNotNull(nodeBack01);
		assertEquals(nodeId01, nodeBack01.getId());
		assertEquals(nodeName01, nodeBack01.getName());
		assertEquals(parentId01, nodeBack01.getParentId());
	}

	@Test
	public void testPurgeNodeForUser() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		//
		//           root
		//           /  \
		//          A1  A2
		//          |    |
		//          B1  B2
		//          |
		//          C1
		//
		final Node nodeA1 = new Node();
		final String nodeNameA1 = "TrashManagerImplAutowiredTest.testPurge() A1";
		nodeA1.setName(nodeNameA1);
		nodeA1.setNodeType(EntityType.project.name());
		final String nodeIdA1 = nodeManager.createNewNode(nodeA1, testUserInfo);
		assertNotNull(nodeIdA1);
		toClearList.add(nodeIdA1);

		final Node nodeA2 = new Node();
		final String nodeNameA2 = "TrashManagerImplAutowiredTest.testPurge() A2";
		nodeA2.setName(nodeNameA2);
		nodeA2.setNodeType(EntityType.project.name());
		final String nodeIdA2 = nodeManager.createNewNode(nodeA2, testUserInfo);
		assertNotNull(nodeIdA2);
		toClearList.add(nodeIdA2);

		final Node nodeB1 = new Node();
		final String nodeNameB1 = "TrashManagerImplAutowiredTest.testPurge() B1";
		nodeB1.setName(nodeNameB1);
		nodeB1.setNodeType(EntityType.folder.name());
		nodeB1.setParentId(nodeIdA1);
		final String nodeIdB1 = nodeManager.createNewNode(nodeB1, testUserInfo);
		assertNotNull(nodeIdB1);
		toClearList.add(nodeIdB1);
		
		final Node nodeB2 = new Node();
		final String nodeNameB2 = "TrashManagerImplAutowiredTest.testPurge() B2";
		nodeB2.setName(nodeNameB2);
		nodeB2.setNodeType(EntityType.folder.name());
		nodeB2.setParentId(nodeIdA2);
		final String nodeIdB2 = nodeManager.createNewNode(nodeB2, testUserInfo);
		assertNotNull(nodeIdB2);
		toClearList.add(nodeIdB2);

		final Node nodeC1 = new Node();
		final String nodeNameC1 = "TrashManagerImplAutowiredTest.testPurge() C1";
		nodeC1.setName(nodeNameC1);
		nodeC1.setNodeType(EntityType.dataset.name());
		nodeC1.setParentId(nodeIdB1);
		final String nodeIdC1 = nodeManager.createNewNode(nodeC1, testUserInfo);
		assertNotNull(nodeIdC1);
		toClearList.add(nodeIdC1);
		
		// Move all of them to trash can
		trashManager.moveToTrash(testUserInfo, nodeIdA1);
		trashManager.moveToTrash(testUserInfo, nodeIdA2);

		// Purge B2 (a child)
		trashManager.purgeTrashForUser(testUserInfo, nodeIdB2);
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(4L, results.getTotalNumberOfResults());
		assertEquals(4, results.getResults().size());
		for (TrashedEntity trash : results.getResults()) {
			if (nodeIdB2.equals(trash.getEntityId())) {
				fail();
			}
		}
		String testUserId = testUserInfo.getId().toString();
		assertFalse(trashCanDao.exists(testUserId, nodeIdB2));

		// Purge A1 (a root with 2 descendants)
		trashManager.purgeTrashForUser(testUserInfo, nodeIdA1);
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(1L, results.getTotalNumberOfResults());
		assertEquals(1, results.getResults().size());
		assertEquals(nodeIdA2, results.getResults().get(0).getEntityId());
		assertFalse(trashCanDao.exists(testUserId, nodeIdA1));
		assertFalse(trashCanDao.exists(testUserId, nodeIdB1));
		assertFalse(trashCanDao.exists(testUserId, nodeIdC1));

		// Purge A2 (a root with no children)
		trashManager.purgeTrashForUser(testUserInfo, nodeIdA2);
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());
		assertFalse(trashCanDao.exists(testUserId, nodeIdA2));
	}

	@Test
	public void testPurgeAllForUser() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		//
		//           root
		//           /  \
		//          A1  A2
		//          |    |
		//          B1  B2
		//          |
		//          C1
		//
		final Node nodeA1 = new Node();
		final String nodeNameA1 = "TrashManagerImplAutowiredTest.testPurge() A1";
		nodeA1.setName(nodeNameA1);
		nodeA1.setNodeType(EntityType.project.name());
		final String nodeIdA1 = nodeManager.createNewNode(nodeA1, testUserInfo);
		assertNotNull(nodeIdA1);
		toClearList.add(nodeIdA1);

		final Node nodeA2 = new Node();
		final String nodeNameA2 = "TrashManagerImplAutowiredTest.testPurge() A2";
		nodeA2.setName(nodeNameA2);
		nodeA2.setNodeType(EntityType.project.name());
		final String nodeIdA2 = nodeManager.createNewNode(nodeA2, testUserInfo);
		assertNotNull(nodeIdA2);
		toClearList.add(nodeIdA2);

		final Node nodeB1 = new Node();
		final String nodeNameB1 = "TrashManagerImplAutowiredTest.testPurge() B1";
		nodeB1.setName(nodeNameB1);
		nodeB1.setNodeType(EntityType.folder.name());
		nodeB1.setParentId(nodeIdA1);
		final String nodeIdB1 = nodeManager.createNewNode(nodeB1, testUserInfo);
		assertNotNull(nodeIdB1);
		toClearList.add(nodeIdB1);
		
		final Node nodeB2 = new Node();
		final String nodeNameB2 = "TrashManagerImplAutowiredTest.testPurge() B2";
		nodeB2.setName(nodeNameB2);
		nodeB2.setNodeType(EntityType.folder.name());
		nodeB2.setParentId(nodeIdA2);
		final String nodeIdB2 = nodeManager.createNewNode(nodeB2, testUserInfo);
		assertNotNull(nodeIdB2);
		toClearList.add(nodeIdB2);

		final Node nodeC1 = new Node();
		final String nodeNameC1 = "TrashManagerImplAutowiredTest.testPurge() C1";
		nodeC1.setName(nodeNameC1);
		nodeC1.setNodeType(EntityType.dataset.name());
		nodeC1.setParentId(nodeIdB1);
		final String nodeIdC1 = nodeManager.createNewNode(nodeC1, testUserInfo);
		assertNotNull(nodeIdC1);
		toClearList.add(nodeIdC1);

		// Move all of them to trash can
		trashManager.moveToTrash(testUserInfo, nodeIdA1);
		trashManager.moveToTrash(testUserInfo, nodeIdA2);

		// Purge the trash can
		trashManager.purgeTrashForUser(testUserInfo);
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA1)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB1)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdC1)));
	}

	@Test
	public void testPurgeList() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		//
		//           root
		//           /  \
		//          A1  A2
		//          |    |
		//          B1  B2
		//          |
		//          C1
		//
		final Node nodeA1 = new Node();
		final String nodeNameA1 = "TrashManagerImplAutowiredTest.testPurge() A1";
		nodeA1.setName(nodeNameA1);
		nodeA1.setNodeType(EntityType.project.name());
		final String nodeIdA1 = nodeManager.createNewNode(nodeA1, testUserInfo);
		assertNotNull(nodeIdA1);
		toClearList.add(nodeIdA1);

		final Node nodeA2 = new Node();
		final String nodeNameA2 = "TrashManagerImplAutowiredTest.testPurge() A2";
		nodeA2.setName(nodeNameA2);
		nodeA2.setNodeType(EntityType.project.name());
		final String nodeIdA2 = nodeManager.createNewNode(nodeA2, testUserInfo);
		assertNotNull(nodeIdA2);
		toClearList.add(nodeIdA2);

		final Node nodeB1 = new Node();
		final String nodeNameB1 = "TrashManagerImplAutowiredTest.testPurge() B1";
		nodeB1.setName(nodeNameB1);
		nodeB1.setNodeType(EntityType.folder.name());
		nodeB1.setParentId(nodeIdA1);
		final String nodeIdB1 = nodeManager.createNewNode(nodeB1, testUserInfo);
		assertNotNull(nodeIdB1);
		toClearList.add(nodeIdB1);
		
		final Node nodeB2 = new Node();
		final String nodeNameB2 = "TrashManagerImplAutowiredTest.testPurge() B2";
		nodeB2.setName(nodeNameB2);
		nodeB2.setNodeType(EntityType.folder.name());
		nodeB2.setParentId(nodeIdA2);
		final String nodeIdB2 = nodeManager.createNewNode(nodeB2, testUserInfo);
		assertNotNull(nodeIdB2);
		toClearList.add(nodeIdB2);

		final Node nodeC1 = new Node();
		final String nodeNameC1 = "TrashManagerImplAutowiredTest.testPurge() C1";
		nodeC1.setName(nodeNameC1);
		nodeC1.setNodeType(EntityType.dataset.name());
		nodeC1.setParentId(nodeIdB1);
		final String nodeIdC1 = nodeManager.createNewNode(nodeC1, testUserInfo);
		assertNotNull(nodeIdC1);
		toClearList.add(nodeIdC1);

		// Move all of them to trash can
		trashManager.moveToTrash(testUserInfo, nodeIdA1);
		trashManager.moveToTrash(testUserInfo, nodeIdA2);

		// Purge B1, C1, A2, B2, and keep A1
		List<TrashedEntity> allTrash = trashManager.viewTrashForUser(
				testUserInfo, testUserInfo, 0L, 1000L).getResults();
		List<TrashedEntity> purgeList = new ArrayList<TrashedEntity>(4);
		for (TrashedEntity trash : allTrash) {
			if (nodeIdC1.equals(trash.getEntityId())) {
				purgeList.add(trash);
			}
			if (nodeIdB1.equals(trash.getEntityId())) {
				purgeList.add(trash);
			}
			if (nodeIdB2.equals(trash.getEntityId())) {
				purgeList.add(trash);
			}
			if (nodeIdA2.equals(trash.getEntityId())) {
				purgeList.add(trash);
			}
		}
		assertEquals(4, purgeList.size());
		trashManager.purgeTrash(purgeList);
		results = trashManager.viewTrashForUser(testUserInfo, testUserInfo, 0L, 1000L);
		assertEquals(1L, results.getTotalNumberOfResults());
		assertEquals(1, results.getResults().size());
		assertTrue(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA1)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB1)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdC1)));
	}

	@Test
	public void testAdmin() throws Exception {

		QueryResults<TrashedEntity> results = trashManager.viewTrash(
				testAdminUserInfo, 0L, Long.MAX_VALUE);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());

		//
		//           root
		//           /  \
		//          A1  A2
		//          |    |
		//          B1  B2
		//          |
		//          C1
		//
		// A1, B1, C1 are created by test admin user
		// A2, B2 are create by test user
		//
		final Node nodeA1 = new Node();
		final String nodeNameA1 = "TrashManagerImplAutowiredTest.testPurge() A1";
		nodeA1.setName(nodeNameA1);
		nodeA1.setNodeType(EntityType.project.name());
		final String nodeIdA1 = nodeManager.createNewNode(nodeA1, testAdminUserInfo);
		assertNotNull(nodeIdA1);
		toClearList.add(nodeIdA1);

		final Node nodeA2 = new Node();
		final String nodeNameA2 = "TrashManagerImplAutowiredTest.testPurge() A2";
		nodeA2.setName(nodeNameA2);
		nodeA2.setNodeType(EntityType.project.name());
		final String nodeIdA2 = nodeManager.createNewNode(nodeA2, testUserInfo);
		assertNotNull(nodeIdA2);
		toClearList.add(nodeIdA2);

		final Node nodeB1 = new Node();
		final String nodeNameB1 = "TrashManagerImplAutowiredTest.testPurge() B1";
		nodeB1.setName(nodeNameB1);
		nodeB1.setNodeType(EntityType.folder.name());
		nodeB1.setParentId(nodeIdA1);
		final String nodeIdB1 = nodeManager.createNewNode(nodeB1, testAdminUserInfo);
		assertNotNull(nodeIdB1);
		toClearList.add(nodeIdB1);

		final Node nodeB2 = new Node();
		final String nodeNameB2 = "TrashManagerImplAutowiredTest.testPurge() B2";
		nodeB2.setName(nodeNameB2);
		nodeB2.setNodeType(EntityType.folder.name());
		nodeB2.setParentId(nodeIdA2);
		final String nodeIdB2 = nodeManager.createNewNode(nodeB2, testUserInfo);
		assertNotNull(nodeIdB2);
		toClearList.add(nodeIdB2);

		final Node nodeC1 = new Node();
		final String nodeNameC1 = "TrashManagerImplAutowiredTest.testPurge() C1";
		nodeC1.setName(nodeNameC1);
		nodeC1.setNodeType(EntityType.dataset.name());
		nodeC1.setParentId(nodeIdB1);
		final String nodeIdC1 = nodeManager.createNewNode(nodeC1, testAdminUserInfo);
		assertNotNull(nodeIdC1);
		toClearList.add(nodeIdC1);

		// Move all of them to trash can
		trashManager.moveToTrash(testAdminUserInfo, nodeIdA1);
		trashManager.moveToTrash(testUserInfo, nodeIdA2);

		results = trashManager.viewTrash(testAdminUserInfo, 0L, Long.MAX_VALUE);
		assertNotNull(results);
		assertEquals(5, results.getTotalNumberOfResults());
		assertEquals(5, results.getResults().size());
		results = trashManager.viewTrashForUser(testAdminUserInfo, testUserInfo, 0L, Long.MAX_VALUE);
		assertNotNull(results);
		assertEquals(2, results.getTotalNumberOfResults());
		assertEquals(2, results.getResults().size());
		try {
			results = trashManager.viewTrash(testUserInfo, 0L, Long.MAX_VALUE);
			fail();
		} catch (UnauthorizedException e) {
			assertTrue(true);
		}
		try {
			results = trashManager.viewTrashForUser(testUserInfo, testAdminUserInfo, 0L, Long.MAX_VALUE);
			fail();
		} catch (UnauthorizedException e) {
			assertTrue(true);
		}

		try {
			nodeManager.get(testAdminUserInfo, nodeIdA1);
			fail("Once the entity is in trash can, even admin can't view it.");
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}
		try {
			nodeManager.get(testAdminUserInfo, nodeIdA2);
			fail("Once the entity is in trash can, even admin can't view it.");
		} catch (EntityInTrashCanException e) {
			assertTrue(true);
		}

		// Admin can restore items put in trash can by test user
		try {
			trashManager.restoreFromTrash(testAdminUserInfo, nodeIdA2, null);
		} catch (UnauthorizedException e) {
			fail("Admin should be able to restore.");
		}

		// But the test user cannot restore items put by admin
		try {
			trashManager.restoreFromTrash(testUserInfo, nodeIdA1, null);
		} catch (UnauthorizedException e) {
			assertTrue(true);
		}

		try {
			trashManager.purgeTrash(testUserInfo);
			fail();
		} catch (UnauthorizedException e) {
			assertTrue(true);
		}
		trashManager.purgeTrash(testAdminUserInfo);
		results = trashManager.viewTrash(testAdminUserInfo, 0L, 1000L);
		assertEquals(0L, results.getTotalNumberOfResults());
		assertEquals(0, results.getResults().size());
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA1)));
		// Node A2 has been restored by admin
		assertTrue(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdA2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB1)));
		// Node B2 has been restored by admin
		assertTrue(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdB2)));
		assertFalse(nodeDAO.doesNodeExist(KeyFactory.stringToKey(nodeIdC1)));
	}

	@Test(expected=EntityInTrashCanException.class)
	public void testCanDownload() throws Exception {
		final Node node = new Node();
		final String nodeName = "TrashManagerImplAutowiredTest.testCanDownload()";
		node.setName(nodeName);
		node.setNodeType(EntityType.project.name());
		final String nodeId = nodeManager.createNewNode(node, testAdminUserInfo);
		assertNotNull(nodeId);
		toClearList.add(nodeId);
		trashManager.moveToTrash(testAdminUserInfo, nodeId);
		entityPermissionsManager.hasAccess(nodeId, ACCESS_TYPE.DOWNLOAD, testAdminUserInfo);
	}

	private void cleanUp() throws Exception {
		if (accessRequirementToDelete!=null) {
			accessRequirementManager.deleteAccessRequirement(testAdminUserInfo, accessRequirementToDelete.getId().toString());
		}
		for (String nodeId : toClearList) {
			try {
				nodeManager.delete(testAdminUserInfo, nodeId);
			} catch (NotFoundException e) {}
		}
		List<TrashedEntity> trashList = trashCanDao.getInRange(0L, Long.MAX_VALUE);
		for (TrashedEntity trash : trashList) {
			trashCanDao.delete(trash.getDeletedByPrincipalId(), trash.getEntityId());
		}
		List<String> children = nodeDAO.getChildrenIdsAsList(trashCanId);
		for (String child : children) {
			try {
				nodeManager.delete(testAdminUserInfo, child);
			} catch (NotFoundException e) {}
		}
	}
}
