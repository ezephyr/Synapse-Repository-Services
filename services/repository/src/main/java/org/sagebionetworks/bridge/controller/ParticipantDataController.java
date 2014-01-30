package org.sagebionetworks.bridge.controller;

import java.util.List;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagebionetworks.bridge.BridgeUrlHelpers;
import org.sagebionetworks.bridge.model.data.ParticipantDataColumnDescriptor;
import org.sagebionetworks.bridge.model.data.ParticipantDataCurrentRow;
import org.sagebionetworks.bridge.model.data.ParticipantDataDescriptor;
import org.sagebionetworks.bridge.model.data.ParticipantDataRow;
import org.sagebionetworks.bridge.model.data.ParticipantDataStatusList;
import org.sagebionetworks.bridge.service.BridgeServiceProvider;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import org.sagebionetworks.repo.model.ListWrapper;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.PaginatedResults;
import org.sagebionetworks.repo.model.ServiceConstants;
import org.sagebionetworks.repo.model.IdList;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ControllerInfo(displayName = "Participant data", path = BridgeUrlHelpers.BASE_V1)
@Controller
public class ParticipantDataController {
	
	private static final Logger logger = LogManager.getLogger(ParticipantDataController.class.getName());

	@Autowired
	BridgeServiceProvider serviceProvider;

	/**
	 * Append new participant data rows
	 * 
	 * @param userId
	 * @param participantDataId
	 * @param data
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_ID, method = RequestMethod.POST)
	public @ResponseBody
	ListWrapper<ParticipantDataRow> appendParticipantData(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@PathVariable String participantDataId, @RequestBody ListWrapper<ParticipantDataRow> data) throws Exception {
		List<ParticipantDataRow> rows = serviceProvider.getParticipantDataService().append(userId, participantDataId, data.getList());
		return ListWrapper.wrap(rows, ParticipantDataRow.class);
	}

	/**
	 * Append participant data rows for another participant
	 * 
	 * @param userId
	 * @param participantId
	 * @param participantDataId
	 * @param data
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = BridgeUrlHelpers.APPEND_FOR_PARTICIPANT_DATA, method = RequestMethod.POST)
	public @ResponseBody
	ListWrapper<ParticipantDataRow> appendParticipantData(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId, @PathVariable String participantId,
			@PathVariable String participantDataId, @RequestBody ListWrapper<ParticipantDataRow> data) throws Exception {
		List<ParticipantDataRow> rows = serviceProvider.getParticipantDataService().append(userId, participantId, participantDataId,
				data.getList());
		return ListWrapper.wrap(rows, ParticipantDataRow.class);
	}
	
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@RequestMapping(value = BridgeUrlHelpers.DELETE_FOR_PARTICIPANT_DATA, method = RequestMethod.POST)
	public void deleteParticipantData(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@PathVariable("participantDataId") String participantDataId, @RequestBody IdList data) throws Exception {
		serviceProvider.getParticipantDataService().deleteRows(userId, participantDataId, data);
	}

	/**
	 * update existing participant data rows
	 * 
	 * @param userId
	 * @param participantDataId
	 * @param data
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_ID, method = RequestMethod.PUT)
	public @ResponseBody
	ListWrapper<ParticipantDataRow> updateParticipantData(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@PathVariable String participantDataId, @RequestBody ListWrapper<ParticipantDataRow> data) throws Exception {
		List<ParticipantDataRow> rows = serviceProvider.getParticipantDataService().update(userId, participantDataId, data.getList());
		return ListWrapper.wrap(rows, ParticipantDataRow.class);
	}

	/**
	 * get partitipant data
	 * 
	 * @param userId
	 * @param participantDataId
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_ID, method = RequestMethod.GET)
	public @ResponseBody
	PaginatedResults<ParticipantDataRow> getParticipantData(
			@RequestParam(value = ServiceConstants.PAGINATION_LIMIT_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_LIMIT_PARAM) Integer limit,
			@RequestParam(value = ServiceConstants.PAGINATION_OFFSET_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_OFFSET_PARAM_NEW) Integer offset,
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId, @PathVariable String participantDataId)
			throws Exception {
		return serviceProvider.getParticipantDataService().get(userId, participantDataId, limit, offset);
	}

	/**
	 * get partitipant data row
	 * 
	 * @param userId
	 * @param participantDataId
	 * @param rowId
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_ROW_ID, method = RequestMethod.GET)
	public @ResponseBody
	ParticipantDataRow getParticipantDataRow(@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@PathVariable String participantDataId, @PathVariable Long rowId) throws Exception {
		return serviceProvider.getParticipantDataService().getRow(userId, participantDataId, rowId);
	}

	/**
	 * get partitipant data
	 * 
	 * @param userId
	 * @param participantDataId
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_CURRENT_DATA_ID, method = RequestMethod.GET)
	public @ResponseBody
	ParticipantDataCurrentRow getCurrentParticipantData(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId, @PathVariable String participantDataId)
			throws Exception {
		return serviceProvider.getParticipantDataService().getCurrent(userId, participantDataId);
	}

	/**
	 * Get the list of participant data for this user
	 * 
	 * @param limit
	 * @param offset
	 * @param userId
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA, method = RequestMethod.GET)
	public @ResponseBody
	PaginatedResults<ParticipantDataDescriptor> getUserParticipantDatas(
			@RequestParam(value = ServiceConstants.PAGINATION_LIMIT_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_LIMIT_PARAM) Integer limit,
			@RequestParam(value = ServiceConstants.PAGINATION_OFFSET_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_OFFSET_PARAM_NEW) Integer offset,
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId) throws Exception {
		return serviceProvider.getParticipantDataService().getUserParticipantDataDescriptors(userId, limit, offset);
	}

	/**
	 * create a new participant data description
	 * 
	 * @param userId
	 * @param participantDataDescriptor
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_DESCRIPTOR, method = RequestMethod.POST)
	public @ResponseBody
	ParticipantDataDescriptor createParticipantDataDescriptor(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@RequestBody ParticipantDataDescriptor participantDataDescriptor) throws Exception {
		return serviceProvider.getParticipantDataService().createParticipantDataDescriptor(userId, participantDataDescriptor);
	}
	
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_DESCRIPTOR, method = RequestMethod.PUT)
	public void updateParticipantDataDescriptor(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@RequestBody ParticipantDataDescriptor participantDataDescriptor) throws Exception {
		serviceProvider.getParticipantDataService().updateParticipantDataDescriptor(userId, participantDataDescriptor);
	}

	/**
	 * get a participant data description
	 * 
	 * @param userId
	 * @param participantDataId
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_DESCRIPTOR_ID, method = RequestMethod.GET)
	public @ResponseBody
	ParticipantDataDescriptor getParticipantDataDescriptor(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId, @PathVariable String participantDataId)
			throws DatastoreException, NotFoundException {
		return serviceProvider.getParticipantDataService().getParticipantDataDescriptor(userId, participantDataId);
	}

	/**
	 * create all participant data descriptions
	 * 
	 * @param limit
	 * @param offset
	 * @param userId
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_DESCRIPTOR, method = RequestMethod.GET)
	public @ResponseBody
	PaginatedResults<ParticipantDataDescriptor> getAllParticipantDatas(
			@RequestParam(value = ServiceConstants.PAGINATION_LIMIT_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_LIMIT_PARAM) Integer limit,
			@RequestParam(value = ServiceConstants.PAGINATION_OFFSET_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_OFFSET_PARAM_NEW) Integer offset,
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId) throws DatastoreException,
			NotFoundException {
		return serviceProvider.getParticipantDataService().getAllParticipantDataDescriptors(userId, limit, offset);
	}

	/**
	 * create a new participant data column description
	 * 
	 * @param userId
	 * @param participantDataColumnDescriptor
	 * @return
	 * @throws Exception
	 */
	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_COLUMN_DESCRIPTORS, method = RequestMethod.POST)
	public @ResponseBody
	ParticipantDataColumnDescriptor createParticipantDataColumnDescriptor(
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@RequestBody ParticipantDataColumnDescriptor participantDataColumnDescriptor) throws Exception {
		return serviceProvider.getParticipantDataService().createParticipantDataColumnDescriptor(userId, participantDataColumnDescriptor);
	}

	/**
	 * get all participant data column descriptions for a participant data descriptor
	 * 
	 * @param limit
	 * @param offset
	 * @param userId
	 * @param participantDataId
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.PARTICIPANT_DATA_COLUMN_DESCRIPTORS_FOR_PARTICIPANT_DATA_ID, method = RequestMethod.GET)
	public @ResponseBody
	PaginatedResults<ParticipantDataColumnDescriptor> getParticipantDataColumnDescriptors(
			@RequestParam(value = ServiceConstants.PAGINATION_LIMIT_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_LIMIT_PARAM) Integer limit,
			@RequestParam(value = ServiceConstants.PAGINATION_OFFSET_PARAM, required = false, defaultValue = ServiceConstants.DEFAULT_PAGINATION_OFFSET_PARAM_NEW) Integer offset,
			@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId, @PathVariable String participantDataId)
			throws DatastoreException, NotFoundException {
		return serviceProvider.getParticipantDataService().getParticipantDataColumnDescriptors(userId, participantDataId, limit, offset);
	}

	/**
	 * get all participant data column descriptions for a participant data descriptor
	 * 
	 * @param limit
	 * @param offset
	 * @param userId
	 * @param participantDataId
	 * @return
	 * @throws DatastoreException
	 * @throws NotFoundException
	 */
	@ResponseStatus(HttpStatus.OK)
	@RequestMapping(value = BridgeUrlHelpers.SEND_PARTICIPANT_DATA_DESCRIPTORS_UPDATES, method = RequestMethod.PUT)
	public void updateParticipantDataStatuses(@RequestParam(value = AuthorizationConstants.USER_ID_PARAM, required = false) Long userId,
			@RequestBody ParticipantDataStatusList statusList) throws DatastoreException, NotFoundException {
		serviceProvider.getParticipantDataService().updateParticipantStatuses(userId, statusList.getUpdates());
	}
}
