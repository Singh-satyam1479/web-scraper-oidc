package com.kirabiz;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import com.opencsv.CSVWriter;
import com.opencsv.CSVReader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    public static void main(String[] args) {
        List<String> websites = readWebsiteUrlsFromCSV("C:\\Users\\Administrator\\Documents\\Kirabiz\\Project 1\\dataset.csv");
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try (CSVWriter csvWriter = new CSVWriter(new FileWriter("C:\\Users\\Administrator\\Documents\\Kirabiz\\Project 1\\output2.csv"))) {
            String[] headers = {"Website", "Cookie Popup", "Login Patterns", "Google OIDC Patterns", "Detection Method", "Language Check"};
            csvWriter.writeNext(headers);

            List<Future<String[]>> futures = new ArrayList<>();

            for (String website : websites) {
                Future<String[]> future = executor.submit(new WebsiteProcessorTask(website));
                futures.add(future);
            }

            for (Future<String[]> future : futures) {
                try {
                    String[] row = future.get();
                    csvWriter.writeNext(row);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    static class WebsiteProcessorTask implements Callable<String[]> {
        private String website;

        WebsiteProcessorTask(String website) {
            this.website = website;
        }

        @Override
        public String[] call() throws Exception {
            WebDriver driver = null;
            try {
                System.setProperty("webdriver.chrome.driver", "C:\\Users\\Administrator\\Documents\\Kirabiz\\Project 1\\chromedriver.exe");
                driver = new ChromeDriver();
                driver.get(website);

                String cookiePopupResult = handleCookiePopUp(driver);
                String loginPatternsResult = clickOnLoginPatterns(driver);
                String googleOIDCPatternsResult = handleGoogleOIDCPatterns(driver);
                String detectionMethod = determineDetectionMethod(driver);
                boolean isEnglish = isEnglishWebsite(website);
                String languageCheckResult = isEnglish ? "In English" : "Not in English";

                return new String[]{website, cookiePopupResult, loginPatternsResult, googleOIDCPatternsResult, detectionMethod, languageCheckResult};

            } catch (Exception e) {
                System.err.println("Error navigating to the website: " + e.getMessage());
                e.printStackTrace();
                return new String[]{website, "Error", "Error", "Error", "Error", "Error"};
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }


        private static String determineDetectionMethod(WebDriver driver) {
            try {
                if (driver.findElement(By.id("popupModal")).isDisplayed()) {
                    return "Pop-Up Modal";
                } else {
                    return "Redirect URL";
                }
            } catch (NoSuchElementException e) {
                return "Redirect URL";
            }
        }

        private static String handleCookiePopUp(WebDriver driver) {
            String patt = "";
            List<String> cookiePatterns = Arrays.asList(
                    "Accept all & visit the site", "Accept", "Agree", "Close", "Ignore",
                    "Agree & Continue", "Agree & Close", "AGREE & CLOSE", "CONSENT", "Consent",
                    "ACCEPT", "AGREE", "Accept all cookies", "Accept All Cookies", "Allow", "ALLOW",
                    "OK", "Ok", "I accept", "I Accept", "I ACCEPT"
            );

            for (String pattern : cookiePatterns) {
                try {
                    WebElement element = driver.findElement(By.xpath("//*[contains(text(), '" + pattern + "')]"));
                    patt = patt.concat(pattern).concat(", ");
                    element.click();
                    break;
                } catch (Exception e) {
                    // Continue searching for the next pattern
                }
            }
            return patt.isEmpty() ? "Not Found" : patt.substring(0, patt.length() - 2); // Removes the trailing comma and space
        }

        private static String clickOnLoginPatterns(WebDriver driver) {
            String patt = "";
            List<String> loginPatterns = Arrays.asList(
                    "Login", "Log in", "Sign in", "Sign In", "Log In", "LOGON", "SIGN IN", "LOGIN",
                    "LOG IN", "Login/Register", "Account", "My Account"
            );

            for (String pattern : loginPatterns) {
                try {
                    WebElement element = driver.findElement(By.xpath("//*[contains(text(), '" + pattern + "')]"));
                    patt = patt.concat(pattern).concat(", ");
                    element.click();
                    break;
                } catch (Exception e) {
                    // Continue searching for the next pattern
                }
            }
            return patt.isEmpty() ? "Not Found" : patt.substring(0, patt.length() - 2); // Removes the trailing comma and space
        }


        private static String handleGoogleOIDCPatterns(WebDriver driver) {
            List<String> googleOIDCPatterns = Arrays.asList(
                    "Sign in with Google", "Continue with Google", "Sign In with Google", "Login with Google",
                    "Sign in using Google", "Log In with Google", "Login using Google", "Sign up with Google",
                    "LOG IN using Google", "LOG IN with Google", "LOG IN WITH GOOGLE","Sign in to Google"
            );

            for (String pattern : googleOIDCPatterns) {
                try {
                    WebElement element = driver.findElement(By.xpath("//*[contains(text(), '" + pattern + "')]"));
                    element.click();
                    return pattern; // Returns the text of the clicked pattern
                } catch (Exception e) {
                    // Continue searching for the next pattern
                }
            }

            return "Not Found";
        }

        private static boolean isEnglishWebsite(String websiteUrl) {
            try {
                Document doc = Jsoup.connect(websiteUrl).get();
                Element htmlTag = doc.select("html").first();

                if (htmlTag != null && htmlTag.hasAttr("lang")) {
                    String langAttribute = htmlTag.attr("lang").toLowerCase();
                    return langAttribute.startsWith("en");
                }

                Elements metaTags = doc.select("meta[http-equiv=content-language], meta[name=language], meta[name=lang]");
                for (Element metaTag : metaTags) {
                    String content = metaTag.attr("content").toLowerCase();
                    if (content.startsWith("en")) {
                        return true;
                    }
                }

                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private static List<String> readWebsiteUrlsFromCSV(String filePath) {
        List<String> websites = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                websites.add(line[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return websites;
    }
}
