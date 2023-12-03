package org.example;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

class Delimiter {
    public static final String value= ";";
}

class Header {
    public String banCode;
    public double totalAmount;
    public double taxAmount;
    public long numOfDebitRows;

    Header(String headerRow) {
        String[] data = headerRow.split(Delimiter.value);

        int banCodeColIdx = 1;
        int banCodeLength = 8;
        this.banCode = data[banCodeColIdx].trim().substring(0, banCodeLength);

        int totalAmountColIdx = 7;
        String totalAmount = data[totalAmountColIdx].trim();
        this.totalAmount = Double.parseDouble(totalAmount.substring(0, totalAmount.length() - 1));

        int taxAmountColIdx = 8;
        String taxAmount = data[taxAmountColIdx].trim();
        this.taxAmount = Double.parseDouble(taxAmount.substring(0, taxAmount.length() - 1));

        int numOfDebitColumnsColIdx = 9;
        this.numOfDebitRows = Long.parseLong(data[numOfDebitColumnsColIdx].trim());
    }
}

class DebitRow {
    public String glCode;
    public double amount;
    public String taxCode;
    public String revenueType;
    public boolean isTax;

    DebitRow(String debitRow) {
        String[] data = debitRow.split(Delimiter.value);

        this.isTax = data[0].trim().equals("T");

        int glCodeColIdx = 1;
        this.glCode = String.valueOf(Long.parseLong(data[glCodeColIdx].trim()));

        int amountColIdx = 3;
        String amount = data[amountColIdx].trim();
        boolean isDebit = amount.substring(amount.length() - 1, amount.length()).equals("D");
        this.amount = Double.parseDouble(amount.substring(0, amount.length() - 1));
        if (isDebit) {
        	this.amount = -this.amount;
        }

        int taxCodeColIdx = 4;
        this.taxCode = data[taxCodeColIdx].trim();

        int revenueTypeColIdx = 5;
        this.revenueType = data[revenueTypeColIdx].trim();
    }
}

class Footer {
    public long totalNumberOfRows;
    public double totalAmount;
    public double totalTaxAmount;

    Footer(String headerRow) {
        String[] data = headerRow.split(Delimiter.value);

        int totalNumberOfRowsColIdx = 1;
        this.totalNumberOfRows = Long.parseLong(data[totalNumberOfRowsColIdx].trim());

        int totalAmountColIdx = 2;
        String totalAmount = data[totalAmountColIdx].trim();
        this.totalAmount = Double.parseDouble(totalAmount.substring(0, totalAmount.length() - 1));

        int taxAmountColIdx = 3;
        String taxAmount = data[taxAmountColIdx].trim();
        this.totalTaxAmount = Double.parseDouble(taxAmount.substring(0, taxAmount.length() - 1));
    }
}

class GLCodeRevenueTypeMap {
    HashMap<String, List<String>> map;

    GLCodeRevenueTypeMap() {
        map = new HashMap<>();
    }

    public void add(String glCode, String revenueType) {
        if (!this.map.containsKey(glCode)) {
            this.map.put(glCode, new ArrayList<>());
        }
        this.map.get(glCode).add(revenueType);
    }

    public boolean contains(String glCode, String revenueType) {
        if (!this.map.containsKey(glCode))
            return false;
        return this.map.get(glCode).contains(revenueType);
    }
}
class GLCodeRevenueTypeMapFile {
    public GLCodeRevenueTypeMap map;

    GLCodeRevenueTypeMapFile(Path excelFilePath) {
        this.map = new GLCodeRevenueTypeMap();

        int glCodeIndex = 1;
        int revenueTypeIndex = 0;

        try (InputStream is = new FileInputStream(excelFilePath.toFile()); ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.getFirstSheet();
            try (Stream<Row> rows = sheet.openStream()) {
                rows.forEach(r -> {
                    if (r.getRowNum() > 1 && !r.getCellText(0).trim().equals("")){
                        String glCode = r.getCellText(glCodeIndex).trim();
                        String revenueType = r.getCellText(revenueTypeIndex).trim();
                        this.map.add(glCode, revenueType);
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

class SAPFile {
    private final Path filePath;
    private final Header header;
    private final List<DebitRow> debitRows;
    private final Footer footer;
    private final JSONObject taxCodeBanCodeMap;
    GLCodeRevenueTypeMapFile glCodeRevenueTypeMapFile;

    public SAPFile(Path filePath, String header, List<String> debitRows, String footer, Path taxCodeBanCodeJsonFilePath, Path glCodeRevenueTypeMapFile) throws IOException, ParseException {
        this.filePath = filePath;
        this.header = new Header(header);
        this.debitRows = new ArrayList<>();
        debitRows.forEach(row -> {
        	if(!row.trim().equals("")) {
        		DebitRow debitRow = new DebitRow(row);
                this.debitRows.add(debitRow);
        	}
        });
        this.footer = new Footer(footer);
        this.taxCodeBanCodeMap = this.readJSONFile(taxCodeBanCodeJsonFilePath);
        this.glCodeRevenueTypeMapFile = new GLCodeRevenueTypeMapFile(glCodeRevenueTypeMapFile);
    }

    public void checkAllTestCases() {
        System.out.println("FILE " + filePath.getFileName());
        System.out.println("BAN CODE " + this.header.banCode);
        
        System.out.print("CHECKING TOTAL NUMBER OF ROWS IN HEADER: ");
        System.out.println(this.checkTotalNumberOfRowsInHeader()? "PASS": "FAIL");
        
        System.out.print("CHECKING TOTAL NUMBER OF ROWS IN FOOTER: ");
        System.out.println(this.checkTotalNumberOfRowsInFooter() ? "PASS": "FAIL");

        System.out.print("CHECKING HEADER TOTAL AMOUNT: ");
        System.out.println(this.checkHeaderTotalAmount() ? "PASS" : "FAIL");
        
        System.out.print("CHECKING HEADER TAX AMOUNT: ");
        System.out.println(this.checkHeaderTaxAmount() ? "PASS" : "FAIL");
        
        System.out.print("CHECKING FOOTER TOTAL AMOUNT: ");
        System.out.println(this.checkFooterTotalAmount() ? "PASS" : "FAIL");
        
        System.out.print("CHECKING FOOTER TAX AMOUNT: ");
        System.out.println(this.checkFooterTaxAmount() ? "PASS" : "FAIL");

        System.out.print("CHECKING TAX CODE BAN CODE MAPPING: ");
        System.out.println(this.checkTaxCodeBanCodeMapping()? "PASS": "FAIL");

//        System.out.print("CHECKING GL CODE REVENUE TYPE MAPPING: ");
//        System.out.println(this.checkGLCodeRevenueTypeMapping()? "PASS": "FAIL");

        System.out.println("\n\n");
    }
    private JSONObject readJSONFile(Path fileName) throws IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        String jsonString = new String(Files.readAllBytes(fileName));
        return (JSONObject) jsonParser.parse(jsonString);
    }
    
    private boolean checkHeaderTotalAmount() {
        double totalAmountForDebitRows = debitRows.stream()
                .filter(debitRow -> !debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);
        double totalTaxAmount = debitRows.stream()
                .filter(debitRow -> debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);

        return totalAmountForDebitRows + totalTaxAmount == this.header.totalAmount;
    }
    
    private boolean checkHeaderTaxAmount() {
        double totalTaxAmount = debitRows.stream()
                .filter(debitRow -> debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);

        return  totalTaxAmount == this.header.totalAmount;
    }

    private boolean checkFooterTotalAmount() {
        double totalAmountForDebitRows = debitRows.stream()
                .filter(debitRow -> !debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);

        return totalAmountForDebitRows == this.footer.totalAmount;
    }
    
    private boolean checkFooterTaxAmount() {
    	double totalTaxAmount = debitRows.stream()
                .filter(debitRow -> debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);
    	return totalTaxAmount == this.footer.totalTaxAmount;
    }

    private boolean checkTaxCodeBanCodeMapping() {
        boolean result = true;
        for (DebitRow debitRow: this.debitRows) {
        	if (debitRow.isTax && !debitRow.taxCode.equals("B1")) {
        		result = false;
        	}
            final JSONArray banCodes = (JSONArray) taxCodeBanCodeMap.get(debitRow.taxCode);
            if (banCodes == null)
                return false;
            boolean doesContains = false;
            for (Object banCode : banCodes) {
                doesContains = doesContains || banCode.toString().equals(this.header.banCode);
            }
            result = result && doesContains;
        }
        return result;
    }
    
    private boolean checkTotalNumberOfRowsInHeader() {
        return this.debitRows.size() == this.header.numOfDebitRows;
    }
    
    
    private boolean checkTotalNumberOfRowsInFooter() {
        return this.debitRows.size() + 2 == this.footer.totalNumberOfRows;
    }

    
//    private boolean checkGLCodeRevenueTypeMapping() {
//        boolean result = true;
//        for (DebitRow debitRow: this.debitRows) {
//            if (!glCodeRevenueTypeMapFile.map.contains(debitRow.glCode, debitRow.revenueType)) {
//                result = false;
//            }
//        }
//        return result;
//    }
}

public class SAPFileChecker {
    private final List<SAPFile> sapFiles;

    SAPFileChecker() {
        this.sapFiles = new ArrayList<>();
    }

    private void readFile(Path filePath, Path taxCodeBanCodeMapFilePath, Path glCodeRevenueTypeMapFile) throws IOException, ParseException {
        List<String> lines = Files.readAllLines(filePath);
        String header = lines.get(0);
        List<String> debitRows = lines.subList(1, lines.size() - 1);
        String footer = lines.get(lines.size() - 1);

        SAPFile file = new SAPFile(filePath, header, debitRows, footer, taxCodeBanCodeMapFilePath, glCodeRevenueTypeMapFile);
        this.sapFiles.add(file);
    }

    public static void main(String[] args) throws IOException, ParseException {
        String inputDir = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/test.txt";
        String taxCodeBanCodeMapFile = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/tax_code_ban_code_mapping.json";
        String glCodeRevenueTypeMapFile = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/Untitled 2.xlsx";
        SAPFileChecker sapFileChecker = new SAPFileChecker();
        
        File dir = new File(inputDir);
        File[] files = dir.listFiles();
        if (files == null) {
        	System.out.println("ERROR: Error in reading directory");
        }
        for (File file: files) {
        	if (!file.isDirectory()) {
        		sapFileChecker.readFile(Paths.get(file.getAbsolutePath()), Paths.get(taxCodeBanCodeMapFile), Paths.get(glCodeRevenueTypeMapFile));
        	}
        }
        for (SAPFile sapFile: sapFileChecker.sapFiles) {
        	sapFile.checkAllTestCases();
        }
        
    }

}
