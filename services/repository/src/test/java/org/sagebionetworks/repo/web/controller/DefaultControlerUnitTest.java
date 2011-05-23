package org.sagebionetworks.repo.web.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.web.UrlHelpers;

public class DefaultControlerUnitTest {
	

	@Test (expected=IllegalArgumentException.class)
	public void testObjectTypeForUnknonwUrl(){
		// This should throw an exception
		ObjectType type = ObjectType.getTypeForUrl("/some/uknown/url");
	}
	
	@Test
	public void testObjectTypeForDatasetUrl(){
		ObjectType type = ObjectType.getTypeForUrl(UrlHelpers.DATASET);
		assertEquals(ObjectType.dataset, type);
	}

	@Test
	public void testObjectTypeForProjectUrl(){
		ObjectType type = ObjectType.getTypeForUrl(UrlHelpers.PROJECT);
		assertEquals(ObjectType.project, type);
	}
	
	@Test
	public void testObjectTypeForLayerUrl(){
		ObjectType type = ObjectType.getTypeForUrl(UrlHelpers.LAYER);
		assertEquals(ObjectType.layer, type);
	}
}
