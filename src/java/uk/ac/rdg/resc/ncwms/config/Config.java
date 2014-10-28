/*
 * Copyright (c) 2007 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package uk.ac.rdg.resc.ncwms.config;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.joda.time.DateTime;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Commit;
import org.simpleframework.xml.core.PersistenceException;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.core.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import ucar.nc2.dataset.NetcdfDataset;
import ucar.unidata.io.RandomAccessFile;
import uk.ac.rdg.resc.edal.util.Utils;
import uk.ac.rdg.resc.ncwms.controller.ServerConfig;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.security.Users;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;

/**
 * <p>
 * Configuration of the ncWMS server. We use Simple XML Serialization
 * (http://simple.sourceforge.net/) to convert to and from XML.
 * </p>
 * <p>
 * This implements {@link ServerConfig}, which is the general interface for
 * providing access to server metadata and data. (ServerConfig can be
 * implemented by other configuration systems and catalogs, e.g. THREDDS.)
 * </p>
 *
 * @author Jon Blower
 */
@Root(name = "config")
public class Config implements ServerConfig, ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    /** Reads and writes XML to/from disk. Fully thread safe. */
    private static final Serializer PERSISTER = new Persister();

    // We don't do "private List<Dataset> datasetList..." here because if we do,
    // the config file will contain "<datasets class="java.util.ArrayList>",
    // presumably because the definition doesn't clarify what sort of List should
    // be used.
    // This is a temporary store of datasets that are read from the config file.
    // The real set of all datasets is in the datasets Map.
    @ElementList(name = "datasets", type = Dataset.class)
    private ArrayList<Dataset> datasetList = new ArrayList<Dataset>();

    // Nothing happens to this at the moment... TODO for the future
    @Element(name = "threddsCatalog", required = false)
    private String threddsCatalogLocation = " "; //location of the Thredds Catalog.xml (if there is one...)

    @Element(name = "contact", required = false)
    private Contact contact = new Contact();

    @Element(name = "server")
    private Server server = new Server();

    @Element(name = "cache", required = false)
    private Cache cache = new Cache();

    // Time of the last update to this configuration or any of the contained
    // metadata
    private DateTime lastUpdateTime;

    private File configFile; // Location of the file from which this information has been read
    private File configBackup; // Location of backup for config file

    // Will be injected by Spring: handles authenticated OPeNDAP calls
    private NcwmsCredentialsProvider credentialsProvider;

    /**
     * This contains the map of dataset IDs to Dataset objects. We use a
     * LinkedHashMap so that the order of datasets in the Map is preserved.
     */
    private Map<String, Dataset> datasets = new LinkedHashMap<String, Dataset>();

    /** The scheduler that will handle the background (re)loading of datasets */
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    /**
     * Contains handles to background threads that can be used to cancel
     * reloading of datasets. Maps dataset Ids to Future objects
     */
    private Map<String, ScheduledFuture<?>> futures = new HashMap<String, ScheduledFuture<?>>();

    //############  From code added by ndp 4/6/2014 ############
    /**
     * This is a temporary store of datasets that are read from the config file.
     * The real set of all datasets is in the datasets Map.
     * 
     * @author ndp 4/6/2014
     */
    @ElementList(name = "dynamicServices", type = DynamicService.class, required = false)
    private ArrayList<DynamicService> dynamicServicesList = new ArrayList<DynamicService>();

    /**
     * This contains the map of DAP service IDs to DapService objects. We use a
     * LinkedHashMap so that the order of DAP Services in the Map is preserved.
     * 
     * @author ndp 4/6/2014
     */
    private Map<String, DynamicService> dynamicServices = new LinkedHashMap<String, DynamicService>();

    //################################################

    /**
     * Private constructor. This prevents other classes from creating new Config
     * objects directly.
     */
    private Config() {
    }

    /**
     * Reads configuration information from the given config file
     */
    public static Config readConfig(File configFile) throws Exception {
        Config config;

        if (configFile.exists()) {
            /*
             * Don't read in strict mode. That way any extraneous information
             * (e.g. from other ncWMS versions) doesn't cause problems
             */
            config = PERSISTER.read(Config.class, configFile, false);
            config.configFile = configFile;
            logger.debug("Loaded configuration from {}", configFile.getPath());
        } else {
            // We must make a new config file and save it
            config = new Config();
            config.configFile = configFile;
            config.save();
            logger.debug("Created new configuration object and saved to {}", configFile.getPath());
        }

        config.lastUpdateTime = new DateTime();

        // Initialize the cache of NetcdfDatasets.  Hold between 50 and 500
        // datasets, clearing out the cache every 5 minutes.  If the number of
        // individual files in the cache exceeds the limit, the least-recently-used
        // files will be closed in the clearout process.
        // **NOTE** the age of the files in the cache is not taken into account,
        // therefore the cache could hold on to files forever.  The only way
        // to expire out-of-date information is to set a recheckEvery parameter
        // in an NcML aggregation.  It is not possible currently to expire
        // information based on time for single NetCDF files or OPeNDAP datasets.
        // Therefore the DefaultDataReader only uses this cache for NcML aggregations.
        // TODO: move the initialization of the cache to the NcwmsController?
        NetcdfDataset.initNetcdfFileCache(50, 500, 500, 5 * 60);
        logger.debug("NetcdfDatasetCache initialized");
        if (logger.isDebugEnabled()) {
            // Allows us to see how many RAFs are in the NetcdfFileCache at
            // any one time
            RandomAccessFile.setDebugLeaks(true);
        }

        // Set up background threads to reload dataset metadata
        for (Dataset ds : config.datasets.values()) {
            ds.setConfig(config);
            config.scheduleReloading(ds);
        }

        //############  Added by ndp 4/6/2014 ############
        // Load the config into the DAP Service objects.
        // NB: We don't schedule a reload of a Dap Service because it doesn't make sense to do so...
        for (DynamicService ds : config.dynamicServices.values()) {
            ds.setConfig(config);
        }
        //################################################

        return config;
    }

    /**
     * Saves configuration information to the disk. Other classes can call this
     * method when they have altered the contents of this object.
     * 
     * @throws Exception
     *             if there was an error saving the configuration
     * @throws IllegalStateException
     *             if the config file has not previously been saved.
     */
    public synchronized void save() throws Exception {
        if (this.configFile == null) {
            throw new IllegalStateException("No location set for config file");
        }
        // Take a backup of the existing config file
        if (this.configBackup == null) {
            String backupName = this.configFile.getAbsolutePath() + ".bak";
            this.configBackup = new File(backupName);
        }
        // Copy current config file to the backup file.
        if (this.configFile.exists()) {
            // Delete existing backup
            this.configBackup.delete();
            Utils.copyFile(this.configFile, this.configBackup);
        }

        PERSISTER.write(this, this.configFile);
        logger.debug("Config information saved to {}", this.configFile.getPath());
    }

    /**
     * Checks that the data we have read are valid. Checks that there are no
     * duplicate dataset IDs or duplicate URLs for third-party layer providers.
     */
    @Validate
    public void validate() throws PersistenceException {
        List<String> dsIds = new ArrayList<String>();
        for (Dataset ds : this.datasetList) {
            String dsId = ds.getId();
            if (dsIds.contains(dsId)) {
                throw new PersistenceException("Duplicate dataset id %s", dsId);
            }
            dsIds.add(dsId);
        }

        //############  Added by ndp 4/6/2014 ############
        List<String> dapServiceIds = new ArrayList<String>();
        for (DynamicService ds : this.dynamicServicesList) {
            String dsId = ds.getId();
            if (dapServiceIds.contains(dsId)) {
                throw new PersistenceException("Duplicate DapService id %s", dsId);
            }
            dapServiceIds.add(dsId);
        }
        //################################################

    }

    /**
     * Called when we have checked that the configuration is valid. Populates
     * the datasets hashmap.
     * 
     * @todo load the datasets from the THREDDS catalog and populate the hashmap
     *       of third-party layer providers.)
     */
    @Commit
    public void build() {
        for (Dataset ds : this.datasetList) {
            this.datasets.put(ds.getId(), ds);
        }

        //############  Added by ndp 4/6/2014 ############
        for (DynamicService ds : this.dynamicServicesList) {
            this.dynamicServices.put(ds.getId(), ds);
        }
        //################################################

    }

    void setLastUpdateTime(DateTime date) {
        if (date.isAfter(this.lastUpdateTime)) {
            this.lastUpdateTime = date;
        }
    }

    /**
     * Schedules the regular reloading of the given dataset
     */
    private void scheduleReloading(final Dataset ds) {
        Runnable reloader = new Runnable() {
            @Override
            public void run() {
                // This will check to see if the metadata need reloading, then
                // go ahead if so.
                ds.loadLayers();
                // Here we're checking for leaks of open file handles
                logger.debug("num RAFs open = {}", RandomAccessFile.getOpenFiles().size());
            }
        };
        ScheduledFuture<?> future = this.scheduler.scheduleWithFixedDelay(reloader, // The reloading task to run
                0, // Schedule the first run immediately
                1, TimeUnit.SECONDS // Schedule each subsequent run 1 second after
                                    // the previous one finished.
                                    // Hence the dataset will be polled once every
                                    // second.
                );
        // We need to keep a handle to the Future object so we can cancel it
        this.futures.put(ds.getId(), future);
        logger.debug("Scheduled auto-reloading of dataset {}", ds.getId());
    }

    /**
     * @return the time at which this configuration was last updated
     */
    @Override
    public DateTime getLastUpdateTime() {
        return this.lastUpdateTime;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Cache getCache() {
        return this.cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    /**
     * Gets an unmodifiable Map of dataset IDs to Dataset objects for all
     * datasets on this server.
     */
    public Map<String, Dataset> getAllDatasets() {
        return Collections.unmodifiableMap(this.datasets);
    }

    /**
     * Returns the dataset with the given ID, or null if there is no available
     * dataset with the given id.
     */
    public Dataset getDatasetById(String datasetId) {
        Dataset ds = datasets.get(datasetId);
        if (ds == null) {
            /*
             * TODO use the dynamic datasets store instead
             */
            String dynamicAlias = null;
            for (String testDynamicAlias : getAllDynamicServices().keySet()) {
                if (datasetId.startsWith(testDynamicAlias)) {
                    dynamicAlias = testDynamicAlias;
                }
            }
            if (dynamicAlias == null) {
                return null;
            }
            DynamicService dynamicService = getDynamicServiceById(dynamicAlias);
            String datasetPath = datasetId.substring(dynamicAlias.length());

            /*
             * Check if we allow this path or if it is disallowed by the dynamic
             * dataset regex
             */
            if (!dynamicService.getIdMatchPattern().matcher(datasetPath).matches()) {
                return null;
            }

            String datasetUrl = dynamicService.getServiceUrl() + datasetPath;

            String title = datasetId;
            while (title.startsWith("/") && title.length() > 0)
                title = title.substring(1);

            Dataset dynamicDataset = new Dataset();
            dynamicDataset.setId("");
            dynamicDataset.setTitle(title);
            dynamicDataset.setConfig(this);
            dynamicDataset.setLocation(datasetUrl);
            dynamicDataset.setCopyrightStatement(dynamicService.getCopyrightStatement());
            dynamicDataset.setMoreInfo(dynamicService.getMoreInfoUrl());
            dynamicDataset.forceRefresh();
            dynamicDataset.loadLayers();

            if (!dynamicDataset.isReady()) {
                return null;
            }

            ds = dynamicDataset;
        }
        return ds;
    }

    public synchronized void addDataset(Dataset ds) {
        ds.setConfig(this);
        this.datasetList.add(ds);
        this.datasets.put(ds.getId(), ds);
        this.scheduleReloading(ds);
    }

    public synchronized void removeDataset(Dataset ds) {
        this.datasetList.remove(ds);
        this.datasets.remove(ds.getId());
        // Cancel the auto-reloading of this dataset
        ScheduledFuture<?> future = this.futures.remove(ds.getId());
        // We allow the reloading task to be interrupted
        if (future != null)
            future.cancel(true);
    }

    public synchronized void changeDatasetId(Dataset ds, String newId) {
        String oldId = ds.getId();
        this.datasets.remove(oldId);
        ScheduledFuture<?> future = this.futures.remove(oldId);
        ds.setId(newId);
        this.datasets.put(newId, ds);
        this.futures.put(newId, future);
        logger.debug("Changed dataset with ID {} to {}", oldId, newId);
    }

    //##################################################################################################################
    //##################################################################################################################
    //##################################################################################################################
    //
    // The following adds configuration support for the DAP Service backed ncWMS.
    //
    // Based on code added by ndp 4/6/2014
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Adds a DAP Service to the configuration.
     *
     * @param ds
     *            The DapService to add to the configuration.
     * @author ndp 4/6/2014
     */
    public synchronized void addDynamicService(DynamicService ds) {
        this.dynamicServicesList.add(ds);
        this.dynamicServices.put(ds.getId(), ds);

        // NB: We don't pre-load a dap service because that's crazy talk.
        // this.scheduleReloading(ds);
    }

    /**
     * Returns the DapService with the given ID, or null if there is no
     * available dataset with the given id.
     * 
     * @param serviceId
     *            The ID of the DAP Service to retrieve.
     * @author ndp 4/6/2014
     */
    public DynamicService getDynamicServiceById(String serviceId) {
        return this.dynamicServices.get(serviceId);
    }

    /**
     * Change the id of the DynamicService.
     *
     * @param ds
     *            The {@link DynamicService} whose ID is to be changed.
     * @param newId
     *            The new ID for the supplied DynamicService.
     * @author ndp 4/6/2014
     */
    public synchronized void changeDynamicServiceId(DynamicService ds, String newId) {

        String oldId = ds.getId();
        try {
            ds.setId(newId);
        } catch (WmsException e) {
            logger.error("Invalid DAP Service ID: {}  Ignoring!", newId);
            return;
        }
        this.dynamicServices.remove(oldId);
        this.dynamicServices.put(newId, ds);

        logger.debug("Changed dataset with ID {} to {}", oldId, newId);
    }

    /**
     * Removes the {@link DynamicService} from the configuration.
     *
     * @param ds
     *            The {@link DynamicService} to remove from the config.
     * @author ndp 4/6/2014
     */
    public synchronized void removeDynamicService(DynamicService ds) {
        this.dynamicServicesList.remove(ds);
        this.dynamicServices.remove(ds.getId());

        // NB: We don't cancel auto-reloading of a dap service because they are never auto-loaded in the first place

    }

    /**
     * Gets an unmodifiable Map of DapService IDs to Dataset objects for all
     * datasets on this server.
     * 
     * @author ndp 4/6/2014
     */
    public Map<String, DynamicService> getAllDynamicServices() {
        return Collections.unmodifiableMap(this.dynamicServices);
    }

    //##################################################################################################################
    //##################################################################################################################
    //##################################################################################################################
    //##################################################################################################################

    /**
     * If s is whitespace-only or empty, returns a space, otherwise returns s.
     * This is to work around problems with the Simple XML software, which
     * throws an Exception if it tries to read an empty field from an XML file.
     */
    static String checkEmpty(String s) {
        if (s == null)
            return " ";
        s = s.trim();
        return s.equals("") ? " " : s;
    }

    /**
     * If the given dataset is an OPeNDAP location, this looks for a username
     * and password and, if it finds one, updates the credentials provider. This
     * is called whenever a dataset's metadata is being loaded (i.e. by
     * {@link Dataset#loadLayers()}. We must keep checking the dataset location
     * in case it has been changed by the admin app.
     */
    void updateCredentialsProvider(Dataset ds) {
        logger.debug("Called updateCredentialsProvider, {}", ds.getLocation());
        if (WmsUtils.isOpendapLocation(ds.getLocation())) {
            // Make sure the URL starts with "http://" or the
            // URL parsing might not work
            // (TODO: register dods:// as a valid protocol?)
            String newLoc = "http" + ds.getLocation().substring(4);
            try {
                URL url = new URL(newLoc);
                String userInfo = url.getUserInfo();
                logger.debug("user info = {}", userInfo);
                if (userInfo != null) {
                    this.credentialsProvider.setCredentials(
                            new AuthScope(url.getHost(), url.getPort()),
                            new UsernamePasswordCredentials(userInfo));
                }
                // Change the location to "dods://..." so that the Java NetCDF
                // library knows to use the OPeNDAP protocol rather than plain
                // http
                ds.setLocation("dods" + newLoc.substring(4));
            } catch (MalformedURLException mue) {
                logger.warn(newLoc + " is not a valid url");
            }
        }
    }

    /**
     * Called by the Spring framework to clean up this object. Closes all
     * background threads.
     */
    public void shutdown() {
        this.scheduler.shutdownNow(); // Tries its best to stop ongoing threads
        NetcdfDataset.shutdown();
        logger.info("Cleaned up Config object");
    }

    @Override
    public String getTitle() {
        return this.server.getTitle();
    }

    @Override
    public String getServerAbstract() {
        return this.server.getServerAbstract();
    }

    @Override
    public int getMaxImageWidth() {
        return this.server.getMaxImageWidth();
    }

    @Override
    public int getMaxImageHeight() {
        return this.server.getMaxImageHeight();
    }

    @Override
    public Set<String> getKeywords() {
        String[] keysArray = this.server.getKeywords().split(",");
        // preserves iteration order
        Set<String> keywords = new LinkedHashSet<String>(keysArray.length);
        for (String keyword : keysArray) {
            keywords.add(keyword);
        }
        return keywords;
    }

    public boolean getAllowsGlobalCapabilities() {
        return this.server.isAllowGlobalCapabilities();
    }

    @Override
    public String getServiceProviderUrl() {
        return this.server.getUrl();
    }

    @Override
    public String getContactName() {
        return this.contact.getName();
    }

    @Override
    public String getContactEmail() {
        return this.contact.getEmail();
    }

    @Override
    public String getContactOrganization() {
        return this.contact.getOrg();
    }

    @Override
    public String getContactTelephone() {
        return this.contact.getTel();
    }

    /** Not used. */
    public String getThreddsCatalogLocation() {
        return this.threddsCatalogLocation;
    }

    /** Not used. */
    public void setThreddsCatalogLocation(String threddsCatalogLocation) {
        this.threddsCatalogLocation = checkEmpty(threddsCatalogLocation);
    }

    /** Called by Spring to set the credentials provider */
    public void setCredentialsProvider(NcwmsCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Called automatically by Spring. When we have the application context we
     * can set the admin password in the Users object that is used by Acegi.
     * This is called after the Config object has been created.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Set the admin password in the Users bean, which we'll need to
        // get from the app context
        Users users = (Users) applicationContext.getBean("users");
        if (users == null) {
            logger.error("Could not retrieve Users object from application context");
        } else {
            logger.debug("Setting admin password in Users object");
            users.setAdminPassword(this.server.getAdminPassword());
        }
    }

    @Override
    public File getPaletteFilesLocation(ServletContext context) {
        return new File(context.getRealPath("/WEB-INF/conf/palettes"));
    }

}
