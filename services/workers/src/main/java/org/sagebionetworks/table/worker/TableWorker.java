package org.sagebionetworks.table.worker;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.asynchronous.workers.sqs.MessageUtils;
import org.sagebionetworks.asynchronous.workers.sqs.WorkerProgress;
import org.sagebionetworks.repo.manager.table.TableRowManager;
import org.sagebionetworks.repo.model.ConflictingUpdateException;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.dbo.dao.table.TableModelUtils;
import org.sagebionetworks.repo.model.exception.LockUnavilableException;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.ChangeType;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.RowSet;
import org.sagebionetworks.repo.model.table.TableRowChange;
import org.sagebionetworks.repo.model.table.TableStatus;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.table.cluster.ConnectionFactory;
import org.sagebionetworks.table.cluster.TableIndexDAO;

import com.amazonaws.services.sqs.model.Message;
import com.google.common.collect.SetMultimap;

/**
 * This worker updates the index used to support the tables features. It will
 * listen to table changes and apply them to the RDS table that acts as the
 * index of the table feature.
 * 
 * @author John
 * 
 */
public class TableWorker implements Callable<List<Message>> {

	enum State {
		SUCCESS, UNRECOVERABLE_FAILURE, RECOVERABLE_FAILURE,
	}

	private static final long BATCH_SIZE = 16000;

	static private Logger log = LogManager.getLogger(TableWorker.class);
	List<Message> messages;
	ConnectionFactory tableConnectionFactory;
	TableRowManager tableRowManager;
	boolean featureEnabled;
	long timeoutMS;
	WorkerProgress workerProgress;

	/**
	 * This worker has many dependencies.
	 * 
	 * @param messages
	 * @param tableConnectionFactory
	 * @param tableTruthDAO
	 * @param columnModelDAO
	 * @param tableIndexDAO
	 * @param semaphoreDao
	 * @param tableStatusDAO
	 * @param configuration
	 * @param timeoutMS
	 */
	public TableWorker(List<Message> messages,
			ConnectionFactory tableConnectionFactory,
			TableRowManager tableRowManager, StackConfiguration configuration, WorkerProgress workerProgress) {
		super();
		this.messages = messages;
		this.tableConnectionFactory = tableConnectionFactory;
		this.tableRowManager = tableRowManager;
		this.featureEnabled = configuration.getTableEnabled();
		this.timeoutMS = configuration.getTableWorkerTimeoutMS();
		this.workerProgress = workerProgress;
	}

	@Override
	public List<Message> call() throws Exception {
		List<Message> processedMessages = new LinkedList<Message>();
		// If the feature is disabled then we simply swallow all messages
		if (!featureEnabled) {
			return messages;
		}
		// process each message
		for (Message message : messages) {
			// Extract the ChangeMessage
			ChangeMessage change = MessageUtils.extractMessageBody(message);
			// We only care about entity messages here
			if (ObjectType.TABLE.equals((change.getObjectType()))) {
				if (ChangeType.DELETE.equals(change.getChangeType())) {
					// Delete the table in the index
					TableIndexDAO indexDao = tableConnectionFactory
							.getConnection(change.getObjectId());
					if (indexDao != null) {
						indexDao.deleteTable(change.getObjectId());
						indexDao.deleteStatusTable(change.getObjectId());
					}
					processedMessages.add(message);
				} else {
					// Create or update.
					String tableId = change.getObjectId();
					// this method does the real work.
					State state = createOrUpdateTable(tableId,
							change.getObjectEtag(), message);
					if (!State.RECOVERABLE_FAILURE.equals(state)) {
						// Only recoverable failures should remain in the queue.
						// All other must be removed.
						processedMessages.add(message);
					}
				}
			} else {
				// Non-table messages must be returned so they can be removed
				// from the queue.
				processedMessages.add(message);
			}
		}
		return processedMessages;
	}

	/**
	 * This is where a single table index is created or updated.
	 * 
	 * @param tableId
	 * @return
	 */
	public State createOrUpdateTable(final String tableId,
			final String tableResetToken, final Message message) {
		// Attempt to run with
		try {
			// If the passed token does not match the current token then this 
			// is an old message that should be removed from the queue.
			// See PLFM-2641.  We must check message before we acquire the lock.
			TableStatus status = tableRowManager.getTableStatusOrCreateIfNotExists(tableId);
			// If the reset-tokens do not match this message should be ignored
			if (!tableResetToken.equals(status.getResetToken())) {
				// This is an old message so we ignore it
				return State.SUCCESS;
			}
			// Run with the exclusive lock on the table if we can get it.
			return tableRowManager.tryRunWithTableExclusiveLock(tableId,
					timeoutMS, new Callable<State>() {
						@Override
						public State call() throws Exception {
							// This method does the real work.
							return createOrUpdateWhileHoldingLock(tableId,
									tableResetToken, message);
						}
					});
		} catch (LockUnavilableException e) {
			// We did not get the lock on this table.
			// This is a recoverable failure as we can try again later.
			return State.RECOVERABLE_FAILURE;
		}catch (ConflictingUpdateException e) {
			// This is thrown when the table gets updated while it is being
			// processed.
			// We cannot continue with this attempt.
			return State.UNRECOVERABLE_FAILURE;
		} catch (NotFoundException e) {
			// This is thrown if the table no longer exits
			return State.UNRECOVERABLE_FAILURE;
		} catch (InterruptedException e) {
			// This is a recoverable failure as we can try again later.
			return State.RECOVERABLE_FAILURE;
		} catch (Exception e) {
			log.error("Failed with unknown error", e);
			// Cannot recover from this.
			return State.UNRECOVERABLE_FAILURE;
		}
	}

	/**
	 * This is where the table status gets stated and error handling is
	 * performed.
	 * 
	 * @param tableId
	 * @param token
	 * @return
	 * @throws NotFoundException 
	 * @throws ConflictingUpdateException 
	 */
	private State createOrUpdateWhileHoldingLock(String tableId,
			String tableResetToken, Message message) throws ConflictingUpdateException, NotFoundException {
		// Start the real work
		try {
			// Save the status before we start
			// Try to get a connection.
			TableIndexDAO indexDAO = tableConnectionFactory
					.getConnection(tableId);
			// If we do not have connection we can try again later
			if (indexDAO == null) {
				return State.RECOVERABLE_FAILURE;
			}
			// This method will do the rest of the work.
			String lastEtag = synchIndexWithTable(indexDAO, tableId, tableResetToken, message);
			// We are finished set the status
			tableRowManager.attemptToSetTableStatusToAvailable(tableId,	tableResetToken, lastEtag);
			return State.SUCCESS;
		} catch (Exception e) {
			// Failed.
			// Get the stack trace.
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			// Attempt to set the status to failed.
			tableRowManager.attemptToSetTableStatusToFailed(tableId, tableResetToken, e.getMessage(), writer.toString());
			// This is not an error we can recover from.
			return State.UNRECOVERABLE_FAILURE;
		}
	}

	/**
	 * Synchronizes the table index with the table truth data. After the
	 * successful completion of this method the table index schema will match
	 * the schema of the truth and all row changes that have not already been
	 * applied will be applied. Note: This method will do no work if the index
	 * and truth are already synchronized.
	 * 
	 * @param connection
	 * @param tableId
	 * @param status
	 * @throws DatastoreException
	 * @throws NotFoundException
	 * @throws IOException
	 */
	String synchIndexWithTable(TableIndexDAO indexDao,
			String tableId, String resetToken, Message message) throws DatastoreException, NotFoundException,
			IOException {
		// The first task is to get the table schema in-synch.
		// Get the current schema of the table.
		List<ColumnModel> currentSchema = tableRowManager.getColumnModelsForTable(tableId);
		// Create or update the table with this schema.
		tableRowManager.attemptToUpdateTableProgress(tableId, resetToken, "Creating table ", 0L, 100L);
		if(currentSchema.isEmpty()){
			// If there is no schema delete the table
			indexDao.deleteTable(tableId);
		}else{
			// We have a schema so create or update the table
			indexDao.createOrUpdateTable(currentSchema, tableId);
		}
		// Now determine which changes need to be applied to the table
		Long maxCurrentCompleteVersion = indexDao.getMaxCurrentCompleteVersionForTable(tableId);
		// List all of the changes
		tableRowManager.attemptToUpdateTableProgress(tableId, resetToken, "Getting current table row versions ", 0L, 100L);

		TableRowChange lastTableRowChange = tableRowManager.getLastTableRowChange(tableId);
		if (lastTableRowChange == null) {
			// nothing to do, move along
			return null;
		}

		long currentProgress = 0;
		long maxRowId = tableRowManager.getMaxRowId(tableId);
		for (long rowId = 0; rowId <= maxRowId; rowId += BATCH_SIZE) {
			Map<Long, Long> currentRowVersions = tableRowManager.getCurrentRowVersions(tableId, maxCurrentCompleteVersion + 1, rowId,
					BATCH_SIZE);

			// gather rows by version
			SetMultimap<Long, Long> versionToRowsMap = TableModelUtils.createVersionToRowsMap(currentRowVersions);
			for (Entry<Long, Collection<Long>> versionWithRows : versionToRowsMap.asMap().entrySet()) {
				Set<Long> rowsToGet = (Set<Long>) versionWithRows.getValue();
				Long version = versionWithRows.getKey();

				// Keep this message invisible
				workerProgress.progressMadeForMessage(message);

				// This is a change that we must apply.
				RowSet rowSet = tableRowManager.getRowSet(tableId, version, rowsToGet);

				tableRowManager.attemptToUpdateTableProgress(tableId, resetToken, "Applying rows " + rowSet.getRows().size()
						+ " to version: " + version, currentProgress, currentProgress);
				// apply the change to the table
				indexDao.createOrUpdateOrDeleteRows(rowSet, currentSchema);
				currentProgress += rowsToGet.size();
			}
		}

		// If we successfully updated the table and got here, then all we know is that we are at least at the version
		// the table was at when we started. It is still possible that a newer version came in and the we applied
		// partial updates from that version to the index. A subsequent update will make things consistent again.
		indexDao.setMaxCurrentCompleteVersionForTable(tableId, lastTableRowChange.getRowVersion());

		return lastTableRowChange.getEtag();
	}
}
