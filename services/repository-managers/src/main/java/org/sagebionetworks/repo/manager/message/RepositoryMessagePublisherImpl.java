package org.sagebionetworks.repo.manager.message;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.TransactionalMessenger;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.EntityFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishRequest;

/**
 * The basic implementation of the RepositoryMessagePublisher.  This implementation will publish all messages to an AWS topic
 * where external subscribers can receive notification of changes to the repository.
 * 
 * @author John
 *
 */
public class RepositoryMessagePublisherImpl implements RepositoryMessagePublisher {

	public static final String SEMAPHORE_KEY = "UNSENT_MESSAGE_WORKER";
	static private Log log = LogFactory.getLog(RepositoryMessagePublisherImpl.class);

	@Autowired
	TransactionalMessenger transactionalMessanger;

	@Autowired
	AmazonSNSClient awsSNSClient;
	
	private boolean shouldMessagesBePublishedToTopic;
	
	// Maps each object type to its topic
	Map<ObjectType, TopicInfo> typeToTopicMap = new HashMap<ObjectType, TopicInfo>();;
	// The prefix applied to each topic.
	String topicPrefix;
	
	/**
	 * This is injected from spring.
	 * 
	 * @param shouldMessagesBePublishedToTopic
	 */
	public void setShouldMessagesBePublishedToTopic(
			boolean shouldMessagesBePublishedToTopic) {
		this.shouldMessagesBePublishedToTopic = shouldMessagesBePublishedToTopic;
	}

	/**
	 * Default.
	 */
	public RepositoryMessagePublisherImpl(){
		super();
	}

	/**
	 * IoC constructor.
	 * @param transactionalMessanger
	 * @param awsSNSClient
	 * @param topicArn
	 * @param topicName
	 * @param messageQueue
	 */
	public RepositoryMessagePublisherImpl(TransactionalMessenger transactionalMessanger,
			AmazonSNSClient awsSNSClient) {
		super();
		this.transactionalMessanger = transactionalMessanger;
		this.awsSNSClient = awsSNSClient;
	}

	/**
	 * Used by tests to inject a mock client.
	 * @param awsSNSClient
	 */
	public void setAwsSNSClient(AmazonSNSClient awsSNSClient) {
		this.awsSNSClient = awsSNSClient;
	}


	private ConcurrentLinkedQueue<ChangeMessage> messageQueue = new ConcurrentLinkedQueue<ChangeMessage>();

	public RepositoryMessagePublisherImpl(final String topicPrefix) {
		if (topicPrefix == null) {
			throw new IllegalArgumentException("topicPrefix cannot be null");
		}
		this.topicPrefix = topicPrefix;
	}

	/**
	 *
	 * This is called by Spring when this bean is created.  This is where we register this class as
	 * an observer of the TransactionalMessenger
	 */
	public void initialize(){
		// We only want to be in the list once
		transactionalMessanger.removeObserver(this);
		transactionalMessanger.registerObserver(this);
	}

	/**
	 * This is the method that the TransactionalMessenger will call after a transaction is committed.
	 * This is our chance to push these messages to our AWS topic.
	 */
	@Override
	public void fireChangeMessage(ChangeMessage message) {
		if(message == null) throw new IllegalArgumentException("ChangeMessage cannot be null");
		if(message.getChangeNumber()  == null) throw new IllegalArgumentException("ChangeMessage.getChangeNumber() cannot be null");
		if(message.getObjectId()  == null) throw new IllegalArgumentException("ChangeMessage.getObjectId() cannot be null");
		if(message.getObjectType()  == null) throw new IllegalArgumentException("ChangeMessage.getObjectType() cannot be null");
		if(message.getTimestamp()  == null) throw new IllegalArgumentException("ChangeMessage.getTimestamp() cannot be null");
		// Add the message to a queue
		messageQueue.add(message);
	}

	@Override
	public String getTopicName(ObjectType type){
		return getTopicInfoLazy(type).getName();
	}

	@Override
	public String getTopicArn(ObjectType type) {
		return getTopicInfoLazy(type).getArn();
	}

	/**
	 * Quartz will fire this method on a timer.  This is where we actually publish the data. 
	 */
	@Override
	public void timerFired(){
		// Poll all data from the queue.
		List<ChangeMessage> currentQueue = pollListFromQueue();
		if(!shouldMessagesBePublishedToTopic){
			// The messages should not be broadcast
			if(log.isDebugEnabled() && currentQueue.size() > 0){
				log.debug("RepositoryMessagePublisherImpl.shouldBroadcast = false.  So "+currentQueue.size()+" messages will be thrown away.");
			}
			return;
		}
		// Publish each message to the topic
		for(ChangeMessage message: currentQueue){
			try {
				publishToTopic(message);
			} catch (Throwable e) {
				// If one messages fails, we must send the rest.
				log.error("Failed to publish message.", e);
			}
		}
	}
	
	/**
	 * Poll all data currently on the queue and add it to a list.
	 * @return
	 */
	private List<ChangeMessage> pollListFromQueue(){
		List<ChangeMessage> list = new LinkedList<ChangeMessage>();
		for(ChangeMessage cm = this.messageQueue.poll(); cm != null; cm = this.messageQueue.poll()){
			// Add to the list
			list.add(cm);
		}
		return list;
	}
	/**
	 * Get the topic info for a given type (lazy loaded).
	 * @param type
	 * @return
	 */
	private TopicInfo getTopicInfoLazy(ObjectType type){
		if(type == null){
			throw new IllegalArgumentException("ObjectType cannot be null");
		}
		TopicInfo info = this.typeToTopicMap.get(type);
		if(info == null){
			// Create the topic
			String name = this.topicPrefix+type.name();
			CreateTopicResult result = awsSNSClient.createTopic(new CreateTopicRequest(name));
			String arn = result.getTopicArn();
			info = new TopicInfo(name, arn);
			this.typeToTopicMap.put(type, info);
		}
		return info;
	}

	/**
	 * Publish the message and recored it as sent.
	 * Each sent message requires its own transaction.
	 * @param message
	 */
	@Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
	@Override
	public boolean publishToTopic(ChangeMessage message) {
		String json;
		try {
			json = EntityFactory.createJSONStringForEntity(message);
		} catch (JSONObjectAdapterException e) {
			// should never occur
			throw new RuntimeException(e);
		}
		if (log.isTraceEnabled()) {
			log.info("Publishing a message: " + json);
		}
		// Register the message was sent within this transaction.
		// It is important to do this before we actual send the message to the
		// topic because we do not want to sent out duplicate messages (see
		// PLFM-2821)
		boolean isChange = this.transactionalMessanger.registerMessageSent(message);
		if(isChange){
			String topicArn = getTopicInfoLazy(message.getObjectType()).getArn();
			// Publish the message to the topic.
			// NOTE: If this fails the transaction will be rolled back so
			// the message will not be registered as sent.
			awsSNSClient.publish(new PublishRequest(topicArn, json));
		}
		return true;
	}
	
	/**
	 * Information about a topic.
	 *
	 */
	private static class TopicInfo{
		private String name;
		private String arn;
		public TopicInfo(String name, String arn) {
			super();
			this.name = name;
			this.arn = arn;
		}
		public String getName() {
			return name;
		}
		public String getArn() {
			return arn;
		}
	}
}
