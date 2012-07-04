package au.com.gaiaresources.bdrs.service.survey;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.Session;

import au.com.gaiaresources.bdrs.db.impl.PersistentImpl;
import au.com.gaiaresources.bdrs.file.FileService;
import au.com.gaiaresources.bdrs.json.JSONObject;
import au.com.gaiaresources.bdrs.model.location.Location;
import au.com.gaiaresources.bdrs.model.location.LocationService;
import au.com.gaiaresources.bdrs.model.method.CensusMethod;
import au.com.gaiaresources.bdrs.model.record.Record;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.taxa.Attribute;
import au.com.gaiaresources.bdrs.model.taxa.AttributeOption;
import au.com.gaiaresources.bdrs.model.user.UserDAO;
import au.com.gaiaresources.bdrs.service.survey.handler.AttributeValueImportHandler;
import au.com.gaiaresources.bdrs.service.survey.handler.MetadataImportHandler;
import au.com.gaiaresources.bdrs.service.survey.handler.RecordImportHandler;
import au.com.gaiaresources.bdrs.service.survey.handler.SimpleImportHandler;
import au.com.gaiaresources.bdrs.service.survey.handler.UserImportHandler;

/**
 * Provides a mapping between data types that can be imported and the appropriate parser for the encoded data.
 */
public class ImportHandlerRegistry extends HashMap<String, ImportHandler> {

    /**
     * Creates a new instance and registers all parsers.
     *
     * @param locationService Provides WKT to Geometry conversion facilities.
     * @param fileService     Provides access to the BDRS file store.
     * @param userDAO         Provides access to the BDRS users for user import.
     */
    public ImportHandlerRegistry(LocationService locationService, FileService fileService, UserDAO userDAO) {
        new SimpleImportHandler(locationService, Survey.class).register(this);
        new MetadataImportHandler(locationService, fileService).register(this);
        new SimpleImportHandler(locationService, Attribute.class).register(this);
        new SimpleImportHandler(locationService, AttributeOption.class).register(this);
        new SimpleImportHandler(locationService, CensusMethod.class).register(this);
        new SimpleImportHandler(locationService, Location.class).register(this);
        new RecordImportHandler(locationService, Record.class).register(this);
        new AttributeValueImportHandler(locationService, fileService).register(this);
        // don't register the user import handler if there is no way to access 
        // the system user accounts
        if (userDAO != null) {
            new UserImportHandler(locationService, userDAO).register(this);
        }
    }

    /**
     * The main point of entry for this registry. When invoked, this method will create the specified
     * encoded instance and all dependencies.
     *
     * @param persistentLookup a registry of persisted instances that may be dependencies of the encoded object.
     * @param jsonPersistent   the encoded representation to be decoded and instantiated.
     * @param sesh             the session to use when saving created instance.
     * @param importData       a registry of not yet persisted instances that may be dependencies of the encoded object.
     * @return an instantiated and populated instance of the encoded representation.
     * @throws InvocationTargetException thrown if there has been an error introspecting the object to be created.
     * @throws NoSuchMethodException     thrown if there has been an error introspecting the object to be created.
     * @throws IllegalAccessException    thrown if there has been an error introspecting the object to be created.
     * @throws InstantiationException    thrown if there has been an error introspecting the object to be created.
     */
    public Object importData(Session sesh, JSONObject importData, Map<Class, Map<Integer, PersistentImpl>> persistentLookup, JSONObject jsonPersistent) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        String klazz = getClassName(jsonPersistent);
        if (klazz == null) {
            throw new IllegalArgumentException("The specified JSON object is not a Persistent. " + jsonPersistent.toString());
        }
        ImportHandler handler = this.get(klazz);
        if (handler == null) {
            throw new IllegalArgumentException("No import handler registered for class: " + klazz);
        }
        return handler.importData(sesh, importData, persistentLookup, jsonPersistent);
    }

    /**
     * Returns the class name of the encoded representation.
     *
     * @param jsonPersistent the encoded representation to be interrogated.
     * @return the class name of the encoded representation or null if it cannot be determined.
     */
    private String getClassName(JSONObject jsonPersistent) {
        return jsonPersistent.optString(PersistentImpl.FLATTEN_KEY_CLASS, null);
    }
    
    /**
     * Returns messages for the import
     * @return
     */
    public Map<String, Object[]> getMessages() {
        Map<String, Object[]> messages = new LinkedHashMap<String, Object[]>();
        for (ImportHandler handler : this.values()) {
            messages.putAll(handler.getMessages());
        }
        return messages;
    }

    /**
     * Clears any messages that have been accumulated during import.
     */
    public void clearMessages() {
        for (ImportHandler handler : this.values()) {
            handler.clearMessages();
        }
    }
}
