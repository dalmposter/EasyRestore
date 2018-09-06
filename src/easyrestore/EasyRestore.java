/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package easyrestore;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

/**
 *
 * @author dominic.cousins
 */
public class EasyRestore {

    private static String installDir = "C:\\Atlassian\\Bitbucket\\5.13.1";
    private static String homeDir = "C:\\Atlassian\\ApplicationData\\Bitbucket";
    private static String restoreDir = "C:\\Atlassian\\ApplicationData\\Bitbucket(restored)";
    private static String backupDir = "C:\\Atlassian\\Backups\\backups\\bitbucket-20180905-102803-342.tar";
    
    private static String restoreClientDir = "C:\\Atlassian\\bitbucket-backup-client-3.3.4\\bitbucket-restore-client.jar";
    
    private static String dbIP = "localhost:5432";
    private static String dbName = "bitbucket";
    private static String dbAccount = "bitbucketuser";
    private static String dbPassword = "CH4NG3M3";
    private static String dbRoot = "postgres";
    private static String dbRootPw = "Emp1r32202";
    
    private static Connection connect = null;
    private static PreparedStatement preparedStatement = null;
    private static ResultSet resultSet = null;
    
    /**
     * @param args the command line arguments
     */
    
    public static void main(String[] args)
    {
        if(execute()) System.out.println("Success");
        else System.out.println("Failure");
    }
    
    public static void sleep(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(Exception e)
        {

        }
    }
    
    public static boolean execute()
    {
        //stop and remove services
        try
        {
            Runtime.getRuntime().exec("cmd /C start /wait sc stop atlassianbitbucket");
            Runtime.getRuntime().exec("cmd /C start /wait sc stop atlassianbitbucketelasticsearch");
            System.out.println("Stopped services");
            
            sleep(1000);
        }
        catch (Exception e)
        {
            System.out.println("Error stopping services: " + e);
            e.printStackTrace();
            return false;
        }
        
        if(startConnect())
        {
            try
            {
                preparedStatement = connect.prepareStatement("DROP DATABASE bitbucket");
                //preparedStatement.setString(1, dbName);
                System.out.println("Executing: " + preparedStatement);
                preparedStatement.executeUpdate();
                
                sleep(1000);
            }
            catch (Exception e)
            {
                System.out.println("Couldnt drop database: " + e);
                e.printStackTrace();
            }
            
            try
            {
                preparedStatement = connect.prepareStatement("CREATE DATABASE " + dbName + " "
                    + "WITH OWNER = " + dbAccount);
                System.out.println("Executing: " + preparedStatement);
                preparedStatement.executeUpdate();
                
                preparedStatement = connect.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?");
                preparedStatement.setString(1, dbAccount);
                System.out.println("Executing: " + preparedStatement);
                resultSet = preparedStatement.executeQuery();
                
                boolean userExists = false;
                
                while(resultSet.next())
                {
                    if(resultSet.getInt(1) == 1)
                    {
                        System.out.println("Found the user. Not creaing");
                        userExists = true;
                        break;
                    }
                }
                
                if(!userExists)
                {
                    System.out.println("Did not find user. Creating");
                    preparedStatement = connect.prepareStatement("CREATE USER ? WITH PASSWORD '?' CREATEDB CREATEROLE");
                    preparedStatement.setString(1, dbAccount);
                    preparedStatement.setString(2, dbPassword);
                    System.out.println("Executing: " + preparedStatement);
                    preparedStatement.executeUpdate();
                }
            }
            catch (Exception e)
            {
                System.out.println("Error recreating datbase: " + e);
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
                System.out.println("Deleting restore directory");
                deleteDirectoryRecursion(Paths.get(restoreDir));
            }
            catch (java.nio.file.NoSuchFileException e)
            {
                System.out.println("Restore directory already deleted, continuing...");
            }
            catch (Exception e)
            {
                System.out.println("Error deleting restore directory: " + e);
                e.printStackTrace();
            }
            
            sleep(1000);
            
            try
            {
                System.out.println("Running restore client");
                Process restoreClient = Runtime.getRuntime().exec("cmd /C start /wait java -ja            \n" +
"                BufferedReader stdInput = new BufferedReader(new \n" +
"                InputStreamReader(restoreClient.getInputStream()));\n" +
"\n" +
"                BufferedReader stdError = new BufferedReader(new \n" +
"                InputStreamReader(restoreClient.getErrorStream()));r -noverify -Dbitbucket.home=" + restoreDir + " " + restoreClientDir + " " + backupDir);


                sleep(1000);
            }
            catch (Exception e)
            {
                System.out.println("Couldn't run restore client. Stopping...");
            }
            
            sleep(10000);
            
            try
            {
                System.out.println("Deleting contents of home directory");
                deleteContents(Paths.get(homeDir));
            }
            catch (Exception e)
            {
                System.out.println("Couldn't delete contents of home directory. Stopping: " + e);
                e.printStackTrace();
            }
            
            sleep(2000);
            
            System.out.println("Copying restored home directory");
            copyDirectoryContentsOnly(new File(restoreDir), new File(homeDir));
            
            sleep(5000);
            System.out.println("Starting Bitbucket...");
            try
            {
                Runtime.getRuntime().exec("cmd /C start /wait sc stop atlassianbitbucket");
            }
            catch (Exception e)
            {
                System.out.println("Couldn't start service : " + e);
                System.out.println("Restore may have been successful anyway");
            }
        }
        else
        {
            System.out.println("Couldn't establish connection with database. Stopping...");
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
    
    public static void close()
    {
        try
        {
            if(preparedStatement != null) preparedStatement.close();
            if(resultSet != null) resultSet.close();
            if(connect != null) connect.close();
        }
        catch (Exception e)
        {
            System.out.println("Error closing connection: " + e);
            e.printStackTrace();
        }
    }
    
    public static boolean startConnect()
    {
        try
        {
            Class.forName("org.postgresql.Driver");
            connect = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", dbRoot, dbRootPw);
        }
        catch(Exception e)
        {
            System.out.println("Failed to open connection to database: " + e);
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
 
           System.out.println("Directory does not exist.");
           System.exit(0);
 
        }else{
 
           try{
        	   
               delete(directory);
        	
           }catch(IOException e){
               e.printStackTrace();
               System.exit(0);
           }
        }
 
    	System.out.println("Done");
    }
 
    public static void delete(File file) throws IOException
    {
    	if(file.isDirectory())
        {
 
            //directory is empty, then delete it
            if(file.list().length == 0)
            {

               file.delete();
               System.out.println("Directory is deleted : " 
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
                 System.out.println("Directory is deleted : " 
                                              + file.getAbsolutePath());
               }
            }
    		
    	}
        else
        {
            //if file, then delete it
            file.delete();
            System.out.println("File is deleted : " + file.getAbsolutePath());
    	}
    }
}
