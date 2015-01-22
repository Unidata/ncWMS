# Unidata ncWMS Notes

**upgrade_for_TDS_4.5.4**


## Updating for TDS 4.5.4

### Merging latest ncWMS 1.x changes from ncWMS SVN repository

Changes from latest ncWMS 1.2+ merged into Unidata/ncWMS code:

- Added support for Proleptic Gregorian calendar (rev [1020](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1020) and [1022](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1022))
- Changes to how color palettes are supported (rev [1069](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1069))
- "Prevent XSS attacks" (rev [1027](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1027))
- "Fix 0001 date bug" (rev [994](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/994))
- Fix timeseries plot problem (rev [990](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/990))
- Hide animation creation link when animation playing (rev [1053](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1053))
- "Fixed animation/Google Earth export when the view crosses the dateline" (rev [1047](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1047))
- "Added months and years to possible time units. Special case for 360 day calendar. [And lots of reformatting that I ignored.]" (rev [1078](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1078))
- "Bugfix for screenshots from a remote server" (rev [1000](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1000))
- GoogleEarth KMZ changes in AbstractWmsController.java (sometime between revs [1010](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1010) and [1052](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1052))
- Improve error msgs if color palette directory not found (try/catch added) (rev [1052](http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1052))

Changes not merged from ncWMS SVN:

- Handle dynamic datasets to ncWMS (rev [[http://www.resc.rdg.ac.uk/trac/ncWMS/changeset/1073][1073]])

Pull requests and such still not merged:

- [PR #1](https://github.com/Unidata/ncWMS/pull/1):
  Patch for scaling vectors depending on the magnitude.
- [PR #4](https://github.com/Unidata/ncWMS/pull/4):
  Corrected handling of vector components for grib files.
-

Changes in Unidata/ncWMS for possible propogation to UofReading ncWMS

- Changes from
- ???Support for Interval time strings (replacing list of times) in get capabilities doc.
  - web/WEB-INF/taglib/wms/wmsUtils.tld

- Switched from getName() to getFullName() and getShortName()
  - src/java/.../edal/cdm/CdmCoverageMetadata.java
  - src/java/.../edal/cdm/CdmUtils.java
  - src/java/.../edal/cdm/DataChunk.java
  - src/java/.../edal/cdm/RangesList.java
- Switch from straight joda-time to ucar.nc2.time.CalendarDate
  - src/java/.../edal/cdm/CdmUtils.java
- Switch to using ucar.nc2.util.net.HTTPSession
  - src/java/.../ncwms/config/NcwmsCredentialsProvider.java (NOT USED BY TDS)
    - Commit 767cd97 on 4 Dec 2013
- Added additionl vector styles (originally from Kyle?)
  - src/java/.../ncwms/controller/AbstractWmsController.java
  - src/java/.../ncwms/graphics/ImageProducer.java
  - web/WEB-INF/jsp/capabilities_xml.jsp
  - web/WEB-INF/jsp/capabilities_1_1_1_xml.jsp
- Fix for direction of vectors (in southern hemisphere?)
  - ???
- Add support for GetCapabilities document listing time intervals instead of just lists of times
  - ??? Layer.java
  - ???

### Updating Versions of External Libraries to work with TDS 4.5.4

| name                               | Req | SuppliedBy   |   TDS ver |  avail | Uni ncWMS | ncWMS ver |
|:-------------------------------|-----:|--------------:|-----------:|--------:|-----------:|-----------:|
| edu.ucar:cdm                       | Y   |              |     4.5.4 |        |     4.5.4 |     4.5.3 |
| - edal.time               [Note 1](#Note_1) |     |              |   CDM ver | ?same? |   CDM ver | ncWMS ver |
| org.geotoolkit:geotk-referencing   | Y   | TDS was 3.21 | Uni ncWMS |   3.21 |      3.21 |      3.17 |
| net.sourceforge.jsi:jsi            | Y   |              | Uni ncWMS |  1.0b8 |     1.0b8 |     1.0b6 |
| org.khelekore:prtree      [Note 2](#Note_2) | Y   |              | Uni ncWMS |    1.7 |       1.4 |       1.4 |
| net.sf.trove4j:trove4j    [Note 3](#Note_3) | Y   |              | Uni ncWMS |  3.0.3 |     2.1.0 |     2.0.2 |
| gov.noaa.pmel:SGT_toolkit [Note 4](#Note_4) | Y   |              | Uni ncWMS |      - |       3.0 |       3.0 |
|                                    |     |              |           |        |           |           |
| joda-time:joda-time                |     |              |       2.2 |    2.6 |       2.2 |       2.2 |
|                                    |     |              |           |        |           |           |
| org.jfree:jcommon                  | S   | TDS          |   1.0.17+ | 1.0.23 |    1.0.23 |    1.0.16 |
| org.jfree:jfreechart               | S   | TDS          |   1.0.14+ | 1.0.19 |    1.0.19 |    1.0.13 |
|                                    |     |              |           |        |           |           |
| oro:oro                   [Note 5](#Note_5) |     |              |     2.0.8 |      - |     2.0.8 |     2.0.8 |
|                                    |     |              |           |        |           |           |
| org.slf4j:slf4j-api                | S   | TDS          |    1.7.5+ |  1.7.7 |     1.7.7 |     1.5.6 |
|                                    |     |              |           |        |           |           |
| org.springframework:spring-core    |     |              |    3.2.3+ |        |         - |         - |
| org.springframework:spring-context |     |              |    3.2.3+ |        |         - |         - |
| org.springframework:spring-beans   |     |              |    3.2.3+ |        |         - |         - |
| org.springframework:spring-webmvc  |     |              |    3.2.3+ | 3.2.12 |    3.2.12 |       2.5 |
| org.springframework:security       |     |              |    3.1.3+ |  3.2.5 |         - |         - |
|                                    |     |              |           |        |           |           |
| javax.servlet:javax.servlet-api    | S   |              |    3.0.1+ |  3.1.0 |     3.1.0 |       2.4 |
| javax.servlet:jstl                 | S   |              |       1.2 |      - |       1.2 |       1.2 |
| json-taglib:json-taglib            | S   |              |     0.4.1 |      - |     0.4.1 |     0.4.1 |

####  <a name="Note_1"></a>Note 1

Due to circular dependencies and the fact that edal.time is not a separate artifact:

- edal.time code has been sucked into the CDM code base, and
- edal.time is excluded from the Unidata ncWMS build

Hmmm. I think we used to build a separate edal.time library from the Uni
ncWMS repo and deploy that to the Unidata Maven repo. (Yes, it is in
buildTds.xml but evidently didn't make it over to the Maven build.)

#### <a name="Note_2"></a> Note 2
 
PRTree is an implementation of a priority R-Tree, a spatial index.
It is available from the [PRTree web site][PRTree] as well as from the [sk89q.com
Maven repository][PRTree_mvn], though the sk89q.com maven repository is not always
up-to-date with the current version. To work around that issue, we can
grab the PRTree binary class and source jar files and upload them to
Unidata's internal [3rd Party Maven repo][Unidata-3rd-party]
(using group "org.khelekore" and artifact "prtree").

PRTree 1.4 is latest version compatible with ncWMS. PRTree 1.4 is designed
for 2-D data, 1.5+ adds support for n-D data and has API changes. Use of
PRTree 1.5 would require some changes to the ncWMS code.

#### <a name="Note_3"></a> Note 3
Trove4j 2.0.4, 2.1.0, 3.0.3
https://bitbucket.org/robeden/trove/
http://trove.starlight-systems.com/

#### <a name="Note_4"></a> Note 4
SGT from NOAA/PMEL was last release (v3.0) in Sept 2003

#### <a name="Note_5"></a> Note 5
Apache Jakarta Oro no longer supported (in Apache Attic).
They suggest using newer stuff in Java libraries.

[TDS]: http://www.unidata.ucar.edu/software/thredds/current/tds

[ncWMS]:   http://www.resc.rdg.ac.uk/trac/ncWMS
[ncWMS_sourceforge]: http://sourceforge.net/projects/ncwms/
[ncWMS_repo_browse]: http://sourceforge.net/p/ncwms/code/HEAD/tree/
[ncWMS_repo_browse_trac]: http://www.resc.rdg.ac.uk/trac/ncWMS/browser

[PRTree]: http://www.khelekore.org/prtree/
[PRTree_mvn]: http://mvn2.sk89q.com/repo/org/khelekore/prtree/1.4/

[Unidata_artifacts_repo]: https://artifacts.unidata.ucar.edu
[Unidata_snapshots]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-snapshots/
[Unidata_releases]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases/
[Unidata-3rd-party]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-3rdparty/
