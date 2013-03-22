package org.sagebionetworks.file.services;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileUploadException;
import org.sagebionetworks.repo.manager.UserManager;
import org.sagebionetworks.repo.manager.file.FileHandleManager;
import org.sagebionetworks.repo.manager.file.FileUploadResults;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.file.ChunkedFileToken;
import org.sagebionetworks.repo.model.file.ChunkedPartRequest;
import org.sagebionetworks.repo.model.file.ExternalFileHandle;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.file.FileHandleResults;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.repo.web.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Basic implementation of the file upload service.
 * @author John
 *
 */
public class FileUploadServiceImpl implements FileUploadService {
	
	
	@Autowired
	UserManager userManager;
	
	@Autowired
	FileHandleManager fileUploadManager;

	@Override
	public FileHandleResults uploadFiles(String username, FileItemIterator itemIterator) throws DatastoreException, NotFoundException, FileUploadException, IOException, ServiceUnavailableException {
		if(username == null) throw new UnauthorizedException("The user must be authenticated");
		if(itemIterator == null) throw new IllegalArgumentException("FileItemIterator cannot be null");
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(username);
		FileUploadResults innerResults = fileUploadManager.uploadfiles(userInfo, new HashSet<String>(0), itemIterator);
		FileHandleResults results = new FileHandleResults();
		List<FileHandle> list = new LinkedList<FileHandle>();
		results.setList(list);
		for(S3FileHandle handle: innerResults.getFiles()){
			list.add(handle);
		}
		return results;
	}

	@Override
	public FileHandle getFileHandle(String handleId, String userId) throws DatastoreException, NotFoundException {
		if(userId == null) throw new UnauthorizedException("The user must be authenticated");
		if(handleId == null) throw new IllegalArgumentException("FileHandleId cannot be null");
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(userId);
		return fileUploadManager.getRawFileHandle(userInfo, handleId);
	}

	@Override
	public void deleteFileHandle(String handleId, String userId) throws DatastoreException, NotFoundException {
		if(userId == null) throw new UnauthorizedException("The user must be authenticated");
		if(handleId == null) throw new IllegalArgumentException("FileHandleId cannot be null");
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(userId);
		fileUploadManager.deleteFileHandle(userInfo, handleId);
	}

	@Override
	public ExternalFileHandle createExternalFileHandle(String userId,
			ExternalFileHandle fileHandle) throws DatastoreException, NotFoundException {
		if(userId == null) throw new UnauthorizedException("The user must be authenticated");
		if(fileHandle == null) throw new IllegalArgumentException("FileHandleId cannot be null");
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(userId);
		return fileUploadManager.createExternalFileHandle(userInfo, fileHandle);
	}

	@Override
	public ChunkedFileToken createChunkedFileUploadToken(String userId,	String fileName, String contentType) throws DatastoreException, NotFoundException {
		if(userId == null) throw new UnauthorizedException("The user must be authenticated");
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(userId);
		return fileUploadManager.createChunkedFileUploadToken(userInfo, fileName, contentType);
	}

	@Override
	public URL createChunkedFileUploadPartURL(String userId, ChunkedPartRequest cpr) throws DatastoreException, NotFoundException {
		// resolve the user
		UserInfo userInfo = userManager.getUserInfo(userId);
		return fileUploadManager.createChunkedFileUploadPartURL(userInfo, cpr);
	}

}
