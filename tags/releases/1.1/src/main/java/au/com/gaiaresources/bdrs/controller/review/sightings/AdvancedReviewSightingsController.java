package au.com.gaiaresources.bdrs.controller.review.sightings;

import au.com.gaiaresources.bdrs.db.FilterManager;
import au.com.gaiaresources.bdrs.db.impl.HqlQuery;
import au.com.gaiaresources.bdrs.db.impl.HqlQuery.SortOrder;
import au.com.gaiaresources.bdrs.db.impl.Predicate;
import au.com.gaiaresources.bdrs.json.JSONArray;
import au.com.gaiaresources.bdrs.kml.KMLWriter;
import au.com.gaiaresources.bdrs.model.preference.Preference;
import au.com.gaiaresources.bdrs.model.preference.PreferenceDAO;
import au.com.gaiaresources.bdrs.model.preference.PreferenceUtil;
import au.com.gaiaresources.bdrs.model.record.Record;
import au.com.gaiaresources.bdrs.model.record.ScrollableRecords;
import au.com.gaiaresources.bdrs.model.record.impl.ScrollableRecordsImpl;
import au.com.gaiaresources.bdrs.model.report.Report;
import au.com.gaiaresources.bdrs.model.report.ReportCapability;
import au.com.gaiaresources.bdrs.model.report.ReportDAO;
import au.com.gaiaresources.bdrs.model.report.impl.ReportView;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.user.User;
import au.com.gaiaresources.bdrs.service.facet.Facet;
import au.com.gaiaresources.bdrs.service.facet.FacetOption;
import au.com.gaiaresources.bdrs.service.facet.FacetService;
import au.com.gaiaresources.bdrs.service.facet.SurveyFacet;
import au.com.gaiaresources.bdrs.service.report.ReportService;
import au.com.gaiaresources.bdrs.servlet.RequestContext;
import au.com.gaiaresources.bdrs.util.KMLUtils;
import au.com.gaiaresources.bdrs.util.StringUtils;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.log4j.Logger;
import org.hibernate.FlushMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.*;

/**
 * Provides view controllers for the Facet, Map and List view of the ALA
 * 'My Sightings' Page. 
 */
@SuppressWarnings("unchecked")
@Controller
public class AdvancedReviewSightingsController extends SightingsController {
    
    public static final String  ADVANCED_REVIEW_REPORT_URL = "/review/sightings/advancedReviewReport.htm";
    
    public static final String VIEW_TYPE_TABLE = "table";
    public static final String VIEW_TYPE_MAP = "map";
    public static final String VIEW_TYPE_DOWNLOAD = "download";
    
    public static final String PARAM_VIEW_TYPE = "viewType";
    
    public static final Set<String> VALID_SORT_PROPERTIES;
    
    
    public static final String SORT_BY_QUERY_PARAM_NAME = "sortBy";
    public static final String SORT_ORDER_QUERY_PARAM_NAME = "sortOrder";
    public static final String SEARCH_QUERY_PARAM_NAME = "searchText";
    public static final String RESULTS_PER_PAGE_QUERY_PARAM_NAME = "resultsPerPage";
    public static final String PAGE_NUMBER_QUERY_PARAM_NAME = "pageNumber";
    public static final String LATEST_RECORD_ID = "recordId";
    
    public static final String DEFAULT_RESULTS_PER_PAGE = "20";
    public static final String DEFAULT_PAGE_NUMBER = "1";
    
    public static final String MODEL_DOWNLOAD_VIEW_SELECTED = "downloadViewSelected";
    public static final String MODEL_TABLE_VIEW_SELECTED = "tableViewSelected";
    public static final String MODEL_MAP_VIEW_SELECTED = "mapViewSelected";

    public static final String QUERY_PARAM_REPORT_ID = "reportId";
    
    static {
        Set<String> temp = new HashSet<String>();
        temp.add("record.when");
        temp.add("species.scientificName");
        temp.add("species.commonName"); 
        temp.add("location.name");
        temp.add("censusMethod.type");
        temp.add("record.user");
        VALID_SORT_PROPERTIES = Collections.unmodifiableSet(temp);
    }
    
    private Logger log = Logger.getLogger(getClass());
    
    @Autowired
    private FacetService facetService;

    /** Used to retrieve the default search results view (map or list) */
    @Autowired
    private PreferenceDAO preferenceDAO;

    /** Used to retrieve the reports eligible for the advanced review page. */
    @Autowired
    private ReportDAO reportDAO;
    
    @Autowired
    private ReportService reportService;

    /**
     * Provides a view of the facet listing and a skeleton of the map or list
     * view. The map or list view will populate itself via asynchronous 
     * javascript requests. 
     */
    @RequestMapping(value = "/review/sightings/advancedReview.htm", method = RequestMethod.GET)
    public ModelAndView advancedReview(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       @RequestParam(value=SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME, required=false) Integer surveyId,
                                       @RequestParam(value=RESULTS_PER_PAGE_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_RESULTS_PER_PAGE) Integer resultsPerPage,
                                       @RequestParam(value=PAGE_NUMBER_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_PAGE_NUMBER) Integer pageNumber) {

        configureHibernateSession();

        List<Facet> facetList = facetService.getFacetList(currentUser(), (Map<String, String[]>)request.getParameterMap());
        Long recordCount = countMatchingRecords(facetList,
                                                surveyId,
                                                request.getParameter(SEARCH_QUERY_PARAM_NAME));
        long pageCount = recordCount / resultsPerPage;
        if((recordCount % resultsPerPage) > 0) {
            pageCount += 1;
        }
        
        ModelAndView mv = new ModelAndView("advancedReview");
        
        mv.addObject(getViewType(request.getParameter(PARAM_VIEW_TYPE)), true);

        String sortBy = request.getParameter(SORT_BY_QUERY_PARAM_NAME);
        String sortOrder = request.getParameter(SORT_ORDER_QUERY_PARAM_NAME);

        mv.addObject("facetList", facetList);
        mv.addObject("surveyId", request.getParameter(SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME));
        
        // set sortBy or use default if none requested.
        mv.addObject("sortBy", sortBy != null ? sortBy : "record.when");
        // set sortOrder or use default if none requested.
        mv.addObject("sortOrder", sortOrder != null ? sortOrder : "DESC");
        
        mv.addObject("searchText", request.getParameter(SEARCH_QUERY_PARAM_NAME));
        mv.addObject("recordCount", recordCount);
        mv.addObject("resultsPerPage", resultsPerPage);
        mv.addObject("pageCount", pageCount);
        mv.addObject("reportList", reportDAO.getReports(ReportCapability.SCROLLABLE_RECORDS, ReportView.ADVANCED_REVIEW));
        
        // Avoid the situation where the number of results per page is increased
        // thereby leaving a page number higher than the total page count.
        mv.addObject("pageNumber", Math.min(pageCount, pageNumber.longValue()));

        // Add an optional parameter to set a record to highlight
        if (!StringUtils.nullOrEmpty(request.getParameter(LATEST_RECORD_ID))) {
            mv.addObject(LATEST_RECORD_ID, request.getParameter(LATEST_RECORD_ID));
        }
        
        return mv;
    }
    
    /**
     * Returns the list of records matching the {@link Facet} criteria as KML.
     */
    @RequestMapping(value = "/review/sightings/advancedReviewKMLSightings.htm", method = RequestMethod.GET)
    public void advancedReviewKMLSightings(HttpServletRequest request, HttpServletResponse response) throws IOException, JAXBException {

        configureHibernateSession();

        Integer surveyId = null;
        if(request.getParameter(SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME) != null) {
            surveyId = Integer.parseInt(request.getParameter(SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME));
        }
        List<Facet> facetList = facetService.getFacetList(currentUser(), (Map<String, String[]>)request.getParameterMap());
        
        KMLWriter writer = KMLUtils.createKMLWriter(request.getContextPath(), null);
        User currentUser = getRequestContext().getUser();
        String contextPath = request.getContextPath();
        Session sesh = getRequestContext().getHibernate();
        
        ScrollableRecords sr = getMatchingRecordsAsScrollableRecords(facetList, surveyId, 
                                                                     request.getParameter(SORT_BY_QUERY_PARAM_NAME), 
                                                                     request.getParameter(SORT_ORDER_QUERY_PARAM_NAME),
                                                                     request.getParameter(SEARCH_QUERY_PARAM_NAME));
        int recordCount = 0;
        List<Record> rList = new ArrayList<Record>(ScrollableRecords.RECORD_BATCH_SIZE);
        while (sr.hasMoreElements()) {
            rList.add(sr.nextElement());
            
            // evict to ensure garbage collection
            if (++recordCount % ScrollableRecords.RECORD_BATCH_SIZE == 0) {
                KMLUtils.writeRecords(writer, currentUser, contextPath, rList);
                rList.clear();
                sesh.clear();
            }
        }
        KMLUtils.writeRecords(writer, currentUser, contextPath, rList);
        
        response.setContentType(KMLUtils.KML_CONTENT_TYPE);
        writer.write(false, response.getOutputStream());
    }
    
    /**
     * Returns a JSON array of records matching the {@link Facet} criteria.
     */
    @RequestMapping(value = "/review/sightings/advancedReviewJSONSightings.htm", method = RequestMethod.GET)
    public void advancedReviewJSONSightings(HttpServletRequest request, 
                                            HttpServletResponse response,
                                            @RequestParam(value=RESULTS_PER_PAGE_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_RESULTS_PER_PAGE) Integer resultsPerPage,
                                            @RequestParam(value=PAGE_NUMBER_QUERY_PARAM_NAME, required=false, defaultValue=DEFAULT_PAGE_NUMBER) Integer pageNumber) throws IOException {
       configureHibernateSession();
        
        Integer surveyId = null;
        if(request.getParameter(SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME) != null) {
            surveyId = Integer.parseInt(request.getParameter(SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME));
        }
        List<Facet> facetList = facetService.getFacetList(currentUser(), (Map<String, String[]>)request.getParameterMap());
        ScrollableRecords sc = getMatchingRecordsAsScrollableRecords(facetList,
                                                                     surveyId,
                                                                     request.getParameter(SORT_BY_QUERY_PARAM_NAME), 
                                                                     request.getParameter(SORT_ORDER_QUERY_PARAM_NAME),
                                                                     request.getParameter(SEARCH_QUERY_PARAM_NAME),
                                                                     pageNumber, resultsPerPage);
        
        int recordCount = 0;
        JSONArray array = new JSONArray();
        Record r;
        while(sc.hasMoreElements()) {
            r = sc.nextElement();
            array.add(r.flatten(2));
            if (++recordCount % ScrollableRecords.RECORD_BATCH_SIZE == 0) {
                getRequestContext().getHibernate().clear();
            }
        }
        
        response.setContentType("application/json");
        response.getWriter().write(array.toString());
        response.getWriter().flush();
    }
    
    /**
     * Returns an XLS representation of representation of records matching the
     * {@link Facet} criteria. This function should only be used if the records
     * are part of a single survey.
     * @throws Exception 
     */
    @RequestMapping(value = "/review/sightings/advancedReviewDownload.htm", method = RequestMethod.GET)
    public void advancedReviewDownload(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       @RequestParam(value=SurveyFacet.SURVEY_ID_QUERY_PARAM_NAME, required=false) Integer surveyId,
                                       @RequestParam(value=QUERY_PARAM_DOWNLOAD_FORMAT, required=true) String[] downloadFormat) throws Exception {
        configureHibernateSession();
        
        User currentUser = currentUser();
        List<Facet> facetList = facetService.getFacetList(currentUser, (Map<String, String[]>)request.getParameterMap());
        
        SurveyFacet surveyFacet = facetService.getFacetByType(facetList, SurveyFacet.class);
        
        // list of surveys to download
        List<Survey> surveyList = surveyFacet.getSelectedSurveys();
               
        // In the case that no surveys are selected to filter by - we will use
        // all the surveys available for the accessing user
        if (surveyList.isEmpty()) {
            surveyList = surveyDAO.getActiveSurveysForUser(currentUser);
        }
        
        // I think 'surveyId' is not used for AdvancedReview but is used for MySightings
        ScrollableRecords sc = getMatchingRecordsAsScrollableRecords(facetList,
                                                     surveyId,
                                                     request.getParameter(SORT_BY_QUERY_PARAM_NAME), 
                                                     request.getParameter(SORT_ORDER_QUERY_PARAM_NAME),
                                                     request.getParameter(SEARCH_QUERY_PARAM_NAME));
        
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
    @RequestMapping(value = ADVANCED_REVIEW_REPORT_URL, method = RequestMethod.GET)
    public ModelAndView advancedReviewReport(HttpServletRequest request,
                                             HttpServletResponse response,
                                             @RequestParam(value = QUERY_PARAM_REPORT_ID, required = true) int reportId) throws Exception {
        configureHibernateSession();

        User currentUser = currentUser();
        List<Facet> facetList = facetService.getFacetList(currentUser, (Map<String, String[]>) request.getParameterMap());

        Report report = reportDAO.getReport(reportId);

        ScrollableRecords sc = getMatchingRecordsAsScrollableRecords(facetList,
                null,
                request.getParameter(SORT_BY_QUERY_PARAM_NAME),
                request.getParameter(SORT_ORDER_QUERY_PARAM_NAME),
                request.getParameter(SEARCH_QUERY_PARAM_NAME));

        return reportService.renderReport(request, response, report, sc);
    }
    
    private long countMatchingRecords(List<Facet> facetList, Integer surveyId, String searchText) {
        HqlQuery hqlQuery = new HqlQuery("select count(distinct record) from Record record");
        applyFacetsToQuery(hqlQuery, facetList, surveyId, searchText);

        Query query = toHibernateQuery(hqlQuery);
        Object result = query.uniqueResult();
        return Long.parseLong(result.toString());
    }

    private void applyFacetsToQuery(HqlQuery hqlQuery, List<Facet> facetList,
            Integer surveyId, String searchText) {

        hqlQuery.leftJoin("record.location", "location");
        hqlQuery.leftJoin("location.attributes", "locAttribute");
        
        hqlQuery.leftJoin("record.species", "species");
        hqlQuery.leftJoin("record.censusMethod", "censusMethod");
        
        hqlQuery.leftJoin("record.attributes", "recordAttributeVal");
        hqlQuery.leftJoin("recordAttributeVal.attribute", "recordAttribute");
        
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
            Predicate searchPredicate = Predicate.ilike("record.notes", String.format("%%%s%%", searchText));
            searchPredicate.or(Predicate.ilike("species.scientificName", String.format("%%%s%%", searchText)));
            searchPredicate.or(Predicate.ilike("species.commonName", String.format("%%%s%%", searchText)));
            
            hqlQuery.and(searchPredicate);
        }
        
        if(surveyId != null) {
            hqlQuery.and(Predicate.eq("record.survey.id", surveyId));
        }
    }

    /**
     * Applies the selection criteria represented by the provided {@link Facet}s
     * and the associated {@link FacetOption}s returning the matching {@link List}
     * of {@link Record}. 
     * 
     * @param facetList the {@link Facet}s providing the selection criteria.
     * @param surveyId the primary key of the survey containing all eligible records.
     * The <code>surveyId</code> may be null if all surveys are allowed.
     * @param sortProperty the HQL property that should be used for sorting. 
     * The sortProperty may be null if no sorting is necessary.
     * @param sortOrder the sorting order
     * @param searchText textual restriction to be applied to matching records.
     * @return the {@link List} of matching {@link Record}s.
     * 
     * @see SortOrder
     */
    List<Record> getMatchingRecordsAsList(List<Facet> facetList,
                                            Integer surveyId,
                                            String sortProperty, 
                                            String sortOrder, 
                                            String searchText,
                                            Integer resultsPerPage,
                                            Integer pageNumber) {
        
        
        Query query = getMatchingRecordsQuery(facetList, surveyId, sortProperty, sortOrder, searchText);
        if(resultsPerPage != null && pageNumber != null && resultsPerPage > 0 && pageNumber > 0) {
            query.setFirstResult((pageNumber-1) * resultsPerPage);
            query.setMaxResults(resultsPerPage);
        }
        
        List<Object[]> rowList = query.list();
        List<Record> recordList = new ArrayList<Record>(rowList.size());
        for(Object[] rowObj : rowList) {
            recordList.add((Record)rowObj[0]);
        }
        
        return recordList;
    }
    
    /**
     * Applies the selection criteria represented by the provided {@link Facet}s
     * and the associated {@link FacetOption}s returning the matching {@link List}
     * of {@link Record}. 
     * 
     * @param facetList the {@link Facet}s providing the selection criteria.
     * @param surveyId the primary key of the survey containing all eligible records.
     * The <code>surveyId</code> may be null if all surveys are allowed.
     * @param sortProperty the HQL property that should be used for sorting. 
     * The sortProperty may be null if no sorting is necessary.
     * @param sortOrder the sorting order
     * @param searchText textual restriction to be applied to matching records.
     * @return the Query to select the matching records {@link Record}s.
     * 
     * @see SortOrder
     */
    private Query getMatchingRecordsQuery(List<Facet> facetList,
                                            Integer surveyId,
                                            String sortProperty, 
                                            String sortOrder, 
                                            String searchText) {
        return createFacetQuery(facetList, surveyId, sortProperty, sortOrder, searchText);
    }
    
    /**
     * Applies the selection criteria represented by the provided {@link Facet}s
     * and the associated {@link FacetOption}s returning the matching {@link List}
     * of {@link Record}. 
     * 
     * @param facetList the {@link Facet}s providing the selection criteria.
     * @param surveyId the primary key of the survey containing all eligible records.
     * The <code>surveyId</code> may be null if all surveys are allowed.
     * @param sortProperty the HQL property that should be used for sorting. 
     * The sortProperty may be null if no sorting is necessary.
     * @param sortOrder the sorting order
     * @param searchText textual restriction to be applied to matching records.
     * @return the Query to select the matching records {@link Record}s.
     * 
     * @see SortOrder
     */
    private ScrollableRecords getMatchingRecordsAsScrollableRecords(List<Facet> facetList,
                                                                    Integer surveyId,
                                                                    String sortProperty, 
                                                                    String sortOrder, 
                                                                    String searchText) {
        Query query = getMatchingRecordsQuery(facetList, surveyId, sortProperty, sortOrder, searchText);
        return new ScrollableRecordsImpl(query);
    }
    
    private ScrollableRecords getMatchingRecordsAsScrollableRecords(List<Facet> facetList,
                                                                    Integer surveyId,
                                                                    String sortProperty, 
                                                                    String sortOrder, 
                                                                    String searchText,
                                                                    int pageNumber, int entriesPerPage) {
        Query query = getMatchingRecordsQuery(facetList, surveyId, sortProperty, sortOrder, searchText);
        return new ScrollableRecordsImpl(query, pageNumber, entriesPerPage);
    }
    
    private Query createFacetQuery(List<Facet> facetList, Integer surveyId, String sortProperty, String sortOrder, String searchText) {
        // extra columns in select are used for ordering
        HqlQuery hqlQuery = new HqlQuery("select distinct record, species.scientificName, species.commonName, location.name, censusMethod.type from Record record");
        
        applyFacetsToQuery(hqlQuery, facetList, surveyId, searchText);

        // NO SQL injection for you
        if(sortProperty != null && sortOrder != null && VALID_SORT_PROPERTIES.contains(sortProperty)) {
            hqlQuery.order(sortProperty, 
                           SortOrder.valueOf(sortOrder).name(),
                           null);
        }
        return toHibernateQuery(hqlQuery);  
    }

    /**
     * Converts the {@link HqlQuery} to a {@ Query} representation.
     */
    private Query toHibernateQuery(HqlQuery hqlQuery) {
        Session sesh = getRequestContext().getHibernate();
        Query query = sesh.createQuery(hqlQuery.getQueryString());
        Object[] parameterValues = hqlQuery.getParametersValue();
        for(int i=0; i<parameterValues.length; i++) {
            query.setParameter(i, parameterValues[i]);
        }
        return query;
    }

    /**
     * Configures the flush mode and installs an appropriate Record filter on the current hibernate session.
     */
    private void configureHibernateSession() {
        // We are changing the flush mode here to prevent checking for dirty
        // objects in the session cache. Normally this is desireable so that
        // you will not receive stale objects however in this situation
        // the controller will only be performing reads and the objects cannot
        // be stale. We are explicitly setting the flush mode here because
        // we are potentially loading a lot of objects into the session cache
        // and continually checking if it is dirty is prohibitively expensive.
        // https://forum.hibernate.org/viewtopic.php?f=1&t=936174&view=next
        RequestContext requestContext = getRequestContext();
        requestContext.getHibernate().setFlushMode(FlushMode.MANUAL);
        User user = requestContext.getUser();

        // Enabling this filter users from seeing records not allowed by their current role.
        FilterManager.enableRecordFilter(requestContext.getHibernate(), user);
    }

    /**
     * @return the authenticated user from the request context, or null if the request originated from an
     * anonymous user.
     */
    private User currentUser() {
        return getRequestContext().getUser();
    }

    /**
     * Returns the default view model to use if a view type has not been specified in the request.
     * The default is determined by the value of the Preference.DEFAULT_TO_MAP_VIEW_KEY preference.
     * @return the default view model name to use.
     */
    private String defaultView() {
        PreferenceUtil preferenceUtil = new PreferenceUtil(preferenceDAO);
        boolean useMap = preferenceUtil.getBooleanPreference(Preference.ADVANCED_REVIEW_DEFAULT_VIEW_KEY);
        return useMap ? MODEL_MAP_VIEW_SELECTED : MODEL_TABLE_VIEW_SELECTED;
    }

    /**
     * Returns the model object that represents the view to display based on the supplied request parameter.
     * @param viewTypeParameter the value of the request parameter used to request a particular view type.  May be null,
     *                          in which case the default is determined by the Preference.DEFAULT_TO_MAP_VIEW_KEY preference.
     * @return the name of the view model object used by the page to select the correct view.
     */
    private String getViewType(String viewTypeParameter) {
        if (VIEW_TYPE_DOWNLOAD.equals(viewTypeParameter)) {
           return MODEL_DOWNLOAD_VIEW_SELECTED;
        } else if (VIEW_TYPE_TABLE.equals(viewTypeParameter)) {
            return MODEL_TABLE_VIEW_SELECTED;
        } else if (VIEW_TYPE_MAP.equals(viewTypeParameter)) {
            return MODEL_MAP_VIEW_SELECTED;
        }
        else {
            return defaultView();
        }
    }
}