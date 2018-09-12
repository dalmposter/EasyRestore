/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author dominic.cousins
 */
public class EasyRestore implements Runnable{

    private static MainGUI gui;
    
    private static String installDir = "C:\\Atlassian\\Bitbucket\\5.13.1";
    private static String homeDir = "C:\\Atlassian\\ApplicationData\\Bitbucket";
    private static String restoreDir = "C:\\Atlassian\\ApplicationData\\Bitbucket(restored)";
    private static String backupDir = "C:\\Atlassian\\Backups\\backups\\bitbucket-20180905-102803-342.tar";
    private static String backupHomeDir = "C:\\Atlassian\\ApplicationData\\Bitbucket";
    private static String backupBackupDir = "C:\\Atlassian\\Backups\\backups\\bitbucket-20180905-102803-342.tar";
    
    private static String backupClientDir = "C:\\Atlassian\\bitbucket-backup-client-3.3.4\\bitbucket-backup-client.jar";
    private static String restoreClientDir = "C:\\Atlassian\\bitbucket-backup-client-3.3.4\\bitbucket-restore-client.jar";
    
    private static String adminName = "admin";
    private static String adminPassword = "password";
    private static String baseUrl = "localhost:7990";
    
    private static String dbIP = "localhost:5432";
    private static String dbName = "bitbucket";
    private static String dbAccount = "bitbucketuser";
    private static String dbPassword = "CH4NG3M3";
    
    private static Connection connect = null;
    private static PreparedStatement preparedStatement = null;
    private static ResultSet resultSet = null;
    
    private static Thread t;
    private static String threadName;
    
    private static Logger logger;
    private static String[] connectors = {"org.postgresql.Driver", "com.mysql.cj.jdbc.Driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "oracle.jdbc.OracleDriver"};
    private static String[] connections = {"jdbc:postgresql://", "jdbc:mysql://", "jdbc:sqlserver://", "jdbc:oracle:thin:@//"};
    private static int dbProvider = 0;
    
    private static EasyRestore main;
    public boolean executing = false;
    public boolean executingBackup = false;
    
    /**
     * @param args the command line arguments
     */
    
    static
    {
        //get logging.properties from resources
        InputStream stream = EasyRestore.class.getClassLoader().getResourceAsStream("main/resources/logging.properties");
        
        try
        {
            //send properties to the LogManager
            LogManager.getLogManager().readConfiguration(stream);
        }
        catch(java.io.IOException e)
        {
            System.out.println("Error reading log config " + e);
        }
    }
    
    public boolean busy()
    {
        return executing || executingBackup;
    }
    
    public void start()
    {
        if(t == null)
        {
            t = new Thread(this, threadName);
            t.start();
        }
    }
    
    @Override
    public void run()
    {
        while(true)
        {
            if(executing)
            {
                gui.addProgress = 0;
                if(execute()) log(Level.INFO, "Restore Success");
                else log(Level.SEVERE, "Restore Failure");
                executing = false;
                
                sleep(1000);
            }
            else if(executingBackup)
            {
                gui.addBackupProgress = 0;
                if(executeBackup()) logBackup(Level.INFO, "Restore Success");
                else log(Level.SEVERE, "Backup Failure");
                executing = false;
            }
            else
            {
                sleep(1000);
            }
        }
    }
    
    private boolean executeBackup()
    {
        return false;
    }
    
    EasyRestore()
    {
        threadName = "main thread";
    }
    
    public static void main(String[] args)
    {
        //create logger
        logger = Logger.getLogger("Log");
        
        main = new EasyRestore();
        main.start();
        
        gui = new MainGUI(main);
        gui.start();
        gui.setVisible(true);
        
        String path = System.getProperty("user.home") + "\\EasyRestore\\Logs";
        new File(path).mkdirs();
        log(Level.INFO, "Logs can be found at : " + path);
    }
    
    public static void logBackup(Level level, String msg)
    {
        logger.log(level, msg);
        gui.logBackup(msg);
    }
    
    public static void log(Level level, String message)
    {
        logger.log(level, message);
        gui.log(message);
    }
    
    private String getBackupVariables()
    {
        backupHomeDir = gui.getBackupHomeDir();
        if(backupHomeDir == null || "".equals(backupHomeDir)) return "Home Directory";
        logBackup(Level.INFO, "Found home directory : " + backupHomeDir);
        
        backupBackupDir = gui.getBackupBackupDir();
        if(backupBackupDir == null || "".equals(backupBackupDir)) return "Backup Directory";
        logBackup(Level.INFO, "Found backup location : " + backupBackupDir);
        
        backupClientDir = gui.getBackupClientDir();
        if(backupClientDir == null || "".equals(backupClientDir)) return "Backup Client Location";
        logBackup(Level.INFO, "Found backup client location : " + backupClientDir);
        
        adminName = gui.getAdminName();
        if(adminName == null || "".equals(adminName)) return "Admin Account";
        logBackup(Level.INFO, "Found admin account : " + adminName);
        
        adminPassword = gui.getAdminPw();
        if(adminPassword == null || "".equals(adminPassword)) return "Admin Password";
        logBackup(Level.INFO, "Found admin password : " + adminPassword);
        
        baseUrl = gui.getBaseUrl();
        if(baseUrl == null || "".equals(baseUrl)) return "Base URL";
        logBackup(Level.INFO, "Found base URL : " + baseUrl);
        
        return "";
    }
    
    private String getVariables()
    {
        installDir = gui.getInstallDir();
        if(installDir == null || "".equals(installDir)) return "Install Directory";
        log(Level.INFO, "Found install directory : " + installDir);
        
        homeDir = gui.getHomeDir();
        if(homeDir == null || "".equals(homeDir)) return "Home Directory";
        log(Level.INFO, "Found home directory : " + homeDir);
        restoreDir = (new File(homeDir)).getParentFile().getName() + "\\Bitbucket(restored)";
        log(Level.INFO, "Found restore directory : " + restoreDir);
        
        backupDir = gui.getBackupDir();
        if(backupDir == null || "".equals(backupDir)) return "Backup Location";
        log(Level.INFO, "Found backup location : " + backupDir);
        
        dbIP = gui.getDbIp();
        if(dbIP == null || "".equals(dbIP)) return "Database IP:port";
        log(Level.INFO, "Found database IP : " + dbIP);
        
        dbName = gui.getDbName();
        if(dbName == null || "".equals(dbName)) return "Database Name";
        log(Level.INFO, "Found database name : " + dbName);
        
        dbAccount = gui.getDbAccount();
        if(dbAccount == null || "".equals(dbAccount)) return "Bitbucket Database Account";
        log(Level.INFO, "Found database account : " + dbAccount);
        
        dbPassword = gui.getDbPw();
        if(dbPassword == null || "".equals(dbPassword)) return "Bitbucket Database Password";
        log(Level.INFO, "Found database password : " + dbPassword);
        
        dbProvider = gui.getDatabase();
        if(dbProvider < -1 || dbProvider > 4) return "Database Provider";
        log(Level.INFO, "Found database provider :" + dbProvider);
        
        restoreClientDir = gui.getClientDir();
        if(restoreClientDir == null || "".equals(restoreClientDir)) return "Restore Client Location";
        log(Level.INFO, "Found restore client directory : " + restoreClientDir);
        
        return "none";
    }
    
    public void sleep(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(Exception e)
        {

        }
    }
    
    public boolean execute()
    {
        String variableReturn = getVariables();
        if("none".equals(variableReturn))
        {
            //stop and remove services
            try
            {
                gui.addProgress += 10;
                
                log(Level.INFO, "Stopping Services...");
                Runtime.getRuntime().exec("cmd /C start /wait sc stop atlassianbitbucket");
                Runtime.getRuntime().exec("cmd /C start /wait sc stop atlassianbitbucketelasticsearch");

                sleep(5000);
                log(Level.INFO, "Stopped services");
            }
            catch (Exception e)
            {
                log(Level.INFO, "Error stopping services: " + e);
                e.printStackTrace();
                return false;
            }

            if(startConnect())
            {
                try
                {
                    gui.addProgress += 10;
                    
                    preparedStatement = connect.prepareStatement("DROP DATABASE " + dbName);
                    log(Level.INFO, "Executing: " + preparedStatement);
                    preparedStatement.executeUpdate();
                    
                    sleep(1000);
                }
                catch (Exception e)
                {
                    log(Level.SEVERE, "Couldnt drop database: " + e);
                    e.printStackTrace();
                    log(Level.INFO, "Try running this as administrator");
                }

                try
                {
                    gui.addProgress += 10;
                    
                    preparedStatement = connect.prepareStatement("CREATE DATABASE " + dbName + " WITH OWNER = " + dbAccount);
                    log(Level.INFO, "Executing: " + preparedStatement);
                    preparedStatement.executeUpdate();
                }
                catch (Exception e)
                {
                    log(Level.INFO, "Error recreating datbase: " + e);
                    e.printStackTrace();
                    return false;
                }
                finally
                {
                    close();
                }

                sleep(1000);

                try
                {
                    gui.addProgress += 10;
                    
                    log(Level.INFO, "Deleting restore directory");
                    deleteDirectoryRecursion(Paths.get(restoreDir));
                }
                catch (java.nio.file.NoSuchFileException e)
                {
                    log(Level.INFO, "Restore directory already deleted, continuing...");
                }
                catch (Exception e)
                {
                    log(Level.INFO, "Error deleting restore directory: " + e);
                    e.printStackTrace();
                }

                sleep(1000);

                try
                {
                    gui.addProgress += 10;
                    
                    log(Level.INFO, "Running restore client");
                    Process restoreClient = Runtime.getRuntime().exec("cmd.exe /c /wait java -jar -noverify -Dbitbucket.home=" + restoreDir + " " + restoreClientDir + " " + backupDir);

                    sleep(1000);
                }
                catch (Exception e)
                {
                    log(Level.INFO, "Couldn't run restore client. Stopping...");
                }

                gui.addProgress += 20;
                
                sleep(10000);

                try
                {
                    gui.addProgress += 10;
                    
                    log(Level.INFO, "Deleting contents of home directory");
                    deleteContents(Paths.get(homeDir));
                }
                catch (Exception e)
                {
                    log(Level.INFO, "Couldn't delete contents of home directory. Stopping: " + e);
                    e.printStackTrace();
                }

                sleep(2000);

                gui.addProgress += 10;
                log(Level.INFO, "Copying restored home directory");
                copyDirectoryContentsOnly(new File(restoreDir), new File(homeDir));

                sleep(5000);
                gui.addProgress += 4;
                log(Level.INFO, "Starting Bitbucket...");
                try
                {
                    Runtime.getRuntime().exec("cmd /C start /wait sc start atlassianbitbucket");
                    log(Level.INFO, "Started bitbucket");
                }
                catch (Exception e)
                {
                    log(Level.INFO, "Couldn't start service : " + e);
                    log(Level.INFO, "Restore may have been successful anyway");
                }
                gui.addProgress += 6;
            }
            else
            {
                log(Level.INFO, "Couldn't establish connection with database. Stopping...");
            }
        }
        else
        {
            JOptionPane.showMessageDialog(null, "Please enter a valid " + variableReturn, "Invalid Input", JOptionPane.OK_OPTION);
            log(Level.WARNING, variableReturn + " invalid");
            return false;
        }
        return true;
    }
    
    public static void copyDirectoryContentsOnly(File source, File destination)
    {
        if(source.isDirectory() && source.listFiles() != null){
            for(File file : source.listFiles()) {               
                copyDirectoryContents(file, destination);
            }
        }
    }
    
    public static void copyDirectoryContents(File source, File destination){
        try {
            String destinationPathString = destination.getPath() + "\\" + source.getName();
            Path destinationPath = Paths.get(destinationPathString);
            Files.copy(source.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }        
        catch (UnsupportedOperationException e) {
            //UnsupportedOperationException
        }
        catch (DirectoryNotEmptyException e) {
            //DirectoryNotEmptyException
        }
        catch (IOException e) {
            //IOException
        }
        catch (SecurityException e) {
            //SecurityException
        }

        if(source.isDirectory() && source.listFiles() != null){
            for(File file : source.listFiles()) {               
                copyDirectoryContents(file, new File(destination.getPath() + "\\" + source.getName()));
            }
        }

    }
    
    public static void deleteContents(Path path) throws IOException
    {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path))
            {
                for (Path entry : entries)
                {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
    }
    
    public static void deleteDirectoryRecursion(Path path) throws IOException
    {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path))
            {
                for (Path entry : entries)
                {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
        Files.delete(path);
    }
    
    public void close()
    {
        try
        {
            if(preparedStatement != null) preparedStatement.close();
            if(resultSet != null) resultSet.close();
            if(connect != null) connect.close();
        }
        catch (Exception e)
        {
            log(Level.INFO, "Error closing connection: " + e);
            e.printStackTrace();
        }
    }
    
    public boolean startConnect()
    {
        try
        {
            Class.forName(connectors[dbProvider]);
            connect = DriverManager.getConnection(connections[dbProvider] + dbIP + "/" + dbName, dbAccount, dbPassword);
            
            try
            {
                preparedStatement = connect.prepareStatement("CREATE DATABASE temp");
                preparedStatement.execute();
            }
            catch (Exception f)
            {
                log(Level.WARNING, f + " Couldn't create temp database, it probably already exists, continuing");
            }
            
            close();
            connect = DriverManager.getConnection(connections[dbProvider] + dbIP + "/temp", dbAccount, dbPassword);
        }
        catch(Exception e)
        {
            log(Level.INFO, "Failed to open connection to database: " + e);
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
    
    public static void ifExistsDelete(String filePath)
    {	
    	
    	File directory = new File(filePath);
 
    	//make sure directory exists
    	if(!directory.exists()){
 
           log(Level.INFO, "Directory does not exist.");
           System.exit(0);
 
        }else{
 
           try{
        	   
               delete(directory);
        	
           }catch(IOException e){
               e.printStackTrace();
               System.exit(0);
           }
        }
 
    	log(Level.INFO, "Done");
    }
 
    public static void delete(File file) throws IOException
    {
    	if(file.isDirectory())
        {
 
            //directory is empty, then delete it
            if(file.list().length == 0)
            {

               file.delete();
               log(Level.INFO, "Directory is deleted : " 
                                             + file.getAbsolutePath());

            }
            else
            {

               //list all the directory contents
               String files[] = file.list();

               for (String temp : files)
               {
                  //construct the file structure
                  File fileDelete = new File(file, temp);

                  //recursive delete
                 delete(fileDelete);
               }

               //check the directory again, if empty then delete it
               if(file.list().length == 0)
               {
                 file.delete();
                 log(Level.INFO, "Directory is deleted : " 
                                              + file.getAbsolutePath());
               }
            }
    		
    	}
        else
        {
            //if file, then delete it
            file.delete();
            log(Level.INFO, "File is deleted : " + file.getAbsolutePath());
    	}
    }
}
