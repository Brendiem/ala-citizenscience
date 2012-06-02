package au.com.gaiaresources.bdrs.controller.review.sightings;

import au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController;
import au.com.gaiaresources.bdrs.db.ScrollableResults;
import au.com.gaiaresources.bdrs.db.impl.HqlQuery;
import au.com.gaiaresources.bdrs.db.impl.HqlQuery.SortOrder;
import au.com.gaiaresources.bdrs.db.impl.PortalPersistentImpl;
import au.com.gaiaresources.bdrs.db.impl.Predicate;
import au.com.gaiaresources.bdrs.kml.KMLWriter;
import au.com.gaiaresources.bdrs.model.location.Location;
import au.com.gaiaresources.bdrs.model.method.CensusMethod;
import au.com.gaiaresources.bdrs.model.record.Record;
import au.com.gaiaresources.bdrs.model.report.Report;
import au.com.gaiaresources.bdrs.model.report.ReportCapability;
import au.com.gaiaresources.bdrs.model.report.impl.ReportView;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.taxa.IndicatorSpecies;
import au.com.gaiaresources.bdrs.model.user.User;
import au.com.gaiaresources.bdrs.service.facet.Facet;
import au.com.gaiaresources.bdrs.service.facet.LocationFacet;
import au.com.gaiaresources.bdrs.service.facet.SurveyFacet;
import au.com.gaiaresources.bdrs.service.facet.record.RecordSurveyFacet;
import au.com.gaiaresources.bdrs.servlet.BdrsWebConstants;
import au.com.gaiaresources.bdrs.util.KMLUtils;
import au.com.gaiaresources.bdrs.util.StringUtils;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides view controllers for the Facet, Map and List view of the ALA
 * 'My Sightings' Page. 
 */
@SuppressWarnings("unchecked")
@Controller
public class AdvancedReviewSightingsController extends AdvancedReviewController<Record> {



    /**
     * Report URL.
     */
    public static final String  ADVANCED_REVIEW_REPORT_URL = "/review/sightings/advancedReviewReport.htm";

    /**
     * Parameter for latest record.
     */
    public static final String LATEST_RECORD_ID = BdrsWebConstants.PARAM_RECORD_ID;
    
    /**
     * Set of Strings that are valid sorting parameters for the database query.
     */
    public static final Set<String> VALID_SORT_PROPERTIES;

    /** The names of the Record properties to be included in the JSON returned by a search */
    private static final Map<Class<?>, Set<String>> JSON_PROPERTIES;
    
    /**
     * Parameter for report id in query.
     */
    public static final String QUERY_PARAM_REPORT_ID = "reportId";
    
    /**
     * Alias used in the facet indepedent joins for the facet query.
     */
    private static final String SPECIES_ALIAS = "species";
    /**
     * Alias used in the facet independent joins for the facet query.
     */
    private static final String ATTRIBUTE_VALUE_ALIAS = "recordAttributeVal";
    /**
     * Alias used in the facet independent joins for the facet query.
     */
    private static final String ATTRIBUTE_VALUE_SPECIES_ALIAS = "attributeValueSpecies";
    
    /**
     * The base hql query used for a facet query.
     */
    public static final String BASE_FACET_QUERY = "select distinct record, "
        + SPECIES_ALIAS + ".scientificName, " + SPECIES_ALIAS 
        + ".commonName, location.name, censusMethod.type from Record record";
    
    static {
        Set<String> temp = new HashSet<String>();
        temp.add("record.when");
        temp.add(SPECIES_ALIAS + ".scientificName");
        temp.add(SPECIES_ALIAS + ".commonName"); 
        temp.add("location.name");
        temp.add("censusMethod.type");
        temp.add("record.user");
        VALID_SORT_PROPERTIES = Collections.unmodifiableSet(temp);

        String[] recordProperties = {"when", "createdAt", "id", "user", "geometry", "latitude", "longitude", "species", "censusMethod", "survey"};
        String[] speciesProperties = {"scientificName", "commonName"};
        String[] userProperties = {"name"};
        String[] censusMethodProperties = {"type"};
        String[] surveyProperties = {"id"};

        Map<Class<?>, Set<String>> jsonProps = new HashMap<Class<?>, Set<String>>();
        jsonProps.put(Record.class, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(recordProperties))));
        jsonProps.put(IndicatorSpecies.class, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(speciesProperties))));
        jsonProps.put(User.class, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(userProperties))));
        jsonProps.put(CensusMethod.class, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(censusMethodProperties))));
        jsonProps.put(Survey.class, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(surveyProperties))));

        JSON_PROPERTIES = Collections.unmodifiableMap(jsonProps);
    }
    
    private Logger log = Logger.getLogger(getClass());

    /**
     * Provides a view of the facet listing and a skeleton of the map or list
     * view. The map or list view will populate itself via asynchronous 
     * javascript requests. 
     */
    @RequestMapping(value = "/review/sightings/advancedReview.htm", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView advancedReview(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       @RequestParam(value=SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME, required=false) Integer surveyId,
                                       @RequestParam(value=RESULTS_PER_PAGE_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_RESULTS_PER_PAGE) Integer resultsPerPage,
                                       @RequestParam(value=PAGE_NUMBER_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_PAGE_NUMBER) Integer pageNumber) {

        configureHibernateSession();
     
        Map<String, String[]> newParamMap = new HashMap<String, String[]>(request.getParameterMap());
        
        if (newParamMap.containsKey("sourcePage")) {
            fixLocationParams(newParamMap, surveyId);
        }
        List<Facet> facetList = facetService.getFacetList(currentUser(), newParamMap);
        Long recordCount = countMatchingRecords(facetList,
                                                surveyId,
                                                getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME));
        ModelAndView mv = getAdvancedReviewView(newParamMap, surveyId, resultsPerPage, pageNumber, facetList, recordCount, "advancedReviewRecords");
        // Add an optional parameter to set a record to highlight
        if (!StringUtils.nullOrEmpty(getParameter(newParamMap, LATEST_RECORD_ID))) {
            mv.addObject(LATEST_RECORD_ID, getParameter(newParamMap, LATEST_RECORD_ID));
        }
        
        return mv;
    }

    /**
     * Adds the locations to the parameter map as facet selections and removes them from the parameter list.
     * If there are no locations, passed, uses the locationArea parameter and facet selections
     * to create the facet selection parameters and removes the parameters after translating them.
     * @param newParamMap the request parameter map
     * @param surveyId the survey id
     */
    private void fixLocationParams(Map<String, String[]> newParamMap,
            Integer surveyId) {
        // some locations have been selected, add them to the parameters as facet selections
        String locations = getParameter(newParamMap, PARAM_LOCATIONS);
        if (StringUtils.nullOrEmpty(locations)) {
            // only use the location wkt if no locations have been selected
            // a location area has been selected, add contained locations to the parameters as facet selections
            String locationArea = getParameter(newParamMap, PARAM_LOCATION_AREA);
            locations = getLocationListInWkt(locationArea, newParamMap, surveyId);
            newParamMap.remove(PARAM_LOCATION_AREA);
        }
        // this code translates the locations parameter into facet selections
        // there is currently not a good way to get the input name (parameter 
        // selection name) from a facet before it's creation or to build a mock
        // facet for retrieving this parameter so the input name is hard-coded 
        // here
        String inputName = LocationFacet.QUERY_PARAM_NAME+LocationFacet.OPTION_SUFFIX;
        if (!StringUtils.nullOrEmpty(locations) && !newParamMap.containsKey(inputName)) {
            newParamMap.put(inputName, locations.split(","));
            newParamMap.remove(PARAM_LOCATIONS);
        }
    }

    /**
     * Gets a comma-separated list of location ids contained within the area 
     * described by the wkt string
     * @param locationArea the wkt string describing the selection area
     * @param newParamMap 
     * @return a String that is a comma-separated list of location ids contained within the wkt area
     */
    private String getLocationListInWkt(String locationArea, Map<String, String[]> newParamMap, Integer surveyId) {
        List<Facet> facetList = facetService.getLocationFacetList(currentUser(), newParamMap);
        Query q = createLocationFacetQuery(facetList, surveyId, null, null, 
                                           getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME), 
                                           locationArea, null);
        
        // remove any facet parameters from the map
        for (Facet facet : facetList) {
            if (newParamMap.containsKey(facet.getInputName())) {
                newParamMap.remove(facet.getInputName());
            }
        }
        
        List<Object[]> list = q.list();
        List<Integer> idsList = new ArrayList<Integer>(list.size());
        for (Object[] objects : list) {
            for (Object object : objects) {
                if (object instanceof Location) {
                    idsList.add(((Location)object).getId());
                }
            }
        }
        return StringUtils.joinList(idsList, ",");
    }

    /**
     * Returns the list of records matching the {@link Facet} criteria as KML.
     */
    @RequestMapping(value = "/review/sightings/advancedReviewKMLSightings.htm", method = {RequestMethod.GET, RequestMethod.POST})
    public void advancedReviewKMLSightings(HttpServletRequest request, HttpServletResponse response) throws IOException, JAXBException {

        configureHibernateSession();
        Map<String, String[]> newParamMap = decodeParamMap(request.getParameterMap());
        
        Integer surveyId = null;
        if(getParameter(newParamMap, SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME) != null) {
            surveyId = Integer.parseInt(getParameter(newParamMap, SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME));
        }

        List<Facet> facetList = facetService.getFacetList(currentUser(), newParamMap);
        
        ScrollableResults<Record> sr = getScrollableResults(facetList, surveyId, 
                                                            getParameter(newParamMap, SORT_BY_QUERY_PARAM_NAME), 
                                                            getParameter(newParamMap, SORT_ORDER_QUERY_PARAM_NAME),
                                                            getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME));
        advancedReviewKMLSightings(request, response, facetList, sr);
    }
    
    /**
     * Sets the request parameters as session attributes for KML GET request
     */
    @RequestMapping(value = "/review/sightings/setKMLParameters.htm", method = {RequestMethod.POST})
    public void setSessionAttributes(HttpServletRequest request, HttpServletResponse response) throws IOException, JAXBException {
        // create the attribute value from a copy of the parameter map
        Map<String, String[]> paramMap = new HashMap<String, String[]>(request.getParameterMap());
        getRequestContext().setSessionAttribute(ATTRIBUTE_KML_PARAMS, paramMap);
    }
    
    /**
     * Sets the request parameters as session attributes for KML GET request
     */
    @RequestMapping(value = "/review/sightings/clearKMLParameters.htm", method = {RequestMethod.POST})
    public void clearSessionAttributes(HttpServletRequest request, HttpServletResponse response) throws IOException, JAXBException {
        getRequestContext().removeSessionAttribute(ATTRIBUTE_KML_PARAMS);
    }
    
    /**
     * Returns a JSON array of records matching the {@link Facet} criteria.
     */
    @RequestMapping(value = "/review/sightings/advancedReviewJSONSightings.htm", method = {RequestMethod.GET, RequestMethod.POST})
    public void advancedReviewJSONSightings(HttpServletRequest request, 
                                            HttpServletResponse response,
                                            @RequestParam(value=RESULTS_PER_PAGE_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_RESULTS_PER_PAGE) Integer resultsPerPage,
                                            @RequestParam(value=PAGE_NUMBER_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_PAGE_NUMBER) Integer pageNumber) throws IOException {
        configureHibernateSession();
        HashMap<String, String[]> newParamMap = new HashMap<String, String[]>(request.getParameterMap());
        
        Integer surveyId = null;
        if(getParameter(newParamMap, SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME) != null) {
            surveyId = Integer.parseInt(getParameter(newParamMap, SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME));
        }
        
        List<Facet> facetList = facetService.getFacetList(currentUser(), newParamMap);
        ScrollableResults<Record> sc = getScrollableResults(facetList,
                                                             surveyId,
                                                             getParameter(newParamMap, SORT_BY_QUERY_PARAM_NAME), 
                                                             getParameter(newParamMap, SORT_ORDER_QUERY_PARAM_NAME),
                                                             getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME),
                                                             pageNumber, resultsPerPage);
        advancedReviewJSONSightings(response, facetList, sc);
    }
    
    /**
     * Returns an XLS representation of representation of records matching the
     * {@link Facet} criteria. This function should only be used if the records
     * are part of a single survey.
     * @throws Exception 
     */
    @RequestMapping(value = "/review/sightings/advancedReviewDownload.htm", method = {RequestMethod.GET, RequestMethod.POST})
    public void advancedReviewDownload(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       @RequestParam(value=SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME, required=false) Integer surveyId,
                                       @RequestParam(value=QUERY_PARAM_DOWNLOAD_FORMAT, required=true) String[] downloadFormat) throws Exception {
        configureHibernateSession();
        
        User currentUser = currentUser();
        HashMap<String, String[]> newParamMap = new HashMap<String, String[]>(request.getParameterMap());
        
        List<Facet> facetList = facetService.getFacetList(currentUser, newParamMap);
        
        SurveyFacet surveyFacet = facetService.getFacetByType(facetList, RecordSurveyFacet.class);
        
        // list of surveys to download
        List<Survey> surveyList = surveyFacet.getSelectedSurveys();
               
        // In the case that no surveys are selected to filter by - we will use
        // all the surveys available for the accessing user
        if (surveyList.isEmpty()) {
            surveyList = surveyDAO.getActiveSurveysForUser(currentUser);
        }
        
        // I think 'surveyId' is not used for AdvancedReview but is used for MySightings
        ScrollableResults<Record> sc = getScrollableResults(facetList,
                                                     surveyId,
                                                     getParameter(newParamMap, SORT_BY_QUERY_PARAM_NAME), 
                                                     getParameter(newParamMap, SORT_ORDER_QUERY_PARAM_NAME),
                                                     getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME));
        
        downloadSightings(request, response, downloadFormat, sc, surveyList);
    }

    /**
     * Creates a scrollable records instance from the facet selection and renders the specified report using the
     * scrollable records.
     *
     * @param request  the client request.
     * @param response the server response.
     * @param reportId the primary key of the report to be run.
     * @return the report view.
     * @throws Exception thrown if there is an error running the report.
     */
    @RequestMapping(value = ADVANCED_REVIEW_REPORT_URL, method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView advancedReviewReport(HttpServletRequest request,
                                             HttpServletResponse response,
                                             @RequestParam(value = QUERY_PARAM_REPORT_ID, required = true) int reportId) throws Exception {
        configureHibernateSession();
        HashMap<String, String[]> newParamMap = new HashMap<String, String[]>(request.getParameterMap());
        
        User currentUser = currentUser();
        List<Facet> facetList = facetService.getFacetList(currentUser, newParamMap);

        Report report = reportDAO.getReport(reportId);

        ScrollableResults<Record> sc = getScrollableResults(facetList,
                null,
                getParameter(newParamMap, SORT_BY_QUERY_PARAM_NAME),
                getParameter(newParamMap, SORT_ORDER_QUERY_PARAM_NAME),
                getParameter(newParamMap, SEARCH_QUERY_PARAM_NAME));

        return reportService.renderReport(request, response, report, sc);
    }

    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#getCountSelect()
     */
    protected String getCountSelect() {
        return "select count(distinct record) from Record record";
    }
    
    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#applyFacetsToQuery(au.com.gaiaresources.bdrs.db.impl.HqlQuery, java.util.List, java.lang.Integer, java.lang.String)
     */
    protected void applyFacetsToQuery(HqlQuery hqlQuery, List<Facet> facetList,
            Integer surveyId, String searchText) {

    	applyJoinsForBaseQuery(hqlQuery);
    	
    	// If we are doing a text search, add a few extra joins.
    	if(searchText != null && !searchText.isEmpty()) {
            hqlQuery.leftJoin("record.attributes", ATTRIBUTE_VALUE_ALIAS);
            hqlQuery.leftJoin("recordAttributeVal.attribute", "recordAttribute");
            hqlQuery.leftJoin(ATTRIBUTE_VALUE_ALIAS+".species", ATTRIBUTE_VALUE_SPECIES_ALIAS);
    	}
        
    	//hqlQuery.leftJoin("record.location", "location");
        //hqlQuery.leftJoin("record.species", "species");
        //hqlQuery.leftJoin("record.censusMethod", "censusMethod");
        
        for(Facet f : facetList) {
            if(f.isActive()) {
                Predicate p = f.getPredicate();
                if (p != null) {
                    f.applyCustomJoins(hqlQuery);
                    hqlQuery.and(p);
                }
            }
        }
        
        if(searchText != null && !searchText.isEmpty()) {
        	String formattedSearchText = String.format("%%%s%%", searchText);
            Predicate searchPredicate = Predicate.ilike("record.notes", formattedSearchText);
            searchPredicate.or(Predicate.ilike(SPECIES_ALIAS + ".scientificName", formattedSearchText));
            searchPredicate.or(Predicate.ilike(SPECIES_ALIAS + ".commonName", formattedSearchText));
            searchPredicate.or(Predicate.ilike(ATTRIBUTE_VALUE_SPECIES_ALIAS+".scientificName", formattedSearchText));
            searchPredicate.or(Predicate.ilike(ATTRIBUTE_VALUE_SPECIES_ALIAS+".commonName", formattedSearchText));
            //Predicate searchPredicate = Predicate.ilike("record.notes", String.format("%%%s%%", searchText));
            //searchPredicate.or(Predicate.ilike("species.scientificName", String.format("%%%s%%", searchText)));
            //searchPredicate.or(Predicate.ilike("species.commonName", String.format("%%%s%%", searchText)));
            //searchPredicate.or(Predicate.ilike("record.user.name", String.format("%%%s%%", searchText)));
            hqlQuery.and(searchPredicate);
        }
        
        if(surveyId != null) {
            hqlQuery.and(Predicate.eq("record.survey.id", surveyId));
        }
    }
    
    /**
     * Applies the minimum of left joins required to do a facet query.
     * 
     * Exposing for testing. Does not effect state of object.
     * @param hqlQuery
     */
    public static void applyJoinsForBaseQuery(HqlQuery hqlQuery) {
    	hqlQuery.leftJoin("record.location", "location");
        // hqlQuery.leftJoin("location.attributes", "locAttribute");
        hqlQuery.leftJoin("record.species", SPECIES_ALIAS);
        hqlQuery.leftJoin("record.censusMethod", "censusMethod");
    }

    /**
     * Turns the supplied PortalPersistentImpl into an Map containing only the properties required by the
     * advanced review sightings page.
     * @param persistent the object to flatten.
     * @return a Map containing the PortalPersistentImpl property names as keys and property values as the values.
     */
    @Override
    protected Map<String, Object> flatten(PortalPersistentImpl persistent) {
        return persistent.flatten(2, false, false, false, JSON_PROPERTIES);
    }

    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#createFacetQuery(java.util.List, java.lang.Integer, java.lang.String, java.lang.String, java.lang.String)
     */
    protected Query createFacetQuery(List<Facet> facetList, Integer surveyId, String sortProperty, String sortOrder, String searchText) {
        // extra columns in select are used for ordering
        HqlQuery hqlQuery = new HqlQuery(BASE_FACET_QUERY);
        
        applyFacetsToQuery(hqlQuery, facetList, surveyId, searchText);

        // NO SQL injection for you
        if(sortProperty != null && sortOrder != null && VALID_SORT_PROPERTIES.contains(sortProperty)) {
            hqlQuery.order(sortProperty, 
                           SortOrder.valueOf(sortOrder).name(),
                           null);
        }
        return toHibernateQuery(hqlQuery);  
    }
    
    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#writeKMLResults(au.com.gaiaresources.bdrs.kml.KMLWriter, au.com.gaiaresources.bdrs.model.user.User, java.lang.String, java.util.List)
     */
    @Override
    protected void writeKMLResults(KMLWriter writer, User currentUser,
            String contextPath, List<Record> rList) {
        KMLUtils.writeRecords(writer, currentUser, contextPath, rList);
    }

    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#getDefaultSortString()
     */
    @Override
    protected String getDefaultSortString() {
        return "record.when";
    }
    
    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.review.AdvancedReviewController#getReportList()
     */
    @Override
    protected List<Report> getReportList() {
        return reportDAO.getReports(ReportCapability.SCROLLABLE_RECORDS, ReportView.ADVANCED_REVIEW);
    }

    @Override
    protected String getKMLFolderName() {
        return KMLUtils.KML_RECORD_FOLDER;
    }
}