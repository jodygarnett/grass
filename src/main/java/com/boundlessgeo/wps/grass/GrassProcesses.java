/* Copyright (c) 2013 - 2014 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.wps.grass;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import static java.io.File.pathSeparator;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.FileUtils;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.factory.StaticMethodsProcessFactory;
import org.geotools.referencing.CRS;
import org.geotools.text.Text;
import org.geotools.util.KVP;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class GrassProcesses extends StaticMethodsProcessFactory<GrassProcesses> {
	enum Env { LINUX, MAC, WINDOWS, UNKNOWN }
	
	private static final Logger LOGGER = Logging.getLogger("org.geoserver.wps.grass");
	static String EXEC;
	static String BIN;
	final static Env SYSTEM;
	private static final SecureRandom random = new SecureRandom();
	
	static {
		String system = System.getProperty("os.name").toLowerCase();
		if (system.contains("nix") || system.contains("nux") || system.contains("aix")) {
			SYSTEM = Env.LINUX;
		}
		else if (system.contains("mac")) {
			SYSTEM = Env.MAC;
		}
		else if (system.contains("win")) {
			SYSTEM = Env.WINDOWS;
		}
		else {
			SYSTEM = Env.UNKNOWN;
		}
		
	}
	
	public GrassProcesses() {
		super(Text.text("Geographic Resources Analysis Support System"),
				"grass", GrassProcesses.class);
		
		String grass = GeoServerExtensions.getProperty("GRASS");
		String grass_mod = GeoServerExtensions.getProperty("GRASS_MODULES");
		if (grass != null) {
			LOGGER.info("defined GRASS="+grass);
			EXEC = grass;
		} else if (SYSTEM == Env.LINUX){
			EXEC = "/usr/local/bin/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if (SYSTEM == Env.MAC){
			EXEC = "/Applications/GRASS-7.0.app/Contents/MacOS/grass70";
			LOGGER.info("default GRASS="+EXEC);
		} else if(SYSTEM == Env.WINDOWS){
		        if (new File("C:\\Program Files (x86)").exists()) {
                            EXEC = "C:\\Program Files (x86)\\GRASS GIS 7.0.0\\grass70.bat";
                        } else {
                            EXEC = "C:\\Program Files\\GRASS GIS 7.0.0\\grass70.bat";
                        }
			LOGGER.info("default GRASS="+EXEC);
		} else {
			LOGGER.warning(
				"GRASS default executable unavailable for '"+System.getProperty("os.name")+
				"'. Please use GRASS environmental variable, context parameter or system property"
			);
			EXEC = null;
		}
		if (grass_mod != null) {
                        LOGGER.info("defined GRASS_MODULES="+grass_mod);
                        BIN = grass_mod;
                } else if (SYSTEM == Env.LINUX){
                        BIN = "/usr/lib/grass70/bin";
                        LOGGER.info("default GRASS_MODULES="+BIN);
                } else if (SYSTEM == Env.MAC){
                        BIN = "/Applications/GRASS-7.0.app/Contents/MacOS/bin";
                        LOGGER.info("default GRASS_MODULES="+BIN);
                } else if(SYSTEM == Env.WINDOWS){
                        if (new File("C:\\Program Files (x86)").exists()) {
                            BIN = "C:\\Program Files (x86)\\GRASS GIS 7.0.0\\bin";
                        } else {
                            BIN = "C:\\Program Files\\GRASS GIS 7.0.0\\bin";
                        }
                        
                        LOGGER.info("default GRASS_MODULES="+BIN);
                } else {
                        LOGGER.warning(
                                "GRASS modules unavailable for '"+System.getProperty("os.name")+
                                "'. Please use GRASS_MODULES environmental variable, context parameter or system property"
                        );
                        BIN = null;
                }
		if( EXEC != null ){
			File exec = new File(EXEC);
			if( !exec.exists()){
				LOGGER.warning(EXEC+" does not exist");
				EXEC = null;
			}
			if( !exec.canExecute()){
				LOGGER.warning(EXEC+" not executable");
				EXEC = null;
			}
		}
		if( BIN != null ){
                    File exec = new File(BIN);
                    if( !exec.exists()){
                            LOGGER.warning(BIN+" does not exist");
                            BIN = null;
                    }
		}
	}
	
	@Override
	public Set<Name> getNames() {
		if( EXEC == null ){
			// do not advertise grass processes if executable is not available
			return Collections.emptySet();
		}
		return super.getNames();
	}

	
	@DescribeProcess(title = "GRASS Version", description = "Retreive the version of GRASS used for computation")
	@DescribeResult(description = "Version")
	public static String version() {
		if (EXEC == null ){
			return "unavailable";
		}
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-v");
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);	
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			executor.setStreamHandler(new PumpStreamHandler(outputStream));
			
			LOGGER.info("exec: "+cmd.toString());
			int exitValue = executor.execute(cmd);
			return outputStream.toString();
		} catch (ExecuteException huh){
			return "exit code: "+huh.getExitValue()+ " ("+huh.getMessage()+")";
		} catch (IOException e) {
			return "unavailable: "+e.getClass().getSimpleName()+":"+e.getMessage();
		}
	}
	
	@DescribeProcess(title = "r.viewshed", description = "Computes the viewshed of a point on an elevation raster map.")
	@DescribeResult(description="area visible from provided location")
	public static GridCoverage2D viewshed(
			@DescribeParameter(name = "dem", description = "digitial elevation model")
			GridCoverage2D dem,
            @DescribeParameter(name = "x", description = "x location in map units")
			double x,
            @DescribeParameter(name = "y", description = "y location in map units")
			double y) throws Exception{
		
		String COMMAND = "viewshed";
		File geodb = new File(System.getProperty("user.home"),"grassdata");		
		File location = new File( geodb, COMMAND + Long.toString(random.nextLong()) + "location");
		//File location = new File( geodb, COMMAND );
		
		// stage dem file
		File file = new File( geodb, "dem.tif");		
		//The file must exist for FileImageOutputStreamExtImplSpi to create the output stream
		if (!file.exists()) {
		    file.getParentFile().mkdirs();
		    file.createNewFile();
		}
		final GeoTiffFormat format = new GeoTiffFormat();
		GridCoverageWriter writer = format.getWriter(file);
		writer.write(dem, null);
		LOGGER.info("Staging file:"+file);
		
		// use file to create location with (returns PERMANENT mapset) 
		File mapset = location( location, file );
				
		DefaultExecutor executor = new DefaultExecutor();
		executor.setWatchdog(new ExecuteWatchdog(60000));		
		executor.setStreamHandler(new PumpStreamHandler(System.out));		
		executor.setWorkingDirectory(mapset);
		
		Map<String,String> env = customEnv( geodb, location, mapset );

		// EXPORT IMPORT DEM
		// r.in.gdal input=~/grassdata/viewshed/PERMANENT/dem.tif output=dem --overwrite

		File r_in_gdal = bin("r.in.gdal");
		CommandLine cmd = new CommandLine( r_in_gdal );
		cmd.addArgument("input=${file}");
		cmd.addArgument("output=dem");
		cmd.addArgument("--overwrite");		
		cmd.setSubstitutionMap(new KVP("file",file));						
		try {
			LOGGER.info(cmd.toString());
			executor.setExitValue(0);		
			int exitValue = executor.execute(cmd,env);
		}
		catch( ExecuteException fail ){
			LOGGER.warning(r_in_gdal.getName()+":"+fail.getLocalizedMessage());
			throw fail;
		}
		
		// EXECUTE VIEWSHED
		File r_viewshed = bin("r.viewshed");
		cmd = new CommandLine( r_viewshed );
		cmd.addArgument("input=dem");
		cmd.addArgument("output=viewshed");
		cmd.addArgument("coordinates=${x},${y}");	
		cmd.addArgument("--overwrite");
		cmd.setSubstitutionMap(new KVP("x",x,"y",y));
		
		try {
			LOGGER.info(cmd.toString());
			executor.setExitValue(0);		
			int exitValue = executor.execute(cmd,env);
		}
		catch( ExecuteException fail ){
			LOGGER.warning(r_viewshed.getName()+":"+fail.getLocalizedMessage());
			throw fail;
		}
		
		// EXECUTE EXPORT VIEWSHED
		// r.out.gdal --overwrite input=viewshed@PERMANENT output=/Users/jody/grassdata/viewshed/viewshed.tif format=GTiff
		File viewshed = new File( location, "viewshed.tif");

		File r_out_gdal = bin("r.out.gdal");
		cmd = new CommandLine( r_out_gdal );
		cmd.addArgument("input=viewshed");
		cmd.addArgument("output=${viewshed}");
		cmd.addArgument("--overwrite");
		cmd.addArgument("format=GTiff");		
		cmd.setSubstitutionMap(new KVP("viewshed", viewshed));
		
		try {
			LOGGER.info(cmd.toString());
			executor.setExitValue(0);
			int exitValue = executor.execute(cmd,env);
		}
		catch( ExecuteException fail ){
			LOGGER.warning(r_out_gdal.getName()+":"+fail.getLocalizedMessage());
			throw fail;
		}
		
		// STAGE RESULT
		
		if( !viewshed.exists() ){
			throw new IOException("Generated viweshed.tif not found");
		}		
		GeoTiffReader reader = format.getReader( viewshed );
		GridCoverage2D coverage = reader.read(null);		
		cleanup( new File(env.get("GISRC")));
		return coverage;
	}

	private static void cleanup(File ... files) {
		for( File file : files ){
			FileUtils.deleteQuietly(file);
		}	
	}

	private static File bin(String command) {
	        File exec;
	        if (SYSTEM == Env.WINDOWS) {
	            exec = new File(new File(BIN),command+".bat");
	            if (!exec.exists()) {
	                exec = new File(new File(BIN),command+".exe");
	            }
	        } else {
	            exec = new File(new File(BIN),command);
	        }
		
		if( !exec.exists()){
			throw new IllegalStateException(command+" not found:"+exec);
		}
		if( !exec.canExecute()){
			throw new IllegalStateException(command+" not executable:"+exec);
		}
		return exec;
	}
	/**
	 * Define environment variable for independent grass operation.
	 * see: http://grasswiki.osgeo.org/wiki/GRASS_and_Shell		
	 * @param geodb
	 * @param location
	 * @param mapset
	 * @return
	 * @throws IOException
	 */		
	private static Map<String, String> customEnv(File geodb, File location, File mapset) throws IOException {	
		Map<String, String> env = EnvironmentUtils.getProcEnvironment();
		// GRASS ENV
		File GISBASE = new File(EXEC).getParentFile();
		String GRASS_VERSION = "7.0.0";
		EnvironmentUtils.addVariableToEnvironment(env, "GISBASE="+GISBASE);
		EnvironmentUtils.addVariableToEnvironment(env, "GRASS_VERSION="+GRASS_VERSION);
		
		File GISRC = new File(System.getProperty("user.home"),".grassrc."+GRASS_VERSION+"."+location.getName());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(GISRC))) {
			writer.write("GISDBASE: " + geodb);
			writer.newLine();
			writer.write("LOCATION_NAME: " + location.getName());
			writer.newLine();
			writer.write("MAPSET: PERMANENT");
			writer.newLine();
			writer.write("GRASS_GUI: text");
			writer.newLine();
		}
		EnvironmentUtils.addVariableToEnvironment(env, "GISRC="+GISRC);

		// SYSTEM ENV
		String bin = new File(GISBASE,"bin").getAbsolutePath();
		String scripts = new File(GISBASE,"scripts").getAbsolutePath();				
		String lib = new File(GISBASE,"lib").getAbsolutePath();
		if (SYSTEM == Env.WINDOWS) {
			String PATH = env.get("PATH") + pathSeparator + bin + pathSeparator + scripts + pathSeparator + lib;
			EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + PATH);
		} else {
			String PATH = env.get("PATH") + pathSeparator + bin + pathSeparator + scripts;
			EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + PATH);
			if (SYSTEM == Env.LINUX) {
				String LD_LIBRARY_PATH = (env.containsKey("LD_LIBRARY_PATH")
						? env.get("LD_LIBRARY_PATH") + pathSeparator : "") + lib;
				EnvironmentUtils.addVariableToEnvironment(env, "LD_LIBRARY_PATH=" + LD_LIBRARY_PATH);
			} else if (SYSTEM == Env.MAC) {
				String DYLD_LIBRARY_PATH = (env.containsKey("DYLD_LIBRARY_PATH")
						? env.get("DYLD_LIBRARY_PATH") + pathSeparator : "") + lib;
				EnvironmentUtils.addVariableToEnvironment(env, "DYLD_LIBRARY_PATH=" + DYLD_LIBRARY_PATH);
			}
		}	
		return env;
	}

	/**
	 * Define a GISBASE/LOCATION_NAME/PERMANENT for the provided dem.
	 * 
	 * The dem is staged in GISBASE/dem.tif and then moved to
	 * GISBASE/LOCATION_NAME/PERMANENT/dem.tif
	 * 
	 * @param operation
	 *            Name used for the location on disk
	 * @param dem
	 *            File used to establish CRS and Bounds for the location
	 * @return Array of files consisting of {GISBASE, LOCATION, MAPSET, dem.tif}
	 * @throws Exception
	 */
	static File location( File location, File raster ) throws Exception {		
		// grass70 + ' -c ' + myfile + ' -e ' + location_path
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("${raster}");
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(new KVP("raster", raster, "location", location));		
		LOGGER.info( cmd.toString() );
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);		
		executor.setStreamHandler(new PumpStreamHandler(System.out));

		LOGGER.info(cmd.toString());
		try {
			int exitValue = executor.execute(cmd);
		} catch (ExecuteException fail) {
			LOGGER.warning("grass70:" + fail.getLocalizedMessage());
			throw fail;
		}

		File mapset = new File( location, "PERMANENT" );
		if( !mapset.exists() ){
			throw new IllegalStateException("Did not create mapset "+mapset);
		}
		return location;
	}

	/**
	 * Define a GISBASE/LOCATION_NAME/PERMANENT for the provided dem.
	 * 
	 * The dem is staged in GISBASE/dem.tif and then moved to
	 * GISBASE/LOCATION_NAME/PERMANENT/dem.tif
	 * 
	 * @param operation
	 *            Name used for the location on disk
	 * @param dem
	 *            File used to establish CRS and Bounds for the location
	 * @return Array of files consisting of {GISBASE, LOCATION, MAPSET, dem.tif}
	 * @throws Exception
	 */
	static File[] location( String operation, GridCoverage2D dem ) throws Exception {
		File geodb = new File(System.getProperty("user.home"),"grassdata");
		//File location = Files.createTempDirectory(geodb.toPath(),operation).toFile();
		File location = new File( geodb, operation );
		File mapset = new File( location, "PERMANENT" );
		File file = new File( geodb, "dem.tif");
		
		final GeoTiffFormat format = new GeoTiffFormat();
		GridCoverageWriter writer = format.getWriter(file);
		writer.write(dem, null);
		System.out.println("Staging file:"+file);
		
		// grass70 + ' -c ' + myfile + ' -e ' + location_path
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("${file}");
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(new KVP("file", file, "location", location));		
		LOGGER.info( cmd.toString() );
		
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		executor.setWatchdog(watchdog);		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		int exitValue = executor.execute(cmd);
		
		File origional = file;
		file = new File(mapset, file.getName());
		Files.move(origional.toPath(),  file.toPath() );
		return new File[]{geodb,location,mapset,file};
	}

	/**
	 * Define a GISBASE/LOCATION_NAME for the provided dem.
	 * 
	 * @param operation Name used for the location on disk
	 * @param dem File used to establish CRS and Bounds for the location
	 * @return
	 * @throws Exception
	 */
	static File location( CoordinateReferenceSystem crs ) throws Exception {
		String code = CRS.toSRS(crs, true);
		File geodb = new File(System.getProperty("user.home"),"grassdata");
		File location = Files.createTempDirectory(geodb.toPath(),code).toFile();
		KVP kvp = new KVP(
				"geodb",geodb,
				"location",location);
		
		// grass70 + ' -c epsg:' + myepsg + ' -e ' + location_path
		CommandLine cmd = new CommandLine(EXEC);
		cmd.addArgument("-c");
		cmd.addArgument("epsg:"+code);
		cmd.addArgument("-e");
		cmd.addArgument("${location}");
		cmd.setSubstitutionMap(kvp);
	
		DefaultExecutor executor = new DefaultExecutor();
		executor.setExitValue(0);
		executor.setWatchdog(new ExecuteWatchdog(60000));		
		executor.setStreamHandler(new PumpStreamHandler(System.out));
		
		int exitValue = executor.execute(cmd);		
		return location;
	}
	
}
