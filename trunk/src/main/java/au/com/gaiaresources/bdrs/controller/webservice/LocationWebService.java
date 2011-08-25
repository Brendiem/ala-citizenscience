package au.com.gaiaresources.bdrs.controller.webservice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.http.HTTPException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import au.com.gaiaresources.bdrs.controller.AbstractController;
import au.com.gaiaresources.bdrs.model.location.Location;
import au.com.gaiaresources.bdrs.model.location.LocationDAO;
import au.com.gaiaresources.bdrs.model.metadata.Metadata;
import au.com.gaiaresources.bdrs.model.metadata.MetadataDAO;
import au.com.gaiaresources.bdrs.model.user.User;
import au.com.gaiaresources.bdrs.model.user.UserDAO;

import com.vividsolutions.jts.geom.Geometry;

@Controller
public class LocationWebService extends AbstractController {
    @Autowired
    private au.com.gaiaresources.bdrs.model.location.LocationService locationService;
    @Autowired
    private LocationDAO locationDAO;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private MetadataDAO metadataDAO;
    
    public static final String JSON_KEY_ISVALID = "isValid";
    public static final String JSON_KEY_MESSAGE = "message";
    
    public static final String PARAM_WKT = "wkt";
    
    public static final String IS_WKT_VALID_URL = "/webservice/location/isValidWkt.htm";

    private Logger log = Logger.getLogger(getClass());
    
    @RequestMapping(value="/webservice/location/getLocationById.htm", method=RequestMethod.GET)
    public void getLocationById(HttpServletRequest request,
                                HttpServletResponse response,
                                @RequestParam(value="id", required=true) int pk)
        throws IOException {

        Location location = locationDAO.getLocation(pk);
        response.setContentType("application/json");
        response.getWriter().write(JSONObject.fromObject(location.flatten()).toString());
    }
    
    @RequestMapping(value="/webservice/location/getLocationsById.htm", method=RequestMethod.GET)
    public void getLocationsById(HttpServletRequest request,
                                HttpServletResponse response,
                                @RequestParam(value="ids", required=true) String ids)
        throws IOException {

        JSONArray arr = JSONArray.fromObject(ids);
        List<Integer> idList = new ArrayList<Integer>();
        for (int i = 0; i < arr.size(); i++) {
            idList.add(Integer.parseInt(arr.get(i).toString()));
        }
        List<Location> locationList = locationDAO.getLocations(idList);
        Geometry g = null;
        for (Location loc : locationList) {
            Geometry locGeo = loc.getLocation().getEnvelope();
            
            if (g == null) {
                g = locGeo;
            } else {
                g = g.getEnvelope().union(locGeo);
            }
        }
        JSONObject ob = new JSONObject();
        ob.element("geometry", g.toText());
        response.setContentType("application/json");
        response.getWriter().write(ob.toString());
    }
    

    @RequestMapping(value="/webservice/location/bookmarkUserLocation.htm", method=RequestMethod.GET)
    public void bookmarkUserLocation(HttpServletRequest request,
                                HttpServletResponse response,
                                @RequestParam(value="ident", required=true) String ident,
                                @RequestParam(value="locationName", required=true) String locationName,
                                @RequestParam(value="latitude", required=true) double latitude,
                                @RequestParam(value="longitude", required=true) double longitude,
                                @RequestParam(value="isDefault", required=true) boolean isDefault)
        throws IOException {

        if(locationName.isEmpty()) {
            locationName = String.valueOf(latitude) + ", " + String.valueOf(longitude);
        }
        
        User user = userDAO.getUserByRegistrationKey(ident);
        if(user == null) {
            // Perhaps a wrong ident or a wrong portal
            throw new HTTPException(HttpServletResponse.SC_UNAUTHORIZED);
        }
        
        Location loc = new Location();
        loc.setName(locationName);
        loc.setUser(user);
        loc.setLocation(locationService.createPoint(latitude, longitude));
        loc = locationDAO.save(loc);
        
        if(isDefault) {
            Metadata defaultLocIdMd = user.getMetadataObj(Metadata.DEFAULT_LOCATION_ID);
            if(defaultLocIdMd == null) {
                defaultLocIdMd = new Metadata();
                defaultLocIdMd.setKey(Metadata.DEFAULT_LOCATION_ID);
            }
            defaultLocIdMd.setValue(loc.getId().toString());
            metadataDAO.save(defaultLocIdMd);
            
            user.getMetadata().add(defaultLocIdMd);
            userDAO.updateUser(user);
        }
        
        response.setContentType("application/json");
        response.getWriter().write(JSONObject.fromObject(loc.flatten()).toString());
    }
    
    @RequestMapping(value=IS_WKT_VALID_URL, method=RequestMethod.GET)
    public void bookmarkUserLocation(HttpServletRequest request,
                                HttpServletResponse response, 
                                @RequestParam(value=PARAM_WKT, required=false) String wkt) throws IOException {
        
        JSONObject result = new JSONObject();
        result.put(JSON_KEY_ISVALID, true);
        result.put(JSON_KEY_MESSAGE, "");
        
        if (!StringUtils.hasLength(wkt)) {
            result.put(JSON_KEY_ISVALID, false);
            result.put(JSON_KEY_MESSAGE, "");   // no message
        } else {
            try {
                Geometry geom = locationService.createGeometryFromWKT(wkt);
                if (geom == null) {
                    result.put(JSON_KEY_ISVALID, false);
                    result.put(JSON_KEY_MESSAGE, "Geometry is null");
                } else {
                    if (!geom.isValid()) {
                        result.put(JSON_KEY_ISVALID, false);
                        result.put(JSON_KEY_MESSAGE, "Geometry is invalid. Note that self intersecting polygons are not allowed.");
                    }
                }
            } catch (IllegalArgumentException iae) {
                result.put(JSON_KEY_ISVALID, false);
                result.put(JSON_KEY_MESSAGE, iae.getMessage());
            }  
        }
        writeJson(request, response, result.toString());
    }
}