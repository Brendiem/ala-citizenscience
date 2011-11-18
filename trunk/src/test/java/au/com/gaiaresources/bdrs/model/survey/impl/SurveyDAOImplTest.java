/**
 * 
 */
package au.com.gaiaresources.bdrs.model.survey.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import au.com.gaiaresources.bdrs.geometry.GeometryBuilder;
import au.com.gaiaresources.bdrs.model.group.Group;
import au.com.gaiaresources.bdrs.model.group.GroupDAO;
import au.com.gaiaresources.bdrs.model.location.Location;
import au.com.gaiaresources.bdrs.model.location.LocationDAO;
import au.com.gaiaresources.bdrs.model.region.Region;
import au.com.gaiaresources.bdrs.model.region.RegionDAO;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.survey.SurveyDAO;
import au.com.gaiaresources.bdrs.model.taxa.Attribute;
import au.com.gaiaresources.bdrs.model.taxa.AttributeOption;
import au.com.gaiaresources.bdrs.model.taxa.AttributeType;
import au.com.gaiaresources.bdrs.model.taxa.IndicatorSpecies;
import au.com.gaiaresources.bdrs.model.taxa.TaxaDAO;
import au.com.gaiaresources.bdrs.model.taxa.TaxonGroup;
import au.com.gaiaresources.bdrs.model.user.User;
import au.com.gaiaresources.bdrs.model.user.UserDAO;
import au.com.gaiaresources.bdrs.test.AbstractTransactionalTest;

import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
/**
 * @author kehan
 *
 */
public class SurveyDAOImplTest extends AbstractTransactionalTest {
    @Autowired
    private SurveyDAO surveyDAO;
    @Autowired
    private TaxaDAO taxaDAO;
    @Autowired
    private RegionDAO regionDAO;
    @Autowired
    private LocationDAO locationDAO;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private GroupDAO groupDAO;
    
    
    private Logger log = Logger.getLogger(getClass());
   

    private Survey survey1;
    private Survey survey2;
    private Survey survey3;
    private TaxonGroup taxonGroup;
    private IndicatorSpecies speciesA;
    private IndicatorSpecies speciesB;
    private IndicatorSpecies speciesC;
    private Region regionA;
    private Region regionB;
    private GeometryBuilder geometryBuilder = new GeometryBuilder();
    private List<IndicatorSpecies> species;
    private User user1;
    private Group group1;
    private static final String GROUP_1_NAME = "Group1";
    private Group group2;
    private static final String GROUP_2_NAME = "Group2";
    ArrayList<Location> locations;
    private User user2;
    private User user3;
    
    
    
    /**
     * Set up a few test species etc that we can test against
     * Copied from SingleSiteMultiTaxaControllerTest.java
     */
    @Before
    public void setUp() {
        user1 = userDAO.getUser("admin");
        taxonGroup = new TaxonGroup();
        taxonGroup.setName("Birds");
        taxonGroup = taxaDAO.save(taxonGroup);
        user2 = userDAO.createUser("user2", "firstname","lastname" , "user2@mailinator.com", "password", "user2key", new String[]{"ROLE_USER"});
        
        speciesA = new IndicatorSpecies();
        speciesA.setCommonName("Indicator Species A");
        speciesA.setScientificName("Indicator Species A");
        speciesA.setTaxonGroup(taxonGroup);
        speciesA = taxaDAO.save(speciesA);
        
        speciesB = new IndicatorSpecies();
        speciesB.setCommonName("Indicator Species B");
        speciesB.setScientificName("Indicator Species B");
        speciesB.setTaxonGroup(taxonGroup);
        speciesB = taxaDAO.save(speciesB);
        speciesC = new IndicatorSpecies();
        speciesC.setCommonName("Indicator Species B");
        speciesC.setScientificName("Indicator Species B");
        speciesC.setTaxonGroup(taxonGroup);
        speciesC = taxaDAO.save(speciesB);
        species = new ArrayList<IndicatorSpecies>(2);
        species.add(speciesA);
        species.add(speciesB);
           
        
        List<Attribute> attributeList = new ArrayList<Attribute>();
        Attribute attr;
        for(AttributeType attrType : AttributeType.values()) {  
            attr = new Attribute();
            attr.setRequired(true);
            attr.setName(attrType.toString());
            attr.setTypeCode(attrType.getCode());
            attr.setTag(false);
            
            if(AttributeType.STRING_WITH_VALID_VALUES.equals(attrType)) {
                List<AttributeOption> optionList = new ArrayList<AttributeOption>();
                for(int i=0; i<4; i++) {
                    AttributeOption opt = new AttributeOption();
                    opt.setValue(String.format("Option %d", i));
                    opt = taxaDAO.save(opt);
                    optionList.add(opt);
                }
                attr.setOptions(optionList);
            }
            
            attr = taxaDAO.save(attr);
            attributeList.add(attr);
        }
        //Create a Survey
        survey1 = new Survey();
        survey1.setName("Survey 1");
        survey1.setActive(true);
        survey1.setStartDate(new Date());
        survey1.setPublic(true);
        survey1.setDescription("Survey To Test Survey DAO with");
        survey1.setAttributes(attributeList);
        
        
        //Add The species to the survey
        survey1.setSpecies(new HashSet<IndicatorSpecies>(species));
        survey1 = surveyDAO.save(survey1);
        
        //Create some regions
        Set<Polygon> polygons = new HashSet<Polygon>();
        polygons.add(geometryBuilder.createSquare(-50, -50, 50));
        MultiPolygon multiPolygon = new MultiPolygon((Polygon[])polygons.toArray(new Polygon[polygons.size()]), geometryBuilder.getFactory());
        regionA = regionDAO.createRegion(speciesA.getScientificName(), multiPolygon);
        polygons = new HashSet<Polygon>();
        polygons.add(geometryBuilder.createSquare(10, 10, 50));
        multiPolygon = new MultiPolygon((Polygon[])polygons.toArray(new Polygon[polygons.size()]), geometryBuilder.getFactory());
        regionB = regionDAO.createRegion(speciesB.getScientificName(), multiPolygon);

        //Add them to the species
        speciesA.getRegions().add(regionA);
        speciesA = taxaDAO.save(speciesA);
        speciesB.getRegions().add(regionB);
        speciesB = taxaDAO.save(speciesB);
        
        //Create some Locations
        Location loc;
        locations = new ArrayList<Location>(2);
        loc = new Location();
        loc.setName("A1");
        loc.setLocation(geometryBuilder.createPoint(-20, -20));
        //Why do we have to have a user1 in a location?
        loc.setUser(user1);
        locations.add(locationDAO.createLocation(loc));
        loc = new Location();
        loc.setName("A2");
        loc.setLocation(geometryBuilder.createPoint(-10, -10));
        loc.setUser(user1);
        locations.add(locationDAO.createLocation(loc));
        //Create a group1
        group1 = groupDAO.createGroup(GROUP_1_NAME);
        group1.getUsers().add(user1);
        
        group1 = groupDAO.updateGroup(group1);
        //Add the locations to the survey
        survey1.setLocations(locations);
        
        //Add the group1 to the survey
        survey1.getGroups().add(group1);
        survey1 = surveyDAO.updateSurvey(survey1);
        
        group2 = groupDAO.createGroup(GROUP_2_NAME);
        group2.getUsers().add(user1);
        user3 = userDAO.createUser("user3", "firstname","lastname" , "user3@mailinator.com", "password", "user3key", new String[]{"ROLE_USER"});
        group2.getUsers().add(user3);
        group2 = groupDAO.updateGroup(group2);

        survey2 = surveyDAO.createSurvey("Survey2");
        survey2.setPublic(true);
        survey2.getUsers().add(user2);
        
        survey2.getGroups().add(group2);
        survey2 = surveyDAO.updateSurvey(survey2);
        
        survey3 = surveyDAO.createSurvey("Survey3");
        survey3.getGroups().add(group2);
        survey3 = surveyDAO.updateSurvey(survey3);
        
    }
    
    /**
     * Test survey creation
     */
    @Test
    public void testCreateSurvey() throws Exception {
        String surveyName = "testSurvey";
        Survey s = surveyDAO.createSurvey(surveyName);
        Assert.assertEquals(surveyName, s.getName());
        Assert.assertTrue("Newly created surveys shoudl be active", s.isActive());
    }
    /**
     * Make sure we can load the survey created in the @Before above
     * @throws Exception
     */
    @Test
    public void testCreatedSurveysExist() throws Exception{
        Survey s = surveyDAO.getSurveyByName(survey1.getName());
        Assert.assertNotNull(s);
        Assert.assertEquals(survey1.getId(), s.getId());
    }
    
    /**
     * Retrieve species that fall within the points that make up a survey
     * @throws Exception
     */
    @Test
    public void testGetSpeciesWithinSurveyLocations() throws Exception{
        Set<IndicatorSpecies> retrievedSpecies = surveyDAO.getSpeciesWithinSurveyLocations(survey1);
        //We have only added one species to the region, there should only be a single result
        Assert.assertEquals(1, retrievedSpecies.size());
        for(IndicatorSpecies sp: retrievedSpecies){
            //And this should be speciesA
            Assert.assertEquals(speciesA, sp);
        }
    }
    /**
     * Runs the previous test, but retrieves the cached species from the survey.
     * @throws Exception
     */
    @Test
    public void testGetSpeciesWithinSurveyLocationsCache() throws Exception{
      //And now we do it all over again. The survey species will be cached so this covers the 
      //species from the cache
        this.testGetSpeciesWithinSurveyLocations();
    }
    
    @Test
    public void testSurveySpeciesCacheExpiry() throws Exception{
        //Get species from the cache
        Set<IndicatorSpecies> cachedSpecies = surveyDAO.getSpeciesWithinSurveyLocations(survey1);
        //Make sure we're loading from the cache
        cachedSpecies = surveyDAO.getSpeciesWithinSurveyLocations(survey1);
        Assert.assertEquals(1, cachedSpecies.size());
        for(IndicatorSpecies sp: cachedSpecies){
            //And this should be speciesA
            Assert.assertEquals(speciesA, sp);
        }
        speciesC.getRegions().add(regionA);
        taxaDAO.save(speciesC);
        surveyDAO.save(survey1);
        //Now the cache should have expired
        Set<IndicatorSpecies> refreshedSpecies = surveyDAO.getSpeciesWithinSurveyLocations(survey1);
        Assert.assertEquals(2, refreshedSpecies.size());
        Assert.assertTrue("Returned set should contain species A", refreshedSpecies.contains(speciesA));
        Assert.assertTrue("Returned set should contain species C", refreshedSpecies.contains(speciesC));
    }
    
    @Test
    public void testGetActiveSurveysForUser(){
        testGetActiveSurveysForUser(user1, 3, survey1);
        testGetActiveSurveysForUser(user1, 3, survey2);
        testGetActiveSurveysForUser(user1, 3, survey3);
        testGetActiveSurveysForUser(user2, 2, survey1);
        testGetActiveSurveysForUser(user2, 2, survey2);
        testGetActiveSurveysForUser(user3, 3, survey1);
        testGetActiveSurveysForUser(user3, 3, survey2);
        testGetActiveSurveysForUser(user3, 3, survey3);
        
    }
    private void testGetActiveSurveysForUser(User u, int expectedLength, Survey expectedSurvey){
        List<Survey> surveys = surveyDAO.getActiveSurveysForUser(u);
        Assert.assertEquals("Returned surveys should have equal length", expectedLength, surveys.size());
        // Private survey  which the user is a member of
        if(expectedLength > 0)
        {
            Assert.assertNotNull(surveys);
            Assert.assertTrue("Returned list should contain the survey",surveys.contains(expectedSurvey));
        }
    }
    

    @Test
    public void testGetSurveys(){
        List<Survey> surveys = surveyDAO.getSurveys(user1);
        //Returns  all surveys for admins
        Assert.assertTrue("Returned list should contain the survey",surveys.contains(survey1));
        Assert.assertTrue("Returned list should contain the survey",surveys.contains(survey2));
        surveys = surveyDAO.getSurveys(user2);
        //Only return survey's associated with the user
        Assert.assertTrue("Returned list should contain the survey",surveys.contains(survey2));
        //We have to use user2 for this because the admin user has access to all surveys
        Assert.assertEquals("Only surveys that " + user2.getName() + " is a member of should return", 1, surveys.size());
    }
    @Test
    public void testGetActiveSurveysForUserWithGroupId(){
        testGetActiveSurveysForUserWithGroupId(user1, group1, 1, survey1);
        testGetActiveSurveysForUserWithGroupId(user2, group2, 1, survey2);
        testGetActiveSurveysForUserWithGroupId(user3, group2, 2, survey2);
        testGetActiveSurveysForUserWithGroupId(user3, group2, 2, survey3);
        testGetActiveSurveysForUserWithGroupId(user3, group1, 1, survey1);
    }
    
    @Test
    public void testSearchSpeciesForSurveyInjectionAttack() {
        // will cause an error if hql injection is possible
        surveyDAO.getSpeciesForSurveySearch(survey1.getId(), "%Carnaby's%Black%");
        surveyDAO.getSpeciesForSurveySearch(survey1.getId(), ";drop table portal;");
    }
    
    private void testGetActiveSurveysForUserWithGroupId(User u, Group group, int expectedLength, Survey expectedSurvey){
        List<Survey> surveys = surveyDAO.getActiveSurveysForUser(u, group);
        Assert.assertNotNull(surveys);
        Assert.assertEquals("Returned surveys should have equal length", expectedLength, surveys.size());
        if(expectedLength >0 && expectedSurvey != null) {
            Assert.assertTrue("The group is added to the survey", surveys.get(0).getGroups().contains(group));
            Assert.assertFalse("The user should not be directly added to the survey", surveys.get(0).getUsers().contains(user1));
            Assert.assertTrue("Returned list should contain the survey",surveys.contains(expectedSurvey));
        }
    }
}