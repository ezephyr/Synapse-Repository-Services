package org.sagebionetworks.annotations.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sagebionetworks.asynchronous.workers.sqs.MessageUtils;
import org.sagebionetworks.cloudwatch.WorkerLogger;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.SubmissionStatusAnnotationsAsyncManager;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.ChangeType;
import org.sagebionetworks.repo.web.NotFoundException;

import com.amazonaws.services.sqs.model.Message;

/**
 * Test for AnnotationsWorker
 */
public class AnnotationsWorkerTest {
	
	SubmissionStatusAnnotationsAsyncManager mockDAO;
	WorkerLogger mockWorkerLogger;
	
	@Before
	public void before(){
		mockDAO = Mockito.mock(SubmissionStatusAnnotationsAsyncManager.class);
		mockWorkerLogger = Mockito.mock(WorkerLogger.class);
	}
	
	/**
	 * non entity messages should be ignored.
	 * @throws Exception 
	 */
	@Test
	public void testCallNonSubmission() throws Exception{
		ChangeMessage message = new ChangeMessage();
		message.setObjectType(ObjectType.ENTITY);
		message.setObjectId("123");
		Message awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		List<Message> list = new LinkedList<Message>();
		list.add(awsMessage);
		// Make the call
		AnnotationsWorker worker = new AnnotationsWorker(list, mockDAO, mockWorkerLogger);
		List<Message> resultList = worker.call();
		assertNotNull(resultList);
		// Non-Submission messages should be returned so they can be removed from the queue.
		assertEquals("Non-Submission messages must be returned so they can be removed from the queue!",list, resultList);
		// the DAO should not be called
		verify(mockDAO, never()).updateEvaluationSubmissionStatuses(any(String.class),any(String.class));
		verify(mockDAO, never()).deleteEvaluationSubmissionStatuses(any(String.class),any(String.class));
	}
	
	@Test
	public void testUpdateSubmissionStatus() throws Exception{
		ChangeMessage message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.UPDATE);
		message.setObjectId("123");
		Message awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		List<Message> list = new LinkedList<Message>();
		list.add(awsMessage);
		// Make the call
		AnnotationsWorker worker = new AnnotationsWorker(list, mockDAO, mockWorkerLogger);
		list = worker.call();
		assertNotNull(list);
		// the manager should not be called
		verify(mockDAO).updateEvaluationSubmissionStatuses(eq(message.getObjectId()), anyString());
		verify(mockDAO, never()).deleteEvaluationSubmissionStatuses(eq(message.getObjectId()), anyString());
	}
	
	@Test
	public void testDeleteSubmission() throws Exception{
		ChangeMessage message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.DELETE);
		message.setObjectId("123");
		Message awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		List<Message> list = new LinkedList<Message>();
		list.add(awsMessage);
		// Make the call
		AnnotationsWorker worker = new AnnotationsWorker(list, mockDAO, mockWorkerLogger);
		list = worker.call();
		assertNotNull(list);
		// the manager should not be called
		verify(mockDAO, never()).updateEvaluationSubmissionStatuses(eq(message.getObjectId()), anyString());
		verify(mockDAO).deleteEvaluationSubmissionStatuses(any(String.class),any(String.class));
	}
	
	
	/**
	 * When a not found exception is thrown we want to process and remove the message from the queue.
	 * @throws Exception
	 */
	@Test
	public void testNotFound() throws Exception{
		// Test the case where an error occurs and and there is success
		List<Message> list = new LinkedList<Message>();
		// This will succeed
		ChangeMessage message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.UPDATE);
		String successId = "success";
		message.setObjectId(successId);
		Message awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		list.add(awsMessage);
		// This will fail
		message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.UPDATE);
		String failId = "fail";
		message.setObjectId(failId);
		awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		list.add(awsMessage);
		// Simulate a not found
		doThrow(new NotFoundException()).when(mockDAO).updateEvaluationSubmissionStatuses(eq(failId), anyString());
		AnnotationsWorker worker = new AnnotationsWorker(list, mockDAO, mockWorkerLogger);
		List<Message> resultLIst = worker.call();
		assertEquals(list, resultLIst);
	}
	
	/**
	 * When an unknown exception occurs we should not clear the message from the queue.
	 * @throws Exception
	 */
	@Test
	public void testUnknownException() throws Exception{
		// Test the case where an error occurs and and there is success
		List<Message> list = new LinkedList<Message>();
		// This will succeed
		ChangeMessage message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.UPDATE);
		String successId = "success";
		message.setObjectId(successId);
		Message awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		list.add(awsMessage);
		// This will fail
		message = new ChangeMessage();
		message.setObjectType(ObjectType.EVALUATION_SUBMISSIONS);
		message.setChangeType(ChangeType.UPDATE);
		String failId = "fail";
		message.setObjectId(failId);
		awsMessage = MessageUtils.createMessage(message, "abc", "handle");
		list.add(awsMessage);
		// Simulate a runtime exception
		doThrow(new RuntimeException()).when(mockDAO).updateEvaluationSubmissionStatuses(eq(failId), anyString());
		AnnotationsWorker worker = new AnnotationsWorker(list, mockDAO, mockWorkerLogger);
		List<Message> resultLIst = worker.call();
		assertEquals(list, resultLIst);
	}
}
