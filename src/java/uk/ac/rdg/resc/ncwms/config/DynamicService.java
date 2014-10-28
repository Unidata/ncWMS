package uk.ac.rdg.resc.ncwms.config;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

import java.util.regex.Pattern;

/**
 * A DapService object in the ncWMS configuration system: This object links a DAP server (local or remote) to
 * the ncWMS system. Once a DapService is added to the configuration ncWMS will attempt to service any
 * incoming WMS request whose URL resolves to the ID of the service plus a matching (via regex) dataset ID, using the
 * dataset (on the DAP server) as the source of (meta)data=.
 *
 * @author Nathan D Potter
 */
@Root(name="dapService")
public class DynamicService  {



    @Attribute(name="id")
    private String _id; // Unique ID for this dataset


    @Attribute(name="serviceUrl")
    private String _serviceUrl; // A URL representing the entry point for a DAP service


    @Attribute(name="datasetIdMatch")
    private String _datasetIdMatch; // A regular expression used to match requested ID's with ID's on the DAP service

    @Attribute(name="dataReaderClass", required=false)
    private String _dataReaderClass = ""; // We'll use a default data reader
                                         // unless this is overridden in the config file

    @Attribute(name="copyrightStatement", required=false)
    private String _copyrightStatement = "";

    @Attribute(name="moreInfoUrl", required=false)
    private String _moreInfoUrl = "";

    @Attribute(name="disabled", required=false)
    private boolean _disabled = false; // Set true to disable the dataset without removing it completely

    private Config _config;

    private Pattern _idMatchPattern;

    public Config getConfig()
    {
        return _config;
    }

    public void setConfig(Config config)
    {
        this._config = config;
    }



    public String getId()
    {
        return _id;
    }

    public void setId(String id) throws WmsException {
        String myid = id.trim();
        while(myid.startsWith("/") && myid.length()>0)
            myid = myid.substring(1);

        if(myid.length()==0 || myid.contains("."))
            throw new WmsException("Invalid DAP Service ID: " + id);

        this._id = myid;
    }


    public String getServiceUrl()
    {
        return _serviceUrl;
    }

    public void setServiceUrl(String serviceUrl)
    {
        this._serviceUrl = serviceUrl.trim();
    }

    public String getDatasetIdMatch()
     {
         return _datasetIdMatch;
     }

    public void setDatasetIdMatch(String datasetIdMatch)
    {
        this._datasetIdMatch = datasetIdMatch.trim();

        _idMatchPattern =  Pattern.compile(datasetIdMatch);

    }

    public Pattern getIdMatchPattern()
    {
        if(_idMatchPattern==null)
            _idMatchPattern =  Pattern.compile(_datasetIdMatch);

        return _idMatchPattern;
    }



    public String getDataReaderClass(){
        return _dataReaderClass;
    }
    public void setDataReaderClass(String dataReaderClass)
    {
        this._dataReaderClass = dataReaderClass.trim();
    }



    public String getCopyrightStatement(){
        return _copyrightStatement;
    }
    public void setCopyrightStatement(String copyrightStatement)
    {
        this._copyrightStatement = copyrightStatement.trim();
    }


    public String getMoreInfoUrl(){
        return _moreInfoUrl;
    }
    public void setMoreInfoUrl(String moreInfoUrl)
    {
        this._moreInfoUrl = moreInfoUrl.trim();
    }


    public boolean isDisabled(){
        return _disabled;
    }
    public boolean getDisabled(){
        return _disabled;
    }
    public void setDisabled(boolean disabled)
    {
        this._disabled = disabled;
    }



}
