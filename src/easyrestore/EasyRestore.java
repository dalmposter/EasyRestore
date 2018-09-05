/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package easyrestore;

import java.io.File;
import java.io.IOException;
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
    
    private static String dbIP = "localhost:5432";
    private static String dbName = "bitbucket";
    private static String dbAccount = "bitbucketuser";
    private static String dbPassword = "CH4NG3M3";
    
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
    
    public static boolean execute()
    {
        //stop and remove services
//        try
//        {
//            Runtime.getRuntime().exec("cmd /c start \"\" C:\\Users\\dominic.cousins\\Documents\\NetBeansProjects\\EasyRestore\\removeServices.bat");
//        }
//        catch (Exception e)
//        {
//            System.out.println("Error running bat: " + e);
//            e.printStackTrace();
//            return false;
//        }
        
        if(startConnect())
        {
            try
            {
                preparedStatement = connect.prepareStatement("DROP DATABASE bitbucket");
                preparedStatement.executeUpdate();
                preparedStatement = connect.prepareStatement("CREATE DATABASE bitbucket "
                    + "WITH OWNER = bitbucketuser ENCODING = 'UTF8' "
                    + "TABLESPACE = pg_default"
                    + "LC_COLLATE = 'English_United Kingdom.1252'"
                    + "LC_CTYPE = 'English_United Kingdom.1252'"
                    + "CONNECTION LIMIT = -1;");
                
                preparedStatement = connect.prepareStatement("DROP ROLE bitbucketuser");
                preparedStatement.executeUpdate();
                preparedStatement = connect.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = 'jirauser'");
                resultSet = preparedStatement.executeQuery();
                
                boolean userExists = false;
                
                while(resultSet.next())
                {
                    if(resultSet.getInt(1) == 1)
                    {
                        userExists = true;
                        break;
                    }
                }
                
                if(!userExists)
                {
                    
                }
            }
            catch (Exception e)
            {
                System.out.println("Error dropping/recreating datbase: " + e);
                e.printStackTrace();
                return false;
            }
        }
        
        return true;
    }
    
    public static boolean startConnect()
    {
        try
        {
            Class.forName("org.postgresql.Driver");
            connect = DriverManager.getConnection("jdbc:postgresql://localhost/postgres", dbAccount, dbPassword);
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
