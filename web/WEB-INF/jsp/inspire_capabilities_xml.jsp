<%@include file="xml_header.jsp"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="/WEB-INF/taglib/wms/wmsUtils" prefix="utils"%> <%-- tag library for useful utility functions --%>
<%
response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
response.setHeader("Pragma","no-cache"); //HTTP 1.0
response.setDateHeader ("Expires", 0); //prevents caching at the proxy server
%>
<%-- Displays the Capabilities document in XML.
     Data (models) passed in to this page:
         config     = Configuration of this server (uk.ac.rdg.resc.ncwms.wms.ServerConfig)
         datasets   = collection of datasets to display in this Capabilities document (Collection<uk.ac.rdg.resc.ncwms.wms.Dataset>)
         lastUpdate = Last update time of the dataset(s) displayed in this document (org.joda.time.DateTime)
         wmsBaseUrl = Base URL of this server (java.lang.String)
         supportedCrsCodes = List of Strings of supported Coordinate Reference System codes
         supportedImageFormats = Set of Strings representing MIME types of supported image formats
         layerLimit = Maximum number of layers that can be requested simultaneously from this server (int)
         featureInfoFormats = Array of Strings representing MIME types of supported feature info formats
         legendWidth, legendHeight = size of the legend that will be returned from GetLegendGraphic
         paletteNames = Names of colour palettes that are supported by this server (Set<String>)
         verboseTime = boolean flag to indicate whether we should use a verbose or concise version of the TIME value string
     --%>
<WMS_Capabilities
        version="1.3.0"
        updateSequence="${utils:dateTimeToISO8601(lastUpdate)}"
        xmlns="http://www.opengis.net/wms"
        xmlns:inspire_vs="http://inspire.ec.europa.eu/schemas/inspire_vs/1.0"
        xmlns:inspire_common="http://inspire.ec.europa.eu/schemas/common/1.0"
        xmlns:xlink="http://www.w3.org/1999/xlink"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd">
        
    <Service>
        <Name>WMS</Name>
        <Title><c:out value="${config.title}"/></Title>
        <Abstract><c:out value="${config.serverAbstract}"/></Abstract>
        <KeywordList>
            <%-- forEach recognizes that keywords is a comma-delimited String --%>
            <c:forEach var="keyword" items="${config.keywords}">
            <Keyword>${keyword}</Keyword>
            </c:forEach>
            <Keyword>humanGeographicViewer</Keyword>
        </KeywordList>
        <OnlineResource xlink:type="simple" xlink:href="<c:out value="${config.serviceProviderUrl}"/>"/>
        <ContactInformation>
            <ContactPersonPrimary>
                <ContactPerson><c:out value="${config.contactName}"/></ContactPerson>
                <ContactOrganization><c:out value="${config.contactOrganization}"/></ContactOrganization>
            </ContactPersonPrimary>
            <ContactVoiceTelephone><c:out value="${config.contactTelephone}"/></ContactVoiceTelephone>
            <ContactElectronicMailAddress><c:out value="${config.contactEmail}"/></ContactElectronicMailAddress>
        </ContactInformation>
        <Fees>##FEES##</Fees>
        <AccessConstraints>none</AccessConstraints>
        <LayerLimit>${layerLimit}</LayerLimit>
        <MaxWidth>${config.maxImageWidth}</MaxWidth>
        <MaxHeight>${config.maxImageHeight}</MaxHeight>
    </Service>
    <Capability>
        <Request>
            <GetCapabilities>
                <Format>text/xml</Format>
                <DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="<c:out value="${wmsBaseUrl}"/>"/></Get></HTTP></DCPType>
            </GetCapabilities>
            <GetMap>
                <c:forEach var="mimeType" items="${supportedImageFormats}">
                <Format>${mimeType}</Format>
                </c:forEach>
                <DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="<c:out value="${wmsBaseUrl}"/>"/></Get></HTTP></DCPType>
            </GetMap>
            <GetFeatureInfo>
                <c:forEach var="mimeType" items="${featureInfoFormats}">
                <Format>${mimeType}</Format>
                </c:forEach>
                <DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="<c:out value="${wmsBaseUrl}"/>"/></Get></HTTP></DCPType>
            </GetFeatureInfo>
        </Request>
        <Exception>
            <Format>XML</Format>
        </Exception>
        <Layer>
            <Title><c:out value="${config.title}"/></Title><%-- Use of c:out escapes XML --%>
            <c:forEach var="crsCode" items="${supportedCrsCodes}">
            <CRS>${crsCode}</CRS>
            </c:forEach>
            <CRS>EPSG:4258</CRS>
            <c:forEach var="dataset" items="${datasets}">
            <c:if test="${dataset.ready}">
                <Layer>
                    <Title><c:out value="${dataset.title}"/></Title>
                        <c:if test="${not empty dataset.metadataUrl}">
					        <MetadataURL type="unstructured">
					            <c:if test="${not empty dataset.metadataDesc}">
					                <Description>${dataset.metadataDesc}</Description>
					            </c:if>
					            <c:if test="${not empty dataset.metadataMimetype}">
					                <Format>${dataset.metadataMimetype}</Format>
					            </c:if>
					            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="${dataset.metadataUrl}" />
					        </MetadataURL>
					    </c:if>
                    <c:forEach var="layer" items="${dataset.layerTree}">
                        <utils:capLayer layer="${layer}"/>
                    </c:forEach>
                </Layer>
            </c:if> <%-- End if dataset is ready --%>
            </c:forEach> <%-- End loop through datasets --%>
        </Layer>
    </Capability>
	<inspire_vs:ExtendedCapabilities>
		<inspire_common:ResourceType>service</inspire_common:ResourceType>
        <inspire_common:ResourceLocator>
            <inspire_common:URL>
                <c:out value="${wmsBaseUrl}"/>?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0
            </inspire_common:URL>        
        </inspire_common:ResourceLocator>
		<inspire_common:SpatialDataServiceType>view</inspire_common:SpatialDataServiceType>
		<inspire_common:TemporalReference>
			<inspire_common:DateOfLastRevision>##LAST REVISION DATE##</inspire_common:DateOfLastRevision>
		</inspire_common:TemporalReference>
		<inspire_common:Conformity>
			<inspire_common:Degree>notEvaluated</inspire_common:Degree>
		</inspire_common:Conformity>
		<inspire_common:MetadataURL>
            <inspire_common:URL>
                ##METADATA URL##
            </inspire_common:URL>        
		</inspire_common:MetadataURL>
		<inspire_common:MetadataPointOfContact>
			<inspire_common:OrganisationName>##ORGANISATION NAME##</inspire_common:OrganisationName>
			<inspire_common:EmailAddress>##CONTACT EMAIL ADDRESS##</inspire_common:EmailAddress>
		</inspire_common:MetadataPointOfContact>
		<inspire_common:MetadataDate>##METADATA LAST REVISION DATE##</inspire_common:MetadataDate>
        <c:forEach var="keyword" items="${config.keywords}">
            <inspire_common:Keyword xsi:type="inspire_common:classificationOfSpatialDataService">
				<inspire_common:KeywordValue>
	               ${keyword}
				</inspire_common:KeywordValue>
            </inspire_common:Keyword>
        </c:forEach>
        <inspire_common:Keyword xsi:type="inspire_common:classificationOfSpatialDataService">
            <inspire_common:KeywordValue>
                Oceanographic geographical features
            </inspire_common:KeywordValue>
        </inspire_common:Keyword>
		<inspire_common:SupportedLanguages>
			<inspire_common:DefaultLanguage>
				<inspire_common:Language>eng</inspire_common:Language>
			</inspire_common:DefaultLanguage>
		</inspire_common:SupportedLanguages>
		<inspire_common:ResponseLanguage>
			<inspire_common:Language>eng</inspire_common:Language>
		</inspire_common:ResponseLanguage>
	</inspire_vs:ExtendedCapabilities>

</WMS_Capabilities>