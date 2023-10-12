package com.tinnybioprint.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.logging.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import static java.awt.print.Printable.NO_SUCH_PAGE;
import static java.awt.print.Printable.PAGE_EXISTS;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.Sides;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PrinterResolution;
import javax.print.attribute.standard.Sides;

import org.imgscalr.Scalr;

public class PrinterConfigApp {

    private JFrame frame;
    private JPanel panel;
    private JLabel facilityIdLabel;
    private Map<String, JCheckBox[]> printerCheckboxes = new HashMap<>();
    private Map<String, PrintService> printerSelection = new HashMap<>(); // Track printer selection
    private Map<String, PrintService> printerServices = new HashMap<>(); // All Printers
    private final String configFileName = "C:\\BioPrint\\tinybioprintconfig\\.tinyconfig";
    private boolean serviceRunning = false;
    private Thread pollingThreadTemp;
    private Thread pollingThreadPerm;
    private int fid = 0;
    private boolean isDevServer = true;
    private String BaseURL = "http://idmsdev.kaa.go.ke/";
    private String AckReceivedURL = "main/mwclient/index5.php?type=ackReceived";
    private String AckPrintedURL = "main/mwclient/index5.php?type=ackPrinted";
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("TinyBioPrintLogger");

    private int HEART_BEAT = 2500;

    public PrinterConfigApp() {
        frame = new JFrame("TinyBioPrint Configuration Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 300);

        LOGGER.debug("Testing file logging...");

        // Set a look and feel for a more modern appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Add a title for available printers
        JLabel titleLabel = new JLabel("Available Printers");
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Add a JPanel for the top section
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        facilityIdLabel = new JLabel("Facility ID: " + fid);
        facilityIdLabel.setHorizontalAlignment(JLabel.CENTER);
        facilityIdLabel.setFont(new Font("Arial", Font.BOLD, 14));
        topPanel.add(facilityIdLabel, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.CENTER);

        //setUpThreads();
        // Add printers with checkboxes to the panel
        listPrinters();

        // Load and set checkbox configuration
        loadConfig();

        frame.getContentPane().add(panel);
        frame.setVisible(true);
    }

    public boolean printImageFromURL(String imageUrl, PrintService printService) {
        boolean printResult = false;
        try {
            BufferedImage image = ImageIO.read(new URL(imageUrl));

            // Prepare the image for printing
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(image, flavor, null);

            // Create a print job
            DocPrintJob printJob = printService.createPrintJob();

            // Print the image
            printJob.print(doc, null);
            printResult = true;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.debug("Failed To Print Image", e);
        }
        return printResult;
    }

    private boolean printPdfFromURL(String pdfUrl, PrintService printService) {
        boolean printResult = false;
        try {
            URL url = new URL(pdfUrl);
            PDDocument document = PDDocument.load(url.openStream());

            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printService);
            job.setPageable(new PDFPageable(document));

            job.print();

            document.close();
            printResult = true;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Failed to print pdf document", e);
        }
        return printResult;
    }

    private byte[] convertImageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public boolean printFrontAndBackImages(PrintService printService, String frontImageUrl, String backImageUrl) {
        try {
            // Load front and back images from URLs
            BufferedImage frontImage = ImageIO.read(new URL(frontImageUrl));
            BufferedImage backImage = ImageIO.read(new URL(backImageUrl));

            // Convert images to bytes
            byte[] frontImageData = convertImageToBytes(frontImage);
            byte[] backImageData = convertImageToBytes(backImage);

            // Prepare the data for printing
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;

            // Create a print job
            DocPrintJob printJob = printService.createPrintJob();

            // Configure print attributes for double-sided printing
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            //attributes.add(MediaPrintableArea.ISO_A4);
            attributes.add(PrintQuality.HIGH);
            attributes.add(Sides.DUPLEX);

            // Print the front image on the first page
            Doc frontDoc = new SimpleDoc(frontImageData, flavor, null);
            printJob.print(frontDoc, attributes);

            // Print the back image on the second page
            Doc backDoc = new SimpleDoc(backImageData, flavor, null);
            printJob.print(backDoc, attributes);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.debug("Failed To Print Images", e);
        }
        return false;
    }

    public static BufferedImage rotate(BufferedImage bimg, double angle) {

        int w = bimg.getWidth();
        int h = bimg.getHeight();

        BufferedImage rotated = new BufferedImage(w, h, bimg.getType());
        Graphics2D graphic = rotated.createGraphics();
        graphic.rotate(Math.toRadians(angle), w / 2, h / 2);
        graphic.drawImage(bimg, null, 0, 0);
        graphic.dispose();
        return rotated;
    }

    public boolean printPermanentPass(PrintService printerService, 
            String printOrientation, String frontImageUrl, String backImageUrl) {
        boolean result = false;
        try {
            LOGGER.debug("Fetching ImagesForPermPass: Front->"+frontImageUrl+"  Back->"+backImageUrl);
            // Load front and back images from URLs
            BufferedImage frontImage = ImageIO.read(new URL(frontImageUrl));
            BufferedImage backImage = ImageIO.read(new URL(backImageUrl));
            LOGGER.debug("Images Received Success");
            BufferedImage[] images = {frontImage, null};
            if (printOrientation.equals("LANDSCAPE")) {
                BufferedImage rotatedImage = rotate(backImage, 180);
                images[1] = rotatedImage;
            } else {
                images[1] = backImage;
            }
            LOGGER.debug("Initializing PrinterService");
            final PrinterJob printJob = PrinterJob.getPrinterJob();
            printJob.setPrintService(printerService);
            LOGGER.debug("Initialization Done");
            final PageFormat pf = printJob.defaultPage();
            double pageWidth = pf.getWidth();
            double pageHeight = pf.getHeight();
            LOGGER.debug("Printer Page width=" + pageWidth + " height=" + pageHeight);
            final Paper paper = new Paper();
            paper.setSize(pageWidth * 72.0f, pageHeight * 72.0f);
            LOGGER.debug("paper width: {}, height: {}", paper.getWidth(), paper.getHeight());
            paper.setImageableArea(0.0, 0.0, paper.getWidth(), paper.getHeight());
            pf.setPaper(paper);
            LOGGER.debug("print Orientation: " + printOrientation);
            if (printOrientation.equals("LANDSCAPE")) {
                pf.setOrientation(PageFormat.LANDSCAPE);
            } else {
                pf.setOrientation(PageFormat.PORTRAIT);
            }
            printJob.setPrintable(new Printable() {
                public int print(Graphics graphics, PageFormat pageFormat,
                        int pageIndex) throws PrinterException {
                    LOGGER.debug("current pageIndex: " + pageIndex);
                    if (pageIndex <= 1) {
                        Graphics2D g2 = (Graphics2D) graphics;
                        final double xScale, yScale, xTranslate, yTranslate;
                        if (true){//printerService.getName().equals("DTC4500e Card Printer")) {
                            xScale = 1;
                            yScale = 1;
                            xTranslate = 0;
                            yTranslate = 0;
                        } else {
                            /*if (printOrientation.equals("Landscape")) {
                                if (pageIndex == 0) {
                                    // front of card
                                    xScale = 0.92;
                                    yScale = 0.90;
                                    xTranslate = 2.8;
                                    yTranslate = 5;
                                } else {
                                    // back of card
                                    xScale = 0.92;
                                    yScale = 0.87;
                                    xTranslate = 105;
                                    yTranslate = 120;
                                }
                            } else {
                                // Portrait orientation
                                if (pageIndex == 0) {
                                    // front of card
                                    xScale = 0.90;
                                    yScale = 0.90;
                                    xTranslate = 20;
                                    yTranslate = 3;
                                } else {
                                    // back of card
                                    xScale = 0.88;
                                    yScale = 0.88;
                                    xTranslate = 0;
                                    yTranslate = 40;
                                }
                            }*/
                        }
                        final double widthScale = (pageFormat.getWidth() / images[pageIndex]
                                .getWidth()) * xScale;
                        final double heightScale = (pageFormat.getHeight() / images[pageIndex]
                                .getHeight()) * yScale;
                        LOGGER.debug("Setting scale to " + widthScale + "x" + heightScale);
                        final AffineTransform at = AffineTransform.getScaleInstance(
                                widthScale, heightScale);
                        LOGGER.debug("Setting translate to " + xTranslate
                                + "x" + yTranslate);
                        at.translate(xTranslate, yTranslate);

//                        g2.drawRenderedImage(pageIndex == 1 ? rotatedImage : images[pageIndex], at);
                        g2.drawRenderedImage(images[pageIndex], at);
                        return PAGE_EXISTS;
                    } else {
                        return NO_SUCH_PAGE;
                    }
                }
            }, pf);
            printJob.print();
            result=true;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.debug("PrintPermanentPassFailed", e);
        }
        return result;
    }

    private void setUpThreads() {
        // Start a separate thread for polling a URL for JSON data
        pollingThreadPerm = new Thread(() -> {
            while (true) {

                try {
                    PrintService printService1 = PrintServiceLookup.lookupDefaultPrintService();
                    //String permUrl = "http://idmsdev.kaa.go.ke/main/printpermits/6/perm/test.png";
                    //printFrontAndBackImages(printService1,permUrl,permUrl);
                    //printPermanentPass(printService1,"Potrait",permUrl,permUrl);
                    URL url = new URL(BaseURL + "main/mwclient/index5.php?type=getpermit&fid=" + fid + "&ptype=p");
                    LOGGER.debug("New Perm Worker Started at:" + url.toString());
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

                        String jsonData = response.toString().trim();
                        LOGGER.debug("Perm Worker Response:" + jsonData);
                        try {
                            // Parse the JSON data
                            JSONObject jsonObject = new JSONObject(jsonData);
                            String status = jsonObject.getString("status");
                            LOGGER.debug("Perm Worker Response Status:" + status);
                            JSONArray urlsArray = jsonObject.getJSONArray("urls");

                            ArrayList<String> recIDS = new ArrayList<>();
                            ArrayList<String> frontUrls = new ArrayList();
                            ArrayList<String> backUrls = new ArrayList();
                            ArrayList<String> orientation = new ArrayList();
                            
                            LOGGER.debug("urlsArray: " + urlsArray);
                            LOGGER.debug("json object in urlsArray: " + urlsArray.getJSONObject(0));
                            // Extract and store the IDs in recIDS
                            for (int i = 0; i < urlsArray.length(); i++) {
                                JSONObject urlObject = urlsArray.getJSONObject(i);
                                LOGGER.debug("urlObject: " + urlObject);
                                String recId = urlObject.getString("id");
                                recIDS.add(recId);
                                String theUrl = urlObject.getString("url");
                                String side = urlObject.getString("side");
                                LOGGER.debug("side: " + side);
                                LOGGER.debug("theUrl: " + theUrl);
                                if(side.equals("FRONT")) frontUrls.add(theUrl);
                                else backUrls.add(theUrl);
                                orientation.add(urlObject.getString("orientation"));
                            }

//                            sendAcknowledgement(BaseURL + AckReceivedURL, recIDS.toArray());
                            ArrayList<String> printIDS = new ArrayList<>();

                            // Process and print each URL
                            /*for (int i = 0; i < urlsArray.length(); i++) {
                                JSONObject urlObject = urlsArray.getJSONObject(i);
                                String url__ = urlObject.getString("url");
                                String prnId = urlObject.getString("id");
                                String orientation = urlObject.getString("orientation");
                                String side = urlObject.getString("side");
                                System.out.println("URL " + (i + 1) + ": " + url__);
                                //get printer
                                PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
                                //printImageFromURL(url__, printerSelection.get("Temp"));
                                if (printPermanentPass(printService, orientation,url__, "" )) {
//                                    if (printImageFromURL(url__, printerSelection.get("Perm"))) {
                                    printIDS.add(prnId);
                                } else {
                                    //LOGGER.debug("");
                                }
                            }*/
                            LOGGER.debug("recIDS: " + recIDS);
                            LOGGER.debug("frontUrls: " + frontUrls);
                            PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
                            for(int i = 0; i < frontUrls.size(); i++) {
                                boolean bool = printPermanentPass(printService, 
                                        orientation.get(i), 
                                        frontUrls.get(i), 
                                        backUrls.get(i));
                                LOGGER.debug("bool: " + bool);
                                if(bool) {
                                    printIDS.add(recIDS.get(i));
                                } else {
                                    LOGGER.debug("id not added to ack ids: " + recIDS.get(i));
                                }
                            }
                            LOGGER.debug("printIds: " + printIDS);
                            sendAcknowledgement(BaseURL + AckPrintedURL, printIDS.toArray());
                        } catch (Exception e) {
                            e.printStackTrace();
                            LOGGER.debug("PERM_WORKER_JSON_ERROR", e);
                        }
                    } else {
                        System.out.println("HTTP request failed with response code: " + responseCode);
                    }

                    Thread.sleep(HEART_BEAT); // Poll every 10 seconds
                } catch (Exception e) {
                    try {
                        e.printStackTrace();
                        LOGGER.debug("PERM_WORKER_ERROR", e);
                        Thread.sleep(HEART_BEAT);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(PrinterConfigApp.class.getName()).log(Level.SEVERE, null, ex);
                        LOGGER.debug("PERM_WORKER_ERROR", ex);
                    }
                }
            }
        });

        pollingThreadTemp = new Thread(() -> {
            while (true) {
                try {
                    URL url = new URL(BaseURL + "main/mwclient/index5.php?type=getpermit&fid=" + fid + "&ptype=t");
                    LOGGER.debug("New Temp Worker Started at:" + url.toString());
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

                        String jsonData = response.toString().trim();
                        LOGGER.debug("Temp Worker Response:" + jsonData);
                        try {
                            // Parse the JSON data
                            JSONObject jsonObject = new JSONObject(jsonData);
                            String status = jsonObject.getString("status");
                            LOGGER.debug("Temp Worker Response Status:" + status);
                            JSONArray urlsArray = jsonObject.getJSONArray("urls");

                            ArrayList<String> recIDS = new ArrayList<>();

                            // Extract and store the IDs in recIDS
                            for (int i = 0; i < urlsArray.length(); i++) {
                                JSONObject urlObject = urlsArray.getJSONObject(i);
                                String recId = urlObject.getString("id");
                                recIDS.add(recId);
                            }

                            sendAcknowledgement(BaseURL + AckReceivedURL, recIDS.toArray());
                            ArrayList<String> printIDS = new ArrayList<>();

                            // Process and print each URL
                            for (int i = 0; i < urlsArray.length(); i++) {
                                JSONObject urlObject = urlsArray.getJSONObject(i);
                                String url__ = urlObject.getString("url");
                                String prnId = urlObject.getString("id");
                                System.out.println("URL " + (i + 1) + ": " + url__);
                                //PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
                                //printPdfFromURL(url__, printerSelection.get("Temp"));
                                //if (printPdfFromURL(url__, printService)) {
                                if (printPdfFromURL(url__, printerSelection.get("Temp"))) {
                                    printIDS.add(prnId);
                                } else {
                                    //LOGGER.debug("");
                                }
                            }
                            sendAcknowledgement(BaseURL + AckPrintedURL, printIDS.toArray());
                        } catch (Exception e) {
                            e.printStackTrace();
                            LOGGER.debug("TEMP_WORKER_JSON_ERROR", e);
                        }
                    } else {
                        System.out.println("HTTP request failed with response code: " + responseCode);
                    }
                    Thread.sleep(HEART_BEAT); // Poll every 10 seconds
                } catch (Exception e) {
                    try {
                        e.printStackTrace();
                        Thread.sleep(HEART_BEAT);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(PrinterConfigApp.class.getName()).log(Level.SEVERE, null, ex);
                        LOGGER.debug("TEMP_WORKER_ERROR", ex);
                    }
                }
            }
        });
    }

    private void listPrinters() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        JPanel printersPanel = new JPanel();
        printersPanel.setLayout(new GridLayout(0, 3));
        JScrollPane scrollPane = new JScrollPane(printersPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        printerSelection.put("Perm", PrintServiceLookup.lookupDefaultPrintService());
        printerSelection.put("Temp", PrintServiceLookup.lookupDefaultPrintService());

        for (PrintService printService : printServices) {
            String printerName = printService.getName();
            printerServices.put(printerName, printService);
            JCheckBox checkBoxP = new JCheckBox("Perm");
            JCheckBox checkBoxT = new JCheckBox("Temp");

            // Add action listener to ensure only one checkbox is selected at a time
            ActionListener checkBoxListener = e -> {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox sourceCheckBox = (JCheckBox) e.getSource();
                    String option = sourceCheckBox.getText();
                    if (sourceCheckBox.isSelected()) {
                        for (JCheckBox checkBox : printerCheckboxes.get(printerSelection.get(option).getName())) {
                            checkBox.setSelected(false);
                        }
                        // Unselect the other checkbox for this printer
                        printerSelection.put(option, printerServices.get(printerName));
                        LOGGER.debug("SelectedPrinter For:" + option + " PrinterName:" + printerSelection.get(option).getName());
                        for (JCheckBox checkBox : printerCheckboxes.get(printerName)) {
                            if (checkBox != sourceCheckBox) {
                                checkBox.setSelected(false);
                            }
                        }
                    }
                    // Save the configuration to the config file
                    saveConfig();
                }
            };
            /*
            // Add action listener to ensure only one checkbox is selected at a time
            ActionListener checkBoxListener = e -> {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox sourceCheckBox = (JCheckBox) e.getSource();
                    String option = sourceCheckBox.getText();
                    String currentSelection = printerSelection.get(option);

                    if (sourceCheckBox.isSelected()) {
                        // Unselect the other checkbox for this option
                        for (Map.Entry<String, JCheckBox[]> entry : printerCheckboxes.entrySet()) {
                            String printer = entry.getKey();
                            JCheckBox[] checkBoxes = entry.getValue();
                            if (printerSelection.containsKey(option) && !printer.equals(currentSelection)) {
                                checkBoxes[option.equals("P") ? 0 : 1].setSelected(false);
                            }
                        }
                        printerSelection.put(option, printerName);
                    } else {
                        printerSelection.put(option, "");
                    }
                    // Save the configuration to the config file
                    saveConfig();
                }
            };*/

            checkBoxP.addActionListener(checkBoxListener);
            checkBoxT.addActionListener(checkBoxListener);

            // Add checkboxes to the panel
            printersPanel.add(new JLabel(printerName));
            printersPanel.add(checkBoxP);
            printersPanel.add(checkBoxT);

            // Store checkboxes for each printer
            printerCheckboxes.put(printerName, new JCheckBox[]{checkBoxP, checkBoxT});
        }

        // Add a Start/Stop Service button at the bottom center
        JButton serviceButton = new JButton(serviceRunning ? "Stop Service" : "Start Tiny Service");
        serviceButton.setBackground(new Color(144, 238, 144)); // Light green color
        serviceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (serviceRunning) {
                    pollingThreadPerm.stop();
                    //pollingThreadTemp.stop();
                    serviceRunning = false;
                    serviceButton.setText("Start Tiny Service");
                    serviceButton.setBackground(new Color(144, 238, 144));

                    JOptionPane.showMessageDialog(frame, "Tiny Bioprint Service has stopped!", "Message", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    setUpThreads();
                    pollingThreadPerm.start();
                    //pollingThreadTemp.start();

                    serviceRunning = true;
                    serviceButton.setText("Stop Tiny Service");
                    serviceButton.setBackground(Color.RED);
                    JOptionPane.showMessageDialog(frame, "Tiny Bioprint Service has started!", "Message", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(serviceButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(scrollPane, BorderLayout.CENTER);
    }

    private void saveConfig() {
        /*try (PrintWriter writer = new PrintWriter(new FileWriter(configFileName))) {
            for (String printerName : printerCheckboxes.keySet()) {
                JCheckBox[] checkBoxes = printerCheckboxes.get(printerName);
                writer.println(printerName + "," + (checkBoxes[0].isSelected() ? "P" : "T"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFileName))) {
            for (String option : printerSelection.keySet()) {
                String printerName = printerSelection.get(option);
                if (!printerName.isEmpty()) {
                    writer.println(printerName + "," + option);
                }
            }
            writer.println("fid," + fid); // Save the integer field
        }*/
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFileName))) {
            for (String printerName : printerCheckboxes.keySet()) {
                JCheckBox[] checkBoxes = printerCheckboxes.get(printerName);
                String selectedOption = "";

                if (checkBoxes[0].isSelected()) {
                    selectedOption = "P";
                } else if (checkBoxes[1].isSelected()) {
                    selectedOption = "T";
                }

                if (!selectedOption.isEmpty()) {
                    writer.println(printerName + "," + selectedOption);
                }
            }
            writer.println("fid," + fid);
            writer.println("server," + BaseURL);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        File configFile = new File(configFileName);
        if (configFile.exists()) {
            /*try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String printerName = parts[0];
                        String selectedCheckBox = parts[1];
                        JCheckBox[] checkBoxes = printerCheckboxes.get(printerName);
                        if (checkBoxes != null) {
                            if ("P".equals(selectedCheckBox)) {
                                checkBoxes[0].setSelected(true);
                            } else if ("T".equals(selectedCheckBox)) {
                                checkBoxes[1].setSelected(true);
                            }
                        }
                    }
                }
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String key = parts[0];
                        String value = parts[1];
                        if (key.equals("fid")) {
                            fid = Integer.parseInt(value); // Set the integer field
                            facilityIdLabel.setText("Facility ID: " + fid);
                        } else {
                            String printerName = key;
                            String selectedOption = value;
                            JCheckBox[] checkBoxes = printerCheckboxes.get(printerName);
                            if (checkBoxes != null) {
                                for (JCheckBox checkBox : checkBoxes) {
                                    if (selectedOption.equals(checkBox.getText())) {
                                        checkBox.setSelected(true);
                                        printerSelection.put(selectedOption, printerName);
                                    }
                                }
                            }
                        }
                    }
                }
            }*/
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String key = parts[0];
                        String value = parts[1];
                        if (key.equals("fid")) {
                            fid = Integer.parseInt(value); // Set the integer field
                            facilityIdLabel.setText("Facility ID: " + fid); // Update the label
                        } else if (key.equals("server")) {
                            /*isDevServer =Boolean.parseBoolean(value);
                            if(!isDevServer){
                                BaseURL = "http://idms.kaa.go.ke/";
                            }*/
                            BaseURL = value;
                        } else {
                            String printerName = parts[0];
                            String selectedCheckBox = parts[1];
                            JCheckBox[] checkBoxes = printerCheckboxes.get(printerName);
                            if (checkBoxes != null) {
                                if ("P".equals(selectedCheckBox)) {
                                    checkBoxes[0].setSelected(true);
                                } else if ("T".equals(selectedCheckBox)) {
                                    checkBoxes[1].setSelected(true);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendAcknowledgement(String ackURL, Object[] ackData) {
        try {
            var postData = new HashMap<String, Object[]>() {
                {
                    put("ids", ackData);
                }
            };
            var objectMapper = new ObjectMapper();
            String requestBody = objectMapper
                    .writeValueAsString(postData);
            LOGGER.debug("Send acknowlegdgement request body: ", requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ackURL))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.debug("SendAckn to:" + ackURL);
            LOGGER.debug("SendAckn Response:" + response.body());

            try {
                String strResponse = "" + response.body();
                LOGGER.debug("strResponse:" + strResponse);
                var myObj = new JSONObject(strResponse);
                LOGGER.debug("SendAckn Response json obj:" + myObj);
                String status = myObj.getString("status");
                LOGGER.debug("SendAckn Response Status:" + status);
                if (status.equals("success")) {
                } else if (status.equals("failure")) {

                }
            } catch (Exception e) {
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("SendAckFailed", e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrinterConfigApp());
    }
}
