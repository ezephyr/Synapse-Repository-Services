package org.sagebionetworks.dynamo.workers.sqs;

import java.util.List;
import java.util.concurrent.Callable;

import org.sagebionetworks.asynchronous.workers.sqs.MessageWorkerFactory;
import org.sagebionetworks.asynchronous.workers.sqs.WorkerProgress;

import com.amazonaws.services.sqs.model.Message;

public class DynamoQueueRemoverFactory implements MessageWorkerFactory {
	@Override
	public Callable<List<Message>> createWorker(final List<Message> messages, WorkerProgress workerProgress) {
		return new Callable<List<Message>>() {
			@Override
			public List<Message> call() {
				return messages;
			}
		};
	}
}
