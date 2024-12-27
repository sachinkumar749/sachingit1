package org.example;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedCondition;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException, ParseException {



        // Configure ChromeOptions
//        ChromeOptions options = new ChromeOptions();
//
//        // Specify the user data directory path
//        options.addArguments("user-data-dir=/home/user/.config/google-chrome/Default"); // Replace with your actual path
//
//        // Specify a profile directory (e.g., Default, Profile 1)
//        options.addArguments("--profile-directory=Default");
//
//        // Optionally, add the MetaMask extension
//        options.addExtensions(new File("/home/user/Downloads/metamask 12.9.1.0.crx"));

        // Initialize the ChromeDriver with options
//        WebDriver driver = new ChromeDriver();
//            // Maximize browser window
//            driver.manage().window().maximize();
//
//            driver.get("https://www.hyrtutorials.com/p/calendar-practice.html");



        String targetDate="31-Dec-2024";
        SimpleDateFormat formatedDate=new SimpleDateFormat("dd-MMM-yyyy");
        formatedDate.setLenient(false);
           System.out.println ( formatedDate.parse(targetDate));

    }
}
