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
    public long numOfDebitColumns;

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
        this.numOfDebitColumns = Long.parseLong(data[numOfDebitColumnsColIdx].trim());
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
        this.amount = Double.parseDouble(amount.substring(0, amount.length() - 1));

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
            DebitRow debitRow = new DebitRow(row);
            this.debitRows.add(debitRow);
        });
        this.footer = new Footer(footer);
        this.taxCodeBanCodeMap = this.readJSONFile(taxCodeBanCodeJsonFilePath);
        this.glCodeRevenueTypeMapFile = new GLCodeRevenueTypeMapFile(glCodeRevenueTypeMapFile);
    }

    public void checkAllTestCases() {
        System.out.println("FILE " + filePath.getFileName());

        System.out.print("CHECKING TOTAL AMOUNT: ");
        System.out.println(this.checkTotalAmount() ? "PASS" : "FAIL");

        System.out.print("CHECKING TAX CODE BAN CODE MAPPING: ");
        System.out.println(this.checkTaxCodeBanCodeMapping()? "PASS": "FAIL");

        System.out.print("CHECKING GL CODE REVENUE TYPE MAPPING: ");
        System.out.println(this.checkGLCodeRevenueTypeMapping()? "PASS": "FAIL");

        System.out.println("\n\n");
    }
    private JSONObject readJSONFile(Path fileName) throws IOException, ParseException {
        JSONParser jsonParser = new JSONParser();
        String jsonString = Files.readString(fileName);
        return (JSONObject) jsonParser.parse(jsonString);
    }

    private boolean checkTotalAmount() {
        double totalAmountForDebitRows = debitRows.stream()
                .filter(debitRow -> !debitRow.isTax)
                .map(debitRow -> debitRow.amount)
                .reduce(0.0, Double::sum);
        return totalAmountForDebitRows == this.header.totalAmount &&
                totalAmountForDebitRows == this.footer.totalAmount;
    }

    private boolean checkTaxCodeBanCodeMapping() {
        boolean result = true;
        for (DebitRow debitRow: this.debitRows) {
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

    private boolean checkGLCodeRevenueTypeMapping() {
        boolean result = true;
        for (DebitRow debitRow: this.debitRows) {
            if (!glCodeRevenueTypeMapFile.map.contains(debitRow.glCode, debitRow.revenueType)) {
                result = false;
            }
        }
        return result;
    }
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
        String inputFile = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/test.txt";
        String taxCodeBanCodeMapFile = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/tax_code_ban_code_mapping.json";
        String glCodeRevenueTypeMapFile = "/Users/ranajithore/IdeaProjects/QATools/src/main/java/org/example/Untitled 2.xlsx";
        SAPFileChecker sapFileChecker = new SAPFileChecker();
        sapFileChecker.readFile(Paths.get(inputFile), Paths.get(taxCodeBanCodeMapFile), Paths.get(glCodeRevenueTypeMapFile));
        sapFileChecker.sapFiles.get(0).checkAllTestCases();
    }

}