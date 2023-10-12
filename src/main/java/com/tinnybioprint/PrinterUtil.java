package com.tinnybioprint;


import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.SimpleDoc;

public class PrinterUtil {

    public static void listPrinters() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        System.out.println("Available Printers:");
        for (PrintService printer : printServices) {
            System.out.println(printer.getName());
        }
    }

    public static void printImage(String imagePath) {
        try {
            PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
            if (defaultPrinter != null) {
                FileInputStream fileInputStream = new FileInputStream(imagePath);
                DocFlavor docFlavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
                Doc doc = new SimpleDoc(fileInputStream, docFlavor, null);
                DocPrintJob printJob = defaultPrinter.createPrintJob();
                printJob.print(doc, null);
                fileInputStream.close();
            } else {
                System.out.println("No default printer found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // List all available printers
        listPrinters();

        // Start a separate thread for polling a URL for JSON data
        Thread pollingThread = new Thread(() -> {
            while (true) {
                
                try {
                    URL url = new URL("http://idmsdev.kaa.go.ke/main/mwclient/index.php?type=getpermit&fid=6&ptype=t"); // Replace with your URL
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String line;
                        StringBuilder response = new StringBuilder();

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        String jsonData = response.toString();
                        System.out.println("Received JSON data: " + jsonData);
                    } else {
                        System.out.println("HTTP request failed with response code: " + responseCode);
                    }

                    Thread.sleep(30000); // Poll every 30 seconds
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        pollingThread.start();

        // Print an image (replace with the path to your image)
        String imagePath = "path/to/your/image.jpg";
        printImage(imagePath);
    }
}
