# Unidata ncWMS Code Repository

This ncWMS code repository is used for building the subset of [ncWMS]
used by the THREDDS Data Server ([TDS]).
It both tracks the main ncWMS subversion repository ([svn][ncWMS_repo])([browse][ncWMS_repo_browse]), or at least
the main milestones in that repository)= and any changes or additions
needed for integration in the TDS.

The THREDDS team and the ncWMS team work closely (and with the larger
ncWMS community) to ensure that changes are shared as appropriate.

## Resources

- ncWMS
  - [ncWMS project page][ncWMS] (and [Sourceforge page][ncWMS_sourceforge])
  - [ncWMS Subversion repository][ncWMS_repo]
    - [Browsing code][ncWMS_repo_browse] (and on [Trac][ncWMS_repo_browse_trac])  
- [TDS project page][TDS]

##External Libraries

PRTree is an implementation of a priority R-Tree, a spatial index.
It is available from the [PRTree web site][PRTree] as well as from the [sk89q.com
Maven repository][PRTree_mvn], though the sk89q.com maven repository is not always
up-to-date with the current version. To work around that issue, we can
grab the PRTree binary class and source jar files and upload them to
Unidata's internal [3rd Party Maven repo][Unidata-3rd-party]
(using group "org.khelekore" and artifact "prtree").

The ncWMS code requires PRTree 1.4 (as of 3 Dec 2014, SVN rev 1085).
Added support for n-D data (from 2-D only) in PRTree 1.5 would require
some work in ncWMS code.

## Updating Dependency Versions

|------------------------------------+-----+--------------+-----------+--------+-----------+-----------|
| name                               | Req | SuppliedBy   |   TDS ver |  avail | Uni ncWMS | ncWMS ver |
|------------------------------------+-----+--------------+-----------+--------+-----------+-----------|
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
|------------------------------------+-----+--------------+-----------+--------+-----------+-----------|

### Note 1 <a name="Note_1"></a>

Due to circular dependencies and the fact that edal.time is not a separate artifact:

- edal.time code has been sucked into the CDM code base, and
- edal.time is excluded from the Unidata ncWMS build

Hmmm. I think we used to build a separate edal.time library from the Uni
ncWMS repo and deploy that to the Unidata Maven repo. (Yes, it is in
buildTds.xml but evidently didn't make it over to the Maven build.)

### Note 2 <a name="Note_2"></a>
 
PRTree 1.4 is latest version compatible with ncWMS (1.4 is designed
for 2-D data, 1.5+ is for n-D data and has API changes

### Note 3 <a name="Note_3"></a>
Trove4j 2.0.4, 2.1.0, 3.0.3
https://bitbucket.org/robeden/trove/
http://trove.starlight-systems.com/

### Note 4 <a name="Note_4"></a>
SGT from NOAA/PMEL was last release (v3.0) in Sept 2003

### Note 5 <a name="Note_5"></a>
Apache Jakarta Oro no longer supported (in Apache Attic).
They suggest using newer stuff in Java libraries.

[TDS]: http://www.unidata.ucar.edu/software/thredds/current/tds

[ncWMS]:   http://www.resc.rdg.ac.uk/trac/ncWMS
[ncWMS_sourceforge]:  (on SourceForge) http://sourceforge.net/projects/ncwms/
[ncWMS_repo]:  svn://svn.code.sf.net/p/ncwms
[ncWMS_repo_browse]: http://sourceforge.net/p/ncwms/code/HEAD/tree/
[ncWMS_repo_browse_trac]: http://www.resc.rdg.ac.uk/trac/ncWMS/browser

[PRTree]: http://www.khelekore.org/prtree/
[PRTree_mvn]: http://mvn2.sk89q.com/repo/org/khelekore/prtree/1.4/

[Unidata_artifacts_repo]: https://artifacts.unidata.ucar.edu
[Unidata_snapshots]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-snapshots/
[Unidata_releases]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases/
[Unidata-3rd-party]: https://artifacts.unidata.ucar.edu/content/repositories/unidata-3rdparty/