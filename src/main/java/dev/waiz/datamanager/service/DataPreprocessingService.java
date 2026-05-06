package dev.waiz.datamanager.service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.waiz.datamanager.model.exceldata;
import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.repository.ExcelDataRepository;
import dev.waiz.datamanager.repository.FileUploadRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataPreprocessingService {

    private final ExcelDataRepository excelDataRepository;
    private final FileUploadRepository fileUploadRepository;
    private final ObjectMapper objectMapper;

    public exceldata processExcelData(UUID uploadId,MultipartFile file) throws Exception{
        fileupload upload = fileUploadRepository.findById(uploadId)
        .orElseThrow(() -> new RuntimeException("Upload not found:" + uploadId));

        InputStream input = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(input);
        Sheet sheet = workbook.getSheetAt(0);

        Row headerrow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        for (Cell c:headerrow){
            headers.add(c.getStringCellValue().trim());
        }

        List<Map<String,String>> rows = new ArrayList<>();
        for (int i =1;i <= sheet.getLastRowNum();i++){
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Map<String,String> rowMap = new LinkedHashMap<>();
            for (int j = 0;j < headers.size();j++){
                Cell cell = row.getCell(j,Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                rowMap.put(headers.get(j), getCellValue(cell));
            }
            rows.add(rowMap);
        }
        workbook.close();


        String headersJson = objectMapper.writeValueAsString(headers);
        String rowJson = objectMapper.writeValueAsString(rows);

        exceldata data = new exceldata();
        data.setUpload(upload);
        data.setColumnHeader(headersJson);
        data.setRowData(rowJson);
        data.setRowCount(rows.size());
        data.setProcessedAt(OffsetDateTime.now());

        return excelDataRepository.save(data);
    }

    public List<exceldata> getByUploadId (UUID uploadId){
        return excelDataRepository.findByUpload_UploadId(uploadId);
    }

    private String getCellValue(Cell cell){
        return switch (cell.getCellType()){
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
            ? cell.getLocalDateTimeCellValue().toString()
            :String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

}
