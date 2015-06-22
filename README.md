# Grass WPS Module

## License

Module is provided under the same [GPL](LICENSE.txt) (with EPL exception) as GeoServer.

## Environment

Preconditions:

* This jar is compatible with the GeoServer 2.7 series
* This jar requires Grass 7.0 to be installed and available to the tomcat user

The module needs to know the location of the grass executable. While a sensible default has been hard coded the settings can be supplied using a global environmental variable, web.xml context parameter, or command line system property as appropriate for your deployment.

To define the location of grass as a command line system property:

1. Edit /etc/sysconfig/tomcat
2. Add a definition for GRASS
   
   ```
   # You can pass some parameters to java here if you wish to
   JAVA_OPTS="-DGRASS=/usr/bin/grass70"
   ```
   
An alternative is to edit the web.xml file as outlined in the [OpenGeoSuite 4.6.1 instructions](http://suite.opengeo.org/opengeo-docs/intro/installation/redhat/postinstall.html#intro-installation-redhat-postinstall).

1. Edit the file /usr/share/opengeo/geoserver/WEB-INF/web.xml.
2. Search for GEOSERVER_DATA_DIR section and add the following entry
   
   ```
   <context-param>
      <param-name>GRASS</param-name>
      <!-- directory where GRASS lives -->
      <param-value>/Applications/GRASS-7.0.app/Contents/MacOS/grass70</param-value>
   </context-param>
   ```
   
## Building


To build the application run maven:

```
% mvn clean install -DGRASS=/usr/bin/grass70
```

The resulting jar is located in the target folder.

## Installation

To use this module:

1. Copy the gs-grass.jar into GeoServer WEB-INF/libs folder
2. Double check environment expected 
3. Restart tomcat
   
   ```
   service tomcat restart
   ```
   
4. This module includes dummy process double check the expected grass environment:

   ![demo grass:version](/img/grass_version.png)
   
   When executed this process will return the GRASS version information.
   
   Note: You can disable access to this process, after you have confirmed a working execution enviornment, using the wps security and access control. 

## Contact

You may reach me at jgarnett@boundlessgeo.com or contact professional services.