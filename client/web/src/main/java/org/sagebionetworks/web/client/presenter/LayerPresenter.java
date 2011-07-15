package org.sagebionetworks.web.client.presenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.web.client.DisplayConstants;
import org.sagebionetworks.web.client.DisplayUtils;
import org.sagebionetworks.web.client.PlaceChanger;
import org.sagebionetworks.web.client.place.LoginPlace;
import org.sagebionetworks.web.client.place.ProjectsHome;
import org.sagebionetworks.web.client.security.AuthenticationController;
import org.sagebionetworks.web.client.services.NodeServiceAsync;
import org.sagebionetworks.web.client.transform.NodeModelCreator;
import org.sagebionetworks.web.client.view.LayerView;
import org.sagebionetworks.web.client.widget.licenseddownloader.LicenceServiceAsync;
import org.sagebionetworks.web.shared.Agreement;
import org.sagebionetworks.web.shared.Dataset;
import org.sagebionetworks.web.shared.DownloadLocation;
import org.sagebionetworks.web.shared.EULA;
import org.sagebionetworks.web.shared.FileDownload;
import org.sagebionetworks.web.shared.Layer;
import org.sagebionetworks.web.shared.LayerPreview;
import org.sagebionetworks.web.shared.LicenseAgreement;
import org.sagebionetworks.web.shared.NodeType;
import org.sagebionetworks.web.shared.PagedResults;
import org.sagebionetworks.web.shared.exceptions.RestServiceException;
import org.sagebionetworks.web.shared.users.AclUtils;
import org.sagebionetworks.web.shared.users.PermissionLevel;
import org.sagebionetworks.web.shared.users.UserData;

import com.google.gwt.activity.shared.AbstractActivity;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.place.shared.Place;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;

public class LayerPresenter extends AbstractActivity implements LayerView.Presenter{	

	private org.sagebionetworks.web.client.place.Layer place;
	private NodeServiceAsync nodeService;
	private PlaceController placeController;
	private PlaceChanger placeChanger;
	private LayerView view;
	private String layerId;	
	private String datasetId;
	private Boolean showDownload;
	private Layer model;
	private LayerPreview layerPreview;
	private LicenceServiceAsync licenseService;
	private NodeModelCreator nodeModelCreator;
	private AuthenticationController authenticationController;	
	
	/**
	 * Everything is injected via Guice.
	 * @param view
	 * @param datasetService
	 */
	@Inject
	public LayerPresenter(LayerView view, NodeServiceAsync nodeService, LicenceServiceAsync licenseService, NodeModelCreator nodeModelCreator, AuthenticationController authenticationController) {
		this.view = view;
		this.nodeService = nodeService;
		this.licenseService = licenseService;
		this.nodeModelCreator = nodeModelCreator;
		this.authenticationController = authenticationController;
		view.setPresenter(this);
	}

	@Override
	public void start(AcceptsOneWidget panel, EventBus eventBus) {
		this.placeController = DisplayUtils.placeController;
		this.placeChanger = new PlaceChanger() {			
			@Override
			public void goTo(Place place) {
				placeController.goTo(place);
			}
		};
		// add the view to the panel
		panel.setWidget(view);		
	}

	public void setPlace(org.sagebionetworks.web.client.place.Layer place) {
		this.place = place;
		this.layerId = place.getLayerId();
		this.datasetId = place.getDatasetId();
		this.showDownload = place.getDownload();
		refresh();
	}
	

	@Override
	public void licenseAccepted() {
//		UserData currentUser = authenticationController.getLoggedInUser();
//		Agreement agreement = new Agreement();		
//		agreement.setCreatedBy(currentUser.getEmail());
//		
//		// TODO : need to set the dataset id or something into the agreement...
//		
//		nodeService.createNode(NodeType.AGREEMENT, agreement.toJson(), new AsyncCallback<String>() {
//			@Override
//			public void onSuccess(String result) {
//				// agreement saved
//			}
//
//			@Override
//			public void onFailure(Throwable caught) {
//				view.showInfo("Error", DisplayConstants.ERROR_FAILED_PERSIST_AGREEMENT_TEXT);
//			}
//		});
	}

	@Override
	public void refresh() {
		refreshFromServer();
	}

	@Override
	public PlaceChanger getPlaceChanger() {
		return placeChanger;
	}

	@Override
	public boolean downloadAttempted() {
		if(authenticationController.getLoggedInUser() != null) {
			return true;
		} else {
			view.showInfo("Login Required", "Please Login to download data.");			
			placeChanger.goTo(new LoginPlace(new org.sagebionetworks.web.client.place.Layer(layerId, null, false)));
		}
		return false;
	}

	@Override
	public void delete() {
		nodeService.deleteNode(NodeType.LAYER, layerId, new AsyncCallback<Void>() {
			@Override
			public void onSuccess(Void result) {
				view.showInfo("Layer Deleted", "The layer was successfully deleted.");
				placeChanger.goTo(new ProjectsHome(DisplayUtils.DEFAULT_PLACE_TOKEN));
			}
			
			@Override
			public void onFailure(Throwable caught) {
				view.showErrorMessage("Layer delete failed.");
			}
		});
	}

	
	/*
	 * Protected Methods
	 */
	
	private void refreshFromServer() {
		view.clear();
		// Fetch the data about this dataset from the server
		nodeService.getNodeJSON(NodeType.LAYER, this.layerId, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String layerJson) {				
				Layer layer = null;
				try {
					layer = nodeModelCreator.createLayer(layerJson);
				} catch (RestServiceException ex) {
					DisplayUtils.handleServiceException(ex, placeChanger);
				}
				
				setLayer(layer);
			}
			
			@Override
			public void onFailure(Throwable caught) {
				view.showErrorMessage("An error occured retrieving this Layer. Please try reloading the page.");
			}			
		});		
	}

	protected void setLayer(final Layer layer) {
		this.model = layer;
		if(layer != null) {			
			UserData currentUser = authenticationController.getLoggedInUser();
			if(currentUser != null) {
				AclUtils.getHighestPermissionLevel(NodeType.PROJECT, layer.getId(), nodeService, new AsyncCallback<PermissionLevel>() {
					@Override
					public void onSuccess(PermissionLevel result) {
						boolean isAdministrator = false;
						boolean canEdit = false;
						if(result == PermissionLevel.CAN_EDIT) {
							canEdit = true;
						} else if(result == PermissionLevel.CAN_ADMINISTER) {
							canEdit = true;
							isAdministrator = true;
						}
						setLayerDetails(isAdministrator, canEdit);
					}
					
					@Override
					public void onFailure(Throwable caught) {				
						view.showErrorMessage(DisplayConstants.ERROR_GETTING_PERMISSIONS_TEXT);
						setLayerDetails(false, false);
					}			
				});
			} else {
				// because this is a public page, they can view
				setLayerDetails(false, false);
			}
		}
		
	}
	
	

	private void setLayerDetails(boolean isAdministrator, boolean canEdit) {
		
		// process the layer and send values to view
		view.setLayerDetails(model.getId(), 
							 model.getName(), 
						 	 model.getProcessingFacility(), 
							 model.getQcBy(), "#",
							 "qc_script.R", "#",
							 model.getQcDate(),
							 model.getDescription(),
							 5,
							 Integer.MAX_VALUE, // TODO : get total number of rows in layer
							 "Public", // TODO : replace with security object
							 "<a href=\"#Dataset:"+ this.datasetId +"\">Dataset</a>", // TODO : have dataset name included in layer metadata
							 model.getPlatform(), 
							 isAdministrator, 
							 canEdit);
		
		// get the preview string to get file header order, then get the previewAsData
		nodeService.getNodePreview(NodeType.LAYER, layerId, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String pagedResultString) {
				LayerPreview layerPreview = null;				
				try {
					PagedResults  pagedResult = nodeModelCreator.createPagedResults(pagedResultString);
					List<String> results = pagedResult.getResults();
					if(results.size() > 0) {
						layerPreview = nodeModelCreator.createLayerPreview(results.get(0));
					} else {
						view.showLayerPreviewUnavailable();
						return;
					}					
				} catch (RestServiceException ex) {
					DisplayUtils.handleServiceException(ex, placeChanger);
				}				

				setLayerPreview(layerPreview);
				// continue
				setLicenseAgreement();
			}

			@Override
			public void onFailure(Throwable caught) {
				view.showLayerPreviewUnavailable();

				// continue
				setLicenseAgreement();
			}
		});		

	}

	private void setLicenseAgreement() {
		// get Dataset to get its EULA id
		nodeService.getNodeJSON(NodeType.DATASET, model.getParentId(), new AsyncCallback<String>() {
			@Override
			public void onSuccess(String datasetJson) {
				Dataset dataset = null;
				try {
					dataset = nodeModelCreator.createDataset(datasetJson);
				} catch (RestServiceException ex) {
					DisplayUtils.handleServiceException(ex, placeChanger);
				}
				if(dataset != null) {
					final String eulaId = dataset.getEulaId();
					if(eulaId != null) {
						
						// now query to see if user has accepted the agreement
						UserData currentUser = authenticationController.getLoggedInUser();
						licenseService.hasAccepted(currentUser.getEmail(), eulaId, new AsyncCallback<Boolean>() {
							@Override
							public void onSuccess(Boolean hasAccepted) {
								view.requireLicenseAcceptance(!hasAccepted);

								// load license agreement (needed for viewing even if hasAccepted)
								nodeService.getNodeJSON(NodeType.EULA, eulaId, new AsyncCallback<String>() {
									@Override
									public void onSuccess(String eulaJson) {
										EULA eula = null;
										try {
											eula = nodeModelCreator.createEULA(eulaJson);
										} catch (RestServiceException ex) {
											DisplayUtils.handleServiceException(ex, placeChanger);
										}
										if(eula != null) {
											// set licence agreement text
											LicenseAgreement agreement = new LicenseAgreement();				
											agreement.setLicenseHtml(eula.getAgreement());
											view.setLicenseAgreement(agreement);
										} else {
											setDownloadFailure();
										}
									}
									
									@Override
									public void onFailure(Throwable caught) {
										setDownloadFailure();
									}									
								});
								
								// load download locations
								loadDownloadLocations();				
								
							}
							
							@Override
							public void onFailure(Throwable caught) {
								setDownloadFailure();
							}
						});									
					}
				} else {
					setDownloadFailure();
				}
			} 

			@Override
			public void onFailure(Throwable caught) {
				setDownloadFailure();
			}
		});
	}

	private void setDownloadFailure() {
		view.showErrorMessage("Dataset downloading unavailable. Please try reloading the page.");
		view.setDownloadUnavailable();
		view.disableLicensedDownloads(true);
	}
	
	protected void setLayerPreview(LayerPreview preview) {
		this.layerPreview = preview;

		
		// get column display order, if possible from the layer preview
		List<String> columnDisplayOrder = preview.getHeaders();

		// TODO : get columns descriptions from service
		Map<String, String> columnDescriptions = getTempColumnDescriptions();
		Map<String, String> columnUnits = getTempColumnUnits();
		
		// append units onto description
		for(String key : columnUnits.keySet()) {
			String units = columnUnits.get(key);
			columnDescriptions.put(key, columnDescriptions.get(key) + " (" + units + ")");
		}		
		
		view.setLayerPreviewTable(preview.getRows(), columnDisplayOrder, columnDescriptions, columnUnits);

	}

	private void loadDownloadLocations() {
		nodeService.getNodeLocations(NodeType.LAYER, layerId, new AsyncCallback<String>() {
			@Override
			public void onSuccess(String pagedResultString) {				
				List<FileDownload> downloads = new ArrayList<FileDownload>();						
				try {							
					PagedResults pagedResult = nodeModelCreator.createPagedResults(pagedResultString);
					List<String> results = pagedResult.getResults();
					for(String fileDownloadString : results) {
						DownloadLocation downloadLocation = nodeModelCreator.createDownloadLocation(fileDownloadString);
						if(downloadLocation != null && downloadLocation.getPath() != null) {
							FileDownload dl = new FileDownload(downloadLocation.getPath(), "Download " + model.getName(), downloadLocation.getMd5sum());
							downloads.add(dl);
						}	
					}
				} catch (RestServiceException ex) {
					DisplayUtils.handleServiceException(ex, placeChanger);
				}				
				view.setLicensedDownloads(downloads);
				
				// show download if requested
				if(showDownload != null && showDownload == true) {
					if(downloadAttempted()) {
						view.showDownload();
					}
				}
			}
			@Override
			public void onFailure(Throwable caught) {
				view.setDownloadUnavailable();						
			}
		});
	}
	
	private Map<String, String> getTempColumnUnits() {
		Map<String,String> units = new LinkedHashMap<String, String>();
		
		units.put("predxbxpsa","ng/mL");
		units.put("age","years");
		units.put("pre_treatment_psa","ng/mL");
		units.put("bcr_freetime","years");
		units.put("survtime","months");
		units.put("nomogram_pfp_postrp","probability");
		units.put("nomogram_nomopred_extra_capsular_extension","probability");
		units.put("nomogram_nomopred_lni","probability");
		units.put("nomogram_nomopred_ocd","probability");
		units.put("nomogram_nomopred_seminal_vesicle_invasion","probability");
		
		return units;
	}

	private Map<String, String> getTempColumnDescriptions() {
		Map<String,String> descriptions = new LinkedHashMap<String, String>();
		
		descriptions.put("sample_type","Type of sample that was profiled. One of: Benign=benign tumor ,CELL LINE=cancer cell line , MET=metastatic tumor, PRIMARY=primary tumor or XENOGRAFT. ");
		descriptions.put("metastatic_site","Site in the body where metastatic tumor was detected.");
		descriptions.put("ethnicity","ethnicity of the individual");
		descriptions.put("predxbxpsa","PSA prior to diagnostic biopsy");
		descriptions.put("age","Age at diagnosis");
		descriptions.put("clinical_primary_gleason","clinical gleason score of the majority of the tumor");
		descriptions.put("clinical_secondary_gleason","clinical gleason score of the minority of the tumor");
		descriptions.put("clinical_gleason_score","clinical tertiary gleason score");
		descriptions.put("pre_treatment_psa","Prostate specific antigen level prior to treatment");
		descriptions.put("clinical_tnm_stage_t","Cancer stage based on the Tumor, Node, Metastasis scoring system");
		descriptions.put("neoadjradtx","neo-adjuvant treatment");
		descriptions.put("chemotx","chemotherapy treatment");
		descriptions.put("hormtx","Hormone treatment");
		descriptions.put("radtxtype","radiation treatment type");
		descriptions.put("rp_type","Surgical treatment. RP=radical prostatectomy, LP=laproscopic prostatectomy, SALRP=Salvage radical prostatectomy");
		descriptions.put("sms","somatostatin");
		descriptions.put("extra_capsular_extension","extra-capsular extension: cancer extending beyond the prostate capsule");
		descriptions.put("seminal_vesicle_invasion","seminal vesicle invasion. Either \"positive\" or \"negative\"");
		descriptions.put("tnm_stage_n","TNM \"N\" stage");
		descriptions.put("number_nodes_removed","Number of cancerous nodes removed");
		descriptions.put("number_nodes_positive","number node-positive nodes");
		descriptions.put("pathologic_tnm_stage_t","pathologic TNM \"T\" stage");
		descriptions.put("pathologic_primary_gleason","pathalogic gleason score of the majority of the tumor");
		descriptions.put("pathologic_secondary_gleason","pathalagic gleason score of the minority of the tumor");
		descriptions.put("pathologic_gleason_score","pathalogic tertiary gleason score");
		descriptions.put("bcr_freetime","elapsed time before bichemical recurrance");
		descriptions.put("bcr_event","was a biochemical recurrance event detected");
		descriptions.put("metsevent","metastatic event");
		descriptions.put("survtime","Survival time post-diagnosis");
		descriptions.put("event","Cause of death");
		descriptions.put("nomogram_pfp_postrp","nomogram progression-free probability post radical prostatectomy");
		descriptions.put("nomogram_nomopred_extra_capsular_extension","probability of extra capsular extension");
		descriptions.put("nomogram_nomopred_lni","probability of lymph node involvement");
		descriptions.put("nomogram_nomopred_ocd","probability of organ confined disease");
		descriptions.put("nomogram_nomopred_seminal_vesicle_invasion","probability of seminal vesicle invasion");
		descriptions.put("copy_number_cluster","copy number cluster size");
		descriptions.put("expression_array_tissue_source","Source of tissue used for expression profiling");		

		return descriptions;
	}

}

